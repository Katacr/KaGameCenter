package org.katacr.kaGameCenter.game

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class GameManager(private val plugin: JavaPlugin) {
    private val definitions = linkedMapOf<String, GameDefinition>()
    private val gameFile = File(plugin.dataFolder, "games.yml")

    fun load() {
        if (!gameFile.exists()) {
            plugin.saveResource("games.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(gameFile)
        definitions.clear()

        val gamesSection = config.getConfigurationSection("games") ?: return
        for (id in gamesSection.getKeys(false)) {
            val section = gamesSection.getConfigurationSection(id) ?: continue
            register(readDefinition(id, section))
        }
    }

    fun reload() {
        load()
    }

    fun register(definition: GameDefinition) {
        definitions[definition.id.lowercase()] = definition
    }

    fun get(id: String): GameDefinition? = definitions[id.lowercase()]

    fun all(): Collection<GameDefinition> = definitions.values

    fun enabled(): Collection<GameDefinition> = definitions.values.filter { it.enabled }

    private fun readDefinition(id: String, section: ConfigurationSection): GameDefinition {
        val maps = section.getStringList("maps")
        return GameDefinition(
            id = id.lowercase(),
            displayName = section.getString("display-name", id) ?: id,
            enabled = section.getBoolean("enabled", true),
            minPlayers = section.getInt("min-players", 1),
            maxPlayers = section.getInt("max-players", 16),
            defaultDurationSeconds = section.getInt("default-duration-seconds", 300),
            prepareSeconds = section.getInt("prepare-seconds", 10),
            countdownSeconds = section.getInt("countdown-seconds", 10),
            mapTemplates = maps,
            resourcePack = section.getString("resource-pack"),
            description = section.getString("description", "") ?: ""
        )
    }
}
