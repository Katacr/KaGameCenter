package org.katacr.kaGameCenter.event

import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.block.Action
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.katacr.kaGameCenter.game.GameRoom

/** 在玩法允许玩家持物交互后，为模块商品和外部自定义物品提供稳定的使用扩展入口。 */
class GameItemUseEvent(
    val room: GameRoom,
    val player: Player,
    val itemId: String?,
    val item: ItemStack,
    val action: Action,
    val hand: EquipmentSlot,
    val clickedBlock: Block?,
    val clickedFace: BlockFace?
) : Event(), Cancellable {
    private var cancelled = false

    /** 为 true 时由监听器负责行为、反馈和物品消耗，玩法会跳过默认行为并取消原版交互。 */
    var handled: Boolean = false

    /** 返回本次物品使用是否已被外部监听器拒绝。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置拒绝状态；拒绝后玩法和原版都不会继续处理本次交互。 */
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
