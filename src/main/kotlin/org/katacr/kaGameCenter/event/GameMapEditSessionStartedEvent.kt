package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** 在共享地图编辑会话首次创建并完成首位编辑者传送后发布启动快照。 */
class GameMapEditSessionStartedEvent(
    val sessionId: String,
    val worldName: String,
    val editorId: UUID,
    val editor: Player
) : Event() {
    /** 返回本事件的 Bukkit 处理器列表。 */
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** 返回 Bukkit 注册监听器所需的静态处理器列表。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
