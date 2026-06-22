package org.katacr.kaGameCenter.game

import org.katacr.kaGameCenter.spectator.SpectatorPolicy

data class GameDefinition(
    val id: String,
    val displayName: String,
    val enabled: Boolean = true,
    val minPlayers: Int = 1,
    val maxPlayers: Int = 16,
    val defaultDurationSeconds: Int = 300,
    val prepareSeconds: Int = 10,
    val countdownSeconds: Int = 10,
    val mapTemplates: List<String> = emptyList(),
    val requiredPlugins: List<String> = emptyList(),
    val optionalPlugins: List<String> = emptyList(),
    val resourcePack: String? = null,
    val spectatorPolicy: SpectatorPolicy = SpectatorPolicy.DEFAULT,
    val description: String = ""
)
