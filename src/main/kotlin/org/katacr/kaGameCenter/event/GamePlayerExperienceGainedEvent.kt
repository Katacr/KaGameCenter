package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom
import java.util.UUID

/** 在玩法经验持久化并重新计算等级后发布来源与完整进度快照。 */
class GamePlayerExperienceGainedEvent(
    val room: GameRoom,
    val playerId: UUID,
    val player: Player?,
    val sourceId: String,
    val amount: Int,
    val totalExperience: Int,
    val level: Int,
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
