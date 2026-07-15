package org.katacr.kagamecenter.skywars

import org.katacr.kaGameCenter.api.GameModuleContext
import org.katacr.kaGameCenter.api.GameModuleProvider
import org.katacr.kaGameCenter.chat.GameChatChannel
import org.katacr.kaGameCenter.i18n.ModuleLanguage

/** 装配并注册 SkyWars 模块、监听器、管理命令和托管游戏编辑器。 */
class SkyWarsModuleProvider : GameModuleProvider {
    private var configService: SkyWarsConfigService? = null

    override fun onLoad(context: GameModuleContext) {
        val configService = SkyWarsConfigService(context.dataFolder, context.managedMapPointService)
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
                    "skywars.chat_team",
                    chat.team?.displayName ?: "-",
                    chat.player.name,
                    chat.message
                )
                GameChatChannel.ROOM -> language.getMessage(
                    "skywars.chat_room",
                    chat.room.id,
                    chat.player.name,
                    chat.message
                )
                GameChatChannel.GLOBAL -> null
            }
        }

        context.registerModule(
            SkyWarsGameModule(
                configService = configService,
                worldService = context.worldService,
                language = language,
                packetService = context.packetService,
                roomManager = context.roomManager,
                resultService = context.resultService,
                playerRuntimeStateService = context.playerRuntimeStateService,
                roomBroadcastService = context.roomBroadcastService,
                nametagService = context.nametagService,
                eliminationService = context.eliminationService,
                roomResourceScopeService = context.roomResourceScopeService,
                spawnAssignmentService = context.spawnAssignmentService,
                teamService = context.teamService,
                teamAssignmentService = context.teamAssignmentService
            )
        )
        context.registerAdminCommand(
            SkyWarsAdminCommand(
                configService,
                context.selectionService,
                context.packetService,
                language,
                context.mapEditorService,
                context.managedGameCatalog,
                context.managedMapPointService
            )
        )
        context.registerGameEditor(
            SkyWarsManagedGameEditor(
                configService,
                language,
                context.selectionService,
                context.packetService,
                context.worldService,
                context.mapEditorService,
                context.managedGameCatalog,
                context.menuService,
                context.managedMapPointService,
                context.editorPointCaptureService
            )
        )
        context.registerListener(SkyWarsListener(context.roomManager))
    }

    override fun onUnload() {
        configService = null
    }
}
