package org.katacr.kaGameCenter.data

import java.util.UUID

class PlayerStatsService {
    private val stats = linkedMapOf<StatsKey, PlayerGameStats>()

    fun get(playerId: UUID, gameId: String): PlayerGameStats {
        val key = StatsKey(playerId, gameId.lowercase())
        return stats.getOrPut(key) { PlayerGameStats(playerId, key.gameId) }
    }

    fun recordPlay(playerId: UUID, gameId: String) {
        get(playerId, gameId).plays++
    }

    fun recordWin(playerId: UUID, gameId: String, points: Int = 0) {
        get(playerId, gameId).apply {
            wins++
            this.points += points
        }
    }

    fun recordLoss(playerId: UUID, gameId: String) {
        get(playerId, gameId).losses++
    }

    fun recordKill(playerId: UUID, gameId: String, points: Int = 0) {
        get(playerId, gameId).apply {
            kills++
            this.points += points
        }
    }

    fun recordDeath(playerId: UUID, gameId: String) {
        get(playerId, gameId).deaths++
    }

    fun addPoints(playerId: UUID, gameId: String, amount: Int) {
        get(playerId, gameId).points += amount
    }

    fun all(): Collection<PlayerGameStats> = stats.values

    private data class StatsKey(
        val playerId: UUID,
        val gameId: String
    )
}
