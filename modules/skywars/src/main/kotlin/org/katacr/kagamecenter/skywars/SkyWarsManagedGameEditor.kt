package org.katacr.kagamecenter.skywars

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.editor.EditorPointCaptureService
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.game.ModuleGameEditor
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.map.ManagedMapPointService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.selection.SelectionService
import org.katacr.kaGameCenter.world.TemporaryWorldService

/** 提供 SkyWars 托管游戏的可视化地图编辑面板。 */
class SkyWarsManagedGameEditor(
    private val configService: SkyWarsConfigService,
    private val language: ModuleLanguage,
    private val selectionService: SelectionService,
    private val packetService: PacketDispatchService,
    private val worldService: TemporaryWorldService,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val menuService: GameCenterMenuService,
    private val mapPointService: ManagedMapPointService,
    private val pointCaptureService: EditorPointCaptureService
) : ModuleGameEditor {
    override val moduleId: String = "skywars"

    override fun populateDefaults(config: YamlConfiguration, localId: String, displayName: String, sharedMapTemplate: String) {
        val map = configService.findMapByTemplate(sharedMapTemplate) ?: configService.current().firstMap()
        config.set("skywars.map-id", map?.id ?: sharedMapTemplate.substringAfterLast('/'))
        config.set("skywars.team-size", configService.current().teamSize)
        config.set("skywars.next-island-index", 1)
        config.set("skywars.next-chest-index", 1)
        config.set("skywars.island-spawns", emptyList<Map<String, Any>>())
        config.set("skywars.chests", emptyList<Map<String, Any>>())
    }

    override fun openEditor(player: Player, game: ManagedGameConfig) {
        pointCaptureService.cancel(player)
        val configured = configService.readManagedGame(game)
        val menu = YamlConfiguration()
        menu.set("Title", language.getMessage("skywars.editor_title", game.displayName))
        menu.set("Settings.can_escape", true)
        menu.set("Settings.after_action", "WAIT_FOR_RESPONSE")
        menu.set("Body.summary.type", "message")
        menu.set("Body.summary.width", 400)
        menu.set("Body.summary.text", listOf(
            language.getMessage("skywars.editor_summary_name", game.displayName),
            language.getMessage("skywars.editor_summary_shared_map", game.sharedMapTemplate),
            language.getMessage("skywars.editor_summary_private_map", status(game.hasPrivateSnapshot())),
            language.getMessage("skywars.editor_summary_lobby", status(configured.lobby != null)),
            language.getMessage("skywars.editor_summary_spectator", status(configured.spectatorSpawn != null)),
            language.getMessage("skywars.editor_summary_play_region", status(configured.playRegion != null)),
            language.getMessage("skywars.editor_summary_islands", configured.islandSpawns.size),
            language.getMessage("skywars.editor_summary_chests", configured.chests.size),
            language.getMessage("skywars.editor_summary_team_size", configured.teamSize ?: configService.current().teamSize),
            language.getMessage("skywars.editor_summary_void_y", configured.voidY ?: configService.current().defaultVoidY)
        ))
        input(menu, "island_id", language.getMessage("skywars.editor_input_island_id"), nextId("island", configured.islandSpawns.map { it.id }))
        input(menu, "chest_id", language.getMessage("skywars.editor_input_chest_id"), nextId("chest", configured.chests.map { it.id }))
        input(menu, "chest_tier", language.getMessage("skywars.editor_input_chest_tier"), "island")
        input(menu, "team_size", language.getMessage("skywars.editor_input_team_size"), (configured.teamSize ?: configService.current().teamSize).toString())
        menu.set("Bottom.type", "multi")
        menu.set("Bottom.columns", 3)
        button(menu, "open_world", "skywars.editor_button_open_world", game, "open-world")
        button(menu, "save_world", "skywars.editor_button_save_world", game, "save-world")
        button(menu, "close_world", "skywars.editor_button_close_world", game, "close-world")
        button(menu, "set_lobby", "skywars.editor_button_set_lobby", game, "set-lobby")
        button(menu, "set_spectator", "skywars.editor_button_set_spectator", game, "set-spectator")
        button(menu, "set_region", "skywars.editor_button_set_play_region", game, "set-play-region")
        button(menu, "set_void", "skywars.editor_button_set_void_y", game, "set-void-y")
        button(menu, "set_team_size", "skywars.editor_button_set_team_size", game, "set-team-size")
        button(menu, "add_island", "skywars.editor_button_add_island", game, "add-island")
        button(menu, "remove_island", "skywars.editor_button_remove_island", game, "remove-island")
        button(menu, "add_chest", "skywars.editor_button_add_chest", game, "add-chest")
        button(menu, "remove_chest", "skywars.editor_button_remove_chest", game, "remove-chest")
        button(menu, "preview", "skywars.editor_button_preview", game, "preview")
        menu.set("Bottom.exit.text", language.getMessage("menu.button_back"))
        menu.set("Bottom.exit.actions", listOf("kgc:open-admin-managed-games"))
        menuService.openExternalConfig(player, menu, "kagamecenter:skywars-editor:${game.globalId}")
    }

    override fun handleAction(player: Player, game: ManagedGameConfig, action: String, variables: Map<String, String>): Boolean {
        when (action.lowercase()) {
            "open-world" -> openWorld(player, game)
            "save-world" -> {
                if (!mapEditorService.saveIfEditing(game.globalId)) return fail(player, "skywars.editor_save_failed")
                player.sendMessage(Component.text(language.getMessage("skywars.editor_saved")))
            }
            "close-world" -> {
                if (!mapEditorService.closeSession(game.globalId, save = true, restoreEditors = true)) return fail(player, "skywars.editor_close_failed")
                player.sendMessage(Component.text(language.getMessage("skywars.editor_closed")))
            }
            "set-lobby" -> {
                startPositionCapture(player, game, "skywars.editor_field_lobby") { currentGame, location ->
                    configService.saveManagedLobby(currentGame, mapPointService.fromLocation(location))
                }
                return true
            }
            "set-spectator" -> {
                startPositionCapture(player, game, "skywars.editor_field_spectator") { currentGame, location ->
                    configService.saveManagedSpectatorSpawn(currentGame, mapPointService.fromLocation(location))
                }
                return true
            }
            "set-play-region" -> {
                val selection = selectionService.getSelection(player) ?: return fail(player, "selection.not_ready")
                saveField(player, game, "skywars.editor_field_play_region") { configService.saveManagedPlayRegion(game, selection) }
            }
            "set-void-y" -> {
                startPositionCapture(player, game, "skywars.editor_field_void_y") { currentGame, location ->
                    configService.saveManagedVoidY(currentGame, location.y)
                }
                return true
            }
            "set-team-size" -> {
                val size = variable(variables, "team_size")?.toIntOrNull()?.coerceIn(1, 8)
                    ?: return fail(player, "skywars.admin_team_size_invalid")
                saveField(player, game, "skywars.editor_field_team_size") { configService.saveManagedTeamSize(game, size) }
            }
            "add-island" -> {
                startIslandCapture(player, game)
                return true
            }
            "remove-island" -> removePoint(player, game, variable(variables, "island_id"), island = true)
            "add-chest" -> {
                val tier = variable(variables, "chest_tier")?.takeIf(configService.current().loot.tiers::containsKey) ?: "island"
                startChestCapture(player, game, tier)
                return true
            }
            "remove-chest" -> removePoint(player, game, variable(variables, "chest_id"), island = false)
            "preview" -> preview(player, game)
            else -> return false
        }
        val latest = managedGameCatalog.get(game.globalId) ?: game
        openEditor(player, latest)
        return true
    }

    private fun openWorld(player: Player, game: ManagedGameConfig) {
        if (!ensurePrivateSnapshot(game)) return failUnit(player, "skywars.editor_private_snapshot_failed")
        val configured = configService.readManagedGame(game)
        val world = mapEditorService.openEditorDirectory(player, game.globalId, game.runtimeMapFolder) { editWorld ->
            configured.lobby?.toLocation(editWorld)
                ?: configured.islandSpawns.firstOrNull()?.point?.toLocation(editWorld)
                ?: editWorld.spawnLocation
        } ?: return failUnit(player, "skywars.editor_open_failed")
        player.sendMessage(Component.text(language.getMessage("skywars.editor_opened", world.name)))
    }

    private fun ensurePrivateSnapshot(game: ManagedGameConfig): Boolean {
        if (game.hasPrivateSnapshot()) return true
        if (!worldService.snapshotTemplateToDirectory(game.sharedMapTemplate, game.runtimeMapFolder)) return false
        managedGameCatalog.save(game) { it.set("runtime-map-template", "modules/${game.moduleId}/games/map/${game.localId}") }
        return true
    }

    private fun saveField(player: Player, game: ManagedGameConfig, fieldKey: String, save: () -> Unit) {
        save()
        mapEditorService.saveIfEditing(game.globalId)
        player.sendMessage(Component.text(language.getMessage("skywars.editor_saved_field", language.getMessage(fieldKey))))
    }

    /** 启动骨头右键位置采集，并持续覆盖指定单值坐标字段。 */
    private fun startPositionCapture(
        player: Player,
        game: ManagedGameConfig,
        fieldKey: String,
        save: (ManagedGameConfig, Location) -> Unit
    ) {
        if (activeEditedGame(player, game.globalId) == null) return
        pointCaptureService.beginPositionCapture(player, moduleId) { capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId) ?: return@beginPositionCapture false
            save(currentGame, location)
            capturePlayer.sendMessage(Component.text(language.getMessage("skywars.editor_saved_field", language.getMessage(fieldKey))))
            true
        }
    }

    private fun startIslandCapture(player: Player, game: ManagedGameConfig) {
        if (activeEditedGame(player, game.globalId) == null) return
        player.sendMessage(Component.text(language.getMessage("skywars.editor_capture_island_started")))
        pointCaptureService.beginPositionCapture(player, moduleId) { capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId) ?: return@beginPositionCapture false
            val id = configService.addNextManagedIslandSpawn(currentGame, mapPointService.fromLocation(location))
            capturePlayer.sendMessage(Component.text(language.getMessage("skywars.editor_island_added", id)))
            true
        }
    }

    private fun startChestCapture(player: Player, game: ManagedGameConfig, tier: String) {
        if (activeEditedGame(player, game.globalId) == null) return
        player.sendMessage(Component.text(language.getMessage("skywars.editor_capture_chest_started", tier)))
        pointCaptureService.beginBlockCapture(player, moduleId) { capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId) ?: return@beginBlockCapture false
            val id = configService.addNextManagedChest(currentGame, mapPointService.fromBlock(location), tier)
            capturePlayer.sendMessage(Component.text(language.getMessage("skywars.editor_chest_added", id, tier)))
            true
        }
    }

    private fun activeEditedGame(player: Player, globalId: String): ManagedGameConfig? {
        if (mapEditorService.currentSessionId(player) != globalId) {
            player.sendMessage(Component.text(language.getMessage("skywars.editor_capture_wrong_session")))
            return null
        }
        return managedGameCatalog.get(globalId).also { current ->
            if (current == null) player.sendMessage(Component.text(language.getMessage("skywars.editor_capture_wrong_session")))
        }
    }

    private fun removePoint(player: Player, game: ManagedGameConfig, id: String?, island: Boolean) {
        if (id == null) return failUnit(player, "skywars.editor_point_id_missing")
        val removed = if (island) configService.removeManagedIslandSpawn(game, id) else configService.removeManagedChest(game, id)
        if (removed) mapEditorService.saveIfEditing(game.globalId)
        val key = if (!removed) "skywars.editor_point_missing" else if (island) "skywars.editor_island_removed" else "skywars.editor_chest_removed"
        player.sendMessage(Component.text(language.getMessage(key, id)))
    }

    private fun preview(player: Player, game: ManagedGameConfig) {
        val configured = configService.readManagedGame(game)
        configured.playRegion?.edgeLocations(player.world, 160)
            ?.distinctBy { Triple(it.blockX, it.blockY, it.blockZ) }
            ?.take(120)
            ?.forEach { packetService.showBlockGlow(player, it, 10, NamedTextColor.YELLOW) }
        configured.islandSpawns.forEach { packetService.showBeaconBeam(player, it.point.toLocation(player.world), NamedTextColor.GREEN, 10) }
        configured.chests.forEach { packetService.showBeaconBeam(player, it.point.toLocation(player.world), NamedTextColor.GOLD, 10) }
        listOfNotNull(configured.lobby, configured.spectatorSpawn)
            .forEach { packetService.showBeaconBeam(player, it.toLocation(player.world), NamedTextColor.AQUA, 10) }
        player.sendMessage(Component.text(language.getMessage("skywars.editor_previewed")))
    }

    private fun input(menu: YamlConfiguration, id: String, text: String, default: String) {
        menu.set("Inputs.$id.type", "input")
        menu.set("Inputs.$id.text", text)
        menu.set("Inputs.$id.default", default)
        menu.set("Inputs.$id.max_length", 32)
    }

    private fun button(menu: YamlConfiguration, id: String, textKey: String, game: ManagedGameConfig, action: String) {
        menu.set("Bottom.buttons.$id.text", language.getMessage(textKey))
        menu.set("Bottom.buttons.$id.actions", listOf("kgc:module-game-action ${game.globalId} $action"))
    }

    private fun variable(variables: Map<String, String>, key: String): String? = variables[key]?.trim()?.takeIf(String::isNotBlank)

    private fun nextId(prefix: String, ids: List<String>): String {
        var index = ids.size + 1
        while (ids.any { it.equals("${prefix}_$index", ignoreCase = true) }) index++
        return "${prefix}_$index"
    }

    private fun status(value: Boolean): String = language.getMessage(if (value) "skywars.editor_status_set" else "skywars.editor_status_missing")

    private fun fail(player: Player, key: String): Boolean {
        player.sendMessage(Component.text(language.getMessage(key)))
        return false
    }

    private fun failUnit(player: Player, key: String) {
        player.sendMessage(Component.text(language.getMessage(key)))
    }
}
