package org.katacr.kaGameCenter.velocity

import java.util.UUID

data class VelocityProxyJoinRequest(
    val protocolVersion: Int = 1,
    val requestId: String,
    val playerId: UUID,
    val playerName: String,
    val sourceServerId: String,
    val targetServerId: String,
    val targetRoomId: String,
    val createdAt: Long = System.currentTimeMillis()
)
