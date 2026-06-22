package org.katacr.kaGameCenter.game

import org.bukkit.entity.Player

interface GameSession {
    val room: GameRoom

    fun onPrepare() {}
    fun onPlayerJoin(player: Player) {}
    fun onPlayerLeave(player: Player) {}
    fun onSpectatorJoin(player: Player) {}
    fun onSpectatorLeave(player: Player) {}
    fun onPlayerDeath(player: Player) {}
    fun onPlayerKill(killer: Player, victim: Player) {}
    fun usesCustomScoreboard(): Boolean = false
    fun usesCustomActionBar(): Boolean = false
    fun onStart() {}
    fun onTick() {}
    fun onEnd() {}
    fun onClose() {}
}
