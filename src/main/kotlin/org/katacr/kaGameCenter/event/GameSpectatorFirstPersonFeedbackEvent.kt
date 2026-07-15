package org.katacr.kaGameCenter.event

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** 区分原版第一人称观战镜头的进入与退出反馈。 */
enum class GameSpectatorFirstPersonFeedbackAction {
    ENTER,
    LEAVE
}

/** 在第一人称镜头状态提交后、默认标题发送前，允许修改反馈内容和时长。 */
class GameSpectatorFirstPersonFeedbackEvent(
    val roomId: String,
    val spectator: Player,
    val targetId: UUID?,
    val target: Player?,
    val action: GameSpectatorFirstPersonFeedbackAction,
    var title: Component?,
    var subtitle: Component?,
    var fadeInTicks: Int = 0,
    var stayTicks: Int = 40,
    var fadeOutTicks: Int = 10
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
