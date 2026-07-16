package org.katacr.kaGameCenter.reconnect

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** 保存核心在玩家断线瞬间捕获、并在重连或超时后自动释放的通用快照。 */
data class RoomDisconnectSnapshot(
    val roomId: String,
    val playerId: UUID,
    val playerName: String,
    val location: Location,
    val inventoryContents: List<ItemStack>,
    val lastDamagerId: UUID?,
    val disconnectedAtMillis: Long
)

/** 按房间和玩家管理断线位置、背包与最近伤害者快照。 */
class RoomReconnectStateService {
    private val snapshots = linkedMapOf<UUID, RoomDisconnectSnapshot>()

    /** 捕获玩家断线瞬间的通用状态，并替换该玩家的旧快照。 */
    @Synchronized
    fun capture(roomId: String, player: Player, lastDamagerId: UUID?): RoomDisconnectSnapshot {
        val snapshot = RoomDisconnectSnapshot(
            roomId = roomId,
            playerId = player.uniqueId,
            playerName = player.name,
            location = player.location.clone(),
            inventoryContents = player.inventory.contents.filterNotNull().map(ItemStack::clone),
            lastDamagerId = lastDamagerId,
            disconnectedAtMillis = System.currentTimeMillis()
        )
        snapshots[player.uniqueId] = snapshot
        return snapshot
    }

    /** 返回玩家当前断线快照。 */
    @Synchronized
    fun get(playerId: UUID): RoomDisconnectSnapshot? = snapshots[playerId]

    /** 仅在快照属于指定房间时返回玩家断线快照。 */
    @Synchronized
    fun get(roomId: String, playerId: UUID): RoomDisconnectSnapshot? {
        return snapshots[playerId]?.takeIf { it.roomId == roomId }
    }

    /** 移除并返回玩家断线快照。 */
    @Synchronized
    fun remove(playerId: UUID): RoomDisconnectSnapshot? = snapshots.remove(playerId)

    /** 仅移除属于指定房间的玩家断线快照，避免旧定时任务清掉新房间状态。 */
    @Synchronized
    fun remove(roomId: String, playerId: UUID): RoomDisconnectSnapshot? {
        val snapshot = snapshots[playerId]?.takeIf { it.roomId == roomId } ?: return null
        snapshots.remove(playerId)
        return snapshot
    }

    /** 清除指定房间的全部断线快照。 */
    @Synchronized
    fun clearRoom(roomId: String) {
        snapshots.entries.removeIf { it.value.roomId == roomId }
    }

    /** 清除插件内全部断线快照。 */
    @Synchronized
    fun clearAll() {
        snapshots.clear()
    }
}
