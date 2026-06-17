package org.katacr.kaGameCenter.display

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameState
import org.katacr.kaGameCenter.i18n.LanguageManager
import java.time.Duration
import java.util.UUID

class GameDisplayService(
    @Suppress("unused") private val plugin: JavaPlugin,
    private val languageManager: LanguageManager
) {
    private val roomDisplays = linkedMapOf<String, RoomDisplay>()
    private val playerOriginalScoreboards = linkedMapOf<UUID, Scoreboard>()

    fun attach(player: Player, room: GameRoom) {
        val display = roomDisplays.getOrPut(room.id) { createDisplay(room) }
        playerOriginalScoreboards.putIfAbsent(player.uniqueId, player.scoreboard)
        player.scoreboard = display.scoreboard
        display.bossBar.addPlayer(player)
        showTitle(
            player,
            languageManager.getMessage("display.title_joined", room.definition?.displayName ?: room.module.displayName),
            languageManager.getMessage("display.subtitle_room", room.id)
        )
        sendActionBar(player, languageManager.getMessage("display.action_joined", room.id))
        update(room)
    }

    fun detach(player: Player, room: GameRoom) {
        roomDisplays[room.id]?.bossBar?.removePlayer(player)
        playerOriginalScoreboards.remove(player.uniqueId)?.let { player.scoreboard = it }
        player.resetTitle()
        sendActionBar(player, languageManager.getMessage("display.action_left"))
    }

    fun markPreparing(room: GameRoom) {
        room.playersOnline().forEach {
            showTitle(
                it,
                languageManager.getMessage("display.title_preparing"),
                languageManager.getMessage("display.subtitle_room", room.id)
            )
        }
        update(room)
    }

    fun markWaiting(room: GameRoom) {
        room.playersOnline().forEach {
            showTitle(
                it,
                languageManager.getMessage("display.title_waiting"),
                languageManager.getMessage("display.subtitle_players", room.players.size, room.definition?.maxPlayers ?: room.module.maxPlayers)
            )
        }
        update(room)
    }

    fun markStarted(room: GameRoom) {
        room.playersOnline().forEach {
            showTitle(
                it,
                languageManager.getMessage("display.title_started"),
                languageManager.getMessage("display.subtitle_room", room.id)
            )
        }
        update(room)
    }

    fun markClosed(room: GameRoom) {
        room.playersOnline().forEach {
            showTitle(
                it,
                languageManager.getMessage("display.title_closed"),
                languageManager.getMessage("display.subtitle_room", room.id)
            )
        }
        clearRoom(room)
    }

    fun update(room: GameRoom) {
        val display = roomDisplays.getOrPut(room.id) { createDisplay(room) }
        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        val elapsedSeconds = ((System.currentTimeMillis() - display.createdAtMillis) / 1000L).toInt()

        display.bossBar.setTitle(languageManager.getMessage(
            "display.bossbar",
            room.definition?.displayName ?: room.module.displayName,
            room.state,
            room.players.size,
            maxPlayers
        ))
        display.bossBar.progress = when (room.state) {
            GameState.CREATED -> 0.1
            GameState.PREPARING -> 0.25
            GameState.WAITING -> 0.5
            GameState.COUNTDOWN -> 0.75
            GameState.RUNNING -> 1.0
            GameState.ENDING, GameState.CLOSED -> 0.0
        }
        display.bossBar.color = when (room.state) {
            GameState.RUNNING -> BarColor.GREEN
            GameState.ENDING, GameState.CLOSED -> BarColor.RED
            else -> BarColor.BLUE
        }

        updateSidebar(
            display,
            listOf(
                languageManager.getMessage("display.sidebar_game", room.definition?.displayName ?: room.module.displayName),
                languageManager.getMessage("display.sidebar_room", room.id),
                languageManager.getMessage("display.sidebar_state", room.state),
                languageManager.getMessage("display.sidebar_players", room.players.size, maxPlayers),
                languageManager.getMessage("display.sidebar_time", elapsedSeconds),
                languageManager.getMessage("display.sidebar_world", room.world?.name ?: "-")
            )
        )

        room.playersOnline().forEach {
            sendActionBar(it, languageManager.getMessage("display.action_status", room.state, room.players.size, maxPlayers))
        }
    }

    fun clearRoom(room: GameRoom) {
        val display = roomDisplays.remove(room.id) ?: return
        display.bossBar.removeAll()
        display.scoreboard.clearSlot(DisplaySlot.SIDEBAR)
        display.objective.unregister()
    }

    fun clearAll() {
        roomDisplays.values.forEach {
            it.bossBar.removeAll()
            it.scoreboard.clearSlot(DisplaySlot.SIDEBAR)
            it.objective.unregister()
        }
        roomDisplays.clear()
        playerOriginalScoreboards.clear()
    }

    fun sendActionBar(player: Player, text: String) {
        player.sendActionBar(Component.text(text))
    }

    fun sendActionBar(player: Player, text: Component) {
        player.sendActionBar(text)
    }

    private fun showTitle(player: Player, title: String, subtitle: String) {
        player.showTitle(
            Title.title(
                Component.text(title, NamedTextColor.GOLD),
                Component.text(subtitle, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1400), Duration.ofMillis(350))
            )
        )
    }

    private fun createDisplay(room: GameRoom): RoomDisplay {
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective = scoreboard.registerNewObjective(
            objectiveName(room.id),
            Criteria.DUMMY,
            Component.text(languageManager.getMessage("display.sidebar_title"), NamedTextColor.AQUA)
        )
        objective.setDisplaySlot(DisplaySlot.SIDEBAR)

        val bossBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SEGMENTED_10)
        return RoomDisplay(scoreboard, objective, bossBar, System.currentTimeMillis())
    }

    private fun updateSidebar(display: RoomDisplay, lines: List<String>) {
        display.entries.forEach { display.scoreboard.resetScores(it) }
        display.entries.clear()

        lines.take(15).forEachIndexed { index, line ->
            val entry = "${line.take(40)} ${uniqueSuffix(index)}"
            display.entries.add(entry)
            display.objective.getScore(entry).score = lines.size - index
        }
    }

    private fun objectiveName(roomId: String): String {
        return "kgc_${roomId}".take(32767)
    }

    private fun uniqueSuffix(index: Int): String {
        return when (index) {
            0 -> "§0"
            1 -> "§1"
            2 -> "§2"
            3 -> "§3"
            4 -> "§4"
            5 -> "§5"
            6 -> "§6"
            7 -> "§7"
            8 -> "§8"
            9 -> "§9"
            10 -> "§a"
            11 -> "§b"
            12 -> "§c"
            13 -> "§d"
            else -> "§e"
        }
    }

    private fun GameRoom.playersOnline(): List<Player> {
        return players.mapNotNull { Bukkit.getPlayer(it) }
    }

    private data class RoomDisplay(
        val scoreboard: Scoreboard,
        val objective: Objective,
        val bossBar: BossBar,
        val createdAtMillis: Long,
        val entries: MutableList<String> = mutableListOf()
    )
}
