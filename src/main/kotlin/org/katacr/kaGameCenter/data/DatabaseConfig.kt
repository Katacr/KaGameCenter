package org.katacr.kaGameCenter.data

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class DatabaseConfig(
    val enabled: Boolean,
    val type: DatabaseType,
    val sqliteFile: File,
    val mysqlHost: String,
    val mysqlPort: Int,
    val mysqlDatabase: String,
    val mysqlUsername: String,
    val mysqlPassword: String,
    val poolMaximumSize: Int
) {
    companion object {
        fun from(plugin: JavaPlugin, config: FileConfiguration): DatabaseConfig {
            val type = DatabaseType.from(config.getString("database.type", "sqlite"))
            val sqlitePath = config.getString("database.sqlite.file", "data/kagamecenter.db") ?: "data/kagamecenter.db"
            return DatabaseConfig(
                enabled = config.getBoolean("database.enabled", true),
                type = type,
                sqliteFile = File(plugin.dataFolder, sqlitePath),
                mysqlHost = config.getString("database.mysql.host", "127.0.0.1") ?: "127.0.0.1",
                mysqlPort = config.getInt("database.mysql.port", 3306),
                mysqlDatabase = config.getString("database.mysql.database", "kagamecenter") ?: "kagamecenter",
                mysqlUsername = config.getString("database.mysql.username", "root") ?: "root",
                mysqlPassword = config.getString("database.mysql.password", "") ?: "",
                poolMaximumSize = config.getInt("database.pool.maximum-size", 5).coerceAtLeast(1)
            )
        }
    }
}

enum class DatabaseType {
    SQLITE,
    MYSQL;

    companion object {
        fun from(value: String?): DatabaseType {
            return when (value?.lowercase()) {
                "mysql", "mariadb" -> MYSQL
                else -> SQLITE
            }
        }
    }
}
