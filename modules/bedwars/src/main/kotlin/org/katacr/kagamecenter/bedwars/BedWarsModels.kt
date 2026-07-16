package org.katacr.kagamecenter.bedwars

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.Color
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType
import org.katacr.kaGameCenter.spectator.SpectatorAction
import org.katacr.kaGameCenter.spectator.SpectatorMode
import java.util.Locale
import java.util.UUID

/** 保存 BedWars 模块级开关、人数和默认对局规则。 */
data class BedWarsConfig(
    val enabled: Boolean,
    val displayName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val autoStartMinPlayers: Int,
    val countdownSeconds: Int,
    val halfArenaCountdownSeconds: Int,
    val fullArenaCountdownSeconds: Int,
    val joinAllowedSound: BedWarsSoundRule,
    val joinDeniedSound: BedWarsSoundRule,
    val rejoinAllowedSound: BedWarsSoundRule,
    val rejoinDeniedSound: BedWarsSoundRule,
    val spectateAllowedSound: BedWarsSoundRule,
    val spectateDeniedSound: BedWarsSoundRule,
    val spectatorTargetClickSound: BedWarsSoundRule,
    val arenaSelectorOpenSound: BedWarsSoundRule,
    val statsMenuOpenSound: BedWarsSoundRule,
    val countdownSound: BedWarsSoundRule,
    val countdownFinalSounds: Map<Int, BedWarsSoundRule>,
    val gameStartSound: BedWarsSoundRule,
    val respawnSound: BedWarsSoundRule,
    val killSound: BedWarsSoundRule,
    val bedDestroyedSound: BedWarsSoundRule,
    val ownBedDestroyedSound: BedWarsSoundRule,
    val allBedsDestroyedSound: BedWarsSoundRule,
    val suddenDeathSound: BedWarsSoundRule,
    val gameEndSound: BedWarsSoundRule,
    val halloweenActive: Boolean,
    val allowedCommands: Set<String>,
    val defaultItemGroups: Map<String, List<BedWarsDefaultItem>>,
    val preGameItems: List<BedWarsCommandItem>,
    val spectatorItems: List<BedWarsSpectatorItem>,
    val spectatorEnabled: Boolean,
    val spectatorMode: SpectatorMode,
    val chatFormattingEnabled: Boolean,
    val shoutCooldownSeconds: Int,
    val allowHungerWaiting: Boolean,
    val allowHungerInGame: Boolean,
    val lobbyVoidTeleportEnabled: Boolean,
    val lobbyVoidHeight: Double,
    val durationSeconds: Int,
    val respawnSeconds: Int,
    val respawnInvulnerabilitySeconds: Int,
    val reconnectGraceSeconds: Int,
    val afkSeconds: Int,
    val resultDisplaySeconds: Int,
    val closeDelaySeconds: Int,
    val chatTopStatistic: BedWarsResultStatistic,
    val chatTopHideMissing: Boolean,
    val sidebarTopStatistic: BedWarsResultStatistic,
    val sidebarTopHideMissing: Boolean,
    val lobbySidebarEnabled: Boolean,
    val sidebarEnabled: Boolean,
    val sidebarTitleRefreshTicks: Int,
    val sidebarPlaceholdersRefreshTicks: Int,
    val sidebarServerIp: String,
    val sidebarPoweredBy: String,
    val tabHeaderFooterEnabled: Boolean,
    val tabHeaderFooterRefreshTicks: Int,
    val tabPlayerListWaitingEnabled: Boolean,
    val tabPlayerListCountdownEnabled: Boolean,
    val tabPlayerListRunningEnabled: Boolean,
    val tabPlayerListResultEnabled: Boolean,
    val tabPlayerListRefreshTicks: Int,
    val healthDisplayEnabled: Boolean,
    val healthDisplayInTab: Boolean,
    val healthAnimationRefreshTicks: Int,
    val defaultVoidY: Double,
    val worldBorderSize: Int,
    val winPoints: Int,
    val levelRules: BedWarsLevelRules,
    val moneyRewardRules: BedWarsMoneyRewardRules,
    val bedsDestroySeconds: Int,
    val suddenDeathSeconds: Int,
    val maxBuildY: Int,
    val islandRadius: Double,
    val disableEmptyTeamGenerators: Boolean,
    val disableEmptyTeamNpcs: Boolean,
    val useBedHologram: Boolean,
    val vanillaDeathDrops: Boolean,
    val markLeaveAsAbandon: Boolean,
    val blockRules: BedWarsBlockRules,
    val inventoryRules: BedWarsInventoryRules,
    val generatorRules: BedWarsGeneratorRules,
    val forgeRules: BedWarsForgeRules,
    val dragonRules: BedWarsDragonRules,
    val shop: BedWarsShopConfig,
    val specials: BedWarsSpecialRules,
    val maps: Map<String, BedWarsMapConfig>
)

/** 定义累计经验的等级曲线和六类参考奖励。 */
data class BedWarsLevelRules(
    val enabled: Boolean,
    val rankupCosts: List<Int>,
    val defaultRankupCost: Int,
    val progressBarSymbol: String,
    val progressBarUnlockedColor: String,
    val progressBarLockedColor: String,
    val progressBarFormat: String,
    val perMinuteExperience: Int,
    val perTeammateExperience: Int,
    val gameWinExperience: Int,
    val bedDestroyedExperience: Int,
    val regularKillExperience: Int,
    val finalKillExperience: Int
)

/** 定义通过可选 Vault Economy 发放的六类参考金币奖励。 */
data class BedWarsMoneyRewardRules(
    val perMinute: Int,
    val perTeammate: Int,
    val gameWin: Int,
    val bedDestroyed: Int,
    val regularKill: Int,
    val finalKill: Int
)

/** 保存由累计经验派生出的当前等级、级内经验和下一阈值。 */
data class BedWarsLevelProgress(
    val level: Int,
    val levelExperience: Int,
    val nextLevelExperience: Int
)

/** 描述参考默认物品组中的永久物品、数量和可选显示名。 */
data class BedWarsDefaultItem(
    val material: Material,
    val amount: Int,
    val displayName: String?
)

/** 描述等待大厅内一个可配置槽位、材质和玩家命令快捷物品。 */
data class BedWarsCommandItem(
    val id: String,
    val material: Material,
    val slot: Int,
    val enchanted: Boolean,
    val command: String
)

/** 描述 BedWars 托管观战快捷栏的显示内容和内建动作或玩家命令。 */
data class BedWarsSpectatorItem(
    val id: String,
    val material: Material,
    val slot: Int,
    val enchanted: Boolean,
    val displayName: String?,
    val lore: List<String>,
    val action: SpectatorAction?,
    val command: String?
)

/** 定义 TNT、火球、桥蛋、召唤物、魔法牛奶和弹出塔的运行参数。 */
data class BedWarsSpecialRules(
    val tntBarycenterAlterationY: Double,
    val tntStrengthReduction: Double,
    val tntYAxisReduction: Double,
    val tntDamageSelf: Double,
    val tntDamageTeammates: Double,
    val tntDamageOthers: Double,
    val tntSpoilCarriers: Boolean,
    val fireballSpeed: Double,
    val fireballYield: Float,
    val fireballMakeFire: Boolean,
    val fireballCooldownTicks: Int,
    val fireballHorizontalKnockback: Double,
    val fireballVerticalKnockback: Double,
    val fireballDamageSelf: Double,
    val fireballDamageTeammates: Double,
    val fireballDamageEnemies: Double,
    val bridgeBlockSound: BedWarsSoundRule,
    val bridgeStartDistance: Double,
    val bridgeMaxDistance: Double,
    val bridgeMaxVerticalDrop: Double,
    val enderPearlLandedSound: BedWarsSoundRule,
    val popupTowerBuildSound: BedWarsSoundRule,
    val bedBugDurationTicks: Int,
    val bedBugHealth: Double,
    val bedBugDamage: Double,
    val bedBugSpeed: Double,
    val dreamDefenderDurationTicks: Int,
    val dreamDefenderHealth: Double,
    val dreamDefenderDamage: Double,
    val dreamDefenderSpeed: Double,
    val speedPotionSeconds: Int,
    val jumpPotionSeconds: Int,
    val invisibilityPotionSeconds: Int,
    val removeInvisibilityOnDamage: Boolean,
    val magicMilkSeconds: Int,
    val towerRadius: Int,
    val towerWallHeight: Int,
    val towerBlocksPerTick: Int
)

/** 保存物品商店、队伍升级和陷阱的配置商品。 */
data class BedWarsShopConfig(
    val hologramsEnabled: Boolean,
    val trapCategoryIcon: Material,
    val trapCategoryIconAmount: Int,
    val trapCategoryIconEnchanted: Boolean,
    val upgradeSeparatorIcon: Material,
    val upgradeSeparatorIconAmount: Int,
    val upgradeSeparatorIconEnchanted: Boolean,
    val upgradeSeparatorPlayerCommands: List<String>,
    val upgradeSeparatorConsoleCommands: List<String>,
    val boughtSound: BedWarsSoundRule,
    val insufficientSound: BedWarsSoundRule,
    val autoEquipSound: BedWarsSoundRule,
    val trapTriggerSound: BedWarsSoundRule,
    val healPoolParticlesEnabled: Boolean,
    val healPoolParticlesTeamOnly: Boolean,
    val blindnessTrapDurationTicks: Int,
    val blindnessTrapAmplifier: Int,
    val counterOffensiveTrapDurationTicks: Int,
    val counterOffensiveTrapAmplifier: Int,
    val alarmTrapGlowingTicks: Int,
    val minerFatigueTrapDurationTicks: Int,
    val minerFatigueTrapAmplifier: Int,
    val pickaxeEfficiencyLevels: List<Int>,
    val pickaxeSharpnessLevels: List<Int>,
    val axeEfficiencyLevels: List<Int>,
    val items: List<BedWarsShopItem>,
    val upgrades: List<BedWarsUpgradeItem>,
    val quickBuyDefaults: List<String>,
    val defaultTrapRules: BedWarsTrapRules,
    val trapGroupRules: Map<String, BedWarsTrapRules>,
    val defaultUpgradeMenuRules: BedWarsUpgradeMenuRules,
    val upgradeMenuGroupRules: Map<String, BedWarsUpgradeMenuRules>,
    val trapCategoryRules: BedWarsTrapCategoryRules
)

/** 保存默认或 selector-group 覆盖后的陷阱容量、动态起价、增量和缺省货币。 */
data class BedWarsTrapRules(
    val queueLimit: Int,
    val startPrice: Int,
    val priceIncrement: Int,
    val currency: Material
)

/** 保存某个 selector-group 升级主菜单中的强化顺序和陷阱结构组件可见性。 */
data class BedWarsUpgradeMenuRules(
    val upgradeTypes: List<BedWarsUpgradeType>,
    val directTrapTypes: List<BedWarsUpgradeType>,
    val trapCategoryVisible: Boolean,
    val trapQueueVisible: Boolean,
    val separatorVisible: Boolean
)

/** 保存陷阱分类子页中的陷阱顺序及参考 separator-back 可见性。 */
data class BedWarsTrapCategoryRules(
    val trapTypes: List<BedWarsUpgradeType>,
    val backVisible: Boolean
)

/** 描述物品商店中的一次性商品、永久装备等级及支付后命令。 */
data class BedWarsShopItem(
    val id: String,
    val displayName: String,
    val displayLore: List<String>,
    val icon: Material,
    val iconAmount: Int,
    val iconEnchanted: Boolean,
    val iconPotionDisplay: String?,
    val iconPotionColor: Color?,
    val productType: BedWarsProductType,
    val category: String?,
    val weight: Int,
    val item: Material,
    val amount: Int,
    val itemName: String?,
    val unbreakable: Boolean,
    val enchantments: Map<Enchantment, Int>,
    val potionEffects: List<BedWarsPotionEffect>,
    val potionColor: Color?,
    val autoEquip: Boolean,
    val permanent: Boolean,
    val downgradable: Boolean,
    val deliverProduct: Boolean,
    val buyItems: List<BedWarsShopDelivery>,
    val commandsAsPlayer: List<String>,
    val commandsAsConsole: List<String>,
    val currency: Material,
    val price: Int,
    val tier: Int
)

/** 描述参考 ContentTier.buy-items 中一次购买按顺序发放的单个物品。 */
data class BedWarsShopDelivery(
    val material: Material,
    val amount: Int,
    val itemName: String?,
    val enchantments: Map<Enchantment, Int>,
    val potionEffects: List<BedWarsPotionEffect>,
    val potionColor: Color?,
    val autoEquip: Boolean,
    val unbreakable: Boolean
)

/** 描述配置药水的一项效果、持续 tick 和原始 Bukkit amplifier。 */
data class BedWarsPotionEffect(
    val type: PotionEffectType,
    val durationTicks: Int,
    val amplifier: Int
)

/** 描述使用钻石购买的队伍强化或一次性陷阱。 */
data class BedWarsUpgradeItem(
    val id: String,
    val displayName: String,
    val displayLore: List<String>,
    val icon: Material,
    val iconAmount: Int,
    val iconEnchanted: Boolean,
    val upgradeType: BedWarsUpgradeType,
    val currency: Material,
    val price: Int,
    val trapDynamicPrice: Boolean,
    val trapUsesConfiguredStartPrice: Boolean,
    val trapUsesConfiguredCurrency: Boolean,
    val tier: Int,
    val actions: List<BedWarsUpgradeAction>,
    val trapActions: List<BedWarsTrapAction>,
    val customAnnounce: Boolean,
    val trapSound: BedWarsSoundRule?
)

/** 标记一次参考 receive 中可按声明顺序执行的队伍升级动作。 */
sealed interface BedWarsUpgradeAction

/** 描述一次队伍升级购买后执行的参考命令模式和命令模板。 */
data class BedWarsUpgradeCommand(
    val type: BedWarsUpgradeCommandType,
    val command: String
) : BedWarsUpgradeAction

/** 描述向队伍武器、护甲或弓保存并应用的一项附魔动作。 */
data class BedWarsUpgradeEnchantAction(
    val enchantment: Enchantment,
    val amplifier: Int,
    val target: BedWarsUpgradeEnchantTarget
) : BedWarsUpgradeAction

/** 区分 receive 附魔应用到近战武器、护甲或弓。 */
enum class BedWarsUpgradeEnchantTarget {
    SWORD,
    ARMOR,
    BOW
}

/** 描述向全队或己方基地成员保存并应用的一项药水效果。 */
data class BedWarsUpgradeEffectAction(
    val effectType: PotionEffectType,
    val amplifier: Int,
    val durationTicks: Int,
    val target: BedWarsUpgradeEffectTarget
) : BedWarsUpgradeAction

/** 区分 receive 药水效果覆盖全队或仅覆盖己方基地。 */
enum class BedWarsUpgradeEffectTarget {
    TEAM,
    BASE
}

/** 描述队伍资源生成器的精确间隔、产量和地面上限。 */
data class BedWarsUpgradeGeneratorAction(
    val generatorType: BedWarsGeneratorType,
    val intervalTicks: Int,
    val amount: Int,
    val spawnLimit: Int
) : BedWarsUpgradeAction

/** 描述殊死决战时该队伍使用的精确末影龙数量。 */
data class BedWarsUpgradeDragonAction(
    val amount: Int
) : BedWarsUpgradeAction

/** 标记敌人进入基地并消费队首陷阱时执行的参考动作。 */
sealed interface BedWarsTrapAction

/** 描述陷阱向敌人、守方全队或基地内守方施加的药水效果。 */
data class BedWarsTrapEffectAction(
    val effectType: PotionEffectType,
    val amplifier: Int,
    val durationTicks: Int,
    val target: BedWarsTrapEffectTarget
) : BedWarsTrapAction

/** 区分陷阱药水效果的敌人、守方全队和守方基地目标。 */
enum class BedWarsTrapEffectTarget {
    ENEMY,
    TEAM,
    BASE
}

/** 描述陷阱触发时从入侵者移除的一项药水效果。 */
data class BedWarsTrapRemoveEffectAction(
    val effectType: PotionEffectType
) : BedWarsTrapAction

/** 描述陷阱触发时从入侵者指定装备类别移除的一项附魔。 */
data class BedWarsTrapDisenchantAction(
    val enchantment: Enchantment,
    val target: BedWarsUpgradeEnchantTarget
) : BedWarsTrapAction

/** 保存陷阱商品身份、固定回退类型、反馈配置和触发期动作快照。 */
data class BedWarsQueuedTrap(
    val productId: String,
    val upgradeType: BedWarsUpgradeType,
    val actions: List<BedWarsTrapAction>,
    val customAnnounce: Boolean,
    val sound: BedWarsSoundRule?
)

/** 区分单次控制台和逐队员控制台或玩家命令。 */
enum class BedWarsUpgradeCommandType {
    ONCE_AS_CONSOLE,
    FOREACH_MEMBER_AS_CONSOLE,
    FOREACH_MEMBER_AS_PLAYER;

    companion object {
        /** 兼容参考配置中的连字符或下划线命令模式。 */
        fun parse(value: String): BedWarsUpgradeCommandType? {
            val normalized = value.trim().uppercase().replace('-', '_')
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/** 区分一次性物品和死亡后恢复的永久装备类型。 */
enum class BedWarsProductType {
    ITEM,
    ARMOR,
    PICKAXE,
    AXE,
    SHEARS,
    POTION,
    SPECIAL;

    companion object {
        /** 从模块配置安全解析商品类型。 */
        fun parse(value: String?): BedWarsProductType? = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        }
    }
}

/** 定义物品商店和队伍升级商店两类界面。 */
enum class BedWarsShopKind {
    ITEM,
    UPGRADE;

    companion object {
        /** 从菜单上下文安全解析商店类型。 */
        fun parse(value: String?): BedWarsShopKind? = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        }
    }
}

/** 定义队伍升级及进入敌方基地后触发的陷阱。 */
enum class BedWarsUpgradeType {
    SHARPNESS,
    PROTECTION,
    HASTE,
    FORGE,
    DRAGON_BUFF,
    HEAL_POOL,
    TRAP_BLINDNESS,
    TRAP_COUNTER_OFFENSIVE,
    TRAP_ALARM,
    TRAP_MINER_FATIGUE;

    val trap: Boolean get() = name.startsWith("TRAP_")

    companion object {
        /** 从模块配置安全解析升级类型。 */
        fun parse(value: String?): BedWarsUpgradeType? = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        }
    }
}

/** 定义玩家建筑、自动点燃 TNT 和关键点保护半径。 */
data class BedWarsBlockRules(
    val placeAllowed: Set<Material>,
    val breakableMapBlocks: Set<Material>,
    val allowFireExtinguish: Boolean,
    val autoPrimeTnt: Boolean,
    val tntFuseTicks: Int,
    val blastProofGlassBlocksRays: Boolean,
    val spawnProtectionRadius: Double,
    val shopProtectionRadius: Double,
    val generatorProtectionRadius: Double,
    val teamChestRadius: Double
)

/** 定义 BedWars 对局中默认禁用的制作与工作站入口。 */
data class BedWarsInventoryRules(
    val disableCraftingTable: Boolean,
    val disableEnchantingTable: Boolean,
    val disableFurnace: Boolean,
    val disableBrewingStand: Boolean,
    val disableAnvil: Boolean
)

/** 定义各资源的生成数量、堆积上限和升级阶段。 */
data class BedWarsGeneratorRules(
    val hologramsEnabled: Boolean,
    val rotateHologramItems: Boolean,
    val stackItems: Boolean,
    val teamSplitEnabled: Boolean,
    val teamSplitRadius: Double,
    val diamondUpgradeSound: BedWarsSoundRule,
    val emeraldUpgradeSound: BedWarsSoundRule,
    val ironAmount: Int,
    val ironSpawnLimit: Int,
    val goldAmount: Int,
    val goldSpawnLimit: Int,
    val diamondTiers: List<BedWarsGeneratorTier>,
    val emeraldTiers: List<BedWarsGeneratorTier>
) {
    /** 返回指定公共资源在当前游戏秒数生效的最高阶段。 */
    fun tier(type: BedWarsGeneratorType, elapsedSeconds: Int): BedWarsGeneratorTier? {
        val tiers = when (type) {
            BedWarsGeneratorType.DIAMOND -> diamondTiers
            BedWarsGeneratorType.EMERALD -> emeraldTiers
            else -> return null
        }
        return tiers.lastOrNull { elapsedSeconds >= it.startSeconds } ?: tiers.firstOrNull()
    }
}

/** 定义一个可关闭并可调音量、音高的 BedWars 事件音效。 */
data class BedWarsSoundRule(
    val sound: Sound?,
    val volume: Float,
    val pitch: Float
)

/** 定义队伍锻炉各阶的生成加速与绿宝石产出。 */
data class BedWarsForgeRules(
    val speedMultipliers: List<Double>,
    val emeraldIntervalTicks: Int,
    val tier3EmeraldAmount: Int,
    val tier4EmeraldAmount: Int,
    val emeraldSpawnLimit: Int
) {
    /** 返回指定锻炉等级的生成间隔倍率。 */
    fun speedMultiplier(tier: Int): Double = speedMultipliers.getOrElse((tier - 1).coerceAtLeast(0)) {
        speedMultipliers.lastOrNull() ?: 1.0
    }
}

/** 定义 Sudden Death 阶段末影龙的数量、属性和追击参数。 */
data class BedWarsDragonRules(
    val baseDragons: Int,
    val buffExtraDragons: Int,
    val spawnHeight: Double,
    val health: Double,
    val damage: Double,
    val speed: Double,
    val attackRadius: Double
)

/** 描述公共资源生成器的一个时间升级阶段。 */
data class BedWarsGeneratorTier(
    val tier: Int,
    val startSeconds: Int,
    val intervalTicks: Int,
    val amount: Int,
    val spawnLimit: Int
)

/** 描述一个可供 Sidebar 与公共事件共同选择的主时间线条目。 */
data class BedWarsTimelineEntry(
    val id: String,
    val languageKey: String,
    val startSeconds: Int
)

/** 描述模块配置中的一个公共地图模板。 */
data class BedWarsMapConfig(
    val id: String,
    val displayName: String,
    val template: String
)

/** 参考竞技场默认使用的原版游戏规则列表。 */
internal val BEDWARS_DEFAULT_GAME_RULES = listOf(
    "doDaylightCycle:false",
    "announceAdvancements:false",
    "doInsomnia:false",
    "doImmediateRespawn:true",
    "doWeatherCycle:false",
    "doFireTick:false"
)

/** 保存托管 BedWars 游戏的大厅、游戏规则、队伍和资源点。 */
data class BedWarsGameConfig(
    val lobby: BedWarsPoint?,
    val spectatorSpawn: BedWarsPoint?,
    val voidY: Double?,
    val maxBuildY: Int?,
    val worldBorderSize: Int?,
    val allowSpectate: Boolean,
    val allowMapBreak: Boolean,
    val islandRadius: Double,
    val disableEmptyTeamGenerators: Boolean,
    val disableEmptyTeamNpcs: Boolean,
    val vanillaDeathDrops: Boolean,
    val useBedHologram: Boolean,
    val showEliminatedAtGameEnd: Boolean,
    val teleportEliminatedAtGameEnd: Boolean,
    val chatTopStatistic: BedWarsResultStatistic?,
    val chatTopHideMissing: Boolean?,
    val sidebarTopStatistic: BedWarsResultStatistic?,
    val sidebarTopHideMissing: Boolean?,
    val itemGroup: String,
    val gameRules: List<String>,
    val spawnProtectionRadius: Double?,
    val shopProtectionRadius: Double?,
    val upgradeShopProtectionRadius: Double?,
    val generatorProtectionRadius: Double?,
    val teams: List<BedWarsTeamConfig>,
    val generators: List<BedWarsGeneratorConfig>
) {
    /** 返回阻止对局启动的地图配置问题。 */
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (lobby == null) errors += "lobby"
        if (spectatorSpawn == null) errors += "spectator-spawn"
        if (teams.size < 2) errors += "teams"
        teams.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys.forEach { id ->
            errors += "teams.$id.duplicate"
        }
        if (generators.none { it.type == BedWarsGeneratorType.DIAMOND }) errors += "generators.diamond"
        if (generators.none { it.type == BedWarsGeneratorType.EMERALD }) errors += "generators.emerald"
        teams.forEach { team ->
            if (team.spawn == null) errors += "teams.${team.id}.spawn"
            if (team.bed == null) errors += "teams.${team.id}.bed"
            if (team.shop == null) errors += "teams.${team.id}.shop"
            if (team.upgradeShop == null) errors += "teams.${team.id}.upgrade-shop"
            if (team.generators.none { it.type == BedWarsGeneratorType.IRON }) errors += "teams.${team.id}.generators.iron"
            if (team.generators.none { it.type == BedWarsGeneratorType.GOLD }) errors += "teams.${team.id}.generators.gold"
        }
        return errors.distinct()
    }

    /** 返回所有已配置点位相对实际运行世界高度和边界的问题。 */
    fun worldValidationErrors(world: World): List<String> {
        val points = buildList {
            lobby?.let { add("lobby" to it) }
            spectatorSpawn?.let { add("spectator-spawn" to it) }
            teams.forEach { team ->
                team.spawn?.let { add("teams.${team.id}.spawn" to it) }
                team.bed?.let { add("teams.${team.id}.bed" to it) }
                team.killDrops?.let { add("teams.${team.id}.kill-drops" to it) }
                team.shop?.let { add("teams.${team.id}.shop" to it) }
                team.upgradeShop?.let { add("teams.${team.id}.upgrade-shop" to it) }
                team.generators.forEach { generator ->
                    add("teams.${team.id}.generators.${generator.id}" to generator.point)
                }
            }
            generators.forEach { generator -> add("generators.${generator.id}" to generator.point) }
        }
        val errors = mutableListOf<String>()
        points.forEach { (path, point) ->
            if (!point.isValidCoordinate()) {
                errors += "$path.coordinates"
                return@forEach
            }
            if (point.y < world.minHeight || point.y >= world.maxHeight) errors += "$path.height"
            if (!world.worldBorder.isInside(point.toLocation(world))) errors += "$path.world-border"
        }
        return errors.distinct()
    }
}

/** 描述一个队伍的颜色、容量和岛屿关键点。 */
data class BedWarsTeamConfig(
    val id: String,
    val displayName: String,
    val color: BedWarsTeamColor,
    val maxPlayers: Int,
    val spawn: BedWarsPoint?,
    val bed: BedWarsPoint?,
    val killDrops: BedWarsPoint?,
    val shop: BedWarsPoint?,
    val upgradeShop: BedWarsPoint?,
    val generators: List<BedWarsGeneratorConfig>
)

/** 描述队伍内或公共岛屿上的一个资源生成点。 */
data class BedWarsGeneratorConfig(
    val id: String,
    val type: BedWarsGeneratorType,
    val point: BedWarsPoint,
    val intervalTicks: Int
)

/** 定义 BedWars 使用的四类标准资源。 */
enum class BedWarsGeneratorType(val material: Material) {
    IRON(Material.IRON_INGOT),
    GOLD(Material.GOLD_INGOT),
    DIAMOND(Material.DIAMOND),
    EMERALD(Material.EMERALD);

    companion object {
        /** 从配置字符串安全解析资源类型。 */
        fun parse(value: String?): BedWarsGeneratorType? = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        }
    }
}

/** 定义可用于队伍名牌和装备染色的标准队色。 */
enum class BedWarsTeamColor(
    val textColor: NamedTextColor,
    val armorColor: Color,
    val wool: Material,
    private val materialColor: String
) {
    RED(NamedTextColor.RED, Color.RED, Material.RED_WOOL, "RED"),
    BLUE(NamedTextColor.BLUE, Color.BLUE, Material.BLUE_WOOL, "BLUE"),
    GREEN(NamedTextColor.GREEN, Color.LIME, Material.LIME_WOOL, "LIME"),
    YELLOW(NamedTextColor.YELLOW, Color.YELLOW, Material.YELLOW_WOOL, "YELLOW"),
    AQUA(NamedTextColor.AQUA, Color.AQUA, Material.LIGHT_BLUE_WOOL, "LIGHT_BLUE"),
    WHITE(NamedTextColor.WHITE, Color.WHITE, Material.WHITE_WOOL, "WHITE"),
    PINK(NamedTextColor.LIGHT_PURPLE, Color.FUCHSIA, Material.PINK_WOOL, "PINK"),
    GRAY(NamedTextColor.GRAY, Color.GRAY, Material.LIGHT_GRAY_WOOL, "LIGHT_GRAY");

    /** 按参考 colourItem 把床、玻璃板、玻璃、陶瓦和羊毛转换为当前队伍的现代材质颜色。 */
    fun colorize(material: Material): Material {
        val suffix = when {
            material.name.endsWith("_BED") -> "BED"
            material == Material.GLASS_PANE || material.name.endsWith("_STAINED_GLASS_PANE") -> "STAINED_GLASS_PANE"
            material == Material.GLASS || material.name.endsWith("_STAINED_GLASS") -> "STAINED_GLASS"
            material.name.endsWith("_TERRACOTTA") -> "TERRACOTTA"
            material.name.endsWith("_WOOL") -> "WOOL"
            else -> return material
        }
        return Material.matchMaterial("${materialColor}_$suffix") ?: material
    }

    companion object {
        /** 从配置字符串解析队色，非法值回退为白色。 */
        fun parse(value: String?): BedWarsTeamColor = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: WHITE
    }
}

/** 保存不绑定运行世界名称的可移植坐标。 */
data class BedWarsPoint(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    /** 判断坐标是否为有限数且位于 Minecraft 可寻址的水平范围内。 */
    fun isValidCoordinate(): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite() && yaw.isFinite() && pitch.isFinite() &&
            x in -MAX_HORIZONTAL_COORDINATE..MAX_HORIZONTAL_COORDINATE &&
            z in -MAX_HORIZONTAL_COORDINATE..MAX_HORIZONTAL_COORDINATE
    }

    /** 将可移植坐标绑定到指定运行世界。 */
    fun toLocation(world: World): Location = Location(world, x, y, z, yaw, pitch)

    /** 将坐标写入指定 YAML 节点。 */
    fun writeTo(section: ConfigurationSection) {
        section.set("x", x)
        section.set("y", y)
        section.set("z", z)
        section.set("yaw", yaw.toDouble())
        section.set("pitch", pitch.toDouble())
    }

    companion object {
        /** 从 Bukkit 位置创建不含世界名的坐标。 */
        fun from(location: Location): BedWarsPoint = BedWarsPoint(
            location.x,
            location.y,
            location.z,
            location.yaw,
            location.pitch
        )

        /** 从 YAML 节点读取坐标，字段不完整时返回空。 */
        fun read(section: ConfigurationSection?): BedWarsPoint? {
            if (section == null || !section.contains("x") || !section.contains("y") || !section.contains("z")) return null
            return BedWarsPoint(
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                section.getDouble("yaw").toFloat(),
                section.getDouble("pitch").toFloat()
            ).takeIf(BedWarsPoint::isValidCoordinate)
        }

        private const val MAX_HORIZONTAL_COORDINATE = 30_000_000.0
    }
}

/** 表示 BedWars 房间内部生命周期阶段。 */
enum class BedWarsPhase {
    WAITING,
    COUNTDOWN,
    RUNNING,
    RESULT,
    CLOSING
}

/** 定义结算榜单可选择的单局统计项及其本地化名称。 */
enum class BedWarsResultStatistic(val languageKey: String) {
    KILLS("bedwars.result_stat_kills"),
    KILLS_FINAL("bedwars.result_stat_final_kills"),
    DEATHS("bedwars.result_stat_deaths"),
    DEATHS_FINAL("bedwars.result_stat_final_deaths"),
    BEDS_DESTROYED("bedwars.result_stat_beds_destroyed");

    /** 从玩家单局状态读取当前统计项的数值。 */
    fun valueOf(state: BedWarsPlayerState): Int = when (this) {
        KILLS -> state.kills
        KILLS_FINAL -> state.finalKills
        DEATHS -> state.deaths
        DEATHS_FINAL -> state.finalDeaths
        BEDS_DESTROYED -> state.bedsBroken
    }

    companion object {
        /** 宽松解析参考配置枚举，非法值安全回退到总击杀。 */
        fun parse(value: String?): BedWarsResultStatistic {
            val normalized = value?.trim()?.uppercase(Locale.ROOT)?.replace('-', '_')
            return entries.firstOrNull { it.name == normalized } ?: KILLS
        }
    }
}

/** 保存一个参赛玩家在当前 BedWars 房间中的队伍和生存状态。 */
data class BedWarsPlayerState(
    var teamId: String,
    var participant: Boolean = true,
    var eliminated: Boolean = false,
    var respawning: Boolean = false,
    var firstSpawned: Boolean = false,
    var respawnTicks: Int = 0,
    var respawnProtectionUntilTick: Int = 0,
    var kills: Int = 0,
    var deaths: Int = 0,
    var bedsBroken: Int = 0,
    var finalKills: Int = 0,
    var finalDeaths: Int = 0,
    var armorTier: Int = 0,
    var pickaxeTier: Int = 0,
    var axeTier: Int = 0,
    var shears: Boolean = false,
    var disconnected: Boolean = false,
    var trapImmuneUntilMillis: Long = 0L,
    val categoryWeights: MutableMap<String, Int> = linkedMapOf(),
    val permanentProductIds: MutableSet<String> = linkedSetOf(),
    val enteredEnemyBases: MutableSet<String> = linkedSetOf()
)

/** 保存玩家最近一次受到敌方攻击的来源和对局 tick。 */
data class BedWarsLastHitState(
    val attackerId: UUID,
    val gameTick: Int
)

/** 保存玩家最近一次受到召唤物攻击的队伍、类型和对局 tick。 */
data class BedWarsSpecialMobHitState(
    val teamId: String,
    val specialId: String,
    val gameTick: Int
)

/** 保存一个队伍在当前对局中的床存活状态。 */
data class BedWarsTeamState(
    val config: BedWarsTeamConfig,
    var bedAlive: Boolean = true,
    val upgrades: MutableMap<BedWarsUpgradeType, Int> = linkedMapOf(),
    val traps: ArrayDeque<BedWarsQueuedTrap> = ArrayDeque(),
    val actionEnchantments: MutableMap<BedWarsUpgradeEnchantTarget, MutableMap<Enchantment, Int>> = linkedMapOf(),
    val teamEffects: MutableMap<PotionEffectType, BedWarsUpgradeEffectAction> = linkedMapOf(),
    val baseEffects: MutableMap<PotionEffectType, BedWarsUpgradeEffectAction> = linkedMapOf(),
    val generatorEdits: MutableMap<BedWarsGeneratorType, BedWarsUpgradeGeneratorAction> = linkedMapOf(),
    var dragonCount: Int? = null,
    var forgeEmeraldTicks: Int = 0
)

/** 保存一个资源点在当前房间中的倒计时和升级状态。 */
data class BedWarsGeneratorState(
    val config: BedWarsGeneratorConfig,
    val teamId: String?,
    var tier: Int = 1,
    var ticksUntilSpawn: Int = config.intervalTicks,
    var hologram: BedWarsGeneratorHologramState? = null
)

/** 保存公共资源点三行提示和旋转资源标识的实体引用。 */
data class BedWarsGeneratorHologramState(
    val tierEntityId: UUID,
    val typeEntityId: UUID,
    val timerEntityId: UUID,
    val itemEntityId: UUID,
    var rotationDegrees: Double = 0.0
)

/** 保存一枚桥蛋的玩家、队伍和起点。 */
data class BedWarsBridgeState(
    val projectileId: UUID,
    val ownerId: UUID,
    val teamId: String
)

/** 保存床虫或梦境守卫的归属和到期 tick。 */
data class BedWarsSpecialMobState(
    val ownerId: UUID,
    val teamId: String,
    val specialId: String,
    val expiresAtTick: Int,
    val damage: Double
)

/** 保存 Sudden Death 末影龙的队伍归属和巡航中心。 */
data class BedWarsDragonState(
    val teamId: String,
    val center: Location
)

/** 描述弹出塔队列中一个相对方块及可选梯子朝向。 */
data class BedWarsTowerPlacement(
    val offsetX: Int,
    val offsetY: Int,
    val offsetZ: Int,
    val ladderFacing: BlockFace? = null
)

/** 保存一个正在逐 tick 展开的弹出塔。 */
data class BedWarsTowerState(
    val ownerId: UUID,
    val teamId: String,
    val origin: Location,
    val placements: ArrayDeque<BedWarsTowerPlacement>
)

/** 以运行世界和方块坐标标识床的两个方块。 */
data class BedWarsBlockKey(
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int
) {
    companion object {
        /** 从 Bukkit 位置创建房间内方块键。 */
        fun from(location: Location): BedWarsBlockKey? {
            val world = location.world ?: return null
            return BedWarsBlockKey(world.uid, location.blockX, location.blockY, location.blockZ)
        }
    }
}

internal fun String.normalizedBedWarsId(): String = lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "-")
