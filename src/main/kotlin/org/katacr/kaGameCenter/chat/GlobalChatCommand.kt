package org.katacr.kaGameCenter.chat

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.i18n.LanguageManager

class GlobalChatCommand(
    private val chatService: GameChatService,
    private val languageManager: LanguageManager
) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(languageManager.getMessage("command.only_player_join"))
            return true
        }
        if (args.isEmpty()) {
            player.sendMessage(Component.text(languageManager.getMessage("chat.global_usage", label)))
            return true
        }
        chatService.sendGlobalChat(player, args.joinToString(" "))
        return true
    }
}
