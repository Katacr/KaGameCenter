package org.katacr.kaGameCenter.game

import java.util.UUID

data class PlayerSession(
    val playerId: UUID,
    val roomId: String,
    val spectator: Boolean = false
)
