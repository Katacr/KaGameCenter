package org.katacr.kaGameCenter.i18n

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStream

class ModuleLanguage(
    private val plugin: JavaPlugin,
    private val fallback: LanguageManager,
    private val moduleFolder: File,
    private val moduleResourcePath: String,
    private val resourceLoader: (String) -> InputStream? = { path -> plugin.getResource(path) }
) {
    private val fallbackLanguage = "en_US"
    private val messages = linkedMapOf<String, String>()
    private val fallbackMessages = linkedMapOf<String, String>()

    fun reload() {
        val langFolder = File(moduleFolder, "lang")
        if (!langFolder.exists()) langFolder.mkdirs()
        saveResourceIfMissing("zh_CN")
        saveResourceIfMissing("en_US")
        messages.clear()
        fallbackMessages.clear()
        loadFile(File(langFolder, "${fallback.getCurrentLanguage()}.yml"), messages)
        loadFile(File(langFolder, "$fallbackLanguage.yml"), fallbackMessages)
    }

    fun getMessage(key: String, vararg args: Any): String {
        var message = messages[key] ?: fallbackMessages[key] ?: fallback.getMessage(key)
        args.forEachIndexed { index, arg ->
            message = message.replace("{$index}", arg.toString())
        }
        return message
    }

    private fun saveResourceIfMissing(language: String) {
        val file = File(moduleFolder, "lang/$language.yml")
        if (file.exists()) return
        val resource = resourceLoader("$moduleResourcePath/lang/$language.yml") ?: return
        file.parentFile.mkdirs()
        resource.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
    }

    private fun loadFile(file: File, target: MutableMap<String, String>) {
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        config.getKeys(true).forEach { key ->
            if (config.isString(key)) target[key] = config.getString(key) ?: return@forEach
        }
    }
}
