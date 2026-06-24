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
            "setlobby" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                configService.saveManagedLobby(game, ParkourPoint.from(player.location))
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_lobby"))))
            }
            "setstart" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                configService.saveManagedStartSpawn(game, ParkourPoint.from(player.location))
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_start"))))
            }
            "setstartregion" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                configService.saveManagedStartRegion(game, region)
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_start_region"))))
            }
            "setfinish" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                configService.saveManagedFinishRegion(game, region)
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_saved_field", language.getMessage("parkour.editor_field_finish"))))
            }
            "addcheckpoint" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                val checkpointId = args.getOrNull(1)?.let(::normalizeCheckpointId) ?: nextCheckpointId(game)
                val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                configService.addManagedCheckpoint(game, checkpointId, region, ParkourPoint.fromBlock(player.location))
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_checkpoint_added", checkpointId)))
            }
            "removecheckpoint" -> {
                val player = sender as? Player
                val game = player?.let { currentManagedGame(it) } ?: return help(sender, label)
                val checkpointId = args.getOrNull(1)?.let(::normalizeCheckpointId) ?: return help(sender, label)
                val removed = configService.removeManagedCheckpoint(game, checkpointId)
                if (removed) syncManagedTemplate(game)
                sender.sendMessage(Component.text(language.getMessage(if (removed) "parkour.admin_checkpoint_removed" else "parkour.admin_checkpoint_missing", checkpointId)))
            }
            "addspeedbuff" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                val buffId = args.getOrNull(1) ?: nextBuffId(game)
                configService.addManagedSpeedBuff(game, buffId, ParkourPoint.fromBlock(player.location))
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("parkour.editor_buff_added", buffId)))
            }
            "removebuff" -> {
                val player = sender as? Player
                val game = player?.let { currentManagedGame(it) } ?: return help(sender, label)
                val buffId = args.getOrNull(1) ?: return help(sender, label)
                val removed = configService.removeManagedBuff(game, buffId)
                if (removed) syncManagedTemplate(game)
                sender.sendMessage(Component.text(language.getMessage(if (removed) "parkour.admin_buff_removed" else "parkour.admin_buff_missing", buffId)))
            }
            "preview" -> requirePlayer(sender) { player ->
                val region = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                visualBlockPoints(region, player.world, 96).forEach { packetService.showBlockGlow(player, it, 10) }
                player.sendMessage(Component.text(language.getMessage("parkour.admin_preview")))
            }
            "previewall" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                previewManagedRoute(player, game)
            }
            else -> help(sender, label)
        }
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> filter(args[0], listOf("help", "reload", "setlobby", "setstart", "setstartregion", "setfinish", "addcheckpoint", "removecheckpoint", "addspeedbuff", "removebuff", "preview", "previewall"))
            else -> emptyList()
        }
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

    private fun filter(prefix: String, values: List<String>): List<String> {
        val lower = prefix.lowercase()
        return values.filter { it.lowercase().startsWith(lower) }
    }
}
