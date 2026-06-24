package org.katacr.kagamecenter.parkour

import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.katacr.kaGameCenter.display.SidebarBoardRenderer
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.game.GameState
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.selection.RegionSelection
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.time.Duration
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import org.joml.Quaternionf
import org.joml.Vector3f

class ParkourGameSession(
    private val plugin: JavaPlugin,
    override val room: GameRoom,
    private val configService: ParkourConfigService,
    private val worldService: TemporaryWorldService,
    private val language: ModuleLanguage,
    private val packetService: PacketDispatchService,
    private val roomManager: GameRoomManager,
    private val resultService: GameResultService
) : GameSession {
    override fun usesCustomScoreboard(): Boolean = true

    override fun usesCustomActionBar(): Boolean = true

    private val states = linkedMapOf<UUID, RunnerState>()
    private var config: ParkourConfig = configService.current()
    private var mapConfig: ParkourMapConfig? = null
    private var routeConfig: ParkourRouteConfig? = null
    private var joinLocation: Location? = null
    private var phase = ParkourPhase.WAITING
    private var countdownTicks = 0
    private var finishCountdownTicks = -1
    private var closeCountdownTicks = -1
    private var startedAtMillis: Long = 0L
    private var scoreboardTick = 0
    private var glowTick = 0
    private var buffTick = 0
    private var closed = false
    private var routeDistanceCache = RouteDistanceCache.EMPTY
    private val spectatorItemKey = NamespacedKey(plugin, "parkour_spectator_action")
    private val spectatorTargets = linkedMapOf<UUID, UUID>()
    private val targetVisuals = linkedMapOf<UUID, ActiveTargetVisual>()

    override fun onPrepare() {
        config = configService.reload()
        mapConfig = resolveMap()
        routeConfig = room.configuredGame?.let { configService.readManagedRoute(it) } ?: resolveRoute(mapConfig)
        val worldName = "kgc_${room.id}"
        val template = mapConfig?.template ?: room.mapTemplate ?: room.definition?.mapTemplates?.firstOrNull()
        room.world = room.templateDirectory?.let {
            worldService.createRoomWorldFromDirectory(it, worldName, allowFlatFallback = false)
        } ?: worldService.createRoomWorldFromTemplate(template, worldName, allowFlatFallback = false)
        room.world?.let { world ->
            joinLocation = routeConfig?.lobby?.toLocation(world)
                ?: routeConfig?.start?.spawn?.toLocation(world)
                ?: worldService.readTemplateSpawn(template, world)
            joinLocation?.let { world.spawnLocation = it }
            routeDistanceCache = routeConfig?.let { buildRouteDistanceCache(it, world) } ?: RouteDistanceCache.EMPTY
        }
    }

    override fun onPlayerJoin(player: Player) {
        val world = room.world ?: return
        val route = routeConfig
        val spawn = route?.lobby?.toLocation(world) ?: joinLocation ?: world.spawnLocation
        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = false
        player.isFlying = false
        player.isInvisible = false
        player.isInvulnerable = false
        player.inventory.clear()
        player.teleport(spawn)
        states[player.uniqueId] = RunnerState(
            respawn = route?.start?.spawn?.toLocation(world) ?: spawn.clone()
        )
        player.sendMessage(Component.text(language.getMessage("parkour.joined", room.id)))
    }

    override fun onPlayerLeave(player: Player) {
        restoreFinishedSpectator(player)
        restoreMovement(player)
        states.remove(player.uniqueId)
        spectatorTargets.remove(player.uniqueId)
        clearTargetVisual(player)
        packetService.clearViewer(player)
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        player.sendMessage(Component.text(language.getMessage("parkour.left")))
    }

    override fun onSpectatorJoin(player: Player) {
        routeConfig?.lobby?.toLocation(room.world ?: return)?.let(player::teleport)
    }

    override fun onStart() {
        val world = room.world ?: return
        val route = routeConfig ?: run {
            broadcast("parkour.config_missing")
            return
        }
        val start = route.start?.spawn?.toLocation(world) ?: joinLocation ?: world.spawnLocation
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            player.teleport(start)
            player.gameMode = GameMode.ADVENTURE
            player.allowFlight = false
            player.isFlying = false
            player.isInvisible = false
            player.isInvulnerable = false
            player.walkSpeed = 0f
            player.flySpeed = 0f
            states.computeIfAbsent(player.uniqueId) { RunnerState(start.clone()) }.apply {
                respawn = start.clone()
                frozen = true
                checkpointIndex = -1
                startedAtMillis = 0L
                finishedAtMillis = 0L
                resultRecorded = false
                awardedPoints = 0
            }
        }
        countdownTicks = config.startCountdownSeconds * 20
        phase = ParkourPhase.COUNTDOWN
        room.state = GameState.COUNTDOWN
        broadcast("parkour.countdown_started", config.startCountdownSeconds)
    }

    override fun onTick() {
        when (phase) {
            ParkourPhase.WAITING -> Unit
            ParkourPhase.COUNTDOWN -> tickCountdown()
            ParkourPhase.RUNNING, ParkourPhase.ENDING -> tickRunning()
            ParkourPhase.RESULT -> tickResult()
            ParkourPhase.CLOSING -> tickClosing()
        }
    }

    override fun onEnd() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            restoreFinishedSpectator(player)
            restoreMovement(player)
            clearTargetVisual(player)
            packetService.clearViewer(player)
            player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
            player.sendMessage(Component.text(language.getMessage("parkour.ended", room.id)))
        }
    }

    override fun onClose() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach {
            restoreFinishedSpectator(it)
            restoreMovement(it)
            clearTargetVisual(it)
        }
        targetVisuals.clear()
        spectatorTargets.clear()
        states.clear()
    }

    fun handleMove(player: Player, to: Location) {
        val state = states[player.uniqueId] ?: return
        if (state.frozen) {
            state.respawn?.let { player.teleport(it) }
            return
        }
        val route = routeConfig ?: return
        if (phase != ParkourPhase.RUNNING && phase != ParkourPhase.ENDING) return
        if (to.y < (route.fallY ?: config.fallY)) {
            teleportToCheckpoint(player)
            state.falls++
        }
    }

    fun allPlayersFinished(): Boolean = activeRunnerIds().all { states[it]?.finishedAtMillis ?: 0L > 0L }

    fun handleInteract(event: PlayerInteractEvent): Boolean {
        val player = event.player
        if (!isFinishedSpectator(player)) return false
        val action = spectatorAction(event.item) ?: return false
        event.isCancelled = true
        when (action) {
            FinishedSpectatorAction.NEXT_RUNNER -> followNextRunner(player)
            FinishedSpectatorAction.RETURN_TO_FINISH -> returnToFinish(player)
            FinishedSpectatorAction.LEAVE_ROOM -> roomManager.leaveCurrentRoom(player)
        }
        return true
    }

    fun handleInventoryClick(event: InventoryClickEvent): Boolean {
        val player = event.whoClicked as? Player ?: return false
        if (!isFinishedSpectator(player)) return false
        event.isCancelled = true
        return true
    }

    fun handleDrop(event: PlayerDropItemEvent): Boolean {
        if (!isFinishedSpectator(event.player)) return false
        if (spectatorAction(event.itemDrop.itemStack) == null) return false
        event.isCancelled = true
        return true
    }

    fun handleSwapHandItems(event: PlayerSwapHandItemsEvent): Boolean {
        if (!isFinishedSpectator(event.player)) return false
        event.isCancelled = true
        return true
    }

    fun handleDamage(event: EntityDamageEvent): Boolean {
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return false
        val player = event.entity as? Player ?: return false
        val state = states[player.uniqueId] ?: return false
        if (System.currentTimeMillis() > state.fallDamageImmuneUntilMillis) return false
        event.isCancelled = true
        state.fallDamageImmuneUntilMillis = 0L
        player.fallDistance = 0f
        return true
    }

    private fun tickCountdown() {
        val secondsLeft = max(1, (countdownTicks + 19) / 20)
        if (countdownTicks % 20 == 0) {
            room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
                player.showTitle(Title.title(
                    Component.text(language.getMessage("parkour.countdown_title", secondsLeft)),
                    Component.text(language.getMessage("parkour.countdown_subtitle")),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))
                ))
            }
        }
        countdownTicks--
        if (countdownTicks > 0) return

        startedAtMillis = System.currentTimeMillis()
        phase = ParkourPhase.RUNNING
        room.state = GameState.RUNNING
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            restoreMovement(player)
            states[player.uniqueId]?.apply {
                frozen = false
                startedAtMillis = this@ParkourGameSession.startedAtMillis
                showNextGlow(player, this)
            }
            player.showTitle(Title.title(
                Component.text(language.getMessage("parkour.go_title")),
                Component.text(language.getMessage("parkour.go_subtitle")),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(300))
            ))
        }
    }

    private fun tickRunning() {
        detectGoals()
        scoreboardTick++
        glowTick++
        if (scoreboardTick >= 5) {
            scoreboardTick = 0
            updateScoreboards()
        }
        if (glowTick >= TARGET_PARTICLE_REFRESH_TICKS) {
            glowTick = 0
            refreshTargetGlow()
        }
        buffTick++
        if (buffTick >= 20) {
            buffTick = 0
            refreshBuffPickups()
        }
        if (phase == ParkourPhase.ENDING) {
            finishCountdownTicks--
            if (finishCountdownTicks <= 0 || allPlayersFinished()) {
                showResults()
            }
        }
    }

    private fun tickResult() {
        closeCountdownTicks--
        if (closeCountdownTicks <= 0) {
            phase = ParkourPhase.CLOSING
            closeCountdownTicks = config.closeDelaySeconds * 20
        }
    }

    private fun tickClosing() {
        closeCountdownTicks--
        if (closeCountdownTicks <= 0 && !closed) {
            closed = true
            roomManager.closeRoom(room.id)
        }
    }

    private fun detectGoals() {
        val route = routeConfig ?: return
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId] ?: return@forEach
            if (state.finishedAtMillis > 0L) return@forEach
            val next = nextRegion(route, state) ?: return@forEach
            if (!next.contains(player.location, ignoreWorld = true)) return@forEach
            if (state.checkpointIndex + 1 < route.checkpoints.size) {
                val checkpoint = route.checkpoints[state.checkpointIndex + 1]
                state.checkpointIndex++
                state.respawn = checkpoint.respawn.toLocation(player.world)
                state.splitTimes[checkpoint.id] = System.currentTimeMillis() - startedAtMillis
                player.sendActionBar(Component.text(language.getMessage("parkour.checkpoint_reached", checkpoint.displayName)))
                clearTargetVisual(player)
                showNextGlow(player, state)
            } else {
                finish(player)
            }
        }
    }

    private fun finish(player: Player) {
        val state = states[player.uniqueId] ?: return
        if (state.finishedAtMillis > 0L) return
        state.finishedAtMillis = System.currentTimeMillis()
        val elapsedMillis = state.finishedAtMillis - startedAtMillis
        state.cachedDistance = totalRouteDistance()
        val points = rewardPoints(elapsedMillis, finishedCount() + 1)
        state.awardedPoints = points
        if (!state.resultRecorded) {
            resultService.recordWin(room, player.uniqueId, points)
            state.resultRecorded = true
        }
        clearTargetVisual(player)
        packetService.clearViewer(player)
        giveFinishedSpectatorHotbar(player)
        player.sendMessage(Component.text(language.getMessage("parkour.finished", formatDuration(elapsedMillis), points)))
        if (phase == ParkourPhase.RUNNING) {
            phase = ParkourPhase.ENDING
            room.state = GameState.ENDING
            finishCountdownTicks = config.finishCountdownSeconds * 20
            broadcast("parkour.finish_countdown_started", config.finishCountdownSeconds)
        }
    }

    private fun showResults() {
        if (phase == ParkourPhase.RESULT) return
        phase = ParkourPhase.RESULT
        closeCountdownTicks = config.resultDisplaySeconds * 20
        recordUnfinishedLosses()
        val lines = rankings().take(8).joinToString("\n") { ranked ->
            val playerName = Bukkit.getOfflinePlayer(ranked.playerId).name ?: ranked.playerId.toString().take(8)
            if (ranked.finishedMillis != null) {
                language.getMessage("parkour.result_line_finished", ranked.rank, playerName, formatDuration(ranked.finishedMillis))
            } else {
                language.getMessage("parkour.result_line_distance", ranked.rank, playerName, "%.1f".format(ranked.distance))
            }
        }
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            restoreMovement(player)
            player.sendMessage(Component.text(language.getMessage("parkour.result_header")))
            player.sendMessage(Component.text(lines.ifBlank { language.getMessage("parkour.result_empty") }))
            player.sendMessage(Component.text(language.getMessage("parkour.result_rewards_preview")))
        }
    }

    private fun updateScoreboards() {
        val route = routeConfig ?: return
        val ranked = rankings()
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId]
            val elapsed = if (startedAtMillis > 0L) System.currentTimeMillis() - startedAtMillis else 0L
            val ownRank = ranked.firstOrNull { it.playerId == player.uniqueId }
            val remaining = state?.let { remainingDistance(route, it, player.location) } ?: 0.0
            val lines = mutableListOf<String>()
            lines += "§e${config.displayName}"
            lines += "§7用时 §f${formatDuration(elapsed)}"
            lines += "§8────────"
            ranked.take(5).forEach { entry ->
                val name = Bukkit.getOfflinePlayer(entry.playerId).name ?: entry.playerId.toString().take(6)
                val value = entry.finishedMillis?.let { formatDuration(it) } ?: "%.1fm".format(entry.distance)
                lines += "${rankColor(entry.rank)}#${entry.rank} §f$name §a$value"
            }
            while (lines.size < 8) lines += "§8-"
            lines += "§8────────§r"
            lines += "§b自己 §f#${ownRank?.rank ?: "-"} §7${ownRank?.distance?.let { "%.1fm".format(it) } ?: "0.0m"}"
            lines += "§d剩余 §f${"%.1fm".format(remaining)}"
            SidebarBoardRenderer.show(
                player = player,
                objectiveId = "pk_${room.id}",
                title = Component.text(config.displayName.take(16)),
                lines = lines
            )
            if (state?.finishedAtMillis == 0L) {
                player.sendActionBar(Component.text(language.getMessage("parkour.remaining_actionbar", "%.1f".format(remaining))))
            }
        }
    }

    private fun refreshTargetGlow() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId] ?: return@forEach
            if (state.finishedAtMillis > 0L) return@forEach
            showNextGlow(player, state)
        }
    }

    private fun showNextGlow(player: Player, state: RunnerState) {
        val world = player.world
        val indicator = nextTargetIndicator(routeConfig ?: return, state, world) ?: return
        showTargetIndicator(player, indicator)
    }

    private fun refreshBuffPickups() {
        val route = routeConfig ?: return
        if (!packetService.available || route.buffs.isEmpty()) return
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId] ?: return@forEach
            if (state.finishedAtMillis > 0L) return@forEach
            route.buffs.forEach { buff ->
                val nextAvailable = state.buffCooldowns[buff.id] ?: 0L
                if (System.currentTimeMillis() < nextAvailable) return@forEach
                showBuffPickup(player, state, buff)
            }
        }
    }

    private fun showBuffPickup(player: Player, state: RunnerState, buff: ParkourBuffConfig) {
        val location = buff.point.toLocation(player.world)
        val color = parseNamedTextColor(buff.color, net.kyori.adventure.text.format.NamedTextColor.AQUA)
        packetService.showBeaconBeam(player, location, color, durationSeconds = 2)
        packetService.showPrivatePickup(player, location, ItemStack(Material.SUGAR), glowing = true, color = color, durationSeconds = 2) { picker ->
            if (picker.uniqueId != player.uniqueId) return@showPrivatePickup
            val now = System.currentTimeMillis()
            if (now < (state.buffCooldowns[buff.id] ?: 0L)) return@showPrivatePickup
            state.buffCooldowns[buff.id] = now + buff.respawnSeconds * 1000L
            if (buff.type.equals("speed2", ignoreCase = true)) {
                picker.addPotionEffect(PotionEffect(PotionEffectType.SPEED, buff.durationSeconds * 20, buff.amplifier, true, true, true))
                picker.sendActionBar(Component.text(language.getMessage("parkour.buff_speed", buff.durationSeconds)))
            }
        }
    }

    private fun nextRegion(route: ParkourRouteConfig, state: RunnerState): RegionSelection? {
        val nextCheckpoint = state.checkpointIndex + 1
        return if (nextCheckpoint < route.checkpoints.size) route.checkpoints[nextCheckpoint].region else route.finish?.region
    }

    private fun nextGlowRegion(route: ParkourRouteConfig, state: RunnerState): RegionSelection? {
        val nextCheckpoint = state.checkpointIndex + 1
        return if (nextCheckpoint < route.checkpoints.size) {
            route.checkpoints[nextCheckpoint].glowRegion ?: route.checkpoints[nextCheckpoint].region
        } else {
            route.finish?.glowRegion ?: route.finish?.region
        }
    }

    private fun nextTargetIndicator(route: ParkourRouteConfig, state: RunnerState, world: org.bukkit.World): TargetIndicator? {
        val nextCheckpoint = state.checkpointIndex + 1
        return if (nextCheckpoint < route.checkpoints.size) {
            val checkpoint = route.checkpoints[nextCheckpoint]
            TargetIndicator(
                key = "checkpoint:${checkpoint.id}",
                center = checkpoint.respawn.toLocation(world),
                material = Material.YELLOW_STAINED_GLASS,
                particleColor = Color.fromRGB(255, 221, 64)
            )
        } else {
            val finish = route.finish ?: return null
            TargetIndicator(
                key = "finish",
                center = finish.region.center(world),
                material = Material.LIME_STAINED_GLASS,
                particleColor = Color.fromRGB(80, 255, 120)
            )
        }
    }

    private fun showTargetIndicator(player: Player, indicator: TargetIndicator) {
        val existing = targetVisuals[player.uniqueId]
        if (existing?.key != indicator.key) {
            clearTargetVisual(player)
            targetVisuals[player.uniqueId] = ActiveTargetVisual(
                key = indicator.key,
                entities = spawnArrowDisplays(player, indicator)
            )
        }
        showTargetCircle(player, indicator)
    }

    private fun showTargetCircle(player: Player, indicator: TargetIndicator) {
        val dust = Particle.DustOptions(indicator.particleColor, 1.2f)
        val center = indicator.center
        repeat(TARGET_CIRCLE_POINTS) { index ->
            val angle = (PI * 2.0 * index) / TARGET_CIRCLE_POINTS
            val location = center.clone().add(
                cos(angle) * TARGET_CIRCLE_RADIUS,
                TARGET_CIRCLE_Y_OFFSET,
                sin(angle) * TARGET_CIRCLE_RADIUS
            )
            player.spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    private fun spawnArrowDisplays(player: Player, indicator: TargetIndicator): List<Entity> {
        val center = indicator.center
        val material = indicator.material.createBlockData()
        return TARGET_ARROW_OFFSETS.map { offset ->
            center.world.spawn(center.clone().add(offset.x, offset.y, offset.z), BlockDisplay::class.java) { entity ->
                entity.setBlock(material)
                entity.setVisibleByDefault(false)
                entity.viewRange = 96f
                entity.shadowRadius = 0f
                entity.shadowStrength = 0f
                entity.isPersistent = false
                entity.isGlowing = true
                entity.transformation = Transformation(
                    Vector3f(-TARGET_ARROW_BLOCK_SCALE / 2f, -TARGET_ARROW_BLOCK_SCALE / 2f, -TARGET_ARROW_BLOCK_SCALE / 2f),
                    Quaternionf(),
                    Vector3f(TARGET_ARROW_BLOCK_SCALE, TARGET_ARROW_BLOCK_SCALE, TARGET_ARROW_BLOCK_SCALE),
                    Quaternionf()
                )
            }.also { player.showEntity(plugin, it) }
        }
    }

    private fun clearTargetVisual(player: Player) {
        val visual = targetVisuals.remove(player.uniqueId) ?: return
        visual.entities.forEach { entity ->
            if (!entity.isDead) {
                player.hideEntity(plugin, entity)
                entity.remove()
            }
        }
    }

    private fun teleportToCheckpoint(player: Player) {
        val state = states[player.uniqueId] ?: return
        val location = state.respawn ?: joinLocation ?: return
        player.teleport(location)
        player.fallDistance = 0f
        state.fallDamageImmuneUntilMillis = System.currentTimeMillis() + FALL_DAMAGE_IMMUNITY_MILLIS
        player.sendActionBar(Component.text(language.getMessage("parkour.checkpoint_returned")))
    }

    private fun rankings(): List<RankedRunner> {
        val route = routeConfig ?: return emptyList()
        return activeRunnerIds().mapNotNull { playerId ->
            val player = Bukkit.getPlayer(playerId)
            val state = states[playerId] ?: return@mapNotNull null
            val distance = if (player != null) currentDistance(route, state, player.location) else state.cachedDistance
            state.cachedDistance = distance
            RankedRunner(playerId, distance, state.finishedAtMillis.takeIf { it > 0L }?.minus(startedAtMillis))
        }.sortedWith(compareBy<RankedRunner> { it.finishedMillis == null }
            .thenBy { it.finishedMillis ?: Long.MAX_VALUE }
            .thenByDescending { it.distance })
            .mapIndexed { index, ranked -> ranked.copy(rank = index + 1) }
    }

    private fun currentDistance(route: ParkourRouteConfig, state: RunnerState, location: Location): Double {
        if (state.finishedAtMillis > 0L) return totalRouteDistance()
        val world = location.world ?: return 0.0
        val cache = routeDistanceCache.takeIf { it.isFor(world, route) } ?: buildRouteDistanceCache(route, world)
        if (cache.points.isEmpty()) return 0.0
        val completedPointIndex = (state.checkpointIndex + 1).coerceIn(0, cache.points.lastIndex)
        val baseDistance = cache.cumulativeDistances.getOrElse(completedPointIndex) { cache.totalDistance }
        val nextPoint = cache.points.getOrNull(completedPointIndex + 1) ?: return cache.totalDistance
        val segmentProgress = projectedSegmentDistance(cache.points[completedPointIndex], nextPoint, location)
        return (baseDistance + segmentProgress).coerceIn(0.0, cache.totalDistance)
    }

    private fun remainingDistance(route: ParkourRouteConfig, state: RunnerState, location: Location): Double {
        if (state.finishedAtMillis > 0L) return 0.0
        val world = location.world ?: return 0.0
        val cache = routeDistanceCache.takeIf { it.isFor(world, route) } ?: buildRouteDistanceCache(route, world)
        if (cache.points.size < 2) return 0.0
        val completedPointIndex = (state.checkpointIndex + 1).coerceIn(0, cache.points.lastIndex)
        val nextPointIndex = (completedPointIndex + 1).coerceAtMost(cache.points.lastIndex)
        val nextPoint = cache.points[nextPointIndex]
        val afterNext = cache.totalDistance - cache.cumulativeDistances.getOrElse(nextPointIndex) { cache.totalDistance }
        return (location.distanceFlat(nextPoint) + afterNext).coerceAtLeast(0.0)
    }

    private fun totalRouteDistance(): Double {
        if (routeDistanceCache.totalDistance > 0.0) return routeDistanceCache.totalDistance
        val world = room.world ?: return 0.0
        val route = routeConfig ?: return 0.0
        routeDistanceCache = buildRouteDistanceCache(route, world)
        return routeDistanceCache.totalDistance
    }

    private fun routePathPoints(route: ParkourRouteConfig, world: org.bukkit.World): List<Location> {
        val start = route.start?.spawn?.toLocation(world) ?: joinLocation ?: return emptyList()
        val checkpoints = route.checkpoints.map { it.respawn.toLocation(world) }
        val finish = route.finish?.region?.center(world)
        return listOfNotNull(start) + checkpoints + listOfNotNull(finish)
    }

    private fun resolveMap(): ParkourMapConfig? {
        val configuredMapId = room.configuredGame?.config?.getString("parkour.map-id")
        if (!configuredMapId.isNullOrBlank()) {
            config.maps[configuredMapId]?.let { return it }
        }
        val requested = room.mapTemplate
        return config.maps.values.firstOrNull { it.template == requested || it.id == requested?.substringAfterLast('/') }
            ?: config.firstMap()
    }

    private fun resolveRoute(map: ParkourMapConfig?): ParkourRouteConfig? {
        val routeId = room.configuredGame?.config?.getString("parkour.route-id")
        return routeId?.let { map?.routes?.get(it) } ?: map?.firstRoute()
    }

    private fun activeRunnerIds(): List<UUID> = room.players.toList()

    private fun restoreMovement(player: Player) {
        player.isInvisible = false
        player.isInvulnerable = false
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f
        states[player.uniqueId]?.frozen = false
    }

    private fun rewardPoints(elapsedMillis: Long, rank: Int): Int {
        val rewards = config.rewards
        if (!rewards.enabled) return 0
        val timePenalty = (elapsedMillis / 1000L).toInt() * rewards.timePenaltyPerSecond
        val rankBonus = rewards.rankBonus.getOrElse(rank - 1) { 0 }
        return max(rewards.minimumPoints, rewards.basePoints + rankBonus - timePenalty)
    }

    private fun recordUnfinishedLosses() {
        activeRunnerIds().forEach { playerId ->
            val state = states[playerId] ?: return@forEach
            if (state.resultRecorded) return@forEach
            resultService.recordLoss(room, playerId)
            state.resultRecorded = true
        }
    }

    private fun finishedCount(): Int = states.values.count { it.finishedAtMillis > 0L }

    private fun giveFinishedSpectatorHotbar(player: Player) {
        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = true
        player.isFlying = true
        player.isInvisible = true
        player.isInvulnerable = true
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f
        player.inventory.clear()
        player.inventory.setItem(0, spectatorItem(Material.COMPASS, language.getMessage("parkour.spectator_next"), FinishedSpectatorAction.NEXT_RUNNER))
        player.inventory.setItem(4, spectatorItem(Material.ENDER_PEARL, language.getMessage("parkour.spectator_return"), FinishedSpectatorAction.RETURN_TO_FINISH))
        player.inventory.setItem(8, spectatorItem(Material.BARRIER, language.getMessage("parkour.spectator_leave"), FinishedSpectatorAction.LEAVE_ROOM))
        player.updateInventory()
        player.sendActionBar(Component.text(language.getMessage("parkour.spectator_ready")))
    }

    private fun restoreFinishedSpectator(player: Player) {
        if ((states[player.uniqueId]?.finishedAtMillis ?: 0L) <= 0L) return
        FINISHED_SPECTATOR_SLOTS.forEach { slot ->
            if (spectatorAction(player.inventory.getItem(slot)) != null) {
                player.inventory.setItem(slot, null)
            }
        }
        player.isInvisible = false
        player.isInvulnerable = false
    }

    private fun followNextRunner(player: Player) {
        val targets = activeRunnerIds()
            .mapNotNull(Bukkit::getPlayer)
            .filter { it.uniqueId != player.uniqueId && (states[it.uniqueId]?.finishedAtMillis ?: 0L) <= 0L }
            .sortedBy { it.name.lowercase() }
        if (targets.isEmpty()) return player.sendActionBar(Component.text(language.getMessage("parkour.spectator_no_targets")))
        val currentIndex = targets.indexOfFirst { it.uniqueId == spectatorTargets[player.uniqueId] }
        val nextTarget = targets[Math.floorMod(currentIndex + 1, targets.size)]
        spectatorTargets[player.uniqueId] = nextTarget.uniqueId
        player.teleport(nextTarget.location.clone().add(0.0, 1.5, 0.0))
        player.sendActionBar(Component.text(language.getMessage("parkour.spectator_following", nextTarget.name)))
    }

    private fun returnToFinish(player: Player) {
        val world = room.world ?: return
        val route = routeConfig ?: return
        val location = route.finish?.region?.center(world)
            ?: route.lobby?.toLocation(world)
            ?: joinLocation
            ?: world.spawnLocation
        player.teleport(location)
        player.sendActionBar(Component.text(language.getMessage("parkour.spectator_returned")))
    }

    private fun spectatorItem(material: Material, name: String, action: FinishedSpectatorAction): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name))
                meta.persistentDataContainer.set(spectatorItemKey, PersistentDataType.STRING, action.name)
            }
        }
    }

    private fun spectatorAction(item: ItemStack?): FinishedSpectatorAction? {
        val value = item?.itemMeta?.persistentDataContainer?.get(spectatorItemKey, PersistentDataType.STRING) ?: return null
        return runCatching { FinishedSpectatorAction.valueOf(value) }.getOrNull()
    }

    private fun isFinishedSpectator(player: Player): Boolean {
        return (states[player.uniqueId]?.finishedAtMillis ?: 0L) > 0L
    }

    private fun rankColor(rank: Int): String {
        return when (rank) {
            1 -> "§6"
            2 -> "§e"
            3 -> "§a"
            else -> "§7"
        }
    }

    private fun broadcast(key: String, vararg args: Any) {
        val message = Component.text(language.getMessage(key, *args))
        room.players.mapNotNull(Bukkit::getPlayer).forEach { it.sendMessage(message) }
    }

    private fun formatDuration(millis: Long): String {
        val totalMillis = millis.coerceAtLeast(0L)
        val minutes = totalMillis / 60000L
        val seconds = (totalMillis % 60000L) / 1000L
        val ms = totalMillis % 1000L
        return "%02d:%02d.%03d".format(minutes, seconds, ms)
    }

    private fun Location.distanceFlat(other: Location): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun projectedSegmentDistance(start: Location, end: Location, point: Location): Double {
        val vx = end.x - start.x
        val vy = end.y - start.y
        val vz = end.z - start.z
        val segmentLengthSquared = vx * vx + vy * vy + vz * vz
        if (segmentLengthSquared <= 1.0E-9) return 0.0
        val wx = point.x - start.x
        val wy = point.y - start.y
        val wz = point.z - start.z
        val t = ((wx * vx + wy * vy + wz * vz) / segmentLengthSquared).coerceIn(0.0, 1.0)
        return sqrt(segmentLengthSquared) * t
    }

    private fun buildRouteDistanceCache(route: ParkourRouteConfig, world: org.bukkit.World): RouteDistanceCache {
        val points = routePathPoints(route, world)
        if (points.isEmpty()) return RouteDistanceCache.EMPTY
        var total = 0.0
        val cumulative = mutableListOf(0.0)
        points.zipWithNext().forEach { (from, to) ->
            total += from.distanceFlat(to)
            cumulative += total
        }
        return RouteDistanceCache(route.id, world.name, points, cumulative, total)
    }

    private fun visualBlockPoints(region: RegionSelection, world: org.bukkit.World, limit: Int): List<Location> {
        val points = linkedSetOf<BlockPoint>()
        for (x in region.minX..region.maxX) {
            points.add(BlockPoint(x, region.minY, region.minZ))
            points.add(BlockPoint(x, region.minY, region.maxZ))
            points.add(BlockPoint(x, region.maxY, region.minZ))
            points.add(BlockPoint(x, region.maxY, region.maxZ))
        }
        for (y in region.minY..region.maxY) {
            points.add(BlockPoint(region.minX, y, region.minZ))
            points.add(BlockPoint(region.minX, y, region.maxZ))
            points.add(BlockPoint(region.maxX, y, region.minZ))
            points.add(BlockPoint(region.maxX, y, region.maxZ))
        }
        for (z in region.minZ..region.maxZ) {
            points.add(BlockPoint(region.minX, region.minY, z))
            points.add(BlockPoint(region.minX, region.maxY, z))
            points.add(BlockPoint(region.maxX, region.minY, z))
            points.add(BlockPoint(region.maxX, region.maxY, z))
        }
        val step = if (points.size <= limit) 1 else (points.size / limit).coerceAtLeast(1)
        return points
            .asSequence()
            .withIndex()
            .filter { it.index % step == 0 }
            .take(limit)
            .map { (_, point) -> Location(world, point.x.toDouble(), point.y.toDouble(), point.z.toDouble()) }
            .toList()
    }

    private data class BlockPoint(val x: Int, val y: Int, val z: Int)

    private data class RunnerState(
        var respawn: Location?,
        var frozen: Boolean = false,
        var checkpointIndex: Int = -1,
        var startedAtMillis: Long = 0L,
        var finishedAtMillis: Long = 0L,
        var falls: Int = 0,
        var cachedDistance: Double = 0.0,
        var resultRecorded: Boolean = false,
        var awardedPoints: Int = 0,
        var fallDamageImmuneUntilMillis: Long = 0L,
        val splitTimes: MutableMap<String, Long> = linkedMapOf(),
        val buffCooldowns: MutableMap<String, Long> = linkedMapOf()
    )

    private data class RankedRunner(
        val playerId: UUID,
        val distance: Double,
        val finishedMillis: Long?,
        val rank: Int = 0
    )

    private data class TargetIndicator(
        val key: String,
        val center: Location,
        val material: Material,
        val particleColor: Color
    )

    private data class ActiveTargetVisual(
        val key: String,
        val entities: List<Entity>
    )

    private data class ArrowOffset(
        val x: Double,
        val y: Double,
        val z: Double
    )

    private enum class ParkourPhase {
        WAITING,
        COUNTDOWN,
        RUNNING,
        ENDING,
        RESULT,
        CLOSING
    }

    private enum class FinishedSpectatorAction {
        NEXT_RUNNER,
        RETURN_TO_FINISH,
        LEAVE_ROOM
    }

    private data class RouteDistanceCache(
        val routeId: String,
        val worldName: String,
        val points: List<Location>,
        val cumulativeDistances: List<Double>,
        val totalDistance: Double
    ) {
        fun isFor(world: org.bukkit.World, route: ParkourRouteConfig): Boolean = routeId == route.id && worldName == world.name

        companion object {
            val EMPTY = RouteDistanceCache("", "", emptyList(), emptyList(), 0.0)
        }
    }

    private companion object {
        const val FALL_DAMAGE_IMMUNITY_MILLIS = 2_000L
        const val TARGET_PARTICLE_REFRESH_TICKS = 10
        const val TARGET_CIRCLE_POINTS = 40
        const val TARGET_CIRCLE_RADIUS = 1.8
        const val TARGET_CIRCLE_Y_OFFSET = 0.12
        const val TARGET_ARROW_BLOCK_SCALE = 0.42f
        val TARGET_ARROW_OFFSETS = listOf(
            ArrowOffset(0.0, 4.0, 0.0),
            ArrowOffset(0.0, 3.55, 0.0),
            ArrowOffset(0.0, 3.10, 0.0),
            ArrowOffset(0.0, 2.65, 0.0),
            ArrowOffset(0.42, 3.10, 0.0),
            ArrowOffset(-0.42, 3.10, 0.0),
            ArrowOffset(0.0, 3.10, 0.42),
            ArrowOffset(0.0, 3.10, -0.42)
        )
        val FINISHED_SPECTATOR_SLOTS = setOf(0, 4, 8)
    }
}
