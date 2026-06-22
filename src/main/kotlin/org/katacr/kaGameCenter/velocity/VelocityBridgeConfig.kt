package org.katacr.kaGameCenter.velocity

import org.bukkit.configuration.file.FileConfiguration

data class VelocityBridgeConfig(
    val enabled: Boolean = false,
    val serverId: String = "game-1",
    val snapshotTtlSeconds: Long = 15L,
    val heartbeatIntervalSeconds: Long = 5L,
    val roomListPollIntervalSeconds: Long = 1L,
    val reservationPollIntervalMillis: Long = 250L,
    val joinIntentTtlSeconds: Long = 15L,
    val redis: VelocityRedisConfig = VelocityRedisConfig()
) {
    companion object {
        fun from(config: FileConfiguration): VelocityBridgeConfig {
            val section = "velocity"
            return VelocityBridgeConfig(
                enabled = config.getBoolean("$section.enabled", false),
                serverId = config.getString("$section.server-id")?.trim()?.takeIf { it.isNotBlank() } ?: "game-1",
                snapshotTtlSeconds = config.getLong("$section.snapshot-ttl-seconds", 15L).coerceAtLeast(5L),
                heartbeatIntervalSeconds = config.getLong("$section.heartbeat-interval-seconds", 5L).coerceAtLeast(1L),
                roomListPollIntervalSeconds = config.getLong("$section.room-list-poll-interval-seconds", 1L).coerceAtLeast(1L),
                reservationPollIntervalMillis = config.getLong("$section.reservation-poll-interval-ms", 250L).coerceAtLeast(100L),
                joinIntentTtlSeconds = config.getLong("$section.join-intent-ttl-seconds", 15L).coerceAtLeast(5L),
                redis = VelocityRedisConfig(
                    enabled = config.getBoolean("$section.redis.enabled", true),
                    host = config.getString("$section.redis.host") ?: "127.0.0.1",
                    port = config.getInt("$section.redis.port", 6379),
                    password = config.getString("$section.redis.password") ?: "",
                    database = config.getInt("$section.redis.database", 0),
                    ssl = config.getBoolean("$section.redis.ssl", false),
                    keyPrefix = config.getString("$section.redis.key-prefix")?.trim()?.takeIf { it.isNotBlank() } ?: "kgv"
                )
            )
        }
    }
}

data class VelocityRedisConfig(
    val enabled: Boolean = true,
    val host: String = "127.0.0.1",
    val port: Int = 6379,
    val password: String = "",
    val database: Int = 0,
    val ssl: Boolean = false,
    val keyPrefix: String = "kgv"
)
