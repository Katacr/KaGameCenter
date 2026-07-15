package org.katacr.kagamecenter.hunger

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.game.ModuleGameEditor
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.map.ManagedMapPointService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.selection.SelectionService
import org.katacr.kaGameCenter.world.TemporaryWorldService

/** 通过 KaMenu 编辑经典 Hunger 托管游戏的固定点位和区域。 */
class HungerManagedGameEditor(
    private val configService: HungerConfigService,
    private val language: ModuleLanguage,
    private val selectionService: SelectionService,
    private val packetService: PacketDispatchService,
    private val worldService: TemporaryWorldService,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val menuService: GameCenterMenuService,
    private val mapPointService: ManagedMapPointService
) : ModuleGameEditor {
    override val moduleId: String = "hunger"

    override fun populateDefaults(config: YamlConfiguration, localId: String, displayName: String, sharedMapTemplate: String) {
        val map = configService.findMapByTemplate(sharedMapTemplate) ?: configService.current().firstMap()
        config.set("hunger.map-id", map?.id ?: sharedMapTemplate.substringAfterLast('/'))
        config.set("hunger.tribute-spawns", emptyList<Map<String, Any>>())
        config.set("hunger.deathmatch-spawns", emptyList<Map<String, Any>>())
        config.set("hunger.supply-chests", emptyList<Map<String, Any>>())
    }

    override fun openEditor(player: Player, game: ManagedGameConfig) {
        val configured = configService.readManagedGame(game)
        val menu = YamlConfiguration()
        menu.set("Title", language.getMessage("hunger.editor_title", game.displayName))
        menu.set("Settings.can_escape", true)
        menu.set("Settings.after_action", "WAIT_FOR_RESPONSE")
        menu.set("Body.summary.type", "message")
        menu.set("Body.summary.width", 400)
        menu.set("Body.summary.text", listOf(
            language.getMessage("hunger.editor_summary_name", game.displayName),
            language.getMessage("hunger.editor_summary_shared_map", game.sharedMapTemplate),
            language.getMessage("hunger.editor_summary_runtime_map", game.effectiveMapTemplate()),
            language.getMessage("hunger.editor_summary_private_map", status(game.hasPrivateSnapshot())),
            language.getMessage("hunger.editor_summary_lobby", status(configured.lobby != null)),
            language.getMessage("hunger.editor_summary_spectator", status(configured.spectatorSpawn != null)),
            language.getMessage("hunger.editor_summary_play_region", status(configured.playRegion != null)),
            language.getMessage("hunger.editor_summary_tribute_spawns", configured.tributeSpawns.size),
            language.getMessage("hunger.editor_summary_deathmatch_spawns", configured.deathmatchSpawns.size),
            language.getMessage("hunger.editor_summary_supply_chests", configured.supplyChests.size),
            language.getMessage("hunger.editor_summary_void_y", configured.voidY ?: configService.current().defaultVoidY)
        ))
        input(menu, "tribute_id", language.getMessage("hunger.editor_input_tribute_id"), nextId("tribute", configured.tributeSpawns))
        input(menu, "deathmatch_id", language.getMessage("hunger.editor_input_deathmatch_id"), nextId("dm", configured.deathmatchSpawns))
        input(menu, "chest_id", language.getMessage("hunger.editor_input_chest_id"), nextId("chest", configured.supplyChests))
        menu.set("Bottom.type", "multi")
        menu.set("Bottom.columns", 3)
        button(menu, "open_world", "hunger.editor_button_open_world", game, "open-world")
        button(menu, "save_world", "hunger.editor_button_save_world", game, "save-world")
        button(menu, "close_world", "hunger.editor_button_close_world", game, "close-world")
        button(menu, "set_lobby", "hunger.editor_button_set_lobby", game, "set-lobby")
        button(menu, "set_spectator", "hunger.editor_button_set_spectator", game, "set-spectator")
        button(menu, "set_region", "hunger.editor_button_set_play_region", game, "set-play-region")
        button(menu, "set_void", "hunger.editor_button_set_void_y", game, "set-void-y")
        button(menu, "add_tribute", "hunger.editor_button_add_tribute", game, "add-tribute")
        button(menu, "remove_tribute", "hunger.editor_button_remove_tribute", game, "remove-tribute")
        button(menu, "add_dm", "hunger.editor_button_add_deathmatch", game, "add-deathmatch")
        button(menu, "remove_dm", "hunger.editor_button_remove_deathmatch", game, "remove-deathmatch")
        button(menu, "add_chest", "hunger.editor_button_add_chest", game, "add-chest")
        button(menu, "remove_chest", "hunger.editor_button_remove_chest", game, "remove-chest")
        button(menu, "preview", "hunger.editor_button_preview", game, "preview")
        menu.set("Bottom.exit.text", language.getMessage("menu.button_back"))
        menu.set("Bottom.exit.actions", listOf("kgc:open-admin-managed-games"))
        menuService.openExternalConfig(player, menu, "kagamecenter:hunger-editor:${game.globalId}")
    }

    override fun handleAction(player: Player, game: ManagedGameConfig, action: String, variables: Map<String, String>): Boolean {
        when (action.lowercase()) {
            "open-world" -> openWorld(player, game)
            "save-world" -> {
                if (!mapEditorService.saveIfEditing(game.globalId)) return fail(player, "hunger.editor_save_failed")
                player.sendMessage(Component.text(language.getMessage("hunger.editor_saved")))
            }
            "close-world" -> {
                if (!mapEditorService.closeSession(game.globalId, save = true, restoreEditors = true)) return fail(player, "hunger.editor_close_failed")
                player.sendMessage(Component.text(language.getMessage("hunger.editor_closed")))
            }
            "set-lobby" -> saveField(player, game, "hunger.editor_field_lobby") {
                configService.saveManagedLobby(game, mapPointService.fromLocation(player.location))
            }
            "set-spectator" -> saveField(player, game, "hunger.editor_field_spectator") {
                configService.saveManagedSpectatorSpawn(game, mapPointService.fromLocation(player.location))
            }
            "set-play-region" -> {
                val selection = selectionService.getSelection(player) ?: return fail(player, "selection.not_ready")
                saveField(player, game, "hunger.editor_field_play_region") {
                    configService.saveManagedPlayRegion(game, selection)
                }
            }
            "set-void-y" -> saveField(player, game, "hunger.editor_field_void_y") {
                configService.saveManagedVoidY(game, player.location.y)
            }
            "add-tribute" -> {
                val id = variable(variables, "tribute_id") ?: nextId("tribute", configService.readManagedGame(game).tributeSpawns)
                configService.addManagedTributeSpawn(game, id, mapPointService.fromLocation(player.location))
                changed(player, game, "hunger.editor_tribute_added", id)
            }
            "remove-tribute" -> removePoint(player, game, variable(variables, "tribute_id"), "tribute")
            "add-deathmatch" -> {
                val id = variable(variables, "deathmatch_id") ?: nextId("dm", configService.readManagedGame(game).deathmatchSpawns)
                configService.addManagedDeathmatchSpawn(game, id, mapPointService.fromLocation(player.location))
                changed(player, game, "hunger.editor_deathmatch_added", id)
            }
            "remove-deathmatch" -> removePoint(player, game, variable(variables, "deathmatch_id"), "deathmatch")
            "add-chest" -> {
                val id = variable(variables, "chest_id") ?: nextId("chest", configService.readManagedGame(game).supplyChests)
                configService.addManagedSupplyChest(game, id, mapPointService.fromBlock(player.location))
                changed(player, game, "hunger.editor_chest_added", id)
            }
            "remove-chest" -> removePoint(player, game, variable(variables, "chest_id"), "chest")
            "preview" -> preview(player, game)
            else -> return false
        }
        val latest = managedGameCatalog.get(game.globalId) ?: game
        openEditor(player, latest)
        return true
    }

    private fun openWorld(player: Player, game: ManagedGameConfig) {
        if (!ensurePrivateSnapshot(game)) {
            fail(player, "hunger.editor_private_snapshot_failed")
            return
        }
        val configured = configService.readManagedGame(game)
        val world = mapEditorService.openEditorDirectory(player, game.globalId, game.runtimeMapFolder) { editWorld ->
            configured.lobby?.toLocation(editWorld)
                ?: configured.tributeSpawns.firstOrNull()?.point?.toLocation(editWorld)
                ?: editWorld.spawnLocation
        }
        if (world == null) {
            fail(player, "hunger.editor_open_failed")
            return
        }
        player.sendMessage(Component.text(language.getMessage("hunger.editor_opened", world.name)))
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
        player.sendMessage(Component.text(language.getMessage("hunger.editor_saved_field", language.getMessage(fieldKey))))
    }

    private fun changed(player: Player, game: ManagedGameConfig, key: String, id: String) {
        mapEditorService.saveIfEditing(game.globalId)
        player.sendMessage(Component.text(language.getMessage(key, id)))
    }

    private fun removePoint(player: Player, game: ManagedGameConfig, id: String?, type: String) {
        if (id == null) {
            player.sendMessage(Component.text(language.getMessage("hunger.editor_point_id_missing")))
            return
        }
        val removed = when (type) {
            "tribute" -> configService.removeManagedTributeSpawn(game, id)
            "deathmatch" -> configService.removeManagedDeathmatchSpawn(game, id)
            else -> configService.removeManagedSupplyChest(game, id)
        }
        if (removed) mapEditorService.saveIfEditing(game.globalId)
        val key = when {
            !removed -> "hunger.editor_point_missing"
            type == "tribute" -> "hunger.editor_tribute_removed"
            type == "deathmatch" -> "hunger.editor_deathmatch_removed"
            else -> "hunger.editor_chest_removed"
        }
        player.sendMessage(Component.text(language.getMessage(key, id)))
    }

    private fun preview(player: Player, game: ManagedGameConfig) {
        val configured = configService.readManagedGame(game)
        configured.playRegion?.edgeLocations(player.world, 160)
            ?.distinctBy { Triple(it.blockX, it.blockY, it.blockZ) }
            ?.take(120)
            ?.forEach { packetService.showBlockGlow(player, it, 10, NamedTextColor.YELLOW) }
        configured.tributeSpawns.forEach { packetService.showBeaconBeam(player, it.point.toLocation(player.world), NamedTextColor.GREEN, 10) }
        configured.deathmatchSpawns.forEach { packetService.showBeaconBeam(player, it.point.toLocation(player.world), NamedTextColor.RED, 10) }
        configured.supplyChests.forEach { packetService.showBeaconBeam(player, it.point.toLocation(player.world), NamedTextColor.GOLD, 10) }
        listOfNotNull(configured.lobby, configured.spectatorSpawn)
            .forEach { packetService.showBeaconBeam(player, it.toLocation(player.world), NamedTextColor.AQUA, 10) }
        player.sendMessage(Component.text(language.getMessage("hunger.editor_previewed")))
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

    private fun variable(variables: Map<String, String>, key: String): String? {
        return variables[key]?.trim()?.takeIf(String::isNotBlank)
    }

    private fun nextId(prefix: String, points: List<HungerNamedPoint>): String {
        var index = points.size + 1
        while (points.any { it.id.equals("${prefix}_$index", ignoreCase = true) }) index++
        return "${prefix}_$index"
    }

    private fun status(value: Boolean): String {
        return language.getMessage(if (value) "hunger.editor_status_set" else "hunger.editor_status_missing")
    }

    private fun fail(player: Player, key: String, vararg args: Any): Boolean {
        player.sendMessage(Component.text(language.getMessage(key, *args)))
        return false
    }
}
