package org.katacr.kaGameCenter.module

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.api.GameCenterApi
import org.katacr.kaGameCenter.api.GameModuleContext
import org.katacr.kaGameCenter.api.GameModuleProvider
import org.katacr.kaGameCenter.command.ModuleAdminCommand
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.selection.SelectionService
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.io.File
import java.io.InputStreamReader
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import java.util.jar.JarFile

class ManagedGameModuleService(
    private val plugin: JavaPlugin,
    private val api: GameCenterApi,
    private val roomManager: GameRoomManager,
    private val worldService: TemporaryWorldService,
    private val languageManager: LanguageManager,
    private val packetService: PacketDispatchService,
    private val selectionService: SelectionService,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val menuService: GameCenterMenuService,
    private val moduleAdminCommands: MutableMap<String, ModuleAdminCommand>
) {
    private val modulesFolder: File
        get() = File(plugin.dataFolder, "modules")
    private val loadedModules = linkedMapOf<String, LoadedGameModule>()

    fun loadedModuleIds(): List<String> = loadedModules.keys.toList()

    fun loadedModuleCount(): Int = loadedModules.size

    fun load() {
        modulesFolder.mkdirs()
        releaseBundledModuleConfigs()
        releaseJarModuleResources()
        val moduleIds = discoverConfiguredModules()
        if (moduleIds.isEmpty()) {
            plugin.logger.info("No managed game modules found in ${modulesFolder.absolutePath}")
            return
        }
        moduleIds.forEach(::loadManagedModule)
    }

    fun unload() {
        loadedModules.values.reversed().forEach { loaded ->
            runCatching { loaded.provider.onUnload() }
                .onFailure { plugin.logger.warning("Failed to unload game module ${loaded.id}: ${it.message}") }
            runCatching { loaded.context.cleanup() }
                .onFailure { plugin.logger.warning("Failed to clean game module ${loaded.id}: ${it.message}") }
            runCatching { loaded.classLoader.close() }
                .onFailure { plugin.logger.warning("Failed to close game module classloader ${loaded.id}: ${it.message}") }
        }
        loadedModules.clear()
    }

    private fun loadManagedModule(moduleId: String) {
        val dataFolder = File(modulesFolder, moduleId)
        val config = YamlConfiguration.loadConfiguration(File(dataFolder, "config.yml"))
        if (!config.getBoolean("enabled", true)) {
            plugin.logger.info("Managed game module disabled: $moduleId")
            return
        }

        if (!config.getString("main", "jar").equals("jar", ignoreCase = true)) {
            plugin.logger.warning("Managed game module type is not supported: $moduleId main=${config.getString("main")}")
            return
        }

        val jarFile = resolveJarFile(dataFolder, config.getString("jar", "../$moduleId.jar") ?: "../$moduleId.jar")
        if (!jarFile.isFile) {
            plugin.logger.warning("Managed game module jar not found: ${jarFile.absolutePath}")
            return
        }

        val entrypoint = config.getString("entrypoint")?.takeIf { it.isNotBlank() }
        if (entrypoint == null) {
            plugin.logger.warning("Managed game module entrypoint is empty: $moduleId")
            return
        }

        val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), plugin.javaClass.classLoader)
        try {
            val provider = classLoader.loadClass(entrypoint)
                .getDeclaredConstructor()
                .newInstance() as? GameModuleProvider
                ?: error("Entrypoint does not implement GameModuleProvider: $entrypoint")
            val context = GameModuleContext(
                id = moduleId,
                dataFolder = dataFolder,
                plugin = plugin,
                api = api,
                roomManager = roomManager,
                worldService = worldService,
                languageManager = languageManager,
                packetService = packetService,
                selectionService = selectionService,
                teamService = api.teamService,
                teamAssignmentService = api.teamAssignmentService,
                chatService = api.chatService,
                mapEditorService = mapEditorService,
                managedGameCatalog = managedGameCatalog,
                menuService = menuService,
                chestMenuService = api.chestMenuService,
                roomTaskService = api.roomTaskService,
                entityOwnershipService = api.entityOwnershipService,
                resultService = api.resultService,
                playerRuntimeStateService = api.playerRuntimeStateService,
                roomBroadcastService = api.roomBroadcastService,
                moduleAdminRegistry = moduleAdminCommands
            )
            provider.onLoad(context)
            loadedModules[moduleId] = LoadedGameModule(moduleId, provider, context, classLoader)
            plugin.logger.info("Managed game module jar loaded: $moduleId")
        } catch (error: Throwable) {
            runCatching { classLoader.close() }
            plugin.logger.warning("Failed to load managed game module $moduleId: ${error.message}")
            error.printStackTrace()
        }
    }

    private fun releaseBundledModuleConfigs() {
        bundledModuleConfigResources().forEach { (moduleId, resourcePath) ->
            val target = File(modulesFolder, "$moduleId/config.yml")
            if (target.exists()) return@forEach
            target.parentFile.mkdirs()
            runCatching { plugin.saveResource(resourcePath, false) }
                .onSuccess { plugin.logger.info("Released bundled game module config: $moduleId") }
                .onFailure { plugin.logger.warning("Failed to release bundled game module config $moduleId: ${it.message}") }
        }
    }

    private fun releaseJarModuleResources() {
        modulesFolder.listFiles { file -> file.isFile && file.extension.equals("jar", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?.forEach { jarFile ->
                runCatching { releaseJarModuleResources(jarFile) }
                    .onFailure { plugin.logger.warning("Failed to inspect managed game module jar ${jarFile.name}: ${it.message}") }
            }
    }

    private fun releaseJarModuleResources(jarFile: File) {
        JarFile(jarFile).use { jar ->
            val configEntry = jar.getJarEntry("config.yml")
            val moduleId = readJarModuleId(jar, configEntry, jarFile)
            if (moduleId.isBlank()) {
                plugin.logger.warning("Managed game module jar config has no valid id: ${jarFile.absolutePath}")
                return
            }

            val moduleFolder = File(modulesFolder, moduleId)
            if (configEntry != null) {
                val target = File(moduleFolder, "config.yml")
                if (!target.exists()) {
                    copyJarEntry(jar, configEntry.name, target)
                    plugin.logger.info("Released game module config from jar: $moduleId")
                }
            }

            releaseJarDirectory(jar, "lang/", File(moduleFolder, "lang"), moduleId)
        }
    }

    private fun readJarModuleId(jar: JarFile, configEntry: java.util.jar.JarEntry?, jarFile: File): String {
        if (configEntry == null) return sanitizeModuleId(jarFile.nameWithoutExtension)
        val config = jar.getInputStream(configEntry).use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use(YamlConfiguration::loadConfiguration)
        }
        return config.getString("id")
            ?.takeIf { it.isNotBlank() }
            ?.let(::sanitizeModuleId)
            ?: sanitizeModuleId(jarFile.nameWithoutExtension)
    }

    private fun releaseJarDirectory(
        jar: JarFile,
        resourcePrefix: String,
        targetFolder: File,
        moduleId: String
    ) {
        var copied = 0
        jar.entries().asSequence()
            .filter { !it.isDirectory }
            .filter { it.name.startsWith(resourcePrefix) }
            .forEach { entry ->
                val relative = entry.name.removePrefix(resourcePrefix).trim('/')
                if (relative.isBlank()) return@forEach
                val target = File(targetFolder, relative)
                if (target.exists()) return@forEach
                copyJarEntry(jar, entry.name, target)
                copied++
            }
        if (copied > 0) {
            plugin.logger.info("Released $copied managed game module resources from $resourcePrefix for $moduleId")
        }
    }

    private fun copyJarEntry(jar: JarFile, entryName: String, target: File) {
        target.parentFile?.mkdirs()
        jar.getInputStream(jar.getJarEntry(entryName)).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun discoverConfiguredModules(): List<String> {
        return modulesFolder
            .listFiles { file -> file.isDirectory && File(file, "config.yml").isFile }
            ?.map { it.name }
            ?.map(::sanitizeModuleId)
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.sortedBy { it.lowercase(Locale.ROOT) }
            .orEmpty()
    }

    private fun bundledModuleConfigResources(): List<Pair<String, String>> {
        val prefix = "modules/"
        val suffix = "/config.yml"
        return plugin.javaClass.protectionDomain.codeSource?.location
            ?.let { File(it.toURI()) }
            ?.let { source ->
                when {
                    source.isFile -> bundledModuleConfigResourcesFromJar(source, prefix, suffix)
                    source.isDirectory -> bundledModuleConfigResourcesFromDirectory(source, prefix, suffix)
                    else -> emptyList()
                }
            }
            .orEmpty()
            .distinctBy { it.first.lowercase(Locale.ROOT) }
            .sortedBy { it.first.lowercase(Locale.ROOT) }
    }

    private fun bundledModuleConfigResourcesFromJar(source: File, prefix: String, suffix: String): List<Pair<String, String>> {
        return runCatching {
            JarFile(source).use { jar ->
                jar.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith(prefix) && it.endsWith(suffix) }
                    .mapNotNull { resourcePath ->
                        val moduleId = resourcePath.removePrefix(prefix).removeSuffix(suffix)
                        sanitizeModuleId(moduleId).takeIf { it.isNotBlank() }?.let { it to resourcePath }
                    }
                    .toList()
            }
        }.getOrElse { emptyList() }
    }

    private fun bundledModuleConfigResourcesFromDirectory(source: File, prefix: String, suffix: String): List<Pair<String, String>> {
        val root = source.toPath()
        val moduleRoot = root.resolve(prefix.removeSuffix("/"))
        if (!Files.isDirectory(moduleRoot)) return emptyList()
        return Files.walk(moduleRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .map { root.relativize(it).joinToString("/") }
                .filter { it.startsWith(prefix) && it.endsWith(suffix) }
                .map { resourcePath ->
                    val moduleId = resourcePath.removePrefix(prefix).removeSuffix(suffix)
                    sanitizeModuleId(moduleId).takeIf { it.isNotBlank() }?.let { it to resourcePath }
                }
                .filter { it != null }
                .map { it!! }
                .toList()
        }
    }

    private fun resolveJarFile(dataFolder: File, jarPath: String): File {
        val file = File(jarPath)
        return if (file.isAbsolute) file else File(dataFolder, jarPath).canonicalFile
    }

    private fun sanitizeModuleId(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
    }

    private data class LoadedGameModule(
        val id: String,
        val provider: GameModuleProvider,
        val context: GameModuleContext,
        val classLoader: URLClassLoader
    )
}
