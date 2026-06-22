package org.katacr.kaGameCenter.velocity

import java.util.UUID

data class VelocityJoinIntent(
    val protocolVersion: Int = 1,
    val intentId: String,
    val playerId: UUID,
    val playerName: String,
    val targetServerId: String,
    val targetRoomId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long
)
