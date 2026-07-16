package org.katacr.kagamecenter.bedwars

import org.katacr.kaGameCenter.game.GameDefinition
import org.katacr.kaGameCenter.game.GameModule
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.broadcast.RoomBroadcastService
import org.katacr.kaGameCenter.nametag.PlayerNametagService
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.spectator.SpectatorService
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.task.RoomTaskService
import org.katacr.kaGameCenter.menu.chest.ChestMenuService
import org.katacr.kaGameCenter.world.TemporaryWorldService
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.elimination.PlayerEliminationService
import org.katacr.kaGameCenter.resource.RoomResourceScopeService
import org.katacr.kaGameCenter.entity.RoomPresentationService
import org.katacr.kaGameCenter.reconnect.RoomReconnectStateService
import org.katacr.kaGameCenter.velocity.VelocityBridgeService
import org.bukkit.entity.Player

/** 描述 BedWars 玩法并为每个房间创建隔离的对局 Session。 */
class BedWarsGameModule(
    private val plugin: JavaPlugin,
    private val configService: BedWarsConfigService,
    private val worldService: TemporaryWorldService,
    private val language: ModuleLanguage,
    private val roomManager: GameRoomManager,
    private val teamService: GameTeamService,
    private val teamAssignmentService: TeamAssignmentService,
    private val roomTaskService: RoomTaskService,
    private val resultService: GameResultService,
    private val playerRuntimeStateService: PlayerRuntimeStateService,
    private val roomBroadcastService: RoomBroadcastService,
    private val nametagService: PlayerNametagService,
    private val chestMenuService: ChestMenuService,
    private val quickBuyService: BedWarsQuickBuyService,
    private val eliminationService: PlayerEliminationService,
    private val spectatorService: SpectatorService,
    private val roomResourceScopeService: RoomResourceScopeService,
    private val roomPresentationService: RoomPresentationService,
    private val reconnectStateService: RoomReconnectStateService,
    private val velocityBridgeService: VelocityBridgeService
) : GameModule {
    override val id: String = "bedwars"
    override val displayName: String get() = configService.current().displayName
    override val minPlayers: Int get() = configService.current().minPlayers
    override val maxPlayers: Int get() = configService.current().maxPlayers

    override fun defaultDefinition(): GameDefinition {
        val config = configService.current()
        return GameDefinition(
            id = id,
            displayName = config.displayName,
            enabled = config.enabled,
            minPlayers = config.minPlayers,
            maxPlayers = config.maxPlayers,
            defaultDurationSeconds = config.durationSeconds,
            countdownSeconds = config.countdownSeconds,
            mapTemplates = config.maps.values.map { it.template },
            spectatorPolicy = config.toSpectatorPolicy(language),
            description = "BedWars for KaGameCenter"
        )
    }

    /** 应用地图外部观战开关，并允许管理人员执行旁观检查。 */
    override fun canSpectate(room: GameRoom, player: Player): Boolean {
        if (player.hasPermission("kagamecenter.admin")) return true
        val managed = room.configuredGame ?: return true
        return configService.readManagedGame(managed).allowSpectate
    }

    override fun createSession(room: GameRoom): GameSession = BedWarsGameSession(
        plugin,
        room,
        configService,
        worldService,
        language,
        roomManager,
        teamService,
        teamAssignmentService,
        roomTaskService,
        resultService,
        playerRuntimeStateService,
        roomBroadcastService,
        nametagService,
        chestMenuService,
        quickBuyService,
        eliminationService,
        spectatorService,
        roomResourceScopeService,
        roomPresentationService,
        reconnectStateService,
        velocityBridgeService
    )
}
