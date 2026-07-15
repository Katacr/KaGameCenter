package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在玩法目标完成销毁并提交内部状态后，发布破坏者和双方队伍上下文。 */
class GameObjectiveDestroyedEvent(
    val room: GameRoom,
    val objectiveType: String,
    val objectiveId: String,
    val actor: Player?,
    val actorTeamId: String?,
    val targetTeamId: String?,
    val sourceId: String?
) : Event() {
    /** 保留原有玩家破坏目标构造签名，并把来源留空以兼容既有外置监听器。 */
    constructor(
        room: GameRoom,
        objectiveType: String,
        objectiveId: String,
        actor: Player,
        actorTeamId: String?,
        targetTeamId: String?
    ) : this(room, objectiveType, objectiveId, actor, actorTeamId, targetTeamId, sourceId = null)

    /** 返回本事件的 Bukkit 处理器列表。 */
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** 返回 Bukkit 注册监听器所需的静态处理器列表。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
