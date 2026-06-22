package org.katacr.kagamecenter.tntwars

import org.katacr.kaGameCenter.api.GameModuleContext
import org.katacr.kaGameCenter.api.GameModuleProvider
import org.katacr.kaGameCenter.chat.GameChatChannel
import org.katacr.kaGameCenter.i18n.ModuleLanguage

class TntWarsModuleProvider : GameModuleProvider {
    private var configService: TntWarsConfigService? = null

    override fun onLoad(context: GameModuleContext) {
        val configService = TntWarsConfigService(context.dataFolder)
        configService.reload()
        this.configService = configService

        val language = ModuleLanguage(
            context.plugin,
            context.languageManager,
            context.dataFolder,
            "lang"
        ) { path -> javaClass.classLoader.getResourceAsStream(path) }
        language.reload()

        context.registerChatFormatter { chat ->
            when (chat.channel) {
                GameChatChannel.TEAM -> language.getMessage(
                    "tntwars.chat_team",
                    chat.team?.displayName ?: "-",
                    chat.player.name,
                    chat.message
                )
                GameChatChannel.ROOM -> language.getMessage(
                    "tntwars.chat_room",
                    chat.room.id,
                    chat.player.name,
                    chat.message
                )
                GameChatChannel.GLOBAL -> null
            }
        }

        context.registerModule(
            TntWarsGameModule(
                plugin = context.plugin,
                configService = configService,
                worldService = context.worldService,
                language = language,
                roomManager = context.roomManager,
                teamService = context.teamService,
                teamAssignmentService = context.teamAssignmentService,
                roomTaskService = context.roomTaskService,
                entityOwnershipService = context.entityOwnershipService,
                resultService = context.resultService,
                playerRuntimeStateService = context.playerRuntimeStateService,
                roomBroadcastService = context.roomBroadcastService
            )
        )
        context.registerGameEditor(
            TntWarsManagedGameEditor(
                configService = configService,
                language = language,
                selectionService = context.selectionService,
                packetService = context.packetService,
                worldService = context.worldService,
                mapEditorService = context.mapEditorService,
                managedGameCatalog = context.managedGameCatalog,
                menuService = context.menuService
            )
        )
        context.registerListener(TntWarsListener(context.roomManager))
    }

    override fun onUnload() {
        configService = null
    }
}
