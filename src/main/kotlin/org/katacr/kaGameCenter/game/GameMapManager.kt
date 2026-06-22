package org.katacr.kaGameCenter.game

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class GameMapManager(
    private val plugin: JavaPlugin,
    private val gameManager: GameManager
) {
    private val mapsFolder: File
        get() = File(plugin.dataFolder, "maps")

    init {
        ensureMapsFolder()
    }

    fun ensureMapsFolder() {
        if (!mapsFolder.exists()) {
            mapsFolder.mkdirs()
        }
    }

    fun listGames(): List<String> {
        ensureMapsFolder()
        val configured = gameManager.all().map { it.id }
        val folders = mapsFolder.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name.lowercase() }
            ?: emptyList()
        return (configured + folders).distinct().sorted()
    }

    fun listMaps(gameId: String): List<GameMapInfo> {
        ensureMapsFolder()
        val normalizedGame = normalizeName(gameId) ?: return emptyList()
        val gameFolder = File(mapsFolder, normalizedGame)
        if (!gameFolder.exists() || !gameFolder.isDirectory) return emptyList()

        return gameFolder.listFiles()
            ?.filter { it.isDirectory }
            ?.map { folder ->
                val relativePath = "$normalizedGame/${folder.name}"
                GameMapInfo(
                    gameId = normalizedGame,
                    mapId = folder.name,
                    relativePath = relativePath,
                    folder = folder,
                    active = true
                )
            }
            ?.sortedBy { it.mapId.lowercase() }
            ?: emptyList()
    }

    fun createMap(gameId: String, mapId: String): GameMapResult {
        val normalizedGame = normalizeName(gameId)
            ?: return GameMapResult(false, "Invalid game id: $gameId")
        val normalizedMap = normalizeName(mapId)
            ?: return GameMapResult(false, "Invalid map id: $mapId")

        val folder = File(File(mapsFolder, normalizedGame), normalizedMap)
        if (folder.exists()) {
            return GameMapResult(false, "Map already exists: $normalizedGame/$normalizedMap")
        }
        if (!folder.mkdirs()) {
            return GameMapResult(false, "Failed to create map folder: ${folder.absolutePath}")
        }
        saveSpawn(folder, 0.5, 80.0, 0.5, 0.0f, 0.0f)
        return GameMapResult(true, "Created map folder: $normalizedGame/$normalizedMap")
    }

    fun setSpawn(gameId: String, mapId: String, x: Double, y: Double, z: Double, yaw: Float, pitch: Float): GameMapResult {
        val normalizedGame = normalizeName(gameId)
            ?: return GameMapResult(false, "Invalid game id: $gameId")
        val normalizedMap = normalizeName(mapId)
            ?: return GameMapResult(false, "Invalid map id: $mapId")
        val folder = File(mapsFolder, "$normalizedGame/$normalizedMap")
        if (!folder.exists() || !folder.isDirectory) {
            return GameMapResult(false, "Map folder does not exist: $normalizedGame/$normalizedMap")
        }
        saveSpawn(folder, x, y, z, yaw, pitch)
        return GameMapResult(true, "Updated spawn for $normalizedGame/$normalizedMap: $x, $y, $z")
    }

    fun selectMap(gameId: String, mapId: String): GameMapResult {
        val normalizedGame = normalizeName(gameId)
            ?: return GameMapResult(false, "Invalid game id: $gameId")
        val normalizedMap = normalizeName(mapId)
            ?: return GameMapResult(false, "Invalid map id: $mapId")
        val relativePath = "$normalizedGame/$normalizedMap"
        val folder = File(mapsFolder, relativePath)
        if (!folder.exists() || !folder.isDirectory) {
            return GameMapResult(false, "Map folder does not exist: $relativePath")
        }
        return GameMapResult(true, "Map template is available: $relativePath")
    }

    fun removeMap(gameId: String, mapId: String): GameMapResult {
        val normalizedGame = normalizeName(gameId)
            ?: return GameMapResult(false, "Invalid game id: $gameId")
        val normalizedMap = normalizeName(mapId)
            ?: return GameMapResult(false, "Invalid map id: $mapId")
        val relativePath = "$normalizedGame/$normalizedMap"
        val folder = File(mapsFolder, relativePath)
        if (!folder.exists()) {
            return GameMapResult(false, "Map does not exist: $relativePath")
        }
        if (!folder.isDirectory) {
            return GameMapResult(false, "Map path is not a folder: $relativePath")
        }

        val deleted = folder.deleteRecursively()
        if (!deleted) {
            return GameMapResult(false, "Failed to delete map folder: $relativePath")
        }

        return GameMapResult(true, "Removed map: $relativePath")
    }

    fun reload(): GameMapResult {
        ensureMapsFolder()
        return GameMapResult(true, "Reloaded map templates")
    }

    private fun saveSpawn(folder: File, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        val file = File(folder, "map.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("spawn.x", x)
        config.set("spawn.y", y)
        config.set("spawn.z", z)
        config.set("spawn.yaw", yaw.toDouble())
        config.set("spawn.pitch", pitch.toDouble())
        config.save(file)
    }

    private fun normalizeName(value: String): String? {
        val trimmed = value.trim().trim('/')
        if (trimmed.isBlank()) return null
        if (trimmed.contains("..") || trimmed.contains('/') || trimmed.contains('\\')) return null
        return trimmed.lowercase()
    }
}

data class GameMapInfo(
    val gameId: String,
    val mapId: String,
    val relativePath: String,
    val folder: File,
    val active: Boolean
)

data class GameMapResult(
    val success: Boolean,
    val message: String
)
