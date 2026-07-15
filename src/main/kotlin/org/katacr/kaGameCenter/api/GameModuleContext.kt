package org.katacr.kaGameCenter.api

import org.bukkit.event.Listener
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.katacr.kaGameCenter.broadcast.RoomBroadcastService
import org.katacr.kaGameCenter.command.ModuleAdminCommand
import org.katacr.kaGameCenter.chat.GameChatFormatter
import org.katacr.kaGameCenter.chat.GameChatService
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.editor.EditorPointCaptureService
import org.katacr.kaGameCenter.elimination.PlayerEliminationService
import org.katacr.kaGameCenter.entity.RoomEntityOwnershipService
import org.katacr.kaGameCenter.game.GameModule
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.ModuleGameEditor
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.menu.chest.ChestMenuDataSource
import org.katacr.kaGameCenter.menu.chest.ChestMenuService
import org.katacr.kaGameCenter.map.ManagedMapPointService
import org.katacr.kaGameCenter.nametag.PlayerNametagService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.resource.RoomResourceScopeService
import org.katacr.kaGameCenter.selection.SelectionService
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.spawn.SpawnAssignmentService
import org.katacr.kaGameCenter.spectator.SpectatorService
import org.katacr.kaGameCenter.task.RoomTaskService
import org.katacr.kaGameCenter.world.TemporaryWorldService
import java.io.File

class GameModuleContext(
    val id: String,
    val dataFolder: File,
    val plugin: JavaPlugin,
    val api: GameCenterApi,
    val roomManager: GameRoomManager,
    val worldService: TemporaryWorldService,
    val languageManager: LanguageManager,
    val packetService: PacketDispatchService,
    val selectionService: SelectionService,
    val editorPointCaptureService: EditorPointCaptureService,
    val teamService: GameTeamService,
    val teamAssignmentService: TeamAssignmentService,
    val chatService: GameChatService,
    val mapEditorService: MapEditorService,
    val managedGameCatalog: ManagedGameCatalogService,
    val menuService: GameCenterMenuService,
    val chestMenuService: ChestMenuService,
    val roomTaskService: RoomTaskService,
    val entityOwnershipService: RoomEntityOwnershipService,
    val resultService: GameResultService,
    val playerRuntimeStateService: PlayerRuntimeStateService,
    val roomBroadcastService: RoomBroadcastService,
    val nametagService: PlayerNametagService,
    val eliminationService: PlayerEliminationService,
    val spectatorService: SpectatorService,
    val roomResourceScopeService: RoomResourceScopeService,
    val managedMapPointService: ManagedMapPointService,
    val spawnAssignmentService: SpawnAssignmentService,
    private val moduleAdminRegistry: MutableMap<String, ModuleAdminCommand>
) {
    private val modules = mutableListOf<GameModule>()
    private val listeners = mutableListOf<Listener>()
    private val tasks = mutableListOf<BukkitTask>()
    private val adminCommands = mutableListOf<ModuleAdminCommand>()
    private val gameEditors = mutableListOf<ModuleGameEditor>()
    private val chatFormatters = mutableListOf<GameChatFormatter>()
    private val chestMenuDataSources = mutableListOf<Pair<String, ChestMenuDataSource>>()

    fun registerModule(module: GameModule) {
        api.registerModule(module)
        modules.add(module)
    }

    fun registerListener(listener: Listener) {
        plugin.server.pluginManager.registerEvents(listener, plugin)
        listeners.add(listener)
    }

    fun registerAdminCommand(command: ModuleAdminCommand) {
        moduleAdminRegistry[command.name.lowercase()] = command
        adminCommands.add(command)
    }

    fun registerGameEditor(editor: ModuleGameEditor) {
        managedGameCatalog.registerEditor(editor)
        gameEditors.add(editor)
    }

    fun registerChatFormatter(formatter: GameChatFormatter) {
        chatService.registerFormatter(id, formatter)
        chatFormatters.add(formatter)
    }

    fun registerChestMenuDataSource(type: String, source: ChestMenuDataSource) {
        val namespacedType = "$id:$type"
        chestMenuService.dataSources.register(namespacedType, source)
        chestMenuDataSources.add(namespacedType to source)
    }

    fun trackTask(task: BukkitTask): BukkitTask {
        tasks.add(task)
        return task
    }

    fun runTaskTimer(delayTicks: Long, periodTicks: Long, action: Runnable): BukkitTask {
        return trackTask(plugin.server.scheduler.runTaskTimer(plugin, action, delayTicks, periodTicks))
    }

    fun cleanup() {
        editorPointCaptureService.cancelOwner(id)
        tasks.forEach { task ->
            if (!task.isCancelled) {
                task.cancel()
            }
        }
        tasks.clear()
        listeners.forEach { HandlerList.unregisterAll(it) }
        listeners.clear()
        chatFormatters.asReversed().forEach { formatter -> chatService.unregisterFormatter(id, formatter) }
        chatFormatters.clear()
        chestMenuDataSources.asReversed().forEach { (type, source) ->
            chestMenuService.dataSources.unregister(type, source)
        }
        chestMenuDataSources.clear()
        adminCommands.asReversed().forEach { command ->
            moduleAdminRegistry.entries.removeIf { it.value === command }
        }
        adminCommands.clear()
        gameEditors.asReversed().forEach(managedGameCatalog::unregisterEditor)
        gameEditors.clear()
        modules.asReversed().forEach(api::unregisterModule)
        modules.clear()
    }
}
