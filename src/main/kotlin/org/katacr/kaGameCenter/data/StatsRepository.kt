package org.katacr.kaGameCenter.data

import java.util.UUID

interface StatsRepository {
    val backendName: String

    fun init()

    fun loadAll(): List<PlayerGameStats>

    fun save(stats: PlayerGameStats)

    fun close()
}
