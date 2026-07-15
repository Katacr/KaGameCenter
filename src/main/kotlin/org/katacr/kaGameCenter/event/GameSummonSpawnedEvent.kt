package org.katacr.kaGameCenter.event

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.katacr.kaGameCenter.game.GameRoom

/** 在玩法召唤物生成、配置并登记完成后发布稳定的归属和实体上下文。 */
class GameSummonSpawnedEvent(
    val room: GameRoom,
    val player: Player,
    val teamId: String?,
    val sourceId: String,
    val entity: LivingEntity
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
