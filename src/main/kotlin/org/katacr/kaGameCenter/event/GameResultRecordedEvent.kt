package org.katacr.kaGameCenter.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom
import java.util.Collections
import java.util.UUID

/** 在整局胜负全部持久化成功后，发布不可变的房间赛果快照。 */
class GameResultRecordedEvent(
    val room: GameRoom,
    participants: Collection<UUID>,
    winners: Collection<UUID>,
    losers: Collection<UUID>,
    activeWinners: Collection<UUID>,
    val winnerGroupId: String?,
    val winPoints: Int
) : Event() {
    val participants: Set<UUID> = immutableCopy(participants)
    val winners: Set<UUID> = immutableCopy(winners)
    val losers: Set<UUID> = immutableCopy(losers)
    val activeWinners: Set<UUID> = immutableCopy(activeWinners)

    /** 返回本事件的 Bukkit 处理器列表。 */
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** 创建保留迭代顺序且不允许监听器修改的 UUID 集合。 */
        private fun immutableCopy(values: Collection<UUID>): Set<UUID> {
            return Collections.unmodifiableSet(LinkedHashSet(values))
        }

        /** 返回 Bukkit 注册监听器所需的静态处理器列表。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
