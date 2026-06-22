package org.katacr.kaGameCenter.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.util.UUID

class SqlStatsRepository(
    private val plugin: JavaPlugin,
    private val config: DatabaseConfig
) : StatsRepository {
    private lateinit var dataSource: HikariDataSource

    override val backendName: String
        get() = config.type.name.lowercase()

    override fun init() {
        if (config.type == DatabaseType.SQLITE) {
            config.sqliteFile.parentFile?.mkdirs()
        }

        dataSource = HikariDataSource(HikariConfig().apply {
            poolName = "KaGameCenter-${config.type.name.lowercase()}"
            maximumPoolSize = config.poolMaximumSize
            jdbcUrl = jdbcUrl()
            if (config.type == DatabaseType.MYSQL) {
                username = config.mysqlUsername
                password = config.mysqlPassword
            }
        })

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(createTableSql())
            }
        }
        plugin.logger.info("KaGameCenter stats database initialized: ${config.type}")
    }

    override fun loadAll(): List<PlayerGameStats> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT player_id, game_id, plays, wins, losses, kills, deaths, points
                FROM kgc_player_stats
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { result ->
                    val output = mutableListOf<PlayerGameStats>()
                    while (result.next()) {
                        output.add(
                            PlayerGameStats(
                                playerId = UUID.fromString(result.getString("player_id")),
                                gameId = result.getString("game_id"),
                                plays = result.getInt("plays"),
                                wins = result.getInt("wins"),
                                losses = result.getInt("losses"),
                                kills = result.getInt("kills"),
                                deaths = result.getInt("deaths"),
                                points = result.getInt("points")
                            )
                        )
                    }
                    return output
                }
            }
        }
    }

    override fun save(stats: PlayerGameStats) {
        dataSource.connection.use { connection ->
            when (config.type) {
                DatabaseType.SQLITE -> saveSqlite(connection, stats)
                DatabaseType.MYSQL -> saveMysql(connection, stats)
            }
        }
    }

    override fun close() {
        if (::dataSource.isInitialized) {
            dataSource.close()
        }
    }

    private fun saveSqlite(connection: Connection, stats: PlayerGameStats) {
        connection.prepareStatement(
            """
            INSERT INTO kgc_player_stats
                (player_id, game_id, plays, wins, losses, kills, deaths, points, updated_at)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(player_id, game_id) DO UPDATE SET
                plays = excluded.plays,
                wins = excluded.wins,
                losses = excluded.losses,
                kills = excluded.kills,
                deaths = excluded.deaths,
                points = excluded.points,
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
        ).use { statement ->
            bindStats(statement, stats)
            statement.executeUpdate()
        }
    }

    private fun saveMysql(connection: Connection, stats: PlayerGameStats) {
        connection.prepareStatement(
            """
            INSERT INTO kgc_player_stats
                (player_id, game_id, plays, wins, losses, kills, deaths, points, updated_at)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                plays = VALUES(plays),
                wins = VALUES(wins),
                losses = VALUES(losses),
                kills = VALUES(kills),
                deaths = VALUES(deaths),
                points = VALUES(points),
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
        ).use { statement ->
            bindStats(statement, stats)
            statement.executeUpdate()
        }
    }

    private fun bindStats(statement: java.sql.PreparedStatement, stats: PlayerGameStats) {
        statement.setString(1, stats.playerId.toString())
        statement.setString(2, stats.gameId.lowercase())
        statement.setInt(3, stats.plays)
        statement.setInt(4, stats.wins)
        statement.setInt(5, stats.losses)
        statement.setInt(6, stats.kills)
        statement.setInt(7, stats.deaths)
        statement.setInt(8, stats.points)
    }

    private fun jdbcUrl(): String {
        return when (config.type) {
            DatabaseType.SQLITE -> "jdbc:sqlite:${config.sqliteFile.absolutePath}"
            DatabaseType.MYSQL -> "jdbc:mysql://${config.mysqlHost}:${config.mysqlPort}/${config.mysqlDatabase}?useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
        }
    }

    private fun createTableSql(): String {
        return when (config.type) {
            DatabaseType.SQLITE -> """
                CREATE TABLE IF NOT EXISTS kgc_player_stats (
                    player_id TEXT NOT NULL,
                    game_id TEXT NOT NULL,
                    plays INTEGER NOT NULL DEFAULT 0,
                    wins INTEGER NOT NULL DEFAULT 0,
                    losses INTEGER NOT NULL DEFAULT 0,
                    kills INTEGER NOT NULL DEFAULT 0,
                    deaths INTEGER NOT NULL DEFAULT 0,
                    points INTEGER NOT NULL DEFAULT 0,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_id, game_id)
                )
            """.trimIndent()
            DatabaseType.MYSQL -> """
                CREATE TABLE IF NOT EXISTS kgc_player_stats (
                    player_id VARCHAR(36) NOT NULL,
                    game_id VARCHAR(64) NOT NULL,
                    plays INT NOT NULL DEFAULT 0,
                    wins INT NOT NULL DEFAULT 0,
                    losses INT NOT NULL DEFAULT 0,
                    kills INT NOT NULL DEFAULT 0,
                    deaths INT NOT NULL DEFAULT 0,
                    points INT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_id, game_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.trimIndent()
        }
    }
}
