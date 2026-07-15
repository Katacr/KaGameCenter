package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在玩法确认玩家进入或离开一个队伍基地范围后发布区域状态。 */
class GamePlayerBaseRegionChangedEvent(
    val room: GameRoom,
    val player: Player,
    val baseTeamId: String,
    val entered: Boolean
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
