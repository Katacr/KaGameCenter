package org.katacr.kagamecenter.hunger

import org.katacr.kaGameCenter.broadcast.RoomBroadcastService
import org.katacr.kaGameCenter.elimination.PlayerEliminationService
import org.katacr.kaGameCenter.game.GameDefinition
import org.katacr.kaGameCenter.game.GameModule
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.nametag.PlayerNametagService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.result.GameResultService
import org.katacr.kaGameCenter.runtime.PlayerRuntimeStateService
import org.katacr.kaGameCenter.resource.RoomResourceScopeService
import org.katacr.kaGameCenter.spectator.SpectatorMode
import org.katacr.kaGameCenter.spectator.SpectatorPolicy
import org.katacr.kaGameCenter.spawn.SpawnAssignmentService
import org.katacr.kaGameCenter.world.TemporaryWorldService

/** 描述经典饥饿游戏模块并为每个房间创建独立 Session。 */
class HungerGameModule(
    private val configService: HungerConfigService,
    private val worldService: TemporaryWorldService,
    private val language: ModuleLanguage,
    private val packetService: PacketDispatchService,
    private val roomManager: GameRoomManager,
    private val resultService: GameResultService,
    private val playerRuntimeStateService: PlayerRuntimeStateService,
    private val roomBroadcastService: RoomBroadcastService,
    private val nametagService: PlayerNametagService,
    private val eliminationService: PlayerEliminationService,
    private val roomResourceScopeService: RoomResourceScopeService,
    private val spawnAssignmentService: SpawnAssignmentService
) : GameModule {
    override val id: String = "hunger"
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
            optionalPlugins = listOf("PacketEvents"),
            spectatorPolicy = SpectatorPolicy(
                enabled = true,
                mode = SpectatorMode.MANAGED,
                allowDuringRunning = true,
                allowFollowPlayer = true,
                allowFreeFly = true,
                revealHiddenPlayers = true
            ),
            description = "Classic Survival Games"
        )
    }

    override fun createSession(room: GameRoom): GameSession {
        return HungerGameSession(
            room = room,
            configService = configService,
            worldService = worldService,
            language = language,
            packetService = packetService,
            roomManager = roomManager,
            resultService = resultService,
            playerRuntimeStateService = playerRuntimeStateService,
            roomBroadcastService = roomBroadcastService,
            nametagService = nametagService,
            eliminationService = eliminationService,
            roomResourceScopeService = roomResourceScopeService,
            spawnAssignmentService = spawnAssignmentService
        )
    }
}
