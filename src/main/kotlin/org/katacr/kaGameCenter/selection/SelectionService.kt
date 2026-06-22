package org.katacr.kaGameCenter.selection

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

class SelectionService {
    private val states = linkedMapOf<UUID, SelectionState>()

    fun setFirst(player: Player, location: Location): RegionSelection? {
        val state = states.computeIfAbsent(player.uniqueId) { SelectionState() }
        state.first = location.clone()
        return state.selection()
    }

    fun setSecond(player: Player, location: Location): RegionSelection? {
        val state = states.computeIfAbsent(player.uniqueId) { SelectionState() }
        state.second = location.clone()
        return state.selection()
    }

    fun getSelection(player: Player): RegionSelection? {
        return states[player.uniqueId]?.selection()
    }

    fun clear(player: Player) {
        states.remove(player.uniqueId)
    }

    private data class SelectionState(
        var first: Location? = null,
        var second: Location? = null
    ) {
        fun selection(): RegionSelection? {
            val firstLocation = first ?: return null
            val secondLocation = second ?: return null
            return RegionSelection.from(firstLocation, secondLocation)
        }
    }
}
