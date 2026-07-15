package org.katacr.kaGameCenter.map

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import org.katacr.kaGameCenter.selection.RegionSelection
import kotlin.math.abs

/** 保存不绑定编辑世界名称的可移植地图坐标。 */
data class ManagedMapPoint(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    /** 将可移植坐标映射到指定运行世界。 */
    fun toLocation(world: World): Location {
        val adjustedX = if (x.isWholeNumber()) x + 0.5 else x
        val adjustedZ = if (z.isWholeNumber()) z + 0.5 else z
        return Location(world, adjustedX, y, adjustedZ, yaw, pitch)
    }
}

/** 使用稳定 ID 保存托管地图中的可增删点位。 */
data class ManagedNamedMapPoint(
    val id: String,
    val point: ManagedMapPoint
)

/** 统一读取、写入和维护托管游戏的可移植点位。 */
class ManagedMapPointService {
    /** 从玩家或实体位置创建精确点位。 */
    fun fromLocation(location: Location): ManagedMapPoint {
        return ManagedMapPoint(location.x, location.y, location.z, location.yaw, location.pitch)
    }

    /** 从方块位置创建整数点位。 */
    fun fromBlock(location: Location): ManagedMapPoint {
        return ManagedMapPoint(location.blockX.toDouble(), location.blockY.toDouble(), location.blockZ.toDouble(), location.yaw, location.pitch)
    }

    /** 从 YAML 节点读取点位，缺少任一坐标时返回 null。 */
    fun read(section: ConfigurationSection?): ManagedMapPoint? {
        if (section == null || !section.contains("x") || !section.contains("y") || !section.contains("z")) return null
        return ManagedMapPoint(
            x = section.getDouble("x"),
            y = section.getDouble("y"),
            z = section.getDouble("z"),
            yaw = section.getDouble("yaw", 0.0).toFloat(),
            pitch = section.getDouble("pitch", 0.0).toFloat()
        )
    }

    /** 把点位写入现有 YAML 节点。 */
    fun write(section: ConfigurationSection, point: ManagedMapPoint) {
        section.set("x", point.x)
        section.set("y", point.y)
        section.set("z", point.z)
        section.set("yaw", point.yaw.toDouble())
        section.set("pitch", point.pitch.toDouble())
    }

    /** 替换指定路径并写入单个点位。 */
    fun replace(root: ConfigurationSection, path: String, point: ManagedMapPoint) {
        root.set(path, null)
        write(root.createSection(path), point)
    }

    /** 读取指定路径下全部有效命名点位。 */
    fun readNamedPoints(root: ConfigurationSection, path: String): List<ManagedNamedMapPoint> {
        return root.getMapList(path).mapNotNull { values ->
            val id = values["id"]?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val pointValues = values["point"] as? Map<*, *> ?: return@mapNotNull null
            val point = readMap(pointValues) ?: return@mapNotNull null
            ManagedNamedMapPoint(id, point)
        }
    }

    /** 按 ID 新增或替换命名点位。 */
    fun upsertNamedPoint(root: ConfigurationSection, path: String, id: String, point: ManagedMapPoint) {
        val points = readNamedPoints(root, path).toMutableList()
        val next = ManagedNamedMapPoint(id, point)
        val existing = points.indexOfFirst { it.id == id }
        if (existing >= 0) points[existing] = next else points.add(next)
        root.set(path, points.map(::namedPointToMap))
    }

    /** 按 ID 删除命名点位并返回是否发生修改。 */
    fun removeNamedPoint(root: ConfigurationSection, path: String, id: String): Boolean {
        val points = readNamedPoints(root, path).toMutableList()
        val removed = points.removeIf { it.id == id }
        if (removed) root.set(path, points.map(::namedPointToMap))
        return removed
    }

    /** 移除选区中的编辑世界名称以便映射到临时世界。 */
    fun portable(region: RegionSelection): RegionSelection {
        return if (region.worldName == null) region else region.copy(worldName = null)
    }

    private fun readMap(values: Map<*, *>): ManagedMapPoint? {
        val x = values["x"].toDoubleOrNull() ?: return null
        val y = values["y"].toDoubleOrNull() ?: return null
        val z = values["z"].toDoubleOrNull() ?: return null
        return ManagedMapPoint(
            x = x,
            y = y,
            z = z,
            yaw = values["yaw"].toDoubleOrNull()?.toFloat() ?: 0f,
            pitch = values["pitch"].toDoubleOrNull()?.toFloat() ?: 0f
        )
    }

    private fun namedPointToMap(named: ManagedNamedMapPoint): Map<String, Any> {
        return linkedMapOf("id" to named.id, "point" to pointToMap(named.point))
    }

    private fun pointToMap(point: ManagedMapPoint): Map<String, Any> {
        return linkedMapOf(
            "x" to point.x,
            "y" to point.y,
            "z" to point.z,
            "yaw" to point.yaw.toDouble(),
            "pitch" to point.pitch.toDouble()
        )
    }
}

private fun Any?.toDoubleOrNull(): Double? {
    return when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }
}

private fun Double.isWholeNumber(): Boolean = abs(this - toLong().toDouble()) < 1.0E-6
