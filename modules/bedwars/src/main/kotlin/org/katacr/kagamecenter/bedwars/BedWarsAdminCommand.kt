package org.katacr.kagamecenter.bedwars

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.command.ModuleAdminCommand
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.i18n.ModuleLanguage

/** 提供 BedWars 配置重载、竞技场列表和托管地图编辑快捷命令。 */
class BedWarsAdminCommand(
    private val configService: BedWarsConfigService,
    private val language: ModuleLanguage,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val editor: BedWarsManagedGameEditor
) : ModuleAdminCommand {
    override val name: String = "bedwars"

    /** 执行 BedWars 管理子命令，并把点位操作委托给现有托管编辑器。 */
    override fun execute(sender: CommandSender, label: String, args: Array<out String>) {
        when (args.getOrNull(0)?.lowercase()) {
            null, "help" -> help(sender, label)
            "reload" -> {
                val reloadFailure = runCatching {
                    configService.reload()
                    language.reload()
                }.exceptionOrNull()
                sender.sendMessage(Component.text(
                    if (reloadFailure == null) {
                        language.getMessage("bedwars.admin_reloaded")
                    } else {
                        language.getMessage(
                            "bedwars.admin_reload_failed",
                            reloadFailure.message ?: reloadFailure.javaClass.simpleName
                        )
                    }
                ))
            }
            "list", "arenalist" -> listGames(sender)
            "edit", "setup" -> openEditor(sender, label, args.getOrNull(1))
            "setlobby" -> runEditorAction(sender, label, "set-lobby")
            "setspectator" -> runEditorAction(sender, label, "set-spectator")
            "setvoidy" -> runEditorAction(sender, label, "set-void-y")
            "setmaxbuildy" -> runEditorAction(sender, label, "set-max-build-y")
            "save" -> runEditorAction(sender, label, "save-world")
            "close" -> runEditorAction(sender, label, "close-world")
            "validate" -> runEditorAction(sender, label, "validate")
            "preview" -> runEditorAction(sender, label, "preview")
            else -> help(sender, label)
        }
    }

    /** 补全管理动作及 edit/setup 接受的 BedWars 托管玩法 ID。 */
    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return COMMANDS.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && (args[0].equals("edit", true) || args[0].equals("setup", true))) {
            return bedWarsGames().map { it.globalId }.filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }

    /** 输出所有 BedWars 托管玩法及其启用、地图快照和校验状态。 */
    private fun listGames(sender: CommandSender) {
        val games = bedWarsGames()
        if (games.isEmpty()) {
            sender.sendMessage(Component.text(language.getMessage("bedwars.admin_list_empty")))
            return
        }
        sender.sendMessage(Component.text(language.getMessage("bedwars.admin_list_header", games.size)))
        games.forEach { game ->
            val errors = configService.readManagedGame(game).validationErrors().size
            sender.sendMessage(Component.text(language.getMessage(
                "bedwars.admin_list_entry",
                game.globalId,
                game.displayName,
                game.selectorGroup,
                game.enabled,
                game.hasPrivateSnapshot(),
                errors
            )))
        }
    }

    /** 为玩家打开指定 BedWars 托管玩法的配置编辑菜单。 */
    private fun openEditor(sender: CommandSender, label: String, gameId: String?) {
        val player = requirePlayer(sender) ?: return
        val game = gameId?.let(managedGameCatalog::get)
        if (game == null || !game.moduleId.equals("bedwars", ignoreCase = true) ||
            !managedGameCatalog.openEditor(player, game.globalId)
        ) {
            player.sendMessage(Component.text(language.getMessage("bedwars.admin_game_missing", gameId ?: "-")))
            help(player, label)
        }
    }

    /** 在当前 BedWars 私有编辑世界中复用托管编辑器的已校验动作。 */
    private fun runEditorAction(sender: CommandSender, label: String, action: String) {
        val player = requirePlayer(sender) ?: return
        val game = mapEditorService.currentSessionId(player)?.let(managedGameCatalog::get)
        if (game == null || !game.moduleId.equals("bedwars", ignoreCase = true)) {
            player.sendMessage(Component.text(language.getMessage("bedwars.admin_editor_required")))
            help(player, label)
            return
        }
        editor.handleAction(player, game, action, emptyMap())
    }

    /** 返回按全局 ID 排序的 BedWars 托管玩法。 */
    private fun bedWarsGames() = managedGameCatalog.all()
        .filter { it.moduleId.equals("bedwars", ignoreCase = true) }
        .sortedBy { it.globalId }

    /** 向玩家或控制台发送 BedWars 管理命令帮助。 */
    private fun help(sender: CommandSender, label: String) {
        sender.sendMessage(Component.text(language.getMessage("bedwars.admin_help", label)))
    }

    /** 要求命令发送者为玩家，并在不满足时发送通用提示。 */
    private fun requirePlayer(sender: CommandSender): Player? {
        return (sender as? Player) ?: run {
            sender.sendMessage(Component.text(language.getMessage("command.only_player_dialog")))
            null
        }
    }

    companion object {
        private val COMMANDS = listOf(
            "help", "reload", "list", "edit", "setlobby", "setspectator", "setvoidy",
            "setmaxbuildy", "save", "close", "validate", "preview"
        )
    }
}
