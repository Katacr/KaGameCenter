package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** 在观战服务完成目标切换或清除后发布前后目标快照。 */
class GameSpectatorTargetChangedEvent(
    val roomId: String,
    val spectator: Player,
    val mode: GameSpectatorTargetMode,
    val previousTargetId: UUID?,
    val previousTarget: Player?,
    val targetId: UUID?,
    val target: Player?
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
