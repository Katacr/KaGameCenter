package org.katacr.kaGameCenter.velocity

import org.katacr.kaGameCenter.game.GameRoom
import org.bukkit.entity.Player

interface VelocityBridgeService {
    val enabled: Boolean
    val backendName: String
    val serverId: String
    val heartbeatIntervalTicks: Long

    fun init()

    fun publishRoom(room: GameRoom)

    fun publishAll(rooms: Collection<GameRoom>) {
        rooms.forEach(::publishRoom)
    }

    fun globalRooms(): Collection<VelocityRoomSnapshot>

    fun startReservationHandling(handler: (VelocityReserveRoomRequest) -> VelocityReserveRoomResponse)

    fun consumeJoinIntent(player: Player, handler: (String) -> Boolean)

    fun requestRemoteJoin(player: Player, targetServerId: String, targetRoomId: String): Boolean

    fun removeRoom(room: GameRoom)

    fun removeAllRooms()

    fun shutdown()
}
