package org.katacr.kagamecenter.blockhunt

import org.katacr.kaGameCenter.api.GameModuleContext
import org.katacr.kaGameCenter.api.GameModuleProvider
import org.katacr.kaGameCenter.chat.GameChatChannel
import org.katacr.kaGameCenter.i18n.ModuleLanguage

class BlockhuntModuleProvider : GameModuleProvider {
    private var configService: BlockhuntConfigService? = null

    override fun onLoad(context: GameModuleContext) {
        val configService = BlockhuntConfigService(context.dataFolder)
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
                    "blockhunt.chat_team",
                    chat.team?.displayName ?: "-",
                    chat.player.name,
                    chat.message
                )
                GameChatChannel.ROOM -> language.getMessage(
                    "blockhunt.chat_room",
                    chat.room.id,
                    chat.player.name,
                    chat.message
                )
                GameChatChannel.GLOBAL -> null
            }
        }

        context.registerModule(
            BlockhuntGameModule(
                configService = configService,
                worldService = context.worldService,
                language = language,
                packetService = context.packetService,
                roomManager = context.roomManager,
                teamService = context.teamService,
                teamAssignmentService = context.teamAssignmentService,
                resultService = context.resultService
            )
        )
        context.registerAdminCommand(
            BlockhuntAdminCommand(
                configService = configService,
                selectionService = context.selectionService,
                packetService = context.packetService,
                language = language,
                mapEditorService = context.mapEditorService,
                managedGameCatalog = context.managedGameCatalog
            )
        )
        context.registerGameEditor(
            BlockhuntManagedGameEditor(
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
        context.registerListener(BlockhuntListener(context.roomManager))
    }

    override fun onUnload() {
        configService = null
    }
}
