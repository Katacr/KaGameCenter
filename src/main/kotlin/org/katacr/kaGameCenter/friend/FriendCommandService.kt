package org.katacr.kaGameCenter.friend

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.spectator.SpectatorService
import java.util.UUID

/** 实现 `/kgc friend` 的好友申请、跟随房间和私聊事务。 */
class FriendCommandService(
    private val friendService: FriendService,
    private val roomManager: GameRoomManager,
    private val spectatorService: SpectatorService,
    private val languageManager: LanguageManager
) {
    private val actions = listOf("list", "add", "accept", "deny", "remove", "join", "spectate", "msg")

    fun execute(player: Player, label: String, args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            null, "list" -> showList(player, label)
            "add" -> withTarget(player, args.getOrNull(1)) { target ->
                val result = friendService.request(player.uniqueId, target.uniqueId)
                sendResult(player, result, playerName(target.uniqueId))
                if (result == FriendOperationResult.SENT) {
                    Bukkit.getPlayer(target.uniqueId)?.sendMessage(Component.text(
                        languageManager.getMessage("friend.request_received", player.name, label, player.uniqueId)
                    ))
                }
            }
            "accept" -> withTarget(player, args.getOrNull(1)) { target ->
                val result = friendService.accept(player.uniqueId, target.uniqueId)
                sendResult(player, result, playerName(target.uniqueId))
                if (result == FriendOperationResult.ACCEPTED) {
                    Bukkit.getPlayer(target.uniqueId)?.sendMessage(Component.text(
                        languageManager.getMessage("friend.request_accepted_target", player.name)
                    ))
                }
            }
            "deny" -> withTarget(player, args.getOrNull(1)) { target ->
                sendResult(player, friendService.deny(player.uniqueId, target.uniqueId), playerName(target.uniqueId))
            }
            "remove" -> withTarget(player, args.getOrNull(1)) { target ->
                sendResult(player, friendService.remove(player.uniqueId, target.uniqueId), playerName(target.uniqueId))
            }
            "join" -> withOnlineFriend(player, args.getOrNull(1)) { target -> joinFriendRoom(player, target) }
            "spectate" -> withOnlineFriend(player, args.getOrNull(1)) { target -> spectateFriendRoom(player, target) }
            "msg" -> {
                val targetInput = args.getOrNull(1)
                val message = args.drop(2).joinToString(" ").trim()
                if (targetInput == null || message.isBlank()) {
                    player.sendMessage(Component.text(languageManager.getMessage("friend.message_usage", label)))
                    return
                }
                withOnlineFriend(player, targetInput) { target ->
                    player.sendMessage(Component.text(languageManager.getMessage("friend.message_sent", target.name, message)))
                    target.sendMessage(Component.text(languageManager.getMessage("friend.message_received", player.name, message)))
                }
            }
            else -> player.sendMessage(Component.text(languageManager.getMessage("friend.usage", label)))
        }
    }

    fun tabComplete(player: Player, args: List<String>): List<String> {
        if (args.size <= 1) {
            val prefix = args.firstOrNull().orEmpty()
            return actions.filter { it.startsWith(prefix, ignoreCase = true) }
        }
        if (args.size != 2) return emptyList()
        val candidates = when (args[0].lowercase()) {
            "add" -> Bukkit.getOnlinePlayers().map { it.name }.filterNot { it.equals(player.name, ignoreCase = true) }
            "accept", "deny" -> friendService.incomingRequests(player.uniqueId).map(::playerName)
            "remove", "join", "spectate", "msg" -> friendService.friendsOf(player.uniqueId).map(::playerName)
            else -> emptyList()
        }
        return candidates.distinct().sorted().filter { it.startsWith(args[1], ignoreCase = true) }
    }

    private fun showList(player: Player, label: String) {
        val friendIds = friendService.friendsOf(player.uniqueId)
        val incoming = friendService.incomingRequests(player.uniqueId)
        player.sendMessage(Component.text(languageManager.getMessage("friend.list_title"), NamedTextColor.GOLD))
        if (friendIds.isEmpty()) {
            player.sendMessage(Component.text(languageManager.getMessage("friend.list_empty"), NamedTextColor.GRAY))
        } else {
            friendIds.sortedBy(::playerName).forEach { friendId ->
                val friend = Bukkit.getPlayer(friendId)
                val statusKey = if (friend == null) "friend.status_offline" else "friend.status_online"
                val line = Component.text("[${playerName(friendId)}] ", if (friend == null) NamedTextColor.GRAY else NamedTextColor.GREEN)
                    .append(Component.text(languageManager.getMessage(statusKey), NamedTextColor.DARK_GRAY))
                    .clickEvent(ClickEvent.suggestCommand("/$label friend msg ${playerName(friendId)} "))
                    .hoverEvent(HoverEvent.showText(Component.text(languageManager.getMessage("friend.list_hover"))))
                player.sendMessage(line)
            }
        }
        if (incoming.isNotEmpty()) {
            player.sendMessage(Component.text(languageManager.getMessage("friend.requests_title"), NamedTextColor.YELLOW))
            incoming.sortedBy(::playerName).forEach { senderId ->
                player.sendMessage(
                    Component.text("[${playerName(senderId)}]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/$label friend accept $senderId"))
                        .hoverEvent(HoverEvent.showText(Component.text(languageManager.getMessage("friend.request_hover"))))
                )
            }
        }
    }

    private fun withTarget(player: Player, input: String?, action: (org.bukkit.OfflinePlayer) -> Unit) {
        if (input == null) {
            player.sendMessage(Component.text(languageManager.getMessage("friend.target_required")))
            return
        }
        val target = friendService.resolvePlayer(input)
        if (target == null) {
            player.sendMessage(Component.text(languageManager.getMessage("friend.player_not_found", input)))
            return
        }
        action(target)
    }

    private fun withOnlineFriend(player: Player, input: String?, action: (Player) -> Unit) {
        withTarget(player, input) { offlineTarget ->
            if (friendService.relation(player.uniqueId, offlineTarget.uniqueId) != FriendRelation.FRIENDS) {
                player.sendMessage(Component.text(languageManager.getMessage("friend.not_friends", playerName(offlineTarget.uniqueId))))
                return@withTarget
            }
            val target = Bukkit.getPlayer(offlineTarget.uniqueId)
            if (target == null) {
                player.sendMessage(Component.text(languageManager.getMessage("friend.friend_offline", playerName(offlineTarget.uniqueId))))
                return@withTarget
            }
            action(target)
        }
    }

    private fun joinFriendRoom(player: Player, target: Player) {
        if (roomManager.getPlayerRoom(player) != null) {
            player.sendMessage(Component.text(languageManager.getMessage("room.already_in_room")))
            return
        }
        val room = roomManager.getPlayerRoom(target)
        if (room == null) {
            player.sendMessage(Component.text(languageManager.getMessage("friend.friend_not_in_room", target.name)))
            return
        }
        if (roomManager.joinRoom(player, room.id)) {
            player.sendMessage(Component.text(languageManager.getMessage("friend.joined_room", target.name, room.id)))
        } else {
            player.sendMessage(Component.text(languageManager.getMessage("room.join_failed", room.id)))
        }
    }

    private fun spectateFriendRoom(player: Player, target: Player) {
        if (roomManager.getPlayerRoom(player) != null) {
            player.sendMessage(Component.text(languageManager.getMessage("room.already_in_room")))
            return
        }
        val room = roomManager.getPlayerRoom(target)
        if (room == null) {
            player.sendMessage(Component.text(languageManager.getMessage("friend.friend_not_in_room", target.name)))
            return
        }
        if (!roomManager.spectateRoom(player, room.id)) {
            player.sendMessage(Component.text(languageManager.getMessage("room.spectate_failed", room.id)))
            return
        }
        if (roomManager.canSpectatorFollow(room, player, target)) spectatorService.follow(player, target)
        player.sendMessage(Component.text(languageManager.getMessage("friend.spectating_room", target.name, room.id)))
    }

    private fun sendResult(player: Player, result: FriendOperationResult, targetName: String) {
        player.sendMessage(Component.text(languageManager.getMessage("friend.result_${result.name.lowercase()}", targetName)))
    }

    private fun playerName(playerId: UUID): String {
        return Bukkit.getPlayer(playerId)?.name ?: Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString()
    }
}
