package org.katacr.kaGameCenter.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.spectator.SpectatorAction
import org.katacr.kaGameCenter.spectator.SpectatorService
import org.katacr.kaGameCenter.velocity.VelocityBridgeService

class GamePlayerListener(
    private val roomManager: GameRoomManager,
    private val menuService: GameCenterMenuService,
    private val packetService: PacketDispatchService,
    private val spectatorService: SpectatorService,
    private val velocityBridgeService: VelocityBridgeService
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        velocityBridgeService.consumeJoinIntent(event.player) { roomId ->
            roomManager.joinRoom(event.player, roomId)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        packetService.clearViewer(event.player)
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
    fun onBlockBreak(event: BlockBreakEvent) {
        if (spectatorService.isSpectator(event.player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (spectatorService.isSpectator(event.player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!spectatorService.isSpectator(player)) return

        event.isCancelled = true
        when (spectatorService.action(event.item)) {
            SpectatorAction.FOLLOW -> followNextPlayer(player)
            SpectatorAction.MENU -> roomManager.getPlayerRoom(player)?.let { menuService.openRoomMenu(player, it.id) }
            SpectatorAction.LEAVE -> roomManager.leaveCurrentRoom(player)
            null -> Unit
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        val player = event.damager as? org.bukkit.entity.Player ?: return
        if (spectatorService.isSpectator(player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        if (spectatorService.isSpectator(event.player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        if (spectatorService.isSpectator(player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        if (spectatorService.isSpectator(player)) event.isCancelled = true
    }

    private fun followNextPlayer(player: org.bukkit.entity.Player) {
        val room = roomManager.getPlayerRoom(player) ?: return
        val target = spectatorService.nextTarget(player, room) ?: return
        spectatorService.follow(player, target)
    }

    @EventHandler(ignoreCancelled = true)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        if (spectatorService.isSpectator(player)) event.isCancelled = true
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
