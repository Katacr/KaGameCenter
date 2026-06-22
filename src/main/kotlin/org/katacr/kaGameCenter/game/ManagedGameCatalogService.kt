package org.katacr.kaGameCenter.game

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.io.File
import java.util.Locale

class ManagedGameCatalogService(
    private val plugin: JavaPlugin,
    private val registry: GameRegistry,
    private val worldService: TemporaryWorldService
) {
    private val games = linkedMapOf<String, ManagedGameConfig>()
    private val editors = linkedMapOf<String, ModuleGameEditor>()

    private val modulesFolder: File
        get() = File(plugin.dataFolder, "modules")

    fun load() {
        games.clear()
        modulesFolder.mkdirs()
        modulesFolder.listFiles { file -> file.isDirectory }?.sortedBy { it.name }?.forEach { moduleFolder ->
            val moduleId = moduleFolder.name
            val gamesFolder = File(moduleFolder, "games/game")
            if (!gamesFolder.exists()) {
                gamesFolder.mkdirs()
            }
            gamesFolder.listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    val config = YamlConfiguration.loadConfiguration(file)
                    val localId = config.getString("id")?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                    val globalId = "$moduleId:$localId"
                    val managed = ManagedGameConfig(
                        globalId = globalId,
                        localId = localId,
                        moduleId = moduleId,
                        displayName = config.getString("display-name", localId) ?: localId,
                        enabled = config.getBoolean("enabled", true),
                        sharedMapTemplate = config.getString("shared-map-template", config.getString("map-template", "$moduleId/default")) ?: "$moduleId/default",
                        runtimeMapTemplate = config.getString("runtime-map-template")?.takeIf { it.isNotBlank() },
                        minPlayers = config.getIntOrNull("min-players"),
                        maxPlayers = config.getIntOrNull("max-players"),
                        description = config.getString("description", "") ?: "",
                        file = file,
                        config = config
                    )
                    games[globalId.lowercase(Locale.ROOT)] = managed
                }
        }
    }

    fun all(): Collection<ManagedGameConfig> = games.values

    fun enabled(): Collection<ManagedGameConfig> = games.values.filter { it.enabled }

    fun get(globalId: String): ManagedGameConfig? = games[globalId.lowercase(Locale.ROOT)]

    fun registerEditor(editor: ModuleGameEditor) {
        editors[editor.moduleId.lowercase(Locale.ROOT)] = editor
    }

    fun getEditor(moduleId: String): ModuleGameEditor? = editors[moduleId.lowercase(Locale.ROOT)]

    fun createManagedGame(moduleId: String, sharedMapTemplate: String, displayName: String): ManagedGameConfig? {
        val module = registry.get(moduleId) ?: return null
        val moduleFolder = File(modulesFolder, moduleId).apply { mkdirs() }
        val gamesFolder = File(moduleFolder, "games/game").apply { mkdirs() }
        val localId = nextLocalId(gamesFolder, displayName)
        val file = File(gamesFolder, "$localId.yml")
        val privateMapFolder = File(moduleFolder, "games/map/$localId")
        val config = YamlConfiguration()
        config.set("id", localId)
        config.set("module", moduleId)
        config.set("display-name", displayName)
        config.set("enabled", true)
        config.set("shared-map-template", sharedMapTemplate)
        config.set("runtime-map-template", "modules/$moduleId/games/map/$localId")
        config.set("min-players", module.minPlayers)
        config.set("max-players", module.maxPlayers)
        config.set("description", "")
        getEditor(moduleId)?.populateDefaults(config, localId, displayName, sharedMapTemplate)
        config.save(file)
        if (!worldService.snapshotTemplateToDirectory(sharedMapTemplate, privateMapFolder)) {
            plugin.logger.warning("Failed to create private map snapshot for managed game $moduleId:$localId from $sharedMapTemplate")
        }
        load()
        return get("$moduleId:$localId")
    }

    fun save(game: ManagedGameConfig, mutate: (YamlConfiguration) -> Unit): ManagedGameConfig? {
        val config = YamlConfiguration.loadConfiguration(game.file)
        mutate(config)
        config.save(game.file)
        load()
        return get(game.globalId)
    }

    fun openEditor(player: Player, globalId: String): Boolean {
        val game = get(globalId) ?: return false
        val editor = getEditor(game.moduleId) ?: return false
        editor.openEditor(player, game)
        return true
    }

    fun handleEditorAction(player: Player, globalId: String, action: String, variables: Map<String, String>): Boolean {
        val game = get(globalId) ?: return false
        val editor = getEditor(game.moduleId) ?: return false
        return editor.handleAction(player, game, action, variables)
    }

    private fun nextLocalId(gamesFolder: File, displayName: String): String {
        val base = slugify(displayName).ifBlank { "game" }
        var candidate = base
        var index = 2
        while (File(gamesFolder, "$candidate.yml").exists()) {
            candidate = "$base-$index"
            index++
        }
        return candidate
    }

    private fun slugify(value: String): String {
        val safe = buildString {
            value.trim().forEach { char ->
                when {
                    char.isLetterOrDigit() -> append(char)
                    char == '_' || char == '-' -> append(char)
                    char.isWhitespace() -> append('-')
                    char == '。' || char == '.' -> append('-')
                    char == '：' || char == ':' -> append('-')
                    else -> Unit
                }
            }
        }
        return safe
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(48)
    }
}

private fun YamlConfiguration.getIntOrNull(path: String): Int? {
    return if (contains(path)) getInt(path) else null
}
