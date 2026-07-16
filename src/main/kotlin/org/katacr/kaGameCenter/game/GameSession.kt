package org.katacr.kaGameCenter.game

import org.bukkit.entity.Player
import org.katacr.kaGameCenter.chat.GameChatChannel
import org.katacr.kaGameCenter.chat.GameChatRoute
import org.katacr.kaGameCenter.display.GameBossBarStatus
import java.util.UUID

interface GameSession {
    val room: GameRoom

    fun onPrepare() {}
    fun onPlayerJoin(player: Player) {}
    fun onPlayerLeave(player: Player) {}
    fun onSpectatorJoin(player: Player) {}
    fun onSpectatorLeave(player: Player) {}
    fun onPlayerDeath(player: Player) {}
    fun onPlayerKill(killer: Player, victim: Player) {}
    /** 解析本次死亡的有效击杀者，玩法可覆盖以支持虚空或爆炸等延迟归因。 */
    fun resolveKiller(victim: Player): Player? = victim.killer
    /** 返回需要为该玩家保留房间席位的断线宽限 tick，零表示立即正常离开。 */
    fun reconnectGraceTicks(player: Player): Long = 0L
    /** 在主插件开始保留断线席位时通知玩法暂停该玩家输入。 */
    fun onPlayerDisconnect(player: Player) {}
    /** 返回断线玩家重连时可由事件调整的复活倒计时，null 表示不提供。 */
    fun reconnectRespawnDelayTicks(player: Player): Long? = null
    /** 在重连事件通过后、玩法恢复回调前应用最终复活倒计时。 */
    fun applyReconnectRespawnDelayTicks(player: Player, ticks: Long) {}
    /** 在玩家于宽限时间内重新上线并重新附加显示后恢复玩法状态。 */
    fun onPlayerReconnect(player: Player) {}
    /** 在宽限时间到期时通知玩法执行离线淘汰和私有状态清理。 */
    fun onPlayerReconnectExpired(playerId: UUID) {}
    /** 判断观战者是否可以把指定房间玩家作为跟随目标。 */
    fun canSpectatorFollow(spectator: Player, target: Player): Boolean = true
    /** 允许玩法调整聊天频道、文本和受众；返回 null 表示已由玩法拒绝该消息。 */
    fun routeChat(player: Player, message: String, requestedChannel: GameChatChannel): GameChatRoute? {
        return GameChatRoute(requestedChannel, message)
    }
    fun usesCustomScoreboard(): Boolean = false
    fun usesCustomActionBar(): Boolean = false
    /** 返回通用头像 BossBar 快照；null 表示使用核心默认房间状态条。 */
    fun bossBarStatus(): GameBossBarStatus? = null
    /** 声明玩法只接管 Tab 头尾；主插件仍负责玩家名称与排序。 */
    fun usesCustomTabHeaderFooter(): Boolean = false
    /** 声明玩法只接管 Tab 玩家名称；主插件仍负责稳定排序。 */
    fun usesCustomTabPlayerNames(): Boolean = false
    /** 允许玩法在核心计算默认值后按阶段和身份调整玩家 Tab 排序，返回值必须非负。 */
    fun tabPlayerListOrder(player: Player, defaultOrder: Int): Int = defaultOrder
    fun onStart() {}
    fun onTick() {}
    fun onEnd() {}
    fun onClose() {}
}
