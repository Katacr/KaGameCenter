package org.katacr.kagamecenter.bedwars

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.Color
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.potion.PotionEffectType
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.spectator.SpectatorAction
import org.katacr.kaGameCenter.spectator.SpectatorMode
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.ZoneId

/** 读取模块默认规则和托管地图中的 BedWars 专属字段。 */
class BedWarsConfigService(private val dataFolder: File) {
    private val file = File(dataFolder, "config.yml")
    private val temporaryFile = File(dataFolder, "config.yml.tmp")
    private var config = YamlConfiguration()

    /** 重新读取模块配置并补齐向后兼容的默认值。 */
    fun reload(): BedWarsConfig {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            error("Cannot create BedWars configuration folder: ${dataFolder.absolutePath}")
        }
        val loaded = YamlConfiguration()
        val source = file.takeIf(File::isFile) ?: temporaryFile.takeIf(File::isFile)
        if (source != null) loaded.load(source)
        val previous = config
        config = loaded
        return try {
            ensureDefaults()
            val parsed = current()
            saveConfigAtomically()
            parsed
        } catch (error: Throwable) {
            config = previous
            throw error
        }
    }

    /** 通过同目录临时文件原子提交配置默认值和迁移，失败时保留原文件。 */
    private fun saveConfigAtomically() {
        try {
            config.save(temporaryFile)
            try {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    /** 返回当前内存配置的类型化视图。 */
    fun current(): BedWarsConfig {
        val diamondTiers = readGeneratorTiers(BedWarsGeneratorType.DIAMOND)
        val emeraldTiers = readGeneratorTiers(BedWarsGeneratorType.EMERALD)
        val lastGeneratorUpgrade = (diamondTiers + emeraldTiers).maxOfOrNull(BedWarsGeneratorTier::startSeconds) ?: 0
        val sequentialTimeline = config.contains("countdowns.beds-destroy") ||
            config.contains("countdowns.dragon-spawn") || config.contains("countdowns.game-end")
        val bedsDestroyAt = if (sequentialTimeline) {
            lastGeneratorUpgrade + config.getInt("countdowns.beds-destroy", 360).coerceIn(0, 7200)
        } else {
            config.getInt("game.beds-destroy-seconds", 1200).coerceIn(0, 21_600)
        }
        val suddenDeathAt = if (sequentialTimeline) {
            bedsDestroyAt + config.getInt("countdowns.dragon-spawn", 600).coerceIn(0, 7200)
        } else {
            config.getInt("game.sudden-death-seconds", 1500).coerceIn(0, 21_600)
        }
        val gameEndAt = if (sequentialTimeline) {
            suddenDeathAt + config.getInt("countdowns.game-end", 120).coerceIn(1, 7200)
        } else {
            config.getInt("game.duration-seconds", 1800).coerceIn(60, 21_600)
        }
        val minPlayers = config.getInt("game.min-players", 2).coerceIn(2, 100)
        val maxPlayers = config.getInt("game.max-players", 16).coerceIn(minPlayers, 100)
        val halloweenActive = isHalloweenActive()
        val maps = linkedMapOf<String, BedWarsMapConfig>()
        config.getConfigurationSection("maps")?.getKeys(false)?.forEach { mapId ->
            val section = config.getConfigurationSection("maps.$mapId") ?: return@forEach
            maps[mapId] = BedWarsMapConfig(
                id = mapId,
                displayName = section.getString("display-name", mapId) ?: mapId,
                template = section.getString("template", "bedwars/$mapId") ?: "bedwars/$mapId"
            )
        }
        return BedWarsConfig(
            enabled = config.getBoolean("enabled", false),
            displayName = config.getString("game.display-name", "起床战争") ?: "起床战争",
            minPlayers = minPlayers,
            maxPlayers = maxPlayers,
            countdownSeconds = config.getInt(
                "countdowns.game-start-regular",
                config.getInt("game.countdown-seconds", 40)
            ).coerceIn(1, 300),
            halfArenaCountdownSeconds = config.getInt("countdowns.game-start-half-arena", 25).coerceIn(1, 300),
            fullArenaCountdownSeconds = config.getInt("countdowns.game-start-shortened", 5).coerceIn(1, 300),
            joinAllowedSound = readSoundRule("game.sounds.join-allowed", Sound.ENTITY_SLIME_JUMP),
            joinDeniedSound = readSoundRule("game.sounds.join-denied", Sound.ENTITY_VILLAGER_NO),
            rejoinAllowedSound = readSoundRule("game.sounds.rejoin-allowed", Sound.ENTITY_SLIME_JUMP),
            rejoinDeniedSound = readSoundRule("game.sounds.rejoin-denied", Sound.ENTITY_VILLAGER_NO),
            spectateAllowedSound = readSoundRule("game.sounds.spectate-allowed", Sound.ENTITY_SLIME_JUMP),
            spectateDeniedSound = readSoundRule("game.sounds.spectate-denied", Sound.ENTITY_VILLAGER_NO),
            spectatorTargetClickSound = readSoundRule("game.sounds.spectator-target-click", Sound.ENTITY_SLIME_JUMP),
            arenaSelectorOpenSound = readSoundRule("game.sounds.arena-selector-open", Sound.ENTITY_CHICKEN_EGG),
            statsMenuOpenSound = readSoundRule("game.sounds.stats-menu-open", Sound.ENTITY_CHICKEN_EGG),
            countdownSound = readSoundRule("game.sounds.countdown", Sound.ENTITY_CHICKEN_EGG),
            countdownFinalSounds = (1..4).associateWith { second ->
                readSoundRule("game.sounds.countdown-final.$second", Sound.ENTITY_CHICKEN_EGG)
            },
            gameStartSound = readSoundRule("game.sounds.start", Sound.BLOCK_SLIME_BLOCK_FALL),
            respawnSound = readSoundRule("game.sounds.respawn", Sound.BLOCK_SLIME_BLOCK_FALL),
            killSound = readSoundRule("game.sounds.kill", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),
            bedDestroyedSound = readSoundRule("game.sounds.bed-destroyed", Sound.ENTITY_ENDER_DRAGON_GROWL),
            ownBedDestroyedSound = readSoundRule("game.sounds.own-bed-destroyed", Sound.ENTITY_WITHER_DEATH),
            allBedsDestroyedSound = readSoundRule("game.sounds.all-beds-destroyed", Sound.ENTITY_ENDER_DRAGON_GROWL),
            suddenDeathSound = readSoundRule("game.sounds.sudden-death", Sound.ENTITY_ENDER_DRAGON_FLAP),
            gameEndSound = readSoundRule("game.sounds.end", Sound.ITEM_TRIDENT_THUNDER),
            halloweenActive = halloweenActive,
            allowedCommands = config.getStringList("game.allowed-commands")
                .map { it.trim().removePrefix("/").substringBefore(' ').lowercase() }
                .filter(String::isNotBlank)
                .toSet(),
            defaultItemGroups = readDefaultItemGroups(),
            preGameItems = readPreGameItems(),
            spectatorItems = readSpectatorItems(),
            spectatorEnabled = config.getBoolean("spectator.enabled", true),
            spectatorMode = readSpectatorMode(),
            chatFormattingEnabled = config.getBoolean("chat.format", true),
            shoutCooldownSeconds = config.getInt("chat.shout-cooldown-seconds", 30).coerceIn(0, 3600),
            allowHungerWaiting = config.getBoolean("allow-hunger-depletion.waiting", false),
            allowHungerInGame = config.getBoolean("allow-hunger-depletion.ingame", false),
            lobbyVoidTeleportEnabled = config.getBoolean("lobby-settings.void-tp", true),
            lobbyVoidHeight = config.getFiniteDouble("lobby-settings.void-height", 0.0),
            durationSeconds = gameEndAt,
            respawnSeconds = config.getInt("game.respawn-seconds", 5).coerceIn(1, 60),
            respawnInvulnerabilitySeconds = config.getInt("game.respawn-invulnerability-seconds", 4).coerceIn(0, 30),
            reconnectGraceSeconds = config.getInt("game.reconnect-grace-seconds", 300).coerceIn(0, 3600),
            afkSeconds = config.getInt("game.afk-seconds", 45).coerceIn(0, 3600),
            resultDisplaySeconds = if (config.contains("countdowns.game-restart")) {
                config.getInt("countdowns.game-restart", 45).coerceIn(3, 600)
            } else {
                config.getInt("game.result-display-seconds", 10).coerceIn(3, 600)
            },
            closeDelaySeconds = config.getInt("game.close-delay-seconds", 5).coerceIn(1, 120),
            chatTopStatistic = BedWarsResultStatistic.parse(config.getString("game-end.chat-top.order-by")),
            chatTopHideMissing = config.getBoolean("game-end.chat-top.hide-missing", true),
            sidebarTopStatistic = BedWarsResultStatistic.parse(config.getString("game-end.sb-top.order-by")),
            sidebarTopHideMissing = config.getBoolean("game-end.sb-top.hide-missing", true),
            lobbySidebarEnabled = config.getBoolean("scoreboard-settings.sidebar.enable-lobby-sidebar", true),
            sidebarEnabled = config.getBoolean("scoreboard-settings.sidebar.enable-game-sidebar", true),
            sidebarTitleRefreshTicks = config.getInt(
                "scoreboard-settings.sidebar.title-refresh-interval",
                4
            ).coerceIn(0, 72_000),
            sidebarPlaceholdersRefreshTicks = config.getInt(
                "scoreboard-settings.sidebar.placeholders-refresh-interval",
                20
            ).coerceIn(0, 72_000),
            sidebarServerIp = config.getString(
                "scoreboard-settings.placeholders.server-ip",
                config.getString("server-ip", "") ?: ""
            ) ?: "",
            sidebarPoweredBy = config.getString(
                "scoreboard-settings.placeholders.powered-by",
                config.getString("powered-by", "KaGameCenter") ?: "KaGameCenter"
            ) ?: "KaGameCenter",
            tabHeaderFooterEnabled = config.getBoolean("scoreboard-settings.tab-header-footer.enable", true),
            tabHeaderFooterRefreshTicks = config.getInt(
                "scoreboard-settings.tab-header-footer.refresh-interval",
                10
            ).coerceIn(0, 1200),
            tabPlayerListWaitingEnabled = config.getBoolean(
                "scoreboard-settings.player-list.format-waiting-list",
                false
            ),
            tabPlayerListCountdownEnabled = config.getBoolean(
                "scoreboard-settings.player-list.format-starting-list",
                false
            ),
            tabPlayerListRunningEnabled = config.getBoolean(
                "scoreboard-settings.player-list.format-playing-list",
                true
            ),
            tabPlayerListResultEnabled = config.getBoolean(
                "scoreboard-settings.player-list.format-restarting-list",
                true
            ),
            tabPlayerListRefreshTicks = config.getInt(
                "scoreboard-settings.player-list.names-refresh-interval",
                1200
            ).coerceIn(0, 72_000),
            healthDisplayEnabled = config.getBoolean("scoreboard-settings.health.enable", true),
            healthDisplayInTab = config.getBoolean("scoreboard-settings.health.display-in-tab", true),
            healthAnimationRefreshTicks = config.getInt(
                "scoreboard-settings.health.animation-refresh-interval",
                300
            ).coerceIn(0, 72_000),
            defaultVoidY = config.getFiniteDouble("game.void-y", -64.0),
            worldBorderSize = config.getInt("game.world-border-size", 300).coerceIn(0, 60_000_000),
            winPoints = config.getInt("game.win-points", 3).coerceIn(0, 1_000_000),
            levelRules = BedWarsLevelRules(
                enabled = config.getBoolean("levels.enabled", true),
                rankupCosts = config.getIntegerList("levels.rankup-costs")
                    .ifEmpty { listOf(1000, 2000, 3000, 3500) }
                    .map { it.coerceIn(1, 1_000_000) },
                defaultRankupCost = config.getInt("levels.default-rankup-cost", 5000).coerceIn(1, 1_000_000),
                progressBarSymbol = config.getString("levels.progress-bar.symbol", "■")?.takeIf(String::isNotEmpty) ?: "■",
                progressBarUnlockedColor = config.getString("levels.progress-bar.unlocked-color", "&b") ?: "&b",
                progressBarLockedColor = config.getString("levels.progress-bar.locked-color", "&7") ?: "&7",
                progressBarFormat = config.getString("levels.progress-bar.format", "&8 [{progress}&8]")
                    ?: "&8 [{progress}&8]",
                perMinuteExperience = config.getInt("levels.rewards.per-minute", 10).coerceIn(0, 1_000_000),
                perTeammateExperience = config.getInt("levels.rewards.per-teammate", 5).coerceIn(0, 1_000_000),
                gameWinExperience = config.getInt("levels.rewards.game-win", 100).coerceIn(0, 1_000_000),
                bedDestroyedExperience = config.getInt("levels.rewards.bed-destroyed", 15).coerceIn(0, 1_000_000),
                regularKillExperience = config.getInt("levels.rewards.regular-kill", 10).coerceIn(0, 1_000_000),
                finalKillExperience = config.getInt("levels.rewards.final-kill", 15).coerceIn(0, 1_000_000)
            ),
            moneyRewardRules = BedWarsMoneyRewardRules(
                perMinute = config.getInt("money-rewards.per-minute", 5).coerceIn(0, 1_000_000),
                perTeammate = config.getInt("money-rewards.per-teammate", 30).coerceIn(0, 1_000_000),
                gameWin = config.getInt("money-rewards.game-win", 90).coerceIn(0, 1_000_000),
                bedDestroyed = config.getInt("money-rewards.bed-destroyed", 60).coerceIn(0, 1_000_000),
                regularKill = config.getInt("money-rewards.regular-kill", 10).coerceIn(0, 1_000_000),
                finalKill = config.getInt("money-rewards.final-kill", 40).coerceIn(0, 1_000_000)
            ),
            bedsDestroySeconds = bedsDestroyAt,
            suddenDeathSeconds = suddenDeathAt,
            maxBuildY = config.getInt("game.max-build-y", 180).coerceIn(-64, 1024),
            islandRadius = config.getFiniteDouble("game.island-radius", 17.0).coerceIn(1.0, 128.0),
            disableEmptyTeamGenerators = config.getBoolean("game.disable-empty-team-generators", false),
            disableEmptyTeamNpcs = config.getBoolean("game.disable-empty-team-npcs", true),
            useBedHologram = config.getBoolean("game.use-bed-hologram", true),
            vanillaDeathDrops = config.getBoolean("game.vanilla-death-drops", false),
            markLeaveAsAbandon = config.getBoolean("game.mark-leave-as-abandon", false),
            blockRules = BedWarsBlockRules(
                placeAllowed = config.getStringList("blocks.place-allowed")
                    .mapNotNull(Material::matchMaterial)
                    .toSet(),
                breakableMapBlocks = config.getStringList("blocks.breakable-map-blocks")
                    .mapNotNull(Material::matchMaterial)
                    .toSet(),
                allowFireExtinguish = config.getBoolean("blocks.allow-fire-extinguish", true),
                autoPrimeTnt = config.getBoolean("blocks.auto-prime-tnt", true),
                tntFuseTicks = config.getInt("blocks.tnt-fuse-ticks", 45).coerceIn(1, 1200),
                blastProofGlassBlocksRays = config.getBoolean("blocks.blast-proof-glass-blocks-rays", true),
                spawnProtectionRadius = config.getFiniteDouble("blocks.spawn-protection-radius", 2.0).coerceIn(0.0, 32.0),
                shopProtectionRadius = config.getFiniteDouble("blocks.shop-protection-radius", 1.0).coerceIn(0.0, 32.0),
                generatorProtectionRadius = config.getFiniteDouble("blocks.generator-protection-radius", 1.0).coerceIn(0.0, 32.0),
                teamChestRadius = config.getFiniteDouble("blocks.team-chest-radius", 17.0).coerceIn(1.0, 128.0)
            ),
            inventoryRules = BedWarsInventoryRules(
                disableCraftingTable = config.getBoolean("inventories.disable-crafting-table", true),
                disableEnchantingTable = config.getBoolean("inventories.disable-enchanting-table", true),
                disableFurnace = config.getBoolean("inventories.disable-furnace", true),
                disableBrewingStand = config.getBoolean("inventories.disable-brewing-stand", true),
                disableAnvil = config.getBoolean("inventories.disable-anvil", true)
            ),
            generatorRules = BedWarsGeneratorRules(
                hologramsEnabled = config.getBoolean("generators.holograms.enabled", true),
                rotateHologramItems = config.getBoolean("performance-settings.rotate-generators", true),
                stackItems = config.getBoolean("generators.stack-items", false),
                teamSplitEnabled = config.getBoolean("generators.team-split.enabled", true),
                teamSplitRadius = config.getFiniteDouble("generators.team-split.radius", 1.0).coerceIn(0.5, 4.0),
                diamondUpgradeSound = readSoundRule(
                    "generators.sounds.diamond-upgrade",
                    Sound.ENTITY_PLAYER_LEVELUP
                ),
                emeraldUpgradeSound = readSoundRule(
                    "generators.sounds.emerald-upgrade",
                    Sound.ENTITY_GHAST_WARN
                ),
                ironAmount = config.getInt("generators.iron.amount", 2).coerceIn(1, 64),
                ironSpawnLimit = config.getInt("generators.iron.spawn-limit", 32).coerceIn(1, 4096),
                goldAmount = config.getInt("generators.gold.amount", 2).coerceIn(1, 64),
                goldSpawnLimit = config.getInt("generators.gold.spawn-limit", 7).coerceIn(1, 4096),
                diamondTiers = diamondTiers,
                emeraldTiers = emeraldTiers
            ),
            forgeRules = BedWarsForgeRules(
                speedMultipliers = readForgeMultipliers(),
                emeraldIntervalTicks = config.getInt("generators.forge.emerald-interval-ticks", 200).coerceIn(20, 72_000),
                tier3EmeraldAmount = config.getInt("generators.forge.tier-3-emerald-amount", 1).coerceIn(1, 64),
                tier4EmeraldAmount = config.getInt("generators.forge.tier-4-emerald-amount", 2).coerceIn(1, 64),
                emeraldSpawnLimit = config.getInt("generators.forge.emerald-spawn-limit", 4).coerceIn(1, 4096)
            ),
            dragonRules = BedWarsDragonRules(
                baseDragons = config.getInt("dragons.base-count", 1).coerceIn(1, 4),
                buffExtraDragons = config.getInt("dragons.buff-extra-count", 1).coerceIn(1, 4),
                spawnHeight = config.getFiniteDouble("dragons.spawn-height", 16.0).coerceIn(4.0, 64.0),
                health = config.getFiniteDouble("dragons.health", 100.0).coerceIn(20.0, 2048.0),
                damage = config.getFiniteDouble("dragons.damage", 6.0).coerceIn(0.0, 100.0),
                speed = config.getFiniteDouble("dragons.speed", 0.8).coerceIn(0.1, 3.0),
                attackRadius = config.getFiniteDouble("dragons.attack-radius", 4.5).coerceIn(1.0, 12.0)
            ),
            shop = BedWarsShopConfig(
                hologramsEnabled = config.getBoolean("shop.holograms.enabled", true),
                trapCategoryIcon = Material.matchMaterial(config.getString("shop.trap-category.icon").orEmpty())
                    ?: Material.LEATHER,
                trapCategoryIconAmount = config.getInt("shop.trap-category.amount", 1).coerceIn(1, 99),
                trapCategoryIconEnchanted = config.getBoolean("shop.trap-category.enchanted", false),
                upgradeSeparatorIcon = Material.matchMaterial(config.getString("shop.upgrade-separator.icon").orEmpty())
                    ?: Material.GRAY_STAINED_GLASS_PANE,
                upgradeSeparatorIconAmount = config.getInt("shop.upgrade-separator.amount", 1).coerceIn(1, 99),
                upgradeSeparatorIconEnchanted = config.getBoolean("shop.upgrade-separator.enchanted", false),
                upgradeSeparatorPlayerCommands = readUpgradeSeparatorCommands("player", "as-player"),
                upgradeSeparatorConsoleCommands = readUpgradeSeparatorCommands("console", "as-console"),
                boughtSound = readSoundRule("shop.sounds.bought", Sound.ENTITY_VILLAGER_YES),
                insufficientSound = readSoundRule("shop.sounds.insufficient", Sound.ENTITY_VILLAGER_NO),
                autoEquipSound = readSoundRule("shop.sounds.auto-equip", Sound.ITEM_ARMOR_EQUIP_GENERIC),
                trapTriggerSound = readSoundRule("shop.sounds.trap-trigger", Sound.ENTITY_ENDERMAN_TELEPORT),
                healPoolParticlesEnabled = config.getBoolean("shop.heal-pool.particles.enabled", true),
                healPoolParticlesTeamOnly = config.getBoolean("shop.heal-pool.particles.seen-by-team-only", true),
                blindnessTrapDurationTicks = config.getInt("shop.traps.blindness.duration-seconds", 5).coerceIn(1, 600) * 20,
                blindnessTrapAmplifier = config.getInt("shop.traps.blindness.amplifier", 1).coerceIn(0, 10),
                counterOffensiveTrapDurationTicks = config.getInt(
                    "shop.traps.counter-offensive.duration-seconds",
                    15
                ).coerceIn(1, 600) * 20,
                counterOffensiveTrapAmplifier = config.getInt(
                    "shop.traps.counter-offensive.amplifier",
                    1
                ).coerceIn(0, 10),
                alarmTrapGlowingTicks = config.getInt("shop.traps.alarm.glowing-seconds", 0).coerceIn(0, 600) * 20,
                minerFatigueTrapDurationTicks = config.getInt(
                    "shop.traps.miner-fatigue.duration-seconds",
                    15
                ).coerceIn(1, 600) * 20,
                minerFatigueTrapAmplifier = config.getInt("shop.traps.miner-fatigue.amplifier", 1).coerceIn(0, 10),
                pickaxeEfficiencyLevels = readToolLevels(
                    "shop.tools.pickaxe-efficiency-levels",
                    listOf(0, 2, 3, 3)
                ),
                pickaxeSharpnessLevels = readToolLevels(
                    "shop.tools.pickaxe-sharpness-levels",
                    listOf(0, 0, 2, 0)
                ),
                axeEfficiencyLevels = readToolLevels(
                    "shop.tools.axe-efficiency-levels",
                    listOf(1, 1, 2, 3)
                ),
                items = readShopItems(halloweenActive),
                upgrades = readUpgradeItems(),
                quickBuyDefaults = config.getStringList("shop.quick-buy-defaults")
                    .map(String::normalizedBedWarsId)
                    .take(BedWarsQuickBuyService.SLOT_COUNT),
                defaultTrapRules = readDefaultTrapRules(),
                trapGroupRules = readTrapGroupRules(),
                defaultUpgradeMenuRules = readDefaultUpgradeMenuRules(),
                upgradeMenuGroupRules = readUpgradeMenuGroupRules(),
                trapCategoryRules = readTrapCategoryRules()
            ),
            specials = BedWarsSpecialRules(
                tntBarycenterAlterationY = config.getFiniteDouble("specials.tnt.barycenter-alteration-y", 0.5).coerceIn(-4.0, 4.0),
                tntStrengthReduction = config.getFiniteDouble("specials.tnt.strength-reduction", 5.0).coerceIn(0.1, 100.0),
                tntYAxisReduction = config.getFiniteDouble("specials.tnt.y-axis-reduction", 2.0).coerceIn(0.1, 100.0),
                tntDamageSelf = config.getFiniteDouble("specials.tnt.damage-self", 1.0).coerceIn(-1.0, 100.0),
                tntDamageTeammates = config.getFiniteDouble("specials.tnt.damage-teammates", 5.0).coerceIn(-1.0, 100.0),
                tntDamageOthers = config.getFiniteDouble("specials.tnt.damage-others", 10.0).coerceIn(-1.0, 100.0),
                tntSpoilCarriers = config.getBoolean("specials.tnt.spoil-carriers", true),
                fireballSpeed = config.getFiniteDouble("specials.fireball.speed", 10.0).coerceIn(0.1, 20.0),
                fireballYield = config.getFiniteDouble("specials.fireball.yield", 3.0).toFloat().coerceIn(0.0f, 10.0f),
                fireballMakeFire = config.getBoolean("specials.fireball.make-fire", false),
                fireballCooldownTicks = config.getInt("specials.fireball.cooldown-ticks", 10).coerceIn(0, 1200),
                fireballHorizontalKnockback = config.getFiniteDouble("specials.fireball.horizontal-knockback", 1.0).coerceIn(0.0, 5.0),
                fireballVerticalKnockback = config.getFiniteDouble("specials.fireball.vertical-knockback", 0.65).coerceIn(0.0, 5.0),
                fireballDamageSelf = config.getFiniteDouble("specials.fireball.damage-self", 2.0).coerceIn(0.0, 100.0),
                fireballDamageTeammates = config.getFiniteDouble("specials.fireball.damage-teammates", 0.0).coerceIn(0.0, 100.0),
                fireballDamageEnemies = config.getFiniteDouble("specials.fireball.damage-enemies", 2.0).coerceIn(0.0, 100.0),
                bridgeBlockSound = readSoundRule("specials.sounds.bridge-block", Sound.ENTITY_CHICKEN_EGG),
                bridgeStartDistance = config.getFiniteDouble("specials.bridge-egg.start-distance", 4.0).coerceIn(0.0, 32.0),
                bridgeMaxDistance = config.getFiniteDouble("specials.bridge-egg.max-distance", 27.0).coerceIn(4.0, 128.0),
                bridgeMaxVerticalDrop = config.getFiniteDouble("specials.bridge-egg.max-vertical-drop", 9.0).coerceIn(0.0, 64.0),
                enderPearlLandedSound = readSoundRule("specials.sounds.ender-pearl-landed", Sound.ENTITY_ENDERMAN_TELEPORT),
                popupTowerBuildSound = readSoundRule("specials.sounds.popup-tower-build", Sound.ENTITY_CHICKEN_EGG),
                bedBugDurationTicks = config.getInt("specials.bed-bug.duration-seconds", 15).coerceIn(1, 600) * 20,
                bedBugHealth = config.getFiniteDouble("specials.bed-bug.health", 8.0).coerceIn(1.0, 2048.0),
                bedBugDamage = config.getFiniteDouble("specials.bed-bug.damage", 4.0).coerceIn(-1.0, 100.0),
                bedBugSpeed = config.getFiniteDouble("specials.bed-bug.speed", 0.25).coerceIn(0.0, 2.0),
                dreamDefenderDurationTicks = config.getInt("specials.dream-defender.duration-seconds", 240).coerceIn(1, 1800) * 20,
                dreamDefenderHealth = config.getFiniteDouble("specials.dream-defender.health", 100.0).coerceIn(1.0, 2048.0),
                dreamDefenderDamage = config.getFiniteDouble("specials.dream-defender.damage", -1.0).coerceIn(-1.0, 100.0),
                dreamDefenderSpeed = config.getFiniteDouble("specials.dream-defender.speed", 0.25).coerceIn(0.0, 2.0),
                speedPotionSeconds = config.getInt("specials.potions.speed-seconds", 45).coerceIn(1, 600),
                jumpPotionSeconds = config.getInt("specials.potions.jump-seconds", 45).coerceIn(1, 600),
                invisibilityPotionSeconds = config.getInt("specials.potions.invisibility-seconds", 30).coerceIn(1, 600),
                removeInvisibilityOnDamage = config.getBoolean("specials.potions.remove-invisibility-on-damage", true),
                magicMilkSeconds = config.getInt("specials.magic-milk.duration-seconds", 30).coerceIn(1, 600),
                towerRadius = config.getInt("specials.popup-tower.radius", 2).coerceIn(2, 4),
                towerWallHeight = config.getInt("specials.popup-tower.wall-height", 5).coerceIn(3, 8),
                towerBlocksPerTick = config.getInt("specials.popup-tower.blocks-per-tick", 2).coerceIn(1, 32)
            ),
            maps = maps
        )
    }

    /** 返回当前聊天格式开关，供消息热路径避免重建完整类型化配置。 */
    fun isChatFormattingEnabled(): Boolean = config.getBoolean("chat.format", true)

    /** 读取某个托管游戏的队伍、床和资源点配置。 */
    fun readManagedGame(game: ManagedGameConfig): BedWarsGameConfig {
        val yaml = YamlConfiguration.loadConfiguration(game.file)
        val section = yaml.getConfigurationSection("bedwars") ?: yaml.createSection("bedwars")
        return BedWarsGameConfig(
            lobby = BedWarsPoint.read(section.getConfigurationSection("lobby")),
            spectatorSpawn = BedWarsPoint.read(section.getConfigurationSection("spectator-spawn")),
            voidY = section.getDoubleOrNull("void-y"),
            maxBuildY = section.getIntOrNull("max-build-y"),
            worldBorderSize = section.getIntOrNull("world-border")?.coerceIn(0, 60_000_000),
            allowSpectate = section.getBoolean("allow-spectate", true),
            allowMapBreak = section.getBoolean("allow-map-break", false),
            islandRadius = section.getFiniteDouble(
                "island-radius",
                config.getFiniteDouble("game.island-radius", 17.0)
            ).coerceIn(1.0, 128.0),
            disableEmptyTeamGenerators = section.getBoolean(
                "disable-generator-for-empty-teams",
                config.getBoolean("game.disable-empty-team-generators", false)
            ),
            disableEmptyTeamNpcs = section.getBoolean(
                "disable-npcs-for-empty-teams",
                config.getBoolean("game.disable-empty-team-npcs", true)
            ),
            vanillaDeathDrops = section.getBoolean(
                "vanilla-death-drops",
                config.getBoolean("game.vanilla-death-drops", false)
            ),
            useBedHologram = section.getBoolean(
                "use-bed-hologram",
                config.getBoolean("game.use-bed-hologram", true)
            ),
            showEliminatedAtGameEnd = section.getBoolean("game-end.show-eliminated", true),
            teleportEliminatedAtGameEnd = section.getBoolean("game-end.teleport-eliminated", true),
            chatTopStatistic = section.getString("game-end.chat-top.order-by")
                ?.let(BedWarsResultStatistic::parse),
            chatTopHideMissing = if (section.contains("game-end.chat-top.hide-missing")) {
                section.getBoolean("game-end.chat-top.hide-missing")
            } else null,
            sidebarTopStatistic = section.getString("game-end.sb-top.order-by")
                ?.let(BedWarsResultStatistic::parse),
            sidebarTopHideMissing = if (section.contains("game-end.sb-top.hide-missing")) {
                section.getBoolean("game-end.sb-top.hide-missing")
            } else null,
            itemGroup = section.getString("item-group", "default")?.trim()?.ifBlank { "default" } ?: "default",
            gameRules = if (section.contains("game-rules")) {
                section.getStringList("game-rules").map(String::trim).filter(String::isNotEmpty)
            } else {
                BEDWARS_DEFAULT_GAME_RULES
            },
            spawnProtectionRadius = section.getProtectionRadiusOrNull("spawn-protection"),
            shopProtectionRadius = section.getProtectionRadiusOrNull("shop-protection"),
            upgradeShopProtectionRadius = section.getProtectionRadiusOrNull("upgrades-protection"),
            generatorProtectionRadius = section.getProtectionRadiusOrNull("generator-protection"),
            teams = readTeams(section),
            generators = readGenerators(section.getConfigurationSection("generators"))
        )
    }

    /** 保存托管游戏的等待大厅。 */
    fun saveManagedLobby(game: ManagedGameConfig, point: BedWarsPoint) {
        savePoint(game, "bedwars.lobby", point)
    }

    /** 保存托管游戏的观战出生点。 */
    fun saveManagedSpectatorSpawn(game: ManagedGameConfig, point: BedWarsPoint) {
        savePoint(game, "bedwars.spectator-spawn", point)
    }

    /** 保存托管游戏的虚空淘汰高度。 */
    fun saveManagedVoidY(game: ManagedGameConfig, y: Double) {
        saveManaged(game) { it.set("bedwars.void-y", y) }
    }

    /** 保存托管游戏允许放置方块的最大高度。 */
    fun saveManagedMaxBuildY(game: ManagedGameConfig, y: Int) {
        saveManaged(game) { it.set("bedwars.max-build-y", y) }
    }

    /** 保存竞技场使用的模块默认物品组名称。 */
    fun saveManagedItemGroup(game: ManagedGameConfig, group: String) {
        saveManaged(game) { it.set("bedwars.item-group", group.trim().ifBlank { "default" }) }
    }

    /** 保存托管地图的岛屿半径、空队资源、死亡掉落和床全息规则。 */
    fun saveManagedArenaRules(
        game: ManagedGameConfig,
        islandRadius: Double,
        disableEmptyTeamGenerators: Boolean,
        disableEmptyTeamNpcs: Boolean,
        vanillaDeathDrops: Boolean,
        useBedHologram: Boolean
    ) {
        saveManaged(game) { managed ->
            managed.set("bedwars.island-radius", islandRadius.coerceIn(1.0, 128.0))
            managed.set("bedwars.disable-generator-for-empty-teams", disableEmptyTeamGenerators)
            managed.set("bedwars.disable-npcs-for-empty-teams", disableEmptyTeamNpcs)
            managed.set("bedwars.vanilla-death-drops", vanillaDeathDrops)
            managed.set("bedwars.use-bed-hologram", useBedHologram)
        }
    }

    /** 保存托管地图的世界边界、访问开关和四类关键点保护半径。 */
    fun saveManagedMapProtectionRules(
        game: ManagedGameConfig,
        worldBorderSize: Int,
        allowSpectate: Boolean,
        allowMapBreak: Boolean,
        spawnProtectionRadius: Double,
        shopProtectionRadius: Double,
        upgradeShopProtectionRadius: Double,
        generatorProtectionRadius: Double
    ) {
        saveManaged(game) { managed ->
            managed.set("bedwars.world-border", worldBorderSize.coerceIn(0, 60_000_000))
            managed.set("bedwars.allow-spectate", allowSpectate)
            managed.set("bedwars.allow-map-break", allowMapBreak)
            managed.set("bedwars.spawn-protection", spawnProtectionRadius.coerceIn(0.0, 32.0))
            managed.set("bedwars.shop-protection", shopProtectionRadius.coerceIn(0.0, 32.0))
            managed.set("bedwars.upgrades-protection", upgradeShopProtectionRadius.coerceIn(0.0, 32.0))
            managed.set("bedwars.generator-protection", generatorProtectionRadius.coerceIn(0.0, 32.0))
        }
    }

    /** 保存托管地图的结算显示开关和原版游戏规则列表。 */
    fun saveManagedGameEndRules(
        game: ManagedGameConfig,
        showEliminated: Boolean,
        teleportEliminated: Boolean,
        chatTopStatistic: BedWarsResultStatistic,
        chatTopHideMissing: Boolean,
        sidebarTopStatistic: BedWarsResultStatistic,
        sidebarTopHideMissing: Boolean,
        gameRules: List<String>
    ) {
        saveManaged(game) { managed ->
            managed.set("bedwars.game-end.show-eliminated", showEliminated)
            managed.set("bedwars.game-end.teleport-eliminated", teleportEliminated)
            managed.set("bedwars.game-end.chat-top.order-by", chatTopStatistic.name)
            managed.set("bedwars.game-end.chat-top.hide-missing", chatTopHideMissing)
            managed.set("bedwars.game-end.sb-top.order-by", sidebarTopStatistic.name)
            managed.set("bedwars.game-end.sb-top.hide-missing", sidebarTopHideMissing)
            managed.set("bedwars.game-rules", gameRules)
        }
    }

    /** 新建或更新一个队伍的基本属性，同时保留已有点位。 */
    fun upsertManagedTeam(
        game: ManagedGameConfig,
        teamId: String,
        displayName: String,
        color: BedWarsTeamColor,
        maxPlayers: Int
    ): String? {
        val id = teamId.normalizedBedWarsId().takeIf(String::isNotBlank) ?: return null
        saveManaged(game) { managed ->
            val path = "bedwars.teams.$id"
            managed.set("$path.display-name", displayName.ifBlank { id })
            managed.set("$path.color", color.name)
            managed.set("$path.max-players", maxPlayers.coerceIn(1, 100))
        }
        return id
    }

    /** 删除指定队伍及其所有岛屿点位。 */
    fun removeManagedTeam(game: ManagedGameConfig, teamId: String): Boolean {
        val id = teamId.normalizedBedWarsId()
        return removePath(game, "bedwars.teams.$id")
    }

    /** 保存指定队伍的出生点、床、最终掉落回收点或商店点。 */
    fun saveManagedTeamPoint(game: ManagedGameConfig, teamId: String, field: String, point: BedWarsPoint): Boolean {
        val id = teamId.normalizedBedWarsId()
        if (!teamExists(game, id) || field !in TEAM_POINT_FIELDS) return false
        savePoint(game, "bedwars.teams.$id.$field", point)
        return true
    }

    /** 新建或更新队伍岛屿内的资源生成器。 */
    fun upsertManagedTeamGenerator(
        game: ManagedGameConfig,
        teamId: String,
        generatorId: String,
        type: BedWarsGeneratorType,
        point: BedWarsPoint,
        intervalTicks: Int
    ): String? {
        val normalizedTeamId = teamId.normalizedBedWarsId()
        if (!teamExists(game, normalizedTeamId)) return null
        return upsertGenerator(
            game,
            "bedwars.teams.$normalizedTeamId.generators",
            generatorId,
            type,
            point,
            intervalTicks
        )
    }

    /** 删除队伍岛屿内的指定资源生成器。 */
    fun removeManagedTeamGenerator(game: ManagedGameConfig, teamId: String, generatorId: String): Boolean {
        val normalizedTeamId = teamId.normalizedBedWarsId()
        val normalizedGeneratorId = generatorId.normalizedBedWarsId()
        return removePath(game, "bedwars.teams.$normalizedTeamId.generators.$normalizedGeneratorId")
    }

    /** 新建或更新钻石岛、绿宝石岛等公共资源生成器。 */
    fun upsertManagedGenerator(
        game: ManagedGameConfig,
        generatorId: String,
        type: BedWarsGeneratorType,
        point: BedWarsPoint,
        intervalTicks: Int
    ): String? = upsertGenerator(game, "bedwars.generators", generatorId, type, point, intervalTicks)

    /** 删除指定公共资源生成器。 */
    fun removeManagedGenerator(game: ManagedGameConfig, generatorId: String): Boolean {
        return removePath(game, "bedwars.generators.${generatorId.normalizedBedWarsId()}")
    }

    private fun readTeams(section: ConfigurationSection): List<BedWarsTeamConfig> {
        val teams = section.getConfigurationSection("teams") ?: return emptyList()
        return teams.getKeys(false).mapNotNull { rawId ->
            val team = teams.getConfigurationSection(rawId) ?: return@mapNotNull null
            val id = rawId.normalizedBedWarsId()
            BedWarsTeamConfig(
                id = id,
                displayName = team.getString("display-name", rawId) ?: rawId,
                color = BedWarsTeamColor.parse(team.getString("color")),
                maxPlayers = team.getInt("max-players", 1).coerceIn(1, 100),
                spawn = BedWarsPoint.read(team.getConfigurationSection("spawn")),
                bed = BedWarsPoint.read(team.getConfigurationSection("bed")),
                killDrops = BedWarsPoint.read(team.getConfigurationSection("kill-drops")),
                shop = BedWarsPoint.read(team.getConfigurationSection("shop")),
                upgradeShop = BedWarsPoint.read(team.getConfigurationSection("upgrade-shop")),
                generators = readGenerators(team.getConfigurationSection("generators"))
            )
        }
    }

    private fun readGenerators(section: ConfigurationSection?): List<BedWarsGeneratorConfig> {
        if (section == null) return emptyList()
        return section.getKeys(false).mapNotNull { rawId ->
            val generator = section.getConfigurationSection(rawId) ?: return@mapNotNull null
            val type = BedWarsGeneratorType.parse(generator.getString("type")) ?: return@mapNotNull null
            val point = BedWarsPoint.read(generator.getConfigurationSection("point")) ?: return@mapNotNull null
            BedWarsGeneratorConfig(
                id = rawId.normalizedBedWarsId(),
                type = type,
                point = point,
                intervalTicks = generator.getInt("interval-ticks", defaultInterval(type)).coerceIn(1, 72_000)
            )
        }
    }

    private fun defaultInterval(type: BedWarsGeneratorType): Int = when (type) {
        BedWarsGeneratorType.IRON -> 40
        BedWarsGeneratorType.GOLD -> 120
        BedWarsGeneratorType.DIAMOND -> 600
        BedWarsGeneratorType.EMERALD -> 1200
    }

    private fun readGeneratorTiers(type: BedWarsGeneratorType): List<BedWarsGeneratorTier> {
        val base = "generators.${type.name.lowercase()}.tiers"
        return (1..3).map { tier ->
            val path = "$base.$tier"
            BedWarsGeneratorTier(
                tier = tier,
                startSeconds = config.getInt("$path.start-seconds", defaultTierStart(type, tier)).coerceIn(0, 7200),
                intervalTicks = config.getInt("$path.interval-ticks", defaultTierInterval(type, tier)).coerceIn(1, 72_000),
                amount = config.getInt("$path.amount", 1).coerceIn(1, 64),
                spawnLimit = config.getInt("$path.spawn-limit", 2 + tier * 2).coerceIn(1, 4096)
            )
        }.sortedBy(BedWarsGeneratorTier::startSeconds)
    }

    /** 读取并补齐四阶锻炉生成间隔倍率。 */
    private fun readForgeMultipliers(): List<Double> {
        val configured = config.getDoubleList("generators.forge.speed-multipliers")
        val defaults = listOf(2.0 / 3.0, 0.5, 0.5, 1.0 / 3.0)
        return List(4) { index ->
            configured.getOrNull(index)?.takeIf(Double::isFinite)?.coerceIn(0.05, 1.0) ?: defaults[index]
        }
    }

    /** 读取并补齐四阶永久工具附魔等级。 */
    private fun readToolLevels(path: String, defaults: List<Int>): List<Int> {
        val configured = config.getIntegerList(path)
        return List(4) { index -> configured.getOrNull(index)?.coerceIn(0, 10) ?: defaults[index] }
    }

    /** 读取参考 material,data,amount,name 格式的默认物品组，现代版本忽略 legacy data。 */
    private fun readDefaultItemGroups(): Map<String, List<BedWarsDefaultItem>> {
        val section = config.getConfigurationSection("game.default-item-groups")
        val groups = linkedMapOf<String, List<BedWarsDefaultItem>>()
        section?.getKeys(false)?.forEach { group ->
            groups[group.lowercase()] = section.getStringList(group).mapNotNull { raw ->
                val parts = raw.split(',')
                val material = parts.firstOrNull()?.trim()?.let(Material::matchMaterial) ?: return@mapNotNull null
                val amount = parts.getOrNull(2)?.trim()?.toIntOrNull()?.coerceIn(1, material.maxStackSize) ?: 1
                val displayName = parts.getOrNull(3)?.trim()?.takeIf(String::isNotEmpty)
                BedWarsDefaultItem(material, amount, displayName)
            }
        }
        if (groups["default"].isNullOrEmpty()) {
            groups["default"] = listOf(BedWarsDefaultItem(Material.WOODEN_SWORD, 1, null))
        }
        return groups
    }

    /** 读取等待大厅命令物品，并按槽位覆盖重复配置。 */
    private fun readPreGameItems(): List<BedWarsCommandItem> {
        val section = config.getConfigurationSection("pre-game-items") ?: return emptyList()
        val bySlot = linkedMapOf<Int, BedWarsCommandItem>()
        section.getKeys(false).forEach { rawId ->
            val itemSection = section.getConfigurationSection(rawId) ?: return@forEach
            val id = rawId.normalizedBedWarsId().takeIf(String::isNotBlank) ?: return@forEach
            val material = itemSection.getString("material")?.let(Material::matchMaterial) ?: return@forEach
            val slot = itemSection.getInt("slot", -1).takeIf { it in 0..8 } ?: return@forEach
            val command = itemSection.getString("command")?.trim()?.removePrefix("/")
                ?.take(256)?.takeIf(String::isNotBlank) ?: return@forEach
            bySlot[slot] = BedWarsCommandItem(
                id,
                material,
                slot,
                itemSection.getBoolean("enchanted", false),
                command
            )
        }
        return bySlot.values.sortedBy(BedWarsCommandItem::slot)
    }

    /** 读取托管观战快捷栏，并按槽位覆盖重复配置和忽略无交互项目。 */
    private fun readSpectatorItems(): List<BedWarsSpectatorItem> {
        val section = config.getConfigurationSection("spectator-items") ?: return emptyList()
        val bySlot = linkedMapOf<Int, BedWarsSpectatorItem>()
        section.getKeys(false).forEach { rawId ->
            val itemSection = section.getConfigurationSection(rawId) ?: return@forEach
            val id = rawId.normalizedBedWarsId().takeIf(String::isNotBlank) ?: return@forEach
            val material = itemSection.getString("material")?.let(Material::matchMaterial) ?: return@forEach
            val slot = itemSection.getInt("slot", -1).takeIf { it in 0..8 } ?: return@forEach
            val action = itemSection.getString("action")
                ?.trim()
                ?.uppercase()
                ?.let { runCatching { SpectatorAction.valueOf(it) }.getOrNull() }
            val command = itemSection.getString("command")
                ?.trim()
                ?.removePrefix("/")
                ?.take(256)
                ?.takeIf(String::isNotBlank)
            if (action == null && command == null) return@forEach
            bySlot[slot] = BedWarsSpectatorItem(
                id,
                material,
                slot,
                itemSection.getBoolean("enchanted", false),
                itemSection.getString("display-name")?.trim()?.takeIf(String::isNotBlank),
                itemSection.getStringList("lore"),
                action,
                command
            )
        }
        return bySlot.values.sortedBy(BedWarsSpectatorItem::slot)
    }

    /** 读取外部观战模式，并把未知值安全回退为参考快捷栏所需的托管模式。 */
    private fun readSpectatorMode(): SpectatorMode {
        val configured = config.getString("spectator.mode")?.trim()?.uppercase() ?: return SpectatorMode.MANAGED
        return runCatching { SpectatorMode.valueOf(configured) }.getOrDefault(SpectatorMode.MANAGED)
    }

    /** 读取事件音效；将 sound 设为 NONE 可关闭。 */
    private fun readSoundRule(path: String, fallback: Sound): BedWarsSoundRule {
        val configured = config.getString("$path.sound", fallback.name())?.trim().orEmpty()
        val sound = if (configured.equals("NONE", ignoreCase = true)) {
            null
        } else {
            runCatching { Sound.valueOf(configured.uppercase()) }.getOrDefault(fallback)
        }
        return BedWarsSoundRule(
            sound,
            config.getFiniteDouble("$path.volume", 1.0).toFloat().coerceIn(0.0f, 10.0f),
            config.getFiniteDouble("$path.pitch", 1.0).toFloat().coerceIn(0.5f, 2.0f)
        )
    }

    /** 读取升级商品内联音效；无配置或非法值回退全局陷阱音效，NONE 表示显式静音。 */
    private fun readInlineSoundRule(value: Any?): BedWarsSoundRule? {
        val configured: String
        val volume: Float
        val pitch: Float
        when (value) {
            is ConfigurationSection -> {
                configured = value.getString("sound")?.trim().orEmpty()
                volume = value.getFiniteDouble("volume", 1.0).toFloat()
                pitch = value.getFiniteDouble("pitch", 1.0).toFloat()
            }
            is Map<*, *> -> {
                configured = value["sound"]?.toString()?.trim().orEmpty()
                volume = value["volume"]?.toString()?.toFloatOrNull()?.takeIf(Float::isFinite) ?: 1.0f
                pitch = value["pitch"]?.toString()?.toFloatOrNull()?.takeIf(Float::isFinite) ?: 1.0f
            }
            null -> return null
            else -> {
                configured = value.toString().trim()
                volume = 1.0f
                pitch = 1.0f
            }
        }
        if (configured.isBlank()) return null
        val sound = if (configured.equals("NONE", ignoreCase = true)) {
            null
        } else {
            runCatching { Sound.valueOf(configured.uppercase()) }.getOrNull() ?: return null
        }
        return BedWarsSoundRule(sound, volume.coerceIn(0.0f, 10.0f), pitch.coerceIn(0.5f, 2.0f))
    }

    private fun defaultTierStart(type: BedWarsGeneratorType, tier: Int): Int = when (type) {
        BedWarsGeneratorType.DIAMOND -> listOf(0, 360, 1080)[tier - 1]
        BedWarsGeneratorType.EMERALD -> listOf(0, 720, 1440)[tier - 1]
        else -> 0
    }

    private fun defaultTierInterval(type: BedWarsGeneratorType, tier: Int): Int = when (type) {
        BedWarsGeneratorType.DIAMOND -> listOf(600, 400, 300)[tier - 1]
        BedWarsGeneratorType.EMERALD -> listOf(1400, 1000, 600)[tier - 1]
        else -> defaultInterval(type)
    }

    private fun readShopItems(halloweenActive: Boolean): List<BedWarsShopItem> {
        val entries = config.getMapList("shop.items").toMutableList()
        if (halloweenActive && entries.none { it["id"]?.toString()?.normalizedBedWarsId() == HALLOWEEN_PRODUCT_ID }) {
            entries += halloweenShopItem()
        }
        return entries.mapNotNull { values ->
            val id = values["id"]?.toString()?.normalizedBedWarsId()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val seasonalProduct = halloweenActive && id == HALLOWEEN_PRODUCT_ID
            if (!values["enabled"].toBooleanValue(true) && !seasonalProduct) return@mapNotNull null
            val productType = BedWarsProductType.parse(values["type"]?.toString()) ?: return@mapNotNull null
            val commandsAsPlayer = readShopCommands(values["buy-cmds"], "as-player")
            val commandsAsConsole = readShopCommands(values["buy-cmds"], "as-console")
            val buyItems = readShopBuyItems(values["buy-items"])
            val configuredItem = Material.matchMaterial((values["material"] ?: values["item"])?.toString().orEmpty())
            val icon = Material.matchMaterial(values["icon"]?.toString().orEmpty())
                ?: configuredItem
                ?: return@mapNotNull null
            if (configuredItem == null && buyItems.isEmpty() && commandsAsPlayer.isEmpty() && commandsAsConsole.isEmpty()) {
                return@mapNotNull null
            }
            val item = configuredItem ?: icon
            val amount = values["amount"].toIntValue(1).coerceIn(1, 64)
            val currency = readShopCurrency(values["currency"]) ?: return@mapNotNull null
            BedWarsShopItem(
                id = id,
                displayName = values["display-name"]?.toString()?.takeIf(String::isNotBlank) ?: id,
                displayLore = readShopDisplayLore(values["display-lore"] ?: values["item-lore"] ?: values["lore"]),
                icon = icon,
                iconAmount = (values["icon-amount"] ?: values["tier-item-amount"] ?: amount)
                    .toIntValue(amount)
                    .coerceIn(1, 99),
                iconEnchanted = (values["icon-enchanted"] ?: values["enchanted"]).toBooleanValue(false),
                iconPotionDisplay = values["potion-display"]?.toString()?.trim()?.takeIf(String::isNotBlank),
                iconPotionColor = readShopPotionColor(values["icon-potion-color"] ?: values["potion-color"]),
                productType = productType,
                category = (values["category"] ?: values["category-id"])
                    ?.toString()
                    ?.normalizedBedWarsId()
                    ?.takeIf(String::isNotBlank),
                weight = (values["weight"] ?: values["category-weight"])
                    .toIntValue(0)
                    .coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()),
                item = item,
                amount = amount,
                itemName = (values["name"] ?: values["item-name"])?.toString()?.takeIf(String::isNotBlank),
                unbreakable = values["unbreakable"].toBooleanValue(false),
                enchantments = readShopEnchantments(values["enchants"] ?: values["enchantments"]),
                potionEffects = readShopPotionEffects(values["potion"]),
                potionColor = readShopPotionColor(values["potion-color"]),
                autoEquip = values["auto-equip"].toBooleanValue(false),
                permanent = (values["permanent"] ?: values["is-permanent"]).toBooleanValue(false),
                downgradable = (values["downgradable"] ?: values["is-downgradable"]).toBooleanValue(
                    productType == BedWarsProductType.PICKAXE || productType == BedWarsProductType.AXE
                ),
                deliverProduct = configuredItem != null,
                buyItems = buyItems,
                commandsAsPlayer = commandsAsPlayer,
                commandsAsConsole = commandsAsConsole,
                currency = currency,
                price = values["price"].toIntValue(1).coerceIn(1, 4096),
                tier = values["tier"].toIntValue(0).coerceIn(0, 16)
            )
        }
    }

    /** 生成旧管理员商品列表缺失时使用的参考万圣节南瓜商品。 */
    private fun halloweenShopItem(): Map<String, Any> = linkedMapOf(
        "id" to HALLOWEEN_PRODUCT_ID,
        "display-name" to "万圣节南瓜 x12",
        "item-name" to "Happy Halloween!",
        "icon" to "PUMPKIN",
        "type" to "ITEM",
        "item" to "PUMPKIN",
        "amount" to 12,
        "currency" to "IRON_INGOT",
        "price" to 4,
        "tier" to 0
    )

    /** 按参考 Europe/Rome 时区判断 10 月 22 日至 11 月 1 日的万圣节活动窗口。 */
    private fun isHalloweenActive(): Boolean {
        if (!config.getBoolean("seasonal.halloween.enabled", true)) return false
        val date = LocalDate.now(ZoneId.of("Europe/Rome"))
        return date.monthValue == 10 && date.dayOfMonth > 21 || date.monthValue == 11 && date.dayOfMonth < 2
    }

    /** 读取参考 ContentTier.buy-items 的命名映射或列表，并保留管理员声明顺序。 */
    private fun readShopBuyItems(value: Any?): List<BedWarsShopDelivery> {
        val entries = when (value) {
            is ConfigurationSection -> value.getKeys(false).mapNotNull { key -> shopValueMap(value.get(key)) }
            is Map<*, *> -> if (value.keys.any { it.toString().equals("material", ignoreCase = true) }) {
                listOf(value)
            } else {
                value.values.mapNotNull(::shopValueMap)
            }
            is Iterable<*> -> value.mapNotNull(::shopValueMap)
            else -> emptyList()
        }
        return entries.mapNotNull { values ->
            val material = Material.matchMaterial(
                (values["material"] ?: values["item"])?.toString().orEmpty()
            ) ?: return@mapNotNull null
            BedWarsShopDelivery(
                material = material,
                amount = values["amount"].toIntValue(1).coerceIn(1, 64),
                itemName = (values["name"] ?: values["item-name"])?.toString()?.takeIf(String::isNotBlank),
                enchantments = readShopEnchantments(values["enchants"] ?: values["enchantments"]),
                potionEffects = readShopPotionEffects(values["potion"]),
                potionColor = readShopPotionColor(values["potion-color"]),
                autoEquip = values["auto-equip"].toBooleanValue(false),
                unbreakable = values["unbreakable"].toBooleanValue(false)
            )
        }
    }

    /** 将 Bukkit 配置节或普通映射规范化为 buy-items 字段映射。 */
    private fun shopValueMap(value: Any?): Map<String, Any?>? = when (value) {
        is ConfigurationSection -> value.getValues(false).mapKeys { it.key.lowercase() }
        is Map<*, *> -> value.entries.associate { it.key.toString().lowercase() to it.value }
        else -> null
    }

    /** 读取参考逗号分隔药水效果，按现代注册表解析名称并兼容旧 Bukkit 别名。 */
    private fun readShopPotionEffects(value: Any?): List<BedWarsPotionEffect> {
        val configured = when (value) {
            is Iterable<*> -> value.mapNotNull { it?.toString() }
            is String -> value.split(',')
            else -> emptyList()
        }
        return configured.mapNotNull { entry ->
            val parts = entry.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val type = readPotionEffectType(parts.firstOrNull()) ?: return@mapNotNull null
            val durationSeconds = if (parts.size >= 3) parts[1].toLongOrNull() ?: return@mapNotNull null else 50L
            val amplifier = if (parts.size >= 3) parts[2].toIntOrNull() ?: return@mapNotNull null else 1
            BedWarsPotionEffect(
                type,
                durationSeconds.coerceIn(1L, Int.MAX_VALUE / 20L).toInt() * 20,
                amplifier.coerceIn(0, 255)
            )
        }
    }

    /** 按现代注册表解析药水效果，并兼容参考配置使用的旧 Bukkit 名称。 */
    private fun readPotionEffectType(value: String?): PotionEffectType? {
        val effectName = when (val rawName = value?.trim()?.uppercase() ?: return null) {
            "JUMP" -> "JUMP_BOOST"
            "SLOW" -> "SLOWNESS"
            "FAST_DIGGING" -> "HASTE"
            "SLOW_DIGGING" -> "MINING_FATIGUE"
            "INCREASE_DAMAGE" -> "STRENGTH"
            "HEAL" -> "INSTANT_HEALTH"
            "HARM" -> "INSTANT_DAMAGE"
            "CONFUSION" -> "NAUSEA"
            "DAMAGE_RESISTANCE" -> "RESISTANCE"
            else -> rawName
        }
        val key = NamespacedKey.minecraft(effectName.lowercase())
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(key)
    }

    /** 把参考十进制或十六进制 CustomPotionColor 解析为 Bukkit 24 位颜色。 */
    private fun readShopPotionColor(value: Any?): Color? {
        val configured = value?.toString()?.trim()?.takeIf(String::isNotBlank) ?: return null
        val rgb = when {
            configured.startsWith("#") -> configured.drop(1).toIntOrNull(16)
            configured.startsWith("0x", ignoreCase = true) -> configured.drop(2).toIntOrNull(16)
            else -> configured.toIntOrNull() ?: configured.toIntOrNull(16)
        } ?: return null
        return rgb.takeIf { it in 0..0xFFFFFF }?.let(Color::fromRGB)
    }

    /** 读取商品预览 Lore，兼容字符串列表和带换行的单个字符串。 */
    private fun readShopDisplayLore(value: Any?): List<String> = when (value) {
        is Iterable<*> -> value.mapNotNull { it?.toString() }
        is String -> value.lines()
        else -> emptyList()
    }

    /** 从参考 buy-cmds 节点读取玩家或控制台命令，兼容字符串列表和单个字符串。 */
    private fun readShopCommands(value: Any?, key: String): List<String> {
        val configured = when (value) {
            is ConfigurationSection -> value.get(key)
            is Map<*, *> -> value.entries.firstOrNull { it.key.toString().equals(key, ignoreCase = true) }?.value
            else -> null
        }
        val commands = when (configured) {
            is Iterable<*> -> configured.mapNotNull { it?.toString() }
            is String -> listOf(configured)
            else -> emptyList()
        }
        return commands.map { it.trim().removePrefix("/") }.filter(String::isNotBlank)
    }

    /** 合并模块与参考 separator-glass 节点中的点击命令别名并保持声明顺序去重。 */
    private fun readUpgradeSeparatorCommands(vararg keys: String): List<String> {
        val sources = listOf(
            config.get("shop.upgrade-separator.on-click"),
            config.get("separator-glass.on-click")
        )
        return sources.flatMap { source ->
            keys.flatMap { key -> readShopCommands(source, key) }
        }.distinct()
    }

    /** 把参考 vault/economy 名称映射为 AIR 标记，其余货币继续按 Bukkit 材料解析。 */
    private fun readShopCurrency(value: Any?): Material? {
        val configured = value?.toString()?.trim().orEmpty()
        if (configured.equals("vault", ignoreCase = true) || configured.equals("economy", ignoreCase = true)) {
            return Material.AIR
        }
        return Material.matchMaterial(configured)
    }

    /** 读取扩展商品的命名空间附魔表，同时兼容映射和逗号分隔字符串。 */
    private fun readShopEnchantments(value: Any?): Map<Enchantment, Int> {
        val configured = when (value) {
            is Map<*, *> -> value.entries.map { it.key.toString() to it.value }
            is String -> value.split(',').mapNotNull { token ->
                val parts = token.trim().split(Regex("\\s+"), limit = 2)
                parts.firstOrNull()?.takeIf(String::isNotBlank)?.let { it to parts.getOrNull(1) }
            }
            else -> emptyList()
        }
        return configured.mapNotNull { (rawName, rawLevel) ->
            val normalized = rawName.trim().lowercase().replace(' ', '_')
            val key = NamespacedKey.fromString(if (':' in normalized) normalized else "minecraft:$normalized")
                ?: return@mapNotNull null
            val enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key)
                ?: return@mapNotNull null
            enchantment to rawLevel.toIntValue(1).coerceIn(1, 255)
        }.toMap()
    }

    /** 读取模块默认陷阱规则，并兼容参考 default-upgrades-settings 节点。 */
    private fun readDefaultTrapRules(): BedWarsTrapRules {
        val hardcoded = BedWarsTrapRules(3, 1, 1, Material.DIAMOND)
        val moduleDefaults = readTrapRules(config.getConfigurationSection("shop"), hardcoded)
        return readTrapRules(config.getConfigurationSection("default-upgrades-settings"), moduleDefaults)
    }

    /** 读取参考 group-upgrades-settings 和模块 shop.group-settings 的 selector-group 覆盖。 */
    private fun readTrapGroupRules(): Map<String, BedWarsTrapRules> {
        val defaults = readDefaultTrapRules()
        val rules = linkedMapOf<String, BedWarsTrapRules>()
        config.getKeys(false)
            .filter { it.endsWith("-upgrades-settings", ignoreCase = true) }
            .filterNot { it.equals("default-upgrades-settings", ignoreCase = true) }
            .forEach { path ->
                val group = path.dropLast("-upgrades-settings".length).trim().lowercase()
                if (group.isNotBlank()) {
                    rules[group] = readTrapRules(config.getConfigurationSection(path), defaults)
                }
            }
        config.getConfigurationSection("shop.group-settings")?.let { groups ->
            groups.getKeys(false).forEach { rawGroup ->
                val group = rawGroup.trim().lowercase()
                if (group.isNotBlank()) {
                    rules[group] = readTrapRules(groups.getConfigurationSection(rawGroup), rules[group] ?: defaults)
                }
            }
        }
        return rules
    }

    /** 把一个规则节点叠加到回退规则，并保留参考零值表示未覆盖的约定。 */
    private fun readTrapRules(
        section: ConfigurationSection?,
        fallback: BedWarsTrapRules
    ): BedWarsTrapRules {
        if (section == null) return fallback
        val queueLimit = when {
            section.contains("max-queued-traps") -> section.getInt("max-queued-traps").coerceIn(1, 16)
            section.getInt("trap-queue-limit", 0) > 0 -> section.getInt("trap-queue-limit").coerceIn(1, 16)
            else -> fallback.queueLimit
        }
        val startPrice = section.getInt("trap-start-price", 0)
            .takeIf { it > 0 }
            ?.coerceIn(1, 4096)
            ?: fallback.startPrice
        val priceIncrement = when {
            section.contains("trap-price-increment") -> section.getInt("trap-price-increment").coerceIn(0, 64)
            section.getInt("trap-increment-price", 0) > 0 -> section.getInt("trap-increment-price").coerceIn(1, 64)
            else -> fallback.priceIncrement
        }
        val currency = readShopCurrency(section.get("trap-currency")) ?: fallback.currency
        return BedWarsTrapRules(queueLimit, startPrice, priceIncrement, currency)
    }

    /** 读取模块默认升级菜单，并让参考 default-upgrades-settings.menu-content 优先。 */
    private fun readDefaultUpgradeMenuRules(): BedWarsUpgradeMenuRules {
        val fallback = BedWarsUpgradeMenuRules(
            BedWarsUpgradeType.entries.filterNot { it.trap },
            emptyList(),
            trapCategoryVisible = true,
            trapQueueVisible = true,
            separatorVisible = true
        )
        val moduleRules = if (config.isList("shop.upgrade-menu-content")) {
            parseUpgradeMenuRules(config.getStringList("shop.upgrade-menu-content"))
        } else {
            fallback
        }
        return if (config.isList("default-upgrades-settings.menu-content")) {
            parseUpgradeMenuRules(config.getStringList("default-upgrades-settings.menu-content"))
        } else {
            moduleRules
        }
    }

    /** 读取参考和模块 selector-group 的完整升级菜单内容，模块同组列表优先。 */
    private fun readUpgradeMenuGroupRules(): Map<String, BedWarsUpgradeMenuRules> {
        val rules = linkedMapOf<String, BedWarsUpgradeMenuRules>()
        config.getKeys(false)
            .filter { it.endsWith("-upgrades-settings", ignoreCase = true) }
            .filterNot { it.equals("default-upgrades-settings", ignoreCase = true) }
            .forEach { path ->
                val menuPath = "$path.menu-content"
                val group = path.dropLast("-upgrades-settings".length).trim().lowercase()
                if (group.isNotBlank() && config.isList(menuPath)) {
                    rules[group] = parseUpgradeMenuRules(config.getStringList(menuPath))
                }
            }
        config.getConfigurationSection("shop.group-settings")?.let { groups ->
            groups.getKeys(false).forEach { rawGroup ->
                val group = rawGroup.trim().lowercase()
                val menuPath = "shop.group-settings.$rawGroup.menu-content"
                if (group.isNotBlank() && config.isList(menuPath)) {
                    rules[group] = parseUpgradeMenuRules(config.getStringList(menuPath))
                }
            }
        }
        return rules
    }

    /** 把参考 menu-content 组件列表转换为模块升级顺序和结构可见性。 */
    private fun parseUpgradeMenuRules(entries: List<String>): BedWarsUpgradeMenuRules {
        val components = entries.map { it.substringBefore(',').trim().lowercase() }.filter(String::isNotBlank)
        val upgrades = linkedSetOf<BedWarsUpgradeType>()
        val directTraps = linkedSetOf<BedWarsUpgradeType>()
        components.forEach { component ->
            val type = upgradeTypeForMenuComponent(component) ?: return@forEach
            if (type.trap) directTraps.add(type) else upgrades.add(type)
        }
        return BedWarsUpgradeMenuRules(
            upgradeTypes = upgrades.toList(),
            directTrapTypes = directTraps.toList(),
            trapCategoryVisible = components.any { it == "category-traps" },
            trapQueueVisible = components.any { it.startsWith("trap-slot-") },
            separatorVisible = components.any { it.startsWith("separator-") }
        )
    }

    /** 映射参考内置升级/陷阱组件名，并兼容直接填写模块升级枚举。 */
    private fun upgradeTypeForMenuComponent(component: String): BedWarsUpgradeType? {
        return when (component) {
            "upgrade-swords" -> BedWarsUpgradeType.SHARPNESS
            "upgrade-armor" -> BedWarsUpgradeType.PROTECTION
            "upgrade-miner" -> BedWarsUpgradeType.HASTE
            "upgrade-forge" -> BedWarsUpgradeType.FORGE
            "upgrade-heal-pool" -> BedWarsUpgradeType.HEAL_POOL
            "upgrade-dragon" -> BedWarsUpgradeType.DRAGON_BUFF
            "base-trap-1" -> BedWarsUpgradeType.TRAP_BLINDNESS
            "base-trap-2" -> BedWarsUpgradeType.TRAP_COUNTER_OFFENSIVE
            "base-trap-3" -> BedWarsUpgradeType.TRAP_ALARM
            "base-trap-4" -> BedWarsUpgradeType.TRAP_MINER_FATIGUE
            else -> BedWarsUpgradeType.parse(component.removePrefix("upgrade-").replace('-', '_'))
        }
    }

    /** 读取陷阱分类组件顺序，并让参考 category-traps.category-content 优先。 */
    private fun readTrapCategoryRules(): BedWarsTrapCategoryRules {
        val entries = when {
            config.isList("category-traps.category-content") -> config.getStringList("category-traps.category-content")
            config.isList("shop.trap-category.menu-content") -> config.getStringList("shop.trap-category.menu-content")
            else -> DEFAULT_TRAP_CATEGORY_CONTENT
        }
        val components = entries.map { it.substringBefore(',').trim().lowercase() }.filter(String::isNotBlank)
        val traps = linkedSetOf<BedWarsUpgradeType>()
        components.forEach { component ->
            upgradeTypeForMenuComponent(component)?.takeIf { it.trap }?.let(traps::add)
        }
        return BedWarsTrapCategoryRules(
            trapTypes = traps.toList(),
            backVisible = components.any { it == "separator-back" }
        )
    }

    private fun readUpgradeItems(): List<BedWarsUpgradeItem> {
        val defaultTrapRules = readDefaultTrapRules()
        return config.getMapList("shop.upgrades").mapNotNull { values ->
            val id = values["id"]?.toString()?.normalizedBedWarsId()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val upgradeType = BedWarsUpgradeType.parse(values["type"]?.toString()) ?: return@mapNotNull null
            val displayItem = shopValueMap(values["display-item"]).orEmpty()
            val icon = Material.matchMaterial((values["icon"] ?: displayItem["material"])?.toString().orEmpty())
                ?: return@mapNotNull null
            val referenceCost = values["cost"]?.toIntValue(0)
            val price = when {
                upgradeType.trap && referenceCost != null && referenceCost <= 0 -> defaultTrapRules.startPrice
                referenceCost != null -> referenceCost
                else -> values["price"].toIntValue(1)
            }.coerceIn(1, 4096)
            val currencyValue = values["currency"]
            val currency = readShopCurrency(currencyValue)
                ?: defaultTrapRules.currency.takeIf { upgradeType.trap }
                ?: Material.DIAMOND
            val trapDynamicPrice = upgradeType.trap && when {
                values.containsKey("dynamic-price") -> values["dynamic-price"].toBooleanValue(true)
                values.containsKey("price-scales") -> values["price-scales"].toBooleanValue(true)
                referenceCost != null -> referenceCost <= 0
                else -> true
            }
            BedWarsUpgradeItem(
                id = id,
                displayName = values["display-name"]?.toString()?.takeIf(String::isNotBlank) ?: id,
                displayLore = readShopDisplayLore(values["display-lore"] ?: values["item-lore"] ?: values["lore"]),
                icon = icon,
                iconAmount = (values["icon-amount"] ?: values["display-item-amount"] ?: displayItem["amount"])
                    .toIntValue(1)
                    .coerceIn(1, 99),
                iconEnchanted = (values["icon-enchanted"] ?: values["display-item-enchanted"] ?: displayItem["enchanted"])
                    .toBooleanValue(false),
                upgradeType = upgradeType,
                currency = currency,
                price = price,
                trapDynamicPrice = trapDynamicPrice,
                trapUsesConfiguredStartPrice = upgradeType.trap && referenceCost != null && referenceCost <= 0,
                trapUsesConfiguredCurrency = upgradeType.trap && values["currency"] == null,
                tier = values["tier"].toIntValue(1).coerceIn(1, 16),
                actions = readUpgradeActions(values["receive"]),
                trapActions = readTrapActions(values["receive"]),
                customAnnounce = values["custom-announce"].toBooleanValue(false),
                trapSound = readInlineSoundRule(values["sound"])
            )
        }
    }

    /** 按声明顺序解析参考 receive 的附魔、效果、生成器、末影龙和命令动作。 */
    private fun readUpgradeActions(value: Any?): List<BedWarsUpgradeAction> {
        val actions = when (value) {
            is Iterable<*> -> value.mapNotNull { it?.toString() }
            is String -> listOf(value)
            else -> emptyList()
        }
        return actions.mapNotNull { action ->
            val actionParts = action.split(':', limit = 2)
            if (actionParts.size != 2) return@mapNotNull null
            val type = actionParts[0].trim().lowercase().replace('_', '-')
            val data = actionParts[1].trim()
            when (type) {
                "enchant-item" -> readUpgradeEnchantAction(data)
                "player-effect" -> readUpgradeEffectAction(data)
                "generator-edit" -> readUpgradeGeneratorAction(data)
                "dragon" -> data.toIntOrNull()?.coerceIn(0, 8)?.let(::BedWarsUpgradeDragonAction)
                "command" -> readUpgradeCommand(data)
                else -> null
            }
        }
    }

    /** 解析 enchant-item:附魔,等级,sword|armor|bow 动作。 */
    private fun readUpgradeEnchantAction(value: String): BedWarsUpgradeEnchantAction? {
        val data = value.split(',').map(String::trim)
        if (data.size < 3) return null
        val enchantment = readUpgradeEnchantment(data[0]) ?: return null
        val amplifier = data[1].toIntOrNull()?.coerceIn(0, 255) ?: 1
        val target = runCatching { BedWarsUpgradeEnchantTarget.valueOf(data[2].uppercase()) }.getOrNull() ?: return null
        return BedWarsUpgradeEnchantAction(enchantment, amplifier, target)
    }

    /** 按现代注册表解析附魔，并兼容参考默认配置的旧 Bukkit 名称。 */
    private fun readUpgradeEnchantment(value: String): Enchantment? {
        val enchantmentName = when (val rawName = value.trim().uppercase()) {
            "DAMAGE_ALL" -> "SHARPNESS"
            "PROTECTION_ENVIRONMENTAL" -> "PROTECTION"
            "ARROW_DAMAGE" -> "POWER"
            "ARROW_KNOCKBACK" -> "PUNCH"
            "DIG_SPEED" -> "EFFICIENCY"
            else -> rawName
        }
        val normalized = enchantmentName.lowercase().replace(' ', '_')
        val key = NamespacedKey.fromString(if (':' in normalized) normalized else "minecraft:$normalized") ?: return null
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key)
    }

    /** 解析 player-effect:效果,等级,秒数,team|base 动作。 */
    private fun readUpgradeEffectAction(value: String): BedWarsUpgradeEffectAction? {
        val data = value.split(',').map(String::trim)
        if (data.size < 4) return null
        val effectType = readPotionEffectType(data[0]) ?: return null
        val amplifier = data[1].toIntOrNull()?.coerceIn(0, 255) ?: 1
        val rawDurationSeconds = data[2].toLongOrNull() ?: 0L
        val durationSeconds = if (rawDurationSeconds == Long.MIN_VALUE) {
            Long.MAX_VALUE
        } else {
            kotlin.math.abs(rawDurationSeconds)
        }
        val durationTicks = if (durationSeconds == 0L) {
            Int.MAX_VALUE
        } else {
            (durationSeconds.coerceAtMost(Int.MAX_VALUE / 20L) * 20L).toInt()
        }
        val target = runCatching { BedWarsUpgradeEffectTarget.valueOf(data[3].uppercase()) }.getOrNull() ?: return null
        return BedWarsUpgradeEffectAction(effectType, amplifier, durationTicks, target)
    }

    /** 解析 generator-edit:类型,间隔秒,数量,上限 动作及参考单字母类型别名。 */
    private fun readUpgradeGeneratorAction(value: String): BedWarsUpgradeGeneratorAction? {
        val data = value.split(',').map(String::trim)
        if (data.size < 4) return null
        val generatorType = when (data[0].lowercase()) {
            "iron", "i" -> BedWarsGeneratorType.IRON
            "gold", "g" -> BedWarsGeneratorType.GOLD
            "emerald", "e" -> BedWarsGeneratorType.EMERALD
            else -> return null
        }
        val intervalSeconds = data[1].toLongOrNull()?.coerceIn(1L, Int.MAX_VALUE / 20L) ?: return null
        val amount = data[2].toIntOrNull()?.coerceIn(1, 64) ?: return null
        val spawnLimit = data[3].toIntOrNull()?.coerceIn(1, 4096) ?: return null
        return BedWarsUpgradeGeneratorAction(generatorType, (intervalSeconds * 20L).toInt(), amount, spawnLimit)
    }

    /** 解析 command:模式,命令 动作，并保留命令中的后续逗号。 */
    private fun readUpgradeCommand(value: String): BedWarsUpgradeCommand? {
        val commandParts = value.split(',', limit = 2)
        if (commandParts.size != 2) return null
        val type = BedWarsUpgradeCommandType.parse(commandParts[0]) ?: return null
        val command = commandParts[1].trim().takeIf(String::isNotBlank) ?: return null
        return BedWarsUpgradeCommand(type, command)
    }

    /** 按声明顺序解析陷阱 receive 的效果、移除效果和移除附魔动作。 */
    private fun readTrapActions(value: Any?): List<BedWarsTrapAction> {
        val actions = when (value) {
            is Iterable<*> -> value.mapNotNull { it?.toString() }
            is String -> listOf(value)
            else -> emptyList()
        }
        return actions.mapNotNull { action ->
            val actionParts = action.split(':', limit = 2)
            if (actionParts.size != 2) return@mapNotNull null
            when (actionParts[0].trim().lowercase().replace('_', '-')) {
                "player-effect" -> readTrapEffectAction(actionParts[1])
                "remove-effect" -> readTrapRemoveEffectAction(actionParts[1])
                "disenchant-item" -> readTrapDisenchantAction(actionParts[1])
                else -> null
            }
        }
    }

    /** 解析陷阱 player-effect:效果,等级,秒数,enemy|team|base 动作。 */
    private fun readTrapEffectAction(value: String): BedWarsTrapEffectAction? {
        val data = value.split(',').map(String::trim)
        if (data.size < 4) return null
        val effectType = readPotionEffectType(data[0]) ?: return null
        val amplifier = data[1].toIntOrNull()?.coerceIn(0, 255) ?: 1
        val rawDurationSeconds = data[2].toLongOrNull() ?: 0L
        val durationSeconds = if (rawDurationSeconds == Long.MIN_VALUE) {
            Long.MAX_VALUE
        } else {
            kotlin.math.abs(rawDurationSeconds)
        }
        val durationTicks = if (durationSeconds == 0L) {
            Int.MAX_VALUE
        } else {
            (durationSeconds.coerceAtMost(Int.MAX_VALUE / 20L) * 20L).toInt()
        }
        val targetName = if (data[3].equals("enemies", ignoreCase = true)) "ENEMY" else data[3].uppercase()
        val target = runCatching { BedWarsTrapEffectTarget.valueOf(targetName) }.getOrNull() ?: return null
        return BedWarsTrapEffectAction(effectType, amplifier, durationTicks, target)
    }

    /** 解析陷阱 remove-effect:效果 动作，兼容参考附带但忽略的目标参数。 */
    private fun readTrapRemoveEffectAction(value: String): BedWarsTrapRemoveEffectAction? {
        val effectType = readPotionEffectType(value.substringBefore(',')) ?: return null
        return BedWarsTrapRemoveEffectAction(effectType)
    }

    /** 解析陷阱 disenchant-item:附魔,sword|armor|bow 动作。 */
    private fun readTrapDisenchantAction(value: String): BedWarsTrapDisenchantAction? {
        val data = value.split(',').map(String::trim)
        if (data.size < 2) return null
        val enchantment = readUpgradeEnchantment(data[0]) ?: return null
        val target = runCatching { BedWarsUpgradeEnchantTarget.valueOf(data[1].uppercase()) }.getOrNull() ?: return null
        return BedWarsTrapDisenchantAction(enchantment, target)
    }

    private fun teamExists(game: ManagedGameConfig, teamId: String): Boolean {
        if (teamId.isBlank()) return false
        val managed = YamlConfiguration.loadConfiguration(game.file)
        return managed.isConfigurationSection("bedwars.teams.$teamId")
    }

    private fun upsertGenerator(
        game: ManagedGameConfig,
        parentPath: String,
        generatorId: String,
        type: BedWarsGeneratorType,
        point: BedWarsPoint,
        intervalTicks: Int
    ): String? {
        val id = generatorId.normalizedBedWarsId().takeIf(String::isNotBlank) ?: return null
        saveManaged(game) { managed ->
            val path = "$parentPath.$id"
            managed.set("$path.type", type.name)
            managed.set("$path.interval-ticks", intervalTicks.coerceIn(1, 72_000))
            point.writeTo(managed.createSectionReplacing("$path.point"))
        }
        return id
    }

    private fun savePoint(game: ManagedGameConfig, path: String, point: BedWarsPoint) {
        saveManaged(game) { point.writeTo(it.createSectionReplacing(path)) }
    }

    private fun removePath(game: ManagedGameConfig, path: String): Boolean {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        if (!managed.contains(path)) return false
        managed.set(path, null)
        managed.save(game.file)
        return true
    }

    private fun saveManaged(game: ManagedGameConfig, mutate: (YamlConfiguration) -> Unit) {
        val managed = YamlConfiguration.loadConfiguration(game.file)
        mutate(managed)
        managed.save(game.file)
    }

    private fun ensureDefaults() {
        val hasSequentialTimeline = config.contains("countdowns.beds-destroy") ||
            config.contains("countdowns.dragon-spawn") || config.contains("countdowns.game-end")
        val hasLegacyTimeline = config.contains("game.beds-destroy-seconds") ||
            config.contains("game.sudden-death-seconds") || config.contains("game.duration-seconds")
        if (!hasSequentialTimeline && !hasLegacyTimeline) {
            config.set("countdowns.beds-destroy", 360)
            config.set("countdowns.dragon-spawn", 600)
            config.set("countdowns.game-end", 120)
        }
        if (!config.contains("countdowns.game-restart") && !config.contains("game.result-display-seconds")) {
            config.set("countdowns.game-restart", 45)
        }
        if (!config.contains("countdowns.game-start-regular")) {
            val regularCountdown = if (config.contains("game.countdown-seconds")) {
                config.getInt("game.countdown-seconds", 40)
            } else {
                40
            }
            config.set("countdowns.game-start-regular", regularCountdown)
        }
        if (!config.contains("game.island-radius")) {
            val legacyRadius = if (config.contains("shop.trap-radius")) {
                config.getFiniteDouble("shop.trap-radius", 17.0)
            } else {
                17.0
            }
            config.set("game.island-radius", legacyRadius)
        }
        if (!config.contains("scoreboard-settings.sidebar.enable-lobby-sidebar")) {
            config.set(
                "scoreboard-settings.sidebar.enable-lobby-sidebar",
                config.getBoolean("scoreboard-settings.sidebar.enable-game-sidebar", true)
            )
        }
        if (!config.contains("chat.format")) {
            val formattingEnabled = when {
                config.contains("chat-settings.format") -> config.getBoolean("chat-settings.format", true)
                config.contains("formatChat") -> config.getBoolean("formatChat", true)
                else -> true
            }
            config.set("chat.format", formattingEnabled)
        }
        if (!config.contains("seasonal.halloween.enabled") && config.contains("enable-halloween-feature")) {
            config.set("seasonal.halloween.enabled", config.getBoolean("enable-halloween-feature"))
        }
        val countdownSound = config.getString("game.sounds.countdown.sound", "ENTITY_CHICKEN_EGG")
            ?: "ENTITY_CHICKEN_EGG"
        val countdownVolume = config.getFiniteDouble("game.sounds.countdown.volume", 1.0)
        val countdownPitch = config.getFiniteDouble("game.sounds.countdown.pitch", 1.0)
        val defaults = mutableMapOf<String, Any>(
            "id" to "bedwars",
            "name" to "起床战争",
            "enabled" to false,
            "main" to "jar",
            "jar" to "../bedwars.jar",
            "entrypoint" to "org.katacr.kagamecenter.bedwars.BedWarsModuleProvider",
            "description" to "KaGameCenter BedWars module (development preview).",
            "game.display-name" to "起床战争",
            "game.min-players" to 2,
            "game.max-players" to 16,
            "game.default-item-groups.default" to listOf("WOODEN_SWORD"),
            "pre-game-items.stats.material" to "PLAYER_HEAD",
            "pre-game-items.stats.slot" to 0,
            "pre-game-items.stats.enchanted" to false,
            "pre-game-items.stats.command" to "kagamecenter stats bedwars",
            "pre-game-items.leave.material" to "RED_BED",
            "pre-game-items.leave.slot" to 8,
            "pre-game-items.leave.enchanted" to false,
            "pre-game-items.leave.command" to "kagamecenter leave",
            "spectator-items.teleporter.material" to "PLAYER_HEAD",
            "spectator-items.teleporter.slot" to 0,
            "spectator-items.teleporter.enchanted" to false,
            "spectator-items.teleporter.action" to "FOLLOW",
            "spectator-items.menu.material" to "NETHER_STAR",
            "spectator-items.menu.slot" to 4,
            "spectator-items.menu.enchanted" to false,
            "spectator-items.menu.action" to "MENU",
            "spectator-items.leave.material" to "RED_BED",
            "spectator-items.leave.slot" to 8,
            "spectator-items.leave.enchanted" to false,
            "spectator-items.leave.action" to "LEAVE",
            "countdowns.game-start-half-arena" to 25,
            "countdowns.game-start-shortened" to 5,
            "lobby-settings.void-tp" to true,
            "lobby-settings.void-height" to 0.0,
            "chat.format" to true,
            "chat.shout-cooldown-seconds" to 30,
            "game.sounds.join-allowed.sound" to "ENTITY_SLIME_JUMP",
            "game.sounds.join-allowed.volume" to 1.0,
            "game.sounds.join-allowed.pitch" to 1.0,
            "game.sounds.join-denied.sound" to "ENTITY_VILLAGER_NO",
            "game.sounds.join-denied.volume" to 1.0,
            "game.sounds.join-denied.pitch" to 1.0,
            "game.sounds.rejoin-allowed.sound" to "ENTITY_SLIME_JUMP",
            "game.sounds.rejoin-allowed.volume" to 1.0,
            "game.sounds.rejoin-allowed.pitch" to 1.0,
            "game.sounds.rejoin-denied.sound" to "ENTITY_VILLAGER_NO",
            "game.sounds.rejoin-denied.volume" to 1.0,
            "game.sounds.rejoin-denied.pitch" to 1.0,
            "game.sounds.spectate-allowed.sound" to "ENTITY_SLIME_JUMP",
            "game.sounds.spectate-allowed.volume" to 1.0,
            "game.sounds.spectate-allowed.pitch" to 1.0,
            "game.sounds.spectate-denied.sound" to "ENTITY_VILLAGER_NO",
            "game.sounds.spectate-denied.volume" to 1.0,
            "game.sounds.spectate-denied.pitch" to 1.0,
            "game.sounds.spectator-target-click.sound" to "ENTITY_SLIME_JUMP",
            "game.sounds.spectator-target-click.volume" to 1.0,
            "game.sounds.spectator-target-click.pitch" to 1.0,
            "game.sounds.arena-selector-open.sound" to "ENTITY_CHICKEN_EGG",
            "game.sounds.arena-selector-open.volume" to 1.0,
            "game.sounds.arena-selector-open.pitch" to 1.0,
            "game.sounds.stats-menu-open.sound" to "ENTITY_CHICKEN_EGG",
            "game.sounds.stats-menu-open.volume" to 1.0,
            "game.sounds.stats-menu-open.pitch" to 1.0,
            "game.sounds.countdown.sound" to "ENTITY_CHICKEN_EGG",
            "game.sounds.countdown.volume" to 1.0,
            "game.sounds.countdown.pitch" to 1.0,
            "game.sounds.start.sound" to "BLOCK_SLIME_BLOCK_FALL",
            "game.sounds.start.volume" to 1.0,
            "game.sounds.start.pitch" to 1.0,
            "game.sounds.respawn.sound" to "BLOCK_SLIME_BLOCK_FALL",
            "game.sounds.respawn.volume" to 1.0,
            "game.sounds.respawn.pitch" to 1.0,
            "game.sounds.kill.sound" to "ENTITY_EXPERIENCE_ORB_PICKUP",
            "game.sounds.kill.volume" to 1.0,
            "game.sounds.kill.pitch" to 1.0,
            "game.sounds.bed-destroyed.sound" to "ENTITY_ENDER_DRAGON_GROWL",
            "game.sounds.bed-destroyed.volume" to 1.0,
            "game.sounds.bed-destroyed.pitch" to 1.0,
            "game.sounds.own-bed-destroyed.sound" to "ENTITY_WITHER_DEATH",
            "game.sounds.own-bed-destroyed.volume" to 1.0,
            "game.sounds.own-bed-destroyed.pitch" to 1.0,
            "game.sounds.all-beds-destroyed.sound" to "ENTITY_ENDER_DRAGON_GROWL",
            "game.sounds.all-beds-destroyed.volume" to 1.0,
            "game.sounds.all-beds-destroyed.pitch" to 1.0,
            "game.sounds.sudden-death.sound" to "ENTITY_ENDER_DRAGON_FLAP",
            "game.sounds.sudden-death.volume" to 1.0,
            "game.sounds.sudden-death.pitch" to 1.0,
            "game.sounds.end.sound" to "ITEM_TRIDENT_THUNDER",
            "game.sounds.end.volume" to 1.0,
            "game.sounds.end.pitch" to 1.0,
            "game.allowed-commands" to listOf("kagamecenter", "kgc", "allchat", "a", "globalchat", "g"),
            "allow-hunger-depletion.waiting" to false,
            "allow-hunger-depletion.ingame" to false,
            "game.respawn-seconds" to 5,
            "game.respawn-invulnerability-seconds" to 4,
            "game.reconnect-grace-seconds" to 300,
            "game.afk-seconds" to 45,
            "game.close-delay-seconds" to 5,
            "game.void-y" to -64.0,
            "game.world-border-size" to 300,
            "game.win-points" to 3,
            "levels.enabled" to true,
            "levels.rankup-costs" to listOf(1000, 2000, 3000, 3500),
            "levels.default-rankup-cost" to 5000,
            "levels.progress-bar.symbol" to "■",
            "levels.progress-bar.unlocked-color" to "&b",
            "levels.progress-bar.locked-color" to "&7",
            "levels.progress-bar.format" to "&8 [{progress}&8]",
            "levels.rewards.per-minute" to 10,
            "levels.rewards.per-teammate" to 5,
            "levels.rewards.game-win" to 100,
            "levels.rewards.bed-destroyed" to 15,
            "levels.rewards.regular-kill" to 10,
            "levels.rewards.final-kill" to 15,
            "money-rewards.per-minute" to 5,
            "money-rewards.per-teammate" to 30,
            "money-rewards.game-win" to 90,
            "money-rewards.bed-destroyed" to 60,
            "money-rewards.regular-kill" to 10,
            "money-rewards.final-kill" to 40,
            "game.max-build-y" to 180,
            "game.island-radius" to 17.0,
            "game.disable-empty-team-generators" to false,
            "game.disable-empty-team-npcs" to true,
            "game.use-bed-hologram" to true,
            "game.vanilla-death-drops" to false,
            "game.mark-leave-as-abandon" to false,
            "seasonal.halloween.enabled" to true,
            "game-end.chat-top.order-by" to "KILLS",
            "game-end.chat-top.hide-missing" to true,
            "game-end.sb-top.order-by" to "KILLS",
            "game-end.sb-top.hide-missing" to true,
            "scoreboard-settings.sidebar.enable-lobby-sidebar" to true,
            "scoreboard-settings.sidebar.enable-game-sidebar" to true,
            "scoreboard-settings.sidebar.title-refresh-interval" to 4,
            "scoreboard-settings.sidebar.placeholders-refresh-interval" to 20,
            "scoreboard-settings.placeholders.server-ip" to "",
            "scoreboard-settings.placeholders.powered-by" to "KaGameCenter",
            "scoreboard-settings.tab-header-footer.enable" to true,
            "scoreboard-settings.tab-header-footer.refresh-interval" to 10,
            "scoreboard-settings.player-list.format-waiting-list" to false,
            "scoreboard-settings.player-list.format-starting-list" to false,
            "scoreboard-settings.player-list.format-playing-list" to true,
            "scoreboard-settings.player-list.format-restarting-list" to true,
            "scoreboard-settings.player-list.names-refresh-interval" to 1200,
            "scoreboard-settings.health.enable" to true,
            "scoreboard-settings.health.display-in-tab" to true,
            "scoreboard-settings.health.animation-refresh-interval" to 300,
            "blocks.place-allowed" to DEFAULT_PLACE_MATERIALS,
            "blocks.breakable-map-blocks" to DEFAULT_BREAKABLE_MAP_MATERIALS,
            "blocks.allow-fire-extinguish" to true,
            "blocks.auto-prime-tnt" to true,
            "blocks.tnt-fuse-ticks" to 45,
            "blocks.blast-proof-glass-blocks-rays" to true,
            "blocks.spawn-protection-radius" to 2.0,
            "blocks.shop-protection-radius" to 1.0,
            "blocks.generator-protection-radius" to 1.0,
            "blocks.team-chest-radius" to 17.0,
            "inventories.disable-crafting-table" to true,
            "inventories.disable-enchanting-table" to true,
            "inventories.disable-furnace" to true,
            "inventories.disable-brewing-stand" to true,
            "inventories.disable-anvil" to true,
            "generators.holograms.enabled" to true,
            "performance-settings.rotate-generators" to true,
            "generators.stack-items" to false,
            "generators.team-split.enabled" to true,
            "generators.team-split.radius" to 1.0,
            "generators.sounds.diamond-upgrade.sound" to "ENTITY_PLAYER_LEVELUP",
            "generators.sounds.diamond-upgrade.volume" to 1.0,
            "generators.sounds.diamond-upgrade.pitch" to 1.0,
            "generators.sounds.emerald-upgrade.sound" to "ENTITY_GHAST_WARN",
            "generators.sounds.emerald-upgrade.volume" to 1.0,
            "generators.sounds.emerald-upgrade.pitch" to 1.0,
            "generators.iron.amount" to 2,
            "generators.iron.spawn-limit" to 32,
            "generators.gold.amount" to 2,
            "generators.gold.spawn-limit" to 7,
            "generators.diamond.tiers.1.start-seconds" to 0,
            "generators.diamond.tiers.1.interval-ticks" to 600,
            "generators.diamond.tiers.1.amount" to 1,
            "generators.diamond.tiers.1.spawn-limit" to 4,
            "generators.diamond.tiers.2.start-seconds" to 360,
            "generators.diamond.tiers.2.interval-ticks" to 400,
            "generators.diamond.tiers.2.amount" to 1,
            "generators.diamond.tiers.2.spawn-limit" to 6,
            "generators.diamond.tiers.3.start-seconds" to 1080,
            "generators.diamond.tiers.3.interval-ticks" to 300,
            "generators.diamond.tiers.3.amount" to 1,
            "generators.diamond.tiers.3.spawn-limit" to 8,
            "generators.emerald.tiers.1.start-seconds" to 0,
            "generators.emerald.tiers.1.interval-ticks" to 1400,
            "generators.emerald.tiers.1.amount" to 1,
            "generators.emerald.tiers.1.spawn-limit" to 4,
            "generators.emerald.tiers.2.start-seconds" to 720,
            "generators.emerald.tiers.2.interval-ticks" to 1000,
            "generators.emerald.tiers.2.amount" to 1,
            "generators.emerald.tiers.2.spawn-limit" to 6,
            "generators.emerald.tiers.3.start-seconds" to 1440,
            "generators.emerald.tiers.3.interval-ticks" to 600,
            "generators.emerald.tiers.3.amount" to 1,
            "generators.emerald.tiers.3.spawn-limit" to 8,
            "generators.forge.speed-multipliers" to listOf(0.6667, 0.5, 0.5, 0.3333),
            "generators.forge.emerald-interval-ticks" to 200,
            "generators.forge.tier-3-emerald-amount" to 1,
            "generators.forge.tier-4-emerald-amount" to 2,
            "generators.forge.emerald-spawn-limit" to 4,
            "dragons.base-count" to 1,
            "dragons.buff-extra-count" to 1,
            "dragons.spawn-height" to 16.0,
            "dragons.health" to 100.0,
            "dragons.damage" to 6.0,
            "dragons.speed" to 0.8,
            "dragons.attack-radius" to 4.5,
            "shop.holograms.enabled" to true,
            "shop.trap-category.icon" to "LEATHER",
            "shop.trap-category.amount" to 1,
            "shop.trap-category.enchanted" to false,
            "shop.trap-category.menu-content" to DEFAULT_TRAP_CATEGORY_CONTENT,
            "shop.upgrade-separator.icon" to "GRAY_STAINED_GLASS_PANE",
            "shop.upgrade-separator.amount" to 1,
            "shop.upgrade-separator.enchanted" to false,
            "shop.upgrade-separator.on-click.player" to emptyList<String>(),
            "shop.upgrade-separator.on-click.console" to emptyList<String>(),
            "shop.sounds.bought.sound" to "ENTITY_VILLAGER_YES",
            "shop.sounds.bought.volume" to 1.0,
            "shop.sounds.bought.pitch" to 1.0,
            "shop.sounds.insufficient.sound" to "ENTITY_VILLAGER_NO",
            "shop.sounds.insufficient.volume" to 1.0,
            "shop.sounds.insufficient.pitch" to 1.0,
            "shop.sounds.auto-equip.sound" to "ITEM_ARMOR_EQUIP_GENERIC",
            "shop.sounds.auto-equip.volume" to 1.0,
            "shop.sounds.auto-equip.pitch" to 1.0,
            "shop.sounds.trap-trigger.sound" to "ENTITY_ENDERMAN_TELEPORT",
            "shop.sounds.trap-trigger.volume" to 1.0,
            "shop.sounds.trap-trigger.pitch" to 1.0,
            "shop.heal-pool.particles.enabled" to true,
            "shop.heal-pool.particles.seen-by-team-only" to true,
            "shop.traps.blindness.duration-seconds" to 5,
            "shop.traps.blindness.amplifier" to 1,
            "shop.traps.counter-offensive.duration-seconds" to 15,
            "shop.traps.counter-offensive.amplifier" to 1,
            "shop.traps.alarm.glowing-seconds" to 0,
            "shop.traps.miner-fatigue.duration-seconds" to 15,
            "shop.traps.miner-fatigue.amplifier" to 1,
            "shop.tools.pickaxe-efficiency-levels" to listOf(0, 2, 3, 3),
            "shop.tools.pickaxe-sharpness-levels" to listOf(0, 0, 2, 0),
            "shop.tools.axe-efficiency-levels" to listOf(1, 1, 2, 3),
            "shop.max-queued-traps" to 3,
            "shop.trap-start-price" to 1,
            "shop.trap-currency" to "DIAMOND",
            "shop.trap-price-increment" to 1,
            "shop.upgrade-menu-content" to DEFAULT_UPGRADE_MENU_CONTENT,
            "shop.quick-buy-defaults" to DEFAULT_QUICK_BUY,
            "shop.items" to defaultShopItems(),
            "shop.upgrades" to defaultUpgradeItems(),
            "specials.tnt.barycenter-alteration-y" to 0.5,
            "specials.tnt.strength-reduction" to 5.0,
            "specials.tnt.y-axis-reduction" to 2.0,
            "specials.tnt.damage-self" to 1.0,
            "specials.tnt.damage-teammates" to 5.0,
            "specials.tnt.damage-others" to 10.0,
            "specials.tnt.spoil-carriers" to true,
            "specials.fireball.speed" to 10.0,
            "specials.fireball.yield" to 3.0,
            "specials.fireball.make-fire" to false,
            "specials.fireball.cooldown-ticks" to 10,
            "specials.fireball.horizontal-knockback" to 1.0,
            "specials.fireball.vertical-knockback" to 0.65,
            "specials.fireball.damage-self" to 2.0,
            "specials.fireball.damage-teammates" to 0.0,
            "specials.fireball.damage-enemies" to 2.0,
            "specials.sounds.bridge-block.sound" to "ENTITY_CHICKEN_EGG",
            "specials.sounds.bridge-block.volume" to 1.0,
            "specials.sounds.bridge-block.pitch" to 1.0,
            "specials.sounds.ender-pearl-landed.sound" to "ENTITY_ENDERMAN_TELEPORT",
            "specials.sounds.ender-pearl-landed.volume" to 1.0,
            "specials.sounds.ender-pearl-landed.pitch" to 1.0,
            "specials.sounds.popup-tower-build.sound" to "ENTITY_CHICKEN_EGG",
            "specials.sounds.popup-tower-build.volume" to 1.0,
            "specials.sounds.popup-tower-build.pitch" to 1.0,
            "specials.bridge-egg.max-distance" to 27.0,
            "specials.bridge-egg.start-distance" to 4.0,
            "specials.bridge-egg.max-vertical-drop" to 9.0,
            "specials.bed-bug.duration-seconds" to 15,
            "specials.bed-bug.health" to 8.0,
            "specials.bed-bug.damage" to 4.0,
            "specials.bed-bug.speed" to 0.25,
            "specials.dream-defender.duration-seconds" to 240,
            "specials.dream-defender.health" to 100.0,
            "specials.dream-defender.damage" to -1.0,
            "specials.dream-defender.speed" to 0.25,
            "specials.potions.speed-seconds" to 45,
            "specials.potions.jump-seconds" to 45,
            "specials.potions.invisibility-seconds" to 30,
            "specials.potions.remove-invisibility-on-damage" to true,
            "specials.magic-milk.duration-seconds" to 30,
            "specials.popup-tower.radius" to 2,
            "specials.popup-tower.wall-height" to 5,
            "specials.popup-tower.blocks-per-tick" to 2,
            "maps.default.display-name" to "默认地图",
            "maps.default.template" to "bedwars/default",
            "spectator.enabled" to true,
            "spectator.mode" to "managed"
        )
        (1..4).forEach { second ->
            defaults["game.sounds.countdown-final.$second.sound"] = countdownSound
            defaults["game.sounds.countdown-final.$second.volume"] = countdownVolume
            defaults["game.sounds.countdown-final.$second.pitch"] = countdownPitch
        }
        defaults.forEach { (path, value) -> if (!config.contains(path)) config.set(path, value) }
    }

    private companion object {
        const val HALLOWEEN_PRODUCT_ID = "halloween-pumpkin"
        val TEAM_POINT_FIELDS = setOf("spawn", "bed", "kill-drops", "shop", "upgrade-shop")
        val DEFAULT_PLACE_MATERIALS = listOf(
            "WHITE_WOOL",
            "RED_WOOL",
            "BLUE_WOOL",
            "GREEN_WOOL",
            "YELLOW_WOOL",
            "CYAN_WOOL",
            "PINK_WOOL",
            "GRAY_WOOL",
            "OAK_PLANKS",
            "ORANGE_TERRACOTTA",
            "END_STONE",
            "GLASS",
            "LADDER",
            "OBSIDIAN",
            "SPONGE",
            "TNT"
        )
        val DEFAULT_BREAKABLE_MAP_MATERIALS = listOf(
            "SHORT_GRASS",
            "TALL_GRASS",
            "FERN",
            "LARGE_FERN",
            "SEAGRASS",
            "TALL_SEAGRASS",
            "SUGAR_CANE",
            "SUNFLOWER",
            "LILAC",
            "ROSE_BUSH",
            "PEONY",
            "DIRT_PATH"
        )
        val DEFAULT_QUICK_BUY = listOf(
            "_", "_", "_", "_", "_", "_", "_",
            "wool", "stone-sword", "chain-armor", "_", "bow", "speed-potion", "tnt",
            "planks", "iron-sword", "iron-armor", "shears", "arrows", "jump-potion", "water-bucket"
        )
        val DEFAULT_UPGRADE_MENU_CONTENT = listOf(
            "upgrade-swords,10",
            "upgrade-armor,11",
            "upgrade-miner,12",
            "upgrade-forge,13",
            "upgrade-heal-pool,14",
            "upgrade-dragon,15",
            "category-traps,16",
            "separator-glass,18,19,20,21,22,23,24,25,26",
            "trap-slot-first,30",
            "trap-slot-second,31",
            "trap-slot-third,32"
        )
        val DEFAULT_TRAP_CATEGORY_CONTENT = listOf(
            "base-trap-1,10",
            "base-trap-2,11",
            "base-trap-3,12",
            "base-trap-4,13",
            "separator-back,31"
        )
        val DOWNGRADABLE = mapOf<String, Any>("downgradable" to true)

        fun defaultShopItems(): List<Map<String, Any>> = listOf(
            shopItem("wool", "羊毛 x16", "WHITE_WOOL", "ITEM", "WHITE_WOOL", 16, "IRON_INGOT", 4),
            shopItem("clay", "陶瓦 x16", "ORANGE_TERRACOTTA", "ITEM", "ORANGE_TERRACOTTA", 16, "IRON_INGOT", 12),
            shopItem("planks", "木板 x16", "OAK_PLANKS", "ITEM", "OAK_PLANKS", 16, "GOLD_INGOT", 4),
            shopItem("end-stone", "末地石 x16", "END_STONE", "ITEM", "END_STONE", 16, "IRON_INGOT", 24),
            shopItem("glass", "防爆玻璃 x4", "GLASS", "ITEM", "GLASS", 4, "IRON_INGOT", 12),
            shopItem("ladder", "梯子 x16", "LADDER", "ITEM", "LADDER", 16, "IRON_INGOT", 4),
            shopItem("obsidian", "黑曜石 x4", "OBSIDIAN", "ITEM", "OBSIDIAN", 4, "EMERALD", 4),
            shopItem("tnt", "TNT", "TNT", "ITEM", "TNT", 1, "GOLD_INGOT", 4),
            shopItem("stone-sword", "石剑", "STONE_SWORD", "ITEM", "STONE_SWORD", 1, "IRON_INGOT", 10),
            shopItem("iron-sword", "铁剑", "IRON_SWORD", "ITEM", "IRON_SWORD", 1, "GOLD_INGOT", 7),
            shopItem("diamond-sword", "钻石剑", "DIAMOND_SWORD", "ITEM", "DIAMOND_SWORD", 1, "EMERALD", 4),
            shopItem("knockback-stick", "击退棒", "STICK", "ITEM", "STICK", 1, "GOLD_INGOT", 10),
            shopItem("arrows", "箭 x8", "ARROW", "ITEM", "ARROW", 8, "GOLD_INGOT", 2),
            shopItem("bow", "弓", "BOW", "ITEM", "BOW", 1, "GOLD_INGOT", 12),
            shopItem("power-bow", "力量弓", "BOW", "ITEM", "BOW", 1, "GOLD_INGOT", 24),
            shopItem("punch-bow", "力量击退弓", "BOW", "ITEM", "BOW", 1, "EMERALD", 6),
            shopItem("speed-potion", "速度药水", "POTION", "POTION", "POTION", 1, "EMERALD", 1) +
                mapOf("potion" to "SPEED 45 1", "potion-display" to "speed"),
            shopItem("jump-potion", "跳跃药水", "POTION", "POTION", "POTION", 1, "EMERALD", 1) +
                mapOf("potion" to "JUMP 45 4", "potion-display" to "jump"),
            shopItem("invisibility-potion", "隐身药水", "POTION", "POTION", "POTION", 1, "EMERALD", 2) +
                mapOf("potion" to "INVISIBILITY 30 0", "potion-display" to "invisibility"),
            shopItem("golden-apple", "金苹果", "GOLDEN_APPLE", "ITEM", "GOLDEN_APPLE", 1, "GOLD_INGOT", 3),
            shopItem("bed-bug", "床虫", "SNOWBALL", "SPECIAL", "SNOWBALL", 1, "IRON_INGOT", 40),
            shopItem("dream-defender", "梦境守卫", "IRON_GOLEM_SPAWN_EGG", "SPECIAL", "IRON_GOLEM_SPAWN_EGG", 1, "IRON_INGOT", 120),
            shopItem("fireball", "火球", "FIRE_CHARGE", "SPECIAL", "FIRE_CHARGE", 1, "IRON_INGOT", 40),
            shopItem("ender-pearl", "末影珍珠", "ENDER_PEARL", "ITEM", "ENDER_PEARL", 1, "EMERALD", 4),
            shopItem("water-bucket", "水桶", "WATER_BUCKET", "ITEM", "WATER_BUCKET", 1, "GOLD_INGOT", 4),
            shopItem("bridge-egg", "搭桥蛋", "EGG", "SPECIAL", "EGG", 1, "EMERALD", 3),
            shopItem("magic-milk", "魔法牛奶", "MILK_BUCKET", "SPECIAL", "MILK_BUCKET", 1, "GOLD_INGOT", 4),
            shopItem("sponge", "海绵", "SPONGE", "ITEM", "SPONGE", 1, "GOLD_INGOT", 3),
            shopItem("popup-tower", "袖珍弹出塔", "CHEST", "SPECIAL", "CHEST", 1, "IRON_INGOT", 24),
            shopItem("chain-armor", "锁链护甲", "CHAINMAIL_BOOTS", "ARMOR", "CHAINMAIL_BOOTS", 1, "IRON_INGOT", 40, 1) +
                mapOf("category" to "armor", "weight" to 1),
            shopItem("iron-armor", "铁护甲", "IRON_BOOTS", "ARMOR", "IRON_BOOTS", 1, "GOLD_INGOT", 12, 2) +
                mapOf("category" to "armor", "weight" to 2),
            shopItem("diamond-armor", "钻石护甲", "DIAMOND_BOOTS", "ARMOR", "DIAMOND_BOOTS", 1, "EMERALD", 6, 3) +
                mapOf("category" to "armor", "weight" to 3),
            shopItem("wood-pickaxe", "木镐", "WOODEN_PICKAXE", "PICKAXE", "WOODEN_PICKAXE", 1, "IRON_INGOT", 10, 1) + DOWNGRADABLE,
            shopItem("iron-pickaxe", "铁镐", "IRON_PICKAXE", "PICKAXE", "IRON_PICKAXE", 1, "IRON_INGOT", 10, 2) + DOWNGRADABLE,
            shopItem("gold-pickaxe", "金镐", "GOLDEN_PICKAXE", "PICKAXE", "GOLDEN_PICKAXE", 1, "GOLD_INGOT", 3, 3) + DOWNGRADABLE,
            shopItem("diamond-pickaxe", "钻石镐", "DIAMOND_PICKAXE", "PICKAXE", "DIAMOND_PICKAXE", 1, "GOLD_INGOT", 6, 4) + DOWNGRADABLE,
            shopItem("wood-axe", "木斧", "WOODEN_AXE", "AXE", "WOODEN_AXE", 1, "IRON_INGOT", 10, 1) + DOWNGRADABLE,
            shopItem("iron-axe", "铁斧", "IRON_AXE", "AXE", "IRON_AXE", 1, "IRON_INGOT", 10, 2) + DOWNGRADABLE,
            shopItem("gold-axe", "金斧", "GOLDEN_AXE", "AXE", "GOLDEN_AXE", 1, "GOLD_INGOT", 3, 3) + DOWNGRADABLE,
            shopItem("diamond-axe", "钻石斧", "DIAMOND_AXE", "AXE", "DIAMOND_AXE", 1, "GOLD_INGOT", 6, 4) + DOWNGRADABLE,
            shopItem("shears", "剪刀", "SHEARS", "SHEARS", "SHEARS", 1, "IRON_INGOT", 20, 1),
            shopItem("halloween-pumpkin", "万圣节南瓜 x12", "PUMPKIN", "ITEM", "PUMPKIN", 12, "IRON_INGOT", 4) + mapOf(
                "enabled" to false,
                "item-name" to "Happy Halloween!"
            )
        )

        fun defaultUpgradeItems(): List<Map<String, Any>> = listOf(
            upgradeItem("sharpness-1", "锋利 I", "IRON_SWORD", "SHARPNESS", 4, 1),
            upgradeItem("protection-1", "保护 I", "IRON_CHESTPLATE", "PROTECTION", 2, 1),
            upgradeItem("protection-2", "保护 II", "IRON_CHESTPLATE", "PROTECTION", 4, 2),
            upgradeItem("protection-3", "保护 III", "DIAMOND_CHESTPLATE", "PROTECTION", 8, 3),
            upgradeItem("protection-4", "保护 IV", "DIAMOND_CHESTPLATE", "PROTECTION", 16, 4),
            upgradeItem("haste-1", "急迫 I", "GOLDEN_PICKAXE", "HASTE", 2, 1),
            upgradeItem("haste-2", "急迫 II", "DIAMOND_PICKAXE", "HASTE", 4, 2),
            upgradeItem("forge-1", "铁锻炉", "FURNACE", "FORGE", 2, 1),
            upgradeItem("forge-2", "金锻炉", "FURNACE", "FORGE", 4, 2),
            upgradeItem("forge-3", "绿宝石锻炉", "FURNACE", "FORGE", 6, 3),
            upgradeItem("forge-4", "熔融锻炉", "BLAST_FURNACE", "FORGE", 8, 4),
            upgradeItem("dragon-buff", "龙增益", "DRAGON_EGG", "DRAGON_BUFF", 5, 1),
            upgradeItem("heal-pool", "治愈池", "BEACON", "HEAL_POOL", 1, 1),
            upgradeItem("blindness-trap", "失明陷阱", "TRIPWIRE_HOOK", "TRAP_BLINDNESS", 1, 1),
            upgradeItem("counter-offensive-trap", "反攻陷阱", "FEATHER", "TRAP_COUNTER_OFFENSIVE", 1, 1),
            upgradeItem("alarm-trap", "警报陷阱", "REDSTONE_TORCH", "TRAP_ALARM", 1, 1) + mapOf(
                "custom-announce" to true
            ),
            upgradeItem("miner-fatigue-trap", "挖掘疲劳陷阱", "IRON_PICKAXE", "TRAP_MINER_FATIGUE", 1, 1)
        )

        fun shopItem(
            id: String,
            displayName: String,
            icon: String,
            type: String,
            item: String,
            amount: Int,
            currency: String,
            price: Int,
            tier: Int = 0
        ): Map<String, Any> = linkedMapOf(
            "id" to id,
            "display-name" to displayName,
            "icon" to icon,
            "type" to type,
            "item" to item,
            "amount" to amount,
            "currency" to currency,
            "price" to price,
            "tier" to tier
        )

        fun upgradeItem(
            id: String,
            displayName: String,
            icon: String,
            type: String,
            price: Int,
            tier: Int
        ): Map<String, Any> = linkedMapOf(
            "id" to id,
            "display-name" to displayName,
            "icon" to icon,
            "type" to type,
            "currency" to "DIAMOND",
            "price" to price,
            "tier" to tier
        )
    }

}

/** 读取有限浮点数，非法的 NaN/Infinity 使用调用方默认值。 */
private fun ConfigurationSection.getFiniteDouble(path: String, fallback: Double): Double {
    return getDouble(path, fallback).takeIf(Double::isFinite) ?: fallback
}

private fun ConfigurationSection.getDoubleOrNull(path: String): Double? {
    return if (contains(path)) getDouble(path).takeIf(Double::isFinite) else null
}
private fun ConfigurationSection.getIntOrNull(path: String): Int? = if (contains(path)) getInt(path) else null

/** 读取托管地图中限制在 0 至 32 格的可选保护半径。 */
private fun ConfigurationSection.getProtectionRadiusOrNull(path: String): Double? {
    return if (contains(path)) getDouble(path).takeIf(Double::isFinite)?.coerceIn(0.0, 32.0) else null
}

private fun ConfigurationSection.createSectionReplacing(path: String): ConfigurationSection {
    set(path, null)
    return createSection(path)
}

private fun Any?.toIntValue(fallback: Int): Int = when (this) {
    is Number -> toInt()
    else -> toString().toIntOrNull() ?: fallback
}

/** 将宽松 YAML 值解析为布尔开关。 */
private fun Any?.toBooleanValue(fallback: Boolean): Boolean = when (this) {
    is Boolean -> this
    is Number -> toInt() != 0
    else -> toString().toBooleanStrictOrNull() ?: fallback
}
