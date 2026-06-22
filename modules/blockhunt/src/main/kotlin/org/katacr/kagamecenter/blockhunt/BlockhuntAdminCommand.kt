package org.katacr.kagamecenter.blockhunt

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

class BlockhuntAdminCommand(
    private val configService: BlockhuntConfigService,
    private val selectionService: SelectionService,
    private val packetService: PacketDispatchService,
    private val language: ModuleLanguage,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService
) : ModuleAdminCommand {
    override val name: String = "blockhunt"

    override fun execute(sender: CommandSender, label: String, args: Array<out String>) {
        when (args.getOrNull(0)?.lowercase()) {
            null, "help" -> help(sender, label)
            "reload" -> {
                configService.reload()
                language.reload()
                sender.sendMessage(Component.text(language.getMessage("blockhunt.admin_reloaded")))
            }
            "setlobby" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                configService.saveManagedLobby(game, BlockhuntPoint.from(player.location))
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("blockhunt.editor_saved_field", language.getMessage("blockhunt.editor_field_lobby"))))
            }
            "sethunter" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                configService.saveManagedHunterSpawn(game, BlockhuntPoint.from(player.location))
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("blockhunt.editor_saved_field", language.getMessage("blockhunt.editor_field_hunter_spawn"))))
            }
            "sethider" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                configService.saveManagedHiderSpawn(game, BlockhuntPoint.from(player.location))
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("blockhunt.editor_saved_field", language.getMessage("blockhunt.editor_field_hider_spawn"))))
            }
            "setregion" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                val selection = selectionService.getSelection(player) ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                configService.saveManagedPlayRegion(game, selection)
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("blockhunt.editor_saved_field", language.getMessage("blockhunt.editor_field_play_region"))))
            }
            "additem" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                val id = args.getOrNull(1)?.takeIf { it.isNotBlank() } ?: nextItemId(game)
                configService.addManagedItemSpawn(game, id, BlockhuntPoint.fromBlock(player.location))
                syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage("blockhunt.editor_item_added", id)))
            }
            "removeitem" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                val id = args.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@requirePlayer player.sendMessage(Component.text(language.getMessage("blockhunt.editor_item_id_missing")))
                val removed = configService.removeManagedItemSpawn(game, id)
                if (removed) syncManagedTemplate(game)
                player.sendMessage(Component.text(language.getMessage(if (removed) "blockhunt.editor_item_removed" else "blockhunt.editor_item_missing", id)))
            }
            "preview" -> requirePlayer(sender) { player ->
                val game = currentManagedGame(player) ?: return@requirePlayer help(sender, label)
                preview(player, game)
            }
            else -> help(sender, label)
        }
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> filter(args[0], listOf("help", "reload", "setlobby", "sethunter", "sethider", "setregion", "additem", "removeitem", "preview"))
            else -> emptyList()
        }
    }

    private fun currentManagedGame(player: Player): ManagedGameConfig? {
        val sessionId = mapEditorService.currentSessionId(player) ?: return null
        return managedGameCatalog.get(sessionId)
    }

    private fun nextItemId(game: ManagedGameConfig): String {
        return "item_${configService.readManagedGame(game).itemSpawns.size + 1}"
    }

    private fun syncManagedTemplate(game: ManagedGameConfig) {
        mapEditorService.saveIfEditing(game.globalId)
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

    private fun requirePlayer(sender: CommandSender, action: (Player) -> Unit) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(Component.text("Only players can use this command."))
            return
        }
        action(player)
    }

    private fun help(sender: CommandSender, label: String) {
        sender.sendMessage(Component.text(language.getMessage("blockhunt.admin_help", label)))
    }

    private fun filter(input: String, values: List<String>): List<String> {
        return values.filter { it.startsWith(input, ignoreCase = true) }
    }
}
