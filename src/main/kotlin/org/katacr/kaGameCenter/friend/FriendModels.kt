package org.katacr.kaGameCenter.friend

import java.util.UUID

/** 保存一条无方向好友关系，两个 UUID 始终按字符串顺序归一化。 */
class Friendship private constructor(
    val firstPlayerId: UUID,
    val secondPlayerId: UUID
) {
    fun contains(playerId: UUID): Boolean = playerId == firstPlayerId || playerId == secondPlayerId

    fun other(playerId: UUID): UUID? = when (playerId) {
        firstPlayerId -> secondPlayerId
        secondPlayerId -> firstPlayerId
        else -> null
    }

    override fun equals(other: Any?): Boolean {
        return other is Friendship && firstPlayerId == other.firstPlayerId && secondPlayerId == other.secondPlayerId
    }

    override fun hashCode(): Int = 31 * firstPlayerId.hashCode() + secondPlayerId.hashCode()

    companion object {
        fun of(first: UUID, second: UUID): Friendship {
            require(first != second) { "A player cannot befriend itself" }
            return if (first.toString() <= second.toString()) Friendship(first, second) else Friendship(second, first)
        }
    }
}

/** 保存一条有方向且等待处理的好友申请。 */
data class FriendRequest(
    val senderId: UUID,
    val receiverId: UUID
)

/** 描述当前玩家与目标玩家之间的好友状态。 */
enum class FriendRelation {
    SELF,
    NONE,
    OUTGOING_REQUEST,
    INCOMING_REQUEST,
    FRIENDS
}

/** 描述好友关系修改的稳定结果，供命令、菜单和模块 API 统一处理。 */
enum class FriendOperationResult {
    SENT,
    ACCEPTED,
    DENIED,
    REMOVED,
    SELF,
    ALREADY_FRIENDS,
    REQUEST_ALREADY_SENT,
    INCOMING_REQUEST_EXISTS,
    REQUEST_NOT_FOUND,
    NOT_FRIENDS
}
