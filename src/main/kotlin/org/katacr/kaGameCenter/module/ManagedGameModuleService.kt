package org.katacr.kaGameCenter.module

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.Bukkit
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

    fun loadedModuleIds(): List<String> = loadedModules.values.map { "${it.id}@${it.version}" }

    fun loadedModuleCount(): Int = loadedModules.size

    /** 返回可由重载命令选择的已加载或已配置模块 ID。 */
    fun reloadableModuleIds(): List<String> {
        return (loadedModules.keys + discoverConfiguredModules())
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    fun load() {
        modulesFolder.mkdirs()
        releaseBundledModuleConfigs()
        releaseJarModuleResources()
        val moduleIds = discoverConfiguredModules()
        if (moduleIds.isEmpty()) {
            plugin.logger.info("No managed game modules found in ${modulesFolder.absolutePath}")
            return
        }
        moduleIds.forEach { loadManagedModule(it) }
    }

    fun unload() {
        loadedModules.values.reversed().forEach { loaded ->
            unloadManagedModule(loaded)
        }
        loadedModules.clear()
    }

    /** 关闭目标模块全部房间和编辑会话后，卸载并重新加载当前配置的模块 JAR。 */
    fun reloadModule(moduleId: String): ManagedModuleReloadResult {
        check(Bukkit.isPrimaryThread()) { "Managed game modules must be reloaded on the Bukkit main thread" }
        releaseJarModuleResources()
        return reloadModuleInternal(sanitizeModuleId(moduleId))
    }

    /** 依次安全重载全部已加载或已配置模块，并为每个模块保留独立结果。 */
    fun reloadAllModules(): List<ManagedModuleReloadResult> {
        check(Bukkit.isPrimaryThread()) { "Managed game modules must be reloaded on the Bukkit main thread" }
        releaseJarModuleResources()
        return reloadableModuleIds().map(::reloadModuleInternal)
    }

    private fun reloadModuleInternal(moduleId: String): ManagedModuleReloadResult {
        if (moduleId.isBlank()) return ManagedModuleReloadResult.failure(moduleId, "invalid module id")
        val configFile = File(modulesFolder, "$moduleId/config.yml")
        val loaded = loadedModules[moduleId]
        if (!configFile.isFile && loaded == null) {
            return ManagedModuleReloadResult.failure(moduleId, "module config not found")
        }

        val roomIds = roomManager.listRooms()
            .filter { it.module.id.equals(moduleId, ignoreCase = true) }
            .map { it.id }
        roomIds.forEach(roomManager::closeRoom)
        val remainingRooms = roomManager.listRooms().filter { it.module.id.equals(moduleId, ignoreCase = true) }
        if (remainingRooms.isNotEmpty()) {
            return ManagedModuleReloadResult.failure(
                moduleId,
                "failed to close rooms: ${remainingRooms.joinToString { it.id }}",
                closedRooms = roomIds.size - remainingRooms.size
            )
        }

        val editorResult = mapEditorService.closeModuleSessions(moduleId, save = true)
        if (!editorResult.success) {
            return ManagedModuleReloadResult.failure(
                moduleId,
                "failed to save or close editor sessions: ${editorResult.failedSessionIds.joinToString()}",
                closedRooms = roomIds.size,
                closedEditorSessions = editorResult.attempted - editorResult.failedSessionIds.size
            )
        }

        if (loaded != null) {
            loadedModules.remove(moduleId)
            val unloadErrors = unloadManagedModule(loaded)
            if (unloadErrors.isNotEmpty()) {
                managedGameCatalog.load()
                return ManagedModuleReloadResult.failure(
                    moduleId,
                    unloadErrors.joinToString("; "),
                    closedRooms = roomIds.size,
                    closedEditorSessions = editorResult.attempted
                )
            }
        }

        val loadAttempt = loadManagedModule(moduleId)
        managedGameCatalog.load()
        return when {
            loadAttempt.loaded -> ManagedModuleReloadResult(
                success = true,
                moduleId = moduleId,
                active = true,
                version = loadAttempt.version,
                closedRooms = roomIds.size,
                closedEditorSessions = editorResult.attempted
            )
            loadAttempt.disabled -> ManagedModuleReloadResult(
                success = true,
                moduleId = moduleId,
                active = false,
                version = loadAttempt.version,
                closedRooms = roomIds.size,
                closedEditorSessions = editorResult.attempted
            )
            else -> ManagedModuleReloadResult.failure(
                moduleId,
                loadAttempt.detail ?: "module load failed",
                closedRooms = roomIds.size,
                closedEditorSessions = editorResult.attempted
            )
        }
    }

    private fun unloadManagedModule(loaded: LoadedGameModule): List<String> {
        val errors = mutableListOf<String>()
        runCatching { loaded.provider.onUnload() }
            .onFailure {
                plugin.logger.warning("Failed to unload game module ${loaded.id}: ${it.message}")
                errors += "provider unload failed: ${it.message}"
            }
        runCatching { loaded.context.cleanup() }
            .onFailure {
                plugin.logger.warning("Failed to clean game module ${loaded.id}: ${it.message}")
                errors += "context cleanup failed: ${it.message}"
            }
        runCatching { loaded.classLoader.close() }
            .onFailure {
                plugin.logger.warning("Failed to close game module classloader ${loaded.id}: ${it.message}")
                errors += "classloader close failed: ${it.message}"
            }
        return errors
    }

    private fun loadManagedModule(moduleId: String): ModuleLoadAttempt {
        if (loadedModules.containsKey(moduleId)) {
            return ModuleLoadAttempt(detail = "module is already loaded")
        }
        val dataFolder = File(modulesFolder, moduleId)
        val configFile = File(dataFolder, "config.yml")
        if (!configFile.isFile) return ModuleLoadAttempt(detail = "module config not found")
        val config = YamlConfiguration.loadConfiguration(configFile)
        if (!config.getBoolean("enabled", true)) {
            plugin.logger.info("Managed game module disabled: $moduleId")
            return ModuleLoadAttempt(disabled = true, version = config.getString("version"))
        }

        if (!config.getString("main", "jar").equals("jar", ignoreCase = true)) {
            plugin.logger.warning("Managed game module type is not supported: $moduleId main=${config.getString("main")}")
            return ModuleLoadAttempt(detail = "unsupported module type: ${config.getString("main")}")
        }

        val configuredJarPath = config.getString("jar", "../$moduleId.jar") ?: "../$moduleId.jar"
        val jarFile = resolveJarFile(dataFolder, moduleId, configuredJarPath)
        if (!jarFile.isFile) {
            plugin.logger.warning("Managed game module jar not found: ${jarFile.absolutePath}")
            return ModuleLoadAttempt(detail = "module jar not found: ${jarFile.absolutePath}")
        }
        val moduleVersion = readJarModuleVersion(jarFile, config.getString("version"))
        syncResolvedModuleMetadata(configFile, config, dataFolder, configuredJarPath, jarFile, moduleVersion)

        val entrypoint = config.getString("entrypoint")?.takeIf { it.isNotBlank() }
        if (entrypoint == null) {
            plugin.logger.warning("Managed game module entrypoint is empty: $moduleId")
            return ModuleLoadAttempt(detail = "module entrypoint is empty")
        }

        val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), plugin.javaClass.classLoader)
        var provider: GameModuleProvider? = null
        var context: GameModuleContext? = null
        var loadStarted = false
        try {
            val loadedProvider = classLoader.loadClass(entrypoint)
                .getDeclaredConstructor()
                .newInstance() as? GameModuleProvider
                ?: error("Entrypoint does not implement GameModuleProvider: $entrypoint")
            provider = loadedProvider
            val moduleContext = GameModuleContext(
                id = moduleId,
                version = moduleVersion,
                dataFolder = dataFolder,
                plugin = plugin,
                api = api,
                roomManager = roomManager,
                friendService = api.friendService,
                playerStatusDisplayService = api.playerStatusDisplayService,
                worldService = worldService,
                languageManager = languageManager,
                packetService = packetService,
                selectionService = selectionService,
                editorPointCaptureService = api.editorPointCaptureService,
                teamService = api.teamService,
                teamAssignmentService = api.teamAssignmentService,
                chatService = api.chatService,
                mapEditorService = mapEditorService,
                managedGameCatalog = managedGameCatalog,
                menuService = menuService,
                chestMenuService = api.chestMenuService,
                roomTaskService = api.roomTaskService,
                entityOwnershipService = api.entityOwnershipService,
                roomPresentationService = api.roomPresentationService,
                resultService = api.resultService,
                playerRuntimeStateService = api.playerRuntimeStateService,
                roomBroadcastService = api.roomBroadcastService,
                nametagService = api.nametagService,
                eliminationService = api.eliminationService,
                spectatorService = api.spectatorService,
                roomResourceScopeService = api.roomResourceScopeService,
                reconnectStateService = api.reconnectStateService,
                managedMapPointService = api.managedMapPointService,
                spawnAssignmentService = api.spawnAssignmentService,
                moduleAdminRegistry = moduleAdminCommands
            )
            context = moduleContext
            loadStarted = true
            loadedProvider.onLoad(moduleContext)
            loadedModules[moduleId] = LoadedGameModule(moduleId, moduleVersion, loadedProvider, moduleContext, classLoader)
            plugin.logger.info("Managed game module jar loaded: $moduleId v$moduleVersion (${jarFile.name})")
            return ModuleLoadAttempt(loaded = true, version = moduleVersion)
        } catch (error: Throwable) {
            if (loadStarted) {
                runCatching { provider?.onUnload() }
                    .onFailure { plugin.logger.warning("Failed to roll back game module provider $moduleId: ${it.message}") }
            }
            runCatching { context?.cleanup() }
                .onFailure { plugin.logger.warning("Failed to roll back game module context $moduleId: ${it.message}") }
            runCatching { classLoader.close() }
                .onFailure { plugin.logger.warning("Failed to close failed game module classloader $moduleId: ${it.message}") }
            plugin.logger.warning("Failed to load managed game module $moduleId: ${error.message}")
            error.printStackTrace()
            return ModuleLoadAttempt(detail = error.message ?: error.javaClass.simpleName, version = moduleVersion)
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
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name.lowercase(Locale.ROOT) })
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

    /** 解析模块 JAR；旧版 `<id>.jar` 配置优先迁移到最新修改的版本化产物。 */
    private fun resolveJarFile(dataFolder: File, moduleId: String, jarPath: String): File {
        val file = File(jarPath)
        val configured = if (file.isAbsolute) file else File(dataFolder, jarPath).canonicalFile
        val legacyName = "$moduleId.jar"
        val versionedPrefix = "$moduleId-"
        val supportsVersionFallback = configured.name.equals(legacyName, ignoreCase = true) ||
            configured.name.startsWith(versionedPrefix, ignoreCase = true)
        if (configured.isFile && !configured.name.equals(legacyName, ignoreCase = true)) return configured
        if (!supportsVersionFallback) return configured
        return configured.parentFile
            ?.listFiles { candidate ->
                candidate.isFile &&
                    candidate.extension.equals("jar", ignoreCase = true) &&
                    candidate.name.startsWith(versionedPrefix, ignoreCase = true)
            }
            ?.maxWithOrNull(compareBy<File> { it.lastModified() }.thenBy { it.name.lowercase(Locale.ROOT) })
            ?: configured
    }

    /** 从模块 JAR 内嵌配置读取构建版本，旧 JAR 缺少字段时回退到外部配置。 */
    private fun readJarModuleVersion(jarFile: File, configuredVersion: String?): String {
        val embeddedVersion = runCatching {
            JarFile(jarFile).use { jar ->
                val entry = jar.getJarEntry("config.yml") ?: return@use null
                jar.getInputStream(entry).use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use(YamlConfiguration::loadConfiguration)
                }.getString("version")
            }
        }.getOrNull()
        return embeddedVersion?.trim()?.takeIf { it.isNotBlank() }
            ?: configuredVersion?.trim()?.takeIf { it.isNotBlank() }
            ?: "unknown"
    }

    /** 在回退到版本化 JAR 后同步外部配置中的版本和实际相对路径。 */
    private fun syncResolvedModuleMetadata(
        configFile: File,
        config: YamlConfiguration,
        dataFolder: File,
        configuredJarPath: String,
        jarFile: File,
        moduleVersion: String
    ) {
        var changed = false
        if (config.getString("version") != moduleVersion) {
            config.set("version", moduleVersion)
            changed = true
        }
        val configuredJar = resolveConfiguredJar(dataFolder, configuredJarPath)
        if (configuredJar != jarFile.canonicalFile) {
            val dataPath = dataFolder.canonicalFile.toPath()
            val jarPath = jarFile.canonicalFile.toPath()
            val persistedJarPath = runCatching { dataPath.relativize(jarPath).joinToString("/") }
                .getOrElse { jarPath.toString().replace(File.separatorChar, '/') }
            config.set("jar", persistedJarPath)
            changed = true
        }
        if (!changed) return
        runCatching { config.save(configFile) }
            .onFailure { plugin.logger.warning("Failed to update managed module metadata ${configFile.absolutePath}: ${it.message}") }
    }

    /** 将外部配置中的绝对或相对 JAR 路径规范化为标准文件。 */
    private fun resolveConfiguredJar(dataFolder: File, jarPath: String): File {
        val file = File(jarPath)
        return (if (file.isAbsolute) file else File(dataFolder, jarPath)).canonicalFile
    }

    private fun sanitizeModuleId(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
    }

    private data class LoadedGameModule(
        val id: String,
        val version: String,
        val provider: GameModuleProvider,
        val context: GameModuleContext,
        val classLoader: URLClassLoader
    )

    private data class ModuleLoadAttempt(
        val loaded: Boolean = false,
        val disabled: Boolean = false,
        val version: String? = null,
        val detail: String? = null
    )
}

/** 描述一次托管小游戏模块重载的清理数量、激活状态和失败原因。 */
data class ManagedModuleReloadResult(
    val success: Boolean,
    val moduleId: String,
    val active: Boolean,
    val version: String?,
    val closedRooms: Int,
    val closedEditorSessions: Int,
    val detail: String? = null
) {
    companion object {
        /** 创建不会继续加载新模块实例的失败结果。 */
        fun failure(
            moduleId: String,
            detail: String,
            closedRooms: Int = 0,
            closedEditorSessions: Int = 0
        ): ManagedModuleReloadResult {
            return ManagedModuleReloadResult(
                success = false,
                moduleId = moduleId,
                active = false,
                version = null,
                closedRooms = closedRooms,
                closedEditorSessions = closedEditorSessions,
                detail = detail
            )
        }
    }
}
