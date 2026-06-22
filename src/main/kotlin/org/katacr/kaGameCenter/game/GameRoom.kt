package org.katacr.kaGameCenter.game

import org.bukkit.World
import java.io.File
import java.util.UUID

class GameRoom(
    val id: String,
    val module: GameModule
) {
    var definition: GameDefinition? = null
    var configuredGame: ManagedGameConfig? = null
    var mapTemplate: String? = null
    var templateDirectory: File? = null
    var name: String = id
    val players: MutableSet<UUID> = linkedSetOf()
    val spectators: MutableSet<UUID> = linkedSetOf()
    var owner: UUID? = null
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
