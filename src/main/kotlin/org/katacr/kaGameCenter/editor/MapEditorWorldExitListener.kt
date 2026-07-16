package org.katacr.kaGameCenter.editor

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

/** 监听玩家离开地图编辑世界，并在世界空载后自动保存、关闭编辑会话。 */
class MapEditorWorldExitListener(
    private val plugin: JavaPlugin,
    private val mapEditorService: MapEditorService
) : Listener {
    private val pendingSessionChecks = linkedSetOf<String>()

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val sessionId = mapEditorService.handleEditorWorldExit(
            event.player,
            event.from.name,
            restoreLocation = false
        ) ?: return
        scheduleEmptyWorldCheck(sessionId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val sessionId = mapEditorService.handleEditorWorldExit(
            event.player,
            event.player.world.name,
            restoreLocation = true
        ) ?: return
        scheduleEmptyWorldCheck(sessionId)
    }

    /** 合并同一 tick 的重复退出事件，并在 Bukkit 完成玩家迁移后检查世界人数。 */
    private fun scheduleEmptyWorldCheck(sessionId: String) {
        if (!pendingSessionChecks.add(sessionId)) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            pendingSessionChecks.remove(sessionId)
            mapEditorService.closeSessionIfWorldEmpty(sessionId)
        })
    }
}
