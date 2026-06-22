package org.katacr.kagamecenter.parkour

import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
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
import kotlin.math.max
import kotlin.math.sqrt

class ParkourGameSession(
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
                ?: world.spawnLocation
            joinLocation?.let { world.spawnLocation = it }
        }
    }

    override fun onPlayerJoin(player: Player) {
        val world = room.world ?: return
        val route = routeConfig
        val spawn = route?.lobby?.toLocation(world) ?: joinLocation ?: world.spawnLocation
        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = false
        player.isFlying = false
        player.inventory.clear()
        player.teleport(spawn)
        states[player.uniqueId] = RunnerState(
            respawn = route?.start?.spawn?.toLocation(world) ?: spawn.clone()
        )
        player.sendMessage(Component.text(language.getMessage("parkour.joined", room.id)))
    }

    override fun onPlayerLeave(player: Player) {
        states.remove(player.uniqueId)
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
            player.walkSpeed = 0f
            player.flySpeed = 0f
            states.computeIfAbsent(player.uniqueId) { RunnerState(start.clone()) }.apply {
                respawn = start.clone()
                frozen = true
                checkpointIndex = -1
                startedAtMillis = 0L
                finishedAtMillis = 0L
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
            restoreMovement(player)
            packetService.clearViewer(player)
            player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
            player.sendMessage(Component.text(language.getMessage("parkour.ended", room.id)))
        }
    }

    override fun onClose() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { restoreMovement(it) }
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
        if (glowTick >= max(10, config.checkpointGlowSeconds * 10)) {
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
        val points = max(1, 1000 - (elapsedMillis / 1000L).toInt() * 10)
        resultService.recordWin(room, player.uniqueId, points)
        packetService.clearViewer(player)
        player.allowFlight = true
        player.isFlying = true
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
            val remaining = max(0.0, totalRouteDistance(route) - (ownRank?.distance ?: 0.0))
            val lines = mutableListOf<String>()
            lines += "§e${config.displayName}"
            lines += "§7用时 §f${formatDuration(elapsed)}"
            lines += "§8────────"
            ranked.take(5).forEach { entry ->
                val name = Bukkit.getOfflinePlayer(entry.playerId).name ?: entry.playerId.toString().take(6)
                val value = entry.finishedMillis?.let { formatDuration(it) } ?: "%.1fm".format(entry.distance)
                lines += "§${entry.rank}#$${entry.rank} §f$name §a$value".replace("$", "")
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
        if (!packetService.available) return
        val world = player.world
        val region = nextGlowRegion(routeConfig ?: return, state) ?: return
        val color = parseNamedTextColor(config.checkpointGlowColor, net.kyori.adventure.text.format.NamedTextColor.YELLOW)
        visualBlockPoints(region, world, 64).forEach { location ->
            packetService.showBlockGlow(player, location, config.checkpointGlowSeconds, color)
        }
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

    private fun teleportToCheckpoint(player: Player) {
        val state = states[player.uniqueId] ?: return
        val location = state.respawn ?: joinLocation ?: return
        player.teleport(location)
        player.sendActionBar(Component.text(language.getMessage("parkour.checkpoint_returned")))
    }

    private fun rankings(): List<RankedRunner> {
        val route = routeConfig ?: return emptyList()
        return activeRunnerIds().mapNotNull { playerId ->
            val player = Bukkit.getPlayer(playerId)
            val state = states[playerId] ?: return@mapNotNull null
            val distance = if (player != null) currentDistance(route, state, player.location) else state.cachedDistance
            state.cachedDistance = distance
            RankedRunner(playerId, distance, state.finishedAtMillis.takeIf { it > 0L }?.minus(startedAtMillis) ?: null)
        }.sortedWith(compareBy<RankedRunner> { it.finishedMillis == null }
            .thenBy { it.finishedMillis ?: Long.MAX_VALUE }
            .thenByDescending { it.distance })
            .mapIndexed { index, ranked -> ranked.copy(rank = index + 1) }
    }

    private fun currentDistance(route: ParkourRouteConfig, state: RunnerState, location: Location): Double {
        val points = routePathPoints(route, location.world ?: return 0.0)
        if (points.isEmpty()) return 0.0
        val completedIndex = (state.checkpointIndex + 1).coerceAtLeast(0)
        var distance = 0.0
        for (index in 0 until completedIndex.coerceAtMost(points.lastIndex)) {
            distance += points[index].distanceFlat(points[index + 1])
        }
        val currentBase = points.getOrNull(completedIndex) ?: points.last()
        distance += currentBase.distanceFlat(location)
        return distance.coerceAtMost(totalRouteDistance(route))
    }

    private fun totalRouteDistance(route: ParkourRouteConfig): Double {
        val world = room.world ?: return 0.0
        val points = routePathPoints(route, world)
        return points.zipWithNext().sumOf { (a, b) -> a.distanceFlat(b) }
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
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f
        states[player.uniqueId]?.frozen = false
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

    private fun visualBlockPoints(region: RegionSelection, world: org.bukkit.World, limit: Int): List<Location> {
        return region.edgeLocations(world, limit * 2)
            .distinctBy { Triple(it.blockX, it.blockY, it.blockZ) }
            .take(limit)
    }

    private data class RunnerState(
        var respawn: Location?,
        var frozen: Boolean = false,
        var checkpointIndex: Int = -1,
        var startedAtMillis: Long = 0L,
        var finishedAtMillis: Long = 0L,
        var falls: Int = 0,
        var cachedDistance: Double = 0.0,
        val splitTimes: MutableMap<String, Long> = linkedMapOf(),
        val buffCooldowns: MutableMap<String, Long> = linkedMapOf()
    )

    private data class RankedRunner(
        val playerId: UUID,
        val distance: Double,
        val finishedMillis: Long?,
        val rank: Int = 0
    )

    private enum class ParkourPhase {
        WAITING,
        COUNTDOWN,
        RUNNING,
        ENDING,
        RESULT,
        CLOSING
    }
}
