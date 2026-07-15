package org.katacr.kagamecenter.skywars

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.map.ManagedMapPoint
import org.katacr.kaGameCenter.map.ManagedMapPointService
import org.katacr.kaGameCenter.selection.RegionSelection
import java.io.File

/** 读取 SkyWars 模块配置并维护托管地图的玩法专属字段。 */
class SkyWarsConfigService(
    private val dataFolder: File,
    private val mapPointService: ManagedMapPointService
) {
    private val file = File(dataFolder, "config.yml")
    private var config = YamlConfiguration()

    /** 从磁盘重新加载配置并补齐当前版本缺失的默认字段。 */
    fun reload(): SkyWarsConfig {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        if (!file.exists()) file.createNewFile()
        config = YamlConfiguration.loadConfiguration(file)
        ensureDefaults()
        config.save(file)
        return current()
    }

    /** 将当前 YAML 转换为运行时不可变配置快照。 */
    fun current(): SkyWarsConfig {
        val maps = linkedMapOf<String, SkyWarsMapConfig>()
        config.getConfigurationSection("maps")?.getKeys(false)?.forEach { mapId ->
            val section = config.getConfigurationSection("maps.$mapId") ?: return@forEach
            maps[mapId] = SkyWarsMapConfig(
                id = mapId,
                displayName = section.getString("display-name", mapId) ?: mapId,
                template = section.getString("template", "skywars/$mapId") ?: "skywars/$mapId"
            )
        }
        return SkyWarsConfig(
            enabled = config.getBoolean("enabled", true),
            displayName = config.getString("game.display-name", "空岛战争") ?: "空岛战争",
            minPlayers = config.getInt("game.min-players", 2).coerceAtLeast(2),
            maxPlayers = config.getInt("game.max-players", 12).coerceAtLeast(2),
            teamSize = config.getInt("game.team-size", 1).coerceIn(1, 8),
            countdownSeconds = config.getInt("game.countdown-seconds", 10).coerceIn(1, 300),
            pvpGraceSeconds = config.getInt("game.pvp-grace-seconds", 5).coerceIn(0, 120),
            durationSeconds = config.getInt("game.duration-seconds", 900).coerceIn(30, 7200),
            refillAfterSeconds = config.getInt("game.refill-after-seconds", 300).coerceIn(0, 7200),
            resultDisplaySeconds = config.getInt("game.result-display-seconds", 10).coerceIn(3, 120),
            closeDelaySeconds = config.getInt("game.close-delay-seconds", 5).coerceIn(1, 120),
            reconnectGraceSeconds = config.getInt("game.reconnect-grace-seconds", 20).coerceIn(0, 300),
            defaultVoidY = config.getDouble("game.void-y", -64.0),
            maxHealth = config.getDouble("game.max-health", 20.0).coerceIn(1.0, 2048.0),
            allowMonsterSpawns = config.getBoolean("game.allow-monsters", false),
            winPoints = config.getInt("game.win-points", 7).coerceIn(0, 1_000_000),
            killAttributionSeconds = config.getInt("game.kill-attribution-seconds", 10).coerceIn(0, 60),
            loot = readLootConfig(),
            kits = readKits(),
            blocks = SkyWarsBlockRules(
                allowBreak = config.getBoolean("blocks.allow-break", true),
                allowPlace = config.getBoolean("blocks.allow-place", true),
                protectedMaterials = readMaterials("blocks.protected-materials", DEFAULT_PROTECTED_MATERIALS),
                explosionBlockDamage = config.getBoolean("blocks.explosion-block-damage", true),
                fireSpread = config.getBoolean("blocks.fire-spread", false)
            ),
            maps = maps
        )
    }

    /** 按模板路径查找模块地图元数据。 */
    fun findMapByTemplate(template: String?): SkyWarsMapConfig? {
        return current().maps.values.firstOrNull { it.template == template || it.id == template?.substringAfterLast('/') }
    }

    /** 读取一个托管游戏中的 SkyWars 地图配置。 */
    fun readManagedGame(game: ManagedGameConfig): SkyWarsGameConfig {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        val section = managed.getConfigurationSection("skywars") ?: managed.createSection("skywars")
        return SkyWarsGameConfig(
            mapId = section.getString("map-id"),
            lobby = mapPointService.read(section.getConfigurationSection("lobby")),
            spectatorSpawn = mapPointService.read(section.getConfigurationSection("spectator-spawn")),
            playRegion = RegionSelection.read(section.getConfigurationSection("play-region")),
            islandSpawns = mapPointService.readNamedPoints(section, "island-spawns"),
            chests = readChestPoints(section),
            voidY = if (section.contains("void-y")) section.getDouble("void-y") else null,
            teamSize = if (section.contains("team-size")) section.getInt("team-size").coerceIn(1, 8) else null
        )
    }

    fun saveManagedLobby(game: ManagedGameConfig, point: ManagedMapPoint) = saveManaged(game) {
        mapPointService.replace(it, "skywars.lobby", point)
    }

    fun saveManagedSpectatorSpawn(game: ManagedGameConfig, point: ManagedMapPoint) = saveManaged(game) {
        mapPointService.replace(it, "skywars.spectator-spawn", point)
    }

    fun saveManagedPlayRegion(game: ManagedGameConfig, region: RegionSelection) = saveManaged(game) {
        it.set("skywars.play-region", null)
        mapPointService.portable(region).writeTo(it.createSection("skywars.play-region"))
    }

    fun saveManagedVoidY(game: ManagedGameConfig, voidY: Double) = saveManaged(game) {
        it.set("skywars.void-y", voidY)
    }

    fun saveManagedTeamSize(game: ManagedGameConfig, teamSize: Int) = saveManaged(game) {
        it.set("skywars.team-size", teamSize.coerceIn(1, 8))
    }

    fun addManagedIslandSpawn(game: ManagedGameConfig, id: String, point: ManagedMapPoint) = saveManaged(game) {
        mapPointService.upsertNamedPoint(it, "skywars.island-spawns", id, point)
    }

    /** 自动分配单调递增的岛屿编号并保存位置。 */
    fun addNextManagedIslandSpawn(game: ManagedGameConfig, point: ManagedMapPoint): String {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        val existingIds = mapPointService.readNamedPoints(
            managed.getConfigurationSection("skywars") ?: managed.createSection("skywars"),
            "island-spawns"
        ).map { it.id }
        val index = nextGeneratedIndex(managed, "skywars.next-island-index", "island", existingIds)
        val id = "island_$index"
        mapPointService.upsertNamedPoint(managed, "skywars.island-spawns", id, point)
        managed.set("skywars.next-island-index", index + 1)
        managed.save(game.file)
        return id
    }

    fun removeManagedIslandSpawn(game: ManagedGameConfig, id: String): Boolean {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        val removed = mapPointService.removeNamedPoint(managed, "skywars.island-spawns", id)
        if (removed) managed.save(game.file)
        return removed
    }

    /** 新增或替换托管地图中的箱子点位和物资等级。 */
    fun addManagedChest(game: ManagedGameConfig, id: String, point: ManagedMapPoint, tier: String) {
        saveManaged(game) { root ->
            val section = root.getConfigurationSection("skywars") ?: root.createSection("skywars")
            val chests = readChestPoints(section).toMutableList()
            val next = SkyWarsChestPoint(id, point, tier)
            val index = chests.indexOfFirst { it.id.equals(id, ignoreCase = true) }
            if (index >= 0) chests[index] = next else chests.add(next)
            section.set("chests", chests.map(::chestPointToMap))
        }
    }

    /** 自动分配单调递增的物资箱编号并保存方块位置。 */
    fun addNextManagedChest(game: ManagedGameConfig, point: ManagedMapPoint, tier: String): String {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        val section = managed.getConfigurationSection("skywars") ?: managed.createSection("skywars")
        val chests = readChestPoints(section).toMutableList()
        val index = nextGeneratedIndex(managed, "skywars.next-chest-index", "chest", chests.map { it.id })
        val id = "chest_$index"
        chests.add(SkyWarsChestPoint(id, point, tier))
        section.set("chests", chests.map(::chestPointToMap))
        managed.set("skywars.next-chest-index", index + 1)
        managed.save(game.file)
        return id
    }

    /** 按稳定 ID 删除托管地图箱子。 */
    fun removeManagedChest(game: ManagedGameConfig, id: String): Boolean {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        val section = managed.getConfigurationSection("skywars") ?: return false
        val chests = readChestPoints(section).toMutableList()
        val removed = chests.removeIf { it.id.equals(id, ignoreCase = true) }
        if (removed) {
            section.set("chests", chests.map(::chestPointToMap))
            managed.save(game.file)
        }
        return removed
    }

    private fun readLootConfig(): SkyWarsLootConfig {
        val levels = config.getConfigurationSection("loot.levels")?.getKeys(false).orEmpty().mapNotNull { levelId ->
            val section = config.getConfigurationSection("loot.levels.$levelId") ?: return@mapNotNull null
            val items = section.getMapList("items").mapNotNull(::readItem)
            if (items.isEmpty()) return@mapNotNull null
            SkyWarsLootLevel(
                id = levelId,
                itemValue = section.getInt("item-value", 1).coerceAtLeast(1),
                chance = section.getInt("chance", 1).coerceAtLeast(1),
                items = items
            )
        }.ifEmpty { defaultLootLevels() }
        val tiers = linkedMapOf<String, SkyWarsChestTier>()
        config.getConfigurationSection("loot.tiers")?.getKeys(false)?.forEach { tierId ->
            val section = config.getConfigurationSection("loot.tiers.$tierId") ?: return@forEach
            tiers[tierId] = SkyWarsChestTier(
                id = tierId,
                totalValue = section.getInt("total-value", 20).coerceAtLeast(1),
                minItemValue = section.getInt("min-item-value", 0).coerceAtLeast(0),
                maxItemValue = section.getInt("max-item-value", 100).coerceAtLeast(1),
                refillMultiplier = section.getDouble("refill-multiplier", 1.25).coerceIn(0.1, 10.0)
            )
        }
        if (tiers.isEmpty()) tiers.putAll(defaultTiers())
        return SkyWarsLootConfig(
            containerMaterials = readMaterials("loot.container-materials", DEFAULT_CONTAINER_MATERIALS),
            levels = levels,
            tiers = tiers
        )
    }

    private fun readKits(): LinkedHashMap<String, SkyWarsKit> {
        val kits = linkedMapOf<String, SkyWarsKit>()
        config.getConfigurationSection("kits")?.getKeys(false)?.forEach { kitId ->
            val section = config.getConfigurationSection("kits.$kitId") ?: return@forEach
            if (!section.getBoolean("enabled", true)) return@forEach
            kits[kitId] = SkyWarsKit(
                id = kitId,
                displayName = section.getString("display-name", kitId) ?: kitId,
                icon = section.getString("icon")?.toMaterial() ?: Material.CHEST,
                permission = section.getString("permission")?.trim()?.takeIf(String::isNotBlank),
                items = section.getMapList("items").mapNotNull(::readItem),
                armor = section.getMapList("armor").mapNotNull(::readItem)
            )
        }
        if (kits.isEmpty()) kits.putAll(defaultKits())
        return kits
    }

    private fun readChestPoints(section: ConfigurationSection): List<SkyWarsChestPoint> {
        return section.getMapList("chests").mapNotNull { values ->
            val id = values["id"]?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val pointValues = values["point"] as? Map<*, *> ?: return@mapNotNull null
            val point = mapPoint(pointValues) ?: return@mapNotNull null
            SkyWarsChestPoint(id, point, values["tier"]?.toString()?.trim()?.takeIf(String::isNotBlank) ?: "island")
        }
    }

    private fun readItem(values: Map<*, *>): SkyWarsItem? {
        val material = values["material"]?.toString()?.toMaterial() ?: values["type"]?.toString()?.toMaterial() ?: return null
        val amount = values["amount"].toIntOr(1).coerceIn(1, material.maxStackSize.coerceAtLeast(1))
        return SkyWarsItem(material, amount)
    }

    private fun mapPoint(values: Map<*, *>): ManagedMapPoint? {
        val x = values["x"].toDoubleOrNull() ?: return null
        val y = values["y"].toDoubleOrNull() ?: return null
        val z = values["z"].toDoubleOrNull() ?: return null
        return ManagedMapPoint(
            x,
            y,
            z,
            values["yaw"].toDoubleOrNull()?.toFloat() ?: 0f,
            values["pitch"].toDoubleOrNull()?.toFloat() ?: 0f
        )
    }

    private fun chestPointToMap(chest: SkyWarsChestPoint): Map<String, Any> {
        return linkedMapOf(
            "id" to chest.id,
            "tier" to chest.tier,
            "point" to linkedMapOf(
                "x" to chest.point.x,
                "y" to chest.point.y,
                "z" to chest.point.z,
                "yaw" to chest.point.yaw.toDouble(),
                "pitch" to chest.point.pitch.toDouble()
            )
        )
    }

    private fun readMaterials(path: String, fallback: List<String>): Set<Material> {
        return config.getStringList(path).ifEmpty { fallback }.mapNotNull(String::toMaterial).toSet()
    }

    private fun saveManaged(game: ManagedGameConfig, mutate: (YamlConfiguration) -> Unit) {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        mutate(managed)
        managed.save(game.file)
    }

    private fun nextGeneratedIndex(
        managed: YamlConfiguration,
        counterPath: String,
        prefix: String,
        existingIds: Collection<String>
    ): Int {
        val inferred = existingIds.mapNotNull { id ->
            id.takeIf { it.startsWith("${prefix}_", ignoreCase = true) }
                ?.substringAfterLast('_')
                ?.toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
        var index = maxOf(managed.getInt(counterPath, 1), inferred).coerceAtLeast(1)
        while (existingIds.any { it.equals("${prefix}_$index", ignoreCase = true) }) index++
        return index
    }

    private fun ensureDefaults() {
        val defaults = mapOf(
            "id" to "skywars",
            "name" to "空岛战争",
            "enabled" to true,
            "main" to "jar",
            "jar" to "../skywars.jar",
            "entrypoint" to "org.katacr.kagamecenter.skywars.SkyWarsModuleProvider",
            "game.display-name" to "空岛战争",
            "game.min-players" to 2,
            "game.max-players" to 12,
            "game.team-size" to 1,
            "game.countdown-seconds" to 10,
            "game.pvp-grace-seconds" to 5,
            "game.duration-seconds" to 900,
            "game.refill-after-seconds" to 300,
            "game.result-display-seconds" to 10,
            "game.close-delay-seconds" to 5,
            "game.reconnect-grace-seconds" to 20,
            "game.void-y" to -64.0,
            "game.max-health" to 20.0,
            "game.allow-monsters" to false,
            "game.win-points" to 7,
            "game.kill-attribution-seconds" to 10,
            "loot.container-materials" to DEFAULT_CONTAINER_MATERIALS,
            "blocks.allow-break" to true,
            "blocks.allow-place" to true,
            "blocks.protected-materials" to DEFAULT_PROTECTED_MATERIALS,
            "blocks.explosion-block-damage" to true,
            "blocks.fire-spread" to false,
            "maps.default.display-name" to "默认地图",
            "maps.default.template" to "skywars/default",
            "spectator.enabled" to true,
            "spectator.mode" to "managed"
        )
        defaults.forEach { (path, value) -> if (!config.contains(path)) config.set(path, value) }
        if (!config.contains("loot.levels")) writeLootDefaults()
        if (!config.contains("loot.tiers")) writeTierDefaults()
        if (!config.contains("kits")) writeKitDefaults()
    }

    private fun writeLootDefaults() {
        defaultLootLevels().forEach { level ->
            config.set("loot.levels.${level.id}.item-value", level.itemValue)
            config.set("loot.levels.${level.id}.chance", level.chance)
            config.set("loot.levels.${level.id}.items", level.items.map(::itemToMap))
        }
    }

    private fun writeTierDefaults() {
        defaultTiers().values.forEach { tier ->
            config.set("loot.tiers.${tier.id}.total-value", tier.totalValue)
            config.set("loot.tiers.${tier.id}.min-item-value", tier.minItemValue)
            config.set("loot.tiers.${tier.id}.max-item-value", tier.maxItemValue)
            config.set("loot.tiers.${tier.id}.refill-multiplier", tier.refillMultiplier)
        }
    }

    private fun writeKitDefaults() {
        defaultKits().values.forEach { kit ->
            config.set("kits.${kit.id}.enabled", true)
            config.set("kits.${kit.id}.display-name", kit.displayName)
            config.set("kits.${kit.id}.icon", kit.icon.name)
            config.set("kits.${kit.id}.permission", kit.permission ?: "")
            config.set("kits.${kit.id}.items", kit.items.map(::itemToMap))
            config.set("kits.${kit.id}.armor", kit.armor.map(::itemToMap))
        }
    }

    private fun itemToMap(item: SkyWarsItem): Map<String, Any> = linkedMapOf("material" to item.material.name, "amount" to item.amount)

    private fun defaultTiers(): LinkedHashMap<String, SkyWarsChestTier> = linkedMapOf(
        "island" to SkyWarsChestTier("island", 18, 0, 5, 1.25),
        "middle" to SkyWarsChestTier("middle", 28, 3, 10, 1.35)
    )

    private fun defaultKits(): LinkedHashMap<String, SkyWarsKit> = linkedMapOf(
        "default" to SkyWarsKit("default", "无职业", Material.CHEST, null, emptyList(), emptyList()),
        "builder" to SkyWarsKit("builder", "建筑师", Material.OAK_PLANKS, null, listOf(SkyWarsItem(Material.OAK_PLANKS, 16)), emptyList()),
        "archer" to SkyWarsKit("archer", "弓箭手", Material.BOW, null, listOf(SkyWarsItem(Material.BOW, 1), SkyWarsItem(Material.ARROW, 8)), emptyList())
    )

    private fun defaultLootLevels(): List<SkyWarsLootLevel> = listOf(
        SkyWarsLootLevel("common", 1, 45, listOf(
            SkyWarsItem(Material.WOODEN_SWORD, 1), SkyWarsItem(Material.STONE_PICKAXE, 1),
            SkyWarsItem(Material.OAK_PLANKS, 16), SkyWarsItem(Material.COBBLESTONE, 16),
            SkyWarsItem(Material.COOKED_BEEF, 4), SkyWarsItem(Material.ARROW, 8)
        )),
        SkyWarsLootLevel("uncommon", 3, 35, listOf(
            SkyWarsItem(Material.STONE_SWORD, 1), SkyWarsItem(Material.BOW, 1),
            SkyWarsItem(Material.IRON_PICKAXE, 1), SkyWarsItem(Material.CHAINMAIL_CHESTPLATE, 1),
            SkyWarsItem(Material.ENDER_PEARL, 1), SkyWarsItem(Material.GOLDEN_APPLE, 1)
        )),
        SkyWarsLootLevel("rare", 5, 15, listOf(
            SkyWarsItem(Material.IRON_SWORD, 1), SkyWarsItem(Material.IRON_CHESTPLATE, 1),
            SkyWarsItem(Material.DIAMOND_BOOTS, 1), SkyWarsItem(Material.ENDER_PEARL, 2),
            SkyWarsItem(Material.TNT, 2)
        )),
        SkyWarsLootLevel("legendary", 10, 5, listOf(
            SkyWarsItem(Material.DIAMOND_SWORD, 1), SkyWarsItem(Material.DIAMOND_CHESTPLATE, 1),
            SkyWarsItem(Material.ENCHANTED_GOLDEN_APPLE, 1)
        ))
    )

    companion object {
        private val DEFAULT_CONTAINER_MATERIALS = listOf("CHEST", "TRAPPED_CHEST", "BARREL")
        private val DEFAULT_PROTECTED_MATERIALS = listOf("BEDROCK", "BARRIER", "END_PORTAL_FRAME", "CHEST", "TRAPPED_CHEST", "BARREL")
    }
}

private fun String.toMaterial(): Material? = Material.matchMaterial(trim().removePrefix("minecraft:").uppercase().replace('-', '_'))

private fun Any?.toIntOr(fallback: Int): Int = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull() ?: fallback
    else -> fallback
}

private fun Any?.toDoubleOrNull(): Double? = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else -> null
}
