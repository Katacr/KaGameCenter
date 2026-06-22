package org.katacr.kaGameCenter.selection

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection

data class RegionSelection(
    val worldName: String?,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int
) {
    fun contains(location: Location, ignoreWorld: Boolean = false): Boolean {
        if (!ignoreWorld && worldName != null && location.world?.name != worldName) return false
        return location.x >= minX &&
            location.x <= maxX + 1.0 &&
            location.y >= minY &&
            location.y <= maxY + 1.0 &&
            location.z >= minZ &&
            location.z <= maxZ + 1.0
    }

    fun center(world: World): Location {
        return Location(
            world,
            (minX + maxX + 1) / 2.0,
            (minY + maxY + 1) / 2.0,
            (minZ + maxZ + 1) / 2.0
        )
    }

    fun edgeLocations(world: World, maxPoints: Int = 96): List<Location> {
        val points = linkedSetOf<BlockPoint>()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                points.add(BlockPoint(x, y, minZ))
                points.add(BlockPoint(x, y, maxZ))
            }
        }
        for (z in minZ..maxZ) {
            for (y in minY..maxY) {
                points.add(BlockPoint(minX, y, z))
                points.add(BlockPoint(maxX, y, z))
            }
        }
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                points.add(BlockPoint(x, minY, z))
                points.add(BlockPoint(x, maxY, z))
            }
        }
        val step = if (points.size <= maxPoints) 1 else (points.size / maxPoints).coerceAtLeast(1)
        return points
            .asSequence()
            .withIndex()
            .filter { indexed -> indexed.index % step == 0 }
            .take(maxPoints)
            .map { (_, point) -> Location(world, point.x.toDouble(), point.y.toDouble(), point.z.toDouble()) }
            .toList()
    }

    fun writeTo(section: ConfigurationSection) {
        section.set("min.x", minX)
        section.set("min.y", minY)
        section.set("min.z", minZ)
        section.set("max.x", maxX)
        section.set("max.y", maxY)
        section.set("max.z", maxZ)
    }

    private data class BlockPoint(val x: Int, val y: Int, val z: Int)

    companion object {
        fun from(first: Location, second: Location): RegionSelection? {
            val firstWorld = first.world ?: return null
            val secondWorld = second.world ?: return null
            if (firstWorld.name != secondWorld.name) return null
            return RegionSelection(
                worldName = firstWorld.name,
                minX = minOf(first.blockX, second.blockX),
                minY = minOf(first.blockY, second.blockY),
                minZ = minOf(first.blockZ, second.blockZ),
                maxX = maxOf(first.blockX, second.blockX),
                maxY = maxOf(first.blockY, second.blockY),
                maxZ = maxOf(first.blockZ, second.blockZ)
            )
        }

        fun read(section: ConfigurationSection?): RegionSelection? {
            if (section == null) return null
            return RegionSelection(
                worldName = section.getString("world"),
                minX = section.getInt("min.x"),
                minY = section.getInt("min.y"),
                minZ = section.getInt("min.z"),
                maxX = section.getInt("max.x"),
                maxY = section.getInt("max.y"),
                maxZ = section.getInt("max.z")
            )
        }
    }
}
