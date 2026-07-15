package org.katacr.kaGameCenter.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.entity.EntityDamageEvent
import org.katacr.kaGameCenter.game.GameRoom
import java.util.UUID

/** 在玩法提交一次普通死亡或最终淘汰后，发布归因完整且支持离线受害者的结果。 */
class GamePlayerDeathResolvedEvent(
    val room: GameRoom,
    val victimId: UUID,
    val victim: Player?,
    val victimTeamId: String?,
    val killerId: UUID?,
    val killer: Player?,
    val killerTeamId: String?,
    val damageCause: EntityDamageEvent.DamageCause?,
    val sourceId: String?,
    val finalDeath: Boolean
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
