package org.katacr.kaGameCenter.menu.chest

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

data class ChestMenuEntry(
    val variables: Map<String, String>,
    val display: ConfigurationSection? = null
)

interface ChestMenuDataSource {
    fun entries(player: Player, context: Map<String, String>): List<ChestMenuEntry>
}

class ChestMenuDataSourceRegistry {
    private val sources = linkedMapOf<String, ChestMenuDataSource>()

    fun register(type: String, source: ChestMenuDataSource) {
        sources[type.lowercase()] = source
    }

    fun unregister(type: String) {
        sources.remove(type.lowercase())
    }

    /** 仅注销当前仍由指定实例占用的数据源，避免旧模块上下文删除替代实例。 */
    fun unregister(type: String, source: ChestMenuDataSource): Boolean {
        val key = type.lowercase()
        if (sources[key] !== source) return false
        sources.remove(key)
        return true
    }

    fun get(type: String): ChestMenuDataSource? {
        return sources[type.lowercase()]
    }
}
