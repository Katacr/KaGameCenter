package org.katacr.kagamecenter.blockhunt

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.command.ModuleAdminCommand
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.i18n.ModuleLanguage

class BlockhuntAdminCommand(
    private val configService: BlockhuntConfigService,
    private val language: ModuleLanguage,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val editor: BlockhuntManagedGameEditor
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
            "list" -> listGames(sender)
            "edit", "setup" -> openEditor(sender, label, args.getOrNull(1), openWorld = false)
            "openworld" -> openEditor(sender, label, args.getOrNull(1), openWorld = true)
            "setlobby" -> runEditorAction(sender, label, "set-lobby")
            "sethunter" -> runEditorAction(sender, label, "set-hunter-spawn")
            "sethider" -> runEditorAction(sender, label, "set-hider-spawn")
            "setregion" -> runEditorAction(sender, label, "set-play-region")
            "additem" -> runEditorAction(sender, label, "add-item-spawn")
            "removeitem" -> runEditorAction(sender, label, "remove-item-spawn", mapOf("item_id" to args.getOrNull(1).orEmpty()))
            "save" -> runEditorAction(sender, label, "save-world")
            "close" -> runEditorAction(sender, label, "close-world")
            "preview" -> runEditorAction(sender, label, "preview")
            else -> help(sender, label)
        }
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> filter(args[0], COMMANDS)
            2 -> when (args[0].lowercase()) {
                "edit", "setup", "openworld" -> filter(args[1], blockhuntGames().map { it.globalId })
                "removeitem" -> {
                    val game = (sender as? Player)?.let { mapEditorService.currentSessionId(it) }?.let(managedGameCatalog::get)
                    filter(args[1], game?.let { configService.readManagedGame(it).itemSpawns.map { spawn -> spawn.id } }.orEmpty())
                }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    /** 输出方块躲猫猫托管游戏列表及私有快照状态。 */
    private fun listGames(sender: CommandSender) {
        val games = blockhuntGames()
        if (games.isEmpty()) {
            sender.sendMessage(Component.text(language.getMessage("blockhunt.admin_list_empty")))
            return
        }
        sender.sendMessage(Component.text(language.getMessage("blockhunt.admin_list_header", games.size)))
        games.forEach { game ->
            sender.sendMessage(Component.text(language.getMessage(
                "blockhunt.admin_list_entry",
                game.globalId,
                game.displayName,
                game.enabled,
                game.hasPrivateSnapshot()
            )))
        }
    }

    /** 按全局或本地 ID 打开编辑菜单，或直接进入私有编辑世界。 */
    private fun openEditor(sender: CommandSender, label: String, gameId: String?, openWorld: Boolean) {
        val player = requirePlayer(sender) ?: return
        val game = gameId?.let { managedGameCatalog.get(it) ?: managedGameCatalog.get("blockhunt:$it") }
        if (game == null || !game.moduleId.equals("blockhunt", ignoreCase = true)) {
            player.sendMessage(Component.text(language.getMessage("blockhunt.admin_game_missing", gameId ?: "-")))
            help(player, label)
            return
        }
        if (openWorld) editor.openWorld(player, game) else editor.openEditor(player, game)
    }

    /** 将管理命令动作委托给当前编辑会话使用的同一 ModuleGameEditor。 */
    private fun runEditorAction(
        sender: CommandSender,
        label: String,
        action: String,
        variables: Map<String, String> = emptyMap()
    ) {
        val player = requirePlayer(sender) ?: return
        val game = mapEditorService.currentSessionId(player)?.let(managedGameCatalog::get)
        if (game == null || !game.moduleId.equals("blockhunt", ignoreCase = true)) {
            player.sendMessage(Component.text(language.getMessage("blockhunt.admin_editor_required")))
            help(player, label)
            return
        }
        editor.handleAction(player, game, action, variables)
    }

    /** 返回按全局 ID 排序的方块躲猫猫托管游戏。 */
    private fun blockhuntGames() = managedGameCatalog.all()
        .filter { it.moduleId.equals("blockhunt", ignoreCase = true) }
        .sortedBy { it.globalId }

    /** 要求命令发送者为玩家。 */
    private fun requirePlayer(sender: CommandSender): Player? {
        return (sender as? Player) ?: run {
            sender.sendMessage(Component.text(language.getMessage("command.only_player_dialog")))
            null
        }
    }

    private fun help(sender: CommandSender, label: String) {
        sender.sendMessage(Component.text(language.getMessage("blockhunt.admin_help", label)))
    }

    private fun filter(input: String, values: List<String>): List<String> {
        return values.filter { it.startsWith(input, ignoreCase = true) }
    }

    companion object {
        private val COMMANDS = listOf(
            "help", "reload", "list", "edit", "openworld", "setlobby", "sethunter",
            "sethider", "setregion", "additem", "removeitem", "save", "close", "preview"
        )
    }
}
