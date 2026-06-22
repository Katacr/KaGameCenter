package org.katacr.kagamecenter.parkour

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.katacr.kaGameCenter.game.GameRoomManager

class ParkourListener(
    private val roomManager: GameRoomManager
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to
        if (from.x == to.x && from.y == to.y && from.z == to.z && from.world == to.world) return

        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? ParkourGameSession ?: return
        session.handleMove(event.player, to)
    }
}
