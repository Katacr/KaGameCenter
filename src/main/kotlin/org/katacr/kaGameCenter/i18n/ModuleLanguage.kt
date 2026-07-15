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
    private val messageLists = linkedMapOf<String, List<String>>()
    private val bundledMessages = linkedMapOf<String, String>()
    private val bundledMessageLists = linkedMapOf<String, List<String>>()
    private val fallbackMessages = linkedMapOf<String, String>()
    private val fallbackMessageLists = linkedMapOf<String, List<String>>()
    private val bundledFallbackMessages = linkedMapOf<String, String>()
    private val bundledFallbackMessageLists = linkedMapOf<String, List<String>>()

    fun reload() {
        val langFolder = File(moduleFolder, "lang")
        if (!langFolder.exists()) langFolder.mkdirs()
        saveResourceIfMissing("zh_CN")
        saveResourceIfMissing("en_US")
        messages.clear()
        messageLists.clear()
        bundledMessages.clear()
        bundledMessageLists.clear()
        fallbackMessages.clear()
        fallbackMessageLists.clear()
        bundledFallbackMessages.clear()
        bundledFallbackMessageLists.clear()
        val currentLanguage = fallback.getCurrentLanguage()
        loadFile(File(langFolder, "$currentLanguage.yml"), messages, messageLists)
        loadResource(currentLanguage, bundledMessages, bundledMessageLists)
        loadFile(File(langFolder, "$fallbackLanguage.yml"), fallbackMessages, fallbackMessageLists)
        loadResource(fallbackLanguage, bundledFallbackMessages, bundledFallbackMessageLists)
    }

    fun getMessage(key: String, vararg args: Any): String {
        var message = messages[key]
            ?: messageLists[key]?.joinToString("\n")
            ?: bundledMessages[key]
            ?: bundledMessageLists[key]?.joinToString("\n")
            ?: fallbackMessages[key]
            ?: fallbackMessageLists[key]?.joinToString("\n")
            ?: bundledFallbackMessages[key]
            ?: bundledFallbackMessageLists[key]?.joinToString("\n")
            ?: fallback.getMessage(key)
        args.forEachIndexed { index, arg ->
            message = message.replace("{$index}", arg.toString())
        }
        return message
    }

    /** 按与单行消息相同的优先级读取字符串列表，并兼容既有标量语言键。 */
    fun getMessageList(key: String, vararg args: Any): List<String> {
        val values = messageLists[key]
            ?: messages[key]?.let(::listOf)
            ?: bundledMessageLists[key]
            ?: bundledMessages[key]?.let(::listOf)
            ?: fallbackMessageLists[key]
            ?: fallbackMessages[key]?.let(::listOf)
            ?: bundledFallbackMessageLists[key]
            ?: bundledFallbackMessages[key]?.let(::listOf)
            ?: listOf(fallback.getMessage(key))
        return values.map { value ->
            var message = value
            args.forEachIndexed { index, arg ->
                message = message.replace("{$index}", arg.toString())
            }
            message
        }
    }

    private fun saveResourceIfMissing(language: String) {
        val file = File(moduleFolder, "lang/$language.yml")
        if (file.exists()) return
        val resource = resourceLoader(resourcePath(language)) ?: return
        file.parentFile.mkdirs()
        resource.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
    }

    /** 从模块 JAR 读取指定语言，作为不覆盖管理员文件的新增键回退。 */
    private fun loadResource(
        language: String,
        messagesTarget: MutableMap<String, String>,
        listsTarget: MutableMap<String, List<String>>
    ) {
        val resource = resourceLoader(resourcePath(language)) ?: return
        resource.use { input ->
            val config = YamlConfiguration.loadConfiguration(input.reader())
            copyMessages(config, messagesTarget, listsTarget)
        }
    }

    /** 规范化模块传入的资源目录，并避免重复拼接 lang/lang。 */
    private fun resourcePath(language: String): String {
        val directory = moduleResourcePath.trim().trim('/')
        return if (directory.isBlank()) "lang/$language.yml" else "$directory/$language.yml"
    }

    private fun loadFile(
        file: File,
        messagesTarget: MutableMap<String, String>,
        listsTarget: MutableMap<String, List<String>>
    ) {
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        copyMessages(config, messagesTarget, listsTarget)
    }

    /** 把 YAML 中的字符串和字符串列表叶节点复制到扁平语言映射。 */
    private fun copyMessages(
        config: YamlConfiguration,
        messagesTarget: MutableMap<String, String>,
        listsTarget: MutableMap<String, List<String>>
    ) {
        config.getKeys(true).forEach { key ->
            when {
                config.isString(key) -> messagesTarget[key] = config.getString(key) ?: return@forEach
                config.isList(key) -> listsTarget[key] = config.getStringList(key)
            }
        }
    }
}
