package org.katacr.kaGameCenter.event

import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在玩法自动结构方块完成放置和房间追踪后发布来源与归属上下文。 */
class GameStructureBlockPlacedEvent(
    val room: GameRoom,
    val player: Player?,
    val teamId: String?,
    val sourceId: String,
    val block: Block
) : Event() {
    /** 返回本事件的 Bukkit 处理器列表。 */
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** 返回 Bukkit 注册监听器所需的静态处理器列表。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
