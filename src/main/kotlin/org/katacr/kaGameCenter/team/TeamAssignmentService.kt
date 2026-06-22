package org.katacr.kaGameCenter.team

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.ceil

class TeamAssignmentService(
    private val teamService: GameTeamService
) {
    fun registerTeams(roomId: String, teams: Iterable<GameTeam>) {
        teams.forEach { teamService.register(roomId, it) }
    }

    fun joinSmallestTeam(roomId: String, player: Player, teamIds: Collection<String>): String? {
        return teamIds
            .filter { teamService.getTeams(roomId).any { team -> team.id.equals(it, ignoreCase = true) } }
            .sortedBy { teamService.getMembers(roomId, it).size }
            .firstOrNull { teamService.join(roomId, player, it) }
    }

    fun assignRoundRobin(roomId: String, playerIds: Collection<UUID>, teamIds: List<String>): Map<UUID, String> {
        if (teamIds.isEmpty()) return emptyMap()
        val assignments = linkedMapOf<UUID, String>()
        playerIds.shuffled().forEachIndexed { index, playerId ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEachIndexed
            val teamId = teamIds[index % teamIds.size]
            if (teamService.join(roomId, player, teamId)) {
                assignments[playerId] = teamId
            }
        }
        return assignments
    }

    fun assignRatio(
        roomId: String,
        playerIds: Collection<UUID>,
        primaryTeamId: String,
        secondaryTeamId: String,
        primaryRatio: Double,
        minPrimary: Int = 1,
        keepSecondary: Boolean = true
    ): Map<UUID, String> {
        val players = playerIds.shuffled()
        if (players.isEmpty()) return emptyMap()
        val maxPrimary = if (keepSecondary && players.size > 1) players.size - 1 else players.size
        val primaryCount = ceil(players.size * primaryRatio.coerceIn(0.0, 1.0))
            .toInt()
            .coerceAtLeast(minPrimary)
            .coerceAtMost(maxPrimary.coerceAtLeast(0))
        val assignments = linkedMapOf<UUID, String>()
        players.forEachIndexed { index, playerId ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEachIndexed
            val teamId = if (index < primaryCount) primaryTeamId else secondaryTeamId
            if (teamService.join(roomId, player, teamId)) {
                assignments[playerId] = teamId
            }
        }
        return assignments
    }
}
