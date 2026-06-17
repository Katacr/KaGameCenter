package org.katacr.kaGameCenter.data

import java.util.UUID

data class PlayerGameStats(
    val playerId: UUID,
    val gameId: String,
    var plays: Int = 0,
    var wins: Int = 0,
    var losses: Int = 0,
    var kills: Int = 0,
    var deaths: Int = 0,
    var points: Int = 0
)
