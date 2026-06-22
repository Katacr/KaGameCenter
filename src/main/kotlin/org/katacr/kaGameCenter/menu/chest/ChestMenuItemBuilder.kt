package org.katacr.kaGameCenter.menu.chest

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
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
}
