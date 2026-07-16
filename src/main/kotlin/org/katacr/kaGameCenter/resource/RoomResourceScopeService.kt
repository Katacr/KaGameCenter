package org.katacr.kaGameCenter.resource

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Entity
import org.katacr.kaGameCenter.entity.RoomEntityOwnershipService
import org.katacr.kaGameCenter.nametag.PlayerNametagService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.task.RoomTaskService
import java.util.UUID

/** 为每个房间创建并关闭统一的临时资源作用域。 */
class RoomResourceScopeService(
    private val roomTaskService: RoomTaskService,
    private val entityOwnershipService: RoomEntityOwnershipService,
    private val packetService: PacketDispatchService,
    private val nametagService: PlayerNametagService
) {
    private val scopes = linkedMapOf<String, RoomResourceScope>()

    /** 获取房间唯一且可重复调用的资源作用域。 */
    @Synchronized
    fun open(roomId: String): RoomResourceScope {
        return scopes.getOrPut(roomId) {
            RoomResourceScope(roomId, roomTaskService, entityOwnershipService, packetService, nametagService)
        }
    }

    /** 从已存在的房间作用域释放实体，不会为已关闭房间重新创建空作用域。 */
    @Synchronized
    fun releaseEntity(roomId: String, entityId: UUID) {
        scopes[roomId]?.releaseEntity(entityId)
    }

    /** 判断实体是否登记在指定房间的现有资源作用域中。 */
    @Synchronized
    fun isEntityTracked(roomId: String, entityId: UUID): Boolean {
        return scopes[roomId]?.isEntityTracked(entityId) == true
    }

    /** 关闭并移除指定房间的资源作用域。 */
    @Synchronized
    fun closeRoom(roomId: String) {
        scopes.remove(roomId)?.close()
    }

    /** 关闭插件内仍然存在的全部房间资源作用域。 */
    @Synchronized
    fun closeAll() {
        scopes.values.toList().forEach(RoomResourceScope::close)
        scopes.clear()
    }
}

/** 保存一个房间的任务、实体、临时方块和私有视觉资源。 */
class RoomResourceScope internal constructor(
    val roomId: String,
    private val roomTaskService: RoomTaskService,
    private val entityOwnershipService: RoomEntityOwnershipService,
    private val packetService: PacketDispatchService,
    private val nametagService: PlayerNametagService
) {
    private data class BlockKey(val worldId: UUID, val x: Int, val y: Int, val z: Int)

    private val entities = linkedSetOf<UUID>()
    private val blocks = linkedMapOf<BlockKey, BlockData>()
    private val viewers = linkedSetOf<UUID>()
    private var closed = false

    /** 登记房间临时实体，并可同时登记实体拥有者。 */
    fun trackEntity(entity: Entity, ownerId: UUID? = null, type: String? = null): Entity {
        checkOpen()
        entities.add(entity.uniqueId)
        if (ownerId != null) entityOwnershipService.track(roomId, entity, ownerId, type)
        return entity
    }

    /** 从作用域移除已自行销毁或已完成处理的实体。 */
    fun releaseEntity(entityId: UUID) {
        entities.remove(entityId)
        entityOwnershipService.remove(entityId)
    }

    /** 判断实体是否仍由当前房间资源作用域托管。 */
    fun isEntityTracked(entityId: UUID): Boolean = entityId in entities

    /** 首次修改方块前保存其原始 BlockData，关闭房间时自动恢复。 */
    fun captureBlock(block: Block) {
        checkOpen()
        val key = BlockKey(block.world.uid, block.x, block.y, block.z)
        blocks.putIfAbsent(key, block.blockData.clone())
    }

    /** 登记接收过房间私有 Packet 视觉的玩家。 */
    fun trackViewer(playerId: UUID) {
        checkOpen()
        viewers.add(playerId)
    }

    /** 按固定顺序幂等释放房间资源。 */
    fun close() {
        if (closed) return
        closed = true
        roomTaskService.cancelRoom(roomId)
        entities.mapNotNull(Bukkit::getEntity).forEach(Entity::remove)
        entities.clear()
        entityOwnershipService.clearRoom(roomId)
        restoreBlocks()
        viewers.mapNotNull(Bukkit::getPlayer).forEach(packetService::clearViewer)
        viewers.clear()
        nametagService.clearRoom(roomId)
    }

    private fun restoreBlocks() {
        blocks.forEach { (key, blockData) ->
            val world: World = Bukkit.getWorld(key.worldId) ?: return@forEach
            world.getBlockAt(key.x, key.y, key.z).blockData = blockData
        }
        blocks.clear()
    }

    private fun checkOpen() {
        check(!closed) { "Room resource scope is already closed: $roomId" }
    }
}
