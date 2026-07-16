package org.katacr.kaGameCenter.editor

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.event.GameMapEditSessionClosedEvent
import org.katacr.kaGameCenter.i18n.LanguageManager
import java.util.Locale
import java.util.UUID

/** 表示骨头编辑工具当前连续采集的是方块、整数位置还是精确位置。 */
enum class EditorPointCaptureMode {
    BLOCK,
    POSITION,
    EXACT_POSITION
}

/**
 * 管理管理员骨头采点工具及模块保存回调。
 * 同一玩家同时只保留一个连续采集模式，模块卸载或编辑世界关闭时自动释放。
 */
class EditorPointCaptureService(
    private val plugin: JavaPlugin,
    private val languageManager: LanguageManager
) : Listener {
    private data class CaptureState(
        val ownerId: String,
        val mode: EditorPointCaptureMode,
        val handler: (Player, Location) -> Boolean,
        var captureCount: Int = 0
    )

    private val states = linkedMapOf<UUID, CaptureState>()
    private val toolKey = NamespacedKey(plugin, "editor_point_capture_tool")

    /** 开始连续采集方块；处理器返回 false 时结束当前模式。 */
    fun beginBlockCapture(player: Player, ownerId: String, handler: (Player, Location) -> Boolean) {
        begin(player, ownerId, EditorPointCaptureMode.BLOCK, handler)
    }

    /** 开始连续采集玩家脚下整数位置；处理器返回 false 时结束当前模式。 */
    fun beginPositionCapture(player: Player, ownerId: String, handler: (Player, Location) -> Boolean) {
        begin(player, ownerId, EditorPointCaptureMode.POSITION, handler)
    }

    /** 开始连续采集玩家脚下精确浮点位置；处理器返回 false 时结束当前模式。 */
    fun beginExactPositionCapture(player: Player, ownerId: String, handler: (Player, Location) -> Boolean) {
        begin(player, ownerId, EditorPointCaptureMode.EXACT_POSITION, handler)
    }

    /** 结束指定玩家的采集模式并移除服务发放的骨头工具。 */
    fun cancel(player: Player) {
        states.remove(player.uniqueId)
        removeIssuedTools(player)
    }

    /** 结束指定模块创建的全部采集模式，防止模块卸载后遗留回调。 */
    fun cancelOwner(ownerId: String) {
        val playerIds = states.filterValues { it.ownerId.equals(ownerId, ignoreCase = true) }.keys.toList()
        playerIds.forEach { playerId ->
            states.remove(playerId)
            plugin.server.getPlayer(playerId)?.let(::removeIssuedTools)
        }
    }

    /** 清空插件内全部采集状态和仍在线玩家的临时工具。 */
    fun clearAll() {
        states.keys.toList().forEach { playerId -> plugin.server.getPlayer(playerId)?.let(::removeIssuedTools) }
        states.clear()
    }

    /** 路由主手骨头点击到当前模块的方块或位置处理器。 */
    @EventHandler(ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val player = event.player
        val state = states[player.uniqueId] ?: return
        if (!player.hasPermission("kagamecenter.admin") || event.item?.type != Material.BONE) return
        event.isCancelled = true

        val location = when (state.mode) {
            EditorPointCaptureMode.BLOCK -> {
                if (event.action != Action.LEFT_CLICK_BLOCK || event.clickedBlock == null) {
                    player.sendMessage(Component.text(languageManager.getMessage("editor_capture.block_required")))
                    return
                }
                event.clickedBlock!!.location
            }
            EditorPointCaptureMode.POSITION -> {
                if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
                    player.sendMessage(Component.text(languageManager.getMessage("editor_capture.position_required")))
                    return
                }
                integerLocation(player.location)
            }
            EditorPointCaptureMode.EXACT_POSITION -> {
                if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
                    player.sendMessage(Component.text(languageManager.getMessage("editor_capture.position_required")))
                    return
                }
                player.location.clone()
            }
        }

        val keepCapturing = runCatching { state.handler(player, location) }
            .onFailure { error -> plugin.logger.warning("Editor point capture failed for ${state.ownerId}: ${error.message}") }
            .getOrDefault(false)
        if (!keepCapturing) {
            cancel(player)
            player.sendMessage(Component.text(languageManager.getMessage("editor_capture.stopped")))
            return
        }
        if (states[player.uniqueId] !== state) return
        state.captureCount++
        player.sendMessage(Component.text(languageManager.getMessage(
            "editor_capture.captured",
            state.captureCount,
            location.world?.name ?: "-",
            displayCoordinate(location.x, state.mode),
            displayCoordinate(location.y, state.mode),
            displayCoordinate(location.z, state.mode)
        )))
    }

    /** 玩家退出时释放采集闭包，避免保留离线 Player 或模块类加载器。 */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        states.remove(event.player.uniqueId)
    }

    /** 编辑会话关闭后清理其中全部编辑者的采点工具和状态。 */
    @EventHandler
    fun onEditSessionClosed(event: GameMapEditSessionClosedEvent) {
        event.editorIds.forEach { playerId ->
            states.remove(playerId)
            plugin.server.getPlayer(playerId)?.let(::removeIssuedTools)
        }
    }

    private fun begin(
        player: Player,
        ownerId: String,
        mode: EditorPointCaptureMode,
        handler: (Player, Location) -> Boolean
    ) {
        states[player.uniqueId] = CaptureState(ownerId, mode, handler)
        player.closeDialog()
        player.closeInventory()
        ensureTool(player)
        val key = when (mode) {
            EditorPointCaptureMode.BLOCK -> "editor_capture.block_started"
            EditorPointCaptureMode.POSITION -> "editor_capture.position_started"
            EditorPointCaptureMode.EXACT_POSITION -> "editor_capture.exact_position_started"
        }
        player.sendMessage(Component.text(languageManager.getMessage(key)))
    }

    private fun ensureTool(player: Player) {
        val inventory = player.inventory
        val existing = inventory.contents.indexOfFirst(::isIssuedTool)
        if (existing >= 0) {
            if (existing in 0..8) inventory.heldItemSlot = existing
            return
        }
        val slot = (0..8).firstOrNull { inventory.getItem(it)?.type?.isAir != false } ?: inventory.firstEmpty()
        if (slot < 0) {
            player.sendMessage(Component.text(languageManager.getMessage("editor_capture.inventory_full")))
            return
        }
        inventory.setItem(slot, captureTool())
        if (slot in 0..8) inventory.heldItemSlot = slot
    }

    private fun captureTool(): ItemStack {
        val item = ItemStack(Material.BONE)
        item.editMeta { meta ->
            meta.displayName(Component.text(languageManager.getMessage("editor_capture.tool_name"), NamedTextColor.AQUA))
            meta.lore(listOf(
                Component.text(languageManager.getMessage("editor_capture.tool_left"), NamedTextColor.GRAY),
                Component.text(languageManager.getMessage("editor_capture.tool_right"), NamedTextColor.GRAY)
            ))
            meta.persistentDataContainer.set(toolKey, PersistentDataType.BYTE, 1.toByte())
        }
        return item
    }

    private fun removeIssuedTools(player: Player) {
        val inventory = player.inventory
        inventory.contents.forEachIndexed { index, item ->
            if (isIssuedTool(item)) inventory.setItem(index, null)
        }
    }

    private fun isIssuedTool(item: ItemStack?): Boolean {
        return item?.type == Material.BONE && item.itemMeta.persistentDataContainer.has(toolKey, PersistentDataType.BYTE)
    }

    private fun integerLocation(location: Location): Location {
        return Location(
            location.world,
            location.blockX.toDouble(),
            location.blockY.toDouble(),
            location.blockZ.toDouble(),
            location.yaw,
            location.pitch
        )
    }

    /** 按采集模式显示整数坐标或最多三位小数的精确坐标。 */
    private fun displayCoordinate(value: Double, mode: EditorPointCaptureMode): Any {
        return if (mode == EditorPointCaptureMode.EXACT_POSITION) {
            String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')
        } else {
            value.toInt()
        }
    }
}
