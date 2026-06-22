package org.katacr.kaGameCenter.velocity

data class VelocityReserveRoomResponse(
    val protocolVersion: Int = 1,
    val requestId: String,
    val accepted: Boolean,
    val targetServerId: String,
    val targetRoomId: String,
    val reason: String? = null,
    val expiresAt: Long? = null
)
