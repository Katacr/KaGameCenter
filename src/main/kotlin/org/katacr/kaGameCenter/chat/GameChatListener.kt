package org.katacr.kaGameCenter.chat

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class GameChatListener(
    private val plugin: JavaPlugin,
    private val chatService: GameChatService
) : Listener {
    private val plain = PlainTextComponentSerializer.plainText()

    @EventHandler
    fun onAsyncChat(event: AsyncChatEvent) {
        val message = plain.serialize(event.message())
        if (message.isBlank()) return
        val player = event.player
        if (!player.isOnline) return
        if (!chatService.shouldHandleDefaultChat(player)) return

        event.isCancelled = true
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline) {
                chatService.handleDefaultChat(player, message)
            }
        })
    }
}
