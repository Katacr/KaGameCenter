package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在玩家或外部观战者提交房间成员关系前提供可取消入口。 */
class GamePlayerRoomJoinEvent(
    val room: GameRoom,
    val player: Player,
    val spectator: Boolean
) : Event(), Cancellable {
    private var cancelled = false

    /** 返回本次房间加入是否已被外部监听器拒绝。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置拒绝状态；取消后不会改变玩家现有房间、快照或显示状态。 */
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    /** 返回本事件的 Bukkit 处理器列表。 */
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** 返回 Bukkit 注册监听器所需的静态处理器列表。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
