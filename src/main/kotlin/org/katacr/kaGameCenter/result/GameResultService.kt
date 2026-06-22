package org.katacr.kaGameCenter.result

import org.katacr.kaGameCenter.data.PlayerStatsService
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

    fun recordWinLoss(room: GameRoom, participants: Collection<UUID>, winners: Set<UUID>, winPoints: Int = 0) {
        participants.forEach { playerId ->
            if (playerId in winners) {
                recordWin(room, playerId, winPoints)
            } else {
                recordLoss(room, playerId)
            }
        }
    }
}
