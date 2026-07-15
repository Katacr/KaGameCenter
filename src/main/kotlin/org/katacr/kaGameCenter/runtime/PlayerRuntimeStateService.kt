package org.katacr.kaGameCenter.runtime

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID

class PlayerRuntimeStateService {
    private val statesByRoom = linkedMapOf<String, MutableMap<UUID, RuntimeState>>()

    @Synchronized
    fun captureIfAbsent(roomId: String, player: Player) {
        statesByRoom.getOrPut(roomId) { linkedMapOf() }
            .putIfAbsent(player.uniqueId, RuntimeState.capture(player))
    }

    @Synchronized
    fun restore(roomId: String, player: Player): Boolean {
        val state = statesByRoom[roomId]?.remove(player.uniqueId) ?: return false
        state.restore(player)
        if (statesByRoom[roomId]?.isEmpty() == true) {
            statesByRoom.remove(roomId)
        }
        return true
    }

    @Synchronized
    fun restoreRoom(roomId: String, players: Iterable<Player>) {
        players.forEach { player -> statesByRoom[roomId]?.remove(player.uniqueId)?.restore(player) }
        statesByRoom.remove(roomId)
    }

    @Synchronized
    fun clear(roomId: String, playerId: UUID) {
        statesByRoom[roomId]?.remove(playerId)
        if (statesByRoom[roomId]?.isEmpty() == true) {
            statesByRoom.remove(roomId)
        }
    }

    @Synchronized
    fun clearRoom(roomId: String) {
        statesByRoom.remove(roomId)
    }

    @Synchronized
    fun clearAll() {
        statesByRoom.clear()
    }

    data class RuntimeState(
        val gameMode: GameMode,
        val allowFlight: Boolean,
        val flying: Boolean,
        val walkSpeed: Float,
        val flySpeed: Float,
        val gravity: Boolean,
        val collidable: Boolean,
        val collidableExemptions: Set<UUID>,
        val invisible: Boolean,
        val invulnerable: Boolean,
        val scoreboard: Scoreboard,
        val effects: List<PotionEffect>
    ) {
        fun restore(player: Player) {
            player.gameMode = gameMode
            player.allowFlight = allowFlight
            player.isFlying = flying && allowFlight
            player.walkSpeed = walkSpeed
            player.flySpeed = flySpeed
            player.setGravity(gravity)
            player.isCollidable = collidable
            player.collidableExemptions.clear()
            player.collidableExemptions.addAll(collidableExemptions)
            player.isInvisible = invisible
            player.isInvulnerable = invulnerable
            player.scoreboard = scoreboard
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            effects.forEach { player.addPotionEffect(it) }
        }

        companion object {
            fun capture(player: Player): RuntimeState {
                return RuntimeState(
                    gameMode = player.gameMode,
                    allowFlight = player.allowFlight,
                    flying = player.isFlying,
                    walkSpeed = player.walkSpeed,
                    flySpeed = player.flySpeed,
                    gravity = player.hasGravity(),
                    collidable = player.isCollidable,
                    collidableExemptions = player.collidableExemptions.toSet(),
                    invisible = player.isInvisible,
                    invulnerable = player.isInvulnerable,
                    scoreboard = player.scoreboard,
                    effects = player.activePotionEffects.toList()
                )
            }
        }
    }
}
