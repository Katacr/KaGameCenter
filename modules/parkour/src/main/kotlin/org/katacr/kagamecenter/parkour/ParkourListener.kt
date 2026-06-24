package org.katacr.kagamecenter.parkour

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
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

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? ParkourGameSession ?: return
        session.handleInteract(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        val room = roomManager.getPlayerRoom(player) ?: return
        val session = room.session as? ParkourGameSession ?: return
        session.handleInventoryClick(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? ParkourGameSession ?: return
        session.handleDrop(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? ParkourGameSession ?: return
        session.handleSwapHandItems(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        val room = roomManager.getPlayerRoom(player) ?: return
        val session = room.session as? ParkourGameSession ?: return
        session.handleDamage(event)
    }
}
