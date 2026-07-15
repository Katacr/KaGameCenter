package org.katacr.kaGameCenter.packet

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCollectItem
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemStack as BukkitItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaGameCenter.nametag.NametagCollisionRule
import org.katacr.kaGameCenter.nametag.NametagVisibility
import org.katacr.kaGameCenter.nametag.PlayerNametag
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PacketEventsDispatchService(
    private val plugin: JavaPlugin
) : PacketDispatchService {
    private val entityIds = AtomicInteger(2_000_000_000)
    private val visualsByViewer = ConcurrentHashMap<UUID, MutableList<ActiveVisual>>()
    private val entityMetadataOverlays = ConcurrentHashMap<ViewerEntityKey, EntityOverlay>()
    private var packetListener: PacketListenerCommon? = null

    override val backendName: String = "PacketEvents"
    override val available: Boolean
        get() = Bukkit.getPluginManager().isPluginEnabled("packetevents") ||
            Bukkit.getPluginManager().isPluginEnabled("PacketEvents")

    override fun init() {
        if (available) {
            registerPacketListener()
            plugin.logger.info("PacketEvents backend is available for KaGameCenter visual packets.")
        } else {
            plugin.logger.info("PacketEvents is not installed; visual packet features will stay disabled.")
        }
    }

    override fun shutdown() {
        visualsByViewer.keys.mapNotNull(Bukkit::getPlayer).forEach(::clearViewer)
        visualsByViewer.clear()
        entityMetadataOverlays.clear()
        packetListener?.let { PacketEvents.getAPI().eventManager.unregisterListener(it) }
        packetListener = null
    }

    override fun clearViewer(player: Player) {
        val visuals = visualsByViewer.remove(player.uniqueId) ?: return
        visuals.forEach { it.clear(player) }
    }

    override fun disguisePlayerAsBlock(
        target: Player,
        material: Material,
        viewers: Collection<Player>,
        durationSeconds: Int
    ) {
        if (!available) return
        viewers.filter { it.isOnline }.forEach { viewer ->
            val display = spawnViewerBlockDisplay(viewer, target.location, material)
            setEntityOverlay(viewer, target, invisible = true)
            send(viewer, WrapperPlayServerEntityMetadata(target.entityId, playerEntityFlags(target, invisible = true)))

            val followTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                if (!target.isOnline || !viewer.isOnline || target.world != viewer.world) return@Runnable
                display.teleport(fixedRotationLocation(target.location))
            }, 1L, 2L)

            track(
                viewer,
                ActiveVisual(
                    disguiseTargetId = target.uniqueId,
                    tasks = listOf(followTask),
                    removeEntities = listOf(display),
                    restore = {
                        clearEntityOverlay(viewer, target)
                        restoreEntityMetadata(viewer, target)
                    }
                ),
                durationSeconds
            )
        }
    }

    override fun disguisePlayerAsMob(
        target: Player,
        entityType: EntityType,
        viewers: Collection<Player>,
        durationSeconds: Int
    ) {
        if (!available) return
        val packetType = runCatching { SpigotConversionUtil.fromBukkitEntityType(entityType) }.getOrNull()
        if (packetType == null || !packetType.isInstanceOf(EntityTypes.LIVINGENTITY)) return

        viewers.filter { it.isOnline }.forEach { viewer ->
            val fakeEntityId = nextEntityId()
            val fakeEntityUuid = UUID.randomUUID()
            val teamName = teamName(fakeEntityId)
            val spawn = WrapperPlayServerSpawnEntity(
                fakeEntityId,
                Optional.of(fakeEntityUuid),
                packetType,
                vector(target.location),
                target.location.pitch,
                target.location.yaw,
                target.location.yaw,
                0,
                Optional.of(Vector3d.zero())
            )
            sendNoCollisionTeam(viewer, teamName, target.name, fakeEntityUuid.toString())
            setEntityOverlay(viewer, target, invisible = true)
            send(viewer, WrapperPlayServerEntityMetadata(target.entityId, playerEntityFlags(target, invisible = true)))
            send(viewer, spawn)
            send(viewer, WrapperPlayServerEntityMetadata(fakeEntityId, entityFlags(noGravity = true)))

            var lastLocation = target.location.clone()
            val followTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                if (!target.isOnline || !viewer.isOnline || target.world != viewer.world) return@Runnable
                lastLocation = sendSmoothDisguiseTransform(viewer, fakeEntityId, target, lastLocation)
            }, 1L, 2L)

            track(
                viewer,
                ActiveVisual(
                    disguiseTargetId = target.uniqueId,
                    entityIds = listOf(fakeEntityId),
                    teamNames = listOf(teamName),
                    tasks = listOf(followTask),
                    restore = {
                        clearEntityOverlay(viewer, target)
                        restoreEntityMetadata(viewer, target)
                    }
                ),
                durationSeconds
            )
        }
    }

    override fun clearDisguise(target: Player, viewers: Collection<Player>) {
        if (!available) return
        viewers.filter { it.isOnline }.forEach { viewer ->
            visualsByViewer[viewer.uniqueId]
                ?.toList()
                ?.filter { it.disguiseTargetId == target.uniqueId }
                ?.forEach { removeVisual(viewer, it) }
        }
    }

    override fun showBlockGlow(viewer: Player, location: Location, durationSeconds: Int, color: NamedTextColor) {
        if (!available || !viewer.isOnline) return
        val block = location.block
        val display = location.world.spawn(block.location, BlockDisplay::class.java) { entity ->
            entity.setBlock(previewMaterial(color).createBlockData())
            entity.setVisibleByDefault(false)
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
            entity.isGlowing = true
        }
        viewer.showEntity(plugin, display)
        val teamName = teamName(display.entityId)
        sendGlowTeam(viewer, teamName, display.uniqueId.toString(), color)
        track(
            viewer,
            ActiveVisual(
                entityIds = listOf(display.entityId),
                teamNames = listOf(teamName),
                removeEntities = listOf(display)
            ),
            durationSeconds
        )
    }

    private fun previewMaterial(color: NamedTextColor): Material {
        return when (color) {
            NamedTextColor.BLACK -> Material.BLACK_STAINED_GLASS
            NamedTextColor.DARK_BLUE, NamedTextColor.BLUE -> Material.BLUE_STAINED_GLASS
            NamedTextColor.DARK_GREEN, NamedTextColor.GREEN -> Material.GREEN_STAINED_GLASS
            NamedTextColor.DARK_AQUA, NamedTextColor.AQUA -> Material.CYAN_STAINED_GLASS
            NamedTextColor.DARK_RED, NamedTextColor.RED -> Material.RED_STAINED_GLASS
            NamedTextColor.DARK_PURPLE, NamedTextColor.LIGHT_PURPLE -> Material.PURPLE_STAINED_GLASS
            NamedTextColor.GOLD, NamedTextColor.YELLOW -> Material.YELLOW_STAINED_GLASS
            NamedTextColor.GRAY, NamedTextColor.DARK_GRAY -> Material.GRAY_STAINED_GLASS
            NamedTextColor.WHITE -> Material.WHITE_STAINED_GLASS
            else -> Material.YELLOW_STAINED_GLASS
        }
    }

    override fun showPlayerGlow(viewer: Player, target: Player, durationSeconds: Int) {
        if (!available || !viewer.isOnline || !target.isOnline) return
        val teamName = teamName(target.entityId)
        sendGlowTeam(viewer, teamName, target.name, NamedTextColor.AQUA)
        setEntityOverlay(viewer, target, glowing = true)
        send(viewer, WrapperPlayServerEntityMetadata(target.entityId, playerEntityFlags(target, glowing = true)))

        track(
            viewer,
            ActiveVisual(
                teamNames = listOf(teamName),
                restore = {
                    clearEntityOverlay(viewer, target)
                    restoreEntityMetadata(viewer, target)
                }
            ),
            durationSeconds
        )
    }

    override fun showPrivateDrop(viewer: Player, location: Location, itemStack: ItemStack, durationSeconds: Int) {
        showPrivatePickup(viewer, location, itemStack, glowing = false, color = NamedTextColor.WHITE, durationSeconds = durationSeconds, scale = 1.8f) { player ->
            val leftover = player.inventory.addItem(itemStack.clone())
            if (leftover.isNotEmpty()) return@showPrivatePickup
        }
    }

    override fun showPrivatePickup(
        viewer: Player,
        location: Location,
        itemStack: ItemStack,
        glowing: Boolean,
        color: NamedTextColor,
        durationSeconds: Int,
        scale: Float,
        onPickup: (Player) -> Unit
    ) {
        if (!available || !viewer.isOnline) return
        val visualScale = scale.coerceIn(0.25f, 8.0f)
        val display = location.world.spawn(location.clone().add(0.0, 0.35, 0.0), ItemDisplay::class.java) { entity ->
            entity.setItemStack(itemStack.clone())
            entity.setVisibleByDefault(false)
            entity.viewRange = 64f
            entity.isPersistent = false
            entity.transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                Quaternionf(),
                Vector3f(visualScale, visualScale, visualScale),
                Quaternionf()
            )
            entity.teleportDuration = 2
            entity.interpolationDuration = 2
            entity.isGlowing = glowing
        }
        viewer.showEntity(plugin, display)
        val teamName = if (glowing) teamName(display.entityId) else null
        if (teamName != null) sendGlowTeam(viewer, teamName, display.uniqueId.toString(), color)

        val pickupTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!viewer.isOnline || viewer.world != location.world) return@Runnable
            if (viewer.location.distanceSquared(location) > 2.25) return@Runnable
            onPickup(viewer)
            removeVisual(viewer, display.entityId)
        }, 5L, 5L)

        track(
            viewer,
            ActiveVisual(
                entityIds = listOf(display.entityId),
                teamNames = listOfNotNull(teamName),
                tasks = listOf(pickupTask),
                removeEntities = listOf(display)
            ),
            durationSeconds
        )
    }

    override fun showBeaconBeam(viewer: Player, location: Location, color: NamedTextColor, durationSeconds: Int) {
        if (!available || !viewer.isOnline) return
        val material = previewMaterial(color)
        val displays = (0 until 8).map { index ->
            location.world.spawn(location.clone().add(0.0, index.toDouble(), 0.0), BlockDisplay::class.java) { entity ->
                entity.setBlock(material.createBlockData())
                entity.setVisibleByDefault(false)
                entity.viewRange = 96f
                entity.shadowRadius = 0f
                entity.shadowStrength = 0f
                entity.isPersistent = false
                entity.transformation = Transformation(
                    Vector3f(-0.2f, 0f, -0.2f),
                    Quaternionf(),
                    Vector3f(0.4f, 1f, 0.4f),
                    Quaternionf()
                )
            }.also { viewer.showEntity(plugin, it) }
        }
        track(viewer, ActiveVisual(removeEntities = displays), durationSeconds)
    }

    override fun showProbe(viewer: Player, message: String) {
        if (!available || !viewer.isOnline) return
        send(viewer, WrapperPlayServerActionBar(Component.text(message, NamedTextColor.YELLOW)))
    }

    override fun sendPlayerNametag(viewer: Player, teamName: String, targetName: String, nametag: PlayerNametag) {
        if (!available || !viewer.isOnline) return
        val info = WrapperPlayServerTeams.ScoreBoardTeamInfo(
            Component.text(teamName),
            nametag.prefix,
            nametag.suffix,
            nametag.visibility.toPacketVisibility(),
            nametag.collisionRule.toPacketCollisionRule(),
            nametag.color,
            WrapperPlayServerTeams.OptionData.NONE
        )
        send(viewer, WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, info, targetName))
    }

    override fun clearNametagTeam(viewer: Player, teamName: String) {
        if (!available || !viewer.isOnline) return
        send(
            viewer,
            WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.REMOVE,
                null as WrapperPlayServerTeams.ScoreBoardTeamInfo?
            )
        )
    }

    private fun track(viewer: Player, visual: ActiveVisual, durationSeconds: Int) {
        visualsByViewer.computeIfAbsent(viewer.uniqueId) { mutableListOf() }.add(visual)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            removeVisual(viewer, visual)
        }, durationSeconds.coerceAtLeast(1) * 20L)
    }

    private fun registerPacketListener() {
        if (packetListener != null) return
        packetListener = PacketEvents.getAPI().eventManager.registerListener(
            object : PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
                override fun onPacketSend(event: PacketSendEvent) {
                    if (event.packetType != PacketType.Play.Server.ENTITY_METADATA) return
                    val viewer = event.getPlayer<Player>()
                    val wrapper = runCatching { WrapperPlayServerEntityMetadata(event) }.getOrNull() ?: return
                    val overlay = entityMetadataOverlays[ViewerEntityKey(viewer.uniqueId, wrapper.entityId)] ?: return
                    val metadata = applyOverlay(wrapper.entityMetadata, overlay)
                    wrapper.entityMetadata = metadata
                    event.markForReEncode(true)
                }
            }
        )
    }

    private fun removeVisual(viewer: Player, entityId: Int) {
        val visuals = visualsByViewer[viewer.uniqueId] ?: return
        val visual = visuals.firstOrNull { entityId in it.entityIds } ?: return
        removeVisual(viewer, visual)
    }

    private fun removeVisual(viewer: Player, visual: ActiveVisual) {
        visualsByViewer[viewer.uniqueId]?.remove(visual)
        visual.clear(viewer)
    }

    private fun ActiveVisual.clear(viewer: Player) {
        tasks.forEach(BukkitTask::cancel)
        removeEntities.forEach { entity ->
            if (!entity.isDead) entity.remove()
        }
        if (viewer.isOnline) {
            if (entityIds.isNotEmpty()) {
                send(viewer, WrapperPlayServerDestroyEntities(*entityIds.toIntArray()))
            }
            teamNames.forEach { team ->
                send(
                    viewer,
                    WrapperPlayServerTeams(
                        team,
                        WrapperPlayServerTeams.TeamMode.REMOVE,
                        null as WrapperPlayServerTeams.ScoreBoardTeamInfo?
                    )
                )
            }
            restore.invoke()
        }
    }

    private fun restoreEntityMetadata(viewer: Player, target: Player) {
        if (!viewer.isOnline || !target.isOnline) return
        val metadata = runCatching { SpigotConversionUtil.getEntityMetadata(target) }.getOrNull() ?: return
        send(viewer, WrapperPlayServerEntityMetadata(target.entityId, metadata))
    }

    private fun sendDisguiseTransform(viewer: Player, fakeEntityId: Int, target: Player) {
        val location = target.location
        val yaw = location.yaw
        val pitch = location.pitch
        send(viewer, WrapperPlayServerEntityTeleport(fakeEntityId, vector(location), yaw, pitch, target.isOnGround))
        send(viewer, WrapperPlayServerEntityRotation(fakeEntityId, yaw, pitch, target.isOnGround))
        send(viewer, WrapperPlayServerEntityHeadLook(fakeEntityId, yaw))
    }

    private fun spawnViewerBlockDisplay(viewer: Player, location: Location, material: Material): BlockDisplay {
        val display = location.world.spawn(fixedRotationLocation(location), BlockDisplay::class.java) { entity ->
            entity.setBlock(material.createBlockData())
            entity.setVisibleByDefault(false)
            entity.teleportDuration = 2
            entity.interpolationDuration = 2
            entity.viewRange = 64f
            entity.shadowRadius = 0f
            entity.shadowStrength = 0f
            entity.transformation = Transformation(
                Vector3f(-0.5f, 0f, -0.5f),
                Quaternionf(),
                Vector3f(1f, 1f, 1f),
                Quaternionf()
            )
            entity.isPersistent = false
        }
        viewer.showEntity(plugin, display)
        return display
    }

    private fun fixedRotationLocation(location: Location): Location {
        return location.clone().apply {
            yaw = 0f
            pitch = 0f
        }
    }

    private fun sendSmoothDisguiseTransform(
        viewer: Player,
        fakeEntityId: Int,
        target: Player,
        previousLocation: Location
    ): Location {
        val location = target.location
        val yaw = location.yaw
        val pitch = location.pitch
        val deltaX = location.x - previousLocation.x
        val deltaY = location.y - previousLocation.y
        val deltaZ = location.z - previousLocation.z

        if (canUseRelativeMove(location, previousLocation, deltaX, deltaY, deltaZ)) {
            send(
                viewer,
                WrapperPlayServerEntityRelativeMoveAndRotation(
                    fakeEntityId,
                    deltaX,
                    deltaY,
                    deltaZ,
                    yaw,
                    pitch,
                    target.isOnGround
                )
            )
        } else {
            send(viewer, WrapperPlayServerEntityTeleport(fakeEntityId, vector(location), yaw, pitch, target.isOnGround))
        }

        send(viewer, WrapperPlayServerEntityRotation(fakeEntityId, yaw, pitch, target.isOnGround))
        send(viewer, WrapperPlayServerEntityHeadLook(fakeEntityId, yaw))
        return location.clone()
    }

    private fun canUseRelativeMove(
        location: Location,
        previousLocation: Location,
        deltaX: Double,
        deltaY: Double,
        deltaZ: Double
    ): Boolean {
        if (location.world != previousLocation.world) return false
        return kotlin.math.abs(deltaX) < 7.9 &&
            kotlin.math.abs(deltaY) < 7.9 &&
            kotlin.math.abs(deltaZ) < 7.9
    }

    private fun sendGlowTeam(viewer: Player, teamName: String, entityName: String, color: NamedTextColor) {
        val info = WrapperPlayServerTeams.ScoreBoardTeamInfo(
            Component.text(teamName),
            Component.empty(),
            Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
            WrapperPlayServerTeams.CollisionRule.NEVER,
            color,
            WrapperPlayServerTeams.OptionData.NONE
        )
        send(viewer, WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, info, entityName))
    }

    private fun sendNoCollisionTeam(viewer: Player, teamName: String, vararg entityNames: String) {
        val info = WrapperPlayServerTeams.ScoreBoardTeamInfo(
            Component.text(teamName),
            Component.empty(),
            Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
            WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE,
            WrapperPlayServerTeams.OptionData.NONE
        )
        send(viewer, WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, info, *entityNames))
    }

    private fun entityFlags(
        invisible: Boolean = false,
        glowing: Boolean = false,
        noGravity: Boolean = false
    ): List<EntityData<*>> {
        var flags = 0
        if (invisible) flags = flags or 0x20
        if (glowing) flags = flags or 0x40
        val metadata = mutableListOf<EntityData<*>>(EntityData(0, EntityDataTypes.BYTE, flags.toByte()))
        if (noGravity) metadata.add(EntityData(5, EntityDataTypes.BOOLEAN, true))
        return metadata
    }

    private fun setEntityOverlay(
        viewer: Player,
        target: Player,
        invisible: Boolean = false,
        glowing: Boolean = false
    ) {
        entityMetadataOverlays[ViewerEntityKey(viewer.uniqueId, target.entityId)] = EntityOverlay(invisible, glowing)
    }

    private fun clearEntityOverlay(viewer: Player, target: Player) {
        entityMetadataOverlays.remove(ViewerEntityKey(viewer.uniqueId, target.entityId))
    }

    private fun applyOverlay(metadata: List<EntityData<*>>, overlay: EntityOverlay): List<EntityData<*>> {
        var foundFlags = false
        val updated = metadata.map { data ->
            if (data.index != 0) return@map data
            foundFlags = true
            EntityData(0, EntityDataTypes.BYTE, applyFlagOverlay(data.value, overlay).toByte())
        }.toMutableList()
        if (!foundFlags) {
            updated.add(EntityData(0, EntityDataTypes.BYTE, applyFlagOverlay(0, overlay).toByte()))
        }
        return updated
    }

    private fun applyFlagOverlay(value: Any?, overlay: EntityOverlay): Int {
        var flags = when (value) {
            is Byte -> value.toInt() and 0xFF
            is Number -> value.toInt() and 0xFF
            else -> 0
        }
        if (overlay.invisible) flags = flags or 0x20
        if (overlay.glowing) flags = flags or 0x40
        return flags
    }

    private fun playerEntityFlags(
        target: Player,
        invisible: Boolean = false,
        glowing: Boolean = false
    ): List<EntityData<*>> {
        var flags = currentEntityFlags(target)
        if (invisible) flags = flags or 0x20
        if (glowing) flags = flags or 0x40
        return listOf(EntityData(0, EntityDataTypes.BYTE, flags.toByte()))
    }

    private fun currentEntityFlags(target: Player): Int {
        val metadata = runCatching { SpigotConversionUtil.getEntityMetadata(target) }.getOrNull() ?: return 0
        val value = metadata.firstOrNull { it.index == 0 }?.value
        return when (value) {
            is Byte -> value.toInt() and 0xFF
            is Number -> value.toInt() and 0xFF
            else -> 0
        }
    }

    private fun vector(location: Location): Vector3d {
        return Vector3d(location.x, location.y, location.z)
    }

    private fun nextEntityId(): Int {
        return entityIds.getAndDecrement()
    }

    private fun teamName(entityId: Int): String {
        return "kgc${entityId.toString().takeLast(12)}"
    }

    private fun NametagVisibility.toPacketVisibility(): WrapperPlayServerTeams.NameTagVisibility {
        return when (this) {
            NametagVisibility.ALWAYS -> WrapperPlayServerTeams.NameTagVisibility.ALWAYS
            NametagVisibility.NEVER -> WrapperPlayServerTeams.NameTagVisibility.NEVER
            NametagVisibility.HIDE_FOR_OTHER_TEAMS -> WrapperPlayServerTeams.NameTagVisibility.HIDE_FOR_OTHER_TEAMS
            NametagVisibility.HIDE_FOR_OWN_TEAM -> WrapperPlayServerTeams.NameTagVisibility.HIDE_FOR_OWN_TEAM
        }
    }

    private fun NametagCollisionRule.toPacketCollisionRule(): WrapperPlayServerTeams.CollisionRule {
        return when (this) {
            NametagCollisionRule.ALWAYS -> WrapperPlayServerTeams.CollisionRule.ALWAYS
            NametagCollisionRule.NEVER -> WrapperPlayServerTeams.CollisionRule.NEVER
            NametagCollisionRule.PUSH_OTHER_TEAMS -> WrapperPlayServerTeams.CollisionRule.PUSH_OTHER_TEAMS
            NametagCollisionRule.PUSH_OWN_TEAM -> WrapperPlayServerTeams.CollisionRule.PUSH_OWN_TEAM
        }
    }

    private fun send(player: Player, packet: PacketWrapper<*>) {
        val user = runCatching { PacketEvents.getAPI().playerManager.getUser(player) }.getOrNull()
        runCatching {
            if (user != null) {
                user.sendPacketSilently(packet)
            } else {
                PacketEvents.getAPI().playerManager.sendPacketSilently(player, packet)
            }
        }.onFailure {
            plugin.logger.warning("Failed to send KaGameCenter packet to ${player.name}: ${it.message}")
        }
    }

    private data class ActiveVisual(
        val disguiseTargetId: UUID? = null,
        val entityIds: List<Int> = emptyList(),
        val teamNames: List<String> = emptyList(),
        val tasks: List<BukkitTask> = emptyList(),
        val removeEntities: List<org.bukkit.entity.Entity> = emptyList(),
        val restore: () -> Unit = {}
    )

    private data class ViewerEntityKey(
        val viewerId: UUID,
        val entityId: Int
    )

    private data class EntityOverlay(
        val invisible: Boolean = false,
        val glowing: Boolean = false
    )
}
