package org.katacr.kaGameCenter.event

import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import org.katacr.kaGameCenter.game.GameRoom

/** 在有效玩家拾取玩法生成器的地面资源前提供可取消扩展入口。 */
class GameResourceCollectEvent(
    val room: GameRoom,
    val player: Player,
    val item: Item
) : Event(), Cancellable {
    private var cancelled = false

    /** 返回拾取事件当前使用的实时物品堆。 */
    val itemStack: ItemStack
        get() = item.itemStack

    /** 返回本次资源拾取是否已被外部监听器取消。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置取消状态；取消后资源实体保留在世界中。 */
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
