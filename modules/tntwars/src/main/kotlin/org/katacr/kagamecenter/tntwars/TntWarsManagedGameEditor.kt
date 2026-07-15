package org.katacr.kagamecenter.tntwars

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

class TntWarsManagedGameEditor(
    private val configService: TntWarsConfigService,
    private val language: ModuleLanguage,
    private val selectionService: SelectionService,
    private val packetService: PacketDispatchService,
    private val worldService: TemporaryWorldService,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val menuService: GameCenterMenuService,
    private val pointCaptureService: EditorPointCaptureService
) : ModuleGameEditor {
    override val moduleId: String = "tntwars"

    override fun populateDefaults(
        config: YamlConfiguration,
        localId: String,
        displayName: String,
        sharedMapTemplate: String
    ) {
        val map = configService.findMapByTemplate(sharedMapTemplate) ?: configService.current().firstMap()
        config.set("tntwars.map-id", map?.id ?: sharedMapTemplate.substringAfterLast('/'))
    }

    override fun openEditor(player: Player, game: ManagedGameConfig) {
        pointCaptureService.cancel(player)
        val menu = YamlConfiguration()
        val config = configService.readManagedGame(game)

        menu.set("Title", language.getMessage("tntwars.editor_title", game.displayName))
        menu.set("Settings.can_escape", true)
        menu.set("Settings.after_action", "WAIT_FOR_RESPONSE")
        menu.set("Body.summary.type", "message")
        menu.set("Body.summary.width", 360)
        menu.set("Body.summary.text", listOf(
            language.getMessage("tntwars.editor_summary_name", game.displayName),
            language.getMessage("tntwars.editor_summary_shared_map", game.sharedMapTemplate),
            language.getMessage("tntwars.editor_summary_runtime_map", game.effectiveMapTemplate()),
            language.getMessage("tntwars.editor_summary_private_map", status(game.hasPrivateSnapshot())),
            language.getMessage("tntwars.editor_summary_lobby", status(config.lobby != null)),
            language.getMessage("tntwars.editor_summary_spectator", status(config.spectatorSpawn != null)),
            language.getMessage("tntwars.editor_summary_red_spawn", status(config.redSpawn != null)),
            language.getMessage("tntwars.editor_summary_blue_spawn", status(config.blueSpawn != null)),
            language.getMessage("tntwars.editor_summary_region", status(config.playRegion != null)),
            language.getMessage("tntwars.editor_summary_void_y", config.voidY ?: configService.current().defaultVoidY)
        ))
        menu.set("Bottom.type", "multi")
        menu.set("Bottom.columns", 3)
        button(menu, "open_world", language.getMessage("tntwars.editor_button_open_world"), "kgc:module-game-action ${game.globalId} open-world")
        button(menu, "save_world", language.getMessage("tntwars.editor_button_save_world"), "kgc:module-game-action ${game.globalId} save-world")
        button(menu, "close_world", language.getMessage("tntwars.editor_button_close_world"), "kgc:module-game-action ${game.globalId} close-world")
        button(menu, "set_lobby", language.getMessage("tntwars.editor_button_set_lobby"), "kgc:module-game-action ${game.globalId} set-lobby")
        button(menu, "set_spectator", language.getMessage("tntwars.editor_button_set_spectator"), "kgc:module-game-action ${game.globalId} set-spectator")
        button(menu, "set_red", language.getMessage("tntwars.editor_button_set_red_spawn"), "kgc:module-game-action ${game.globalId} set-red-spawn")
        button(menu, "set_blue", language.getMessage("tntwars.editor_button_set_blue_spawn"), "kgc:module-game-action ${game.globalId} set-blue-spawn")
        button(menu, "set_region", language.getMessage("tntwars.editor_button_set_region"), "kgc:module-game-action ${game.globalId} set-region")
        button(menu, "set_void_y", language.getMessage("tntwars.editor_button_set_void_y"), "kgc:module-game-action ${game.globalId} set-void-y")
        button(menu, "preview", language.getMessage("tntwars.editor_button_preview"), "kgc:module-game-action ${game.globalId} preview")
        menu.set("Bottom.exit.text", language.getMessage("menu.button_back"))
        menu.set("Bottom.exit.actions", listOf("kgc:open-admin-managed-games"))
        menuService.openExternalConfig(player, menu, "kagamecenter:tntwars-editor:${game.globalId}")
    }

    override fun handleAction(player: Player, game: ManagedGameConfig, action: String, variables: Map<String, String>): Boolean {
        when (action.lowercase()) {
            "open-world" -> {
                ensurePrivateSnapshot(game) || return fail(player, "tntwars.editor_private_snapshot_failed")
                val config = configService.readManagedGame(game)
                val world = mapEditorService.openEditorDirectory(player, game.globalId, game.runtimeMapFolder) { editWorld ->
                    config.lobby?.toLocation(editWorld)
                        ?: config.redSpawn?.toLocation(editWorld)
                        ?: config.blueSpawn?.toLocation(editWorld)
                        ?: editWorld.spawnLocation
                }
                if (world == null) return fail(player, "tntwars.editor_open_failed")
                player.sendMessage(Component.text(language.getMessage("tntwars.editor_opened", world.name)))
            }
            "save-world" -> {
                val saved = mapEditorService.saveIfEditing(game.globalId)
                if (!saved) return fail(player, "tntwars.editor_save_failed")
                player.sendMessage(Component.text(language.getMessage("tntwars.editor_saved")))
            }
            "close-world" -> {
                val closed = mapEditorService.closeSession(game.globalId, save = true, restoreEditors = true)
                if (!closed) return fail(player, "tntwars.editor_close_failed")
                player.sendMessage(Component.text(language.getMessage("tntwars.editor_closed")))
            }
            "set-lobby" -> {
                startPositionCapture(player, game, "tntwars.editor_field_lobby") { currentGame, location ->
                    configService.saveManagedLobby(currentGame, TntWarsPoint.from(location))
                }
                return true
            }
            "set-spectator" -> {
                startPositionCapture(player, game, "tntwars.editor_field_spectator") { currentGame, location ->
                    configService.saveManagedSpectatorSpawn(currentGame, TntWarsPoint.from(location))
                }
                return true
            }
            "set-red-spawn" -> {
                startPositionCapture(player, game, "tntwars.editor_field_red_spawn") { currentGame, location ->
                    configService.saveManagedTeamSpawn(currentGame, TntWarsTeam.RED, TntWarsPoint.from(location))
                }
                return true
            }
            "set-blue-spawn" -> {
                startPositionCapture(player, game, "tntwars.editor_field_blue_spawn") { currentGame, location ->
                    configService.saveManagedTeamSpawn(currentGame, TntWarsTeam.BLUE, TntWarsPoint.from(location))
                }
                return true
            }
            "set-region" -> {
                val selection = selectionService.getSelection(player) ?: return fail(player, "selection.not_ready")
                configService.saveManagedPlayRegion(game, selection)
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("tntwars.editor_saved_field", language.getMessage("tntwars.editor_field_region"))))
            }
            "set-void-y" -> {
                startPositionCapture(player, game, "tntwars.editor_field_void_y") { currentGame, location ->
                    configService.saveManagedVoidY(currentGame, location.y)
                }
                return true
            }
            "preview" -> preview(player, game)
            else -> return false
        }

        val latest = managedGameCatalog.get(game.globalId) ?: game
        openEditor(player, latest)
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
            capturePlayer.sendMessage(Component.text(language.getMessage("tntwars.editor_saved_field", language.getMessage(fieldKey))))
            true
        }
    }

    /** 确认玩家仍在目标托管游戏的编辑世界，并读取最新配置实例。 */
    private fun activeEditedGame(player: Player, globalId: String): ManagedGameConfig? {
        if (mapEditorService.currentSessionId(player) != globalId) {
            player.sendMessage(Component.text(language.getMessage("tntwars.editor_capture_wrong_session")))
            return null
        }
        return managedGameCatalog.get(globalId).also { current ->
            if (current == null) player.sendMessage(Component.text(language.getMessage("tntwars.editor_capture_wrong_session")))
        }
    }

    private fun preview(player: Player, game: ManagedGameConfig) {
        val config = configService.readManagedGame(game)
        config.playRegion?.edgeLocations(player.world, 160)
            ?.distinctBy { Triple(it.blockX, it.blockY, it.blockZ) }
            ?.take(120)
            ?.forEach { packetService.showBlockGlow(player, it, 10, NamedTextColor.YELLOW) }
        listOfNotNull(
            config.lobby?.toLocation(player.world),
            config.spectatorSpawn?.toLocation(player.world),
            config.redSpawn?.toLocation(player.world),
            config.blueSpawn?.toLocation(player.world)
        ).forEach { packetService.showBeaconBeam(player, it, NamedTextColor.AQUA, 10) }
        player.sendMessage(Component.text(language.getMessage("tntwars.editor_previewed")))
    }

    private fun fail(player: Player, key: String, vararg args: Any): Boolean {
        player.sendMessage(Component.text(language.getMessage(key, *args)))
        return false
    }

    private fun status(value: Boolean): String {
        return if (value) language.getMessage("tntwars.editor_status_set") else language.getMessage("tntwars.editor_status_missing")
    }

    private fun button(config: YamlConfiguration, key: String, text: String, action: String) {
        config.set("Bottom.buttons.$key.text", text)
        config.set("Bottom.buttons.$key.actions", listOf(action))
    }
}
