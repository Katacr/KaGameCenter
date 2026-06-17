package org.katacr.kaGameCenter.dialog

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.game.GameMapManager
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.i18n.LanguageManager
import java.lang.reflect.Proxy

class GameCenterMenuService(
    private val plugin: JavaPlugin,
    private val fallbackDialogService: GameCenterDialogService,
    private val roomManager: GameRoomManager,
    private val mapManager: GameMapManager,
    private val languageManager: LanguageManager
) {
    private var actionHandlerRegistered = false
    private var actionHandlerProxy: Any? = null

    fun init() {
        registerActionHandler()
    }

    fun shutdown() {
        if (!actionHandlerRegistered) return
        runCatching {
            val apiClass = Class.forName("org.katacr.kamenu.api.KaMenuAPI")
            val method = apiClass.getMethod("unregisterActionHandler", String::class.java)
            method.invoke(null, ACTION_NAMESPACE)
        }
        actionHandlerRegistered = false
        actionHandlerProxy = null
    }

    fun openMainMenu(player: Player) {
        openOrFallback(player, mainMenuConfig(player), "kagamecenter:main")
    }

    fun openGamesMenu(player: Player) {
        openOrFallback(player, gamesMenuConfig(), "kagamecenter:games")
    }

    fun openRoomsMenu(player: Player) {
        openOrFallback(player, roomsMenuConfig(), "kagamecenter:rooms")
    }

    fun openRoomMenu(player: Player, roomId: String) {
        val room = roomManager.getRoom(roomId)
        if (room == null) {
            player.sendMessage(Component.text(languageManager.getMessage("command.room_not_found", roomId)))
            openRoomsMenu(player)
            return
        }
        openOrFallback(player, roomMenuConfig(room), "kagamecenter:room:$roomId")
    }

    fun openMapsMenu(player: Player) {
        openOrFallback(player, mapsMenuConfig(), "kagamecenter:maps")
    }

    private fun openMapsForGameMenu(player: Player, gameId: String) {
        openOrFallback(player, mapsForGameConfig(gameId), "kagamecenter:maps:$gameId")
    }

    private fun openMapMenu(player: Player, gameId: String, mapId: String) {
        val map = mapManager.listMaps(gameId).firstOrNull { it.mapId == mapId }
        if (map == null) {
            player.sendMessage(Component.text(languageManager.getMessage("map.not_found", "$gameId/$mapId")))
            openMapsForGameMenu(player, gameId)
            return
        }
        openOrFallback(player, mapDetailConfig(map.gameId, map.mapId), "kagamecenter:map:${map.gameId}/${map.mapId}")
    }

    private fun openCreateMapMenu(player: Player, gameId: String) {
        openOrFallback(player, createMapConfig(gameId), "kagamecenter:map-create:$gameId")
    }

    private fun openOrFallback(player: Player, config: YamlConfiguration, contextId: String) {
        if (!openKaMenuConfig(player, config, contextId)) {
            fallbackDialogService.openMainDialog(player)
        }
    }

    private fun openKaMenuConfig(player: Player, config: YamlConfiguration, contextId: String): Boolean {
        if (!isKaMenuEnabled()) return false
        registerActionHandler()

        return runCatching {
            val apiClass = Class.forName("org.katacr.kamenu.api.KaMenuAPI")
            val openConfig = apiClass.getMethod(
                "openConfig",
                Player::class.java,
                YamlConfiguration::class.java,
                String::class.java
            )
            openConfig.invoke(null, player, config, contextId) == true
        }.getOrElse {
            runCatching {
                val apiClass = Class.forName("org.katacr.kamenu.api.KaMenuAPI")
                val openYaml = apiClass.getMethod(
                    "openYaml",
                    Player::class.java,
                    String::class.java,
                    String::class.java
                )
                openYaml.invoke(null, player, config.saveToString(), contextId) == true
            }.getOrDefault(false)
        }
    }

    private fun registerActionHandler() {
        if (actionHandlerRegistered || !isKaMenuEnabled()) return

        runCatching {
            val apiClass = Class.forName("org.katacr.kamenu.api.KaMenuAPI")
            val handlerInterface = Class.forName("org.katacr.kamenu.api.KaMenuActionHandler")
            val proxy = Proxy.newProxyInstance(
                handlerInterface.classLoader,
                arrayOf(handlerInterface)
            ) { _, method, args ->
                when (method.name) {
                    "execute" -> {
                        val player = args?.getOrNull(0) as? Player ?: return@newProxyInstance false
                        val action = args.getOrNull(1) as? String ?: return@newProxyInstance false
                        handleExternalAction(player, action)
                    }
                    "toString" -> "KaGameCenterKaMenuActionHandler"
                    else -> false
                }
            }
            val registerMethod = apiClass.getMethod("registerActionHandler", String::class.java, handlerInterface)
            if (registerMethod.invoke(null, ACTION_NAMESPACE, proxy) == true) {
                actionHandlerRegistered = true
                actionHandlerProxy = proxy
                plugin.logger.info("Registered KaMenu action handler: $ACTION_NAMESPACE")
            }
        }.onFailure {
            plugin.logger.fine("KaMenu external action API is not available: ${it.message}")
        }
    }

    private fun handleExternalAction(player: Player, action: String): Boolean {
        val payload = action.trim().removePrefix("$ACTION_NAMESPACE:").trim()
        if (payload.isBlank()) return false

        runSync {
            val parts = payload.split(Regex("\\s+")).filter { it.isNotBlank() }
            when (parts.firstOrNull()) {
                "open-main" -> openMainMenu(player)
                "open-games" -> openGamesMenu(player)
                "open-rooms" -> openRoomsMenu(player)
                "open-room" -> parts.getOrNull(1)?.let { openRoomMenu(player, it) }
                "open-maps" -> openMapsMenu(player)
                "open-maps-game" -> parts.getOrNull(1)?.let { openMapsForGameMenu(player, it) }
                "open-map" -> if (parts.size >= 3) openMapMenu(player, parts[1], parts[2])
                "open-map-create" -> parts.getOrNull(1)?.let { openCreateMapMenu(player, it) }
                "quickjoin" -> quickJoin(player, parts.getOrNull(1) ?: DEFAULT_GAME_ID)
                "join" -> parts.getOrNull(1)?.let { joinRoom(player, it) }
                "start" -> parts.getOrNull(1)?.let { startRoom(player, it) }
                "leave" -> leaveRoom(player)
                "close-room" -> if (requireAdmin(player)) parts.getOrNull(1)?.let { closeRoom(player, it) }
                "map-select" -> if (parts.size >= 3 && requireAdmin(player)) handleMapResult(player, mapManager.selectMap(parts[1], parts[2])) {
                    openMapMenu(player, parts[1], parts[2])
                }
                "map-setspawn" -> if (parts.size >= 3 && requireAdmin(player)) {
                    val location = player.location
                    handleMapResult(
                        player,
                        mapManager.setSpawn(parts[1], parts[2], location.x, location.y, location.z, location.yaw, location.pitch)
                    ) { openMapMenu(player, parts[1], parts[2]) }
                }
                "map-create" -> if (parts.size >= 3 && requireAdmin(player)) {
                    handleMapResult(player, mapManager.createMap(parts[1], parts[2])) {
                        openMapsForGameMenu(player, parts[1])
                    }
                }
                "map-reload" -> if (requireAdmin(player)) handleMapResult(player, mapManager.reload()) { openMapsMenu(player) }
                else -> return@runSync
            }
        }
        return true
    }

    private fun quickJoin(player: Player, gameId: String) {
        val room = roomManager.joinNewRoom(player, gameId)
        player.sendMessage(Component.text(
            if (room == null) languageManager.getMessage("command.game_not_found", gameId)
            else languageManager.getMessage("room.quick_joined", room.id)
        ))
    }

    private fun joinRoom(player: Player, roomId: String) {
        val joined = roomManager.joinRoom(player, roomId)
        if (!joined) {
            player.sendMessage(Component.text(languageManager.getMessage("room.join_failed", roomId)))
            openRoomMenu(player, roomId)
        }
    }

    private fun startRoom(player: Player, roomId: String) {
        val started = roomManager.startRoom(roomId)
        player.sendMessage(Component.text(
            if (started) languageManager.getMessage("room.started", roomId)
            else languageManager.getMessage("room.start_failed", roomId)
        ))
        openRoomMenu(player, roomId)
    }

    private fun leaveRoom(player: Player) {
        val left = roomManager.leaveCurrentRoom(player)
        player.sendMessage(Component.text(
            if (left) languageManager.getMessage("room.left")
            else languageManager.getMessage("command.no_room")
        ))
        openMainMenu(player)
    }

    private fun closeRoom(player: Player, roomId: String) {
        val closed = roomManager.closeRoom(roomId)
        player.sendMessage(Component.text(
            if (closed) languageManager.getMessage("room.closed", roomId)
            else languageManager.getMessage("command.room_not_found", roomId)
        ))
        openRoomsMenu(player)
    }

    private fun handleMapResult(player: Player, result: org.katacr.kaGameCenter.game.GameMapResult, next: () -> Unit) {
        player.sendMessage(Component.text(result.message))
        next()
    }

    private fun requireAdmin(player: Player): Boolean {
        if (player.hasPermission("kagamecenter.admin")) return true
        player.sendMessage(Component.text(languageManager.getMessage("command.no_permission")))
        return false
    }

    private fun mainMenuConfig(player: Player): YamlConfiguration {
        return menu(languageManager.getMessage("menu.main_title")).apply {
            message("intro", listOf(
                languageManager.getMessage("menu.main_line_1"),
                languageManager.getMessage("menu.main_line_2")
            ))
            multi(columns = 2)
            button("games", languageManager.getMessage("menu.button_games"), "kgc:open-games")
            button("rooms", languageManager.getMessage("menu.button_rooms"), "kgc:open-rooms")
            button("quick", languageManager.getMessage("menu.button_quickjoin"), "kgc:quickjoin $DEFAULT_GAME_ID")
            if (player.hasPermission("kagamecenter.admin")) {
                button("maps", languageManager.getMessage("menu.button_maps"), "kgc:open-maps")
            }
            if (roomManager.getPlayerRoom(player) != null) {
                button("leave", languageManager.getMessage("menu.button_leave"), "kgc:leave")
            }
            exit("close")
        }
    }

    private fun gamesMenuConfig(): YamlConfiguration {
        return menu(languageManager.getMessage("menu.games_title")).apply {
            val games = roomManager.listDefinitions().toList()
            message("intro", if (games.isEmpty()) {
                languageManager.getMessage("command.no_games")
            } else {
                languageManager.getMessage("menu.games_line", games.size)
            })
            multi(columns = 1)
            games.forEachIndexed { index, game ->
                button(
                    "game_$index",
                    languageManager.getMessage("menu.game_button", game.displayName),
                    "kgc:quickjoin ${game.id}",
                    listOf(
                        languageManager.getMessage("menu.game_tooltip_players", game.minPlayers, game.maxPlayers),
                        languageManager.getMessage("menu.game_tooltip_duration", game.defaultDurationSeconds),
                        game.description.ifBlank { game.id }
                    )
                )
            }
            exit("kgc:open-main", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun roomsMenuConfig(): YamlConfiguration {
        return menu(languageManager.getMessage("menu.rooms_title")).apply {
            val rooms = roomManager.listRooms().toList()
            message("intro", if (rooms.isEmpty()) {
                languageManager.getMessage("room.status_empty")
            } else {
                languageManager.getMessage("menu.rooms_line", rooms.size)
            })
            multi(columns = 1)
            rooms.forEachIndexed { index, room ->
                val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
                button(
                    "room_$index",
                    languageManager.getMessage("menu.room_button", room.id, room.players.size, maxPlayers),
                    "kgc:open-room ${room.id}",
                    listOf(
                        languageManager.getMessage("display.sidebar_game", room.definition?.displayName ?: room.module.displayName),
                        languageManager.getMessage("display.sidebar_state", room.state),
                        languageManager.getMessage("display.sidebar_world", room.world?.name ?: "-")
                    )
                )
            }
            exit("kgc:open-main", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun roomMenuConfig(room: GameRoom): YamlConfiguration {
        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        return menu(languageManager.getMessage("menu.room_title", room.id)).apply {
            message("intro", listOf(
                languageManager.getMessage("display.sidebar_game", room.definition?.displayName ?: room.module.displayName),
                languageManager.getMessage("display.sidebar_state", room.state),
                languageManager.getMessage("display.sidebar_players", room.players.size, maxPlayers),
                languageManager.getMessage("display.sidebar_world", room.world?.name ?: "-")
            ))
            multi(columns = 2)
            button("join", languageManager.getMessage("menu.button_join_room"), "kgc:join ${room.id}")
            button("start", languageManager.getMessage("menu.button_start_room"), "kgc:start ${room.id}")
            button("leave", languageManager.getMessage("menu.button_leave"), "kgc:leave")
            button("close_room", languageManager.getMessage("menu.button_close_room"), "kgc:close-room ${room.id}")
            exit("kgc:open-rooms", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun mapsMenuConfig(): YamlConfiguration {
        return menu(languageManager.getMessage("menu.maps_title")).apply {
            val games = mapManager.listGames()
            message("intro", if (games.isEmpty()) languageManager.getMessage("map.no_games") else languageManager.getMessage("menu.maps_line", games.size))
            multi(columns = 1)
            games.forEachIndexed { index, gameId ->
                button("map_game_$index", languageManager.getMessage("menu.map_game_button", gameId), "kgc:open-maps-game $gameId")
            }
            button("reload", languageManager.getMessage("menu.button_reload_maps"), "kgc:map-reload")
            exit("kgc:open-main", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun mapsForGameConfig(gameId: String): YamlConfiguration {
        return menu(languageManager.getMessage("menu.maps_game_title", gameId)).apply {
            val maps = mapManager.listMaps(gameId)
            message("intro", if (maps.isEmpty()) languageManager.getMessage("map.none", gameId) else languageManager.getMessage("menu.maps_game_line", maps.size))
            multi(columns = 1)
            maps.forEachIndexed { index, map ->
                button(
                    "map_$index",
                    languageManager.getMessage(
                        "menu.map_button",
                        map.mapId,
                        if (map.active) languageManager.getMessage("map.active") else languageManager.getMessage("map.inactive")
                    ),
                    "kgc:open-map ${map.gameId} ${map.mapId}",
                    listOf(map.relativePath, map.folder.absolutePath)
                )
            }
            button("create", languageManager.getMessage("menu.button_create_map"), "kgc:open-map-create $gameId")
            exit("kgc:open-maps", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun mapDetailConfig(gameId: String, mapId: String): YamlConfiguration {
        return menu(languageManager.getMessage("menu.map_title", "$gameId/$mapId")).apply {
            message("intro", listOf(
                languageManager.getMessage("menu.map_detail_path", "$gameId/$mapId"),
                languageManager.getMessage("menu.map_detail_setspawn")
            ))
            multi(columns = 2)
            button("select", languageManager.getMessage("menu.button_select_map"), "kgc:map-select $gameId $mapId")
            button("setspawn", languageManager.getMessage("menu.button_setspawn"), "kgc:map-setspawn $gameId $mapId")
            exit("kgc:open-maps-game $gameId", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun createMapConfig(gameId: String): YamlConfiguration {
        return menu(languageManager.getMessage("menu.create_map_title", gameId)).apply {
            message("intro", languageManager.getMessage("menu.create_map_line", gameId))
            set("Inputs.map_id.type", "input")
            set("Inputs.map_id.text", languageManager.getMessage("menu.input_map_id"))
            set("Inputs.map_id.default", "new_map")
            set("Inputs.map_id.max_length", 32)
            set("Bottom.type", "confirmation")
            set("Bottom.confirm.text", languageManager.getMessage("menu.button_create_map"))
            set("Bottom.confirm.actions", listOf("kgc:map-create $gameId \$(map_id)"))
            set("Bottom.deny.text", languageManager.getMessage("menu.button_back"))
            set("Bottom.deny.actions", listOf("kgc:open-maps-game $gameId"))
        }
    }

    private fun menu(title: String): YamlConfiguration {
        return YamlConfiguration().apply {
            set("Title", title)
            set("Settings.can_escape", true)
            set("Settings.after_action", "NONE")
        }
    }

    private fun YamlConfiguration.message(key: String, text: String, width: Int = 320) {
        set("Body.$key.type", "message")
        set("Body.$key.text", text)
        set("Body.$key.width", width)
    }

    private fun YamlConfiguration.message(key: String, text: List<String>, width: Int = 320) {
        set("Body.$key.type", "message")
        set("Body.$key.text", text)
        set("Body.$key.width", width)
    }

    private fun YamlConfiguration.multi(columns: Int) {
        set("Bottom.type", "multi")
        set("Bottom.columns", columns)
    }

    private fun YamlConfiguration.button(key: String, text: String, action: String, tooltip: List<String> = emptyList()) {
        set("Bottom.buttons.$key.text", text)
        set("Bottom.buttons.$key.actions", listOf(action))
        if (tooltip.isNotEmpty()) {
            set("Bottom.buttons.$key.tooltip", tooltip)
        }
    }

    private fun YamlConfiguration.exit(action: String, text: String = languageManager.getMessage("menu.button_close")) {
        set("Bottom.exit.text", text)
        set("Bottom.exit.actions", listOf(action))
    }

    private fun runSync(block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            block()
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable { block() })
        }
    }

    private fun isKaMenuEnabled(): Boolean {
        return plugin.server.pluginManager.getPlugin("KaMenu")?.isEnabled == true
    }

    companion object {
        private const val ACTION_NAMESPACE = "kgc"
        private const val DEFAULT_GAME_ID = "parkour"
    }
}
