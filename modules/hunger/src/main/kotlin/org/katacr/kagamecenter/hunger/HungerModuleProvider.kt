package org.katacr.kagamecenter.hunger

import org.katacr.kaGameCenter.api.GameModuleContext
import org.katacr.kaGameCenter.api.GameModuleProvider
import org.katacr.kaGameCenter.chat.GameChatChannel
import org.katacr.kaGameCenter.i18n.ModuleLanguage

/** 注册 Hunger 模块、事件路由、管理员命令和托管游戏编辑器。 */
class HungerModuleProvider : GameModuleProvider {
    private var configService: HungerConfigService? = null

    override fun onLoad(context: GameModuleContext) {
        val configService = HungerConfigService(context.dataFolder, context.managedMapPointService)
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
                    "hunger.chat_team",
                    chat.team?.displayName ?: "-",
                    chat.player.name,
                    chat.message
                )
                GameChatChannel.ROOM -> language.getMessage(
                    "hunger.chat_room",
                    chat.room.id,
                    chat.player.name,
                    chat.message
                )
                GameChatChannel.GLOBAL -> null
            }
        }

        context.registerModule(
            HungerGameModule(
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
                spawnAssignmentService = context.spawnAssignmentService
            )
        )
        context.registerAdminCommand(
            HungerAdminCommand(
                configService = configService,
                selectionService = context.selectionService,
                packetService = context.packetService,
                language = language,
                mapEditorService = context.mapEditorService,
                managedGameCatalog = context.managedGameCatalog,
                mapPointService = context.managedMapPointService
            )
        )
        context.registerGameEditor(
            HungerManagedGameEditor(
                configService = configService,
                language = language,
                selectionService = context.selectionService,
                packetService = context.packetService,
                worldService = context.worldService,
                mapEditorService = context.mapEditorService,
                managedGameCatalog = context.managedGameCatalog,
                menuService = context.menuService,
                mapPointService = context.managedMapPointService,
                pointCaptureService = context.editorPointCaptureService
            )
        )
        context.registerListener(HungerListener(context.roomManager))
    }

    override fun onUnload() {
        configService = null
    }
}
