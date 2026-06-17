@file:Suppress("UnstableApiUsage")

package org.katacr.kaGameCenter.dialog

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.i18n.LanguageManager

class GameCenterDialogService(private val languageManager: LanguageManager) {

    fun openMainDialog(player: Player) {
        val body = listOf(
            DialogBody.plainMessage(Component.text(languageManager.getMessage("dialog.main_body_1")), 260),
            DialogBody.plainMessage(Component.text(languageManager.getMessage("dialog.main_body_2")), 260)
        )

        val gamesButton = ActionButton.builder(Component.text(languageManager.getMessage("dialog.button_games")))
            .width(120)
            .action(
                DialogAction.customClick({ _, audience ->
                    audience.sendMessage(Component.text(languageManager.getMessage("dialog.action_games")))
                }, ClickCallback.Options.builder().build())
            )
            .build()

        val roomsButton = ActionButton.builder(Component.text(languageManager.getMessage("dialog.button_rooms")))
            .width(120)
            .action(
                DialogAction.customClick({ _, audience ->
                    audience.sendMessage(Component.text(languageManager.getMessage("dialog.action_rooms")))
                }, ClickCallback.Options.builder().build())
            )
            .build()

        val mapsButton = ActionButton.builder(Component.text(languageManager.getMessage("dialog.button_maps")))
            .width(120)
            .action(
                DialogAction.customClick({ _, audience ->
                    audience.sendMessage(Component.text(languageManager.getMessage("dialog.action_maps")))
                }, ClickCallback.Options.builder().build())
            )
            .build()

        val base = DialogBase.builder(Component.text(languageManager.getMessage("dialog.main_title")))
            .body(body)
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .build()

        player.showDialog(
            Dialog.create {
                it.empty()
                    .base(base)
                    .type(DialogType.multiAction(listOf(gamesButton, roomsButton, mapsButton), null, 3))
            }
        )
    }
}
