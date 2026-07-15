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

    /** 仅注销当前仍由指定实例占用的格式器，避免旧模块上下文删除替代实例。 */
    fun unregisterFormatter(moduleId: String, formatter: GameChatFormatter): Boolean {
        return formatters.remove(moduleId.lowercase(), formatter)
    }

    fun shouldHandleDefaultChat(player: Player): Boolean {
        return enabled && roomManager.getPlayerRoom(player) != null
    }

    fun handleDefaultChat(player: Player, message: String): Boolean {
        if (!enabled) return false
        val room = roomManager.getPlayerRoom(player) ?: return false
        val requestedChannel = if (teamService.getTeam(room.id, player.uniqueId) == null) {
            GameChatChannel.ROOM
        } else {
            GameChatChannel.TEAM
        }
        sendRoutedChat(player, room, message, requestedChannel)
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
        sendRoutedChat(player, room, message, GameChatChannel.ROOM)
        return true
    }

    /** 应用玩法会话路由并在显式受众、频道受众和管理员旁听之间保持房间隔离。 */
    private fun sendRoutedChat(
        player: Player,
        room: GameRoom,
        message: String,
        requestedChannel: GameChatChannel
    ) {
        val requestedRoute = runCatching { room.session.routeChat(player, message, requestedChannel) }
            .onFailure {
                plugin.logger.warning("Game chat route failed for ${room.module.id} in room ${room.id}: ${it.message}")
            }
            .getOrNull() ?: return
        if (roomManager.getRoom(room.id) !== room || roomManager.getPlayerRoom(player) !== room) return
        val team = teamService.getTeam(room.id, player.uniqueId)
        val route = if (requestedRoute.channel == GameChatChannel.TEAM && team == null) {
            requestedRoute.copy(channel = GameChatChannel.ROOM)
        } else {
            requestedRoute
        }
        val formatted = formatChat(GameChatContext(
            route.channel,
            room,
            player,
            route.message,
            team,
            route.variant
        ))
        val audience = route.audience?.let { explicitAudience(room, route.channel, it) }
            ?: when (route.channel) {
                GameChatChannel.ROOM -> roomAudience(room)
                GameChatChannel.TEAM -> teamAudience(room, team?.id)
                GameChatChannel.GLOBAL -> Bukkit.getOnlinePlayers().toList()
            }
        audience.forEach { it.sendMessage(Component.text(formatted)) }
        sendSpyChat(room, audience.map { it.uniqueId }.toSet(), formatted)
        Bukkit.getConsoleSender().sendMessage(formatted)
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

    /** 返回当前房间指定队伍的在线成员，避免同名队伍跨房串台。 */
    private fun teamAudience(room: GameRoom, teamId: String?): List<Player> {
        if (teamId == null) return emptyList()
        return teamService.getMembers(room.id, teamId)
            .mapNotNull(Bukkit::getPlayer)
            .distinctBy { it.uniqueId }
    }

    /** 将玩法显式受众限制在当前房间；只有显式全局频道可包含房间外玩家。 */
    private fun explicitAudience(room: GameRoom, channel: GameChatChannel, audience: Set<UUID>): List<Player> {
        val roomMembers = room.players + room.spectators
        return audience.asSequence()
            .filter { channel == GameChatChannel.GLOBAL || it in roomMembers }
            .mapNotNull(Bukkit::getPlayer)
            .distinctBy { it.uniqueId }
            .toList()
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
