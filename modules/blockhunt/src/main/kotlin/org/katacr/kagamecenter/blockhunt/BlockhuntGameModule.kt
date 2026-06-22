package org.katacr.kagamecenter.blockhunt

import org.katacr.kaGameCenter.game.GameDefinition
import org.katacr.kaGameCenter.game.GameModule
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.spectator.SpectatorMode
import org.katacr.kaGameCenter.spectator.SpectatorPolicy
import org.katacr.kaGameCenter.team.GameTeamService
import org.katacr.kaGameCenter.team.TeamAssignmentService
import org.katacr.kaGameCenter.world.TemporaryWorldService

class BlockhuntGameModule(
    private val configService: BlockhuntConfigService,
    private val worldService: TemporaryWorldService,
    private val language: ModuleLanguage,
    private val packetService: PacketDispatchService,
    private val roomManager: GameRoomManager,
    private val teamService: GameTeamService,
    private val teamAssignmentService: TeamAssignmentService,
    private val resultService: GameResultService
) : GameModule {
    override val id: String = "blockhunt"
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
            description = "Block Hunt"
        )
    }

    override fun createSession(room: GameRoom): GameSession {
        return BlockhuntGameSession(room, configService, worldService, language, packetService, roomManager, teamService, teamAssignmentService, resultService)
    }
}
