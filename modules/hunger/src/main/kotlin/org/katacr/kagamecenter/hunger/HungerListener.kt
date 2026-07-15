package org.katacr.kagamecenter.hunger

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.entity.Monster
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.katacr.kaGameCenter.game.GameRoomManager

/** 把 Bukkit 玩法事件按玩家或世界路由到对应的 HungerGameSession。 */
class HungerListener(
    private val roomManager: GameRoomManager
) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (event.from.x == event.to.x && event.from.y == event.to.y && event.from.z == event.to.z && event.from.world == event.to.world) return
        val session = session(event.player) ?: return
        if (session.handleMove(event)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val session = session(player) ?: return
        if (session.handleDamage(event)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamageByEntity(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val session = session(victim) ?: return
        if (session.handleDamageByEntity(event)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val session = session(event.player) ?: return
        if (session.handleInteract(event)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        val session = session(player) ?: return
        if (!session.allowsHunger(player)) {
            event.isCancelled = true
            player.foodLevel = 20
            player.saturation = 20f
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val session = session(event.player) ?: return
        if (session.handleBlockBreak(event)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val session = session(event.player) ?: return
        if (session.handleBlockPlace(event)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onExplosion(event: EntityExplodeEvent) {
        session(event.entity.world)?.handleExplosion(event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        val session = session(event.block.world) ?: return
        if (!session.allowsFireSpread()) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockIgnite(event: BlockIgniteEvent) {
        if (event.cause != BlockIgniteEvent.IgniteCause.SPREAD) return
        val session = session(event.block.world) ?: return
        if (!session.allowsFireSpread()) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val session = session(event.location.world) ?: return
        if (event.entity is Monster && !session.allowsMonsterSpawns()) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        val session = session(event.player) ?: return
        if (!session.isAliveParticipant(event.player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val session = session(player) ?: return
        if (!session.isAliveParticipant(player)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = session(player) ?: return
        if (!session.isAliveParticipant(player)) event.isCancelled = true
    }

    private fun session(player: Player): HungerGameSession? {
        val room = roomManager.getPlayerRoom(player) ?: return null
        return room.session as? HungerGameSession
    }

    private fun session(world: World): HungerGameSession? {
        val room = roomManager.listRooms().firstOrNull { it.world == world } ?: return null
        return room.session as? HungerGameSession
    }
}
