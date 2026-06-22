package org.katacr.kaGameCenter.api

import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.broadcast.RoomBroadcastService
import org.katacr.kaGameCenter.chat.GameChatService
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.entity.RoomEntityOwnershipService
import org.katacr.kaGameCenter.game.GameModule
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.GameRegistry
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.menu.chest.ChestMenuService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.selection.SelectionService
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.task.RoomTaskService
import org.katacr.kaGameCenter.world.TemporaryWorldService

class GameCenterApi(
    private val registry: GameRegistry,
    val roomManager: GameRoomManager,
    val worldService: TemporaryWorldService,
    val languageManager: LanguageManager,
    val packetService: PacketDispatchService,
    val selectionService: SelectionService,
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
    val roomBroadcastService: RoomBroadcastService
) {
    fun registerModule(module: GameModule) {
        registry.register(module)
    }
}
