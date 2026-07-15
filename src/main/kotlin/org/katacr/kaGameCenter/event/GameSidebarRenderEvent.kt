package org.katacr.kaGameCenter.event

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** 在通用模块 Sidebar 创建并替换玩家计分板前提供完整可修改渲染参数。 */
class GameSidebarRenderEvent(
    val player: Player,
    val objectiveId: String,
    var title: Component,
    lines: List<String>,
    maxLineLength: Int,
    showHealthBelowName: Boolean,
    showHealthInPlayerList: Boolean,
    var healthLabel: Component
) : Event(), Cancellable {
    private var cancelled = false

    val lines: MutableList<String> = lines.toMutableList()
    var maxLineLength: Int = maxLineLength
    var showHealthBelowName: Boolean = showHealthBelowName
    var showHealthInPlayerList: Boolean = showHealthInPlayerList

    /** 返回本次 Sidebar 渲染是否已被外部监听器取消。 */
    override fun isCancelled(): Boolean = cancelled

    /** 设置取消状态；取消后保留玩家当前计分板且不创建默认 Sidebar。 */
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
