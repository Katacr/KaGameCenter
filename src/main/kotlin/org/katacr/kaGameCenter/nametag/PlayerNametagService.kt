package org.katacr.kaGameCenter.nametag

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.packet.PacketDispatchService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerNametagService(
    private val packetService: PacketDispatchService
) {
    private val tags = ConcurrentHashMap<RoomTargetKey, PlayerNametag>()
    private val sentTeams = ConcurrentHashMap<UUID, MutableSet<String>>()

    fun set(room: GameRoom, target: Player, nametag: PlayerNametag, viewers: Collection<Player> = room.viewersOnline()) {
        val key = RoomTargetKey(room.id, target.uniqueId)
        tags[key] = nametag
        if (!packetService.available) return
        viewers
            .filter { it.isOnline && it.world == target.world }
            .forEach { viewer -> send(viewer, target, key, nametag) }
    }

    fun refresh(room: GameRoom, target: Player, viewers: Collection<Player> = room.viewersOnline()) {
        val nametag = tags[RoomTargetKey(room.id, target.uniqueId)] ?: return
        set(room, target, nametag, viewers)
    }

    /** 只向指定 viewer 发送临时名牌覆盖，不修改房间保存的标准名牌。 */
    fun sendOverride(room: GameRoom, target: Player, nametag: PlayerNametag, viewers: Collection<Player>) {
        if (!packetService.available) return
        val key = RoomTargetKey(room.id, target.uniqueId)
        viewers
            .filter { it.isOnline && it.world == target.world }
            .forEach { viewer -> send(viewer, target, key, nametag) }
    }

    fun refreshRoom(room: GameRoom) {
        if (!packetService.available) return
        val viewers = room.viewersOnline()
        room.participantsOnline().forEach { target ->
            val nametag = tags[RoomTargetKey(room.id, target.uniqueId)] ?: return@forEach
            viewers
                .filter { it.isOnline && it.world == target.world }
                .forEach { viewer -> send(viewer, target, RoomTargetKey(room.id, target.uniqueId), nametag) }
        }
    }

    fun refreshViewer(room: GameRoom, viewer: Player) {
        if (!packetService.available || !viewer.isOnline) return
        room.participantsOnline()
            .filter { it.world == viewer.world }
            .forEach { target ->
                val key = RoomTargetKey(room.id, target.uniqueId)
                val nametag = tags[key] ?: return@forEach
                send(viewer, target, key, nametag)
            }
    }

    fun clear(room: GameRoom, target: Player, viewers: Collection<Player> = room.viewersOnline()) {
        val key = RoomTargetKey(room.id, target.uniqueId)
        tags.remove(key)
        viewers
            .filter { it.isOnline }
            .forEach { viewer -> clear(viewer, key) }
    }

    fun clearTarget(targetId: UUID) {
        tags.keys
            .filter { it.targetId == targetId }
            .forEach { key ->
                tags.remove(key)
                clearTeamFromAllViewers(key)
            }
    }

    fun clearViewer(viewer: Player) {
        val teams = sentTeams.remove(viewer.uniqueId).orEmpty()
        teams.forEach { teamName -> packetService.clearNametagTeam(viewer, teamName) }
    }

    fun clearRoom(roomId: String) {
        tags.keys
            .filter { it.roomId == roomId }
            .forEach { key ->
                tags.remove(key)
                clearTeamFromAllViewers(key)
            }
    }

    fun clearAll() {
        Bukkit.getOnlinePlayers().forEach(::clearViewer)
        tags.clear()
        sentTeams.clear()
    }

    private fun send(viewer: Player, target: Player, key: RoomTargetKey, nametag: PlayerNametag) {
        val teamName = teamName(key)
        if (sentTeams[viewer.uniqueId]?.contains(teamName) == true) {
            packetService.clearNametagTeam(viewer, teamName)
        }
        packetService.sendPlayerNametag(viewer, teamName, target.name, nametag)
        sentTeams.computeIfAbsent(viewer.uniqueId) { ConcurrentHashMap.newKeySet() }.add(teamName)
    }

    private fun clear(viewer: Player, key: RoomTargetKey) {
        val teamName = teamName(key)
        packetService.clearNametagTeam(viewer, teamName)
        sentTeams[viewer.uniqueId]?.remove(teamName)
    }

    private fun clearTeamFromAllViewers(key: RoomTargetKey) {
        val teamName = teamName(key)
        Bukkit.getOnlinePlayers().forEach { viewer ->
            packetService.clearNametagTeam(viewer, teamName)
            sentTeams[viewer.uniqueId]?.remove(teamName)
        }
    }

    private fun GameRoom.viewersOnline(): List<Player> {
        return (players + spectators).mapNotNull(Bukkit::getPlayer)
    }

    private fun GameRoom.participantsOnline(): List<Player> {
        return players.mapNotNull(Bukkit::getPlayer)
    }

    private fun teamName(key: RoomTargetKey): String {
        val hash = "${key.roomId}:${key.targetId}".hashCode().toUInt().toString(36)
        return "kgcn${hash.take(12)}"
    }

    private data class RoomTargetKey(
        val roomId: String,
        val targetId: UUID
    )
}
