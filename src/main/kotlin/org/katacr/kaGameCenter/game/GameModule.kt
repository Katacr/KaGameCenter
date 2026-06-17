package org.katacr.kaGameCenter.game

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

    fun createSession(room: GameRoom): GameSession
}
