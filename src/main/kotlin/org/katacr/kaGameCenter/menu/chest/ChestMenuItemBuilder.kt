package org.katacr.kaGameCenter.menu.chest

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionType
import org.katacr.kaGameCenter.display.IconTextParser

class ChestMenuItemBuilder(
    private val plugin: JavaPlugin
) {
    fun build(button: ConfigurationSection, variables: Map<String, String>): ItemStack? {
        val display = button.getConfigurationSection("display") ?: button
        val materialName = display.getString("material")
            ?: display.getString("mat")
            ?: display.getString("materials")
            ?: return null
        val material = Material.matchMaterial(replace(materialName, variables)) ?: Material.BARRIER
        val item = ItemStack(material, display.getInt("amount", 1).coerceIn(1, 99))
        val meta = item.itemMeta ?: return item

        display.getString("name")?.let {
            meta.displayName(IconTextParser.parse(replace(it, variables)))
        }
        val lore = display.getStringList("lore")
        if (lore.isNotEmpty()) {
            meta.lore(lore.map { IconTextParser.parse(replace(it, variables)) })
        }
        if (display.contains("custom_data")) {
            @Suppress("DEPRECATION")
            meta.setCustomModelData(display.getInt("custom_data"))
        }
        display.getString("item_model")?.let { applyItemModel(meta, replace(it, variables)) }
        if (display.getBoolean("enchanted", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
        if (meta is PotionMeta) {
            display.getString("potion-display")
                ?.let { parsePotionType(replace(it, variables)) }
                ?.let { meta.basePotionType = it }
            display.get("potion-color")
                ?.let { parsePotionColor(replace(it.toString(), variables)) }
                ?.let(meta::setColor)
        }
        item.itemMeta = meta
        return item
    }

    fun replace(text: String, variables: Map<String, String>): String {
        var output = text
        variables.forEach { (key, value) ->
            output = output.replace("{$key}", value)
        }
        return output
    }

    private fun applyItemModel(meta: org.bukkit.inventory.meta.ItemMeta, value: String) {
        val parts = value.split(":", limit = 2)
        if (parts.size != 2) return
        runCatching {
            val key = NamespacedKey(parts[0], parts[1])
            val method = meta.javaClass.methods.firstOrNull {
                it.name == "setItemModel" && it.parameterTypes.size == 1 && it.parameterTypes[0] == NamespacedKey::class.java
            } ?: meta.javaClass.methods.firstOrNull {
                it.name == "itemModel" && it.parameterTypes.size == 1 && it.parameterTypes[0] == NamespacedKey::class.java
            }
            method?.invoke(meta, key)
        }.onFailure {
            plugin.logger.fine("Unable to apply item_model $value: ${it.message}")
        }
    }

    /** 将参考药水 NBT 名称和旧 Bukkit 名称解析为当前服务端 PotionType。 */
    private fun parsePotionType(value: String): PotionType? {
        val normalized = value.substringAfter(':').trim().uppercase().replace('-', '_')
        val alias = when (normalized) {
            "SPEED" -> "SWIFTNESS"
            "SWIFTNESS" -> "SPEED"
            "JUMP" -> "LEAPING"
            "LEAPING" -> "JUMP"
            "INSTANT_HEAL" -> "HEALING"
            "HEALING" -> "INSTANT_HEAL"
            "INSTANT_DAMAGE" -> "HARMING"
            "HARMING" -> "INSTANT_DAMAGE"
            "REGEN" -> "REGENERATION"
            "REGENERATION" -> "REGEN"
            else -> null
        }
        return sequenceOf(normalized, alias)
            .filterNotNull()
            .mapNotNull { candidate -> PotionType.entries.firstOrNull { it.name == candidate } }
            .firstOrNull()
    }

    /** 将十进制、0x 或 # 前缀的 24 位颜色解析为 Bukkit Color。 */
    private fun parsePotionColor(value: String): Color? {
        val configured = value.trim()
        val rgb = when {
            configured.startsWith("#") -> configured.drop(1).toIntOrNull(16)
            configured.startsWith("0x", ignoreCase = true) -> configured.drop(2).toIntOrNull(16)
            else -> configured.toIntOrNull() ?: configured.toIntOrNull(16)
        } ?: return null
        return rgb.takeIf { it in 0..0xFFFFFF }?.let(Color::fromRGB)
    }
}
