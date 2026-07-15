package org.katacr.kaGameCenter.velocity

import com.google.gson.Gson
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.game.GameRoom
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class RedisVelocityBridgeService(
    private val plugin: JavaPlugin,
    private val config: VelocityBridgeConfig
) : VelocityBridgeService {
    private val gson = Gson()
    private val keyspace = VelocityRedisKeyspace(config.redis.keyPrefix)
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "KaGameCenter-VelocityBridge").apply { isDaemon = true }
    }
    private val snapshots = ConcurrentHashMap<String, VelocityRoomSnapshot>()
    private var client: RedisClient? = null
    private var connection: StatefulRedisConnection<String, String>? = null
    @Volatile
    private var reservationHandler: ((VelocityReserveRoomRequest) -> VelocityReserveRoomResponse)? = null

    override val enabled: Boolean
        get() = connection?.isOpen == true
    override val backendName: String
        get() = if (enabled) "redis:${config.serverId}" else "disabled"
    override val serverId: String
        get() = config.serverId
    override val heartbeatIntervalTicks: Long
        get() = config.heartbeatIntervalSeconds * 20L

    override fun init() {
        if (!config.enabled || !config.redis.enabled) return
        runCatching {
            val builder = RedisURI.builder()
                .withHost(config.redis.host)
                .withPort(config.redis.port)
                .withDatabase(config.redis.database)
                .withTimeout(Duration.ofSeconds(3))
            if (config.redis.password.isNotBlank()) {
                builder.withPassword(config.redis.password.toCharArray())
            }
            if (config.redis.ssl) {
                builder.withSsl(true)
            }
            client = RedisClient.create(builder.build())
            connection = client!!.connect()
            executor.scheduleWithFixedDelay(
                { refreshSnapshotsSafely() },
                0L,
                config.roomListPollIntervalSeconds,
                TimeUnit.SECONDS
            )
            executor.scheduleWithFixedDelay(
                { pollReservationRequestsSafely() },
                config.reservationPollIntervalMillis,
                config.reservationPollIntervalMillis,
                TimeUnit.MILLISECONDS
            )
        }.onFailure {
            plugin.logger.warning("Velocity bridge Redis connection failed: ${it.message}")
            shutdown()
        }
    }

    override fun publishRoom(room: GameRoom) {
        val snapshot = room.toVelocitySnapshot(config.serverId)
        snapshots[snapshot.cacheKey()] = snapshot
        executor.execute {
            runCatching { writeSnapshot(snapshot) }
                .onFailure { plugin.logger.warning("Velocity bridge publish failed: ${it.message}") }
        }
    }

    override fun removeRoom(room: GameRoom) {
        val roomId = room.id
        snapshots.remove("${config.serverId}:$roomId")
        executor.execute {
            runCatching {
                val sync = connection?.sync() ?: return@runCatching
                val roomKey = keyspace.room(config.serverId, roomId)
                sync.del(roomKey)
                sync.srem(keyspace.roomsIndex, roomKey)
                sync.publish(keyspace.events, """{"type":"room-remove","serverId":"${config.serverId}","roomId":"$roomId","updatedAt":${System.currentTimeMillis()}}""")
            }.onFailure {
                plugin.logger.warning("Velocity bridge room remove failed: ${it.message}")
            }
        }
    }

    override fun removeAllRooms() {
        val roomIds = snapshots.values
            .filter { it.serverId == config.serverId }
            .map { it.roomId }
            .toSet()
        snapshots.entries.removeIf { it.value.serverId == config.serverId }
        val sync = connection?.sync()
        if (sync != null) {
            runCatching {
                roomIds.forEach { roomId ->
                    val roomKey = keyspace.room(config.serverId, roomId)
                    sync.del(roomKey)
                    sync.srem(keyspace.roomsIndex, roomKey)
                }
                sync.del(keyspace.server(config.serverId))
                sync.srem(keyspace.servers, config.serverId)
                sync.publish(keyspace.events, """{"type":"server-remove","serverId":"${config.serverId}","updatedAt":${System.currentTimeMillis()}}""")
            }.onFailure {
                plugin.logger.warning("Velocity bridge server room cleanup failed: ${it.message}")
            }
        }
    }

    override fun shutdown() {
        removeAllRooms()
        runCatching { executor.shutdownNow() }
        runCatching { connection?.close() }
        runCatching { client?.shutdown() }
        connection = null
        client = null
    }

    override fun globalRooms(): Collection<VelocityRoomSnapshot> {
        val now = System.currentTimeMillis()
        val expireMillis = config.snapshotTtlSeconds * 1000L
        snapshots.entries.removeIf { now - it.value.updatedAt > expireMillis }
        return snapshots.values.sortedWith(compareBy<VelocityRoomSnapshot> { it.gameId }.thenBy { it.serverId }.thenBy { it.roomId })
    }

    override fun startReservationHandling(handler: (VelocityReserveRoomRequest) -> VelocityReserveRoomResponse) {
        reservationHandler = handler
    }

    override fun consumeJoinIntent(player: Player, handler: (String) -> Boolean) {
        executor.execute {
            runCatching {
                val sync = connection?.sync() ?: return@runCatching
                val playerKey = keyspace.playerIntent(player.uniqueId.toString())
                val json = sync.get(playerKey) ?: return@runCatching
                val intent = gson.fromJson(json, VelocityJoinIntent::class.java) ?: return@runCatching
                if (intent.targetServerId != config.serverId || intent.playerId != player.uniqueId || intent.expiresAt < System.currentTimeMillis()) {
                    sync.del(playerKey)
                    sync.del(keyspace.joinIntent(intent.intentId))
                    return@runCatching
                }
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val accepted = handler(intent.targetRoomId)
                    if (accepted) {
                        executor.execute {
                            runCatching {
                                sync.del(playerKey)
                                sync.del(keyspace.joinIntent(intent.intentId))
                            }
                        }
                    }
                })
            }.onFailure {
                plugin.logger.warning("Velocity bridge join intent consume failed: ${it.message}")
            }
        }
    }

    override fun requestRemoteJoin(player: Player, targetServerId: String, targetRoomId: String): Boolean {
        if (!enabled) return false
        val request = VelocityProxyJoinRequest(
            requestId = UUID.randomUUID().toString(),
            playerId = player.uniqueId,
            playerName = player.name,
            sourceServerId = config.serverId,
            targetServerId = targetServerId,
            targetRoomId = targetRoomId
        )
        executor.execute {
            runCatching {
                val sync = connection?.sync() ?: return@runCatching
                sync.setex(keyspace.proxyJoinRequest(request.requestId), config.joinIntentTtlSeconds, gson.toJson(request))
                sync.lpush(keyspace.proxyJoinRequestQueue, request.requestId)
            }.onFailure {
                plugin.logger.warning("Velocity bridge remote join request failed: ${it.message}")
            }
        }
        return true
    }

    private fun writeSnapshot(snapshot: VelocityRoomSnapshot) {
        val sync = connection?.sync() ?: return
        val roomKey = keyspace.room(snapshot.serverId, snapshot.roomId)
        val ttlSeconds = config.snapshotTtlSeconds
        sync.setex(roomKey, ttlSeconds, gson.toJson(snapshot))
        sync.sadd(keyspace.roomsIndex, roomKey)
        sync.setex(keyspace.server(snapshot.serverId), ttlSeconds, gson.toJson(mapOf(
            "serverId" to snapshot.serverId,
            "updatedAt" to System.currentTimeMillis(),
            "rooms" to 1
        )))
        sync.sadd(keyspace.servers, snapshot.serverId)
        sync.publish(keyspace.events, gson.toJson(mapOf(
            "type" to "room-update",
            "serverId" to snapshot.serverId,
            "roomId" to snapshot.roomId,
            "updatedAt" to snapshot.updatedAt
        )))
    }

    private fun refreshSnapshotsSafely() {
        runCatching { refreshSnapshots() }
            .onFailure { plugin.logger.warning("Velocity bridge room list refresh failed: ${it.message}") }
    }

    private fun pollReservationRequestsSafely() {
        runCatching { pollReservationRequests() }
            .onFailure { plugin.logger.warning("Velocity bridge reservation poll failed: ${it.message}") }
    }

    private fun pollReservationRequests() {
        val handler = reservationHandler ?: return
        val sync = connection?.sync() ?: return
        repeat(32) {
            val requestId = sync.rpop(keyspace.reserveRequestQueue(config.serverId)) ?: return
            handleReservationRequestId(handler, requestId)
        }
    }

    private fun handleReservationRequestId(
        handler: (VelocityReserveRoomRequest) -> VelocityReserveRoomResponse,
        requestId: String
    ) {
        val sync = connection?.sync() ?: return
        val key = keyspace.reserveRequest(requestId)
        val json = sync.get(key)
        if (json.isNullOrBlank()) return
        val request = runCatching { gson.fromJson(json, VelocityReserveRoomRequest::class.java) }.getOrNull() ?: return
        if (request.targetServerId != config.serverId) return
        if ((sync.del(key) ?: 0L) <= 0L) return
        val response = runReservationOnMainThread(handler, request).withDefaultExpiresAt()
        if (response.accepted) {
            writeJoinIntent(request, response.expiresAt ?: (System.currentTimeMillis() + config.joinIntentTtlSeconds * 1000L))
        }
        sync.setex(keyspace.reserveResponse(request.requestId), config.joinIntentTtlSeconds, gson.toJson(response))
    }

    private fun VelocityReserveRoomResponse.withDefaultExpiresAt(): VelocityReserveRoomResponse {
        if (!accepted || expiresAt != null) return this
        return copy(expiresAt = System.currentTimeMillis() + config.joinIntentTtlSeconds * 1000L)
    }

    private fun runReservationOnMainThread(
        handler: (VelocityReserveRoomRequest) -> VelocityReserveRoomResponse,
        request: VelocityReserveRoomRequest
    ): VelocityReserveRoomResponse {
        val future = CompletableFuture<VelocityReserveRoomResponse>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            runCatching { handler(request) }
                .onSuccess { future.complete(it) }
                .onFailure {
                    future.complete(
                        VelocityReserveRoomResponse(
                            requestId = request.requestId,
                            accepted = false,
                            targetServerId = request.targetServerId,
                            targetRoomId = request.targetRoomId,
                            reason = it.message ?: "RESERVATION_FAILED"
                        )
                    )
                }
        })
        return future.get(2, TimeUnit.SECONDS)
    }

    private fun writeJoinIntent(request: VelocityReserveRoomRequest, expiresAt: Long) {
        val sync = connection?.sync() ?: return
        val intent = VelocityJoinIntent(
            intentId = UUID.randomUUID().toString(),
            playerId = request.playerId,
            playerName = request.playerName,
            targetServerId = request.targetServerId,
            targetRoomId = request.targetRoomId,
            expiresAt = expiresAt
        )
        val ttlSeconds = ((expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(5L)
        val json = gson.toJson(intent)
        sync.setex(keyspace.joinIntent(intent.intentId), ttlSeconds, json)
        sync.setex(keyspace.playerIntent(intent.playerId.toString()), ttlSeconds, json)
    }

    private fun refreshSnapshots() {
        val sync = connection?.sync() ?: return
        val values = sync.smembers(keyspace.roomsIndex).orEmpty()
        val loaded = linkedMapOf<String, VelocityRoomSnapshot>()
        values.forEach { value ->
            val roomKey = if (value.startsWith("${config.redis.keyPrefix}:room:")) {
                value
            } else {
                val parts = value.split(":", limit = 2)
                if (parts.size != 2) return@forEach
                keyspace.room(parts[0], parts[1])
            }
            val json = sync.get(roomKey) ?: return@forEach
            val snapshot = runCatching { gson.fromJson(json, VelocityRoomSnapshot::class.java) }.getOrNull() ?: return@forEach
            loaded[snapshot.cacheKey()] = snapshot
        }
        snapshots.keys
            .filter { key -> loaded.containsKey(key).not() && !key.startsWith("${config.serverId}:") }
            .forEach { snapshots.remove(it) }
        loaded.values.forEach { snapshots[it.cacheKey()] = it }
    }

    private fun GameRoom.toVelocitySnapshot(serverId: String): VelocityRoomSnapshot {
        val definition = definition
        val maxPlayers = definition?.maxPlayers ?: module.maxPlayers
        val gameId = definition?.id ?: configuredGame?.globalId ?: module.id
        val tags = buildList {
            add("module:${module.id}")
            add("group:${configuredGame?.selectorGroup ?: "default"}")
            configuredGame?.let { add("managed:${it.globalId}") }
        }
        return VelocityRoomSnapshot(
            serverId = serverId,
            roomId = id,
            roomName = name,
            gameId = gameId,
            gameName = module.displayName,
            mapId = configuredGame?.displayName ?: mapTemplate?.substringAfterLast('/') ?: "-",
            state = state.name,
            players = players.size,
            maxPlayers = maxPlayers,
            spectators = spectators.size,
            joinable = canJoin() && players.size < maxPlayers,
            tags = tags
        )
    }

    private fun VelocityRoomSnapshot.cacheKey(): String = "$serverId:$roomId"
}
