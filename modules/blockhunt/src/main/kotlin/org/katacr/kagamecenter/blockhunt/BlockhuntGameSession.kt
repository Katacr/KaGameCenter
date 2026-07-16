package org.katacr.kagamecenter.blockhunt

import net.kyori.adventure.text.Component
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.katacr.kaGameCenter.display.SidebarBoardRenderer
import org.katacr.kaGameCenter.display.GameBossBarStatus
import org.katacr.kaGameCenter.display.PlayerAvatarStatus
import org.katacr.kaGameCenter.display.PlayerStatusSide
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.game.GameState
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.team.GameTeam
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.world.TemporaryWorldService
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.time.Duration
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

class BlockhuntGameSession(
    override val room: GameRoom,
    private val configService: BlockhuntConfigService,
    private val worldService: TemporaryWorldService,
    private val language: ModuleLanguage,
    private val packetService: PacketDispatchService,
    private val roomManager: GameRoomManager,
    private val teamService: GameTeamService,
    private val teamAssignmentService: TeamAssignmentService,
    private val resultService: GameResultService
) : GameSession {
    override fun usesCustomScoreboard(): Boolean = true
    override fun usesCustomActionBar(): Boolean = true

    /** 为核心通用 BossBar 提供猎人与躲藏者头像及当前阶段倒计时。 */
    override fun bossBarStatus(): GameBossBarStatus? {
        if (states.isEmpty()) return null
        val hunters = states.filterValues { it.role == BlockhuntRole.HUNTER }.map { (playerId, state) ->
            PlayerAvatarStatus(playerId, playerName(playerId), state.alive)
        }
        val hiders = states.filterValues { it.role == BlockhuntRole.HIDER }.map { (playerId, state) ->
            PlayerAvatarStatus(playerId, playerName(playerId), state.alive)
        }
        val seconds = when (phase) {
            BlockhuntPhase.COUNTDOWN -> countdownTicks / 20
            BlockhuntPhase.HIDING -> hidingTicks / 20
            BlockhuntPhase.RUNNING -> runningTicks / 20
            BlockhuntPhase.RESULT -> resultTicks / 20
            BlockhuntPhase.CLOSING -> closeTicks / 20
            BlockhuntPhase.WAITING -> 0
        }.coerceAtLeast(0)
        return GameBossBarStatus(
            left = PlayerStatusSide(Component.text(roleName(BlockhuntRole.HUNTER), NamedTextColor.RED), hunters),
            center = Component.text(formatBossBarTime(seconds), NamedTextColor.WHITE),
            right = PlayerStatusSide(Component.text(roleName(BlockhuntRole.HIDER), NamedTextColor.GREEN), hiders),
            progress = when (phase) {
                BlockhuntPhase.RUNNING -> seconds.toFloat() / config.durationSeconds.coerceAtLeast(1)
                else -> 1.0f
            },
            color = BossBar.Color.GREEN
        )
    }

    private val states = linkedMapOf<UUID, BlockhuntPlayerState>()
    private val activePickups = linkedMapOf<String, ActivePickup>()
    private var config: BlockhuntConfig = configService.current()
    private var gameConfig: BlockhuntGameConfig? = null
    private var mapConfig: BlockhuntMapConfig? = null
    private var phase = BlockhuntPhase.WAITING
    private var countdownTicks = 0
    private var hidingTicks = 0
    private var runningTicks = 0
    private var resultTicks = 0
    private var closeTicks = 0
    private var scoreboardTick = 0
    private var itemTick = 0
    private var disguiseTick = 0
    private var frenzyStarted = false
    private var closed = false

    init {
        teamAssignmentService.registerTeams(
            room.id,
            listOf(
                GameTeam(HUNTER_TEAM, language.getMessage("blockhunt.role_hunter"), NamedTextColor.RED),
                GameTeam(HIDER_TEAM, language.getMessage("blockhunt.role_hider"), NamedTextColor.GREEN)
            )
        )
    }

    override fun onPrepare() {
        config = configService.reload()
        mapConfig = resolveMap()
        gameConfig = room.configuredGame?.let { configService.readManagedGame(it) } ?: BlockhuntGameConfig(null, null, null, null, emptyList())
        val worldName = "kgc_${room.id}"
        val template = mapConfig?.template ?: room.mapTemplate ?: room.definition?.mapTemplates?.firstOrNull()
        room.world = room.templateDirectory?.let {
            worldService.createRoomWorldFromDirectory(it, worldName, allowFlatFallback = false)
        } ?: worldService.createRoomWorldFromTemplate(template, worldName, allowFlatFallback = false)
        room.world?.let { world ->
            val spawn = gameConfig?.lobby?.toLocation(world)
                ?: gameConfig?.hiderSpawn?.toLocation(world)
                ?: worldService.readTemplateSpawn(template, world)
            world.spawnLocation = spawn
        }
    }

    override fun onPlayerJoin(player: Player) {
        val world = room.world ?: return
        val lateJoin = phase == BlockhuntPhase.COUNTDOWN || phase == BlockhuntPhase.HIDING
        if (lateJoin && !states.containsKey(player.uniqueId)) {
            val state = BlockhuntPlayerState(role = BlockhuntRole.HIDER, probeUsesLeft = 0)
            state.disguise = chooseDisguise(gameConfig?.hiderSpawn?.toLocation(world) ?: world.spawnLocation)
            states[player.uniqueId] = state
            teamService.join(room.id, player, HIDER_TEAM)
        }
        val spawn = gameConfig?.lobby?.toLocation(world)
            ?: gameConfig?.hiderSpawn?.toLocation(world)
            ?: world.spawnLocation
        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = false
        player.isFlying = false
        player.inventory.clear()
        player.teleport(if (lateJoin) gameConfig?.hiderSpawn?.toLocation(world) ?: spawn else spawn)
        player.sendMessage(Component.text(language.getMessage("blockhunt.joined", room.id)))
        if (lateJoin) {
            player.sendMessage(Component.text(language.getMessage("blockhunt.assigned_hider")))
            states[player.uniqueId]?.let { state ->
                player.sendMessage(Component.text(language.getMessage("blockhunt.disguise_changed", state.disguise.name.lowercase())))
                refreshDisguise(player, state)
            }
        }
    }

    override fun onPlayerLeave(player: Player) {
        states.remove(player.uniqueId)?.let { state ->
            restorePlayerState(player, state)
            removeLockedDisguise(state)
        }
        packetService.clearViewer(player)
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        player.inventory.clear()
        player.sendMessage(Component.text(language.getMessage("blockhunt.left")))
        if (phase == BlockhuntPhase.RUNNING || phase == BlockhuntPhase.HIDING) {
            checkWinConditions()
        }
    }

    override fun onSpectatorJoin(player: Player) {
        val world = room.world ?: return
        val spawn = gameConfig?.lobby?.toLocation(world) ?: gameConfig?.hiderSpawn?.toLocation(world) ?: world.spawnLocation
        player.teleport(spawn)
    }

    override fun onStart() {
        val world = room.world ?: return
        val game = gameConfig ?: return broadcast("blockhunt.config_missing")
        if (game.hunterSpawn == null || game.hiderSpawn == null) {
            broadcast("blockhunt.config_missing")
            return
        }
        assignRoles()
        states.forEach { (playerId, state) ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            player.inventory.clear()
            player.gameMode = GameMode.ADVENTURE
            player.walkSpeed = if (state.role == BlockhuntRole.HUNTER) 0f else 0.2f
            player.teleport(if (state.role == BlockhuntRole.HUNTER) game.hunterSpawn.toLocation(world) else game.hiderSpawn.toLocation(world))
            if (state.role == BlockhuntRole.HUNTER) {
                player.sendMessage(Component.text(language.getMessage("blockhunt.assigned_hunter")))
            } else {
                state.disguise = chooseDisguise(player.location)
                player.sendMessage(Component.text(language.getMessage("blockhunt.assigned_hider")))
                player.sendMessage(Component.text(language.getMessage("blockhunt.disguise_changed", state.disguise.name.lowercase())))
                refreshDisguise(player, state)
            }
        }
        countdownTicks = config.startCountdownSeconds * 20
        hidingTicks = config.hunterReleaseSeconds * 20
        runningTicks = config.durationSeconds * 20
        phase = BlockhuntPhase.COUNTDOWN
        room.state = GameState.COUNTDOWN
        broadcast("blockhunt.countdown_started", config.startCountdownSeconds)
        updateScoreboards()
    }

    override fun onTick() {
        when (phase) {
            BlockhuntPhase.WAITING -> Unit
            BlockhuntPhase.COUNTDOWN -> tickCountdown()
            BlockhuntPhase.HIDING -> tickHiding()
            BlockhuntPhase.RUNNING -> tickRunning()
            BlockhuntPhase.RESULT -> tickResult()
            BlockhuntPhase.CLOSING -> tickClosing()
        }
    }

    override fun onEnd() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            states[player.uniqueId]?.let { restorePlayerState(player, it) } ?: restorePlayerState(player, null)
            player.inventory.clear()
            packetService.clearViewer(player)
            player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
            player.sendMessage(Component.text(language.getMessage("blockhunt.ended", room.id)))
        }
        states.values.forEach(::removeLockedDisguise)
        cleanupFakeBlocks()
    }

    override fun onClose() {
        states.values.forEach(::removeLockedDisguise)
        cleanupFakeBlocks()
        activePickups.clear()
        states.clear()
    }

    fun handleSneak(player: Player) {
        val state = states[player.uniqueId] ?: return
        if (state.role != BlockhuntRole.HIDER || !state.alive) return
        val now = System.currentTimeMillis()
        if (now - state.lastSneakAt > config.doubleSneakMs) {
            state.lastSneakAt = now
            return
        }
        state.lastSneakAt = 0L
        if (!state.locked) {
            val anchor = resolveLockAnchor(player.location)
            if (anchor == null) {
                player.sendActionBar(Component.text(language.getMessage("blockhunt.hider_lock_invalid")))
                return
            }
            lockHider(player, state, anchor)
            player.sendActionBar(Component.text(language.getMessage("blockhunt.hider_locked", state.disguise.name.lowercase())))
        } else {
            unlockHider(player, state)
            player.sendActionBar(Component.text(language.getMessage("blockhunt.hider_unlocked")))
        }
        refreshDisguise(player, state)
    }

    fun handleMove(player: Player, to: Location) {
        val state = states[player.uniqueId] ?: return
        if (state.frozenTicks > 0) {
            state.frozenLocation?.let(player::teleport)
            return
        }
        if (state.locked) return
        if (phase != BlockhuntPhase.RUNNING && phase != BlockhuntPhase.HIDING) return
        val region = gameConfig?.playRegion ?: return
        if (!region.contains(to, ignoreWorld = true)) {
            val fallback = when (state.role) {
                BlockhuntRole.HUNTER -> gameConfig?.hunterSpawn?.toLocation(player.world)
                BlockhuntRole.HIDER -> gameConfig?.hiderSpawn?.toLocation(player.world)
            } ?: player.world.spawnLocation
            player.teleport(fallback)
        }
    }

    fun handleDamage(attacker: Player, victim: Player): Boolean {
        val attackerState = states[attacker.uniqueId] ?: return false
        val victimState = states[victim.uniqueId] ?: return false
        if (phase != BlockhuntPhase.RUNNING) return true
        if (attackerState.role != BlockhuntRole.HUNTER || victimState.role != BlockhuntRole.HIDER || !victimState.alive) return true
        catchHider(victim, attacker)
        return false
    }

    fun handleSnowballHit(shooter: Player, victim: Player): Boolean {
        val shooterState = states[shooter.uniqueId] ?: return false
        val victimState = states[victim.uniqueId] ?: return false
        if (phase != BlockhuntPhase.RUNNING) return true
        if (shooterState.role != BlockhuntRole.HUNTER || victimState.role != BlockhuntRole.HIDER || !victimState.alive) return true
        catchHider(victim, shooter, "blockhunt.hider_caught_by_snowball")
        return false
    }

    fun handleLockedDisguiseHit(attacker: Player, entity: Entity): Boolean {
        val attackerState = states[attacker.uniqueId] ?: return false
        if (phase != BlockhuntPhase.RUNNING) return true
        if (attackerState.role != BlockhuntRole.HUNTER) return true
        val victim = lockedHiderByEntity(entity) ?: return true
        catchHider(victim, attacker)
        return false
    }

    fun handleLockedDisguiseSnowballHit(shooter: Player, entity: Entity): Boolean {
        val shooterState = states[shooter.uniqueId] ?: return false
        if (phase != BlockhuntPhase.RUNNING) return true
        if (shooterState.role != BlockhuntRole.HUNTER) return true
        val victim = lockedHiderByEntity(entity) ?: return true
        catchHider(victim, shooter, "blockhunt.hider_caught_by_snowball")
        return false
    }

    fun handleLockedBlockHit(attacker: Player, block: Block): Boolean {
        val attackerState = states[attacker.uniqueId] ?: return false
        if (phase != BlockhuntPhase.RUNNING) return true
        if (attackerState.role != BlockhuntRole.HUNTER) return true
        val victim = lockedHiderByBlock(block) ?: return true
        catchHider(victim, attacker)
        return false
    }

    fun handleLockedBlockSnowballHit(shooter: Player, block: Block): Boolean {
        val shooterState = states[shooter.uniqueId] ?: return false
        if (phase != BlockhuntPhase.RUNNING) return true
        if (shooterState.role != BlockhuntRole.HUNTER) return true
        val victim = lockedHiderByBlock(block) ?: return true
        catchHider(victim, shooter, "blockhunt.hider_caught_by_snowball")
        return false
    }

    private fun tickCountdown() {
        val secondsLeft = max(1, (countdownTicks + 19) / 20)
        if (countdownTicks % 20 == 0) {
            room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
                player.showTitle(Title.title(
                    Component.text(language.getMessage("blockhunt.countdown_title", secondsLeft)),
                    Component.text(language.getMessage("blockhunt.countdown_subtitle")),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))
                ))
            }
        }
        countdownTicks--
        if (countdownTicks > 0) return
        phase = if (config.hunterReleaseSeconds > 0) BlockhuntPhase.HIDING else BlockhuntPhase.RUNNING
        room.state = if (phase == BlockhuntPhase.HIDING) GameState.COUNTDOWN else GameState.RUNNING
        states.forEach { (playerId, state) ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            if (state.role == BlockhuntRole.HIDER) {
                player.showTitle(Title.title(
                    Component.text(language.getMessage("blockhunt.hider_start_title")),
                    Component.text(language.getMessage("blockhunt.hider_start_subtitle")),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(300))
                ))
                refreshDisguise(player, state)
            }
        }
        if (phase == BlockhuntPhase.RUNNING) releaseHunters()
    }

    private fun tickHiding() {
        hidingTicks--
        val left = max(0, (hidingTicks + 19) / 20)
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            player.sendActionBar(Component.text(language.getMessage("blockhunt.actionbar_waiting_hunter", left)))
        }
        tickCommon()
        if (hidingTicks <= 0) {
            releaseHunters()
        }
    }

    private fun tickRunning() {
        runningTicks--
        tickCommon()
        if (!frenzyStarted && config.frenzySeconds > 0 && runningTicks <= config.frenzySeconds * 20) {
            startFrenzy()
        }
        if (runningTicks <= 0) {
            finish(hidersWin = aliveHiders().isNotEmpty())
        } else {
            checkWinConditions()
        }
    }

    private fun tickCommon() {
        scoreboardTick++
        itemTick++
        disguiseTick++
        tickFrozenHunters()
        tickInvisibleHiders()
        if (scoreboardTick >= 10) {
            scoreboardTick = 0
            updateScoreboards()
            updateActionBars()
        }
        if (itemTick >= config.itemRefreshSeconds * 20) {
            itemTick = 0
            refreshPickups()
        }
        if (disguiseTick >= config.disguiseRefreshSeconds * 20) {
            disguiseTick = 0
            refreshAllDisguises()
        }
    }

    private fun tickResult() {
        resultTicks--
        if (resultTicks <= 0) {
            phase = BlockhuntPhase.CLOSING
            closeTicks = config.closeDelaySeconds * 20
        }
    }

    private fun tickClosing() {
        closeTicks--
        if (closeTicks <= 0 && !closed) {
            closed = true
            roomManager.closeRoom(room.id)
        }
    }

    private fun releaseHunters() {
        phase = BlockhuntPhase.RUNNING
        room.state = GameState.RUNNING
        states.forEach { (playerId, state) ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            if (state.role == BlockhuntRole.HUNTER) {
                player.walkSpeed = 0.2f
                giveHunterKit(player)
                player.showTitle(Title.title(
                    Component.text(language.getMessage("blockhunt.hunter_release_title")),
                    Component.text(language.getMessage("blockhunt.hunter_release_subtitle")),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(300))
                ))
            }
        }
        refreshPickups()
    }

    private fun assignRoles() {
        states.clear()
        val assignments = teamAssignmentService.assignRatio(
            room.id,
            room.players,
            primaryTeamId = HUNTER_TEAM,
            secondaryTeamId = HIDER_TEAM,
            primaryRatio = config.hunterRatio,
            minPrimary = 1,
            keepSecondary = true
        )
        assignments.forEach { (playerId, teamId) ->
            val role = if (teamId.equals(HUNTER_TEAM, ignoreCase = true)) BlockhuntRole.HUNTER else BlockhuntRole.HIDER
            states[playerId] = BlockhuntPlayerState(role = role, probeUsesLeft = config.hunterProbeUses)
        }
    }

    private fun giveHunterKit(player: Player) {
        val amount = if (frenzyStarted) 64 else config.hunterSnowballs
        if (amount > 0) {
            player.inventory.addItem(ItemStack(Material.SNOWBALL, amount))
        }
    }

    private fun startFrenzy() {
        frenzyStarted = true
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId] ?: return@forEach
            player.showTitle(Title.title(
                Component.text(language.getMessage("blockhunt.frenzy_title")),
                Component.text(language.getMessage("blockhunt.frenzy_subtitle")),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(300))
            ))
            if (state.role == BlockhuntRole.HIDER && state.alive) {
                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, runningTicks.coerceAtLeast(20), config.hiderFrenzyAmplifier, true, false, false))
            }
            if (state.role == BlockhuntRole.HUNTER) {
                player.inventory.addItem(ItemStack(Material.SNOWBALL, 64))
            }
        }
    }

    private fun refreshPickups() {
        val world = room.world ?: return
        val spawns = gameConfig?.itemSpawns.orEmpty()
        if (spawns.isEmpty() || config.maxActivePickupsPerRole <= 0) return
        activePickups.clear()
        val hunterTypes = listOf(BlockhuntPickupType.HUNTER_GLOW, BlockhuntPickupType.HUNTER_PROBE, BlockhuntPickupType.HUNTER_SNOWBALLS)
        val hiderTypes = listOf(BlockhuntPickupType.HIDER_BLIND, BlockhuntPickupType.HIDER_FREEZE, BlockhuntPickupType.HIDER_FAKE_BLOCK, BlockhuntPickupType.HIDER_INVISIBLE)
        val shuffled = spawns.shuffled()
        val hunters = activeHunters().takeIf { it.isNotEmpty() } ?: emptyList()
        val hiders = aliveHiders().takeIf { it.isNotEmpty() } ?: emptyList()
        shuffled.getOrNull(0)?.let { spawn ->
            showPickupFor(hunters, spawn.point.toLocation(world), hunterTypes.random())
        }
        shuffled.getOrNull(1)?.let { spawn ->
            showPickupFor(hiders, spawn.point.toLocation(world), hiderTypes.random())
        }
    }

    private fun showPickupFor(players: List<Player>, location: Location, type: BlockhuntPickupType) {
        if (players.isEmpty()) return
        val id = "${type.name}:${location.world?.name}:${location.blockX}:${location.blockY}:${location.blockZ}:${System.nanoTime()}"
        activePickups[id] = ActivePickup(id, type)
        players.forEach { player ->
            packetService.showPrivatePickup(
                viewer = player,
                location = location,
                itemStack = ItemStack(type.material),
                glowing = true,
                color = if (type.name.startsWith("HUNTER")) NamedTextColor.RED else NamedTextColor.GREEN,
                durationSeconds = config.pickupDurationSeconds,
                scale = config.pickupScale
            ) { picker ->
                val active = activePickups.remove(id) ?: return@showPrivatePickup
                applyPickup(picker, active.type)
            }
        }
    }

    private fun applyPickup(player: Player, type: BlockhuntPickupType) {
        val pickerState = states[player.uniqueId] ?: return
        val isHunterItem = type.name.startsWith("HUNTER")
        if ((isHunterItem && pickerState.role != BlockhuntRole.HUNTER) || (!isHunterItem && pickerState.role != BlockhuntRole.HIDER)) return
        if (!pickerState.alive) return
        if (pickerState.locked) {
            player.sendActionBar(Component.text(language.getMessage("blockhunt.item_locked_denied")))
            return
        }
        player.sendMessage(Component.text(language.getMessage("blockhunt.item_pickup", language.getMessage(type.languageKey))))
        when (type) {
            BlockhuntPickupType.HUNTER_GLOW -> {
                val viewers = activeHunters()
                aliveHiders().forEach { hider ->
                    viewers.forEach { viewer -> packetService.showPlayerGlow(viewer, hider, config.hunterGlowSeconds) }
                    states[hider.uniqueId]?.lockedBlock?.let { block ->
                        viewers.forEach { viewer ->
                            packetService.showBlockGlow(viewer, block.location, config.hunterGlowSeconds, NamedTextColor.AQUA)
                        }
                    }
                }
                player.sendMessage(Component.text(language.getMessage("blockhunt.hunter_item_glow", config.hunterGlowSeconds)))
            }
            BlockhuntPickupType.HUNTER_PROBE -> {
                val state = states[player.uniqueId] ?: return
                if (state.probeUsesLeft <= 0) return
                state.probeUsesLeft--
                val found = aliveHiders().any { hider ->
                    val state = states[hider.uniqueId]
                    val target = state?.lockedBlock?.location ?: hider.location
                    target.world == player.world && target.distanceSquared(player.location) <= config.hunterProbeRadius * config.hunterProbeRadius
                }
                player.sendActionBar(Component.text(language.getMessage(
                    if (found) "blockhunt.hunter_item_probe_found" else "blockhunt.hunter_item_probe_empty",
                    config.hunterProbeRadius.toInt(),
                    state.probeUsesLeft
                )))
            }
            BlockhuntPickupType.HUNTER_SNOWBALLS -> {
                val amount = config.hunterSnowballs.coerceAtLeast(1)
                player.inventory.addItem(ItemStack(Material.SNOWBALL, amount))
                player.sendMessage(Component.text(language.getMessage("blockhunt.hunter_item_snowballs", amount)))
            }
            BlockhuntPickupType.HIDER_BLIND -> {
                activeHunters().forEach {
                    it.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, config.hiderBlindSeconds * 20, 0, true, false, false))
                }
                player.sendMessage(Component.text(language.getMessage("blockhunt.hider_item_blind", config.hiderBlindSeconds)))
            }
            BlockhuntPickupType.HIDER_FREEZE -> {
                activeHunters().forEach { hunter ->
                    states[hunter.uniqueId]?.let { state ->
                        state.frozenTicks = config.hiderFreezeSeconds * 20
                        state.frozenLocation = hunter.location.clone()
                    }
                    hunter.walkSpeed = 0f
                }
                player.sendMessage(Component.text(language.getMessage("blockhunt.hider_item_freeze", config.hiderFreezeSeconds)))
            }
            BlockhuntPickupType.HIDER_FAKE_BLOCK -> {
                val state = states[player.uniqueId] ?: return
                spawnFakeBlock(player.location, state.disguise)
                player.sendMessage(Component.text(language.getMessage("blockhunt.hider_item_fake_block", config.hiderFakeBlockSeconds)))
            }
            BlockhuntPickupType.HIDER_INVISIBLE -> {
                val state = states[player.uniqueId] ?: return
                applyHiderInvisibility(player, state)
                player.sendMessage(Component.text(language.getMessage("blockhunt.hider_item_invisible", config.hiderInvisibleSeconds)))
            }
        }
    }

    private fun applyHiderInvisibility(player: Player, state: BlockhuntPlayerState) {
        state.invisibleTicks = config.hiderInvisibleSeconds * 20
        if (!state.locked) {
            player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, state.invisibleTicks, 0, true, false, false))
            packetService.clearDisguise(player, disguiseViewers(player))
        }
    }

    private fun tickInvisibleHiders() {
        states.forEach { (playerId, state) ->
            if (state.invisibleTicks <= 0) return@forEach
            state.invisibleTicks--
            if (state.invisibleTicks > 0) return@forEach
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            if (state.locked) {
                return@forEach
            } else {
                player.removePotionEffect(PotionEffectType.INVISIBILITY)
                if (state.role == BlockhuntRole.HIDER && state.alive) refreshDisguise(player, state)
            }
        }
    }

    private fun tickFrozenHunters() {
        states.forEach { (playerId, state) ->
            if (state.frozenTicks <= 0) return@forEach
            state.frozenTicks--
            if (state.frozenTicks == 0) {
                state.frozenLocation = null
                Bukkit.getPlayer(playerId)?.walkSpeed = 0.2f
            } else {
                val player = Bukkit.getPlayer(playerId)
                val location = state.frozenLocation
                if (player != null && location != null) {
                    player.teleport(location)
                }
            }
        }
    }

    private fun refreshAllDisguises() {
        states.forEach { (playerId, state) ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            if (state.role == BlockhuntRole.HIDER && state.alive) refreshDisguise(player, state)
        }
    }

    private fun refreshDisguise(player: Player, state: BlockhuntPlayerState) {
        applyHiderEntityState(player)
        if (state.invisibleTicks > 0) return
        if (state.locked) return
        val viewers = disguiseViewers(player)
        packetService.clearDisguise(player, viewers)
        packetService.disguisePlayerAsBlock(player, state.disguise, viewers, config.disguiseRefreshSeconds + 3)
    }

    private fun lockHider(player: Player, state: BlockhuntPlayerState, anchor: LockAnchor) {
        state.locked = true
        state.lockLocation = anchor.location
        packetService.clearDisguise(player, disguiseViewers(player))
        removeLockedDisguise(state)
        val block = anchor.location.block
        state.lockedBlock = block
        state.lockedOriginalBlockData = block.blockData.clone()
        block.setBlockData(state.disguise.createBlockData(), false)
        state.lockedHitbox = spawnLockedHitbox(anchor.location)
        applyHiderEntityState(player)
        player.gameMode = GameMode.SPECTATOR
        player.allowFlight = true
        player.isFlying = true
        player.walkSpeed = 0.2f
    }

    private fun unlockHider(player: Player, state: BlockhuntPlayerState) {
        state.locked = false
        removeLockedDisguise(state)
        state.lockLocation?.let(player::teleport)
        state.lockLocation = null
        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = false
        player.isFlying = false
        player.walkSpeed = 0.2f
        applyHiderEntityState(player)
        if (state.invisibleTicks > 0) {
            player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, state.invisibleTicks, 0, true, false, false))
            packetService.clearDisguise(player, disguiseViewers(player))
        }
    }

    private fun applyHiderEntityState(player: Player) {
        player.isInvisible = true
        player.isCollidable = false
    }

    private fun spawnLockedHitbox(location: Location): Interaction {
        return location.world.spawn(location, Interaction::class.java) { entity ->
            entity.interactionWidth = 1.0f
            entity.interactionHeight = 1.0f
            entity.isResponsive = true
            entity.isPersistent = false
        }
    }

    private fun lockedHiderByEntity(entity: Entity): Player? {
        return states.entries.firstOrNull { (_, state) ->
            state.locked && state.lockedHitbox?.uniqueId == entity.uniqueId
        }?.key?.let(Bukkit::getPlayer)
    }

    private fun lockedHiderByBlock(block: Block): Player? {
        return states.entries.firstOrNull { (_, state) ->
            state.locked && state.lockedBlock?.let { locked ->
                locked.world == block.world &&
                    locked.x == block.x &&
                    locked.y == block.y &&
                    locked.z == block.z
            } == true
        }?.key?.let(Bukkit::getPlayer)
    }

    private fun chooseDisguise(location: Location): Material {
        val below = location.clone().subtract(0.0, 1.0, 0.0).block.type
        if (below in config.disguiseWhitelist) return below
        return config.disguiseWhitelist.randomOrNull() ?: Material.OAK_PLANKS
    }

    private fun resolveLockAnchor(location: Location): LockAnchor? {
        val candidates = listOf(
            location.clone(),
            location.clone().add(0.0, 1.0, 0.0)
        )
        return candidates.firstNotNullOfOrNull { candidate ->
            val block = candidate.block
            if (!isLegalLockSpace(block.type)) return@firstNotNullOfOrNull null
            LockAnchor(block.location.add(0.5, 0.0, 0.5).apply {
                yaw = location.yaw
                pitch = location.pitch
            })
        }
    }

    private fun isLegalLockSpace(material: Material): Boolean {
        return material.isAir
    }

    private fun removeLockedDisguise(state: BlockhuntPlayerState) {
        val block = state.lockedBlock
        val original = state.lockedOriginalBlockData
        if (block != null && original != null && block.world.isChunkLoaded(block.x shr 4, block.z shr 4)) {
            block.setBlockData(original, false)
        }
        state.lockedHitbox?.remove()
        state.lockedBlock = null
        state.lockedOriginalBlockData = null
        state.lockedHitbox = null
    }

    private fun catchHider(victim: Player, hunter: Player, messageKey: String = "blockhunt.hider_caught") {
        val state = states[victim.uniqueId] ?: return
        if (!state.alive) return
        state.alive = false
        state.locked = false
        state.invisibleTicks = 0
        removeLockedDisguise(state)
        restorePlayerState(victim, state)
        packetService.clearViewer(victim)
        room.players.mapNotNull(Bukkit::getPlayer).forEach { viewer -> packetService.showPlayerGlow(viewer, victim, 2) }
        if (messageKey == "blockhunt.hider_caught") {
            broadcast(messageKey, victim.name, hunter.name)
        } else {
            broadcast(messageKey, victim.name)
        }
        resultService.recordKill(room, hunter.uniqueId, victim.uniqueId, points = 1)
        if (config.caughtHiderBecomesHunter) {
            convertCaughtHiderToHunter(victim)
        } else {
            victim.gameMode = GameMode.SPECTATOR
        }
        checkWinConditions()
    }

    private fun convertCaughtHiderToHunter(player: Player) {
        val state = states[player.uniqueId] ?: return
        state.role = BlockhuntRole.HUNTER
        teamService.join(room.id, player, HUNTER_TEAM)
        state.alive = true
        state.probeUsesLeft = config.hunterProbeUses
        state.lockLocation = null
        removeLockedDisguise(state)
        state.frozenTicks = 0
        state.frozenLocation = null
        state.invisibleTicks = 0
        player.removePotionEffect(PotionEffectType.INVISIBILITY)
        player.gameMode = GameMode.ADVENTURE
        player.inventory.clear()
        gameConfig?.hunterSpawn?.toLocation(player.world)?.let(player::teleport)
        giveHunterKit(player)
        player.sendMessage(Component.text(language.getMessage("blockhunt.caught_becomes_hunter")))
    }

    private fun spawnFakeBlock(location: Location, material: Material) {
        val world = location.world ?: return
        val display = world.spawn(location.block.location, BlockDisplay::class.java) { entity ->
            entity.setBlock(material.createBlockData())
            entity.viewRange = 96f
            entity.shadowRadius = 0f
            entity.shadowStrength = 0f
            entity.isPersistent = false
            entity.transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                Quaternionf(),
                Vector3f(1f, 1f, 1f),
                Quaternionf()
            )
        }
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("KaGameCenter") ?: return,
            Runnable {
                if (!display.isDead) display.remove()
            },
            config.hiderFakeBlockSeconds * 20L
        )
    }

    private fun cleanupFakeBlocks() {
        room.world?.entities
            ?.filter { it is BlockDisplay || it is Interaction }
            ?.filter { !it.isDead && !it.isPersistent }
            ?.forEach { it.remove() }
    }

    private fun restorePlayerState(player: Player, state: BlockhuntPlayerState?) {
        state?.frozenTicks = 0
        state?.frozenLocation = null
        state?.invisibleTicks = 0
        state?.let(::removeLockedDisguise)
        player.walkSpeed = 0.2f
        player.allowFlight = false
        player.isFlying = false
        player.isInvisible = false
        player.isCollidable = true
        if (state?.locked == true || player.gameMode == GameMode.SPECTATOR) {
            player.gameMode = GameMode.ADVENTURE
        }
        player.removePotionEffect(PotionEffectType.BLINDNESS)
        player.removePotionEffect(PotionEffectType.SPEED)
        player.removePotionEffect(PotionEffectType.INVISIBILITY)
        val viewers = (room.players + room.spectators)
            .mapNotNull(Bukkit::getPlayer)
            .toMutableList()
            .also { viewers ->
                if (viewers.none { it.uniqueId == player.uniqueId }) viewers.add(player)
            }
        packetService.clearDisguise(player, viewers)
    }

    private fun disguiseViewers(player: Player): List<Player> {
        return (activeHunters() + player).distinctBy { it.uniqueId }
    }

    private fun checkWinConditions() {
        if (phase != BlockhuntPhase.RUNNING && phase != BlockhuntPhase.HIDING) return
        if (aliveHiders().isEmpty()) {
            finish(hidersWin = false)
        }
    }

    private fun finish(hidersWin: Boolean) {
        if (phase == BlockhuntPhase.RESULT || phase == BlockhuntPhase.CLOSING) return
        phase = BlockhuntPhase.RESULT
        room.state = GameState.ENDING
        resultTicks = config.resultDisplaySeconds * 20
        val key = if (hidersWin) "blockhunt.result_hiders_win" else "blockhunt.result_hunters_win"
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId]
            if (state != null) {
                restorePlayerState(player, state)
                state.locked = false
                state.lockLocation = null
            } else {
                restorePlayerState(player, null)
            }
            val winner = (hidersWin && state?.role == BlockhuntRole.HIDER && state.alive) || (!hidersWin && state?.role == BlockhuntRole.HUNTER)
            if (winner) resultService.recordWin(room, player.uniqueId, points = 3) else resultService.recordLoss(room, player.uniqueId)
            player.walkSpeed = 0.2f
            player.showTitle(Title.title(
                Component.text(language.getMessage(key)),
                Component.text(language.getMessage("blockhunt.result_rewards_preview")),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))
            ))
        }
    }

    private fun updateActionBars() {
        val seconds = max(0, runningTicks / 20)
        val alive = aliveHiders().size
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val role = roleName(states[player.uniqueId]?.role)
            player.sendActionBar(Component.text(language.getMessage("blockhunt.actionbar_running", role, seconds, alive)))
        }
    }

    private fun updateScoreboards() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val lines = listOf(
                "§7房间 §f${room.id}",
                "§7阶段 §f${phase.name}",
                "§7身份 §f${roleName(states[player.uniqueId]?.role)}",
                "§7躲藏者 §a${aliveHiders().size}",
                "§7猎人 §c${activeHunters().size}",
                "§7剩余 §f${max(0, runningTicks / 20)}s"
            )
            SidebarBoardRenderer.show(
                player = player,
                objectiveId = "bh_${room.id}",
                title = Component.text(config.displayName, NamedTextColor.GREEN),
                lines = lines,
                maxLineLength = 36
            )
        }
    }

    private fun roleName(role: BlockhuntRole?): String {
        return when (role) {
            BlockhuntRole.HUNTER -> language.getMessage("blockhunt.role_hunter")
            BlockhuntRole.HIDER -> language.getMessage("blockhunt.role_hider")
            null -> language.getMessage("blockhunt.role_spectator")
        }
    }

    private fun playerName(playerId: UUID): String {
        return Bukkit.getPlayer(playerId)?.name ?: Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString().take(8)
    }

    private fun formatBossBarTime(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

    private fun BlockhuntRole.teamId(): String {
        return when (this) {
            BlockhuntRole.HUNTER -> HUNTER_TEAM
            BlockhuntRole.HIDER -> HIDER_TEAM
        }
    }

    private fun activeHunters(): List<Player> {
        return states.filterValues { it.role == BlockhuntRole.HUNTER }
            .keys
            .mapNotNull(Bukkit::getPlayer)
    }

    private fun aliveHiders(): List<Player> {
        return states.filterValues { it.role == BlockhuntRole.HIDER && it.alive }
            .keys
            .mapNotNull(Bukkit::getPlayer)
    }

    private fun resolveMap(): BlockhuntMapConfig? {
        val template = room.mapTemplate ?: room.configuredGame?.sharedMapTemplate
        return configService.findMapByTemplate(template) ?: config.firstMap()
    }

    private fun broadcast(key: String, vararg args: Any) {
        val message = Component.text(language.getMessage(key, *args))
        room.players.mapNotNull(Bukkit::getPlayer).forEach { it.sendMessage(message) }
    }

    private data class ActivePickup(
        val id: String,
        val type: BlockhuntPickupType
    )

    private data class LockAnchor(
        val location: Location
    )

    companion object {
        private const val HUNTER_TEAM = "hunter"
        private const val HIDER_TEAM = "hider"
    }
}
