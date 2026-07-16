package org.katacr.kaGameCenter.editor

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.event.GameMapEditSessionClosedEvent
import org.katacr.kaGameCenter.event.GameMapEditSessionStartedEvent
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.util.UUID
import java.util.Locale

class MapEditorService(
    private val worldService: TemporaryWorldService
) {
    private val sessions = linkedMapOf<String, EditSession>()
    private val playerSessions = linkedMapOf<UUID, String>()

    fun openEditor(player: Player, sessionId: String, templatePath: String, spawnResolver: (World) -> Location?): World? {
        leaveCurrentSession(player, restore = true)
        var created = false
        val session = sessions.getOrPut(sessionId) {
            val worldName = buildWorldName(sessionId)
            val world = worldService.createEditorWorldFromTemplate(templatePath, worldName) ?: return null
            created = true
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
        if (created) {
            Bukkit.getPluginManager().callEvent(
                GameMapEditSessionStartedEvent(session.id, world.name, player.uniqueId, player)
            )
        }
        return world
    }

    fun openEditorDirectory(player: Player, sessionId: String, templateDirectory: java.io.File, spawnResolver: (World) -> Location?): World? {
        leaveCurrentSession(player, restore = true)
        var created = false
        val session = sessions.getOrPut(sessionId) {
            val worldName = buildWorldName(sessionId)
            val world = worldService.createEditorWorldFromDirectory(templateDirectory, worldName) ?: return null
            created = true
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
        if (created) {
            Bukkit.getPluginManager().callEvent(
                GameMapEditSessionStartedEvent(session.id, world.name, player.uniqueId, player)
            )
        }
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
        val editorIds = session.editors.keys.toList()
        val saveSucceeded = if (save) {
            Bukkit.getWorld(session.worldName)?.let {
                if (session.templateDirectory != null) {
                    worldService.saveWorldToDirectory(it, session.templateDirectory)
                } else {
                    worldService.saveWorldToTemplate(it, session.templatePath)
                }
            } ?: false
        } else {
            true
        }
        editorIds.forEach(playerSessions::remove)
        editorIds.mapNotNull(Bukkit::getPlayer).forEach { player ->
            if (restoreEditors) {
                session.editors[player.uniqueId]?.restore(player)
            }
        }
        val worldCleanupSucceeded = worldService.unloadAndDelete(session.worldName)
        Bukkit.getPluginManager().callEvent(
            GameMapEditSessionClosedEvent(
                session.id,
                session.worldName,
                editorIds,
                save,
                saveSucceeded,
                restoreEditors,
                worldCleanupSucceeded
            )
        )
        return saveSucceeded && worldCleanupSucceeded
    }

    fun closeCurrentSession(player: Player, save: Boolean = true): Boolean {
        val sessionId = playerSessions[player.uniqueId] ?: return false
        return closeSession(sessionId, save = save, restoreEditors = true)
    }

    fun currentSessionId(player: Player): String? = playerSessions[player.uniqueId]

    /** 处理玩家离开编辑世界，恢复其编辑前状态并返回需要检查空载关闭的会话 ID。 */
    fun handleEditorWorldExit(player: Player, worldName: String, restoreLocation: Boolean): String? {
        val session = sessions.values.firstOrNull { it.worldName == worldName } ?: return null
        if (playerSessions[player.uniqueId] == session.id) {
            playerSessions.remove(player.uniqueId)
            val snapshot = session.editors.remove(player.uniqueId)
            snapshot?.restore(player, restoreLocation)
        }
        return session.id
    }

    /** 在编辑世界已无玩家时保存快照并关闭会话，避免无人编辑世界长期保持加载。 */
    fun closeSessionIfWorldEmpty(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        val world = Bukkit.getWorld(session.worldName)
        if (world != null && world.players.isNotEmpty()) return false
        return closeSession(sessionId, save = true, restoreEditors = false)
    }

    /** 保存并关闭指定模块的全部托管地图编辑会话，供模块安全卸载前清理。 */
    fun closeModuleSessions(moduleId: String, save: Boolean = true): ModuleEditSessionCloseResult {
        val prefix = "${moduleId.trim().lowercase(Locale.ROOT)}:"
        val sessionIds = sessions.keys.filter { it.lowercase(Locale.ROOT).startsWith(prefix) }
        val failures = sessionIds.filterNot { closeSession(it, save = save, restoreEditors = true) }
        return ModuleEditSessionCloseResult(sessionIds.size, failures)
    }

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
            restore(player, restoreLocation = true)
        }

        /** 恢复编辑前游戏状态；跨世界主动离开时可保留玩家的新位置。 */
        fun restore(player: Player, restoreLocation: Boolean) {
            player.gameMode = gameMode
            player.allowFlight = allowFlight
            player.isFlying = isFlying
            if (restoreLocation) {
                player.teleport(location)
            }
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

/** 描述模块卸载前地图编辑会话的关闭数量与失败会话 ID。 */
data class ModuleEditSessionCloseResult(
    val attempted: Int,
    val failedSessionIds: List<String>
) {
    val success: Boolean
        get() = failedSessionIds.isEmpty()
}
