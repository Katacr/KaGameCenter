package org.katacr.kaGameCenter.data

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class PlayerSnapshotService(private val plugin: JavaPlugin) {
    private val snapshots = linkedMapOf<UUID, PlayerSnapshot>()
    private val snapshotFolder = File(plugin.dataFolder, "data/player-snapshots")

    fun captureIfAbsent(player: Player) {
        if (snapshots.containsKey(player.uniqueId)) return
        check(!hasSnapshot(player.uniqueId)) {
            "Player ${player.uniqueId} still has a pending persisted snapshot"
        }
        val snapshot = PlayerSnapshot.capture(player)
        snapshots[player.uniqueId] = snapshot
        try {
            saveSnapshot(player.uniqueId, snapshot)
        } catch (error: Throwable) {
            if (snapshots[player.uniqueId] === snapshot) snapshots.remove(player.uniqueId)
            throw error
        }
    }

    fun restore(player: Player): Boolean {
        val snapshot = snapshots[player.uniqueId] ?: loadSnapshot(player.uniqueId)?.also {
            snapshots[player.uniqueId] = it
        } ?: return false
        val originalWorld = snapshot.location.world
        val loadedWorld = originalWorld?.let { Bukkit.getWorld(it.uid) ?: Bukkit.getWorld(it.name) }
        val destination = loadedWorld?.let { world ->
            snapshot.location.clone().apply { this.world = world }
        } ?: Bukkit.getWorlds().firstOrNull()?.spawnLocation
            ?: error("Cannot restore player ${player.uniqueId}: no loaded fallback world")
        check(player.teleport(destination)) {
            "Cannot restore player ${player.uniqueId}: teleport to ${destination.world?.name} was rejected"
        }

        player.gameMode = snapshot.gameMode
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)
        maxHealth?.baseValue = snapshot.maxHealthBaseValue
        player.healthScale = snapshot.healthScale
        player.isHealthScaled = snapshot.healthScaled
        player.inventory.contents = snapshot.inventory
        player.inventory.armorContents = snapshot.armorContents
        player.inventory.extraContents = snapshot.extraContents
        player.enderChest.contents = snapshot.enderChestContents
        player.health = snapshot.health.coerceAtMost(maxHealth?.value ?: snapshot.health)
        player.absorptionAmount = snapshot.absorptionAmount
        player.foodLevel = snapshot.foodLevel
        player.saturation = snapshot.saturation
        player.level = snapshot.level
        player.exp = snapshot.exp
        player.totalExperience = snapshot.totalExperience
        player.fireTicks = snapshot.fireTicks
        player.freezeTicks = snapshot.freezeTicks
        player.remainingAir = snapshot.remainingAir
        player.arrowsInBody = snapshot.arrowsInBody
        player.beeStingersInBody = snapshot.beeStingersInBody
        player.fallDistance = snapshot.fallDistance
        player.velocity = snapshot.velocity
        player.allowFlight = snapshot.allowFlight
        player.isFlying = snapshot.flying
        player.isInvisible = snapshot.invisible
        player.isInvulnerable = snapshot.invulnerable
        player.isCollidable = snapshot.collidable
        player.collidableExemptions.clear()
        player.collidableExemptions.addAll(snapshot.collidableExemptions)
        deleteSnapshotFiles(player.uniqueId, failOnError = true)
        if (snapshots[player.uniqueId] === snapshot) snapshots.remove(player.uniqueId)
        return true
    }

    fun clear(playerId: UUID) {
        snapshots.remove(playerId)
        deleteSnapshotFiles(playerId, failOnError = false)
    }

    /** 判断内存或磁盘是否仍有等待恢复的玩家快照。 */
    fun hasSnapshot(playerId: UUID): Boolean {
        return snapshots.containsKey(playerId) || snapshotFile(playerId).isFile || temporarySnapshotFile(playerId).isFile
    }

    /** 返回 UUID 对应的插件私有快照文件。 */
    private fun snapshotFile(playerId: UUID): File = File(snapshotFolder, "$playerId.yml")

    /** 返回原子写入中断后可能遗留的同目录临时快照文件。 */
    private fun temporarySnapshotFile(playerId: UUID): File = File(snapshotFolder, "$playerId.yml.tmp")

    /** 使用同目录临时文件原子替换玩家快照，避免停服或崩溃留下半写 YAML。 */
    private fun saveSnapshot(playerId: UUID, snapshot: PlayerSnapshot) {
        if (!snapshotFolder.exists() && !snapshotFolder.mkdirs()) {
            error("Cannot create player snapshot folder: ${snapshotFolder.absolutePath}")
        }
        val target = snapshotFile(playerId)
        val temporary = temporarySnapshotFile(playerId)
        try {
            YamlConfiguration().apply { snapshot.writeTo(this) }.save(temporary)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /** 从磁盘按需读取快照，损坏文件保留并记录警告以便人工取证。 */
    private fun loadSnapshot(playerId: UUID): PlayerSnapshot? {
        val file = snapshotFile(playerId).takeIf(File::isFile)
            ?: temporarySnapshotFile(playerId).takeIf(File::isFile)
            ?: return null
        return runCatching {
            PlayerSnapshot.read(YamlConfiguration.loadConfiguration(file))
                ?: error("Unsupported or damaged player snapshot")
        }
            .onFailure { plugin.logger.warning("Failed to load player snapshot ${file.absolutePath}: ${it.message}") }
            .getOrNull()
    }

    /** 删除已成功恢复或明确丢弃的正式与临时快照文件。 */
    private fun deleteSnapshotFiles(playerId: UUID, failOnError: Boolean) {
        listOf(snapshotFile(playerId), temporarySnapshotFile(playerId)).forEach { file ->
            if (!file.exists() || file.delete()) return@forEach
            val message = "Failed to delete player snapshot ${file.absolutePath}"
            if (failOnError) error(message) else plugin.logger.warning(message)
        }
    }
}
