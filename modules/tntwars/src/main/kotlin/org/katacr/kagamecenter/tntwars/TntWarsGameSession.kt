package org.katacr.kagamecenter.tntwars

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Creeper
import org.bukkit.entity.Entity
import org.bukkit.entity.Fireball
import org.bukkit.entity.LargeFireball
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.minecart.ExplosiveMinecart
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.katacr.kaGameCenter.broadcast.RoomBroadcastService
import org.katacr.kaGameCenter.entity.RoomEntityOwnershipService
import org.katacr.kaGameCenter.display.SidebarBoardRenderer
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.game.GameState
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.phase.GamePhaseTimer
import org.katacr.kaGameCenter.reward.WeightedPool
import org.katacr.kaGameCenter.reward.WeightedRewardDistributor
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.team.GameTeam
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.task.RoomTaskService
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max

class TntWarsGameSession(
    private val plugin: JavaPlugin,
    override val room: GameRoom,
    private val configService: TntWarsConfigService,
    private val worldService: TemporaryWorldService,
    private val language: ModuleLanguage,
    private val roomManager: GameRoomManager,
    private val teamService: GameTeamService,
    private val teamAssignmentService: TeamAssignmentService,
    private val roomTaskService: RoomTaskService,
    private val entityOwnershipService: RoomEntityOwnershipService,
    private val resultService: GameResultService,
    private val playerRuntimeStateService: PlayerRuntimeStateService,
    private val roomBroadcastService: RoomBroadcastService
) : GameSession {
    private companion object {
        const val BLAST_ATTRIBUTION_MILLIS = 15_000L
    }

    override fun usesCustomScoreboard(): Boolean = true
    override fun usesCustomActionBar(): Boolean = true

    private val itemKey = NamespacedKey(plugin, "tntwars_item")
    private val states = linkedMapOf<UUID, TntWarsPlayerState>()
    private val activeRains = mutableSetOf<TntWarsItemType>()
    private val countdownTimer = GamePhaseTimer()
    private val remainingTimer = GamePhaseTimer()
    private val resultTimer = GamePhaseTimer()
    private val closeTimer = GamePhaseTimer()
    private var config: TntWarsConfig = configService.current()
    private var gameConfig: TntWarsGameConfig? = null
    private var phase = TntWarsPhase.WAITING
    private var itemTicks = 0
    private var scoreboardTicks = 0
    private var closed = false

    override fun onPrepare() {
        config = configService.reload()
        gameConfig = room.configuredGame?.let { configService.readManagedGame(it) }
        val worldName = "kgc_${room.id}"
        val template = room.mapTemplate ?: room.definition?.mapTemplates?.firstOrNull() ?: config.firstMap()?.template
        room.world = room.templateDirectory?.let {
            worldService.createRoomWorldFromDirectory(it, worldName, allowFlatFallback = false)
        } ?: worldService.createRoomWorldFromTemplate(template, worldName, allowFlatFallback = false)
        room.world?.let { world ->
            val spawn = gameConfig?.lobby?.toLocation(world)
                ?: gameConfig?.redSpawn?.toLocation(world)
                ?: worldService.readTemplateSpawn(template, world)
            world.spawnLocation = spawn
        }
        registerTeams()
    }

    override fun onPlayerJoin(player: Player) {
        val world = room.world ?: return
        playerRuntimeStateService.captureIfAbsent(room.id, player)
        val spawn = gameConfig?.lobby?.toLocation(world) ?: world.spawnLocation
        player.gameMode = GameMode.ADVENTURE
        player.inventory.clear()
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        player.foodLevel = 20
        player.health = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        player.teleport(spawn)
        assignWaitingTeam(player)
        player.sendMessage(Component.text(language.getMessage("tntwars.joined", room.id)))
    }

    override fun onPlayerLeave(player: Player) {
        if (phase == TntWarsPhase.RUNNING && states[player.uniqueId]?.alive == true) {
            eliminate(player, killerId = null, recordDeath = true)
        }
        states.remove(player.uniqueId)
        playerRuntimeStateService.restore(room.id, player)
        player.sendMessage(Component.text(language.getMessage("tntwars.left")))
    }

    override fun onSpectatorJoin(player: Player) {
        val world = room.world ?: return
        val spawn = gameConfig?.spectatorSpawn?.toLocation(world)
            ?: gameConfig?.lobby?.toLocation(world)
            ?: world.spawnLocation
        player.teleport(spawn)
    }

    override fun onStart() {
        val world = room.world ?: return
        val configured = gameConfig
        if (configured?.redSpawn == null || configured.blueSpawn == null || configured.playRegion == null) {
            broadcast("tntwars.config_missing")
            room.state = GameState.ENDING
            phase = TntWarsPhase.CLOSING
            closeTimer.resetTicks(60)
            return
        }
        registerTeams()
        balanceTeams()
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId] ?: assignWaitingTeam(player)
            val spawn = if (state.team == TntWarsTeam.RED) configured.redSpawn else configured.blueSpawn
            player.teleport(spawn.toLocation(world))
            player.gameMode = GameMode.ADVENTURE
            player.inventory.clear()
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            state.alive = true
            player.showTitle(Title.title(
                Component.text(language.getMessage("tntwars.start_title")),
                Component.text(language.getMessage(if (state.team == TntWarsTeam.RED) "tntwars.start_subtitle_red" else "tntwars.start_subtitle_blue")),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
            ))
        }
        countdownTimer.resetSeconds(config.startCountdownSeconds)
        remainingTimer.resetSeconds(config.durationSeconds)
        itemTicks = config.initialItemDelaySeconds * 20
        phase = TntWarsPhase.COUNTDOWN
        room.state = GameState.COUNTDOWN
    }

    override fun onTick() {
        when (phase) {
            TntWarsPhase.WAITING -> Unit
            TntWarsPhase.COUNTDOWN -> tickCountdown()
            TntWarsPhase.RUNNING -> tickRunning()
            TntWarsPhase.RESULT -> tickResult()
            TntWarsPhase.CLOSING -> tickClosing()
        }
    }

    override fun onPlayerDeath(player: Player) {
        eliminate(player, killerId = player.killer?.uniqueId, recordDeath = false, recordStats = false)
    }

    override fun onEnd() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            restore(player)
            player.sendMessage(Component.text(language.getMessage("tntwars.ended")))
        }
        cleanupExplosiveEntities()
    }

    override fun onClose() {
        states.clear()
        activeRains.clear()
        playerRuntimeStateService.clearRoom(room.id)
        roomTaskService.cancelRoom(room.id)
        entityOwnershipService.clearRoom(room.id)
    }

    fun handleMove(player: Player, to: Location) {
        if (phase != TntWarsPhase.RUNNING) return
        val state = states[player.uniqueId] ?: return
        if (!state.alive) return
        val configured = gameConfig ?: return
        if (to.y <= (configured.voidY ?: config.defaultVoidY)) {
            eliminate(player, recentBlastOwner(state), recordDeath = true)
            return
        }
        if (configured.playRegion?.contains(to, ignoreWorld = true) == false) {
            state.lastBlastOwner = null
            state.lastBlastAt = 0L
        }
    }

    fun handleInteract(event: PlayerInteractEvent): Boolean {
        val player = event.player
        if (phase != TntWarsPhase.RUNNING) return false
        val state = states[player.uniqueId] ?: return false
        if (!state.alive) return false
        val item = event.item ?: return false
        val type = readItemType(item) ?: return false
        val itemConfig = config.items[type]?.takeIf { it.enabled } ?: return true
        when (type) {
            TntWarsItemType.TNT_MINECART -> launchTntMinecarts(player, itemConfig)
            TntWarsItemType.TNT -> launchTnt(player, itemConfig)
            TntWarsItemType.LONG_TNT -> launchTnt(player, itemConfig)
            TntWarsItemType.CREEPER -> launchCreeper(player, itemConfig)
            TntWarsItemType.FIREBALL -> launchFireball(player, itemConfig)
            TntWarsItemType.TNT_BOW -> return false
            TntWarsItemType.TNT_RAIN -> if (!startRain(player, itemConfig, TntWarsItemType.TNT_RAIN)) return true
            TntWarsItemType.CREEPER_RAIN -> if (!startRain(player, itemConfig, TntWarsItemType.CREEPER_RAIN)) return true
            TntWarsItemType.FIREBALL_RAIN -> if (!startRain(player, itemConfig, TntWarsItemType.FIREBALL_RAIN)) return true
        }
        if (config.consumeOnUse && player.gameMode != GameMode.CREATIVE) {
            item.amount = item.amount - 1
        }
        return true
    }

    fun handleExplosion(entity: Entity, location: Location) {
        val record = entityOwnershipService.record(entity.uniqueId) ?: return
        if (record.roomId != room.id) return
        entityOwnershipService.remove(entity.uniqueId)
        val ownerId = record.ownerId
        val ownerTeam = states[ownerId]?.team ?: return
        val now = System.currentTimeMillis()
        room.players.mapNotNull(Bukkit::getPlayer)
            .filter { it.world == location.world }
            .filter { it.uniqueId != ownerId }
            .filter { states[it.uniqueId]?.alive == true }
            .filter { states[it.uniqueId]?.team != ownerTeam }
            .filter { it.location.distanceSquared(location) <= 16.0 * 16.0 }
            .forEach {
                states[it.uniqueId]?.lastBlastOwner = ownerId
                states[it.uniqueId]?.lastBlastAt = now
            }
    }

    fun handleBowShoot(event: EntityShootBowEvent): Boolean {
        val player = event.entity as? Player ?: return false
        if (phase != TntWarsPhase.RUNNING) return false
        if (states[player.uniqueId]?.alive != true) return false
        val type = event.bow?.let(::readItemType) ?: return false
        if (type != TntWarsItemType.TNT_BOW) return false
        if (config.items[type]?.enabled != true) return true
        val projectile = event.projectile
        projectile.addScoreboardTag("kgc_tntwars_arrow")
        entityOwnershipService.track(room.id, projectile, player.uniqueId, "tnt_bow_arrow")
        if (projectile is AbstractArrow) {
            projectile.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
        }
        event.setConsumeArrow(false)
        return true
    }

    fun handleProjectileHit(event: ProjectileHitEvent): Boolean {
        val projectile = event.entity
        val record = entityOwnershipService.record(projectile.uniqueId) ?: return false
        if (record.roomId != room.id || record.type != "tnt_bow_arrow") return false
        entityOwnershipService.remove(projectile.uniqueId)
        val ownerId = record.ownerId
        val owner = Bukkit.getPlayer(ownerId) ?: return false
        val itemConfig = config.items[TntWarsItemType.TNT_BOW] ?: return false
        val tnt = projectile.world.spawn(projectile.location, TNTPrimed::class.java) {
            it.fuseTicks = itemConfig.fuseTicks
            it.yield = itemConfig.power
            it.source = owner
            it.addScoreboardTag("kgc_tntwars")
        }
        entityOwnershipService.track(room.id, tnt, ownerId, "tnt_bow_tnt")
        projectile.remove()
        return true
    }

    private fun tickCountdown() {
        if (countdownTimer.isSecondBoundary) {
            val secondsLeft = max(1, countdownTimer.secondsLeft)
            room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
                player.showTitle(Title.title(
                    Component.text(language.getMessage("tntwars.countdown_title", secondsLeft)),
                    Component.text(language.getMessage("tntwars.countdown_subtitle")),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))
                ))
            }
        }
        if (!countdownTimer.tick()) return
        phase = TntWarsPhase.RUNNING
        room.state = GameState.RUNNING
        room.players.mapNotNull(Bukkit::getPlayer).forEach {
            it.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 40, config.resistanceAmplifier, true, false, false))
        }
    }

    private fun tickRunning() {
        itemTicks--
        scoreboardTicks++
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            if (states[player.uniqueId]?.alive == true) {
                player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 40, config.resistanceAmplifier, true, false, false))
                if (config.glowingEnabled) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false, false))
                }
            }
        }
        if (itemTicks <= 0) {
            giveRandomItems()
            itemTicks = config.itemIntervalSeconds * 20
        }
        if (scoreboardTicks >= 10) {
            scoreboardTicks = 0
            updateScoreboards()
        }
        checkWin()
        if (remainingTimer.tick() && phase == TntWarsPhase.RUNNING) {
            finish(null)
        }
    }

    private fun tickResult() {
        if (resultTimer.tick()) {
            phase = TntWarsPhase.CLOSING
            closeTimer.resetSeconds(config.closeDelaySeconds)
        }
    }

    private fun tickClosing() {
        if (closeTimer.tick() && !closed) {
            closed = true
            roomManager.closeRoom(room.id)
        }
    }

    private fun registerTeams() {
        teamAssignmentService.registerTeams(
            room.id,
            listOf(
                GameTeam(TntWarsTeam.RED.id, language.getMessage("tntwars.team_red"), NamedTextColor.RED),
                GameTeam(TntWarsTeam.BLUE.id, language.getMessage("tntwars.team_blue"), NamedTextColor.BLUE)
            )
        )
    }

    private fun assignWaitingTeam(player: Player): TntWarsPlayerState {
        val team = teamAssignmentService.joinSmallestTeam(
            room.id,
            player,
            listOf(TntWarsTeam.RED.id, TntWarsTeam.BLUE.id)
        )?.let { TntWarsTeam.fromId(it) } ?: TntWarsTeam.RED
        return states.getOrPut(player.uniqueId) { TntWarsPlayerState(team) }.also {
            it.team = team
            it.alive = true
        }
    }

    private fun balanceTeams() {
        val assignments = teamAssignmentService.assignRoundRobin(
            room.id,
            room.players,
            listOf(TntWarsTeam.RED.id, TntWarsTeam.BLUE.id)
        )
        assignments.forEach { (playerId, teamId) ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            val team = TntWarsTeam.fromId(teamId) ?: return@forEach
            states.getOrPut(player.uniqueId) { TntWarsPlayerState(team) }.team = team
        }
    }

    private fun giveRandomItems() {
        val pool = weightedItemPool()
        if (pool.isEmpty) return
        val players = room.players.mapNotNull(Bukkit::getPlayer)
            .filter { states[it.uniqueId]?.alive == true }
        WeightedRewardDistributor(pool).distribute(players, config.givePerPlayer) { player, type ->
            player.inventory.addItem(createItem(type))
            if (type == TntWarsItemType.TNT_BOW) {
                player.inventory.addItem(ItemStack(Material.ARROW, 1))
            }
            player.sendActionBar(Component.text(language.getMessage("tntwars.item_received", language.getMessage(type.languageKey))))
        }
    }

    private fun weightedItemPool(): WeightedPool<TntWarsItemType> {
        val weighted = config.items
            .filter { it.value.enabled && it.value.weight > 0 }
            .map { (type, itemConfig) -> WeightedPool.Entry(type, itemConfig.weight) }
        return WeightedPool(weighted)
    }

    private fun createItem(type: TntWarsItemType): ItemStack {
        val material = when (type) {
            TntWarsItemType.TNT_MINECART -> Material.TNT_MINECART
            TntWarsItemType.CREEPER -> Material.CREEPER_HEAD
            TntWarsItemType.FIREBALL -> Material.FIRE_CHARGE
            TntWarsItemType.TNT_BOW -> Material.BOW
            TntWarsItemType.TNT_RAIN,
            TntWarsItemType.CREEPER_RAIN,
            TntWarsItemType.FIREBALL_RAIN -> Material.TNT
            else -> Material.CARROT_ON_A_STICK
        }
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(language.getMessage(type.languageKey)))
                meta.persistentDataContainer.set(itemKey, PersistentDataType.STRING, type.configKey)
            }
        }
    }

    private fun readItemType(item: ItemStack): TntWarsItemType? {
        val value = item.itemMeta?.persistentDataContainer?.get(itemKey, PersistentDataType.STRING) ?: return null
        return TntWarsItemType.fromConfigKey(value)
    }

    private fun launchTnt(player: Player, itemConfig: TntWarsItemConfig) {
        val world = player.world
        val direction = player.eyeLocation.direction.normalize().multiply(itemConfig.velocity)
        val spawn = player.eyeLocation.add(direction.clone().normalize().multiply(1.2))
        val tnt = world.spawn(spawn, TNTPrimed::class.java) {
            it.fuseTicks = itemConfig.fuseTicks
            it.yield = itemConfig.power
            it.velocity = direction
            it.source = player
            it.addScoreboardTag("kgc_tntwars")
        }
        entityOwnershipService.track(room.id, tnt, player.uniqueId, "tnt")
    }

    private fun launchTntMinecarts(player: Player, itemConfig: TntWarsItemConfig) {
        val baseDirection = player.eyeLocation.direction.normalize()
        listOf(1.5, 3.0, 4.5).forEach { distance ->
            val spawn = player.eyeLocation.add(baseDirection.clone().multiply(distance))
            val minecart = player.world.spawn(spawn, ExplosiveMinecart::class.java) {
                it.fuseTicks = itemConfig.fuseTicks
                it.velocity = baseDirection.clone().multiply(itemConfig.velocity)
                it.addScoreboardTag("kgc_tntwars")
                it.ignite()
            }
            entityOwnershipService.track(room.id, minecart, player.uniqueId, "tnt_minecart")
        }
    }

    private fun launchCreeper(player: Player, itemConfig: TntWarsItemConfig) {
        val direction = player.eyeLocation.direction.normalize().multiply(itemConfig.velocity)
        val spawn = player.eyeLocation.add(direction.clone().normalize().multiply(1.2))
        val creeper = player.world.spawn(spawn, Creeper::class.java) {
            it.setMaxFuseTicks(itemConfig.fuseTicks)
            it.setFuseTicks(itemConfig.fuseTicks)
            it.setExplosionRadius(itemConfig.power.toInt().coerceAtLeast(1))
            it.velocity = direction
            it.addScoreboardTag("kgc_tntwars")
            it.ignite(player)
        }
        entityOwnershipService.track(room.id, creeper, player.uniqueId, "creeper")
    }

    private fun launchFireball(player: Player, itemConfig: TntWarsItemConfig) {
        val direction = player.eyeLocation.direction.normalize()
        val spawn = player.eyeLocation.add(direction.clone().multiply(1.2))
        val fireball = player.world.spawn(spawn, LargeFireball::class.java) {
            it.direction = direction.multiply(itemConfig.velocity)
            it.yield = itemConfig.power
            it.setIsIncendiary(false)
            it.shooter = player
            it.addScoreboardTag("kgc_tntwars")
        }
        entityOwnershipService.track(room.id, fireball, player.uniqueId, "fireball")
    }

    private fun startRain(player: Player, itemConfig: TntWarsItemConfig, type: TntWarsItemType): Boolean {
        val world = room.world ?: return false
        val region = gameConfig?.playRegion ?: return false
        if (!activeRains.add(type)) return false
        val totalTicks = itemConfig.durationSeconds * 20
        var elapsed = 0
        val taskRef = arrayOf<org.bukkit.scheduler.BukkitTask?>(null)
        taskRef[0] = roomTaskService.runTaskTimer(room.id, 0L, 20L, Runnable {
            if (phase != TntWarsPhase.RUNNING || elapsed >= totalTicks) {
                activeRains.remove(type)
                taskRef[0]?.cancel()
                return@Runnable
            }
            repeat(itemConfig.dropsPerSecond) {
                val x = ThreadLocalRandom.current().nextInt(region.minX, region.maxX + 1) + 0.5
                val z = ThreadLocalRandom.current().nextInt(region.minZ, region.maxZ + 1) + 0.5
                val y = region.maxY + 8.0
                val entity = when (type) {
                    TntWarsItemType.CREEPER_RAIN -> world.spawn(Location(world, x, y, z), Creeper::class.java) {
                        it.setMaxFuseTicks(itemConfig.fuseTicks)
                        it.setFuseTicks(itemConfig.fuseTicks)
                        it.setExplosionRadius(itemConfig.power.toInt().coerceAtLeast(1))
                        it.velocity = org.bukkit.util.Vector(0.0, -0.5, 0.0)
                        it.addScoreboardTag("kgc_tntwars")
                        it.ignite(player)
                    }
                    TntWarsItemType.FIREBALL_RAIN -> world.spawn(Location(world, x, y, z), LargeFireball::class.java) {
                        it.direction = org.bukkit.util.Vector(0.0, -0.8, 0.0)
                        it.yield = itemConfig.power
                        it.setIsIncendiary(false)
                        it.shooter = player
                        it.addScoreboardTag("kgc_tntwars")
                    }
                    else -> world.spawn(Location(world, x, y, z), TNTPrimed::class.java) {
                        it.fuseTicks = itemConfig.fuseTicks
                        it.yield = itemConfig.power
                        it.velocity = org.bukkit.util.Vector(0.0, -0.8, 0.0)
                        it.source = player
                        it.addScoreboardTag("kgc_tntwars")
                    }
                }
                entityOwnershipService.track(room.id, entity, player.uniqueId, type.configKey)
            }
            elapsed += 20
        })
        return true
    }

    private fun eliminate(player: Player, killerId: UUID?, recordDeath: Boolean, recordStats: Boolean = true) {
        val state = states[player.uniqueId] ?: return
        if (!state.alive) return
        state.alive = false
        player.gameMode = GameMode.SPECTATOR
        player.inventory.clear()
        gameConfig?.spectatorSpawn?.toLocation(room.world ?: player.world)?.let(player::teleport)
        val killer = killerId?.let(Bukkit::getPlayer)
            ?.takeIf { it.uniqueId != player.uniqueId && states[it.uniqueId]?.alive == true }
        if (killer != null) {
            if (recordStats) resultService.recordKill(room, killer.uniqueId, player.uniqueId, points = 1)
            broadcastRaw(language.getMessage("tntwars.eliminated_by", player.name, killer.name, teamName(state.team), aliveCount(state.team)))
        } else {
            if (recordStats && recordDeath) resultService.recordDeath(room, player.uniqueId)
            broadcastRaw(language.getMessage("tntwars.eliminated", player.name, teamName(state.team), aliveCount(state.team)))
        }
        checkWin()
    }

    private fun checkWin() {
        if (phase != TntWarsPhase.RUNNING) return
        val redAlive = aliveCount(TntWarsTeam.RED)
        val blueAlive = aliveCount(TntWarsTeam.BLUE)
        when {
            redAlive <= 0 && blueAlive <= 0 -> finish(null)
            redAlive <= 0 -> finish(TntWarsTeam.BLUE)
            blueAlive <= 0 -> finish(TntWarsTeam.RED)
        }
    }

    private fun finish(winner: TntWarsTeam?) {
        if (phase == TntWarsPhase.RESULT || phase == TntWarsPhase.CLOSING) return
        phase = TntWarsPhase.RESULT
        room.state = GameState.ENDING
        resultTimer.resetSeconds(config.resultDisplaySeconds)
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            val state = states[player.uniqueId]
            if (winner == null) {
                player.showTitle(Title.title(Component.text(language.getMessage("tntwars.draw_title")), Component.empty()))
            } else {
                val won = state?.team == winner
                if (won) resultService.recordWin(room, player.uniqueId, points = 3) else resultService.recordLoss(room, player.uniqueId)
                player.showTitle(Title.title(
                    Component.text(language.getMessage(if (won) "tntwars.win_title" else "tntwars.lose_title")),
                    Component.text(language.getMessage("tntwars.win_subtitle", teamName(winner)))
                ))
            }
            player.gameMode = GameMode.SPECTATOR
        }
        cleanupExplosiveEntities()
    }

    private fun updateScoreboards() {
        room.players.mapNotNull(Bukkit::getPlayer).forEach { player ->
            SidebarBoardRenderer.show(
                player = player,
                objectiveId = "tntwars_${room.id}",
                title = Component.text("TNT Wars"),
                lines = listOf(
                    "§c红队: §f${aliveCount(TntWarsTeam.RED)}",
                    "§9蓝队: §f${aliveCount(TntWarsTeam.BLUE)}",
                    "§e道具: §f${max(0, itemTicks / 20)}s",
                    "§7房间: §f${room.id}"
                )
            )
        }
    }

    private fun restore(player: Player) {
        SidebarBoardRenderer.clear(player)
        if (playerRuntimeStateService.restore(room.id, player)) return
        player.removePotionEffect(PotionEffectType.RESISTANCE)
        player.removePotionEffect(PotionEffectType.GLOWING)
    }

    private fun recentBlastOwner(state: TntWarsPlayerState): UUID? {
        val ownerId = state.lastBlastOwner ?: return null
        val age = System.currentTimeMillis() - state.lastBlastAt
        if (age in 0..BLAST_ATTRIBUTION_MILLIS) return ownerId
        state.lastBlastOwner = null
        state.lastBlastAt = 0L
        return null
    }

    private fun cleanupExplosiveEntities() {
        room.world?.entities
            ?.filter { it !is Player }
            ?.filter { it.scoreboardTags.contains("kgc_tntwars") || it.scoreboardTags.contains("kgc_tntwars_arrow") }
            ?.forEach(Entity::remove)
    }

    private fun aliveCount(team: TntWarsTeam): Int {
        return states.values.count { it.team == team && it.alive }
    }

    private fun teamName(team: TntWarsTeam): String = language.getMessage(team.languageKey)

    private fun broadcast(key: String, vararg args: Any) {
        roomBroadcastService.localized(room, language, key, *args, includeSpectators = true)
    }

    private fun broadcastRaw(message: String) {
        roomBroadcastService.message(room, message, includeSpectators = true)
    }
}
