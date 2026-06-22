package org.katacr.kaGameCenter.packet

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.format.NamedTextColor

interface PacketDispatchService {
    val backendName: String
    val available: Boolean

    fun init()

    fun shutdown()

    fun clearViewer(player: Player)

    fun disguisePlayerAsBlock(target: Player, material: Material, viewers: Collection<Player>, durationSeconds: Int)

    fun disguisePlayerAsMob(target: Player, entityType: EntityType, viewers: Collection<Player>, durationSeconds: Int)

    fun clearDisguise(target: Player, viewers: Collection<Player>)

    fun showBlockGlow(viewer: Player, location: Location, durationSeconds: Int, color: NamedTextColor = NamedTextColor.YELLOW)

    fun showPlayerGlow(viewer: Player, target: Player, durationSeconds: Int)

    fun showPrivateDrop(viewer: Player, location: Location, itemStack: ItemStack, durationSeconds: Int)

    fun showPrivatePickup(
        viewer: Player,
        location: Location,
        itemStack: ItemStack,
        glowing: Boolean,
        color: NamedTextColor = NamedTextColor.AQUA,
        durationSeconds: Int,
        scale: Float = 1.8f,
        onPickup: (Player) -> Unit
    )

    fun showBeaconBeam(viewer: Player, location: Location, color: NamedTextColor = NamedTextColor.AQUA, durationSeconds: Int)

    fun showProbe(viewer: Player, message: String)
}
