package org.katacr.kagamecenter.bedwars

import io.papermc.paper.event.player.PlayerSwapWithEquipmentSlotEvent
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockCanBuildEvent
import org.bukkit.event.block.BlockDispenseArmorEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.ItemDespawnEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.entity.ItemMergeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerPickupArrowEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Painting
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.event.GamePlayerRoomAdmissionDeniedEvent
import org.katacr.kaGameCenter.event.GameRoomAdmissionType
import org.katacr.kaGameCenter.event.GameRoomsMenuOpenedEvent
import org.katacr.kaGameCenter.event.GameStatsMenuOpenedEvent
import org.katacr.kaGameCenter.event.GameSpectatorTargetMode
import org.katacr.kaGameCenter.event.GameSpectatorTargetSelectEvent
import java.util.Collections
import java.util.IdentityHashMap

/** 将 BedWars 玩家事件按房间路由到对应 Session。 */
class BedWarsListener(
    private val plugin: JavaPlugin,
    private val roomManager: GameRoomManager,
    private val configService: BedWarsConfigService,
    private val language: ModuleLanguage
) : Listener {
    private val claimedConsumptions = Collections.newSetFromMap(
        IdentityHashMap<PlayerItemConsumeEvent, Boolean>()
    )
    private val evaluatedConsumptions = Collections.newSetFromMap(
        IdentityHashMap<PlayerItemConsumeEvent, Boolean>()
    )
    private val claimedPlayerInteractions = Collections.newSetFromMap(
        IdentityHashMap<PlayerInteractEvent, Boolean>()
    )
    private val evaluatedPlayerInteractions = Collections.newSetFromMap(
        IdentityHashMap<PlayerInteractEvent, Boolean>()
    )
    private val claimedInventoryClicks = Collections.newSetFromMap(
        IdentityHashMap<InventoryClickEvent, Boolean>()
    )
    private val claimedInventoryDrags = Collections.newSetFromMap(
        IdentityHashMap<InventoryDragEvent, Boolean>()
    )
    private val claimedEntityInteractions = Collections.newSetFromMap(
        IdentityHashMap<PlayerInteractEntityEvent, Boolean>()
    )
    private val claimedPreciseEntityInteractions = Collections.newSetFromMap(
        IdentityHashMap<PlayerInteractAtEntityEvent, Boolean>()
    )
    private val claimedArmorStandManipulations = Collections.newSetFromMap(
        IdentityHashMap<PlayerArmorStandManipulateEvent, Boolean>()
    )
    private val claimedEquipmentSlotSwaps = Collections.newSetFromMap(
        IdentityHashMap<PlayerSwapWithEquipmentSlotEvent, Boolean>()
    )
    private val claimedBedEntries = Collections.newSetFromMap(
        IdentityHashMap<PlayerBedEnterEvent, Boolean>()
    )
    private val claimedItemDrops = Collections.newSetFromMap(
        IdentityHashMap<PlayerDropItemEvent, Boolean>()
    )
    private val claimedItemPickups = Collections.newSetFromMap(
        IdentityHashMap<EntityPickupItemEvent, Boolean>()
    )
    private val evaluatedItemPickups = Collections.newSetFromMap(
        IdentityHashMap<EntityPickupItemEvent, Boolean>()
    )
    private val claimedArrowPickups = Collections.newSetFromMap(
        IdentityHashMap<PlayerPickupArrowEvent, Boolean>()
    )
    private val claimedHandSwaps = Collections.newSetFromMap(
        IdentityHashMap<PlayerSwapHandItemsEvent, Boolean>()
    )
    private val claimedDamageEvents = Collections.newSetFromMap(
        IdentityHashMap<EntityDamageEvent, Boolean>()
    )
    private val evaluatedDamageEvents = Collections.newSetFromMap(
        IdentityHashMap<EntityDamageEvent, Boolean>()
    )
    private val claimedEntityDamageEvents = Collections.newSetFromMap(
        IdentityHashMap<EntityDamageByEntityEvent, Boolean>()
    )
    private val evaluatedEntityDamageEvents = Collections.newSetFromMap(
        IdentityHashMap<EntityDamageByEntityEvent, Boolean>()
    )
    private val claimedItemFrameDamageEvents = Collections.newSetFromMap(
        IdentityHashMap<EntityDamageByEntityEvent, Boolean>()
    )
    private val claimedBlockBreaks = Collections.newSetFromMap(
        IdentityHashMap<BlockBreakEvent, Boolean>()
    )
    private val evaluatedBlockBreaks = Collections.newSetFromMap(
        IdentityHashMap<BlockBreakEvent, Boolean>()
    )
    private val claimedBlockPlacements = Collections.newSetFromMap(
        IdentityHashMap<BlockPlaceEvent, Boolean>()
    )
    private val evaluatedBlockPlacements = Collections.newSetFromMap(
        IdentityHashMap<BlockPlaceEvent, Boolean>()
    )
    private val claimedBucketEmpties = Collections.newSetFromMap(
        IdentityHashMap<PlayerBucketEmptyEvent, Boolean>()
    )
    private val evaluatedBucketEmpties = Collections.newSetFromMap(
        IdentityHashMap<PlayerBucketEmptyEvent, Boolean>()
    )
    private val claimedBucketFills = Collections.newSetFromMap(
        IdentityHashMap<PlayerBucketFillEvent, Boolean>()
    )
    private val evaluatedBucketFills = Collections.newSetFromMap(
        IdentityHashMap<PlayerBucketFillEvent, Boolean>()
    )
    private val claimedCommands = Collections.newSetFromMap(
        IdentityHashMap<PlayerCommandPreprocessEvent, Boolean>()
    )
    private val evaluatedCreatureSpawns = Collections.newSetFromMap(
        IdentityHashMap<CreatureSpawnEvent, Boolean>()
    )
    private val claimedArmorDispenses = Collections.newSetFromMap(
        IdentityHashMap<BlockDispenseArmorEvent, Boolean>()
    )
    private val evaluatedEntityExplosions = Collections.newSetFromMap(
        IdentityHashMap<EntityExplodeEvent, Boolean>()
    )
    private val evaluatedDeaths = Collections.newSetFromMap(
        IdentityHashMap<PlayerDeathEvent, Boolean>()
    )
    private val allowedDeathDrops = IdentityHashMap<PlayerDeathEvent, List<org.bukkit.inventory.ItemStack>>()

    /** 参考 BedWars1058，在管理员加入后延迟提示常见的世界管理与出生保护冲突。 */
    @EventHandler
    fun onOperatorJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (!player.isOp) return
        val warnings = buildList {
            if (plugin.server.pluginManager.isPluginEnabled("Multiverse-Core")) {
                add(language.getMessage("bedwars.warning_multiverse_core"))
            }
            plugin.server.spawnRadius.takeIf { it > 0 }?.let { radius ->
                add(language.getMessage("bedwars.warning_spawn_protection", radius))
            }
        }
        if (warnings.isEmpty()) return
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!player.isOnline || !player.isOp) return@Runnable
            warnings.forEach { player.sendMessage(Component.text(it)) }
        }, 5L)
    }

    /** 按正式加入、观战或重连类型播放参考准入拒绝音效。 */
    @EventHandler
    fun onAdmissionDenied(event: GamePlayerRoomAdmissionDeniedEvent) {
        if (event.room.session !is BedWarsGameSession) return
        val config = configService.current()
        val rule = when (event.type) {
            GameRoomAdmissionType.JOIN -> config.joinDeniedSound
            GameRoomAdmissionType.SPECTATE -> config.spectateDeniedSound
            GameRoomAdmissionType.RECONNECT -> config.rejoinDeniedSound
        }
        playSoundRule(event.player, rule)
    }

    /** 在 BedWars 专属房间选择器完成打开后播放参考 GUI 反馈。 */
    @EventHandler
    fun onRoomsMenuOpened(event: GameRoomsMenuOpenedEvent) {
        if (!event.gameId?.substringBefore(':').equals("bedwars", ignoreCase = true)) return
        playSoundRule(event.player, configService.current().arenaSelectorOpenSound)
    }

    /** 在 BedWars 战绩详情完成打开后播放参考 GUI 反馈。 */
    @EventHandler
    fun onStatsMenuOpened(event: GameStatsMenuOpenedEvent) {
        if (!event.gameId.equals("bedwars", ignoreCase = true)) return
        playSoundRule(event.player, configService.current().statsMenuOpenSound)
    }

    /** 在有效托管目标点击后播放参考反馈，包括被外部监听器取消的选择。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onSpectatorTargetSelect(event: GameSpectatorTargetSelectEvent) {
        if (event.mode != GameSpectatorTargetMode.TELEPORT) return
        val session = roomManager.getRoom(event.roomId)?.session as? BedWarsGameSession ?: return
        session.playSpectatorTargetClick(event.spectator)
    }

    /** 玩家被外部逻辑传送出竞技场世界时立即释放 BedWars 房间状态。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val room = roomManager.getPlayerRoom(event.player) ?: return
        if (room.session !is BedWarsGameSession || event.player.world == room.world) return
        roomManager.leaveCurrentRoom(event.player)
    }

    /** 在玩家跨方块移动时检查当前房间的虚空边界。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? BedWarsGameSession ?: return
        session.markPlayerActive(event.player)
        val from = event.from
        val to = event.to
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ && from.world == to.world) return
        session.handleMove(event.player, to)
    }

    /** 在同一竞技场内传送后同步基地区域、治愈池、床提示和陷阱状态。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? BedWarsGameSession ?: return
        session.handleTeleport(event.player, event.to)
    }

    /** 应用倒计时、复活和淘汰阶段的伤害保护。 */
    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        val session = session(player) ?: return
        evaluatedDamageEvents.add(event)
        if (session.handleDamage(event)) {
            claimedDamageEvents.add(event)
            event.isCancelled = true
        }
    }

    /** 应用同队免伤和跨房间攻击隔离。 */
    @EventHandler(ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        val session = session(event.entity.world) ?: return
        evaluatedEntityDamageEvents.add(event)
        if (session.handleDamageByEntity(event)) {
            claimedEntityDamageEvents.add(event)
            event.isCancelled = true
        }
    }

    /** 阻止攻击 BedWars 模板中的普通或发光物品展示框。 */
    @EventHandler(ignoreCancelled = true)
    fun onItemFrameDamage(event: EntityDamageByEntityEvent) {
        if (event.entity is ItemFrame && session(event.entity.world) != null) {
            claimedItemFrameDamageEvents.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保护非实体伤害；实体伤害由专用终态入口按完整顺序处理。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onDamageComplete(event: EntityDamageEvent) {
        if (event is EntityDamageByEntityEvent) return
        val player = event.entity as? org.bukkit.entity.Player ?: return
        if (completeDamage(event)) session(player)?.commitDamage(event)
    }

    /** 最终依次保护通用、实体和展示框伤害，再发送投射物生命反馈。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onDamageByEntityComplete(event: EntityDamageByEntityEvent) {
        val damageAllowed = completeDamage(event)
        val entityDamageAllowed = completeEntityDamage(event)
        if (damageAllowed && entityDamageAllowed && !event.isCancelled) {
            val player = event.entity as? org.bukkit.entity.Player
            if (player != null) session(player)?.commitDamage(event)
            session(event.entity.world)?.handleProjectileDamageFeedback(event)
        }
    }

    /** 清理通用伤害评估记录，并只对先前跳过且最终放行的事件补判一次。 */
    private fun completeDamage(event: EntityDamageEvent): Boolean {
        val claimed = claimedDamageEvents.remove(event)
        val evaluated = evaluatedDamageEvents.remove(event)
        if (claimed) {
            event.isCancelled = true
            return false
        }
        if (event.isCancelled) return false
        val player = event.entity as? org.bukkit.entity.Player ?: return true
        val session = session(player) ?: return true
        if (!evaluated && session.handleDamage(event)) {
            event.isCancelled = true
            return false
        }
        return true
    }

    /** 清理实体伤害评估记录，并补判敌我、房间、召唤物及展示框保护。 */
    private fun completeEntityDamage(event: EntityDamageByEntityEvent): Boolean {
        val claimed = claimedEntityDamageEvents.remove(event)
        val frameClaimed = claimedItemFrameDamageEvents.remove(event)
        val evaluated = evaluatedEntityDamageEvents.remove(event)
        if (claimed || frameClaimed) {
            event.isCancelled = true
            return false
        }
        if (event.isCancelled) return false
        val session = session(event.entity.world)
        if (!evaluated && session?.handleDamageByEntity(event) == true) {
            event.isCancelled = true
            return false
        }
        if (event.entity is ItemFrame && session != null) {
            event.isCancelled = true
            return false
        }
        return true
    }

    /** 把床破坏事件交给 Session 并阻止未开放的地图破坏。 */
    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val session = session(event.player) ?: return
        evaluatedBlockBreaks.add(event)
        if (session.handleBlockBreak(event)) {
            claimedBlockBreaks.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保护受限破坏，并只提交成功破坏的建筑、床和蜘蛛网状态。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockBreakComplete(event: BlockBreakEvent) {
        if (claimedBlockBreaks.remove(event)) {
            evaluatedBlockBreaks.remove(event)
            event.isCancelled = true
            return
        }
        val evaluated = evaluatedBlockBreaks.remove(event)
        if (event.isCancelled) return
        val session = session(event.player) ?: return
        if (!evaluated && session.handleBlockBreak(event)) {
            event.isCancelled = true
            return
        }
        session.handleBlockBreakComplete(event)
    }

    /** 验证并追踪当前房间中的玩家建筑。 */
    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val session = session(event.player) ?: return
        evaluatedBlockPlacements.add(event)
        if (session.handleBlockPlace(event)) {
            claimedBlockPlacements.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保护受限放置，并只提交成功建筑或自动点燃 TNT。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockPlaceComplete(event: BlockPlaceEvent) {
        if (claimedBlockPlacements.remove(event)) {
            evaluatedBlockPlacements.remove(event)
            event.isCancelled = true
            return
        }
        val evaluated = evaluatedBlockPlacements.remove(event)
        if (event.isCancelled) return
        val session = session(event.player) ?: return
        if (!evaluated && session.handleBlockPlace(event)) {
            event.isCancelled = true
            return
        }
        session.handleBlockPlaceComplete(event)
    }

    /** 阻止发射器替换 BedWars 玩家由模块维护的四槽护甲。 */
    @EventHandler(ignoreCancelled = true)
    fun onDispenseArmor(event: BlockDispenseArmorEvent) {
        val player = event.targetEntity as? org.bukkit.entity.Player ?: return
        val session = session(player) ?: return
        claimedArmorDispenses.add(event)
        event.isCancelled = true
        session.refreshInvisibleArmorAfterInventoryAttempt(player)
    }

    /** 最终保持发射器护甲替换取消，并补查被前置取消后又放行的事件。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onDispenseArmorComplete(event: BlockDispenseArmorEvent) {
        if (claimedArmorDispenses.remove(event)) {
            event.isCancelled = true
            return
        }
        if (event.isCancelled) return
        val player = event.targetEntity as? org.bukkit.entity.Player ?: return
        val session = session(player) ?: return
        event.isCancelled = true
        session.refreshInvisibleArmorAfterInventoryAttempt(player)
    }

    /** 把水桶倒水纳入房间建筑保护和临时方块恢复。 */
    @EventHandler(ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        val session = session(event.player) ?: return
        evaluatedBucketEmpties.add(event)
        if (session.handleBucketEmpty(event)) {
            claimedBucketEmpties.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保护受限倒水，并只提交成功水源的登记与空桶清理。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBucketEmptyComplete(event: PlayerBucketEmptyEvent) {
        if (claimedBucketEmpties.remove(event)) {
            evaluatedBucketEmpties.remove(event)
            event.isCancelled = true
            return
        }
        val evaluated = evaluatedBucketEmpties.remove(event)
        if (event.isCancelled) return
        val session = session(event.player) ?: return
        if (!evaluated && session.handleBucketEmpty(event)) {
            event.isCancelled = true
            return
        }
        session.handleBucketEmptyComplete(event)
    }

    /** 阻止玩家取走地图原生液体，只允许回收本局放置的水源。 */
    @EventHandler(ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        val session = session(event.player) ?: return
        evaluatedBucketFills.add(event)
        if (session.handleBucketFill(event)) {
            claimedBucketFills.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保护受限取水，并只释放成功取回的局内水源登记。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBucketFillComplete(event: PlayerBucketFillEvent) {
        if (claimedBucketFills.remove(event)) {
            evaluatedBucketFills.remove(event)
            event.isCancelled = true
            return
        }
        val evaluated = evaluatedBucketFills.remove(event)
        if (event.isCancelled) return
        val session = session(event.player) ?: return
        if (!evaluated && session.handleBucketFill(event)) {
            event.isCancelled = true
            return
        }
        session.handleBucketFillComplete(event)
    }

    /** 过滤实体爆炸，使其只能破坏本局玩家放置的方块。 */
    @EventHandler(ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        val session = session(event.location.world) ?: return
        evaluatedEntityExplosions.add(event)
        session.handleExplosion(event)
    }

    /** 最终重滤实体爆炸清单，并释放实际被破坏的房间方块登记。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityExplodeComplete(event: EntityExplodeEvent) {
        val evaluated = evaluatedEntityExplosions.remove(event)
        if (event.isCancelled) return
        val session = session(event.location.world) ?: return
        if (evaluated) {
            session.filterEntityExplosionBlocks(event)
        } else {
            session.handleExplosion(event)
        }
        session.handleExplosionComplete(event.blockList())
    }

    /** 过滤床爆炸等方块爆炸，保护地图模板和队伍床。 */
    @EventHandler(ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        session(event.block.world)?.handleBlockExplosion(event.block.location.add(0.5, 0.5, 0.5), event.blockList())
    }

    /** 最终重滤方块爆炸清单，并释放实际被破坏的房间方块登记。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockExplodeComplete(event: BlockExplodeEvent) {
        if (event.isCancelled) return
        val session = session(event.block.world) ?: return
        session.handleBlockExplosion(event.block.location.add(0.5, 0.5, 0.5), event.blockList())
        session.handleExplosionComplete(event.blockList())
    }

    /** 阻止 BedWars 运行世界重新进入降雨或雷暴。 */
    @EventHandler(ignoreCancelled = true)
    fun onWeatherChange(event: WeatherChangeEvent) {
        if (event.toWeatherState() && session(event.world) != null) event.isCancelled = true
    }

    /** 最终保持 BedWars 运行世界晴天，防止后续监听器重新放行降雨。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onWeatherChangeComplete(event: WeatherChangeEvent) {
        if (event.toWeatherState() && session(event.world) != null) event.isCancelled = true
    }

    /** 仅允许模块通过 CUSTOM 原因生成商店、召唤物和末影龙。 */
    @EventHandler(ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val session = session(event.entity.world) ?: return
        evaluatedCreatureSpawns.add(event)
        if (event.spawnReason != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.isCancelled = true
            return
        }
        session.handleHalloweenCreatureSpawn(event.entity)
    }

    /** 最终拒绝自然刷怪，并为前置取消后放行的自定义生物补做一次模块装饰。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onCreatureSpawnComplete(event: CreatureSpawnEvent) {
        val evaluated = evaluatedCreatureSpawns.remove(event)
        if (event.spawnReason != CreatureSpawnEvent.SpawnReason.CUSTOM && session(event.entity.world) != null) {
            event.isCancelled = true
            return
        }
        if (event.isCancelled || evaluated) return
        session(event.entity.world)?.handleHalloweenCreatureSpawn(event.entity)
    }

    /** 阻止火焰烧毁模板或本局玩家建筑。 */
    @EventHandler(ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        val session = session(event.block.world) ?: return
        if (session.shouldCancelBlockBurn(event.block)) event.isCancelled = true
    }

    /** 最终保持模板与队伍床燃烧保护，防止后续监听器重新放行。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockBurnComplete(event: BlockBurnEvent) {
        val session = session(event.block.world) ?: return
        if (session.shouldCancelBlockBurn(event.block)) event.isCancelled = true
    }

    /** 阻止竞技场模板中的冰在对局期间融化。 */
    @EventHandler(ignoreCancelled = true)
    fun onBlockFade(event: BlockFadeEvent) {
        if (event.block.type == Material.ICE && session(event.block.world) != null) event.isCancelled = true
    }

    /** 最终保持竞技场冰块不融化。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockFadeComplete(event: BlockFadeEvent) {
        if (event.block.type == Material.ICE && session(event.block.world) != null) event.isCancelled = true
    }

    /** 阻止相邻方块变化使竞技场仙人掌自行破坏。 */
    @EventHandler(ignoreCancelled = true)
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        if (event.block.type == Material.CACTUS && session(event.block.world) != null) event.isCancelled = true
    }

    /** 最终保持竞技场仙人掌不因物理更新自行破坏。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockPhysicsComplete(event: BlockPhysicsEvent) {
        if (event.block.type == Material.CACTUS && session(event.block.world) != null) event.isCancelled = true
    }

    /** 阻止生物踩踏使竞技场耕地变为泥土。 */
    @EventHandler(ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (event.block.type == Material.FARMLAND && event.to == Material.DIRT && session(event.block.world) != null) {
            event.isCancelled = true
        }
    }

    /** 最终阻止实体踩踏竞技场耕地。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityChangeBlockComplete(event: EntityChangeBlockEvent) {
        if (event.block.type == Material.FARMLAND && event.to == Material.DIRT && session(event.block.world) != null) {
            event.isCancelled = true
        }
    }

    /** 过滤非运行阶段、床物品和小麦种子的地面实体生成。 */
    @EventHandler(ignoreCancelled = true)
    fun onItemSpawn(event: ItemSpawnEvent) {
        val session = session(event.entity.world) ?: return
        if (session.shouldBlockItemSpawn(event.entity.itemStack.type)) event.isCancelled = true
    }

    /** 最终过滤非运行阶段、床物品和小麦种子的地面实体生成。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemSpawnComplete(event: ItemSpawnEvent) {
        val session = session(event.entity.world) ?: return
        if (session.shouldBlockItemSpawn(event.entity.itemStack.type)) event.isCancelled = true
    }

    /** 床全息阻挡原版建造判定时恢复床正上方的合法放置。 */
    @EventHandler(priority = EventPriority.LOW)
    fun onBlockCanBuild(event: BlockCanBuildEvent) {
        session(event.block.world)?.handleBlockCanBuild(event)
    }

    /** 延迟区块加载时移除模板遗留实体并保留本局登记实体。 */
    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        session(event.world)?.handleEntitiesLoaded(event.entities)
    }

    /** 阻止玩家或其他实体移除竞技场中的画和物品展示框。 */
    @EventHandler(ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakByEntityEvent) {
        if ((event.entity is Painting || event.entity is ItemFrame) && session(event.entity.world) != null) {
            event.isCancelled = true
        }
    }

    /** 最终保持竞技场画与物品展示框不可被实体移除。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onHangingBreakComplete(event: HangingBreakByEntityEvent) {
        if ((event.entity is Painting || event.entity is ItemFrame) && session(event.entity.world) != null) {
            event.isCancelled = true
        }
    }

    /** 阻止 BedWars 玩家进入地图模板中的床。 */
    @EventHandler(ignoreCancelled = true)
    fun onBedEnter(event: PlayerBedEnterEvent) {
        if (session(event.player) != null) {
            claimedBedEntries.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持房间床交互取消，并补查被前置取消后又放行的事件。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBedEnterComplete(event: PlayerBedEnterEvent) {
        if (claimedBedEntries.remove(event)) {
            event.isCancelled = true
            return
        }
        if (!event.isCancelled && session(event.player) != null) event.isCancelled = true
    }

    /** 阻止 BedWars 玩家取放地图盔甲架和模块全息的装备。 */
    @EventHandler(ignoreCancelled = true)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        if (session(event.player) != null) {
            claimedArmorStandManipulations.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持盔甲架操作取消，并补查被前置取消后又放行的事件。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onArmorStandManipulateComplete(event: PlayerArmorStandManipulateEvent) {
        if (claimedArmorStandManipulations.remove(event)) {
            event.isCancelled = true
            return
        }
        if (!event.isCancelled && session(event.player) != null) event.isCancelled = true
    }

    /** 将 Bukkit 重生事件路由到队伍复活或最终淘汰流程。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) {
        session(event.player)?.handleRespawn(event)
    }

    /** 移除会在复活时重新发放的永久装备掉落。 */
    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val session = session(event.player) ?: return
        evaluatedDeaths.add(event)
        session.handleDeathDrops(event)
        session.captureDeathDropSnapshot(event)?.let { allowedDeathDrops[event] = it }
    }

    /** 最终过滤后置新增的永久或非法死亡掉落，避免重复执行资源转移。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeathComplete(event: PlayerDeathEvent) {
        val evaluated = evaluatedDeaths.remove(event)
        val allowedDrops = allowedDeathDrops.remove(event)
        val session = session(event.player) ?: return
        if (evaluated) session.finalizeDeathDrops(event, allowedDrops) else session.handleDeathDrops(event)
    }

    /** 点击模块商店村民时打开物品或队伍升级菜单。 */
    @EventHandler(ignoreCancelled = true)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (shouldCancelEntityInteract(event.player, event.rightClicked, event.hand)) {
            claimedEntityInteractions.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持普通实体交互取消，并补查被前置取消后又放行的事件。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteractEntityComplete(event: PlayerInteractEntityEvent) {
        if (claimedEntityInteractions.remove(event)) {
            event.isCancelled = true
            return
        }
        if (!event.isCancelled && shouldCancelEntityInteract(event.player, event.rightClicked, event.hand)) {
            event.isCancelled = true
        }
    }

    /** 精确命中实体时复用普通实体交互入口，覆盖客户端 INTERACT_AT 数据包。 */
    @EventHandler(ignoreCancelled = true)
    fun onInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        if (shouldCancelEntityInteract(event.player, event.rightClicked, event.hand)) {
            claimedPreciseEntityInteractions.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持精确实体交互取消，并补查被前置取消后又放行的事件。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteractAtEntityComplete(event: PlayerInteractAtEntityEvent) {
        if (claimedPreciseEntityInteractions.remove(event)) {
            event.isCancelled = true
            return
        }
        if (!event.isCancelled && shouldCancelEntityInteract(event.player, event.rightClicked, event.hand)) {
            event.isCancelled = true
        }
    }

    /** 统一处理两类实体交互事件中的展示框保护和商店 NPC 打开逻辑。 */
    private fun shouldCancelEntityInteract(
        player: org.bukkit.entity.Player,
        entity: org.bukkit.entity.Entity,
        hand: EquipmentSlot
    ): Boolean {
        val session = session(player) ?: return false
        val frame = entity as? ItemFrame
        if (frame != null) {
            val heldItem = if (hand == EquipmentSlot.HAND) {
                player.inventory.itemInMainHand
            } else {
                player.inventory.itemInOffHand
            }
            return frame.item.type != Material.AIR || session.handlePermanentItemFrameInsert(player, heldItem)
        }
        return session.handleShopInteract(player, entity)
    }

    /** 将右键使用的特殊商品交由当前 BedWars Session 执行。 */
    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val session = session(event.player) ?: return
        evaluatedPlayerInteractions.add(event)
        if (handlePlayerInteract(session, event)) {
            claimedPlayerInteractions.add(event)
            event.isCancelled = true
        }
    }

    /** 最终确认模块接管的玩家交互保持取消，并补查被前置取消后又放行的事件。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteractComplete(event: PlayerInteractEvent) {
        if (claimedPlayerInteractions.remove(event)) {
            evaluatedPlayerInteractions.remove(event)
            event.isCancelled = true
            return
        }
        val alreadyEvaluated = evaluatedPlayerInteractions.remove(event)
        if (event.useInteractedBlock() == Event.Result.DENY && event.useItemInHand() == Event.Result.DENY) return
        if (alreadyEvaluated) return
        val session = session(event.player) ?: return
        if (handlePlayerInteract(session, event)) event.isCancelled = true
    }

    /** 依既有顺序处理活动刷新、等待快捷物品、方块限制和特殊商品交互。 */
    private fun handlePlayerInteract(session: BedWarsGameSession, event: PlayerInteractEvent): Boolean {
        session.markPlayerActive(event.player)
        return session.handlePreGameInteract(event) ||
            session.handleBlockInteract(event) ||
            session.handleSpecialInteract(event)
    }

    /** 将桥蛋和床虫的投射物命中事件路由到所属房间。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        session(event.entity.world)?.handleProjectileHit(event)
    }

    /** 执行蛋糕拒绝或参考牛奶接管，并记录模块评估与取消状态。 */
    @EventHandler(ignoreCancelled = true)
    fun onItemConsume(event: PlayerItemConsumeEvent) {
        val session = session(event.player) ?: return
        evaluatedConsumptions.add(event)
        session.handleItemConsume(event)
        if (event.isCancelled) claimedConsumptions.add(event)
    }

    /** 最终保护模块拒绝的消费，补判前置放行，并提交成功药水的空瓶清理。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemConsumeComplete(event: PlayerItemConsumeEvent) {
        if (claimedConsumptions.remove(event)) {
            evaluatedConsumptions.remove(event)
            event.isCancelled = true
            return
        }
        val evaluated = evaluatedConsumptions.remove(event)
        if (event.isCancelled) return
        val session = session(event.player) ?: return
        if (!evaluated) {
            session.handleItemConsume(event)
            if (event.isCancelled) return
        }
        session.handleItemConsumeComplete(event)
    }

    /** 阻止召唤物锁定队友、观战者或其他房间玩家。 */
    @EventHandler(ignoreCancelled = true)
    fun onEntityTarget(event: EntityTargetLivingEntityEvent) {
        val session = session(event.entity.world) ?: return
        if (session.handleSpecialMobTarget(event)) event.isCancelled = true
    }

    /** 最终阻止召唤物锁定队友、观战者、无效目标或友军召唤物。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityTargetComplete(event: EntityTargetLivingEntityEvent) {
        val session = session(event.entity.world) ?: return
        if (session.handleSpecialMobTarget(event)) event.isCancelled = true
    }

    /** 清除模块召唤物的原版死亡掉落并立即解除房间追踪。 */
    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        session(event.entity.world)?.handleSpecialMobDeath(event)
    }

    /** 按 BedWars 等待/运行阶段配置控制饥饿变化。 */
    @EventHandler(ignoreCancelled = true)
    fun onFoodChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        val session = session(player) ?: return
        if (session.shouldCancelFoodChange(player)) event.isCancelled = true
    }

    /** 最终保持当前阶段和身份对应的饥饿变化限制。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onFoodChangeComplete(event: FoodLevelChangeEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        val session = session(player) ?: return
        if (session.shouldCancelFoodChange(player)) event.isCancelled = true
    }

    /** 阻止复活中、已淘汰玩家或永久装备丢弃，并维护出生默认剑。 */
    @EventHandler(ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        val session = session(event.player) ?: return
        if (!session.isActiveParticipant(event.player) ||
            session.handleItemDrop(event.player, event.itemDrop.itemStack)
        ) {
            claimedItemDrops.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保护受限丢弃，并只为确实成功丢弃最后一把剑的玩家恢复出生剑。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onDropComplete(event: PlayerDropItemEvent) {
        if (claimedItemDrops.remove(event)) {
            event.isCancelled = true
            return
        }
        if (event.isCancelled) return
        val session = session(event.player) ?: return
        if (!session.isActiveParticipant(event.player) ||
            session.handleItemDrop(event.player, event.itemDrop.itemStack)
        ) {
            event.isCancelled = true
            return
        }
        session.handleItemDropComplete(event.player, event.itemDrop.itemStack)
    }

    /** 阻止复活中或已淘汰玩家拾取资源。 */
    @EventHandler(ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        val session = session(player) ?: return
        evaluatedItemPickups.add(event)
        if (session.shouldCancelPickup(player, event.item)) {
            claimedItemPickups.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保护受限拾取，并在拾取真正生效后释放已被完全取走的房间物品实体。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPickupComplete(event: EntityPickupItemEvent) {
        if (claimedItemPickups.remove(event)) {
            evaluatedItemPickups.remove(event)
            event.isCancelled = true
            return
        }
        val alreadyEvaluated = evaluatedItemPickups.remove(event)
        if (event.isCancelled) return
        val player = event.entity as? org.bukkit.entity.Player
        if (player != null && !alreadyEvaluated && session(player)?.shouldCancelPickup(player, event.item) == true) {
            event.isCancelled = true
            return
        }
        val session = session(event.item.world) ?: return
        if (player == null) {
            session.handleTrackedItemPickup(event.item, event.remaining)
        } else {
            session.handlePlayerPickupComplete(player, event.item, event.remaining)
        }
    }

    /** 默认阻止生成器独立资源实体互相或与普通掉落物合并。 */
    @EventHandler(ignoreCancelled = true)
    fun onItemMerge(event: ItemMergeEvent) {
        val session = session(event.entity.world) ?: return
        if (session.shouldCancelResourceMerge(event.entity, event.target)) event.isCancelled = true
    }

    /** 最终保护独立资源，并在允许合并后转移标记和释放源实体。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemMergeComplete(event: ItemMergeEvent) {
        val session = session(event.entity.world) ?: return
        if (session.shouldCancelResourceMerge(event.entity, event.target)) {
            event.isCancelled = true
            return
        }
        if (!event.isCancelled) session.handleResourceMerge(event.entity, event.target)
    }

    /** 在物品自然消失后立即释放房间实体追踪。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemDespawn(event: ItemDespawnEvent) {
        session(event.entity.world)?.handleTrackedItemDespawn(event.entity)
    }

    /** 阻止观战、复活和非运行阶段玩家拾取射入地图的箭。 */
    @EventHandler(ignoreCancelled = true)
    fun onPickupArrow(event: PlayerPickupArrowEvent) {
        val session = session(event.player) ?: return
        if (!session.isActiveParticipant(event.player)) {
            claimedArrowPickups.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持非活动玩家拾箭取消，并补查被前置取消后又放行的事件。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPickupArrowComplete(event: PlayerPickupArrowEvent) {
        if (claimedArrowPickups.remove(event)) {
            event.isCancelled = true
            return
        }
        val session = session(event.player) ?: return
        if (!event.isCancelled && !session.isActiveParticipant(event.player)) event.isCancelled = true
    }

    /** 阻止观战、复活和非运行阶段玩家通过副手交换移动物品。 */
    @EventHandler(ignoreCancelled = true)
    fun onSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val session = session(event.player) ?: return
        if (!session.isActiveParticipant(event.player)) {
            claimedHandSwaps.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持非活动玩家副手交换取消，并补查被前置取消后又放行的事件。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onSwapHandItemsComplete(event: PlayerSwapHandItemsEvent) {
        if (claimedHandSwaps.remove(event)) {
            event.isCancelled = true
            return
        }
        val session = session(event.player) ?: return
        if (!event.isCancelled && !session.isActiveParticipant(event.player)) event.isCancelled = true
    }

    /** 阻止右键装备交换绕过库存点击事件替换模块护甲。 */
    @EventHandler(ignoreCancelled = true)
    fun onSwapEquipmentSlot(event: PlayerSwapWithEquipmentSlotEvent) {
        val session = session(event.player) ?: return
        if (session.handleEquipmentSlotSwap(event.player, event.slot)) {
            claimedEquipmentSlotSwaps.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持装备槽交换取消，并补查被前置取消后又放行的事件。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onSwapEquipmentSlotComplete(event: PlayerSwapWithEquipmentSlotEvent) {
        if (claimedEquipmentSlotSwaps.remove(event)) {
            event.isCancelled = true
            return
        }
        if (!event.isCancelled && session(event.player)?.handleEquipmentSlotSwap(event.player, event.slot) == true) {
            event.isCancelled = true
        }
    }

    /** 阻止复活中或已淘汰玩家操作容器。 */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        val session = session(player) ?: return
        val holder = event.inventory.holder as? org.katacr.kaGameCenter.menu.chest.ChestMenuHolder
        if (holder != null && session.handleShopClick(player, holder, event.rawSlot, event.click)) {
            event.isCancelled = true
            return
        }
        if (session.handlePermanentInventoryClick(event)) {
            claimedInventoryClicks.add(event)
            event.isCancelled = true
            return
        }
        if (!session.isActiveParticipant(player)) {
            claimedInventoryClicks.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持永久装备及非活动玩家的库存点击取消，防止后续监听器放行。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerInventoryClickComplete(event: InventoryClickEvent) {
        if (claimedInventoryClicks.remove(event)) event.isCancelled = true
    }

    /** 阻止观战者操作背包及永久装备拖入外部栏位。 */
    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        val session = session(player) ?: return
        if (!session.isActiveParticipant(player) || session.handlePermanentInventoryDrag(event)) {
            claimedInventoryDrags.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持永久装备及非活动玩家的库存拖拽取消，防止后续监听器放行。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerInventoryDragComplete(event: InventoryDragEvent) {
        if (claimedInventoryDrags.remove(event)) event.isCancelled = true
    }

    /** 关闭外部库存后恢复被移出背包的最后一把普通剑。 */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.type == InventoryType.PLAYER) return
        val player = event.player as? org.bukkit.entity.Player ?: return
        session(player)?.handleExternalInventoryClose(player)
    }

    /** 清空当前 BedWars 房间内被禁用的合成结果。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        val player = event.view.player as? org.bukkit.entity.Player ?: return
        session(player)?.handlePrepareCraft(event)
    }

    /** 阻止 BedWars 房间玩家执行未列入模块白名单的命令。 */
    @EventHandler(ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val session = session(event.player) ?: return
        if (session.handleCommand(event.player, event.message)) {
            claimedCommands.add(event)
            event.isCancelled = true
        }
    }

    /** 最终保持模块拒绝的命令取消，并按最终命令文本补查高优先级改写。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onCommandComplete(event: PlayerCommandPreprocessEvent) {
        if (claimedCommands.remove(event)) {
            event.isCancelled = true
            return
        }
        if (event.isCancelled) return
        val session = session(event.player) ?: return
        if (session.handleCommand(event.player, event.message)) event.isCancelled = true
    }

    private fun session(player: org.bukkit.entity.Player): BedWarsGameSession? {
        val room = roomManager.getPlayerRoom(player) ?: return null
        return room.session as? BedWarsGameSession
    }

    private fun session(world: World): BedWarsGameSession? {
        val room = roomManager.listRooms().firstOrNull { it.world == world } ?: return null
        return room.session as? BedWarsGameSession
    }

    /** 向单名玩家播放可关闭且带音量、音高的模块音效规则。 */
    private fun playSoundRule(player: org.bukkit.entity.Player, rule: BedWarsSoundRule) {
        val sound = rule.sound ?: return
        player.playSound(player.location, sound, rule.volume, rule.pitch)
    }
}
