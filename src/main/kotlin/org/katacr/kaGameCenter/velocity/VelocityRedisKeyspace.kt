package org.katacr.kaGameCenter.velocity

class VelocityRedisKeyspace(prefix: String) {
    private val base = prefix.trim().ifBlank { "kgv" }

    val servers: String = "$base:servers"
    val roomsIndex: String = "$base:rooms:index"
    val events: String = "$base:events"
    val proxyJoinRequestQueue: String = "$base:join:req:queue"

    fun server(serverId: String): String = "$base:server:$serverId"

    fun room(serverId: String, roomId: String): String = "$base:room:$serverId:$roomId"

    fun reserveRequestQueue(serverId: String): String = "$base:reserve:req:queue:$serverId"

    fun reserveRequest(requestId: String): String = "$base:reserve:req:$requestId"

    fun proxyJoinRequest(requestId: String): String = "$base:join:req:$requestId"

    fun reserveResponse(requestId: String): String = "$base:reserve:res:$requestId"

    fun joinIntent(intentId: String): String = "$base:intent:$intentId"

    fun playerIntent(playerId: String): String = "$base:intent:player:$playerId"
}
