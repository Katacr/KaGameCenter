package org.katacr.kaGameCenter.menu.chest

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.scheduler.BukkitTask

class ChestMenuHolder(
    val menuId: String,
    val layout: List<String>,
    val buttons: ConfigurationSection?,
    val context: Map<String, String>,
    var currentPage: Int
) : InventoryHolder {
    private var backingInventory: Inventory? = null

    var updateTask: BukkitTask? = null
        private set

    val slotVariables: MutableMap<Int, Map<String, String>> = linkedMapOf()

    fun bind(inventory: Inventory) {
        this.backingInventory = inventory
    }

    override fun getInventory(): Inventory {
        return backingInventory ?: throw IllegalStateException("Chest menu inventory is not bound")
    }

    fun setUpdateTask(task: BukkitTask?) {
        updateTask?.cancel()
        updateTask = task
    }

    fun stopUpdate() {
        updateTask?.cancel()
        updateTask = null
    }

    fun iconAt(slot: Int): String? {
        if (slot < 0) return null
        val row = slot / 9
        val column = slot % 9
        val line = layout.getOrNull(row) ?: return null
        return line.getOrNull(column)?.toString()
    }
}
