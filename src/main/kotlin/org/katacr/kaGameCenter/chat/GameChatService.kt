package org.katacr.kaGameCenter.chat

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.team.GameTeamService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameChatService(
    private val plugin: JavaPlugin,
    private val roomManager: GameRoomManager,
    private val teamService: GameTeamService,
    private val languageManager: LanguageManager
) {
    private val formatters = ConcurrentHashMap<String, GameChatFormatter>()
    private val enabled: Boolean
        get() = plugin.config.getBoolean("chat.enabled", true)
    private val spyEnabled: Boolean
        get() = plugin.config.getBoolean("chat.spy.enabled", false)

    fun registerFormatter(moduleId: String, formatter: GameChatFormatter) {
        formatters[moduleId.lowercase()] = formatter
    }

    fun unregisterFormatter(moduleId: String) {
        formatters.remove(moduleId.lowercase())
    }

    fun shouldHandleDefaultChat(player: Player): Boolean {
        return enabled && roomManager.getPlayerRoom(player) != null
    }

    fun handleDefaultChat(player: Player, message: String): Boolean {
        if (!enabled) return false
        val room = roomManager.getPlayerRoom(player) ?: return false
        if (sendTeamChatIfPossible(player, room, message)) return true
        sendRoomChat(player, room, message)
        return true
    }

    fun sendGlobalChat(player: Player, message: String) {
        val formatted = languageManager.getMessage("chat.global_format", player.name, message)
        Bukkit.getOnlinePlayers().forEach { it.sendMessage(Component.text(formatted)) }
        Bukkit.getConsoleSender().sendMessage(formatted)
    }

    fun sendRoomChat(player: Player, message: String): Boolean {
        if (!enabled) return false
        val room = roomManager.getPlayerRoom(player) ?: return false
        sendRoomChat(player, room, message)
        return true
    }

    private fun sendRoomChat(player: Player, room: GameRoom, message: String) {
        val formatted = formatChat(GameChatContext(GameChatChannel.ROOM, room, player, message))
        val audience = roomAudience(room)
        audience.forEach { it.sendMessage(Component.text(formatted)) }
        sendSpyChat(room, audience.map { it.uniqueId }.toSet(), formatted)
        Bukkit.getConsoleSender().sendMessage(formatted)
    }

    private fun sendTeamChatIfPossible(player: Player, room: GameRoom, message: String): Boolean {
        val team = teamService.getTeam(room.id, player.uniqueId) ?: return false
        val formatted = formatChat(GameChatContext(GameChatChannel.TEAM, room, player, message, team))
        val audience = teamService.getMembers(room.id, team.id)
            .mapNotNull { Bukkit.getPlayer(it) }
            .distinctBy { it.uniqueId }
        audience.forEach { it.sendMessage(Component.text(formatted)) }
        sendSpyChat(room, audience.map { it.uniqueId }.toSet(), formatted)
        Bukkit.getConsoleSender().sendMessage(formatted)
        return true
    }

    private fun formatChat(context: GameChatContext): String {
        val formatter = formatters[context.room.module.id.lowercase()]
        if (formatter != null) {
            runCatching { formatter.format(context) }
                .onSuccess { if (it != null) return it }
                .onFailure { plugin.logger.warning("Game chat formatter failed for ${context.room.module.id}: ${it.message}") }
        }
        return when (context.channel) {
            GameChatChannel.ROOM -> languageManager.getMessage(
                "chat.room_format",
                context.room.id,
                context.room.name,
                context.room.definition?.displayName ?: context.room.module.displayName,
                context.player.name,
                context.message
            )
            GameChatChannel.TEAM -> languageManager.getMessage(
                "chat.team_format",
                context.room.id,
                context.room.name,
                context.team?.displayName ?: "-",
                context.player.name,
                context.message
            )
            GameChatChannel.GLOBAL -> languageManager.getMessage("chat.global_format", context.player.name, context.message)
        }
    }

    private fun roomAudience(room: GameRoom): List<Player> {
        return (room.players + room.spectators)
            .mapNotNull { Bukkit.getPlayer(it) }
            .distinctBy { it.uniqueId }
    }

    private fun sendSpyChat(room: GameRoom, excluded: Set<UUID>, formatted: String) {
        if (!spyEnabled) return
        val spyMessage = Component.text(languageManager.getMessage("chat.spy_format", room.id, formatted))
        Bukkit.getOnlinePlayers()
            .asSequence()
            .filter { it.uniqueId !in excluded }
            .filter { it.hasPermission("kagamecenter.chat.spy") }
            .forEach { it.sendMessage(spyMessage) }
    }
}
