package org.katacr.kaGameCenter.friend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.data.DatabaseConfig
import org.katacr.kaGameCenter.data.DatabaseType
import java.sql.Connection
import java.util.UUID

/** 使用 KaGameCenter 现有 SQLite/MySQL 配置持久化好友关系与申请。 */
class SqlFriendRepository(
    private val plugin: JavaPlugin,
    private val config: DatabaseConfig
) : FriendRepository {
    private lateinit var dataSource: HikariDataSource

    override fun init() {
        if (config.type == DatabaseType.SQLITE) config.sqliteFile.parentFile?.mkdirs()
        dataSource = HikariDataSource(HikariConfig().apply {
            poolName = "KaGameCenter-Friends"
            maximumPoolSize = if (config.type == DatabaseType.SQLITE) 1 else config.poolMaximumSize
            minimumIdle = 1
            if (config.type == DatabaseType.SQLITE) {
                connectionInitSql = "PRAGMA busy_timeout=5000"
            }
            jdbcUrl = when (config.type) {
                DatabaseType.SQLITE -> "jdbc:sqlite:${config.sqliteFile.absolutePath}"
                DatabaseType.MYSQL -> "jdbc:mysql://${config.mysqlHost}:${config.mysqlPort}/${config.mysqlDatabase}?useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
            }
            if (config.type == DatabaseType.MYSQL) {
                username = config.mysqlUsername
                password = config.mysqlPassword
            }
        })
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                if (config.type == DatabaseType.SQLITE) {
                    statement.execute("PRAGMA journal_mode=WAL")
                }
                statement.execute(friendshipsTableSql())
                statement.execute(requestsTableSql())
            }
        }
    }

    override fun loadFriendships(): Set<Friendship> {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT first_player_id, second_player_id FROM kgc_friendships").use { statement ->
                statement.executeQuery().use { result ->
                    val friendships = linkedSetOf<Friendship>()
                    while (result.next()) {
                        runCatching {
                            Friendship.of(
                                UUID.fromString(result.getString("first_player_id")),
                                UUID.fromString(result.getString("second_player_id"))
                            )
                        }.onSuccess(friendships::add)
                            .onFailure { plugin.logger.warning("Ignored invalid friendship row: ${it.message}") }
                    }
                    return friendships
                }
            }
        }
    }

    override fun loadRequests(): Set<FriendRequest> {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT sender_id, receiver_id FROM kgc_friend_requests").use { statement ->
                statement.executeQuery().use { result ->
                    val requests = linkedSetOf<FriendRequest>()
                    while (result.next()) {
                        runCatching {
                            FriendRequest(
                                UUID.fromString(result.getString("sender_id")),
                                UUID.fromString(result.getString("receiver_id"))
                            )
                        }.onSuccess(requests::add)
                            .onFailure { plugin.logger.warning("Ignored invalid friend request row: ${it.message}") }
                    }
                    return requests
                }
            }
        }
    }

    override fun saveRequest(request: FriendRequest) {
        dataSource.connection.use { connection ->
            val sql = when (config.type) {
                DatabaseType.SQLITE -> "INSERT OR IGNORE INTO kgc_friend_requests (sender_id, receiver_id) VALUES (?, ?)"
                DatabaseType.MYSQL -> "INSERT IGNORE INTO kgc_friend_requests (sender_id, receiver_id) VALUES (?, ?)"
            }
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, request.senderId.toString())
                statement.setString(2, request.receiverId.toString())
                statement.executeUpdate()
            }
        }
    }

    override fun deleteRequest(request: FriendRequest) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM kgc_friend_requests WHERE sender_id = ? AND receiver_id = ?").use { statement ->
                statement.setString(1, request.senderId.toString())
                statement.setString(2, request.receiverId.toString())
                statement.executeUpdate()
            }
        }
    }

    override fun acceptRequest(request: FriendRequest, friendship: Friendship) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                deleteBothRequests(connection, request.senderId, request.receiverId)
                val sql = when (config.type) {
                    DatabaseType.SQLITE -> "INSERT OR IGNORE INTO kgc_friendships (first_player_id, second_player_id) VALUES (?, ?)"
                    DatabaseType.MYSQL -> "INSERT IGNORE INTO kgc_friendships (first_player_id, second_player_id) VALUES (?, ?)"
                }
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, friendship.firstPlayerId.toString())
                    statement.setString(2, friendship.secondPlayerId.toString())
                    statement.executeUpdate()
                }
                connection.commit()
            } catch (error: Throwable) {
                runCatching(connection::rollback)
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun deleteFriendship(friendship: Friendship) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM kgc_friendships WHERE first_player_id = ? AND second_player_id = ?"
            ).use { statement ->
                statement.setString(1, friendship.firstPlayerId.toString())
                statement.setString(2, friendship.secondPlayerId.toString())
                statement.executeUpdate()
            }
        }
    }

    override fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun deleteBothRequests(connection: Connection, first: UUID, second: UUID) {
        connection.prepareStatement(
            "DELETE FROM kgc_friend_requests WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)"
        ).use { statement ->
            statement.setString(1, first.toString())
            statement.setString(2, second.toString())
            statement.setString(3, second.toString())
            statement.setString(4, first.toString())
            statement.executeUpdate()
        }
    }

    private fun friendshipsTableSql(): String = when (config.type) {
        DatabaseType.SQLITE -> """
            CREATE TABLE IF NOT EXISTS kgc_friendships (
                first_player_id TEXT NOT NULL,
                second_player_id TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (first_player_id, second_player_id)
            )
        """.trimIndent()
        DatabaseType.MYSQL -> """
            CREATE TABLE IF NOT EXISTS kgc_friendships (
                first_player_id VARCHAR(36) NOT NULL,
                second_player_id VARCHAR(36) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (first_player_id, second_player_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """.trimIndent()
    }

    private fun requestsTableSql(): String = when (config.type) {
        DatabaseType.SQLITE -> """
            CREATE TABLE IF NOT EXISTS kgc_friend_requests (
                sender_id TEXT NOT NULL,
                receiver_id TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (sender_id, receiver_id)
            )
        """.trimIndent()
        DatabaseType.MYSQL -> """
            CREATE TABLE IF NOT EXISTS kgc_friend_requests (
                sender_id VARCHAR(36) NOT NULL,
                receiver_id VARCHAR(36) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (sender_id, receiver_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """.trimIndent()
    }
}
