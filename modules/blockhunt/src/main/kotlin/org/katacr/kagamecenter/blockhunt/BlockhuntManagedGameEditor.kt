package org.katacr.kagamecenter.blockhunt

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
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.selection.SelectionService
import org.katacr.kaGameCenter.world.TemporaryWorldService

class BlockhuntManagedGameEditor(
    private val configService: BlockhuntConfigService,
    private val language: ModuleLanguage,
    private val selectionService: SelectionService,
    private val packetService: PacketDispatchService,
    private val worldService: TemporaryWorldService,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val menuService: GameCenterMenuService,
    private val pointCaptureService: EditorPointCaptureService
) : ModuleGameEditor {
    override val moduleId: String = "blockhunt"

    override fun populateDefaults(
        config: YamlConfiguration,
        localId: String,
        displayName: String,
        sharedMapTemplate: String
    ) {
        val map = configService.findMapByTemplate(sharedMapTemplate) ?: configService.current().firstMap()
        config.set("blockhunt.map-id", map?.id ?: sharedMapTemplate.substringAfterLast('/'))
        config.set("blockhunt.next-item-index", 1)
    }

    override fun openEditor(player: Player, game: ManagedGameConfig) {
        openEditorMenu(player, game, openWorldOnFailure = true)
    }

    /** 构造编辑菜单；仅首次入口失败时允许直接打开私有编辑世界。 */
    private fun openEditorMenu(player: Player, game: ManagedGameConfig, openWorldOnFailure: Boolean) {
        pointCaptureService.cancel(player)
        val menu = YamlConfiguration()
        val config = configService.readManagedGame(game)

        menu.set("Title", language.getMessage("blockhunt.editor_title", game.displayName))
        menu.set("Settings.can_escape", true)
        menu.set("Settings.after_action", "WAIT_FOR_RESPONSE")
        menu.set("Body.summary.type", "message")
        menu.set("Body.summary.width", 360)
        menu.set("Body.summary.text", listOf(
            language.getMessage("blockhunt.editor_summary_name", game.displayName),
            language.getMessage("blockhunt.editor_summary_shared_map", game.sharedMapTemplate),
            language.getMessage("blockhunt.editor_summary_runtime_map", game.effectiveMapTemplate()),
            language.getMessage("blockhunt.editor_summary_private_map", status(game.hasPrivateSnapshot())),
            language.getMessage("blockhunt.editor_summary_lobby", status(config.lobby != null)),
            language.getMessage("blockhunt.editor_summary_hunter_spawn", status(config.hunterSpawn != null)),
            language.getMessage("blockhunt.editor_summary_hider_spawn", status(config.hiderSpawn != null)),
            language.getMessage("blockhunt.editor_summary_play_region", status(config.playRegion != null)),
            language.getMessage("blockhunt.editor_summary_item_spawns", config.itemSpawns.size)
        ))
        menu.set("Inputs.item_id.type", "input")
        menu.set("Inputs.item_id.text", language.getMessage("blockhunt.editor_input_item_id"))
        menu.set("Inputs.item_id.default", "item_${config.itemSpawns.size + 1}")
        menu.set("Inputs.item_id.max_length", 32)
        menu.set("Bottom.type", "multi")
        menu.set("Bottom.columns", 3)
        button(menu, "open_world", language.getMessage("blockhunt.editor_button_open_world"), "kgc:module-game-action ${game.globalId} open-world")
        button(menu, "save_world", language.getMessage("blockhunt.editor_button_save_world"), "kgc:module-game-action ${game.globalId} save-world")
        button(menu, "close_world", language.getMessage("blockhunt.editor_button_close_world"), "kgc:module-game-action ${game.globalId} close-world")
        button(menu, "set_lobby", language.getMessage("blockhunt.editor_button_set_lobby"), "kgc:module-game-action ${game.globalId} set-lobby")
        button(menu, "set_hunter", language.getMessage("blockhunt.editor_button_set_hunter_spawn"), "kgc:module-game-action ${game.globalId} set-hunter-spawn")
        button(menu, "set_hider", language.getMessage("blockhunt.editor_button_set_hider_spawn"), "kgc:module-game-action ${game.globalId} set-hider-spawn")
        button(menu, "set_region", language.getMessage("blockhunt.editor_button_set_play_region"), "kgc:module-game-action ${game.globalId} set-play-region")
        button(menu, "add_item", language.getMessage("blockhunt.editor_button_add_item_spawn"), "kgc:module-game-action ${game.globalId} add-item-spawn")
        button(menu, "remove_item", language.getMessage("blockhunt.editor_button_remove_item_spawn"), "kgc:module-game-action ${game.globalId} remove-item-spawn")
        button(menu, "preview", language.getMessage("blockhunt.editor_button_preview"), "kgc:module-game-action ${game.globalId} preview")
        menu.set("Bottom.exit.text", language.getMessage("menu.button_back"))
        menu.set("Bottom.exit.actions", listOf("kgc:open-admin-managed-games"))
        if (!menuService.openExternalConfig(player, menu, "kagamecenter:blockhunt-editor:${game.globalId}")) {
            player.sendMessage(Component.text(language.getMessage("blockhunt.editor_menu_open_failed")))
            if (openWorldOnFailure) openWorld(player, game)
        }
    }

    override fun handleAction(player: Player, game: ManagedGameConfig, action: String, variables: Map<String, String>): Boolean {
        when (action.lowercase()) {
            "open-world" -> if (!openWorld(player, game)) return false
            "save-world" -> {
                val saved = mapEditorService.saveIfEditing(game.globalId)
                if (!saved) return fail(player, "blockhunt.editor_save_failed")
                player.sendMessage(Component.text(language.getMessage("blockhunt.editor_saved")))
            }
            "close-world" -> {
                val closed = mapEditorService.closeSession(game.globalId, save = true, restoreEditors = true)
                if (!closed) return fail(player, "blockhunt.editor_close_failed")
                player.sendMessage(Component.text(language.getMessage("blockhunt.editor_closed")))
            }
            "set-lobby" -> {
                startPositionCapture(player, game, "blockhunt.editor_field_lobby") { currentGame, location ->
                    configService.saveManagedLobby(currentGame, BlockhuntPoint.from(location))
                }
                return true
            }
            "set-hunter-spawn" -> {
                startPositionCapture(player, game, "blockhunt.editor_field_hunter_spawn") { currentGame, location ->
                    configService.saveManagedHunterSpawn(currentGame, BlockhuntPoint.from(location))
                }
                return true
            }
            "set-hider-spawn" -> {
                startPositionCapture(player, game, "blockhunt.editor_field_hider_spawn") { currentGame, location ->
                    configService.saveManagedHiderSpawn(currentGame, BlockhuntPoint.from(location))
                }
                return true
            }
            "set-play-region" -> {
                val selection = selectionService.getSelection(player) ?: return fail(player, "selection.not_ready")
                configService.saveManagedPlayRegion(game, selection)
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("blockhunt.editor_saved_field", language.getMessage("blockhunt.editor_field_play_region"))))
            }
            "add-item-spawn" -> {
                startItemCapture(player, game)
                return true
            }
            "remove-item-spawn" -> {
                val id = variables["item_id"]?.takeIf { it.isNotBlank() } ?: return fail(player, "blockhunt.editor_item_id_missing")
                val removed = configService.removeManagedItemSpawn(game, id)
                if (!removed) return fail(player, "blockhunt.editor_item_missing", id)
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("blockhunt.editor_item_removed", id)))
            }
            "preview" -> preview(player, game)
            else -> return false
        }

        val latest = managedGameCatalog.get(game.globalId) ?: game
        openEditorMenu(player, latest, openWorldOnFailure = false)
        return true
    }

    private fun ensurePrivateSnapshot(game: ManagedGameConfig): Boolean {
        if (game.hasPrivateSnapshot()) return true
        val ok = worldService.snapshotTemplateToDirectory(game.sharedMapTemplate, game.runtimeMapFolder)
        if (!ok) return false
        managedGameCatalog.save(game) { config ->
            config.set("runtime-map-template", "modules/${game.moduleId}/games/map/${game.localId}")
        }
        return true
    }

    /** 打开指定托管游戏的私有编辑世界，供菜单和管理命令复用。 */
    fun openWorld(player: Player, game: ManagedGameConfig): Boolean {
        if (!ensurePrivateSnapshot(game)) return fail(player, "blockhunt.editor_private_snapshot_failed")
        val config = configService.readManagedGame(game)
        val world = mapEditorService.openEditorDirectory(player, game.globalId, game.runtimeMapFolder) { editWorld ->
            config.lobby?.toLocation(editWorld)
                ?: config.hiderSpawn?.toLocation(editWorld)
                ?: editWorld.spawnLocation
        }
        if (world == null) return fail(player, "blockhunt.editor_open_failed")
        player.sendMessage(Component.text(language.getMessage("blockhunt.editor_opened", world.name)))
        return true
    }

    private fun saveEditingWorld(game: ManagedGameConfig) {
        mapEditorService.saveIfEditing(game.globalId)
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
            capturePlayer.sendMessage(Component.text(language.getMessage("blockhunt.editor_saved_field", language.getMessage(fieldKey))))
            true
        }
    }

    /** 启动骨头右键连续添加道具刷新位置，并由配置服务分配稳定递增编号。 */
    private fun startItemCapture(player: Player, game: ManagedGameConfig) {
        if (activeEditedGame(player, game.globalId) == null) return
        pointCaptureService.beginPositionCapture(player, moduleId) { capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId) ?: return@beginPositionCapture false
            val id = configService.addNextManagedItemSpawn(currentGame, BlockhuntPoint.from(location))
            capturePlayer.sendMessage(Component.text(language.getMessage("blockhunt.editor_item_added", id)))
            true
        }
    }

    /** 确认玩家仍在目标托管游戏的编辑世界，并读取最新配置实例。 */
    private fun activeEditedGame(player: Player, globalId: String): ManagedGameConfig? {
        if (mapEditorService.currentSessionId(player) != globalId) {
            player.sendMessage(Component.text(language.getMessage("blockhunt.editor_capture_wrong_session")))
            return null
        }
        return managedGameCatalog.get(globalId).also { current ->
            if (current == null) player.sendMessage(Component.text(language.getMessage("blockhunt.editor_capture_wrong_session")))
        }
    }

    private fun preview(player: Player, game: ManagedGameConfig) {
        val config = configService.readManagedGame(game)
        config.playRegion?.edgeLocations(player.world, 192)
            ?.distinctBy { Triple(it.blockX, it.blockY, it.blockZ) }
            ?.take(96)
            ?.forEach { packetService.showBlockGlow(player, it, 10, NamedTextColor.YELLOW) }
        config.itemSpawns.forEach { spawn ->
            val location = spawn.point.toLocation(player.world)
            packetService.showBlockGlow(player, location, 10, NamedTextColor.AQUA)
            packetService.showBeaconBeam(player, location, NamedTextColor.AQUA, 10)
        }
        player.sendMessage(Component.text(language.getMessage("blockhunt.editor_previewed")))
    }

    private fun fail(player: Player, key: String, vararg args: Any): Boolean {
        player.sendMessage(Component.text(language.getMessage(key, *args)))
        return false
    }

    private fun status(value: Boolean): String {
        return if (value) language.getMessage("blockhunt.editor_status_set") else language.getMessage("blockhunt.editor_status_missing")
    }

    private fun button(config: YamlConfiguration, key: String, text: String, action: String) {
        config.set("Bottom.buttons.$key.text", text)
        config.set("Bottom.buttons.$key.actions", listOf(action))
    }
}
