package org.katacr.kaGameCenter.display

import net.kyori.adventure.text.Component
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameState
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.team.GameTeam
import org.katacr.kaGameCenter.team.GameTeamService
import java.time.Duration
import java.util.UUID

class GameDisplayService(
    @Suppress("unused") private val plugin: JavaPlugin,
    private val languageManager: LanguageManager,
    private val teamService: GameTeamService,
    private val playerStatusDisplayService: PlayerStatusDisplayService
) {
    private val roomDisplays = linkedMapOf<String, RoomDisplay>()
    private val playerOriginalScoreboards = linkedMapOf<UUID, Scoreboard>()
    private val playerOriginalListNames = linkedMapOf<UUID, Component?>()
    private val playerOriginalListOrders = linkedMapOf<UUID, Int>()
    private val playerOriginalListHeaders = linkedMapOf<UUID, Component>()
    private val playerOriginalListFooters = linkedMapOf<UUID, Component>()

    fun attach(player: Player, room: GameRoom) {
        val display = roomDisplays.getOrPut(room.id) { createDisplay(room) }
        playerOriginalScoreboards.putIfAbsent(player.uniqueId, player.scoreboard)
        playerOriginalListNames.putIfAbsent(player.uniqueId, player.playerListName())
        playerOriginalListOrders.putIfAbsent(player.uniqueId, player.playerListOrder)
        playerOriginalListHeaders.putIfAbsent(player.uniqueId, player.playerListHeader() ?: Component.empty())
        playerOriginalListFooters.putIfAbsent(player.uniqueId, player.playerListFooter() ?: Component.empty())
        if (!room.session.usesCustomScoreboard()) {
            player.scoreboard = display.scoreboard
        }
        showTitle(
            player,
            languageManager.getMessage("display.title_joined", room.definition?.displayName ?: room.module.displayName),
            languageManager.getMessage("display.subtitle_room", room.id)
        )
        sendActionBar(player, languageManager.getMessage("display.action_joined", room.id))
        update(room)
    }

    fun detach(player: Player, room: GameRoom) {
        playerStatusDisplayService.removeViewer(room.id, player)
        playerOriginalScoreboards.remove(player.uniqueId)?.let { player.scoreboard = it }
        player.playerListName(playerOriginalListNames.remove(player.uniqueId))
        playerOriginalListOrders.remove(player.uniqueId)?.let { player.playerListOrder = it }
        player.sendPlayerListHeaderAndFooter(
            playerOriginalListHeaders.remove(player.uniqueId) ?: Component.empty(),
            playerOriginalListFooters.remove(player.uniqueId) ?: Component.empty()
        )
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
        val stateName = languageManager.getStateName(room.state)

        val progress = when (room.state) {
            GameState.CREATED -> 0.1
            GameState.PREPARING -> 0.25
            GameState.WAITING -> 0.5
            GameState.COUNTDOWN -> 0.75
            GameState.RUNNING -> 1.0
            GameState.ENDING, GameState.CLOSED -> 0.0
        }.toFloat()
        val color = when (room.state) {
            GameState.RUNNING -> BossBar.Color.GREEN
            GameState.ENDING, GameState.CLOSED -> BossBar.Color.RED
            else -> BossBar.Color.BLUE
        }
        val viewers = (room.players + room.spectators).mapNotNull(Bukkit::getPlayer)
        val customStatus = room.session.bossBarStatus()
        if (customStatus == null) {
            playerStatusDisplayService.update(
                room.id,
                viewers,
                IconTextParser.parse(languageManager.getMessage(
                    "display.bossbar",
                    room.definition?.displayName ?: room.module.displayName,
                    stateName,
                    room.players.size,
                    maxPlayers
                )),
                progress,
                color,
                BossBar.Overlay.NOTCHED_10
            )
        } else {
            playerStatusDisplayService.update(room.id, viewers, customStatus)
        }

        if (!room.session.usesCustomScoreboard()) {
            updateSidebar(
                display,
                listOf(
                    languageManager.getMessage("display.sidebar_game", room.definition?.displayName ?: room.module.displayName),
                    languageManager.getMessage("display.sidebar_room", room.id),
                    languageManager.getMessage("display.sidebar_state", stateName),
                    languageManager.getMessage("display.sidebar_players", room.players.size, maxPlayers),
                    languageManager.getMessage("display.sidebar_time", elapsedSeconds),
                    languageManager.getMessage("display.sidebar_world", room.world?.name ?: "-")
                )
            )
        }

        if (!room.session.usesCustomActionBar()) {
            room.playersOnline().forEach {
                sendActionBar(it, languageManager.getMessage("display.action_status", stateName, room.players.size, maxPlayers))
            }
        }
        updateTabList(room)
    }

    fun clearRoom(room: GameRoom) {
        playerStatusDisplayService.clearRoom(room.id)
        val display = roomDisplays.remove(room.id) ?: return
        display.scoreboard.clearSlot(DisplaySlot.SIDEBAR)
        display.objective.unregister()
    }

    fun clearAll() {
        roomDisplays.values.forEach {
            it.scoreboard.clearSlot(DisplaySlot.SIDEBAR)
            it.objective.unregister()
        }
        roomDisplays.clear()
        playerStatusDisplayService.clearAll()
        playerOriginalListNames.forEach { (playerId, name) ->
            Bukkit.getPlayer(playerId)?.playerListName(name)
        }
        playerOriginalListOrders.forEach { (playerId, order) ->
            Bukkit.getPlayer(playerId)?.playerListOrder = order
        }
        playerOriginalListHeaders.forEach { (playerId, header) ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            player.sendPlayerListHeaderAndFooter(header, playerOriginalListFooters[playerId] ?: Component.empty())
        }
        playerOriginalScoreboards.clear()
        playerOriginalListNames.clear()
        playerOriginalListOrders.clear()
        playerOriginalListHeaders.clear()
        playerOriginalListFooters.clear()
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

        return RoomDisplay(scoreboard, objective, System.currentTimeMillis())
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

    private fun updateTabList(room: GameRoom) {
        val teams = teamService.getTeams(room.id).take(MAX_RENDER_TEAMS)
        val players = room.playersOnline()
        val spectators = room.spectators
            .mapNotNull(Bukkit::getPlayer)
            .sortedBy { it.name.lowercase() }
        val customHeaderFooter = room.session.usesCustomTabHeaderFooter()
        val customPlayerNames = room.session.usesCustomTabPlayerNames()
        val header = if (customHeaderFooter) Component.empty() else buildTabHeader(room, teams)
        val footer = if (customHeaderFooter) Component.empty() else buildTabFooter(room, teams)

        if (teams.isEmpty()) {
            players.sortedBy { it.name.lowercase() }.forEachIndexed { index, player ->
                player.playerListOrder = room.session
                    .tabPlayerListOrder(player, TAB_BASE_ORDER + index)
                    .coerceAtLeast(0)
                if (!customPlayerNames) {
                    player.playerListName(tabComponent(languageManager.getMessage("display.tab_player", player.name), NamedTextColor.WHITE))
                }
                if (!customHeaderFooter) player.sendPlayerListHeaderAndFooter(header, footer)
            }
        } else {
            val orderByPlayer = linkedMapOf<UUID, Int>()
            teams.forEachIndexed { teamIndex, team ->
                teamService.getMembers(room.id, team.id)
                    .mapNotNull { Bukkit.getPlayer(it) }
                    .sortedBy { it.name.lowercase() }
                    .forEachIndexed { slotIndex, player ->
                        orderByPlayer[player.uniqueId] = TAB_BASE_ORDER + teamIndex * TEAM_MEMBER_SLOTS + slotIndex
                        if (!customPlayerNames) {
                            player.playerListName(tabComponent(
                                languageManager.getMessage("display.tab_team_player", team.displayName, player.name),
                                team.color ?: NamedTextColor.WHITE
                            ))
                        }
                    }
            }

            teamService.getUngroupedPlayers(room.id, room.players)
                .mapNotNull { Bukkit.getPlayer(it) }
                .sortedBy { it.name.lowercase() }
                .forEachIndexed { index, player ->
                    orderByPlayer[player.uniqueId] = TAB_UNGROUPED_ORDER + index
                    if (!customPlayerNames) {
                        player.playerListName(tabComponent(
                            languageManager.getMessage("display.tab_ungrouped_player", player.name),
                            NamedTextColor.GRAY
                        ))
                    }
                }

            players.forEachIndexed { fallbackIndex, player ->
                val defaultOrder = orderByPlayer[player.uniqueId] ?: (TAB_UNGROUPED_ORDER + fallbackIndex)
                player.playerListOrder = room.session.tabPlayerListOrder(player, defaultOrder).coerceAtLeast(0)
                if (!customHeaderFooter) player.sendPlayerListHeaderAndFooter(header, footer)
            }
        }

        spectators.forEachIndexed { index, player ->
            player.playerListOrder = room.session
                .tabPlayerListOrder(player, TAB_SPECTATOR_ORDER + index)
                .coerceAtLeast(0)
            if (!customHeaderFooter) player.sendPlayerListHeaderAndFooter(header, footer)
        }
    }

    private fun buildTabHeader(room: GameRoom, teams: List<GameTeam>): Component {
        val maxPlayers = room.definition?.maxPlayers ?: room.module.maxPlayers
        return tabComponent(languageManager.getMessage(
            "display.tab_header",
            room.definition?.displayName ?: room.module.displayName,
            room.id,
            languageManager.getStateName(room.state),
            room.players.size,
            maxPlayers
        ), NamedTextColor.WHITE)
    }

    private fun buildTabFooter(room: GameRoom, teams: List<GameTeam>): Component {
        val text = if (teams.isEmpty()) {
            val players = room.players
                .map { Bukkit.getPlayer(it)?.name ?: Bukkit.getOfflinePlayer(it).name ?: it.toString().take(8) }
                .chunked(SOLO_MEMBER_COLUMNS)
                .joinToString("\n") { row -> row.joinToString("    ") { languageManager.getMessage("display.tab_slot_player", it) } }
                .ifBlank { languageManager.getMessage("display.tab_empty_room") }
            languageManager.getMessage("display.tab_footer_solo", players)
        } else {
            val headers = teams.joinToString("    ") { languageManager.getMessage("display.tab_slot_team", it.displayName) }
            val rows = (0 until TEAM_MEMBER_SLOTS).joinToString("\n") { slot ->
                teams.joinToString("    ") { team ->
                    val member = teamService.getMembers(room.id, team.id)
                        .toList()
                        .getOrNull(slot)
                        ?.let { Bukkit.getPlayer(it)?.name ?: Bukkit.getOfflinePlayer(it).name ?: it.toString().take(8) }
                    if (member == null) {
                        languageManager.getMessage("display.tab_slot_empty")
                    } else {
                        languageManager.getMessage("display.tab_slot_player", member)
                    }
                }
            }
            languageManager.getMessage("display.tab_footer_teams", headers, rows)
        }
        return tabComponent(text, NamedTextColor.WHITE)
    }

    /** 解析 Tab 文本颜色和图标，并为未显式着色的内容提供回退颜色。 */
    private fun tabComponent(text: String, fallbackColor: TextColor): Component {
        return Component.text().color(fallbackColor).append(IconTextParser.parse(text)).build()
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
        val createdAtMillis: Long,
        val entries: MutableList<String> = mutableListOf()
    )

    companion object {
        private const val SOLO_MEMBER_COLUMNS = 3
        private const val MAX_RENDER_TEAMS = 4
        private const val TEAM_MEMBER_SLOTS = 6
        private const val TAB_BASE_ORDER = 2000
        private const val TAB_UNGROUPED_ORDER = 2900
        private const val TAB_SPECTATOR_ORDER = 3900
    }
}
