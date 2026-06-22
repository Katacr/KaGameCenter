package org.katacr.kagamecenter.blockhunt

import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.katacr.kaGameCenter.game.GameRoomManager

class BlockhuntListener(
    private val roomManager: GameRoomManager
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to
        if (from.x == to.x && from.y == to.y && from.z == to.z && from.yaw == to.yaw && from.pitch == to.pitch && from.world == to.world) return
        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? BlockhuntGameSession ?: return
        session.handleMove(event.player, to)
    }

    @EventHandler(ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) return
        val room = roomManager.getPlayerRoom(event.player) ?: return
        val session = room.session as? BlockhuntGameSession ?: return
        session.handleSneak(event.player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val room = roomManager.getPlayerRoom(attacker) ?: return
        val session = room.session as? BlockhuntGameSession ?: return
        val victim = event.entity as? Player
        event.isCancelled = if (victim != null) {
            if (roomManager.getPlayerRoom(victim)?.id != room.id) return
            !session.handleDamage(attacker, victim)
        } else {
            !session.handleLockedDisguiseHit(attacker, event.entity)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val snowball = event.entity as? Snowball ?: return
        val shooter = snowball.shooter as? Player ?: return
        val room = roomManager.getPlayerRoom(shooter) ?: return
        val session = room.session as? BlockhuntGameSession ?: return
        val hit = event.hitEntity ?: return
        val victim = hit as? Player
        if (victim != null) {
            if (roomManager.getPlayerRoom(victim)?.id != room.id) return
            event.isCancelled = !session.handleSnowballHit(shooter, victim)
        } else {
            event.isCancelled = !session.handleLockedDisguiseSnowballHit(shooter, hit)
        }
    }
}
