package org.katacr.kaGameCenter.friend

/** 定义好友关系和待处理申请的持久化边界。 */
interface FriendRepository {
    fun init()
    fun loadFriendships(): Set<Friendship>
    fun loadRequests(): Set<FriendRequest>
    fun saveRequest(request: FriendRequest)
    fun deleteRequest(request: FriendRequest)
    fun acceptRequest(request: FriendRequest, friendship: Friendship)
    fun deleteFriendship(friendship: Friendship)
    fun close()
}
