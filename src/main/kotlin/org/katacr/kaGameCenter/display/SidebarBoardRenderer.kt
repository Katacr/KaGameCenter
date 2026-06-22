package org.katacr.kaGameCenter.display

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot

object SidebarBoardRenderer {
    fun show(
        player: Player,
        objectiveId: String,
        title: Component,
        lines: List<String>,
        maxLineLength: Int = 40
    ) {
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective = scoreboard.registerNewObjective(objectiveId.sanitizeObjectiveId(), Criteria.DUMMY, title)
        objective.displaySlot = DisplaySlot.SIDEBAR
        val visibleLines = lines.take(15)
        visibleLines.forEachIndexed { index, line ->
            val entry = "${line.take(maxLineLength.coerceIn(1, 128))}${uniqueSuffix(index)}"
            objective.getScore(entry).score = visibleLines.size - index
        }
        player.scoreboard = scoreboard
    }

    fun clear(player: Player) {
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }

    private fun String.sanitizeObjectiveId(): String {
        return filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "kgc_sidebar" }
            .take(32767)
    }

    private fun uniqueSuffix(index: Int): String {
        return when (index) {
            0 -> "§0"
            1 -> "§1"
            2 -> "§2"
            3 -> "§3"
            4 -> "§4"
            5 -> "§5"
            6 -> "§6"
            7 -> "§7"
            8 -> "§8"
            9 -> "§9"
            10 -> "§a"
            11 -> "§b"
            12 -> "§c"
            13 -> "§d"
            else -> "§e"
        }
    }
}
