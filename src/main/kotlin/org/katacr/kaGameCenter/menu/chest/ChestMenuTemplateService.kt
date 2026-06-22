package org.katacr.kaGameCenter.menu.chest

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ChestMenuTemplateService(
    private val plugin: JavaPlugin
) {
    private val folder: File
        get() = File(plugin.dataFolder, "chest-menus")

    fun init() {
        DEFAULT_TEMPLATES.forEach(::ensureTemplate)
    }

    fun load(menuId: String): YamlConfiguration? {
        ensureTemplate(menuId)
        val file = File(folder, "$menuId.yml")
        if (!file.isFile) return null
        return YamlConfiguration.loadConfiguration(file)
    }

    fun layout(config: ConfigurationSection): List<String> {
        val key = config.getKeys(false).firstOrNull { it.equals("layout", true) || it.equals("layouts", true) }
        return key?.let(config::getStringList).orEmpty()
            .map { it.padEnd(9).take(9) }
            .take(6)
    }

    fun buttons(config: ConfigurationSection): ConfigurationSection? {
        val key = config.getKeys(false).firstOrNull { it.equals("button", true) || it.equals("buttons", true) }
        return key?.let(config::getConfigurationSection)
    }

    private fun ensureTemplate(menuId: String) {
        val target = File(folder, "$menuId.yml")
        if (target.exists()) return
        target.parentFile?.mkdirs()
        val resourcePath = "chest-menus/$menuId.yml"
        if (plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false)
        }
    }

    companion object {
        private val DEFAULT_TEMPLATES = listOf("main")
    }
}
