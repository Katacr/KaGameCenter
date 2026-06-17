package org.katacr.kaGameCenter.spectator

import org.bukkit.entity.Player
import java.util.UUID

class SpectatorService {
    private val spectators = linkedMapOf<String, MutableSet<UUID>>()

    fun add(roomId: String, player: Player) {
        spectators.getOrPut(roomId) { linkedSetOf() }.add(player.uniqueId)
    }

    fun remove(roomId: String, playerId: UUID) {
        spectators[roomId]?.remove(playerId)
    }

    fun isSpectator(roomId: String, playerId: UUID): Boolean {
        return spectators[roomId]?.contains(playerId) == true
    }

    fun clearRoom(roomId: String) {
        spectators.remove(roomId)
    }
}
