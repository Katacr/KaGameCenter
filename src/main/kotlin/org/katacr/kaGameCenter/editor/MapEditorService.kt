package org.katacr.kaGameCenter.editor

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.util.UUID

class MapEditorService(
    private val worldService: TemporaryWorldService
) {
    private val sessions = linkedMapOf<String, EditSession>()
    private val playerSessions = linkedMapOf<UUID, String>()

    fun openEditor(player: Player, sessionId: String, templatePath: String, spawnResolver: (World) -> Location?): World? {
        leaveCurrentSession(player, restore = true)
        val session = sessions.getOrPut(sessionId) {
            val worldName = buildWorldName(sessionId)
            val world = worldService.createEditorWorldFromTemplate(templatePath, worldName) ?: return null
            EditSession(sessionId, templatePath, null, world.name)
        }
        val world = Bukkit.getWorld(session.worldName) ?: worldService.createEditorWorldFromTemplate(templatePath, session.worldName) ?: return null
        session.editors.putIfAbsent(player.uniqueId, EditorSnapshot.capture(player))
        playerSessions[player.uniqueId] = sessionId
        val target = spawnResolver(world) ?: world.spawnLocation
        player.gameMode = GameMode.CREATIVE
        player.allowFlight = true
        player.isFlying = false
        player.teleport(target)
        return world
    }

    fun openEditorDirectory(player: Player, sessionId: String, templateDirectory: java.io.File, spawnResolver: (World) -> Location?): World? {
        leaveCurrentSession(player, restore = true)
        val session = sessions.getOrPut(sessionId) {
            val worldName = buildWorldName(sessionId)
            val world = worldService.createEditorWorldFromDirectory(templateDirectory, worldName) ?: return null
            EditSession(sessionId, null, templateDirectory, world.name)
        }
        val world = Bukkit.getWorld(session.worldName) ?: worldService.createEditorWorldFromDirectory(templateDirectory, session.worldName) ?: return null
        session.editors.putIfAbsent(player.uniqueId, EditorSnapshot.capture(player))
        playerSessions[player.uniqueId] = sessionId
        val target = spawnResolver(world) ?: world.spawnLocation
        player.gameMode = GameMode.CREATIVE
        player.allowFlight = true
        player.isFlying = false
        player.teleport(target)
        return world
    }

    fun saveSession(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        val world = Bukkit.getWorld(session.worldName) ?: return false
        return when {
            session.templateDirectory != null -> worldService.saveWorldToDirectory(world, session.templateDirectory)
            else -> worldService.saveWorldToTemplate(world, session.templatePath)
        }
    }

    fun saveCurrentSession(player: Player): Boolean {
        val sessionId = playerSessions[player.uniqueId] ?: return false
        return saveSession(sessionId)
    }

    fun saveIfEditing(sessionId: String): Boolean {
        if (!sessions.containsKey(sessionId)) return false
        return saveSession(sessionId)
    }

    fun closeSession(sessionId: String, save: Boolean = true, restoreEditors: Boolean = true): Boolean {
        val session = sessions.remove(sessionId) ?: return false
        if (save) {
            Bukkit.getWorld(session.worldName)?.let {
                if (session.templateDirectory != null) {
                    worldService.saveWorldToDirectory(it, session.templateDirectory)
                } else {
                    worldService.saveWorldToTemplate(it, session.templatePath)
                }
            }
        }
        session.editors.keys.mapNotNull(Bukkit::getPlayer).forEach { player ->
            playerSessions.remove(player.uniqueId)
            if (restoreEditors) {
                session.editors[player.uniqueId]?.restore(player)
            }
        }
        return worldService.unloadAndDelete(session.worldName)
    }

    fun closeCurrentSession(player: Player, save: Boolean = true): Boolean {
        val sessionId = playerSessions[player.uniqueId] ?: return false
        return closeSession(sessionId, save = save, restoreEditors = true)
    }

    fun currentSessionId(player: Player): String? = playerSessions[player.uniqueId]

    fun shutdown(save: Boolean = true) {
        sessions.keys.toList().forEach { closeSession(it, save = save, restoreEditors = true) }
        sessions.clear()
        playerSessions.clear()
    }

    private fun leaveCurrentSession(player: Player, restore: Boolean) {
        val sessionId = playerSessions.remove(player.uniqueId) ?: return
        val session = sessions[sessionId] ?: return
        val snapshot = session.editors.remove(player.uniqueId)
        if (restore) {
            snapshot?.restore(player)
        }
    }

    private fun buildWorldName(sessionId: String): String {
        val sanitized = sessionId.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').take(40)
        return "kgc_edit_${sanitized.ifBlank { "map" }}"
    }

    private data class EditSession(
        val id: String,
        val templatePath: String?,
        val templateDirectory: java.io.File?,
        val worldName: String,
        val editors: MutableMap<UUID, EditorSnapshot> = linkedMapOf()
    )

    private data class EditorSnapshot(
        val location: Location,
        val gameMode: GameMode,
        val allowFlight: Boolean,
        val isFlying: Boolean
    ) {
        fun restore(player: Player) {
            player.gameMode = gameMode
            player.allowFlight = allowFlight
            player.isFlying = isFlying
            player.teleport(location)
        }

        companion object {
            fun capture(player: Player): EditorSnapshot {
                return EditorSnapshot(
                    location = player.location.clone(),
                    gameMode = player.gameMode,
                    allowFlight = player.allowFlight,
                    isFlying = player.isFlying
                )
            }
        }
    }
}
