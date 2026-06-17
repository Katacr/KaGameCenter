package org.katacr.kaGameCenter.i18n

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStream

class LanguageManager(private val plugin: JavaPlugin) {

    private val defaultLanguage = "zh_CN"
    private val fallbackLanguage = "en_US"
    private var currentLanguage = defaultLanguage
    private val messages = mutableMapOf<String, String>()
    private val internalMessages = mutableMapOf<String, YamlConfiguration>()

    fun init() {
        currentLanguage = plugin.config.getString("language") ?: defaultLanguage
        saveDefaultMessages()
        loadInternalMessages()
        loadMessages(currentLanguage)
    }

    fun reload() {
        currentLanguage = plugin.config.getString("language") ?: defaultLanguage
        saveDefaultMessages()
        loadMessages(currentLanguage)
    }

    fun getCurrentLanguage(): String = currentLanguage

    fun getMessage(key: String, vararg args: Any): String {
        var message = messages[key] ?: getDefaultMessage(key) ?: run {
            plugin.logger.warning("not found lang key: $key")
            return key
        }

        args.forEachIndexed { index, arg ->
            message = message.replace("{$index}", arg.toString())
        }
        return message
    }

    private fun saveDefaultMessages() {
        val langFolder = File(plugin.dataFolder, "lang")
        if (!langFolder.exists()) {
            langFolder.mkdirs()
        }

        listOf("zh_CN", "en_US").forEach { lang ->
            val file = File(langFolder, "$lang.yml")
            if (!file.exists()) {
                plugin.saveResource("lang/$lang.yml", false)
            }
        }
    }

    private fun loadInternalMessages() {
        listOf("zh_CN", "en_US").forEach { lang ->
            val inputStream: InputStream? = plugin.getResource("lang/$lang.yml")
            if (inputStream != null) {
                inputStream.use {
                    internalMessages[lang] = YamlConfiguration.loadConfiguration(it.reader())
                }
            }
        }
    }

    private fun loadMessages(language: String) {
        val langFolder = File(plugin.dataFolder, "lang")
        val file = File(langFolder, "$language.yml")
        if (!file.exists()) {
            plugin.logger.warning("语言文件不存在: lang/$language.yml，使用默认语言")
            if (language != defaultLanguage) {
                loadMessages(defaultLanguage)
            }
            return
        }

        messages.clear()
        val config = YamlConfiguration.loadConfiguration(file)
        for (key in config.getKeys(true)) {
            if (config.isString(key)) {
                messages[key] = config.getString(key) ?: continue
            }
        }
    }

    private fun getDefaultMessage(key: String): String? {
        internalMessages[currentLanguage]?.getString(key)?.let { return it }
        if (currentLanguage != fallbackLanguage) {
            internalMessages[fallbackLanguage]?.getString(key)?.let { return it }
        }
        return null
    }
}
