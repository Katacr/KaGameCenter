package org.katacr.kaGameCenter.menu.chest

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.display.IconTextParser

class ChestMenuActionRouter(
    private val plugin: JavaPlugin,
    private val menuService: ChestMenuService,
    private val gameCenterMenuService: GameCenterMenuService,
    private val itemBuilder: ChestMenuItemBuilder
) {
    fun execute(
        player: Player,
        holder: ChestMenuHolder,
        button: ConfigurationSection,
        clickKey: String,
        slotVariables: Map<String, String>
    ) {
        val actions = button.getConfigurationSection("actions")?.getList(clickKey) ?: return
        val variables = holder.context + slotVariables + mapOf(
            "menu.id" to holder.menuId,
            "menu.page" to holder.currentPage.toString(),
            "viewer.name" to player.name,
            "viewer.uuid" to player.uniqueId.toString()
        )
        var delay = 0L
        actions.forEach { node ->
            when (node) {
                is String -> {
                    val line = itemBuilder.replace(applyPlaceholders(player, node), variables)
                    if (line.startsWith("wait:", true)) {
                        delay += line.substringAfter(":").trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    } else {
                        schedule(player, holder, line, variables, delay)
                    }
                }
                is Map<*, *> -> {
                    val condition = node["condition"]?.toString()
                    val selected = if (checkCondition(player, itemBuilder.replace(condition.orEmpty(), variables))) {
                        node["actions"]
                    } else {
                        node["deny"]
                    }
                    val lines = (selected as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                    lines.forEach { raw ->
                        val line = itemBuilder.replace(applyPlaceholders(player, raw), variables)
                        if (line.startsWith("wait:", true)) {
                            delay += line.substringAfter(":").trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        } else {
                            schedule(player, holder, line, variables, delay)
                        }
                    }
                }
            }
        }
    }

    private fun schedule(
        player: Player,
        holder: ChestMenuHolder,
        line: String,
        variables: Map<String, String>,
        delay: Long
    ) {
        if (delay <= 0) {
            executeLine(player, holder, line, variables)
            return
        }
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            executeLine(player, holder, line, variables)
        }, delay)
    }

    private fun executeLine(player: Player, holder: ChestMenuHolder, rawLine: String, variables: Map<String, String>) {
        val line = rawLine.trim()
        if (line.isBlank()) return

        when {
            line.equals("close", true) -> player.closeInventory()
            line.equals("update", true) -> menuService.refresh(player, holder)
            line.equals("PAGE_NEXT", true) -> menuService.open(player, holder.menuId, holder.context, holder.currentPage + 1)
            line.equals("PAGE_PREV", true) -> menuService.open(player, holder.menuId, holder.context, (holder.currentPage - 1).coerceAtLeast(0))
            line.startsWith("open:", true) -> {
                val menuId = line.substringAfter(":").trim()
                if (menuId.isNotBlank()) menuService.open(player, menuId, variables, 0)
            }
            line.startsWith("tell:", true) || line.startsWith("message:", true) -> {
                player.sendMessage(IconTextParser.parse(line.substringAfter(":").trim()))
            }
            line.startsWith("sound:", true) -> playSound(player, line.substringAfter(":").trim())
            line.startsWith("command:", true) -> {
                val command = line.substringAfter(":").trim().removePrefix("/")
                if (command.isNotBlank()) player.performCommand(command)
            }
            line.startsWith("console:", true) -> {
                val command = line.substringAfter(":").trim().removePrefix("/")
                if (command.isNotBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            }
            line.startsWith("kgc:", true) -> gameCenterMenuService.handleAction(player, line, variables)
            else -> plugin.logger.fine("Unknown chest menu action: $line")
        }
    }

    private fun playSound(player: Player, payload: String) {
        val parts = payload.split(Regex("\\s+")).filter { it.isNotBlank() }
        val sound = parts.getOrNull(0)?.uppercase()?.let { runCatching { Sound.valueOf(it) }.getOrNull() } ?: return
        val volume = parts.getOrNull(1)?.toFloatOrNull() ?: 1.0f
        val pitch = parts.getOrNull(2)?.toFloatOrNull() ?: 1.0f
        player.playSound(player.location, sound, volume, pitch)
    }

    private fun checkCondition(player: Player, raw: String): Boolean {
        if (raw.isBlank()) return true
        val condition = applyPlaceholders(player, raw).trim()
        splitOutside(condition, "||").takeIf { it.size > 1 }?.let { parts ->
            return parts.any { checkCondition(player, it) }
        }
        splitOutside(condition, "&&").takeIf { it.size > 1 }?.let { parts ->
            return parts.all { checkCondition(player, it) }
        }
        return compare(condition)
    }

    private fun compare(condition: String): Boolean {
        val operators = listOf(">=", "<=", "==", "!=", ">", "<", "=")
        val operator = operators.firstOrNull { condition.contains(it) } ?: return condition.toBooleanStrictOrNull() ?: false
        val left = condition.substringBefore(operator).trim().trim('"', '\'')
        val right = condition.substringAfter(operator).trim().trim('"', '\'')
        val leftNumber = left.toDoubleOrNull()
        val rightNumber = right.toDoubleOrNull()
        if (leftNumber != null && rightNumber != null) {
            return when (operator) {
                ">=" -> leftNumber >= rightNumber
                "<=" -> leftNumber <= rightNumber
                ">" -> leftNumber > rightNumber
                "<" -> leftNumber < rightNumber
                "!=" -> leftNumber != rightNumber
                else -> leftNumber == rightNumber
            }
        }
        return when (operator) {
            "!=" -> !left.equals(right, true)
            else -> left.equals(right, true)
        }
    }

    private fun splitOutside(text: String, operator: String): List<String> {
        return text.split(operator).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun applyPlaceholders(player: Player, text: String): String {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text
        return runCatching {
            val api = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            val method = api.getMethod("setPlaceholders", Player::class.java, String::class.java)
            method.invoke(null, player, text) as? String ?: text
        }.getOrDefault(text)
    }
}
