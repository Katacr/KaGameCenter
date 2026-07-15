package org.katacr.kaGameCenter.data

import java.util.UUID

/** 保存一个玩家在指定玩法下的可扩展持久计数指标。 */
data class PlayerGameMetric(
    val playerId: UUID,
    val gameId: String,
    val metricId: String,
    var value: Int = 0
)
