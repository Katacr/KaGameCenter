package org.katacr.kaGameCenter.display

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import java.util.UUID

/** 描述一个玩家头像的名称、存活状态和可选存活颜色。 */
data class PlayerAvatarStatus(
    val playerId: UUID,
    val playerName: String,
    val alive: Boolean,
    val aliveColor: TextColor? = null
)

/** 描述 BossBar 左右两侧的一组头像和可选标签。 */
data class PlayerStatusSide(
    val label: Component = Component.empty(),
    val players: List<PlayerAvatarStatus> = emptyList()
)

/** 描述类似竞技游戏对阵条的头像、中央计时和进度。 */
data class GameBossBarStatus(
    val left: PlayerStatusSide,
    val center: Component,
    val right: PlayerStatusSide,
    val progress: Float = 1.0f,
    val color: BossBar.Color = BossBar.Color.BLUE,
    val overlay: BossBar.Overlay = BossBar.Overlay.PROGRESS,
    val maxHeadsPerSide: Int = 5
)
