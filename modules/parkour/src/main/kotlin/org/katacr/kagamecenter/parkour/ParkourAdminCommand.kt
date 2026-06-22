package org.katacr.kagamecenter.parkour

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.command.ModuleAdminCommand
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.selection.SelectionService

class ParkourAdminCommand(
    private val configService: ParkourConfigService,
    private val selectionService: SelectionService,
    private val packetService: PacketDispatchService,
    private val language: ModuleLanguage,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService
) : ModuleAdminCommand {
    override val name: String = "parkour"

    override fun execute(sender: CommandSender, label: String, args: Array<out String>) {
        when (args.getOrNull(0)?.lowercase()) {
            null, "help" -> help(sender, label)
            "reload" -> {
                configService.reload()
                language.reload()
                sender.sendMessage(Component.text(language.getMessage("parkour.admin_reloaded")))
            }
            "edit" -> requirePlayer(sender) { player ->
                val mapId = args.getOrNull(1) ?: return@requirePlayer help(sender, label)
                val map = resolveMap(mapId) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("parkour.admin_map_missing", mapId)))
                val sessionId = sessionId(map.id)
                val world = mapEditorService.openEditor(player, sessionId, map.template) { world ->
                    configService.current().maps[map.id]?.firstRoute()?.lobby?.toLocation(world)
                        ?: configService.current().maps[map.id]?.firstRoute()?.start?.spawn?.toLocation(world)
                        ?: world.spawnLocation
                }
                if (world == null) {
                    player.sendMessage(Component.text(language.getMessage("parkour.admin_edit_failed", mapId)))
                } else {
                    player.sendMessage(Component.text(language.getMessage("parkour.admin_edit_opened", mapId, world.name)))
                }
            }
            "saveedit" -> requirePlayer(sender) { player ->
                val mapId = args.getOrNull(1)
                val saved = if (mapId != null) mapEditorService.saveIfEditing(sessionId(mapId)) else mapEditorService.saveCurrentSession(player)
                player.sendMessage(Component.text(language.getMessage(if (saved) "parkour.admin_edit_saved" else "parkour.admin_edit_save_failed", mapId ?: "current")))
            }
            "closeedit" -> requirePlayer(sender) { player ->
                val mapId = args.getOrNull(1)
                val closed = if (mapId != null) mapEditorService.closeSession(sessionId(mapId), save = true, restoreEditors = true) else mapEditorService.closeCurrentSession(player, save = true)
                player.sendMessage(Component.text(language.getMessage(if (closed) "parkour.admin_edit_closed" else "parkour.admin_edit_close_failed", mapId ?: "current")))
            }
            "maps" -> sender.sendMessage(Component.text(configService.mapIds().joinToString(", ").ifBlank { "-" }))
            "routes" -> {
                val mapId = args.getOrNull(1) ?: return help(sender, label)
                sender.sendMessage(Component.text(configService.routeIds(mapId).joinToString(", ").ifBlank { "-" }))
            }
            "setlobby" -> requirePlayer(sender) { player ->
                currentManagedGame(player)?.let { game ->
                    configService.saveManagedLobby(game, ParkourPoint.from(player.location))
                    syncManagedTemplate(game)
                    player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_lobby"))))
                    return@requirePlayer
                }
                val (mapId, routeId) = mapRoute(args) ?: return@requirePlayer help(sender, label)
                configService.saveLobby(mapId, routeId, ParkourPoint.from(player.location))
                syncEditedTemplate(mapId)
                player.sendMessage(Component.text(language.getMessage("parkour.admin_saved", "lobby", mapId, routeId)))
            }
            "setstart" -> requirePlayer(sender) { player ->
                currentManagedGame(player)?.let { game ->
                    configService.saveManagedStartSpawn(game, ParkourPoint.from(player.location))
                    syncManagedTemplate(game)
                    player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_start"))))
                    return@requirePlayer
                }
                val (mapId, routeId) = mapRoute(args) ?: return@requirePlayer help(sender, label)
                configService.saveStartSpawn(mapId, routeId, ParkourPoint.from(player.location))
                syncEditedTemplate(mapId)
                player.sendMessage(Component.text(language.getMessage("parkour.admin_saved", "start", mapId, routeId)))
            }
            "setstartregion" -> requirePlayer(sender) { player ->
                currentManagedGame(player)?.let { game ->
                    val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                    configService.saveManagedStartRegion(game, region)
                    syncManagedTemplate(game)
                    player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_start_region"))))
                    return@requirePlayer
                }
                val (mapId, routeId) = mapRoute(args) ?: return@requirePlayer help(sender, label)
                val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                configService.saveStartRegion(mapId, routeId, region)
                syncEditedTemplate(mapId)
                player.sendMessage(Component.text(language.getMessage("parkour.admin_saved", "start-region", mapId, routeId)))
            }
            "setfinish" -> requirePlayer(sender) { player ->
                currentManagedGame(player)?.let { game ->
                    val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                    configService.saveManagedFinishRegion(game, region)
                    syncManagedTemplate(game)
                    player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_finish"))))
                    return@requirePlayer
                }
                val (mapId, routeId) = mapRoute(args) ?: return@requirePlayer help(sender, label)
                val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                configService.saveFinishRegion(mapId, routeId, region)
                syncEditedTemplate(mapId)
                player.sendMessage(Component.text(language.getMessage("parkour.admin_saved", "finish", mapId, routeId)))
            }
            "addcheckpoint" -> requirePlayer(sender) { player ->
                currentManagedGame(player)?.let { game ->
                    val checkpointId = args.getOrNull(1)?.let(::normalizeCheckpointId) ?: nextCheckpointId(game)
                    val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                    configService.addManagedCheckpoint(game, checkpointId, region, ParkourPoint.fromBlock(player.location))
                    syncManagedTemplate(game)
                    player.sendMessage(Component.text(language.getMessage("parkour.editor_checkpoint_added", checkpointId)))
                    return@requirePlayer
                }
                val mapId = args.getOrNull(1) ?: return@requirePlayer help(sender, label)
                val routeId = args.getOrNull(2) ?: return@requirePlayer help(sender, label)
                val checkpointId = args.getOrNull(3) ?: return@requirePlayer help(sender, label)
                val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                configService.addCheckpoint(mapId, routeId, checkpointId, region, ParkourPoint.fromBlock(player.location))
                syncEditedTemplate(mapId)
                player.sendMessage(Component.text(language.getMessage("parkour.admin_checkpoint_added", checkpointId, mapId, routeId)))
            }
            "removecheckpoint" -> {
                val player = sender as? Player
                val game = player?.let { currentManagedGame(it) }
                if (game != null) {
                    val checkpointId = args.getOrNull(1)?.let(::normalizeCheckpointId) ?: return help(sender, label)
                    val removed = configService.removeManagedCheckpoint(game, checkpointId)
                    if (removed) syncManagedTemplate(game)
                    sender.sendMessage(Component.text(language.getMessage(if (removed) "parkour.admin_checkpoint_removed" else "parkour.admin_checkpoint_missing", checkpointId)))
                    return
                }
                val mapId = args.getOrNull(1) ?: return help(sender, label)
                val routeId = args.getOrNull(2) ?: return help(sender, label)
                val checkpointId = args.getOrNull(3) ?: return help(sender, label)
                val removed = configService.removeCheckpoint(mapId, routeId, checkpointId)
                if (removed) syncEditedTemplate(mapId)
                sender.sendMessage(Component.text(language.getMessage(if (removed) "parkour.admin_checkpoint_removed" else "parkour.admin_checkpoint_missing", checkpointId)))
            }
            "addspeedbuff" -> requirePlayer(sender) { player ->
                currentManagedGame(player)?.let { game ->
                    val buffId = args.getOrNull(1) ?: nextBuffId(game)
                    configService.addManagedSpeedBuff(game, buffId, ParkourPoint.fromBlock(player.location))
                    syncManagedTemplate(game)
                    player.sendMessage(Component.text(language.getMessage("parkour.editor_buff_added", buffId)))
                    return@requirePlayer
                }
                val mapId = args.getOrNull(1) ?: return@requirePlayer help(sender, label)
                val routeId = args.getOrNull(2) ?: return@requirePlayer help(sender, label)
                val buffId = args.getOrNull(3) ?: return@requirePlayer help(sender, label)
                configService.addSpeedBuff(mapId, routeId, buffId, ParkourPoint.fromBlock(player.location))
                syncEditedTemplate(mapId)
                player.sendMessage(Component.text(language.getMessage("parkour.admin_buff_added", buffId, mapId, routeId)))
            }
            "removebuff" -> {
                val player = sender as? Player
                val game = player?.let { currentManagedGame(it) }
                if (game != null) {
                    val buffId = args.getOrNull(1) ?: return help(sender, label)
                    val removed = configService.removeManagedBuff(game, buffId)
                    if (removed) syncManagedTemplate(game)
                    sender.sendMessage(Component.text(language.getMessage(if (removed) "parkour.admin_buff_removed" else "parkour.admin_buff_missing", buffId)))
                    return
                }
                val mapId = args.getOrNull(1) ?: return help(sender, label)
                val routeId = args.getOrNull(2) ?: return help(sender, label)
                val buffId = args.getOrNull(3) ?: return help(sender, label)
                val removed = configService.removeBuff(mapId, routeId, buffId)
                if (removed) syncEditedTemplate(mapId)
                sender.sendMessage(Component.text(language.getMessage(if (removed) "parkour.admin_buff_removed" else "parkour.admin_buff_missing", buffId)))
            }
            "preview" -> requirePlayer(sender) { player ->
                val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                visualBlockPoints(region, player.world, 96).forEach { packetService.showBlockGlow(player, it, 10) }
                player.sendMessage(Component.text(language.getMessage("parkour.admin_preview")))
            }
            "previewall" -> requirePlayer(sender) { player ->
                currentManagedGame(player)?.let { game ->
                    previewManagedRoute(player, game)
                    return@requirePlayer
                }
                val mapId = args.getOrNull(1) ?: return@requirePlayer help(sender, label)
                val routeId = args.getOrNull(2) ?: return@requirePlayer help(sender, label)
                val route = resolveRoute(mapId, routeId) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("parkour.admin_route_missing", mapId, routeId)))
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
                player.sendMessage(Component.text(language.getMessage("parkour.admin_preview_all", mapId, routeId)))
            }
            else -> help(sender, label)
        }
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> filter(args[0], listOf("help", "reload", "edit", "saveedit", "closeedit", "maps", "routes", "setlobby", "setstart", "setstartregion", "setfinish", "addcheckpoint", "removecheckpoint", "addspeedbuff", "removebuff", "preview", "previewall"))
            2 -> filter(args[1], configService.mapIds())
            3 -> filter(args[2], configService.routeIds(args[1]))
            else -> emptyList()
        }
    }

    private fun resolveMap(mapId: String): ParkourMapConfig? = configService.current().maps[mapId]

    private fun resolveRoute(mapId: String, routeId: String): ParkourRouteConfig? = resolveMap(mapId)?.routes?.get(routeId)

    private fun syncEditedTemplate(mapId: String) {
        mapEditorService.saveIfEditing(sessionId(mapId))
    }

    private fun syncManagedTemplate(game: ManagedGameConfig) {
        mapEditorService.saveIfEditing(game.globalId)
    }

    private fun currentManagedGame(player: Player): ManagedGameConfig? {
        val sessionId = mapEditorService.currentSessionId(player) ?: return null
        return managedGameCatalog.get(sessionId)
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

    private fun nextBuffId(game: ManagedGameConfig): String {
        return "speed_${configService.readManagedRoute(game).buffs.size + 1}"
    }

    private fun previewManagedRoute(player: Player, game: ManagedGameConfig) {
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

    private fun visualBlockPoints(region: org.katacr.kaGameCenter.selection.RegionSelection, world: org.bukkit.World, limit: Int): List<org.bukkit.Location> {
        return region.edgeLocations(world, limit * 2)
            .distinctBy { Triple(it.blockX, it.blockY, it.blockZ) }
            .take(limit)
    }

    private fun sessionId(mapId: String): String = "parkour:$mapId"

    private fun help(sender: CommandSender, label: String) {
        sender.sendMessage(Component.text(language.getMessage("parkour.admin_help", label)))
    }

    private fun requirePlayer(sender: CommandSender, action: (Player) -> Unit) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(Component.text(language.getMessage("command.only_player_enter")))
            return
        }
        action(player)
    }

    private fun mapRoute(args: Array<out String>): Pair<String, String>? {
        val mapId = args.getOrNull(1) ?: return null
        val routeId = args.getOrNull(2) ?: return null
        return mapId to routeId
    }

    private fun filter(prefix: String, values: List<String>): List<String> {
        val lower = prefix.lowercase()
        return values.filter { it.lowercase().startsWith(lower) }
    }
}
