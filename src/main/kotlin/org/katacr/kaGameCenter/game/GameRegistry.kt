package org.katacr.kaGameCenter.game

class GameRegistry(private val gameManager: GameManager) {
    private val modules = linkedMapOf<String, GameModule>()

    fun register(module: GameModule) {
        modules[module.id.lowercase()] = module
        if (gameManager.get(module.id) == null) {
            gameManager.register(module.defaultDefinition())
        }
    }

    /** 仅注销当前仍由同一实例占用的模块，避免旧上下文移除后注册的替代实例。 */
    fun unregister(module: GameModule): Boolean {
        val key = module.id.lowercase()
        if (modules[key] !== module) return false
        modules.remove(key)
        gameManager.unregister(module.id)
        return true
    }

    fun get(id: String): GameModule? = modules[id.lowercase()]

    fun all(): Collection<GameModule> = modules.values
}
