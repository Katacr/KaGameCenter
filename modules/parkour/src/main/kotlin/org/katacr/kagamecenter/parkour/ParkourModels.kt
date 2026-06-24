package org.katacr.kagamecenter.parkour

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import net.kyori.adventure.text.format.NamedTextColor
import org.katacr.kaGameCenter.selection.RegionSelection
import kotlin.math.abs

data class ParkourConfig(
    val enabled: Boolean,
    val displayName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val startCountdownSeconds: Int,
    val finishCountdownSeconds: Int,
    val resultDisplaySeconds: Int,
    val closeDelaySeconds: Int,
    val fallY: Double,
    val checkpointGlowSeconds: Int,
    val checkpointGlowColor: String,
    val rewards: ParkourRewardConfig,
    val maps: Map<String, ParkourMapConfig>
) {
    fun firstMap(): ParkourMapConfig? = maps.values.firstOrNull()
}

data class ParkourRewardConfig(
    val enabled: Boolean,
    val basePoints: Int,
    val minimumPoints: Int,
    val timePenaltyPerSecond: Int,
    val rankBonus: List<Int>
)

data class ParkourMapConfig(
    val id: String,
    val displayName: String,
    val template: String,
    val routes: Map<String, ParkourRouteConfig>
) {
    fun firstRoute(): ParkourRouteConfig? = routes.values.firstOrNull()
}

data class ParkourRouteConfig(
    val id: String,
    val displayName: String,
    val maxPlayers: Int,
    val lobby: ParkourPoint?,
    val start: ParkourStartConfig?,
    val checkpoints: List<ParkourCheckpointConfig>,
    val finish: ParkourFinishConfig?,
    val fallY: Double?,
    val buffs: List<ParkourBuffConfig>
) {
    fun totalGoals(): Int = checkpoints.size + 1
}

data class ParkourStartConfig(
    val region: RegionSelection?,
    val spawn: ParkourPoint?
)

data class ParkourCheckpointConfig(
    val id: String,
    val displayName: String,
    val region: RegionSelection,
    val respawn: ParkourPoint,
    val glowRegion: RegionSelection?
)

data class ParkourFinishConfig(
    val region: RegionSelection,
    val glowRegion: RegionSelection?
)

data class ParkourBuffConfig(
    val id: String,
    val type: String,
    val point: ParkourPoint,
    val color: String,
    val durationSeconds: Int,
    val amplifier: Int,
    val respawnSeconds: Int
)

data class ParkourPoint(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    fun toLocation(world: World): Location {
        val adjustedX = if (x.isWholeNumber()) x + 0.5 else x
        val adjustedZ = if (z.isWholeNumber()) z + 0.5 else z
        return Location(world, adjustedX, y, adjustedZ, yaw, pitch)
    }

    fun writeTo(section: ConfigurationSection) {
        section.set("x", x)
        section.set("y", y)
        section.set("z", z)
        section.set("yaw", yaw.toDouble())
        section.set("pitch", pitch.toDouble())
    }

    companion object {
        fun from(location: Location): ParkourPoint {
            return ParkourPoint(location.x, location.y, location.z, location.yaw, location.pitch)
        }

        fun fromBlock(location: Location): ParkourPoint {
            return ParkourPoint(
                x = location.blockX.toDouble(),
                y = location.blockY.toDouble(),
                z = location.blockZ.toDouble(),
                yaw = location.yaw,
                pitch = location.pitch
            )
        }

        fun read(section: ConfigurationSection?): ParkourPoint? {
            if (section == null) return null
            if (!section.contains("x") || !section.contains("y") || !section.contains("z")) return null
            return ParkourPoint(
                x = section.getDouble("x"),
                y = section.getDouble("y"),
                z = section.getDouble("z"),
                yaw = section.getDouble("yaw", 0.0).toFloat(),
                pitch = section.getDouble("pitch", 0.0).toFloat()
            )
        }
    }
}

private fun Double.isWholeNumber(): Boolean = abs(this - toLong().toDouble()) < 1.0E-6

internal fun RegionSelection.withoutWorld(): RegionSelection {
    return if (worldName == null) this else copy(worldName = null)
}

fun parseNamedTextColor(value: String?, fallback: NamedTextColor): NamedTextColor {
    return when (value?.lowercase()) {
        "black" -> NamedTextColor.BLACK
        "dark_blue", "blue" -> NamedTextColor.BLUE
        "dark_green", "green" -> NamedTextColor.GREEN
        "dark_aqua", "aqua", "cyan" -> NamedTextColor.AQUA
        "dark_red", "red" -> NamedTextColor.RED
        "dark_purple", "purple" -> NamedTextColor.LIGHT_PURPLE
        "gold", "orange" -> NamedTextColor.GOLD
        "gray", "grey" -> NamedTextColor.GRAY
        "dark_gray", "dark_grey" -> NamedTextColor.DARK_GRAY
        "light_purple", "pink" -> NamedTextColor.LIGHT_PURPLE
        "yellow" -> NamedTextColor.YELLOW
        "white" -> NamedTextColor.WHITE
        else -> fallback
    }
}
