package org.katacr.kaGameCenter.result

import org.bukkit.Bukkit
import org.katacr.kaGameCenter.data.PlayerStatsService
import org.katacr.kaGameCenter.event.GameResultRecordedEvent
import org.katacr.kaGameCenter.game.GameRoom
import java.util.UUID

class GameResultService(
    private val statsService: PlayerStatsService
) {
    fun recordKill(room: GameRoom, killerId: UUID, victimId: UUID, points: Int = 0) {
        statsService.recordKill(killerId, room.module.id, points)
        statsService.recordDeath(victimId, room.module.id)
    }

    fun recordDeath(room: GameRoom, playerId: UUID) {
        statsService.recordDeath(playerId, room.module.id)
    }

    fun recordWin(room: GameRoom, playerId: UUID, points: Int = 0) {
        statsService.recordWin(playerId, room.module.id, points)
    }

    fun recordLoss(room: GameRoom, playerId: UUID) {
        statsService.recordLoss(playerId, room.module.id)
    }

    /** 按既有签名持久化整局胜负，并把全部获胜者视为仍有效的获胜成员。 */
    fun recordWinLoss(
        room: GameRoom,
        participants: Collection<UUID>,
        winners: Set<UUID>,
        winPoints: Int = 0
    ) {
        recordWinLoss(room, participants, winners, winPoints, winnerGroupId = null, activeWinners = winners)
    }

    /** 持久化带获胜组详情的整局胜负，并在全部写入成功后发布规范化的不可变赛果事件。 */
    fun recordWinLoss(
        room: GameRoom,
        participants: Collection<UUID>,
        winners: Set<UUID>,
        winPoints: Int,
        winnerGroupId: String?,
        activeWinners: Set<UUID>
    ) {
        val participantIds = participants.toCollection(linkedSetOf())
        val winnerIds = winners.filterTo(linkedSetOf()) { it in participantIds }
        val activeWinnerIds = activeWinners.filterTo(linkedSetOf()) { it in winnerIds }
        val loserIds = participantIds.filterTo(linkedSetOf()) { it !in winnerIds }
        participantIds.forEach { playerId ->
            if (playerId in winnerIds) {
                recordWin(room, playerId, winPoints)
            } else {
                recordLoss(room, playerId)
            }
        }
        Bukkit.getPluginManager().callEvent(
            GameResultRecordedEvent(
                room,
                participantIds,
                winnerIds,
                loserIds,
                activeWinnerIds,
                winnerGroupId,
                winPoints
            )
        )
    }

    /** 累加玩法专属持久指标，并自动使用当前房间的模块 ID。 */
    fun addMetric(room: GameRoom, playerId: UUID, metricId: String, amount: Int = 1) {
        statsService.addMetric(playerId, room.module.id, metricId, amount)
    }

    /** 查询当前房间玩法下玩家已持久化的专属指标。 */
    fun metric(room: GameRoom, playerId: UUID, metricId: String): Int {
        return statsService.metric(playerId, room.module.id, metricId)
    }
}
