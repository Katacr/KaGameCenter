package org.katacr.kaGameCenter.spawn

import kotlin.random.Random

/** 保存一次参与者与不重复出生点的分配结果。 */
data class SpawnAssignment<Participant, Spawn>(
    val participant: Participant,
    val spawn: Spawn
)

/** 为任意参与者随机分配不重复出生点，不执行玩法传送或状态修改。 */
class SpawnAssignmentService {
    /** 出生点不足时返回 null，否则保持参与者顺序返回随机分配结果。 */
    fun <Participant, Spawn> assign(
        participants: List<Participant>,
        spawns: List<Spawn>,
        random: Random = Random.Default
    ): List<SpawnAssignment<Participant, Spawn>>? {
        if (spawns.size < participants.size) return null
        val shuffledSpawns = spawns.shuffled(random)
        return participants.mapIndexed { index, participant ->
            SpawnAssignment(participant, shuffledSpawns[index])
        }
    }
}
