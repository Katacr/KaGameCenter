package org.katacr.kagamecenter.skywars

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
import org.katacr.kaGameCenter.resource.RoomResourceScopeService
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.reward.WeightedPool
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.spawn.SpawnAssignmentService
import org.katacr.kaGameCenter.team.GameTeam
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** 运行一个独立 SkyWars 房间的队伍、箱子、职业、淘汰和结算状态机。 */
class SkyWarsGameSession(
    override val room: GameRoom,
    private val configService: SkyWarsConfigService,
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
    private val spawnAssignmentService: SpawnAssignmentService,
    private val teamService: GameTeamService,
    private val teamAssignmentService: TeamAssignmentService
) : GameSession {
    private val states = linkedMapOf<UUID, SkyWarsPlayerState>()
    private val participants = linkedSetOf<UUID>()
    private val configuredChests = linkedMapOf<SkyWarsLocationKey, SkyWarsChestPoint>()
    private val resources = roomResourceScopeService.open(room.id)
    private val phaseTimer = GamePhaseTimer()
    private val refillTimer = GamePhaseTimer()
    private var config = configService.current()
    private var gameConfig: SkyWarsGameConfig? = null
    private var phase = SkyWarsPhase.WAITING
    private var refillDone = false
    private var resultRecorded = false
    private var displayTicks = 0
    private var closed = false

    override fun usesCustomScoreboard(): Boolean = true

    override fun usesCustomActionBar(): Boolean = true

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
                ?: gameConfig?.islandSpawns?.firstOrNull()?.point?.toLocation(world)
                ?: worldService.readTemplateSpawn(template, world)
            world.spawnLocation = spawn
            eliminationService.enableImmediateRespawn(world)
            prepareConfiguredChests()
        }
    }

    override fun onPlayerJoin(player: Player) {
        val world = room.world ?: return
        resources.trackViewer(player.uniqueId)
        playerRuntimeStateService.captureIfAbsent(room.id, player)
        val lateJoin = phase != SkyWarsPhase.WAITING
        val state = states.getOrPut(player.uniqueId) { SkyWarsPlayerState(participant = !lateJoin) }
        state.originalMaxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: 20.0
        state.participant = !lateJoin
        state.alive = !lateJoin
        state.disconnected = false
        resetPlayer(player, clearInventory = true)
        if (lateJoin) {
            enterEliminatedSpectator(player)
            player.sendMessage(Component.text(language.getMessage("skywars.late_spectator")))
            return
        }
        participants.add(player.uniqueId)
        state.selectedKitId = availableKits(player).firstOrNull()?.id
        giveKitSelector(player, state)
        player.teleport(gameConfig?.lobby?.toLocation(world) ?: world.spawnLocation)
        player.sendMessage(Component.text(language.getMessage("skywars.joined", room.id)))
    }

    override fun onPlayerLeave(player: Player) {
        val state = states[player.uniqueId]
        if (phase == SkyWarsPhase.WAITING) {
            states.remove(player.uniqueId)
            participants.remove(player.uniqueId)
        } else if (state?.alive == true) {
            eliminate(player, recentAttacker(state), recordStats = true, enterSpectator = false)
        }
        nametagService.clear(room, player)
        packetService.clearViewer(player)
        restorePlayer(player, state)
        if (isCombatPhase()) checkWinner()
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

    override fun reconnectGraceTicks(player: Player): Long {
        return if (isCombatPhase() && states[player.uniqueId]?.alive == true) config.reconnectGraceSeconds * 20L else 0L
    }

    override fun onPlayerDisconnect(player: Player) {
        val state = states[player.uniqueId] ?: return
        state.disconnected = true
        state.reconnectLocation = player.location.clone()
        packetService.clearViewer(player)
        nametagService.clearViewer(player)
    }

    override fun onPlayerReconnect(player: Player) {
        val state = states[player.uniqueId] ?: return
        state.disconnected = false
        resources.trackViewer(player.uniqueId)
        state.reconnectLocation?.takeIf { it.world == room.world }?.let(player::teleport)
        state.reconnectLocation = null
        if (state.alive) {
            player.gameMode = if (phase == SkyWarsPhase.COUNTDOWN) GameMode.ADVENTURE else GameMode.SURVIVAL
            player.isInvulnerable = phase != SkyWarsPhase.RUNNING
            setPlayerNametag(player, state)
        } else {
            enterEliminatedSpectator(player)
        }
        updateDisplays()
    }

    override fun onPlayerReconnectExpired(playerId: UUID) {
        val state = states[playerId] ?: return
        if (!state.alive) return
        state.alive = false
        state.disconnected = false
        resultService.recordDeath(room, playerId)
        val playerName = Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString()
        broadcastElimination(playerName, recentAttacker(state), alivePlayerIds().size)
        checkWinner()
    }

    override fun onStart() {
        val world = room.world ?: return
        val managed = gameConfig ?: return failConfiguration()
        val players = room.players.mapNotNull(Bukkit::getPlayer)
        val teamSize = (managed.teamSize ?: config.teamSize).coerceIn(1, players.size.coerceAtLeast(1))
        val teamCount = ceil(players.size.toDouble() / teamSize).toInt()
        if (teamCount < 2 || managed.lobby == null || managed.spectatorSpawn == null ||
            managed.playRegion == null || managed.islandSpawns.size < teamCount
        ) {
            failConfiguration()
            return
        }

        participants.clear()
        participants.addAll(room.players)
        refillDone = false
        resultRecorded = false
        val teamIds = registerTeams(teamCount, teamSize)
        val assignments = teamAssignmentService.assignRoundRobin(room.id, room.players, teamIds)
        if (assignments.size != players.size) return failConfiguration()
        val islandAssignments = spawnAssignmentService.assign(teamIds, managed.islandSpawns) ?: return failConfiguration()
        val islandByTeam = islandAssignments.associate { it.participant to it.spawn.point }
        val memberIndexes = linkedMapOf<String, Int>()

        players.forEach { player ->
            val state = states.getOrPut(player.uniqueId) { SkyWarsPlayerState() }
            val teamId = assignments[player.uniqueId] ?: return@forEach
            val memberIndex = memberIndexes.getOrDefault(teamId, 0)
            memberIndexes[teamId] = memberIndex + 1
            val spawn = offsetSpawn(islandByTeam.getValue(teamId).toLocation(world), memberIndex, teamSize)
            state.alive = true
            state.participant = true
            state.disconnected = false
            state.teamId = teamId
            state.kills = 0
            state.chestsOpened = 0
            state.openedChests.clear()
            state.frozenLocation = spawn.clone()
            state.lastAttackerId = null
            state.lastAttackedAtMillis = 0L
            resetPlayer(player, clearInventory = true)
            applyKit(player, state)
            player.teleport(spawn)
            setPlayerNametag(player, state)
        }
        fillAllChests(refill = false)
        phase = SkyWarsPhase.COUNTDOWN
        phaseTimer.resetSeconds(config.countdownSeconds)
        room.state = GameState.COUNTDOWN
        roomBroadcastService.localized(room, language, "skywars.countdown_started", config.countdownSeconds, includeSpectators = true)
        updateDisplays()
    }

    override fun onTick() {
        when (phase) {
            SkyWarsPhase.WAITING -> Unit
            SkyWarsPhase.COUNTDOWN -> tickCountdown()
            SkyWarsPhase.GRACE -> tickGrace()
            SkyWarsPhase.RUNNING -> tickRunning()
            SkyWarsPhase.RESULT -> tickResult()
            SkyWarsPhase.CLOSING -> tickClosing()
        }
        if (phase != SkyWarsPhase.WAITING) {
            displayTicks++
            if (displayTicks >= 20) {
                displayTicks = 0
                updateDisplays()
            }
        }
    }

    override fun onPlayerDeath(player: Player) {
        if (!isCombatPhase()) return
        val state = states[player.uniqueId] ?: return
        if (!state.alive) return
        state.alive = false
        state.frozenLocation = null
        state.lastAttackerId = null
        nametagService.clear(room, player)
        enterEliminatedSpectator(player)
        broadcastElimination(player.name, player.killer?.uniqueId, alivePlayerIds().size)
        checkWinner()
    }

    override fun onPlayerKill(killer: Player, victim: Player) {
        val state = states[killer.uniqueId] ?: return
        if (!state.alive) return
        state.kills++
        setPlayerNametag(killer, state)
    }

    override fun onEnd() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            packetService.clearViewer(player)
            nametagService.clear(room, player)
            restorePlayer(player, states[player.uniqueId])
        }
        room.spectators.mapNotNull(Bukkit::getPlayer).forEach { spectator ->
            packetService.clearViewer(spectator)
            nametagService.clearViewer(spectator)
        }
    }

    override fun onClose() {
        configuredChests.clear()
        playerRuntimeStateService.clearRoom(room.id)
        states.clear()
        participants.clear()
    }

    /** 按阶段决定玩家受到的环境伤害是否应取消。 */
    fun handleDamage(event: EntityDamageEvent): Boolean {
        val player = event.entity as? Player ?: return false
        val state = states[player.uniqueId] ?: return false
        if (!state.alive) return true
        return phase != SkyWarsPhase.RUNNING
    }

    /** 校验 PVP、友军伤害和攻击者归属，并记录虚空击杀归因。 */
    fun handleDamageByEntity(event: EntityDamageByEntityEvent): Boolean {
        val victim = event.entity as? Player ?: return false
        val victimState = states[victim.uniqueId] ?: return false
        if (!victimState.alive) return true
        val attacker = directPlayer(event.damager) ?: return phase != SkyWarsPhase.RUNNING
        if (roomManager.getPlayerRoom(attacker)?.id != room.id || states[attacker.uniqueId]?.alive != true) return true
        if (phase != SkyWarsPhase.RUNNING) return true
        val attackerTeam = states[attacker.uniqueId]?.teamId
        if (attackerTeam != null && attackerTeam == victimState.teamId) return true
        victimState.lastAttackerId = attacker.uniqueId
        victimState.lastAttackedAtMillis = System.currentTimeMillis()
        return false
    }

    /** 冻结出生岛倒计时玩家、限制地图区域并处理虚空淘汰。 */
    fun handleMove(event: PlayerMoveEvent): Boolean {
        val player = event.player
        val state = states[player.uniqueId] ?: return false
        val to = event.to
        state.frozenLocation?.let { frozen ->
            if (event.from.x != to.x || event.from.y != to.y || event.from.z != to.z) {
                event.to = frozen.clone().apply { yaw = to.yaw; pitch = to.pitch }
            }
            return false
        }
        if (!state.alive || !isCombatPhase()) return false
        if (to.y <= (gameConfig?.voidY ?: config.defaultVoidY)) {
            eliminate(player, recentAttacker(state), recordStats = true, enterSpectator = true)
            checkWinner()
            return false
        }
        val region = gameConfig?.playRegion ?: return false
        return !region.contains(to, ignoreWorld = true)
    }

    /** 处理等待阶段职业切换和运行阶段箱子打开统计。 */
    fun handleInteract(event: PlayerInteractEvent): Boolean {
        val player = event.player
        val state = states[player.uniqueId] ?: return false
        if (phase == SkyWarsPhase.WAITING && event.action.isRightClick && event.item?.type == KIT_SELECTOR) {
            cycleKit(player, state)
            return true
        }
        if (!state.alive) return true
        val block = event.clickedBlock ?: return false
        if (block.type !in config.loot.containerMaterials) return false
        if (!isCombatPhase()) return true
        val key = SkyWarsLocationKey.from(block.location) ?: return true
        if (key !in configuredChests) return true
        if (state.openedChests.add(key)) state.chestsOpened++
        return false
    }

    /** 根据阶段、区域和保护方块集合决定是否允许破坏。 */
    fun handleBlockBreak(event: BlockBreakEvent): Boolean {
        if (!canModifyBlocks(event.player, event.block.location)) return true
        return !config.blocks.allowBreak || event.block.type in config.blocks.protectedMaterials
    }

    /** 根据阶段、区域和保护方块集合决定是否允许放置。 */
    fun handleBlockPlace(event: BlockPlaceEvent): Boolean {
        if (!canModifyBlocks(event.player, event.block.location)) return true
        return !config.blocks.allowPlace || event.block.type in config.blocks.protectedMaterials
    }

    /** 限制爆炸只修改玩法区域内且未受保护的方块。 */
    fun handleExplosion(event: EntityExplodeEvent) {
        if (!config.blocks.explosionBlockDamage) {
            event.blockList().clear()
            return
        }
        val region = gameConfig?.playRegion
        event.blockList().removeIf { block ->
            block.type in config.blocks.protectedMaterials || region?.contains(block.location, ignoreWorld = true) != true
        }
    }

    /** 判断当前阶段是否允许存活玩家消耗饥饿值。 */
    fun allowsHunger(player: Player): Boolean = states[player.uniqueId]?.alive == true && isCombatPhase()

    /** 判断当前房间是否允许火焰蔓延。 */
    fun allowsFireSpread(): Boolean = config.blocks.fireSpread

    /** 判断当前房间是否允许自然怪物生成。 */
    fun allowsMonsterSpawns(): Boolean = config.allowMonsterSpawns

    /** 判断玩家是否仍可操作局内物品。 */
    fun isAliveParticipant(player: Player): Boolean = states[player.uniqueId]?.alive == true

    private fun tickCountdown() {
        announceTimer("skywars.countdown", phaseTimer.secondsLeft)
        if (!phaseTimer.tick()) return
        states.forEach { (playerId, state) ->
            state.frozenLocation = null
            Bukkit.getPlayer(playerId)?.let { player ->
                player.gameMode = GameMode.SURVIVAL
                player.isInvulnerable = config.pvpGraceSeconds > 0
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
            }
        }
        room.state = GameState.RUNNING
        if (config.pvpGraceSeconds > 0) {
            phase = SkyWarsPhase.GRACE
            phaseTimer.resetSeconds(config.pvpGraceSeconds)
            roomBroadcastService.localized(room, language, "skywars.grace_started", config.pvpGraceSeconds, includeSpectators = true)
        } else {
            startRunning()
        }
    }

    private fun tickGrace() {
        announceTimer("skywars.grace", phaseTimer.secondsLeft)
        checkWinner()
        if (phase == SkyWarsPhase.GRACE && phaseTimer.tick()) startRunning()
    }

    private fun tickRunning() {
        announceTimer("skywars.game", phaseTimer.secondsLeft)
        if (!refillDone && refillTimer.active && refillTimer.tick()) {
            refillDone = true
            fillAllChests(refill = true)
            roomBroadcastService.localized(room, language, "skywars.refilled", includeSpectators = true)
            roomBroadcastService.players(room).forEach { it.playSound(it.location, Sound.BLOCK_CHEST_OPEN, 1f, 1f) }
        }
        checkWinner()
        if (phase == SkyWarsPhase.RUNNING && phaseTimer.tick()) finish(emptySet())
    }

    private fun tickResult() {
        if (phaseTimer.tick()) {
            phase = SkyWarsPhase.CLOSING
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
        phase = SkyWarsPhase.RUNNING
        phaseTimer.resetSeconds(config.durationSeconds)
        if (config.refillAfterSeconds > 0) refillTimer.resetSeconds(config.refillAfterSeconds) else refillTimer.clear()
        room.players.mapNotNull(Bukkit::getPlayer).forEach { it.isInvulnerable = false }
        roomBroadcastService.localized(room, language, "skywars.pvp_enabled", includeSpectators = true)
        roomBroadcastService.title(
            room,
            Component.text(language.getMessage("skywars.start_title")),
            Component.text(language.getMessage("skywars.start_subtitle")),
            includeSpectators = true
        )
    }

    private fun checkWinner() {
        if (!isCombatPhase()) return
        val alive = alivePlayerIds()
        if (alive.isEmpty()) {
            finish(emptySet())
            return
        }
        val aliveTeams = alive.mapNotNull { states[it]?.teamId }.toSet()
        if (aliveTeams.size <= 1) {
            val winningTeam = aliveTeams.firstOrNull()
            finish(participants.filterTo(linkedSetOf()) { states[it]?.teamId == winningTeam })
        }
    }

    private fun finish(winners: Set<UUID>) {
        if (phase == SkyWarsPhase.RESULT || phase == SkyWarsPhase.CLOSING) return
        phase = SkyWarsPhase.RESULT
        phaseTimer.resetSeconds(config.resultDisplaySeconds)
        refillTimer.clear()
        room.state = GameState.ENDING
        if (!resultRecorded) {
            resultRecorded = true
            resultService.recordWinLoss(room, participants, winners, config.winPoints)
        }
        val winnerTeam = winners.firstOrNull()?.let { states[it]?.teamId }
        val winnerName = winnerTeam?.let(::teamDisplayName)
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val won = player.uniqueId in winners
            player.showTitle(
                Title.title(
                    Component.text(language.getMessage(if (won) "skywars.win_title" else if (winners.isEmpty()) "skywars.draw_title" else "skywars.lose_title")),
                    Component.text(winnerName?.let { language.getMessage("skywars.win_subtitle", it) } ?: language.getMessage("skywars.draw_subtitle")),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))
                )
            )
            nametagService.clear(room, player)
            enterEliminatedSpectator(player)
        }
        updateDisplays()
    }

    private fun eliminate(player: Player, killerId: UUID?, recordStats: Boolean, enterSpectator: Boolean) {
        val state = states[player.uniqueId] ?: return
        if (!state.alive) return
        state.alive = false
        state.frozenLocation = null
        state.lastAttackerId = null
        if (recordStats) {
            if (killerId != null && killerId != player.uniqueId && states[killerId]?.alive == true) {
                resultService.recordKill(room, killerId, player.uniqueId, points = 1)
                states[killerId]?.let { killerState ->
                    killerState.kills++
                    Bukkit.getPlayer(killerId)?.let { setPlayerNametag(it, killerState) }
                }
            } else {
                resultService.recordDeath(room, player.uniqueId)
            }
        }
        nametagService.clear(room, player)
        if (enterSpectator) enterEliminatedSpectator(player)
        broadcastElimination(player.name, killerId, alivePlayerIds().size)
    }

    private fun broadcastElimination(playerName: String, killerId: UUID?, remaining: Int) {
        val killerName = killerId?.let { Bukkit.getOfflinePlayer(it).name }
        if (killerName == null) {
            roomBroadcastService.localized(room, language, "skywars.eliminated", playerName, remaining, includeSpectators = true)
        } else {
            roomBroadcastService.localized(room, language, "skywars.eliminated_by", playerName, killerName, remaining, includeSpectators = true)
        }
    }

    private fun registerTeams(teamCount: Int, teamSize: Int): List<String> {
        val teamIds = (1..teamCount).map { "team_$it" }
        val teams = teamIds.mapIndexed { index, teamId ->
            GameTeam(teamId, language.getMessage("skywars.team_name", index + 1), TEAM_COLORS[index % TEAM_COLORS.size], teamSize)
        }
        teamAssignmentService.registerTeams(room.id, teams)
        return teamIds
    }

    private fun prepareConfiguredChests() {
        val world = room.world ?: return
        gameConfig?.chests.orEmpty().forEach { configured ->
            val block = configured.point.toLocation(world).block
            val key = SkyWarsLocationKey.from(block.location) ?: return@forEach
            configuredChests[key] = configured
            if (block.type !in config.loot.containerMaterials) {
                resources.captureBlock(block)
                block.type = Material.BARREL
            }
            (block.state as? Container)?.inventory?.clear()
        }
    }

    private fun fillAllChests(refill: Boolean) {
        val world = room.world ?: return
        configuredChests.values.forEach { configured ->
            val container = configured.point.toLocation(world).block.state as? Container ?: return@forEach
            fillChest(container.inventory, config.loot.tiers[configured.tier] ?: config.loot.tiers.values.first(), refill)
        }
        if (refill) states.values.forEach { it.openedChests.clear() }
    }

    private fun fillChest(inventory: Inventory, tier: SkyWarsChestTier, refill: Boolean) {
        inventory.viewers.toList().forEach { it.closeInventory() }
        inventory.clear()
        val levels = config.loot.levels.filter { it.itemValue in tier.minItemValue..tier.maxItemValue && it.items.isNotEmpty() }
        val pool = WeightedPool(levels.map { WeightedPool.Entry(it, it.chance) })
        if (pool.isEmpty) return
        val random = ThreadLocalRandom.current()
        val budget = (tier.totalValue * if (refill) tier.refillMultiplier else 1.0).toInt().coerceAtLeast(1)
        val slots = (0 until inventory.size).shuffled().toMutableList()
        var totalValue = 0
        while (totalValue < budget && slots.isNotEmpty()) {
            val level = pool.next(random) ?: break
            val item = level.items[random.nextInt(level.items.size)]
            inventory.setItem(slots.removeAt(slots.lastIndex), ItemStack(item.material, item.amount))
            totalValue += level.itemValue
        }
    }

    private fun availableKits(player: Player): List<SkyWarsKit> {
        return config.kits.values.filter { it.permission == null || player.hasPermission(it.permission) }
    }

    private fun cycleKit(player: Player, state: SkyWarsPlayerState) {
        val kits = availableKits(player)
        if (kits.isEmpty()) return
        val currentIndex = kits.indexOfFirst { it.id == state.selectedKitId }
        val kit = kits[(currentIndex + 1).mod(kits.size)]
        state.selectedKitId = kit.id
        giveKitSelector(player, state)
        player.sendMessage(Component.text(language.getMessage("skywars.kit_selected", kit.displayName)))
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1.2f)
    }

    private fun giveKitSelector(player: Player, state: SkyWarsPlayerState) {
        val kit = config.kits[state.selectedKitId]
        val item = ItemStack(KIT_SELECTOR)
        item.editMeta { meta ->
            meta.displayName(Component.text(language.getMessage("skywars.kit_selector", kit?.displayName ?: "-"), NamedTextColor.AQUA))
        }
        player.inventory.setItem(4, item)
    }

    private fun applyKit(player: Player, state: SkyWarsPlayerState) {
        val kit = config.kits[state.selectedKitId] ?: return
        if (kit.permission != null && !player.hasPermission(kit.permission)) return
        kit.items.forEach { player.inventory.addItem(ItemStack(it.material, it.amount)) }
        kit.armor.forEach { item ->
            val stack = ItemStack(item.material, item.amount)
            when {
                item.material.name.endsWith("_HELMET") -> player.inventory.helmet = stack
                item.material.name.endsWith("_CHESTPLATE") -> player.inventory.chestplate = stack
                item.material.name.endsWith("_LEGGINGS") -> player.inventory.leggings = stack
                item.material.name.endsWith("_BOOTS") -> player.inventory.boots = stack
                else -> player.inventory.addItem(stack)
            }
        }
    }

    private fun setPlayerNametag(player: Player, state: SkyWarsPlayerState) {
        val color = state.teamId?.let(::teamColor) ?: NamedTextColor.GREEN
        nametagService.set(
            room,
            player,
            PlayerNametag(
                prefix = Component.text("[${state.teamId?.let(::teamDisplayName) ?: "-"}] ", color),
                suffix = Component.text(language.getMessage("skywars.nametag_suffix", state.kills), NamedTextColor.GRAY),
                color = color,
                collisionRule = NametagCollisionRule.NEVER
            )
        )
    }

    private fun teamDisplayName(teamId: String): String = teamService.getTeams(room.id).firstOrNull { it.id == teamId }?.displayName ?: teamId

    private fun teamColor(teamId: String): NamedTextColor {
        return teamService.getTeams(room.id).firstOrNull { it.id == teamId }?.color as? NamedTextColor ?: NamedTextColor.WHITE
    }

    private fun recentAttacker(state: SkyWarsPlayerState): UUID? {
        val age = System.currentTimeMillis() - state.lastAttackedAtMillis
        return state.lastAttackerId?.takeIf { age <= config.killAttributionSeconds * 1000L }
    }

    private fun offsetSpawn(base: Location, memberIndex: Int, teamSize: Int): Location {
        if (teamSize <= 1 || memberIndex == 0) return base
        val angle = 2.0 * PI * memberIndex / teamSize
        return base.clone().add(cos(angle) * 0.8, 0.0, sin(angle) * 0.8)
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
        player.isInvulnerable = true
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

    private fun restorePlayer(player: Player, state: SkyWarsPlayerState?) {
        state?.frozenLocation = null
        state?.let { player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = it.originalMaxHealth }
        eliminationService.clear(room.id, player.uniqueId)
        playerRuntimeStateService.restore(room.id, player)
    }

    private fun updateDisplays() {
        val alive = alivePlayerIds().size
        val aliveTeams = alivePlayerIds().mapNotNull { states[it]?.teamId }.toSet().size
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId]
            SidebarBoardRenderer.show(
                player,
                "sky_${room.id}",
                Component.text(language.getMessage("skywars.scoreboard_title")),
                listOf(
                    language.getMessage("skywars.scoreboard_phase", language.getMessage(phase.languageKey)),
                    language.getMessage("skywars.scoreboard_time", phaseTimer.secondsLeft),
                    language.getMessage("skywars.scoreboard_alive", alive),
                    language.getMessage("skywars.scoreboard_teams", aliveTeams),
                    language.getMessage("skywars.scoreboard_kills", state?.kills ?: 0),
                    language.getMessage("skywars.scoreboard_chests", state?.chestsOpened ?: 0),
                    language.getMessage("skywars.scoreboard_refill", if (refillDone) language.getMessage("skywars.refill_done") else refillTimer.secondsLeft)
                )
            )
            when (phase) {
                SkyWarsPhase.COUNTDOWN -> player.sendActionBar(Component.text(language.getMessage("skywars.actionbar_countdown", phaseTimer.secondsLeft)))
                SkyWarsPhase.GRACE -> player.sendActionBar(Component.text(language.getMessage("skywars.actionbar_grace", phaseTimer.secondsLeft)))
                SkyWarsPhase.RUNNING -> player.sendActionBar(Component.text(language.getMessage("skywars.actionbar_running", alive, aliveTeams, phaseTimer.secondsLeft)))
                else -> Unit
            }
        }
    }

    private fun announceTimer(key: String, seconds: Int) {
        if (!phaseTimer.isSecondBoundary || !isAnnouncementSecond(seconds)) return
        roomBroadcastService.localized(room, language, key, seconds, includeSpectators = true)
        roomBroadcastService.players(room).forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f) }
    }

    private fun isAnnouncementSecond(seconds: Int): Boolean {
        return seconds == 900 || seconds == 600 || seconds == 300 || seconds == 120 ||
            seconds == 60 || seconds == 30 || seconds == 10 || seconds in 1..5
    }

    private fun canModifyBlocks(player: Player, location: Location): Boolean {
        return states[player.uniqueId]?.alive == true && isCombatPhase() &&
            gameConfig?.playRegion?.contains(location, ignoreWorld = true) == true
    }

    private fun alivePlayerIds(): List<UUID> {
        return states.filterValues { it.alive && it.participant }.keys.filter(room.players::contains)
    }

    private fun isCombatPhase(): Boolean = phase == SkyWarsPhase.GRACE || phase == SkyWarsPhase.RUNNING

    private fun directPlayer(entity: Entity): Player? = when (entity) {
        is Player -> entity
        is Projectile -> entity.shooter as? Player
        else -> null
    }

    private fun spectatorLocation(world: org.bukkit.World): Location {
        return gameConfig?.spectatorSpawn?.toLocation(world)
            ?: gameConfig?.lobby?.toLocation(world)
            ?: world.spawnLocation
    }

    private fun failConfiguration() {
        roomBroadcastService.localized(room, language, "skywars.config_missing", includeSpectators = true)
        phase = SkyWarsPhase.CLOSING
        phaseTimer.resetSeconds(3)
        room.state = GameState.ENDING
    }

    companion object {
        private val KIT_SELECTOR = Material.NETHER_STAR
        private val TEAM_COLORS = listOf(
            NamedTextColor.RED,
            NamedTextColor.BLUE,
            NamedTextColor.GREEN,
            NamedTextColor.YELLOW,
            NamedTextColor.AQUA,
            NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.GOLD,
            NamedTextColor.WHITE
        )
    }
}
