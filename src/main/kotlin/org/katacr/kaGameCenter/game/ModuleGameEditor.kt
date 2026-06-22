package org.katacr.kaGameCenter.game

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player

interface ModuleGameEditor {
    val moduleId: String

    fun populateDefaults(
        config: YamlConfiguration,
        localId: String,
        displayName: String,
        sharedMapTemplate: String
    ) = Unit

    fun openEditor(player: Player, game: ManagedGameConfig)

    fun handleAction(
        player: Player,
        game: ManagedGameConfig,
        action: String,
        variables: Map<String, String>
    ): Boolean = false
}
