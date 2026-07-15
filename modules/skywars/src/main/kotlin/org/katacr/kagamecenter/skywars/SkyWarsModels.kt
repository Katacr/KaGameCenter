package org.katacr.kagamecenter.skywars

import org.bukkit.Location
import org.bukkit.Material
import org.katacr.kaGameCenter.map.ManagedMapPoint
import org.katacr.kaGameCenter.map.ManagedNamedMapPoint
import org.katacr.kaGameCenter.selection.RegionSelection
import java.util.UUID

/** 保存 SkyWars 模块级玩法、箱子、职业和地图默认配置。 */
data class SkyWarsConfig(
    val enabled: Boolean,
    val displayName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val teamSize: Int,
    val countdownSeconds: Int,
    val pvpGraceSeconds: Int,
    val durationSeconds: Int,
    val refillAfterSeconds: Int,
    val resultDisplaySeconds: Int,
    val closeDelaySeconds: Int,
    val reconnectGraceSeconds: Int,
    val defaultVoidY: Double,
    val maxHealth: Double,
    val allowMonsterSpawns: Boolean,
    val winPoints: Int,
    val killAttributionSeconds: Int,
    val loot: SkyWarsLootConfig,
    val kits: LinkedHashMap<String, SkyWarsKit>,
    val blocks: SkyWarsBlockRules,
    val maps: Map<String, SkyWarsMapConfig>
) {
    fun firstMap(): SkyWarsMapConfig? = maps.values.firstOrNull()
}

/** 定义按价值预算生成箱子物资所需的等级和默认箱型。 */
data class SkyWarsLootConfig(
    val containerMaterials: Set<Material>,
    val levels: List<SkyWarsLootLevel>,
    val tiers: Map<String, SkyWarsChestTier>
)

/** 描述随机箱物资等级、出现权重和同等级候选物品。 */
data class SkyWarsLootLevel(
    val id: String,
    val itemValue: Int,
    val chance: Int,
    val items: List<SkyWarsItem>
)

/** 描述一种箱型的总价值预算与可选物资价值区间。 */
data class SkyWarsChestTier(
    val id: String,
    val totalValue: Int,
    val minItemValue: Int,
    val maxItemValue: Int,
    val refillMultiplier: Double
)

/** 描述可应用到职业或箱子中的基础物品。 */
data class SkyWarsItem(
    val material: Material,
    val amount: Int
)

/** 描述玩家开局可循环选择并应用的一套职业物品。 */
data class SkyWarsKit(
    val id: String,
    val displayName: String,
    val icon: Material,
    val permission: String?,
    val items: List<SkyWarsItem>,
    val armor: List<SkyWarsItem>
)

/** 定义一局中方块、爆炸、火焰和建筑区域规则。 */
data class SkyWarsBlockRules(
    val allowBreak: Boolean,
    val allowPlace: Boolean,
    val protectedMaterials: Set<Material>,
    val explosionBlockDamage: Boolean,
    val fireSpread: Boolean
)

/** 描述模块配置中的公共 SkyWars 地图模板。 */
data class SkyWarsMapConfig(
    val id: String,
    val displayName: String,
    val template: String
)

/** 保存托管游戏的岛屿出生点、箱子、区域和局部规则覆盖。 */
data class SkyWarsGameConfig(
    val mapId: String?,
    val lobby: ManagedMapPoint?,
    val spectatorSpawn: ManagedMapPoint?,
    val playRegion: RegionSelection?,
    val islandSpawns: List<ManagedNamedMapPoint>,
    val chests: List<SkyWarsChestPoint>,
    val voidY: Double?,
    val teamSize: Int?
)

/** 保存一个托管地图箱子的稳定 ID、坐标与物资等级。 */
data class SkyWarsChestPoint(
    val id: String,
    val point: ManagedMapPoint,
    val tier: String
)

/** 表示单局 SkyWars 的内部阶段。 */
enum class SkyWarsPhase(val languageKey: String) {
    WAITING("skywars.phase_waiting"),
    COUNTDOWN("skywars.phase_countdown"),
    GRACE("skywars.phase_grace"),
    RUNNING("skywars.phase_running"),
    RESULT("skywars.phase_result"),
    CLOSING("skywars.phase_closing")
}

/** 保存单名玩家在当前房间中的队伍、职业和淘汰状态。 */
data class SkyWarsPlayerState(
    var alive: Boolean = true,
    var participant: Boolean = true,
    var disconnected: Boolean = false,
    var teamId: String? = null,
    var selectedKitId: String? = null,
    var kills: Int = 0,
    var chestsOpened: Int = 0,
    var originalMaxHealth: Double = 20.0,
    var frozenLocation: Location? = null,
    var reconnectLocation: Location? = null,
    var lastAttackerId: UUID? = null,
    var lastAttackedAtMillis: Long = 0L,
    val openedChests: MutableSet<SkyWarsLocationKey> = linkedSetOf()
)

/** 以世界 UUID 和方块坐标标识单局中的物资箱。 */
data class SkyWarsLocationKey(
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int
) {
    companion object {
        fun from(location: Location): SkyWarsLocationKey? {
            val world = location.world ?: return null
            return SkyWarsLocationKey(world.uid, location.blockX, location.blockY, location.blockZ)
        }
    }
}
