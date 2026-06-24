package org.katacr.kagamecenter.parkour

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.selection.RegionSelection
import java.io.File

class ParkourConfigService(
    private val dataFolder: File
) {
    private val file = File(dataFolder, "config.yml")
    private var config = YamlConfiguration()

    fun reload(): ParkourConfig {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        if (!file.exists()) file.createNewFile()
        config = YamlConfiguration.loadConfiguration(file)
        ensureDefaults()
        save()
        return current()
    }

    fun current(): ParkourConfig {
        val maps = linkedMapOf<String, ParkourMapConfig>()
        config.getConfigurationSection("maps")?.getKeys(false)?.forEach { mapId ->
            val section = config.getConfigurationSection("maps.$mapId") ?: return@forEach
            maps[mapId] = readMap(mapId, section)
        }
        return ParkourConfig(
            enabled = config.getBoolean("enabled", true),
            displayName = config.getString("game.display-name", "跑酷") ?: "跑酷",
            minPlayers = config.getInt("game.min-players", 1).coerceAtLeast(1),
            maxPlayers = config.getInt("game.max-players", 16).coerceAtLeast(1),
            startCountdownSeconds = config.getInt("game.start-countdown-seconds", 5).coerceIn(1, 30),
            finishCountdownSeconds = config.getInt("game.finish-countdown-seconds", 60).coerceIn(5, 600),
            resultDisplaySeconds = config.getInt("game.result-display-seconds", 15).coerceIn(3, 120),
            closeDelaySeconds = config.getInt("game.close-delay-seconds", 10).coerceIn(1, 120),
            fallY = config.getDouble("game.fall-y", -32.0),
            checkpointGlowSeconds = config.getInt("game.checkpoint-glow-seconds", 3).coerceIn(1, 30),
            checkpointGlowColor = config.getString("game.checkpoint-glow-color", "yellow") ?: "yellow",
            rewards = ParkourRewardConfig(
                enabled = config.getBoolean("game.rewards.enabled", true),
                basePoints = config.getInt("game.rewards.base-points", 1000).coerceAtLeast(0),
                minimumPoints = config.getInt("game.rewards.minimum-points", 1).coerceAtLeast(0),
                timePenaltyPerSecond = config.getInt("game.rewards.time-penalty-per-second", 10).coerceAtLeast(0),
                rankBonus = config.getIntegerList("game.rewards.rank-bonus").ifEmpty { listOf(100, 50, 25) }
            ),
            maps = maps
        )
    }

    fun saveLobby(mapId: String, routeId: String, point: ParkourPoint) {
        point.writeTo(routeSection(mapId, routeId).createSectionReplacing("lobby"))
        save()
    }

    fun saveStartSpawn(mapId: String, routeId: String, point: ParkourPoint) {
        point.writeTo(routeSection(mapId, routeId).createSectionReplacing("start.spawn"))
        save()
    }

    fun saveStartRegion(mapId: String, routeId: String, region: RegionSelection) {
        region.withoutWorld().writeTo(routeSection(mapId, routeId).createSectionReplacing("start.region"))
        save()
    }

    fun saveFinishRegion(mapId: String, routeId: String, region: RegionSelection) {
        val route = routeSection(mapId, routeId)
        region.withoutWorld().writeTo(route.createSectionReplacing("finish.region"))
        region.withoutWorld().writeTo(route.createSectionReplacing("finish.glow-region"))
        save()
    }

    fun addCheckpoint(mapId: String, routeId: String, checkpointId: String, region: RegionSelection, respawn: ParkourPoint) {
        val route = routeSection(mapId, routeId)
        val checkpoints = route.getMapList("checkpoints").map { linkedMapOf<String, Any?>(*it.entries.map { entry -> entry.key.toString() to entry.value }.toTypedArray()) }.toMutableList()
        val existingIndex = checkpoints.indexOfFirst { it["id"] == checkpointId }
        val checkpoint = linkedMapOf<String, Any?>(
            "id" to checkpointId,
            "display-name" to checkpointId,
            "region" to regionToMap(region),
            "respawn" to pointToMap(respawn),
            "glow-region" to regionToMap(region)
        )
        if (existingIndex >= 0) checkpoints[existingIndex] = checkpoint else checkpoints.add(checkpoint)
        route.set("checkpoints", checkpoints)
        save()
    }

    fun removeCheckpoint(mapId: String, routeId: String, checkpointId: String): Boolean {
        val route = routeSection(mapId, routeId)
        val checkpoints = route.getMapList("checkpoints").toMutableList()
        val removed = checkpoints.removeIf { it["id"] == checkpointId }
        if (removed) {
            route.set("checkpoints", checkpoints)
            save()
        }
        return removed
    }

    fun addSpeedBuff(mapId: String, routeId: String, buffId: String, point: ParkourPoint) {
        val route = routeSection(mapId, routeId)
        val buffs = route.getMapList("buffs").map { linkedMapOf<String, Any?>(*it.entries.map { entry -> entry.key.toString() to entry.value }.toTypedArray()) }.toMutableList()
        val existingIndex = buffs.indexOfFirst { it["id"] == buffId }
        val buff = linkedMapOf<String, Any?>(
            "id" to buffId,
            "type" to "speed2",
            "point" to pointToMap(point),
            "color" to "aqua",
            "duration-seconds" to 10,
            "amplifier" to 1,
            "respawn-seconds" to 15
        )
        if (existingIndex >= 0) buffs[existingIndex] = buff else buffs.add(buff)
        route.set("buffs", buffs)
        save()
    }

    fun removeBuff(mapId: String, routeId: String, buffId: String): Boolean {
        val route = routeSection(mapId, routeId)
        val buffs = route.getMapList("buffs").toMutableList()
        val removed = buffs.removeIf { it["id"] == buffId }
        if (removed) {
            route.set("buffs", buffs)
            save()
        }
        return removed
    }

    fun mapIds(): List<String> = config.getConfigurationSection("maps")?.getKeys(false)?.toList().orEmpty()

    fun routeIds(mapId: String): List<String> = config.getConfigurationSection("maps.$mapId.routes")?.getKeys(false)?.toList().orEmpty()

    fun findMapByTemplate(template: String?): ParkourMapConfig? {
        val current = current()
        return current.maps.values.firstOrNull { it.template == template || it.id == template?.substringAfterLast('/') }
    }

    fun findMap(mapId: String?): ParkourMapConfig? = mapId?.let { current().maps[it] }

    fun findRoute(mapId: String?, routeId: String?): ParkourRouteConfig? {
        return findMap(mapId)?.routes?.get(routeId)
    }

    fun readManagedRoute(game: ManagedGameConfig): ParkourRouteConfig {
        val managedConfig = YamlConfiguration.loadConfiguration(game.file)
        val section = managedConfig.getConfigurationSection("parkour") ?: managedConfig.createSection("parkour")
        return readRoute(game.localId, section)
    }

    fun saveManagedLobby(game: ManagedGameConfig, point: ParkourPoint) {
        saveManaged(game) { point.writeTo(it.createSectionReplacing("parkour.lobby")) }
    }

    fun saveManagedStartSpawn(game: ManagedGameConfig, point: ParkourPoint) {
        saveManaged(game) { point.writeTo(it.createSectionReplacing("parkour.start.spawn")) }
    }

    fun saveManagedStartRegion(game: ManagedGameConfig, region: RegionSelection) {
        saveManaged(game) { region.withoutWorld().writeTo(it.createSectionReplacing("parkour.start.region")) }
    }

    fun saveManagedFinishRegion(game: ManagedGameConfig, region: RegionSelection) {
        saveManaged(game) {
            region.withoutWorld().writeTo(it.createSectionReplacing("parkour.finish.region"))
            region.withoutWorld().writeTo(it.createSectionReplacing("parkour.finish.glow-region"))
        }
    }

    fun addManagedCheckpoint(game: ManagedGameConfig, checkpointId: String, region: RegionSelection, respawn: ParkourPoint) {
        saveManaged(game) { managedConfig ->
            val checkpoints = managedConfig.getMapList("parkour.checkpoints")
                .map { linkedMapOf<String, Any?>(*it.entries.map { entry -> entry.key.toString() to entry.value }.toTypedArray()) }
                .toMutableList()
            val existingIndex = checkpoints.indexOfFirst { it["id"] == checkpointId }
            val checkpoint = linkedMapOf<String, Any?>(
                "id" to checkpointId,
                "display-name" to checkpointId,
                "region" to regionToMap(region),
                "respawn" to pointToMap(respawn),
                "glow-region" to regionToMap(region)
            )
            if (existingIndex >= 0) checkpoints[existingIndex] = checkpoint else checkpoints.add(checkpoint)
            managedConfig.set("parkour.checkpoints", checkpoints)
        }
    }

    fun removeManagedCheckpoint(game: ManagedGameConfig, checkpointId: String): Boolean {
        val managedConfig = YamlConfiguration.loadConfiguration(game.file)
        val checkpoints = managedConfig.getMapList("parkour.checkpoints").toMutableList()
        val removed = checkpoints.removeIf { it["id"] == checkpointId }
        if (removed) {
            managedConfig.set("parkour.checkpoints", checkpoints)
            managedConfig.save(game.file)
        }
        return removed
    }

    fun addManagedSpeedBuff(game: ManagedGameConfig, buffId: String, point: ParkourPoint) {
        saveManaged(game) { managedConfig ->
            val buffs = managedConfig.getMapList("parkour.buffs")
                .map { linkedMapOf<String, Any?>(*it.entries.map { entry -> entry.key.toString() to entry.value }.toTypedArray()) }
                .toMutableList()
            val existingIndex = buffs.indexOfFirst { it["id"] == buffId }
            val buff = linkedMapOf<String, Any?>(
                "id" to buffId,
                "type" to "speed2",
                "point" to pointToMap(point),
                "color" to "aqua",
                "duration-seconds" to 10,
                "amplifier" to 1,
                "respawn-seconds" to 15
            )
            if (existingIndex >= 0) buffs[existingIndex] = buff else buffs.add(buff)
            managedConfig.set("parkour.buffs", buffs)
        }
    }

    fun removeManagedBuff(game: ManagedGameConfig, buffId: String): Boolean {
        val managedConfig = YamlConfiguration.loadConfiguration(game.file)
        val buffs = managedConfig.getMapList("parkour.buffs").toMutableList()
        val removed = buffs.removeIf { it["id"] == buffId }
        if (removed) {
            managedConfig.set("parkour.buffs", buffs)
            managedConfig.save(game.file)
        }
        return removed
    }

    private fun saveManaged(game: ManagedGameConfig, mutate: (YamlConfiguration) -> Unit) {
        val managedConfig = YamlConfiguration.loadConfiguration(game.file)
        mutate(managedConfig)
        managedConfig.save(game.file)
    }

    private fun readMap(id: String, section: ConfigurationSection): ParkourMapConfig {
        val routes = linkedMapOf<String, ParkourRouteConfig>()
        section.getConfigurationSection("routes")?.getKeys(false)?.forEach { routeId ->
            val routeSection = section.getConfigurationSection("routes.$routeId") ?: return@forEach
            routes[routeId] = readRoute(routeId, routeSection)
        }
        return ParkourMapConfig(
            id = id,
            displayName = section.getString("display-name", id) ?: id,
            template = section.getString("template", "parkour/$id") ?: "parkour/$id",
            routes = routes
        )
    }

    private fun readRoute(id: String, section: ConfigurationSection): ParkourRouteConfig {
        val checkpoints = readSectionList(section, "checkpoints").mapNotNull { memory ->
            val checkpointId = memory.getString("id") ?: return@mapNotNull null
            val region = RegionSelection.read(memory.getConfigurationSection("region")) ?: return@mapNotNull null
            val respawn = ParkourPoint.read(memory.getConfigurationSection("respawn")) ?: return@mapNotNull null
            ParkourCheckpointConfig(
                id = checkpointId,
                displayName = memory.getString("display-name", checkpointId) ?: checkpointId,
                region = region,
                respawn = respawn,
                glowRegion = RegionSelection.read(memory.getConfigurationSection("glow-region")) ?: region
            )
        }
        val buffs = readSectionList(section, "buffs").mapNotNull { memory ->
            val buffId = memory.getString("id") ?: return@mapNotNull null
            val point = ParkourPoint.read(memory.getConfigurationSection("point")) ?: return@mapNotNull null
            ParkourBuffConfig(
                id = buffId,
                type = memory.getString("type", "speed2") ?: "speed2",
                point = point,
                color = memory.getString("color", "aqua") ?: "aqua",
                durationSeconds = memory.getInt("duration-seconds", 10).coerceIn(1, 120),
                amplifier = memory.getInt("amplifier", 1).coerceIn(0, 10),
                respawnSeconds = memory.getInt("respawn-seconds", 15).coerceIn(1, 600)
            )
        }
        return ParkourRouteConfig(
            id = id,
            displayName = section.getString("display-name", id) ?: id,
            maxPlayers = section.getInt("max-players", config.getInt("game.max-players", 16)).coerceAtLeast(1),
            lobby = ParkourPoint.read(section.getConfigurationSection("lobby")),
            start = ParkourStartConfig(
                region = RegionSelection.read(section.getConfigurationSection("start.region")),
                spawn = ParkourPoint.read(section.getConfigurationSection("start.spawn"))
            ),
            checkpoints = checkpoints,
            finish = RegionSelection.read(section.getConfigurationSection("finish.region"))?.let { region ->
                ParkourFinishConfig(
                    region = region,
                    glowRegion = RegionSelection.read(section.getConfigurationSection("finish.glow-region")) ?: region
                )
            },
            fallY = if (section.contains("fall-y")) section.getDouble("fall-y") else null,
            buffs = buffs
        )
    }

    private fun readSectionList(section: ConfigurationSection, path: String): List<YamlConfiguration> {
        return section.getMapList(path).map { map ->
            YamlConfiguration().also { memory ->
                writeMap(memory, "", map)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeMap(config: YamlConfiguration, prefix: String, map: Map<*, *>) {
        map.forEach { (rawKey, value) ->
            val key = rawKey?.toString() ?: return@forEach
            val path = if (prefix.isBlank()) key else "$prefix.$key"
            when (value) {
                is Map<*, *> -> writeMap(config, path, value)
                else -> config.set(path, value)
            }
        }
    }

    private fun ensureDefaults() {
        config.addDefault("enabled", true)
        config.addDefault("game.display-name", "跑酷")
        if (config.getString("game.display-name") == "Parkour") {
            config.set("game.display-name", "跑酷")
        }
        config.addDefault("game.min-players", 1)
        config.addDefault("game.max-players", 16)
        config.addDefault("game.start-countdown-seconds", 5)
        config.addDefault("game.finish-countdown-seconds", 60)
        config.addDefault("game.result-display-seconds", 15)
        config.addDefault("game.close-delay-seconds", 10)
        config.addDefault("game.fall-y", -32.0)
        config.addDefault("game.checkpoint-glow-seconds", 3)
        config.addDefault("game.checkpoint-glow-color", "yellow")
        config.addDefault("game.rewards.enabled", true)
        config.addDefault("game.rewards.base-points", 1000)
        config.addDefault("game.rewards.minimum-points", 1)
        config.addDefault("game.rewards.time-penalty-per-second", 10)
        config.addDefault("game.rewards.rank-bonus", listOf(100, 50, 25))
        config.addDefault("spectator.enabled", true)
        config.addDefault("spectator.mode", "managed")
        config.options().copyDefaults(true)
    }

    private fun routeSection(mapId: String, routeId: String): ConfigurationSection {
        val mapSection = config.getConfigurationSection("maps.$mapId") ?: config.createSection("maps.$mapId")
        if (!mapSection.contains("display-name")) mapSection.set("display-name", mapId)
        if (!mapSection.contains("template")) mapSection.set("template", "parkour/$mapId")
        return config.getConfigurationSection("maps.$mapId.routes.$routeId") ?: config.createSection("maps.$mapId.routes.$routeId")
    }

    private fun save() {
        config.save(file)
    }

    private fun regionToMap(region: RegionSelection): Map<String, Any> = linkedMapOf(
        "min" to linkedMapOf("x" to region.minX, "y" to region.minY, "z" to region.minZ),
        "max" to linkedMapOf("x" to region.maxX, "y" to region.maxY, "z" to region.maxZ)
    )

    private fun pointToMap(point: ParkourPoint): Map<String, Any> = linkedMapOf(
        "x" to point.x,
        "y" to point.y,
        "z" to point.z,
        "yaw" to point.yaw,
        "pitch" to point.pitch
    )
}

private fun ConfigurationSection.createSectionReplacing(path: String): ConfigurationSection {
    set(path, null)
    return createSection(path)
}
