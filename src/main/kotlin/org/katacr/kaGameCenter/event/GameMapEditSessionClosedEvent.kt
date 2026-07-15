package org.katacr.kaGameCenter.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.Collections
import java.util.UUID

/** 在共享地图编辑会话保存、编辑者恢复和临时世界清理均完成后发布关闭快照。 */
class GameMapEditSessionClosedEvent(
    val sessionId: String,
    val worldName: String,
    editorIds: Collection<UUID>,
    val saveRequested: Boolean,
    val saveSucceeded: Boolean,
    val restoreEditorsRequested: Boolean,
    val worldCleanupSucceeded: Boolean
) : Event() {
    val editorIds: Set<UUID> = immutableCopy(editorIds)

    /** 返回本事件的 Bukkit 处理器列表。 */
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        /** 创建保留迭代顺序且不允许监听器修改的编辑者 UUID 集合。 */
        private fun immutableCopy(values: Collection<UUID>): Set<UUID> {
            return Collections.unmodifiableSet(LinkedHashSet(values))
        }

        /** 返回 Bukkit 注册监听器所需的静态处理器列表。 */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
