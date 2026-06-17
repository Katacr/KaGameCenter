package org.katacr.kaGameCenter.data

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

data class PlayerSnapshot(
    val location: Location,
    val gameMode: GameMode,
    val health: Double,
    val foodLevel: Int,
    val saturation: Float,
    val level: Int,
    val exp: Float,
    val totalExperience: Int,
    val allowFlight: Boolean,
    val flying: Boolean,
    val inventory: Array<ItemStack?>,
    val armorContents: Array<ItemStack?>,
    val extraContents: Array<ItemStack?>
) {
    companion object {
        fun capture(player: Player): PlayerSnapshot {
            return PlayerSnapshot(
                location = player.location.clone(),
                gameMode = player.gameMode,
                health = player.health,
                foodLevel = player.foodLevel,
                saturation = player.saturation,
                level = player.level,
                exp = player.exp,
                totalExperience = player.totalExperience,
                allowFlight = player.allowFlight,
                flying = player.isFlying,
                inventory = player.inventory.contents.clone(),
                armorContents = player.inventory.armorContents.clone(),
                extraContents = player.inventory.extraContents.clone()
            )
        }
    }
}
