package org.katacr.kagamecenter.blockhunt

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.Material
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.selection.RegionSelection
import java.io.File

class BlockhuntConfigService(
    private val dataFolder: File
) {
    private val file = File(dataFolder, "config.yml")
    private var config = YamlConfiguration()

    fun reload(): BlockhuntConfig {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        if (!file.exists()) file.createNewFile()
        config = YamlConfiguration.loadConfiguration(file)
        ensureDefaults()
        save()
        return current()
    }

    fun current(): BlockhuntConfig {
        val maps = linkedMapOf<String, BlockhuntMapConfig>()
        config.getConfigurationSection("maps")?.getKeys(false)?.forEach { mapId ->
            val section = config.getConfigurationSection("maps.$mapId") ?: return@forEach
            maps[mapId] = BlockhuntMapConfig(
                id = mapId,
                displayName = section.getString("display-name", mapId) ?: mapId,
                template = section.getString("template", "blockhunt/$mapId") ?: "blockhunt/$mapId"
            )
        }

        return BlockhuntConfig(
            enabled = config.getBoolean("enabled", true),
            displayName = config.getString("game.display-name", "方块躲猫猫") ?: "方块躲猫猫",
            minPlayers = config.getInt("game.min-players", 2).coerceAtLeast(1),
            maxPlayers = config.getInt("game.max-players", 16).coerceAtLeast(2),
            startCountdownSeconds = config.getInt("game.start-countdown-seconds", 5).coerceIn(1, 30),
            durationSeconds = config.getInt("game.duration-seconds", 300).coerceIn(30, 3600),
            hunterReleaseSeconds = config.getInt("game.hunter-release-seconds", 20).coerceIn(0, 300),
            frenzySeconds = config.getInt("game.frenzy-seconds", 20).coerceIn(0, 300),
            resultDisplaySeconds = config.getInt("game.result-display-seconds", 12).coerceIn(3, 120),
            closeDelaySeconds = config.getInt("game.close-delay-seconds", 8).coerceIn(1, 120),
            hunterRatio = config.getDouble("game.hunter-ratio", 0.25).coerceIn(0.05, 0.75),
            caughtHiderBecomesHunter = config.getBoolean("game.caught-hider-becomes-hunter", true),
            hiderFrenzyAmplifier = config.getInt("game.hider-speed-frenzy-amplifier", 2).coerceIn(0, 10),
            doubleSneakMs = config.getLong("game.hider-lock-double-sneak-ms", 450L).coerceIn(150L, 1500L),
            disguiseRefreshSeconds = config.getInt("game.disguise-refresh-seconds", 45).coerceIn(5, 600),
            disguiseWhitelist = readMaterialList("game.disguise-whitelist", DEFAULT_DISGUISE_WHITELIST),
            itemRefreshSeconds = config.getInt("items.refresh-seconds", 30).coerceIn(3, 600),
            pickupDurationSeconds = config.getInt("items.pickup-duration-seconds", 25).coerceIn(3, 600),
            pickupScale = config.getDouble("items.pickup-scale", 1.5).toFloat().coerceIn(0.25f, 8f),
            maxActivePickupsPerRole = config.getInt("items.max-active-per-role", 1).coerceIn(0, 32),
            hunterSnowballs = config.getInt("items.hunter-snowballs", 16).coerceIn(0, 64),
            hunterGlowSeconds = config.getInt("items.hunter-glow-seconds", 5).coerceIn(1, 60),
            hunterProbeRadius = config.getDouble("items.hunter-probe-radius", 10.0).coerceIn(1.0, 128.0),
            hunterProbeUses = config.getInt("items.hunter-probe-uses", 3).coerceIn(0, 99),
            hiderBlindSeconds = config.getInt("items.hider-blind-seconds", 5).coerceIn(1, 60),
            hiderFreezeSeconds = config.getInt("items.hider-freeze-seconds", 5).coerceIn(1, 60),
            hiderFakeBlockSeconds = config.getInt("items.hider-fake-block-seconds", 30).coerceIn(3, 300),
            hiderInvisibleSeconds = config.getInt("items.hider-invisible-seconds", 5).coerceIn(1, 60),
            maps = maps
        )
    }

    fun findMapByTemplate(template: String?): BlockhuntMapConfig? {
        return current().maps.values.firstOrNull { it.template == template || it.id == template?.substringAfterLast('/') }
    }

    fun readManagedGame(game: ManagedGameConfig): BlockhuntGameConfig {
        val managedConfig = YamlConfiguration.loadConfiguration(game.file)
        val section = managedConfig.getConfigurationSection("blockhunt") ?: managedConfig.createSection("blockhunt")
        return readGame(section)
    }

    fun saveManagedLobby(game: ManagedGameConfig, point: BlockhuntPoint) {
        saveManaged(game) { point.writeTo(it.createSectionReplacing("blockhunt.lobby")) }
    }

    fun saveManagedHunterSpawn(game: ManagedGameConfig, point: BlockhuntPoint) {
        saveManaged(game) { point.writeTo(it.createSectionReplacing("blockhunt.hunter-spawn")) }
    }

    fun saveManagedHiderSpawn(game: ManagedGameConfig, point: BlockhuntPoint) {
        saveManaged(game) { point.writeTo(it.createSectionReplacing("blockhunt.hider-spawn")) }
    }

    fun saveManagedPlayRegion(game: ManagedGameConfig, region: RegionSelection) {
        saveManaged(game) { region.withoutWorld().writeTo(it.createSectionReplacing("blockhunt.play-region")) }
    }

    fun addManagedItemSpawn(game: ManagedGameConfig, id: String, point: BlockhuntPoint) {
        saveManaged(game) { managedConfig ->
            val spawns = managedConfig.getMapList("blockhunt.item-spawns")
                .map { linkedMapOf<String, Any?>(*it.entries.map { entry -> entry.key.toString() to entry.value }.toTypedArray()) }
                .toMutableList()
            val next = linkedMapOf<String, Any?>("id" to id, "point" to pointToMap(point))
            val existing = spawns.indexOfFirst { it["id"] == id }
            if (existing >= 0) spawns[existing] = next else spawns.add(next)
            managedConfig.set("blockhunt.item-spawns", spawns)
        }
    }

    fun removeManagedItemSpawn(game: ManagedGameConfig, id: String): Boolean {
        val managedConfig = YamlConfiguration.loadConfiguration(game.file)
        val spawns = managedConfig.getMapList("blockhunt.item-spawns").toMutableList()
        val removed = spawns.removeIf { it["id"] == id }
        if (removed) {
            managedConfig.set("blockhunt.item-spawns", spawns)
            managedConfig.save(game.file)
        }
        return removed
    }

    private fun readGame(section: ConfigurationSection): BlockhuntGameConfig {
        return BlockhuntGameConfig(
            lobby = BlockhuntPoint.read(section.getConfigurationSection("lobby")),
            hunterSpawn = BlockhuntPoint.read(section.getConfigurationSection("hunter-spawn")),
            hiderSpawn = BlockhuntPoint.read(section.getConfigurationSection("hider-spawn")),
            playRegion = RegionSelection.read(section.getConfigurationSection("play-region")),
            itemSpawns = readSectionList(section, "item-spawns").mapNotNull { memory ->
                val id = memory.getString("id") ?: return@mapNotNull null
                val point = BlockhuntPoint.read(memory.getConfigurationSection("point")) ?: return@mapNotNull null
                BlockhuntItemSpawn(id, point)
            }
        )
    }

    private fun saveManaged(game: ManagedGameConfig, mutate: (YamlConfiguration) -> Unit) {
        val managedConfig = YamlConfiguration.loadConfiguration(game.file)
        mutate(managedConfig)
        managedConfig.save(game.file)
    }

    private fun ensureDefaults() {
        val defaults = mapOf(
            "id" to "blockhunt",
            "name" to "方块躲猫猫",
            "enabled" to true,
            "main" to "jar",
            "jar" to "../blockhunt.jar",
            "entrypoint" to "org.katacr.kagamecenter.blockhunt.BlockhuntModuleProvider",
            "game.display-name" to "方块躲猫猫",
            "game.min-players" to 2,
            "game.max-players" to 16,
            "game.start-countdown-seconds" to 5,
            "game.duration-seconds" to 300,
            "game.hunter-release-seconds" to 20,
            "game.frenzy-seconds" to 20,
            "game.result-display-seconds" to 12,
            "game.close-delay-seconds" to 8,
            "game.hunter-ratio" to 0.25,
            "game.caught-hider-becomes-hunter" to true,
            "game.hider-speed-frenzy-amplifier" to 2,
            "game.hider-lock-double-sneak-ms" to 450,
            "game.disguise-refresh-seconds" to 45,
            "game.disguise-whitelist" to DEFAULT_DISGUISE_WHITELIST,
            "items.refresh-seconds" to 30,
            "items.pickup-duration-seconds" to 25,
            "items.pickup-scale" to 1.5,
            "items.max-active-per-role" to 1,
            "items.hunter-snowballs" to 16,
            "items.hunter-glow-seconds" to 5,
            "items.hunter-probe-radius" to 10,
            "items.hunter-probe-uses" to 3,
            "items.hider-blind-seconds" to 5,
            "items.hider-freeze-seconds" to 5,
            "items.hider-fake-block-seconds" to 30,
            "items.hider-invisible-seconds" to 5,
            "maps.default.display-name" to "默认地图",
            "maps.default.template" to "blockhunt/default",
            "spectator.enabled" to true,
            "spectator.mode" to "managed"
        )
        defaults.forEach { (path, value) ->
            if (!config.contains(path)) config.set(path, value)
        }
    }

    private fun save() {
        config.save(file)
    }

    private fun pointToMap(point: BlockhuntPoint): Map<String, Any> {
        return linkedMapOf(
            "x" to point.x,
            "y" to point.y,
            "z" to point.z,
            "yaw" to point.yaw.toDouble(),
            "pitch" to point.pitch.toDouble()
        )
    }

    private fun readSectionList(section: ConfigurationSection, path: String): List<YamlConfiguration> {
        return section.getMapList(path).map { map ->
            YamlConfiguration().apply {
                map.forEach { (key, value) -> set(key.toString(), value) }
            }
        }
    }

    private fun readMaterialList(path: String, fallback: List<String>): List<Material> {
        val values = config.getStringList(path).ifEmpty { fallback }
        val materials = values.mapNotNull { value ->
            val normalized = value.trim().removePrefix("minecraft:").uppercase().replace('-', '_')
            Material.matchMaterial(normalized)?.takeIf { it.isBlock && !it.isAir }
        }.distinct()
        return materials.ifEmpty {
            fallback.mapNotNull { Material.matchMaterial(it) }.filter { it.isBlock && !it.isAir }
        }
    }

    companion object {
        val DEFAULT_DISGUISE_WHITELIST = listOf(
            "GRASS_BLOCK",
            "DIRT",
            "COARSE_DIRT",
            "PODZOL",
            "ROOTED_DIRT",
            "STONE",
            "COBBLESTONE",
            "STONE_BRICKS",
            "SMOOTH_STONE",
            "OAK_LOG",
            "SPRUCE_LOG",
            "BIRCH_LOG",
            "OAK_PLANKS",
            "SPRUCE_PLANKS",
            "BIRCH_PLANKS",
            "OAK_LEAVES",
            "SPRUCE_LEAVES",
            "BIRCH_LEAVES",
            "GLASS",
            "BOOKSHELF",
            "BARREL",
            "CHEST",
            "HAY_BLOCK",
            "SAND",
            "SANDSTONE",
            "GRAVEL",
            "BRICKS",
            "TERRACOTTA",
            "WHITE_WOOL",
            "RED_WOOL",
            "BLUE_WOOL",
            "CRAFTING_TABLE",
            "FURNACE",
            "LECTERN",
            "SEA_LANTERN",
            "GLOWSTONE",
            "QUARTZ_BLOCK"
        )
    }
}

private fun ConfigurationSection.createSectionReplacing(path: String): ConfigurationSection {
    set(path, null)
    return createSection(path)
}
