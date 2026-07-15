package org.katacr.kaGameCenter.game

import org.bukkit.entity.Player
import org.katacr.kaGameCenter.spectator.SpectatorPolicy

interface GameModule {
    val id: String
    val displayName: String
    val minPlayers: Int
    val maxPlayers: Int

    fun defaultDefinition(): GameDefinition {
        return GameDefinition(
            id = id,
            displayName = displayName,
            minPlayers = minPlayers,
            maxPlayers = maxPlayers
        )
    }

    fun spectatorPolicy(room: GameRoom): SpectatorPolicy = room.definition?.spectatorPolicy ?: SpectatorPolicy.DEFAULT

    /** 判断指定玩家是否满足玩法自定义的外部观战准入条件。 */
    fun canSpectate(room: GameRoom, player: Player): Boolean = true

    fun createSession(room: GameRoom): GameSession
}
