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
import org.katacr.kaGameCenter.spectator.SpectatorService
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.velocity.VelocityBridgeService
import org.katacr.kaGameCenter.velocity.VelocityReserveRoomRequest
import org.katacr.kaGameCenter.velocity.VelocityReserveRoomResponse
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class GameRoomManager(
    private val plugin: JavaPlugin,
    private val registry: GameRegistry,
    private val gameManager: GameManager,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val worldService: TemporaryWorldService,
    private val statsService: PlayerStatsService,
    private val snapshotService: PlayerSnapshotService,
    private val displayService: GameDisplayService,
    private val spectatorService: SpectatorService,
    private val languageManager: LanguageManager,
    private val teamService: GameTeamService,
    private val velocityBridgeService: VelocityBridgeService
) {
    private val rooms = linkedMapOf<String, GameRoom>()
    private val playerSessions = linkedMapOf<UUID, PlayerSession>()
    private var tickTask: BukkitTask? = null
    private var velocityHeartbeatTask: BukkitTask? = null

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
        if (velocityBridgeService.enabled && velocityHeartbeatTask == null) {
            velocityHeartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                velocityBridgeService.publishAll(rooms.values.filter { it.state != GameState.CLOSED })
            }, velocityBridgeService.heartbeatIntervalTicks, velocityBridgeService.heartbeatIntervalTicks)
        }
    }

    fun stop() {
        tickTask?.cancel()
        tickTask = null
        velocityHeartbeatTask?.cancel()
        velocityHeartbeatTask = null
        rooms.keys.toList().forEach { closeRoom(it) }
    }

    fun createRoom(gameId: String, owner: UUID? = null, mapTemplate: String? = null, name: String? = null): GameRoom? {
        if (owner != null && isPlaying(owner)) return null
        val managedGame = managedGameCatalog.get(gameId)
        val module = registry.get(managedGame?.moduleId ?: gameId) ?: return null
        val definition = managedGame?.toDefinition(module.defaultDefinition()) ?: gameManager.get(gameId) ?: module.defaultDefinition()
        if (!definition.enabled) return null
        if (managedGame != null && !managedGame.hasPrivateSnapshot()) {
            plugin.logger.warning(languageManager.getMessage("managed_game.private_snapshot_missing", managedGame.globalId, managedGame.runtimeMapFolder.absolutePath))
            return null
        }
        val missingPlugins = missingRequiredPlugins(definition)
        if (missingPlugins.isNotEmpty()) {
            plugin.logger.warning(languageManager.getMessage("game.missing_required_plugins", definition.id, missingPlugins.joinToString(", ")))
            return null
        }
        val roomId = nextRoomId() ?: return null
        val room = GameRoom(roomId, module)
        room.definition = definition
        room.configuredGame = managedGame
        room.mapTemplate = managedGame?.effectiveMapTemplate() ?: mapTemplate
        room.templateDirectory = managedGame?.runtimeMapFolder?.takeIf { it.isDirectory }
        room.name = sanitizeRoomName(name, definition.displayName)
        room.owner = owner
        room.session = module.createSession(room)
        rooms[room.id] = room
        publishRoom(room)
        return room
    }

    fun createRoomFailureMessage(gameId: String): String {
        val managedGame = managedGameCatalog.get(gameId)
        val module = registry.get(managedGame?.moduleId ?: gameId) ?: return languageManager.getMessage("command.game_not_found", gameId)
        val definition = managedGame?.toDefinition(module.defaultDefinition()) ?: gameManager.get(gameId) ?: module.defaultDefinition()
        if (!definition.enabled) return languageManager.getMessage("game.disabled", definition.id)
        if (managedGame != null && !managedGame.hasPrivateSnapshot()) {
            return languageManager.getMessage("managed_game.private_snapshot_missing", managedGame.globalId, managedGame.runtimeMapFolder.absolutePath)
        }

        val missingPlugins = missingRequiredPlugins(definition)
        if (missingPlugins.isNotEmpty()) {
            return languageManager.getMessage("game.missing_required_plugins", definition.id, missingPlugins.joinToString(", "))
        }

        return languageManager.getMessage("room.create_failed", definition.id)
    }

    fun prepareRoom(roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        if (room.state != GameState.CREATED) return true

        room.state = GameState.PREPARING
        displayService.markPreparing(room)
        publishRoom(room)
        room.session.onPrepare()
        if (room.world == null) {
            room.state = GameState.CREATED
            displayService.update(room)
            publishRoom(room)
            return false
        }
        room.state = GameState.WAITING
        displayService.markWaiting(room)
        publishRoom(room)
        return true
    }

    fun joinRoom(player: Player, roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        val currentSession = currentSession(player.uniqueId)
        if (currentSession?.roomId == roomId && !currentSession.spectator && room.players.contains(player.uniqueId)) {
            displayService.update(room)
            return true
        }
        if (currentSession != null && !currentSession.spectator) {
            player.sendMessage(Component.text(languageManager.getMessage("room.already_in_room")))
            return false
        }
        if (!room.canJoin()) {
            player.sendMessage(Component.text(languageManager.getMessage("room.room_not_joinable")))
            return false
        }
        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        if (room.players.size >= maxPlayers && currentSession?.roomId != roomId) {
            player.sendMessage(Component.text(languageManager.getMessage("room.room_full")))
            return false
        }

        if (!prepareRoom(room.id)) return false
        if (currentSession?.roomId == roomId && currentSession.spectator) {
            room.spectators.remove(player.uniqueId)
            room.session.onSpectatorLeave(player)
            displayService.detach(player, room)
            spectatorService.exit(player)
            snapshotService.restore(player)
            snapshotService.clear(player.uniqueId)
        } else {
            leaveCurrentRoom(player)
        }
        snapshotService.captureIfAbsent(player)

        val added = room.players.add(player.uniqueId)
        playerSessions[player.uniqueId] = PlayerSession(player.uniqueId, room.id)
        displayService.attach(player, room)
        if (added) {
            room.session.onPlayerJoin(player)
            statsService.recordPlay(player.uniqueId, room.module.id)
        }
        publishRoom(room)
        return true
    }

    fun spectateRoom(player: Player, roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        val currentSession = currentSession(player.uniqueId)
        if (currentSession != null && !currentSession.spectator) {
            player.sendMessage(Component.text(languageManager.getMessage("room.already_in_room")))
            return false
        }
        val policy = room.module.spectatorPolicy(room)
        if (!spectatorService.canSpectate(room, policy)) {
            player.sendMessage(Component.text(languageManager.getMessage("spectator.not_allowed")))
            return false
        }
        if (currentSession?.roomId == roomId && currentSession.spectator && room.spectators.contains(player.uniqueId)) {
            displayService.update(room)
            return true
        }
        if (!prepareRoom(room.id)) return false

        leaveCurrentRoom(player)
        snapshotService.captureIfAbsent(player)

        room.spectators.add(player.uniqueId)
        playerSessions[player.uniqueId] = PlayerSession(player.uniqueId, room.id, spectator = true)
        displayService.attach(player, room)
        spectatorService.enter(player, room, policy)
        room.session.onSpectatorJoin(player)
        publishRoom(room)
        return true
    }

    fun joinNewRoom(player: Player, gameId: String): GameRoom? {
        if (isPlaying(player.uniqueId)) {
            player.sendMessage(Component.text(languageManager.getMessage("room.already_in_room")))
            return null
        }
        val room = createRoom(gameId) ?: return null
        return if (joinRoom(player, room.id)) room else null
    }

    fun leaveCurrentRoom(player: Player): Boolean {
        val session = playerSessions.remove(player.uniqueId) ?: return false
        val room = rooms[session.roomId] ?: return false

        room.players.remove(player.uniqueId)
        val wasSpectator = room.spectators.remove(player.uniqueId)
        teamService.leave(room.id, player.uniqueId)
        if (wasSpectator) {
            room.session.onSpectatorLeave(player)
            spectatorService.exit(player)
        } else {
            room.session.onPlayerLeave(player)
        }
        displayService.detach(player, room)
        snapshotService.restore(player)
        snapshotService.clear(player.uniqueId)
        if (room.players.isEmpty() && room.spectators.isEmpty()) {
            closeRoom(room.id)
        } else if (room.owner == player.uniqueId) {
            room.owner = room.players.firstOrNull()
            displayService.update(room)
            publishRoom(room)
        } else {
            publishRoom(room)
        }
        return true
    }

    fun kickPlayer(roomId: String, targetId: UUID): Boolean {
        val player = Bukkit.getPlayer(targetId) ?: return false
        val room = rooms[roomId] ?: return false
        if (!room.players.contains(targetId) && !room.spectators.contains(targetId)) return false
        return leaveCurrentRoom(player)
    }

    fun transferOwner(roomId: String, targetId: UUID): Boolean {
        val room = rooms[roomId] ?: return false
        if (!room.players.contains(targetId)) return false
        room.owner = targetId
        displayService.update(room)
        publishRoom(room)
        return true
    }

    fun isOwner(room: GameRoom, playerId: UUID): Boolean {
        return room.owner == playerId
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
        publishRoom(room)
        return true
    }

    fun closeRoom(roomId: String): Boolean {
        val room = rooms.remove(roomId) ?: return false
        velocityBridgeService.removeRoom(room)
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
                room.session.onSpectatorLeave(it)
                spectatorService.exit(it)
                snapshotService.restore(it)
            }
            playerSessions.remove(playerId)
        }
        displayService.markClosed(room)
        room.session.onClose()
        spectatorService.clearRoom(room.id)
        teamService.clearRoom(room.id)
        room.world?.let { worldService.unloadAndDelete(it.name) }
        room.state = GameState.CLOSED
        return true
    }

    fun getRoom(roomId: String): GameRoom? = rooms[roomId]

    fun getPlayerRoom(player: Player): GameRoom? = playerSessions[player.uniqueId]?.let { rooms[it.roomId] }

    fun getPlayerRoom(playerId: UUID): GameRoom? = playerSessions[playerId]?.let { rooms[it.roomId] }

    fun getPlayerRoomId(playerId: UUID): String? = playerSessions[playerId]?.roomId

    fun isSpectator(playerId: UUID): Boolean = playerSessions[playerId]?.spectator == true

    fun isPlaying(playerId: UUID): Boolean = currentSession(playerId)?.spectator == false

    private fun currentSession(playerId: UUID): PlayerSession? {
        val session = playerSessions[playerId] ?: return null
        if (rooms.containsKey(session.roomId)) return session
        playerSessions.remove(playerId)
        return null
    }

    fun listRooms(): Collection<GameRoom> = rooms.values

    fun listModules(): Collection<GameModule> = registry.all()

    fun listDefinitions(): Collection<GameDefinition> = gameManager.all()

    fun listConfiguredGames(): Collection<ManagedGameConfig> = managedGameCatalog.enabled()

    fun missingRequiredPlugins(definition: GameDefinition): List<String> {
        return definition.requiredPlugins.filterNot { pluginName ->
            Bukkit.getPluginManager().isPluginEnabled(pluginName)
        }
    }

    fun status(): String {
        if (rooms.isEmpty()) return languageManager.getMessage("room.status_empty")
        return rooms.values.joinToString(separator = "\n") {
            languageManager.getMessage("room.status_line", it.id, it.name, it.module.id, it.mapTemplate ?: "-", it.state, it.players.size, it.world?.name ?: "-")
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

    fun reserveRoomForProxy(request: VelocityReserveRoomRequest): VelocityReserveRoomResponse {
        val room = rooms[request.targetRoomId]
            ?: return reservationRejected(request, "ROOM_NOT_FOUND")
        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        if (!room.canJoin()) {
            return reservationRejected(request, "ROOM_NOT_JOINABLE")
        }
        if (room.players.size >= maxPlayers && !room.players.contains(request.playerId)) {
            return reservationRejected(request, "ROOM_FULL")
        }
        return VelocityReserveRoomResponse(
            requestId = request.requestId,
            accepted = true,
            targetServerId = request.targetServerId,
            targetRoomId = request.targetRoomId
        )
    }

    private fun nextRoomId(): String? {
        repeat(100) {
            val candidate = ThreadLocalRandom.current().nextInt(1000, 10000).toString()
            if (!rooms.containsKey(candidate)) return candidate
        }
        return (1000..9999)
            .asSequence()
            .map { it.toString() }
            .firstOrNull { !rooms.containsKey(it) }
    }

    private fun sanitizeRoomName(name: String?, fallback: String): String {
        val trimmed = name?.trim().orEmpty()
        val value = trimmed.ifBlank { fallback }
        return value.take(16)
    }

    private fun publishRoom(room: GameRoom) {
        if (velocityBridgeService.enabled) {
            velocityBridgeService.publishRoom(room)
        }
    }

    private fun reservationRejected(request: VelocityReserveRoomRequest, reason: String): VelocityReserveRoomResponse {
        return VelocityReserveRoomResponse(
            requestId = request.requestId,
            accepted = false,
            targetServerId = request.targetServerId,
            targetRoomId = request.targetRoomId,
            reason = reason
        )
    }
}

private fun ManagedGameConfig.toDefinition(base: GameDefinition): GameDefinition {
    return base.copy(
        id = globalId,
        displayName = displayName,
        enabled = enabled,
        minPlayers = minPlayers ?: base.minPlayers,
        maxPlayers = maxPlayers ?: base.maxPlayers,
        mapTemplates = listOf(effectiveMapTemplate()),
        description = description.ifBlank { base.description }
    )
}
