package org.katacr.kaGameCenter.game

import org.bukkit.World
import java.util.UUID

class GameRoom(
    val id: String,
    val module: GameModule
) {
    var definition: GameDefinition? = null
    val players: MutableSet<UUID> = linkedSetOf()
    val spectators: MutableSet<UUID> = linkedSetOf()
    var state: GameState = GameState.CREATED
    var world: World? = null
    lateinit var session: GameSession

    fun playerCount(): Int = players.size

    fun canJoin(): Boolean {
        return state == GameState.CREATED ||
            state == GameState.PREPARING ||
            state == GameState.WAITING ||
            state == GameState.COUNTDOWN
    }
}
