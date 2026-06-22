package org.katacr.kaGameCenter.velocity

data class VelocityRoomSnapshot(
    val protocolVersion: Int = 1,
    val serverId: String,
    val roomId: String,
    val roomName: String,
    val gameId: String,
    val gameName: String,
    val mapId: String,
    val state: String,
    val players: Int,
    val maxPlayers: Int,
    val spectators: Int,
    val joinable: Boolean,
    val tags: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
