package org.katacr.kaGameCenter.event

import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在有效玩家点击玩法商店实体、默认界面打开前提供可取消扩展入口。 */
class GameShopOpenEvent(
    val room: GameRoom,
    val player: Player,
    val shopId: String,
    val teamId: String?,
    val shopEntity: Entity
) : Event(), Cancellable {
    private var cancelled = false

    /** 返回默认商店打开是否已被外部监听器取消。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置取消状态；取消后玩法不会打开默认商店界面。 */
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
