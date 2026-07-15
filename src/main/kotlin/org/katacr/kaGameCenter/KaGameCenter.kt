package org.katacr.kaGameCenter

import net.byteflux.libby.BukkitLibraryManager
import net.byteflux.libby.Library
import org.katacr.kaGameCenter.api.GameCenterApi
import org.katacr.kaGameCenter.broadcast.RoomBroadcastService
import org.katacr.kaGameCenter.chat.GameChatListener
import org.katacr.kaGameCenter.chat.GameChatService
import org.katacr.kaGameCenter.chat.GlobalChatCommand
import org.katacr.kaGameCenter.chat.RoomChatCommand
import org.katacr.kaGameCenter.data.PlayerSnapshotService
import org.katacr.kaGameCenter.command.KaGameCenterCommand
import org.katacr.kaGameCenter.command.ModuleAdminCommand
import org.katacr.kaGameCenter.data.DatabaseConfig
import org.katacr.kaGameCenter.data.MemoryStatsRepository
import org.katacr.kaGameCenter.data.PlayerStatsService
import org.katacr.kaGameCenter.data.SqlStatsRepository
import org.katacr.kaGameCenter.dialog.GameCenterDialogService
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.display.GameDisplayService
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.editor.EditorPointCaptureService
import org.katacr.kaGameCenter.elimination.PlayerEliminationService
import org.katacr.kaGameCenter.entity.RoomEntityOwnershipService
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.GameRegistry
import org.katacr.kaGameCenter.game.GameManager
import org.katacr.kaGameCenter.game.GameMapManager
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.listener.GamePlayerListener
import org.katacr.kaGameCenter.map.ManagedMapPointService
import org.katacr.kaGameCenter.menu.chest.ChestMenuListener
import org.katacr.kaGameCenter.menu.chest.ChestMenuService
import org.katacr.kaGameCenter.module.ManagedGameModuleService
import org.katacr.kaGameCenter.nametag.PlayerNametagService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.packet.PacketEventsDispatchService
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.resource.RoomResourceScopeService
import org.katacr.kaGameCenter.selection.SelectionListener
import org.katacr.kaGameCenter.selection.SelectionService
import org.katacr.kaGameCenter.spectator.SpectatorService
import org.katacr.kaGameCenter.spawn.SpawnAssignmentService
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.task.RoomTaskService
import org.katacr.kaGameCenter.velocity.NoopVelocityBridgeService
import org.katacr.kaGameCenter.velocity.RedisVelocityBridgeService
import org.katacr.kaGameCenter.velocity.VelocityBridgeConfig
import org.katacr.kaGameCenter.velocity.VelocityBridgeService
import org.katacr.kaGameCenter.world.TemporaryWorldService
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class KaGameCenter : JavaPlugin() {

    private lateinit var dialogService: GameCenterDialogService
    private lateinit var registry: GameRegistry
    private lateinit var gameManager: GameManager
    private lateinit var gameMapManager: GameMapManager
    private lateinit var temporaryWorldService: TemporaryWorldService
    private lateinit var statsService: PlayerStatsService
    private lateinit var snapshotService: PlayerSnapshotService
    private lateinit var roomManager: GameRoomManager
    private lateinit var languageManager: LanguageManager
    private lateinit var displayService: GameDisplayService
    private lateinit var menuService: GameCenterMenuService
    private lateinit var chestMenuService: ChestMenuService
    private lateinit var teamService: GameTeamService
    private lateinit var spectatorService: SpectatorService
    private lateinit var packetService: PacketDispatchService
    private lateinit var selectionService: SelectionService
    private lateinit var editorPointCaptureService: EditorPointCaptureService
    private lateinit var mapEditorService: MapEditorService
    private lateinit var managedGameCatalog: ManagedGameCatalogService
    private lateinit var gameCenterApi: GameCenterApi
    private lateinit var moduleService: ManagedGameModuleService
    private lateinit var velocityBridgeService: VelocityBridgeService
    private lateinit var chatService: GameChatService
    private lateinit var roomTaskService: RoomTaskService
    private lateinit var entityOwnershipService: RoomEntityOwnershipService
    private lateinit var teamAssignmentService: TeamAssignmentService
    private lateinit var resultService: GameResultService
    private lateinit var playerRuntimeStateService: PlayerRuntimeStateService
    private lateinit var roomBroadcastService: RoomBroadcastService
    private lateinit var nametagService: PlayerNametagService
    private lateinit var eliminationService: PlayerEliminationService
    private lateinit var roomResourceScopeService: RoomResourceScopeService
    private lateinit var managedMapPointService: ManagedMapPointService
    private lateinit var spawnAssignmentService: SpawnAssignmentService
    private val moduleAdminCommands = linkedMapOf<String, ModuleAdminCommand>()

    companion object {
        private var instanceApi: GameCenterApi? = null

        fun api(): GameCenterApi {
            return instanceApi ?: throw IllegalStateException("KaGameCenter API is not ready")
        }
    }

    override fun onLoad() {
        val librariesDir = File(dataFolder.parentFile.parentFile, "libraries")
        if (!librariesDir.exists()) {
            librariesDir.mkdirs()
        }

        val libraryManager = BukkitLibraryManager(this, librariesDir.absolutePath)
        libraryManager.addMavenCentral()
        libraryManager.addRepository("https://maven.aliyun.com/repository/public")

        val kotlinStd = Library.builder()
            .groupId("org{}jetbrains{}kotlin")
            .artifactId("kotlin-stdlib")
            .version("2.3.20")
            .build()

        val sqlite = Library.builder()
            .groupId("org{}xerial")
            .artifactId("sqlite-jdbc")
            .version("3.46.1.0")
            .build()

        val mysql = Library.builder()
            .groupId("com{}mysql")
            .artifactId("mysql-connector-j")
            .version("9.1.0")
            .build()

        val hikari = Library.builder()
            .groupId("com{}zaxxer")
            .artifactId("HikariCP")
            .version("5.1.0")
            .build()

        val gson = Library.builder()
            .groupId("com{}google{}code{}gson")
            .artifactId("gson")
            .version("2.10.1")
            .build()

        val lettuce = Library.builder()
            .groupId("io{}lettuce")
            .artifactId("lettuce-core")
            .version("6.3.2.RELEASE")
            .build()

        logger.info("Checking KaGameCenter runtime libraries...")
        libraryManager.loadLibrary(kotlinStd)
        libraryManager.loadLibrary(sqlite)
        libraryManager.loadLibrary(mysql)
        libraryManager.loadLibrary(hikari)
        libraryManager.loadLibrary(gson)
        libraryManager.loadLibrary(lettuce)
    }

    override fun onEnable() {
        saveDefaultConfig()

        languageManager = LanguageManager(this)
        languageManager.init()

        dialogService = GameCenterDialogService(languageManager)
        teamService = GameTeamService()
        teamAssignmentService = TeamAssignmentService(teamService)
        packetService = PacketEventsDispatchService(this)
        packetService.init()
        nametagService = PlayerNametagService(packetService)
        selectionService = SelectionService()
        editorPointCaptureService = EditorPointCaptureService(this, languageManager)
        spectatorService = SpectatorService(this, languageManager)
        displayService = GameDisplayService(this, languageManager, teamService)
        gameManager = GameManager(this)
        gameMapManager = GameMapManager(this, gameManager)
        registry = GameRegistry(gameManager)
        temporaryWorldService = TemporaryWorldService(this)
        val cleanedTemporaryWorlds = temporaryWorldService.cleanupStaleTemporaryWorlds()
        if (cleanedTemporaryWorlds > 0) {
            logger.info(languageManager.getMessage("world.cleanup_stale_temporary", cleanedTemporaryWorlds))
        }
        managedGameCatalog = ManagedGameCatalogService(this, registry, temporaryWorldService)
        mapEditorService = MapEditorService(temporaryWorldService)
        roomTaskService = RoomTaskService(this)
        entityOwnershipService = RoomEntityOwnershipService()
        eliminationService = PlayerEliminationService(roomTaskService, spectatorService)
        roomResourceScopeService = RoomResourceScopeService(roomTaskService, entityOwnershipService, packetService, nametagService)
        managedMapPointService = ManagedMapPointService()
        spawnAssignmentService = SpawnAssignmentService()
        playerRuntimeStateService = PlayerRuntimeStateService()
        roomBroadcastService = RoomBroadcastService()
        statsService = createStatsService()
        resultService = GameResultService(statsService)
        snapshotService = PlayerSnapshotService(this)
        velocityBridgeService = createVelocityBridgeService()
        roomManager = GameRoomManager(
            this,
            registry,
            gameManager,
            managedGameCatalog,
            temporaryWorldService,
            statsService,
            snapshotService,
            displayService,
            spectatorService,
            languageManager,
            teamService,
            velocityBridgeService,
            nametagService,
            eliminationService,
            roomResourceScopeService
        )
        chatService = GameChatService(this, roomManager, teamService, languageManager)
        menuService = GameCenterMenuService(this, dialogService, roomManager, gameMapManager, teamService, languageManager, managedGameCatalog, velocityBridgeService)
        chestMenuService = ChestMenuService(this, menuService)
        menuService.bindChestMenuService(chestMenuService)
        gameCenterApi = GameCenterApi(
            registry,
            roomManager,
            temporaryWorldService,
            languageManager,
            packetService,
            selectionService,
            editorPointCaptureService,
            teamService,
            teamAssignmentService,
            chatService,
            mapEditorService,
            managedGameCatalog,
            menuService,
            chestMenuService,
            roomTaskService,
            entityOwnershipService,
            resultService,
            playerRuntimeStateService,
            roomBroadcastService,
            nametagService,
            eliminationService,
            spectatorService,
            roomResourceScopeService,
            managedMapPointService,
            spawnAssignmentService,
            velocityBridgeService
        )
        moduleService = ManagedGameModuleService(this, gameCenterApi, roomManager, temporaryWorldService, languageManager, packetService, selectionService, mapEditorService, managedGameCatalog, menuService, moduleAdminCommands)

        gameManager.load()
        moduleService.load()
        managedGameCatalog.load()
        velocityBridgeService.init()
        velocityBridgeService.startReservationHandling(roomManager::reserveRoomForProxy)
        instanceApi = gameCenterApi
        roomManager.start()
        menuService.init()
        chestMenuService.init()

        server.pluginManager.registerEvents(
            GamePlayerListener(
                roomManager,
                menuService,
                packetService,
                spectatorService,
                velocityBridgeService,
                nametagService,
                eliminationService
            ),
            this
        )
        server.pluginManager.registerEvents(SelectionListener(selectionService, languageManager), this)
        server.pluginManager.registerEvents(editorPointCaptureService, this)
        server.pluginManager.registerEvents(GameChatListener(this, chatService), this)
        server.pluginManager.registerEvents(ChestMenuListener(chestMenuService), this)

        val command = KaGameCenterCommand(menuService, chestMenuService, roomManager, gameMapManager, managedGameCatalog, languageManager, packetService, moduleAdminCommands)
        getCommand("kagamecenter")?.setExecutor(command)
        getCommand("kagamecenter")?.tabCompleter = command
        getCommand("globalchat")?.setExecutor(GlobalChatCommand(chatService, languageManager))
        getCommand("allchat")?.setExecutor(RoomChatCommand(chatService, languageManager))

        printStartupInfo()
        logger.info(languageManager.getMessage("plugin.enabled"))
    }

    override fun onDisable() {
        if (::menuService.isInitialized) {
            menuService.shutdown()
        }
        if (::chestMenuService.isInitialized) {
            chestMenuService.shutdown()
        }
        if (::roomManager.isInitialized) {
            roomManager.stop()
        }
        if (::roomResourceScopeService.isInitialized) {
            roomResourceScopeService.closeAll()
        }
        if (::eliminationService.isInitialized) {
            eliminationService.clearAll()
        }
        if (::moduleService.isInitialized) {
            moduleService.unload()
        }
        if (::editorPointCaptureService.isInitialized) {
            editorPointCaptureService.clearAll()
        }
        if (::roomTaskService.isInitialized) {
            roomTaskService.cancelAll()
        }
        if (::entityOwnershipService.isInitialized) {
            entityOwnershipService.clearAll()
        }
        if (::playerRuntimeStateService.isInitialized) {
            playerRuntimeStateService.clearAll()
        }
        if (::mapEditorService.isInitialized) {
            mapEditorService.shutdown(save = true)
        }
        if (::displayService.isInitialized) {
            displayService.clearAll()
        }
        if (::nametagService.isInitialized) {
            nametagService.clearAll()
        }
        if (::packetService.isInitialized) {
            packetService.shutdown()
        }
        if (::velocityBridgeService.isInitialized) {
            velocityBridgeService.shutdown()
        }
        if (::statsService.isInitialized) {
            statsService.close()
        }
        if (::languageManager.isInitialized) {
            logger.info(languageManager.getMessage("plugin.disabled"))
        }
        instanceApi = null
    }

    private fun createStatsService(): PlayerStatsService {
        val databaseConfig = DatabaseConfig.from(this, config)
        if (!databaseConfig.enabled) {
            logger.info("KaGameCenter stats database is disabled; using memory stats.")
            return PlayerStatsService(MemoryStatsRepository())
        }

        return runCatching {
            PlayerStatsService(SqlStatsRepository(this, databaseConfig)).also { it.init() }
        }.getOrElse {
            logger.warning("Failed to initialize stats database, using memory stats: ${it.message}")
            PlayerStatsService(MemoryStatsRepository()).also { service -> service.init() }
        }
    }

    private fun createVelocityBridgeService(): VelocityBridgeService {
        val bridgeConfig = VelocityBridgeConfig.from(config)
        if (!bridgeConfig.enabled) {
            return NoopVelocityBridgeService()
        }
        return RedisVelocityBridgeService(this, bridgeConfig)
    }

    private fun printStartupInfo() {
        val console = server.consoleSender
        val version = description.version
        val gameVersion = server.version.substringAfter("MC: ", server.version).removeSuffix(")")
        val loadedModules = moduleService.loadedModuleIds().joinToString(", ").ifBlank { "-" }
        val registeredGames = roomManager.listModules().joinToString(", ") { it.id }.ifBlank { "-" }
        val enabled = languageManager.getMessage("startup.status_enabled")
        val disabled = languageManager.getMessage("startup.status_disabled")
        val packetStatus = if (packetService.available) {
            languageManager.getMessage("startup.status_enabled_detail", packetService.backendName)
        } else {
            disabled
        }
        val kaMenuStatus = when {
            menuService.isActionHandlerRegistered() -> languageManager.getMessage("startup.status_enabled_detail", "kgc handler")
            menuService.isKaMenuAvailable() -> enabled
            else -> disabled
        }
        val placeholderStatus = if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) enabled else disabled
        val velocityStatus = if (velocityBridgeService.enabled) {
            languageManager.getMessage("startup.status_enabled_detail", velocityBridgeService.backendName)
        } else {
            disabled
        }

        val logo = """
            §e________________________________________________________
            §b
            §b  _  __       §3 ____                         §b
            §b | |/ / __ _  §3/ ___| __ _ _ __ ___   ___    §b
            §b | ' / / _` | §3| |  _ / _` | '_ ` _ \ / _ \   §b
            §b | . \| (_| | §3| |_| | (_| | | | | | |  __/   §b
            §b |_|\_\\__,_| §3\____|\__,_|_| |_| |_|\___|   §b
            §b
            ${languageManager.getMessage("startup.version", version)}
            ${languageManager.getMessage("startup.minecraft", gameVersion)}
            ${languageManager.getMessage("startup.language", languageManager.getCurrentLanguage())}
            ${languageManager.getMessage("startup.database", statsService.backendName)}
            ${languageManager.getMessage("startup.modules", moduleService.loadedModuleCount(), loadedModules)}
            ${languageManager.getMessage("startup.games", registeredGames)}
            ${languageManager.getMessage("startup.kamenu", kaMenuStatus)}
            ${languageManager.getMessage("startup.placeholderapi", placeholderStatus)}
            ${languageManager.getMessage("startup.packet", packetStatus)}
            ${languageManager.getMessage("startup.velocity", velocityStatus)}
            §e________________________________________________________
        """.trimIndent()

        logo.split("\n").forEach { console.sendMessage(it) }
    }
}
