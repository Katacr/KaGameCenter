package org.katacr.kaGameCenter.world

import net.kyori.adventure.util.TriState
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.GameRules
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class TemporaryWorldService(private val plugin: JavaPlugin) {
    private val worldDataDirectories = setOf("region", "entities", "poi", "data", "playerdata", "advancements", "stats", "DIM1", "DIM-1")
    private val worldDataFiles = setOf("level.dat", "level.dat_old", "session.lock", "uid.dat", "icon.png")
    private val templateCleanupDirectories = worldDataDirectories + setOf("dimensions", "players")
    private val temporaryWorldPrefixes = listOf("kgc_", "kgc_edit_", "kagamecenter_demo_")
    private val runtimeWorldBounds = linkedMapOf<String, RegionBounds>()

    private val mapsFolder: File
        get() = File(plugin.dataFolder, "maps")

    init {
        ensureReservedFolders()
    }

    fun createRoomWorld(worldName: String): World? {
        ensureReservedFolders()
        Bukkit.getWorld(worldName)?.let { return it }

        val creator = WorldCreator.name(worldName)
            .environment(World.Environment.NORMAL)
            .type(WorldType.FLAT)
            .generateStructures(false)
            .keepSpawnLoaded(TriState.FALSE)

        val world = creator.createWorld() ?: return null
        configure(world)
        return world
    }

    fun createRoomWorldFromTemplate(templatePath: String?, worldName: String, allowFlatFallback: Boolean = true): World? {
        ensureReservedFolders()
        if (templatePath.isNullOrBlank()) {
            return if (allowFlatFallback) createRoomWorld(worldName) else null
        }

        Bukkit.getWorld(worldName)?.let { return it }

        val source = File(mapsFolder, templatePath.trimStart('/'))
        if (!source.exists() || !source.isDirectory) {
            plugin.logger.warning("World template not found: ${source.absolutePath}")
            return if (allowFlatFallback) createRoomWorld(worldName) else null
        }
        if (!isUsableTemplate(source)) {
            plugin.logger.warning("World template is incomplete: ${source.absolutePath}. Required files: level.dat and region/*.mca")
            return if (allowFlatFallback) createRoomWorld(worldName) else null
        }

        val target = File(Bukkit.getWorldContainer(), worldName)
        deleteWorldStorage(worldName)
        if (!target.exists() && !target.mkdirs()) return null
        if (!copyWorldData(source, target)) return null
        cleanupRuntimeWorldIdentity(target)

        val creator = WorldCreator.name(worldName)
            .environment(World.Environment.NORMAL)
            .generateStructures(false)
            .keepSpawnLoaded(TriState.FALSE)

        val world = creator.createWorld() ?: return null
        configure(world, source)
        return world
    }

    fun createRoomWorldFromDirectory(templateDir: File?, worldName: String, allowFlatFallback: Boolean = true): World? {
        ensureReservedFolders()
        if (templateDir == null) {
            return if (allowFlatFallback) createRoomWorld(worldName) else null
        }

        Bukkit.getWorld(worldName)?.let { return it }

        if (!templateDir.exists() || !templateDir.isDirectory) {
            plugin.logger.warning("World template directory not found: ${templateDir.absolutePath}")
            return if (allowFlatFallback) createRoomWorld(worldName) else null
        }
        if (!isUsableTemplate(templateDir)) {
            plugin.logger.warning("World template directory is incomplete: ${templateDir.absolutePath}. Required files: level.dat and region/*.mca")
            return if (allowFlatFallback) createRoomWorld(worldName) else null
        }

        val target = File(Bukkit.getWorldContainer(), worldName)
        deleteWorldStorage(worldName)
        if (!target.exists() && !target.mkdirs()) return null
        if (!copyWorldData(templateDir, target)) return null
        cleanupRuntimeWorldIdentity(target)

        val creator = WorldCreator.name(worldName)
            .environment(World.Environment.NORMAL)
            .generateStructures(false)
            .keepSpawnLoaded(TriState.FALSE)

        val world = creator.createWorld() ?: return null
        configure(world, templateDir)
        return world
    }

    fun createEditorWorldFromTemplate(templatePath: String?, worldName: String): World? {
        val world = createRoomWorldFromTemplate(templatePath, worldName, allowFlatFallback = false) ?: return null
        world.isAutoSave = true
        return world
    }

    fun createEditorWorldFromDirectory(templateDir: File?, worldName: String): World? {
        val world = createRoomWorldFromDirectory(templateDir, worldName, allowFlatFallback = false) ?: return null
        world.isAutoSave = true
        return world
    }

    fun readTemplateSpawn(templatePath: String?, world: World): org.bukkit.Location {
        if (templatePath.isNullOrBlank()) {
            return world.spawnLocation
        }

        val metadataFile = File(File(mapsFolder, templatePath.trimStart('/')), "map.yml")
        if (!metadataFile.exists()) {
            return world.spawnLocation
        }

        val config = YamlConfiguration.loadConfiguration(metadataFile)
        val section = config.getConfigurationSection("spawn") ?: return world.spawnLocation
        val x = section.getDouble("x", world.spawnLocation.x)
        val y = section.getDouble("y", world.spawnLocation.y)
        val z = section.getDouble("z", world.spawnLocation.z)
        val yaw = section.getDouble("yaw", world.spawnLocation.yaw.toDouble()).toFloat()
        val pitch = section.getDouble("pitch", world.spawnLocation.pitch.toDouble()).toFloat()
        return org.bukkit.Location(world, x, y, z, yaw, pitch)
    }

    fun ensureReservedFolders() {
        if (!mapsFolder.exists()) {
            mapsFolder.mkdirs()
        }
    }

    fun cleanupStaleTemporaryWorlds(): Int {
        if (!plugin.config.getBoolean("worlds.cleanup-stale-temporary-on-startup", true)) return 0

        val container = Bukkit.getWorldContainer()
        val dimensionRoot = File(File(File(container, "world"), "dimensions"), "minecraft")
        val names = linkedSetOf<String>()

        container.listFiles()?.forEach { file ->
            if (file.isDirectory && isTemporaryWorldName(file.name)) {
                names.add(file.name)
            }
        }
        dimensionRoot.listFiles()?.forEach { file ->
            if (file.isDirectory && isTemporaryWorldName(file.name)) {
                names.add(file.name)
            }
        }

        return names.count { unloadAndDelete(it) }
    }

    fun unloadAndDelete(worldName: String): Boolean {
        runtimeWorldBounds.remove(worldName)
        val world = Bukkit.getWorld(worldName)
        if (world != null) {
            world.players.forEach { player ->
                player.teleport(Bukkit.getWorlds().first().spawnLocation)
            }
            Bukkit.unloadWorld(world, false)
        }

        return deleteWorldStorage(worldName)
    }

    fun clampToTemplateBorder(location: org.bukkit.Location): org.bukkit.Location? {
        if (!plugin.config.getBoolean("worlds.restrict-to-template-border.enabled", true)) return null
        val world = location.world ?: return null
        val bounds = runtimeWorldBounds[world.name] ?: return null
        if (bounds.contains(location.x, location.z)) return null

        val clampedX = location.x.coerceIn(bounds.minX + 0.5, bounds.maxX - 0.5)
        val clampedZ = location.z.coerceIn(bounds.minZ + 0.5, bounds.maxZ - 0.5)
        return location.clone().apply {
            x = clampedX
            z = clampedZ
        }
    }

    fun hasTemplateBorder(worldName: String): Boolean {
        return runtimeWorldBounds.containsKey(worldName)
    }

    fun saveWorldToTemplate(world: World, templatePath: String?): Boolean {
        ensureReservedFolders()
        if (templatePath.isNullOrBlank()) return false
        val source = File(Bukkit.getWorldContainer(), world.name)
        if (!source.exists() || !source.isDirectory) return false
        val target = File(mapsFolder, templatePath.trimStart('/'))
        if (!target.exists() && !target.mkdirs()) return false

        world.save()
        cleanupTemplateWorldData(target)
        if (!copyWorldData(source, target)) return false
        cleanupRuntimeWorldIdentity(target)
        return true
    }

    fun saveWorldToDirectory(world: World, target: File?): Boolean {
        ensureReservedFolders()
        if (target == null) return false
        val source = File(Bukkit.getWorldContainer(), world.name)
        if (!source.exists() || !source.isDirectory) return false
        if (!target.exists() && !target.mkdirs()) return false

        world.save()
        cleanupTemplateWorldData(target)
        if (!copyWorldData(source, target)) return false
        cleanupRuntimeWorldIdentity(target)
        return true
    }

    fun snapshotTemplateToDirectory(templatePath: String?, target: File): Boolean {
        ensureReservedFolders()
        if (templatePath.isNullOrBlank()) return false
        val source = File(mapsFolder, templatePath.trimStart('/'))
        if (!source.exists() || !source.isDirectory || !isUsableTemplate(source)) return false
        if (!target.parentFile.exists()) {
            target.parentFile.mkdirs()
        }
        if (target.exists()) {
            cleanupTemplateWorldData(target)
        } else if (!target.mkdirs()) {
            return false
        }
        if (!copyWorldData(source, target)) return false
        cleanupRuntimeWorldIdentity(target)
        return true
    }

    private fun configure(world: World, templateDirectory: File? = null) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        world.setGameRule(GameRules.LOCATOR_BAR, false)
        world.setGameRule(GameRules.SPECTATORS_GENERATE_CHUNKS, false)
        world.time = plugin.config.getLong("demo.time", 6000L)
        world.setStorm(false)
        world.isAutoSave = false
        applyTemplateWorldBorder(world, templateDirectory)
    }

    private fun cleanupTemplateWorldData(target: File) {
        target.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name in templateCleanupDirectories) {
                file.deleteRecursively()
            }
            if (file.isFile && file.name in worldDataFiles) {
                file.delete()
            }
        }
    }

    private fun applyTemplateWorldBorder(world: World, templateDirectory: File?) {
        if (!plugin.config.getBoolean("worlds.restrict-to-template-border.enabled", true)) return
        val bounds = templateDirectory?.let(::regionBounds) ?: return
        runtimeWorldBounds[world.name] = bounds
        val bufferBlocks = plugin.config.getInt("worlds.restrict-to-template-border.buffer-chunks", 0).coerceAtLeast(0) * 16.0
        val minX = bounds.minX - bufferBlocks
        val maxX = bounds.maxX + bufferBlocks
        val minZ = bounds.minZ - bufferBlocks
        val maxZ = bounds.maxZ + bufferBlocks
        val effectiveBounds = RegionBounds(minX, maxX, minZ, maxZ)
        runtimeWorldBounds[world.name] = effectiveBounds
        val width = maxX - minX
        val depth = maxZ - minZ
        val size = maxOf(width, depth).coerceAtLeast(1.0)
        val border = world.worldBorder

        border.setCenter((minX + maxX) / 2.0, (minZ + maxZ) / 2.0)
        border.setSize(size)
        border.warningDistance = 0
        border.damageBuffer = 0.0
        border.damageAmount = 0.2
    }

    private fun regionBounds(templateDirectory: File): RegionBounds? {
        val regionFolder = primaryWorldDataFolder(templateDirectory)?.let { File(it, "region") } ?: return null
        val regionFiles = regionFolder.listFiles { file ->
            file.isFile && file.name.matches(REGION_FILE_REGEX)
        } ?: return null
        if (regionFiles.isEmpty()) return null

        var minRegionX = Int.MAX_VALUE
        var maxRegionX = Int.MIN_VALUE
        var minRegionZ = Int.MAX_VALUE
        var maxRegionZ = Int.MIN_VALUE

        regionFiles.forEach { file ->
            val match = REGION_FILE_REGEX.matchEntire(file.name) ?: return@forEach
            val regionX = match.groupValues[1].toIntOrNull() ?: return@forEach
            val regionZ = match.groupValues[2].toIntOrNull() ?: return@forEach
            minRegionX = minOf(minRegionX, regionX)
            maxRegionX = maxOf(maxRegionX, regionX)
            minRegionZ = minOf(minRegionZ, regionZ)
            maxRegionZ = maxOf(maxRegionZ, regionZ)
        }

        if (minRegionX == Int.MAX_VALUE || minRegionZ == Int.MAX_VALUE) return null
        return RegionBounds(
            minX = minRegionX * REGION_BLOCK_SIZE.toDouble(),
            maxX = (maxRegionX + 1) * REGION_BLOCK_SIZE.toDouble(),
            minZ = minRegionZ * REGION_BLOCK_SIZE.toDouble(),
            maxZ = (maxRegionZ + 1) * REGION_BLOCK_SIZE.toDouble()
        )
    }

    private fun isTemporaryWorldName(worldName: String): Boolean {
        return temporaryWorldPrefixes.any { worldName.startsWith(it) }
    }

    private fun deleteWorldStorage(worldName: String): Boolean {
        val container = Bukkit.getWorldContainer()
        val rootWorld = File(container, worldName)
        val dimensionWorld = File(File(File(File(container, "world"), "dimensions"), "minecraft"), worldName)
        var deleted = true
        listOf(rootWorld, dimensionWorld).distinctBy { it.absolutePath }.forEach { folder ->
            if (folder.exists()) {
                deleted = folder.deleteRecursively() && deleted
            }
        }
        return deleted
    }

    private fun copyWorldData(source: File, target: File): Boolean {
        val sourceWorldData = primaryWorldDataFolder(source) ?: return false
        source.listFiles()?.forEach { file ->
            if (file.name in ignoredTemplateCopyNames) return@forEach
            if (sourceWorldData != source && file.name in worldDataDirectories) return@forEach
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                file.copyRecursively(targetFile, overwrite = true)
            } else {
                file.copyTo(targetFile, overwrite = true)
            }
        }
        if (sourceWorldData != source) {
            copyWorldDataDirectories(sourceWorldData, target)
        }
        return true
    }

    private fun isUsableTemplate(source: File): Boolean {
        val levelDat = File(source, "level.dat")
        val regionFolder = primaryWorldDataFolder(source)?.let { File(it, "region") }
        val hasRegionFiles = regionFolder?.isDirectory == true && regionFolder.listFiles { file ->
            file.isFile && file.extension.equals("mca", ignoreCase = true)
        }?.isNotEmpty() == true
        return levelDat.isFile && hasRegionFiles
    }

    private fun primaryWorldDataFolder(source: File): File? {
        if (hasRegionFiles(File(source, "region"))) return source

        val dimensionsRoot = File(File(source, "dimensions"), "minecraft")
        val overworld = File(dimensionsRoot, "overworld")
        if (hasRegionFiles(File(overworld, "region"))) return overworld

        return dimensionsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.firstOrNull { hasRegionFiles(File(it, "region")) }
    }

    private fun hasRegionFiles(regionFolder: File): Boolean {
        return regionFolder.isDirectory && regionFolder.listFiles { file ->
            file.isFile && file.extension.equals("mca", ignoreCase = true)
        }?.isNotEmpty() == true
    }

    private fun copyWorldDataDirectories(source: File, target: File) {
        worldDataDirectories.forEach { name ->
            val sourceFile = File(source, name)
            if (!sourceFile.exists()) return@forEach
            val targetFile = File(target, name)
            if (sourceFile.isDirectory) {
                sourceFile.copyRecursively(targetFile, overwrite = true)
            } else {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        }
    }

    private fun cleanupRuntimeWorldIdentity(target: File) {
        File(target, "uid.dat").delete()
        File(target, "session.lock").delete()
    }

    private data class RegionBounds(
        val minX: Double,
        val maxX: Double,
        val minZ: Double,
        val maxZ: Double
    ) {
        fun contains(x: Double, z: Double): Boolean {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ
        }
    }

    private companion object {
        private const val REGION_BLOCK_SIZE = 512
        private val REGION_FILE_REGEX = Regex("""r\.(-?\d+)\.(-?\d+)\.mca""")
        private val ignoredTemplateCopyNames = setOf(
            "uid.dat",
            "session.lock",
            "dimensions",
            "players",
            "playerdata",
            "advancements",
            "stats"
        )
    }
}
