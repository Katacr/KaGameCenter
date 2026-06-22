package org.katacr.kaGameCenter.command

import org.bukkit.command.CommandSender

interface ModuleAdminCommand {
    val name: String

    fun execute(sender: CommandSender, label: String, args: Array<out String>)

    fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> = emptyList()
}
