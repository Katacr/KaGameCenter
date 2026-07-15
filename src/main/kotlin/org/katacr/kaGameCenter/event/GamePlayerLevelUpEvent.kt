package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom
import java.util.UUID

/** 在玩法经验一次或连续跨越等级阈值后发布最终等级快照。 */
class GamePlayerLevelUpEvent(
    val room: GameRoom,
    val playerId: UUID,
    val player: Player?,
    val sourceId: String,
    val previousLevel: Int,
    val newLevel: Int,
    val levelExperience: Int,
    val nextLevelExperience: Int
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
