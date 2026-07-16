package org.katacr.kaGameCenter.friend

/** 数据库关闭或初始化失败时使用的进程内好友仓库。 */
class MemoryFriendRepository : FriendRepository {
    private val friendships = linkedSetOf<Friendship>()
    private val requests = linkedSetOf<FriendRequest>()

    override fun init() = Unit

    override fun loadFriendships(): Set<Friendship> = friendships.toSet()

    override fun loadRequests(): Set<FriendRequest> = requests.toSet()

    override fun saveRequest(request: FriendRequest) {
        requests.add(request)
    }

    override fun deleteRequest(request: FriendRequest) {
        requests.remove(request)
    }

    override fun acceptRequest(request: FriendRequest, friendship: Friendship) {
        requests.remove(request)
        requests.remove(FriendRequest(request.receiverId, request.senderId))
        friendships.add(friendship)
    }

    override fun deleteFriendship(friendship: Friendship) {
        friendships.remove(friendship)
    }

    override fun close() = Unit
}
