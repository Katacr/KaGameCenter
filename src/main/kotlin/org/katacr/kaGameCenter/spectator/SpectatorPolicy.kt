package org.katacr.kaGameCenter.spectator

data class SpectatorPolicy(
    val enabled: Boolean = true,
    val mode: SpectatorMode = SpectatorMode.VANILLA,
    val allowDuringRunning: Boolean = true,
    val allowFreeFly: Boolean = true,
    val allowFollowPlayer: Boolean = true,
    val revealHiddenPlayers: Boolean = true,
    val delaySeconds: Int = 0
) {
    companion object {
        val DEFAULT = SpectatorPolicy()
    }
}
