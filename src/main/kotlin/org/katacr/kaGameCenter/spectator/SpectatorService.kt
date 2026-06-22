package org.katacr.kaGameCenter.spectator

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameState
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.bukkit.NamespacedKey
import java.util.UUID

class SpectatorService(
    private val plugin: JavaPlugin,
    private val languageManager: LanguageManager
) {
    private val spectatorStates = linkedMapOf<UUID, SpectatorState>()
    private val followTargets = linkedMapOf<UUID, UUID>()
    private val itemKey = NamespacedKey(plugin, "spectator_action")

    fun canSpectate(room: GameRoom, policy: SpectatorPolicy): Boolean {
        if (!policy.enabled) return false
        if (room.state == GameState.CLOSED) return false
        if (!policy.allowDuringRunning && room.state == GameState.RUNNING) return false
        return true
    }

    fun enter(player: Player, room: GameRoom, policy: SpectatorPolicy = SpectatorPolicy.DEFAULT) {
        spectatorStates[player.uniqueId] = SpectatorState(room.id, policy.mode, policy.allowFollowPlayer)
        applyMode(player, room, policy)
        teleportToSpectatorSpawn(player, room)
        player.sendActionBar(Component.text(languageManager.getMessage("spectator.action_joined", room.id), NamedTextColor.GRAY))
    }

    fun exit(player: Player) {
        spectatorStates.remove(player.uniqueId)
        followTargets.remove(player.uniqueId)
        clearSpectatorTarget(player)
        player.sendActionBar(Component.text(languageManager.getMessage("spectator.action_left"), NamedTextColor.GRAY))
    }

    fun clearRoom(roomId: String) {
        val removed = spectatorStates.filterValues { it.roomId == roomId }.keys
        spectatorStates.entries.removeIf { it.value.roomId == roomId }
        removed.forEach(followTargets::remove)
    }

    fun isSpectator(playerId: UUID): Boolean {
        return spectatorStates.containsKey(playerId)
    }

    fun isSpectator(player: Player): Boolean {
        return isSpectator(player.uniqueId)
    }

    fun roomId(playerId: UUID): String? {
        return spectatorStates[playerId]?.roomId
    }

    fun follow(player: Player, target: Player): Boolean {
        val state = spectatorStates[player.uniqueId] ?: return false
        if (!state.allowFollowPlayer) return false
        followTargets[player.uniqueId] = target.uniqueId
        if (state.mode != SpectatorMode.VANILLA) {
            player.teleport(target.location)
            player.sendActionBar(Component.text(languageManager.getMessage("spectator.following", target.name), NamedTextColor.GRAY))
            return true
        }
        if (player.gameMode != GameMode.SPECTATOR) {
            player.gameMode = GameMode.SPECTATOR
        }
        player.spectatorTarget = target
        player.sendActionBar(Component.text(languageManager.getMessage("spectator.following", target.name), NamedTextColor.GRAY))
        return true
    }

    fun nextTarget(player: Player, room: GameRoom): Player? {
        val targets = room.players
            .mapNotNull(Bukkit::getPlayer)
            .filter { it.uniqueId != player.uniqueId && it.isOnline }
            .sortedBy { it.name.lowercase() }
        if (targets.isEmpty()) return null

        val current = followTargets[player.uniqueId]
        val currentIndex = targets.indexOfFirst { it.uniqueId == current }
        return targets[(currentIndex + 1).floorMod(targets.size)]
    }

    fun action(itemStack: ItemStack?): SpectatorAction? {
        val meta = itemStack?.itemMeta ?: return null
        val value = meta.persistentDataContainer.get(itemKey, PersistentDataType.STRING) ?: return null
        return runCatching { SpectatorAction.valueOf(value.uppercase()) }.getOrNull()
    }

    fun sendHotbar(player: Player) {
        val state = spectatorStates[player.uniqueId] ?: return
        if (state.mode != SpectatorMode.MANAGED) return

        player.inventory.clear()
        player.inventory.setItem(0, menuItem(Material.COMPASS, languageManager.getMessage("spectator.item_follow"), SpectatorAction.FOLLOW))
        player.inventory.setItem(4, menuItem(Material.NETHER_STAR, languageManager.getMessage("spectator.item_menu"), SpectatorAction.MENU))
        player.inventory.setItem(8, menuItem(Material.BARRIER, languageManager.getMessage("spectator.item_leave"), SpectatorAction.LEAVE))
        player.updateInventory()
    }

    private fun applyMode(player: Player, room: GameRoom, policy: SpectatorPolicy) {
        when (policy.mode) {
            SpectatorMode.VANILLA -> {
                player.gameMode = GameMode.SPECTATOR
                player.spectatorTarget = null
            }
            SpectatorMode.MANAGED -> {
                clearSpectatorTarget(player)
                player.gameMode = GameMode.ADVENTURE
                player.isInvisible = true
                player.isInvulnerable = true
                player.allowFlight = policy.allowFreeFly
                player.isFlying = policy.allowFreeFly
                sendHotbar(player)
            }
        }
        if (room.players.isNotEmpty()) {
            val firstTarget = room.players.firstNotNullOfOrNull(Bukkit::getPlayer)
            if (firstTarget != null && policy.mode == SpectatorMode.VANILLA && policy.allowFollowPlayer) {
                player.spectatorTarget = firstTarget
            }
        }
    }

    private fun clearSpectatorTarget(player: Player) {
        if (player.gameMode == GameMode.SPECTATOR) {
            player.spectatorTarget = null
        }
    }

    private fun teleportToSpectatorSpawn(player: Player, room: GameRoom) {
        val location = spectatorSpawn(room) ?: return
        player.teleport(location)
    }

    private fun spectatorSpawn(room: GameRoom): Location? {
        return room.world?.spawnLocation ?: Bukkit.getWorlds().firstOrNull()?.spawnLocation
    }

    private fun menuItem(material: Material, name: String, action: SpectatorAction): ItemStack {
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(name, NamedTextColor.AQUA))
                meta.persistentDataContainer.set(itemKey, PersistentDataType.STRING, action.name)
            }
        }
    }

    private data class SpectatorState(
        val roomId: String,
        val mode: SpectatorMode,
        val allowFollowPlayer: Boolean
    )

    private fun Int.floorMod(modulus: Int): Int {
        return ((this % modulus) + modulus) % modulus
    }
}
