package org.katacr.kagamecenter.parkour

import org.katacr.kaGameCenter.api.GameModuleContext
import org.katacr.kaGameCenter.api.GameModuleProvider
import org.katacr.kaGameCenter.i18n.ModuleLanguage

class ParkourModuleProvider : GameModuleProvider {
    private var configService: ParkourConfigService? = null

    override fun onLoad(context: GameModuleContext) {
        val configService = ParkourConfigService(context.dataFolder)
        configService.reload()
        this.configService = configService
        val language = ModuleLanguage(
            context.plugin,
            context.languageManager,
            context.dataFolder,
            "lang"
        ) { path -> javaClass.classLoader.getResourceAsStream(path) }
        language.reload()
        context.registerModule(ParkourGameModule(configService, context.worldService, language, context.packetService, context.roomManager, context.resultService))
        context.registerAdminCommand(
            ParkourAdminCommand(
                configService,
                context.selectionService,
                context.packetService,
                language,
                context.mapEditorService,
                context.managedGameCatalog
            )
        )
        context.registerGameEditor(
            ParkourManagedGameEditor(
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
        context.registerListener(ParkourListener(context.roomManager))
    }

    override fun onUnload() {
        configService = null
    }
}
