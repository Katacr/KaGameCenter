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

        val clickedBlock = event.clickedBlock ?: return
        val action = event.action
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return

        event.isCancelled = true
        val selection = if (action == Action.LEFT_CLICK_BLOCK) {
            selectionService.setFirst(player, clickedBlock.location)
        } else {
            selectionService.setSecond(player, clickedBlock.location)
        }
        val point = if (action == Action.LEFT_CLICK_BLOCK) "pos1" else "pos2"
        player.sendMessage(Component.text(languageManager.getMessage(
            "selection.point_set",
            point,
            clickedBlock.world.name,
            clickedBlock.x,
            clickedBlock.y,
            clickedBlock.z
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
