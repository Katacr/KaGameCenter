package org.katacr.kaGameCenter.data

import java.util.UUID

class PlayerStatsService(
    private val repository: StatsRepository = MemoryStatsRepository()
) {
    private val stats = linkedMapOf<StatsKey, PlayerGameStats>()

    val backendName: String
        get() = repository.backendName

    fun init() {
        repository.init()
        repository.loadAll().forEach {
            stats[StatsKey(it.playerId, it.gameId.lowercase())] = it
        }
    }

    fun close() {
        repository.close()
    }

    fun get(playerId: UUID, gameId: String): PlayerGameStats {
        val key = StatsKey(playerId, gameId.lowercase())
        return stats.getOrPut(key) { PlayerGameStats(playerId, key.gameId) }
    }

    fun recordPlay(playerId: UUID, gameId: String) {
        mutate(playerId, gameId) { plays++ }
    }

    fun recordWin(playerId: UUID, gameId: String, points: Int = 0) {
        mutate(playerId, gameId) {
            wins++
            this.points += points
        }
    }

    fun recordLoss(playerId: UUID, gameId: String) {
        mutate(playerId, gameId) { losses++ }
    }

    fun recordKill(playerId: UUID, gameId: String, points: Int = 0) {
        mutate(playerId, gameId) {
            kills++
            this.points += points
        }
    }

    fun recordDeath(playerId: UUID, gameId: String) {
        mutate(playerId, gameId) { deaths++ }
    }

    fun addPoints(playerId: UUID, gameId: String, amount: Int) {
        mutate(playerId, gameId) { points += amount }
    }

    fun all(): Collection<PlayerGameStats> = stats.values

    private fun mutate(playerId: UUID, gameId: String, action: PlayerGameStats.() -> Unit) {
        val current = get(playerId, gameId)
        current.action()
        repository.save(current)
    }

    private data class StatsKey(
        val playerId: UUID,
        val gameId: String
    )
}
