package org.katacr.kagamecenter.parkour

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
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.selection.SelectionService
import org.katacr.kaGameCenter.world.TemporaryWorldService

class ParkourManagedGameEditor(
    private val configService: ParkourConfigService,
    private val language: ModuleLanguage,
    private val selectionService: SelectionService,
    private val packetService: PacketDispatchService,
    private val worldService: TemporaryWorldService,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val menuService: GameCenterMenuService
) : ModuleGameEditor {
    override val moduleId: String = "parkour"

    override fun populateDefaults(
        config: YamlConfiguration,
        localId: String,
        displayName: String,
        sharedMapTemplate: String
    ) {
        val map = configService.findMapByTemplate(sharedMapTemplate) ?: configService.current().firstMap()
        config.set("parkour.map-id", map?.id ?: sharedMapTemplate.substringAfterLast('/'))
    }

    override fun openEditor(player: Player, game: ManagedGameConfig) {
        val config = YamlConfiguration()
        val route = configService.readManagedRoute(game)

        config.set("Title", language.getMessage("parkour.editor_title", game.displayName))
        config.set("Settings.can_escape", true)
        config.set("Settings.after_action", "WAIT_FOR_RESPONSE")
        config.set("Body.summary.type", "message")
        config.set("Body.summary.width", 340)
        config.set("Body.summary.text", listOf(
            language.getMessage("parkour.editor_summary_name", game.displayName),
            language.getMessage("parkour.editor_summary_shared_map", game.sharedMapTemplate),
            language.getMessage("parkour.editor_summary_runtime_map", game.effectiveMapTemplate()),
            language.getMessage("parkour.editor_summary_private_map", status(game.hasPrivateSnapshot())),
            language.getMessage("parkour.editor_summary_lobby", status(route.lobby != null)),
            language.getMessage("parkour.editor_summary_start_spawn", status(route.start?.spawn != null)),
            language.getMessage("parkour.editor_summary_start_region", status(route.start?.region != null)),
            language.getMessage("parkour.editor_summary_finish", status(route.finish != null)),
            language.getMessage("parkour.editor_summary_checkpoints", route.checkpoints.size),
            language.getMessage("parkour.editor_summary_buffs", route.buffs.size)
        ))
        config.set("Inputs.checkpoint_index.type", "input")
        config.set("Inputs.checkpoint_index.text", language.getMessage("parkour.editor_input_checkpoint_index"))
        config.set("Inputs.checkpoint_index.default", route.checkpoints.size.toString())
        config.set("Inputs.checkpoint_index.max_length", 8)
        config.set("Inputs.buff_id.type", "input")
        config.set("Inputs.buff_id.text", language.getMessage("parkour.editor_input_buff"))
        config.set("Inputs.buff_id.default", "speed_${route.buffs.size + 1}")
        config.set("Inputs.buff_id.max_length", 32)
        config.set("Bottom.type", "multi")
        config.set("Bottom.columns", 3)
        button(config, "open_world", language.getMessage("parkour.editor_button_open_world"), "kgc:module-game-action ${game.globalId} open-world")
        button(config, "save_world", language.getMessage("parkour.editor_button_save_world"), "kgc:module-game-action ${game.globalId} save-world")
        button(config, "close_world", language.getMessage("parkour.editor_button_close_world"), "kgc:module-game-action ${game.globalId} close-world")
        button(config, "set_lobby", language.getMessage("parkour.editor_button_set_lobby"), "kgc:module-game-action ${game.globalId} set-lobby")
        button(config, "set_start", language.getMessage("parkour.editor_button_set_start"), "kgc:module-game-action ${game.globalId} set-start")
        button(config, "set_start_region", language.getMessage("parkour.editor_button_set_start_region"), "kgc:module-game-action ${game.globalId} set-start-region")
        button(config, "set_finish", language.getMessage("parkour.editor_button_set_finish"), "kgc:module-game-action ${game.globalId} set-finish")
        button(config, "add_checkpoint", language.getMessage("parkour.editor_button_add_checkpoint"), "kgc:module-game-action ${game.globalId} add-checkpoint")
        button(config, "remove_checkpoint", language.getMessage("parkour.editor_button_remove_checkpoint"), "kgc:module-game-action ${game.globalId} remove-checkpoint")
        button(config, "add_buff", language.getMessage("parkour.editor_button_add_buff"), "kgc:module-game-action ${game.globalId} add-speed-buff")
        button(config, "remove_buff", language.getMessage("parkour.editor_button_remove_buff"), "kgc:module-game-action ${game.globalId} remove-buff")
        button(config, "preview", language.getMessage("parkour.editor_button_preview"), "kgc:module-game-action ${game.globalId} preview-route")
        config.set("Bottom.exit.text", language.getMessage("menu.button_back"))
        config.set("Bottom.exit.actions", listOf("kgc:open-admin-managed-games"))
        menuService.openExternalConfig(player, config, "kagamecenter:parkour-editor:${game.globalId}")
    }

    override fun handleAction(player: Player, game: ManagedGameConfig, action: String, variables: Map<String, String>): Boolean {
        when (action.lowercase()) {
            "open-world" -> {
                ensurePrivateSnapshot(game) || return fail(player, "parkour.editor_private_snapshot_failed")
                val route = configService.readManagedRoute(game)
                val world = mapEditorService.openEditorDirectory(player, game.globalId, game.runtimeMapFolder) { editWorld ->
                    route?.lobby?.toLocation(editWorld)
                        ?: route?.start?.spawn?.toLocation(editWorld)
                        ?: editWorld.spawnLocation
                }
                if (world == null) return fail(player, "parkour.editor_open_failed")
                player.sendMessage(Component.text(language.getMessage("parkour.editor_opened", world.name)))
            }
            "save-world" -> {
                val saved = mapEditorService.saveIfEditing(game.globalId)
                if (!saved) return fail(player, "parkour.editor_save_failed")
                player.sendMessage(Component.text(language.getMessage("parkour.editor_saved")))
            }
            "close-world" -> {
                val closed = mapEditorService.closeSession(game.globalId, save = true, restoreEditors = true)
                if (!closed) return fail(player, "parkour.editor_close_failed")
                player.sendMessage(Component.text(language.getMessage("parkour.editor_closed")))
            }
            "set-lobby" -> {
                configService.saveManagedLobby(game, ParkourPoint.from(player.location))
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_lobby"))))
            }
            "set-start" -> {
                configService.saveManagedStartSpawn(game, ParkourPoint.from(player.location))
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_start"))))
            }
            "set-start-region" -> {
                val selection = selectionService.getSelection(player) ?: return fail(player, "selection.not_ready")
                configService.saveManagedStartRegion(game, selection)
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_start_region"))))
            }
            "set-finish" -> {
                val selection = selectionService.getSelection(player) ?: return fail(player, "selection.not_ready")
                configService.saveManagedFinishRegion(game, selection)
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_finish"))))
            }
            "add-checkpoint" -> {
                val checkpointId = nextCheckpointId(game)
                val selection = selectionService.getSelection(player) ?: return fail(player, "selection.not_ready")
                configService.addManagedCheckpoint(game, checkpointId, selection, ParkourPoint.fromBlock(player.location))
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_checkpoint_added", checkpointId)))
            }
            "remove-checkpoint" -> {
                val checkpointId = normalizeCheckpointId(variables["checkpoint_index"] ?: return fail(player, "parkour.editor_checkpoint_id_missing"))
                val removed = configService.removeManagedCheckpoint(game, checkpointId)
                if (!removed) return fail(player, "parkour.admin_checkpoint_missing", checkpointId)
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("parkour.admin_checkpoint_removed", checkpointId)))
            }
            "add-speed-buff" -> {
                val buffId = variables["buff_id"]?.takeIf { it.isNotBlank() } ?: return fail(player, "parkour.editor_buff_id_missing")
                configService.addManagedSpeedBuff(game, buffId, ParkourPoint.fromBlock(player.location))
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_buff_added", buffId)))
            }
            "remove-buff" -> {
                val buffId = variables["buff_id"]?.takeIf { it.isNotBlank() } ?: return fail(player, "parkour.editor_buff_id_missing")
                val removed = configService.removeManagedBuff(game, buffId)
                if (!removed) return fail(player, "parkour.admin_buff_missing", buffId)
                saveEditingWorld(game)
                player.sendMessage(Component.text(language.getMessage("parkour.admin_buff_removed", buffId)))
            }
            "preview-route" -> {
                val route = configService.readManagedRoute(game)
                route.checkpoints.forEach { checkpoint ->
                    val region = checkpoint.glowRegion ?: checkpoint.region
                    visualBlockPoints(region, player.world, 96).forEach { point ->
                        packetService.showBlockGlow(player, point, 10, NamedTextColor.YELLOW)
                    }
                }
                route.finish?.let { finish ->
                    val region = finish.glowRegion ?: finish.region
                    visualBlockPoints(region, player.world, 96).forEach { point ->
                        packetService.showBlockGlow(player, point, 10, NamedTextColor.GREEN)
                    }
                }
                player.sendMessage(Component.text(language.getMessage("parkour.editor_previewed")))
            }
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

    private fun nextCheckpointId(game: ManagedGameConfig): String {
        val used = configService.readManagedRoute(game).checkpoints
            .mapNotNull { it.id.toIntOrNull() }
            .toSet()
        var id = 1
        while (id in used) id++
        return id.toString()
    }

    private fun normalizeCheckpointId(value: String): String {
        return value.trim().removePrefix("checkpoint_")
    }

    private fun fail(player: Player, key: String, vararg args: Any): Boolean {
        player.sendMessage(Component.text(language.getMessage(key, *args)))
        return false
    }

    private fun status(value: Boolean): String {
        return if (value) language.getMessage("parkour.editor_status_set") else language.getMessage("parkour.editor_status_missing")
    }

    private fun button(config: YamlConfiguration, key: String, text: String, action: String) {
        config.set("Bottom.buttons.$key.text", text)
        config.set("Bottom.buttons.$key.actions", listOf(action))
    }

    private fun visualBlockPoints(region: org.katacr.kaGameCenter.selection.RegionSelection, world: org.bukkit.World, limit: Int): List<org.bukkit.Location> {
        return region.edgeLocations(world, limit * 2)
            .distinctBy { Triple(it.blockX, it.blockY, it.blockZ) }
            .take(limit)
    }
}
