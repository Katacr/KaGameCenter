package org.katacr.kaGameCenter.game

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaGameCenter.data.PlayerSnapshotService
import org.katacr.kaGameCenter.data.PlayerStatsService
import org.katacr.kaGameCenter.display.GameDisplayService
import org.katacr.kaGameCenter.elimination.PlayerEliminationService
import org.katacr.kaGameCenter.event.GamePlayerRoomAdmissionDeniedEvent
import org.katacr.kaGameCenter.event.GamePlayerRoomJoinEvent
import org.katacr.kaGameCenter.event.GamePlayerRoomLeaveEvent
import org.katacr.kaGameCenter.event.GamePlayerRoomReconnectEvent
import org.katacr.kaGameCenter.event.GameRoomAdmissionDeniedReason
import org.katacr.kaGameCenter.event.GameRoomAdmissionType
import org.katacr.kaGameCenter.event.GameRoomClosedEvent
import org.katacr.kaGameCenter.event.GameRoomLeaveReason
import org.katacr.kaGameCenter.event.GameRoomPreparedEvent
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.nametag.PlayerNametagService
import org.katacr.kaGameCenter.resource.RoomResourceScopeService
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
    private val velocityBridgeService: VelocityBridgeService,
    private val nametagService: PlayerNametagService,
    private val eliminationService: PlayerEliminationService,
    private val roomResourceScopeService: RoomResourceScopeService
) {
    private val rooms = linkedMapOf<String, GameRoom>()
    private val playerSessions = linkedMapOf<UUID, PlayerSession>()
    private val reconnectExpiryTasks = linkedMapOf<UUID, BukkitTask>()
    private val pendingSnapshotRestores = linkedSetOf<UUID>()
    private var tickTask: BukkitTask? = null
    private var velocityHeartbeatTask: BukkitTask? = null

    fun start() {
        if (tickTask != null) return
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            rooms.values
                .toList()
                .filter { it.state != GameState.CLOSED }
                .forEach(::runRoomTick)
        }, 1L, 1L)
        if (velocityBridgeService.enabled && velocityHeartbeatTask == null) {
            velocityHeartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                rooms.values
                    .toList()
                    .filter { rooms[it.id] === it && it.state != GameState.CLOSED }
                    .forEach { room ->
                        runCatching { velocityBridgeService.publishRoom(room) }
                            .onFailure {
                                plugin.logger.warning("Failed to publish heartbeat for room ${room.id}: ${it.message}")
                            }
                    }
            }, velocityBridgeService.heartbeatIntervalTicks, velocityBridgeService.heartbeatIntervalTicks)
        }
    }

    fun stop() {
        tickTask?.cancel()
        tickTask = null
        velocityHeartbeatTask?.cancel()
        velocityHeartbeatTask = null
        reconnectExpiryTasks.values.forEach(BukkitTask::cancel)
        reconnectExpiryTasks.clear()
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
        val session = runCatching { module.createSession(room) }
            .onFailure { plugin.logger.warning("Failed to create game session for ${module.id}: ${it.message}") }
            .getOrNull() ?: return null
        if (session.room !== room) {
            plugin.logger.warning("Failed to create game session for ${module.id}: session returned a different room")
            runCatching { session.onClose() }
                .onFailure { plugin.logger.warning("Failed to close rejected game session for ${module.id}: ${it.message}") }
            return null
        }
        room.session = session
        rooms[room.id] = room
        val publishFailure = runCatching { publishRoom(room) }.exceptionOrNull()
        if (publishFailure != null) {
            failActiveRoom(room, "publish created room", publishFailure)
            return null
        }
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
        if (room.state != GameState.CREATED) {
            return room.state == GameState.WAITING ||
                room.state == GameState.COUNTDOWN ||
                room.state == GameState.RUNNING
        }

        val transitionFailure = runCatching { room.state = GameState.PREPARING }.exceptionOrNull()
        if (transitionFailure != null) {
            failActiveRoom(room, "enter room preparation", transitionFailure)
            return false
        }
        if (rooms[roomId] !== room || room.state != GameState.PREPARING) return false
        val prepareFailure = runCatching {
            displayService.markPreparing(room)
            publishRoom(room)
            room.session.onPrepare()
        }.exceptionOrNull()
        if (prepareFailure != null) {
            plugin.logger.warning("Failed to prepare game room ${room.id}: ${prepareFailure.message}")
            if (rooms[roomId] === room) closeRoom(roomId)
            return false
        }
        if (rooms[roomId] !== room) return false
        if (room.state != GameState.PREPARING) {
            return room.world != null && (
                room.state == GameState.WAITING ||
                    room.state == GameState.COUNTDOWN ||
                    room.state == GameState.RUNNING
                )
        }
        val preparedWorld = room.world
        if (preparedWorld == null) {
            val rollbackFailure = runCatching {
                room.state = GameState.CREATED
                displayService.update(room)
                publishRoom(room)
            }.exceptionOrNull()
            if (rollbackFailure != null) failActiveRoom(room, "rollback empty room preparation", rollbackFailure)
            return false
        }
        val commitFailure = runCatching {
            room.state = GameState.WAITING
            check(rooms[roomId] === room && room.state == GameState.WAITING)
            displayService.markWaiting(room)
            publishRoom(room)
            Bukkit.getPluginManager().callEvent(GameRoomPreparedEvent(room, preparedWorld.name))
        }.exceptionOrNull()
        if (commitFailure != null) {
            plugin.logger.warning("Failed to commit prepared game room ${room.id}: ${commitFailure.message}")
            if (rooms[roomId] === room) closeRoom(roomId)
            return false
        }
        return rooms[roomId] === room && (
            room.state == GameState.WAITING ||
                room.state == GameState.COUNTDOWN ||
                room.state == GameState.RUNNING
            )
    }

    fun joinRoom(player: Player, roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        val currentSession = currentSession(player.uniqueId)
        if (currentSession?.roomId == roomId && !currentSession.spectator && room.players.contains(player.uniqueId)) {
            val displayFailure = runCatching { displayService.update(room) }.exceptionOrNull()
            if (displayFailure != null) {
                failActiveRoom(room, "refresh existing player ${player.uniqueId}", displayFailure)
                return false
            }
            return true
        }
        if (currentSession == null && !restoreSnapshotBeforeAdmission(player, room, GameRoomAdmissionType.JOIN)) {
            return false
        }
        if (currentSession != null && !currentSession.spectator) {
            player.sendMessage(Component.text(languageManager.getMessage("room.already_in_room")))
            publishAdmissionDenied(room, player, GameRoomAdmissionType.JOIN, GameRoomAdmissionDeniedReason.ALREADY_ACTIVE)
            return false
        }
        if (!room.canJoin()) {
            player.sendMessage(Component.text(languageManager.getMessage("room.room_not_joinable")))
            publishAdmissionDenied(room, player, GameRoomAdmissionType.JOIN, GameRoomAdmissionDeniedReason.ROOM_NOT_JOINABLE)
            return false
        }
        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        if (room.players.size >= maxPlayers && currentSession?.roomId != roomId) {
            player.sendMessage(Component.text(languageManager.getMessage("room.room_full")))
            publishAdmissionDenied(room, player, GameRoomAdmissionType.JOIN, GameRoomAdmissionDeniedReason.ROOM_FULL)
            return false
        }

        if (!prepareRoom(room.id)) {
            publishAdmissionDenied(room, player, GameRoomAdmissionType.JOIN, GameRoomAdmissionDeniedReason.PREPARATION_FAILED)
            return false
        }
        val joinEvent = GamePlayerRoomJoinEvent(room, player, spectator = false)
        val joinEventFailure = runCatching { Bukkit.getPluginManager().callEvent(joinEvent) }.exceptionOrNull()
        if (joinEventFailure != null) {
            plugin.logger.warning("Failed to check player admission for ${player.uniqueId} in room ${room.id}: ${joinEventFailure.message}")
            publishAdmissionDenied(room, player, GameRoomAdmissionType.JOIN, GameRoomAdmissionDeniedReason.CALLBACK_FAILED)
            return false
        }
        if (rooms[room.id] !== room) return false
        if (joinEvent.isCancelled) {
            publishAdmissionDenied(room, player, GameRoomAdmissionType.JOIN, GameRoomAdmissionDeniedReason.EVENT_CANCELLED)
            return false
        }
        if (currentSession?.roomId == roomId && currentSession.spectator) {
            removePlayerMembership(
                player,
                room,
                wasSpectator = true,
                reason = GameRoomLeaveReason.ROLE_CHANGE,
                closeWhenEmpty = false
            )
        } else {
            leaveCurrentRoom(player, GameRoomLeaveReason.ROLE_CHANGE)
        }

        var added = false
        var callbackStarted = false
        val admissionFailure = runCatching {
            snapshotService.captureIfAbsent(player)
            added = room.players.add(player.uniqueId)
            playerSessions[player.uniqueId] = PlayerSession(player.uniqueId, room.id)
            displayService.attach(player, room)
            if (added) {
                callbackStarted = true
                room.session.onPlayerJoin(player)
            }
            check(rooms[room.id] === room && room.players.contains(player.uniqueId))
        }.exceptionOrNull()
        if (admissionFailure != null) {
            plugin.logger.warning("Failed to admit player ${player.uniqueId} to room ${room.id}: ${admissionFailure.message}")
            if (rooms[room.id] === room) {
                removePlayerMembership(
                    player,
                    room,
                    wasSpectator = false,
                    reason = GameRoomLeaveReason.ROLE_CHANGE,
                    notifySession = callbackStarted,
                    publishLeave = false
                )
            }
            publishAdmissionDenied(room, player, GameRoomAdmissionType.JOIN, GameRoomAdmissionDeniedReason.CALLBACK_FAILED)
            return false
        }
        if (added) runRoomCleanup(room, "record play for ${player.uniqueId}") {
            statsService.recordPlay(player.uniqueId, room.module.id)
        }
        runRoomCleanup(room, "refresh viewer ${player.uniqueId}") { nametagService.refreshViewer(room, player) }
        runRoomCleanup(room, "refresh room nametags") { nametagService.refreshRoom(room) }
        runRoomCleanup(room, "publish admitted player ${player.uniqueId}") { publishRoom(room) }
        return true
    }

    fun spectateRoom(player: Player, roomId: String): Boolean {
        val room = rooms[roomId] ?: return false
        val currentSession = currentSession(player.uniqueId)
        if (currentSession != null && !currentSession.spectator) {
            player.sendMessage(Component.text(languageManager.getMessage("room.already_in_room")))
            publishAdmissionDenied(room, player, GameRoomAdmissionType.SPECTATE, GameRoomAdmissionDeniedReason.ALREADY_ACTIVE)
            return false
        }
        val policy = runCatching { room.module.spectatorPolicy(room) }
            .onFailure {
                plugin.logger.warning("Failed to read spectator policy for ${player.uniqueId} in room ${room.id}: ${it.message}")
            }
            .getOrElse {
                publishAdmissionDenied(room, player, GameRoomAdmissionType.SPECTATE, GameRoomAdmissionDeniedReason.CALLBACK_FAILED)
                return false
            }
        val canSpectate = runCatching {
            room.module.canSpectate(room, player) && spectatorService.canSpectate(room, policy)
        }.onFailure {
            plugin.logger.warning("Failed to check spectator admission for ${player.uniqueId} in room ${room.id}: ${it.message}")
        }.getOrElse {
            publishAdmissionDenied(room, player, GameRoomAdmissionType.SPECTATE, GameRoomAdmissionDeniedReason.CALLBACK_FAILED)
            return false
        }
        if (!canSpectate) {
            player.sendMessage(Component.text(languageManager.getMessage("spectator.not_allowed")))
            publishAdmissionDenied(
                room,
                player,
                GameRoomAdmissionType.SPECTATE,
                GameRoomAdmissionDeniedReason.SPECTATE_NOT_ALLOWED
            )
            return false
        }
        if (currentSession?.roomId == roomId && currentSession.spectator && room.spectators.contains(player.uniqueId)) {
            val displayFailure = runCatching { displayService.update(room) }.exceptionOrNull()
            if (displayFailure != null) {
                failActiveRoom(room, "refresh existing spectator ${player.uniqueId}", displayFailure)
                return false
            }
            return true
        }
        if (currentSession == null && !restoreSnapshotBeforeAdmission(player, room, GameRoomAdmissionType.SPECTATE)) {
            return false
        }
        if (!prepareRoom(room.id)) {
            publishAdmissionDenied(room, player, GameRoomAdmissionType.SPECTATE, GameRoomAdmissionDeniedReason.PREPARATION_FAILED)
            return false
        }
        val joinEvent = GamePlayerRoomJoinEvent(room, player, spectator = true)
        val joinEventFailure = runCatching { Bukkit.getPluginManager().callEvent(joinEvent) }.exceptionOrNull()
        if (joinEventFailure != null) {
            plugin.logger.warning("Failed to check spectator admission for ${player.uniqueId} in room ${room.id}: ${joinEventFailure.message}")
            publishAdmissionDenied(room, player, GameRoomAdmissionType.SPECTATE, GameRoomAdmissionDeniedReason.CALLBACK_FAILED)
            return false
        }
        if (rooms[room.id] !== room) return false
        if (joinEvent.isCancelled) {
            publishAdmissionDenied(room, player, GameRoomAdmissionType.SPECTATE, GameRoomAdmissionDeniedReason.EVENT_CANCELLED)
            return false
        }

        leaveCurrentRoom(player, GameRoomLeaveReason.ROLE_CHANGE)

        var callbackStarted = false
        val admissionFailure = runCatching {
            snapshotService.captureIfAbsent(player)
            room.spectators.add(player.uniqueId)
            playerSessions[player.uniqueId] = PlayerSession(player.uniqueId, room.id, spectator = true)
            displayService.attach(player, room)
            callbackStarted = true
            spectatorService.enter(player, room, policy)
            room.session.onSpectatorJoin(player)
            check(rooms[room.id] === room && room.spectators.contains(player.uniqueId))
        }.exceptionOrNull()
        if (admissionFailure != null) {
            plugin.logger.warning("Failed to admit spectator ${player.uniqueId} to room ${room.id}: ${admissionFailure.message}")
            if (rooms[room.id] === room) {
                removePlayerMembership(
                    player,
                    room,
                    wasSpectator = true,
                    reason = GameRoomLeaveReason.ROLE_CHANGE,
                    notifySession = callbackStarted,
                    publishLeave = false
                )
            }
            publishAdmissionDenied(room, player, GameRoomAdmissionType.SPECTATE, GameRoomAdmissionDeniedReason.CALLBACK_FAILED)
            return false
        }
        runRoomCleanup(room, "refresh spectator ${player.uniqueId}") { nametagService.refreshViewer(room, player) }
        runRoomCleanup(room, "publish admitted spectator ${player.uniqueId}") { publishRoom(room) }
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

    /** 结束玩家当前房间成员关系，并在全部运行状态清理后发布离开原因。 */
    fun leaveCurrentRoom(player: Player, reason: GameRoomLeaveReason = GameRoomLeaveReason.LEAVE): Boolean {
        val session = playerSessions[player.uniqueId] ?: return false
        val room = rooms[session.roomId] ?: return false
        val wasSpectator = session.spectator
        val lastDamager = if (wasSpectator) null else runCatching { room.session.resolveKiller(player) }
            .onFailure { plugin.logger.warning("Failed to resolve killer for ${player.uniqueId} in room ${room.id}: ${it.message}") }
            .getOrNull()
        removePlayerMembership(player, room, wasSpectator, reason, lastDamager)
        return true
    }

    /** 在玩法声明重连宽限时保留玩家席位、快照和队伍，返回是否接管退出。 */
    fun disconnectCurrentRoom(player: Player): Boolean {
        val playerSession = playerSessions[player.uniqueId] ?: return false
        if (playerSession.spectator) return false
        val room = rooms[playerSession.roomId] ?: return false
        val graceTicks = runCatching { room.session.reconnectGraceTicks(player).coerceAtLeast(0L) }
            .onFailure {
                plugin.logger.warning("Failed to read reconnect grace for ${player.uniqueId} in room ${room.id}: ${it.message}")
            }
            .getOrDefault(0L)
        if (graceTicks <= 0L) return false

        reconnectExpiryTasks.remove(player.uniqueId)?.cancel()
        val disconnectFailure = runCatching {
            displayService.detach(player, room)
            nametagService.clearTarget(player.uniqueId)
            nametagService.clearViewer(player)
            room.session.onPlayerDisconnect(player)
            reconnectExpiryTasks[player.uniqueId] = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                expireReconnect(player.uniqueId, room.id)
            }, graceTicks)
        }.exceptionOrNull()
        if (disconnectFailure != null) {
            plugin.logger.warning("Failed to preserve reconnect seat for ${player.uniqueId} in room ${room.id}: ${disconnectFailure.message}")
            reconnectExpiryTasks.remove(player.uniqueId)?.cancel()
            return false
        }
        runRoomCleanup(room, "publish disconnected player ${player.uniqueId}") { publishRoom(room) }
        return true
    }

    /** 在玩家上线时恢复仍处于宽限期的房间席位。 */
    fun reconnectPlayer(player: Player): Boolean {
        val task = reconnectExpiryTasks[player.uniqueId] ?: return false
        val playerSession = playerSessions[player.uniqueId] ?: return false
        val room = rooms[playerSession.roomId] ?: return false
        if (!room.players.contains(player.uniqueId) || playerSession.spectator) return false
        val reconnectEvent = GamePlayerRoomReconnectEvent(room, player)
        val eventFailure = runCatching {
            reconnectEvent.respawnDelayTicks = room.session.reconnectRespawnDelayTicks(player)
            Bukkit.getPluginManager().callEvent(reconnectEvent)
        }.exceptionOrNull()
        if (eventFailure != null) {
            plugin.logger.warning("Failed to prepare reconnect for ${player.uniqueId} in room ${room.id}: ${eventFailure.message}")
            reconnectExpiryTasks.remove(player.uniqueId)?.cancel()
            publishAdmissionDenied(room, player, GameRoomAdmissionType.RECONNECT, GameRoomAdmissionDeniedReason.CALLBACK_FAILED)
            leaveCurrentRoom(player, GameRoomLeaveReason.RECONNECT_REJECTED)
            return true
        }
        if (reconnectEvent.isCancelled) {
            publishAdmissionDenied(
                room,
                player,
                GameRoomAdmissionType.RECONNECT,
                GameRoomAdmissionDeniedReason.EVENT_CANCELLED
            )
            leaveCurrentRoom(player, GameRoomLeaveReason.RECONNECT_REJECTED)
            return true
        }

        val reconnectFailure = runCatching {
            reconnectEvent.respawnDelayTicks?.let { delay ->
                room.session.applyReconnectRespawnDelayTicks(player, delay.coerceAtLeast(0L))
            }
            displayService.attach(player, room)
            room.session.onPlayerReconnect(player)
            check(rooms[room.id] === room && room.players.contains(player.uniqueId))
        }.exceptionOrNull()
        reconnectExpiryTasks.remove(player.uniqueId)
        task.cancel()
        if (reconnectFailure != null) {
            plugin.logger.warning("Failed to reconnect player ${player.uniqueId} to room ${room.id}: ${reconnectFailure.message}")
            publishAdmissionDenied(room, player, GameRoomAdmissionType.RECONNECT, GameRoomAdmissionDeniedReason.CALLBACK_FAILED)
            if (rooms[room.id] === room) leaveCurrentRoom(player, GameRoomLeaveReason.RECONNECT_REJECTED)
            return true
        }
        runRoomCleanup(room, "refresh reconnected viewer ${player.uniqueId}") { nametagService.refreshViewer(room, player) }
        runRoomCleanup(room, "refresh reconnected room nametags") { nametagService.refreshRoom(room) }
        runRoomCleanup(room, "publish reconnected player ${player.uniqueId}") { publishRoom(room) }
        return true
    }

    /** 恢复因房间关闭或重连超时而等待玩家上线的大厅快照。 */
    fun restorePendingSnapshot(player: Player): Boolean {
        val pendingInMemory = pendingSnapshotRestores.remove(player.uniqueId)
        if (!pendingInMemory && !snapshotService.hasSnapshot(player.uniqueId)) return false
        return runCatching { snapshotService.restore(player) }
            .onFailure {
                pendingSnapshotRestores.add(player.uniqueId)
                plugin.logger.warning("Failed to restore pending snapshot for ${player.uniqueId}: ${it.message}")
            }
            .getOrDefault(false)
    }

    /** 判断玩家是否仍有必须先恢复的内存或磁盘快照。 */
    fun hasPendingSnapshot(playerId: UUID): Boolean = snapshotService.hasSnapshot(playerId)

    fun kickPlayer(roomId: String, targetId: UUID): Boolean {
        val player = Bukkit.getPlayer(targetId) ?: return false
        val room = rooms[roomId] ?: return false
        if (!room.players.contains(targetId) && !room.spectators.contains(targetId)) return false
        return leaveCurrentRoom(player, GameRoomLeaveReason.KICK)
    }

    fun transferOwner(roomId: String, targetId: UUID): Boolean {
        val room = rooms[roomId] ?: return false
        if (!room.players.contains(targetId)) return false
        room.owner = targetId
        runRoomCleanup(room, "update room after owner transfer to $targetId") { displayService.update(room) }
        runRoomCleanup(room, "publish room after owner transfer to $targetId") { publishRoom(room) }
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

        val startFailure = runCatching { room.session.onStart() }.exceptionOrNull()
        if (startFailure != null) {
            failActiveRoom(room, "start game session", startFailure)
            return false
        }
        if (rooms[room.id] !== room) return false
        if (room.state != GameState.WAITING && room.state != GameState.COUNTDOWN && room.state != GameState.RUNNING) {
            runRoomCleanup(room, "publish rejected room start") { publishRoom(room) }
            return false
        }
        val commitFailure = runCatching {
            if (room.state == GameState.WAITING) room.state = GameState.RUNNING
            check(rooms[room.id] === room)
            check(room.state == GameState.COUNTDOWN || room.state == GameState.RUNNING)
            displayService.markStarted(room)
            check(rooms[room.id] === room)
            publishRoom(room)
        }.exceptionOrNull()
        if (commitFailure != null) {
            failActiveRoom(room, "commit room start", commitFailure)
            return false
        }
        return rooms[room.id] === room &&
            (room.state == GameState.COUNTDOWN || room.state == GameState.RUNNING)
    }

    fun closeRoom(roomId: String): Boolean {
        val room = rooms.remove(roomId) ?: return false
        val closingPlayers = room.players.toList()
        val closingSpectators = room.spectators.toList()
        runRoomCleanup(room, "remove Velocity room") { velocityBridgeService.removeRoom(room) }
        runRoomCleanup(room, "mark room ending") { room.state = GameState.ENDING }
        runRoomCleanup(room, "end game session") { room.session.onEnd() }
        closingPlayers.forEach { playerId ->
            reconnectExpiryTasks.remove(playerId)?.let { task ->
                runRoomCleanup(room, "cancel reconnect task for $playerId") { task.cancel() }
            }
            val player = Bukkit.getPlayer(playerId)
            if (player != null) {
                runRoomCleanup(room, "detach player $playerId") { displayService.detach(player, room) }
                runRoomCleanup(room, "restore player $playerId") { snapshotService.restore(player) }
            } else {
                pendingSnapshotRestores.add(playerId)
            }
            playerSessions.remove(playerId)
        }
        closingSpectators.forEach { playerId ->
            Bukkit.getPlayer(playerId)?.let { spectator ->
                runRoomCleanup(room, "detach spectator $playerId") { displayService.detach(spectator, room) }
                runRoomCleanup(room, "notify spectator leave $playerId") { room.session.onSpectatorLeave(spectator) }
                runRoomCleanup(room, "exit spectator $playerId") { spectatorService.exit(spectator) }
                runRoomCleanup(room, "restore spectator $playerId") { snapshotService.restore(spectator) }
            }
            playerSessions.remove(playerId)
        }
        runRoomCleanup(room, "mark room closed") { displayService.markClosed(room) }
        runRoomCleanup(room, "close game session") { room.session.onClose() }
        runRoomCleanup(room, "close resource scope") { roomResourceScopeService.closeRoom(room.id) }
        runRoomCleanup(room, "clear eliminations") { eliminationService.clearRoom(room.id) }
        runRoomCleanup(room, "clear spectators") { spectatorService.clearRoom(room.id) }
        runRoomCleanup(room, "clear teams") { teamService.clearRoom(room.id) }
        runRoomCleanup(room, "clear nametags") { nametagService.clearRoom(room.id) }
        room.players.clear()
        room.spectators.clear()
        closingPlayers.forEach { playerId ->
            runRoomCleanup(room, "publish player leave $playerId") {
                publishPlayerLeave(room, playerId, Bukkit.getPlayer(playerId), spectator = false, GameRoomLeaveReason.ROOM_CLOSED)
            }
        }
        closingSpectators.forEach { playerId ->
            runRoomCleanup(room, "publish spectator leave $playerId") {
                publishPlayerLeave(room, playerId, Bukkit.getPlayer(playerId), spectator = true, GameRoomLeaveReason.ROOM_CLOSED)
            }
        }
        val worldName = room.world?.name
        val worldCleanupSucceeded = worldName?.let { name ->
            runCatching { worldService.unloadAndDelete(name) }
                .onFailure { plugin.logger.warning("Failed to unload world $name for room ${room.id}: ${it.message}") }
                .getOrDefault(false)
        } ?: true
        runRoomCleanup(room, "set final room state") { room.state = GameState.CLOSED }
        runRoomCleanup(room, "publish room closed") {
            Bukkit.getPluginManager().callEvent(GameRoomClosedEvent(room, worldName, worldCleanupSucceeded))
        }
        return true
    }

    private fun expireReconnect(playerId: UUID, roomId: String) {
        reconnectExpiryTasks.remove(playerId)
        if (Bukkit.getPlayer(playerId)?.isOnline == true) return
        val playerSession = playerSessions[playerId] ?: return
        if (playerSession.roomId != roomId || playerSession.spectator) return
        val room = rooms[roomId] ?: return

        runRoomCleanup(room, "notify reconnect expiry for $playerId") {
            room.session.onPlayerReconnectExpired(playerId)
        }
        if (rooms[room.id] !== room) return
        runRoomCleanup(room, "stop following expired player $playerId") {
            spectatorService.stopFollowingTarget(playerId)
        }
        runRoomCleanup(room, "clear expired player elimination $playerId") {
            eliminationService.clear(room.id, playerId)
        }
        room.players.remove(playerId)
        runRoomCleanup(room, "leave team for expired player $playerId") {
            teamService.leave(room.id, playerId)
        }
        playerSessions.remove(playerId)
        pendingSnapshotRestores.add(playerId)
        runRoomCleanup(room, "publish reconnect expiry for $playerId") {
            publishPlayerLeave(room, playerId, null, spectator = false, GameRoomLeaveReason.RECONNECT_EXPIRED)
        }
        if (rooms[room.id] !== room) return
        if (room.owner == playerId) room.owner = room.players.firstOrNull()
        if (room.players.isEmpty() && room.spectators.isEmpty()) {
            closeRoom(room.id)
        } else {
            runRoomCleanup(room, "update room after reconnect expiry for $playerId") {
                displayService.update(room)
            }
            runRoomCleanup(room, "publish room after reconnect expiry for $playerId") {
                publishRoom(room)
            }
        }
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

    /** 统一提交玩家死亡统计与玩法回调，异常时在事件结束后关闭故障房间。 */
    fun handlePlayerDeath(victim: Player): Boolean {
        val room = getPlayerRoom(victim) ?: return false
        runRoomCleanup(room, "stop spectators following dead player ${victim.uniqueId}") {
            spectatorService.stopFollowingTarget(victim.uniqueId)
        }
        val killerResult = runCatching { room.session.resolveKiller(victim) }
        val killerFailure = killerResult.exceptionOrNull()
        if (killerFailure != null) {
            failActiveRoomNextTick(room, "resolve killer for ${victim.uniqueId}", killerFailure)
            return true
        }
        if (rooms[room.id] !== room) return true
        val killer = killerResult.getOrNull()?.takeIf {
            it.uniqueId != victim.uniqueId && room.players.contains(it.uniqueId)
        }
        val statsFailure = runCatching {
            if (killer != null) {
                recordKill(killer.uniqueId, victim.uniqueId, room.module.id, points = 1)
            } else {
                recordDeath(victim.uniqueId, room.module.id)
            }
        }.exceptionOrNull()
        if (statsFailure != null) {
            failActiveRoomNextTick(room, "record death for ${victim.uniqueId}", statsFailure)
            return true
        }
        if (rooms[room.id] !== room) return true
        if (killer != null) {
            val killFailure = runCatching { room.session.onPlayerKill(killer, victim) }.exceptionOrNull()
            if (killFailure != null) {
                failActiveRoomNextTick(room, "notify kill for ${victim.uniqueId}", killFailure)
                return true
            }
        }
        if (rooms[room.id] !== room) return true
        val deathFailure = runCatching { room.session.onPlayerDeath(victim) }.exceptionOrNull()
        if (deathFailure != null) failActiveRoomNextTick(room, "notify death for ${victim.uniqueId}", deathFailure)
        return true
    }

    /** 安全调用玩法观战目标过滤器；异常目标不会进入观战候选列表。 */
    fun canSpectatorFollow(room: GameRoom, spectator: Player, target: Player): Boolean {
        if (rooms[room.id] !== room) return false
        return runCatching { room.session.canSpectatorFollow(spectator, target) }
            .onFailure {
                plugin.logger.warning("Failed to check spectator target ${target.uniqueId} in room ${room.id}: ${it.message}")
            }
            .getOrDefault(false)
    }

    fun statsSnapshot(): Collection<org.katacr.kaGameCenter.data.PlayerGameStats> = statsService.all()

    /** 返回指定玩家按玩法 ID 排序的基础统计快照。 */
    fun statsSnapshot(playerId: UUID): List<org.katacr.kaGameCenter.data.PlayerGameStats> {
        return statsService.all().filter { it.playerId == playerId }.sortedBy { it.gameId }
    }

    /** 返回指定玩家按玩法及指标 ID 排序的扩展统计快照。 */
    fun metricSnapshot(playerId: UUID): List<org.katacr.kaGameCenter.data.PlayerGameMetric> {
        return statsService.allMetrics()
            .filter { it.playerId == playerId }
            .sortedWith(compareBy({ it.gameId }, { it.metricId }))
    }

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

    /** 逐项撤销玩家房间成员关系，可用于正常离房、身份切换和准入失败回滚。 */
    private fun removePlayerMembership(
        player: Player,
        room: GameRoom,
        wasSpectator: Boolean,
        reason: GameRoomLeaveReason,
        lastDamager: Player? = null,
        notifySession: Boolean = true,
        publishLeave: Boolean = true,
        closeWhenEmpty: Boolean = true
    ) {
        reconnectExpiryTasks.remove(player.uniqueId)?.let { task ->
            runRoomCleanup(room, "cancel reconnect task for ${player.uniqueId}") { task.cancel() }
        }
        if (playerSessions[player.uniqueId]?.roomId == room.id) playerSessions.remove(player.uniqueId)
        room.players.remove(player.uniqueId)
        room.spectators.remove(player.uniqueId)
        runRoomCleanup(room, "stop spectator target for ${player.uniqueId}") {
            spectatorService.stopFollowingTarget(player.uniqueId)
        }
        runRoomCleanup(room, "clear nametag target for ${player.uniqueId}") {
            nametagService.clearTarget(player.uniqueId)
        }
        runRoomCleanup(room, "clear nametag viewer ${player.uniqueId}") { nametagService.clearViewer(player) }
        runRoomCleanup(room, "leave team for ${player.uniqueId}") { teamService.leave(room.id, player.uniqueId) }
        if (notifySession) {
            if (wasSpectator) {
                runRoomCleanup(room, "notify spectator leave ${player.uniqueId}") { room.session.onSpectatorLeave(player) }
            } else {
                runRoomCleanup(room, "notify player leave ${player.uniqueId}") { room.session.onPlayerLeave(player) }
            }
        }
        runRoomCleanup(room, "exit spectator state for ${player.uniqueId}") {
            if (spectatorService.isSpectator(player)) spectatorService.exit(player)
        }
        runRoomCleanup(room, "clear elimination for ${player.uniqueId}") {
            eliminationService.clear(room.id, player.uniqueId)
        }
        runRoomCleanup(room, "detach display for ${player.uniqueId}") { displayService.detach(player, room) }
        runCatching { snapshotService.restore(player) }
            .onSuccess { restored -> if (restored) pendingSnapshotRestores.remove(player.uniqueId) }
            .onFailure {
                pendingSnapshotRestores.add(player.uniqueId)
                plugin.logger.warning("Failed to restore player ${player.uniqueId} from room ${room.id}: ${it.message}")
            }
        if (publishLeave) {
            runRoomCleanup(room, "publish player leave ${player.uniqueId}") {
                publishPlayerLeave(room, player.uniqueId, player, wasSpectator, reason, lastDamager)
            }
        }
        if (rooms[room.id] !== room) return
        if (closeWhenEmpty && room.players.isEmpty() && room.spectators.isEmpty()) {
            closeRoom(room.id)
            return
        }
        if (room.owner == player.uniqueId) room.owner = room.players.firstOrNull()
        runRoomCleanup(room, "update room after player leave ${player.uniqueId}") { displayService.update(room) }
        runRoomCleanup(room, "publish room after player leave ${player.uniqueId}") { publishRoom(room) }
    }

    /** 隔离单个关房清理步骤的异常，确保后续玩家、资源和世界仍会释放。 */
    private fun runRoomCleanup(room: GameRoom, step: String, action: () -> Unit) {
        runCatching(action)
            .onFailure { plugin.logger.warning("Failed to $step for room ${room.id}: ${it.message}") }
    }

    /** 隔离单个房间的周期回调与显示刷新，失败时只关闭故障房间。 */
    private fun runRoomTick(room: GameRoom) {
        if (rooms[room.id] !== room || room.state == GameState.CLOSED) return
        val tickFailure = runCatching { room.session.onTick() }.exceptionOrNull()
        if (tickFailure != null) {
            failActiveRoom(room, "tick game session", tickFailure)
            return
        }
        if (rooms[room.id] !== room || room.state == GameState.CLOSED) return
        val displayFailure = runCatching { displayService.update(room) }.exceptionOrNull()
        if (displayFailure != null) failActiveRoom(room, "refresh room display", displayFailure)
    }

    /** 记录活动房间的致命回调异常，并通过统一关房事务恢复成员和资源。 */
    private fun failActiveRoom(room: GameRoom, step: String, error: Throwable) {
        plugin.logger.warning("Failed to $step for room ${room.id}; closing room: ${error.message}")
        if (rooms[room.id] === room) closeRoom(room.id)
    }

    /** 将事件回调中的致命房间故障延后关闭，避免在死亡等 Bukkit 提交流程内恢复玩家。 */
    private fun failActiveRoomNextTick(room: GameRoom, step: String, error: Throwable) {
        plugin.logger.warning("Failed to $step for room ${room.id}; scheduling room close: ${error.message}")
        runCatching {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (rooms[room.id] === room) closeRoom(room.id)
            })
        }.onFailure { failActiveRoom(room, "schedule failure cleanup after $step", it) }
    }

    /** 在新准入前恢复遗留快照，恢复失败时保留证据并拒绝覆盖原状态。 */
    private fun restoreSnapshotBeforeAdmission(
        player: Player,
        room: GameRoom,
        type: GameRoomAdmissionType
    ): Boolean {
        if (!snapshotService.hasSnapshot(player.uniqueId)) return true
        val restored = restorePendingSnapshot(player)
        if (restored && !snapshotService.hasSnapshot(player.uniqueId)) return true
        plugin.logger.warning("Refusing $type admission for ${player.uniqueId}: pending snapshot could not be restored")
        runRoomCleanup(room, "publish pending snapshot rejection for ${player.uniqueId}") {
            publishAdmissionDenied(room, player, type, GameRoomAdmissionDeniedReason.CALLBACK_FAILED)
        }
        return false
    }

    /** 发布目标房间已存在但准入未提交的稳定拒绝上下文。 */
    private fun publishAdmissionDenied(
        room: GameRoom,
        player: Player,
        type: GameRoomAdmissionType,
        reason: GameRoomAdmissionDeniedReason
    ) {
        runCatching {
            Bukkit.getPluginManager().callEvent(GamePlayerRoomAdmissionDeniedEvent(room, player, type, reason))
        }.onFailure {
            plugin.logger.warning("Failed to publish $type admission denial for ${player.uniqueId} in room ${room.id}: ${it.message}")
        }
    }

    /** 发布已结束的玩家房间关系，并保留离线 UUID 与可选最近伤害者。 */
    private fun publishPlayerLeave(
        room: GameRoom,
        playerId: UUID,
        player: Player?,
        spectator: Boolean,
        reason: GameRoomLeaveReason,
        lastDamager: Player? = null
    ) {
        Bukkit.getPluginManager().callEvent(
            GamePlayerRoomLeaveEvent(
                room,
                playerId,
                player,
                spectator,
                reason,
                lastDamager?.uniqueId,
                lastDamager
            )
        )
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
