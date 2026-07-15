package org.katacr.kaGameCenter.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在房间成员、资源和临时世界清理完成并进入关闭状态后发布生命周期快照。 */
class GameRoomClosedEvent(
    val room: GameRoom,
    val worldName: String?,
    val worldCleanupSucceeded: Boolean
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
