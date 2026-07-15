package org.katacr.kagamecenter.hunger

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.block.Container
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.katacr.kaGameCenter.broadcast.RoomBroadcastService
import org.katacr.kaGameCenter.display.SidebarBoardRenderer
import org.katacr.kaGameCenter.elimination.PlayerEliminationService
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.game.GameState
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.nametag.NametagCollisionRule
import org.katacr.kaGameCenter.nametag.PlayerNametag
import org.katacr.kaGameCenter.nametag.PlayerNametagService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.phase.GamePhaseTimer
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.reward.WeightedPool
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.resource.RoomResourceScopeService
import org.katacr.kaGameCenter.spawn.SpawnAssignmentService
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/** 运行一间经典饥饿游戏房间，并独占该房间的全部玩法状态。 */
class HungerGameSession(
    override val room: GameRoom,
    private val configService: HungerConfigService,
    private val worldService: TemporaryWorldService,
    private val language: ModuleLanguage,
    private val packetService: PacketDispatchService,
    private val roomManager: GameRoomManager,
    private val resultService: GameResultService,
    private val playerRuntimeStateService: PlayerRuntimeStateService,
    private val roomBroadcastService: RoomBroadcastService,
    private val nametagService: PlayerNametagService,
    private val eliminationService: PlayerEliminationService,
    roomResourceScopeService: RoomResourceScopeService,
    private val spawnAssignmentService: SpawnAssignmentService
) : GameSession {
    override fun usesCustomScoreboard(): Boolean = true
    override fun usesCustomActionBar(): Boolean = true

    private val states = linkedMapOf<UUID, HungerPlayerState>()
    private val participants = linkedSetOf<UUID>()
    private val supplyChests = linkedMapOf<HungerLocationKey, Inventory>()
    private val configuredSupplyKeys = linkedSetOf<HungerLocationKey>()
    private val resources = roomResourceScopeService.open(room.id)
    private val phaseTimer = GamePhaseTimer()
    private val refillTimer = GamePhaseTimer()
    private var config: HungerConfig = configService.current()
    private var gameConfig: HungerGameConfig? = null
    private var phase = HungerPhase.WAITING
    private var refillDone = false
    private var forceDeathmatchTriggered = false
    private var resultRecorded = false
    private var displayTicks = 0
    private var winnerId: UUID? = null
    private var closed = false

    override fun onPrepare() {
        config = configService.reload()
        gameConfig = room.configuredGame?.let(configService::readManagedGame)
        val worldName = "kgc_${room.id}"
        val template = room.mapTemplate ?: room.definition?.mapTemplates?.firstOrNull() ?: config.firstMap()?.template
        room.world = room.templateDirectory?.let {
            worldService.createRoomWorldFromDirectory(it, worldName, allowFlatFallback = false)
        } ?: worldService.createRoomWorldFromTemplate(template, worldName, allowFlatFallback = false)
        room.world?.let { world ->
            val spawn = gameConfig?.lobby?.toLocation(world)
                ?: gameConfig?.tributeSpawns?.firstOrNull()?.point?.toLocation(world)
                ?: worldService.readTemplateSpawn(template, world)
            world.spawnLocation = spawn
            eliminationService.enableImmediateRespawn(world)
            prepareConfiguredSupplyChests()
        }
    }

    override fun onPlayerJoin(player: Player) {
        val world = room.world ?: return
        resources.trackViewer(player.uniqueId)
        playerRuntimeStateService.captureIfAbsent(room.id, player)
        val lateJoin = phase != HungerPhase.WAITING
        val state = states.getOrPut(player.uniqueId) {
            HungerPlayerState(participant = !lateJoin)
        }
        state.originalMaxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: 20.0
        state.participant = !lateJoin
        state.alive = !lateJoin
        resetPlayer(player, clearInventory = true)
        if (lateJoin) {
            enterEliminatedSpectator(player)
            player.sendMessage(Component.text(language.getMessage("hunger.late_spectator")))
            return
        }
        participants.add(player.uniqueId)
        player.teleport(gameConfig?.lobby?.toLocation(world) ?: world.spawnLocation)
        player.sendMessage(Component.text(language.getMessage("hunger.joined", room.id)))
    }

    override fun onPlayerLeave(player: Player) {
        val state = states[player.uniqueId]
        if (phase == HungerPhase.WAITING) {
            states.remove(player.uniqueId)
            participants.remove(player.uniqueId)
        } else if (state?.alive == true) {
            eliminate(player, killerId = null, recordDeath = true, enterSpectator = false)
        }
        nametagService.clear(room, player)
        packetService.clearViewer(player)
        restorePlayer(player, state)
        if (isCombatPhase()) checkWinOrForceDeathmatch()
    }

    override fun onSpectatorJoin(player: Player) {
        resources.trackViewer(player.uniqueId)
        room.world?.let { player.teleport(spectatorLocation(it)) }
        nametagService.refreshViewer(room, player)
    }

    override fun onSpectatorLeave(player: Player) {
        packetService.clearViewer(player)
        nametagService.clearViewer(player)
    }

    override fun onStart() {
        val world = room.world ?: return
        val game = gameConfig ?: return failConfiguration()
        val players = room.players.mapNotNull(Bukkit::getPlayer)
        val requiredDeathmatchSpawns = minOf(config.forceDeathmatchPlayers, players.size)
        if (game.lobby == null || game.spectatorSpawn == null || game.playRegion == null ||
            game.tributeSpawns.size < players.size || game.deathmatchSpawns.size < requiredDeathmatchSpawns
        ) {
            failConfiguration()
            return
        }

        participants.clear()
        participants.addAll(room.players)
        supplyChests.clear()
        refillDone = false
        forceDeathmatchTriggered = false
        resultRecorded = false
        winnerId = null
        val assignments = spawnAssignmentService.assign(players, game.tributeSpawns) ?: return failConfiguration()
        assignments.forEach { assignment ->
            val player = assignment.participant
            val spawn = assignment.spawn.point.toLocation(world)
            val state = states.getOrPut(player.uniqueId) { HungerPlayerState() }
            state.alive = true
            state.participant = true
            state.kills = 0
            state.chestsOpened = 0
            state.openedChests.clear()
            state.frozenLocation = spawn.clone()
            state.eliminatedAtMillis = 0L
            resetPlayer(player, clearInventory = true)
            player.teleport(spawn)
            setTributeNametag(player, state)
        }
        phase = HungerPhase.COUNTDOWN
        phaseTimer.resetSeconds(config.countdownSeconds)
        room.state = GameState.COUNTDOWN
        roomBroadcastService.localized(room, language, "hunger.countdown_started", config.countdownSeconds, includeSpectators = true)
        updateDisplays()
    }

    override fun onTick() {
        when (phase) {
            HungerPhase.WAITING -> Unit
            HungerPhase.COUNTDOWN -> tickCountdown()
            HungerPhase.PROTECTION -> tickProtection()
            HungerPhase.RUNNING -> tickRunning()
            HungerPhase.DEATHMATCH -> tickDeathmatch()
            HungerPhase.RESULT -> tickResult()
            HungerPhase.CLOSING -> tickClosing()
        }
        if (phase != HungerPhase.WAITING) {
            displayTicks++
            if (displayTicks >= 20) {
                displayTicks = 0
                updateDisplays()
            }
        }
    }

    override fun onPlayerDeath(player: Player) {
        if (!isCombatPhase()) return
        states[player.uniqueId]?.let { state ->
            if (!state.alive) return
            state.alive = false
            state.eliminatedAtMillis = System.currentTimeMillis()
            state.frozenLocation = null
        }
        nametagService.clear(room, player)
        enterEliminatedSpectator(player)
        broadcastElimination(player, player.killer?.uniqueId)
        checkWinOrForceDeathmatch()
    }

    override fun onPlayerKill(killer: Player, victim: Player) {
        val state = states[killer.uniqueId] ?: return
        if (!state.alive) return
        state.kills++
        setTributeNametag(killer, state)
    }

    override fun onEnd() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            packetService.clearViewer(player)
            nametagService.clear(room, player)
            restorePlayer(player, states[player.uniqueId])
            player.sendMessage(Component.text(language.getMessage("hunger.ended")))
        }
        room.spectators.mapNotNull(Bukkit::getPlayer).forEach { spectator ->
            packetService.clearViewer(spectator)
            nametagService.clearViewer(spectator)
        }
    }

    override fun onClose() {
        supplyChests.clear()
        configuredSupplyKeys.clear()
        playerRuntimeStateService.clearRoom(room.id)
        states.clear()
        participants.clear()
    }

    /** 判断玩家当前受到的任意伤害是否应被经典阶段规则取消。 */
    fun handleDamage(event: EntityDamageEvent): Boolean {
        val player = event.entity as? Player ?: return false
        val state = states[player.uniqueId] ?: return false
        if (!state.alive) return true
        return phase != HungerPhase.RUNNING && phase != HungerPhase.DEATHMATCH
    }

    /** 判断玩家或其投射物造成的 PVP 是否来自本房间存活贡品。 */
    fun handleDamageByEntity(event: EntityDamageByEntityEvent): Boolean {
        val victim = event.entity as? Player ?: return false
        if (states[victim.uniqueId]?.alive != true) return true
        val attacker = directPlayer(event.damager) ?: return false
        if (roomManager.getPlayerRoom(attacker)?.id != room.id) return true
        if (states[attacker.uniqueId]?.alive != true) return true
        return phase != HungerPhase.RUNNING && phase != HungerPhase.DEATHMATCH
    }

    /** 锁定贡品台位置、限制游戏区域并处理淘汰高度。 */
    fun handleMove(event: PlayerMoveEvent): Boolean {
        val player = event.player
        val state = states[player.uniqueId] ?: return false
        val to = event.to
        state.frozenLocation?.let { frozen ->
            if (event.from.x != to.x || event.from.y != to.y || event.from.z != to.z) {
                event.to = frozen.clone().apply {
                    yaw = to.yaw
                    pitch = to.pitch
                }
            }
            return false
        }
        if (!state.alive || !isCombatPhase()) return false
        if (to.y <= (gameConfig?.voidY ?: config.defaultVoidY)) {
            player.health = 0.0
            return false
        }
        val region = gameConfig?.playRegion ?: return false
        return !region.contains(to, ignoreWorld = true)
    }

    /** 打开共享补给箱或使用可选的经典玩家追踪指南针。 */
    fun handleInteract(event: PlayerInteractEvent): Boolean {
        val player = event.player
        val state = states[player.uniqueId] ?: return false
        if (!state.alive) return true
        if (event.action.isRightClick && event.item?.type == Material.COMPASS && config.tracker.enabled) {
            trackNearestTribute(player)
            return true
        }
        val block = event.clickedBlock ?: return false
        if (block.type !in config.loot.containerMaterials) return false
        if (phase != HungerPhase.PROTECTION && phase != HungerPhase.RUNNING && phase != HungerPhase.DEATHMATCH) return true
        val key = HungerLocationKey.from(block.location) ?: return true
        val inRegion = gameConfig?.playRegion?.contains(block.location, ignoreWorld = true) == true
        if (!inRegion && key !in configuredSupplyKeys) return true
        val inventory = supplyChests.getOrPut(key) { generateSupplyInventory() }
        if (state.openedChests.add(key)) state.chestsOpened++
        player.openInventory(inventory)
        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 1f, 1f)
        return true
    }

    /** 按阶段和模块白名单决定方块是否可被破坏。 */
    fun handleBlockBreak(event: BlockBreakEvent): Boolean {
        if (!canModifyBlocks(event.player)) return true
        return event.block.type !in config.blocks.breakAllowed
    }

    /** 按白名单放置方块，并把允许的 TNT 自动转换为带归属的点燃实体。 */
    fun handleBlockPlace(event: BlockPlaceEvent): Boolean {
        val player = event.player
        if (!canModifyBlocks(player)) return true
        if (event.block.type !in config.blocks.placeAllowed) return true
        if (event.block.type == Material.TNT && config.blocks.autoPrimeTnt) {
            event.block.type = Material.AIR
            val tnt = event.block.world.spawn(event.block.location.add(0.5, 0.0, 0.5), TNTPrimed::class.java) {
                it.fuseTicks = config.blocks.tntFuseTicks
                it.source = player
            }
            resources.trackEntity(tnt, player.uniqueId, "hunger_tnt")
        }
        return false
    }

    /** 处理房间爆炸的地图破坏策略并清理实体归属。 */
    fun handleExplosion(event: EntityExplodeEvent) {
        if (!config.blocks.explosionBlockDamage) event.blockList().clear()
        resources.releaseEntity(event.entity.uniqueId)
    }

    /** 决定当前阶段是否允许玩家饥饿值变化。 */
    fun allowsHunger(player: Player): Boolean {
        return states[player.uniqueId]?.alive == true &&
            (phase == HungerPhase.RUNNING || phase == HungerPhase.DEATHMATCH)
    }

    /** 判断模块是否允许该房间中的火焰继续蔓延。 */
    fun allowsFireSpread(): Boolean = config.blocks.fireSpread

    /** 判断当前房间是否允许自然怪物生成。 */
    fun allowsMonsterSpawns(): Boolean = config.allowMonsterSpawns

    /** 判断当前玩家是否仍可拾取、丢弃和操作局内物品。 */
    fun isAliveParticipant(player: Player): Boolean = states[player.uniqueId]?.alive == true

    private fun tickCountdown() {
        announceTimer("hunger.countdown", phaseTimer.secondsLeft)
        if (!phaseTimer.tick()) return
        states.forEach { (playerId, state) ->
            state.frozenLocation = null
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        }
        phase = HungerPhase.PROTECTION
        phaseTimer.resetSeconds(config.protectionSeconds)
        room.state = GameState.RUNNING
        roomBroadcastService.localized(room, language, "hunger.protection_started", config.protectionSeconds, includeSpectators = true)
    }

    private fun tickProtection() {
        announceTimer("hunger.protection", phaseTimer.secondsLeft)
        if (!phaseTimer.tick()) return
        startRunning()
    }

    private fun tickRunning() {
        announceTimer("hunger.game", phaseTimer.secondsLeft)
        if (!refillDone && refillTimer.active && refillTimer.tick()) {
            refillDone = true
            refillSupplyChests()
            roomBroadcastService.localized(room, language, "hunger.refilled", includeSpectators = true)
            roomBroadcastService.players(room).forEach { it.playSound(it.location, Sound.BLOCK_CHEST_OPEN, 1f, 1f) }
        }
        checkWinOrForceDeathmatch()
        if (phase == HungerPhase.RUNNING && phaseTimer.tick()) startDeathmatch()
    }

    private fun tickDeathmatch() {
        announceTimer("hunger.deathmatch", phaseTimer.secondsLeft)
        checkWinOrForceDeathmatch()
        if (phase == HungerPhase.DEATHMATCH && phaseTimer.tick()) finish(null)
    }

    private fun tickResult() {
        if (phaseTimer.tick()) {
            phase = HungerPhase.CLOSING
            phaseTimer.resetSeconds(config.closeDelaySeconds)
        }
    }

    private fun tickClosing() {
        if (phaseTimer.tick() && !closed) {
            closed = true
            roomManager.closeRoom(room.id)
        }
    }

    private fun startRunning() {
        phase = HungerPhase.RUNNING
        phaseTimer.resetSeconds(config.durationSeconds)
        if (config.refillAfterSeconds > 0) refillTimer.resetSeconds(config.refillAfterSeconds) else refillTimer.clear()
        roomBroadcastService.localized(room, language, "hunger.pvp_enabled", includeSpectators = true)
        roomBroadcastService.title(
            room,
            Component.text(language.getMessage("hunger.pvp_title")),
            Component.text(language.getMessage("hunger.pvp_subtitle")),
            includeSpectators = true
        )
    }

    private fun startDeathmatch() {
        val world = room.world ?: return finish(null)
        val alive = alivePlayerIds().mapNotNull(Bukkit::getPlayer)
        val spawns = gameConfig?.deathmatchSpawns.orEmpty()
        val assignments = spawnAssignmentService.assign(alive, spawns)
        if (assignments == null) {
            roomBroadcastService.localized(room, language, "hunger.deathmatch_spawns_missing", alive.size, spawns.size, includeSpectators = true)
            finish(null)
            return
        }
        assignments.forEach { assignment ->
            val player = assignment.participant
            player.teleport(assignment.spawn.point.toLocation(world))
            player.fallDistance = 0f
        }
        phase = HungerPhase.DEATHMATCH
        phaseTimer.resetSeconds(config.deathmatchSeconds)
        refillTimer.clear()
        roomBroadcastService.localized(room, language, "hunger.deathmatch_started", includeSpectators = true)
        roomBroadcastService.title(
            room,
            Component.text(language.getMessage("hunger.deathmatch_title")),
            Component.text(language.getMessage("hunger.deathmatch_subtitle")),
            includeSpectators = true
        )
    }

    private fun checkWinOrForceDeathmatch() {
        if (!isCombatPhase()) return
        val alive = alivePlayerIds()
        if (alive.size <= 1) {
            finish(alive.firstOrNull())
            return
        }
        if (phase == HungerPhase.RUNNING && !forceDeathmatchTriggered && alive.size <= config.forceDeathmatchPlayers) {
            forceDeathmatchTriggered = true
            if (phaseTimer.secondsLeft > config.forceDeathmatchDelaySeconds) {
                phaseTimer.resetSeconds(config.forceDeathmatchDelaySeconds)
            }
            roomBroadcastService.localized(
                room,
                language,
                "hunger.force_deathmatch",
                alive.size,
                config.forceDeathmatchDelaySeconds,
                includeSpectators = true
            )
        }
    }

    private fun finish(winner: UUID?) {
        if (phase == HungerPhase.RESULT || phase == HungerPhase.CLOSING) return
        winnerId = winner
        phase = HungerPhase.RESULT
        phaseTimer.resetSeconds(config.resultDisplaySeconds)
        room.state = GameState.ENDING
        if (!resultRecorded) {
            resultRecorded = true
            resultService.recordWinLoss(room, participants, winner?.let(::setOf).orEmpty(), config.winPoints)
        }
        val winnerName = winner?.let { Bukkit.getOfflinePlayer(it).name ?: it.toString() }
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val won = player.uniqueId == winner
            player.showTitle(
                Title.title(
                    Component.text(language.getMessage(if (won) "hunger.win_title" else if (winner == null) "hunger.draw_title" else "hunger.lose_title")),
                    Component.text(if (winnerName == null) language.getMessage("hunger.draw_subtitle") else language.getMessage("hunger.win_subtitle", winnerName)),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))
                )
            )
            nametagService.clear(room, player)
            enterEliminatedSpectator(player)
        }
        supplyChests.clear()
        updateDisplays()
    }

    private fun eliminate(player: Player, killerId: UUID?, recordDeath: Boolean, enterSpectator: Boolean) {
        val state = states[player.uniqueId] ?: return
        if (!state.alive) return
        state.alive = false
        state.eliminatedAtMillis = System.currentTimeMillis()
        state.frozenLocation = null
        if (recordDeath) resultService.recordDeath(room, player.uniqueId)
        nametagService.clear(room, player)
        if (enterSpectator) enterEliminatedSpectator(player)
        broadcastElimination(player, killerId)
    }

    private fun broadcastElimination(player: Player, killerId: UUID?) {
        val remaining = alivePlayerIds().size
        val killerName = killerId?.let { Bukkit.getOfflinePlayer(it).name }
        if (killerName == null) {
            roomBroadcastService.localized(room, language, "hunger.eliminated", player.name, remaining, includeSpectators = true)
        } else {
            roomBroadcastService.localized(room, language, "hunger.eliminated_by", player.name, killerName, remaining, includeSpectators = true)
        }
    }

    private fun prepareConfiguredSupplyChests() {
        val world = room.world ?: return
        gameConfig?.supplyChests.orEmpty().forEach { configured ->
            val block = configured.point.toLocation(world).block
            val key = HungerLocationKey.from(block.location) ?: return@forEach
            configuredSupplyKeys.add(key)
            if (block.type !in config.loot.containerMaterials) {
                resources.captureBlock(block)
                block.type = Material.BARREL
            }
            (block.state as? Container)?.inventory?.clear()
        }
    }

    private fun generateSupplyInventory(): Inventory {
        val inventory = Bukkit.createInventory(null, 27, Component.text(language.getMessage("hunger.supply_chest_title")))
        val pool = WeightedPool(config.loot.entries.map { WeightedPool.Entry(it, it.weight) })
        if (pool.isEmpty) return inventory
        val random = ThreadLocalRandom.current()
        val count = random.nextInt(config.loot.minItemsPerChest, config.loot.maxItemsPerChest + 1)
        val slots = (0 until inventory.size).toMutableList()
        repeat(count.coerceAtMost(inventory.size)) {
            val entry = pool.next(random) ?: return@repeat
            val amount = random.nextInt(entry.minAmount, entry.maxAmount + 1)
            val slot = slots.removeAt(random.nextInt(slots.size))
            inventory.setItem(slot, ItemStack(entry.material, amount))
        }
        return inventory
    }

    private fun refillSupplyChests() {
        val oldInventories = supplyChests.values.toSet()
        room.players.mapNotNull(Bukkit::getPlayer)
            .filter { it.openInventory.topInventory in oldInventories }
            .forEach(Player::closeInventory)
        supplyChests.clear()
    }

    private fun trackNearestTribute(player: Player) {
        val rangeSquared = config.tracker.range * config.tracker.range
        val target = alivePlayerIds()
            .filter { it != player.uniqueId }
            .mapNotNull(Bukkit::getPlayer)
            .filter { it.world == player.world }
            .filter { it.location.distanceSquared(player.location) <= rangeSquared }
            .minByOrNull { it.location.distanceSquared(player.location) }
        if (target == null) {
            player.sendActionBar(Component.text(language.getMessage("hunger.tracker_empty", config.tracker.range.toInt())))
            return
        }
        player.compassTarget = target.location
        player.sendActionBar(
            Component.text(language.getMessage("hunger.tracker_target", target.name, player.location.distance(target.location).toInt()))
        )
    }

    private fun setTributeNametag(player: Player, state: HungerPlayerState) {
        nametagService.set(
            room,
            player,
            PlayerNametag(
                prefix = Component.text(language.getMessage("hunger.nametag_prefix"), NamedTextColor.GREEN),
                suffix = Component.text(language.getMessage("hunger.nametag_suffix", state.kills), NamedTextColor.GRAY),
                color = NamedTextColor.GREEN,
                collisionRule = NametagCollisionRule.NEVER
            )
        )
    }

    private fun enterEliminatedSpectator(player: Player) {
        room.world?.let { eliminationService.eliminate(room, player, spectatorLocation(it)) }
    }

    private fun resetPlayer(player: Player, clearInventory: Boolean) {
        val attribute = player.getAttribute(Attribute.MAX_HEALTH)
        attribute?.baseValue = config.maxHealth
        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = false
        player.isFlying = false
        player.isInvisible = false
        player.isInvulnerable = false
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f
        if (clearInventory) {
            player.inventory.clear()
            player.inventory.armorContents = arrayOfNulls<ItemStack>(4)
        }
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        player.foodLevel = 20
        player.saturation = 20f
        player.fireTicks = 0
        player.fallDistance = 0f
        player.health = config.maxHealth.coerceAtMost(attribute?.value ?: config.maxHealth)
    }

    private fun restorePlayer(player: Player, state: HungerPlayerState?) {
        state?.frozenLocation = null
        state?.let { player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = it.originalMaxHealth }
        playerRuntimeStateService.restore(room.id, player)
    }

    private fun updateDisplays() {
        val alive = alivePlayerIds().size
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId]
            SidebarBoardRenderer.show(
                player,
                "hunger_${room.id}",
                Component.text(language.getMessage("hunger.scoreboard_title")),
                listOf(
                    language.getMessage("hunger.scoreboard_phase", language.getMessage(phase.languageKey)),
                    language.getMessage("hunger.scoreboard_time", phaseTimer.secondsLeft),
                    language.getMessage("hunger.scoreboard_alive", alive),
                    language.getMessage("hunger.scoreboard_kills", state?.kills ?: 0),
                    language.getMessage("hunger.scoreboard_chests", state?.chestsOpened ?: 0),
                    language.getMessage("hunger.scoreboard_refill", if (refillDone) language.getMessage("hunger.refill_done") else refillTimer.secondsLeft)
                )
            )
            when (phase) {
                HungerPhase.COUNTDOWN -> player.sendActionBar(Component.text(language.getMessage("hunger.actionbar_countdown", phaseTimer.secondsLeft)))
                HungerPhase.PROTECTION -> player.sendActionBar(Component.text(language.getMessage("hunger.actionbar_protection", phaseTimer.secondsLeft)))
                HungerPhase.RUNNING -> player.sendActionBar(Component.text(language.getMessage("hunger.actionbar_running", alive, phaseTimer.secondsLeft)))
                HungerPhase.DEATHMATCH -> player.sendActionBar(Component.text(language.getMessage("hunger.actionbar_deathmatch", alive, phaseTimer.secondsLeft)))
                else -> Unit
            }
        }
    }

    private fun announceTimer(key: String, seconds: Int) {
        if (!isAnnouncementSecond(seconds) || !phaseTimer.isSecondBoundary) return
        roomBroadcastService.localized(room, language, key, seconds, includeSpectators = true)
        roomBroadcastService.players(room).forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f) }
    }

    private fun isAnnouncementSecond(seconds: Int): Boolean {
        return seconds == 900 || seconds == 600 || seconds == 300 || seconds == 120 ||
            seconds == 60 || seconds == 30 || seconds == 10 || seconds in 1..5
    }

    private fun canModifyBlocks(player: Player): Boolean {
        return states[player.uniqueId]?.alive == true &&
            (phase == HungerPhase.PROTECTION || phase == HungerPhase.RUNNING || phase == HungerPhase.DEATHMATCH)
    }

    private fun alivePlayerIds(): List<UUID> {
        return states.filterValues { it.alive && it.participant }.keys.filter(room.players::contains)
    }

    private fun isCombatPhase(): Boolean {
        return phase == HungerPhase.PROTECTION || phase == HungerPhase.RUNNING || phase == HungerPhase.DEATHMATCH
    }

    private fun directPlayer(entity: Entity): Player? {
        return when (entity) {
            is Player -> entity
            is Projectile -> entity.shooter as? Player
            else -> null
        }
    }

    private fun spectatorLocation(world: org.bukkit.World): Location {
        return gameConfig?.spectatorSpawn?.toLocation(world)
            ?: gameConfig?.lobby?.toLocation(world)
            ?: world.spawnLocation
    }

    private fun failConfiguration() {
        roomBroadcastService.localized(room, language, "hunger.config_missing", includeSpectators = true)
        phase = HungerPhase.CLOSING
        phaseTimer.resetSeconds(3)
        room.state = GameState.ENDING
    }
}
