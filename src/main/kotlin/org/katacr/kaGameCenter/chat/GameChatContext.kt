package org.katacr.kaGameCenter.chat

import org.bukkit.entity.Player
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.team.GameTeam

data class GameChatContext(
    val channel: GameChatChannel,
    val room: GameRoom,
    val player: Player,
    val message: String,
    val team: GameTeam? = null
)
