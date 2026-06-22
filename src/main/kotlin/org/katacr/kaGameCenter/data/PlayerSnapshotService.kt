package org.katacr.kaGameCenter.data

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

class PlayerSnapshotService {
    private val snapshots = linkedMapOf<UUID, PlayerSnapshot>()

    fun captureIfAbsent(player: Player) {
        snapshots.putIfAbsent(player.uniqueId, PlayerSnapshot.capture(player))
    }

    fun restore(player: Player): Boolean {
        val snapshot = snapshots.remove(player.uniqueId) ?: return false
        val world = snapshot.location.world ?: Bukkit.getWorlds().firstOrNull()
        if (world != null && world.uid != snapshot.location.world?.uid) {
            snapshot.location.world?.let { originalWorld ->
                if (Bukkit.getWorld(originalWorld.name) == null) {
                    player.teleport(world.spawnLocation)
                } else {
                    player.teleport(snapshot.location)
                }
            } ?: player.teleport(world.spawnLocation)
        } else {
            player.teleport(snapshot.location)
        }

        player.gameMode = snapshot.gameMode
        player.health = snapshot.health
        player.foodLevel = snapshot.foodLevel
        player.saturation = snapshot.saturation
        player.level = snapshot.level
        player.exp = snapshot.exp
        player.totalExperience = snapshot.totalExperience
        player.allowFlight = snapshot.allowFlight
        player.isFlying = snapshot.flying
        player.isInvisible = snapshot.invisible
        player.isInvulnerable = snapshot.invulnerable
        player.inventory.contents = snapshot.inventory
        player.inventory.armorContents = snapshot.armorContents
        player.inventory.extraContents = snapshot.extraContents
        return true
    }

    fun clear(playerId: UUID) {
        snapshots.remove(playerId)
    }
}
