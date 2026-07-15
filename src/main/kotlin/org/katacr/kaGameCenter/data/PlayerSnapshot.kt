package org.katacr.kaGameCenter.data

import org.bukkit.GameMode
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.UUID

data class PlayerSnapshot(
    val location: Location,
    val gameMode: GameMode,
    val health: Double,
    val maxHealthBaseValue: Double,
    val absorptionAmount: Double,
    val healthScaled: Boolean,
    val healthScale: Double,
    val foodLevel: Int,
    val saturation: Float,
    val level: Int,
    val exp: Float,
    val totalExperience: Int,
    val fireTicks: Int,
    val freezeTicks: Int,
    val remainingAir: Int,
    val arrowsInBody: Int,
    val beeStingersInBody: Int,
    val fallDistance: Float,
    val velocity: Vector,
    val allowFlight: Boolean,
    val flying: Boolean,
    val invisible: Boolean,
    val invulnerable: Boolean,
    val collidable: Boolean,
    val collidableExemptions: Set<UUID>,
    val inventory: Array<ItemStack?>,
    val armorContents: Array<ItemStack?>,
    val extraContents: Array<ItemStack?>,
    val enderChestContents: Array<ItemStack?>
) {
    /** 将完整玩家快照写入插件私有 YAML，不依赖临时房间世界对象继续存活。 */
    fun writeTo(config: YamlConfiguration) {
        config.set("version", 1)
        config.set("location.world", location.world?.name)
        config.set("location.world-id", location.world?.uid?.toString())
        config.set("location.x", location.x)
        config.set("location.y", location.y)
        config.set("location.z", location.z)
        config.set("location.yaw", location.yaw.toDouble())
        config.set("location.pitch", location.pitch.toDouble())
        config.set("game-mode", gameMode.name)
        config.set("health", health)
        config.set("max-health-base-value", maxHealthBaseValue)
        config.set("absorption-amount", absorptionAmount)
        config.set("health-scaled", healthScaled)
        config.set("health-scale", healthScale)
        config.set("food-level", foodLevel)
        config.set("saturation", saturation.toDouble())
        config.set("level", level)
        config.set("exp", exp.toDouble())
        config.set("total-experience", totalExperience)
        config.set("fire-ticks", fireTicks)
        config.set("freeze-ticks", freezeTicks)
        config.set("remaining-air", remainingAir)
        config.set("arrows-in-body", arrowsInBody)
        config.set("bee-stingers-in-body", beeStingersInBody)
        config.set("fall-distance", fallDistance.toDouble())
        config.set("velocity.x", velocity.x)
        config.set("velocity.y", velocity.y)
        config.set("velocity.z", velocity.z)
        config.set("allow-flight", allowFlight)
        config.set("flying", flying)
        config.set("invisible", invisible)
        config.set("invulnerable", invulnerable)
        config.set("collidable", collidable)
        config.set("collidable-exemptions", collidableExemptions.map(UUID::toString))
        writeItems(config, "inventory", inventory)
        writeItems(config, "armor", armorContents)
        writeItems(config, "extra", extraContents)
        writeItems(config, "ender-chest", enderChestContents)
    }

    /** 以槽位节点保存物品数组，避免 YAML 列表丢弃中间空槽。 */
    private fun writeItems(config: YamlConfiguration, path: String, items: Array<ItemStack?>) {
        config.set(path, null)
        config.set("$path.size", items.size)
        items.forEachIndexed { index, item ->
            if (item != null) config.set("$path.items.$index", item)
        }
    }

    companion object {
        /** 从持久 YAML 重建快照；未知世界保留为空，由恢复服务选择安全出生点。 */
        fun read(config: YamlConfiguration): PlayerSnapshot? {
            if (config.getInt("version", 0) != 1) return null
            val world = config.getString("location.world-id")?.let {
                runCatching { Bukkit.getWorld(UUID.fromString(it)) }.getOrNull()
            } ?: config.getString("location.world")?.let(Bukkit::getWorld)
            val location = Location(
                world,
                finiteDouble(config, "location.x", world?.spawnLocation?.x ?: 0.0),
                finiteDouble(config, "location.y", world?.spawnLocation?.y ?: 64.0),
                finiteDouble(config, "location.z", world?.spawnLocation?.z ?: 0.0),
                finiteDouble(config, "location.yaw", 0.0).toFloat(),
                finiteDouble(config, "location.pitch", 0.0).toFloat()
            )
            val gameMode = config.getString("game-mode")?.let {
                runCatching { GameMode.valueOf(it) }.getOrNull()
            } ?: GameMode.SURVIVAL
            return PlayerSnapshot(
                location = location,
                gameMode = gameMode,
                health = finiteDouble(config, "health", 20.0).coerceAtLeast(0.0),
                maxHealthBaseValue = finiteDouble(config, "max-health-base-value", 20.0).coerceIn(1.0, 2048.0),
                absorptionAmount = finiteDouble(config, "absorption-amount", 0.0).coerceAtLeast(0.0),
                healthScaled = config.getBoolean("health-scaled", false),
                healthScale = finiteDouble(config, "health-scale", 20.0).coerceIn(1.0, 2048.0),
                foodLevel = config.getInt("food-level", 20).coerceIn(0, 20),
                saturation = finiteDouble(config, "saturation", 5.0).toFloat().coerceIn(0.0f, 20.0f),
                level = config.getInt("level", 0).coerceAtLeast(0),
                exp = finiteDouble(config, "exp", 0.0).toFloat().coerceIn(0.0f, 1.0f),
                totalExperience = config.getInt("total-experience", 0).coerceAtLeast(0),
                fireTicks = config.getInt("fire-ticks", 0),
                freezeTicks = config.getInt("freeze-ticks", 0).coerceAtLeast(0),
                remainingAir = config.getInt("remaining-air", 300).coerceAtLeast(0),
                arrowsInBody = config.getInt("arrows-in-body", 0).coerceAtLeast(0),
                beeStingersInBody = config.getInt("bee-stingers-in-body", 0).coerceAtLeast(0),
                fallDistance = finiteDouble(config, "fall-distance", 0.0).toFloat().coerceAtLeast(0.0f),
                velocity = Vector(
                    finiteDouble(config, "velocity.x", 0.0),
                    finiteDouble(config, "velocity.y", 0.0),
                    finiteDouble(config, "velocity.z", 0.0)
                ),
                allowFlight = config.getBoolean("allow-flight", false),
                flying = config.getBoolean("flying", false),
                invisible = config.getBoolean("invisible", false),
                invulnerable = config.getBoolean("invulnerable", false),
                collidable = config.getBoolean("collidable", true),
                collidableExemptions = config.getStringList("collidable-exemptions")
                    .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                    .toSet(),
                inventory = readItems(config, "inventory", 41),
                armorContents = readItems(config, "armor", 4),
                extraContents = readItems(config, "extra", 1),
                enderChestContents = readItems(config, "ender-chest", 27)
            )
        }

        fun capture(player: Player): PlayerSnapshot {
            return PlayerSnapshot(
                location = player.location.clone(),
                gameMode = player.gameMode,
                health = player.health,
                maxHealthBaseValue = player.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: 20.0,
                absorptionAmount = player.absorptionAmount,
                healthScaled = player.isHealthScaled,
                healthScale = player.healthScale,
                foodLevel = player.foodLevel,
                saturation = player.saturation,
                level = player.level,
                exp = player.exp,
                totalExperience = player.totalExperience,
                fireTicks = player.fireTicks,
                freezeTicks = player.freezeTicks,
                remainingAir = player.remainingAir,
                arrowsInBody = player.arrowsInBody,
                beeStingersInBody = player.beeStingersInBody,
                fallDistance = player.fallDistance,
                velocity = player.velocity.clone(),
                allowFlight = player.allowFlight,
                flying = player.isFlying,
                invisible = player.isInvisible,
                invulnerable = player.isInvulnerable,
                collidable = player.isCollidable,
                collidableExemptions = player.collidableExemptions.toSet(),
                inventory = player.inventory.contents.clone(),
                armorContents = player.inventory.armorContents.clone(),
                extraContents = player.inventory.extraContents.clone(),
                enderChestContents = player.enderChest.contents.clone()
            )
        }

        /** 读取指定槽位数组，并对损坏或超大 size 使用安全上限。 */
        private fun readItems(config: YamlConfiguration, path: String, fallbackSize: Int): Array<ItemStack?> {
            val storedSize = config.getInt("$path.size", fallbackSize)
                .takeIf { it in 0..fallbackSize }
                ?: fallbackSize
            return arrayOfNulls<ItemStack>(fallbackSize).also { items ->
                for (index in 0 until storedSize) items[index] = config.getItemStack("$path.items.$index")
            }
        }

        /** 读取有限浮点值，损坏文件中的 NaN/Infinity 回退安全默认值。 */
        private fun finiteDouble(config: YamlConfiguration, path: String, fallback: Double): Double {
            return config.getDouble(path, fallback).takeIf(Double::isFinite) ?: fallback
        }
    }
}
