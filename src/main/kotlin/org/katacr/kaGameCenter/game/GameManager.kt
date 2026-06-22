package org.katacr.kaGameCenter.game

import org.bukkit.plugin.java.JavaPlugin

class GameManager(private val plugin: JavaPlugin) {
    private val definitions = linkedMapOf<String, GameDefinition>()

    fun load() {
        definitions.clear()
        plugin.logger.fine("Game definitions are provided by loaded game modules.")
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
}
