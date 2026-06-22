package org.katacr.kagamecenter.tntwars

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import org.katacr.kaGameCenter.selection.RegionSelection
import java.util.UUID
import kotlin.math.abs

data class TntWarsConfig(
    val enabled: Boolean,
    val displayName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val startCountdownSeconds: Int,
    val durationSeconds: Int,
    val resultDisplaySeconds: Int,
    val closeDelaySeconds: Int,
    val defaultVoidY: Double,
    val itemIntervalSeconds: Int,
    val initialItemDelaySeconds: Int,
    val resistanceAmplifier: Int,
    val glowingEnabled: Boolean,
    val givePerPlayer: Int,
    val consumeOnUse: Boolean,
    val items: Map<TntWarsItemType, TntWarsItemConfig>,
    val maps: Map<String, TntWarsMapConfig>
) {
    fun firstMap(): TntWarsMapConfig? = maps.values.firstOrNull()
}

data class TntWarsMapConfig(
    val id: String,
    val displayName: String,
    val template: String
)

data class TntWarsGameConfig(
    val lobby: TntWarsPoint?,
    val spectatorSpawn: TntWarsPoint?,
    val redSpawn: TntWarsPoint?,
    val blueSpawn: TntWarsPoint?,
    val playRegion: RegionSelection?,
    val voidY: Double?
)

data class TntWarsPoint(
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
        fun from(location: Location): TntWarsPoint {
            return TntWarsPoint(location.x, location.y, location.z, location.yaw, location.pitch)
        }

        fun read(section: ConfigurationSection?): TntWarsPoint? {
            if (section == null) return null
            if (!section.contains("x") || !section.contains("y") || !section.contains("z")) return null
            return TntWarsPoint(
                x = section.getDouble("x"),
                y = section.getDouble("y"),
                z = section.getDouble("z"),
                yaw = section.getDouble("yaw", 0.0).toFloat(),
                pitch = section.getDouble("pitch", 0.0).toFloat()
            )
        }
    }
}

enum class TntWarsPhase {
    WAITING,
    COUNTDOWN,
    RUNNING,
    RESULT,
    CLOSING
}

enum class TntWarsTeam(
    val id: String,
    val languageKey: String
) {
    RED("red", "tntwars.team_red"),
    BLUE("blue", "tntwars.team_blue");

    fun other(): TntWarsTeam = if (this == RED) BLUE else RED

    companion object {
        fun fromId(id: String): TntWarsTeam? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}

enum class TntWarsItemType(
    val configKey: String,
    val languageKey: String
) {
    TNT_MINECART("tnt_minecart", "tntwars.item_tnt_minecart"),
    TNT("tnt", "tntwars.item_tnt"),
    LONG_TNT("long_tnt", "tntwars.item_long_tnt"),
    CREEPER("creeper", "tntwars.item_creeper"),
    FIREBALL("fireball", "tntwars.item_fireball"),
    TNT_BOW("tnt_bow", "tntwars.item_tnt_bow"),
    TNT_RAIN("tnt_rain", "tntwars.item_tnt_rain"),
    CREEPER_RAIN("creeper_rain", "tntwars.item_creeper_rain"),
    FIREBALL_RAIN("fireball_rain", "tntwars.item_fireball_rain");

    companion object {
        fun fromConfigKey(value: String): TntWarsItemType? {
            return entries.firstOrNull { it.configKey.equals(value, ignoreCase = true) }
        }
    }
}

data class TntWarsItemConfig(
    val enabled: Boolean,
    val weight: Int,
    val fuseTicks: Int,
    val velocity: Double,
    val power: Float,
    val durationSeconds: Int,
    val dropsPerSecond: Int
)

data class TntWarsPlayerState(
    var team: TntWarsTeam,
    var alive: Boolean = true,
    var lastBlastOwner: UUID? = null,
    var lastBlastAt: Long = 0L
)

internal fun RegionSelection.withoutWorld(): RegionSelection {
    return if (worldName == null) this else copy(worldName = null)
}

private fun Double.isWholeNumber(): Boolean = abs(this - toLong().toDouble()) < 1.0E-6
