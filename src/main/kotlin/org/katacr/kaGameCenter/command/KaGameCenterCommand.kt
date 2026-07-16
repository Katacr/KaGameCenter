package org.katacr.kaGameCenter.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.title.Title
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.game.GameMapManager
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.friend.FriendCommandService
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.display.IconTextParser
import org.katacr.kaGameCenter.menu.chest.ChestMenuService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.reload.GameCenterReloadService
import org.katacr.kaGameCenter.module.ManagedModuleReloadResult
import java.time.Duration
import java.util.UUID

class KaGameCenterCommand(
    private val menuService: GameCenterMenuService,
    private val chestMenuService: ChestMenuService,
    private val roomManager: GameRoomManager,
    private val mapManager: GameMapManager,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val languageManager: LanguageManager,
    private val packetService: PacketDispatchService,
    private val friendCommandService: FriendCommandService,
    private val reloadService: GameCenterReloadService,
    private val moduleAdminCommands: Map<String, ModuleAdminCommand>
) : CommandExecutor, TabCompleter {

    private val subCommands = listOf(
        "help",
        "menu",
        "chest",
        "stats",
        "selector",
        "games",
        "rooms",
        "create",
        "join",
        "quickjoin",
        "friend",
        "leave",
        "reload",
        "admin"
    )

    private val adminSubCommands = listOf(
        "help",
        "maps",
        "manage",
        "create",
        "start",
        "close",
        "stats",
        "packet",
        "icon"
    )

    private val iconBossBars = linkedMapOf<UUID, BossBar>()
    private val adminOnlySubCommands = setOf("admin", "reload")
    private val reloadTargets = listOf("config", "lang", "model", "map")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            null, "help" -> showHelp(sender, label)
            "menu" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_dialog")))
                    return true
                }
                menuService.openMainMenu(sender)
            }
            "chest" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_dialog")))
                    return true
                }
                val menuId = args.getOrNull(1) ?: "main"
                if (!chestMenuService.open(sender, menuId)) {
                    sender.sendMessage(Component.text(languageManager.getMessage("menu.chest_not_found", menuId)))
                }
            }
            "stats" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_dialog")))
                    return true
                }
                menuService.openStatsMenu(sender, args.getOrNull(1))
            }
            "selector" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_dialog")))
                    return true
                }
                val gameId = args.getOrNull(1) ?: return usage(sender, label)
                menuService.openRoomsMenu(sender, gameId, args.getOrNull(2))
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
            "create" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_join")))
                    return true
                }
                val gameId = args.getOrNull(1) ?: return usage(sender, label)
                val mapTemplate = args.getOrNull(2)
                val roomName = args.drop(3).joinToString(" ").ifBlank { null }
                val room = roomManager.createRoom(gameId, sender.uniqueId, mapTemplate, roomName)
                if (room == null) {
                    sender.sendMessage(Component.text(roomManager.createRoomFailureMessage(gameId)))
                    return true
                }
                val joined = roomManager.joinRoom(sender, room.id)
                if (!joined) {
                    roomManager.closeRoom(room.id)
                    sender.sendMessage(Component.text(languageManager.getMessage("room.join_failed", room.id)))
                    return true
                }
                sender.sendMessage(Component.text(languageManager.getMessage("room.created", room.id)))
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
                val gameId = args.getOrNull(1)
                    ?: managedGameCatalog.enabled().firstOrNull()?.globalId
                    ?: roomManager.listDefinitions().firstOrNull { it.enabled }?.id
                if (gameId == null) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.no_games")))
                    return true
                }
                val room = roomManager.joinNewRoom(sender, gameId)
                sender.sendMessage(Component.text(if (room == null) roomManager.createRoomFailureMessage(gameId) else languageManager.getMessage("room.quick_joined", room.id)))
            }
            "friend" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_friend")))
                    return true
                }
                friendCommandService.execute(sender, label, args.drop(1))
            }
            "roommember" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) return true
                val roomId = args.getOrNull(1) ?: return usage(sender, label)
                val targetId = args.getOrNull(2)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return true
                menuService.openMemberMenu(sender, roomId, targetId)
            }
            "teamjoin" -> {
                if (!requireUser(sender)) return true
                if (sender !is Player) return true
                val roomId = args.getOrNull(1) ?: return usage(sender, label)
                val teamId = args.getOrNull(2) ?: return usage(sender, label)
                menuService.handleAction(sender, "kgc:join-team $roomId $teamId")
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
            "reload" -> {
                if (!requireAdmin(sender)) return true
                handleReload(sender, label, args)
            }
            "admin" -> {
                if (!requireAdmin(sender)) return true
                handleAdmin(sender, label, args)
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
            return subCommands
                .filter { it !in adminOnlySubCommands || sender.hasPermission("kagamecenter.admin") }
                .filter { it.startsWith(prefix) }
        }

        if (args.firstOrNull()?.equals("admin", ignoreCase = true) == true) {
            if (!sender.hasPermission("kagamecenter.admin")) return emptyList()
            return completeAdmin(sender, args)
        }
        return completeUser(sender, args)
    }

    private fun handleAdmin(sender: CommandSender, label: String, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase()) {
            null, "help" -> showAdminHelp(sender, label)
            "maps" -> handleMaps(sender, label, shiftedArgs(args))
            "manage" -> {
                val player = sender as? Player
                if (player == null) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_dialog")))
                    return
                }
                val gameId = args.getOrNull(2)
                if (gameId == null) {
                    menuService.openAdminManageMenu(player)
                    return
                }
                if (managedGameCatalog.get(gameId) == null) {
                    sender.sendMessage(Component.text(languageManager.getMessage("command.game_not_found", gameId)))
                    return
                }
                if (!managedGameCatalog.openEditor(player, gameId)) {
                    sender.sendMessage(Component.text(languageManager.getMessage("managed_game.editor_unavailable", gameId)))
                }
            }
            "create" -> {
                val gameId = args.getOrNull(2) ?: return showUsage(sender, label)
                val room = roomManager.createRoom(gameId)
                sender.sendMessage(Component.text(if (room == null) roomManager.createRoomFailureMessage(gameId) else languageManager.getMessage("room.created", room.id)))
            }
            "start" -> {
                val roomId = args.getOrNull(2) ?: return showUsage(sender, label)
                val started = roomManager.startRoom(roomId)
                sender.sendMessage(Component.text(if (started) languageManager.getMessage("room.started", roomId) else languageManager.getMessage("room.start_failed", roomId)))
            }
            "close" -> {
                val roomId = args.getOrNull(2) ?: return showUsage(sender, label)
                val closed = roomManager.closeRoom(roomId)
                sender.sendMessage(Component.text(if (closed) languageManager.getMessage("room.closed", roomId) else languageManager.getMessage("command.room_not_found", roomId)))
            }
            "stats" -> {
                val lines = roomManager.statsSnapshot().joinToString("\n") {
                    languageManager.getMessage("stats.snapshot_line", it.playerId, it.gameId, it.plays, it.wins, it.losses, it.kills, it.deaths, it.points)
                }
                sender.sendMessage(Component.text(if (lines.isBlank()) languageManager.getMessage("command.no_stats") else lines))
            }
            "packet" -> handlePacket(sender, label, shiftedArgs(args))
            "icon" -> handleIcon(sender, label, shiftedArgs(args))
            else -> {
                val moduleCommand = moduleAdminCommands[args.getOrNull(1)?.lowercase()]
                if (moduleCommand == null) {
                    showUsage(sender, label)
                } else {
                    moduleCommand.execute(sender, label, args.drop(2).toTypedArray())
                }
            }
        }
    }

    /** 分发核心资源与模块热重载，并输出每个模块的独立清理结果。 */
    private fun handleReload(sender: CommandSender, label: String, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase()) {
            "config" -> {
                val result = reloadService.reloadConfig()
                sender.sendMessage(Component.text(if (result.success) {
                    languageManager.getMessage("reload.config_success")
                } else {
                    languageManager.getMessage("reload.failed", "config", result.detail ?: "unknown")
                }))
            }
            "lang", "language" -> {
                val result = reloadService.reloadLanguage()
                sender.sendMessage(Component.text(if (result.success) {
                    languageManager.getMessage("reload.lang_success", result.value ?: "-")
                } else {
                    languageManager.getMessage("reload.failed", "lang", result.detail ?: "unknown")
                }))
            }
            "map", "maps" -> {
                val result = reloadService.reloadMaps()
                sender.sendMessage(Component.text(if (result.success) {
                    languageManager.getMessage("reload.map_success")
                } else {
                    languageManager.getMessage("reload.failed", "map", result.detail ?: "unknown")
                }))
            }
            "model", "module", "modules" -> {
                val moduleId = args.getOrNull(2)?.lowercase()
                if (moduleId == null) {
                    sender.sendMessage(Component.text(languageManager.getMessage("reload.usage", label)))
                    return
                }
                if (moduleId == "all") {
                    val results = reloadService.reloadAllModules()
                    if (results.isEmpty()) {
                        sender.sendMessage(Component.text(languageManager.getMessage("reload.no_modules")))
                        return
                    }
                    results.forEach { sendModuleReloadResult(sender, it) }
                    sender.sendMessage(Component.text(languageManager.getMessage(
                        "reload.all_summary",
                        results.count { it.success },
                        results.count { !it.success }
                    )))
                } else {
                    sendModuleReloadResult(sender, reloadService.reloadModule(moduleId))
                }
            }
            else -> sender.sendMessage(Component.text(languageManager.getMessage("reload.usage", label)))
        }
    }

    /** 以本地化格式反馈单个模块的版本、房间和编辑会话清理情况。 */
    private fun sendModuleReloadResult(sender: CommandSender, result: ManagedModuleReloadResult) {
        val message = when {
            !result.success -> languageManager.getMessage(
                "reload.module_failed",
                result.moduleId.ifBlank { "-" },
                result.closedRooms,
                result.closedEditorSessions,
                result.detail ?: "unknown"
            )
            result.active -> languageManager.getMessage(
                "reload.module_success",
                result.moduleId,
                result.version ?: "unknown",
                result.closedRooms,
                result.closedEditorSessions
            )
            else -> languageManager.getMessage(
                "reload.module_disabled",
                result.moduleId,
                result.closedRooms,
                result.closedEditorSessions
            )
        }
        sender.sendMessage(Component.text(message))
    }

    private fun handleIcon(sender: CommandSender, label: String, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_icon")))
            return
        }

        val action = args.getOrNull(1)?.lowercase()
        val text = args.drop(2).joinToString(" ").ifBlank { defaultIconTestText(player) }
        val component = IconTextParser.parse(text)

        when (action) {
            "chat" -> player.sendMessage(component)
            "title" -> player.showTitle(Title.title(
                component,
                IconTextParser.parse("&7KaGameCenter icon subtitle"),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))
            ))
            "actionbar" -> player.sendActionBar(component)
            "bossbar" -> {
                clearIconBossBar(player)
                val bossBar = BossBar.bossBar(component, 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
                iconBossBars[player.uniqueId] = bossBar
                player.showBossBar(bossBar)
            }
            "scoreboard" -> showIconScoreboard(player, component)
            "tab" -> {
                player.playerListName(component)
                player.sendPlayerListHeaderAndFooter(component, IconTextParser.parse("&7footer &item:[ender_pearl] <head:${player.name}>"))
            }
            "all" -> {
                player.sendMessage(component)
                player.sendActionBar(component)
                player.showTitle(Title.title(
                    component,
                    IconTextParser.parse("&7Title subtitle &item:[paper]"),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))
                ))
                clearIconBossBar(player)
                val bossBar = BossBar.bossBar(component, 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
                iconBossBars[player.uniqueId] = bossBar
                player.showBossBar(bossBar)
                showIconScoreboard(player, component)
                player.playerListName(component)
                player.sendPlayerListHeaderAndFooter(component, IconTextParser.parse("&7footer &item:[ender_pearl] <head:${player.name}>"))
            }
            "clear" -> {
                clearIconBossBar(player)
                player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                player.playerListName(Component.text(player.name))
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty())
                player.resetTitle()
                player.sendActionBar(Component.empty())
                player.sendMessage(Component.text(languageManager.getMessage("icon.cleared")))
                return
            }
            else -> {
                sender.sendMessage(Component.text(languageManager.getMessage("icon.usage", label)))
                return
            }
        }

        player.sendMessage(Component.text(languageManager.getMessage("icon.sent", action ?: "")))
    }

    private fun showIconScoreboard(player: Player, component: Component) {
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective = scoreboard.registerNewObjective("kgc_icon", Criteria.DUMMY, component)
        objective.displaySlot = DisplaySlot.SIDEBAR

        listOf(
            IconTextParser.parse("&a标题和行测试"),
            component,
            IconTextParser.parse("&item:[diamond] &f物品图标"),
            IconTextParser.parse("<head:${player.name}> &f玩家头像")
        ).forEachIndexed { index, line ->
            val entry = "§${index.toString(16)}"
            val team = scoreboard.registerNewTeam("kgc_icon_$index")
            team.addEntry(entry)
            team.prefix(line)
            objective.getScore(entry).score = 4 - index
        }

        player.scoreboard = scoreboard
    }

    private fun clearIconBossBar(player: Player) {
        iconBossBars.remove(player.uniqueId)?.let(player::hideBossBar)
    }

    private fun defaultIconTestText(player: Player): String {
        return "&a&item:[diamond] icon-test <head:${player.name}> &f${player.name}"
    }

    private fun handlePacket(sender: CommandSender, label: String, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(Component.text(languageManager.getMessage("command.only_player_packet")))
            return
        }

        if (!packetService.available) {
            sender.sendMessage(Component.text(languageManager.getMessage("packet.unavailable")))
            return
        }

        when (args.getOrNull(1)?.lowercase()) {
            "probe" -> {
                val message = args.drop(2).joinToString(" ").ifBlank { "KaGameCenter PacketEvents probe" }
                packetService.showProbe(player, message)
                player.sendMessage(Component.text(languageManager.getMessage("packet.sent_probe", message)))
            }
            "blockself" -> {
                val material = parseMaterial(args.getOrNull(2)) ?: Material.DIAMOND_BLOCK
                val seconds = parseSeconds(args.getOrNull(3))
                packetService.disguisePlayerAsBlock(player, material, Bukkit.getOnlinePlayers(), seconds)
                player.sendMessage(Component.text(languageManager.getMessage("packet.sent_blockself", material.name, seconds)))
            }
            "mobself" -> {
                val entityType = parseEntityType(args.getOrNull(2)) ?: EntityType.PIG
                val seconds = parseSeconds(args.getOrNull(3))
                packetService.disguisePlayerAsMob(player, entityType, Bukkit.getOnlinePlayers(), seconds)
                player.sendMessage(Component.text(languageManager.getMessage("packet.sent_mobself", entityType.name, seconds)))
            }
            "blockglow" -> {
                val seconds = parseSeconds(args.getOrNull(2))
                packetService.showBlockGlow(player, player.getTargetBlockExact(12)?.location ?: player.location.block.location, seconds)
                player.sendMessage(Component.text(languageManager.getMessage("packet.sent_blockglow", seconds)))
            }
            "playerglow" -> {
                val targetName = args.getOrNull(2)
                val target = targetName?.let(Bukkit::getPlayerExact) ?: player
                val seconds = parseSeconds(args.getOrNull(3))
                packetService.showPlayerGlow(player, target, seconds)
                player.sendMessage(Component.text(languageManager.getMessage("packet.sent_playerglow", target.name, seconds)))
            }
            "drop" -> {
                val material = parseMaterial(args.getOrNull(2)) ?: Material.DIAMOND
                val seconds = parseSeconds(args.getOrNull(3))
                val scale = parseScale(args.getOrNull(4))
                packetService.showPrivatePickup(
                    player,
                    player.location.add(player.location.direction.normalize().multiply(2)),
                    ItemStack(material),
                    glowing = true,
                    color = NamedTextColor.AQUA,
                    durationSeconds = seconds,
                    scale = scale
                ) { picker ->
                    picker.inventory.addItem(ItemStack(material))
                }
                player.sendMessage(Component.text(languageManager.getMessage("packet.sent_drop", material.name, seconds, scale)))
            }
            "beam" -> {
                val seconds = parseSeconds(args.getOrNull(2))
                val color = parseNamedTextColor(args.getOrNull(3)) ?: NamedTextColor.AQUA
                packetService.showBeaconBeam(player, player.location.add(player.location.direction.normalize().multiply(2)), color, seconds)
                player.sendMessage(Component.text(languageManager.getMessage("packet.sent_beam", color.toString(), seconds)))
            }
            "clear" -> {
                packetService.clearViewer(player)
                player.sendMessage(Component.text(languageManager.getMessage("packet.cleared")))
            }
            else -> usage(sender, label)
        }
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

    private fun completeUser(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.firstOrNull()?.equals("friend", ignoreCase = true) == true) {
            val player = sender as? Player ?: return emptyList()
            return friendCommandService.tabComplete(player, args.drop(1))
        }
        if (args.firstOrNull()?.equals("reload", ignoreCase = true) == true) {
            if (!sender.hasPermission("kagamecenter.admin")) return emptyList()
            return when (args.size) {
                2 -> reloadTargets.filter { it.startsWith(args[1], ignoreCase = true) }
                3 -> if (args[1].equals("model", ignoreCase = true) ||
                    args[1].equals("module", ignoreCase = true) ||
                    args[1].equals("modules", ignoreCase = true)
                ) {
                    (listOf("all") + reloadService.reloadableModuleIds())
                        .distinct()
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                } else {
                    emptyList()
                }
                else -> emptyList()
            }
        }
        return when (args.size) {
            2 -> {
                val prefix = args[1].lowercase()
                when (args[0].lowercase()) {
                    "join" -> roomManager.listRooms().map { it.id }.filter { it.startsWith(prefix) }
                    "chest" -> listOf("main").filter { it.startsWith(prefix) }
                    "stats", "selector" -> (
                        roomManager.listModules().map { it.id } +
                            roomManager.listDefinitions().map { it.id } +
                            managedGameCatalog.all().map { it.globalId }
                        ).distinct().filter { it.startsWith(prefix) }
                    "create" -> completeCreate(args)
                    "quickjoin" -> (roomManager.listDefinitions().map { it.id } + managedGameCatalog.all().map { it.globalId }).filter { it.startsWith(prefix) }
                    else -> emptyList()
                }
            }
            3 -> {
                when (args[0].lowercase()) {
                    "create" -> completeCreate(args)
                    "selector" -> (listOf("default") + managedGameCatalog.all()
                        .filter {
                            it.moduleId.equals(args[1], ignoreCase = true) ||
                                it.globalId.equals(args[1], ignoreCase = true)
                        }
                        .map { it.selectorGroup })
                        .distinct()
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun completeCreate(args: Array<out String>): List<String> {
        val prefix = args.lastOrNull()?.lowercase().orEmpty()
        return when (args.size) {
            2 -> (roomManager.listDefinitions().map { it.id } + managedGameCatalog.all().map { it.globalId }).filter { it.startsWith(prefix) }
            3 -> mapManager.listMaps(args[1]).map { it.relativePath }.filter { it.startsWith(prefix) }
            else -> emptyList()
        }
    }

    private fun completeAdmin(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> {
                val prefix = args[1].lowercase()
                (adminSubCommands + moduleAdminCommands.keys)
                    .distinct()
                    .filter { it.startsWith(prefix) }
            }
            else -> {
                val adminArgs = shiftedArgs(args)
                when (args.getOrNull(1)?.lowercase()) {
                    "maps" -> completeMaps(adminArgs)
                    "packet" -> completePacket(adminArgs)
                    "icon" -> completeIcon(adminArgs)
                    "manage" -> {
                        if (args.size != 3) return emptyList()
                        managedGameCatalog.all()
                            .map { it.globalId }
                            .sortedBy { it.lowercase() }
                            .filter { it.startsWith(args[2], ignoreCase = true) }
                    }
                    in moduleAdminCommands.keys -> moduleAdminCommands[args[1].lowercase()]?.tabComplete(sender, args.drop(2).toTypedArray()).orEmpty()
                    "create" -> {
                        if (args.size != 3) return emptyList()
                        val prefix = args[2].lowercase()
                        (roomManager.listDefinitions().map { it.id } + managedGameCatalog.all().map { it.globalId })
                            .distinct()
                            .filter { it.startsWith(prefix) }
                    }
                    "start", "close" -> {
                        if (args.size != 3) return emptyList()
                        val prefix = args[2].lowercase()
                        roomManager.listRooms().map { it.id }.filter { it.startsWith(prefix) }
                    }
                    else -> emptyList()
                }
            }
        }
    }

    private fun completeIcon(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> {
                val prefix = args[1].lowercase()
                listOf("all", "chat", "title", "actionbar", "bossbar", "scoreboard", "tab", "clear")
                    .filter { it.startsWith(prefix) }
            }
            3 -> listOf("&item:[diamond]", "&item:[stone]", "<head:")
                .filter { it.lowercase().startsWith(args[2].lowercase()) }
            else -> emptyList()
        }
    }

    private fun completePacket(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> {
                val prefix = args[1].lowercase()
                listOf("probe", "blockself", "mobself", "blockglow", "playerglow", "drop", "beam", "clear").filter { it.startsWith(prefix) }
            }
            3 -> {
                val action = args[1].lowercase()
                val prefix = args[2].uppercase()
                when (action) {
                    "blockself", "drop" -> Material.entries
                        .asSequence()
                        .filter { it.isItem || it.isBlock }
                        .map { it.name }
                        .filter { it.startsWith(prefix) }
                        .take(30)
                        .toList()
                    "mobself" -> EntityType.entries
                        .asSequence()
                        .map { it.name }
                        .filter { it.startsWith(prefix) }
                        .take(30)
                        .toList()
                    "playerglow" -> Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                    "beam" -> listOf("aqua", "yellow", "green", "red", "blue", "light_purple", "white")
                        .filter { it.startsWith(args[2].lowercase()) }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun parseMaterial(value: String?): Material? {
        return value?.uppercase()?.let { runCatching { Material.valueOf(it) }.getOrNull() }
    }

    private fun parseEntityType(value: String?): EntityType? {
        return value?.uppercase()?.let { runCatching { EntityType.valueOf(it) }.getOrNull() }
    }

    private fun parseSeconds(value: String?): Int {
        return value?.toIntOrNull()?.coerceIn(1, 120) ?: 15
    }

    private fun parseScale(value: String?): Float {
        return value?.toFloatOrNull()?.coerceIn(0.25f, 8.0f) ?: 1.8f
    }

    private fun parseNamedTextColor(value: String?): NamedTextColor? {
        if (value == null) return null
        return NamedTextColor.NAMES.value(value.lowercase())
    }

    private fun shiftedArgs(args: Array<out String>): Array<String> {
        if (args.size <= 1) return emptyArray()
        return args.drop(1).toTypedArray()
    }

    private fun showHelp(sender: CommandSender, label: String): Boolean {
        sender.sendMessage(Component.text(languageManager.getMessage("command.help", label)))
        return true
    }

    private fun showAdminHelp(sender: CommandSender, label: String): Boolean {
        sender.sendMessage(Component.text(languageManager.getMessage("command.admin_help", label)))
        return true
    }

    private fun showUsage(sender: CommandSender, label: String) {
        usage(sender, label)
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
