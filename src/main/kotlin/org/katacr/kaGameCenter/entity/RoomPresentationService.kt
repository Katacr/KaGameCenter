package org.katacr.kaGameCenter.entity

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Mob
import org.bukkit.inventory.ItemStack
import org.bukkit.util.EulerAngle
import org.katacr.kaGameCenter.resource.RoomResourceScopeService
import java.util.UUID

/** 统一创建、更新并清理由房间资源作用域托管的 NPC、文本和浮动物品。 */
class RoomPresentationService(
    private val resourceScopeService: RoomResourceScopeService
) {
    /** 创建一个无 AI、无碰撞且随房间关闭清理的通用 NPC。 */
    fun <T : Mob> spawnNpc(
        roomId: String,
        location: Location,
        entityClass: Class<T>,
        ownerId: UUID? = null,
        type: String? = null,
        configure: (T) -> Unit = {}
    ): T {
        val entity = location.world.spawn(location, entityClass) {
            it.setAI(false)
            it.isInvulnerable = true
            it.isCollidable = false
            it.isSilent = true
            it.isPersistent = true
            it.removeWhenFarAway = false
            configure(it)
        }
        resourceScopeService.open(roomId).trackEntity(entity, ownerId, type)
        return entity
    }

    /** 创建一行无碰撞文本，并登记到目标房间的自动清理作用域。 */
    fun spawnText(
        roomId: String,
        location: Location,
        text: Component,
        ownerId: UUID? = null,
        type: String? = null,
        scoreboardTag: String? = null
    ): ArmorStand {
        val entity = location.world.spawn(location, ArmorStand::class.java) {
            it.setGravity(false)
            it.isVisible = false
            it.isMarker = true
            it.isSmall = true
            it.isInvulnerable = true
            it.isSilent = true
            it.isPersistent = true
            it.removeWhenFarAway = false
            it.customName(text)
            it.isCustomNameVisible = true
            scoreboardTag?.takeIf(String::isNotBlank)?.let(it::addScoreboardTag)
        }
        resourceScopeService.open(roomId).trackEntity(entity, ownerId, type)
        return entity
    }

    /** 创建一个以盔甲架头盔槽显示且可旋转的浮动物品。 */
    fun spawnFloatingItem(
        roomId: String,
        location: Location,
        item: ItemStack,
        ownerId: UUID? = null,
        type: String? = null,
        scoreboardTag: String? = null
    ): ArmorStand {
        val entity = location.world.spawn(location, ArmorStand::class.java) {
            it.setGravity(false)
            it.isVisible = false
            it.isMarker = true
            it.isInvulnerable = true
            it.isSilent = true
            it.isPersistent = true
            it.removeWhenFarAway = false
            it.equipment.helmet = item.clone()
            scoreboardTag?.takeIf(String::isNotBlank)?.let(it::addScoreboardTag)
        }
        resourceScopeService.open(roomId).trackEntity(entity, ownerId, type)
        return entity
    }

    /** 更新仍存在的文本全息内容。 */
    fun updateText(entityId: UUID, text: Component): Boolean {
        val entity = Bukkit.getEntity(entityId) as? ArmorStand ?: return false
        entity.customName(text)
        return true
    }

    /** 更新仍存在的浮动物品水平旋转角度。 */
    fun rotateFloatingItem(entityId: UUID, degrees: Double): Boolean {
        val entity = Bukkit.getEntity(entityId) as? ArmorStand ?: return false
        entity.headPose = EulerAngle(0.0, Math.toRadians(degrees), 0.0)
        return true
    }

    /** 移除一个呈现实体并解除房间资源登记。 */
    fun remove(roomId: String, entityId: UUID) {
        Bukkit.getEntity(entityId)?.remove()
        resourceScopeService.releaseEntity(roomId, entityId)
    }

    /** 批量移除呈现实体并解除房间资源登记。 */
    fun removeAll(roomId: String, entityIds: Iterable<UUID>) {
        entityIds.toSet().forEach { remove(roomId, it) }
    }

    /** 返回仍存在的呈现实体。 */
    fun entity(entityId: UUID): Entity? = Bukkit.getEntity(entityId)
}
