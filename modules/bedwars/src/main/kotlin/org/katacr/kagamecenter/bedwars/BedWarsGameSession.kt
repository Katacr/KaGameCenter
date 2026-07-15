package org.katacr.kagamecenter.bedwars

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.GameRules
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Tag
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.Ladder
import org.bukkit.block.data.Openable
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Entity
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Item
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.Villager
import org.bukkit.entity.Egg
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.EnderPearl
import org.bukkit.entity.Fireball
import org.bukkit.entity.IronGolem
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Painting
import org.bukkit.entity.Silverfish
import org.bukkit.entity.Snowball
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockCanBuildEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaGameCenter.broadcast.RoomBroadcastService
import org.katacr.kaGameCenter.chat.GameChatChannel
import org.katacr.kaGameCenter.chat.GameChatRoute
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.game.GameState
import org.katacr.kaGameCenter.display.SidebarBoardRenderer
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.nametag.NametagCollisionRule
import org.katacr.kaGameCenter.nametag.NametagVisibility
import org.katacr.kaGameCenter.nametag.PlayerNametag
import org.katacr.kaGameCenter.nametag.PlayerNametagService
import org.katacr.kaGameCenter.menu.chest.ChestMenuEntry
import org.katacr.kaGameCenter.menu.chest.ChestMenuHolder
import org.katacr.kaGameCenter.menu.chest.ChestMenuService
import org.katacr.kaGameCenter.phase.GamePhaseTimer
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.team.GameTeam
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.task.RoomTaskService
import org.katacr.kaGameCenter.world.TemporaryWorldService
import org.katacr.kaGameCenter.velocity.VelocityBridgeService
import org.katacr.kaGameCenter.elimination.PlayerEliminationService
import org.katacr.kaGameCenter.event.GameItemUseEvent
import org.katacr.kaGameCenter.event.GameObjectiveDestroyedEvent
import org.katacr.kaGameCenter.event.GameObjectiveDestroyedFeedbackEvent
import org.katacr.kaGameCenter.event.GamePlayerAfkStateChangedEvent
import org.katacr.kaGameCenter.event.GamePlayerBaseRegionChangedEvent
import org.katacr.kaGameCenter.event.GamePlayerDeathFeedbackEvent
import org.katacr.kaGameCenter.event.GamePlayerDeathResolvedEvent
import org.katacr.kaGameCenter.event.GamePlayerExperienceGainedEvent
import org.katacr.kaGameCenter.event.GamePlayerFirstSpawnedEvent
import org.katacr.kaGameCenter.event.GamePlayerInvisibilityChangedEvent
import org.katacr.kaGameCenter.event.GamePlayerLevelUpEvent
import org.katacr.kaGameCenter.event.GamePlayerRespawnedEvent
import org.katacr.kaGameCenter.event.GameProjectileLaunchedEvent
import org.katacr.kaGameCenter.event.GamePurchaseEvent
import org.katacr.kaGameCenter.event.GamePurchaseKind
import org.katacr.kaGameCenter.event.GameResourceCollectEvent
import org.katacr.kaGameCenter.event.GameResourceTierChangedEvent
import org.katacr.kaGameCenter.event.GameShopOpenEvent
import org.katacr.kaGameCenter.event.GameStructureBlockPlacedEvent
import org.katacr.kaGameCenter.event.GameSummonSpawnedEvent
import org.katacr.kaGameCenter.event.GameTeamEliminatedEvent
import org.katacr.kaGameCenter.event.GameTimelineStageChangedEvent
import org.katacr.kaGameCenter.resource.RoomResourceScope
import org.katacr.kaGameCenter.resource.RoomResourceScopeService
import org.katacr.kaGameCenter.spectator.SpectatorMode
import org.katacr.kaGameCenter.spectator.SpectatorService
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/** 运行一个隔离的 BedWars 房间并管理床、复活、淘汰和胜负。 */
class BedWarsGameSession(
    private val plugin: JavaPlugin,
    override val room: GameRoom,
    private val configService: BedWarsConfigService,
    private val worldService: TemporaryWorldService,
    private val language: ModuleLanguage,
    private val roomManager: GameRoomManager,
    private val teamService: GameTeamService,
    private val teamAssignmentService: TeamAssignmentService,
    private val roomTaskService: RoomTaskService,
    private val resultService: GameResultService,
    private val playerRuntimeStateService: PlayerRuntimeStateService,
    private val roomBroadcastService: RoomBroadcastService,
    private val nametagService: PlayerNametagService,
    private val chestMenuService: ChestMenuService,
    private val quickBuyService: BedWarsQuickBuyService,
    private val eliminationService: PlayerEliminationService,
    private val spectatorService: SpectatorService,
    private val roomResourceScopeService: RoomResourceScopeService,
    private val velocityBridgeService: VelocityBridgeService
) : GameSession {
    private val vaultEconomy = BedWarsVaultEconomy(plugin)
    private val specialItemKey = NamespacedKey(plugin, "bedwars_special")
    private val potionItemKey = NamespacedKey(plugin, "bedwars_potion")
    private val permanentItemKey = NamespacedKey(plugin, "bedwars_permanent")
    private val defaultItemKey = NamespacedKey(plugin, "bedwars_default_item")
    private val preGameCommandKey = NamespacedKey(plugin, "bedwars_pre_game_command")
    private val playerStates = linkedMapOf<UUID, BedWarsPlayerState>()
    private val teamStates = linkedMapOf<String, BedWarsTeamState>()
    private val bedBlocks = linkedMapOf<BedWarsBlockKey, String>()
    private val participants = linkedSetOf<UUID>()
    private val placedBlocks = linkedSetOf<BedWarsBlockKey>()
    private val halloweenCobwebs = linkedMapOf<BedWarsBlockKey, Int>()
    private val generatorStates = mutableListOf<BedWarsGeneratorState>()
    private val announcedGeneratorTiers = linkedMapOf<BedWarsGeneratorType, Int>()
    private val trackedEntities = linkedSetOf<UUID>()
    private val shopNpcs = linkedMapOf<UUID, BedWarsShopKind>()
    private val shopHolograms = linkedMapOf<UUID, List<UUID>>()
    private val bridgeStates = linkedMapOf<UUID, BedWarsBridgeState>()
    private val specialMobs = linkedMapOf<UUID, BedWarsSpecialMobState>()
    private val fireballCooldowns = linkedMapOf<UUID, Int>()
    private val halloweenAmbienceTasks = linkedMapOf<UUID, BukkitTask>()
    private val quickBuyAssignments = linkedMapOf<UUID, String>()
    private val towerStates = mutableListOf<BedWarsTowerState>()
    private val dragonStates = linkedMapOf<UUID, BedWarsDragonState>()
    private val invisiblePlayers = linkedSetOf<UUID>()
    private val lastCombatHits = linkedMapOf<UUID, BedWarsLastHitState>()
    private val lastSpecialMobHits = linkedMapOf<UUID, BedWarsSpecialMobHitState>()
    private val pendingDeathCauses = linkedMapOf<UUID, EntityDamageEvent.DamageCause>()
    private val disconnectStates = linkedMapOf<UUID, BedWarsDisconnectState>()
    private val lastShoutTicks = linkedMapOf<UUID, Int>()
    private val inactivitySeconds = linkedMapOf<UUID, Int>()
    private val afkPlayers = linkedSetOf<UUID>()
    private val playerBaseRegions = linkedMapOf<UUID, String>()
    private val eliminatedTeams = linkedSetOf<String>()
    private val bedHolograms = linkedMapOf<UUID, UUID>()
    private val healPoolPlayers = linkedSetOf<UUID>()
    private val sidebarViewers = linkedSetOf<UUID>()
    private val sidebarLineTemplates = linkedMapOf<UUID, String>()
    private val sidebarLineFrames = linkedMapOf<UUID, Int>()
    private val tabHeaderFooterTemplates = linkedMapOf<UUID, BedWarsTabHeaderFooterTemplate>()
    private val tabHeaderFooterFrames = linkedMapOf<UUID, Int>()
    private val tabPlayerNameTemplates = linkedMapOf<UUID, String>()
    private val tabPlayerNameFrames = linkedMapOf<UUID, Int>()
    private val oneTimeProductTypes = setOf(BedWarsProductType.ITEM, BedWarsProductType.POTION, BedWarsProductType.SPECIAL)
    private val phaseTimer = GamePhaseTimer()
    private var moduleConfig = configService.current()
    private var gameConfig: BedWarsGameConfig? = null
    private var phase = BedWarsPhase.WAITING
    private var resultRecorded = false
    private var closed = false
    private var gameElapsedTicks = 0
    private var bedsDestroyedByTimer = false
    private var suddenDeathStarted = false
    private var timelineInitialized = false
    private var currentTimelineStageId: String? = null
    private var sidebarPlaceholderTicks = 0
    private var sidebarTitleTicks = 0
    private var sidebarTitleFrame = 0
    private var sidebarTitlePhase = BedWarsPhase.WAITING
    private var healthAnimationTicks = 0
    private var healthAnimationFrame = 0
    private var tabHeaderFooterTicks = 0
    private var tabPlayerListTicks = 0
    private var resultWinnerTeamId: String? = null
    private var resourceScope: RoomResourceScope? = null

    private val effectiveIslandRadius: Double get() = gameConfig?.islandRadius ?: moduleConfig.islandRadius
    private val effectiveDisableEmptyTeamGenerators: Boolean
        get() = gameConfig?.disableEmptyTeamGenerators ?: moduleConfig.disableEmptyTeamGenerators
    private val effectiveDisableEmptyTeamNpcs: Boolean
        get() = gameConfig?.disableEmptyTeamNpcs ?: moduleConfig.disableEmptyTeamNpcs
    private val effectiveVanillaDeathDrops: Boolean
        get() = gameConfig?.vanillaDeathDrops ?: moduleConfig.vanillaDeathDrops
    private val effectiveUseBedHologram: Boolean
        get() = gameConfig?.useBedHologram ?: moduleConfig.useBedHologram

    override fun usesCustomScoreboard(): Boolean = moduleConfig.lobbySidebarEnabled || moduleConfig.sidebarEnabled
    override fun usesCustomActionBar(): Boolean = true
    override fun usesCustomTabHeaderFooter(): Boolean = moduleConfig.tabHeaderFooterEnabled

    /** 任一阶段启用参考玩家列表格式时，由 Session 统一维护房间内名称。 */
    override fun usesCustomTabPlayerNames(): Boolean = moduleConfig.tabPlayerListWaitingEnabled ||
        moduleConfig.tabPlayerListCountdownEnabled || moduleConfig.tabPlayerListRunningEnabled ||
        moduleConfig.tabPlayerListResultEnabled

    /** 按参考标识符顺序排列等待玩家、存活队伍、原队淘汰成员和外部观战者。 */
    override fun tabPlayerListOrder(player: Player, defaultOrder: Int): Int {
        val teamOrderSpan = (gameConfig?.teams?.size ?: 1).coerceAtLeast(1) * TAB_TEAM_ORDER_STRIDE
        val eliminatedPlayerOrder = TAB_ACTIVE_PLAYER_ORDER + teamOrderSpan
        val externalSpectatorOrder = eliminatedPlayerOrder + teamOrderSpan
        if (player.uniqueId in room.spectators) {
            val spectatorIndex = room.spectators
                .mapNotNull(Bukkit::getPlayer)
                .sortedBy { it.name.lowercase() }
                .indexOfFirst { it.uniqueId == player.uniqueId }
                .coerceAtLeast(0)
            return externalSpectatorOrder + spectatorIndex
        }

        val onlinePlayers = room.players
            .mapNotNull(Bukkit::getPlayer)
            .sortedBy { it.name.lowercase() }
        if (phase == BedWarsPhase.WAITING || phase == BedWarsPhase.COUNTDOWN ||
            (phase == BedWarsPhase.CLOSING && !resultRecorded)
        ) {
            val playerIndex = onlinePlayers.indexOfFirst { it.uniqueId == player.uniqueId }.coerceAtLeast(0)
            return TAB_ACTIVE_PLAYER_ORDER + playerIndex
        }

        val state = playerStates[player.uniqueId] ?: return defaultOrder
        val teamIndex = gameConfig?.teams
            ?.indexOfFirst { it.id.equals(state.teamId, ignoreCase = true) }
            ?.takeIf { it >= 0 }
            ?: return defaultOrder
        val memberIndex = onlinePlayers
            .filter { candidate ->
                val candidateState = playerStates[candidate.uniqueId]
                candidateState?.teamId.equals(state.teamId, ignoreCase = true) &&
                    candidateState?.eliminated == state.eliminated
            }
            .indexOfFirst { it.uniqueId == player.uniqueId }
            .coerceAtLeast(0)
        val roleOrder = if (state.eliminated) eliminatedPlayerOrder else TAB_ACTIVE_PLAYER_ORDER
        return roleOrder + teamIndex * TAB_TEAM_ORDER_STRIDE + memberIndex
    }

    /** 观战快捷栏只循环仍可行动且保留参赛资格的玩家。 */
    override fun canSpectatorFollow(spectator: Player, target: Player): Boolean {
        val state = playerStates[target.uniqueId] ?: return false
        return target.uniqueId in room.players && state.participant && !state.eliminated &&
            !state.respawning && !state.disconnected && !target.isDead
    }

    /** 按 BedWars 阶段在等待、队伍、喊话和观战受众之间路由聊天。 */
    override fun routeChat(player: Player, message: String, requestedChannel: GameChatChannel): GameChatRoute? {
        markPlayerActive(player)
        val state = playerStates[player.uniqueId]
        if (isChatSpectator(player, state)) {
            return GameChatRoute(
                GameChatChannel.ROOM,
                message,
                variant = "spectator",
                audience = chatSpectatorAudience()
            )
        }
        if (phase == BedWarsPhase.WAITING || phase == BedWarsPhase.COUNTDOWN) {
            return GameChatRoute(GameChatChannel.ROOM, message)
        }
        if (phase != BedWarsPhase.RUNNING) return null
        val prefixedShout = stripShoutPrefix(message)
        if (requestedChannel == GameChatChannel.ROOM || prefixedShout != null) {
            return routeShout(player, prefixedShout ?: message)
        }
        val soloTeam = state?.let { playerState ->
            gameConfig?.teams?.firstOrNull { it.id == playerState.teamId }?.maxPlayers == 1
        } == true
        return GameChatRoute(
            GameChatChannel.TEAM,
            message,
            audience = if (soloTeam) (room.players + room.spectators).toSet() else null
        )
    }

    override fun onPrepare() {
        moduleConfig = configService.reload()
        gameConfig = room.configuredGame?.let(configService::readManagedGame)
        val configErrors = gameConfig?.validationErrors() ?: listOf("bedwars")
        if (configErrors.isNotEmpty()) {
            plugin.logger.warning(
                "Cannot prepare BedWars room ${room.id}; invalid map configuration: ${configErrors.joinToString(", ")}"
            )
            return
        }
        val teamCapacity = gameConfig?.teams.orEmpty().sumOf(BedWarsTeamConfig::maxPlayers)
        room.definition?.let { definition ->
            val maxPlayers = definition.maxPlayers.coerceIn(2, teamCapacity)
            val minPlayers = definition.minPlayers.coerceIn(2, maxPlayers)
            if (minPlayers != definition.minPlayers || maxPlayers != definition.maxPlayers) {
                plugin.logger.warning(
                    "Adjusted BedWars room ${room.id} capacity from ${definition.minPlayers}-${definition.maxPlayers} " +
                        "to $minPlayers-$maxPlayers for configured team capacity $teamCapacity"
                )
                room.definition = definition.copy(minPlayers = minPlayers, maxPlayers = maxPlayers)
            }
        }
        val worldName = "kgc_${room.id}"
        val template = room.mapTemplate ?: room.definition?.mapTemplates?.firstOrNull()
        val preparedWorld = room.templateDirectory?.let {
            worldService.createRoomWorldFromDirectory(it, worldName, allowFlatFallback = false)
        } ?: worldService.createRoomWorldFromTemplate(template, worldName, allowFlatFallback = false)
            ?: return
        applyArenaWorldBorder(preparedWorld)
        val worldErrors = gameConfig?.worldValidationErrors(preparedWorld).orEmpty()
        if (worldErrors.isNotEmpty()) {
            plugin.logger.warning(
                "Cannot prepare BedWars room ${room.id}; invalid map geometry: ${worldErrors.joinToString(", ")}"
            )
            worldService.unloadAndDelete(preparedWorld.name)
            return
        }
        val invalidBeds = gameConfig?.teams.orEmpty().mapNotNull { team ->
            val bed = team.bed?.toLocation(preparedWorld)?.block
            if (bed == null || !Tag.BEDS.isTagged(bed.type)) "teams.${team.id}.bed-block" else null
        }
        if (invalidBeds.isNotEmpty()) {
            plugin.logger.warning(
                "Cannot prepare BedWars room ${room.id}; invalid map blocks: ${invalidBeds.joinToString(", ")}"
            )
            worldService.unloadAndDelete(preparedWorld.name)
            return
        }
        room.world = preparedWorld
        resourceScope = roomResourceScopeService.open(room.id)
        preparedWorld.spawnLocation = gameConfig?.lobby?.toLocation(preparedWorld)
            ?: worldService.readTemplateSpawn(template, preparedWorld)
        preparedWorld.setGameRule(GameRules.KEEP_INVENTORY, false)
        preparedWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        applyArenaGameRules(preparedWorld)
        handleEntitiesLoaded(preparedWorld.entities)
        registerTeams()
        indexBeds()
    }

    /** 应用参考格式的竞技场 game-rules，并忽略带警告的非法名称或值。 */
    @Suppress("DEPRECATION")
    private fun applyArenaGameRules(world: World) {
        val rules = gameConfig?.gameRules ?: BEDWARS_DEFAULT_GAME_RULES
        rules.forEach { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator == entry.lastIndex) {
                plugin.logger.warning("Ignoring invalid BedWars game rule '$entry' for room ${room.id}")
                return@forEach
            }
            val name = entry.substring(0, separator).trim()
            val value = entry.substring(separator + 1).trim()
            if (name.isEmpty() || value.isEmpty() || !world.setGameRuleValue(name, value)) {
                plugin.logger.warning("Ignoring invalid BedWars game rule '$entry' for room ${room.id}")
            }
        }
    }

    /** 按参考默认值收窄过大的世界边界，同时保留主插件更小的模板边界。 */
    private fun applyArenaWorldBorder(world: World) {
        val size = gameConfig?.worldBorderSize ?: moduleConfig.worldBorderSize
        if (size <= 0 || world.worldBorder.size <= size.toDouble()) return
        val center = gameConfig?.lobby?.toLocation(world) ?: world.spawnLocation
        world.worldBorder.setCenter(center)
        world.worldBorder.setSize(size.toDouble())
    }

    override fun onPlayerJoin(player: Player) {
        val world = room.world ?: return
        hideBedHologramsFrom(player)
        eliminationService.clear(room.id, player.uniqueId)
        playerRuntimeStateService.captureIfAbsent(room.id, player)
        player.enderChest.clear()
        resetPlayer(player, GameMode.ADVENTURE, clearInventory = true)
        if (phase != BedWarsPhase.WAITING && phase != BedWarsPhase.COUNTDOWN) {
            enterEliminatedSpectator(player)
            return
        }
        val teamId = assignTeam(player)
        if (teamId == null) {
            player.sendMessage(Component.text(language.getMessage("bedwars.team_full")))
            playSoundRule(listOf(player), moduleConfig.joinDeniedSound)
            roomTaskService.runTaskLater(room.id, 1L, Runnable {
                if (roomManager.getPlayerRoom(player)?.id == room.id) roomManager.leaveCurrentRoom(player)
            })
            return
        }
        val state = playerStates.getOrPut(player.uniqueId) { BedWarsPlayerState(teamId) }
        state.teamId = teamId
        lastCombatHits.remove(player.uniqueId)
        lastSpecialMobHits.remove(player.uniqueId)
        lastShoutTicks.remove(player.uniqueId)
        markPlayerActive(player)
        disconnectStates.remove(player.uniqueId)
        state.participant = true
        state.eliminated = false
        participants.add(player.uniqueId)
        val target = if (phase == BedWarsPhase.COUNTDOWN) teamSpawn(teamId) else gameConfig?.lobby?.toLocation(world)
        player.teleport(target ?: world.spawnLocation)
        setTeamNametag(player, teamId)
        if (phase == BedWarsPhase.COUNTDOWN) spawnBedHologram(player, teamId)
        if (phase == BedWarsPhase.WAITING) givePreGameItems(player)
        playSoundRule(listOf(player), moduleConfig.joinAllowedSound)
        scheduleHalloweenAmbience(player)
        player.sendMessage(Component.text(language.getMessage("bedwars.joined", room.id)))
        updateDisplay(player)
        updateTabHeaderFooter(player)
        updateTabPlayerName(player)
    }

    override fun onPlayerLeave(player: Player) {
        quickBuyAssignments.remove(player.uniqueId)
        halloweenAmbienceTasks.remove(player.uniqueId)?.cancel()
        healPoolPlayers.remove(player.uniqueId)
        invisiblePlayers.remove(player.uniqueId)
        lastCombatHits.remove(player.uniqueId)
        lastSpecialMobHits.remove(player.uniqueId)
        lastShoutTicks.remove(player.uniqueId)
        inactivitySeconds.remove(player.uniqueId)
        afkPlayers.remove(player.uniqueId)
        playerBaseRegions.remove(player.uniqueId)
        tabHeaderFooterTemplates.remove(player.uniqueId)
        tabHeaderFooterFrames.remove(player.uniqueId)
        tabPlayerNameTemplates.remove(player.uniqueId)
        tabPlayerNameFrames.remove(player.uniqueId)
        sidebarLineTemplates.remove(player.uniqueId)
        sidebarLineFrames.remove(player.uniqueId)
        lastCombatHits.entries.removeIf { it.value.attackerId == player.uniqueId }
        disconnectStates.remove(player.uniqueId)
        removeBedHologram(player.uniqueId)
        val state = playerStates[player.uniqueId]
        if (phase == BedWarsPhase.WAITING) {
            playerStates.remove(player.uniqueId)
            participants.remove(player.uniqueId)
        } else if (state != null && !state.eliminated) {
            state.eliminated = true
            state.respawning = false
            if (phase == BedWarsPhase.RUNNING) {
                if (moduleConfig.markLeaveAsAbandon) {
                    state.participant = false
                    participants.remove(player.uniqueId)
                }
                announceTeamEliminated(state.teamId)
            }
        }
        nametagService.clear(room, player)
        sidebarViewers.remove(player.uniqueId)
        playerRuntimeStateService.restore(room.id, player)
        if (phase == BedWarsPhase.RUNNING) checkWinner()
    }

    override fun onSpectatorJoin(player: Player) {
        hideBedHologramsFrom(player)
        if (player.gameMode != GameMode.SPECTATOR || player.spectatorTarget == null) {
            room.world?.let { player.teleport(spectatorSpawn()) }
        }
        nametagService.refreshViewer(room, player)
        refreshInvisibleAppearanceForViewer(player)
        updateDisplay(player)
        updateTabHeaderFooter(player)
        updateTabPlayerName(player)
        playSoundRule(listOf(player), moduleConfig.spectateAllowedSound)
    }

    override fun onSpectatorLeave(player: Player) {
        nametagService.clearViewer(player)
        sidebarViewers.remove(player.uniqueId)
        tabHeaderFooterTemplates.remove(player.uniqueId)
        tabHeaderFooterFrames.remove(player.uniqueId)
        tabPlayerNameTemplates.remove(player.uniqueId)
        tabPlayerNameFrames.remove(player.uniqueId)
        sidebarLineTemplates.remove(player.uniqueId)
        sidebarLineFrames.remove(player.uniqueId)
    }

    /** 播放托管观战者选择有效目标时的参考点击反馈。 */
    fun playSpectatorTargetClick(player: Player) {
        if (roomManager.getPlayerRoom(player)?.id != room.id) return
        playSoundRule(listOf(player), moduleConfig.spectatorTargetClickSound)
    }

    override fun onStart() {
        if (phase != BedWarsPhase.WAITING) return
        val configured = gameConfig ?: return failConfiguration(listOf("bedwars"))
        val errors = configured.validationErrors().toMutableList()
        if (configured.teams.sumOf { it.maxPlayers } < room.players.size) errors += "teams.capacity"
        configured.teams.forEach { team ->
            val bed = team.bed?.toLocation(room.world ?: return@forEach)?.block
            if (bed == null || !Tag.BEDS.isTagged(bed.type)) errors += "teams.${team.id}.bed-block"
        }
        if (errors.isNotEmpty()) return failConfiguration(errors.distinct())

        participants.clear()
        participants.addAll(room.players)
        resultRecorded = false
        closed = false
        gameElapsedTicks = 0
        sidebarPlaceholderTicks = 0
        sidebarTitleTicks = 0
        sidebarTitleFrame = 0
        sidebarTitlePhase = BedWarsPhase.WAITING
        healthAnimationTicks = 0
        healthAnimationFrame = 0
        bedsDestroyedByTimer = false
        suddenDeathStarted = false
        timelineInitialized = false
        currentTimelineStageId = null
        invisiblePlayers.clear()
        lastCombatHits.clear()
        lastSpecialMobHits.clear()
        pendingDeathCauses.clear()
        disconnectStates.clear()
        lastShoutTicks.clear()
        inactivitySeconds.clear()
        afkPlayers.clear()
        playerBaseRegions.clear()
        eliminatedTeams.clear()
        healPoolPlayers.clear()
        placedBlocks.clear()
        halloweenCobwebs.clear()
        teamStates.clear()
        configured.teams.forEach { teamStates[it.id] = BedWarsTeamState(it) }
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val teamId = teamService.getTeam(room.id, player.uniqueId)?.id ?: assignTeam(player)
                ?: return failConfiguration(listOf("teams.capacity"))
            val state = playerStates.getOrPut(player.uniqueId) { BedWarsPlayerState(teamId) }
            state.teamId = teamId
            state.participant = true
            state.eliminated = false
            state.respawning = false
            state.firstSpawned = false
            state.respawnTicks = 0
            state.respawnProtectionUntilTick = 0
            state.kills = 0
            state.deaths = 0
            state.bedsBroken = 0
            state.finalKills = 0
            state.finalDeaths = 0
            state.armorTier = 0
            state.pickaxeTier = 0
            state.axeTier = 0
            state.shears = false
            state.disconnected = false
            state.categoryWeights.clear()
            state.permanentProductIds.clear()
            state.enteredEnemyBases.clear()
            resetPlayer(player, GameMode.ADVENTURE, clearInventory = true)
            player.teleport(teamSpawn(teamId) ?: return@forEach)
            setTeamNametag(player, teamId)
        }
        indexBeds()
        prepareGenerators()
        spawnShopNpcs()
        spawnBedHolograms()
        phase = BedWarsPhase.COUNTDOWN
        phaseTimer.resetSeconds(moduleConfig.countdownSeconds)
        room.state = GameState.COUNTDOWN
        roomBroadcastService.localized(
            room,
            language,
            "bedwars.countdown_started",
            moduleConfig.countdownSeconds,
            includeSpectators = true
        )
        updateDisplays()
        updateTabHeaderFooters()
        updateTabPlayerNames()
    }

    override fun onTick() {
        when (phase) {
            BedWarsPhase.WAITING -> tickWaitingTeamSelections()
            BedWarsPhase.COUNTDOWN -> tickCountdown()
            BedWarsPhase.RUNNING -> tickRunning()
            BedWarsPhase.RESULT -> tickResult()
            BedWarsPhase.CLOSING -> tickClosing()
        }
        var refreshHealthAnimation = false
        if (phase == BedWarsPhase.RUNNING && moduleConfig.healthDisplayEnabled &&
            moduleConfig.healthAnimationRefreshTicks > 0
        ) {
            healthAnimationTicks++
            if (healthAnimationTicks >= moduleConfig.healthAnimationRefreshTicks) {
                healthAnimationTicks = 0
                healthAnimationFrame = if (healthAnimationFrame == Int.MAX_VALUE) 0 else healthAnimationFrame + 1
                refreshHealthAnimation = true
            }
        } else {
            healthAnimationTicks = 0
        }
        val sidebarEnabled = isSidebarEnabledForCurrentPhase()
        var refreshSidebar = refreshHealthAnimation
        var advanceSidebarAnimation = false
        if (sidebarEnabled && moduleConfig.sidebarPlaceholdersRefreshTicks > 0) {
            sidebarPlaceholderTicks++
            if (sidebarPlaceholderTicks >= moduleConfig.sidebarPlaceholdersRefreshTicks) {
                sidebarPlaceholderTicks = 0
                refreshSidebar = true
                advanceSidebarAnimation = true
            }
        } else {
            sidebarPlaceholderTicks = 0
        }
        if (refreshSidebar) updateDisplays(advanceSidebarAnimation)
        tickSidebarTitleAnimation(sidebarEnabled)
        if (moduleConfig.tabHeaderFooterEnabled && moduleConfig.tabHeaderFooterRefreshTicks > 0) {
            tabHeaderFooterTicks++
            if (tabHeaderFooterTicks >= moduleConfig.tabHeaderFooterRefreshTicks) {
                tabHeaderFooterTicks = 0
                updateTabHeaderFooters(advanceAnimation = true)
            }
        }
        if (usesCustomTabPlayerNames() && moduleConfig.tabPlayerListRefreshTicks > 0) {
            tabPlayerListTicks++
            if (tabPlayerListTicks >= moduleConfig.tabPlayerListRefreshTicks) {
                tabPlayerListTicks = 0
                updateTabPlayerNames(advanceAnimation = true)
            }
        }
    }

    /** 非运行阶段及床、种子材料生成时禁止产生地面物品。 */
    fun shouldBlockItemSpawn(material: Material): Boolean {
        return phase != BedWarsPhase.RUNNING || Tag.BEDS.isTagged(material) || material == Material.WHEAT_SEEDS
    }

    /** 按生成器 stack-items 开关隔离带房间标记的资源实体合并。 */
    fun shouldCancelResourceMerge(source: Item, target: Item): Boolean {
        if (moduleConfig.generatorRules.stackItems) return false
        return GENERATOR_RESOURCE_TAG in source.scoreboardTags || GENERATOR_RESOURCE_TAG in target.scoreboardTags
    }

    /** 在成功拾取后释放已被完全取走的房间物品追踪。 */
    fun handleTrackedItemPickup(item: Item, remaining: Int) {
        if (remaining <= 0) releaseTrackedEntity(item.uniqueId)
    }

    /** 在玩家拾取最终成功后替换出生默认剑，并同步地面实体追踪。 */
    fun handlePlayerPickupComplete(player: Player, item: Item, remaining: Int) {
        if (roomManager.getPlayerRoom(player)?.session !== this || !isActiveParticipant(player)) return
        if (item.itemStack.type.name.endsWith("_SWORD")) removeDefaultSwords(player)
        handleTrackedItemPickup(item, remaining)
    }

    /** 在允许自然合并时保留生成器标记，并释放即将消失的源实体追踪。 */
    fun handleResourceMerge(source: Item, target: Item) {
        val tracked = source.uniqueId in trackedEntities || target.uniqueId in trackedEntities
        if (GENERATOR_RESOURCE_TAG in source.scoreboardTags || GENERATOR_RESOURCE_TAG in target.scoreboardTags) {
            target.addScoreboardTag(GENERATOR_RESOURCE_TAG)
        }
        if (tracked && target.uniqueId !in trackedEntities) trackEntity(target, type = "merged-item")
        releaseTrackedEntity(source.uniqueId)
    }

    /** 在物品自然消失后释放其房间实体追踪。 */
    fun handleTrackedItemDespawn(item: Item) {
        releaseTrackedEntity(item.uniqueId)
    }

    /** 清除区块中除玩家、悬挂装饰和本局登记资源外的模板实体。 */
    fun handleEntitiesLoaded(entities: Collection<Entity>) {
        entities.filter { entity ->
            entity !is Player &&
                entity !is Painting &&
                entity !is ItemFrame &&
                entity.uniqueId !in trackedEntities
        }.forEach(Entity::remove)
    }

    /** 床全息影响原版判定时，允许正式玩家在床正上方放置防御方块。 */
    fun handleBlockCanBuild(event: BlockCanBuildEvent) {
        if (event.isBuildable || phase != BedWarsPhase.RUNNING) return
        val player = event.player ?: return
        if (!isActiveParticipant(player)) return
        val block = event.block
        val below = BedWarsBlockKey(block.world.uid, block.x, block.y - 1, block.z)
        if (below in bedBlocks) event.isBuildable = true
    }

    /** 先硬拒绝队伍传送子命令，再应用根标签白名单和管理员绕过。 */
    fun handleCommand(player: Player, message: String): Boolean {
        val commandParts = message.trim().removePrefix("/")
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .map(String::lowercase)
        val rawRoot = commandParts.firstOrNull() ?: return false
        if (rawRoot.isBlank()) return false
        val rootLabel = rawRoot.substringAfter(':')
        if (rootLabel == "party" && commandParts.drop(1) in FORBIDDEN_PARTY_ARGUMENTS) {
            player.sendMessage(Component.text(language.getMessage("bedwars.command_not_allowed")))
            return true
        }
        if (player.hasPermission("kagamecenter.admin")) return false
        val pluginRoot = rawRoot.takeIf { it.startsWith("kagamecenter:") }?.substringAfter(':')
        if (rawRoot in moduleConfig.allowedCommands || pluginRoot?.let { it in moduleConfig.allowedCommands } == true) return false
        player.sendMessage(Component.text(language.getMessage("bedwars.command_not_allowed")))
        return true
    }

    override fun onPlayerDeath(player: Player) {
        if (phase != BedWarsPhase.RUNNING) return
        val state = playerStates[player.uniqueId] ?: return
        if (state.eliminated || state.respawning) return
        val damageCause = resolvedDeathDamageCause(player)
        val specialHit = directSpecialMobDeathHit(player, damageCause)
        val killer = resolveKiller(player)
        state.deaths++
        state.respawnProtectionUntilTick = 0
        leavePlayerBaseRegion(player)
        clearInvisibleAppearance(player, notify = false)
        state.trapImmuneUntilMillis = 0L
        downgradeTools(state)
        val bedAlive = teamStates[state.teamId]?.bedAlive == true
        if (bedAlive) {
            state.respawning = true
            state.respawnTicks = -1
            updatePlayerCollision(player, state.teamId)
            showDeathTitle(player)
            val messageKey = if (killer == null && specialHit != null) {
                specialMobDeathKey(specialHit.specialId, finalKill = false)
            } else {
                deathMessageKey(killer, damageCause, finalKill = false)
            }
            sendDeathFeedback(
                victimId = player.uniqueId,
                victim = player,
                victimTeamId = state.teamId,
                killerId = killer?.uniqueId,
                killer = killer,
                damageCause = damageCause,
                sourceId = specialHit?.specialId,
                finalDeath = false,
                message = Component.text(language.getMessage(
                    messageKey,
                    player.name,
                    killer?.name ?: specialHit?.let {
                        teamStates[it.teamId]?.config?.displayName ?: it.teamId
                    } ?: "-"
                ))
            )
            publishDeathResolved(player, state.teamId, killer, damageCause, specialHit?.specialId, finalDeath = false)
        } else {
            eliminate(player, state, killer, specialHit, damageCause)
        }
        lastCombatHits.remove(player.uniqueId)
        lastSpecialMobHits.remove(player.uniqueId)
    }

    override fun onPlayerKill(killer: Player, victim: Player) {
        val state = playerStates[killer.uniqueId] ?: return
        val victimState = playerStates[victim.uniqueId] ?: return
        if (state.eliminated || state.teamId == victimState.teamId) return
        state.kills++
        val finalKill = teamStates[victimState.teamId]?.bedAlive == false
        if (finalKill) {
            state.finalKills++
            resultService.addMetric(room, killer.uniqueId, METRIC_FINAL_KILLS)
        }
        awardLevelExperience(
            killer.uniqueId,
            if (finalKill) moduleConfig.levelRules.finalKillExperience else moduleConfig.levelRules.regularKillExperience,
            if (finalKill) XP_SOURCE_FINAL_KILL else XP_SOURCE_REGULAR_KILL
        )
        awardMoney(
            killer,
            if (finalKill) moduleConfig.moneyRewardRules.finalKill else moduleConfig.moneyRewardRules.regularKill,
            if (finalKill) "bedwars.money_reward_final_kill" else "bedwars.money_reward_regular_kill"
        )
        spawnHalloweenKillCobweb(victim)
    }

    /** 优先使用 Bukkit 直接击杀者，并按摔落 10 秒、其他原因 15 秒回溯敌方攻击者。 */
    override fun resolveKiller(victim: Player): Player? {
        val victimState = playerStates[victim.uniqueId] ?: return null
        val specialHit = recentSpecialMobHit(victim)
        val playerHit = lastCombatHits[victim.uniqueId]
        if (specialHit != null && (playerHit == null || specialHit.gameTick >= playerHit.gameTick)) return null
        validKiller(victim.killer, victim, victimState)?.let { return it }
        val lastHit = playerHit ?: return null
        val windowTicks = if (victim.isDead &&
            resolvedDeathDamageCause(victim) == EntityDamageEvent.DamageCause.FALL
        ) {
            PLAYER_PUSH_WINDOW_TICKS
        } else {
            LAST_HIT_WINDOW_TICKS
        }
        if (gameElapsedTicks - lastHit.gameTick !in 0..windowTicks) return null
        return validKiller(Bukkit.getPlayer(lastHit.attackerId), victim, victimState)
    }

    /** 在倒计时或对局阶段为未淘汰玩家声明断线重连宽限。 */
    override fun reconnectGraceTicks(player: Player): Long {
        val state = playerStates[player.uniqueId] ?: return 0L
        if (!state.participant || state.eliminated) return 0L
        if (phase != BedWarsPhase.COUNTDOWN && phase != BedWarsPhase.RUNNING) return 0L
        return moduleConfig.reconnectGraceSeconds * 20L
    }

    /** 标记玩家暂时断线但保留队伍、生存状态和房间席位。 */
    override fun onPlayerDisconnect(player: Player) {
        val state = playerStates[player.uniqueId] ?: return
        quickBuyAssignments.remove(player.uniqueId)
        halloweenAmbienceTasks.remove(player.uniqueId)?.cancel()
        state.disconnected = true
        updatePlayerCollision(player, state.teamId)
        removeBedHologram(player.uniqueId)
        disconnectStates[player.uniqueId] = BedWarsDisconnectState(
            player.location.clone(),
            player.inventory.contents.filterNotNull()
                .filter { it.type in DEATH_TRANSFER_RESOURCES }
                .map(ItemStack::clone),
            resolveKiller(player)?.uniqueId
        )
        inactivitySeconds.remove(player.uniqueId)
        afkPlayers.remove(player.uniqueId)
        playerBaseRegions.remove(player.uniqueId)
        if (state.respawning && state.respawnTicks < 0) {
            state.respawnTicks = moduleConfig.respawnSeconds * 20
        }
        roomBroadcastService.localized(
            room,
            language,
            "bedwars.disconnected",
            player.name,
            moduleConfig.reconnectGraceSeconds,
            includeSpectators = true
        )
    }

    /** 运行期重连按参考行为重新进入配置的复活倒计时。 */
    override fun reconnectRespawnDelayTicks(player: Player): Long? {
        val state = playerStates[player.uniqueId] ?: return null
        if (phase != BedWarsPhase.RUNNING || !state.participant || state.eliminated) return null
        return moduleConfig.respawnSeconds * 20L
    }

    /** 在玩法恢复前应用监听器确认的重连复活 tick，并限制异常超长值。 */
    override fun applyReconnectRespawnDelayTicks(player: Player, ticks: Long) {
        val state = playerStates[player.uniqueId] ?: return
        if (phase != BedWarsPhase.RUNNING || !state.participant || state.eliminated) return
        state.respawning = true
        state.respawnTicks = ticks.coerceIn(0L, MAX_RECONNECT_RESPAWN_TICKS).toInt()
        state.respawnProtectionUntilTick = 0
    }

    /** 恢复宽限期内返回玩家的阶段、位置、装备强化和显示。 */
    override fun onPlayerReconnect(player: Player) {
        val state = playerStates[player.uniqueId] ?: return
        disconnectStates.remove(player.uniqueId)
        state.disconnected = false
        markPlayerActive(player)
        playerRuntimeStateService.captureIfAbsent(room.id, player)
        var completedRespawn = false
        var completedFirstSpawn = false
        when {
            state.eliminated -> enterEliminatedSpectator(player)
            phase == BedWarsPhase.WAITING -> {
                state.participant = true
                resetPlayer(player, GameMode.ADVENTURE, clearInventory = true)
                player.teleport(gameConfig?.lobby?.toLocation(player.world) ?: player.world.spawnLocation)
                givePreGameItems(player)
            }
            phase == BedWarsPhase.COUNTDOWN -> {
                resetPlayer(player, GameMode.ADVENTURE, clearInventory = false)
                teamSpawn(state.teamId)?.let(player::teleport)
            }
            state.respawning && state.respawnTicks > 0 -> {
                player.gameMode = GameMode.SPECTATOR
                player.spectatorTarget = null
                teamSpawn(state.teamId)?.let(player::teleport)
            }
            state.respawning -> {
                state.respawning = false
                resetPlayer(player, GameMode.SURVIVAL, clearInventory = true)
                giveLoadout(player, state)
                startRespawnProtection(state)
                teamSpawn(state.teamId)?.let(player::teleport)
                showRespawnedTitle(player)
                completedRespawn = true
            }
            !state.firstSpawned -> {
                resetPlayer(player, GameMode.SURVIVAL, clearInventory = true)
                giveLoadout(player, state)
                teamSpawn(state.teamId)?.let(player::teleport)
                state.firstSpawned = true
                completedFirstSpawn = true
            }
            else -> {
                player.gameMode = GameMode.SURVIVAL
                player.isInvulnerable = false
                teamSpawn(state.teamId)?.let(player::teleport)
                applyArmor(player, state)
                applyTeamUpgrades(player, state, restoreTeamEffects = true)
            }
        }
        setTeamNametag(player, state.teamId)
        if (phase == BedWarsPhase.COUNTDOWN || phase == BedWarsPhase.RUNNING) {
            spawnBedHologram(player, state.teamId)
        }
        updateDisplay(player)
        updateTabHeaderFooter(player)
        updateTabPlayerName(player)
        refreshInvisibleAppearanceForViewer(player)
        if (completedRespawn) {
            Bukkit.getPluginManager().callEvent(GamePlayerRespawnedEvent(room, player, state.teamId))
        }
        if (completedFirstSpawn) {
            Bukkit.getPluginManager().callEvent(GamePlayerFirstSpawnedEvent(room, player, state.teamId))
        }
        playSoundRule(listOf(player), moduleConfig.rejoinAllowedSound)
        scheduleHalloweenAmbience(player)
        roomBroadcastService.localized(room, language, "bedwars.reconnected", player.name, includeSpectators = true)
    }

    /** 宽限到期后把离线玩家最终淘汰并释放模块运行状态。 */
    override fun onPlayerReconnectExpired(playerId: UUID) {
        val state = playerStates[playerId] ?: return
        val disconnected = disconnectStates.remove(playerId)
        lastShoutTicks.remove(playerId)
        if (phase == BedWarsPhase.WAITING) {
            playerStates.remove(playerId)
            participants.remove(playerId)
            quickBuyAssignments.remove(playerId)
            lastCombatHits.remove(playerId)
            lastSpecialMobHits.remove(playerId)
            playerRuntimeStateService.clear(room.id, playerId)
            return
        }
        val alreadyEliminated = state.eliminated
        state.disconnected = false
        state.eliminated = true
        state.respawning = false
        state.respawnProtectionUntilTick = 0
        lastCombatHits.remove(playerId)
        playerRuntimeStateService.clear(room.id, playerId)
        if (alreadyEliminated) return
        val name = Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString().take(8)
        val attackerId = disconnected?.attackerId
        val finalDeath = phase == BedWarsPhase.RUNNING && teamStates[state.teamId]?.bedAlive == false
        if (finalDeath) {
            state.finalDeaths++
            resultService.addMetric(room, playerId, METRIC_FINAL_DEATHS)
        }
        var feedbackMessage: Component? = null
        if (phase == BedWarsPhase.RUNNING && attackerId != null) {
            roomManager.recordKill(attackerId, playerId, room.module.id, points = 1)
            if (finalDeath) resultService.addMetric(room, attackerId, METRIC_FINAL_KILLS)
            playerStates[attackerId]?.let { attackerState ->
                attackerState.kills++
                if (finalDeath) attackerState.finalKills++
            }
            awardLevelExperience(
                attackerId,
                if (finalDeath) moduleConfig.levelRules.finalKillExperience else moduleConfig.levelRules.regularKillExperience,
                if (finalDeath) XP_SOURCE_FINAL_KILL else XP_SOURCE_REGULAR_KILL
            )
            Bukkit.getPlayer(attackerId)?.let { attacker ->
                awardMoney(
                    attacker,
                    if (finalDeath) moduleConfig.moneyRewardRules.finalKill else moduleConfig.moneyRewardRules.regularKill,
                    if (finalDeath) "bedwars.money_reward_final_kill" else "bedwars.money_reward_regular_kill"
                )
            }
            dropDisconnectedResources(disconnected)
            val attackerName = Bukkit.getOfflinePlayer(attackerId).name ?: attackerId.toString().take(8)
            feedbackMessage = Component.text(language.getMessage(
                if (finalDeath) {
                    "bedwars.reconnect_expired_final_killed"
                } else {
                    "bedwars.reconnect_expired_killed"
                },
                name,
                attackerName
            ))
        } else {
            if (phase == BedWarsPhase.RUNNING) roomManager.recordDeath(playerId, room.module.id)
            if (phase == BedWarsPhase.RUNNING) {
                feedbackMessage = Component.text(language.getMessage("bedwars.reconnect_expired", name))
            } else {
                roomBroadcastService.localized(room, language, "bedwars.reconnect_expired", name, includeSpectators = true)
            }
        }
        if (phase == BedWarsPhase.RUNNING) {
            val attacker = attackerId?.let(Bukkit::getPlayer)
            sendDeathFeedback(
                victimId = playerId,
                victim = null,
                victimTeamId = state.teamId,
                killerId = attackerId,
                killer = attacker,
                damageCause = null,
                sourceId = "disconnect",
                finalDeath = finalDeath,
                message = feedbackMessage
            )
            publishDeathResolved(
                victimId = playerId,
                victim = null,
                victimTeamId = state.teamId,
                killerId = attackerId,
                killer = attacker,
                damageCause = null,
                sourceId = "disconnect",
                finalDeath = finalDeath
            )
        }
        announceTeamEliminated(state.teamId)
        checkWinner()
    }

    override fun onEnd() {
        clearHealPoolEffects()
        restoreAllInvisibleAppearances()
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            nametagService.clear(room, player)
            SidebarBoardRenderer.clear(player)
            playerRuntimeStateService.restore(room.id, player)
            player.sendMessage(Component.text(language.getMessage("bedwars.ended")))
        }
        room.spectators.mapNotNull(Bukkit::getPlayer).forEach { spectator ->
            nametagService.clearViewer(spectator)
            SidebarBoardRenderer.clear(spectator)
        }
        sidebarViewers.clear()
        sidebarLineTemplates.clear()
        sidebarLineFrames.clear()
    }

    override fun onClose() {
        clearHealPoolEffects()
        restoreAllInvisibleAppearances()
        removeShopNpcs()
        removeGeneratorHolograms()
        roomResourceScopeService.closeRoom(room.id)
        eliminationService.clearRoom(room.id)
        trackedEntities.clear()
        nametagService.clearRoom(room.id)
        teamService.clearRoom(room.id)
        playerRuntimeStateService.clearRoom(room.id)
        playerStates.clear()
        teamStates.clear()
        bedBlocks.clear()
        placedBlocks.clear()
        halloweenCobwebs.clear()
        generatorStates.clear()
        announcedGeneratorTiers.clear()
        timelineInitialized = false
        currentTimelineStageId = null
        shopNpcs.clear()
        shopHolograms.clear()
        bridgeStates.clear()
        specialMobs.clear()
        fireballCooldowns.clear()
        halloweenAmbienceTasks.clear()
        quickBuyAssignments.clear()
        towerStates.clear()
        dragonStates.clear()
        invisiblePlayers.clear()
        lastCombatHits.clear()
        lastSpecialMobHits.clear()
        pendingDeathCauses.clear()
        disconnectStates.clear()
        lastShoutTicks.clear()
        inactivitySeconds.clear()
        afkPlayers.clear()
        playerBaseRegions.clear()
        eliminatedTeams.clear()
        bedHolograms.clear()
        healPoolPlayers.clear()
        sidebarViewers.clear()
        tabHeaderFooterTemplates.clear()
        tabHeaderFooterFrames.clear()
        tabPlayerNameTemplates.clear()
        tabPlayerNameFrames.clear()
        sidebarPlaceholderTicks = 0
        sidebarTitleTicks = 0
        sidebarTitleFrame = 0
        sidebarTitlePhase = BedWarsPhase.WAITING
        healthAnimationTicks = 0
        healthAnimationFrame = 0
        tabHeaderFooterTicks = 0
        tabPlayerListTicks = 0
        resultWinnerTeamId = null
        participants.clear()
        resourceScope = null
        phase = BedWarsPhase.CLOSING
    }

    /** 按等待、复活、观战和运行状态处理玩家进入虚空后的行为。 */
    fun handleMove(player: Player, to: Location) {
        val state = playerStates[player.uniqueId]
        if (state?.respawning == true) {
            rescueRespawningPlayerFromVoid(player, state, to)
            return
        }
        if (roomManager.isSpectator(player.uniqueId) || state?.eliminated == true) {
            rescueSpectatorFromVoid(player, to)
            return
        }
        if (state == null) return
        if (phase == BedWarsPhase.WAITING) {
            if (moduleConfig.lobbyVoidTeleportEnabled && to.y < moduleConfig.lobbyVoidHeight) {
                player.teleport(gameConfig?.lobby?.toLocation(player.world) ?: player.world.spawnLocation)
            }
            return
        }
        if (phase == BedWarsPhase.COUNTDOWN) {
            teamSpawn(state.teamId)?.let(player::teleport)
            return
        }
        if (phase != BedWarsPhase.RUNNING) return
        updatePlayerBaseRegion(player, to)
        updateBedHologramVisibility(player, state, to)
        if (to.y <= (gameConfig?.voidY ?: moduleConfig.defaultVoidY)) {
            pendingDeathCauses[player.uniqueId] = EntityDamageEvent.DamageCause.VOID
            player.health = 0.0
            pendingDeathCauses.remove(player.uniqueId)
            return
        }
        updateHealPoolForPlayer(player, state, to)
        checkEnemyBaseEntry(player, state, to)
    }

    /** 在成功的同世界传送提交前，使用目标位置同步基地相关状态。 */
    fun handleTeleport(player: Player, to: Location) {
        if (phase != BedWarsPhase.RUNNING || to.world != room.world) return
        val state = playerStates[player.uniqueId] ?: return
        if (state.eliminated || state.respawning || !state.participant) return
        updatePlayerBaseRegion(player, to)
        updateBedHologramVisibility(player, state, to)
        updateHealPoolForPlayer(player, state, to)
        checkEnemyBaseEntry(player, state, to)
    }

    /** 将落入虚空的复活中玩家送回队伍出生点并保持飞行状态。 */
    private fun rescueRespawningPlayerFromVoid(player: Player, state: BedWarsPlayerState, to: Location) {
        if (to.y >= NON_PLAYING_VOID_Y) return
        player.teleport(teamSpawn(state.teamId) ?: spectatorSpawn())
        player.allowFlight = true
        player.isFlying = true
    }

    /** 将落入虚空的外部或已淘汰观战者送回地图观战点。 */
    private fun rescueSpectatorFromVoid(player: Player, to: Location) {
        if (to.y >= NON_PLAYING_VOID_Y) return
        player.teleport(spectatorSpawn())
        player.allowFlight = true
        player.isFlying = true
    }

    /** 按阶段、存活状态和队伍关系判断是否取消伤害。 */
    fun handleDamage(event: EntityDamageEvent): Boolean {
        val player = event.entity as? Player ?: return false
        val state = playerStates[player.uniqueId] ?: return true
        val cancelled = phase != BedWarsPhase.RUNNING || state.eliminated || state.respawning
        if (!cancelled && isRespawnProtected(state)) return true
        return cancelled
    }

    /** 阻止队友伤害，并隔离其他房间或已淘汰玩家造成的攻击。 */
    fun handleDamageByEntity(event: EntityDamageByEntityEvent): Boolean {
        val fireball = event.damager as? Fireball
        if (event.entity is Player &&
            fireball?.persistentDataContainer?.get(specialItemKey, PersistentDataType.STRING) == "fireball"
        ) return true
        val attackedDragon = dragonStates[event.entity.uniqueId]
        if (attackedDragon != null) {
            val attacker = directPlayer(event) ?: return true
            val attackerState = playerStates[attacker.uniqueId] ?: return true
            return attackerState.eliminated || attackerState.respawning || attackerState.teamId == attackedDragon.teamId
        }
        val attackedSpecialMob = specialMobs[event.entity.uniqueId]
        if (attackedSpecialMob != null) {
            val attacker = directPlayer(event) ?: return false
            val attackerState = playerStates[attacker.uniqueId] ?: return true
            return phase != BedWarsPhase.RUNNING ||
                attackerState.eliminated ||
                attackerState.respawning ||
                attackerState.teamId == attackedSpecialMob.teamId
        }
        val victim = event.entity as? Player ?: return false
        val tnt = event.damager as? TNTPrimed
        if (tnt != null) return handleTntDamage(event, tnt, victim)
        val dragon = dragonStates[event.damager.uniqueId]
        if (dragon != null) {
            val victimState = playerStates[victim.uniqueId] ?: return true
            if (victimState.eliminated || victimState.respawning || victimState.teamId == dragon.teamId) return true
            if (isRespawnProtected(victimState)) return true
            event.damage = moduleConfig.dragonRules.damage
            return false
        }
        val specialMob = specialMobs[event.damager.uniqueId]
        if (specialMob != null) {
            val victimState = playerStates[victim.uniqueId] ?: return true
            if (victimState.eliminated || victimState.respawning || victimState.teamId == specialMob.teamId) return true
            if (isRespawnProtected(victimState)) return true
            if (specialMob.damage >= 0.0) event.damage = specialMob.damage
            return false
        }
        val attacker = directPlayer(event) ?: return false
        val victimState = playerStates[victim.uniqueId] ?: return true
        val attackerState = playerStates[attacker.uniqueId] ?: return true
        if (phase != BedWarsPhase.RUNNING || victimState.eliminated || victimState.respawning) return true
        if (attackerState.eliminated || attackerState.respawning) return true
        val friendly = victimState.teamId == attackerState.teamId
        if (!friendly && isRespawnProtected(victimState)) return true
        return friendly
    }

    /** 按参考参数区分 TNT 自伤、队友伤害和敌方伤害，并保留 TNT 跳跃。 */
    private fun handleTntDamage(event: EntityDamageByEntityEvent, tnt: TNTPrimed, victim: Player): Boolean {
        val attacker = tnt.source as? Player ?: return false
        val victimState = playerStates[victim.uniqueId] ?: return true
        val attackerState = playerStates[attacker.uniqueId] ?: return true
        if (phase != BedWarsPhase.RUNNING || victimState.eliminated || victimState.respawning) return true
        if (attackerState.eliminated || attackerState.respawning || isRespawnProtected(victimState)) return true
        val rules = moduleConfig.specials
        val selfDamage = attacker.uniqueId == victim.uniqueId
        val teammateDamage = !selfDamage && attackerState.teamId == victimState.teamId
        val configuredDamage = when {
            selfDamage -> rules.tntDamageSelf
            teammateDamage -> rules.tntDamageTeammates
            else -> rules.tntDamageOthers
        }
        if (configuredDamage >= 0.0) event.damage = configuredDamage
        return false
    }

    /** 仅在最终未取消的伤害事件中提交隐身、击退和攻击归因副作用。 */
    fun commitDamage(event: EntityDamageEvent) {
        val victim = event.entity as? Player ?: return
        if (event !is EntityDamageByEntityEvent) {
            if (event.finalDamage > 0.0) clearInvisibleAppearance(victim, notify = true)
            return
        }
        val tnt = event.damager as? TNTPrimed
        if (tnt != null) {
            val attacker = tnt.source as? Player ?: return
            val victimState = playerStates[victim.uniqueId] ?: return
            val attackerState = playerStates[attacker.uniqueId] ?: return
            val selfDamage = attacker.uniqueId == victim.uniqueId
            val teammateDamage = !selfDamage && attackerState.teamId == victimState.teamId
            if (selfDamage) applyTntJumpKnockback(victim, tnt)
            if (!selfDamage && !teammateDamage && event.finalDamage > 0.0) {
                attackerState.respawnProtectionUntilTick = 0
                clearInvisibleAppearance(victim, notify = true)
                recordCombatHit(victim, attacker)
            }
            return
        }
        if (dragonStates[event.damager.uniqueId] != null) {
            if (event.finalDamage > 0.0) clearInvisibleAppearance(victim, notify = true)
            return
        }
        val specialMob = specialMobs[event.damager.uniqueId]
        if (specialMob != null) {
            if (event.finalDamage > 0.0) {
                lastCombatHits.remove(victim.uniqueId)
                lastSpecialMobHits[victim.uniqueId] = BedWarsSpecialMobHitState(
                    specialMob.teamId,
                    specialMob.specialId,
                    gameElapsedTicks
                )
                clearInvisibleAppearance(victim, notify = true)
            }
            return
        }
        val attacker = directPlayer(event) ?: return
        val victimState = playerStates[victim.uniqueId] ?: return
        val attackerState = playerStates[attacker.uniqueId] ?: return
        if (victimState.teamId != attackerState.teamId && event.finalDamage > 0.0) {
            attackerState.respawnProtectionUntilTick = 0
            clearInvisibleAppearance(victim, notify = true)
            recordCombatHit(victim, attacker)
        }
    }

    /** 使用参考项目的重心和衰减公式计算 TNT 自爆跳跃速度。 */
    private fun applyTntJumpKnockback(player: Player, tnt: TNTPrimed) {
        val rules = moduleConfig.specials
        val distance = player.location.clone()
            .subtract(0.0, rules.tntBarycenterAlterationY, 0.0)
            .toVector()
            .subtract(tnt.location.toVector())
        val length = distance.length()
        if (length <= 0.0001) return
        val force = (tnt.yield * tnt.yield) / (rules.tntStrengthReduction + length)
        val velocity = distance.normalize().multiply(force)
        velocity.y = velocity.y / (length + rules.tntYAxisReduction)
        player.velocity = velocity
    }

    /** 在伤害事件监视阶段校验投射物敌我关系并发送最终生命反馈。 */
    fun handleProjectileDamageFeedback(event: EntityDamageByEntityEvent) {
        if (phase != BedWarsPhase.RUNNING) return
        val victim = event.entity as? Player ?: return
        val projectile = event.damager as? Projectile ?: return
        val attacker = projectile.shooter as? Player ?: return
        val victimState = playerStates[victim.uniqueId] ?: return
        val attackerState = playerStates[attacker.uniqueId] ?: return
        if (victimState.eliminated || victimState.respawning || attackerState.eliminated || attackerState.respawning) return
        if (victimState.teamId == attackerState.teamId) return
        sendProjectileHitFeedback(attacker, victim, victimState, event)
    }

    /** 按事件最终伤害向投射物射手反馈敌方目标的预计剩余生命。 */
    private fun sendProjectileHitFeedback(
        attacker: Player,
        victim: Player,
        victimState: BedWarsPlayerState,
        event: EntityDamageByEntityEvent
    ) {
        val message = language.getMessage("bedwars.projectile_hit_health")
        if (message.isBlank()) return
        val remainingHealth = kotlin.math.round((victim.health - event.finalDamage).coerceAtLeast(0.0) * 10.0) / 10.0
        val teamName = teamStates[victimState.teamId]?.config?.displayName ?: victimState.teamId
        attacker.sendMessage(Component.text(language.getMessage(
            "bedwars.projectile_hit_health",
            victim.name,
            remainingHealth,
            teamName
        )))
    }

    /** 处理床破坏，并按地图开关限制模板方块。 */
    fun handleBlockBreak(event: BlockBreakEvent): Boolean {
        if (phase != BedWarsPhase.RUNNING) return true
        val playerState = playerStates[event.player.uniqueId] ?: return true
        if (playerState.eliminated || playerState.respawning) return true
        val blockKey = BedWarsBlockKey.from(event.block.location) ?: return true
        if (blockKey in halloweenCobwebs) {
            event.isDropItems = false
            return false
        }
        if (event.block.type in moduleConfig.blockRules.breakableMapBlocks ||
            event.block.type == Material.FIRE && moduleConfig.blockRules.allowFireExtinguish
        ) {
            event.isDropItems = false
            return false
        }
        val teamId = bedBlocks[blockKey]
        if (teamId == null) {
            if (isProtectedLocation(event.block.location)) {
                event.player.sendMessage(Component.text(language.getMessage("bedwars.block_protected")))
                return true
            }
            if (gameConfig?.allowMapBreak == true) {
                return false
            }
            if (blockKey !in placedBlocks) {
                event.player.sendMessage(Component.text(language.getMessage("bedwars.block_protected")))
                return true
            }
            return false
        }
        if (teamId == playerState.teamId) {
            event.player.sendMessage(Component.text(language.getMessage("bedwars.own_bed")))
            movePlayerOutOfBed(event.player)
            return true
        }
        if (teamStates[teamId]?.bedAlive != true) return true
        event.isDropItems = false
        return false
    }

    /** 拒绝破坏己方床时按参考行为上移站在床碰撞箱内的玩家。 */
    private fun movePlayerOutOfBed(player: Player) {
        if (!Tag.BEDS.isTagged(player.location.block.type)) return
        player.teleport(player.location.add(0.0, 0.5, 0.0))
    }

    /** 在方块破坏最终成功后释放玩家建筑，并提交实际发生的床销毁或蜘蛛网奖励。 */
    fun handleBlockBreakComplete(event: BlockBreakEvent) {
        val blockKey = BedWarsBlockKey.from(event.block.location) ?: return
        val teamId = bedBlocks[blockKey]
        if (teamId != null) {
            val playerState = playerStates[event.player.uniqueId] ?: return
            val teamState = teamStates[teamId] ?: return
            if (teamId != playerState.teamId && teamState.bedAlive) {
                destroyBed(event, blockKey, teamId, teamState, playerState)
            }
            return
        }
        placedBlocks.remove(blockKey)
        if (halloweenCobwebs.remove(blockKey) != null) {
            awardLevelExperience(event.player.uniqueId, HALLOWEEN_COBWEB_EXPERIENCE, XP_SOURCE_HALLOWEEN_COBWEB)
        }
    }

    /** 提交一次最终成功的敌床破坏，并在唯一位置写入奖励、事件和反馈。 */
    private fun destroyBed(
        event: BlockBreakEvent,
        blockKey: BedWarsBlockKey,
        teamId: String,
        teamState: BedWarsTeamState,
        playerState: BedWarsPlayerState
    ) {
        teamState.bedAlive = false
        bedBlocks.entries.removeIf { it.value == teamId }
        updateBedHolograms(teamId)
        playerState.bedsBroken++
        resultService.addMetric(room, event.player.uniqueId, METRIC_BEDS_DESTROYED)
        awardLevelExperience(
            event.player.uniqueId,
            moduleConfig.levelRules.bedDestroyedExperience,
            XP_SOURCE_BED_DESTROYED
        )
        awardMoney(
            event.player,
            moduleConfig.moneyRewardRules.bedDestroyed,
            "bedwars.money_reward_bed_destroyed"
        )
        Bukkit.getPluginManager().callEvent(
            GameObjectiveDestroyedEvent(
                room,
                objectiveType = "bed",
                objectiveId = teamId,
                actor = event.player,
                actorTeamId = playerState.teamId,
                targetTeamId = teamId,
                sourceId = "player"
            )
        )
        event.isDropItems = false
        val feedbackEvent = GameObjectiveDestroyedFeedbackEvent(
            room,
            objectiveType = "bed",
            objectiveId = teamId,
            actor = event.player,
            actorTeamId = playerState.teamId,
            targetTeamId = teamId,
            sourceId = "player",
            message = Component.text(language.getMessage(
                "bedwars.bed_destroyed",
                teamState.config.displayName,
                event.player.name
            )),
            targetTitle = Component.text(language.getMessage("bedwars.own_bed_destroyed_title")),
            targetSubtitle = Component.text(language.getMessage("bedwars.own_bed_destroyed_subtitle"))
        )
        feedbackEvent.targetMessage = Component.text(language.getMessage(
            "bedwars.own_bed_destroyed_chat",
            event.player.name
        ))
        Bukkit.getPluginManager().callEvent(feedbackEvent)
        showBedDestroyedFeedback(
            teamId,
            feedbackEvent.message,
            feedbackEvent.targetMessage,
            feedbackEvent.targetTitle,
            feedbackEvent.targetSubtitle
        )
    }

    /** 按地图破坏开关处理自然燃烧，并始终保护队伍床。 */
    fun shouldCancelBlockBurn(block: Block): Boolean {
        val blockKey = BedWarsBlockKey.from(block.location)
        return gameConfig?.allowMapBreak != true || blockKey == null || blockKey in bedBlocks
    }

    /** 向仍存活的受害队成员发送可修改标题/音效，其余房间观众播放普通破床音效。 */
    private fun showBedDestroyedFeedback(
        teamId: String,
        message: Component?,
        targetMessage: Component?,
        title: Component?,
        subtitle: Component?
    ) {
        val audience = roomBroadcastService.participants(room)
        val victims = audience.filter { player ->
            playerStates[player.uniqueId]?.let { it.teamId == teamId && it.participant && !it.eliminated } == true
        }
        message?.let { defaultMessage ->
            val victimIds = victims.mapTo(hashSetOf()) { it.uniqueId }
            audience.forEach { player ->
                player.sendMessage(if (player.uniqueId in victimIds) targetMessage ?: defaultMessage else defaultMessage)
            }
        }
        playSoundRule(victims, moduleConfig.ownBedDestroyedSound)
        playSoundRule(audience - victims.toSet(), moduleConfig.bedDestroyedSound)
        if (title == null && subtitle == null) return
        victims.forEach { player ->
            player.showTitle(Title.title(
                title ?: Component.empty(),
                subtitle ?: Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
            ))
        }
    }

    /** 验证建筑高度、材料和保护区，并追踪本局放置方块。 */
    fun handleBlockPlace(event: BlockPlaceEvent): Boolean {
        val player = event.player
        if (!isActiveParticipant(player)) return true
        val block = event.blockPlaced
        val maxBuildY = gameConfig?.maxBuildY ?: moduleConfig.maxBuildY
        val specialId = event.itemInHand.itemMeta?.persistentDataContainer
            ?.get(specialItemKey, PersistentDataType.STRING)
        if (specialId == "popup-tower") {
            if (block.y + moduleConfig.specials.towerWallHeight + 2 >= maxBuildY || isProtectedLocation(block.location)) {
                player.sendMessage(Component.text(language.getMessage("bedwars.popup_tower_blocked")))
                return true
            }
            consumeHeldItem(player, event.hand, event.itemInHand)
            towerStates += createPopupTower(player, block.location)
            return true
        }
        if (block.y >= maxBuildY) {
            player.sendMessage(Component.text(language.getMessage("bedwars.max_build_y", maxBuildY)))
            return true
        }
        if (!isAllowedPlacementMaterial(block.type)) {
            player.sendMessage(Component.text(language.getMessage("bedwars.block_not_allowed")))
            return true
        }
        if (isProtectedLocation(block.location)) {
            player.sendMessage(Component.text(language.getMessage("bedwars.block_protected")))
            return true
        }
        return false
    }

    /** 在方块放置最终成功后登记玩家建筑，或把 TNT 原位转换为受追踪的点燃实体。 */
    fun handleBlockPlaceComplete(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        if (block.type == Material.TNT && moduleConfig.blockRules.autoPrimeTnt) {
            block.type = Material.AIR
            val tnt = block.world.spawn(block.location.add(0.5, 0.0, 0.5), TNTPrimed::class.java) {
                it.fuseTicks = moduleConfig.blockRules.tntFuseTicks
                it.source = event.player
                it.addScoreboardTag("kgc_bedwars")
            }
            trackEntity(tnt, event.player.uniqueId, "tnt")
            return
        }
        BedWarsBlockKey.from(block.location)?.let(placedBlocks::add)
    }

    /** 允许配置白名单中同一队色材质族的运行期重染结果。 */
    private fun isAllowedPlacementMaterial(material: Material): Boolean {
        val allowed = moduleConfig.blockRules.placeAllowed
        if (material in allowed) return true
        val family = teamColorMaterialFamily(material) ?: return false
        return allowed.any { teamColorMaterialFamily(it) == family }
    }

    /** 返回参考 colourItem 支持的床、玻璃板、玻璃、陶瓦或羊毛材质族。 */
    private fun teamColorMaterialFamily(material: Material): String? = when {
        material.name.endsWith("_BED") -> "BED"
        material == Material.GLASS_PANE || material.name.endsWith("_STAINED_GLASS_PANE") -> "GLASS_PANE"
        material == Material.GLASS || material.name.endsWith("_STAINED_GLASS") -> "GLASS"
        material.name.endsWith("_TERRACOTTA") -> "TERRACOTTA"
        material.name.endsWith("_WOOL") -> "WOOL"
        else -> null
    }

    /** 验证水桶倒水目标，实际资源登记等待事件最终成功后提交。 */
    fun handleBucketEmpty(event: PlayerBucketEmptyEvent): Boolean {
        if (!isActiveParticipant(event.player)) return true
        if (event.bucket != Material.WATER_BUCKET) return true
        val block = event.block
        val maxBuildY = gameConfig?.maxBuildY ?: moduleConfig.maxBuildY
        if (block.y >= maxBuildY) {
            event.player.sendMessage(Component.text(language.getMessage("bedwars.max_build_y", maxBuildY)))
            return true
        }
        if (isProtectedLocation(block.location)) {
            event.player.sendMessage(Component.text(language.getMessage("bedwars.block_protected")))
            return true
        }
        return false
    }

    /** 在倒水事件最终成功后登记临时水源，并按参考时序移除生成的空桶。 */
    fun handleBucketEmptyComplete(event: PlayerBucketEmptyEvent) {
        if (event.bucket != Material.WATER_BUCKET) return
        val block = event.block
        resourceScope?.captureBlock(block)
        BedWarsBlockKey.from(block.location)?.let(placedBlocks::add)
        val player = event.player
        roomTaskService.runTaskLater(room.id, BUCKET_CLEANUP_DELAY_TICKS, Runnable {
            if (roomManager.getPlayerRoom(player)?.id != room.id || !isActiveParticipant(player)) return@Runnable
            removeOneMaterial(player, Material.BUCKET)
        })
    }

    /** 只允许玩家取回本局通过水桶放置并登记的水源，成功后再释放登记。 */
    fun handleBucketFill(event: PlayerBucketFillEvent): Boolean {
        if (!isActiveParticipant(event.player)) return true
        val blockKey = BedWarsBlockKey.from(event.block.location) ?: return true
        if (blockKey !in placedBlocks) {
            event.player.sendMessage(Component.text(language.getMessage("bedwars.block_protected")))
            return true
        }
        return false
    }

    /** 在取水事件最终成功后释放本局水源登记，避免后续取消造成状态漂移。 */
    fun handleBucketFillComplete(event: PlayerBucketFillEvent) {
        BedWarsBlockKey.from(event.block.location)?.let(placedBlocks::remove)
    }

    /** 让爆炸只破坏当前房间由玩家放置且未受保护的方块。 */
    fun handleExplosion(event: EntityExplodeEvent) {
        if (event.entity.uniqueId in dragonStates) {
            filterEntityExplosionBlocks(event)
            return
        }
        trackedEntities.remove(event.entity.uniqueId)
        resourceScope?.releaseEntity(event.entity.uniqueId)
        if (event.entity.persistentDataContainer.get(specialItemKey, PersistentDataType.STRING) == "fireball") {
            applyFireballEffects(event.entity.location, (event.entity as? Fireball)?.shooter as? Player)
        }
        filterEntityExplosionBlocks(event)
    }

    /** 让方块爆炸遵循与实体爆炸相同的玩家建筑保护规则。 */
    fun handleBlockExplosion(origin: Location, blocks: MutableList<Block>) {
        filterExplosionBlocks(origin, blocks)
    }

    /** 按实体类型重滤最终爆炸清单，末影龙爆炸始终不破坏任何方块。 */
    fun filterEntityExplosionBlocks(event: EntityExplodeEvent) {
        if (event.entity.uniqueId in dragonStates) {
            event.blockList().clear()
            return
        }
        filterExplosionBlocks(event.entity.location, event.blockList())
    }

    /** 按最终爆点和当前房间状态移除爆炸清单中的受保护方块。 */
    fun filterExplosionBlocks(origin: Location, blocks: MutableList<Block>) {
        blocks.removeIf { block -> isExplosionProtected(origin, block) }
    }

    /** 在爆炸最终成功后按最终方块清单释放玩家建筑和活动蜘蛛网登记。 */
    fun handleExplosionComplete(blocks: List<Block>) {
        blocks.forEach { block ->
            BedWarsBlockKey.from(block.location)?.let { blockKey ->
                placedBlocks.remove(blockKey)
                halloweenCobwebs.remove(blockKey)
            }
        }
    }

    /** 统一判断床、关键点、玻璃及地图破坏开关下的爆炸保护。 */
    private fun isExplosionProtected(origin: Location, block: Block): Boolean {
        val key = BedWarsBlockKey.from(block.location) ?: return true
        return key in bedBlocks ||
            isProtectedLocation(block.location) ||
            isBlastProofGlass(block.type) ||
            isExplosionRayShielded(origin, block) ||
            gameConfig?.allowMapBreak != true && key !in placedBlocks
    }

    /** 判断材料是否属于当前模块的防爆玻璃。 */
    private fun isBlastProofGlass(material: Material): Boolean = material.name.endsWith("GLASS")

    /** 从爆点周围发出 27 条射线，仅在绝大多数路径受阻时保护目标方块。 */
    private fun isExplosionRayShielded(origin: Location, target: Block): Boolean {
        val world = origin.world ?: return false
        if (world != target.world) return false
        val allowMapBreak = gameConfig?.allowMapBreak == true
        val targetCenter = target.location.add(0.5, 0.5, 0.5).toVector()
        var blockedRays = 0
        var testedRays = 0
        EXPLOSION_RAY_OFFSETS.forEach { offsetX ->
            EXPLOSION_RAY_OFFSETS.forEach { offsetY ->
                EXPLOSION_RAY_OFFSETS.forEach { offsetZ ->
                    val start = origin.toVector().add(Vector(offsetX, offsetY, offsetZ))
                    if (isExplosionRayBlocked(world, start, targetCenter, allowMapBreak)) blockedRays++
                    testedRays++
                    if (blockedRays >= EXPLOSION_BLOCKED_RAY_THRESHOLD) return true
                    val remainingRays = EXPLOSION_TOTAL_RAYS - testedRays
                    if (blockedRays + remainingRays < EXPLOSION_BLOCKED_RAY_THRESHOLD) return false
                }
            }
        }
        return false
    }

    /** 沿单条爆炸射线检查玻璃，以及禁止地图破坏时未由玩家放置的模板方块。 */
    private fun isExplosionRayBlocked(world: World, start: Vector, target: Vector, allowMapBreak: Boolean): Boolean {
        val delta = target.clone().subtract(start)
        val length = delta.length()
        if (length <= EXPLOSION_RAY_STEP) return false
        val direction = delta.normalize()
        var distance = EXPLOSION_RAY_STEP
        while (distance < length - EXPLOSION_RAY_STEP) {
            val sampleVector = start.clone().add(direction.clone().multiply(distance))
            val sample = world.getBlockAt(
                kotlin.math.floor(sampleVector.x).toInt(),
                kotlin.math.floor(sampleVector.y).toInt(),
                kotlin.math.floor(sampleVector.z).toInt()
            )
            if (sample.type != Material.AIR) {
                if (moduleConfig.blockRules.blastProofGlassBlocksRays && isBlastProofGlass(sample.type)) return true
                val sampleKey = BedWarsBlockKey.from(sample.location)
                if (!allowMapBreak && sampleKey !in placedBlocks) return true
            }
            distance += EXPLOSION_RAY_STEP
        }
        return false
    }

    /** 将死亡后的玩家放到队伍复活点或淘汰观战点。 */
    fun handleRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val state = playerStates[player.uniqueId] ?: return
        if (state.eliminated) return
        if (!state.respawning) return
        event.respawnLocation = teamSpawn(state.teamId) ?: spectatorSpawn()
        state.respawnTicks = moduleConfig.respawnSeconds * 20
        roomTaskService.runTaskLater(room.id, 1L, Runnable {
            if (roomManager.getPlayerRoom(player)?.id != room.id || !state.respawning) return@Runnable
            player.gameMode = GameMode.SPECTATOR
            player.spectatorTarget = null
            setTeamNametag(player, state.teamId)
        })
    }

    /** 判断玩家当前是否可操作背包、物品和世界。 */
    fun isActiveParticipant(player: Player): Boolean {
        val state = playerStates[player.uniqueId] ?: return false
        return phase == BedWarsPhase.RUNNING && !state.eliminated && !state.respawning
    }

    /** 按等待/运行配置允许正式玩家消耗饥饿，并始终锁定外部或淘汰观战者。 */
    fun shouldCancelFoodChange(player: Player): Boolean {
        val state = playerStates[player.uniqueId]
        if (state?.participant != true || state.eliminated) return true
        val allowed = when (phase) {
            BedWarsPhase.RUNNING -> moduleConfig.allowHungerInGame
            BedWarsPhase.WAITING,
            BedWarsPhase.COUNTDOWN,
            BedWarsPhase.RESULT,
            BedWarsPhase.CLOSING -> moduleConfig.allowHungerWaiting
        }
        return !allowed
    }

    /** 处理击落资源原地掉落、普通击杀资源直送，以及最终击杀后的基地回收掉落。 */
    fun handleDeathDrops(event: PlayerDeathEvent) {
        event.deathMessage(null)
        event.droppedExp = 0
        event.drops.removeIf(::isPermanentItem)
        if (phase != BedWarsPhase.RUNNING) return
        if (effectiveVanillaDeathDrops) return
        val victim = event.player
        val victimState = playerStates[victim.uniqueId] ?: return
        val killer = resolveKiller(victim)
        if (isRecentKnockbackFall(victim)) {
            event.drops.removeIf { it.type !in DEATH_TRANSFER_RESOURCES }
            return
        }
        if (killer == null) {
            event.drops.removeIf { it.type !in DEATH_TRANSFER_RESOURCES }
            return
        }
        if (teamStates[victimState.teamId]?.bedAlive == true) {
            transferDeathResources(event, killer, victim)
        } else {
            recoverFinalDeathDrops(event, victim, victimState)
        }
    }

    /** 保存经典死亡规则在资源转移完成后仍允许落地的物品快照。 */
    fun captureDeathDropSnapshot(event: PlayerDeathEvent): List<ItemStack>? {
        if (phase != BedWarsPhase.RUNNING || effectiveVanillaDeathDrops) return null
        return event.drops.map(ItemStack::clone)
    }

    /** 最终限制掉落为已处理快照的剩余交集，不重复转移资源或恢复外部移除项。 */
    fun finalizeDeathDrops(event: PlayerDeathEvent, allowedDrops: List<ItemStack>?) {
        event.deathMessage(null)
        event.droppedExp = 0
        event.drops.removeIf(::isPermanentItem)
        val remaining = allowedDrops?.map(ItemStack::clone)?.toMutableList() ?: return
        val iterator = event.drops.listIterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            var allowedAmount = 0
            remaining.forEach { allowed ->
                if (allowed.isSimilar(item)) allowedAmount += allowed.amount
            }
            if (allowedAmount <= 0) {
                iterator.remove()
                continue
            }
            val keptAmount = minOf(item.amount, allowedAmount)
            item.amount = keptAmount
            var consumed = keptAmount
            val allowedIterator = remaining.listIterator()
            while (allowedIterator.hasNext() && consumed > 0) {
                val allowed = allowedIterator.next()
                if (!allowed.isSimilar(item)) continue
                if (allowed.amount <= consumed) {
                    consumed -= allowed.amount
                    allowedIterator.remove()
                } else {
                    allowed.amount -= consumed
                    consumed = 0
                }
            }
        }
    }

    /** 拦截永久物品丢弃，并允许已有同级或更强替代品时丢弃默认剑。 */
    fun handleItemDrop(player: Player, item: ItemStack): Boolean {
        if (isPermanentItem(item)) {
            if (isDefaultSword(item) && hasEqualOrStrongerSword(player, item)) return false
            player.sendMessage(Component.text(language.getMessage("bedwars.permanent_item_locked")))
            return true
        }
        return false
    }

    /** 在丢弃最终成功后为确实失去最后一把剑的活动玩家恢复出生剑。 */
    fun handleItemDropComplete(player: Player, item: ItemStack) {
        if (!item.type.name.endsWith("_SWORD")) return
        if (roomManager.getPlayerRoom(player)?.session !== this || !isActiveParticipant(player)) return
        playerStates[player.uniqueId]?.let { giveDefaultSword(player, it.teamId) }
    }

    /** 玩家关闭箱子等外部库存时，在背包已无剑且仍可活动的情况下恢复当前物品组出生剑。 */
    fun handleExternalInventoryClose(player: Player) {
        if (!isActiveParticipant(player)) return
        val state = playerStates[player.uniqueId] ?: return
        giveDefaultSword(player, state.teamId)
    }

    /** 阻止把死亡后重发的永久装备插入模板物品展示框。 */
    fun handlePermanentItemFrameInsert(player: Player, item: ItemStack): Boolean {
        if (!isPermanentItem(item)) return false
        player.sendMessage(Component.text(language.getMessage("bedwars.permanent_item_locked")))
        return true
    }

    /** 阻止通过光标、Shift 点击、数字键或副手交换把永久物品移入外部栏位。 */
    fun handlePermanentInventoryClick(event: InventoryClickEvent): Boolean {
        val player = event.whoClicked as? Player ?: return false
        val clickedTop = event.clickedInventory == event.view.topInventory
        val clickedBottom = event.clickedInventory == event.view.bottomInventory
        val blocked = when {
            event.slotType == InventoryType.SlotType.ARMOR -> true
            event.clickedInventory == null -> isPermanentItem(event.cursor)
            clickedTop && isPermanentItem(event.cursor) -> true
            clickedTop && event.click == ClickType.NUMBER_KEY && event.hotbarButton >= 0 ->
                isPermanentItem(player.inventory.getItem(event.hotbarButton))
            clickedTop && event.click == ClickType.SWAP_OFFHAND -> isPermanentItem(player.inventory.itemInOffHand)
            clickedBottom && event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY ->
                isPermanentItem(event.currentItem)
            else -> false
        }
        if (blocked && event.slotType == InventoryType.SlotType.ARMOR) {
            refreshInvisibleArmorAfterInventoryAttempt(player)
        }
        if (blocked) player.sendMessage(Component.text(language.getMessage("bedwars.permanent_item_locked")))
        return blocked
    }

    /** 阻止拖拽永久物品时覆盖任何外部栏位。 */
    fun handlePermanentInventoryDrag(event: InventoryDragEvent): Boolean {
        val player = event.whoClicked as? Player ?: return false
        val touchesArmor = event.rawSlots.any { event.view.getSlotType(it) == InventoryType.SlotType.ARMOR }
        if (!touchesArmor && !isPermanentItem(event.oldCursor)) return false
        val blocked = touchesArmor || event.rawSlots.any { it < event.view.topInventory.size }
        if (blocked && touchesArmor) refreshInvisibleArmorAfterInventoryAttempt(player)
        if (blocked) player.sendMessage(Component.text(language.getMessage("bedwars.permanent_item_locked")))
        return blocked
    }

    /** 阻止现代右键交换事件改写模块维护的四个玩家护甲槽。 */
    fun handleEquipmentSlotSwap(player: Player, slot: EquipmentSlot): Boolean {
        val blocked = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST ||
            slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET
        if (blocked) refreshInvisibleArmorAfterInventoryAttempt(player)
        if (blocked) player.sendMessage(Component.text(language.getMessage("bedwars.permanent_item_locked")))
        return blocked
    }

    /** 在取消护甲操作时立即并于下一 tick 重发敌方私有空护甲，覆盖客户端预测和服务端纠正包。 */
    fun refreshInvisibleArmorAfterInventoryAttempt(player: Player) {
        if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY) && player.uniqueId !in invisiblePlayers) return
        val state = playerStates[player.uniqueId] ?: return
        hideInvisibleAppearance(player, state)
        roomTaskService.runTask(room.id, Runnable {
            if (roomManager.getPlayerRoom(player)?.id != room.id || !isActiveParticipant(player)) return@Runnable
            if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return@Runnable
            playerStates[player.uniqueId]?.let { hideInvisibleAppearance(player, it) }
        })
    }

    /** 判断物品是否携带当前模块的独立永久装备标记。 */
    private fun isPermanentItem(item: ItemStack?): Boolean {
        return item?.itemMeta?.persistentDataContainer?.has(permanentItemKey, PersistentDataType.BYTE) == true
    }

    /** 为箱子菜单动态生成当前物品商店或队伍升级条目。 */
    fun shopEntries(player: Player, kind: BedWarsShopKind, view: String?): List<ChestMenuEntry> {
        val state = playerStates[player.uniqueId] ?: return emptyList()
        if (!isActiveParticipant(player)) return emptyList()
        return when (kind) {
            BedWarsShopKind.ITEM -> if (view == QUICK_BUY_VIEW) {
                quickBuyEntries(player, state)
            } else {
                visibleShopProducts(state).map { item ->
                    shopEntry(
                        player,
                        item.id,
                        item.icon,
                        productName(item.id, item.displayName),
                        item.currency,
                        item.price,
                        itemPurchaseStatus(player, state, item),
                        iconAmount = item.iconAmount,
                        iconEnchanted = item.iconEnchanted,
                        iconPotionDisplay = item.iconPotionDisplay,
                        iconPotionColor = item.iconPotionColor,
                        tier = item.tier,
                        customLore = item.displayLore,
                        quickBuyHint = language.getMessage("bedwars.quick_buy_add_hint")
                    )
                }
            }
            BedWarsShopKind.UPGRADE -> {
                val team = teamStates[state.teamId] ?: return emptyList()
                if (view == TRAPS_VIEW) {
                    moduleConfig.shop.trapCategoryRules.trapTypes
                        .flatMap { type -> moduleConfig.shop.upgrades.filter { it.upgradeType == type } }
                        .map { item -> upgradeShopEntry(player, team, item) }
                } else {
                    buildList {
                        addAll(visibleUpgradeProducts(team).map { item -> upgradeShopEntry(player, team, item) })
                        addAll(visibleDirectTrapProducts().map { item -> upgradeShopEntry(player, team, item) })
                        if (upgradeMenuRules().trapCategoryVisible && moduleConfig.shop.upgrades.any { it.upgradeType.trap }) {
                            add(trapCategoryEntry(team))
                        }
                    }
                }
            }
        }
    }

    /** 为升级菜单生成当前队伍完整的陷阱队列槽，空槽同时预览下一次购买价格。 */
    fun trapQueueEntries(player: Player): List<ChestMenuEntry> {
        val state = playerStates[player.uniqueId] ?: return emptyList()
        if (!isActiveParticipant(player)) return emptyList()
        val team = teamStates[state.teamId] ?: return emptyList()
        return (0 until trapRules().queueLimit).map { index ->
            trapQueueEntry(player, team, index)
        }
    }

    /** 识别本房间商店村民并打开对应的 KaGameCenter 箱子菜单。 */
    fun handleShopInteract(player: Player, entity: Entity): Boolean {
        val kind = shopNpcs[entity.uniqueId] ?: return false
        if (!isActiveParticipant(player)) return true
        val state = playerStates[player.uniqueId] ?: return true
        val openEvent = GameShopOpenEvent(
            room,
            player,
            kind.name.lowercase(),
            state.teamId,
            entity
        )
        Bukkit.getPluginManager().callEvent(openEvent)
        if (openEvent.isCancelled) return true
        quickBuyAssignments.remove(player.uniqueId)
        openShop(player, kind)
        return true
    }

    /** 处理 BedWars 商店菜单中的商品槽位点击。 */
    fun handleShopClick(player: Player, holder: ChestMenuHolder, slot: Int, click: ClickType): Boolean {
        if (!holder.menuId.startsWith("kagamecenter:bedwars-shop:${room.id}:")) return false
        val kind = BedWarsShopKind.parse(holder.context["shop.kind"]) ?: return true
        val view = holder.context["shop.view"] ?: ALL_ITEMS_VIEW
        when (holder.iconAt(slot)) {
            "Q" -> {
                openShop(player, kind, view = QUICK_BUY_VIEW)
                return true
            }
            "A" -> {
                quickBuyAssignments.remove(player.uniqueId)
                openShop(player, kind, view = ALL_ITEMS_VIEW)
                return true
            }
            "B" -> {
                if (kind == BedWarsShopKind.UPGRADE) {
                    openShop(player, kind, view = ALL_ITEMS_VIEW)
                }
                return true
            }
            "S" -> {
                if (kind == BedWarsShopKind.UPGRADE) executeUpgradeSeparatorCommands(player)
                return true
            }
            "P" -> {
                openShop(player, kind, (holder.currentPage - 1).coerceAtLeast(0), view)
                return true
            }
            "N" -> {
                openShop(player, kind, holder.currentPage + 1, view)
                return true
            }
        }
        val variables = holder.slotVariables[slot].orEmpty()
        if (kind == BedWarsShopKind.UPGRADE && variables["shop.category"] == TRAPS_VIEW) {
            val state = playerStates[player.uniqueId] ?: return true
            val team = teamStates[state.teamId] ?: return true
            if (team.traps.size >= trapRules().queueLimit) {
                player.sendMessage(shopFeedbackComponent(language.getMessage("bedwars.shop_status_traps_full")))
                return true
            }
            openShop(player, kind, view = TRAPS_VIEW)
            return true
        }
        val productId = variables["product.id"]
        when (kind) {
            BedWarsShopKind.ITEM -> {
                val quickSlot = variables["quick.slot"]?.toIntOrNull()
                val assignment = quickBuyAssignments[player.uniqueId]
                if (view == QUICK_BUY_VIEW && assignment != null && quickSlot != null) {
                    quickBuyService.assign(
                        player.uniqueId,
                        quickSlot,
                        assignment,
                        moduleConfig.shop.quickBuyDefaults,
                        quickBuyProductAliases()
                    )
                    quickBuyAssignments.remove(player.uniqueId)
                    player.sendMessage(shopFeedbackComponent(language.getMessage(
                        "bedwars.quick_buy_assigned",
                        quickBuyFeedbackName(assignment),
                        quickSlot + 1
                    )))
                    openShop(player, kind, view = QUICK_BUY_VIEW)
                    return true
                }
                if (view == QUICK_BUY_VIEW && (click.isRightClick || click.isShiftClick) && quickSlot != null) {
                    if (quickBuyService.remove(
                            player.uniqueId,
                            quickSlot,
                            moduleConfig.shop.quickBuyDefaults,
                            quickBuyProductAliases()
                        )
                    ) {
                        player.sendMessage(shopFeedbackComponent(language.getMessage("bedwars.quick_buy_removed", quickSlot + 1)))
                    }
                    refreshCurrentShop(player, holder)
                    return true
                }
                if (productId == null) return true
                if (click.isShiftClick) {
                    quickBuyAssignments[player.uniqueId] = productId
                    openShop(player, kind, view = QUICK_BUY_VIEW)
                    return true
                }
                buyItem(player, productId)
            }
            BedWarsShopKind.UPGRADE -> if (productId != null) {
                val purchased = buyUpgrade(player, productId)
                if (purchased && view == TRAPS_VIEW) {
                    openShop(player, kind, view = ALL_ITEMS_VIEW)
                    return true
                }
            }
        }
        refreshCurrentShop(player, holder)
        return true
    }

    /** 仅在玩家仍查看原 Holder 时刷新，避免购买事件打开的新界面被旧菜单同步干扰。 */
    private fun refreshCurrentShop(player: Player, holder: ChestMenuHolder) {
        if (player.openInventory.topInventory.holder === holder) chestMenuService.refresh(player, holder)
    }

    /** 以参考玩家/控制台身份执行升级分隔条命令并展开玩家与队伍占位符。 */
    private fun executeUpgradeSeparatorCommands(player: Player) {
        if (!isActiveParticipant(player)) return
        val state = playerStates[player.uniqueId] ?: return
        val teamName = teamStates[state.teamId]?.config?.displayName ?: state.teamId
        val displayName = LegacyComponentSerializer.legacySection().serialize(player.displayName())
        val replacements = linkedMapOf(
            "{playername}" to player.name,
            "{player}" to displayName,
            "{team}" to teamName
        )
        val expand: (String) -> String = { command ->
            replacements.entries.fold(command) { value, (token, replacement) ->
                value.replace(token, replacement)
            }
        }
        moduleConfig.shop.upgradeSeparatorPlayerCommands.forEach { command ->
            Bukkit.dispatchCommand(player, expand(command))
        }
        moduleConfig.shop.upgradeSeparatorConsoleCommands.forEach { command ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expand(command))
        }
    }

    /** 限制工作站入口和敌队访问仍存活队伍出生岛范围内的公共箱子。 */
    fun handleBlockInteract(event: PlayerInteractEvent): Boolean {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return false
        if (spectatorService.action(event.item) != null || spectatorService.command(event.item) != null) return false
        val block = event.clickedBlock ?: return false
        if (isMapBlockTransformInteraction(event, block) && shouldCancelMapBlockTransform(event.player, block)) return true
        if (isDisabledWorkstation(block.type)) return true
        if (!isActiveParticipant(event.player)) return isRestrictedContainerOrOpenable(block)
        if (block.type != Material.CHEST && block.type != Material.TRAPPED_CHEST) return false
        val playerState = playerStates[event.player.uniqueId] ?: return true
        val radiusSquared = moduleConfig.blockRules.teamChestRadius * moduleConfig.blockRules.teamChestRadius
        val owner = teamStates.values
            .mapNotNull { team -> team.config.spawn?.toLocation(block.world)?.let { team to it.distanceSquared(block.location) } }
            .filter { it.second <= radiusSquared }
            .minByOrNull { it.second }
            ?.first
            ?: return false
        if (owner.config.id == playerState.teamId || isTeamEliminated(owner.config.id)) return false
        event.player.sendMessage(Component.text(language.getMessage("bedwars.team_chest_locked", owner.config.displayName)))
        return true
    }

    /** 阻止非活动玩家打开容器、末影箱、现代工作站和任意可开合地图方块。 */
    private fun isRestrictedContainerOrOpenable(block: Block): Boolean {
        return block.type == Material.ENDER_CHEST ||
            block.state is InventoryHolder ||
            block.blockData is Openable ||
            block.type in NON_ACTIVE_WORKSTATIONS
    }

    /** 判断右键是否会通过工具或蜂蜜脾改变模板方块类型或关键 BlockData。 */
    private fun isMapBlockTransformInteraction(event: PlayerInteractEvent, block: Block): Boolean {
        val held = event.item?.type ?: return false
        val blockName = block.type.name
        return when {
            held.name.endsWith("_AXE") ->
                Material.matchMaterial("STRIPPED_$blockName") != null || isMutableCopper(blockName)
            held.name.endsWith("_SHOVEL") ->
                block.type in SHOVEL_TRANSFORM_BLOCKS || block.type == Material.CAMPFIRE ||
                    block.type == Material.SOUL_CAMPFIRE
            held.name.endsWith("_HOE") -> block.type in HOE_TRANSFORM_BLOCKS
            held == Material.HONEYCOMB ->
                blockName.contains("COPPER") && !blockName.startsWith("WAXED_") || blockName.endsWith("_SIGN")
            held == Material.SHEARS ->
                block.type == Material.PUMPKIN || block.type == Material.BEE_NEST || block.type == Material.BEEHIVE
            held == Material.GLASS_BOTTLE -> block.type == Material.BEE_NEST || block.type == Material.BEEHIVE
            else -> false
        }
    }

    /** 判断铜块是否可被斧头刮除氧化层或蜂蜡。 */
    private fun isMutableCopper(blockName: String): Boolean {
        return blockName.contains("COPPER") && (
            blockName.startsWith("WAXED_") || blockName.startsWith("EXPOSED_") ||
                blockName.startsWith("WEATHERED_") || blockName.startsWith("OXIDIZED_")
            )
    }

    /** 按模板破坏开关和关键点保护拦截工具右键造成的原地方块变化。 */
    private fun shouldCancelMapBlockTransform(player: Player, block: Block): Boolean {
        if (!isActiveParticipant(player)) return true
        val blockKey = BedWarsBlockKey.from(block.location) ?: return true
        val blocked = isProtectedLocation(block.location) ||
            gameConfig?.allowMapBreak != true && blockKey !in placedBlocks
        if (blocked) player.sendMessage(Component.text(language.getMessage("bedwars.block_protected")))
        return blocked
    }

    /** 禁用合成时清空工作台和玩家个人合成栏的预览结果。 */
    fun handlePrepareCraft(event: PrepareItemCraftEvent) {
        if (!moduleConfig.inventoryRules.disableCraftingTable) return
        event.inventory.result = ItemStack(Material.AIR)
    }

    /** 在活动窗口内给当前房间新生成的生物装备参考南瓜头。 */
    fun handleHalloweenCreatureSpawn(entity: LivingEntity) {
        if (!moduleConfig.halloweenActive || entity is ArmorStand) return
        entity.equipment?.helmet = ItemStack(Material.PUMPKIN)
    }

    /** 判断指定工作站材料是否被当前 BedWars 配置禁用。 */
    private fun isDisabledWorkstation(material: Material): Boolean {
        val rules = moduleConfig.inventoryRules
        return when (material) {
            Material.CRAFTING_TABLE -> rules.disableCraftingTable
            Material.ENCHANTING_TABLE -> rules.disableEnchantingTable
            Material.FURNACE -> rules.disableFurnace
            Material.BREWING_STAND -> rules.disableBrewingStand
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL -> rules.disableAnvil
            else -> false
        }
    }

    /** 在下一 tick 复核房间与阶段后执行等待大厅快捷物品的可信配置命令。 */
    fun handlePreGameInteract(event: PlayerInteractEvent): Boolean {
        if (phase != BedWarsPhase.WAITING || event.hand != EquipmentSlot.HAND) return false
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return false
        val command = event.item?.itemMeta?.persistentDataContainer
            ?.get(preGameCommandKey, PersistentDataType.STRING)
            ?.trim()
            ?.removePrefix("/")
            ?.takeIf(String::isNotBlank)
            ?: return false
        val player = event.player
        roomTaskService.runTask(room.id, Runnable {
            val currentRoom = roomManager.getPlayerRoom(player) ?: return@Runnable
            if (currentRoom.id != room.id || currentRoom.session !== this || phase != BedWarsPhase.WAITING) return@Runnable
            player.performCommand(command)
        })
        return true
    }

    /** 将活动玩家持物交互开放给扩展事件，再执行带模块标记的默认特殊商品。 */
    fun handleSpecialInteract(event: PlayerInteractEvent): Boolean {
        if (!isActiveParticipant(event.player)) return false
        if (event.action !in ITEM_USE_ACTIONS) return false
        val hand = event.hand ?: return false
        val item = event.item ?: return false
        val itemId = item.itemMeta?.persistentDataContainer?.get(specialItemKey, PersistentDataType.STRING)
        val useEvent = GameItemUseEvent(
            room,
            event.player,
            itemId,
            item,
            event.action,
            hand,
            event.clickedBlock,
            event.blockFace
        )
        Bukkit.getPluginManager().callEvent(useEvent)
        if (useEvent.isCancelled || useEvent.handled) return true
        if (hand != EquipmentSlot.HAND || event.action !in RIGHT_CLICK_ACTIONS) return false
        val specialId = item.itemMeta?.persistentDataContainer?.get(specialItemKey, PersistentDataType.STRING) ?: return false
        val player = event.player
        val state = playerStates[player.uniqueId] ?: return true
        when (specialId) {
            "fireball" -> {
                if ((fireballCooldowns[player.uniqueId] ?: 0) > gameElapsedTicks) {
                    player.sendMessage(Component.text(language.getMessage("bedwars.fireball_cooldown")))
                    return true
                }
                val fireball = player.launchProjectile(Fireball::class.java)
                fireball.shooter = player
                fireball.velocity = player.eyeLocation.direction.normalize().multiply(moduleConfig.specials.fireballSpeed)
                fireball.yield = moduleConfig.specials.fireballYield
                fireball.setIsIncendiary(moduleConfig.specials.fireballMakeFire)
                if (isSpecialProjectileLaunchCancelled(player, state.teamId, specialId, fireball)) return true
                consumeOne(item)
                fireballCooldowns[player.uniqueId] = gameElapsedTicks + moduleConfig.specials.fireballCooldownTicks
                markSpecial(fireball, specialId)
                trackEntity(fireball, player.uniqueId, specialId)
            }
            "bridge-egg" -> {
                val egg = player.launchProjectile(Egg::class.java)
                if (isSpecialProjectileLaunchCancelled(player, state.teamId, specialId, egg)) return true
                consumeOne(item)
                markSpecial(egg, specialId)
                bridgeStates[egg.uniqueId] = BedWarsBridgeState(egg.uniqueId, player.uniqueId, state.teamId)
                trackEntity(egg, player.uniqueId, specialId)
            }
            "bed-bug" -> {
                val snowball = player.launchProjectile(Snowball::class.java)
                if (isSpecialProjectileLaunchCancelled(player, state.teamId, specialId, snowball)) return true
                consumeOne(item)
                markSpecial(snowball, specialId)
                trackEntity(snowball, player.uniqueId, specialId)
            }
            "dream-defender" -> {
                val location = event.clickedBlock?.location?.add(0.5, 1.0, 0.5) ?: return true
                consumeOne(item)
                spawnSpecialMob(location, player, state.teamId, specialId)
            }
            "magic-milk" -> {
                consumeOne(item)
                activateMagicMilk(player, state)
            }
            else -> return false
        }
        return true
    }

    /** 发布已生成的玩法投射物；取消时移除实体并阻止后续物品消耗和生命周期登记。 */
    private fun isSpecialProjectileLaunchCancelled(
        player: Player,
        teamId: String,
        sourceId: String,
        projectile: Projectile
    ): Boolean {
        val launchEvent = GameProjectileLaunchedEvent(room, player, teamId, sourceId, projectile)
        Bukkit.getPluginManager().callEvent(launchEvent)
        if (!launchEvent.isCancelled) return false
        projectile.remove()
        return true
    }

    /** 在特殊投射物命中后结束桥蛋、生成床虫或为仍属本房间的非淘汰射手播放珍珠提示。 */
    fun handleProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (projectile is EnderPearl) {
            val owner = projectile.shooter as? Player ?: return
            val ownerState = playerStates[owner.uniqueId] ?: return
            if (!ownerState.participant || ownerState.eliminated) return
            if (roomManager.getPlayerRoom(owner)?.id != room.id || roomManager.isSpectator(owner.uniqueId)) return
            playSoundRule(roomBroadcastService.players(room), moduleConfig.specials.enderPearlLandedSound)
            return
        }
        val specialId = projectile.persistentDataContainer.get(specialItemKey, PersistentDataType.STRING) ?: return
        bridgeStates.remove(projectile.uniqueId)
        resourceScope?.releaseEntity(projectile.uniqueId)
        trackedEntities.remove(projectile.uniqueId)
        if (specialId != "bed-bug") return
        val owner = (projectile.shooter as? Player) ?: return
        val state = playerStates[owner.uniqueId] ?: return
        spawnSpecialMob(projectile.location, owner, state.teamId, specialId)
    }

    /** 限制床虫和梦境守卫只选择当前房间的敌方存活玩家。 */
    fun handleSpecialMobTarget(event: EntityTargetLivingEntityEvent): Boolean {
        val mobState = specialMobs[event.entity.uniqueId] ?: return false
        val target = event.target ?: return false
        if (target is Player) {
            val targetState = playerStates[target.uniqueId] ?: return true
            return targetState.eliminated || targetState.respawning || targetState.teamId == mobState.teamId
        }
        val targetMobState = specialMobs[target.uniqueId] ?: return true
        return targetMobState.teamId == mobState.teamId
    }

    /** 清空床虫和梦境守卫的原版战利品，并从房间实体追踪中移除。 */
    fun handleSpecialMobDeath(event: EntityDeathEvent) {
        val entityId = event.entity.uniqueId
        if (specialMobs.remove(entityId) == null) return
        event.drops.clear()
        event.droppedExp = 0
        resourceScope?.releaseEntity(entityId)
        trackedEntities.remove(entityId)
    }

    /** 对齐参考牛奶和蛋糕消费语义，并在有效牛奶消费时手动提交陷阱免疫。 */
    fun handleItemConsume(event: PlayerItemConsumeEvent) {
        if (event.item.type == Material.CAKE) {
            event.isCancelled = true
            return
        }
        if (event.item.type == Material.MILK_BUCKET) {
            val state = playerStates[event.player.uniqueId]
            if (state == null || !isActiveParticipant(event.player)) {
                event.isCancelled = true
                return
            }
            event.isCancelled = true
            consumeHeldItem(event.player, event.hand, event.item)
            activateMagicMilk(event.player, state)
        }
    }

    /** 在模块药水最终成功饮用后安排空玻璃瓶清理。 */
    fun handleItemConsumeComplete(event: PlayerItemConsumeEvent) {
        if (event.item.itemMeta?.persistentDataContainer?.has(potionItemKey, PersistentDataType.STRING) != true) return
        roomTaskService.runTaskLater(room.id, POTION_BOTTLE_CLEANUP_DELAY_TICKS, Runnable {
            if (roomManager.getPlayerRoom(event.player)?.id == room.id) removeOneMaterial(event.player, Material.GLASS_BOTTLE)
        })
    }

    /** 开启玩家的限时陷阱免疫并发送统一饮用反馈。 */
    private fun activateMagicMilk(player: Player, state: BedWarsPlayerState) {
        state.trapImmuneUntilMillis = System.currentTimeMillis() + moduleConfig.specials.magicMilkSeconds * 1000L
        player.playSound(player.location, Sound.ENTITY_GENERIC_DRINK, 1f, 1f)
        player.sendMessage(Component.text(language.getMessage("bedwars.magic_milk_active", moduleConfig.specials.magicMilkSeconds)))
    }

    private fun tickCountdown() {
        val onlinePlayers = onlineParticipantCount()
        if (onlinePlayers < effectiveMinPlayers()) {
            cancelCountdown()
            return
        }
        shortenCountdownForPopulation(onlinePlayers)
        if (phaseTimer.isSecondBoundary) {
            roomBroadcastService.actionBar(
                room,
                language.getMessage("bedwars.countdown", phaseTimer.secondsLeft),
                includeSpectators = true
            )
            val seconds = phaseTimer.secondsLeft
            if (seconds > 0 && (seconds % 10 == 0 || seconds <= 5)) {
                playSoundRule(roomBroadcastService.players(room), countdownSound(seconds))
            }
        }
        if (!phaseTimer.tick()) return
        phase = BedWarsPhase.RUNNING
        phaseTimer.resetSeconds(moduleConfig.durationSeconds)
        room.state = GameState.RUNNING
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            resetPlayer(player, GameMode.SURVIVAL, clearInventory = true)
            val state = playerStates[player.uniqueId] ?: return@forEach
            giveLoadout(player, state)
            state.firstSpawned = true
            setTeamNametag(player, state.teamId)
            Bukkit.getPluginManager().callEvent(GamePlayerFirstSpawnedEvent(room, player, state.teamId))
        }
        updateTimelineStage()
        playSoundRule(roomBroadcastService.players(room), moduleConfig.gameStartSound)
        roomBroadcastService.localized(room, language, "bedwars.started", includeSpectators = true)
        updateDisplays()
        updateTabHeaderFooters()
        updateTabPlayerNames()
    }

    /** 按参考有效路径为最后 4 秒选择独立音效，其余提示节点使用通用规则。 */
    private fun countdownSound(seconds: Int): BedWarsSoundRule {
        return moduleConfig.countdownFinalSounds[seconds] ?: moduleConfig.countdownSound
    }

    /** 按配置向指定玩家播放可关闭的 BedWars 事件音效。 */
    private fun playSoundRule(players: Collection<Player>, rule: BedWarsSoundRule) {
        val sound = rule.sound ?: return
        players.forEach { player -> player.playSound(player.location, sound, rule.volume, rule.pitch) }
    }

    /** 按配置在指定世界位置播放可关闭的 BedWars 事件音效。 */
    private fun playSoundRule(location: Location, rule: BedWarsSoundRule) {
        val sound = rule.sound ?: return
        location.world.playSound(location, sound, rule.volume, rule.pitch)
    }

    /** 在活动窗口内延迟一秒播放参考洞穴环境音，并复核玩家仍是本房间参赛者。 */
    private fun scheduleHalloweenAmbience(player: Player) {
        if (!moduleConfig.halloweenActive) return
        halloweenAmbienceTasks.remove(player.uniqueId)?.cancel()
        val task = roomTaskService.runTaskLater(room.id, 20L, Runnable {
            halloweenAmbienceTasks.remove(player.uniqueId)
            if (!player.isOnline || roomManager.getPlayerRoom(player)?.id != room.id || player.uniqueId !in room.players) {
                return@Runnable
            }
            player.world.playSound(player.location, Sound.AMBIENT_CAVE, 3.0f, 1.0f)
        })
        halloweenAmbienceTasks[player.uniqueId] = task
    }

    /** 在活动击杀点播放参考音效，并在未受保护的空气方块生成临时蜘蛛网。 */
    private fun spawnHalloweenKillCobweb(victim: Player) {
        if (!moduleConfig.halloweenActive) return
        val block = victim.location.clone().add(0.0, 1.0, 0.0).block
        if (!block.isEmpty) return
        block.world.playSound(block.location, Sound.ENTITY_GHAST_SCREAM, 2.0f, 1.0f)
        if (isProtectedLocation(block.location)) return
        val blockKey = BedWarsBlockKey.from(block.location) ?: return
        resourceScope?.captureBlock(block)
        block.type = Material.COBWEB
        placedBlocks.add(blockKey)
        halloweenCobwebs[blockKey] = gameElapsedTicks + HALLOWEEN_COBWEB_LIFETIME_TICKS
    }

    private fun tickRunning() {
        gameElapsedTicks++
        if (gameElapsedTicks % LEVEL_EXPERIENCE_INTERVAL_TICKS == 0) tickMinuteRewards()
        if (gameElapsedTicks % 20 == 0) {
            tickHealPools()
            tickAfkPlayers()
        }
        tickTntCarrierParticles()
        tickBridgeEggs()
        tickSpecialMobs()
        tickPopupTowers()
        tickHalloweenCobwebs()
        tickSuddenDeath()
        tickDragons()
        tickInvisibility()
        tickGenerators()
        tickBedsDestroy()
        tickRespawns()
        updateTimelineStage()
        if (phaseTimer.isSecondBoundary && phaseTimer.secondsLeft % 60 == 0) {
            roomBroadcastService.actionBar(
                room,
                language.getMessage("bedwars.time_left", phaseTimer.secondsLeft / 60),
                includeSpectators = true
            )
        }
        if (phase == BedWarsPhase.RUNNING && phaseTimer.tick()) finish(null)
    }

    /** 到期后按参考 7.5 秒寿命自然破坏活动蜘蛛网并释放放置追踪。 */
    private fun tickHalloweenCobwebs() {
        if (halloweenCobwebs.isEmpty()) return
        val world = room.world ?: return halloweenCobwebs.clear()
        val iterator = halloweenCobwebs.iterator()
        while (iterator.hasNext()) {
            val (key, expiresAtTick) = iterator.next()
            if (expiresAtTick > gameElapsedTicks) continue
            val block = world.getBlockAt(key.x, key.y, key.z)
            if (block.type == Material.COBWEB) {
                placedBlocks.remove(key)
                block.breakNaturally()
            }
            iterator.remove()
        }
    }

    /** 清除玩家的房间空闲计时和 AFK 状态。 */
    fun markPlayerActive(player: Player) {
        val idleSeconds = inactivitySeconds.remove(player.uniqueId) ?: 0
        if (afkPlayers.remove(player.uniqueId)) {
            Bukkit.getPluginManager().callEvent(GamePlayerAfkStateChangedEvent(room, player, false, idleSeconds))
        }
    }

    /** 判断玩家身份或 AFK 状态是否要求拒绝本次地面物品拾取。 */
    fun shouldCancelPickup(player: Player, item: Item): Boolean {
        if (!isActiveParticipant(player)) return true
        if (GENERATOR_RESOURCE_TAG !in item.scoreboardTags) return false
        if (player.uniqueId in afkPlayers) return true
        val collectEvent = GameResourceCollectEvent(room, player, item)
        Bukkit.getPluginManager().callEvent(collectEvent)
        return collectEvent.isCancelled
    }

    /** 每秒累计在线有效参赛者的静止时间，并在阈值后登记 AFK。 */
    private fun tickAfkPlayers() {
        if (moduleConfig.afkSeconds <= 0) return
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            if (!isActiveParticipant(player)) return@forEach
            val seconds = (inactivitySeconds[player.uniqueId] ?: 0) + 1
            inactivitySeconds[player.uniqueId] = seconds
            if (seconds >= moduleConfig.afkSeconds && afkPlayers.add(player.uniqueId)) {
                Bukkit.getPluginManager().callEvent(GamePlayerAfkStateChangedEvent(room, player, true, seconds))
            }
        }
    }

    /** 向当前房间其他 viewer 标记携带 TNT 且未隐身的有效参赛者。 */
    private fun tickTntCarrierParticles() {
        if (!moduleConfig.specials.tntSpoilCarriers) return
        val carriers = roomBroadcastService.players(room).filter { player ->
            isActiveParticipant(player) &&
                player.inventory.contains(Material.TNT) &&
                !player.hasPotionEffect(PotionEffectType.INVISIBILITY)
        }
        if (carriers.isEmpty()) return
        val viewers = roomBroadcastService.participants(room)
        carriers.forEach { carrier ->
            val location = carrier.location.add(0.0, 2.6, 0.0)
            viewers.filter { it != carrier && it.world == carrier.world }.forEach { viewer ->
                viewer.spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, TNT_CARRIER_DUST)
            }
        }
    }

    /** 沿桥蛋飞行轨迹生成队色羊毛，并限制最大距离与下落高度。 */
    private fun tickBridgeEggs() {
        val iterator = bridgeStates.iterator()
        while (iterator.hasNext()) {
            val (_, state) = iterator.next()
            val projectile = Bukkit.getEntity(state.projectileId) as? Egg
            if (projectile == null || !projectile.isValid || projectile.isDead) {
                iterator.remove()
                continue
            }
            val owner = Bukkit.getPlayer(state.ownerId)
            val ownerState = playerStates[state.ownerId]
            if (owner == null || !owner.isOnline || ownerState?.participant != true || ownerState.eliminated) {
                projectile.remove()
                resourceScope?.releaseEntity(state.projectileId)
                trackedEntities.remove(state.projectileId)
                iterator.remove()
                continue
            }
            val location = projectile.location
            if (owner.world != location.world ||
                owner.location.distanceSquared(location) > moduleConfig.specials.bridgeMaxDistance * moduleConfig.specials.bridgeMaxDistance ||
                owner.location.y - location.y > moduleConfig.specials.bridgeMaxVerticalDrop
            ) {
                projectile.remove()
                resourceScope?.releaseEntity(projectile.uniqueId)
                trackedEntities.remove(projectile.uniqueId)
                iterator.remove()
                continue
            }
            if (owner.location.distanceSquared(location) <=
                moduleConfig.specials.bridgeStartDistance * moduleConfig.specials.bridgeStartDistance
            ) continue
            val teamMaterial = teamStates[state.teamId]?.config?.color?.wool ?: Material.WHITE_WOOL
            val baseY = location.blockY - 2
            val offsets = listOf(0 to 0, -1 to 0, 0 to -1)
            var blocksBuilt = 0
            offsets.forEach { (offsetX, offsetZ) ->
                val block = location.world.getBlockAt(location.blockX + offsetX, baseY, location.blockZ + offsetZ)
                if (!block.isEmpty || isProtectedLocation(block.location)) return@forEach
                resourceScope?.captureBlock(block)
                block.type = teamMaterial
                BedWarsBlockKey.from(block.location)?.let(placedBlocks::add)
                Bukkit.getPluginManager().callEvent(
                    GameStructureBlockPlacedEvent(room, owner, state.teamId, "bridge-egg", block)
                )
                block.world.spawnParticle(
                    Particle.FLAME,
                    block.location.add(0.5, 0.6, 0.5),
                    3,
                    0.15,
                    0.15,
                    0.15,
                    0.01
                )
                blocksBuilt++
            }
            Bukkit.getPlayer(state.ownerId)?.let { owner ->
                repeat(blocksBuilt) {
                    playSoundRule(listOf(owner), moduleConfig.specials.bridgeBlockSound)
                }
            }
        }
    }

    /** 清理到期召唤物，并周期性为其选择最近的敌方玩家。 */
    private fun tickSpecialMobs() {
        val iterator = specialMobs.iterator()
        while (iterator.hasNext()) {
            val (entityId, state) = iterator.next()
            val mob = Bukkit.getEntity(entityId) as? Mob
            if (mob == null || !mob.isValid || mob.isDead || gameElapsedTicks >= state.expiresAtTick) {
                mob?.remove()
                resourceScope?.releaseEntity(entityId)
                trackedEntities.remove(entityId)
                iterator.remove()
                continue
            }
            if (gameElapsedTicks % 20 != 0) continue
            updateSpecialMobName(mob, state)
            val playerTargets = playerStates.entries
                .asSequence()
                .filter { it.value.teamId != state.teamId && !it.value.eliminated && !it.value.respawning }
                .mapNotNull { Bukkit.getPlayer(it.key) }
                .filter { it.world == mob.world && it.isOnline }
            val mobTargets = specialMobs.entries
                .asSequence()
                .filter { it.key != entityId && it.value.teamId != state.teamId }
                .mapNotNull { Bukkit.getEntity(it.key) as? LivingEntity }
                .filter { it.world == mob.world && it.isValid && !it.isDead }
            mob.target = (playerTargets + mobTargets)
                .minByOrNull { it.location.distanceSquared(mob.location) }
        }
    }

    /** 每 tick 按配置批量展开弹出塔，并把生成方块纳入本局建筑追踪。 */
    private fun tickPopupTowers() {
        val iterator = towerStates.iterator()
        while (iterator.hasNext()) {
            val tower = iterator.next()
            var placed = 0
            while (placed < moduleConfig.specials.towerBlocksPerTick && tower.placements.isNotEmpty()) {
                val placement = tower.placements.removeFirst()
                val block = tower.origin.block.getRelative(placement.offsetX, placement.offsetY, placement.offsetZ)
                if (!block.isEmpty || block.y >= (gameConfig?.maxBuildY ?: moduleConfig.maxBuildY) || isProtectedLocation(block.location)) {
                    continue
                }
                if (placement.ladderFacing != null && !block.getRelative(placement.ladderFacing.oppositeFace).type.isSolid) {
                    continue
                }
                resourceScope?.captureBlock(block)
                if (placement.ladderFacing == null) {
                    block.type = teamStates[tower.teamId]?.config?.color?.wool ?: Material.WHITE_WOOL
                } else {
                    val data = Material.LADDER.createBlockData() as Ladder
                    data.facing = placement.ladderFacing
                    block.blockData = data
                }
                BedWarsBlockKey.from(block.location)?.let(placedBlocks::add)
                Bukkit.getPluginManager().callEvent(
                    GameStructureBlockPlacedEvent(
                        room,
                        Bukkit.getPlayer(tower.ownerId),
                        tower.teamId,
                        "pop-up-tower",
                        block
                    )
                )
                placed++
            }
            playSoundRule(tower.origin, moduleConfig.specials.popupTowerBuildSound)
            if (tower.placements.isEmpty()) iterator.remove()
        }
    }

    private fun tickRespawns() {
        playerStates.forEach { (playerId, state) ->
            if (!state.respawning || state.respawnTicks < 0) return@forEach
            if (state.respawnTicks > 0) {
                if (state.respawnTicks % 20 == 0) {
                    Bukkit.getPlayer(playerId)?.sendActionBar(
                        Component.text(language.getMessage("bedwars.respawn_countdown", (state.respawnTicks + 19) / 20))
                    )
                }
                state.respawnTicks--
                return@forEach
            }
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            if (roomManager.getPlayerRoom(player)?.id != room.id) return@forEach
            state.respawning = false
            resetPlayer(player, GameMode.SURVIVAL, clearInventory = true)
            giveLoadout(player, state)
            startRespawnProtection(state)
            player.teleport(teamSpawn(state.teamId) ?: return@forEach)
            setTeamNametag(player, state.teamId)
            player.sendMessage(Component.text(language.getMessage("bedwars.respawned")))
            showRespawnedTitle(player)
            Bukkit.getPluginManager().callEvent(GamePlayerRespawnedEvent(room, player, state.teamId))
        }
    }

    /** 向可复活死亡玩家显示死亡标题和配置复活秒数。 */
    private fun showDeathTitle(player: Player) {
        player.showTitle(Title.title(
            Component.text(language.getMessage("bedwars.death_title")),
            Component.text(language.getMessage("bedwars.death_subtitle", moduleConfig.respawnSeconds)),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
        ))
    }

    /** 在正常倒计时或重连恢复完成后显示复活标题并播放本人音效。 */
    private fun showRespawnedTitle(player: Player) {
        player.showTitle(Title.title(
            Component.text(language.getMessage("bedwars.respawned_title")),
            Component.empty(),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500))
        ))
        playSoundRule(listOf(player), moduleConfig.respawnSound)
    }

    /** 从当前对局 tick 开始计算玩家的可配置复活保护截止时间。 */
    private fun startRespawnProtection(state: BedWarsPlayerState) {
        state.respawnProtectionUntilTick = gameElapsedTicks + moduleConfig.respawnInvulnerabilitySeconds * 20
    }

    /** 判断玩家是否仍在复活保护窗口，并顺便清理已过期截止值。 */
    private fun isRespawnProtected(state: BedWarsPlayerState): Boolean {
        return respawnProtectionTicksLeft(state) > 0
    }

    /** 返回玩家剩余复活保护 tick。 */
    private fun respawnProtectionTicksLeft(state: BedWarsPlayerState): Int {
        val remaining = (state.respawnProtectionUntilTick - gameElapsedTicks).coerceAtLeast(0)
        if (remaining == 0) state.respawnProtectionUntilTick = 0
        return remaining
    }

    private fun tickResult() {
        if (!phaseTimer.tick()) return
        phase = BedWarsPhase.CLOSING
        phaseTimer.resetSeconds(moduleConfig.closeDelaySeconds)
        updateTabHeaderFooters()
        updateTabPlayerNames()
    }

    private fun tickClosing() {
        if (phaseTimer.tick() && !closed) {
            closed = true
            roomManager.closeRoom(room.id)
        }
    }

    private fun eliminate(
        player: Player,
        state: BedWarsPlayerState,
        killer: Player?,
        specialHit: BedWarsSpecialMobHitState?,
        damageCause: EntityDamageEvent.DamageCause?
    ) {
        state.eliminated = true
        state.respawning = false
        state.respawnProtectionUntilTick = 0
        state.finalDeaths++
        resultService.addMetric(room, player.uniqueId, METRIC_FINAL_DEATHS)
        updatePlayerCollision(player, state.teamId)
        nametagService.clear(room, player)
        eliminationService.eliminate(
            room,
            player,
            spectatorSpawn(),
            spectatorPolicy = moduleConfig.toSpectatorPolicy(
                language,
                enabled = true,
                mode = SpectatorMode.MANAGED
            )
        )
        val messageKey = if (killer == null && specialHit != null) {
            specialMobDeathKey(specialHit.specialId, finalKill = true)
        } else {
            deathMessageKey(killer, damageCause, finalKill = true)
        }
        sendDeathFeedback(
            victimId = player.uniqueId,
            victim = player,
            victimTeamId = state.teamId,
            killerId = killer?.uniqueId,
            killer = killer,
            damageCause = damageCause,
            sourceId = specialHit?.specialId,
            finalDeath = true,
            message = Component.text(language.getMessage(
                messageKey,
                player.name,
                killer?.name ?: specialHit?.let {
                    teamStates[it.teamId]?.config?.displayName ?: it.teamId
                } ?: "-"
            ))
        )
        publishDeathResolved(player, state.teamId, killer, damageCause, specialHit?.specialId, finalDeath = true)
        announceTeamEliminated(state.teamId)
        updateTabPlayerName(player)
        checkWinner()
    }

    private fun checkWinner() {
        if (phase != BedWarsPhase.RUNNING) return
        val aliveTeams = playerStates.values
            .filter { it.participant && !it.eliminated }
            .map { it.teamId }
            .toSet()
        if (aliveTeams.size <= 1) finish(aliveTeams.firstOrNull())
    }

    /** 判断队伍是否已经不存在仍保留参赛资格的成员。 */
    private fun isTeamEliminated(teamId: String): Boolean {
        return playerStates.values.none { it.teamId == teamId && it.participant && !it.eliminated }
    }

    /** 在队伍失去最后一名有效成员时仅广播一次整队淘汰，并登记空队状态。 */
    private fun announceTeamEliminated(teamId: String) {
        if (!isTeamEliminated(teamId) || !eliminatedTeams.add(teamId)) return
        Bukkit.getPluginManager().callEvent(GameTeamEliminatedEvent(room, teamId))
        val teamName = teamStates[teamId]?.config?.displayName ?: teamId
        roomBroadcastService.localized(room, language, "bedwars.team_eliminated", teamName, includeSpectators = true)
    }

    private fun finish(winnerTeamId: String?) {
        if (phase == BedWarsPhase.RESULT || phase == BedWarsPhase.CLOSING) return
        clearHealPoolEffects()
        resultWinnerTeamId = winnerTeamId
        phase = BedWarsPhase.RESULT
        phaseTimer.resetSeconds(moduleConfig.resultDisplaySeconds)
        room.state = GameState.ENDING
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = playerStates[player.uniqueId] ?: return@forEach
            if (state.eliminated) {
                updatePlayerCollision(player, state.teamId)
            } else {
                setTeamNametag(player, state.teamId)
            }
        }
        playSoundRule(roomBroadcastService.participants(room), moduleConfig.gameEndSound)
        val winners = participants.filterTo(linkedSetOf()) { playerStates[it]?.teamId == winnerTeamId }
        val activeWinners = winners.filterTo(linkedSetOf()) { playerStates[it]?.eliminated == false }
        if (!resultRecorded) {
            resultRecorded = true
            awardResultLevelExperience(winners)
            awardResultMoney(winners)
            resultService.recordWinLoss(
                room,
                participants,
                winners,
                moduleConfig.winPoints,
                winnerGroupId = winnerTeamId,
                activeWinners = activeWinners
            )
        }
        val winnerName = winnerTeamId?.let(teamStates::get)?.config?.displayName
        roomBroadcastService.localized(
            room,
            language,
            if (winnerName == null) "bedwars.draw" else "bedwars.winner",
            winnerName ?: "-",
            includeSpectators = true
        )
        announceResultLeaders()
        showResultTitles(winners, winnerName)
        prepareResultPlayers(activeWinners)
        updateDisplays()
        updateTabHeaderFooters()
        updateTabPlayerNames()
    }

    /** 每个完整运行分钟向仍可行动的在线参赛者发放参考经验和金币。 */
    private fun tickMinuteRewards() {
        room.players.mapNotNull(Bukkit::getPlayer)
            .filter(::isActiveParticipant)
            .forEach { player ->
                awardLevelExperience(
                    player.uniqueId,
                    moduleConfig.levelRules.perMinuteExperience,
                    XP_SOURCE_PER_MINUTE
                )
                awardMoney(
                    player,
                    moduleConfig.moneyRewardRules.perMinute,
                    "bedwars.money_reward_per_minute"
                )
            }
    }

    /** 在整局唯一结算点向胜者和历史队伍成员发放胜利、队友经验。 */
    private fun awardResultLevelExperience(winners: Set<UUID>) {
        winners.forEach { playerId ->
            awardLevelExperience(playerId, moduleConfig.levelRules.gameWinExperience, XP_SOURCE_GAME_WIN)
        }
        val teamSizes = participants.groupingBy { playerStates[it]?.teamId }.eachCount()
        participants.forEach { playerId ->
            val teamId = playerStates[playerId]?.teamId ?: return@forEach
            val teamSize = teamSizes[teamId] ?: 0
            if (teamSize <= 1) return@forEach
            val amount = (moduleConfig.levelRules.perTeammateExperience.toLong() * teamSize)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            awardLevelExperience(playerId, amount, XP_SOURCE_PER_TEAMMATE)
        }
    }

    /** 在整局唯一结算点向在线胜者和多人队地图参与者发放参考金币。 */
    private fun awardResultMoney(winners: Set<UUID>) {
        winners.mapNotNull(Bukkit::getPlayer).forEach { player ->
            awardMoney(player, moduleConfig.moneyRewardRules.gameWin, "bedwars.money_reward_game_win")
        }
        if (gameConfig?.teams?.any { it.maxPlayers > 1 } != true) return
        participants.mapNotNull(Bukkit::getPlayer).forEach { player ->
            awardMoney(player, moduleConfig.moneyRewardRules.perTeammate, "bedwars.money_reward_per_teammate")
        }
    }

    /** 仅在可选 Vault Economy 明确完成存款后向在线玩家发送金币奖励反馈。 */
    private fun awardMoney(player: Player, requestedAmount: Int, messageKey: String) {
        if (requestedAmount <= 0 || !vaultEconomy.deposit(player, requestedAmount)) return
        player.sendMessage(Component.text(language.getMessage(messageKey, requestedAmount)))
    }

    /** 持久化累计经验，发布等级与经验事件，并向在线玩家发送来源反馈。 */
    private fun awardLevelExperience(playerId: UUID, requestedAmount: Int, sourceId: String) {
        val rules = moduleConfig.levelRules
        if (!rules.enabled || requestedAmount <= 0) return
        val previousTotal = resultService.metric(room, playerId, METRIC_LEVEL_EXPERIENCE)
        val previous = levelProgress(previousTotal)
        resultService.addMetric(room, playerId, METRIC_LEVEL_EXPERIENCE, requestedAmount)
        val totalExperience = resultService.metric(room, playerId, METRIC_LEVEL_EXPERIENCE)
        val amount = totalExperience - previousTotal
        if (amount <= 0) return
        val progress = levelProgress(totalExperience)
        val player = Bukkit.getPlayer(playerId)
        if (progress.level > previous.level) {
            Bukkit.getPluginManager().callEvent(
                GamePlayerLevelUpEvent(
                    room,
                    playerId,
                    player,
                    sourceId,
                    previous.level,
                    progress.level,
                    progress.levelExperience,
                    progress.nextLevelExperience
                )
            )
        }
        Bukkit.getPluginManager().callEvent(
            GamePlayerExperienceGainedEvent(
                room,
                playerId,
                player,
                sourceId,
                amount,
                totalExperience,
                progress.level,
                progress.levelExperience,
                progress.nextLevelExperience
            )
        )
        player?.sendMessage(Component.text(language.getMessage(
            "bedwars.xp_reward",
            amount,
            levelSourceName(sourceId)
        )))
        if (progress.level > previous.level) {
            player?.sendMessage(Component.text(language.getMessage(
                "bedwars.level_up",
                progress.level,
                progress.nextLevelExperience
            )))
        }
    }

    /** 由累计经验和配置曲线派生等级，支持一次奖励连续跨越多级。 */
    private fun levelProgress(totalExperience: Int): BedWarsLevelProgress {
        val rules = moduleConfig.levelRules
        var remaining = totalExperience.coerceAtLeast(0).toLong()
        var level = 1L
        rules.rankupCosts.forEach { cost ->
            if (remaining < cost) {
                return BedWarsLevelProgress(level.toInt(), remaining.toInt(), cost)
            }
            remaining -= cost
            level++
        }
        val defaultCost = rules.defaultRankupCost.toLong()
        level = (level + remaining / defaultCost).coerceAtMost(Int.MAX_VALUE.toLong())
        remaining %= defaultCost
        return BedWarsLevelProgress(level.toInt(), remaining.toInt(), rules.defaultRankupCost)
    }

    /** 返回经验来源稳定 ID 对应的本地化名称。 */
    private fun levelSourceName(sourceId: String): String {
        return language.getMessage("bedwars.xp_source_${sourceId.replace('-', '_')}")
    }

    /** 按地图结算规则保留仍有效的获胜者，并显示或隐藏已淘汰参赛者。 */
    private fun prepareResultPlayers(aliveWinners: Set<UUID>) {
        restoreAllInvisibleAppearances()
        clearResultInventoriesAndDrops(aliveWinners)
        val configured = gameConfig ?: return
        val onlineWinners = aliveWinners.mapNotNull(Bukkit::getPlayer).filterNot(Player::isDead)
        val camera = onlineWinners.randomOrNull()?.location?.clone()?.apply {
            direction = direction.multiply(-1)
            add(0.0, 2.0, 0.0)
        }
        room.players.mapNotNull(Bukkit::getPlayer).filterNot(Player::isDead).forEach { player ->
            if (player.uniqueId in aliveWinners) {
                player.isInvulnerable = true
                return@forEach
            }
            if (configured.showEliminatedAtGameEnd) {
                eliminationService.clear(room.id, player.uniqueId)
                if (spectatorService.isSpectator(player)) spectatorService.exit(player)
                player.gameMode = GameMode.ADVENTURE
                player.isInvisible = false
                player.isInvulnerable = true
                player.allowFlight = true
                player.isFlying = true
                player.inventory.clear()
                playerStates[player.uniqueId]?.let { setTeamNametag(player, it.teamId) }
            } else {
                enterEliminatedSpectator(player)
            }
            if (configured.teleportEliminatedAtGameEnd) player.teleport(camera ?: spectatorSpawn())
        }
        if (configured.teleportEliminatedAtGameEnd) {
            room.spectators.mapNotNull(Bukkit::getPlayer).forEach { it.teleport(camera ?: spectatorSpawn()) }
        }
    }

    /** 进入结算时关闭残留菜单、清空存活获胜者背包并移除全部地面物品。 */
    private fun clearResultInventoriesAndDrops(aliveWinners: Set<UUID>) {
        (room.players + room.spectators).mapNotNull(Bukkit::getPlayer).forEach { player ->
            player.closeInventory()
            if (player.uniqueId in aliveWinners) player.inventory.clear()
        }
        room.world?.entities?.filterIsInstance<Item>()?.forEach { item ->
            releaseTrackedEntity(item.uniqueId)
            item.remove()
        }
    }

    /** 按获胜历史成员、失败者和平局逐人发送准确的结算标题。 */
    private fun showResultTitles(winners: Set<UUID>, winnerName: String?) {
        (room.players + room.spectators).mapNotNull(Bukkit::getPlayer).forEach { player ->
            val titleKey = when {
                winnerName == null -> "bedwars.draw_title"
                player.uniqueId in winners -> "bedwars.victory_title"
                else -> "bedwars.win_title"
            }
            val subtitle = if (winnerName == null) {
                language.getMessage("bedwars.draw_subtitle")
            } else {
                language.getMessage("bedwars.result_subtitle", winnerName)
            }
            player.showTitle(Title.title(
                Component.text(language.getMessage(titleKey)),
                Component.text(subtitle),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))
            ))
        }
    }

    /** 解析模块强制原因、虚空位置和 Bukkit 最后伤害中的唯一死亡原因。 */
    private fun resolvedDeathDamageCause(player: Player): EntityDamageEvent.DamageCause? {
        val voidY = gameConfig?.voidY ?: moduleConfig.defaultVoidY
        return pendingDeathCauses[player.uniqueId] ?: if (player.location.y <= voidY) {
            EntityDamageEvent.DamageCause.VOID
        } else {
            player.lastDamageCause?.cause
        }
    }

    /** 按已解析死亡原因选择普通或最终击杀消息，并保留现有攻击者归因。 */
    private fun deathMessageKey(
        killer: Player?,
        cause: EntityDamageEvent.DamageCause?,
        finalKill: Boolean
    ): String {
        val suffix = if (finalKill) "final" else "regular"
        return when (cause) {
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION -> if (killer == null) {
                "bedwars.player_exploded_$suffix"
            } else {
                "bedwars.player_exploded_by_$suffix"
            }
            EntityDamageEvent.DamageCause.VOID -> if (killer == null) {
                "bedwars.player_void_$suffix"
            } else {
                "bedwars.player_knocked_void_$suffix"
            }
            EntityDamageEvent.DamageCause.FALL -> if (killer == null) {
                "bedwars.player_fell_$suffix"
            } else {
                "bedwars.player_knocked_fall_$suffix"
            }
            EntityDamageEvent.DamageCause.PROJECTILE -> if (killer == null) {
                if (finalKill) "bedwars.player_eliminated" else "bedwars.player_died"
            } else {
                "bedwars.player_shot_$suffix"
            }
            else -> when {
                killer != null && finalKill -> "bedwars.player_final_killed"
                killer != null -> "bedwars.player_killed"
                finalKill -> "bedwars.player_eliminated"
                else -> "bedwars.player_died"
            }
        }
    }

    /** 发布可变死亡反馈，并按监听器结果发送房间消息和击杀者音效。 */
    private fun sendDeathFeedback(
        victimId: UUID,
        victim: Player?,
        victimTeamId: String,
        killerId: UUID?,
        killer: Player?,
        damageCause: EntityDamageEvent.DamageCause?,
        sourceId: String?,
        finalDeath: Boolean,
        message: Component?
    ) {
        val feedbackEvent = GamePlayerDeathFeedbackEvent(
            room,
            victimId,
            victim,
            victimTeamId,
            killerId,
            killer,
            killerId?.let { playerStates[it]?.teamId },
            damageCause,
            sourceId,
            finalDeath,
            message,
            playKillerSound = true
        )
        Bukkit.getPluginManager().callEvent(feedbackEvent)
        if (feedbackEvent.playKillerSound && killer != null) {
            playSoundRule(listOf(killer), moduleConfig.killSound)
        }
        feedbackEvent.message?.let { roomBroadcastService.message(room, it, includeSpectators = true) }
    }

    /** 发布在线玩家已经完成状态提交的死亡或最终淘汰结果。 */
    private fun publishDeathResolved(
        victim: Player,
        victimTeamId: String,
        killer: Player?,
        damageCause: EntityDamageEvent.DamageCause?,
        sourceId: String?,
        finalDeath: Boolean
    ) {
        publishDeathResolved(
            victim.uniqueId,
            victim,
            victimTeamId,
            killer?.uniqueId,
            killer,
            damageCause,
            sourceId,
            finalDeath
        )
    }

    /** 发布支持离线受害者和离线击杀者 UUID 的统一死亡结果。 */
    private fun publishDeathResolved(
        victimId: UUID,
        victim: Player?,
        victimTeamId: String,
        killerId: UUID?,
        killer: Player?,
        damageCause: EntityDamageEvent.DamageCause?,
        sourceId: String?,
        finalDeath: Boolean
    ) {
        Bukkit.getPluginManager().callEvent(
            GamePlayerDeathResolvedEvent(
                room,
                victimId,
                victim,
                victimTeamId,
                killerId,
                killer,
                killerId?.let { playerStates[it]?.teamId },
                damageCause,
                sourceId,
                finalDeath
            )
        )
    }

    /** 按聊天榜配置的统计项广播本局前三名。 */
    private fun announceResultLeaders() {
        val statistic = gameConfig?.chatTopStatistic ?: moduleConfig.chatTopStatistic
        val hideMissing = gameConfig?.chatTopHideMissing ?: moduleConfig.chatTopHideMissing
        resultLeaders(statistic, hideMissing)
            .forEachIndexed { index, (playerId, state) ->
                val playerName = Bukkit.getPlayer(playerId)?.name
                    ?: Bukkit.getOfflinePlayer(playerId).name
                    ?: playerId.toString().take(8)
                roomBroadcastService.localized(
                    room,
                    language,
                    "bedwars.result_top_stat",
                    index + 1,
                    playerName,
                    statistic.valueOf(state),
                    language.getMessage(statistic.languageKey),
                    includeSpectators = true
                )
            }
    }

    /** 按指定统计项和稳定次序返回最多三名有效历史参赛者。 */
    private fun resultLeaders(
        statistic: BedWarsResultStatistic,
        hideMissing: Boolean
    ): List<Map.Entry<UUID, BedWarsPlayerState>> {
        return playerStates.entries
            .asSequence()
            .filter { it.key in participants && it.value.participant }
            .filter { !hideMissing || statistic.valueOf(it.value) > 0 }
            .sortedWith(
                compareByDescending<Map.Entry<UUID, BedWarsPlayerState>> { statistic.valueOf(it.value) }
                    .thenByDescending { it.value.kills }
                    .thenByDescending { it.value.finalKills }
                    .thenByDescending { it.value.bedsBroken }
                    .thenByDescending { it.value.deaths }
                    .thenByDescending { it.value.finalDeaths }
                    .thenBy { it.key.toString() }
            )
            .take(3)
            .toList()
    }

    private fun failConfiguration(errors: List<String>) {
        val details = errors.joinToString(", ")
        roomBroadcastService.localized(room, language, "bedwars.config_missing", details, includeSpectators = true)
        phase = BedWarsPhase.CLOSING
        room.state = GameState.ENDING
        phaseTimer.resetSeconds(1)
        updateTabHeaderFooters()
        updateTabPlayerNames()
    }

    private fun assignTeam(player: Player): String? {
        teamService.getTeam(room.id, player.uniqueId)?.let { return it.id }
        return teamAssignmentService.joinSmallestTeam(
            room.id,
            player,
            gameConfig?.teams.orEmpty().map { it.id }
        )
    }

    /** 同步等待大厅中的通用选队结果，并立即刷新玩家的队伍显示。 */
    private fun tickWaitingTeamSelections() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = playerStates[player.uniqueId] ?: return@forEach
            val selectedTeam = teamService.getTeam(room.id, player.uniqueId) ?: return@forEach
            if (state.teamId.equals(selectedTeam.id, ignoreCase = true)) return@forEach
            state.teamId = selectedTeam.id
            setTeamNametag(player, selectedTeam.id)
            updateDisplay(player)
            updateTabHeaderFooter(player)
            updateTabPlayerName(player)
            player.sendMessage(Component.text(language.getMessage("bedwars.team_selected", selectedTeam.displayName)))
        }
        if (onlineParticipantCount() >= effectiveMinPlayers()) roomManager.startRoom(room.id)
    }

    /** 返回当前在线且仍占用参赛席位的玩家数，断线宽限席位不计入开局判定。 */
    private fun onlineParticipantCount(): Int {
        return room.players.count { Bukkit.getPlayer(it)?.isOnline == true }
    }

    /** 返回当前房间实际使用的最低开局人数。 */
    private fun effectiveMinPlayers(): Int {
        return room.definition?.minPlayers ?: moduleConfig.minPlayers
    }

    /** 判断玩家是否应进入只对当前房间观战群体开放的聊天受众。 */
    private fun isChatSpectator(player: Player, state: BedWarsPlayerState?): Boolean {
        return roomManager.isSpectator(player.uniqueId) || state?.eliminated == true ||
            phase == BedWarsPhase.RESULT || phase == BedWarsPhase.CLOSING
    }

    /** 返回外部观战者、最终淘汰玩家和结算阶段参赛者组成的房间内受众。 */
    private fun chatSpectatorAudience(): Set<UUID> {
        return buildSet {
            addAll(room.spectators)
            if (phase == BedWarsPhase.RESULT || phase == BedWarsPhase.CLOSING) {
                addAll(room.players)
            } else {
                room.players.filterTo(this) { playerStates[it]?.eliminated == true }
            }
        }
    }

    /** 去除参考实现支持的感叹号、英文或当前语言喊话前缀，非喊话文本返回 null。 */
    private fun stripShoutPrefix(message: String): String? {
        val localizedPrefix = language.getMessage("bedwars.chat_shout_prefix").trim()
        return when {
            message.startsWith("!") -> message.drop(1).trim()
            message.startsWith("shout", ignoreCase = true) -> message.drop(5).trim()
            localizedPrefix.isNotEmpty() && message.startsWith(localizedPrefix, ignoreCase = true) ->
                message.drop(localizedPrefix.length).trim()
            else -> null
        }
    }

    /** 校验喊话内容和冷却，并生成全房间聊天路由。 */
    private fun routeShout(player: Player, message: String): GameChatRoute? {
        if (!player.hasPermission(PERMISSION_SHOUT) && !player.hasPermission("kagamecenter.admin")) {
            player.sendMessage(Component.text(language.getMessage("bedwars.chat_shout_no_permission")))
            return null
        }
        if (message.isBlank()) {
            player.sendMessage(Component.text(language.getMessage("bedwars.chat_shout_empty")))
            return null
        }
        val bypassCooldown = player.hasPermission(PERMISSION_SHOUT_BYPASS) ||
            player.hasPermission("kagamecenter.admin")
        val cooldownTicks = moduleConfig.shoutCooldownSeconds * 20
        val lastShoutTick = lastShoutTicks[player.uniqueId]
        if (!bypassCooldown && cooldownTicks > 0 && lastShoutTick != null) {
            val remainingTicks = cooldownTicks - (gameElapsedTicks - lastShoutTick)
            if (remainingTicks > 0) {
                player.sendMessage(Component.text(language.getMessage(
                    "bedwars.chat_shout_cooldown",
                    (remainingTicks + 19) / 20
                )))
                return null
            }
        }
        if (!bypassCooldown) lastShoutTicks[player.uniqueId] = gameElapsedTicks
        return GameChatRoute(
            GameChatChannel.ROOM,
            message,
            variant = "shout",
            audience = (room.players + room.spectators).toSet()
        )
    }

    /** 根据半满和满员阈值只缩短、不延长当前开局倒计时。 */
    private fun shortenCountdownForPopulation(onlinePlayers: Int) {
        val maxPlayers = (room.definition?.maxPlayers ?: moduleConfig.maxPlayers).coerceAtLeast(1)
        val targetSeconds = when {
            onlinePlayers >= maxPlayers -> moduleConfig.fullArenaCountdownSeconds
            onlinePlayers > effectiveMinPlayers() && onlinePlayers >= maxPlayers / 2 -> {
                moduleConfig.halfArenaCountdownSeconds
            }
            else -> return
        }
        if (phaseTimer.remainingTicks > targetSeconds * 20) phaseTimer.resetSeconds(targetSeconds)
    }

    /** 在人数不足时释放倒计时资源并把在线玩家恢复到等待大厅。 */
    private fun cancelCountdown() {
        phase = BedWarsPhase.WAITING
        phaseTimer.clear()
        room.state = GameState.WAITING
        removeShopNpcs()
        removeGeneratorHolograms()
        bedHolograms.keys.toList().forEach(::removeBedHologram)
        generatorStates.clear()
        announcedGeneratorTiers.clear()
        timelineInitialized = false
        currentTimelineStageId = null
        teamStates.clear()
        bedBlocks.clear()
        participants.clear()
        eliminatedTeams.clear()
        val lobby = room.world?.let { gameConfig?.lobby?.toLocation(it) }
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = playerStates[player.uniqueId] ?: return@forEach
            state.participant = true
            state.eliminated = false
            state.respawning = false
            state.disconnected = false
            resetPlayer(player, GameMode.ADVENTURE, clearInventory = true)
            player.teleport(lobby ?: player.world.spawnLocation)
            setTeamNametag(player, state.teamId)
            givePreGameItems(player)
        }
        roomBroadcastService.localized(room, language, "bedwars.countdown_cancelled", includeSpectators = true)
        updateDisplays()
        updateTabHeaderFooters()
        updateTabPlayerNames()
    }

    private fun registerTeams() {
        val teams = gameConfig?.teams.orEmpty().map {
            GameTeam(it.id, it.displayName, it.color.textColor, it.maxPlayers)
        }
        teamAssignmentService.registerTeams(room.id, teams)
    }

    private fun indexBeds() {
        bedBlocks.clear()
        val world = room.world ?: return
        gameConfig?.teams.orEmpty().forEach { team ->
            val block = team.bed?.toLocation(world)?.block ?: return@forEach
            if (!Tag.BEDS.isTagged(block.type)) return@forEach
            indexBedBlock(block, team.id)
            val data = block.blockData as? Bed ?: return@forEach
            val relative = if (data.part == Bed.Part.FOOT) data.facing else data.facing.oppositeFace
            indexBedBlock(block.getRelative(relative), team.id)
        }
    }

    private fun indexBedBlock(block: Block, teamId: String) {
        BedWarsBlockKey.from(block.location)?.let { bedBlocks[it] = teamId }
    }

    /** 为所有在线参赛玩家生成仅本人可见的己方床提示。 */
    private fun spawnBedHolograms() {
        if (!effectiveUseBedHologram) return
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = playerStates[player.uniqueId] ?: return@forEach
            if (state.participant) spawnBedHologram(player, state.teamId)
        }
    }

    /** 在己方床上方生成一个 viewer 私有盔甲架名称提示。 */
    private fun spawnBedHologram(viewer: Player, teamId: String) {
        if (!effectiveUseBedHologram) return
        removeBedHologram(viewer.uniqueId)
        val team = teamStates[teamId] ?: return
        val world = room.world ?: return
        val location = team.config.bed?.toLocation(world)?.block?.location?.add(0.5, 1.0, 0.5) ?: return
        val hologram = world.spawn(location, ArmorStand::class.java) {
            it.setGravity(false)
            it.isVisible = false
            it.isMarker = true
            it.isSmall = true
            it.isInvulnerable = true
            it.isSilent = true
            it.isPersistent = true
            it.removeWhenFarAway = false
            it.customName(Component.text(language.getMessage(
                if (team.bedAlive) "bedwars.bed_hologram_defend" else "bedwars.bed_hologram_destroyed"
            )))
            it.isCustomNameVisible = true
            it.addScoreboardTag("kgc_bedwars_bed_hologram")
        }
        bedHolograms[viewer.uniqueId] = hologram.uniqueId
        trackEntity(hologram, viewer.uniqueId, "bed-hologram")
        (room.players + room.spectators).mapNotNull(Bukkit::getPlayer).forEach { player ->
            if (player.uniqueId == viewer.uniqueId) {
                player.showEntity(plugin, hologram)
            } else {
                player.hideEntity(plugin, hologram)
            }
        }
    }

    /** 对新进入房间的 viewer 隐藏其他玩家的私有床提示。 */
    private fun hideBedHologramsFrom(viewer: Player) {
        bedHolograms.values.mapNotNull(Bukkit::getEntity).forEach { viewer.hideEntity(plugin, it) }
    }

    /** 玩家进入己方床四格范围时隐藏私有提示，离开后重新显示。 */
    private fun updateBedHologramVisibility(viewer: Player, state: BedWarsPlayerState, location: Location) {
        if (!effectiveUseBedHologram || state.eliminated || state.respawning) return
        val hologram = bedHolograms[viewer.uniqueId]?.let(Bukkit::getEntity) ?: return
        val bed = teamStates[state.teamId]?.config?.bed?.toLocation(location.world) ?: return
        if (location.distanceSquared(bed) < BED_HOLOGRAM_HIDE_DISTANCE_SQUARED) {
            viewer.hideEntity(plugin, hologram)
        } else {
            viewer.showEntity(plugin, hologram)
        }
    }

    /** 在床状态变化后刷新该队所有在线成员的私有提示文字。 */
    private fun updateBedHolograms(teamId: String) {
        val team = teamStates[teamId] ?: return
        bedHolograms.forEach { (viewerId, entityId) ->
            if (playerStates[viewerId]?.teamId != teamId) return@forEach
            val hologram = Bukkit.getEntity(entityId) as? ArmorStand ?: return@forEach
            hologram.customName(Component.text(language.getMessage(
                if (team.bedAlive) "bedwars.bed_hologram_defend" else "bedwars.bed_hologram_destroyed"
            )))
        }
    }

    /** 移除一个玩家对应的床提示并解除房间资源登记。 */
    private fun removeBedHologram(viewerId: UUID) {
        val entityId = bedHolograms.remove(viewerId) ?: return
        Bukkit.getEntity(entityId)?.remove()
        resourceScope?.releaseEntity(entityId)
        trackedEntities.remove(entityId)
    }

    private fun spawnShopNpcs() {
        removeShopNpcs()
        val world = room.world ?: return
        gameConfig?.teams.orEmpty().forEach { team ->
            if (effectiveDisableEmptyTeamNpcs && playerStates.values.none {
                it.teamId == team.id && it.participant
            }) return@forEach
            team.shop?.toLocation(world)?.let { spawnShopNpc(it, BedWarsShopKind.ITEM, team.maxPlayers) }
            team.upgradeShop?.toLocation(world)?.let { spawnShopNpc(it, BedWarsShopKind.UPGRADE, team.maxPlayers) }
        }
    }

    /** 生成商店村民，并按队伍容量附加参考实现的双行交互提示。 */
    private fun spawnShopNpc(location: Location, kind: BedWarsShopKind, teamMaxPlayers: Int) {
        val nameKey = when {
            kind == BedWarsShopKind.ITEM && teamMaxPlayers <= 1 -> "bedwars.shop_solo_item_name"
            kind == BedWarsShopKind.ITEM -> "bedwars.shop_item_name"
            teamMaxPlayers <= 1 -> "bedwars.shop_solo_upgrade_name"
            else -> "bedwars.shop_upgrade_name"
        }
        val villager = location.world.spawn(location, Villager::class.java) {
            it.setAI(false)
            it.isInvulnerable = true
            it.isCollidable = false
            it.isSilent = true
            it.isPersistent = true
            it.removeWhenFarAway = false
            it.profession = if (kind == BedWarsShopKind.ITEM) Villager.Profession.WEAPONSMITH else Villager.Profession.LIBRARIAN
            if (moduleConfig.shop.hologramsEnabled) {
                it.customName(null)
                it.isCustomNameVisible = false
            } else {
                it.customName(Component.text(language.getMessage(nameKey)))
                it.isCustomNameVisible = true
            }
            it.addScoreboardTag("kgc_bedwars_shop")
        }
        shopNpcs[villager.uniqueId] = kind
        trackEntity(villager, type = "shop")
        if (moduleConfig.shop.hologramsEnabled) {
            shopHolograms[villager.uniqueId] = listOf(
                spawnShopHologramLine(
                    location.clone().add(0.0, 2.1, 0.0),
                    language.getMessage(nameKey),
                    "name"
                ).uniqueId,
                spawnShopHologramLine(
                    location.clone().add(0.0, 1.85, 0.0),
                    language.getMessage("bedwars.shop_right_click"),
                    "action"
                ).uniqueId
            )
        }
    }

    /** 生成一行公开可见、无碰撞且随房间清理的商店提示。 */
    private fun spawnShopHologramLine(location: Location, text: String, kind: String): ArmorStand {
        val hologram = location.world.spawn(location, ArmorStand::class.java) {
            it.setGravity(false)
            it.isVisible = false
            it.isMarker = true
            it.isSmall = true
            it.isInvulnerable = true
            it.isSilent = true
            it.isPersistent = true
            it.removeWhenFarAway = false
            it.customName(Component.text(text))
            it.isCustomNameVisible = true
            it.addScoreboardTag("kgc_bedwars_shop_hologram_$kind")
        }
        trackEntity(hologram, type = "shop-hologram-$kind")
        return hologram
    }

    /** 移除商店村民及其全息，避免重复准备房间时留下旧实体。 */
    private fun removeShopNpcs() {
        val entityIds = shopNpcs.keys + shopHolograms.values.flatten()
        entityIds.forEach { entityId ->
            Bukkit.getEntity(entityId)?.remove()
            resourceScope?.releaseEntity(entityId)
            trackedEntities.remove(entityId)
        }
        shopNpcs.clear()
        shopHolograms.clear()
    }

    private fun openShop(
        player: Player,
        kind: BedWarsShopKind,
        page: Int = 0,
        view: String = if (kind == BedWarsShopKind.ITEM) QUICK_BUY_VIEW else ALL_ITEMS_VIEW
    ) {
        val menu = YamlConfiguration()
        val assignment = quickBuyAssignments[player.uniqueId]
        val titleKey = when {
            assignment != null && view == QUICK_BUY_VIEW -> "bedwars.quick_buy_assign_title"
            kind == BedWarsShopKind.ITEM && view == QUICK_BUY_VIEW -> "bedwars.quick_buy_title"
            kind == BedWarsShopKind.ITEM -> "bedwars.shop_item_title"
            view == TRAPS_VIEW -> "bedwars.shop_trap_category_title"
            else -> "bedwars.shop_upgrade_title"
        }
        menu.set("title", language.getMessage(titleKey))
        menu.set("layout", when {
            kind == BedWarsShopKind.ITEM && view == QUICK_BUY_VIEW -> listOf(
                "#########",
                "#IIIIIII#",
                "#IIIIIII#",
                "#IIIIIII#",
                "#########",
                "#Q#A#X###"
            )
            kind == BedWarsShopKind.ITEM -> listOf(
                "#########",
                "#IIIIIII#",
                "#IIIIIII#",
                "#IIIIIII#",
                "#IIIIIII#",
                "#Q#P#X#N#"
            )
            view == TRAPS_VIEW -> listOf(
                "#########",
                "#IIIIIII#",
                "#IIIIIII#",
                "#########",
                if (moduleConfig.shop.trapCategoryRules.backVisible) "##P#B#N##" else "##P###N##"
            )
            else -> upgradeShopLayout()
        })
        menu.set("buttons.#.display.material", "GRAY_STAINED_GLASS_PANE")
        menu.set("buttons.#.display.name", " ")
        menu.set("buttons.I.type", "bedwars:shop_items")
        menu.set("buttons.I.display.material", "BARRIER")
        menu.set("buttons.I.display.name", language.getMessage("bedwars.shop_empty"))
        menu.set("buttons.T.type", "bedwars:trap_queue")
        menu.set("buttons.T.paginate", false)
        menu.set("buttons.T.display.material", "GRAY_STAINED_GLASS")
        menu.set("buttons.T.display.name", language.getMessage("bedwars.shop_trap_none"))
        menu.set("buttons.S.display.material", moduleConfig.shop.upgradeSeparatorIcon.name)
        menu.set("buttons.S.display.amount", moduleConfig.shop.upgradeSeparatorIconAmount)
        menu.set("buttons.S.display.enchanted", moduleConfig.shop.upgradeSeparatorIconEnchanted)
        menu.set("buttons.S.display.name", language.getMessage("bedwars.shop_upgrade_separator_name"))
        menu.set("buttons.S.display.lore", language.getMessageList("bedwars.shop_upgrade_separator_lore"))
        menu.set("buttons.X.display.material", "BARRIER")
        menu.set("buttons.X.display.name", language.getMessage("bedwars.shop_close"))
        menu.set("buttons.X.actions.left", listOf("close"))
        menu.set("buttons.P.display.material", "ARROW")
        menu.set("buttons.P.display.name", language.getMessage("bedwars.shop_previous"))
        menu.set("buttons.N.display.material", "ARROW")
        menu.set("buttons.N.display.name", language.getMessage("bedwars.shop_next"))
        menu.set("buttons.Q.display.material", "NETHER_STAR")
        menu.set("buttons.Q.display.name", language.getMessage("bedwars.quick_buy_tab"))
        menu.set("buttons.A.display.material", "CHEST")
        menu.set("buttons.A.display.name", language.getMessage("bedwars.all_items_tab"))
        menu.set("buttons.B.display.material", "ARROW")
        menu.set("buttons.B.display.name", language.getMessage("bedwars.shop_trap_category_back"))
        menu.set("buttons.B.display.lore", listOf(language.getMessage("bedwars.shop_trap_category_back_lore")))
        chestMenuService.openConfig(
            player,
            menu,
            "kagamecenter:bedwars-shop:${room.id}:${kind.name.lowercase()}",
            mapOf("shop.kind" to kind.name, "shop.view" to view),
            page
        )
    }

    /** 把队伍升级商品转换为带动态价格、状态和逐阶 Lore token 的菜单条目。 */
    private fun upgradeShopEntry(
        player: Player,
        team: BedWarsTeamState,
        item: BedWarsUpgradeItem
    ): ChestMenuEntry {
        val price = upgradePrice(team, item)
        val currency = upgradeCurrency(item)
        return shopEntry(
            player,
            item.id,
            item.icon,
            productName(item.id, item.displayName),
            currency,
            price,
            upgradePurchaseStatus(player, team, item, price),
            iconAmount = item.iconAmount,
            iconEnchanted = item.iconEnchanted,
            tier = item.tier,
            customLore = item.displayLore,
            additionalTokens = upgradeTierLoreTokens(team, item)
        )
    }

    /** 构造参考 category-traps 主菜单入口，并在队列已满时追加拒绝状态。 */
    private fun trapCategoryEntry(team: BedWarsTeamState): ChestMenuEntry {
        val yaml = YamlConfiguration()
        val display = yaml.createSection("display")
        display.set("material", moduleConfig.shop.trapCategoryIcon.name)
        display.set("amount", moduleConfig.shop.trapCategoryIconAmount)
        display.set("enchanted", moduleConfig.shop.trapCategoryIconEnchanted)
        display.set("name", "&r${language.getMessage("bedwars.shop_trap_category_name")}")
        display.set("lore", buildList {
            addAll(language.getMessageList("bedwars.shop_trap_category_lore"))
            if (team.traps.size >= trapRules().queueLimit) {
                add("")
                add(language.getMessage("bedwars.shop_status_traps_full"))
            }
        })
        return ChestMenuEntry(mapOf("shop.category" to TRAPS_VIEW), display)
    }

    /** 在六行菜单中自适应安排升级商品、参考分隔条和最多 16 个陷阱队列槽。 */
    private fun upgradeShopLayout(): List<String> {
        val menuRules = upgradeMenuRules()
        val queueSlots = if (menuRules.trapQueueVisible) trapRules().queueLimit.coerceAtMost(16) else 0
        val trapRows = if (queueSlots == 0) 0 else (queueSlots + 6) / 7
        val separatorRows = if (menuRules.separatorVisible && trapRows <= 2) 1 else 0
        val productRows = 4 - trapRows - separatorRows
        var remainingSlots = queueSlots
        return buildList {
            add("#########")
            repeat(productRows) { add("#IIIIIII#") }
            repeat(separatorRows) { add("SSSSSSSSS") }
            repeat(trapRows) {
                val slotsInRow = remainingSlots.coerceAtMost(7)
                val leftPadding = (9 - slotsInRow) / 2
                val rightPadding = 9 - slotsInRow - leftPadding
                add("#".repeat(leftPadding) + "T".repeat(slotsInRow) + "#".repeat(rightPadding))
                remainingSlots -= slotsInRow
            }
            add("##P#X#N##")
        }
    }

    /** 构造一个参考 MenuTrapSlot：已占用时显示陷阱图标，空闲时显示队列说明和下一价格。 */
    private fun trapQueueEntry(player: Player, team: BedWarsTeamState, index: Int): ChestMenuEntry {
        val yaml = YamlConfiguration()
        val display = yaml.createSection("display")
        val queuedTrap = team.traps.elementAtOrNull(index)
        val product = queuedTrap?.let { queued ->
            moduleConfig.shop.upgrades.firstOrNull { it.id == queued.productId }
                ?: moduleConfig.shop.upgrades.firstOrNull { it.upgradeType == queued.upgradeType }
        }
        display.set("amount", index + 1)
        if (product != null) {
            display.set("material", product.icon.name)
            display.set("enchanted", product.iconEnchanted)
            display.set("name", "&r${language.getMessage(
                "bedwars.shop_trap_slot_name",
                "&a",
                index + 1,
                productName(product.id, product.displayName)
            )}")
            val description = queuedTrapDescription(player, team, product)
            display.set("lore", buildList {
                addAll(description)
                if (description.isNotEmpty()) add("")
                addAll(language.getMessageList("bedwars.shop_trap_slot_occupied_lore", index + 1))
            })
        } else {
            display.set("material", "GRAY_STAINED_GLASS")
            display.set("name", "&r${language.getMessage(
                "bedwars.shop_trap_slot_name",
                "&c",
                index + 1,
                language.getMessage("bedwars.shop_trap_none")
            )}")
            display.set("lore", language.getMessageList(
                "bedwars.shop_trap_slot_empty_lore",
                index + 1,
                nextTrapPriceLabel(team)
            ))
        }
        return ChestMenuEntry(mapOf("trap.slot" to index.toString()), display)
    }

    /** 汇总当前队列长度下各陷阱商品的下一价格，兼容自定义商品使用不同基础价格或货币。 */
    private fun nextTrapPriceLabel(team: BedWarsTeamState): String {
        return moduleConfig.shop.upgrades
            .filter { it.upgradeType.trap }
            .map { product ->
                val currency = upgradeCurrency(product)
                "${shopCurrencyColor(currency)}${upgradePrice(team, product)} ${resourceName(currency)}"
            }
            .distinct()
            .joinToString(" &7/ ")
            .ifBlank { "&b0 ${resourceName(Material.DIAMOND)}" }
    }

    /** 渲染已排队陷阱的自定义说明，确保商店占位符不会原样泄露到队列槽。 */
    private fun queuedTrapDescription(
        player: Player,
        team: BedWarsTeamState,
        product: BedWarsUpgradeItem
    ): List<String> {
        if (product.displayLore.isEmpty()) return emptyList()
        val price = upgradePrice(team, product)
        val currency = upgradeCurrency(product)
        val replacements = linkedMapOf(
            "{tier}" to romanShopTier(product.tier),
            "{color}" to "&a",
            "{cost}" to "${shopCurrencyColor(currency)}$price",
            "{currency}" to "${shopCurrencyColor(currency)}${resourceName(currency)}",
            "{buy_status}" to upgradePurchaseStatus(player, team, product, price),
            "{quick_buy}" to ""
        )
        replacements.putAll(upgradeTierLoreTokens(team, product))
        return product.displayLore.map { line ->
            replacements.entries.fold(line) { text, (token, value) -> text.replace(token, value) }
        }
    }

    private fun shopEntry(
        player: Player,
        id: String,
        icon: Material,
        displayName: String,
        currency: Material,
        price: Int,
        status: String,
        iconAmount: Int = 1,
        iconEnchanted: Boolean = false,
        iconPotionDisplay: String? = null,
        iconPotionColor: Color? = null,
        tier: Int = 0,
        customLore: List<String> = emptyList(),
        quickBuyHint: String? = null,
        additionalTokens: Map<String, String> = emptyMap(),
        variables: Map<String, String> = mapOf("product.id" to id),
    ): ChestMenuEntry {
        val yaml = YamlConfiguration()
        val display = yaml.createSection("display")
        val currencyColor = shopCurrencyColor(currency)
        val replacements = linkedMapOf(
            "{tier}" to romanShopTier(tier),
            "{color}" to if (currencyCount(player, currency) >= price) "&a" else "&c",
            "{cost}" to "$currencyColor$price",
            "{currency}" to "$currencyColor${resourceName(currency)}",
            "{buy_status}" to status,
            "{quick_buy}" to quickBuyHint.orEmpty()
        )
        replacements.putAll(additionalTokens)
        val render: (String) -> String = { template ->
            replacements.entries.fold(template) { text, (token, value) -> text.replace(token, value) }
        }
        display.set("material", icon.name)
        display.set("amount", iconAmount)
        display.set("enchanted", iconEnchanted)
        iconPotionDisplay?.let { display.set("potion-display", it) }
        iconPotionColor?.let { display.set("potion-color", it.asRGB()) }
        display.set("name", "&r${render(displayName)}")
        val lore = if (customLore.isEmpty()) {
            listOf(
                language.getMessage("bedwars.shop_cost", price, resourceName(currency)),
                status
            ) + listOfNotNull(quickBuyHint)
        } else {
            customLore.mapNotNull { line ->
                if ("{quick_buy}" in line && quickBuyHint == null) null else render(line)
            }
        }
        display.set("lore", lore)
        return ChestMenuEntry(variables, display)
    }

    /** 把参考商品等级转换为 I-X 罗马数字，其他值保留十进制。 */
    private fun romanShopTier(tier: Int): String = when (tier) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        5 -> "V"
        6 -> "VI"
        7 -> "VII"
        8 -> "VIII"
        9 -> "IX"
        10 -> "X"
        else -> tier.toString()
    }

    /** 返回参考商店价格和货币占位符使用的旧式颜色。 */
    private fun shopCurrencyColor(currency: Material): String = when (currency) {
        Material.IRON_INGOT -> "&f"
        Material.GOLD_INGOT -> "&6"
        Material.DIAMOND -> "&b"
        Material.EMERALD,
        Material.AIR -> "&2"
        else -> "&2"
    }

    /** 依照玩家持久化偏好生成 21 个快捷购买槽位及空槽提示。 */
    private fun quickBuyEntries(player: Player, state: BedWarsPlayerState): List<ChestMenuEntry> {
        val assignment = quickBuyAssignments[player.uniqueId]
        val products = quickBuyService.products(
            player.uniqueId,
            moduleConfig.shop.quickBuyDefaults,
            quickBuyProductAliases()
        )
        return products.mapIndexed { index, productId ->
            val configured = productId?.let { id -> moduleConfig.shop.items.firstOrNull { it.id == id } }
            val product = configured?.let { resolveQuickBuyProduct(it, state) }
            if (product == null) {
                val yaml = YamlConfiguration()
                val display = yaml.createSection("display")
                display.set("material", "RED_STAINED_GLASS_PANE")
                display.set("name", language.getMessage("bedwars.quick_buy_empty"))
                display.set("lore", listOf(language.getMessage(
                    if (assignment == null) "bedwars.quick_buy_empty_hint" else "bedwars.quick_buy_assign_hint"
                )))
                ChestMenuEntry(mapOf("quick.slot" to index.toString()), display)
            } else {
                shopEntry(
                    player,
                    product.id,
                    product.icon,
                    productName(product.id, product.displayName),
                    product.currency,
                    product.price,
                    itemPurchaseStatus(player, state, product),
                    iconAmount = product.iconAmount,
                    iconEnchanted = product.iconEnchanted,
                    iconPotionDisplay = product.iconPotionDisplay,
                    iconPotionColor = product.iconPotionColor,
                    tier = product.tier,
                    customLore = product.displayLore,
                    quickBuyHint = language.getMessage(
                        if (assignment == null) "bedwars.quick_buy_remove_hint" else "bedwars.quick_buy_assign_hint"
                    ),
                    variables = mapOf("product.id" to product.id, "quick.slot" to index.toString()),
                )
            }
        }
    }

    /** 将镐斧快捷槽解析为玩家当前可购买的下一阶，满级时保留最高阶。 */
    private fun resolveQuickBuyProduct(product: BedWarsShopItem, state: BedWarsPlayerState): BedWarsShopItem {
        val currentTier = when (product.productType) {
            BedWarsProductType.PICKAXE -> state.pickaxeTier
            BedWarsProductType.AXE -> state.axeTier
            else -> return product
        }
        val tiers = moduleConfig.shop.items
            .filter { it.productType == product.productType }
            .sortedBy(BedWarsShopItem::tier)
        return tiers.firstOrNull { it.tier > currentTier } ?: tiers.lastOrNull() ?: product
    }

    /** 把镐和斧的多阶配置各折叠为当前下一阶，普通页与参考 CategoryContent 保持单槽语义。 */
    private fun visibleShopProducts(state: BedWarsPlayerState): List<BedWarsShopItem> {
        val addedToolTypes = linkedSetOf<BedWarsProductType>()
        return buildList {
            moduleConfig.shop.items.forEach { product ->
                if (product.productType != BedWarsProductType.PICKAXE && product.productType != BedWarsProductType.AXE) {
                    add(product)
                    return@forEach
                }
                if (addedToolTypes.add(product.productType)) add(resolveQuickBuyProduct(product, state))
            }
        }
    }

    /** 按当前分组 menu-content 顺序把非陷阱强化折叠为当前下一阶。 */
    private fun visibleUpgradeProducts(team: BedWarsTeamState): List<BedWarsUpgradeItem> {
        return upgradeMenuRules().upgradeTypes.mapNotNull { type ->
            val currentTier = team.upgrades[type] ?: 0
            val tiers = moduleConfig.shop.upgrades
                .filter { it.upgradeType == type }
                .sortedWith(compareBy(BedWarsUpgradeItem::tier, BedWarsUpgradeItem::id))
            tiers.firstOrNull { it.tier > currentTier } ?: tiers.lastOrNull()
        }
    }

    /** 按当前分组 menu-content 顺序返回直接放在主菜单的参考 base-trap 商品。 */
    private fun visibleDirectTrapProducts(): List<BedWarsUpgradeItem> {
        return upgradeMenuRules().directTrapTypes.flatMap { type ->
            moduleConfig.shop.upgrades.filter { it.upgradeType == type }
        }
    }

    /** 构造参考升级 Lore 中逐阶价格、货币和解锁颜色占位符。 */
    private fun upgradeTierLoreTokens(
        team: BedWarsTeamState,
        product: BedWarsUpgradeItem
    ): Map<String, String> {
        if (product.upgradeType.trap) return emptyMap()
        val currentTier = team.upgrades[product.upgradeType] ?: 0
        val tiers = moduleConfig.shop.upgrades
            .filter { it.upgradeType == product.upgradeType }
            .sortedWith(compareBy(BedWarsUpgradeItem::tier, BedWarsUpgradeItem::id))
        return buildMap {
            tiers.forEachIndexed { index, tier ->
                val numbers = setOf(index + 1, tier.tier)
                numbers.forEach { number ->
                    put("{tier_${number}_cost}", tier.price.toString())
                    put("{tier_${number}_currency}", resourceName(tier.currency))
                    put("{tier_${number}_color}", if (currentTier >= tier.tier) "&a" else "&7")
                }
            }
        }
    }

    /** 将镐斧所有阶级 ID 映射到最低阶稳定 ID，使快捷购买按参考 CategoryContent 去重。 */
    private fun quickBuyProductAliases(): Map<String, String> {
        val aliases = linkedMapOf<String, String>()
        listOf(BedWarsProductType.PICKAXE, BedWarsProductType.AXE).forEach { type ->
            val products = moduleConfig.shop.items
                .filter { it.productType == type }
                .sortedWith(compareBy(BedWarsShopItem::tier, BedWarsShopItem::id))
            val canonicalId = products.firstOrNull()?.id ?: return@forEach
            products.forEach { product -> aliases[product.id] = canonicalId }
        }
        return aliases
    }

    private fun itemPurchaseStatus(player: Player, state: BedWarsPlayerState, item: BedWarsShopItem): String {
        if (usesConfiguredPermanentLifecycle(item) && item.id in state.permanentProductIds) {
            return language.getMessage("bedwars.shop_status_owned")
        }
        if (isCategoryWeightLocked(state, item)) return language.getMessage("bedwars.shop_status_owned")
        val currentTier = when (item.productType) {
            BedWarsProductType.ARMOR -> state.armorTier
            BedWarsProductType.PICKAXE -> state.pickaxeTier
            BedWarsProductType.AXE -> state.axeTier
            BedWarsProductType.SHEARS -> if (state.shears) 1 else 0
            BedWarsProductType.ITEM,
            BedWarsProductType.POTION,
            BedWarsProductType.SPECIAL -> -1
        }
        if (currentTier >= item.tier && item.productType !in oneTimeProductTypes) {
            return language.getMessage("bedwars.shop_status_owned")
        }
        if ((item.productType == BedWarsProductType.PICKAXE || item.productType == BedWarsProductType.AXE) && item.tier > currentTier + 1) {
            return language.getMessage("bedwars.shop_status_locked", currentTier + 1)
        }
        return affordabilityStatus(player, item.currency, item.price)
    }

    private fun upgradePurchaseStatus(
        player: Player,
        team: BedWarsTeamState,
        item: BedWarsUpgradeItem,
        price: Int = upgradePrice(team, item)
    ): String {
        val currency = upgradeCurrency(item)
        if (item.upgradeType.trap) {
            if (team.traps.size >= trapRules().queueLimit) {
                return language.getMessage("bedwars.shop_status_traps_full")
            }
            return affordabilityStatus(player, currency, price)
        }
        val current = team.upgrades[item.upgradeType] ?: 0
        if (current >= item.tier) return language.getMessage("bedwars.shop_status_owned")
        if (item.tier > current + 1) return language.getMessage("bedwars.shop_status_locked", current + 1)
        return affordabilityStatus(player, currency, price)
    }

    private fun affordabilityStatus(player: Player, currency: Material, price: Int): String {
        return language.getMessage(
            if (currencyCount(player, currency) >= price) "bedwars.shop_status_buy" else "bedwars.shop_status_cannot_afford"
        )
    }

    private fun buyItem(player: Player, productId: String) {
        val state = playerStates[player.uniqueId] ?: return
        if (!isActiveParticipant(player)) return
        val product = moduleConfig.shop.items.firstOrNull { it.id == productId } ?: return
        if (!canPurchaseItem(state, product)) {
            player.sendMessage(shopFeedbackComponent(itemPurchaseStatus(player, state, product)))
            playSoundRule(listOf(player), moduleConfig.shop.insufficientSound)
            return
        }
        if (currencyCount(player, product.currency) < product.price) {
            takeCurrency(player, product.currency, product.price)
            return
        }
        val purchaseEvent = GamePurchaseEvent(
            room,
            player,
            GamePurchaseKind.ITEM,
            product.id,
            product.productType.name,
            state.teamId,
            product.currency,
            product.price
        )
        Bukkit.getPluginManager().callEvent(purchaseEvent)
        if (purchaseEvent.isCancelled || !isPurchaseContextValid(player, state)) return
        if (!takeCurrency(player, product.currency, product.price)) return
        if (!purchaseEvent.handled) {
            if (usesConfiguredPermanentLifecycle(product)) state.permanentProductIds.add(product.id)
            deliverPurchasedProduct(player, state, product)
            executePurchasedCommands(player, state, product)
            product.category?.let { state.categoryWeights[it] = product.weight }
        }
        playSoundRule(listOf(player), moduleConfig.shop.boughtSound)
        player.sendMessage(shopFeedbackComponent(language.getMessage(
            "bedwars.shop_purchased",
            feedbackProductName(product.id, product.displayName)
        )))
    }

    /** 提交商品等级状态后，选择参考 buy-items 多发放路径或旧扁平商品路径。 */
    private fun deliverPurchasedProduct(player: Player, state: BedWarsPlayerState, product: BedWarsShopItem) {
        when (product.productType) {
            BedWarsProductType.ARMOR -> state.armorTier = product.tier
            BedWarsProductType.PICKAXE -> state.pickaxeTier = product.tier
            BedWarsProductType.AXE -> state.axeTier = product.tier
            BedWarsProductType.SHEARS -> state.shears = true
            else -> Unit
        }
        if (product.buyItems.isNotEmpty()) {
            clearFixedProductItems(player, product.productType)
            giveConfiguredBuyItems(player, state, product)
            return
        }
        if (!product.deliverProduct) return
        when (product.productType) {
            BedWarsProductType.ITEM -> givePurchasedItem(player, state, product)
            BedWarsProductType.POTION -> givePurchasedPotion(player, product)
            BedWarsProductType.SPECIAL -> givePurchasedSpecial(player, product)
            BedWarsProductType.ARMOR -> {
                applyArmor(player, state)
                playSoundRule(listOf(player), moduleConfig.shop.autoEquipSound)
            }
            BedWarsProductType.PICKAXE -> replaceTool(player, state, BedWarsProductType.PICKAXE)
            BedWarsProductType.AXE -> replaceTool(player, state, BedWarsProductType.AXE)
            BedWarsProductType.SHEARS -> replaceTool(player, state, BedWarsProductType.SHEARS)
        }
    }

    /** 支付成功后按参考顺序执行玩家和控制台命令，并展开玩家、队伍及托管竞技场占位符。 */
    private fun executePurchasedCommands(player: Player, state: BedWarsPlayerState, product: BedWarsShopItem) {
        if (product.commandsAsPlayer.isEmpty() && product.commandsAsConsole.isEmpty()) return
        val team = teamStates[state.teamId]?.config
        val configuredGame = room.configuredGame
        val replacements = linkedMapOf(
            "{player}" to player.name,
            "{player_uuid}" to player.uniqueId.toString(),
            "{team}" to state.teamId,
            "{team_display}" to (team?.displayName ?: state.teamId),
            "{team_color}" to teamLegacyColor(team?.color ?: BedWarsTeamColor.WHITE),
            "{arena}" to (configuredGame?.localId ?: room.id),
            "{arena_world}" to (room.world?.name ?: ""),
            "{arena_display}" to (configuredGame?.displayName ?: room.name),
            "{arena_group}" to (configuredGame?.selectorGroup ?: "default")
        )
        val expand: (String) -> String = { command ->
            replacements.entries.fold(command) { value, (token, replacement) ->
                value.replace(token, replacement)
            }
        }
        product.commandsAsPlayer.forEach { player.chat("/${expand(it)}") }
        product.commandsAsConsole.forEach { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expand(it)) }
    }

    /** 返回参考命令占位符使用的队伍旧式聊天颜色码。 */
    private fun teamLegacyColor(color: BedWarsTeamColor): String = when (color) {
        BedWarsTeamColor.RED -> "§c"
        BedWarsTeamColor.BLUE -> "§9"
        BedWarsTeamColor.GREEN -> "§a"
        BedWarsTeamColor.YELLOW -> "§e"
        BedWarsTeamColor.AQUA -> "§b"
        BedWarsTeamColor.WHITE -> "§f"
        BedWarsTeamColor.PINK -> "§d"
        BedWarsTeamColor.GRAY -> "§7"
    }

    private fun buyUpgrade(player: Player, productId: String): Boolean {
        val state = playerStates[player.uniqueId] ?: return false
        if (!isActiveParticipant(player)) return false
        val team = teamStates[state.teamId] ?: return false
        val product = moduleConfig.shop.upgrades.firstOrNull { it.id == productId } ?: return false
        if (!canPurchaseUpgrade(team, product)) {
            player.sendMessage(shopFeedbackComponent(upgradePurchaseStatus(player, team, product)))
            playSoundRule(listOf(player), moduleConfig.shop.insufficientSound)
            return false
        }
        val price = upgradePrice(team, product)
        val currency = upgradeCurrency(product)
        if (currencyCount(player, currency) < price) {
            takeCurrency(player, currency, price)
            return false
        }
        val purchaseEvent = GamePurchaseEvent(
            room,
            player,
            GamePurchaseKind.UPGRADE,
            product.id,
            product.upgradeType.name,
            state.teamId,
            currency,
            price
        )
        Bukkit.getPluginManager().callEvent(purchaseEvent)
        if (purchaseEvent.isCancelled || !isPurchaseContextValid(player, state)) return false
        if (teamStates[state.teamId] !== team) return false
        if (!takeCurrency(player, currency, price)) return false
        if (!purchaseEvent.handled) {
            if (product.upgradeType.trap) {
                team.traps.addLast(BedWarsQueuedTrap(
                    productId = product.id,
                    upgradeType = product.upgradeType,
                    actions = product.trapActions,
                    customAnnounce = product.customAnnounce,
                    sound = product.trapSound
                ))
                triggerTrapForCurrentIntruder(state.teamId, team)
            } else {
                val previousTier = team.upgrades[product.upgradeType] ?: 0
                team.upgrades[product.upgradeType] = product.tier
                if (product.upgradeType == BedWarsUpgradeType.FORGE) {
                    if (previousTier < 3 && product.tier >= 3) {
                        team.forgeEmeraldTicks = moduleConfig.forgeRules.emeraldIntervalTicks
                    }
                    generatorStates.filter { it.teamId == state.teamId }.forEach { generator ->
                        generator.ticksUntilSpawn = minOf(generator.ticksUntilSpawn, generatorInterval(generator, null))
                    }
                }
                teamMembersOnline(state.teamId).forEach { teammate ->
                    playerStates[teammate.uniqueId]?.let { applyTeamUpgrades(teammate, it) }
                }
            }
        }
        playSoundRule(listOf(player), moduleConfig.shop.boughtSound)
        if (!purchaseEvent.handled && !product.upgradeType.trap) {
            executeUpgradeActions(player, state, product)
        }
        roomBroadcastService.localized(
            room,
            language,
            "bedwars.upgrade_purchased",
            player.name,
            feedbackProductName(product.id, product.displayName),
            includeSpectators = true
        )
        return true
    }

    /** 购买事件返回后确认玩家仍属于本 Session、沿用同一状态对象且可以继续参赛。 */
    private fun isPurchaseContextValid(player: Player, state: BedWarsPlayerState): Boolean {
        if (roomManager.getPlayerRoom(player)?.session !== this) return false
        if (playerStates[player.uniqueId] !== state) return false
        return isActiveParticipant(player)
    }

    /** 在非陷阱队伍升级提交后按参考声明顺序执行全部 receive 动作。 */
    private fun executeUpgradeActions(
        buyer: Player,
        state: BedWarsPlayerState,
        product: BedWarsUpgradeItem
    ) {
        if (product.actions.isEmpty()) return
        val team = teamStates[state.teamId]?.config
        val configuredGame = room.configuredGame
        val replacements = linkedMapOf(
            "{buyer}" to buyer.name,
            "{buyer_uuid}" to buyer.uniqueId.toString(),
            "{team}" to state.teamId,
            "{team_display}" to (team?.displayName ?: state.teamId),
            "{team_color}" to teamLegacyColor(team?.color ?: BedWarsTeamColor.WHITE),
            "{arena}" to (configuredGame?.localId ?: room.id),
            "{arena_world}" to (room.world?.name ?: ""),
            "{arena_display}" to (configuredGame?.displayName ?: room.name),
            "{arena_group}" to (configuredGame?.selectorGroup ?: "default")
        )
        val expandBase: (String) -> String = { command ->
            replacements.entries.fold(command) { value, (token, replacement) ->
                value.replace(token, replacement)
            }
        }
        product.actions.forEach { action ->
            when (action) {
                is BedWarsUpgradeEnchantAction -> applyUpgradeEnchantAction(state.teamId, action)
                is BedWarsUpgradeEffectAction -> applyUpgradeEffectAction(state.teamId, action)
                is BedWarsUpgradeGeneratorAction -> applyUpgradeGeneratorAction(state.teamId, action)
                is BedWarsUpgradeDragonAction -> teamStates[state.teamId]?.dragonCount = action.amount
                is BedWarsUpgradeCommand -> {
                    val command = expandBase(action.command).removePrefix("/")
                    when (action.type) {
                        BedWarsUpgradeCommandType.ONCE_AS_CONSOLE -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                        }
                        BedWarsUpgradeCommandType.FOREACH_MEMBER_AS_CONSOLE -> {
                            teamMembersOnline(state.teamId).forEach { member ->
                                Bukkit.dispatchCommand(
                                    Bukkit.getConsoleSender(),
                                    command.replace("{player}", member.name)
                                        .replace("{player_uuid}", member.uniqueId.toString())
                                )
                            }
                        }
                        BedWarsUpgradeCommandType.FOREACH_MEMBER_AS_PLAYER -> {
                            teamMembersOnline(state.teamId).forEach { member ->
                                member.chat(
                                    "/${command.replace("{player}", member.name)
                                        .replace("{player_uuid}", member.uniqueId.toString())}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** 保存 receive 附魔并立即刷新队伍当前武器、护甲或弓。 */
    private fun applyUpgradeEnchantAction(teamId: String, action: BedWarsUpgradeEnchantAction) {
        val team = teamStates[teamId] ?: return
        team.actionEnchantments.getOrPut(action.target, ::linkedMapOf)[action.enchantment] = action.amplifier
        teamMembersOnline(teamId).forEach { player -> applyConfiguredTeamEnchantments(player, teamId) }
    }

    /** 保存 receive 药水效果，并立即应用到全队或当前位于己方基地的成员。 */
    private fun applyUpgradeEffectAction(teamId: String, action: BedWarsUpgradeEffectAction) {
        val team = teamStates[teamId] ?: return
        val recipients = when (action.target) {
            BedWarsUpgradeEffectTarget.TEAM -> {
                team.teamEffects[action.effectType] = action
                teamMembersOnline(teamId)
            }
            BedWarsUpgradeEffectTarget.BASE -> {
                team.baseEffects[action.effectType] = action
                val base = team.config.bed ?: team.config.spawn ?: return
                teamMembersOnline(teamId).filter { within(it.location, base, effectiveIslandRadius) }
            }
        }
        recipients.forEach { player -> applyUpgradePotionEffect(player, action) }
    }

    /** 保存 receive 生成器精确参数，必要时在队伍主生成点创建绿宝石生成器。 */
    private fun applyUpgradeGeneratorAction(teamId: String, action: BedWarsUpgradeGeneratorAction) {
        val team = teamStates[teamId] ?: return
        team.generatorEdits[action.generatorType] = action
        if (action.generatorType == BedWarsGeneratorType.EMERALD) ensureTeamEmeraldGenerator(teamId, action)
        generatorStates.filter { it.teamId == teamId && it.config.type == action.generatorType }.forEach { generator ->
            generator.ticksUntilSpawn = minOf(generator.ticksUntilSpawn, action.intervalTicks)
        }
    }

    /** 在队伍没有绿宝石点时复用首个队伍生成点创建参考自定义生成器。 */
    private fun ensureTeamEmeraldGenerator(teamId: String, action: BedWarsUpgradeGeneratorAction) {
        if (generatorStates.any { it.teamId == teamId && it.config.type == BedWarsGeneratorType.EMERALD }) return
        val source = generatorStates.firstOrNull { it.teamId == teamId } ?: return
        val config = BedWarsGeneratorConfig(
            id = "upgrade-emerald-$teamId",
            type = BedWarsGeneratorType.EMERALD,
            point = source.config.point,
            intervalTicks = action.intervalTicks
        )
        generatorStates += BedWarsGeneratorState(config, teamId, ticksUntilSpawn = action.intervalTicks)
    }

    /** 将 receive 药水效果以强制覆盖方式应用到指定玩家。 */
    private fun applyUpgradePotionEffect(player: Player, action: BedWarsUpgradeEffectAction) {
        player.addPotionEffect(
            PotionEffect(action.effectType, action.durationTicks, action.amplifier, false, false, true),
            true
        )
    }

    private fun givePurchasedItem(player: Player, state: BedWarsPlayerState, product: BedWarsShopItem) {
        val material = teamColoredMaterial(state.teamId, product.item)
        val item = ItemStack(material, product.amount)
        if (material.name.endsWith("_SWORD")) {
            val meta = item.itemMeta
            meta.isUnbreakable = true
            item.itemMeta = meta
            removeReplacedDefaultSwords(player, item)
            applySharpness(item, state.teamId)
        }
        when (product.id) {
            "knockback-stick" -> item.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1)
            "power-bow" -> item.addUnsafeEnchantment(Enchantment.POWER, 1)
            "punch-bow" -> {
                item.addUnsafeEnchantment(Enchantment.POWER, 1)
                item.addUnsafeEnchantment(Enchantment.PUNCH, 1)
            }
        }
        applyConfiguredProductMeta(item, product)
        applyConfiguredTeamEnchantments(item, state.teamId)
        if (!product.autoEquip || !autoEquipPurchasedArmor(player, state, item)) addOrDrop(player, item)
    }

    /** 按管理员声明顺序发放一个 ContentTier 的全部 buy-items。 */
    private fun giveConfiguredBuyItems(
        player: Player,
        state: BedWarsPlayerState,
        product: BedWarsShopItem,
        skipAutoEquippedArmor: Boolean = false,
        playAutoEquipSound: Boolean = true
    ) {
        product.buyItems.forEach { delivery ->
            if (skipAutoEquippedArmor && delivery.autoEquip && armorSlot(delivery.material) != null) return@forEach
            val item = createConfiguredBuyItem(state, product, delivery)
            removeReplacedDefaultSwords(player, item)
            if (!delivery.autoEquip || !autoEquipPurchasedArmor(player, state, item, playAutoEquipSound)) {
                addOrDrop(player, item)
            }
        }
    }

    /** 创建一个继承商品永久、不可损坏及行为标记的 buy-items 成品。 */
    private fun createConfiguredBuyItem(
        state: BedWarsPlayerState,
        product: BedWarsShopItem,
        delivery: BedWarsShopDelivery
    ): ItemStack {
        val material = teamColoredMaterial(state.teamId, delivery.material)
        val item = ItemStack(material, delivery.amount)
        val meta = item.itemMeta
        val permanent = product.permanent || product.productType !in oneTimeProductTypes
        delivery.itemName?.let { meta.displayName(configuredItemName(it)) }
        if (product.unbreakable || permanent || delivery.unbreakable) meta.isUnbreakable = true
        if (permanent) {
            meta.persistentDataContainer.set(permanentItemKey, PersistentDataType.BYTE, 1.toByte())
        }
        if (product.productType == BedWarsProductType.SPECIAL) {
            meta.persistentDataContainer.set(specialItemKey, PersistentDataType.STRING, product.id)
        }
        if (meta is PotionMeta) {
            delivery.potionEffects.forEach { configured ->
                meta.addCustomEffect(PotionEffect(configured.type, configured.durationTicks, configured.amplifier), true)
            }
            if (delivery.potionEffects.isNotEmpty() || product.productType == BedWarsProductType.POTION) {
                meta.setBasePotionType(PotionType.WATER)
                meta.persistentDataContainer.set(potionItemKey, PersistentDataType.STRING, product.id)
            }
            delivery.potionColor?.let(meta::setColor)
        }
        item.itemMeta = meta
        delivery.enchantments.forEach(item::addUnsafeEnchantment)
        if (material.name.endsWith("_SWORD") || material.name.endsWith("_AXE") && !material.name.endsWith("PICKAXE")) {
            applySharpness(item, state.teamId)
        }
        applyConfiguredTeamEnchantments(item, state.teamId)
        return item
    }

    /** 只移除伤害等级不高于新剑的出生默认剑，不触碰已购买或自定义剑。 */
    private fun removeReplacedDefaultSwords(player: Player, item: ItemStack) {
        if (!item.type.name.endsWith("_SWORD")) return
        player.inventory.contents.filterNotNull()
            .filter { existing ->
                existing.type.name.endsWith("_SWORD") &&
                    existing.itemMeta.persistentDataContainer.has(defaultItemKey, PersistentDataType.BYTE) &&
                    swordRank(existing.type) <= swordRank(item.type)
            }
            .forEach { it.amount = 0 }
    }

    /** 拾取其他剑时移除背包中的出生默认剑，保持参考默认物品恢复监听语义。 */
    private fun removeDefaultSwords(player: Player) {
        player.inventory.contents.filterNotNull()
            .filter { existing ->
                existing.type.name.endsWith("_SWORD") &&
                existing.itemMeta.persistentDataContainer.has(defaultItemKey, PersistentDataType.BYTE)
            }
            .forEach { it.amount = 0 }
    }

    /** 判断物品是否为当前模块标记的出生默认剑。 */
    private fun isDefaultSword(item: ItemStack): Boolean {
        return item.type.name.endsWith("_SWORD") &&
            item.itemMeta.persistentDataContainer.has(defaultItemKey, PersistentDataType.BYTE)
    }

    /** 检查丢弃事件移除默认剑后，背包中是否仍有同级或更强的替代剑。 */
    private fun hasEqualOrStrongerSword(player: Player, dropped: ItemStack): Boolean {
        val droppedRank = swordRank(dropped.type)
        return player.inventory.contents.filterNotNull().any { existing ->
            existing.type.name.endsWith("_SWORD") && swordRank(existing.type) >= droppedRank
        }
    }

    /** 返回可自动穿戴材质对应的玩家装备槽。 */
    private fun armorSlot(material: Material): EquipmentSlot? = when {
        material.name.endsWith("_HELMET") -> EquipmentSlot.HEAD
        material.name.endsWith("_CHESTPLATE") || material == Material.ELYTRA -> EquipmentSlot.CHEST
        material.name.endsWith("_LEGGINGS") -> EquipmentSlot.LEGS
        material.name.endsWith("_BOOTS") -> EquipmentSlot.FEET
        else -> null
    }

    /** 把配置为 auto-equip 的实际护甲放入对应槽位，并应用队色和团队保护。 */
    private fun autoEquipPurchasedArmor(
        player: Player,
        state: BedWarsPlayerState,
        item: ItemStack,
        playSound: Boolean = true
    ): Boolean {
        val equipmentSlot = armorSlot(item.type) ?: return false
        (item.itemMeta as? LeatherArmorMeta)?.let { meta ->
            teamStates[state.teamId]?.config?.color?.armorColor?.let(meta::setColor)
            item.itemMeta = meta
        }
        val protection = teamStates[state.teamId]?.upgrades?.get(BedWarsUpgradeType.PROTECTION) ?: 0
        if (protection > 0) item.addUnsafeEnchantment(Enchantment.PROTECTION, protection)
        applyConfiguredTeamEnchantments(item, state.teamId)
        when (equipmentSlot) {
            EquipmentSlot.HEAD -> player.inventory.helmet = item
            EquipmentSlot.CHEST -> player.inventory.chestplate = item
            EquipmentSlot.LEGS -> player.inventory.leggings = item
            EquipmentSlot.FEET -> player.inventory.boots = item
            else -> return false
        }
        if (playSound) playSoundRule(listOf(player), moduleConfig.shop.autoEquipSound)
        return true
    }

    /** 把参考支持的五类商品材质转换为指定队伍颜色，其余材质保持不变。 */
    private fun teamColoredMaterial(teamId: String, material: Material): Material {
        return teamStates[teamId]?.config?.color?.colorize(material) ?: material
    }

    /** 创建带标准 BedWars 时长和等级的可饮用药水。 */
    private fun givePurchasedPotion(player: Player, product: BedWarsShopItem) {
        val state = playerStates[player.uniqueId]
        val item = ItemStack(state?.let { teamColoredMaterial(it.teamId, product.item) } ?: product.item, product.amount)
        val meta = item.itemMeta as? PotionMeta ?: run {
            applyConfiguredProductMeta(item, product)
            playerStates[player.uniqueId]?.let { applyConfiguredTeamEnchantments(item, it.teamId) }
            addOrDrop(player, item)
            return
        }
        val fallbackEffect = when (product.id) {
            "speed-potion" -> PotionEffect(PotionEffectType.SPEED, moduleConfig.specials.speedPotionSeconds * 20, 1)
            "jump-potion" -> PotionEffect(PotionEffectType.JUMP_BOOST, moduleConfig.specials.jumpPotionSeconds * 20, 4)
            "invisibility-potion" -> PotionEffect(
                PotionEffectType.INVISIBILITY,
                moduleConfig.specials.invisibilityPotionSeconds * 20,
                0
            )
            else -> null
        }
        val effects = product.potionEffects.map { configured ->
            PotionEffect(configured.type, configured.durationTicks, configured.amplifier)
        }.ifEmpty { listOfNotNull(fallbackEffect) }
        effects.forEach { meta.addCustomEffect(it, true) }
        meta.setBasePotionType(PotionType.WATER)
        product.potionColor?.let(meta::setColor)
        meta.persistentDataContainer.set(potionItemKey, PersistentDataType.STRING, product.id)
        item.itemMeta = meta
        applyConfiguredProductMeta(item, product)
        playerStates[player.uniqueId]?.let { applyConfiguredTeamEnchantments(item, it.teamId) }
        addOrDrop(player, item)
    }

    /** 创建带 PDC 行为标记的一次性特殊商品。 */
    private fun givePurchasedSpecial(player: Player, product: BedWarsShopItem) {
        val state = playerStates[player.uniqueId]
        val item = ItemStack(state?.let { teamColoredMaterial(it.teamId, product.item) } ?: product.item, product.amount)
        val meta = item.itemMeta
        meta.persistentDataContainer.set(specialItemKey, PersistentDataType.STRING, product.id)
        item.itemMeta = meta
        applyConfiguredProductMeta(item, product)
        playerStates[player.uniqueId]?.let { applyConfiguredTeamEnchantments(item, it.teamId) }
        addOrDrop(player, item)
    }

    /** 把扩展商品配置的名称、不可损坏标记和附魔应用到最终物品。 */
    private fun applyConfiguredProductMeta(item: ItemStack, product: BedWarsShopItem) {
        val meta = item.itemMeta
        product.itemName?.let { meta.displayName(configuredItemName(it)) }
        if (product.unbreakable || product.permanent) meta.isUnbreakable = true
        if (product.permanent) {
            meta.persistentDataContainer.set(permanentItemKey, PersistentDataType.BYTE, 1.toByte())
        }
        item.itemMeta = meta
        product.enchantments.forEach(item::addUnsafeEnchantment)
    }

    private fun currencyCount(player: Player, currency: Material): Int {
        if (currency == Material.AIR) {
            val balance = vaultEconomy.balance(player) ?: return 0
            return balance.toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
        return player.inventory.contents.filterNotNull().filter { it.type == currency }.sumOf(ItemStack::getAmount)
    }

    private fun canPurchaseItem(state: BedWarsPlayerState, item: BedWarsShopItem): Boolean {
        if (usesConfiguredPermanentLifecycle(item) && item.id in state.permanentProductIds) return false
        if (isCategoryWeightLocked(state, item)) return false
        return when (item.productType) {
            BedWarsProductType.ITEM,
            BedWarsProductType.POTION,
            BedWarsProductType.SPECIAL -> true
            BedWarsProductType.ARMOR -> item.tier > state.armorTier
            BedWarsProductType.PICKAXE -> item.tier == state.pickaxeTier + 1
            BedWarsProductType.AXE -> item.tier == state.axeTier + 1
            BedWarsProductType.SHEARS -> !state.shears
        }
    }

    /** 判断玩家在同一商店分类中是否已经购买了权重更高的商品。 */
    private fun isCategoryWeightLocked(state: BedWarsPlayerState, item: BedWarsShopItem): Boolean {
        val category = item.category ?: return false
        return (state.categoryWeights[category] ?: 0) > item.weight
    }

    /** 判断商品是否使用自定义永久 ID 生命周期；固定装备类型继续由现有等级字段恢复。 */
    private fun usesConfiguredPermanentLifecycle(item: BedWarsShopItem): Boolean {
        return item.permanent && item.productType in oneTimeProductTypes
    }

    /** 返回当前托管玩法 selector-group 对应的陷阱规则，缺失时使用模块默认规则。 */
    private fun trapRules(): BedWarsTrapRules {
        val group = room.configuredGame?.selectorGroup?.trim()?.lowercase().orEmpty()
        return moduleConfig.shop.trapGroupRules[group] ?: moduleConfig.shop.defaultTrapRules
    }

    /** 返回当前 selector-group 对应的升级菜单组件规则，缺失时使用默认菜单。 */
    private fun upgradeMenuRules(): BedWarsUpgradeMenuRules {
        val group = room.configuredGame?.selectorGroup?.trim()?.lowercase().orEmpty()
        return moduleConfig.shop.upgradeMenuGroupRules[group] ?: moduleConfig.shop.defaultUpgradeMenuRules
    }

    /** 仅对未显式声明货币的参考陷阱应用当前 selector-group 缺省货币。 */
    private fun upgradeCurrency(item: BedWarsUpgradeItem): Material {
        return if (item.upgradeType.trap && item.trapUsesConfiguredCurrency) trapRules().currency else item.currency
    }

    private fun canPurchaseUpgrade(team: BedWarsTeamState, item: BedWarsUpgradeItem): Boolean {
        if (item.upgradeType.trap) return team.traps.size < trapRules().queueLimit
        return item.tier == (team.upgrades[item.upgradeType] ?: 0) + 1
    }

    private fun upgradePrice(team: BedWarsTeamState, item: BedWarsUpgradeItem): Int {
        if (!item.upgradeType.trap || !item.trapDynamicPrice) return item.price
        val rules = trapRules()
        val startPrice = if (item.trapUsesConfiguredStartPrice) rules.startPrice else item.price
        return startPrice + team.traps.size * rules.priceIncrement
    }

    private fun takeCurrency(player: Player, currency: Material, amount: Int): Boolean {
        val available = if (currency == Material.AIR) {
            val balance = vaultEconomy.balance(player)
            if (balance == null) {
                player.sendMessage(shopFeedbackComponent(language.getMessage("bedwars.shop_economy_unavailable")))
                playSoundRule(listOf(player), moduleConfig.shop.insufficientSound)
                return false
            }
            balance.toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        } else {
            currencyCount(player, currency)
        }
        if (available < amount) {
            player.sendMessage(shopFeedbackComponent(language.getMessage(
                "bedwars.shop_insufficient_resources",
                amount - available,
                resourceName(currency)
            )))
            playSoundRule(listOf(player), moduleConfig.shop.insufficientSound)
            return false
        }
        if (currency == Material.AIR) {
            if (vaultEconomy.withdraw(player, amount)) return true
            player.sendMessage(shopFeedbackComponent(language.getMessage("bedwars.shop_economy_unavailable")))
            playSoundRule(listOf(player), moduleConfig.shop.insufficientSound)
            return false
        }
        var remaining = amount
        player.inventory.contents.forEachIndexed { slot, item ->
            if (remaining <= 0 || item?.type != currency) return@forEachIndexed
            if (item.amount <= remaining) {
                remaining -= item.amount
                player.inventory.setItem(slot, null)
            } else {
                item.amount -= remaining
                remaining = 0
            }
        }
        return remaining == 0
    }

    /** 从玩家背包移除一个指定材料，不产生购买失败提示。 */
    private fun removeOneMaterial(player: Player, material: Material) {
        player.inventory.contents.forEachIndexed { slot, item ->
            if (item?.type != material) return@forEachIndexed
            if (item.amount <= 1) player.inventory.setItem(slot, null) else item.amount--
            return
        }
    }

    private fun addOrDrop(player: Player, item: ItemStack) {
        player.inventory.addItem(item).values.forEach { leftover ->
            player.world.dropItemNaturally(player.location, leftover)
        }
    }

    private fun giveLoadout(player: Player, state: BedWarsPlayerState) {
        giveDefaultItems(player, state.teamId)
        applyArmor(player, state)
        if (state.armorTier > 0) {
            configuredFixedProduct(BedWarsProductType.ARMOR, state.armorTier)?.let { product ->
                giveConfiguredBuyItems(player, state, product, skipAutoEquippedArmor = true, playAutoEquipSound = false)
            }
        }
        if (state.pickaxeTier > 0 && !restoreConfiguredFixedProduct(
                player,
                state,
                BedWarsProductType.PICKAXE,
                state.pickaxeTier
            )
        ) {
            addOrDrop(player, permanentTool(BedWarsProductType.PICKAXE, state.pickaxeTier, state.teamId))
        }
        if (state.axeTier > 0 && !restoreConfiguredFixedProduct(
                player,
                state,
                BedWarsProductType.AXE,
                state.axeTier
            )
        ) {
            addOrDrop(player, permanentTool(BedWarsProductType.AXE, state.axeTier, state.teamId))
        }
        if (state.shears && !restoreConfiguredFixedProduct(player, state, BedWarsProductType.SHEARS, 1)) {
            addOrDrop(player, permanentItem(Material.SHEARS))
        }
        restoreConfiguredPermanentProducts(player, state)
        applyTeamUpgrades(player, state, restoreTeamEffects = true, restoreBaseEffects = true)
    }

    /** 在出生、复活和重连时重建自定义永久商品，并按参考重放其 BuyCommand。 */
    private fun restoreConfiguredPermanentProducts(player: Player, state: BedWarsPlayerState) {
        state.permanentProductIds.mapNotNull { productId ->
            moduleConfig.shop.items.firstOrNull { it.id == productId && usesConfiguredPermanentLifecycle(it) }
        }.forEach { product ->
            if (product.buyItems.isNotEmpty()) {
                giveConfiguredBuyItems(player, state, product, playAutoEquipSound = false)
            } else if (product.deliverProduct) {
                when (product.productType) {
                    BedWarsProductType.ITEM -> givePurchasedItem(player, state, product)
                    BedWarsProductType.POTION -> givePurchasedPotion(player, product)
                    BedWarsProductType.SPECIAL -> givePurchasedSpecial(player, product)
                    else -> Unit
                }
            }
            executePurchasedCommands(player, state, product)
        }
    }

    /** 查找固定装备类型当前等级显式声明的参考 buy-items 商品。 */
    private fun configuredFixedProduct(type: BedWarsProductType, tier: Int): BedWarsShopItem? {
        return moduleConfig.shop.items.firstOrNull {
            it.productType == type && it.tier == tier && it.buyItems.isNotEmpty()
        }
    }

    /** 从固定装备状态恢复显式 buy-items，返回是否接管了默认恢复。 */
    private fun restoreConfiguredFixedProduct(
        player: Player,
        state: BedWarsPlayerState,
        type: BedWarsProductType,
        tier: Int
    ): Boolean {
        val product = configuredFixedProduct(type, tier) ?: return false
        clearFixedProductItems(player, type)
        giveConfiguredBuyItems(player, state, product, playAutoEquipSound = false)
        return true
    }

    /** 按地图选择的参考物品组发放永久物品，并避免重复剑或弓。 */
    private fun giveDefaultItems(player: Player, teamId: String) {
        val group = gameConfig?.itemGroup?.lowercase() ?: "default"
        val items = moduleConfig.defaultItemGroups[group] ?: moduleConfig.defaultItemGroups.getValue("default")
        items.forEach { configured ->
            val isSword = configured.material.name.endsWith("_SWORD")
            val isBow = configured.material == Material.BOW || configured.material == Material.CROSSBOW
            if (isSword && player.inventory.contents.filterNotNull().any { it.type.name.endsWith("_SWORD") }) return@forEach
            if (isBow && player.inventory.contents.filterNotNull().any { it.type == Material.BOW || it.type == Material.CROSSBOW }) {
                return@forEach
            }
            addOrDrop(player, createDefaultItem(configured, teamId))
        }
    }

    /** 从当前地图物品组创建带永久、默认标记、名称和队伍强化的出生物品。 */
    private fun createDefaultItem(configured: BedWarsDefaultItem, teamId: String): ItemStack {
        val item = permanentItem(configured.material)
        item.amount = configured.amount
        val meta = item.itemMeta
        meta.persistentDataContainer.set(defaultItemKey, PersistentDataType.BYTE, 1.toByte())
        configured.displayName?.let { meta.displayName(configuredItemName(it)) }
        item.itemMeta = meta
        if (configured.material.name.endsWith("_SWORD") || configured.material.name.endsWith("_AXE")) {
            applySharpness(item, teamId)
        }
        return item
    }

    /** 在玩家没有任何剑时，从当前地图物品组恢复首个出生默认剑。 */
    private fun giveDefaultSword(player: Player, teamId: String) {
        if (player.inventory.contents.filterNotNull().any { it.type.name.endsWith("_SWORD") }) return
        val group = gameConfig?.itemGroup?.lowercase() ?: "default"
        val items = moduleConfig.defaultItemGroups[group] ?: moduleConfig.defaultItemGroups.getValue("default")
        val sword = items.firstOrNull { it.material.name.endsWith("_SWORD") } ?: return
        addOrDrop(player, createDefaultItem(sword, teamId))
    }

    /** 按参考 BuyItem.name 语义先重置样式，再解析 & 或 § 旧式颜色。 */
    private fun configuredItemName(value: String): Component {
        return LegacyComponentSerializer.legacyAmpersand().deserialize("&r${value.replace('§', '&')}")
    }

    private fun applyArmor(player: Player, state: BedWarsPlayerState) {
        val configuredProduct = if (state.armorTier > 0) {
            configuredFixedProduct(BedWarsProductType.ARMOR, state.armorTier)
        } else {
            null
        }
        val armorMaterials = when {
            configuredProduct != null -> Material.LEATHER_LEGGINGS to Material.LEATHER_BOOTS
            state.armorTier == 1 -> Material.CHAINMAIL_LEGGINGS to Material.CHAINMAIL_BOOTS
            state.armorTier == 2 -> Material.IRON_LEGGINGS to Material.IRON_BOOTS
            state.armorTier == 3 -> Material.DIAMOND_LEGGINGS to Material.DIAMOND_BOOTS
            else -> Material.LEATHER_LEGGINGS to Material.LEATHER_BOOTS
        }
        val helmet = permanentItem(Material.LEATHER_HELMET)
        val chestplate = permanentItem(Material.LEATHER_CHESTPLATE)
        val leggings = permanentItem(armorMaterials.first)
        val boots = permanentItem(armorMaterials.second)
        val color = teamStates[state.teamId]?.config?.color?.armorColor
        if (color != null) {
            listOf(helmet, chestplate, leggings, boots).forEach { armor ->
                (armor.itemMeta as? LeatherArmorMeta)?.let { meta ->
                    meta.setColor(color)
                    armor.itemMeta = meta
                }
            }
        }
        val protection = teamStates[state.teamId]?.upgrades?.get(BedWarsUpgradeType.PROTECTION) ?: 0
        if (protection > 0) {
            listOf(helmet, chestplate, leggings, boots).forEach {
                it.addUnsafeEnchantment(Enchantment.PROTECTION, protection)
            }
        }
        listOf(helmet, chestplate, leggings, boots).forEach {
            applyConfiguredTeamEnchantments(it, state.teamId)
        }
        player.inventory.helmet = helmet
        player.inventory.chestplate = chestplate
        player.inventory.leggings = leggings
        player.inventory.boots = boots
        configuredProduct?.buyItems?.filter { it.autoEquip }?.forEach { delivery ->
            val item = createConfiguredBuyItem(state, configuredProduct, delivery)
            autoEquipPurchasedArmor(player, state, item, playSound = false)
        }
    }

    /** 购买或恢复固定工具前移除同类型旧物品，护甲由装备槽覆盖处理。 */
    private fun clearFixedProductItems(player: Player, type: BedWarsProductType) {
        player.inventory.contents.filterNotNull().filter { stack ->
            when (type) {
                BedWarsProductType.PICKAXE -> stack.type.name.endsWith("_PICKAXE")
                BedWarsProductType.AXE -> stack.type.name.endsWith("_AXE") && !stack.type.name.endsWith("PICKAXE")
                BedWarsProductType.SHEARS -> stack.type == Material.SHEARS
                else -> false
            }
        }.forEach { it.amount = 0 }
    }

    private fun replaceTool(player: Player, state: BedWarsPlayerState, type: BedWarsProductType) {
        clearFixedProductItems(player, type)
        val item = when (type) {
            BedWarsProductType.PICKAXE -> permanentTool(type, state.pickaxeTier, state.teamId)
            BedWarsProductType.AXE -> permanentTool(type, state.axeTier, state.teamId)
            BedWarsProductType.SHEARS -> permanentItem(Material.SHEARS)
            else -> return
        }
        applyConfiguredTeamEnchantments(item, state.teamId)
        addOrDrop(player, item)
    }

    private fun toolMaterial(type: BedWarsProductType, tier: Int): Material = when (type) {
        BedWarsProductType.PICKAXE -> when (tier.coerceIn(1, 4)) {
            1 -> Material.WOODEN_PICKAXE
            2 -> Material.IRON_PICKAXE
            3 -> Material.GOLDEN_PICKAXE
            else -> Material.DIAMOND_PICKAXE
        }
        BedWarsProductType.AXE -> when (tier.coerceIn(1, 4)) {
            1 -> Material.WOODEN_AXE
            2 -> Material.IRON_AXE
            3 -> Material.GOLDEN_AXE
            else -> Material.DIAMOND_AXE
        }
        else -> Material.AIR
    }

    private fun permanentItem(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.isUnbreakable = true
        meta.persistentDataContainer.set(permanentItemKey, PersistentDataType.BYTE, 1.toByte())
        item.itemMeta = meta
        return item
    }

    /** 创建带参考等级附魔和独立永久标记的镐或斧。 */
    private fun permanentTool(type: BedWarsProductType, tier: Int, teamId: String): ItemStack {
        val safeTier = tier.coerceIn(1, 4)
        val item = permanentItem(toolMaterial(type, safeTier))
        val index = safeTier - 1
        val efficiency = when (type) {
            BedWarsProductType.PICKAXE -> moduleConfig.shop.pickaxeEfficiencyLevels[index]
            BedWarsProductType.AXE -> moduleConfig.shop.axeEfficiencyLevels[index]
            else -> 0
        }
        val sharpness = if (type == BedWarsProductType.PICKAXE) {
            moduleConfig.shop.pickaxeSharpnessLevels[index]
        } else {
            0
        }
        if (efficiency > 0) item.addUnsafeEnchantment(Enchantment.EFFICIENCY, efficiency)
        if (sharpness > 0) item.addUnsafeEnchantment(Enchantment.SHARPNESS, sharpness)
        if (type == BedWarsProductType.AXE) applySharpness(item, teamId)
        applyConfiguredTeamEnchantments(item, teamId)
        return item
    }

    private fun applyTeamUpgrades(
        player: Player,
        state: BedWarsPlayerState,
        restoreTeamEffects: Boolean = false,
        restoreBaseEffects: Boolean = false
    ) {
        val team = teamStates[state.teamId] ?: return
        val sharpness = team.upgrades[BedWarsUpgradeType.SHARPNESS] ?: 0
        val protection = team.upgrades[BedWarsUpgradeType.PROTECTION] ?: 0
        player.inventory.contents.filterNotNull().forEach { item ->
            val weapon = item.type.name.endsWith("_SWORD") ||
                item.type.name.endsWith("_AXE") && !item.type.name.endsWith("PICKAXE")
            if (sharpness > 0 && weapon) {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, sharpness)
            }
        }
        player.inventory.armorContents.filterNotNull().forEach { item ->
            if (protection > 0) item.addUnsafeEnchantment(Enchantment.PROTECTION, protection)
        }
        applyConfiguredTeamEnchantments(player, state.teamId)
        val haste = team.upgrades[BedWarsUpgradeType.HASTE] ?: 0
        if (haste > 0) {
            player.addPotionEffect(PotionEffect(PotionEffectType.HASTE, Int.MAX_VALUE, haste - 1, false, false, true))
        } else {
            player.removePotionEffect(PotionEffectType.HASTE)
        }
        if (restoreTeamEffects) team.teamEffects.values.forEach { applyUpgradePotionEffect(player, it) }
        if (restoreBaseEffects) team.baseEffects.values.forEach { applyUpgradePotionEffect(player, it) }
    }

    /** 刷新玩家背包和护甲上由 receive 保存的全部队伍附魔。 */
    private fun applyConfiguredTeamEnchantments(player: Player, teamId: String) {
        player.inventory.contents.filterNotNull().forEach { applyConfiguredTeamEnchantments(it, teamId) }
        player.inventory.armorContents.filterNotNull().forEach { applyConfiguredTeamEnchantments(it, teamId) }
    }

    /** 按物品类别将队伍 receive 附魔应用到单个现有或新建物品。 */
    private fun applyConfiguredTeamEnchantments(item: ItemStack, teamId: String) {
        val team = teamStates[teamId] ?: return
        val targets = buildList {
            if (item.type.name.endsWith("_SWORD") || item.type.name.endsWith("_AXE") &&
                !item.type.name.endsWith("PICKAXE")
            ) {
                add(BedWarsUpgradeEnchantTarget.SWORD)
            }
            if (armorSlot(item.type) != null) add(BedWarsUpgradeEnchantTarget.ARMOR)
            if (item.type == Material.BOW) add(BedWarsUpgradeEnchantTarget.BOW)
        }
        targets.forEach { target ->
            team.actionEnchantments[target].orEmpty().forEach(item::addUnsafeEnchantment)
        }
    }

    private fun applySharpness(item: ItemStack, teamId: String) {
        val level = teamStates[teamId]?.upgrades?.get(BedWarsUpgradeType.SHARPNESS) ?: 0
        if (level > 0) item.addUnsafeEnchantment(Enchantment.SHARPNESS, level)
    }

    /** 按当前阶商品的参考 is-downgradable 标记分别处理镐和斧，并保留一阶保底工具。 */
    private fun downgradeTools(state: BedWarsPlayerState) {
        state.pickaxeTier = downgradedToolTier(BedWarsProductType.PICKAXE, state.pickaxeTier)
        state.axeTier = downgradedToolTier(BedWarsProductType.AXE, state.axeTier)
    }

    /** 返回一次死亡后的工具等级；关闭 downgradable 时保留当前阶。 */
    private fun downgradedToolTier(type: BedWarsProductType, tier: Int): Int {
        if (tier <= 0) return 0
        val product = moduleConfig.shop.items.firstOrNull { it.productType == type && it.tier == tier }
        return if (product?.downgradable != false && tier > 1) tier - 1 else tier
    }

    private fun teamMembersOnline(teamId: String): List<Player> {
        return playerStates.entries
            .filter { it.value.teamId == teamId && !it.value.eliminated }
            .mapNotNull { Bukkit.getPlayer(it.key) }
    }

    private fun swordRank(material: Material): Int = when (material) {
        Material.WOODEN_SWORD, Material.GOLDEN_SWORD -> 1
        Material.STONE_SWORD -> 2
        Material.IRON_SWORD -> 3
        Material.DIAMOND_SWORD -> 4
        Material.NETHERITE_SWORD -> 5
        else -> if (material.name == "COPPER_SWORD") 2 else 0
    }

    private fun productName(id: String, fallback: String): String {
        val key = "bedwars.product_${id.replace('-', '_')}"
        val translated = language.getMessage(key)
        return if (translated == key) fallback else translated
    }

    /** 生成参考购买反馈使用的无颜色商品名，并移除菜单专用颜色和等级 token。 */
    private fun feedbackProductName(id: String, fallback: String): String {
        val value = productName(id, fallback)
            .replace("{color}", "")
            .replace("{tier}", "")
        return PlainTextComponentSerializer.plainText().serialize(configuredItemName(value))
    }

    /** 为快捷分配反馈查找真实商品名，并保留当前条目的罗马等级信息。 */
    private fun quickBuyFeedbackName(productId: String): String {
        val product = moduleConfig.shop.items.firstOrNull { it.id == productId } ?: return productId
        val value = productName(product.id, product.displayName)
            .replace("{color}", "")
            .replace("{tier}", romanShopTier(product.tier))
        return PlainTextComponentSerializer.plainText().serialize(configuredItemName(value))
    }

    /** 把商店语言中的 & 或 § 旧式样式解析为 Adventure 组件。 */
    private fun shopFeedbackComponent(value: String): Component {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(value.replace('§', '&'))
    }

    /** 返回四类死亡转移货币的本地化名称。 */
    private fun resourceName(material: Material): String = language.getMessage(
        when (material) {
            Material.AIR -> "bedwars.resource_money"
            Material.IRON_INGOT -> "bedwars.resource_iron"
            Material.GOLD_INGOT -> "bedwars.resource_gold"
            Material.DIAMOND -> "bedwars.resource_diamond"
            Material.EMERALD -> "bedwars.resource_emerald"
            else -> material.name
        }
    )

    /** 返回升级类型对应的本地化商品名。 */
    private fun upgradeName(type: BedWarsUpgradeType): String {
        val product = moduleConfig.shop.upgrades.firstOrNull { it.upgradeType == type }
        return product?.let { productName(it.id, it.displayName) } ?: type.name
    }

    /** 按配置刷新房间内所有参赛者和观战者的 Tab 头尾。 */
    private fun updateTabHeaderFooters(advanceAnimation: Boolean = false) {
        if (!moduleConfig.tabHeaderFooterEnabled) return
        (room.players + room.spectators).mapNotNull(Bukkit::getPlayer).forEach { player ->
            updateTabHeaderFooter(player, advanceAnimation)
        }
    }

    /** 根据 BedWars 阶段、队伍和身份渲染单名玩家的 Tab 头尾。 */
    private fun updateTabHeaderFooter(player: Player, advanceAnimation: Boolean = false) {
        if (!moduleConfig.tabHeaderFooterEnabled) return
        val state = playerStates[player.uniqueId]
        val template = tabHeaderFooterTemplate(player, state)
        val previousTemplate = tabHeaderFooterTemplates.put(player.uniqueId, template)
        val previousFrame = tabHeaderFooterFrames[player.uniqueId] ?: 0
        val frame = when {
            previousTemplate != template -> 0
            advanceAnimation && previousFrame == Int.MAX_VALUE -> 0
            advanceAnimation -> previousFrame + 1
            else -> previousFrame
        }
        tabHeaderFooterFrames[player.uniqueId] = frame
        val phaseName = if (suddenDeathStarted && phase == BedWarsPhase.RUNNING) {
            language.getMessage("bedwars.phase_sudden_death")
        } else {
            language.getMessage("bedwars.phase_${phase.name.lowercase()}")
        }
        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        val teamName = state?.teamId?.let(teamStates::get)?.config?.displayName ?: "-"
        val winnerName = resultWinnerTeamId?.let(teamStates::get)?.config?.displayName ?: "-"
        val arguments = arrayOf<Any>(
            moduleConfig.displayName,
            phaseName,
            room.id,
            room.players.size,
            maxPlayers,
            tabRoleLabel(player, state),
            teamName,
            phaseTimer.secondsLeft,
            winnerName,
            player.name,
            state?.kills ?: 0,
            state?.finalKills ?: 0,
            state?.bedsBroken ?: 0,
            state?.finalDeaths ?: 0,
            nextEventLine().orEmpty()
        )
        val serializer = LegacyComponentSerializer.legacySection()
        player.sendPlayerListHeaderAndFooter(
            serializer.deserialize(renderTabTemplate(template.headerKey, arguments, frame)),
            serializer.deserialize(renderTabTemplate(template.footerKey, arguments, frame))
        )
    }

    /** 把模板列表拼成多行文本，并按刷新序号选择每行逗号分隔的动画帧。 */
    private fun renderTabTemplate(key: String, arguments: Array<Any>, frame: Int): String {
        return language.getMessageList(key, *arguments).joinToString("\n") { line ->
            val frames = line.split(',')
            frames[Math.floorMod(frame, frames.size)]
        }
    }

    /** 按参考项目的阶段与玩家身份选择独立 Tab 头尾模板。 */
    private fun tabHeaderFooterTemplate(
        player: Player,
        state: BedWarsPlayerState?
    ): BedWarsTabHeaderFooterTemplate {
        val spectator = player.uniqueId in room.spectators || state == null
        return when (phase) {
            BedWarsPhase.WAITING -> if (spectator) {
                BedWarsTabHeaderFooterTemplate.WAITING_SPECTATOR
            } else {
                BedWarsTabHeaderFooterTemplate.WAITING_PLAYER
            }
            BedWarsPhase.COUNTDOWN -> if (spectator) {
                BedWarsTabHeaderFooterTemplate.COUNTDOWN_SPECTATOR
            } else {
                BedWarsTabHeaderFooterTemplate.COUNTDOWN_PLAYER
            }
            BedWarsPhase.RUNNING -> when {
                spectator -> BedWarsTabHeaderFooterTemplate.RUNNING_SPECTATOR
                state.eliminated -> BedWarsTabHeaderFooterTemplate.RUNNING_ELIMINATED
                else -> BedWarsTabHeaderFooterTemplate.RUNNING_PLAYER
            }
            BedWarsPhase.RESULT, BedWarsPhase.CLOSING -> when {
                spectator -> BedWarsTabHeaderFooterTemplate.RESULT_SPECTATOR
                resultWinnerTeamId == null -> BedWarsTabHeaderFooterTemplate.RESULT_DRAW
                state.teamId != resultWinnerTeamId -> BedWarsTabHeaderFooterTemplate.RESULT_LOSER
                state.eliminated -> BedWarsTabHeaderFooterTemplate.RESULT_WINNER_ELIMINATED
                else -> BedWarsTabHeaderFooterTemplate.RESULT_WINNER_ALIVE
            }
        }
    }

    /** 返回参考 Tab 状态分支对应的本地化玩家身份。 */
    private fun tabRoleLabel(player: Player, state: BedWarsPlayerState?): String {
        val resultPhase = phase == BedWarsPhase.RESULT || (phase == BedWarsPhase.CLOSING && resultRecorded)
        val key = when {
            player.uniqueId in room.spectators || state == null -> "bedwars.tab_role_spectator"
            resultPhase && resultWinnerTeamId != null && state.eliminated &&
                state.teamId == resultWinnerTeamId -> "bedwars.tab_role_winner_eliminated"
            resultPhase && resultWinnerTeamId != null &&
                state.teamId == resultWinnerTeamId -> "bedwars.tab_role_winner"
            resultPhase && resultWinnerTeamId == null -> "bedwars.tab_role_draw"
            state.eliminated -> "bedwars.tab_role_eliminated"
            else -> "bedwars.tab_role_participant"
        }
        return language.getMessage(key)
    }

    /** 按当前阶段刷新房间内全部参赛者和外部观战者的 Tab 玩家名称。 */
    private fun updateTabPlayerNames(advanceAnimation: Boolean = false) {
        if (!usesCustomTabPlayerNames()) return
        (room.players + room.spectators).mapNotNull(Bukkit::getPlayer).forEach { player ->
            updateTabPlayerName(player, advanceAnimation)
        }
    }

    /** 为单名玩家应用当前阶段格式；阶段开关关闭时使用原版纯玩家名。 */
    private fun updateTabPlayerName(player: Player, advanceAnimation: Boolean = false) {
        if (!usesCustomTabPlayerNames()) return
        player.playerListName(
            if (isTabPlayerListFormattingEnabled()) {
                tabPlayerName(player, playerStates[player.uniqueId], advanceAnimation)
            } else {
                tabPlayerNameTemplates.remove(player.uniqueId)
                tabPlayerNameFrames.remove(player.uniqueId)
                Component.text(player.name)
            }
        )
    }

    /** 返回当前内部阶段是否启用参考玩家列表格式。 */
    private fun isTabPlayerListFormattingEnabled(): Boolean = when (phase) {
        BedWarsPhase.WAITING -> moduleConfig.tabPlayerListWaitingEnabled
        BedWarsPhase.COUNTDOWN -> moduleConfig.tabPlayerListCountdownEnabled
        BedWarsPhase.RUNNING -> moduleConfig.tabPlayerListRunningEnabled
        BedWarsPhase.RESULT -> moduleConfig.tabPlayerListResultEnabled
        BedWarsPhase.CLOSING -> moduleConfig.tabPlayerListResultEnabled && resultRecorded
    }

    /** 根据阶段、队伍和观战身份构造可本地化的 Tab 玩家名称。 */
    private fun tabPlayerName(
        player: Player,
        state: BedWarsPlayerState?,
        advanceAnimation: Boolean
    ): Component {
        val team = state?.teamId?.let { teamId ->
            teamStates[teamId]?.config ?: gameConfig?.teams?.firstOrNull { it.id == teamId }
        }
        val teamName = team?.displayName ?: "-"
        val teamColor = team?.color?.textColor ?: NamedTextColor.WHITE
        val resultPhase = phase == BedWarsPhase.RESULT || (phase == BedWarsPhase.CLOSING && resultRecorded)
        val spectatorPlayerNameKey = when (phase) {
            BedWarsPhase.WAITING -> "bedwars.tab_player_spectator_waiting"
            BedWarsPhase.COUNTDOWN -> "bedwars.tab_player_spectator_countdown"
            BedWarsPhase.RUNNING -> "bedwars.tab_player_spectator_running"
            BedWarsPhase.RESULT -> "bedwars.tab_player_spectator_result"
            BedWarsPhase.CLOSING -> if (resultRecorded) {
                "bedwars.tab_player_spectator_result"
            } else {
                "bedwars.tab_player_spectator"
            }
        }
        val base = when {
            player.uniqueId in room.spectators || state == null -> tabPlayerNameComponent(
                tabPlayerNameFrame(
                    player.uniqueId,
                    spectatorPlayerNameKey,
                    arrayOf(player.name),
                    advanceAnimation
                ),
                NamedTextColor.GRAY
            ).decorate(TextDecoration.ITALIC)
            resultPhase && resultWinnerTeamId != null && state.eliminated &&
                state.teamId == resultWinnerTeamId -> tabPlayerNameComponent(
                tabPlayerNameFrame(
                    player.uniqueId,
                    "bedwars.tab_player_winner_eliminated",
                    arrayOf(teamName, player.name),
                    advanceAnimation
                ),
                NamedTextColor.GOLD
            ).decorate(TextDecoration.BOLD)
            resultPhase && resultWinnerTeamId != null && state.teamId == resultWinnerTeamId -> tabPlayerNameComponent(
                tabPlayerNameFrame(
                    player.uniqueId,
                    "bedwars.tab_player_winner",
                    arrayOf(teamName, player.name),
                    advanceAnimation
                ),
                NamedTextColor.GOLD
            ).decorate(TextDecoration.BOLD)
            resultPhase && resultWinnerTeamId == null -> tabPlayerNameComponent(
                tabPlayerNameFrame(
                    player.uniqueId,
                    "bedwars.tab_player_draw",
                    arrayOf(teamName, player.name),
                    advanceAnimation
                ),
                NamedTextColor.YELLOW
            )
            resultPhase -> tabPlayerNameComponent(
                tabPlayerNameFrame(
                    player.uniqueId,
                    "bedwars.tab_player_loser",
                    arrayOf(teamName, player.name),
                    advanceAnimation
                ),
                teamColor
            )
            state.eliminated -> tabPlayerNameComponent(
                tabPlayerNameFrame(
                    player.uniqueId,
                    "bedwars.tab_player_eliminated",
                    arrayOf(teamName, player.name),
                    advanceAnimation
                ),
                NamedTextColor.GRAY
            ).decorate(TextDecoration.ITALIC)
            phase == BedWarsPhase.COUNTDOWN -> tabPlayerNameComponent(
                tabPlayerNameFrame(
                    player.uniqueId,
                    "bedwars.tab_player_countdown",
                    arrayOf(player.name),
                    advanceAnimation
                ),
                NamedTextColor.YELLOW
            )
            phase == BedWarsPhase.WAITING -> tabPlayerNameComponent(
                tabPlayerNameFrame(
                    player.uniqueId,
                    "bedwars.tab_player_waiting",
                    arrayOf(player.name),
                    advanceAnimation
                ),
                NamedTextColor.WHITE
            )
            else -> tabPlayerNameComponent(
                tabPlayerNameFrame(
                    player.uniqueId,
                    "bedwars.tab_player_team",
                    arrayOf(teamName, player.name),
                    advanceAnimation
                ),
                teamColor
            )
        }
        return appendTabPlayerLevel(base, player.uniqueId)
    }

    /** 按阶段、名称模板、参数和刷新序号选择列表帧，上下文变化时从首帧重新开始。 */
    private fun tabPlayerNameFrame(
        playerId: UUID,
        key: String,
        arguments: Array<out Any>,
        advanceAnimation: Boolean
    ): String {
        val phaseIdentity = when (phase) {
            BedWarsPhase.RESULT, BedWarsPhase.CLOSING -> "result"
            else -> phase.name
        }
        val templateIdentity = buildString {
            append(phaseIdentity).append(':').append(key)
            arguments.forEach { argument -> append('\u0000').append(argument) }
        }
        val previousTemplate = tabPlayerNameTemplates.put(playerId, templateIdentity)
        val previousFrame = tabPlayerNameFrames[playerId] ?: 0
        val frame = when {
            previousTemplate != templateIdentity -> 0
            advanceAnimation && previousFrame == Int.MAX_VALUE -> 0
            advanceAnimation -> previousFrame + 1
            else -> previousFrame
        }
        tabPlayerNameFrames[playerId] = frame
        val frames = language.getMessageList(key, *arguments)
        if (frames.isEmpty()) return ""
        val value = frames[Math.floorMod(frame, frames.size)]
        val (vaultPrefix, vaultSuffix) = Bukkit.getPlayer(playerId)?.let(::tabPlayerChatAffixes) ?: ("" to "")
        return value
            .replace("{vPrefix}", vaultPrefix)
            .replace("{vSuffix}", vaultSuffix)
    }

    /** 通过可选 PlaceholderAPI 解析参考 Vault 前后缀，缺插件或扩展时安全回退空串。 */
    private fun tabPlayerChatAffixes(player: Player): Pair<String, String> {
        if (!plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) return "" to ""
        return runCatching {
            val api = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            val method = api.getMethod("setPlaceholders", Player::class.java, String::class.java)
            fun resolve(token: String): String {
                val value = method.invoke(null, player, token) as? String ?: token
                return value.takeUnless { it == token } ?: ""
            }
            resolve("%vault_prefix%") to resolve("%vault_suffix%")
        }.getOrDefault("" to "")
    }

    /** 返回聊天模板使用的可选 Vault 前后缀，与 Tab 玩家名保持同一解析语义。 */
    fun chatAffixes(player: Player): Pair<String, String> = tabPlayerChatAffixes(player)

    /** 返回玩家当前持久 BedWars 等级，供聊天模板展开参考 level token。 */
    fun chatLevel(playerId: UUID): Int {
        return levelProgress(resultService.metric(room, playerId, METRIC_LEVEL_EXPERIENCE)).level
    }

    /** 把玩家名模板中的旧式颜色转为 Adventure，并为未着色文本应用身份颜色。 */
    private fun tabPlayerNameComponent(value: String, fallbackColor: NamedTextColor): Component {
        return LegacyComponentSerializer.legacyAmpersand()
            .deserialize(value.replace('§', '&'))
            .colorIfAbsent(fallbackColor)
    }

    /** 在等级系统开启时把持久 BedWars 等级附加到 Tab 玩家名称。 */
    private fun appendTabPlayerLevel(base: Component, playerId: UUID): Component {
        if (!moduleConfig.levelRules.enabled) return base
        val level = levelProgress(resultService.metric(room, playerId, METRIC_LEVEL_EXPERIENCE)).level
        return base.append(Component.text(language.getMessage("bedwars.tab_player_level", level), NamedTextColor.GRAY))
    }

    /** 返回当前阶段是否启用 BedWars 自定义 Sidebar。 */
    private fun isSidebarEnabledForCurrentPhase(): Boolean = when (phase) {
        BedWarsPhase.WAITING, BedWarsPhase.COUNTDOWN -> moduleConfig.lobbySidebarEnabled
        BedWarsPhase.RUNNING, BedWarsPhase.RESULT -> moduleConfig.sidebarEnabled
        BedWarsPhase.CLOSING -> false
    }

    /** 按独立刷新间隔原地推进标题帧，并保护事件监听器已替换的标题。 */
    private fun tickSidebarTitleAnimation(sidebarEnabled: Boolean) {
        if (!sidebarEnabled || moduleConfig.sidebarTitleRefreshTicks <= 0 || sidebarViewers.isEmpty()) {
            sidebarTitleTicks = 0
            return
        }
        val previousTitle = sidebarTitle()
        sidebarTitleTicks++
        if (sidebarTitleTicks < moduleConfig.sidebarTitleRefreshTicks) return
        sidebarTitleTicks = 0
        sidebarTitleFrame = if (sidebarTitleFrame == Int.MAX_VALUE) 0 else sidebarTitleFrame + 1
        val nextTitle = sidebarTitle()
        sidebarViewers.mapNotNull(Bukkit::getPlayer).forEach { viewer ->
            SidebarBoardRenderer.updateTitle(viewer, "bedwars_${room.id}", previousTitle, nextTitle)
        }
    }

    /** 返回当前阶段与动画帧对应的本地化 Sidebar 标题。 */
    private fun sidebarTitle(): Component {
        if (sidebarTitlePhase != phase) {
            sidebarTitlePhase = phase
            sidebarTitleTicks = 0
            sidebarTitleFrame = 0
        }
        val frames = language.getMessageList("bedwars.scoreboard_title")
        if (frames.isEmpty()) return Component.empty()
        val value = frames[Math.floorMod(sidebarTitleFrame, frames.size)]
        return LegacyComponentSerializer.legacySection().deserialize(value)
    }

    /** 返回参考生命目标语言列表在当前动画帧对应的 Adventure 标签。 */
    private fun healthDisplayLabel(): Component {
        val frames = language.getMessageList("bedwars.scoreboard_health")
        if (frames.isEmpty()) return Component.empty()
        val value = frames[Math.floorMod(healthAnimationFrame, frames.size)]
        return LegacyComponentSerializer.legacySection().deserialize(value)
    }

    private fun updateDisplays(advanceSidebarAnimation: Boolean = false) {
        (room.players + room.spectators).mapNotNull(Bukkit::getPlayer).forEach { player ->
            updateDisplay(player, advanceSidebarAnimation)
        }
    }

    private fun updateDisplay(player: Player, advanceSidebarAnimation: Boolean = false) {
        val state = playerStates[player.uniqueId]
        val showHealth = phase == BedWarsPhase.RUNNING && moduleConfig.healthDisplayEnabled
        val healthLabel = if (showHealth) healthDisplayLabel() else Component.empty()
        val sidebarEnabled = isSidebarEnabledForCurrentPhase()
        val phaseName = if (suddenDeathStarted && phase == BedWarsPhase.RUNNING) {
            language.getMessage("bedwars.phase_sudden_death")
        } else {
            language.getMessage("bedwars.phase_${phase.name.lowercase()}")
        }
        if (sidebarEnabled) {
            val teamLines = teamStates.values.take(8).map { team ->
                val alive = playerStates.values.count {
                    it.teamId == team.config.id && it.participant && !it.eliminated
                }
                val bed = if (team.bedAlive) "✓" else "✗"
                language.getMessage("bedwars.scoreboard_team", team.config.displayName, bed, alive)
            }
            val statistic = gameConfig?.sidebarTopStatistic ?: moduleConfig.sidebarTopStatistic
            val hideMissing = gameConfig?.sidebarTopHideMissing ?: moduleConfig.sidebarTopHideMissing
            val leaderLines = if (phase == BedWarsPhase.RESULT) {
                resultLeaders(statistic, hideMissing).mapIndexed { index, (playerId, entry) ->
                    val playerName = Bukkit.getPlayer(playerId)?.name
                        ?: Bukkit.getOfflinePlayer(playerId).name
                        ?: playerId.toString().take(8)
                    language.getMessage(
                        "bedwars.scoreboard_result_entry",
                        index + 1,
                        playerName,
                        statistic.valueOf(entry)
                    )
                }
            } else {
                emptyList()
            }
            val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
            val sidebarLevelProgress = if (moduleConfig.levelRules.enabled) {
                levelProgress(resultService.metric(room, player.uniqueId, METRIC_LEVEL_EXPERIENCE))
            } else {
                null
            }
            val teamName = state?.teamId?.let { teamId ->
                teamStates[teamId]?.config?.displayName
                    ?: gameConfig?.teams?.firstOrNull { it.id == teamId }?.displayName
            } ?: "-"
            val winnerName = resultWinnerTeamId?.let(teamStates::get)?.config?.displayName
            val winnerTeam = resultWinnerTeamId?.let(teamStates::get)
            val winnerColor = winnerTeam?.config?.color?.let(::teamLegacyColor).orEmpty()
            val winnerLetter = winnerName?.firstOrNull()?.toString().orEmpty()
            val configuredGame = room.configuredGame
            val mapDisplayName = configuredGame?.displayName ?: room.name
            val mapName = configuredGame?.localId ?: room.id
            val mapGroup = configuredGame?.selectorGroup ?: "default"
            val winnerLine = winnerName?.let { name ->
                language.getMessage("bedwars.scoreboard_winner", name)
            } ?: language.getMessage("bedwars.tab_role_draw")
            val template = tabHeaderFooterTemplate(player, state)
            val templateUsesMoney = language.getMessageList(template.sidebarLinesKey).any { "{money}" in it }
            val moneyValue = if (templateUsesMoney) sidebarMoneyBalance(player) else null
            val spectatorTarget = sidebarSpectatorTarget(player)
            val progressBar = sidebarLevelProgress?.let(::levelProgressBar)
            val currentExperience = sidebarLevelProgress?.levelExperience?.let(::formatLevelNumber)
            val requiredExperience = sidebarLevelProgress?.nextLevelExperience?.let(::formatLevelNumber)
            val lineValues = linkedMapOf(
                "{phase}" to listOf(language.getMessage("bedwars.scoreboard_phase", phaseName)),
                "{time}" to listOf(language.getMessage("bedwars.scoreboard_time", phaseTimer.secondsLeft)),
                "{players}" to listOf(language.getMessage(
                    "bedwars.scoreboard_players",
                    room.players.size,
                    maxPlayers
                )),
                "{role}" to listOf(language.getMessage(
                    "bedwars.scoreboard_role",
                    tabRoleLabel(player, state)
                )),
                "{team}" to listOf(language.getMessage("bedwars.scoreboard_player_team", teamName)),
                "{money}" to listOfNotNull(moneyValue),
                "{player}" to listOf(player.name),
                "{playerName}" to listOf(player.name),
                "{date}" to listOf(sidebarDate()),
                "{version}" to listOf(plugin.pluginMeta.version),
                "{server}" to listOf(velocityBridgeService.serverId),
                "{serverIp}" to listOf(moduleConfig.sidebarServerIp),
                "{server_ip}" to listOf(moduleConfig.sidebarServerIp),
                "{poweredBy}" to listOf(moduleConfig.sidebarPoweredBy),
                "{map}" to listOf(mapDisplayName),
                "{map_name}" to listOf(mapName),
                "{group}" to listOf(mapGroup),
                "{spectatorTarget}" to listOfNotNull(spectatorTarget),
                "{progress}" to listOfNotNull(progressBar),
                "{level}" to listOfNotNull(sidebarLevelProgress?.let(::levelLine)),
                "{levelUnformatted}" to listOfNotNull(sidebarLevelProgress?.level?.toString()),
                "{currentXp}" to listOfNotNull(currentExperience),
                "{requiredXp}" to listOfNotNull(requiredExperience),
                "{on}" to listOf(room.players.size.toString()),
                "{max}" to listOf(maxPlayers.toString()),
                "{nextEvent}" to listOfNotNull(nextEventLine()),
                "{teams}" to teamLines,
                "{kills}" to state?.let {
                    listOf(language.getMessage("bedwars.scoreboard_kills", it.kills))
                }.orEmpty(),
                "{finalKills}" to state?.let {
                    listOf(language.getMessage("bedwars.scoreboard_final_kills", it.finalKills))
                }.orEmpty(),
                "{beds}" to state?.let {
                    listOf(language.getMessage("bedwars.scoreboard_beds", it.bedsBroken))
                }.orEmpty(),
                "{deaths}" to state?.let {
                    listOf(language.getMessage("bedwars.scoreboard_deaths", it.deaths))
                }.orEmpty(),
                "{finalDeaths}" to state?.let {
                    listOf(language.getMessage("bedwars.scoreboard_final_deaths", it.finalDeaths))
                }.orEmpty(),
                "{winner}" to if (phase == BedWarsPhase.RESULT) {
                    listOf(winnerLine)
                } else {
                    emptyList()
                },
                "{winnerTeamName}" to if (phase == BedWarsPhase.RESULT) listOfNotNull(winnerName) else emptyList(),
                "{winnerTeamLetter}" to if (phase == BedWarsPhase.RESULT && winnerName != null) {
                    listOf(winnerColor + winnerLetter)
                } else {
                    emptyList()
                },
                "{winnerTeamColor}" to if (phase == BedWarsPhase.RESULT && winnerName != null) {
                    listOf(winnerColor)
                } else {
                    emptyList()
                },
                "{resultTop}" to if (phase == BedWarsPhase.RESULT) {
                    listOf(language.getMessage(
                        "bedwars.scoreboard_result_top",
                        language.getMessage(statistic.languageKey)
                    ))
                } else {
                    emptyList()
                },
                "{leaders}" to leaderLines,
                "{room}" to listOf(language.getMessage("bedwars.scoreboard_room", room.id))
            )
            addSidebarTeamTokens(lineValues, state)
            val inlineValues = linkedMapOf(
                "{phase}" to phaseName,
                "{time}" to sidebarTimeValue(),
                "{players}" to "${room.players.size}/$maxPlayers",
                "{role}" to tabRoleLabel(player, state),
                "{team}" to teamName,
                "{money}" to moneyValue.orEmpty(),
                "{player}" to player.name,
                "{playerName}" to player.name,
                "{date}" to sidebarDate(),
                "{version}" to plugin.pluginMeta.version,
                "{server}" to velocityBridgeService.serverId,
                "{serverIp}" to moduleConfig.sidebarServerIp,
                "{server_ip}" to moduleConfig.sidebarServerIp,
                "{poweredBy}" to moduleConfig.sidebarPoweredBy,
                "{map}" to mapDisplayName,
                "{map_name}" to mapName,
                "{group}" to mapGroup,
                "{spectatorTarget}" to spectatorTarget.orEmpty(),
                "{progress}" to progressBar.orEmpty(),
                "{level}" to (sidebarLevelProgress?.level?.toString() ?: ""),
                "{levelUnformatted}" to (sidebarLevelProgress?.level?.toString() ?: ""),
                "{currentXp}" to currentExperience.orEmpty(),
                "{requiredXp}" to requiredExperience.orEmpty(),
                "{on}" to room.players.size.toString(),
                "{max}" to maxPlayers.toString(),
                "{nextEvent}" to nextEventName().orEmpty(),
                "{teams}" to teamLines.joinToString(" | "),
                "{kills}" to (state?.kills?.toString() ?: ""),
                "{finalKills}" to (state?.finalKills?.toString() ?: ""),
                "{beds}" to (state?.bedsBroken?.toString() ?: ""),
                "{deaths}" to (state?.deaths?.toString() ?: ""),
                "{finalDeaths}" to (state?.finalDeaths?.toString() ?: ""),
                "{winner}" to if (phase == BedWarsPhase.RESULT) {
                    winnerName ?: language.getMessage("bedwars.tab_role_draw")
                } else {
                    ""
                },
                "{winnerTeamName}" to if (phase == BedWarsPhase.RESULT) winnerName.orEmpty() else "",
                "{winnerTeamLetter}" to if (phase == BedWarsPhase.RESULT && winnerName != null) {
                    winnerColor + winnerLetter
                } else {
                    ""
                },
                "{winnerTeamColor}" to if (phase == BedWarsPhase.RESULT && winnerName != null) winnerColor else "",
                "{resultTop}" to if (phase == BedWarsPhase.RESULT) language.getMessage(statistic.languageKey) else "",
                "{leaders}" to leaderLines.joinToString(" | "),
                "{room}" to room.id
            )
            val lines = renderSidebarLines(
                player.uniqueId,
                template.sidebarLinesKey,
                lineValues,
                inlineValues,
                advanceSidebarAnimation
            )
            SidebarBoardRenderer.show(
                player,
                "bedwars_${room.id}",
                sidebarTitle(),
                lines,
                showHealthBelowName = showHealth,
                showHealthInPlayerList = showHealth && moduleConfig.healthDisplayInTab,
                healthLabel = healthLabel
            )
            sidebarViewers.add(player.uniqueId)
        } else {
            clearSidebarIfShown(player)
        }
        if (phase == BedWarsPhase.RESULT) return
        if (state?.respawning == true) {
            player.sendActionBar(Component.text(language.getMessage("bedwars.respawn_countdown", state.respawnTicks.coerceAtLeast(0) / 20)))
        } else if (state != null) {
            val bed = if (teamStates[state.teamId]?.bedAlive == true) "✓" else "✗"
            val protectionTicks = respawnProtectionTicksLeft(state)
            if (protectionTicks > 0) {
                player.sendActionBar(Component.text(language.getMessage(
                    "bedwars.actionbar_respawn_protection",
                    (protectionTicks + 19) / 20
                )))
                return
            }
            val teammate = nearestActiveTeammate(player, state)
            val message = if (teammate == null) {
                language.getMessage("bedwars.actionbar_status", bed, phaseTimer.secondsLeft)
            } else {
                language.getMessage(
                    "bedwars.actionbar_status_tracking",
                    bed,
                    phaseTimer.secondsLeft,
                    teamStates[state.teamId]?.config?.displayName ?: state.teamId,
                    player.location.distance(teammate.location).toInt()
                )
            }
            player.sendActionBar(Component.text(message))
        }
    }

    /** 按阶段身份模板展开动态行 token，并在占位符刷新节点推进同行逗号动画。 */
    private fun renderSidebarLines(
        playerId: UUID,
        key: String,
        values: Map<String, List<String>>,
        inlineValues: Map<String, String>,
        advanceAnimation: Boolean
    ): List<String> {
        val previousTemplate = sidebarLineTemplates.put(playerId, key)
        val previousFrame = sidebarLineFrames[playerId] ?: 0
        val frame = when {
            previousTemplate != key -> 0
            advanceAnimation && previousFrame == Int.MAX_VALUE -> 0
            advanceAnimation -> previousFrame + 1
            else -> previousFrame
        }
        sidebarLineFrames[playerId] = frame
        return buildList {
            language.getMessageList(key).forEach { configuredLine ->
                val frames = configuredLine.split(',')
                val line = frames[Math.floorMod(frame, frames.size)]
                val expanded = values[line.trim()]
                if (expanded != null) {
                    addAll(expanded)
                } else {
                    val inline = inlineValues.entries.fold(line) { current, (token, value) ->
                        current.replace(token, value)
                    }
                    add(values.entries.fold(inline) { current, (token, tokenLines) ->
                        current.replace(token, tokenLines.joinToString(" | "))
                    })
                }
            }
        }
    }

    /** 仅在本 Session 曾渲染 Sidebar 时清除它，避免覆盖其他显示提供方。 */
    private fun clearSidebarIfShown(player: Player) {
        sidebarLineTemplates.remove(player.uniqueId)
        sidebarLineFrames.remove(player.uniqueId)
        if (sidebarViewers.remove(player.uniqueId)) SidebarBoardRenderer.clear(player)
    }

    /** 返回参考 Sidebar 使用的 Vault double 余额文本，无经济服务时与 NoEconomy 一致返回 0.0。 */
    private fun sidebarMoneyBalance(player: Player): String = vaultEconomy.balance(player)?.toString() ?: "0.0"

    /** 返回嵌入模板使用的参考原始时间值，运行阶段对齐下一主事件倒计时。 */
    private fun sidebarTimeValue(): String = when (phase) {
        BedWarsPhase.WAITING -> sidebarDate()
        BedWarsPhase.COUNTDOWN -> phaseTimer.secondsLeft.toString()
        BedWarsPhase.RUNNING -> {
            val elapsedSeconds = gameElapsedTicks / 20
            nextTimelineEntry(elapsedSeconds)?.let { formatEventTime(it.startSeconds - elapsedSeconds) } ?: "0:00"
        }
        BedWarsPhase.RESULT, BedWarsPhase.CLOSING -> phaseTimer.secondsLeft.toString()
    }

    /** 返回嵌入模板使用的下一主事件本地化名称。 */
    private fun nextEventName(): String? {
        if (phase != BedWarsPhase.RUNNING) return null
        val next = nextTimelineEntry(gameElapsedTicks / 20) ?: return null
        return language.getMessage(next.languageKey)
    }

    /** 选择尚未发生且剩余时间最短的事件，并生成 sidebar 提示。 */
    private fun nextEventLine(): String? {
        if (phase != BedWarsPhase.RUNNING) return null
        val elapsedSeconds = gameElapsedTicks / 20
        val next = nextTimelineEntry(elapsedSeconds) ?: return null
        return language.getMessage(
            "bedwars.scoreboard_next_event",
            language.getMessage(next.languageKey),
            formatEventTime(next.startSeconds - elapsedSeconds)
        )
    }

    /** 返回玩家当前持久等级的 Sidebar 行。 */
    private fun levelLine(progress: BedWarsLevelProgress): String {
        return language.getMessage(
            "bedwars.scoreboard_level",
            progress.level,
            progress.levelExperience,
            progress.nextLevelExperience
        )
    }

    /** 按当前服务器级语言中的日期格式生成参考 Sidebar 日期。 */
    private fun sidebarDate(): String {
        val pattern = language.getMessage("bedwars.scoreboard_date_format")
        return runCatching { LocalDate.now().format(DateTimeFormatter.ofPattern(pattern)) }
            .getOrElse { LocalDate.now().format(DateTimeFormatter.ofPattern("yy/MM/dd")) }
    }

    /** 返回第一人称观战目标及其队伍，托管自由视角或无效目标时隐藏。 */
    private fun sidebarSpectatorTarget(player: Player): String? {
        val target = player.spectatorTarget as? Player ?: return null
        val targetState = playerStates[target.uniqueId] ?: return null
        val teamName = teamStates[targetState.teamId]?.config?.displayName ?: targetState.teamId
        return language.getMessage("bedwars.scoreboard_spectator_target", teamName, target.name)
    }

    /** 添加参考每队动态 token 及当前玩家所属队伍的快捷 token。 */
    private fun addSidebarTeamTokens(
        values: MutableMap<String, List<String>>,
        viewerState: BedWarsPlayerState?
    ) {
        teamStates.values.forEach { team ->
            val alive = playerStates.values.count {
                it.teamId == team.config.id && it.participant && !it.eliminated
            }
            val member = viewerState?.teamId == team.config.id
            val status = sidebarTeamStatus(team, alive, appendMember = member)
            val color = teamLegacyColor(team.config.color)
            val name = team.config.displayName
            val letter = name.firstOrNull()?.toString().orEmpty()
            sidebarTeamTokenNames(team.config.id).forEach { teamName ->
                values["{Team${teamName}Status}"] = listOf(status)
                values["{Team${teamName}Color}"] = listOf(color)
                values["{Team${teamName}Name}"] = listOf(name)
                values["{Team${teamName}Letter}"] = listOf(letter)
            }
            if (member) {
                values["{teamStatus}"] = listOf(sidebarTeamStatus(team, alive, appendMember = false))
                values["{teamColor}"] = listOf(color)
                values["{teamName}"] = listOf(name)
                values["{teamLetter}"] = listOf(color + letter)
            }
        }
    }

    /** 返回队伍 ID 原样及参考首字母大写形式，供动态 token 兼容。 */
    private fun sidebarTeamTokenNames(teamId: String): Set<String> {
        val referenceName = teamId.split('-', '_').joinToString("") { part ->
            part.replaceFirstChar { it.uppercase() }
        }
        return linkedSetOf(teamId, referenceName).filter(String::isNotBlank).toSet()
    }

    /** 按床、剩余成员和 viewer 所属关系生成参考队伍状态标记。 */
    private fun sidebarTeamStatus(team: BedWarsTeamState, alive: Int, appendMember: Boolean): String {
        val status = when {
            team.bedAlive -> language.getMessage("bedwars.scoreboard_team_status_alive")
            alive > 0 -> language.getMessage("bedwars.scoreboard_team_status_bed_destroyed", alive)
            else -> language.getMessage("bedwars.scoreboard_team_status_eliminated")
        }
        return if (appendMember) status + language.getMessage("bedwars.scoreboard_team_status_you") else status
    }

    /** 按参考十格算法生成可配置颜色、符号和外框的等级进度条。 */
    private fun levelProgressBar(progress: BedWarsLevelProgress): String {
        val required = progress.nextLevelExperience.coerceAtLeast(1)
        var locked = (((required - progress.levelExperience).coerceAtLeast(0) / required.toDouble()) * 10.0).toInt()
        var unlocked = 10 - locked
        if (locked < 0 || unlocked < 0) {
            locked = 10
            unlocked = 0
        }
        val rules = moduleConfig.levelRules
        val bar = rules.progressBarUnlockedColor + rules.progressBarSymbol.repeat(unlocked) +
            rules.progressBarLockedColor + rules.progressBarSymbol.repeat(locked)
        return rules.progressBarFormat.replace("{progress}", bar).replace('&', '§')
    }

    /** 把参考等级经验值格式化为最多两位小数的 k 缩写。 */
    private fun formatLevelNumber(value: Int): String {
        if (value < 1000) return value.toString()
        return BigDecimal.valueOf(value.toLong())
            .divide(BigDecimal.valueOf(1000L), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString() + "k"
    }

    /** 返回当前尚未发生且绝对截止时间最早的主时间线条目。 */
    private fun nextTimelineEntry(elapsedSeconds: Int): BedWarsTimelineEntry? {
        if (phase != BedWarsPhase.RUNNING) return null
        val scheduledEvents = buildList {
            moduleConfig.generatorRules.diamondTiers.filter { it.tier in 2..3 }.forEach { tier ->
                add(BedWarsTimelineEntry(
                    "diamond-tier-${tier.tier}",
                    "bedwars.next_event_diamond_${tier.tier}",
                    tier.startSeconds
                ))
            }
            moduleConfig.generatorRules.emeraldTiers.filter { it.tier in 2..3 }.forEach { tier ->
                add(BedWarsTimelineEntry(
                    "emerald-tier-${tier.tier}",
                    "bedwars.next_event_emerald_${tier.tier}",
                    tier.startSeconds
                ))
            }
            if (!bedsDestroyedByTimer && moduleConfig.bedsDestroySeconds > 0) {
                add(BedWarsTimelineEntry(
                    "beds-destroy",
                    "bedwars.next_event_beds_destroy",
                    moduleConfig.bedsDestroySeconds
                ))
            }
            if (!suddenDeathStarted && moduleConfig.suddenDeathSeconds > 0) {
                add(BedWarsTimelineEntry(
                    "sudden-death",
                    "bedwars.next_event_sudden_death",
                    moduleConfig.suddenDeathSeconds
                ))
            }
            if (moduleConfig.durationSeconds > 0) {
                add(BedWarsTimelineEntry("game-end", "bedwars.next_event_game_end", moduleConfig.durationSeconds))
            }
        }
        return scheduledEvents.filter { it.startSeconds > elapsedSeconds }.minByOrNull { it.startSeconds }
    }

    /** 比较并发布下一主时间线条目的首次确定或后续切换。 */
    private fun updateTimelineStage() {
        if (phase != BedWarsPhase.RUNNING) return
        val elapsedSeconds = gameElapsedTicks / 20
        val next = nextTimelineEntry(elapsedSeconds)
        val previousStageId = currentTimelineStageId
        if (timelineInitialized && previousStageId == next?.id) return
        timelineInitialized = true
        currentTimelineStageId = next?.id
        if (previousStageId == null && next == null) return
        Bukkit.getPluginManager().callEvent(
            GameTimelineStageChangedEvent(
                room,
                previousStageId,
                next?.id,
                elapsedSeconds,
                next?.startSeconds
            )
        )
    }

    /** 将下一事件剩余秒数格式化为不受区域设置影响的分秒文本。 */
    private fun formatEventTime(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        return "${safeSeconds / 60}:${(safeSeconds % 60).toString().padStart(2, '0')}"
    }

    /** 返回同世界中距离玩家最近且可行动的在线队友。 */
    private fun nearestActiveTeammate(player: Player, state: BedWarsPlayerState): Player? {
        if (phase != BedWarsPhase.RUNNING || state.eliminated || state.respawning) return null
        return playerStates.entries.asSequence()
            .filter { (playerId, teammateState) ->
                playerId != player.uniqueId && teammateState.teamId == state.teamId && teammateState.participant &&
                    !teammateState.eliminated && !teammateState.respawning && !teammateState.disconnected
            }
            .mapNotNull { Bukkit.getPlayer(it.key) }
            .filter { it.world == player.world }
            .minByOrNull { it.location.distanceSquared(player.location) }
    }

    private fun prepareGenerators() {
        removeGeneratorHolograms()
        generatorStates.clear()
        announcedGeneratorTiers.clear()
        gameConfig?.teams.orEmpty().forEach { team ->
            team.generators.forEach { generatorStates += BedWarsGeneratorState(it, team.id) }
        }
        gameConfig?.generators.orEmpty().forEach { generatorStates += BedWarsGeneratorState(it, null) }
        spawnGeneratorHolograms()
    }

    private fun tickGenerators() {
        val elapsedSeconds = gameElapsedTicks / 20
        generatorStates.forEach { state ->
            rotateGeneratorHologram(state)
            if (effectiveDisableEmptyTeamGenerators && state.teamId in eliminatedTeams) return@forEach
            val tier = moduleConfig.generatorRules.tier(state.config.type, elapsedSeconds)
            if (tier != null && tier.tier > state.tier) {
                state.tier = tier.tier
                state.ticksUntilSpawn = minOf(state.ticksUntilSpawn, tier.intervalTicks)
                val previousTier = announcedGeneratorTiers[state.config.type] ?: 1
                if (previousTier < tier.tier) {
                    announcedGeneratorTiers[state.config.type] = tier.tier
                    roomBroadcastService.localized(
                        room,
                        language,
                        "bedwars.generator_upgraded",
                        state.config.type.name,
                        tier.tier,
                        includeSpectators = true
                    )
                    playGeneratorUpgradeSound(state.config.type)
                    Bukkit.getPluginManager().callEvent(
                        GameResourceTierChangedEvent(
                            room,
                            state.config.type.name.lowercase(),
                            previousTier,
                            tier.tier,
                            tier.startSeconds,
                            elapsedSeconds
                        )
                    )
                }
            }
            state.ticksUntilSpawn--
            if (state.ticksUntilSpawn <= 0) {
                state.ticksUntilSpawn = generatorInterval(state, tier)
                spawnGeneratorItem(state, tier)
            }
            if (gameElapsedTicks % 20 == 0) updateGeneratorHologram(state)
        }
        tickForgeEmeralds()
    }

    /** 向当前房间参赛者和观战者播放资源类型对应的升阶音效。 */
    private fun playGeneratorUpgradeSound(type: BedWarsGeneratorType) {
        val rule = when (type) {
            BedWarsGeneratorType.DIAMOND -> moduleConfig.generatorRules.diamondUpgradeSound
            BedWarsGeneratorType.EMERALD -> moduleConfig.generatorRules.emeraldUpgradeSound
            else -> return
        }
        playSoundRule(roomBroadcastService.participants(room), rule)
    }

    /** 为公共钻石和绿宝石点生成等级、类型、倒计时及旋转资源标识。 */
    private fun spawnGeneratorHolograms() {
        if (!moduleConfig.generatorRules.hologramsEnabled) return
        val world = room.world ?: return
        generatorStates.filter { it.teamId == null }.forEach { state ->
            if (state.config.type != BedWarsGeneratorType.DIAMOND &&
                state.config.type != BedWarsGeneratorType.EMERALD
            ) return@forEach
            val base = state.config.point.toLocation(world).block.location.add(0.5, 1.3, 0.5)
            val tier = spawnGeneratorHologramLine(
                base.clone().add(0.0, 3.0, 0.0),
                language.getMessage("bedwars.generator_hologram_tier", generatorTierLabel(state.tier)),
                "tier"
            )
            val type = spawnGeneratorHologramLine(
                base.clone().add(0.0, 2.7, 0.0),
                language.getMessage(
                    if (state.config.type == BedWarsGeneratorType.DIAMOND) {
                        "bedwars.generator_hologram_diamond"
                    } else {
                        "bedwars.generator_hologram_emerald"
                    }
                ),
                "type"
            )
            val timer = spawnGeneratorHologramLine(
                base.clone().add(0.0, 2.4, 0.0),
                language.getMessage(
                    "bedwars.generator_hologram_timer",
                    (state.ticksUntilSpawn + 19) / 20
                ),
                "timer"
            )
            val item = world.spawn(base.clone().add(0.0, 0.5, 0.0), ArmorStand::class.java) {
                it.setGravity(false)
                it.isVisible = false
                it.isMarker = true
                it.isInvulnerable = true
                it.isSilent = true
                it.isPersistent = true
                it.removeWhenFarAway = false
                it.equipment.helmet = ItemStack(
                    if (state.config.type == BedWarsGeneratorType.DIAMOND) Material.DIAMOND_BLOCK else Material.EMERALD_BLOCK
                )
                it.addScoreboardTag("kgc_bedwars_generator_item")
            }
            trackEntity(item, type = "generator-hologram-item")
            state.hologram = BedWarsGeneratorHologramState(
                tier.uniqueId,
                type.uniqueId,
                timer.uniqueId,
                item.uniqueId
            )
        }
    }

    /** 生成一行无碰撞且由房间资源作用域管理的生成器提示。 */
    private fun spawnGeneratorHologramLine(location: Location, text: String, kind: String): ArmorStand {
        val hologram = location.world.spawn(location, ArmorStand::class.java) {
            it.setGravity(false)
            it.isVisible = false
            it.isMarker = true
            it.isSmall = true
            it.isInvulnerable = true
            it.isSilent = true
            it.isPersistent = true
            it.removeWhenFarAway = false
            it.customName(Component.text(text))
            it.isCustomNameVisible = true
            it.addScoreboardTag("kgc_bedwars_generator_hologram_$kind")
        }
        trackEntity(hologram, type = "generator-hologram-$kind")
        return hologram
    }

    /** 按当前阶段和剩余生成 tick 刷新公共资源点提示。 */
    private fun updateGeneratorHologram(state: BedWarsGeneratorState) {
        val hologram = state.hologram ?: return
        (Bukkit.getEntity(hologram.tierEntityId) as? ArmorStand)?.customName(
            Component.text(language.getMessage("bedwars.generator_hologram_tier", generatorTierLabel(state.tier)))
        )
        (Bukkit.getEntity(hologram.timerEntityId) as? ArmorStand)?.customName(
            Component.text(language.getMessage(
                "bedwars.generator_hologram_timer",
                (state.ticksUntilSpawn.coerceAtLeast(0) + 19) / 20
            ))
        )
    }

    /** 平滑旋转公共资源点上方的钻石或绿宝石方块。 */
    private fun rotateGeneratorHologram(state: BedWarsGeneratorState) {
        if (!moduleConfig.generatorRules.rotateHologramItems) return
        val hologram = state.hologram ?: return
        val item = Bukkit.getEntity(hologram.itemEntityId) as? ArmorStand ?: return
        hologram.rotationDegrees = (hologram.rotationDegrees + 4.0) % 360.0
        item.headPose = EulerAngle(0.0, Math.toRadians(hologram.rotationDegrees), 0.0)
    }

    /** 返回公共资源点使用的罗马数字阶段名称。 */
    private fun generatorTierLabel(tier: Int): String = when (tier) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        else -> tier.toString()
    }

    /** 移除当前 session 的全部生成器显示实体并解除资源登记。 */
    private fun removeGeneratorHolograms() {
        generatorStates.forEach { state ->
            val hologram = state.hologram ?: return@forEach
            listOf(
                hologram.tierEntityId,
                hologram.typeEntityId,
                hologram.timerEntityId,
                hologram.itemEntityId
            ).forEach { entityId ->
                Bukkit.getEntity(entityId)?.remove()
                resourceScope?.releaseEntity(entityId)
                trackedEntities.remove(entityId)
            }
            state.hologram = null
        }
    }

    private fun generatorInterval(state: BedWarsGeneratorState, tier: BedWarsGeneratorTier?): Int {
        val baseInterval = if (tier == null || tier.tier == 1) state.config.intervalTicks else tier.intervalTicks
        val teamId = state.teamId ?: return baseInterval
        teamStates[teamId]?.generatorEdits?.get(state.config.type)?.let { return it.intervalTicks }
        if (state.config.type != BedWarsGeneratorType.IRON && state.config.type != BedWarsGeneratorType.GOLD) return baseInterval
        val forgeTier = teamStates[teamId]?.upgrades?.get(BedWarsUpgradeType.FORGE) ?: 0
        if (forgeTier <= 0) return baseInterval
        return (baseInterval * moduleConfig.forgeRules.speedMultiplier(forgeTier)).toInt().coerceAtLeast(1)
    }

    private fun spawnGeneratorItem(state: BedWarsGeneratorState, tier: BedWarsGeneratorTier?) {
        val world = room.world ?: return
        val location = state.config.point.toLocation(world)
        val edit = state.teamId?.let { teamStates[it]?.generatorEdits?.get(state.config.type) }
        val (amount, limit) = edit?.let { it.amount to it.spawnLimit } ?: when (state.config.type) {
            BedWarsGeneratorType.IRON -> moduleConfig.generatorRules.ironAmount to moduleConfig.generatorRules.ironSpawnLimit
            BedWarsGeneratorType.GOLD -> moduleConfig.generatorRules.goldAmount to moduleConfig.generatorRules.goldSpawnLimit
            BedWarsGeneratorType.DIAMOND,
            BedWarsGeneratorType.EMERALD -> (tier?.amount ?: 1) to (tier?.spawnLimit ?: 4)
        }
        if (spawnSplitTeamResource(state.teamId, location, state.config.type.material, amount, limit)) return
        spawnResource(location, state.config.type.material, amount, limit)
    }

    /** 在队伍生成器附近有多名有效玩家时，为每人直接发放完整本次资源。 */
    private fun spawnSplitTeamResource(
        teamId: String?,
        location: Location,
        material: Material,
        amount: Int,
        limit: Int
    ): Boolean {
        val rules = moduleConfig.generatorRules
        if (!rules.teamSplitEnabled || teamId == null) return false
        val teamMemberCount = playerStates.values.count {
            it.participant && !it.eliminated && it.teamId == teamId
        }
        if (teamMemberCount == 1) return false
        val existing = location.world.getNearbyEntities(location, 3.0, 3.0, 3.0)
            .filterIsInstance<Item>()
            .filter { it.itemStack.type == material }
            .size
        if (existing >= limit) return true
        val radius = rules.teamSplitRadius
        val nearbyPlayers = location.world.getNearbyEntities(location, radius, radius, radius)
            .filterIsInstance<Player>()
            .filter(::isActiveParticipant)
        if (nearbyPlayers.size <= 1) return false
        nearbyPlayers.forEach { player ->
            player.inventory.addItem(ItemStack(material, amount)).values.forEach { leftover ->
                dropGeneratorResource(player.location, leftover, "generator-split-overflow")
            }
            player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.3f)
        }
        return true
    }

    /** 为三、四阶队伍锻炉生成额外绿宝石。 */
    private fun tickForgeEmeralds() {
        val world = room.world ?: return
        teamStates.forEach { (_, team) ->
            if (effectiveDisableEmptyTeamGenerators && team.config.id in eliminatedTeams) return@forEach
            val forgeTier = team.upgrades[BedWarsUpgradeType.FORGE] ?: 0
            if (forgeTier < 3) return@forEach
            if (BedWarsGeneratorType.EMERALD in team.generatorEdits) return@forEach
            if (team.forgeEmeraldTicks > 0) {
                team.forgeEmeraldTicks--
                return@forEach
            }
            team.forgeEmeraldTicks = moduleConfig.forgeRules.emeraldIntervalTicks
            val point = team.config.generators.firstOrNull { it.type == BedWarsGeneratorType.IRON }?.point
                ?: team.config.spawn
                ?: return@forEach
            val amount = if (forgeTier >= 4) {
                moduleConfig.forgeRules.tier4EmeraldAmount
            } else {
                moduleConfig.forgeRules.tier3EmeraldAmount
            }
            val location = point.toLocation(world)
            val split = spawnSplitTeamResource(
                team.config.id,
                location,
                Material.EMERALD,
                amount,
                moduleConfig.forgeRules.emeraldSpawnLimit
            )
            if (!split) {
                spawnResource(location, Material.EMERALD, amount, moduleConfig.forgeRules.emeraldSpawnLimit)
            }
        }
    }

    /** 在资源点遵守堆积上限生成物品，并登记到房间资源作用域。 */
    private fun spawnResource(location: Location, material: Material, amount: Int, limit: Int) {
        val existing = location.world.getNearbyEntities(location, 3.0, 3.0, 3.0)
            .filterIsInstance<Item>()
            .filter { it.itemStack.type == material }
            .size
        if (existing >= limit) return
        dropGeneratorResource(location, ItemStack(material, amount.coerceAtLeast(1)), "generator-resource")
    }

    /** 按 stack-items 配置生成一个资源堆或多个独立实体，并统一登记房间标记。 */
    private fun dropGeneratorResource(location: Location, stack: ItemStack, trackedType: String) {
        val stacks = if (moduleConfig.generatorRules.stackItems) {
            listOf(stack.clone())
        } else {
            List(stack.amount.coerceAtLeast(1)) { stack.clone().apply { amount = 1 } }
        }
        stacks.forEach { resource ->
            val item = location.world.dropItem(location, resource)
            item.velocity = Vector()
            item.pickupDelay = 0
            item.addScoreboardTag(GENERATOR_RESOURCE_TAG)
            trackEntity(item, type = trackedType)
        }
    }

    private fun tickBedsDestroy() {
        val destroySeconds = moduleConfig.bedsDestroySeconds
        if (bedsDestroyedByTimer || destroySeconds <= 0 || gameElapsedTicks < destroySeconds * 20) return
        destroyAllBeds("beds-destroy")
    }

    /** 销毁所有队伍床并关闭后续复活能力。 */
    private fun destroyAllBeds(sourceId: String) {
        if (bedsDestroyedByTimer) return
        bedsDestroyedByTimer = true
        val destroyedTeamIds = teamStates.values.filter { it.bedAlive }.map { it.config.id }
        teamStates.values.forEach {
            it.bedAlive = false
            updateBedHolograms(it.config.id)
        }
        val world = room.world
        if (world != null) {
            bedBlocks.keys.forEach { world.getBlockAt(it.x, it.y, it.z).type = Material.AIR }
        }
        bedBlocks.clear()
        destroyedTeamIds.forEach { teamId ->
            Bukkit.getPluginManager().callEvent(
                GameObjectiveDestroyedEvent(
                    room,
                    objectiveType = "bed",
                    objectiveId = teamId,
                    actor = null,
                    actorTeamId = null,
                    targetTeamId = teamId,
                    sourceId = sourceId
                )
            )
        }
        roomBroadcastService.localized(room, language, "bedwars.all_beds_destroyed", includeSpectators = true)
        playSoundRule(roomBroadcastService.participants(room), moduleConfig.allBedsDestroyedSound)
    }

    /** 到达配置时间后进入 Sudden Death，销毁床并生成各存活队伍的末影龙。 */
    private fun tickSuddenDeath() {
        val startSeconds = moduleConfig.suddenDeathSeconds
        if (suddenDeathStarted || startSeconds <= 0 || gameElapsedTicks < startSeconds * 20) return
        suddenDeathStarted = true
        destroyAllBeds("sudden-death")
        spawnSuddenDeathDragons()
        roomBroadcastService.localized(room, language, "bedwars.sudden_death_started", includeSpectators = true)
        roomBroadcastService.title(
            room,
            Component.text(language.getMessage("bedwars.sudden_death_title")),
            Component.text(language.getMessage("bedwars.sudden_death_subtitle")),
            includeSpectators = true
        )
        playSoundRule(roomBroadcastService.participants(room), moduleConfig.suddenDeathSound)
    }

    /** 为每个仍有存活成员的队伍生成基础数量及 Dragon Buff 额外末影龙。 */
    private fun spawnSuddenDeathDragons() {
        val world = room.world ?: return
        val center = spectatorSpawn().clone().add(0.0, moduleConfig.dragonRules.spawnHeight, 0.0)
        val aliveTeams = teamStates.values.filter { team ->
            playerStates.values.any { it.teamId == team.config.id && it.participant && !it.eliminated }
        }
        aliveTeams.forEachIndexed { teamIndex, team ->
            val buffTier = team.upgrades[BedWarsUpgradeType.DRAGON_BUFF] ?: 0
            val count = team.dragonCount?.coerceIn(0, 8) ?: (
                moduleConfig.dragonRules.baseDragons + buffTier * moduleConfig.dragonRules.buffExtraDragons
            ).coerceIn(1, 8)
            repeat(count) { dragonIndex ->
                val angle = (teamIndex * 2.0 * Math.PI / aliveTeams.size.coerceAtLeast(1)) + dragonIndex * 0.7
                val spawn = center.clone().add(kotlin.math.cos(angle) * 8.0, dragonIndex * 2.0, kotlin.math.sin(angle) * 8.0)
                val dragon = world.spawn(spawn, EnderDragon::class.java) {
                    it.phase = EnderDragon.Phase.CIRCLING
                    it.isPersistent = true
                    it.removeWhenFarAway = false
                    it.customName(Component.text(
                        language.getMessage("bedwars.dragon_name", team.config.displayName),
                        team.config.color.textColor
                    ))
                    it.isCustomNameVisible = true
                }
                dragon.getAttribute(Attribute.MAX_HEALTH)?.baseValue = moduleConfig.dragonRules.health
                dragon.health = moduleConfig.dragonRules.health.coerceAtMost(
                    dragon.getAttribute(Attribute.MAX_HEALTH)?.value ?: moduleConfig.dragonRules.health
                )
                dragonStates[dragon.uniqueId] = BedWarsDragonState(team.config.id, center.clone())
                trackEntity(dragon, type = "sudden-death-dragon")
            }
        }
    }

    /** 驱动队伍末影龙追击最近敌人，并用房间敌我规则造成接触伤害。 */
    private fun tickDragons() {
        val iterator = dragonStates.iterator()
        while (iterator.hasNext()) {
            val (dragonId, state) = iterator.next()
            val dragon = Bukkit.getEntity(dragonId) as? EnderDragon
            if (dragon == null || !dragon.isValid || dragon.isDead) {
                resourceScope?.releaseEntity(dragonId)
                trackedEntities.remove(dragonId)
                iterator.remove()
                continue
            }
            if (dragon.location.distanceSquared(state.center) > 160.0 * 160.0) dragon.teleport(state.center)
            val target = playerStates.entries.asSequence()
                .filter { it.value.teamId != state.teamId && !it.value.eliminated && !it.value.respawning }
                .mapNotNull { Bukkit.getPlayer(it.key) }
                .filter { it.world == dragon.world && it.isOnline }
                .minByOrNull { it.location.distanceSquared(dragon.location) }
            val destination = target?.eyeLocation ?: state.center
            val delta = destination.toVector().subtract(dragon.location.toVector())
            if (delta.lengthSquared() > 1.0) {
                dragon.velocity = delta.normalize().multiply(moduleConfig.dragonRules.speed)
            }
            dragon.phase = EnderDragon.Phase.CIRCLING
            if (gameElapsedTicks % 10 == 0 && target != null && target.location.distanceSquared(dragon.location) <=
                moduleConfig.dragonRules.attackRadius * moduleConfig.dragonRules.attackRadius
            ) {
                target.damage(moduleConfig.dragonRules.damage, dragon)
            }
        }
    }

    /** 侦测隐身效果增减，并持续向敌队和观战者重发隐藏护甲与名牌覆盖。 */
    private fun tickInvisibility() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { target ->
            val state = playerStates[target.uniqueId] ?: return@forEach
            val invisible = isActiveParticipant(target) && target.hasPotionEffect(PotionEffectType.INVISIBILITY)
            if (invisible && invisiblePlayers.add(target.uniqueId)) {
                hideInvisibleAppearance(target, state)
                publishInvisibilityChange(target, state, invisible = true)
            } else if (!invisible && invisiblePlayers.remove(target.uniqueId)) {
                restoreInvisibleAppearance(target)
                publishInvisibilityChange(target, state, invisible = false)
            } else if (invisible && gameElapsedTicks % 10 == 0) {
                hideInvisibleAppearance(target, state)
            }
        }
        invisiblePlayers.toList().forEach { playerId ->
            if (Bukkit.getPlayer(playerId) != null) return@forEach
            invisiblePlayers.remove(playerId)
        }
    }

    /** 对敌队和观战者隐藏护甲，并按参考行为对全部其他 viewer 隐藏名牌。 */
    private fun hideInvisibleAppearance(
        target: Player,
        state: BedWarsPlayerState,
        viewers: List<Player> = (room.players + room.spectators).mapNotNull(Bukkit::getPlayer)
    ) {
        val visibleViewers = viewers.filter { it != target }
        val hiddenArmor = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
            .associateWith { ItemStack(Material.AIR) }
        visibleViewers
            .filter { viewer ->
                viewer.uniqueId in room.spectators || playerStates[viewer.uniqueId]?.teamId != state.teamId
            }
            .forEach { viewer -> viewer.sendEquipmentChange(target, hiddenArmor) }
        val color = teamStates[state.teamId]?.config?.color?.textColor ?: net.kyori.adventure.text.format.NamedTextColor.WHITE
        nametagService.sendOverride(
            room,
            target,
            PlayerNametag(
                color = color,
                visibility = NametagVisibility.NEVER,
                collisionRule = updatePlayerCollision(target, state.teamId)
            ),
            visibleViewers
        )
    }

    /** 向所有房间 viewer 恢复目标真实护甲和标准队伍名牌。 */
    private fun restoreInvisibleAppearance(target: Player) {
        val viewers = (room.players + room.spectators).mapNotNull(Bukkit::getPlayer).filter { it != target }
        val armor = mapOf(
            EquipmentSlot.HEAD to (target.inventory.helmet ?: ItemStack(Material.AIR)),
            EquipmentSlot.CHEST to (target.inventory.chestplate ?: ItemStack(Material.AIR)),
            EquipmentSlot.LEGS to (target.inventory.leggings ?: ItemStack(Material.AIR)),
            EquipmentSlot.FEET to (target.inventory.boots ?: ItemStack(Material.AIR))
        )
        viewers.forEach { viewer -> viewer.sendEquipmentChange(target, armor) }
        nametagService.refresh(room, target, viewers)
    }

    /** 移除目标隐身并立刻恢复其敌方可见外观。 */
    private fun clearInvisibleAppearance(target: Player, notify: Boolean) {
        if (!moduleConfig.specials.removeInvisibilityOnDamage && notify) return
        val hadInvisibility = target.hasPotionEffect(PotionEffectType.INVISIBILITY) || target.uniqueId in invisiblePlayers
        if (!hadInvisibility) return
        target.removePotionEffect(PotionEffectType.INVISIBILITY)
        if (invisiblePlayers.remove(target.uniqueId)) {
            restoreInvisibleAppearance(target)
            publishInvisibilityChange(target, playerStates[target.uniqueId], invisible = false)
        }
        if (notify) target.sendMessage(Component.text(language.getMessage("bedwars.invisibility_removed_damage")))
    }

    /** 发布玩法已经提交的隐身外观状态变化。 */
    private fun publishInvisibilityChange(target: Player, state: BedWarsPlayerState?, invisible: Boolean) {
        Bukkit.getPluginManager().callEvent(
            GamePlayerInvisibilityChangedEvent(room, target, state?.teamId, invisible)
        )
    }

    /** 向新加入的 viewer 补发当前房间全部敌方隐身覆盖。 */
    private fun refreshInvisibleAppearanceForViewer(viewer: Player) {
        invisiblePlayers.mapNotNull(Bukkit::getPlayer).forEach { target ->
            val state = playerStates[target.uniqueId] ?: return@forEach
            hideInvisibleAppearance(target, state, listOf(viewer))
        }
    }

    /** 在结算或关房前恢复所有隐身玩家的客户端外观覆盖。 */
    private fun restoreAllInvisibleAppearances() {
        invisiblePlayers.mapNotNull(Bukkit::getPlayer).forEach { player ->
            restoreInvisibleAppearance(player)
            publishInvisibilityChange(player, playerStates[player.uniqueId], invisible = false)
        }
        invisiblePlayers.clear()
    }

    /** 比较玩家当前基地范围并按离开、进入顺序发布实际区域切换。 */
    private fun updatePlayerBaseRegion(player: Player, location: Location) {
        val playerId = player.uniqueId
        val playerTeamId = playerStates[playerId]?.teamId
        val currentTeamId = teamStates.entries.firstOrNull { (_, team) ->
            within(location, team.config.bed ?: team.config.spawn, effectiveIslandRadius)
        }?.key
        val previousTeamId = playerBaseRegions[playerId]
        if (previousTeamId == currentTeamId) return
        if (currentTeamId == null) playerBaseRegions.remove(playerId) else playerBaseRegions[playerId] = currentTeamId
        if (previousTeamId != null) {
            if (previousTeamId == playerTeamId) removeStoredBaseEffects(player, previousTeamId)
            Bukkit.getPluginManager().callEvent(
                GamePlayerBaseRegionChangedEvent(room, player, previousTeamId, entered = false)
            )
        }
        if (currentTeamId != null) {
            if (currentTeamId == playerTeamId) applyStoredBaseEffects(player, currentTeamId)
            Bukkit.getPluginManager().callEvent(
                GamePlayerBaseRegionChangedEvent(room, player, currentTeamId, entered = true)
            )
        }
    }

    /** 在死亡等非移动状态切换时结束玩家当前基地范围。 */
    private fun leavePlayerBaseRegion(player: Player) {
        val teamId = playerBaseRegions.remove(player.uniqueId) ?: return
        if (playerStates[player.uniqueId]?.teamId == teamId) removeStoredBaseEffects(player, teamId)
        Bukkit.getPluginManager().callEvent(GamePlayerBaseRegionChangedEvent(room, player, teamId, entered = false))
    }

    /** 玩家进入己方基地时重新应用该队保存的 receive 基地效果。 */
    private fun applyStoredBaseEffects(player: Player, teamId: String) {
        teamStates[teamId]?.baseEffects?.values?.forEach { action -> applyUpgradePotionEffect(player, action) }
    }

    /** 玩家离开己方基地时移除该队 receive 基地效果。 */
    private fun removeStoredBaseEffects(player: Player, teamId: String) {
        teamStates[teamId]?.baseEffects?.keys?.forEach(player::removePotionEffect)
    }

    /** 刷新基地内治愈效果，离开范围时立即移除，并低频发送参考村民粒子。 */
    private fun tickHealPools() {
        val activePlayers = linkedSetOf<UUID>()
        teamStates.forEach { (teamId, team) ->
            if ((team.upgrades[BedWarsUpgradeType.HEAL_POOL] ?: 0) <= 0) return@forEach
            val base = team.config.bed ?: team.config.spawn ?: return@forEach
            teamMembersOnline(teamId)
                .filter { isActiveParticipant(it) && within(it.location, base, effectiveIslandRadius) }
                .forEach {
                    activePlayers += it.uniqueId
                    it.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, true))
                }
            if (moduleConfig.shop.healPoolParticlesEnabled && gameElapsedTicks % HEAL_POOL_PARTICLE_INTERVAL_TICKS == 0) {
                showHealPoolParticles(teamId, team.config.spawn ?: base)
            }
        }
        (healPoolPlayers - activePlayers).mapNotNull(Bukkit::getPlayer).forEach { player ->
            player.removePotionEffect(PotionEffectType.REGENERATION)
        }
        healPoolPlayers.clear()
        healPoolPlayers.addAll(activePlayers)
    }

    /** 在玩家跨方块进入或离开己方基地时立即应用或移除治愈池效果。 */
    private fun updateHealPoolForPlayer(player: Player, state: BedWarsPlayerState, location: Location) {
        val team = teamStates[state.teamId]
        val base = team?.config?.bed ?: team?.config?.spawn
        val active = team != null &&
            (team.upgrades[BedWarsUpgradeType.HEAL_POOL] ?: 0) > 0 &&
            isActiveParticipant(player) &&
            within(location, base, effectiveIslandRadius)
        if (active) {
            healPoolPlayers += player.uniqueId
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, true))
        } else if (healPoolPlayers.remove(player.uniqueId)) {
            player.removePotionEffect(PotionEffectType.REGENERATION)
        }
    }

    /** 在队伍出生区域生成有界随机村民粒子，并按配置限制观看者。 */
    private fun showHealPoolParticles(teamId: String, center: BedWarsPoint) {
        val world = room.world ?: return
        val viewers = if (moduleConfig.shop.healPoolParticlesTeamOnly) {
            teamMembersOnline(teamId)
        } else {
            roomBroadcastService.players(room)
        }.filter { it.world == world }
        if (viewers.isEmpty()) return
        val radius = effectiveIslandRadius
        val random = ThreadLocalRandom.current()
        repeat(HEAL_POOL_PARTICLE_SAMPLES) {
            val location = Location(
                world,
                center.x + random.nextDouble(-radius, radius),
                center.y + random.nextDouble(-radius, radius),
                center.z + random.nextDouble(-radius, radius)
            )
            if (!location.block.isEmpty) return@repeat
            viewers.forEach { viewer ->
                viewer.spawnParticle(Particle.HAPPY_VILLAGER, location, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    /** 移除由治愈池维护的生命恢复效果并清空 Session 跟踪。 */
    private fun clearHealPoolEffects() {
        healPoolPlayers.mapNotNull(Bukkit::getPlayer).forEach { player ->
            player.removePotionEffect(PotionEffectType.REGENERATION)
        }
        healPoolPlayers.clear()
    }

    /** 以事件目标位置判断敌方基地进入状态，避免依赖尚未提交的玩家坐标。 */
    private fun checkEnemyBaseEntry(player: Player, state: BedWarsPlayerState, location: Location) {
        if (state.trapImmuneUntilMillis > System.currentTimeMillis()) return
        teamStates.forEach { (teamId, team) ->
            if (teamId == state.teamId) return@forEach
            val base = team.config.bed ?: team.config.spawn
            val inside = within(location, base, effectiveIslandRadius)
            if (!inside) {
                state.enteredEnemyBases.remove(teamId)
                return@forEach
            }
            if (!state.enteredEnemyBases.add(teamId) || !team.bedAlive || team.traps.isEmpty()) return@forEach
            triggerTrap(teamId, team, player)
        }
    }

    /** 购买陷阱后立即检查已在基地内的首名有效敌人，并按参考语义触发队首陷阱。 */
    private fun triggerTrapForCurrentIntruder(teamId: String, team: BedWarsTeamState) {
        if (!team.bedAlive || team.traps.isEmpty()) return
        val base = team.config.bed ?: team.config.spawn ?: return
        val intruder = playerStates.entries.asSequence()
            .filter { (_, state) ->
                state.participant && !state.eliminated && !state.respawning && state.teamId != teamId
            }
            .mapNotNull { (playerId, _) -> Bukkit.getPlayer(playerId) }
            .firstOrNull { player -> within(player.location, base, effectiveIslandRadius) }
            ?: return
        triggerTrap(teamId, team, intruder)
    }

    /** 消耗并执行队首陷阱，向守方发送参考聊天、标题与音效反馈。 */
    private fun triggerTrap(teamId: String, team: BedWarsTeamState, intruder: Player) {
        if (team.traps.isEmpty()) return
        val queuedTrap = team.traps.removeFirst()
        val trap = queuedTrap.upgradeType
        val rules = moduleConfig.shop
        val base = team.config.bed ?: team.config.spawn
        if (queuedTrap.actions.isNotEmpty()) {
            executeTrapActions(teamId, team, intruder, queuedTrap.actions)
        } else when (trap) {
            BedWarsUpgradeType.TRAP_BLINDNESS -> {
                intruder.addPotionEffect(PotionEffect(
                    PotionEffectType.BLINDNESS,
                    rules.blindnessTrapDurationTicks,
                    rules.blindnessTrapAmplifier,
                    false,
                    false,
                    true
                ))
                intruder.addPotionEffect(PotionEffect(
                    PotionEffectType.SLOWNESS,
                    rules.blindnessTrapDurationTicks,
                    rules.blindnessTrapAmplifier,
                    false,
                    false,
                    true
                ))
            }
            BedWarsUpgradeType.TRAP_COUNTER_OFFENSIVE -> {
                teamMembersOnline(teamId)
                    .filter { isActiveParticipant(it) && within(it.location, base, effectiveIslandRadius) }
                    .forEach { defender ->
                        defender.addPotionEffect(PotionEffect(
                            PotionEffectType.SPEED,
                            rules.counterOffensiveTrapDurationTicks,
                            rules.counterOffensiveTrapAmplifier,
                            false,
                            false,
                            true
                        ))
                    }
            }
            BedWarsUpgradeType.TRAP_ALARM -> {
                clearInvisibleAppearance(intruder, notify = false)
                if (rules.alarmTrapGlowingTicks > 0) {
                    intruder.addPotionEffect(PotionEffect(
                        PotionEffectType.GLOWING,
                        rules.alarmTrapGlowingTicks,
                        0,
                        false,
                        false,
                        true
                    ))
                }
            }
            BedWarsUpgradeType.TRAP_MINER_FATIGUE -> {
                intruder.addPotionEffect(PotionEffect(
                    PotionEffectType.MINING_FATIGUE,
                    rules.minerFatigueTrapDurationTicks,
                    rules.minerFatigueTrapAmplifier,
                    false,
                    false,
                    true
                ))
            }
            else -> return
        }
        val trapName = upgradeName(trap)
        val defenders = teamMembersOnline(teamId)
        val intruderTeam = playerStates[intruder.uniqueId]?.teamId?.let(teamStates::get)?.config
        val customArguments = arrayOf(
            intruder.name,
            trapName,
            intruderTeam?.displayName ?: "NULL",
            intruderTeam?.color?.let(::teamLegacyColor).orEmpty()
        )
        defenders.forEach { defender ->
            val customMessage = if (queuedTrap.customAnnounce) {
                trapCustomMessage(queuedTrap.productId, "message", *customArguments)
            } else null
            val customTitle = if (queuedTrap.customAnnounce) {
                trapCustomMessage(queuedTrap.productId, "title", *customArguments)
            } else null
            val customSubtitle = if (queuedTrap.customAnnounce) {
                trapCustomMessage(queuedTrap.productId, "subtitle", *customArguments)
            } else null
            defender.sendMessage(customMessage?.let(::trapCustomComponent) ?: Component.text(language.getMessage(
                "bedwars.trap_triggered", intruder.name, trapName
            )))
            defender.showTitle(Title.title(
                customTitle?.let(::trapCustomComponent) ?: Component.text(language.getMessage("bedwars.trap_title")),
                customSubtitle?.let(::trapCustomComponent)
                    ?: Component.text(language.getMessage("bedwars.trap_subtitle", intruder.name, trapName)),
                Title.Times.times(Duration.ofMillis(750), Duration.ofMillis(1750), Duration.ofMillis(500))
            ))
        }
        playSoundRule(defenders, queuedTrap.sound ?: rules.trapTriggerSound)
    }

    /** 读取商品 ID 对应的自定义陷阱反馈，缺少语言键时让调用方回退通用反馈。 */
    private fun trapCustomMessage(productId: String, part: String, vararg args: Any): String? {
        val key = "bedwars.trap_${productId.replace('-', '_')}_$part"
        return language.getMessage(key, *args).takeUnless { it == key }
    }

    /** 将自定义陷阱反馈中的 & 或 § 旧式颜色转换为 Adventure 组件。 */
    private fun trapCustomComponent(value: String): Component {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(value.replace('§', '&'))
    }

    /** 按配置顺序执行已消费陷阱的效果、移除效果和移除附魔动作。 */
    private fun executeTrapActions(
        teamId: String,
        team: BedWarsTeamState,
        intruder: Player,
        actions: List<BedWarsTrapAction>
    ) {
        val base = team.config.bed ?: team.config.spawn
        actions.forEach { action ->
            when (action) {
                is BedWarsTrapEffectAction -> {
                    val targets = when (action.target) {
                        BedWarsTrapEffectTarget.ENEMY -> listOf(intruder)
                        BedWarsTrapEffectTarget.TEAM -> teamMembersOnline(teamId)
                        BedWarsTrapEffectTarget.BASE -> teamMembersOnline(teamId).filter {
                            isActiveParticipant(it) && within(it.location, base, effectiveIslandRadius)
                        }
                    }
                    targets.forEach { target ->
                        target.addPotionEffect(
                            PotionEffect(action.effectType, action.durationTicks, action.amplifier, false, false, true),
                            true
                        )
                    }
                }
                is BedWarsTrapRemoveEffectAction -> {
                    if (action.effectType == PotionEffectType.INVISIBILITY) {
                        clearInvisibleAppearance(intruder, notify = false)
                    } else {
                        intruder.removePotionEffect(action.effectType)
                    }
                }
                is BedWarsTrapDisenchantAction -> disenchantTrapTarget(intruder, action)
            }
        }
    }

    /** 从入侵者参考物品类别移除陷阱配置的指定附魔。 */
    private fun disenchantTrapTarget(player: Player, action: BedWarsTrapDisenchantAction) {
        val inventoryItems = player.inventory.contents.filterNotNull()
        val items = when (action.target) {
            BedWarsUpgradeEnchantTarget.SWORD -> inventoryItems.filter { it.type.name.endsWith("_SWORD") }
            BedWarsUpgradeEnchantTarget.ARMOR -> (inventoryItems + player.inventory.armorContents.filterNotNull())
                .filter { armorSlot(it.type) != null }
            BedWarsUpgradeEnchantTarget.BOW -> inventoryItems.filter { it.type == Material.BOW }
        }
        items.forEach { it.removeEnchantment(action.enchantment) }
    }

    private fun isProtectedLocation(location: Location): Boolean {
        val configured = gameConfig ?: return true
        val rules = moduleConfig.blockRules
        val spawnRadius = configured.spawnProtectionRadius ?: rules.spawnProtectionRadius
        val shopRadius = configured.shopProtectionRadius ?: rules.shopProtectionRadius
        val upgradeShopRadius = configured.upgradeShopProtectionRadius ?: rules.shopProtectionRadius
        val generatorRadius = configured.generatorProtectionRadius ?: rules.generatorProtectionRadius
        configured.teams.forEach { team ->
            if (withinProtectionCuboid(location, team.spawn, spawnRadius)) return true
            val hasShopRegions = !effectiveDisableEmptyTeamNpcs || playerStates.values.any {
                it.teamId == team.id && it.participant
            }
            if (hasShopRegions && withinProtectionCuboid(location, team.shop, shopRadius, -1, 4)) return true
            if (hasShopRegions && withinProtectionCuboid(location, team.upgradeShop, upgradeShopRadius, -1, 4)) return true
            if (team.generators.any { withinProtectionCuboid(location, it.point, generatorRadius, -2, 5, 1.3) }) {
                return true
            }
        }
        return configured.generators.any {
            withinProtectionCuboid(location, it.point, generatorRadius, -2, 5, 1.3)
        }
    }

    /** 按参考 Cuboid 的方块坐标、垂直扩展和中心偏移判断关键点保护。 */
    private fun withinProtectionCuboid(
        location: Location,
        point: BedWarsPoint?,
        radius: Double,
        minYExtra: Int = 0,
        maxYExtra: Int = 0,
        centerYOffset: Double = 0.0
    ): Boolean {
        if (point == null) return false
        val safeRadius = radius.coerceIn(0.0, 32.0)
        val minX = kotlin.math.floor(point.x - safeRadius).toInt()
        val maxX = kotlin.math.floor(point.x + safeRadius).toInt()
        val minY = kotlin.math.floor(point.y + centerYOffset - safeRadius).toInt() + minYExtra
        val maxY = kotlin.math.floor(point.y + centerYOffset + safeRadius).toInt() + maxYExtra
        val minZ = kotlin.math.floor(point.z - safeRadius).toInt()
        val maxZ = kotlin.math.floor(point.z + safeRadius).toInt()
        return location.blockX in minX..maxX &&
            location.blockY in minY..maxY &&
            location.blockZ in minZ..maxZ
    }

    private fun within(location: Location, point: BedWarsPoint?, radius: Double): Boolean {
        if (point == null || radius <= 0.0) return false
        val dx = location.x - point.x
        val dy = location.y - point.y
        val dz = location.z - point.z
        return dx * dx + dy * dy + dz * dz <= radius * radius
    }

    private fun teamSpawn(teamId: String): Location? {
        val world = room.world ?: return null
        return teamStates[teamId]?.config?.spawn?.toLocation(world)
            ?: gameConfig?.teams?.firstOrNull { it.id == teamId }?.spawn?.toLocation(world)
    }

    private fun spectatorSpawn(): Location {
        val world = room.world ?: return Bukkit.getWorlds().first().spawnLocation
        return gameConfig?.spectatorSpawn?.toLocation(world) ?: world.spawnLocation
    }

    /** 在房间资源作用域中登记实体，确保关房时统一清理。 */
    private fun trackEntity(entity: Entity, ownerId: UUID? = null, type: String? = null) {
        trackedEntities.add(entity.uniqueId)
        resourceScope?.trackEntity(entity, ownerId, type)
    }

    /** 从 Session 与房间资源作用域同步释放已完成生命周期的实体。 */
    private fun releaseTrackedEntity(entityId: UUID) {
        if (!trackedEntities.remove(entityId)) return
        resourceScope?.releaseEntity(entityId)
    }

    /** 写入特殊道具实体标记，供投射物和爆炸事件识别。 */
    private fun markSpecial(entity: Entity, specialId: String) {
        entity.persistentDataContainer.set(specialItemKey, PersistentDataType.STRING, specialId)
    }

    /** 从玩家手中的一次性特殊物品安全扣除一个。 */
    private fun consumeOne(item: ItemStack) {
        item.amount = (item.amount - 1).coerceAtLeast(0)
    }

    /** 在取消原版放置事件时，从实际使用的主手或副手扣除一个道具。 */
    private fun consumeHeldItem(player: Player, hand: EquipmentSlot, source: ItemStack) {
        val remaining = source.clone().apply { amount = (amount - 1).coerceAtLeast(0) }
        val replacement = remaining.takeUnless { it.amount <= 0 }
        if (hand == EquipmentSlot.HAND) {
            player.inventory.setItemInMainHand(replacement)
        } else {
            player.inventory.setItemInOffHand(replacement)
        }
    }

    /** 按玩家朝向算法生成中空墙体、屋顶、垛口和内部梯子的弹出塔队列。 */
    private fun createPopupTower(player: Player, origin: Location): BedWarsTowerState {
        val state = playerStates.getValue(player.uniqueId)
        val rules = moduleConfig.specials
        val radius = rules.towerRadius
        val wallHeight = rules.towerWallHeight
        val forward = yawFace(player.location.yaw)
        val placements = ArrayDeque<BedWarsTowerPlacement>()

        fun add(localX: Int, y: Int, localZ: Int, ladder: Boolean = false) {
            val (offsetX, offsetZ) = rotateTowerOffset(localX, localZ, forward)
            placements += BedWarsTowerPlacement(offsetX, y, offsetZ, if (ladder) forward else null)
        }

        for (y in 0 until wallHeight) {
            for (localX in (-radius + 1) until radius) {
                add(localX, y, -radius + 1)
                if (localX != 0 || y >= 3) add(localX, y, radius)
            }
            for (localZ in 0 until radius) {
                add(-radius, y, localZ)
                add(radius, y, localZ)
            }
        }
        for (localX in -radius..radius) {
            for (localZ in (-radius + 1)..radius) {
                if (localX == 0 && localZ == 0) continue
                add(localX, wallHeight, localZ)
            }
        }
        for (sideX in listOf(-radius - 1, radius + 1)) {
            for (localZ in (-radius + 1)..radius) {
                if (localZ == -radius + 1 || localZ == radius) {
                    for (y in wallHeight..wallHeight + 2) add(sideX, y, localZ)
                } else {
                    add(sideX, wallHeight + 1, localZ)
                }
            }
        }
        for (sideZ in listOf(-radius, radius + 1)) {
            for (localX in -radius..radius) {
                if (localX == -radius || localX == 0 || localX == radius) {
                    for (y in wallHeight..wallHeight + 2) add(localX, y, sideZ)
                } else {
                    add(localX, wallHeight + 1, sideZ)
                }
            }
        }
        for (y in 0..wallHeight) add(0, y, 0, ladder = true)
        return BedWarsTowerState(player.uniqueId, state.teamId, origin.clone(), placements)
    }

    /** 把塔的本地前/右坐标旋转到玩家面向的世界坐标。 */
    private fun rotateTowerOffset(localX: Int, localZ: Int, forward: BlockFace): Pair<Int, Int> {
        val rightX = forward.modZ
        val rightZ = -forward.modX
        return rightX * localX + forward.modX * localZ to rightZ * localX + forward.modZ * localZ
    }

    /** 将玩家 yaw 归一化为弹出塔入口朝向。 */
    private fun yawFace(yaw: Float): BlockFace {
        val normalized = ((yaw % 360f) + 360f) % 360f
        return when {
            normalized < 45f || normalized >= 315f -> BlockFace.SOUTH
            normalized < 135f -> BlockFace.WEST
            normalized < 225f -> BlockFace.NORTH
            else -> BlockFace.EAST
        }
    }

    /** 根据商品类型生成床虫或梦境守卫，并记录队伍和有效期。 */
    private fun spawnSpecialMob(location: Location, owner: Player, teamId: String, specialId: String) {
        val rules = moduleConfig.specials
        val mob: Mob
        val duration: Int
        val health: Double
        val damage: Double
        val speed: Double
        when (specialId) {
            "bed-bug" -> {
                mob = location.world.spawn(location, Silverfish::class.java)
                duration = rules.bedBugDurationTicks
                health = rules.bedBugHealth
                damage = rules.bedBugDamage
                speed = rules.bedBugSpeed
            }
            "dream-defender" -> {
                mob = location.world.spawn(location, IronGolem::class.java) { it.isPlayerCreated = false }
                duration = rules.dreamDefenderDurationTicks
                health = rules.dreamDefenderHealth
                damage = rules.dreamDefenderDamage
                speed = rules.dreamDefenderSpeed
            }
            else -> return
        }
        mob.removeWhenFarAway = false
        mob.isPersistent = true
        mob.getAttribute(Attribute.MAX_HEALTH)?.baseValue = health
        mob.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = speed
        mob.health = health.coerceAtMost(mob.getAttribute(Attribute.MAX_HEALTH)?.value ?: health)
        markSpecial(mob, specialId)
        val mobState = BedWarsSpecialMobState(
            owner.uniqueId,
            teamId,
            specialId,
            gameElapsedTicks + duration,
            damage
        )
        specialMobs[mob.uniqueId] = mobState
        updateSpecialMobName(mob, mobState)
        trackEntity(mob, owner.uniqueId, specialId)
        Bukkit.getPluginManager().callEvent(GameSummonSpawnedEvent(room, owner, teamId, specialId, mob))
    }

    /** 刷新召唤物的队伍名称、剩余秒数和十段生命条。 */
    private fun updateSpecialMobName(mob: Mob, state: BedWarsSpecialMobState) {
        val maxHealth = mob.getAttribute(Attribute.MAX_HEALTH)?.value?.coerceAtLeast(1.0) ?: 1.0
        val filled = kotlin.math.ceil(mob.health.coerceAtLeast(0.0) / maxHealth * 10.0).toInt().coerceIn(0, 10)
        val healthBar = "❤".repeat(filled) + "♡".repeat(10 - filled)
        val secondsLeft = kotlin.math.ceil((state.expiresAtTick - gameElapsedTicks).coerceAtLeast(0) / 20.0).toInt()
        val teamName = teamStates[state.teamId]?.config?.displayName ?: state.teamId
        val key = if (state.specialId == "dream-defender") {
            "bedwars.dream_defender_entity_name"
        } else {
            "bedwars.bed_bug_entity_name"
        }
        mob.customName(Component.text(language.getMessage(key, teamName, secondsLeft, healthBar)))
        mob.isCustomNameVisible = true
    }

    /** 对火球爆点附近玩家按敌我关系施加配置伤害、击杀归因和击退。 */
    private fun applyFireballEffects(origin: Location, source: Player?) {
        val rules = moduleConfig.specials
        val radius = rules.fireballYield.toDouble()
        val sourceState = source?.let { playerStates[it.uniqueId] }
        room.players.mapNotNull(Bukkit::getPlayer)
            .filter(::isActiveParticipant)
            .filter { player ->
                val location = player.location
                location.world == origin.world &&
                    kotlin.math.abs(location.x - origin.x) <= radius &&
                    kotlin.math.abs(location.y - origin.y) <= radius &&
                    kotlin.math.abs(location.z - origin.z) <= radius
            }
            .forEach { player ->
                val targetState = playerStates[player.uniqueId] ?: return@forEach
                val selfDamage = source?.uniqueId == player.uniqueId
                val teammateDamage = !selfDamage && sourceState?.teamId == targetState.teamId
                val damage = when {
                    sourceState == null -> 0.0
                    selfDamage -> rules.fireballDamageSelf
                    teammateDamage -> rules.fireballDamageTeammates
                    else -> rules.fireballDamageEnemies
                }
                if (damage > 0.0 && !isRespawnProtected(targetState)) {
                    if (!selfDamage && !teammateDamage && source != null) {
                        sourceState?.respawnProtectionUntilTick = 0
                        recordCombatHit(player, source)
                    }
                    pendingDeathCauses[player.uniqueId] = EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    player.damage(damage)
                    pendingDeathCauses.remove(player.uniqueId)
                }
                val away = player.location.toVector().subtract(origin.toVector())
                if (away.lengthSquared() <= 0.0001) return@forEach
                away.normalize()
                var verticalFactor = -away.y
                if (verticalFactor < 0.0) verticalFactor += 1.5
                val vertical = if (verticalFactor <= 0.5) {
                    rules.fireballVerticalKnockback * 1.5
                } else {
                    verticalFactor * rules.fireballVerticalKnockback * 1.5
                }
                player.velocity = away.multiply(rules.fireballHorizontalKnockback).setY(vertical)
            }
    }

    private fun resetPlayer(player: Player, gameMode: GameMode, clearInventory: Boolean) {
        player.gameMode = gameMode
        player.isInvulnerable = false
        player.allowFlight = false
        player.isFlying = false
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f
        player.setGravity(true)
        player.isCollidable = true
        player.collidableExemptions.clear()
        player.isGliding = false
        player.isSneaking = false
        player.isSprinting = false
        if (clearInventory) player.inventory.clear()
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        player.foodLevel = 20
        player.saturation = 20f
        player.level = 0
        player.exp = 0f
        player.totalExperience = 0
        player.healthScale = 20.0
        player.absorptionAmount = 0.0
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)
        maxHealth?.baseValue = 20.0
        player.fireTicks = 0
        player.freezeTicks = 0
        player.remainingAir = player.maximumAir
        player.arrowsInBody = 0
        player.beeStingersInBody = 0
        player.fallDistance = 0f
        player.velocity = Vector()
        player.health = 20.0.coerceAtMost(maxHealth?.value ?: 20.0)
    }

    /** 按模块配置向等待大厅玩家发放槽位固定的命令快捷物品。 */
    private fun givePreGameItems(player: Player) {
        moduleConfig.preGameItems.forEach { configured ->
            val item = ItemStack(configured.material)
            val meta = item.itemMeta
            val key = "bedwars.pre_game_item_${configured.id.replace('-', '_')}"
            val translated = language.getMessage(key)
            meta.displayName(Component.text(if (translated == key) configured.id else translated))
            meta.persistentDataContainer.set(preGameCommandKey, PersistentDataType.STRING, configured.command)
            if (configured.enchanted) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }
            item.itemMeta = meta
            player.inventory.setItem(configured.slot, item)
        }
    }

    private fun enterEliminatedSpectator(player: Player) {
        spectatorService.enterEliminated(
            player,
            room,
            moduleConfig.toSpectatorPolicy(
                language,
                enabled = true,
                mode = SpectatorMode.MANAGED
            ),
            spectatorSpawn()
        )
    }

    /** 同步服务端碰撞例外，并返回对应的客户端 Teams 碰撞规则。 */
    private fun updatePlayerCollision(player: Player, teamId: String): NametagCollisionRule {
        val state = playerStates[player.uniqueId]
        val active = phase == BedWarsPhase.RUNNING && state?.participant == true &&
            !state.eliminated && !state.respawning && !state.disconnected
        player.collidableExemptions.clear()
        player.isCollidable = active
        if (!active) return NametagCollisionRule.NEVER
        room.players
            .asSequence()
            .filter { playerId ->
                playerId != player.uniqueId &&
                    playerStates[playerId]?.teamId?.equals(teamId, ignoreCase = true) == true
            }
            .forEach(player.collidableExemptions::add)
        return NametagCollisionRule.PUSH_OTHER_TEAMS
    }

    private fun setTeamNametag(player: Player, teamId: String) {
        val team = gameConfig?.teams?.firstOrNull { it.id == teamId } ?: return
        nametagService.set(
            room,
            player,
            PlayerNametag(
                prefix = Component.text("[${team.displayName}] ", team.color.textColor),
                color = team.color.textColor,
                collisionRule = updatePlayerCollision(player, teamId)
            )
        )
    }

    private fun directPlayer(event: EntityDamageByEntityEvent): Player? {
        return when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            is TNTPrimed -> damager.source as? Player
            else -> null
        }
    }

    /** 记录一次通过队伍校验的敌方攻击，供虚空、摔落和爆炸死亡追溯。 */
    private fun recordCombatHit(victim: Player, attacker: Player) {
        lastSpecialMobHits.remove(victim.uniqueId)
        lastCombatHits[victim.uniqueId] = BedWarsLastHitState(attacker.uniqueId, gameElapsedTicks)
    }

    /** 返回仍在 15 秒窗口内的最近召唤物攻击。 */
    private fun recentSpecialMobHit(victim: Player): BedWarsSpecialMobHitState? {
        val hit = lastSpecialMobHits[victim.uniqueId] ?: return null
        return hit.takeIf { gameElapsedTicks - it.gameTick in 0..LAST_HIT_WINDOW_TICKS }
    }

    /** 仅在最后一次实体伤害确实来自已登记召唤物时返回专属死亡来源。 */
    private fun directSpecialMobDeathHit(
        victim: Player,
        damageCause: EntityDamageEvent.DamageCause?
    ): BedWarsSpecialMobHitState? {
        if (damageCause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return null
        val damageEvent = victim.lastDamageCause as? EntityDamageByEntityEvent ?: return null
        val specialMob = specialMobs[damageEvent.damager.uniqueId] ?: return null
        val hit = recentSpecialMobHit(victim) ?: return null
        return hit.takeIf { it.teamId == specialMob.teamId && it.specialId == specialMob.specialId }
    }

    /** 判断玩家是否在参考 10 秒击落窗口内因摔落死亡，以便四类资源留在死亡位置。 */
    private fun isRecentKnockbackFall(victim: Player): Boolean {
        if (resolvedDeathDamageCause(victim) != EntityDamageEvent.DamageCause.FALL) return false
        val playerHitTick = lastCombatHits[victim.uniqueId]?.gameTick
        val specialHitTick = lastSpecialMobHits[victim.uniqueId]?.gameTick
        val latestHitTick = listOfNotNull(playerHitTick, specialHitTick).maxOrNull() ?: return false
        return gameElapsedTicks - latestHitTick in 0..PLAYER_PUSH_WINDOW_TICKS
    }

    /** 按召唤物类型和是否最终淘汰选择本地化死亡消息。 */
    private fun specialMobDeathKey(specialId: String, finalKill: Boolean): String {
        val suffix = if (finalKill) "final" else "regular"
        return "bedwars.player_killed_by_${specialId.replace('-', '_')}_$suffix"
    }

    /** 校验候选击杀者仍属于本房间参赛敌队。 */
    private fun validKiller(candidate: Player?, victim: Player, victimState: BedWarsPlayerState): Player? {
        candidate ?: return null
        if (candidate.uniqueId == victim.uniqueId || candidate.uniqueId !in room.players) return null
        val candidateState = playerStates[candidate.uniqueId] ?: return null
        if (!candidateState.participant || candidateState.teamId == victimState.teamId) return null
        return candidate
    }

    /** 把死亡掉落中的铁、金、钻石和绿宝石转入击杀者背包，满背包余量仍原地掉落。 */
    private fun transferDeathResources(event: PlayerDeathEvent, killer: Player, victim: Player) {
        val resources = event.drops.filter { it.type in DEATH_TRANSFER_RESOURCES }.map(ItemStack::clone)
        event.drops.clear()
        if (resources.isEmpty()) return
        val received = linkedMapOf<Material, Int>()
        resources.forEach { item ->
            val amount = item.amount
            val leftovers = killer.inventory.addItem(item).values
            val leftoverAmount = leftovers.sumOf(ItemStack::getAmount)
            if (amount > leftoverAmount) received.merge(item.type, amount - leftoverAmount, Int::plus)
            event.drops.addAll(leftovers.map(ItemStack::clone))
        }
        received.forEach { (material, amount) ->
            killer.sendMessage(Component.text(language.getMessage(
                "bedwars.resource_loot",
                resourceName(material),
                amount,
                victim.name
            )))
        }
    }

    /** 把最终淘汰的可回收背包物和末影箱内容投放到受害队基地回收点。 */
    private fun recoverFinalDeathDrops(event: PlayerDeathEvent, victim: Player, state: BedWarsPlayerState) {
        val team = teamStates[state.teamId]?.config ?: return
        val world = room.world ?: return
        val location = team.killDrops?.toLocation(world)
            ?: team.generators.firstOrNull { it.type == BedWarsGeneratorType.IRON }?.point?.toLocation(world)
            ?: team.generators.firstOrNull()?.point?.toLocation(world)
            ?: team.spawn?.toLocation(world)
            ?: victim.location
        val recovered = buildList {
            addAll(event.drops.filter(::isRecoverableFinalDrop).map(ItemStack::clone))
            addAll(victim.enderChest.contents.filterNotNull().map(ItemStack::clone))
        }
        event.drops.clear()
        victim.enderChest.clear()
        recovered.forEach { item ->
            val dropped = world.dropItemNaturally(location, item)
            dropped.pickupDelay = 0
            trackEntity(dropped, type = "final-kill-drop")
        }
    }

    /** 判断最终击杀时应送回基地的普通方块、资源和消耗品。 */
    private fun isRecoverableFinalDrop(item: ItemStack): Boolean {
        if (item.type.isAir) return false
        val name = item.type.name
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
            name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.endsWith("_SWORD") ||
            name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
        ) return false
        return item.type != Material.BOW && item.type != Material.CROSSBOW && item.type != Material.SHEARS
    }

    /** 在重连宽限到期后把 PvP 断线玩家携带的四类商店货币投放回断线点。 */
    private fun dropDisconnectedResources(disconnected: BedWarsDisconnectState) {
        val world = room.world ?: return
        val location = disconnected.location.clone().apply { this.world = world }
        disconnected.resources.forEach { item ->
            val dropped = world.dropItemNaturally(location, item.clone())
            dropped.pickupDelay = 0
            trackEntity(dropped, type = "disconnect-drop")
        }
    }

    /** 保存各阶段和身份对应的本地化 Tab 头尾及 Sidebar 行键。 */
    private enum class BedWarsTabHeaderFooterTemplate(
        val headerKey: String,
        val footerKey: String,
        val sidebarLinesKey: String
    ) {
        WAITING_PLAYER(
            "bedwars.tab_waiting_player_header",
            "bedwars.tab_waiting_player_footer",
            "bedwars.sidebar_waiting_player"
        ),
        WAITING_SPECTATOR(
            "bedwars.tab_waiting_spectator_header",
            "bedwars.tab_waiting_spectator_footer",
            "bedwars.sidebar_waiting_spectator"
        ),
        COUNTDOWN_PLAYER(
            "bedwars.tab_countdown_player_header",
            "bedwars.tab_countdown_player_footer",
            "bedwars.sidebar_countdown_player"
        ),
        COUNTDOWN_SPECTATOR(
            "bedwars.tab_countdown_spectator_header",
            "bedwars.tab_countdown_spectator_footer",
            "bedwars.sidebar_countdown_spectator"
        ),
        RUNNING_PLAYER(
            "bedwars.tab_running_player_header",
            "bedwars.tab_running_player_footer",
            "bedwars.sidebar_running_player"
        ),
        RUNNING_ELIMINATED(
            "bedwars.tab_running_eliminated_header",
            "bedwars.tab_running_eliminated_footer",
            "bedwars.sidebar_running_eliminated"
        ),
        RUNNING_SPECTATOR(
            "bedwars.tab_running_spectator_header",
            "bedwars.tab_running_spectator_footer",
            "bedwars.sidebar_running_spectator"
        ),
        RESULT_WINNER_ALIVE(
            "bedwars.tab_result_winner_alive_header",
            "bedwars.tab_result_winner_alive_footer",
            "bedwars.sidebar_result_winner_alive"
        ),
        RESULT_WINNER_ELIMINATED(
            "bedwars.tab_result_winner_eliminated_header",
            "bedwars.tab_result_winner_eliminated_footer",
            "bedwars.sidebar_result_winner_eliminated"
        ),
        RESULT_LOSER(
            "bedwars.tab_result_loser_header",
            "bedwars.tab_result_loser_footer",
            "bedwars.sidebar_result_loser"
        ),
        RESULT_SPECTATOR(
            "bedwars.tab_result_spectator_header",
            "bedwars.tab_result_spectator_footer",
            "bedwars.sidebar_result_spectator"
        ),
        RESULT_DRAW(
            "bedwars.tab_result_draw_header",
            "bedwars.tab_result_draw_footer",
            "bedwars.sidebar_result_draw"
        )
    }

    private companion object {
        const val QUICK_BUY_VIEW = "quick"
        const val ALL_ITEMS_VIEW = "all"
        const val TRAPS_VIEW = "traps"
        const val LAST_HIT_WINDOW_TICKS = 15 * 20
        const val PLAYER_PUSH_WINDOW_TICKS = 10 * 20
        const val POTION_BOTTLE_CLEANUP_DELAY_TICKS = 5L
        const val BUCKET_CLEANUP_DELAY_TICKS = 3L
        const val HEAL_POOL_PARTICLE_INTERVAL_TICKS = 80
        const val LEVEL_EXPERIENCE_INTERVAL_TICKS = 60 * 20
        const val HALLOWEEN_COBWEB_LIFETIME_TICKS = 150
        const val HALLOWEEN_COBWEB_EXPERIENCE = 5
        const val EXPLOSION_RAY_OFFSET = 0.73
        const val EXPLOSION_RAY_STEP = 0.3
        const val EXPLOSION_TOTAL_RAYS = 27
        const val EXPLOSION_BLOCKED_RAY_THRESHOLD = 22
        const val HEAL_POOL_PARTICLE_SAMPLES = 48
        const val BED_HOLOGRAM_HIDE_DISTANCE_SQUARED = 16.0
        const val NON_PLAYING_VOID_Y = 0.0
        const val GENERATOR_RESOURCE_TAG = "kgc_bedwars_resource"
        const val METRIC_FINAL_KILLS = "final-kills"
        const val METRIC_FINAL_DEATHS = "final-deaths"
        const val METRIC_BEDS_DESTROYED = "beds-destroyed"
        const val METRIC_LEVEL_EXPERIENCE = "level-experience"
        const val PERMISSION_SHOUT = "kagamecenter.bedwars.shout"
        const val PERMISSION_SHOUT_BYPASS = "kagamecenter.bedwars.shout.bypass"
        const val MAX_RECONNECT_RESPAWN_TICKS = 72_000L
        const val TAB_ACTIVE_PLAYER_ORDER = 2000
        const val TAB_TEAM_ORDER_STRIDE = 100
        const val XP_SOURCE_PER_MINUTE = "per-minute"
        const val XP_SOURCE_PER_TEAMMATE = "per-teammate"
        const val XP_SOURCE_GAME_WIN = "game-win"
        const val XP_SOURCE_BED_DESTROYED = "bed-destroyed"
        const val XP_SOURCE_REGULAR_KILL = "regular-kill"
        const val XP_SOURCE_FINAL_KILL = "final-kill"
        const val XP_SOURCE_HALLOWEEN_COBWEB = "halloween-cobweb"
        val EXPLOSION_RAY_OFFSETS = doubleArrayOf(-EXPLOSION_RAY_OFFSET, 0.0, EXPLOSION_RAY_OFFSET)
        val ITEM_USE_ACTIONS = setOf(
            Action.LEFT_CLICK_AIR,
            Action.LEFT_CLICK_BLOCK,
            Action.RIGHT_CLICK_AIR,
            Action.RIGHT_CLICK_BLOCK
        )
        val RIGHT_CLICK_ACTIONS = setOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK)
        val TNT_CARRIER_DUST = Particle.DustOptions(Color.RED, 1.0f)
        val NON_ACTIVE_WORKSTATIONS = setOf(
            Material.CRAFTING_TABLE,
            Material.ENCHANTING_TABLE,
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL,
            Material.SMITHING_TABLE,
            Material.CARTOGRAPHY_TABLE,
            Material.GRINDSTONE,
            Material.LOOM,
            Material.STONECUTTER
        )
        val SHOVEL_TRANSFORM_BLOCKS = setOf(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.PODZOL,
            Material.MYCELIUM,
            Material.ROOTED_DIRT
        )
        val HOE_TRANSFORM_BLOCKS = setOf(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.DIRT_PATH,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT
        )
        val FORBIDDEN_PARTY_ARGUMENTS = setOf(
            listOf("home"),
            listOf("sethome")
        )
        val DEATH_TRANSFER_RESOURCES = setOf(
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.DIAMOND,
            Material.EMERALD
        )
    }
}
