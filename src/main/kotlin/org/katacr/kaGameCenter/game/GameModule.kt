package org.katacr.kaGameCenter.game

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

    fun createSession(room: GameRoom): GameSession
}
