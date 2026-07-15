package org.katacr.kagamecenter.bedwars

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** 持久化每名玩家的 21 个快捷购买槽位，不把玩法偏好写入主插件战绩表。 */
class BedWarsQuickBuyService(private val dataFolder: File) {
    private val file = File(dataFolder, "quick-buy.yml")
    private var config = YamlConfiguration()

    init {
        reload()
    }

    /** 从模块私有文件重新加载玩家快捷购买偏好。 */
    @Synchronized
    fun reload() {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        config = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()
    }

    /** 返回恰好 21 个槽位，并按逻辑商品别名规范化及去除重复偏好。 */
    @Synchronized
    fun products(
        playerId: UUID,
        defaults: List<String>,
        aliases: Map<String, String> = emptyMap()
    ): List<String?> {
        val stored = config.getStringList(path(playerId))
        val source = if (stored.isEmpty()) defaults else stored
        val seen = linkedSetOf<String>()
        return List(SLOT_COUNT) { index ->
            val productId = source.getOrNull(index)?.takeUnless { it.isBlank() || it == EMPTY_SLOT }
            val canonicalId = productId?.let { aliases[it] ?: it }
            canonicalId?.takeIf(seen::add)
        }
    }

    /** 把规范化商品放入指定快捷槽，并移除同一逻辑商品的旧槽位。 */
    @Synchronized
    fun assign(
        playerId: UUID,
        slot: Int,
        productId: String,
        defaults: List<String>,
        aliases: Map<String, String> = emptyMap()
    ): Boolean {
        if (slot !in 0 until SLOT_COUNT || productId.isBlank()) return false
        val canonicalId = aliases[productId] ?: productId
        val products = products(playerId, defaults, aliases).toMutableList()
        products.indices.filter { products[it] == canonicalId }.forEach { products[it] = null }
        products[slot] = canonicalId
        write(playerId, products)
        return true
    }

    /** 清空指定快捷槽位并立即持久化。 */
    @Synchronized
    fun remove(
        playerId: UUID,
        slot: Int,
        defaults: List<String>,
        aliases: Map<String, String> = emptyMap()
    ): Boolean {
        if (slot !in 0 until SLOT_COUNT) return false
        val products = products(playerId, defaults, aliases).toMutableList()
        if (products[slot] == null) return false
        products[slot] = null
        write(playerId, products)
        return true
    }

    /** 将当前内存数据原子写回模块私有偏好文件。 */
    @Synchronized
    fun flush() {
        if (config.getKeys(false).isEmpty() && !file.exists()) return
        val temporary = File(dataFolder, "${file.name}.tmp")
        config.save(temporary)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun write(playerId: UUID, products: List<String?>) {
        config.set(path(playerId), products.map { it ?: EMPTY_SLOT })
        flush()
    }

    private fun path(playerId: UUID): String = "players.$playerId"

    companion object {
        const val SLOT_COUNT = 21
        private const val EMPTY_SLOT = "_"
    }
}
