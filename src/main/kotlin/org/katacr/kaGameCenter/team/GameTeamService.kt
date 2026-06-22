package org.katacr.kaGameCenter.team

import org.bukkit.entity.Player
import java.util.UUID

class GameTeamService {
    private val roomTeams = linkedMapOf<String, MutableMap<String, GameTeam>>()
    private val teamMembers = linkedMapOf<String, MutableMap<UUID, String>>()

    fun register(roomId: String, team: GameTeam) {
        roomTeams.getOrPut(roomId) { linkedMapOf() }[team.id.lowercase()] = team
    }

    fun join(roomId: String, player: Player, teamId: String): Boolean {
        val team = roomTeams[roomId]?.get(teamId.lowercase()) ?: return false
        val members = teamMembers.getOrPut(roomId) { linkedMapOf() }
        val currentCount = members.values.count { it == team.id }
        if (currentCount >= team.maxPlayers) return false
        members[player.uniqueId] = team.id
        return true
    }

    fun leave(roomId: String, playerId: UUID) {
        teamMembers[roomId]?.remove(playerId)
    }

    fun getTeam(roomId: String, playerId: UUID): GameTeam? {
        val teamId = teamMembers[roomId]?.get(playerId) ?: return null
        return roomTeams[roomId]?.get(teamId)
    }

    fun getTeams(roomId: String): List<GameTeam> {
        return roomTeams[roomId]?.values?.toList().orEmpty()
    }

    fun getMembers(roomId: String, teamId: String): Set<UUID> {
        return teamMembers[roomId]
            ?.filterValues { it.equals(teamId, ignoreCase = true) }
            ?.keys
            ?.toSet()
            .orEmpty()
    }

    fun getUngroupedPlayers(roomId: String, players: Collection<UUID>): Set<UUID> {
        val assigned = teamMembers[roomId]?.keys.orEmpty()
        return players.filterNot { assigned.contains(it) }.toSet()
    }

    fun clearRoom(roomId: String) {
        roomTeams.remove(roomId)
        teamMembers.remove(roomId)
    }
}
