package org.katacr.kagamecenter.tntwars

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.selection.RegionSelection
import java.io.File

class TntWarsConfigService(
    private val dataFolder: File
) {
    private val file = File(dataFolder, "config.yml")
    private var config = YamlConfiguration()

    fun reload(): TntWarsConfig {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        if (!file.exists()) file.createNewFile()
        config = YamlConfiguration.loadConfiguration(file)
        ensureDefaults()
        save()
        return current()
    }

    fun current(): TntWarsConfig {
        val maps = linkedMapOf<String, TntWarsMapConfig>()
        config.getConfigurationSection("maps")?.getKeys(false)?.forEach { mapId ->
            val section = config.getConfigurationSection("maps.$mapId") ?: return@forEach
            maps[mapId] = TntWarsMapConfig(
                id = mapId,
                displayName = section.getString("display-name", mapId) ?: mapId,
                template = section.getString("template", "tntwars/$mapId") ?: "tntwars/$mapId"
            )
        }

        return TntWarsConfig(
            enabled = config.getBoolean("enabled", true),
            displayName = config.getString("game.display-name", "TNT Wars") ?: "TNT Wars",
            minPlayers = config.getInt("game.min-players", 2).coerceAtLeast(2),
            maxPlayers = config.getInt("game.max-players", 16).coerceAtLeast(2),
            startCountdownSeconds = config.getInt("game.start-countdown-seconds", 5).coerceIn(1, 30),
            durationSeconds = config.getInt("game.duration-seconds", 600).coerceIn(60, 3600),
            resultDisplaySeconds = config.getInt("game.result-display-seconds", 10).coerceIn(3, 120),
            closeDelaySeconds = config.getInt("game.close-delay-seconds", 6).coerceIn(1, 120),
            defaultVoidY = config.getDouble("game.void-y", -70.0),
            itemIntervalSeconds = config.getInt("game.item-interval-seconds", 10).coerceIn(3, 600),
            initialItemDelaySeconds = config.getInt("game.initial-item-delay-seconds", 10).coerceIn(0, 600),
            resistanceAmplifier = config.getInt("game.resistance-amplifier", 25).coerceIn(0, 255),
            glowingEnabled = config.getBoolean("game.glowing-enabled", true),
            givePerPlayer = config.getInt("items.give-per-player", 1).coerceIn(1, 16),
            consumeOnUse = config.getBoolean("items.consume-on-use", true),
            items = TntWarsItemType.entries.associateWith { readItemConfig(it) },
            maps = maps
        )
    }

    fun findMapByTemplate(template: String?): TntWarsMapConfig? {
        return current().maps.values.firstOrNull { it.template == template || it.id == template?.substringAfterLast('/') }
    }

    fun readManagedGame(game: ManagedGameConfig): TntWarsGameConfig {
        val managedConfig = YamlConfiguration.loadConfiguration(game.file)
        val section = managedConfig.getConfigurationSection("tntwars") ?: managedConfig.createSection("tntwars")
        return TntWarsGameConfig(
            lobby = TntWarsPoint.read(section.getConfigurationSection("lobby")),
            spectatorSpawn = TntWarsPoint.read(section.getConfigurationSection("spectator-spawn")),
            redSpawn = TntWarsPoint.read(section.getConfigurationSection("team-spawns.red")),
            blueSpawn = TntWarsPoint.read(section.getConfigurationSection("team-spawns.blue")),
            playRegion = RegionSelection.read(section.getConfigurationSection("play-region")),
            voidY = if (section.contains("void-y")) section.getDouble("void-y") else null
        )
    }

    fun saveManagedLobby(game: ManagedGameConfig, point: TntWarsPoint) {
        saveManaged(game) { point.writeTo(it.createSectionReplacing("tntwars.lobby")) }
    }

    fun saveManagedSpectatorSpawn(game: ManagedGameConfig, point: TntWarsPoint) {
        saveManaged(game) { point.writeTo(it.createSectionReplacing("tntwars.spectator-spawn")) }
    }

    fun saveManagedTeamSpawn(game: ManagedGameConfig, team: TntWarsTeam, point: TntWarsPoint) {
        saveManaged(game) { point.writeTo(it.createSectionReplacing("tntwars.team-spawns.${team.id}")) }
    }

    fun saveManagedPlayRegion(game: ManagedGameConfig, region: RegionSelection) {
        saveManaged(game) { region.withoutWorld().writeTo(it.createSectionReplacing("tntwars.play-region")) }
    }

    fun saveManagedVoidY(game: ManagedGameConfig, y: Double) {
        saveManaged(game) { it.set("tntwars.void-y", y) }
    }

    private fun readItemConfig(type: TntWarsItemType): TntWarsItemConfig {
        val path = "items.pool.${type.configKey}"
        return TntWarsItemConfig(
            enabled = config.getBoolean("$path.enabled", true),
            weight = config.getInt("$path.weight", 1).coerceIn(0, 1000),
            fuseTicks = config.getInt("$path.fuse-ticks", 50).coerceIn(1, 20 * 60),
            velocity = config.getDouble("$path.velocity", 1.2).coerceIn(0.1, 10.0),
            power = config.getDouble("$path.power", 4.0).toFloat().coerceIn(0.0f, 20.0f),
            durationSeconds = config.getInt("$path.duration-seconds", 10).coerceIn(1, 120),
            dropsPerSecond = config.getInt("$path.drops-per-second", 4).coerceIn(1, 64)
        )
    }

    private fun saveManaged(game: ManagedGameConfig, mutate: (YamlConfiguration) -> Unit) {
        val managedConfig = YamlConfiguration.loadConfiguration(game.file)
        mutate(managedConfig)
        managedConfig.save(game.file)
    }

    private fun ensureDefaults() {
        val defaults = mapOf(
            "id" to "tntwars",
            "name" to "TNT Wars",
            "enabled" to true,
            "main" to "jar",
            "jar" to "../tntwars.jar",
            "entrypoint" to "org.katacr.kagamecenter.tntwars.TntWarsModuleProvider",
            "game.display-name" to "TNT Wars",
            "game.min-players" to 2,
            "game.max-players" to 16,
            "game.start-countdown-seconds" to 5,
            "game.duration-seconds" to 600,
            "game.result-display-seconds" to 10,
            "game.close-delay-seconds" to 6,
            "game.void-y" to -70.0,
            "game.item-interval-seconds" to 10,
            "game.initial-item-delay-seconds" to 10,
            "game.resistance-amplifier" to 25,
            "game.glowing-enabled" to true,
            "items.give-per-player" to 1,
            "items.consume-on-use" to true,
            "items.pool.tnt_minecart.enabled" to true,
            "items.pool.tnt_minecart.weight" to 3,
            "items.pool.tnt_minecart.fuse-ticks" to 50,
            "items.pool.tnt_minecart.velocity" to 1.4,
            "items.pool.tnt_minecart.power" to 4.0,
            "items.pool.tnt.enabled" to true,
            "items.pool.tnt.weight" to 2,
            "items.pool.tnt.fuse-ticks" to 50,
            "items.pool.tnt.velocity" to 1.2,
            "items.pool.tnt.power" to 4.0,
            "items.pool.long_tnt.enabled" to true,
            "items.pool.long_tnt.weight" to 2,
            "items.pool.long_tnt.fuse-ticks" to 50,
            "items.pool.long_tnt.velocity" to 2.0,
            "items.pool.long_tnt.power" to 4.0,
            "items.pool.creeper.enabled" to true,
            "items.pool.creeper.weight" to 3,
            "items.pool.creeper.fuse-ticks" to 60,
            "items.pool.creeper.velocity" to 1.4,
            "items.pool.creeper.power" to 3.0,
            "items.pool.fireball.enabled" to true,
            "items.pool.fireball.weight" to 5,
            "items.pool.fireball.velocity" to 1.5,
            "items.pool.fireball.power" to 5.0,
            "items.pool.tnt_bow.enabled" to true,
            "items.pool.tnt_bow.weight" to 2,
            "items.pool.tnt_bow.fuse-ticks" to 80,
            "items.pool.tnt_bow.power" to 4.0,
            "items.pool.tnt_rain.enabled" to true,
            "items.pool.tnt_rain.weight" to 1,
            "items.pool.tnt_rain.duration-seconds" to 10,
            "items.pool.tnt_rain.drops-per-second" to 4,
            "items.pool.tnt_rain.power" to 4.0,
            "items.pool.creeper_rain.enabled" to true,
            "items.pool.creeper_rain.weight" to 1,
            "items.pool.creeper_rain.fuse-ticks" to 30,
            "items.pool.creeper_rain.duration-seconds" to 10,
            "items.pool.creeper_rain.drops-per-second" to 2,
            "items.pool.creeper_rain.power" to 3.0,
            "items.pool.fireball_rain.enabled" to true,
            "items.pool.fireball_rain.weight" to 1,
            "items.pool.fireball_rain.duration-seconds" to 10,
            "items.pool.fireball_rain.drops-per-second" to 2,
            "items.pool.fireball_rain.power" to 4.0,
            "maps.default.display-name" to "默认地图",
            "maps.default.template" to "tntwars/default",
            "spectator.enabled" to true,
            "spectator.mode" to "managed"
        )
        defaults.forEach { (path, value) ->
            if (!config.contains(path)) config.set(path, value)
        }
    }

    private fun save() {
        config.save(file)
    }
}

private fun ConfigurationSection.createSectionReplacing(path: String): ConfigurationSection {
    set(path, null)
    return createSection(path)
}
