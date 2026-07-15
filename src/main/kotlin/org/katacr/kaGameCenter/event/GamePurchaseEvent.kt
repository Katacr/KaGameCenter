package org.katacr.kaGameCenter.event

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 区分玩法商店中的普通商品购买和队伍升级购买。 */
enum class GamePurchaseKind {
    ITEM,
    UPGRADE
}

/** 在玩法确认购买资格后、扣除货币前提供稳定的跨模块扩展入口。 */
class GamePurchaseEvent(
    val room: GameRoom,
    val player: Player,
    val kind: GamePurchaseKind,
    val productId: String,
    val productType: String,
    val teamId: String?,
    val currency: Material,
    val price: Int
) : Event(), Cancellable {
    private var cancelled = false

    /** 为 true 时仍正常扣款和反馈，但由监听器接管默认发放或升级行为。 */
    var handled: Boolean = false

    /** 返回购买是否已被外部监听器取消。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置取消状态；取消后模块不会扣除货币。 */
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
