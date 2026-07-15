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

    /** 注销模块对应的默认游戏定义，避免模块卸载后仍可被旧 ID 查询。 */
    fun unregister(id: String) {
        definitions.remove(id.lowercase())
    }

    fun get(id: String): GameDefinition? = definitions[id.lowercase()]

    fun all(): Collection<GameDefinition> = definitions.values

    fun enabled(): Collection<GameDefinition> = definitions.values.filter { it.enabled }
}
