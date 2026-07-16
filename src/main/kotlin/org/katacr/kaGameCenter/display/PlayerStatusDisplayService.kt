package org.katacr.kaGameCenter.display

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/** 渲染彩色玩家头像，并按房间托管 Adventure BossBar 与 viewer。 */
class PlayerStatusDisplayService {
    private data class ManagedBossBar(
        val bossBar: BossBar,
        val viewers: MutableSet<UUID> = linkedSetOf()
    )

    private val bossBars = linkedMapOf<String, ManagedBossBar>()

    /** 将存活玩家渲染为默认颜色头像，将阵亡玩家渲染为红色头像。 */
    fun avatar(status: PlayerAvatarStatus): Component {
        val color = when {
            !status.alive -> "<red>"
            status.aliveColor != null -> "<color:${status.aliveColor.asHexString()}>"
            else -> "&r"
        }
        return IconTextParser.parse("$color<head:${status.playerName}>")
    }

    /** 按每行最多五个头像生成计分板 Component 行。 */
    @JvmOverloads
    fun avatarRows(statuses: Collection<PlayerAvatarStatus>, maxPerLine: Int = 5): List<Component> {
        return statuses.chunked(maxPerLine.coerceIn(1, 5)).map { row ->
            Component.join(JoinConfiguration.separator(Component.space()), row.map(::avatar))
        }
    }

    /** 更新房间竞技状态 BossBar，并同步新增或离开的 viewer。 */
    fun update(roomId: String, viewers: Collection<Player>, status: GameBossBarStatus) {
        val title = versusTitle(status)
        update(
            roomId,
            viewers,
            title,
            status.progress,
            status.color,
            status.overlay
        )
    }

    /** 更新普通 Component BossBar，供没有头像状态快照的房间复用。 */
    fun update(
        roomId: String,
        viewers: Collection<Player>,
        title: Component,
        progress: Float,
        color: BossBar.Color,
        overlay: BossBar.Overlay
    ) {
        val managed = bossBars.getOrPut(roomId) {
            ManagedBossBar(BossBar.bossBar(title, progress.coerceIn(0.0f, 1.0f), color, overlay))
        }
        managed.bossBar.name(title)
        managed.bossBar.progress(progress.coerceIn(0.0f, 1.0f))
        managed.bossBar.color(color)
        managed.bossBar.overlay(overlay)

        val currentViewers = viewers.mapTo(linkedSetOf()) { it.uniqueId }
        (managed.viewers - currentViewers).forEach { playerId ->
            Bukkit.getPlayer(playerId)?.hideBossBar(managed.bossBar)
            managed.viewers.remove(playerId)
        }
        viewers.forEach { player ->
            if (managed.viewers.add(player.uniqueId)) player.showBossBar(managed.bossBar)
        }
    }

    /** 从指定房间 BossBar 移除单个 viewer。 */
    fun removeViewer(roomId: String, player: Player) {
        val managed = bossBars[roomId] ?: return
        if (managed.viewers.remove(player.uniqueId)) player.hideBossBar(managed.bossBar)
    }

    /** 清理一个房间的 BossBar 和全部在线 viewer。 */
    fun clearRoom(roomId: String) {
        val managed = bossBars.remove(roomId) ?: return
        managed.viewers.forEach { playerId -> Bukkit.getPlayer(playerId)?.hideBossBar(managed.bossBar) }
    }

    /** 清理插件关闭时仍存在的全部 BossBar。 */
    fun clearAll() {
        bossBars.keys.toList().forEach(::clearRoom)
    }

    private fun versusTitle(status: GameBossBarStatus): Component {
        val maxHeads = status.maxHeadsPerSide.coerceIn(1, 5)
        val left = side(status.left, maxHeads)
        val right = side(status.right, maxHeads)
        return Component.empty()
            .append(left)
            .append(Component.text("  "))
            .append(status.center)
            .append(Component.text("  "))
            .append(right)
    }

    private fun side(side: PlayerStatusSide, maxHeads: Int): Component {
        val heads = Component.join(
            JoinConfiguration.separator(Component.space()),
            side.players.take(maxHeads).map(::avatar)
        )
        if (side.label == Component.empty()) return heads
        if (side.players.isEmpty()) return side.label
        return side.label.append(Component.space()).append(heads)
    }
}
