package org.katacr.kaGameCenter.command

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.game.GameMapManager
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.dialog.GameCenterMenuService

class KaGameCenterCommand(
    private val menuService: GameCenterMenuService,
    private val roomManager: GameRoomManager,
    private val mapManager: GameMapManager,
    private val languageManager: LanguageManager
) : CommandExecutor, TabCompleter {

    private val subCommands = listOf(
        "menu",
        "games",
        "rooms",
        "maps",
        "create",
        "join",
        "quickjoin",
        "start",
        "leave",
        "close",
        "stats"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            "menu" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_dialog")))
                    return true
                }
                menuService.openMainMenu(sender)
            }
            "games" -> {
                if (!requireUser(sender)) return true
                val games = roomManager.listDefinitions().joinToString("\n") {
                    languageManager.getMessage(
                        "game.definition_line",
                        it.id,
                        it.displayName,
                        it.enabled,
                        it.minPlayers,
                        it.maxPlayers,
                        it.defaultDurationSeconds,
                        it.mapTemplates.joinToString(", ").ifBlank { "-" }
                    )
                }
                sender.sendMessage(Component.text(if (games.isBlank()) languageManager.getMessage("command.no_games") else games))
            }
            "rooms" -> {
                if (!requireUser(sender)) return true
                sender.sendMessage(Component.text(roomManager.status()))
            }
            "maps" -> {
                if (!requireAdmin(sender)) return true
                handleMaps(sender, label, args)
            }
            "create" -> {
                if (!requireAdmin(sender)) return true
                val gameId = args.getOrNull(1) ?: return usage(sender, label)
                val room = roomManager.createRoom(gameId)
                sender.sendMessage(Component.text(if (room == null) languageManager.getMessage("command.game_not_found", gameId) else languageManager.getMessage("room.created", room.id)))
            }
            "join" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_join")))
                    return true
                }
                val roomId = args.getOrNull(1) ?: return usage(sender, label)
                val joined = roomManager.joinRoom(sender, roomId)
                if (!joined) sender.sendMessage(Component.text(languageManager.getMessage("room.join_failed", roomId)))
            }
            "quickjoin" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_join")))
                    return true
                }
                val gameId = args.getOrNull(1) ?: "parkour"
                val room = roomManager.joinNewRoom(sender, gameId)
                sender.sendMessage(Component.text(if (room == null) languageManager.getMessage("command.game_not_found", gameId) else languageManager.getMessage("room.quick_joined", room.id)))
            }
            "start" -> {
                if (!requireAdmin(sender)) return true
                val roomId = args.getOrNull(1) ?: return usage(sender, label)
                val started = roomManager.startRoom(roomId)
                sender.sendMessage(Component.text(if (started) languageManager.getMessage("room.started", roomId) else languageManager.getMessage("room.start_failed", roomId)))
            }
            "leave" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_leave")))
                    return true
                }
                val left = roomManager.leaveCurrentRoom(sender)
                sender.sendMessage(Component.text(if (left) languageManager.getMessage("room.left") else languageManager.getMessage("command.no_room")))
            }
            "close" -> {
                if (!requireAdmin(sender)) return true
                val roomId = args.getOrNull(1) ?: return usage(sender, label)
                val closed = roomManager.closeRoom(roomId)
                sender.sendMessage(Component.text(if (closed) languageManager.getMessage("room.closed", roomId) else languageManager.getMessage("command.room_not_found", roomId)))
            }
            "stats" -> {
                if (!requireAdmin(sender)) return true
                val lines = roomManager.statsSnapshot().joinToString("\n") {
                    languageManager.getMessage("stats.snapshot_line", it.playerId, it.gameId, it.plays, it.wins, it.losses, it.kills, it.deaths, it.points)
                }
                sender.sendMessage(Component.text(if (lines.isBlank()) languageManager.getMessage("command.no_stats") else lines))
            }
            else -> usage(sender, label)
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val prefix = args[0].lowercase()
            return subCommands.filter { it.startsWith(prefix) }
        }

        if (args.firstOrNull()?.equals("maps", ignoreCase = true) == true) {
            return completeMaps(args)
        }
        return emptyList()
    }

    private fun handleMaps(sender: CommandSender, label: String, args: Array<out String>) {
        val gameId = args.getOrNull(1)
        if (gameId == null) {
            val games = mapManager.listGames()
            sender.sendMessage(Component.text(
                if (games.isEmpty()) languageManager.getMessage("map.no_games")
                else languageManager.getMessage("map.games", games.joinToString(", "))
            ))
            return
        }

        val action = args.getOrNull(2)?.lowercase()
        when (action) {
            null, "list" -> {
                val maps = mapManager.listMaps(gameId)
                if (maps.isEmpty()) {
                    sender.sendMessage(Component.text(languageManager.getMessage("map.none", gameId)))
                    return
                }
                val lines = maps.joinToString("\n") {
                    languageManager.getMessage(
                        "map.line",
                        it.relativePath,
                        if (it.active) languageManager.getMessage("map.active") else languageManager.getMessage("map.inactive")
                    )
                }
                sender.sendMessage(Component.text(lines))
            }
            "create" -> {
                val mapId = args.getOrNull(3) ?: run {
                    usage(sender, label)
                    return
                }
                sender.sendMessage(Component.text(mapManager.createMap(gameId, mapId).message))
            }
            "select" -> {
                val mapId = args.getOrNull(3) ?: run {
                    usage(sender, label)
                    return
                }
                sender.sendMessage(Component.text(mapManager.selectMap(gameId, mapId).message))
            }
            "remove" -> {
                val mapId = args.getOrNull(3) ?: run {
                    usage(sender, label)
                    return
                }
                sender.sendMessage(Component.text(mapManager.removeMap(gameId, mapId).message))
            }
            "setspawn" -> {
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_enter")))
                    return
                }
                val mapId = args.getOrNull(3) ?: run {
                    usage(sender, label)
                    return
                }
                val location = sender.location
                sender.sendMessage(Component.text(
                    mapManager.setSpawn(gameId, mapId, location.x, location.y, location.z, location.yaw, location.pitch).message
                ))
            }
            "reload" -> sender.sendMessage(Component.text(mapManager.reload().message))
            else -> usage(sender, label)
        }
    }

    private fun completeMaps(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> {
                val prefix = args[1].lowercase()
                mapManager.listGames().filter { it.startsWith(prefix) }
            }
            3 -> {
                val prefix = args[2].lowercase()
                listOf("list", "create", "select", "remove", "setspawn", "reload").filter { it.startsWith(prefix) }
            }
            4 -> {
                val action = args[2].lowercase()
                if (action != "select" && action != "remove" && action != "setspawn") return emptyList()
                val prefix = args[3].lowercase()
                mapManager.listMaps(args[1]).map { it.mapId }.filter { it.startsWith(prefix) }
            }
            else -> emptyList()
        }
    }

    private fun usage(sender: CommandSender, label: String): Boolean {
        sender.sendMessage(Component.text(languageManager.getMessage("command.usage", label)))
        return true
    }

    private fun requireUser(sender: CommandSender): Boolean {
        if (sender.hasPermission("kagamecenter.user") || sender.hasPermission("kagamecenter.admin")) return true
        sender.sendMessage(Component.text(languageManager.getMessage("command.no_permission")))
        return false
    }

    private fun requireAdmin(sender: CommandSender): Boolean {
        if (sender.hasPermission("kagamecenter.admin")) return true
        sender.sendMessage(Component.text(languageManager.getMessage("command.no_permission")))
        return false
    }
}
