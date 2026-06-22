package org.katacr.kaGameCenter.game

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

data class ManagedGameConfig(
    val globalId: String,
    val localId: String,
    val moduleId: String,
    val displayName: String,
    val enabled: Boolean,
    val sharedMapTemplate: String,
    val runtimeMapTemplate: String?,
    val minPlayers: Int?,
    val maxPlayers: Int?,
    val description: String,
    val file: File,
    val config: YamlConfiguration
) {
    val runtimeMapFolder: File
        get() = File(file.parentFile.parentFile, "map/$localId")

    fun effectiveMapTemplate(): String = runtimeMapTemplate?.takeIf { it.isNotBlank() } ?: sharedMapTemplate

    fun hasPrivateSnapshot(): Boolean = runtimeMapFolder.isDirectory
}
