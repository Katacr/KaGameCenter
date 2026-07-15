package org.katacr.kaGameCenter.data

import java.util.UUID
import java.util.Locale

class PlayerStatsService(
    private val repository: StatsRepository = MemoryStatsRepository()
) {
    private val stats = linkedMapOf<StatsKey, PlayerGameStats>()
    private val metrics = linkedMapOf<MetricKey, PlayerGameMetric>()

    val backendName: String
        get() = repository.backendName

    fun init() {
        repository.init()
        repository.loadAll().forEach {
            stats[StatsKey(it.playerId, it.gameId.lowercase())] = it
        }
        repository.loadMetrics().forEach {
            val key = MetricKey(it.playerId, it.gameId.lowercase(Locale.ROOT), normalizeMetricId(it.metricId))
            metrics[key] = it
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

    /** 返回指定玩法扩展指标；未记录时返回零。 */
    fun metric(playerId: UUID, gameId: String, metricId: String): Int {
        return metrics[metricKey(playerId, gameId, metricId)]?.value ?: 0
    }

    /** 增加指定玩法扩展指标并立即交由当前仓库持久化。 */
    fun addMetric(playerId: UUID, gameId: String, metricId: String, amount: Int = 1) {
        if (amount == 0) return
        val key = metricKey(playerId, gameId, metricId)
        val current = metrics.getOrPut(key) {
            PlayerGameMetric(playerId, key.gameId, key.metricId)
        }
        current.value = (current.value.toLong() + amount)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
        repository.saveMetric(current)
    }

    /** 返回当前已加载的全部玩法扩展指标。 */
    fun allMetrics(): Collection<PlayerGameMetric> = metrics.values

    private fun mutate(playerId: UUID, gameId: String, action: PlayerGameStats.() -> Unit) {
        val current = get(playerId, gameId)
        current.action()
        repository.save(current)
    }

    private data class StatsKey(
        val playerId: UUID,
        val gameId: String
    )

    private data class MetricKey(
        val playerId: UUID,
        val gameId: String,
        val metricId: String
    )

    /** 创建大小写无关且经过安全规范化的玩法指标键。 */
    private fun metricKey(playerId: UUID, gameId: String, metricId: String): MetricKey {
        return MetricKey(playerId, gameId.lowercase(Locale.ROOT), normalizeMetricId(metricId))
    }

    /** 把模块指标名限制为稳定、可安全入库的 64 字符标识。 */
    private fun normalizeMetricId(metricId: String): String {
        val normalized = metricId.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_.-]"), "-")
            .trim('-')
            .take(64)
        require(normalized.isNotBlank()) { "Metric id cannot be blank" }
        return normalized
    }
}
