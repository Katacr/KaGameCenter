package org.katacr.kaGameCenter.listener

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.parkour.ParkourGameSession

class ParkourListener(
    private val roomManager: GameRoomManager
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from.block
        val to = event.to?.block ?: return
        if (from.x == to.x && from.y == to.y && from.z == to.z && from.world == to.world) return

        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? ParkourGameSession ?: return

        when (to.type) {
            Material.HEAVY_WEIGHTED_PRESSURE_PLATE -> session.saveCheckpoint(event.player)
            Material.POLISHED_BLACKSTONE_PRESSURE_PLATE -> {
                val points = session.finish(event.player) ?: return
                roomManager.recordWin(event.player.uniqueId, room.module.id, points)
                if (session.allPlayersFinished()) {
                    roomManager.closeRoom(room.id)
                }
            }
            else -> Unit
        }

        if (event.player.location.y < -32) {
            session.teleportToCheckpoint(event.player)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? ParkourGameSession ?: return
        if (!session.isCheckpointItem(item)) return

        session.teleportToCheckpoint(event.player)
        event.isCancelled = true
    }
}
