package org.katacr.kaGameCenter.dialog

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.katacr.kaGameCenter.event.GamePlayerRoomJoinEvent
import org.katacr.kaGameCenter.event.GamePlayerRoomLeaveEvent
import org.katacr.kaGameCenter.event.GamePlayerRoomReconnectEvent
import org.katacr.kaGameCenter.event.GamePlayerTeamAssignEvent

/** 把房间成员和队伍变化合并为五 tick 延迟的大厅 Dialog 刷新。 */
class RoomDialogRefreshListener(
    private val menuService: GameCenterMenuService
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRoomJoin(event: GamePlayerRoomJoinEvent) {
        menuService.scheduleRoomDialogRefresh(event.room.id)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRoomLeave(event: GamePlayerRoomLeaveEvent) {
        menuService.scheduleRoomDialogRefresh(event.room.id)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRoomReconnect(event: GamePlayerRoomReconnectEvent) {
        menuService.scheduleRoomDialogRefresh(event.room.id)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeamAssign(event: GamePlayerTeamAssignEvent) {
        menuService.scheduleRoomDialogRefresh(event.roomId)
    }
}
