package org.katacr.kaGameCenter.dialog

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.game.GameDefinition
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.team.GameTeam
import java.io.File

class MenuTemplateService(
    private val plugin: JavaPlugin
) {
    private val menusFolder: File
        get() = File(plugin.dataFolder, "menus")

    fun init() {
        DEFAULT_TEMPLATES.forEach { ensureTemplate(it) }
    }

    fun load(menuId: String): YamlConfiguration? {
        ensureTemplate(menuId)
        val file = File(menusFolder, "$menuId.yml")
        if (!file.isFile) return null
        return YamlConfiguration.loadConfiguration(file)
    }

    fun replacePlaceholders(config: YamlConfiguration, values: Map<String, String>): YamlConfiguration {
        replaceSection(config, values)
        return config
    }

    fun buildContext(
        pluginName: String,
        player: Player,
        room: GameRoom? = null,
        game: GameDefinition? = null,
        team: GameTeam? = null,
        teamMemberCount: Int? = null,
        target: Player? = null
    ): Map<String, String> {
        val maxPlayers = room?.definition?.maxPlayers ?: room?.module?.maxPlayers ?: game?.maxPlayers ?: 0
        val roomPlayers = room?.players?.size ?: 0
        val roomSpectators = room?.spectators?.size ?: 0
        return linkedMapOf(
            "plugin.name" to pluginName,
            "viewer.uuid" to player.uniqueId.toString(),
            "viewer.name" to player.name,
            "viewer.display_name" to player.name,
            "viewer.world" to player.world.name,
            "viewer.x" to player.location.x.toString(),
            "viewer.y" to player.location.y.toString(),
            "viewer.z" to player.location.z.toString(),
            "viewer.level" to player.level.toString(),
            "viewer.health" to player.health.toString(),
            "viewer.food" to player.foodLevel.toString(),
            "player.uuid" to player.uniqueId.toString(),
            "player.name" to player.name,
            "player.display_name" to player.name,
            "player.world" to player.world.name,
            "player.x" to player.location.x.toString(),
            "player.y" to player.location.y.toString(),
            "player.z" to player.location.z.toString(),
            "player.level" to player.level.toString(),
            "player.health" to player.health.toString(),
            "player.food" to player.foodLevel.toString(),
            "room.id" to (room?.id ?: "-"),
            "room.name" to (room?.name ?: "-"),
            "room.map_template" to (room?.mapTemplate ?: "-"),
            "room.map_id" to (room?.mapTemplate?.substringAfterLast('/') ?: "-"),
            "room.state" to (room?.state?.name ?: "-"),
            "room.can_join" to (room?.canJoin()?.toString() ?: "false"),
            "room.players" to roomPlayers.toString(),
            "room.player_count" to roomPlayers.toString(),
            "room.max_players" to maxPlayers.toString(),
            "room.spectators" to roomSpectators.toString(),
            "room.spectator_count" to roomSpectators.toString(),
            "room.world" to (room?.world?.name ?: "-"),
            "room.owner_uuid" to (room?.owner?.toString() ?: "-"),
            "room.owner_name" to (room?.owner?.let { org.bukkit.Bukkit.getPlayer(it)?.name ?: it.toString().take(8) } ?: "-"),
            "game.id" to (game?.id ?: room?.module?.id ?: "-"),
            "game.name" to (game?.displayName ?: room?.definition?.displayName ?: room?.module?.displayName ?: "-"),
            "game.enabled" to (game?.enabled?.toString() ?: room?.definition?.enabled?.toString() ?: "false"),
            "game.min_players" to (game?.minPlayers ?: room?.definition?.minPlayers ?: room?.module?.minPlayers ?: 0).toString(),
            "game.max_players" to (game?.maxPlayers ?: room?.definition?.maxPlayers ?: room?.module?.maxPlayers ?: 0).toString(),
            "game.duration" to (game?.defaultDurationSeconds ?: room?.definition?.defaultDurationSeconds ?: 0).toString(),
            "game.description" to (game?.description ?: room?.definition?.description ?: ""),
            "team.id" to (team?.id ?: "-"),
            "team.name" to (team?.displayName ?: "-"),
            "team.max_players" to (team?.maxPlayers?.toString() ?: "0"),
            "team.member_count" to (teamMemberCount?.toString() ?: "0"),
            "target.uuid" to (target?.uniqueId?.toString() ?: "-"),
            "target.name" to (target?.name ?: "-"),
            "target.display_name" to (target?.name ?: "-"),
            "target.world" to (target?.world?.name ?: "-")
        )
    }

    private fun replaceSection(section: ConfigurationSection, values: Map<String, String>) {
        section.getKeys(false).forEach { key ->
            when (val value = section.get(key)) {
                is String -> section.set(key, replace(value, values))
                is List<*> -> section.set(key, value.map { item -> if (item is String) replace(item, values) else item })
                is ConfigurationSection -> replaceSection(value, values)
                else -> Unit
            }
        }
    }

    private fun replace(text: String, values: Map<String, String>): String {
        var output = text
        values.forEach { (key, value) ->
            output = output.replace("{$key}", value)
        }
        return output
    }

    private fun ensureTemplate(menuId: String) {
        val target = File(menusFolder, "$menuId.yml")
        if (target.exists()) return
        target.parentFile?.mkdirs()
        val resourcePath = "menus/$menuId.yml"
        if (plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false)
        }
    }

    companion object {
        private val DEFAULT_TEMPLATES = listOf(
            "main",
            "games",
            "create_game",
            "create_map",
            "rooms",
            "room",
            "room_member",
            "maps",
            "map_detail"
        )
    }
}
