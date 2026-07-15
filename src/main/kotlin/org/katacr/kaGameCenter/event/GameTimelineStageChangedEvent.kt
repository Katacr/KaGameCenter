package org.katacr.kaGameCenter.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在玩法计算出的下一主时间线阶段发生变化后发布前后稳定 ID。 */
class GameTimelineStageChangedEvent(
    val room: GameRoom,
    val previousStageId: String?,
    val nextStageId: String?,
    val elapsedSeconds: Int,
    val nextStageAtSeconds: Int?
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
