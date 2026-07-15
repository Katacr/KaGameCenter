package org.katacr.kagamecenter.skywars

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.command.ModuleAdminCommand
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.map.ManagedMapPointService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.selection.SelectionService

/** 提供 SkyWars 托管地图字段的管理员快捷命令。 */
class SkyWarsAdminCommand(
    private val configService: SkyWarsConfigService,
    private val selectionService: SelectionService,
    private val packetService: PacketDispatchService,
    private val language: ModuleLanguage,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val mapPointService: ManagedMapPointService
) : ModuleAdminCommand {
    override val name: String = "skywars"

    override fun execute(sender: CommandSender, label: String, args: Array<out String>) {
        when (args.getOrNull(0)?.lowercase()) {
            null, "help" -> help(sender, label)
            "reload" -> {
                configService.reload()
                language.reload()
                sender.sendMessage(Component.text(language.getMessage("skywars.admin_reloaded")))
            }
            "setlobby" -> edit(sender, label) { player, game ->
                configService.saveManagedLobby(game, mapPointService.fromLocation(player.location))
                saved(player, game, "skywars.editor_field_lobby")
            }
            "setspectator" -> edit(sender, label) { player, game ->
                configService.saveManagedSpectatorSpawn(game, mapPointService.fromLocation(player.location))
                saved(player, game, "skywars.editor_field_spectator")
            }
            "setregion" -> edit(sender, label) { player, game ->
                val selection = selectionService.getSelection(player)
                    ?: return@edit player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                configService.saveManagedPlayRegion(game, selection)
                saved(player, game, "skywars.editor_field_play_region")
            }
            "setvoidy" -> edit(sender, label) { player, game ->
                configService.saveManagedVoidY(game, player.location.y)
                saved(player, game, "skywars.editor_field_void_y")
            }
            "setteamsize" -> edit(sender, label) { player, game ->
                val size = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 8)
                    ?: return@edit player.sendMessage(Component.text(language.getMessage("skywars.admin_team_size_invalid")))
                configService.saveManagedTeamSize(game, size)
                saved(player, game, "skywars.editor_field_team_size")
            }
            "addisland", "addspawn" -> edit(sender, label) { player, game ->
                val id = args.getOrNull(1)?.takeIf(String::isNotBlank)
                    ?: nextIslandId(configService.readManagedGame(game).islandSpawns.map { it.id })
                configService.addManagedIslandSpawn(game, id, mapPointService.fromLocation(player.location))
                sync(game)
                player.sendMessage(Component.text(language.getMessage("skywars.editor_island_added", id)))
            }
            "removeisland", "removespawn" -> edit(sender, label) { player, game ->
                val id = args.getOrNull(1) ?: return@edit missingId(player)
                val removed = configService.removeManagedIslandSpawn(game, id)
                if (removed) sync(game)
                player.sendMessage(Component.text(language.getMessage(if (removed) "skywars.editor_island_removed" else "skywars.editor_point_missing", id)))
            }
            "addchest" -> edit(sender, label) { player, game ->
                val configured = configService.readManagedGame(game)
                val id = args.getOrNull(1)?.takeIf(String::isNotBlank) ?: nextChestId(configured.chests.map { it.id })
                val tier = args.getOrNull(2)?.takeIf(configService.current().loot.tiers::containsKey) ?: "island"
                configService.addManagedChest(game, id, mapPointService.fromBlock(player.location), tier)
                sync(game)
                player.sendMessage(Component.text(language.getMessage("skywars.editor_chest_added", id, tier)))
            }
            "removechest" -> edit(sender, label) { player, game ->
                val id = args.getOrNull(1) ?: return@edit missingId(player)
                val removed = configService.removeManagedChest(game, id)
                if (removed) sync(game)
                player.sendMessage(Component.text(language.getMessage(if (removed) "skywars.editor_chest_removed" else "skywars.editor_point_missing", id)))
            }
            "preview" -> edit(sender, label, ::preview)
            else -> help(sender, label)
        }
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf(
                "help", "reload", "setlobby", "setspectator", "setregion", "setvoidy", "setteamsize",
                "addisland", "removeisland", "addchest", "removechest", "preview"
            ).filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 3 && args[0].equals("addchest", true)) {
            return configService.current().loot.tiers.keys.filter { it.startsWith(args[2], ignoreCase = true) }
        }
        return emptyList()
    }

    private fun edit(sender: CommandSender, label: String, action: (Player, ManagedGameConfig) -> Unit) {
        val player = sender as? Player ?: return sender.sendMessage(Component.text("Only players can use this command."))
        val game = mapEditorService.currentSessionId(player)?.let(managedGameCatalog::get) ?: return help(sender, label)
        action(player, game)
    }

    private fun saved(player: Player, game: ManagedGameConfig, fieldKey: String) {
        sync(game)
        player.sendMessage(Component.text(language.getMessage("skywars.editor_saved_field", language.getMessage(fieldKey))))
    }

    private fun sync(game: ManagedGameConfig) {
        mapEditorService.saveIfEditing(game.globalId)
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

    private fun nextIslandId(ids: List<String>): String = nextId("island", ids)

    private fun nextChestId(ids: List<String>): String = nextId("chest", ids)

    private fun nextId(prefix: String, ids: List<String>): String {
        var index = ids.size + 1
        while (ids.any { it.equals("${prefix}_$index", ignoreCase = true) }) index++
        return "${prefix}_$index"
    }

    private fun missingId(player: Player) {
        player.sendMessage(Component.text(language.getMessage("skywars.editor_point_id_missing")))
    }

    private fun help(sender: CommandSender, label: String) {
        sender.sendMessage(Component.text(language.getMessage("skywars.admin_help", label)))
    }
}
