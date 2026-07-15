package org.katacr.kagamecenter.bedwars

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.editor.EditorPointCaptureService
import org.katacr.kaGameCenter.editor.MapEditorService
import org.katacr.kaGameCenter.game.ManagedGameCatalogService
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.game.ModuleGameEditor
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.world.TemporaryWorldService

/** 通过 KaMenu 配置 BedWars 托管地图中的队伍、床、商店和资源点。 */
class BedWarsManagedGameEditor(
    private val configService: BedWarsConfigService,
    private val language: ModuleLanguage,
    private val packetService: PacketDispatchService,
    private val worldService: TemporaryWorldService,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val menuService: GameCenterMenuService,
    private val pointCaptureService: EditorPointCaptureService
) : ModuleGameEditor {
    override val moduleId: String = "bedwars"

    /** 初始化 BedWars 托管配置的空队伍和公共生成器节点。 */
    override fun populateDefaults(
        config: YamlConfiguration,
        localId: String,
        displayName: String,
        sharedMapTemplate: String
    ) {
        config.set("selector-group", "default")
        config.set("bedwars.world-border", 300)
        config.set("bedwars.allow-spectate", true)
        config.set("bedwars.allow-map-break", false)
        config.set("bedwars.island-radius", 17)
        config.set("bedwars.disable-generator-for-empty-teams", false)
        config.set("bedwars.disable-npcs-for-empty-teams", true)
        config.set("bedwars.vanilla-death-drops", false)
        config.set("bedwars.use-bed-hologram", true)
        config.set("bedwars.game-end.show-eliminated", true)
        config.set("bedwars.game-end.teleport-eliminated", true)
        config.set("bedwars.item-group", "default")
        config.set("bedwars.game-rules", BEDWARS_DEFAULT_GAME_RULES)
        config.set("bedwars.spawn-protection", 5)
        config.set("bedwars.shop-protection", 1)
        config.set("bedwars.upgrades-protection", 1)
        config.set("bedwars.generator-protection", 1)
        config.createSection("bedwars.teams")
        config.createSection("bedwars.generators")
    }

    /** 构造并打开 BedWars 地图编辑菜单。 */
    override fun openEditor(player: Player, game: ManagedGameConfig) {
        pointCaptureService.cancel(player)
        val configured = configService.readManagedGame(game)
        val moduleConfig = configService.current()
        val blockRules = moduleConfig.blockRules
        val selectedTeam = configured.teams.firstOrNull()
        val menu = YamlConfiguration()
        menu.set("Title", language.getMessage("bedwars.editor_title", game.displayName))
        menu.set("Settings.can_escape", true)
        menu.set("Settings.after_action", "WAIT_FOR_RESPONSE")
        menu.set("Body.summary.type", "message")
        menu.set("Body.summary.width", 420)
        menu.set("Body.summary.text", summary(game, configured))
        input(menu, "team_id", "bedwars.editor_input_team_id", selectedTeam?.id ?: "red", 24)
        input(menu, "team_name", "bedwars.editor_input_team_name", selectedTeam?.displayName ?: "红队", 32)
        input(menu, "team_color", "bedwars.editor_input_team_color", selectedTeam?.color?.name ?: "RED", 16)
        input(menu, "team_max_players", "bedwars.editor_input_team_max_players", selectedTeam?.maxPlayers?.toString() ?: "1", 3)
        input(menu, "item_group", "bedwars.editor_input_item_group", configured.itemGroup, 32)
        input(menu, "selector_group", "bedwars.editor_input_selector_group", game.selectorGroup, 32)
        input(menu, "island_radius", "bedwars.editor_input_island_radius", configured.islandRadius.toString(), 8)
        input(menu, "disable_empty_generators", "bedwars.editor_input_disable_empty_generators", configured.disableEmptyTeamGenerators.toString(), 5)
        input(menu, "disable_empty_npcs", "bedwars.editor_input_disable_empty_npcs", configured.disableEmptyTeamNpcs.toString(), 5)
        input(menu, "vanilla_death_drops", "bedwars.editor_input_vanilla_death_drops", configured.vanillaDeathDrops.toString(), 5)
        input(menu, "use_bed_hologram", "bedwars.editor_input_use_bed_hologram", configured.useBedHologram.toString(), 5)
        input(menu, "world_border", "bedwars.editor_input_world_border", (configured.worldBorderSize ?: moduleConfig.worldBorderSize).toString(), 8)
        input(menu, "allow_spectate", "bedwars.editor_input_allow_spectate", configured.allowSpectate.toString(), 5)
        input(menu, "allow_map_break", "bedwars.editor_input_allow_map_break", configured.allowMapBreak.toString(), 5)
        input(menu, "spawn_protection", "bedwars.editor_input_spawn_protection", (configured.spawnProtectionRadius ?: blockRules.spawnProtectionRadius).toString(), 6)
        input(menu, "shop_protection", "bedwars.editor_input_shop_protection", (configured.shopProtectionRadius ?: blockRules.shopProtectionRadius).toString(), 6)
        input(menu, "upgrades_protection", "bedwars.editor_input_upgrades_protection", (configured.upgradeShopProtectionRadius ?: blockRules.shopProtectionRadius).toString(), 6)
        input(menu, "generator_protection", "bedwars.editor_input_generator_protection", (configured.generatorProtectionRadius ?: blockRules.generatorProtectionRadius).toString(), 6)
        input(menu, "show_eliminated", "bedwars.editor_input_show_eliminated", configured.showEliminatedAtGameEnd.toString(), 5)
        input(menu, "teleport_eliminated", "bedwars.editor_input_teleport_eliminated", configured.teleportEliminatedAtGameEnd.toString(), 5)
        input(menu, "chat_top_statistic", "bedwars.editor_input_chat_top_statistic", (configured.chatTopStatistic ?: moduleConfig.chatTopStatistic).name, 24)
        input(menu, "chat_top_hide_missing", "bedwars.editor_input_chat_top_hide_missing", (configured.chatTopHideMissing ?: moduleConfig.chatTopHideMissing).toString(), 5)
        input(menu, "sidebar_top_statistic", "bedwars.editor_input_sidebar_top_statistic", (configured.sidebarTopStatistic ?: moduleConfig.sidebarTopStatistic).name, 24)
        input(menu, "sidebar_top_hide_missing", "bedwars.editor_input_sidebar_top_hide_missing", (configured.sidebarTopHideMissing ?: moduleConfig.sidebarTopHideMissing).toString(), 5)
        input(menu, "game_rules", "bedwars.editor_input_game_rules", configured.gameRules.joinToString(";"), 512)
        input(menu, "generator_id", "bedwars.editor_input_generator_id", "iron", 24)
        input(menu, "generator_type", "bedwars.editor_input_generator_type", "IRON", 16)
        input(menu, "generator_interval", "bedwars.editor_input_generator_interval", "40", 6)
        menu.set("Bottom.type", "multi")
        menu.set("Bottom.columns", 3)
        button(menu, "open_world", "bedwars.editor_button_open_world", game, "open-world")
        button(menu, "save_world", "bedwars.editor_button_save_world", game, "save-world")
        button(menu, "close_world", "bedwars.editor_button_close_world", game, "close-world")
        button(menu, "set_lobby", "bedwars.editor_button_set_lobby", game, "set-lobby")
        button(menu, "set_spectator", "bedwars.editor_button_set_spectator", game, "set-spectator")
        button(menu, "set_void", "bedwars.editor_button_set_void_y", game, "set-void-y")
        button(menu, "set_build_y", "bedwars.editor_button_set_max_build_y", game, "set-max-build-y")
        button(menu, "save_item_group", "bedwars.editor_button_save_item_group", game, "save-item-group")
        button(menu, "save_selector_group", "bedwars.editor_button_save_selector_group", game, "save-selector-group")
        button(menu, "save_arena_rules", "bedwars.editor_button_save_arena_rules", game, "save-arena-rules")
        button(menu, "save_map_protection", "bedwars.editor_button_save_map_protection", game, "save-map-protection")
        button(menu, "save_game_end_rules", "bedwars.editor_button_save_game_end_rules", game, "save-game-end-rules")
        button(menu, "save_team", "bedwars.editor_button_save_team", game, "save-team")
        button(menu, "remove_team", "bedwars.editor_button_remove_team", game, "remove-team")
        button(menu, "set_spawn", "bedwars.editor_button_set_team_spawn", game, "set-team-spawn")
        button(menu, "set_bed", "bedwars.editor_button_set_team_bed", game, "set-team-bed")
        button(menu, "set_kill_drops", "bedwars.editor_button_set_team_kill_drops", game, "set-team-kill-drops")
        button(menu, "set_shop", "bedwars.editor_button_set_team_shop", game, "set-team-shop")
        button(menu, "set_upgrade", "bedwars.editor_button_set_team_upgrade", game, "set-team-upgrade")
        button(menu, "save_team_gen", "bedwars.editor_button_save_team_generator", game, "save-team-generator")
        button(menu, "remove_team_gen", "bedwars.editor_button_remove_team_generator", game, "remove-team-generator")
        button(menu, "save_gen", "bedwars.editor_button_save_generator", game, "save-generator")
        button(menu, "remove_gen", "bedwars.editor_button_remove_generator", game, "remove-generator")
        button(menu, "validate", "bedwars.editor_button_validate", game, "validate")
        button(menu, "preview", "bedwars.editor_button_preview", game, "preview")
        menu.set("Bottom.exit.text", language.getMessage("menu.button_back"))
        menu.set("Bottom.exit.actions", listOf("kgc:open-admin-managed-games"))
        menuService.openExternalConfig(player, menu, "kagamecenter:bedwars-editor:${game.globalId}")
    }

    /** 执行编辑菜单动作并在修改后刷新菜单。 */
    override fun handleAction(
        player: Player,
        game: ManagedGameConfig,
        action: String,
        variables: Map<String, String>
    ): Boolean {
        when (action.lowercase()) {
            "open-world" -> openWorld(player, game)
            "save-world" -> saveWorld(player, game)
            "close-world" -> closeWorld(player, game)
            "set-lobby" -> {
                startPositionCapture(player, game, "bedwars.editor_field_lobby") { currentGame, location ->
                    configService.saveManagedLobby(currentGame, BedWarsPoint.from(location))
                }
                return true
            }
            "set-spectator" -> {
                startPositionCapture(player, game, "bedwars.editor_field_spectator") { currentGame, location ->
                    configService.saveManagedSpectatorSpawn(currentGame, BedWarsPoint.from(location))
                }
                return true
            }
            "set-void-y" -> {
                startPositionCapture(player, game, "bedwars.editor_field_void_y") { currentGame, location ->
                    configService.saveManagedVoidY(currentGame, location.y)
                }
                return true
            }
            "set-max-build-y" -> {
                startPositionCapture(player, game, "bedwars.editor_field_max_build_y") { currentGame, location ->
                    configService.saveManagedMaxBuildY(currentGame, location.blockY)
                }
                return true
            }
            "save-item-group" -> {
                configService.saveManagedItemGroup(game, variable(variables, "item_group") ?: "default")
                player.sendMessage(Component.text(language.getMessage("bedwars.editor_item_group_saved")))
            }
            "save-selector-group" -> {
                val group = variable(variables, "selector_group")?.lowercase()
                    ?.takeIf { it.length <= 32 && it.all { char -> char.isLetterOrDigit() || char == '_' || char == '-' } }
                if (group == null) {
                    missing(player, "bedwars.editor_selector_group_invalid")
                } else {
                    managedGameCatalog.save(game) { it.set("selector-group", group) }
                    player.sendMessage(Component.text(language.getMessage("bedwars.editor_selector_group_saved", group)))
                }
            }
            "save-arena-rules" -> saveArenaRules(player, game, variables)
            "save-map-protection" -> saveMapProtectionRules(player, game, variables)
            "save-game-end-rules" -> saveGameEndRules(player, game, variables)
            "save-team" -> saveTeam(player, game, variables)
            "remove-team" -> removeTeam(player, game, variables)
            "set-team-spawn" -> {
                startTeamPointCapture(player, game, variables, "spawn", "bedwars.editor_field_team_spawn", block = false)
                return true
            }
            "set-team-bed" -> {
                startTeamPointCapture(player, game, variables, "bed", "bedwars.editor_field_team_bed", block = true)
                return true
            }
            "set-team-kill-drops" -> {
                startTeamPointCapture(player, game, variables, "kill-drops", "bedwars.editor_field_team_kill_drops", block = false)
                return true
            }
            "set-team-shop" -> {
                startTeamPointCapture(player, game, variables, "shop", "bedwars.editor_field_team_shop", block = false)
                return true
            }
            "set-team-upgrade" -> {
                startTeamPointCapture(player, game, variables, "upgrade-shop", "bedwars.editor_field_team_upgrade", block = false)
                return true
            }
            "save-team-generator" -> {
                startGeneratorCapture(player, game, variables, teamScoped = true)
                return true
            }
            "remove-team-generator" -> removeGenerator(player, game, variables, teamScoped = true)
            "save-generator" -> {
                startGeneratorCapture(player, game, variables, teamScoped = false)
                return true
            }
            "remove-generator" -> removeGenerator(player, game, variables, teamScoped = false)
            "validate" -> validate(player, game)
            "preview" -> preview(player, game)
            else -> return false
        }
        openEditor(player, managedGameCatalog.get(game.globalId) ?: game)
        return true
    }

    private fun summary(game: ManagedGameConfig, configured: BedWarsGameConfig): List<String> {
        val moduleConfig = configService.current()
        val blockRules = moduleConfig.blockRules
        val teamDetails = configured.teams.joinToString(" | ") {
            language.getMessage(
                "bedwars.editor_summary_team",
                it.id,
                it.color.name,
                it.maxPlayers,
                status(it.spawn != null && it.bed != null && it.shop != null && it.upgradeShop != null),
                it.generators.size
            )
        }.ifBlank { language.getMessage("bedwars.editor_none") }
        return listOf(
            language.getMessage("bedwars.editor_summary_name", game.displayName),
            language.getMessage("bedwars.editor_summary_shared_map", game.sharedMapTemplate),
            language.getMessage("bedwars.editor_summary_runtime_map", game.effectiveMapTemplate()),
            language.getMessage("bedwars.editor_summary_private_map", status(game.hasPrivateSnapshot())),
            language.getMessage("bedwars.editor_summary_lobby", status(configured.lobby != null)),
            language.getMessage("bedwars.editor_summary_spectator", status(configured.spectatorSpawn != null)),
            language.getMessage("bedwars.editor_summary_void_y", configured.voidY ?: configService.current().defaultVoidY),
            language.getMessage("bedwars.editor_summary_max_build_y", configured.maxBuildY ?: "-"),
            language.getMessage("bedwars.editor_summary_item_group", configured.itemGroup),
            language.getMessage("bedwars.editor_summary_selector_group", game.selectorGroup),
            language.getMessage(
                "bedwars.editor_summary_arena_rules",
                configured.islandRadius,
                configured.disableEmptyTeamGenerators,
                configured.disableEmptyTeamNpcs,
                configured.vanillaDeathDrops,
                configured.useBedHologram
            ),
            language.getMessage(
                "bedwars.editor_summary_map_protection",
                configured.worldBorderSize ?: moduleConfig.worldBorderSize,
                configured.allowSpectate,
                configured.allowMapBreak,
                configured.spawnProtectionRadius ?: blockRules.spawnProtectionRadius,
                configured.shopProtectionRadius ?: blockRules.shopProtectionRadius,
                configured.upgradeShopProtectionRadius ?: blockRules.shopProtectionRadius,
                configured.generatorProtectionRadius ?: blockRules.generatorProtectionRadius
            ),
            language.getMessage(
                "bedwars.editor_summary_game_end_rules",
                configured.showEliminatedAtGameEnd,
                configured.teleportEliminatedAtGameEnd,
                configured.gameRules.size,
                configured.gameRules.joinToString(";").ifBlank { "-" }
            ),
            language.getMessage(
                "bedwars.editor_summary_result_leaders",
                (configured.chatTopStatistic ?: moduleConfig.chatTopStatistic).name,
                configured.chatTopHideMissing ?: moduleConfig.chatTopHideMissing,
                (configured.sidebarTopStatistic ?: moduleConfig.sidebarTopStatistic).name,
                configured.sidebarTopHideMissing ?: moduleConfig.sidebarTopHideMissing
            ),
            language.getMessage("bedwars.editor_summary_teams", configured.teams.size, teamDetails),
            language.getMessage("bedwars.editor_summary_generators", configured.generators.size),
            language.getMessage("bedwars.editor_summary_validation", configured.validationErrors().size)
        )
    }

    private fun openWorld(player: Player, game: ManagedGameConfig) {
        if (!ensurePrivateSnapshot(game)) {
            fail(player, "bedwars.editor_private_snapshot_failed")
            return
        }
        val configured = configService.readManagedGame(game)
        val world = mapEditorService.openEditorDirectory(player, game.globalId, game.runtimeMapFolder) { editWorld ->
            configured.lobby?.toLocation(editWorld)
                ?: configured.teams.firstOrNull()?.spawn?.toLocation(editWorld)
                ?: editWorld.spawnLocation
        }
        if (world == null) {
            fail(player, "bedwars.editor_open_failed")
            return
        }
        player.sendMessage(Component.text(language.getMessage("bedwars.editor_opened", world.name)))
    }

    private fun ensurePrivateSnapshot(game: ManagedGameConfig): Boolean {
        if (game.hasPrivateSnapshot()) return true
        if (!worldService.snapshotTemplateToDirectory(game.sharedMapTemplate, game.runtimeMapFolder)) return false
        managedGameCatalog.save(game) {
            it.set("runtime-map-template", "modules/${game.moduleId}/games/map/${game.localId}")
        }
        return true
    }

    private fun saveWorld(player: Player, game: ManagedGameConfig) {
        if (!mapEditorService.saveIfEditing(game.globalId)) {
            fail(player, "bedwars.editor_save_failed")
            return
        }
        player.sendMessage(Component.text(language.getMessage("bedwars.editor_saved")))
    }

    private fun closeWorld(player: Player, game: ManagedGameConfig) {
        if (!mapEditorService.closeSession(game.globalId, save = true, restoreEditors = true)) {
            fail(player, "bedwars.editor_close_failed")
            return
        }
        player.sendMessage(Component.text(language.getMessage("bedwars.editor_closed")))
    }

    private fun saveTeam(player: Player, game: ManagedGameConfig, variables: Map<String, String>) {
        val rawId = variable(variables, "team_id") ?: return missing(player, "bedwars.editor_team_id_missing")
        val colorName = variable(variables, "team_color") ?: "WHITE"
        val color = BedWarsTeamColor.entries.firstOrNull { it.name.equals(colorName, ignoreCase = true) }
            ?: return missing(player, "bedwars.editor_color_invalid")
        val maxPlayers = variable(variables, "team_max_players")?.toIntOrNull()?.coerceIn(1, 100)
            ?: return missing(player, "bedwars.editor_number_invalid")
        val displayName = variable(variables, "team_name") ?: rawId
        val id = configService.upsertManagedTeam(game, rawId, displayName, color, maxPlayers)
            ?: return missing(player, "bedwars.editor_id_invalid")
        changed(player, game, "bedwars.editor_team_saved", id)
    }

    /** 校验并保存托管地图的五项参考竞技场规则。 */
    private fun saveArenaRules(player: Player, game: ManagedGameConfig, variables: Map<String, String>) {
        val islandRadius = variable(variables, "island_radius")?.toDoubleOrNull()
            ?.takeIf { it in 1.0..128.0 }
            ?: return missing(player, "bedwars.editor_rule_number_invalid")
        val parseBoolean: (String) -> Boolean? = { key ->
            when (variable(variables, key)?.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        val disableGenerators = parseBoolean("disable_empty_generators")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        val disableNpcs = parseBoolean("disable_empty_npcs")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        val vanillaDrops = parseBoolean("vanilla_death_drops")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        val bedHologram = parseBoolean("use_bed_hologram")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        configService.saveManagedArenaRules(
            game,
            islandRadius,
            disableGenerators,
            disableNpcs,
            vanillaDrops,
            bedHologram
        )
        player.sendMessage(Component.text(language.getMessage("bedwars.editor_arena_rules_saved")))
    }

    /** 校验并保存世界边界、访问开关和四类关键点保护半径。 */
    private fun saveMapProtectionRules(player: Player, game: ManagedGameConfig, variables: Map<String, String>) {
        val worldBorderSize = variable(variables, "world_border")?.toIntOrNull()
            ?.takeIf { it in 0..60_000_000 }
            ?: return missing(player, "bedwars.editor_world_border_invalid")
        val parseBoolean: (String) -> Boolean? = { key ->
            when (variable(variables, key)?.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        val allowSpectate = parseBoolean("allow_spectate")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        val allowMapBreak = parseBoolean("allow_map_break")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        val parseRadius: (String) -> Double? = { key ->
            variable(variables, key)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..32.0 }
        }
        val spawnProtectionRadius = parseRadius("spawn_protection")
            ?: return missing(player, "bedwars.editor_protection_radius_invalid")
        val shopProtectionRadius = parseRadius("shop_protection")
            ?: return missing(player, "bedwars.editor_protection_radius_invalid")
        val upgradeShopProtectionRadius = parseRadius("upgrades_protection")
            ?: return missing(player, "bedwars.editor_protection_radius_invalid")
        val generatorProtectionRadius = parseRadius("generator_protection")
            ?: return missing(player, "bedwars.editor_protection_radius_invalid")
        configService.saveManagedMapProtectionRules(
            game,
            worldBorderSize,
            allowSpectate,
            allowMapBreak,
            spawnProtectionRadius,
            shopProtectionRadius,
            upgradeShopProtectionRadius,
            generatorProtectionRadius
        )
        player.sendMessage(Component.text(language.getMessage("bedwars.editor_map_protection_saved")))
    }

    /** 校验并保存结算显示开关和分号分隔的原版游戏规则。 */
    private fun saveGameEndRules(player: Player, game: ManagedGameConfig, variables: Map<String, String>) {
        val parseBoolean: (String) -> Boolean? = { key ->
            when (variable(variables, key)?.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        val showEliminated = parseBoolean("show_eliminated")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        val teleportEliminated = parseBoolean("teleport_eliminated")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        val parseStatistic: (String) -> BedWarsResultStatistic? = { key ->
            val value = variable(variables, key)?.replace('-', '_')
            BedWarsResultStatistic.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
        val chatTopStatistic = parseStatistic("chat_top_statistic")
            ?: return missing(player, "bedwars.editor_result_statistic_invalid")
        val chatTopHideMissing = parseBoolean("chat_top_hide_missing")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        val sidebarTopStatistic = parseStatistic("sidebar_top_statistic")
            ?: return missing(player, "bedwars.editor_result_statistic_invalid")
        val sidebarTopHideMissing = parseBoolean("sidebar_top_hide_missing")
            ?: return missing(player, "bedwars.editor_boolean_invalid")
        val rawGameRules = variables["game_rules"]?.trim()
            ?: return missing(player, "bedwars.editor_game_rule_invalid")
        val gameRules = parseGameRules(rawGameRules)
            ?: return missing(player, "bedwars.editor_game_rule_invalid")
        configService.saveManagedGameEndRules(
            game,
            showEliminated,
            teleportEliminated,
            chatTopStatistic,
            chatTopHideMissing,
            sidebarTopStatistic,
            sidebarTopHideMissing,
            gameRules
        )
        player.sendMessage(Component.text(language.getMessage("bedwars.editor_game_end_rules_saved")))
    }

    /** 将分号分隔输入规范化为已验证名称和值类型的 Bukkit 游戏规则。 */
    @Suppress("DEPRECATION")
    private fun parseGameRules(raw: String): List<String>? {
        if (raw.isBlank()) return emptyList()
        val parsed = mutableListOf<String>()
        raw.split(';').forEach { rawEntry ->
            val entry = rawEntry.trim()
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator == entry.lastIndex) return null
            val name = entry.substring(0, separator).trim()
            val value = entry.substring(separator + 1).trim()
            val rule = GameRule.values().firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return null
            val normalizedValue = when (rule.type) {
                Boolean::class.javaObjectType -> when (value.lowercase()) {
                    "true" -> "true"
                    "false" -> "false"
                    else -> return null
                }
                Int::class.javaObjectType -> value.toIntOrNull()?.toString() ?: return null
                else -> return null
            }
            parsed += "${rule.name}:$normalizedValue"
        }
        return parsed
    }

    private fun removeTeam(player: Player, game: ManagedGameConfig, variables: Map<String, String>) {
        val id = variable(variables, "team_id") ?: return missing(player, "bedwars.editor_team_id_missing")
        val removed = configService.removeManagedTeam(game, id)
        player.sendMessage(Component.text(language.getMessage(
            if (removed) "bedwars.editor_team_removed" else "bedwars.editor_team_missing",
            id
        )))
    }

    /** 启动骨头右键位置采集，并持续覆盖指定单值坐标字段。 */
    private fun startPositionCapture(
        player: Player,
        game: ManagedGameConfig,
        fieldKey: String,
        save: (ManagedGameConfig, Location) -> Unit
    ) {
        if (activeEditedGame(player, game.globalId) == null) return
        pointCaptureService.beginPositionCapture(player, moduleId) { capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId) ?: return@beginPositionCapture false
            save(currentGame, location)
            capturePlayer.sendMessage(Component.text(language.getMessage("bedwars.editor_saved_field", language.getMessage(fieldKey))))
            true
        }
    }

    /** 按队伍字段类型启动骨头位置或方块采集，并保留 Dialog 中选择的队伍。 */
    private fun startTeamPointCapture(
        player: Player,
        game: ManagedGameConfig,
        variables: Map<String, String>,
        field: String,
        fieldKey: String,
        block: Boolean
    ) {
        val teamId = variable(variables, "team_id") ?: return missing(player, "bedwars.editor_team_id_missing")
        if (configService.readManagedGame(game).teams.none { it.id.equals(teamId, ignoreCase = true) }) {
            fail(player, "bedwars.editor_team_missing", teamId)
            return
        }
        if (activeEditedGame(player, game.globalId) == null) return
        val handler: (Player, Location) -> Boolean = handler@{ capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId) ?: return@handler false
            if (!configService.saveManagedTeamPoint(currentGame, teamId, field, BedWarsPoint.from(location))) {
                fail(capturePlayer, "bedwars.editor_team_missing", teamId)
                return@handler false
            }
            capturePlayer.sendMessage(Component.text(language.getMessage("bedwars.editor_saved_field", language.getMessage(fieldKey))))
            true
        }
        if (block) pointCaptureService.beginBlockCapture(player, moduleId, handler)
        else pointCaptureService.beginPositionCapture(player, moduleId, handler)
    }

    /** 启动骨头右键生成器位置采集，并保留 Dialog 中的队伍、类型和刷新间隔。 */
    private fun startGeneratorCapture(
        player: Player,
        game: ManagedGameConfig,
        variables: Map<String, String>,
        teamScoped: Boolean
    ) {
        val generatorId = variable(variables, "generator_id")
            ?: return missing(player, "bedwars.editor_generator_id_missing")
        val typeName = variable(variables, "generator_type")
            ?: return missing(player, "bedwars.editor_generator_type_missing")
        val type = BedWarsGeneratorType.parse(typeName)
            ?: return missing(player, "bedwars.editor_generator_type_invalid")
        val interval = variable(variables, "generator_interval")?.toIntOrNull()?.coerceIn(1, 72_000)
            ?: return missing(player, "bedwars.editor_number_invalid")
        val teamId = if (teamScoped) variable(variables, "team_id")
            ?: return missing(player, "bedwars.editor_team_id_missing") else null
        if (teamId != null && configService.readManagedGame(game).teams.none { it.id.equals(teamId, ignoreCase = true) }) {
            return missing(player, "bedwars.editor_parent_missing")
        }
        if (activeEditedGame(player, game.globalId) == null) return
        pointCaptureService.beginPositionCapture(player, moduleId) { capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId) ?: return@beginPositionCapture false
            val point = BedWarsPoint.from(location)
            val savedId = if (teamId != null) {
                configService.upsertManagedTeamGenerator(currentGame, teamId, generatorId, type, point, interval)
            } else {
                configService.upsertManagedGenerator(currentGame, generatorId, type, point, interval)
            }
            if (savedId == null) {
                missing(capturePlayer, "bedwars.editor_parent_missing")
                return@beginPositionCapture false
            }
            capturePlayer.sendMessage(Component.text(language.getMessage(
                if (teamScoped) "bedwars.editor_team_generator_saved" else "bedwars.editor_generator_saved",
                savedId
            )))
            true
        }
    }

    private fun removeGenerator(
        player: Player,
        game: ManagedGameConfig,
        variables: Map<String, String>,
        teamScoped: Boolean
    ) {
        val generatorId = variable(variables, "generator_id")
            ?: return missing(player, "bedwars.editor_generator_id_missing")
        val removed = if (teamScoped) {
            val teamId = variable(variables, "team_id")
                ?: return missing(player, "bedwars.editor_team_id_missing")
            configService.removeManagedTeamGenerator(game, teamId, generatorId)
        } else {
            configService.removeManagedGenerator(game, generatorId)
        }
        player.sendMessage(Component.text(language.getMessage(
            when {
                !removed -> "bedwars.editor_generator_missing"
                teamScoped -> "bedwars.editor_team_generator_removed"
                else -> "bedwars.editor_generator_removed"
            },
            generatorId
        )))
    }

    /** 确认玩家仍在目标托管游戏的编辑世界，并读取最新配置实例。 */
    private fun activeEditedGame(player: Player, globalId: String): ManagedGameConfig? {
        if (mapEditorService.currentSessionId(player) != globalId) {
            player.sendMessage(Component.text(language.getMessage("bedwars.editor_capture_wrong_session")))
            return null
        }
        return managedGameCatalog.get(globalId).also { current ->
            if (current == null) player.sendMessage(Component.text(language.getMessage("bedwars.editor_capture_wrong_session")))
        }
    }

    private fun changed(player: Player, game: ManagedGameConfig, key: String, id: String) {
        mapEditorService.saveIfEditing(game.globalId)
        player.sendMessage(Component.text(language.getMessage(key, id)))
    }

    private fun validate(player: Player, game: ManagedGameConfig) {
        val errors = configService.readManagedGame(game).validationErrors()
        val key = if (errors.isEmpty()) "bedwars.editor_validation_ok" else "bedwars.editor_validation_failed"
        val details = errors.joinToString(", ").ifBlank { "-" }
        player.sendMessage(Component.text(language.getMessage(key, details)))
    }

    private fun preview(player: Player, game: ManagedGameConfig) {
        val configured = configService.readManagedGame(game)
        listOfNotNull(configured.lobby, configured.spectatorSpawn).forEach {
            packetService.showBeaconBeam(player, it.toLocation(player.world), NamedTextColor.AQUA, 10)
        }
        configured.teams.forEach { team ->
            listOfNotNull(team.spawn, team.killDrops, team.shop, team.upgradeShop).forEach {
                packetService.showBeaconBeam(player, it.toLocation(player.world), team.color.textColor, 10)
            }
            team.bed?.toLocation(player.world)?.let {
                packetService.showBlockGlow(player, it, 10, team.color.textColor)
            }
            team.generators.forEach {
                packetService.showBeaconBeam(player, it.point.toLocation(player.world), generatorColor(it.type), 10)
            }
        }
        configured.generators.forEach {
            packetService.showBeaconBeam(player, it.point.toLocation(player.world), generatorColor(it.type), 10)
        }
        player.sendMessage(Component.text(language.getMessage("bedwars.editor_previewed")))
    }

    private fun generatorColor(type: BedWarsGeneratorType): NamedTextColor = when (type) {
        BedWarsGeneratorType.IRON -> NamedTextColor.WHITE
        BedWarsGeneratorType.GOLD -> NamedTextColor.GOLD
        BedWarsGeneratorType.DIAMOND -> NamedTextColor.AQUA
        BedWarsGeneratorType.EMERALD -> NamedTextColor.GREEN
    }

    private fun input(menu: YamlConfiguration, id: String, textKey: String, default: String, maxLength: Int) {
        menu.set("Inputs.$id.type", "input")
        menu.set("Inputs.$id.text", language.getMessage(textKey))
        menu.set("Inputs.$id.default", default)
        menu.set("Inputs.$id.max_length", maxLength)
    }

    private fun button(menu: YamlConfiguration, id: String, textKey: String, game: ManagedGameConfig, action: String) {
        menu.set("Bottom.buttons.$id.text", language.getMessage(textKey))
        menu.set("Bottom.buttons.$id.actions", listOf("kgc:module-game-action ${game.globalId} $action"))
    }

    private fun variable(variables: Map<String, String>, key: String): String? {
        return variables[key]?.trim()?.takeIf(String::isNotBlank)
    }

    private fun status(value: Boolean): String {
        return language.getMessage(if (value) "bedwars.editor_status_set" else "bedwars.editor_status_missing")
    }

    private fun missing(player: Player, key: String) {
        player.sendMessage(Component.text(language.getMessage(key)))
    }

    private fun fail(player: Player, key: String, vararg args: Any): Boolean {
        player.sendMessage(Component.text(language.getMessage(key, *args)))
        return false
    }
}
