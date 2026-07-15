package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在玩法生成投射物并完成基础配置后、提交默认生命周期前提供可取消扩展入口。 */
class GameProjectileLaunchedEvent(
    val room: GameRoom,
    val player: Player,
    val teamId: String?,
    val sourceId: String,
    val projectile: Projectile
) : Event(), Cancellable {
    private var cancelled = false

    /** 返回本次玩法投射物启动是否已被外部监听器取消。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置取消状态；取消后玩法移除投射物且不提交默认消耗和追踪。 */
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
