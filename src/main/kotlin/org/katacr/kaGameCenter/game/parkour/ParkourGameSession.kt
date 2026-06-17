package org.katacr.kaGameCenter.game.parkour

import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameSession
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.world.TemporaryWorldService
import kotlin.math.max

class ParkourGameSession(
    override val room: GameRoom,
    private val worldService: TemporaryWorldService,
    private val languageManager: LanguageManager
) : GameSession {
    private val checkpointKey = NamespacedKey.fromString("kagamecenter:parkour_checkpoint")
        ?: throw IllegalStateException("Invalid checkpoint key")
    private val checkpointItem by lazy { createCheckpointItem() }
    private val checkpoints = linkedMapOf<java.util.UUID, Location>()
    private val finished = linkedSetOf<java.util.UUID>()
    private var joinLocation: Location? = null
    private var startedAtMillis: Long = 0L

    override fun onPrepare() {
        val worldName = "kgc_${room.id}"
        val template = room.definition?.mapTemplates?.firstOrNull()
        room.world = worldService.createRoomWorldFromTemplate(template, worldName, allowFlatFallback = false)
        room.world?.let { world ->
            joinLocation = worldService.readTemplateSpawn(template, world)
            joinLocation?.let { spawn ->
                world.spawnLocation = spawn
            }
        }
    }

    override fun onPlayerJoin(player: Player) {
        val world = room.world ?: return
        val spawn = joinLocation ?: world.spawnLocation
        player.gameMode = GameMode.ADVENTURE
        player.inventory.clear()
        player.inventory.setItem(4, checkpointItem.clone())
        player.teleport(spawn)
        checkpoints[player.uniqueId] = spawn.clone()
        player.sendMessage(Component.text(languageManager.getMessage("parkour.joined", room.id)))
    }

    override fun onPlayerLeave(player: Player) {
        checkpoints.remove(player.uniqueId)
        finished.remove(player.uniqueId)
        player.sendMessage(Component.text(languageManager.getMessage("parkour.left")))
    }

    override fun onStart() {
        startedAtMillis = System.currentTimeMillis()
        room.players.mapNotNull { playerId -> org.bukkit.Bukkit.getPlayer(playerId) }
            .forEach { it.sendMessage(Component.text(languageManager.getMessage("parkour.started", room.id))) }
    }

    override fun onTick() {
        if (startedAtMillis == 0L) return
        val elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt()
        room.players.mapNotNull { playerId -> org.bukkit.Bukkit.getPlayer(playerId) }
            .forEach { player ->
                if (finished.contains(player.uniqueId)) return@forEach
                player.sendActionBar(Component.text(languageManager.getMessage("room.running_actionbar", room.id, elapsedSeconds)))
            }
    }

    override fun onEnd() {
        room.players.mapNotNull { playerId -> org.bukkit.Bukkit.getPlayer(playerId) }
            .forEach { it.sendMessage(Component.text(languageManager.getMessage("parkour.ended", room.id))) }
    }

    fun isCheckpointItem(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.CARROT_ON_A_STICK || !item.hasItemMeta()) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(checkpointKey, PersistentDataType.BYTE)
    }

    fun saveCheckpoint(player: Player): Boolean {
        val location = player.location.clone()
        checkpoints[player.uniqueId] = location
        player.sendMessage(Component.text(languageManager.getMessage("parkour.checkpoint_saved")))
        return true
    }

    fun teleportToCheckpoint(player: Player): Boolean {
        val location = checkpoints[player.uniqueId]
        if (location == null) {
            player.sendMessage(Component.text(languageManager.getMessage("parkour.no_checkpoint")))
            return false
        }
        player.teleport(location)
        player.sendMessage(Component.text(languageManager.getMessage("parkour.checkpoint_returned")))
        return true
    }

    fun finish(player: Player): Int? {
        if (!finished.add(player.uniqueId)) return null

        if (startedAtMillis == 0L) {
            startedAtMillis = System.currentTimeMillis()
        }
        val elapsedSeconds = ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt()
        val points = max(1, 1000 - elapsedSeconds * 10)
        player.sendMessage(Component.text(languageManager.getMessage("parkour.finished", elapsedSeconds, points)))
        return points
    }

    fun allPlayersFinished(): Boolean {
        return room.players.all { finished.contains(it) }
    }

    private fun createCheckpointItem(): ItemStack {
        val item = ItemStack(Material.CARROT_ON_A_STICK)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(languageManager.getMessage("parkour.item_name"))
        meta.lore = listOf(languageManager.getMessage("parkour.item_lore"))
        meta.persistentDataContainer.set(checkpointKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }
}
