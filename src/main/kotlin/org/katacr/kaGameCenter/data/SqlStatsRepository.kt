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
            maximumPoolSize = if (config.type == DatabaseType.SQLITE) 1 else config.poolMaximumSize
            minimumIdle = 1
            jdbcUrl = jdbcUrl()
            if (config.type == DatabaseType.SQLITE) {
                connectionInitSql = "PRAGMA busy_timeout=5000"
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
                statement.execute(createTableSql())
                statement.execute(createMetricTableSql())
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

    override fun loadMetrics(): List<PlayerGameMetric> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT player_id, game_id, metric_id, metric_value
                FROM kgc_player_metrics
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { result ->
                    val output = mutableListOf<PlayerGameMetric>()
                    while (result.next()) {
                        output.add(
                            PlayerGameMetric(
                                playerId = UUID.fromString(result.getString("player_id")),
                                gameId = result.getString("game_id"),
                                metricId = result.getString("metric_id"),
                                value = result.getInt("metric_value")
                            )
                        )
                    }
                    return output
                }
            }
        }
    }

    override fun saveMetric(metric: PlayerGameMetric) {
        dataSource.connection.use { connection ->
            when (config.type) {
                DatabaseType.SQLITE -> saveMetricSqlite(connection, metric)
                DatabaseType.MYSQL -> saveMetricMysql(connection, metric)
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

    /** 使用 SQLite 冲突更新语法保存单个玩法扩展指标。 */
    private fun saveMetricSqlite(connection: Connection, metric: PlayerGameMetric) {
        connection.prepareStatement(
            """
            INSERT INTO kgc_player_metrics
                (player_id, game_id, metric_id, metric_value, updated_at)
            VALUES
                (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(player_id, game_id, metric_id) DO UPDATE SET
                metric_value = excluded.metric_value,
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
        ).use { statement ->
            bindMetric(statement, metric)
            statement.executeUpdate()
        }
    }

    /** 使用 MySQL 冲突更新语法保存单个玩法扩展指标。 */
    private fun saveMetricMysql(connection: Connection, metric: PlayerGameMetric) {
        connection.prepareStatement(
            """
            INSERT INTO kgc_player_metrics
                (player_id, game_id, metric_id, metric_value, updated_at)
            VALUES
                (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                metric_value = VALUES(metric_value),
                updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
        ).use { statement ->
            bindMetric(statement, metric)
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

    /** 绑定玩法扩展指标的联合主键与计数值。 */
    private fun bindMetric(statement: java.sql.PreparedStatement, metric: PlayerGameMetric) {
        statement.setString(1, metric.playerId.toString())
        statement.setString(2, metric.gameId.lowercase())
        statement.setString(3, metric.metricId.lowercase())
        statement.setInt(4, metric.value)
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

    /** 生成当前数据库方言的玩法扩展指标表结构。 */
    private fun createMetricTableSql(): String {
        return when (config.type) {
            DatabaseType.SQLITE -> """
                CREATE TABLE IF NOT EXISTS kgc_player_metrics (
                    player_id TEXT NOT NULL,
                    game_id TEXT NOT NULL,
                    metric_id TEXT NOT NULL,
                    metric_value INTEGER NOT NULL DEFAULT 0,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_id, game_id, metric_id)
                )
            """.trimIndent()
            DatabaseType.MYSQL -> """
                CREATE TABLE IF NOT EXISTS kgc_player_metrics (
                    player_id VARCHAR(36) NOT NULL,
                    game_id VARCHAR(64) NOT NULL,
                    metric_id VARCHAR(64) NOT NULL,
                    metric_value INT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (player_id, game_id, metric_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """.trimIndent()
        }
    }
}
