package org.katacr.kaGameCenter

import net.byteflux.libby.BukkitLibraryManager
import net.byteflux.libby.Library
import org.katacr.kaGameCenter.data.PlayerSnapshotService
import org.katacr.kaGameCenter.command.KaGameCenterCommand
import org.katacr.kaGameCenter.data.PlayerStatsService
import org.katacr.kaGameCenter.dialog.GameCenterDialogService
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.display.GameDisplayService
import org.katacr.kaGameCenter.game.GameRegistry
import org.katacr.kaGameCenter.game.GameManager
import org.katacr.kaGameCenter.game.GameMapManager
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.parkour.ParkourGameModule
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.listener.GamePlayerListener
import org.katacr.kaGameCenter.listener.ParkourListener
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

        logger.info("Checking KaGameCenter runtime libraries...")
        libraryManager.loadLibrary(kotlinStd)
        libraryManager.loadLibrary(sqlite)
        libraryManager.loadLibrary(mysql)
        libraryManager.loadLibrary(hikari)
    }

    override fun onEnable() {
        saveDefaultConfig()

        languageManager = LanguageManager(this)
        languageManager.init()

        dialogService = GameCenterDialogService(languageManager)
        displayService = GameDisplayService(this, languageManager)
        gameManager = GameManager(this)
        gameMapManager = GameMapManager(this, gameManager)
        registry = GameRegistry(gameManager)
        temporaryWorldService = TemporaryWorldService(this)
        statsService = PlayerStatsService()
        snapshotService = PlayerSnapshotService()
        roomManager = GameRoomManager(this, registry, gameManager, temporaryWorldService, statsService, snapshotService, displayService, languageManager)
        menuService = GameCenterMenuService(this, dialogService, roomManager, gameMapManager, languageManager)

        registry.register(ParkourGameModule(temporaryWorldService, languageManager))
        gameManager.load()
        roomManager.start()
        menuService.init()

        server.pluginManager.registerEvents(GamePlayerListener(roomManager, menuService), this)
        server.pluginManager.registerEvents(ParkourListener(roomManager), this)

        val command = KaGameCenterCommand(menuService, roomManager, gameMapManager, languageManager)
        getCommand("kagamecenter")?.setExecutor(command)
        getCommand("kagamecenter")?.tabCompleter = command

        logger.info(languageManager.getMessage("plugin.enabled"))
    }

    override fun onDisable() {
        if (::menuService.isInitialized) {
            menuService.shutdown()
        }
        if (::roomManager.isInitialized) {
            roomManager.stop()
        }
        if (::displayService.isInitialized) {
            displayService.clearAll()
        }
        if (::languageManager.isInitialized) {
            logger.info(languageManager.getMessage("plugin.disabled"))
        }
    }
}
