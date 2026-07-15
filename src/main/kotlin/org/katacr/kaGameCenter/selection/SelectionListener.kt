package org.katacr.kaGameCenter.selection

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.katacr.kaGameCenter.i18n.LanguageManager

class SelectionListener(
    private val selectionService: SelectionService,
    private val languageManager: LanguageManager
) : Listener {

    @EventHandler(ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!player.hasPermission("kagamecenter.admin")) return
        if (event.item?.type != Material.STONE_AXE) return

        val action = event.action
        val isFirst = action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR
        val isSecond = action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR
        if (!isFirst && !isSecond) return

        event.isCancelled = true
        val location = event.clickedBlock?.location ?: player.location.clone().apply {
            x = blockX.toDouble()
            y = blockY.toDouble()
            z = blockZ.toDouble()
        }
        val selection = if (isFirst) {
            selectionService.setFirst(player, location)
        } else {
            selectionService.setSecond(player, location)
        }
        val point = if (isFirst) "pos1" else "pos2"
        player.sendMessage(Component.text(languageManager.getMessage(
            "selection.point_set",
            point,
            location.world?.name ?: "-",
            location.blockX,
            location.blockY,
            location.blockZ
        )))
        if (selection != null) {
            player.sendMessage(Component.text(languageManager.getMessage(
                "selection.ready",
                selection.worldName ?: "-",
                selection.minX,
                selection.minY,
                selection.minZ,
                selection.maxX,
                selection.maxY,
                selection.maxZ
            )))
        }
    }
}
