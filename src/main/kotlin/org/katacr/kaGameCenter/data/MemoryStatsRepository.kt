package org.katacr.kaGameCenter.data

class MemoryStatsRepository : StatsRepository {
    override val backendName: String = "memory"

    override fun init() {}

    override fun loadAll(): List<PlayerGameStats> = emptyList()

    override fun save(stats: PlayerGameStats) {}

    override fun close() {}
}
