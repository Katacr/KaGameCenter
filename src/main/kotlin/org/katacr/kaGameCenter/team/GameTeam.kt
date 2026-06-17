package org.katacr.kaGameCenter.team

import net.kyori.adventure.text.format.TextColor

data class GameTeam(
    val id: String,
    val displayName: String,
    val color: TextColor? = null,
    val maxPlayers: Int = Int.MAX_VALUE
)
