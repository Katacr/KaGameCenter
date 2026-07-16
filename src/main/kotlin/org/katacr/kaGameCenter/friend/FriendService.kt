package org.katacr.kaGameCenter.friend

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 管理好友关系缓存、玩家解析和异步持久化。 */
class FriendService(
    private val plugin: JavaPlugin,
    private val repository: FriendRepository
) {
    private val friendships = linkedSetOf<Friendship>()
    private val requests = linkedSetOf<FriendRequest>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KaGameCenter-Friends").apply { isDaemon = true }
    }

    @Synchronized
    fun init() {
        repository.init()
        friendships.clear()
        friendships.addAll(repository.loadFriendships())
        requests.clear()
        requests.addAll(repository.loadRequests().filter { it.senderId != it.receiverId })
    }

    @Synchronized
    fun relation(playerId: UUID, targetId: UUID): FriendRelation {
        if (playerId == targetId) return FriendRelation.SELF
        if (Friendship.of(playerId, targetId) in friendships) return FriendRelation.FRIENDS
        if (FriendRequest(playerId, targetId) in requests) return FriendRelation.OUTGOING_REQUEST
        if (FriendRequest(targetId, playerId) in requests) return FriendRelation.INCOMING_REQUEST
        return FriendRelation.NONE
    }

    @Synchronized
    fun friendsOf(playerId: UUID): Set<UUID> {
        return friendships.mapNotNullTo(linkedSetOf()) { it.other(playerId) }
    }

    @Synchronized
    fun incomingRequests(playerId: UUID): Set<UUID> {
        return requests.filter { it.receiverId == playerId }.mapTo(linkedSetOf()) { it.senderId }
    }

    @Synchronized
    fun outgoingRequests(playerId: UUID): Set<UUID> {
        return requests.filter { it.senderId == playerId }.mapTo(linkedSetOf()) { it.receiverId }
    }

    @Synchronized
    fun request(playerId: UUID, targetId: UUID): FriendOperationResult {
        return when (relation(playerId, targetId)) {
            FriendRelation.SELF -> FriendOperationResult.SELF
            FriendRelation.FRIENDS -> FriendOperationResult.ALREADY_FRIENDS
            FriendRelation.OUTGOING_REQUEST -> FriendOperationResult.REQUEST_ALREADY_SENT
            FriendRelation.INCOMING_REQUEST -> FriendOperationResult.INCOMING_REQUEST_EXISTS
            FriendRelation.NONE -> {
                val request = FriendRequest(playerId, targetId)
                requests.add(request)
                persist("save friend request $playerId -> $targetId") { repository.saveRequest(request) }
                FriendOperationResult.SENT
            }
        }
    }

    @Synchronized
    fun accept(playerId: UUID, senderId: UUID): FriendOperationResult {
        val request = FriendRequest(senderId, playerId)
        if (!requests.remove(request)) return FriendOperationResult.REQUEST_NOT_FOUND
        requests.remove(FriendRequest(playerId, senderId))
        val friendship = Friendship.of(playerId, senderId)
        friendships.add(friendship)
        persist("accept friend request $senderId -> $playerId") { repository.acceptRequest(request, friendship) }
        return FriendOperationResult.ACCEPTED
    }

    @Synchronized
    fun deny(playerId: UUID, senderId: UUID): FriendOperationResult {
        val request = FriendRequest(senderId, playerId)
        if (!requests.remove(request)) return FriendOperationResult.REQUEST_NOT_FOUND
        persist("deny friend request $senderId -> $playerId") { repository.deleteRequest(request) }
        return FriendOperationResult.DENIED
    }

    @Synchronized
    fun remove(playerId: UUID, targetId: UUID): FriendOperationResult {
        if (playerId == targetId) return FriendOperationResult.SELF
        val friendship = Friendship.of(playerId, targetId)
        if (!friendships.remove(friendship)) return FriendOperationResult.NOT_FRIENDS
        persist("remove friendship $playerId / $targetId") { repository.deleteFriendship(friendship) }
        return FriendOperationResult.REMOVED
    }

    /** 在主线程按 UUID、在线名称或曾登录名称解析玩家。 */
    fun resolvePlayer(input: String): OfflinePlayer? {
        check(Bukkit.isPrimaryThread()) { "Player identity resolution must run on the Bukkit main thread" }
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        runCatching { UUID.fromString(trimmed) }.getOrNull()?.let(Bukkit::getOfflinePlayer)?.let { return it }
        Bukkit.getPlayerExact(trimmed)?.let { return it }
        return Bukkit.getOfflinePlayers().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    }

    fun close() {
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow()
        repository.close()
    }

    private fun persist(description: String, action: () -> Unit) {
        executor.execute {
            runCatching(action).onFailure { error ->
                plugin.logger.warning("Failed to $description: ${error.message}")
            }
        }
    }
}
