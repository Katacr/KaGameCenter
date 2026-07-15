package org.katacr.kaGameCenter.spectator

import org.bukkit.Material

data class SpectatorPolicy(
    val enabled: Boolean = true,
    val mode: SpectatorMode = SpectatorMode.VANILLA,
    val allowDuringRunning: Boolean = true,
    val allowFreeFly: Boolean = true,
    val allowFollowPlayer: Boolean = true,
    val revealHiddenPlayers: Boolean = true,
    val delaySeconds: Int = 0,
    val hotbarItems: List<SpectatorHotbarItem> = DEFAULT_HOTBAR_ITEMS
) {
    companion object {
        val DEFAULT_HOTBAR_ITEMS = listOf(
            SpectatorHotbarItem("follow", Material.COMPASS, 0, action = SpectatorAction.FOLLOW),
            SpectatorHotbarItem("menu", Material.NETHER_STAR, 4, action = SpectatorAction.MENU),
            SpectatorHotbarItem("leave", Material.BARRIER, 8, action = SpectatorAction.LEAVE)
        )
        val DEFAULT = SpectatorPolicy()
    }
}
