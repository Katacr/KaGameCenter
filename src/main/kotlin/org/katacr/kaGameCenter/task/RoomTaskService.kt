package org.katacr.kaGameCenter.task

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class RoomTaskService(
    private val plugin: JavaPlugin
) {
    private val tasksByRoom = linkedMapOf<String, MutableSet<BukkitTask>>()

    @Synchronized
    fun track(roomId: String, task: BukkitTask): BukkitTask {
        tasksByRoom.getOrPut(roomId) { linkedSetOf() }.add(task)
        return task
    }

    fun runTask(roomId: String, action: Runnable): BukkitTask {
        return track(roomId, plugin.server.scheduler.runTask(plugin, action))
    }

    fun runTaskLater(roomId: String, delayTicks: Long, action: Runnable): BukkitTask {
        return track(roomId, plugin.server.scheduler.runTaskLater(plugin, action, delayTicks))
    }

    fun runTaskTimer(roomId: String, delayTicks: Long, periodTicks: Long, action: Runnable): BukkitTask {
        return track(roomId, plugin.server.scheduler.runTaskTimer(plugin, action, delayTicks, periodTicks))
    }

    @Synchronized
    fun cancelRoom(roomId: String) {
        tasksByRoom.remove(roomId)?.forEach { task ->
            if (!task.isCancelled) task.cancel()
        }
    }

    @Synchronized
    fun cancelAll() {
        tasksByRoom.keys.toList().forEach(::cancelRoom)
        tasksByRoom.clear()
    }
}
