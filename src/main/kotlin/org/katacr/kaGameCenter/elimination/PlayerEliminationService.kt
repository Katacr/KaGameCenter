package org.katacr.kaGameCenter.elimination

import org.bukkit.GameMode
import org.bukkit.GameRules
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerRespawnEvent
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.spectator.SpectatorPolicy
import org.katacr.kaGameCenter.spectator.SpectatorService
import org.katacr.kaGameCenter.task.RoomTaskService
import java.util.UUID

/** 统一处理房间玩家淘汰后的立即重生、观战位置和输入限制标记。 */
class PlayerEliminationService(
    private val roomTaskService: RoomTaskService,
    private val spectatorService: SpectatorService
) {
    private data class EliminationState(
        val roomId: String,
        val spectatorLocation: Location,
        val clearInventory: Boolean,
        val spectatorPolicy: SpectatorPolicy?
    )

    private val states = linkedMapOf<UUID, EliminationState>()

    /** 为需要死亡后直接观战的临时世界启用立即重生。 */
    fun enableImmediateRespawn(world: World) {
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true)
    }

    /** 标记玩家已淘汰；非死亡场景会立即应用原版或可选托管观战状态。 */
    @Synchronized
    fun eliminate(
        room: GameRoom,
        player: Player,
        spectatorLocation: Location,
        clearInventory: Boolean = true,
        spectatorPolicy: SpectatorPolicy? = null
    ) {
        states[player.uniqueId] = EliminationState(
            room.id,
            spectatorLocation.clone(),
            clearInventory,
            spectatorPolicy
        )
        if (!player.isDead) applySpectator(room, player, states.getValue(player.uniqueId))
    }

    /** 在立即重生事件中恢复淘汰玩家的观战位置，并于下一 tick 应用观战模式。 */
    fun handleRespawn(room: GameRoom, event: PlayerRespawnEvent): Boolean {
        val player = event.player
        val state = synchronized(this) { states[player.uniqueId] } ?: return false
        if (state.roomId != room.id) return false
        event.respawnLocation = state.spectatorLocation.clone()
        roomTaskService.runTaskLater(room.id, 1L, Runnable {
            val current = synchronized(this) { states[player.uniqueId] } ?: return@Runnable
            if (current.roomId == room.id && player.isOnline) applySpectator(room, player, current)
        })
        return true
    }

    /** 判断玩家是否处于任意房间的淘汰观战状态。 */
    @Synchronized
    fun isEliminated(playerId: UUID): Boolean = states.containsKey(playerId)

    /** 清除单个玩家在指定房间中的淘汰表现状态。 */
    @Synchronized
    fun clear(roomId: String, playerId: UUID) {
        if (states[playerId]?.roomId == roomId) states.remove(playerId)
    }

    /** 清除房间关闭后遗留的全部淘汰表现状态。 */
    @Synchronized
    fun clearRoom(roomId: String) {
        states.entries.removeIf { it.value.roomId == roomId }
    }

    /** 清除插件关闭时遗留的全部淘汰表现状态。 */
    @Synchronized
    fun clearAll() {
        states.clear()
    }

    private fun applySpectator(room: GameRoom, player: Player, state: EliminationState) {
        val policy = state.spectatorPolicy
        if (policy != null) {
            spectatorService.enterEliminated(player, room, policy, state.spectatorLocation)
            return
        }
        player.gameMode = GameMode.SPECTATOR
        player.spectatorTarget = null
        player.isInvulnerable = true
        if (state.clearInventory) player.inventory.clear()
        player.teleport(state.spectatorLocation)
    }
}
