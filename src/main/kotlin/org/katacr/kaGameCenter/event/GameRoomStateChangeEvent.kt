package org.katacr.kaGameCenter.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameState

/** 在房间状态真实变化后发布前后状态，为所有玩法提供统一的生命周期观察入口。 */
class GameRoomStateChangeEvent(
    val room: GameRoom,
    val previousState: GameState,
    val newState: GameState
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
