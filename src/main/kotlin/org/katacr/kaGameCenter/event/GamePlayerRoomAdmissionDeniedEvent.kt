package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 区分正式加入、外部观战和断线宽限重连三类房间准入。 */
enum class GameRoomAdmissionType {
    JOIN,
    SPECTATE,
    RECONNECT
}

/** 描述已有目标房间时稳定且可供玩法反馈的准入拒绝原因。 */
enum class GameRoomAdmissionDeniedReason {
    ALREADY_ACTIVE,
    ROOM_NOT_JOINABLE,
    ROOM_FULL,
    SPECTATE_NOT_ALLOWED,
    PREPARATION_FAILED,
    EVENT_CANCELLED,
    CALLBACK_FAILED
}

/** 在房间准入确定失败且未提交新成员关系时发布拒绝上下文。 */
class GamePlayerRoomAdmissionDeniedEvent(
    val room: GameRoom,
    val player: Player,
    val type: GameRoomAdmissionType,
    val reason: GameRoomAdmissionDeniedReason
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
