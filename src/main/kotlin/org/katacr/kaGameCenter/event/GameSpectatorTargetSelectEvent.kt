package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** 区分托管观战传送与原版第一人称镜头。 */
enum class GameSpectatorTargetMode {
    TELEPORT,
    FIRST_PERSON
}

/** 在观战服务传送或切换到目标玩家前提供可取消入口。 */
class GameSpectatorTargetSelectEvent(
    val roomId: String,
    val spectator: Player,
    val target: Player,
    val mode: GameSpectatorTargetMode
) : Event(), Cancellable {
    private var cancelled = false

    /** 返回本次目标选择是否已被外部监听器拒绝。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置拒绝状态；取消后保留原目标和镜头位置。 */
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
