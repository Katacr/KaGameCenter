package org.katacr.kaGameCenter.velocity

import org.katacr.kaGameCenter.game.GameRoom
import org.bukkit.entity.Player

class NoopVelocityBridgeService : VelocityBridgeService {
    override val enabled: Boolean = false
    override val backendName: String = "disabled"
    override val serverId: String = "local"
    override val heartbeatIntervalTicks: Long = 20L * 60L

    override fun init() {
    }

    override fun publishRoom(room: GameRoom) {
    }

    override fun removeRoom(room: GameRoom) {
    }

    override fun removeAllRooms() {
    }

    override fun globalRooms(): Collection<VelocityRoomSnapshot> = emptyList()

    override fun startReservationHandling(handler: (VelocityReserveRoomRequest) -> VelocityReserveRoomResponse) {
    }

    override fun consumeJoinIntent(player: Player, handler: (String) -> Boolean) {
    }

    override fun requestRemoteJoin(player: Player, targetServerId: String, targetRoomId: String): Boolean = false

    override fun shutdown() {
    }
}
