package org.katacr.kaGameCenter.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在房间级资源生成规则升阶并完成防重登记后发布通知。 */
class GameResourceTierChangedEvent(
    val room: GameRoom,
    val resourceId: String,
    val previousTier: Int,
    val newTier: Int,
    val scheduledAtSeconds: Int,
    val elapsedSeconds: Int
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
