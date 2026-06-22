package org.katacr.kagamecenter.tntwars

import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.broadcast.RoomBroadcastService
import org.katacr.kaGameCenter.entity.RoomEntityOwnershipService
import org.katacr.kaGameCenter.game.GameDefinition
import org.katacr.kaGameCenter.game.GameModule
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.spectator.SpectatorMode
import org.katacr.kaGameCenter.spectator.SpectatorPolicy
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.task.RoomTaskService
import org.katacr.kaGameCenter.world.TemporaryWorldService

class TntWarsGameModule(
    private val plugin: JavaPlugin,
    private val configService: TntWarsConfigService,
    private val worldService: TemporaryWorldService,
    private val language: ModuleLanguage,
    private val roomManager: GameRoomManager,
    private val teamService: GameTeamService,
    private val teamAssignmentService: TeamAssignmentService,
    private val roomTaskService: RoomTaskService,
    private val entityOwnershipService: RoomEntityOwnershipService,
    private val resultService: GameResultService,
    private val playerRuntimeStateService: PlayerRuntimeStateService,
    private val roomBroadcastService: RoomBroadcastService
) : GameModule {
    override val id: String = "tntwars"
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
            countdownSeconds = config.startCountdownSeconds,
            mapTemplates = config.maps.values.map { it.template },
            spectatorPolicy = SpectatorPolicy(enabled = true, mode = SpectatorMode.MANAGED, allowFollowPlayer = true, allowFreeFly = true),
            description = "TNT Wars"
        )
    }

    override fun createSession(room: GameRoom): GameSession {
        return TntWarsGameSession(plugin, room, configService, worldService, language, roomManager, teamService, teamAssignmentService, roomTaskService, entityOwnershipService, resultService, playerRuntimeStateService, roomBroadcastService)
    }
}
