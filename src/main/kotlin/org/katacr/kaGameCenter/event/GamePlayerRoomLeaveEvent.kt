package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom
import java.util.UUID

/** 描述玩家房间成员关系结束的稳定原因。 */
enum class GameRoomLeaveReason {
    LEAVE,
    KICK,
    DISCONNECT,
    RECONNECT_EXPIRED,
    RECONNECT_REJECTED,
    ROOM_CLOSED,
    ROLE_CHANGE
}

/** 在玩家或观战者完成房间脱离与运行状态清理后发布离开上下文。 */
class GamePlayerRoomLeaveEvent(
    val room: GameRoom,
    val playerId: UUID,
    val player: Player?,
    val spectator: Boolean,
    val reason: GameRoomLeaveReason,
    val lastDamagerId: UUID?,
    val lastDamager: Player?
) : Event() {
    /** 返回本事件的 Bukkit 处理器列表。 */
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** 返回 Bukkit 注册监听器所需的静态处理器列表。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
