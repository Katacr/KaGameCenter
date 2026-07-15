package org.katacr.kagamecenter.hunger

import org.bukkit.Location
import org.bukkit.Material
import org.katacr.kaGameCenter.map.ManagedMapPoint
import org.katacr.kaGameCenter.map.ManagedNamedMapPoint
import org.katacr.kaGameCenter.selection.RegionSelection
import java.util.UUID

/** 保存经典饥饿游戏模块级规则和默认值。 */
data class HungerConfig(
    val enabled: Boolean,
    val displayName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val countdownSeconds: Int,
    val protectionSeconds: Int,
    val durationSeconds: Int,
    val refillAfterSeconds: Int,
    val forceDeathmatchPlayers: Int,
    val forceDeathmatchDelaySeconds: Int,
    val deathmatchSeconds: Int,
    val resultDisplaySeconds: Int,
    val closeDelaySeconds: Int,
    val defaultVoidY: Double,
    val maxHealth: Double,
    val allowMonsterSpawns: Boolean,
    val winPoints: Int,
    val loot: HungerLootConfig,
    val blocks: HungerBlockRules,
    val tracker: HungerTrackerConfig,
    val maps: Map<String, HungerMapConfig>
) {
    fun firstMap(): HungerMapConfig? = maps.values.firstOrNull()
}

/** 定义共享补给箱的容器类型和加权物资表。 */
data class HungerLootConfig(
    val minItemsPerChest: Int,
    val maxItemsPerChest: Int,
    val containerMaterials: Set<Material>,
    val entries: List<HungerLootEntry>
)

/** 描述补给箱中的一个平面加权物品条目。 */
data class HungerLootEntry(
    val material: Material,
    val minAmount: Int,
    val maxAmount: Int,
    val weight: Int
)

/** 定义经典地图允许破坏、放置和爆炸的规则。 */
data class HungerBlockRules(
    val breakAllowed: Set<Material>,
    val placeAllowed: Set<Material>,
    val autoPrimeTnt: Boolean,
    val tntFuseTicks: Int,
    val explosionBlockDamage: Boolean,
    val fireSpread: Boolean
)

/** 定义右键指南针追踪最近贡品的可选规则。 */
data class HungerTrackerConfig(
    val enabled: Boolean,
    val range: Double
)

/** 描述模块配置中的一个公共地图模板。 */
data class HungerMapConfig(
    val id: String,
    val displayName: String,
    val template: String
)

/** 保存一个托管游戏的经典地图点位和区域。 */
data class HungerGameConfig(
    val mapId: String?,
    val lobby: HungerPoint?,
    val spectatorSpawn: HungerPoint?,
    val playRegion: RegionSelection?,
    val tributeSpawns: List<HungerNamedPoint>,
    val deathmatchSpawns: List<HungerNamedPoint>,
    val supplyChests: List<HungerNamedPoint>,
    val voidY: Double?
)

/** Hunger 领域代码对主插件通用命名点位的别名。 */
typealias HungerNamedPoint = ManagedNamedMapPoint

/** Hunger 领域代码对主插件通用可移植坐标的别名。 */
typealias HungerPoint = ManagedMapPoint

/** 表示经典饥饿游戏一局中的内部阶段。 */
enum class HungerPhase(val languageKey: String) {
    WAITING("hunger.phase_waiting"),
    COUNTDOWN("hunger.phase_countdown"),
    PROTECTION("hunger.phase_protection"),
    RUNNING("hunger.phase_running"),
    DEATHMATCH("hunger.phase_deathmatch"),
    RESULT("hunger.phase_result"),
    CLOSING("hunger.phase_closing")
}

/** 保存单名贡品在当前房间内的存活、进度和冻结状态。 */
data class HungerPlayerState(
    var alive: Boolean = true,
    var participant: Boolean = true,
    var kills: Int = 0,
    var chestsOpened: Int = 0,
    var originalMaxHealth: Double = 20.0,
    var frozenLocation: Location? = null,
    var eliminatedAtMillis: Long = 0L,
    val openedChests: MutableSet<HungerLocationKey> = linkedSetOf()
)

/** 以世界 UUID 和方块坐标标识当前房间中的容器或临时方块。 */
data class HungerLocationKey(
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int
) {
    companion object {
        fun from(location: Location): HungerLocationKey? {
            val world = location.world ?: return null
            return HungerLocationKey(world.uid, location.blockX, location.blockY, location.blockZ)
        }
    }
}
