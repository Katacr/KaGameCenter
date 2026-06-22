package org.katacr.kaGameCenter.display

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material

object IconTextParser {
    private val miniMessage = MiniMessage.miniMessage()
    private val legacy = LegacyComponentSerializer.legacyAmpersand()
    private val hexPattern = Regex("&#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?")
    private val itemPattern = Regex("&item:\\[([^\\]]+)]")
    private val miniMessagePattern = Regex("<[a-z_]+(?:[:][^>]*)?>", RegexOption.IGNORE_CASE)
    private val legacyPattern = Regex("[&§][0-9a-fA-FlmnoOrkLKMNO]")

    private val legacyToMiniMessage = mapOf(
        "&0" to "<black>", "§0" to "<black>",
        "&1" to "<dark_blue>", "§1" to "<dark_blue>",
        "&2" to "<dark_green>", "§2" to "<dark_green>",
        "&3" to "<dark_aqua>", "§3" to "<dark_aqua>",
        "&4" to "<dark_red>", "§4" to "<dark_red>",
        "&5" to "<dark_purple>", "§5" to "<dark_purple>",
        "&6" to "<gold>", "§6" to "<gold>",
        "&7" to "<gray>", "§7" to "<gray>",
        "&8" to "<dark_gray>", "§8" to "<dark_gray>",
        "&9" to "<blue>", "§9" to "<blue>",
        "&a" to "<green>", "§a" to "<green>",
        "&b" to "<aqua>", "§b" to "<aqua>",
        "&c" to "<red>", "§c" to "<red>",
        "&d" to "<light_purple>", "§d" to "<light_purple>",
        "&e" to "<yellow>", "§e" to "<yellow>",
        "&f" to "<white>", "§f" to "<white>",
        "&k" to "<obfuscated>", "§k" to "<obfuscated>",
        "&l" to "<bold>", "§l" to "<bold>",
        "&m" to "<strikethrough>", "§m" to "<strikethrough>",
        "&n" to "<underline>", "§n" to "<underline>",
        "&o" to "<italic>", "§o" to "<italic>",
        "&r" to "<reset>", "§r" to "<reset>",
        "&A" to "<green>", "§A" to "<green>",
        "&B" to "<aqua>", "§B" to "<aqua>",
        "&C" to "<red>", "§C" to "<red>",
        "&D" to "<light_purple>", "§D" to "<light_purple>",
        "&E" to "<yellow>", "§E" to "<yellow>",
        "&F" to "<white>", "§F" to "<white>",
        "&K" to "<obfuscated>", "§K" to "<obfuscated>",
        "&L" to "<bold>", "§L" to "<bold>",
        "&M" to "<strikethrough>", "§M" to "<strikethrough>",
        "&N" to "<underline>", "§N" to "<underline>",
        "&O" to "<italic>", "§O" to "<italic>",
        "&R" to "<reset>", "§R" to "<reset>"
    )

    fun parse(text: String?): Component {
        if (text.isNullOrEmpty()) return Component.empty()
        var converted = text.replace(hexPattern) { "<color:#${it.groupValues[1].uppercase()}>" }
        converted = converted.replace(itemPattern) { match ->
            materialSpriteTag(match.groupValues[1]) ?: match.value
        }
        if (!converted.contains(miniMessagePattern)) return legacy.deserialize(converted)
        if (converted.contains(legacyPattern)) {
            legacyToMiniMessage.forEach { (from, to) -> converted = converted.replace(from, to) }
        }
        return miniMessage.deserialize(converted)
    }

    private fun materialSpriteTag(materialName: String): String? {
        val normalized = materialName
            .uppercase()
            .replace("-", "_")
            .replace(" ", "_")
            .replace(Regex("_+"), "_")
            .trim()
        val material = Material.matchMaterial(normalized) ?: return null
        val key = material.key.value().lowercase()
        return if (material.isBlock) {
            "<sprite:blocks:block/$key>"
        } else {
            "<sprite:items:item/$key>"
        }
    }
}
