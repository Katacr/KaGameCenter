package org.katacr.kaGameCenter.chat

fun interface GameChatFormatter {
    fun format(context: GameChatContext): String?
}
