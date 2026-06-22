package org.katacr.kaGameCenter.dialog

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.game.GameDefinition
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.game.GameMapInfo
import org.katacr.kaGameCenter.game.GameMapManager
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.menu.chest.ChestMenuService
import org.katacr.kaGameCenter.team.GameTeam
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.velocity.VelocityBridgeService
import org.katacr.kaGameCenter.velocity.VelocityRoomSnapshot
import java.lang.reflect.Proxy
import java.util.UUID

class GameCenterMenuService(
    private val plugin: JavaPlugin,
    private val fallbackDialogService: GameCenterDialogService,
    private val roomManager: GameRoomManager,
    private val mapManager: GameMapManager,
    private val teamService: GameTeamService,
    private val languageManager: LanguageManager,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val velocityBridgeService: VelocityBridgeService
) {
    private val templateService = MenuTemplateService(plugin)
    private var chestMenuService: ChestMenuService? = null
    private var actionHandlerRegistered = false
    private var actionHandlerProxy: Any? = null

    fun init() {
        templateService.init()
        registerActionHandler()
    }

    fun bindChestMenuService(service: ChestMenuService) {
        chestMenuService = service
    }

    fun isKaMenuAvailable(): Boolean = isKaMenuEnabled()

    fun isActionHandlerRegistered(): Boolean = actionHandlerRegistered

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

    private fun openCreateGameMenu(player: Player) {
        openOrFallback(player, createGameMenuConfig(), "kagamecenter:create-game")
    }

    private fun openCreateMapSelectMenu(player: Player, gameId: String) {
        openOrFallback(player, createMapSelectMenuConfig(gameId), "kagamecenter:create-map:$gameId")
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
        openOrFallback(player, roomMenuConfig(room, player), "kagamecenter:room:$roomId")
    }

    private fun openMemberMenu(player: Player, roomId: String, targetId: UUID) {
        val room = roomManager.getRoom(roomId)
        if (room == null || !room.players.contains(targetId)) {
            player.sendMessage(Component.text(languageManager.getMessage("command.room_not_found", roomId)))
            openRoomsMenu(player)
            return
        }
        openOrFallback(player, memberMenuConfig(room, targetId), "kagamecenter:room-member:$roomId/$targetId")
    }

    fun openMapsMenu(player: Player) {
        openOrFallback(player, mapsMenuConfig(), "kagamecenter:maps")
    }

    fun openAdminManageMenu(player: Player) {
        openOrFallback(player, adminManageMenuConfig(), "kagamecenter:admin-manage")
    }

    fun openAdminManagedGamesMenu(player: Player) {
        openOrFallback(player, adminManagedGamesConfig(), "kagamecenter:admin-managed-games")
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
        if (!openKaMenuConfig(player, config, contextId) && chestMenuService?.openConfig(player, config, contextId) != true) {
            fallbackDialogService.openMainDialog(player)
        }
    }

    fun openExternalConfig(player: Player, config: YamlConfiguration, contextId: String): Boolean {
        return openKaMenuConfig(player, config, contextId) || chestMenuService?.openConfig(player, config, contextId) == true
    }

    fun handleAction(player: Player, action: String, variables: Map<String, String> = emptyMap()): Boolean {
        return handleExternalAction(player, action, variables)
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
                        val variables = (args.getOrNull(2) as? Map<*, *>)
                            ?.mapNotNull { (key, value) ->
                                val stringKey = key as? String ?: return@mapNotNull null
                                stringKey to value.toString()
                            }
                            ?.toMap()
                            ?: emptyMap()
                        handleExternalAction(player, action, variables)
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

    private fun handleExternalAction(player: Player, action: String, variables: Map<String, String> = emptyMap()): Boolean {
        val payload = action.trim().removePrefix("$ACTION_NAMESPACE:").trim()
        if (payload.isBlank()) return false

        runSync {
            val parts = payload.split(Regex("\\s+")).filter { it.isNotBlank() }
            when (parts.firstOrNull()) {
                "open-main" -> openMainMenu(player)
                "open-games" -> openGamesMenu(player)
                "open-create-game" -> openCreateGameMenu(player)
                "open-create-map" -> parts.getOrNull(1)?.let { openCreateMapSelectMenu(player, it) }
                "open-admin-manage" -> if (requireAdmin(player)) openAdminManageMenu(player)
                "open-admin-managed-games" -> if (requireAdmin(player)) openAdminManagedGamesMenu(player)
                "open-admin-game-editor" -> if (requireAdmin(player)) parts.getOrNull(1)?.let { managedGameCatalog.openEditor(player, it) }
                "open-rooms" -> openRoomsMenu(player)
                "open-room" -> parts.getOrNull(1)?.let { openRoomMenu(player, it) }
                "open-member" -> if (parts.size >= 3) openMemberMenu(player, parts[1], UUID.fromString(parts[2]))
                "open-maps" -> openMapsMenu(player)
                "open-maps-game" -> parts.getOrNull(1)?.let { openMapsForGameMenu(player, it) }
                "open-map" -> if (parts.size >= 3) openMapMenu(player, parts[1], parts[2])
                "open-map-create" -> parts.getOrNull(1)?.let { openCreateMapMenu(player, it) }
                "admin-create-managed-game" -> if (requireAdmin(player)) createManagedGame(player, variables)
                "module-game-action" -> if (requireAdmin(player)) {
                    val gameId = parts.getOrNull(1) ?: return@runSync
                    val actionName = parts.drop(2).joinToString(" ")
                    managedGameCatalog.handleEditorAction(player, gameId, actionName, variables)
                }
                "create-room" -> createRoom(
                    player,
                    parts.getOrNull(1) ?: DEFAULT_GAME_ID,
                    parts.getOrNull(2),
                    variables["room_name"]
                )
                "create-room-config" -> createRoom(
                    player,
                    parts.getOrNull(1) ?: return@runSync,
                    null,
                    variables["room_name"]
                )
                "quickjoin" -> quickJoin(player, parts.getOrNull(1) ?: DEFAULT_GAME_ID)
                "join" -> parts.getOrNull(1)?.let { joinRoom(player, it) }
                "spectate" -> parts.getOrNull(1)?.let { spectateRoom(player, it) }
                "proxy-join" -> if (parts.size >= 3) proxyJoinRoom(player, parts[1], parts[2])
                "proxy-spectate" -> if (parts.size >= 3) proxySpectateRoom(player, parts[1], parts[2])
                "start" -> parts.getOrNull(1)?.let { startRoom(player, it) }
                "leave" -> leaveRoom(player)
                "close-room" -> if (requireAdmin(player)) parts.getOrNull(1)?.let { closeRoom(player, it) }
                "kick-player" -> if (parts.size >= 3 && requireRoomOwner(player, parts[1])) {
                    if (roomManager.kickPlayer(parts[1], UUID.fromString(parts[2]))) {
                        openRoomMenu(player, parts[1])
                    }
                }
                "transfer-owner" -> if (parts.size >= 3 && requireRoomOwner(player, parts[1])) {
                    if (roomManager.transferOwner(parts[1], UUID.fromString(parts[2]))) {
                        openRoomMenu(player, parts[1])
                    }
                }
                "join-team" -> if (parts.size >= 3) {
                    if (teamService.join(parts[1], player, parts[2])) {
                        openRoomMenu(player, parts[1])
                    }
                }
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
        if (roomManager.isPlaying(player.uniqueId)) {
            player.sendMessage(Component.text(languageManager.getMessage("room.already_in_room")))
            return
        }
        val room = roomManager.joinNewRoom(player, gameId)
        player.sendMessage(Component.text(
            if (room == null) roomManager.createRoomFailureMessage(gameId)
            else languageManager.getMessage("room.quick_joined", room.id)
        ))
        if (room != null) {
            openRoomMenu(player, room.id)
        }
    }

    private fun createRoom(player: Player, gameId: String, mapTemplate: String? = null, roomName: String? = null) {
        if (roomManager.isPlaying(player.uniqueId)) {
            player.sendMessage(Component.text(languageManager.getMessage("room.already_in_room")))
            return
        }
        val room = roomManager.createRoom(gameId, player.uniqueId, mapTemplate, roomName)
        player.sendMessage(Component.text(
            if (room == null) roomManager.createRoomFailureMessage(gameId)
            else languageManager.getMessage("room.created", room.id)
        ))
        if (room != null) {
            roomManager.joinRoom(player, room.id)
            openRoomMenu(player, room.id)
        }
    }

    private fun joinRoom(player: Player, roomId: String) {
        val joined = roomManager.joinRoom(player, roomId)
        if (!joined) {
            player.sendMessage(Component.text(languageManager.getMessage("room.join_failed", roomId)))
            openRoomMenu(player, roomId)
        } else {
            openRoomMenu(player, roomId)
        }
    }

    private fun spectateRoom(player: Player, roomId: String) {
        val joined = roomManager.spectateRoom(player, roomId)
        if (!joined) {
            player.sendMessage(Component.text(languageManager.getMessage("room.spectate_failed", roomId)))
            openRoomsMenu(player)
        } else {
            player.sendMessage(Component.text(languageManager.getMessage("room.spectating", roomId)))
            openRoomMenu(player, roomId)
        }
    }

    private fun proxyJoinRoom(player: Player, serverId: String, roomId: String) {
        if (serverId == velocityBridgeService.serverId) {
            joinRoom(player, roomId)
            return
        }
        val requested = velocityBridgeService.requestRemoteJoin(player, serverId, roomId)
        player.sendMessage(Component.text(
            if (requested) languageManager.getMessage("velocity.remote_join_requested", serverId, roomId)
            else languageManager.getMessage("velocity.remote_join_not_ready", serverId, roomId)
        ))
        openRoomsMenu(player)
    }

    private fun proxySpectateRoom(player: Player, serverId: String, roomId: String) {
        if (serverId == velocityBridgeService.serverId) {
            spectateRoom(player, roomId)
            return
        }
        player.sendMessage(Component.text(languageManager.getMessage("velocity.remote_spectate_not_ready", serverId, roomId)))
        openRoomsMenu(player)
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

    private fun requireRoomOwner(player: Player, roomId: String): Boolean {
        val room = roomManager.getRoom(roomId)
        if (room == null) {
            player.sendMessage(Component.text(languageManager.getMessage("command.room_not_found", roomId)))
            return false
        }
        if (!roomManager.isOwner(room, player.uniqueId) && !player.hasPermission("kagamecenter.admin")) {
            player.sendMessage(Component.text(languageManager.getMessage("command.no_permission")))
            return false
        }
        return true
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

    private fun templateMenuConfig(
        menuId: String,
        player: Player? = null,
        room: GameRoom? = null,
        game: GameDefinition? = room?.definition,
        extraValues: Map<String, String> = emptyMap(),
        afterReplace: (YamlConfiguration) -> Unit = {}
    ): YamlConfiguration? {
        val config = templateService.load(menuId) ?: return null
        val context = player?.let {
            templateService.buildContext(
                pluginName = plugin.name,
                player = it,
                room = room,
                game = game
            )
        }.orEmpty()
        templateService.replacePlaceholders(config, context + commonMenuValues() + extraValues)
        afterReplace(config)
        return config
    }

    private fun commonMenuValues(): Map<String, String> {
        val roomRows = roomRows()
        return mapOf(
            "menu.main_title" to languageManager.getMessage("menu.main_title"),
            "menu.main_line_1" to languageManager.getMessage("menu.main_line_1"),
            "menu.main_line_2" to languageManager.getMessage("menu.main_line_2"),
            "menu.games_title" to languageManager.getMessage("menu.games_title"),
            "menu.games_line" to languageManager.getMessage("menu.games_line", roomManager.listDefinitions().size),
            "menu.create_room_game_title" to languageManager.getMessage("menu.create_room_game_title"),
            "menu.create_room_game_line" to languageManager.getMessage("menu.create_room_game_line", roomManager.listDefinitions().count { it.enabled }),
            "menu.create_room_map_title" to languageManager.getMessage("menu.create_room_map_title", "{game.name}"),
            "menu.rooms_title" to languageManager.getMessage("menu.rooms_title"),
            "menu.rooms_line" to if (roomRows.isEmpty()) {
                languageManager.getMessage("room.status_empty")
            } else {
                languageManager.getMessage("menu.rooms_line", roomRows.size)
            },
            "menu.button_create_room" to languageManager.getMessage("menu.button_create_room"),
            "menu.button_room_detail" to languageManager.getMessage("menu.button_room_detail"),
            "menu.button_join_room_list" to languageManager.getMessage("menu.button_join_room_list"),
            "menu.button_leave" to languageManager.getMessage("menu.button_leave"),
            "menu.button_back" to languageManager.getMessage("menu.button_back"),
            "menu.button_close" to languageManager.getMessage("menu.button_close"),
            "menu.button_refresh" to languageManager.getMessage("menu.button_refresh"),
            "menu.button_join_short" to languageManager.getMessage("menu.button_join_short"),
            "menu.button_spectate_short" to languageManager.getMessage("menu.button_spectate_short"),
            "menu.input_room_name" to languageManager.getMessage("menu.input_room_name"),
            "menu.default_room_name" to languageManager.getMessage("menu.default_room_name"),
            "room.status_empty" to languageManager.getMessage("room.status_empty"),
            "command.no_games" to languageManager.getMessage("command.no_games")
        )
    }

    private fun removeButton(config: YamlConfiguration, key: String) {
        config.set("Bottom.buttons.$key", null)
    }

    private fun mainMenuConfig(player: Player): YamlConfiguration {
        templateMenuConfig("main", player) {
            if (roomManager.getPlayerRoom(player) == null) {
                removeButton(it, "leave")
            }
        }?.let { return it }

        return menu(languageManager.getMessage("menu.main_title")).apply {
            message("intro", listOf(
                languageManager.getMessage("menu.main_line_1"),
                languageManager.getMessage("menu.main_line_2")
            ))
            multi(columns = 2)
            button("create", languageManager.getMessage("menu.button_create_room"), "kgc:open-create-game")
            button("rooms", languageManager.getMessage("menu.button_join_room_list"), "kgc:open-rooms")
            if (roomManager.getPlayerRoom(player) != null) {
                button("leave", languageManager.getMessage("menu.button_leave"), "kgc:leave")
            }
            exit("close")
        }
    }

    private fun gamesMenuConfig(): YamlConfiguration {
        templateMenuConfig("games") { renderDynamicButtons(it) }?.let { return it }

        return menu(languageManager.getMessage("menu.games_title")).apply {
            val games = roomManager.listDefinitions().toList()
            message("intro", if (games.isEmpty()) {
                languageManager.getMessage("command.no_games")
            } else {
                languageManager.getMessage("menu.games_line", games.size)
            })
            multi(columns = 1)
            games.forEachIndexed { index, game ->
                val requiredPlugins = game.requiredPlugins.joinToString(", ").ifBlank { "-" }
                val optionalPlugins = game.optionalPlugins.joinToString(", ").ifBlank { "-" }
                button(
                    "game_$index",
                    languageManager.getMessage("menu.game_button", game.displayName),
                    "kgc:open-create-map ${game.id}",
                    listOf(
                        languageManager.getMessage("menu.game_tooltip_select_map"),
                        languageManager.getMessage("menu.game_tooltip_players", game.minPlayers, game.maxPlayers),
                        languageManager.getMessage("menu.game_tooltip_duration", game.defaultDurationSeconds),
                        languageManager.getMessage("menu.game_tooltip_required_plugins", requiredPlugins),
                        languageManager.getMessage("menu.game_tooltip_optional_plugins", optionalPlugins),
                        game.description.ifBlank { game.id }
                    )
                )
            }
            button("refresh", languageManager.getMessage("menu.button_refresh"), "kgc:open-games")
            exit("kgc:open-main", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun createGameMenuConfig(): YamlConfiguration {
        return menu(languageManager.getMessage("menu.create_room_game_title")).apply {
            val games = managedGameCatalog.enabled().toList()
            set("Inputs.room_name.type", "input")
            set("Inputs.room_name.text", languageManager.getMessage("menu.input_room_name"))
            set("Inputs.room_name.default", languageManager.getMessage("menu.default_room_name"))
            set("Inputs.room_name.max_length", 16)
            message("intro", if (games.isEmpty()) {
                languageManager.getMessage("command.no_games")
            } else {
                languageManager.getMessage("menu.create_room_game_line", games.size)
            })
            multi(columns = 1)
            games.forEachIndexed { index, game ->
                button(
                    "managed_game_$index",
                    "§a[ ${game.displayName} ]",
                    "kgc:create-room-config ${game.globalId}",
                    listOf(
                        "§7模块: §f${moduleDisplayName(game.moduleId)}",
                        "§7地图: §f${game.effectiveMapTemplate()}",
                        "§7人数: §f${game.minPlayers ?: "-"}-${game.maxPlayers ?: "-"}",
                        game.description.ifBlank { game.globalId }
                    )
                )
            }
            button("refresh", languageManager.getMessage("menu.button_refresh"), "kgc:open-create-game")
            exit("kgc:open-main", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun createMapSelectMenuConfig(gameId: String): YamlConfiguration {
        val game = roomManager.listDefinitions().firstOrNull { it.id == gameId }
        templateMenuConfig(
            "create_map",
            game = game,
            extraValues = mapOf(
                "game.id" to gameId,
                "game.name" to (game?.displayName ?: gameId),
                "menu.create_room_map_title" to languageManager.getMessage("menu.create_room_map_title", game?.displayName ?: gameId),
                "menu.create_room_map_line" to languageManager.getMessage("menu.create_room_map_line", mapManager.listMaps(gameId).size),
                "menu.create_room_no_maps" to languageManager.getMessage("menu.create_room_no_maps", gameId)
            )
        ) {
            renderDynamicButtons(it, gameId = gameId)
        }?.let { return it }

        return menu(languageManager.getMessage("menu.create_room_map_title", game?.displayName ?: gameId)).apply {
            val maps = mapManager.listMaps(gameId)
            set("Inputs.room_name.type", "input")
            set("Inputs.room_name.text", languageManager.getMessage("menu.input_room_name"))
            set("Inputs.room_name.default", languageManager.getMessage("menu.default_room_name"))
            set("Inputs.room_name.max_length", 16)
            message("intro", if (maps.isEmpty()) {
                languageManager.getMessage("menu.create_room_no_maps", gameId)
            } else {
                languageManager.getMessage("menu.create_room_map_line", maps.size)
            })
            multi(columns = 1)
            maps.forEachIndexed { index, map ->
                button(
                    "map_$index",
                    languageManager.getMessage("menu.create_room_map_button", map.mapId),
                    "kgc:create-room ${map.gameId} ${map.relativePath}",
                    listOf(
                        languageManager.getMessage("menu.map_detail_path", map.relativePath),
                        if (map.active) languageManager.getMessage("map.active") else languageManager.getMessage("map.inactive")
                    )
                )
            }
            if (maps.isEmpty()) {
                button("back_no_maps", languageManager.getMessage("menu.button_back"), "kgc:open-create-game")
            }
            button("refresh", languageManager.getMessage("menu.button_refresh"), "kgc:open-create-map $gameId")
            exit("kgc:open-create-game", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun roomsMenuConfig(): YamlConfiguration {
        templateMenuConfig("rooms") { renderDynamicButtons(it) }?.let { return it }

        return menu(languageManager.getMessage("menu.rooms_title")).apply {
            val rooms = roomRows()
            message("intro", if (rooms.isEmpty()) {
                languageManager.getMessage("room.status_empty")
            } else {
                languageManager.getMessage("menu.rooms_line", rooms.size)
            })
            multi(columns = 3)
            button("header_detail", languageManager.getMessage("menu.button_room_detail"), "kgc:open-rooms", width = ROOM_INFO_BUTTON_WIDTH)
            button("create", languageManager.getMessage("menu.button_create_room"), "kgc:open-create-game", width = ROOM_ACTION_BUTTON_WIDTH)
            button("top_refresh", languageManager.getMessage("menu.button_refresh"), "kgc:open-rooms", width = ROOM_ACTION_BUTTON_WIDTH)
            rooms.forEachIndexed { index, room ->
                button(
                    "room_${index}_info",
                    languageManager.getMessage(
                        "menu.room_button",
                        room.roomId,
                        room.roomName,
                        room.gameName,
                        room.mapId.substringAfterLast('/'),
                        room.players,
                        room.maxPlayers,
                        languageManager.getStateName(room.state)
                    ),
                    room.infoAction,
                    listOf(
                        languageManager.getMessage("velocity.room_server", room.serverId),
                        languageManager.getMessage("display.sidebar_game", room.gameName),
                        languageManager.getMessage("display.sidebar_state", languageManager.getStateName(room.state))
                    ),
                    width = ROOM_INFO_BUTTON_WIDTH
                )
                button("room_${index}_join", languageManager.getMessage("menu.button_join_short"), room.joinAction, width = ROOM_ACTION_BUTTON_WIDTH)
                button("room_${index}_spectate", languageManager.getMessage("menu.button_spectate_short"), room.spectateAction, width = ROOM_ACTION_BUTTON_WIDTH)
            }
            exit("kgc:open-main", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun roomMenuConfig(room: GameRoom, viewer: Player): YamlConfiguration {
        templateRoomMenuConfig(room, viewer)?.let { return it }

        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        return menu(languageManager.getMessage("menu.room_title", room.id)).apply {
            message("intro", listOf(
                languageManager.getMessage("display.sidebar_game", room.definition?.displayName ?: room.module.displayName),
                languageManager.getMessage("display.sidebar_state", languageManager.getStateName(room.state)),
                languageManager.getMessage("display.sidebar_players", room.players.size, maxPlayers),
                languageManager.getMessage("display.sidebar_world", room.world?.name ?: "-"),
                languageManager.getMessage("room.owner_line", playerName(room.owner ?: room.players.firstOrNull() ?: UUID(0, 0)))
            ))
            val teams = teamService.getTeams(room.id).take(MAX_RENDER_TEAMS)
            multi(columns = if (teams.isEmpty()) SOLO_MEMBER_COLUMNS else teams.size.coerceAtLeast(1))
            button("join", languageManager.getMessage("menu.button_join_room"), "kgc:join ${room.id}")
            button("start", languageManager.getMessage("menu.button_start_room"), "kgc:start ${room.id}")
            button("leave", languageManager.getMessage("menu.button_leave"), "kgc:leave")
            button("close_room", languageManager.getMessage("menu.button_close_room"), "kgc:close-room ${room.id}")
            button("refresh", languageManager.getMessage("menu.button_refresh"), "kgc:open-room ${room.id}")
            renderRoomMembers(room, teams)
            exit("kgc:open-rooms", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun memberMenuConfig(room: GameRoom, targetId: UUID): YamlConfiguration {
        templateMemberMenuConfig(room, targetId)?.let { return it }

        val owner = room.owner
        val isOwner = owner == targetId
        return menu(languageManager.getMessage("menu.member_title", playerName(targetId))).apply {
            message("intro", listOf(
                languageManager.getMessage("menu.member_tooltip", targetId),
                languageManager.getMessage("menu.member_state_owner", if (isOwner) languageManager.getMessage("menu.member_yes") else languageManager.getMessage("menu.member_no"))
            ))
            multi(columns = 2)
            if (!isOwner) {
                button("transfer_owner", languageManager.getMessage("menu.button_transfer_owner"), "kgc:transfer-owner ${room.id} $targetId")
            }
            button("kick", languageManager.getMessage("menu.button_kick_player"), "kgc:kick-player ${room.id} $targetId")
            exit("kgc:open-room ${room.id}", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun templateRoomMenuConfig(room: GameRoom, viewer: Player): YamlConfiguration? {
        val config = templateService.load("room") ?: return null
        val values = templateService.buildContext(
            pluginName = plugin.name,
            player = viewer,
            room = room,
            game = room.definition
        ) + mapOf(
            "menu.room_title" to languageManager.getMessage("menu.room_title", room.id),
            "menu.button_join_room" to languageManager.getMessage("menu.button_join_room"),
            "menu.button_start_room" to languageManager.getMessage("menu.button_start_room"),
            "menu.button_leave" to languageManager.getMessage("menu.button_leave"),
            "menu.button_close_room" to languageManager.getMessage("menu.button_close_room"),
            "menu.button_refresh" to languageManager.getMessage("menu.button_refresh"),
            "menu.button_back" to languageManager.getMessage("menu.button_back"),
            "menu.member_button" to languageManager.getMessage("menu.member_button", "{player.name}"),
            "menu.member_empty" to languageManager.getMessage("menu.member_empty"),
            "menu.member_tooltip" to languageManager.getMessage("menu.member_tooltip", "{player.uuid}"),
            "room.game_line" to languageManager.getMessage("display.sidebar_game", room.definition?.displayName ?: room.module.displayName),
            "room.state_line" to languageManager.getMessage("display.sidebar_state", languageManager.getStateName(room.state)),
            "room.players_line" to languageManager.getMessage("display.sidebar_players", room.players.size, room.definition?.maxPlayers ?: room.module.maxPlayers),
            "room.world_line" to languageManager.getMessage("display.sidebar_world", room.world?.name ?: "-"),
            "room.owner_line" to languageManager.getMessage("room.owner_line", playerName(room.owner ?: room.players.firstOrNull() ?: UUID(0, 0)))
        )
        templateService.replacePlaceholders(config, values)
        renderDynamicButtons(config, room)
        return config
    }

    private fun templateMemberMenuConfig(room: GameRoom, targetId: UUID): YamlConfiguration? {
        val targetName = playerName(targetId)
        val isOwner = room.owner == targetId
        val config = templateService.load("room_member") ?: return null
        templateService.replacePlaceholders(
            config,
            templateService.buildContext(
                pluginName = plugin.name,
                player = Bukkit.getPlayer(targetId) ?: return config,
                room = room,
                game = room.definition,
                target = Bukkit.getPlayer(targetId),
                teamMemberCount = teamService.getTeam(room.id, targetId)?.let { team ->
                    teamService.getMembers(room.id, team.id).size
                }
            ) + mapOf(
                "menu.member_title" to languageManager.getMessage("menu.member_title", targetName),
                "menu.member_tooltip" to languageManager.getMessage("menu.member_tooltip", targetId),
                "menu.member_state_owner" to languageManager.getMessage(
                    "menu.member_state_owner",
                    if (isOwner) languageManager.getMessage("menu.member_yes") else languageManager.getMessage("menu.member_no")
                ),
                "menu.button_transfer_owner" to languageManager.getMessage("menu.button_transfer_owner"),
                "menu.button_kick_player" to languageManager.getMessage("menu.button_kick_player"),
                "menu.button_back" to languageManager.getMessage("menu.button_back"),
                "room.id" to room.id,
                "player.uuid" to targetId.toString()
            )
        )
        return config
    }

    private fun renderDynamicButtons(
        config: YamlConfiguration,
        room: GameRoom? = null,
        gameId: String? = null
    ) {
        val buttons = config.getConfigurationSection("Bottom.buttons") ?: return
        buttons.getKeys(false).forEach { key ->
            val section = buttons.getConfigurationSection(key) ?: return@forEach
            when (section.getString("TYPE")?.lowercase()) {
                "player_slots" -> if (room != null) renderPlayerSlots(config, "Bottom.buttons", key, section, room)
                "game_list" -> renderGameList(config, "Bottom.buttons", key, section)
                "managed_game_list" -> renderManagedGameList(config, "Bottom.buttons", key, section)
                "admin_managed_game_rows" -> renderAdminManagedGameRows(config, "Bottom.buttons", key, section)
                "map_list" -> renderMapList(config, "Bottom.buttons", key, section, gameId ?: section.getString("game-id"))
                "room_rows" -> renderRoomRows(config, "Bottom.buttons", key, section)
            }
        }
    }

    private fun renderGameList(
        config: YamlConfiguration,
        parentPath: String,
        key: String,
        template: org.bukkit.configuration.ConfigurationSection
    ) {
        config.set("$parentPath.$key", null)
        val games = roomManager.listDefinitions()
            .filter { template.getBoolean("include-disabled", false) || it.enabled }
            .toList()
        if (games.isEmpty()) {
            config.set("$parentPath.${key}_empty.text", template.getString("empty-text", languageManager.getMessage("command.no_games")))
            config.set("$parentPath.${key}_empty.actions", template.getStringList("empty-actions").ifEmpty { listOf("kgc:open-main") })
            return
        }

        games.forEachIndexed { index, game ->
            copyButtonTemplate(config, "$parentPath.${key}_$index", template, gameValues(game, index))
        }
    }

    private fun renderMapList(
        config: YamlConfiguration,
        parentPath: String,
        key: String,
        template: org.bukkit.configuration.ConfigurationSection,
        gameId: String?
    ) {
        config.set("$parentPath.$key", null)
        val targetGameId = gameId?.takeIf { it.isNotBlank() && it != "-" } ?: return
        val maps = mapManager.listMaps(targetGameId)
        if (maps.isEmpty()) {
            config.set("$parentPath.${key}_empty.text", template.getString("empty-text", languageManager.getMessage("menu.create_room_no_maps", targetGameId)))
            config.set("$parentPath.${key}_empty.actions", template.getStringList("empty-actions").ifEmpty { listOf("kgc:open-create-game") })
            return
        }

        maps.forEachIndexed { index, map ->
            copyButtonTemplate(config, "$parentPath.${key}_$index", template, mapValues(map, index))
        }
    }

    private fun renderManagedGameList(
        config: YamlConfiguration,
        parentPath: String,
        key: String,
        template: org.bukkit.configuration.ConfigurationSection
    ) {
        config.set("$parentPath.$key", null)
        val games = managedGameCatalog.enabled().toList()
        if (games.isEmpty()) {
            config.set("$parentPath.${key}_empty.text", template.getString("empty-text", languageManager.getMessage("command.no_games")))
            config.set("$parentPath.${key}_empty.actions", template.getStringList("empty-actions").ifEmpty { listOf("kgc:open-main") })
            return
        }

        games.forEachIndexed { index, game ->
            copyButtonTemplate(config, "$parentPath.${key}_$index", template, managedGameValues(game, index))
        }
    }

    private fun renderAdminManagedGameRows(
        config: YamlConfiguration,
        parentPath: String,
        key: String,
        template: org.bukkit.configuration.ConfigurationSection
    ) {
        config.set("$parentPath.$key", null)
        val games = managedGameCatalog.all().toList()
        if (games.isEmpty()) {
            config.set("$parentPath.${key}_empty.text", template.getString("empty-text", languageManager.getMessage("command.no_games")))
            config.set("$parentPath.${key}_empty.actions", template.getStringList("empty-actions").ifEmpty { listOf("kgc:open-admin-manage") })
            return
        }

        games.forEachIndexed { index, game ->
            val values = managedGameValues(game, index)
            copyNamedButtonTemplate(config, "$parentPath.${key}_${index}_info", template, "info", values)
            copyNamedButtonTemplate(config, "$parentPath.${key}_${index}_edit", template, "edit", values)
        }
    }

    private fun renderRoomRows(
        config: YamlConfiguration,
        parentPath: String,
        key: String,
        template: org.bukkit.configuration.ConfigurationSection
    ) {
        config.set("$parentPath.$key", null)
        val rooms = roomRows()
        if (rooms.isEmpty()) {
            return
        }

        rooms.forEachIndexed { index, room ->
            val values = roomValues(room, index)
            copyNamedButtonTemplate(config, "$parentPath.${key}_${index}_info", template, "info", values)
            copyNamedButtonTemplate(config, "$parentPath.${key}_${index}_join", template, "join", values)
            if (template.contains("refresh-text") || template.contains("refresh-actions")) {
                copyNamedButtonTemplate(config, "$parentPath.${key}_${index}_refresh", template, "refresh", values)
            } else {
                copyNamedButtonTemplate(config, "$parentPath.${key}_${index}_spectate", template, "spectate", values)
            }
        }
    }

    private fun renderPlayerSlots(
        config: YamlConfiguration,
        parentPath: String,
        key: String,
        template: org.bukkit.configuration.ConfigurationSection,
        room: GameRoom
    ) {
        config.set("$parentPath.$key", null)
        val players = room.players.toList()
        if (players.isEmpty()) {
            config.set("$parentPath.${key}_empty.text", template.getString("empty-text", languageManager.getMessage("menu.member_empty")))
            config.set("$parentPath.${key}_empty.actions", listOf("kgc:open-room ${room.id}"))
            return
        }

        players.forEachIndexed { index, playerId ->
            val team = teamService.getTeam(room.id, playerId)
            val playerValues = mapOf(
                "room.id" to room.id,
                "player.uuid" to playerId.toString(),
                "player.name" to playerName(playerId),
                "player.index" to index.toString(),
                "player.number" to (index + 1).toString(),
                "player.online" to (Bukkit.getPlayer(playerId) != null).toString(),
                "player.is_owner" to (room.owner == playerId).toString(),
                "player.team_id" to (team?.id ?: "-"),
                "player.team_name" to (team?.displayName ?: "-")
            )
            copyButtonTemplate(config, "$parentPath.${key}_$index", template, playerValues)
        }
    }

    private fun copyNamedButtonTemplate(
        config: YamlConfiguration,
        buttonPath: String,
        template: org.bukkit.configuration.ConfigurationSection,
        prefix: String,
        values: Map<String, String>
    ) {
        config.set("$buttonPath.text", replaceDynamic(template.getString("$prefix-text", "[$prefix]") ?: "[$prefix]", values))
        val actions = template.getStringList("$prefix-actions").map { replaceDynamic(it, values) }
        if (actions.isNotEmpty()) config.set("$buttonPath.actions", actions)
        val tooltip = template.getStringList("$prefix-tooltip").map { replaceDynamic(it, values) }
        if (tooltip.isNotEmpty()) config.set("$buttonPath.tooltip", tooltip)
        val width = template.getInt("$prefix-width", 0)
        if (width > 0) config.set("$buttonPath.width", width)
    }

    private fun copyButtonTemplate(
        config: YamlConfiguration,
        buttonPath: String,
        template: org.bukkit.configuration.ConfigurationSection,
        values: Map<String, String>
    ) {
        config.set("$buttonPath.text", replaceDynamic(template.getString("text", "") ?: "", values))
        val actions = template.getStringList("actions").map { replaceDynamic(it, values) }
        if (actions.isNotEmpty()) config.set("$buttonPath.actions", actions)
        val tooltip = template.getStringList("tooltip").map { replaceDynamic(it, values) }
        if (tooltip.isNotEmpty()) config.set("$buttonPath.tooltip", tooltip)
        val width = template.getInt("width", 0)
        if (width > 0) config.set("$buttonPath.width", width)
    }

    private fun gameValues(game: GameDefinition, index: Int): Map<String, String> {
        return mapOf(
            "game.index" to index.toString(),
            "game.number" to (index + 1).toString(),
            "game.id" to game.id,
            "game.name" to game.displayName,
            "game.enabled" to game.enabled.toString(),
            "game.min_players" to game.minPlayers.toString(),
            "game.max_players" to game.maxPlayers.toString(),
            "game.duration" to game.defaultDurationSeconds.toString(),
            "game.description" to game.description,
            "game.required_plugins" to game.requiredPlugins.joinToString(", ").ifBlank { "-" },
            "game.optional_plugins" to game.optionalPlugins.joinToString(", ").ifBlank { "-" }
        )
    }

    private fun mapValues(map: GameMapInfo, index: Int): Map<String, String> {
        return mapOf(
            "map.index" to index.toString(),
            "map.number" to (index + 1).toString(),
            "map.game_id" to map.gameId,
            "map.id" to map.mapId,
            "map.relative_path" to map.relativePath,
            "map.active" to map.active.toString(),
            "map.active_label" to if (map.active) languageManager.getMessage("map.active") else languageManager.getMessage("map.inactive"),
            "map.folder" to map.folder.absolutePath
        )
    }

    private fun roomValues(row: RoomMenuRow, index: Int): Map<String, String> {
        return mapOf(
            "room.index" to index.toString(),
            "room.number" to (index + 1).toString(),
            "room.server_id" to row.serverId,
            "room.global_id" to "${row.serverId}:${row.roomId}",
            "room.id" to row.roomId,
            "room.name" to row.roomName,
            "room.game_id" to row.gameId,
            "room.game_name" to row.gameName,
            "room.map_template" to row.mapId,
            "room.map_id" to row.mapId.substringAfterLast('/'),
            "room.configured_game_id" to row.gameId,
            "room.players" to row.players.toString(),
            "room.player_count" to row.players.toString(),
            "room.max_players" to row.maxPlayers.toString(),
            "room.spectators" to row.spectators.toString(),
            "room.state" to languageManager.getStateName(row.state),
            "room.world" to "-",
            "room.joinable" to row.joinable.toString(),
            "room.info_action" to row.infoAction,
            "room.join_action" to row.joinAction,
            "room.spectate_action" to row.spectateAction
        )
    }

    private fun roomValues(room: GameRoom, index: Int): Map<String, String> {
        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        return mapOf(
            "room.index" to index.toString(),
            "room.number" to (index + 1).toString(),
            "room.id" to room.id,
            "room.name" to room.name,
            "room.game_id" to (room.definition?.id ?: room.module.id),
            "room.game_name" to (room.definition?.displayName ?: room.module.displayName),
            "room.map_template" to (room.mapTemplate ?: "-"),
            "room.map_id" to (room.mapTemplate?.substringAfterLast('/') ?: "-"),
            "room.configured_game_id" to (room.configuredGame?.globalId ?: "-"),
            "room.players" to room.players.size.toString(),
            "room.player_count" to room.players.size.toString(),
            "room.max_players" to maxPlayers.toString(),
            "room.spectators" to room.spectators.size.toString(),
            "room.state" to languageManager.getStateName(room.state),
            "room.world" to (room.world?.name ?: "-"),
            "room.joinable" to room.canJoin().toString(),
            "room.info_action" to "kgc:open-room ${room.id}",
            "room.join_action" to "kgc:join ${room.id}",
            "room.spectate_action" to "kgc:spectate ${room.id}"
        )
    }

    private fun roomRows(): List<RoomMenuRow> {
        val globalRooms = if (velocityBridgeService.enabled) velocityBridgeService.globalRooms().toList() else emptyList()
        if (globalRooms.isNotEmpty()) {
            return globalRooms.map { it.toRoomMenuRow(velocityBridgeService.serverId) }
        }
        return roomManager.listRooms().map { it.toRoomMenuRow(velocityBridgeService.serverId) }
    }

    private fun VelocityRoomSnapshot.toRoomMenuRow(localServerId: String): RoomMenuRow {
        val local = serverId == localServerId
        return RoomMenuRow(
            serverId = serverId,
            roomId = roomId,
            roomName = roomName,
            gameId = gameId,
            gameName = gameName,
            mapId = mapId,
            state = state,
            players = players,
            maxPlayers = maxPlayers,
            spectators = spectators,
            joinable = joinable,
            infoAction = if (local) "kgc:open-room $roomId" else "kgc:proxy-join $serverId $roomId",
            joinAction = if (local) "kgc:join $roomId" else "kgc:proxy-join $serverId $roomId",
            spectateAction = if (local) "kgc:spectate $roomId" else "kgc:proxy-spectate $serverId $roomId"
        )
    }

    private fun GameRoom.toRoomMenuRow(localServerId: String): RoomMenuRow {
        val maxPlayers = definition?.maxPlayers ?: module.maxPlayers
        return RoomMenuRow(
            serverId = localServerId,
            roomId = id,
            roomName = name,
            gameId = definition?.id ?: module.id,
            gameName = module.displayName,
            mapId = configuredGame?.displayName ?: mapTemplate?.substringAfterLast('/') ?: "-",
            state = state.name,
            players = players.size,
            maxPlayers = maxPlayers,
            spectators = spectators.size,
            joinable = canJoin(),
            infoAction = "kgc:open-room $id",
            joinAction = "kgc:join $id",
            spectateAction = "kgc:spectate $id"
        )
    }

    private data class RoomMenuRow(
        val serverId: String,
        val roomId: String,
        val roomName: String,
        val gameId: String,
        val gameName: String,
        val mapId: String,
        val state: String,
        val players: Int,
        val maxPlayers: Int,
        val spectators: Int,
        val joinable: Boolean,
        val infoAction: String,
        val joinAction: String,
        val spectateAction: String
    )

    private fun managedGameValues(game: ManagedGameConfig, index: Int): Map<String, String> {
        return mapOf(
            "game.index" to index.toString(),
            "game.number" to (index + 1).toString(),
            "game.id" to game.globalId,
            "game.local_id" to game.localId,
            "game.module_id" to game.moduleId,
            "game.module_name" to moduleDisplayName(game.moduleId),
            "game.name" to game.displayName,
            "game.enabled" to game.enabled.toString(),
            "game.map_template" to game.effectiveMapTemplate(),
            "game.map_id" to game.effectiveMapTemplate().substringAfterLast('/'),
            "game.min_players" to (game.minPlayers?.toString() ?: "-"),
            "game.max_players" to (game.maxPlayers?.toString() ?: "-"),
            "game.description" to game.description.ifBlank { "-" },
            "game.has_private_map" to game.hasPrivateSnapshot().toString()
        )
    }

    private fun replaceDynamic(text: String, values: Map<String, String>): String {
        var output = text
        values.forEach { (key, value) ->
            output = output.replace("{$key}", value)
        }
        return output
    }

    private fun YamlConfiguration.renderRoomMembers(room: GameRoom, teams: List<GameTeam>) {
        if (teams.isEmpty()) {
            renderSoloMembers(room)
        } else {
            renderTeamMembers(room, teams)
        }
    }

    private fun YamlConfiguration.renderSoloMembers(room: GameRoom) {
        val players = room.players.toList()
        if (players.isEmpty()) {
            button("member_empty", languageManager.getMessage("menu.member_empty"), "kgc:open-room ${room.id}")
            return
        }

        players.forEachIndexed { index, playerId ->
            button(
                "member_$index",
                languageManager.getMessage("menu.member_button", playerName(playerId)),
                "kgc:open-room ${room.id}",
                listOf(languageManager.getMessage("menu.member_tooltip", playerId))
            )
        }
    }

    private fun YamlConfiguration.renderTeamMembers(room: GameRoom, teams: List<GameTeam>) {
        teams.forEachIndexed { index, team ->
            button(
                "team_header_$index",
                languageManager.getMessage("menu.team_header", team.displayName),
                "kgc:join-team ${room.id} ${team.id}",
                listOf(languageManager.getMessage("menu.team_tooltip", team.id, team.maxPlayers))
            )
        }

        for (row in 0 until TEAM_MEMBER_SLOTS) {
            teams.forEachIndexed { teamIndex, team ->
                val member = teamService.getMembers(room.id, team.id).toList().getOrNull(row)
                if (member == null) {
                    button("team_${teamIndex}_empty_$row", languageManager.getMessage("menu.team_empty_slot"), "kgc:open-room ${room.id}")
                } else {
                    button(
                        "team_${teamIndex}_member_$row",
                        languageManager.getMessage("menu.member_button", playerName(member)),
                        "kgc:open-member ${room.id} $member",
                        listOf(languageManager.getMessage("menu.member_tooltip", member))
                    )
                }
            }
        }

        val ungrouped = teamService.getUngroupedPlayers(room.id, room.players)
        if (ungrouped.isNotEmpty()) {
            ungrouped.forEachIndexed { index, playerId ->
                button(
                    "team_ungrouped_$index",
                    languageManager.getMessage("menu.member_ungrouped", playerName(playerId)),
                    "kgc:open-member ${room.id} $playerId",
                    listOf(languageManager.getMessage("menu.member_tooltip", playerId))
                )
            }
        }
    }

    private fun playerName(playerId: UUID): String {
        return Bukkit.getPlayer(playerId)?.name ?: Bukkit.getOfflinePlayer(playerId).name ?: playerId.toString().take(8)
    }

    private fun adminManageMenuConfig(): YamlConfiguration {
        val modules = roomManager.listModules().sortedBy { it.id }
        val maps = mapManager.listGames().flatMap { gameId -> mapManager.listMaps(gameId) }
        return menu(languageManager.getMessage("managed_game.menu_title_create")).apply {
            set("Settings.after_action", "WAIT_FOR_RESPONSE")
            message("intro", listOf(
                languageManager.getMessage("managed_game.menu_line_1"),
                languageManager.getMessage("managed_game.menu_line_2")
            ))
            set("Inputs.module_id.type", "dropdown")
            set("Inputs.module_id.text", languageManager.getMessage("managed_game.input_module"))
            set("Inputs.module_id.options", modules.map { "${it.id} => §a${it.displayName} §8(${it.id})" })
            set("Inputs.module_id.default_id", modules.firstOrNull()?.id ?: "")
            set("Inputs.map_template.type", "dropdown")
            set("Inputs.map_template.text", languageManager.getMessage("managed_game.input_map"))
            set("Inputs.map_template.options", maps.map { "${it.relativePath} => §d${it.gameId}§7/§f${it.mapId}" })
            set("Inputs.map_template.default_id", maps.firstOrNull()?.relativePath ?: "")
            set("Inputs.display_name.type", "input")
            set("Inputs.display_name.text", languageManager.getMessage("managed_game.input_name"))
            set("Inputs.display_name.default", languageManager.getMessage("managed_game.default_name"))
            set("Inputs.display_name.max_length", 32)
            multi(columns = 2)
            button("create", languageManager.getMessage("managed_game.button_create"), "kgc:admin-create-managed-game")
            button("list", languageManager.getMessage("managed_game.button_browse"), "kgc:open-admin-managed-games")
            exit("kgc:open-main", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun adminManagedGamesConfig(): YamlConfiguration {
        return menu(languageManager.getMessage("managed_game.menu_title_list")).apply {
            val games = managedGameCatalog.all().toList()
            message("intro", if (games.isEmpty()) {
                languageManager.getMessage("managed_game.none")
            } else {
                languageManager.getMessage("managed_game.list_line", games.size)
            })
            multi(columns = 2)
            games.forEachIndexed { index, game ->
                button(
                    "game_${index}_info",
                    "§8[§e${game.moduleId}§8] §f${game.displayName} §8[§d${game.effectiveMapTemplate().substringAfterLast('/')}§8]",
                    "kgc:open-admin-game-editor ${game.globalId}",
                    listOf(
                        languageManager.getMessage("managed_game.tooltip_id", game.globalId),
                        languageManager.getMessage("managed_game.tooltip_shared_map", game.sharedMapTemplate),
                        languageManager.getMessage("managed_game.tooltip_runtime_map", game.effectiveMapTemplate()),
                        languageManager.getMessage("managed_game.tooltip_private_map", languageManager.getMessage(if (game.hasPrivateSnapshot()) "managed_game.private_ready" else "managed_game.private_missing"))
                    ),
                    width = 250
                )
                button(
                    "game_${index}_edit",
                    languageManager.getMessage("managed_game.button_edit"),
                    "kgc:open-admin-game-editor ${game.globalId}",
                    width = 70
                )
            }
            button("refresh", languageManager.getMessage("menu.button_refresh"), "kgc:open-admin-managed-games")
            exit("kgc:open-admin-manage", languageManager.getMessage("menu.button_back"))
        }
    }

    private fun createManagedGame(player: Player, variables: Map<String, String>) {
        val moduleId = variables["module_id"]?.trim().orEmpty()
        val mapTemplate = variables["map_template"]?.trim().orEmpty()
        val displayName = variables["display_name"]?.trim().orEmpty()
        if (moduleId.isBlank() || mapTemplate.isBlank() || displayName.isBlank()) {
            player.sendMessage(Component.text(languageManager.getMessage("managed_game.create_invalid")))
            openAdminManageMenu(player)
            return
        }
        if (!mapTemplate.startsWith("$moduleId/")) {
            player.sendMessage(Component.text(languageManager.getMessage("managed_game.create_map_mismatch")))
            openAdminManageMenu(player)
            return
        }
        val game = managedGameCatalog.createManagedGame(moduleId, mapTemplate, displayName)
        if (game == null) {
            player.sendMessage(Component.text(languageManager.getMessage("managed_game.create_failed")))
            openAdminManageMenu(player)
            return
        }
        player.sendMessage(Component.text(languageManager.getMessage("managed_game.created", game.displayName, game.globalId)))
        managedGameCatalog.openEditor(player, game.globalId)
    }

    private fun moduleDisplayName(moduleId: String): String {
        return roomManager.listModules().firstOrNull { it.id.equals(moduleId, ignoreCase = true) }?.displayName ?: moduleId
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
            button("refresh", languageManager.getMessage("menu.button_refresh"), "kgc:open-maps")
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
            button("refresh", languageManager.getMessage("menu.button_refresh"), "kgc:open-maps-game $gameId")
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
            button("refresh", languageManager.getMessage("menu.button_refresh"), "kgc:open-map $gameId $mapId")
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

    private fun YamlConfiguration.button(
        key: String,
        text: String,
        action: String,
        tooltip: List<String> = emptyList(),
        width: Int? = null
    ) {
        set("Bottom.buttons.$key.text", text)
        set("Bottom.buttons.$key.actions", listOf(action))
        if (width != null && width > 0) {
            set("Bottom.buttons.$key.width", width)
        }
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
        private const val SOLO_MEMBER_COLUMNS = 3
        private const val MAX_RENDER_TEAMS = 4
        private const val TEAM_MEMBER_SLOTS = 6
        private const val ROOM_INFO_BUTTON_WIDTH = 300
        private const val ROOM_ACTION_BUTTON_WIDTH = 54
    }
}
