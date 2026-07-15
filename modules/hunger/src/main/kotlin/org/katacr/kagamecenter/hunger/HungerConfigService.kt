package org.katacr.kagamecenter.hunger

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.map.ManagedMapPointService
import org.katacr.kaGameCenter.selection.RegionSelection
import java.io.File

/** 读取模块规则并维护托管游戏的经典地图点位。 */
class HungerConfigService(
    private val dataFolder: File,
    private val mapPointService: ManagedMapPointService
) {
    private val file = File(dataFolder, "config.yml")
    private var config = YamlConfiguration()

    fun reload(): HungerConfig {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        if (!file.exists()) file.createNewFile()
        config = YamlConfiguration.loadConfiguration(file)
        ensureDefaults()
        config.save(file)
        return current()
    }

    fun current(): HungerConfig {
        val maps = linkedMapOf<String, HungerMapConfig>()
        config.getConfigurationSection("maps")?.getKeys(false)?.forEach { mapId ->
            val section = config.getConfigurationSection("maps.$mapId") ?: return@forEach
            maps[mapId] = HungerMapConfig(
                id = mapId,
                displayName = section.getString("display-name", mapId) ?: mapId,
                template = section.getString("template", "hunger/$mapId") ?: "hunger/$mapId"
            )
        }

        val minItems = config.getInt("loot.min-items-per-chest", 1).coerceIn(0, 27)
        val maxItems = config.getInt("loot.max-items-per-chest", 10).coerceIn(minItems, 27)
        return HungerConfig(
            enabled = config.getBoolean("enabled", true),
            displayName = config.getString("game.display-name", "饥饿游戏") ?: "饥饿游戏",
            minPlayers = config.getInt("game.min-players", 6).coerceAtLeast(2),
            maxPlayers = config.getInt("game.max-players", 24).coerceAtLeast(2),
            countdownSeconds = config.getInt("game.countdown-seconds", 60).coerceIn(1, 600),
            protectionSeconds = config.getInt("game.protection-seconds", 60).coerceIn(0, 600),
            durationSeconds = config.getInt("game.duration-seconds", 900).coerceIn(60, 7200),
            refillAfterSeconds = config.getInt("game.refill-after-seconds", 300).coerceIn(0, 7200),
            forceDeathmatchPlayers = config.getInt("game.force-deathmatch-players", 4).coerceIn(2, 24),
            forceDeathmatchDelaySeconds = config.getInt("game.force-deathmatch-delay-seconds", 60).coerceIn(0, 600),
            deathmatchSeconds = config.getInt("game.deathmatch-seconds", 300).coerceIn(10, 1800),
            resultDisplaySeconds = config.getInt("game.result-display-seconds", 10).coerceIn(3, 120),
            closeDelaySeconds = config.getInt("game.close-delay-seconds", 5).coerceIn(1, 120),
            defaultVoidY = config.getDouble("game.void-y", -64.0),
            maxHealth = config.getDouble("game.max-health", 20.0).coerceIn(1.0, 2048.0),
            allowMonsterSpawns = config.getBoolean("game.allow-monsters", false),
            winPoints = config.getInt("game.win-points", 3).coerceIn(0, 1_000_000),
            loot = HungerLootConfig(
                minItemsPerChest = minItems,
                maxItemsPerChest = maxItems,
                containerMaterials = readMaterials("loot.container-materials", DEFAULT_CONTAINER_MATERIALS),
                entries = readLootEntries().ifEmpty { defaultLootEntries() }
            ),
            blocks = HungerBlockRules(
                breakAllowed = readMaterials("blocks.break-allowed", DEFAULT_BREAK_MATERIALS),
                placeAllowed = readMaterials("blocks.place-allowed", DEFAULT_PLACE_MATERIALS),
                autoPrimeTnt = config.getBoolean("blocks.auto-prime-tnt", true),
                tntFuseTicks = config.getInt("blocks.tnt-fuse-ticks", 40).coerceIn(1, 1200),
                explosionBlockDamage = config.getBoolean("blocks.explosion-block-damage", false),
                fireSpread = config.getBoolean("blocks.fire-spread", false)
            ),
            tracker = HungerTrackerConfig(
                enabled = config.getBoolean("tracker.enabled", false),
                range = config.getDouble("tracker.range", 100.0).coerceIn(1.0, 2048.0)
            ),
            maps = maps
        )
    }

    fun findMapByTemplate(template: String?): HungerMapConfig? {
        return current().maps.values.firstOrNull { it.template == template || it.id == template?.substringAfterLast('/') }
    }

    fun readManagedGame(game: ManagedGameConfig): HungerGameConfig {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        val section = managed.getConfigurationSection("hunger") ?: managed.createSection("hunger")
        val supplyChests = mapPointService.readNamedPoints(section, "supply-chests").ifEmpty {
            mapPointService.readNamedPoints(section, "chest-spawns")
        }
        return HungerGameConfig(
            mapId = section.getString("map-id"),
            lobby = mapPointService.read(section.getConfigurationSection("lobby")),
            spectatorSpawn = mapPointService.read(section.getConfigurationSection("spectator-spawn")),
            playRegion = RegionSelection.read(section.getConfigurationSection("play-region")),
            tributeSpawns = mapPointService.readNamedPoints(section, "tribute-spawns"),
            deathmatchSpawns = mapPointService.readNamedPoints(section, "deathmatch-spawns"),
            supplyChests = supplyChests,
            voidY = if (section.contains("void-y")) section.getDouble("void-y") else null
        )
    }

    fun saveManagedLobby(game: ManagedGameConfig, point: HungerPoint) {
        saveManaged(game) { mapPointService.replace(it, "hunger.lobby", point) }
    }

    fun saveManagedSpectatorSpawn(game: ManagedGameConfig, point: HungerPoint) {
        saveManaged(game) { mapPointService.replace(it, "hunger.spectator-spawn", point) }
    }

    fun saveManagedPlayRegion(game: ManagedGameConfig, region: RegionSelection) {
        saveManaged(game) {
            it.set("hunger.play-region", null)
            mapPointService.portable(region).writeTo(it.createSection("hunger.play-region"))
        }
    }

    fun saveManagedVoidY(game: ManagedGameConfig, voidY: Double) {
        saveManaged(game) { it.set("hunger.void-y", voidY) }
    }

    fun addManagedTributeSpawn(game: ManagedGameConfig, id: String, point: HungerPoint) {
        upsertNamedPoint(game, "hunger.tribute-spawns", id, point)
    }

    fun removeManagedTributeSpawn(game: ManagedGameConfig, id: String): Boolean {
        return removeNamedPoint(game, "hunger.tribute-spawns", id)
    }

    fun addManagedDeathmatchSpawn(game: ManagedGameConfig, id: String, point: HungerPoint) {
        upsertNamedPoint(game, "hunger.deathmatch-spawns", id, point)
    }

    fun removeManagedDeathmatchSpawn(game: ManagedGameConfig, id: String): Boolean {
        return removeNamedPoint(game, "hunger.deathmatch-spawns", id)
    }

    fun addManagedSupplyChest(game: ManagedGameConfig, id: String, point: HungerPoint) {
        upsertNamedPoint(game, "hunger.supply-chests", id, point)
    }

    /** 自动分配单调递增的贡品出生点编号并保存位置。 */
    fun addNextManagedTributeSpawn(game: ManagedGameConfig, point: HungerPoint): String {
        return addNextNamedPoint(game, "tribute-spawns", "hunger.next-tribute-index", "tribute", point)
    }

    /** 自动分配单调递增的死斗出生点编号并保存位置。 */
    fun addNextManagedDeathmatchSpawn(game: ManagedGameConfig, point: HungerPoint): String {
        return addNextNamedPoint(game, "deathmatch-spawns", "hunger.next-deathmatch-index", "dm", point)
    }

    /** 自动分配单调递增的补给箱编号并保存方块位置。 */
    fun addNextManagedSupplyChest(game: ManagedGameConfig, point: HungerPoint): String {
        return addNextNamedPoint(game, "supply-chests", "hunger.next-chest-index", "chest", point)
    }

    fun removeManagedSupplyChest(game: ManagedGameConfig, id: String): Boolean {
        return removeNamedPoint(game, "hunger.supply-chests", id)
    }

    private fun readLootEntries(): List<HungerLootEntry> {
        return config.getMapList("loot.entries").mapNotNull { values ->
            val material = values["material"]?.toString()?.toMaterial() ?: return@mapNotNull null
            val minAmount = values["min-amount"].toIntOr(1).coerceIn(1, material.maxStackSize)
            val maxAmount = values["max-amount"].toIntOr(minAmount).coerceIn(minAmount, material.maxStackSize)
            val weight = values["weight"].toIntOr(1)
            HungerLootEntry(material, minAmount, maxAmount, weight)
        }.filter { it.weight > 0 }
    }

    private fun upsertNamedPoint(game: ManagedGameConfig, path: String, id: String, point: HungerPoint) {
        saveManaged(game) { mapPointService.upsertNamedPoint(it, path, id, point) }
    }

    /** 为指定 Hunger 点位列表推断并持久化下一个可用编号。 */
    private fun addNextNamedPoint(
        game: ManagedGameConfig,
        listPath: String,
        counterPath: String,
        prefix: String,
        point: HungerPoint
    ): String {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        val section = managed.getConfigurationSection("hunger") ?: managed.createSection("hunger")
        val existingIds = mapPointService.readNamedPoints(section, listPath).map { it.id }
        val inferred = existingIds.mapNotNull { id ->
            id.takeIf { it.startsWith("${prefix}_", ignoreCase = true) }?.substringAfterLast('_')?.toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
        var index = maxOf(managed.getInt(counterPath, 1), inferred).coerceAtLeast(1)
        while (existingIds.any { it.equals("${prefix}_$index", ignoreCase = true) }) index++
        val id = "${prefix}_$index"
        mapPointService.upsertNamedPoint(managed, "hunger.$listPath", id, point)
        managed.set(counterPath, index + 1)
        managed.save(game.file)
        return id
    }

    private fun removeNamedPoint(game: ManagedGameConfig, path: String, id: String): Boolean {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        val removed = mapPointService.removeNamedPoint(managed, path, id)
        if (removed) managed.save(game.file)
        return removed
    }

    private fun saveManaged(game: ManagedGameConfig, mutate: (YamlConfiguration) -> Unit) {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        mutate(managed)
        managed.save(game.file)
    }

    private fun readMaterials(path: String, fallback: List<String>): Set<Material> {
        val names = config.getStringList(path).ifEmpty { fallback }
        return names.mapNotNull(String::toMaterial).toSet()
    }

    private fun ensureDefaults() {
        val defaults = mapOf(
            "id" to "hunger",
            "name" to "饥饿游戏",
            "enabled" to true,
            "main" to "jar",
            "jar" to "../hunger.jar",
            "entrypoint" to "org.katacr.kagamecenter.hunger.HungerModuleProvider",
            "game.display-name" to "饥饿游戏",
            "game.min-players" to 6,
            "game.max-players" to 24,
            "game.countdown-seconds" to 60,
            "game.protection-seconds" to 60,
            "game.duration-seconds" to 900,
            "game.refill-after-seconds" to 300,
            "game.force-deathmatch-players" to 4,
            "game.force-deathmatch-delay-seconds" to 60,
            "game.deathmatch-seconds" to 300,
            "game.result-display-seconds" to 10,
            "game.close-delay-seconds" to 5,
            "game.void-y" to -64.0,
            "game.max-health" to 20.0,
            "game.allow-monsters" to false,
            "game.win-points" to 3,
            "loot.min-items-per-chest" to 1,
            "loot.max-items-per-chest" to 10,
            "loot.container-materials" to DEFAULT_CONTAINER_MATERIALS,
            "loot.entries" to defaultLootEntries().map(::lootEntryToMap),
            "blocks.break-allowed" to DEFAULT_BREAK_MATERIALS,
            "blocks.place-allowed" to DEFAULT_PLACE_MATERIALS,
            "blocks.auto-prime-tnt" to true,
            "blocks.tnt-fuse-ticks" to 40,
            "blocks.explosion-block-damage" to false,
            "blocks.fire-spread" to false,
            "tracker.enabled" to false,
            "tracker.range" to 100.0,
            "maps.default.display-name" to "默认地图",
            "maps.default.template" to "hunger/default",
            "spectator.enabled" to true,
            "spectator.mode" to "managed"
        )
        defaults.forEach { (path, value) ->
            if (!config.contains(path)) config.set(path, value)
        }
    }

    private fun lootEntryToMap(entry: HungerLootEntry): Map<String, Any> {
        return linkedMapOf(
            "material" to entry.material.name,
            "min-amount" to entry.minAmount,
            "max-amount" to entry.maxAmount,
            "weight" to entry.weight
        )
    }

    private fun defaultLootEntries(): List<HungerLootEntry> {
        return listOf(
            HungerLootEntry(Material.WOODEN_SWORD, 1, 1, 5),
            HungerLootEntry(Material.STONE_SWORD, 1, 1, 3),
            HungerLootEntry(Material.IRON_SWORD, 1, 1, 1),
            HungerLootEntry(Material.BOW, 1, 1, 3),
            HungerLootEntry(Material.ARROW, 2, 8, 6),
            HungerLootEntry(Material.LEATHER_HELMET, 1, 1, 5),
            HungerLootEntry(Material.LEATHER_CHESTPLATE, 1, 1, 5),
            HungerLootEntry(Material.LEATHER_LEGGINGS, 1, 1, 5),
            HungerLootEntry(Material.LEATHER_BOOTS, 1, 1, 5),
            HungerLootEntry(Material.CHAINMAIL_HELMET, 1, 1, 3),
            HungerLootEntry(Material.CHAINMAIL_CHESTPLATE, 1, 1, 3),
            HungerLootEntry(Material.CHAINMAIL_LEGGINGS, 1, 1, 3),
            HungerLootEntry(Material.CHAINMAIL_BOOTS, 1, 1, 3),
            HungerLootEntry(Material.IRON_HELMET, 1, 1, 1),
            HungerLootEntry(Material.COOKED_BEEF, 1, 4, 8),
            HungerLootEntry(Material.BREAD, 1, 3, 8),
            HungerLootEntry(Material.GOLDEN_CARROT, 1, 2, 3),
            HungerLootEntry(Material.COBWEB, 1, 3, 4),
            HungerLootEntry(Material.TNT, 1, 2, 2),
            HungerLootEntry(Material.FLINT_AND_STEEL, 1, 1, 1),
            HungerLootEntry(Material.FISHING_ROD, 1, 1, 3),
            HungerLootEntry(Material.COMPASS, 1, 1, 2)
        )
    }

    companion object {
        private val DEFAULT_CONTAINER_MATERIALS = listOf("CHEST", "TRAPPED_CHEST", "BARREL")
        private val DEFAULT_BREAK_MATERIALS = listOf("COBWEB", "OAK_LEAVES", "SPRUCE_LEAVES", "BIRCH_LEAVES")
        private val DEFAULT_PLACE_MATERIALS = listOf("COBWEB", "TNT")
    }
}

private fun String.toMaterial(): Material? {
    return Material.matchMaterial(trim().removePrefix("minecraft:").uppercase().replace('-', '_'))
}

private fun Any?.toIntOr(fallback: Int): Int {
    return when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: fallback
        else -> fallback
    }
}
