package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在宽限期玩家重新附加房间显示和玩法状态前提供可取消入口。 */
class GamePlayerRoomReconnectEvent(
    val room: GameRoom,
    val player: Player
) : Event(), Cancellable {
    private var cancelled = false

    /** 玩法提供的重连复活倒计时；null 表示该玩法不支持或保持现状。 */
    var respawnDelayTicks: Long? = null

    /** 返回本次断线重连是否已被外部监听器拒绝。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置拒绝状态；取消后结束保留席位并按正常离房恢复玩家。 */
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
