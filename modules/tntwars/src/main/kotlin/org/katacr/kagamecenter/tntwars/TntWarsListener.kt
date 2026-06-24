package org.katacr.kagamecenter.tntwars

import org.bukkit.entity.Entity
import org.bukkit.entity.Creeper
import org.bukkit.entity.Fireball
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.minecart.ExplosiveMinecart
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.katacr.kaGameCenter.game.GameRoomManager

class TntWarsListener(
    private val roomManager: GameRoomManager
) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ && from.world == to.world) return
        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? TntWarsGameSession ?: return
        session.handleMove(event.player, to)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? TntWarsGameSession ?: return
        if (session.handleInteract(event)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onExplode(event: EntityExplodeEvent) {
        val entity = event.entity
        if (!isTntWarsExplosive(entity)) return
        roomManager.listRooms()
            .firstOrNull { it.world == entity.world && it.session is TntWarsGameSession }
            ?.let { (it.session as TntWarsGameSession).handleExplosion(entity, event.location) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onShootBow(event: EntityShootBowEvent) {
        val player = event.entity as? org.bukkit.entity.Player ?: return
        val room = roomManager.getPlayerRoom(player) ?: return
        val session = room.session as? TntWarsGameSession ?: return
        if (session.handleBowShoot(event)) {
            event.isCancelled = false
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val shooter = event.entity.shooter as? org.bukkit.entity.Player
        val room = shooter?.let(roomManager::getPlayerRoom)
            ?: roomManager.listRooms().firstOrNull { it.world == event.entity.world && it.session is TntWarsGameSession }
            ?: return
        val session = room.session as? TntWarsGameSession ?: return
        session.handleProjectileHit(event)
    }

    private fun isTntWarsExplosive(entity: Entity): Boolean {
        return (entity is TNTPrimed || entity is Fireball || entity is Creeper || entity is ExplosiveMinecart) &&
            entity.scoreboardTags.contains("kgc_tntwars")
    }
}
