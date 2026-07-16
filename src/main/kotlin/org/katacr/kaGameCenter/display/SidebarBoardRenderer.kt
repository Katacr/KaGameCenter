package org.katacr.kaGameCenter.display

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.RenderType
import org.bukkit.scoreboard.Scoreboard
import org.katacr.kaGameCenter.event.GameSidebarRenderEvent

object SidebarBoardRenderer {
    fun show(
        player: Player,
        objectiveId: String,
        title: Component,
        lines: List<String>,
        maxLineLength: Int = 40,
        showHealthBelowName: Boolean = false,
        showHealthInPlayerList: Boolean = false,
        healthLabel: Component = Component.text("❤")
    ) {
        val renderEvent = GameSidebarRenderEvent(
            player,
            objectiveId,
            title,
            lines,
            maxLineLength,
            showHealthBelowName,
            showHealthInPlayerList,
            healthLabel
        )
        Bukkit.getPluginManager().callEvent(renderEvent)
        if (renderEvent.isCancelled) return
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective = scoreboard.registerNewObjective(
            objectiveId.sanitizeObjectiveId(),
            Criteria.DUMMY,
            renderEvent.title
        )
        objective.displaySlot = DisplaySlot.SIDEBAR
        val visibleLines = renderEvent.lines.take(15)
        renderLines(
            scoreboard,
            objective,
            visibleLines.map { IconTextParser.parse(it.take(renderEvent.maxLineLength.coerceIn(1, 128))) }
        )
        if (renderEvent.showHealthBelowName) {
            registerHealthObjective(scoreboard, objectiveId, "_health", DisplaySlot.BELOW_NAME, renderEvent.healthLabel)
        }
        if (renderEvent.showHealthInPlayerList) {
            registerHealthObjective(scoreboard, objectiveId, "_health_tab", DisplaySlot.PLAYER_LIST, renderEvent.healthLabel)
        }
        player.scoreboard = scoreboard
    }

    /** 使用 Adventure Component 渲染支持头像和物品图标的计分板行。 */
    @JvmOverloads
    fun showComponents(
        player: Player,
        objectiveId: String,
        title: Component,
        lines: List<Component>,
        showHealthBelowName: Boolean = false,
        showHealthInPlayerList: Boolean = false,
        healthLabel: Component = Component.text("❤")
    ) {
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective = scoreboard.registerNewObjective(
            objectiveId.sanitizeObjectiveId(),
            Criteria.DUMMY,
            title
        )
        objective.displaySlot = DisplaySlot.SIDEBAR
        renderLines(scoreboard, objective, lines.take(15))
        if (showHealthBelowName) {
            registerHealthObjective(scoreboard, objectiveId, "_health", DisplaySlot.BELOW_NAME, healthLabel)
        }
        if (showHealthInPlayerList) {
            registerHealthObjective(scoreboard, objectiveId, "_health_tab", DisplaySlot.PLAYER_LIST, healthLabel)
        }
        player.scoreboard = scoreboard
    }

    fun clear(player: Player) {
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }

    /** 仅在现有标题仍等于模块上一帧时原地推进，避免覆盖事件监听器的自定义标题。 */
    fun updateTitle(
        player: Player,
        objectiveId: String,
        previousTitle: Component,
        nextTitle: Component
    ): Boolean {
        val objective = player.scoreboard.getObjective(objectiveId.sanitizeObjectiveId()) ?: return false
        if (objective.displayName() != previousTitle) return false
        objective.displayName(nextTitle)
        return true
    }

    /** 注册由服务端自动同步玩家生命值的原生计分目标。 */
    private fun registerHealthObjective(
        scoreboard: Scoreboard,
        objectiveId: String,
        suffix: String,
        displaySlot: DisplaySlot,
        label: Component
    ) {
        scoreboard.registerNewObjective(
            objectiveId.sanitizeObjectiveId(suffix),
            Criteria.HEALTH,
            label,
            RenderType.HEARTS
        ).displaySlot = displaySlot
    }

    /** 使用不可见唯一 entry 和 Team 前缀承载完整 Component 行。 */
    private fun renderLines(scoreboard: Scoreboard, objective: org.bukkit.scoreboard.Objective, lines: List<Component>) {
        lines.forEachIndexed { index, line ->
            val entry = uniqueSuffix(index)
            val team = scoreboard.registerNewTeam("kgc_line_$index")
            team.prefix(line)
            team.addEntry(entry)
            objective.getScore(entry).score = lines.size - index
        }
    }

    private fun String.sanitizeObjectiveId(suffix: String = ""): String {
        val sanitized = filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "kgc_sidebar" }
        return sanitized.take((32767 - suffix.length).coerceAtLeast(1)) + suffix
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
