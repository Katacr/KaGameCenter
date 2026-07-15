package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** 在通用房间列表完成打开后发布玩家、模块/玩法及可选分组筛选上下文。 */
class GameRoomsMenuOpenedEvent(
    val player: Player,
    val gameId: String?,
    val group: String?
) : Event() {
    /** 保留未携带分组的旧构造签名。 */
    constructor(player: Player, gameId: String?) : this(player, gameId, null)

    /** 返回本事件的 Bukkit 处理器列表。 */
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** 返回 Bukkit 注册监听器所需的静态处理器列表。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
