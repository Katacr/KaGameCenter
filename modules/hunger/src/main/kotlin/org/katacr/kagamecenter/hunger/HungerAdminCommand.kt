package org.katacr.kagamecenter.hunger

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

/** 提供经典 Hunger 托管地图点位的管理员快捷命令。 */
class HungerAdminCommand(
    private val configService: HungerConfigService,
    private val selectionService: SelectionService,
    private val packetService: PacketDispatchService,
    private val language: ModuleLanguage,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val mapPointService: ManagedMapPointService
) : ModuleAdminCommand {
    override val name: String = "hunger"

    override fun execute(sender: CommandSender, label: String, args: Array<out String>) {
        when (args.getOrNull(0)?.lowercase()) {
            null, "help" -> help(sender, label)
            "reload" -> {
                configService.reload()
                language.reload()
                sender.sendMessage(Component.text(language.getMessage("hunger.admin_reloaded")))
            }
            "setlobby" -> edit(sender, label) { player, game ->
                configService.saveManagedLobby(game, mapPointService.fromLocation(player.location))
                saved(player, game, "hunger.editor_field_lobby")
            }
            "setspectator" -> edit(sender, label) { player, game ->
                configService.saveManagedSpectatorSpawn(game, mapPointService.fromLocation(player.location))
                saved(player, game, "hunger.editor_field_spectator")
            }
            "setregion" -> edit(sender, label) { player, game ->
                val selection = selectionService.getSelection(player)
                    ?: return@edit player.sendMessage(Component.text(language.getMessage("selection.not_ready")))
                configService.saveManagedPlayRegion(game, selection)
                saved(player, game, "hunger.editor_field_play_region")
            }
            "setvoidy" -> edit(sender, label) { player, game ->
                configService.saveManagedVoidY(game, player.location.y)
                saved(player, game, "hunger.editor_field_void_y")
            }
            "addtribute", "addspawn" -> edit(sender, label) { player, game ->
                val id = args.getOrNull(1)?.takeIf(String::isNotBlank) ?: nextId("tribute", configService.readManagedGame(game).tributeSpawns)
                configService.addManagedTributeSpawn(game, id, mapPointService.fromLocation(player.location))
                sync(game)
                player.sendMessage(Component.text(language.getMessage("hunger.editor_tribute_added", id)))
            }
            "removetribute", "removespawn" -> edit(sender, label) { player, game ->
                removePoint(player, game, args.getOrNull(1), "tribute")
            }
            "adddeathmatch", "adddm" -> edit(sender, label) { player, game ->
                val id = args.getOrNull(1)?.takeIf(String::isNotBlank) ?: nextId("dm", configService.readManagedGame(game).deathmatchSpawns)
                configService.addManagedDeathmatchSpawn(game, id, mapPointService.fromLocation(player.location))
                sync(game)
                player.sendMessage(Component.text(language.getMessage("hunger.editor_deathmatch_added", id)))
            }
            "removedeathmatch", "removedm" -> edit(sender, label) { player, game ->
                removePoint(player, game, args.getOrNull(1), "deathmatch")
            }
            "addchest" -> edit(sender, label) { player, game ->
                val id = args.getOrNull(1)?.takeIf(String::isNotBlank) ?: nextId("chest", configService.readManagedGame(game).supplyChests)
                configService.addManagedSupplyChest(game, id, mapPointService.fromBlock(player.location))
                sync(game)
                player.sendMessage(Component.text(language.getMessage("hunger.editor_chest_added", id)))
            }
            "removechest" -> edit(sender, label) { player, game ->
                removePoint(player, game, args.getOrNull(1), "chest")
            }
            "preview" -> edit(sender, label) { player, game -> preview(player, game) }
            else -> help(sender, label)
        }
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size != 1) return emptyList()
        return listOf(
            "help", "reload", "setlobby", "setspectator", "setregion", "setvoidy",
            "addtribute", "removetribute", "adddeathmatch", "removedeathmatch",
            "addchest", "removechest", "preview"
        ).filter { it.startsWith(args[0], ignoreCase = true) }
    }

    private fun edit(sender: CommandSender, label: String, action: (Player, ManagedGameConfig) -> Unit) {
        val player = sender as? Player ?: return sender.sendMessage(Component.text("Only players can use this command."))
        val game = currentManagedGame(player) ?: return help(sender, label)
        action(player, game)
    }

    private fun currentManagedGame(player: Player): ManagedGameConfig? {
        return mapEditorService.currentSessionId(player)?.let(managedGameCatalog::get)
    }

    private fun saved(player: Player, game: ManagedGameConfig, fieldKey: String) {
        sync(game)
        player.sendMessage(Component.text(language.getMessage("hunger.editor_saved_field", language.getMessage(fieldKey))))
    }

    private fun removePoint(player: Player, game: ManagedGameConfig, input: String?, type: String) {
        val id = input?.takeIf(String::isNotBlank)
            ?: return player.sendMessage(Component.text(language.getMessage("hunger.editor_point_id_missing")))
        val removed = when (type) {
            "tribute" -> configService.removeManagedTributeSpawn(game, id)
            "deathmatch" -> configService.removeManagedDeathmatchSpawn(game, id)
            else -> configService.removeManagedSupplyChest(game, id)
        }
        if (removed) sync(game)
        val key = when {
            !removed -> "hunger.editor_point_missing"
            type == "tribute" -> "hunger.editor_tribute_removed"
            type == "deathmatch" -> "hunger.editor_deathmatch_removed"
            else -> "hunger.editor_chest_removed"
        }
        player.sendMessage(Component.text(language.getMessage(key, id)))
    }

    private fun nextId(prefix: String, points: List<HungerNamedPoint>): String {
        var index = points.size + 1
        while (points.any { it.id.equals("${prefix}_$index", ignoreCase = true) }) index++
        return "${prefix}_$index"
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
        configured.tributeSpawns.forEach { packetService.showBeaconBeam(player, it.point.toLocation(player.world), NamedTextColor.GREEN, 10) }
        configured.deathmatchSpawns.forEach { packetService.showBeaconBeam(player, it.point.toLocation(player.world), NamedTextColor.RED, 10) }
        configured.supplyChests.forEach { packetService.showBeaconBeam(player, it.point.toLocation(player.world), NamedTextColor.GOLD, 10) }
        listOfNotNull(configured.lobby, configured.spectatorSpawn)
            .forEach { packetService.showBeaconBeam(player, it.toLocation(player.world), NamedTextColor.AQUA, 10) }
        player.sendMessage(Component.text(language.getMessage("hunger.editor_previewed")))
    }

    private fun help(sender: CommandSender, label: String) {
        sender.sendMessage(Component.text(language.getMessage("hunger.admin_help", label)))
    }
}
