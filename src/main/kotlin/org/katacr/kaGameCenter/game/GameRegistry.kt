package org.katacr.kaGameCenter.game

class GameRegistry(private val gameManager: GameManager) {
    private val modules = linkedMapOf<String, GameModule>()

    fun register(module: GameModule) {
        modules[module.id.lowercase()] = module
        if (gameManager.get(module.id) == null) {
            gameManager.register(module.defaultDefinition())
        }
    }

    fun get(id: String): GameModule? = modules[id.lowercase()]

    fun all(): Collection<GameModule> = modules.values
}
