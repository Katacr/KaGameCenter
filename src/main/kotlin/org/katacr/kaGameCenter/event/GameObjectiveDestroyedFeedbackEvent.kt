package org.katacr.kaGameCenter.event

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在玩法目标状态提交后、默认反馈发送前，允许修改普通广播和受害方专属反馈。 */
class GameObjectiveDestroyedFeedbackEvent(
    val room: GameRoom,
    val objectiveType: String,
    val objectiveId: String,
    val actor: Player?,
    val actorTeamId: String?,
    val targetTeamId: String?,
    val sourceId: String?,
    var message: Component?,
    var targetTitle: Component?,
    var targetSubtitle: Component?
) : Event() {
    /** 受害方专属聊天；为空时回退普通消息，普通消息为空时仍整体抑制聊天。 */
    var targetMessage: Component? = null

    /** 返回本事件的 Bukkit 处理器列表。 */
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** 返回 Bukkit 注册监听器所需的静态处理器列表。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
