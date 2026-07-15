package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** 在通用队伍服务完成容量校验后、写入新队伍前提供可取消分配入口。 */
class GamePlayerTeamAssignEvent(
    val roomId: String,
    val player: Player,
    val previousTeamId: String?,
    val teamId: String
) : Event(), Cancellable {
    private var cancelled = false

    /** 返回本次队伍分配是否已被外部监听器拒绝。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置拒绝状态；取消后通用队伍服务不会修改当前分配。 */
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
