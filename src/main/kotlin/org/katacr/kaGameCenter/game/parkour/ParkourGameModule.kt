package org.katacr.kaGameCenter.game.parkour

import org.katacr.kaGameCenter.game.GameModule
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.world.TemporaryWorldService

class ParkourGameModule(
    private val worldService: TemporaryWorldService,
    private val languageManager: LanguageManager
) : GameModule {
    override val id: String = "parkour"
    override val displayName: String = "Parkour"
    override val minPlayers: Int = 1
    override val maxPlayers: Int = 16

    override fun createSession(room: GameRoom): GameSession {
        return ParkourGameSession(room, worldService, languageManager)
    }
}
