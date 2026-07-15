package org.katacr.kaGameCenter.data

import java.util.UUID

interface StatsRepository {
    val backendName: String

    fun init()

    fun loadAll(): List<PlayerGameStats>

    fun save(stats: PlayerGameStats)

    /** 加载各玩法注册的扩展计数指标。 */
    fun loadMetrics(): List<PlayerGameMetric>

    /** 保存一个玩家、玩法和指标组成的唯一计数。 */
    fun saveMetric(metric: PlayerGameMetric)

    fun close()
}
