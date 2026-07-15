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
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.elimination.PlayerEliminationService
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.event.GameRoomLeaveReason
import org.katacr.kaGameCenter.nametag.PlayerNametagService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.spectator.SpectatorAction
import org.katacr.kaGameCenter.spectator.SpectatorService
import org.katacr.kaGameCenter.velocity.VelocityBridgeService

class GamePlayerListener(
    private val roomManager: GameRoomManager,
    private val menuService: GameCenterMenuService,
    private val packetService: PacketDispatchService,
    private val spectatorService: SpectatorService,
    private val velocityBridgeService: VelocityBridgeService,
    private val nametagService: PlayerNametagService,
    private val eliminationService: PlayerEliminationService
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (roomManager.reconnectPlayer(event.player)) return
        roomManager.restorePendingSnapshot(event.player)
        if (roomManager.hasPendingSnapshot(event.player.uniqueId)) return
        velocityBridgeService.consumeJoinIntent(event.player) { roomId ->
            roomManager.joinRoom(event.player, roomId)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        packetService.clearViewer(event.player)
        nametagService.clearViewer(event.player)
        nametagService.clearTarget(event.player.uniqueId)
        if (!roomManager.disconnectCurrentRoom(event.player)) {
            roomManager.leaveCurrentRoom(event.player, GameRoomLeaveReason.DISCONNECT)
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        roomManager.handlePlayerDeath(event.player)
    }

    /** 在原版观战者潜行退出第一人称时同步清除服务目标。 */
    @EventHandler(ignoreCancelled = true)
    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking || event.player.gameMode != org.bukkit.GameMode.SPECTATOR) return
        spectatorService.stopFollowing(event.player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val room = roomManager.getPlayerRoom(event.player) ?: return
        eliminationService.handleRespawn(room, event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (isRestrictedSpectator(event.player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (isRestrictedSpectator(event.player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!isRestrictedSpectator(player)) return

        event.isCancelled = true
        if (!spectatorService.isSpectator(player)) return
        when (spectatorService.action(event.item)) {
            SpectatorAction.FOLLOW -> followNextPlayer(player)
            SpectatorAction.MENU -> roomManager.getPlayerRoom(player)?.let { menuService.openRoomMenu(player, it.id) }
            SpectatorAction.LEAVE -> roomManager.leaveCurrentRoom(player)
            null -> spectatorService.command(event.item)?.let(player::performCommand)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        val player = event.damager as? org.bukkit.entity.Player ?: return
        if (isRestrictedSpectator(player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        if (isRestrictedSpectator(event.player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        if (isRestrictedSpectator(player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        if (isRestrictedSpectator(player)) event.isCancelled = true
    }

    private fun followNextPlayer(player: org.bukkit.entity.Player) {
        val room = roomManager.getPlayerRoom(player) ?: return
        val target = spectatorService.nextTarget(player, room) { target ->
            roomManager.canSpectatorFollow(room, player, target)
        } ?: return
        spectatorService.follow(player, target)
    }

    @EventHandler(ignoreCancelled = true)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        if (isRestrictedSpectator(player)) event.isCancelled = true
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

    private fun isRestrictedSpectator(player: org.bukkit.entity.Player): Boolean {
        return spectatorService.isSpectator(player) || eliminationService.isEliminated(player.uniqueId)
    }
}
