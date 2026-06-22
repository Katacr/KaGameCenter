package org.katacr.kagamecenter.blockhunt

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Interaction
import org.katacr.kaGameCenter.selection.RegionSelection
import kotlin.math.abs

data class BlockhuntConfig(
    val enabled: Boolean,
    val displayName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val startCountdownSeconds: Int,
    val durationSeconds: Int,
    val hunterReleaseSeconds: Int,
    val frenzySeconds: Int,
    val resultDisplaySeconds: Int,
    val closeDelaySeconds: Int,
    val hunterRatio: Double,
    val caughtHiderBecomesHunter: Boolean,
    val hiderFrenzyAmplifier: Int,
    val doubleSneakMs: Long,
    val disguiseRefreshSeconds: Int,
    val disguiseWhitelist: List<Material>,
    val itemRefreshSeconds: Int,
    val pickupDurationSeconds: Int,
    val pickupScale: Float,
    val maxActivePickupsPerRole: Int,
    val hunterSnowballs: Int,
    val hunterGlowSeconds: Int,
    val hunterProbeRadius: Double,
    val hunterProbeUses: Int,
    val hiderBlindSeconds: Int,
    val hiderFreezeSeconds: Int,
    val hiderFakeBlockSeconds: Int,
    val hiderInvisibleSeconds: Int,
    val maps: Map<String, BlockhuntMapConfig>
) {
    fun firstMap(): BlockhuntMapConfig? = maps.values.firstOrNull()
}

data class BlockhuntMapConfig(
    val id: String,
    val displayName: String,
    val template: String
)

data class BlockhuntGameConfig(
    val lobby: BlockhuntPoint?,
    val hunterSpawn: BlockhuntPoint?,
    val hiderSpawn: BlockhuntPoint?,
    val playRegion: RegionSelection?,
    val itemSpawns: List<BlockhuntItemSpawn>
)

data class BlockhuntItemSpawn(
    val id: String,
    val point: BlockhuntPoint
)

data class BlockhuntPoint(
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
        fun from(location: Location): BlockhuntPoint {
            return BlockhuntPoint(location.x, location.y, location.z, location.yaw, location.pitch)
        }

        fun fromBlock(location: Location): BlockhuntPoint {
            return BlockhuntPoint(
                x = location.blockX.toDouble(),
                y = location.blockY.toDouble(),
                z = location.blockZ.toDouble(),
                yaw = location.yaw,
                pitch = location.pitch
            )
        }

        fun read(section: ConfigurationSection?): BlockhuntPoint? {
            if (section == null) return null
            if (!section.contains("x") || !section.contains("y") || !section.contains("z")) return null
            return BlockhuntPoint(
                x = section.getDouble("x"),
                y = section.getDouble("y"),
                z = section.getDouble("z"),
                yaw = section.getDouble("yaw", 0.0).toFloat(),
                pitch = section.getDouble("pitch", 0.0).toFloat()
            )
        }
    }
}

enum class BlockhuntPhase {
    WAITING,
    COUNTDOWN,
    HIDING,
    RUNNING,
    RESULT,
    CLOSING
}

enum class BlockhuntRole {
    HUNTER,
    HIDER
}

enum class BlockhuntPickupType(
    val material: Material,
    val languageKey: String
) {
    HUNTER_GLOW(Material.GLOWSTONE_DUST, "blockhunt.item_hunter_glow"),
    HUNTER_PROBE(Material.COMPASS, "blockhunt.item_hunter_probe"),
    HUNTER_SNOWBALLS(Material.SNOWBALL, "blockhunt.item_hunter_snowballs"),
    HIDER_BLIND(Material.INK_SAC, "blockhunt.item_hider_blind"),
    HIDER_FREEZE(Material.PACKED_ICE, "blockhunt.item_hider_freeze"),
    HIDER_FAKE_BLOCK(Material.ARMOR_STAND, "blockhunt.item_hider_fake_block"),
    HIDER_INVISIBLE(Material.PHANTOM_MEMBRANE, "blockhunt.item_hider_invisible")
}

data class BlockhuntPlayerState(
    var role: BlockhuntRole,
    var alive: Boolean = true,
    var locked: Boolean = false,
    var lockLocation: Location? = null,
    var lockedBlock: Block? = null,
    var lockedOriginalBlockData: BlockData? = null,
    var lockedHitbox: Interaction? = null,
    var disguise: Material = Material.OAK_PLANKS,
    var lastSneakAt: Long = 0L,
    var probeUsesLeft: Int = 0,
    var frozenTicks: Int = 0,
    var frozenLocation: Location? = null,
    var invisibleTicks: Int = 0
)

internal fun RegionSelection.withoutWorld(): RegionSelection {
    return if (worldName == null) this else copy(worldName = null)
}

private fun Double.isWholeNumber(): Boolean = abs(this - toLong().toDouble()) < 1.0E-6
