package org.katacr.kaGameCenter.game

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaGameCenter.data.PlayerSnapshotService
import org.katacr.kaGameCenter.data.PlayerStatsService
import org.katacr.kaGameCenter.display.GameDisplayService
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class GameRoomManager(
    private val plugin: JavaPlugin,
    private val registry: GameRegistry,
    private val gameManager: GameManager,
    private val worldService: TemporaryWorldService,
    private val statsService: PlayerStatsService,
    private val snapshotService: PlayerSnapshotService,
    private val displayService: GameDisplayService,
    private val languageManager: LanguageManager
) {
    private val roomSequence = AtomicInteger(1)
    private val rooms = linkedMapOf<String, GameRoom>()
    private val playerSessions = linkedMapOf<UUID, PlayerSession>()
    private var tickTask: BukkitTask? = null

    fun start() {
        if (tickTask != null) return
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            rooms.values
                .filter { it.state != GameState.CLOSED }
                .forEach {
                    it.session.onTick()
                    displayService.update(it)
                }
        }, 1L, 1L)
    }

    fun stop() {
        tickTask?.cancel()
        tickTask = null
        rooms.keys.toList().forEach { closeRoom(it) }
    }

    fun createRoom(gameId: String): GameRoom? {
        val module = registry.get(gameId) ?: return null
        val definition = gameManager.get(gameId) ?: module.defaultDefinition()
        if (!definition.enabled) return null
        val room = GameRoom(nextRoomId(module.id), module)
        room.definition = definition
        room.session = module.createSession(room)
        rooms[room.id] = room
        return room
    }

    fun prepareRoom(roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        if (room.state != GameState.CREATED) return true

        room.state = GameState.PREPARING
        displayService.markPreparing(room)
        room.session.onPrepare()
        if (room.world == null) {
            room.state = GameState.CREATED
            displayService.update(room)
            return false
        }
        room.state = GameState.WAITING
        displayService.markWaiting(room)
        return true
    }

    fun joinRoom(player: Player, roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        if (!room.canJoin()) {
            player.sendMessage(Component.text(languageManager.getMessage("room.room_not_joinable")))
            return false
        }
        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        if (room.players.size >= maxPlayers) {
            player.sendMessage(Component.text(languageManager.getMessage("room.room_full")))
            return false
        }

        if (!prepareRoom(room.id)) return false
        leaveCurrentRoom(player)
        snapshotService.captureIfAbsent(player)

        room.players.add(player.uniqueId)
        playerSessions[player.uniqueId] = PlayerSession(player.uniqueId, room.id)
        displayService.attach(player, room)
        room.session.onPlayerJoin(player)
        statsService.recordPlay(player.uniqueId, room.module.id)
        return true
    }

    fun joinNewRoom(player: Player, gameId: String): GameRoom? {
        val room = createRoom(gameId) ?: return null
        return if (joinRoom(player, room.id)) room else null
    }

    fun leaveCurrentRoom(player: Player): Boolean {
        val session = playerSessions.remove(player.uniqueId) ?: return false
        val room = rooms[session.roomId] ?: return false

        room.players.remove(player.uniqueId)
        room.spectators.remove(player.uniqueId)
        room.session.onPlayerLeave(player)
        displayService.detach(player, room)
        snapshotService.restore(player)
        snapshotService.clear(player.uniqueId)
        if (room.players.isEmpty() && room.spectators.isEmpty()) {
            closeRoom(room.id)
        }
        return true
    }

    fun startRoom(roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        if (room.state == GameState.RUNNING) return true
        if (!prepareRoom(roomId)) return false
        val minPlayers = room.definition?.minPlayers ?: room.module.minPlayers
        if (room.players.size < minPlayers) return false

        room.state = GameState.RUNNING
        room.session.onStart()
        displayService.markStarted(room)
        return true
    }

    fun closeRoom(roomId: String): Boolean {
        val room = rooms.remove(roomId) ?: return false
        room.state = GameState.ENDING
        room.session.onEnd()
        room.players.forEach { playerId ->
            Bukkit.getPlayer(playerId)?.let {
                displayService.detach(it, room)
                snapshotService.restore(it)
            }
            playerSessions.remove(playerId)
        }
        room.spectators.forEach { playerId ->
            Bukkit.getPlayer(playerId)?.let {
                displayService.detach(it, room)
                snapshotService.restore(it)
            }
            playerSessions.remove(playerId)
        }
        displayService.markClosed(room)
        room.session.onClose()
        room.world?.let { worldService.unloadAndDelete(it.name) }
        room.state = GameState.CLOSED
        return true
    }

    fun getRoom(roomId: String): GameRoom? = rooms[roomId]

    fun getPlayerRoom(player: Player): GameRoom? = playerSessions[player.uniqueId]?.let { rooms[it.roomId] }

    fun getPlayerRoom(playerId: UUID): GameRoom? = playerSessions[playerId]?.let { rooms[it.roomId] }

    fun getPlayerRoomId(playerId: UUID): String? = playerSessions[playerId]?.roomId

    fun listRooms(): Collection<GameRoom> = rooms.values

    fun listModules(): Collection<GameModule> = registry.all()

    fun listDefinitions(): Collection<GameDefinition> = gameManager.all()

    fun status(): String {
        if (rooms.isEmpty()) return languageManager.getMessage("room.status_empty")
        return rooms.values.joinToString(separator = "\n") {
            languageManager.getMessage("room.status_line", it.id, it.module.id, it.state, it.players.size, it.world?.name ?: "-")
        }
    }

    fun recordKill(killer: UUID, victim: UUID, gameId: String, points: Int = 0) {
        statsService.recordKill(killer, gameId, points)
        statsService.recordDeath(victim, gameId)
    }

    fun recordWin(playerId: UUID, gameId: String, points: Int = 0) {
        statsService.recordWin(playerId, gameId, points)
    }

    fun recordLoss(playerId: UUID, gameId: String) {
        statsService.recordLoss(playerId, gameId)
    }

    fun recordDeath(playerId: UUID, gameId: String) {
        statsService.recordDeath(playerId, gameId)
    }

    fun statsSnapshot(): Collection<org.katacr.kaGameCenter.data.PlayerGameStats> = statsService.all()

    private fun nextRoomId(gameId: String): String {
        return "${gameId}_${roomSequence.getAndIncrement().toString().padStart(3, '0')}"
    }
}
