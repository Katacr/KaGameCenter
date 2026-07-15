package org.katacr.kaGameCenter.menu.chest

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent

class ChestMenuListener(
    private val menuService: ChestMenuService
) : Listener {
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? ChestMenuHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        if (slot < 0 || slot >= event.inventory.size) return
        val clickKey = when {
            event.click == ClickType.DROP -> "drop"
            event.click.isLeftClick && event.isShiftClick -> "shift_left"
            event.click.isRightClick && event.isShiftClick -> "shift_right"
            event.click.isLeftClick -> "left"
            event.click.isRightClick -> "right"
            else -> return
        }
        menuService.handleClick(player, holder, slot, clickKey)
    }

    /** 最终保持虚拟箱子菜单点击取消，防止后续监听器把渲染图标放入玩家背包。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onClickComplete(event: InventoryClickEvent) {
        if (event.inventory.holder is ChestMenuHolder) event.isCancelled = true
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is ChestMenuHolder) {
            event.isCancelled = true
        }
    }

    /** 最终保持虚拟箱子菜单拖拽取消，防止后续监听器向菜单或背包写入物品。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onDragComplete(event: InventoryDragEvent) {
        if (event.inventory.holder is ChestMenuHolder) event.isCancelled = true
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        (event.inventory.holder as? ChestMenuHolder)?.stopUpdate()
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        (event.player.openInventory.topInventory.holder as? ChestMenuHolder)?.stopUpdate()
    }
}
