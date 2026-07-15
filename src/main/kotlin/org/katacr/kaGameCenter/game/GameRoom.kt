package org.katacr.kaGameCenter.game

import org.bukkit.Bukkit
import org.bukkit.World
import org.katacr.kaGameCenter.event.GameRoomStateChangeEvent
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
    /** 保存房间公共状态，并在值真实变化后同步发布生命周期事件。 */
    var state: GameState = GameState.CREATED
        set(value) {
            if (field == value) return
            val previousState = field
            field = value
            Bukkit.getPluginManager().callEvent(GameRoomStateChangeEvent(this, previousState, value))
        }
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
