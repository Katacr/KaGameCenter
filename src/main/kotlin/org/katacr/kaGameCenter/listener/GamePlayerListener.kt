package org.katacr.kaGameCenter.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.game.GameRoomManager

class GamePlayerListener(
    private val roomManager: GameRoomManager,
    private val menuService: GameCenterMenuService
) : Listener {

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        roomManager.leaveCurrentRoom(event.player)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val victim = event.player
        val room = roomManager.getPlayerRoom(victim) ?: return
        val killer = victim.killer

        if (killer != null && room.players.contains(killer.uniqueId)) {
            roomManager.recordKill(killer.uniqueId, victim.uniqueId, room.module.id, points = 1)
            room.session.onPlayerKill(killer, victim)
        } else {
            roomManager.recordDeath(victim.uniqueId, room.module.id)
        }

        room.session.onPlayerDeath(victim)
    }

    @EventHandler(ignoreCancelled = true)
    fun onSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!player.isSneaking) return

        event.isCancelled = true
        val room = roomManager.getPlayerRoom(player)
        if (room == null) {
            menuService.openMainMenu(player)
        } else {
            menuService.openRoomMenu(player, room.id)
        }
    }
}
