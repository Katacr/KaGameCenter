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
import org.katacr.kaGameCenter.menu.chest.ChestMenuService
import org.katacr.kaGameCenter.packet.PacketDispatchService
import org.katacr.kaGameCenter.world.TemporaryWorldService

/** 通过多级内置箱子菜单配置 BedWars 托管地图中的规则、队伍和资源点。 */
class BedWarsManagedGameEditor(
    private val configService: BedWarsConfigService,
    private val language: ModuleLanguage,
    private val packetService: PacketDispatchService,
    private val worldService: TemporaryWorldService,
    private val mapEditorService: MapEditorService,
    private val managedGameCatalog: ManagedGameCatalogService,
    private val menuService: GameCenterMenuService,
    private val chestMenuService: ChestMenuService,
    private val pointCaptureService: EditorPointCaptureService
) : ModuleGameEditor {
    override val moduleId: String = "bedwars"
    private val menuFactory = BedWarsEditorMenuFactory(language)

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

    /** 打开 BedWars 多级箱子编辑器首页。 */
    override fun openEditor(player: Player, game: ManagedGameConfig) {
        pointCaptureService.cancel(player)
        val configured = configService.readManagedGame(game)
        openChest(player, game, "main", menuFactory.main(game, configured))
    }

    /** 执行编辑菜单动作并在修改后刷新菜单。 */
    override fun handleAction(
        player: Player,
        game: ManagedGameConfig,
        action: String,
        variables: Map<String, String>
    ): Boolean {
        val parts = action.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val command = parts.firstOrNull()?.lowercase() ?: return false
        when (command) {
            "open-section" -> {
                openSection(player, game, parts.getOrNull(1) ?: "main")
                return true
            }
            "open-teams-page" -> {
                openTeams(player, game, parts.getOrNull(1)?.toIntOrNull() ?: 0)
                return true
            }
            "open-generators-page" -> {
                openGenerators(player, game, parts.getOrNull(1)?.toIntOrNull() ?: 0)
                return true
            }
            "open-team" -> {
                openTeam(player, game, parts.getOrNull(1))
                return true
            }
            "open-generator" -> {
                openGenerator(player, game, null, parts.getOrNull(1))
                return true
            }
            "open-team-generator" -> {
                openGenerator(player, game, parts.getOrNull(1), parts.getOrNull(2))
                return true
            }
            "open-form" -> {
                openForm(player, game, parts.drop(1))
                return true
            }
            "toggle-arena" -> {
                toggleArenaRule(game, parts.getOrNull(1))
                openSection(player, game, "arena")
                return true
            }
            "adjust-arena" -> {
                adjustArenaRule(game, parts.getOrNull(1), parts.getOrNull(2)?.toDoubleOrNull())
                openSection(player, game, "arena")
                return true
            }
            "toggle-protection" -> {
                toggleProtectionRule(game, parts.getOrNull(1))
                openSection(player, game, "protection")
                return true
            }
            "adjust-protection" -> {
                adjustProtectionRule(game, parts.getOrNull(1), parts.getOrNull(2)?.toDoubleOrNull())
                openSection(player, game, "protection")
                return true
            }
            "toggle-game-end" -> {
                toggleGameEndRule(game, parts.getOrNull(1))
                openSection(player, game, "game-end")
                return true
            }
            "game-end-stat" -> {
                cycleGameEndStatistic(game, parts.getOrNull(1), parts.getOrNull(2)?.toIntOrNull() ?: 1)
                openSection(player, game, "game-end")
                return true
            }
            "generator-type" -> {
                cycleGeneratorType(game, parts.getOrNull(1), parts.getOrNull(2), parts.getOrNull(3)?.toIntOrNull() ?: 1)
                reopenGenerator(player, game, parts.getOrNull(1), parts.getOrNull(2))
                return true
            }
            "adjust-generator-interval" -> {
                adjustGeneratorInterval(game, parts.getOrNull(1), parts.getOrNull(2), parts.getOrNull(3)?.toIntOrNull())
                reopenGenerator(player, game, parts.getOrNull(1), parts.getOrNull(2))
                return true
            }
            "set-generator-position" -> {
                val scope = parts.getOrNull(1)
                val generatorId = parts.getOrNull(2)
                startExistingGeneratorCapture(player, game, scope, generatorId)
                return true
            }
            "start-generator-batch" -> {
                val type = BedWarsGeneratorType.parse(parts.getOrNull(1))
                    ?: return fail(player, "bedwars.editor_generator_type_invalid")
                startPublicGeneratorBatchCapture(player, game, type)
                return true
            }
            "start-team-wizard" -> {
                startTeamCaptureWizard(player, game, parts.getOrNull(1))
                return true
            }
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
            "save-groups" -> saveGroups(player, game, variables)
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
            "remove-team" -> removeTeam(player, game, variables + mapOf("team_id" to (parts.getOrNull(1) ?: "")))
            "set-team-spawn" -> {
                startTeamPointCapture(player, game, variables + actionVariable(parts, 1, "team_id"), "spawn", "bedwars.editor_field_team_spawn", block = false)
                return true
            }
            "set-team-bed" -> {
                startTeamPointCapture(player, game, variables + actionVariable(parts, 1, "team_id"), "bed", "bedwars.editor_field_team_bed", block = true)
                return true
            }
            "set-team-kill-drops" -> {
                startTeamPointCapture(player, game, variables + actionVariable(parts, 1, "team_id"), "kill-drops", "bedwars.editor_field_team_kill_drops", block = false)
                return true
            }
            "set-team-shop" -> {
                startTeamPointCapture(
                    player,
                    game,
                    variables + actionVariable(parts, 1, "team_id"),
                    "shop",
                    "bedwars.editor_field_team_shop",
                    block = false,
                    precise = true
                )
                return true
            }
            "set-team-upgrade" -> {
                startTeamPointCapture(
                    player,
                    game,
                    variables + actionVariable(parts, 1, "team_id"),
                    "upgrade-shop",
                    "bedwars.editor_field_team_upgrade",
                    block = false,
                    precise = true
                )
                return true
            }
            "save-team-generator" -> {
                startGeneratorCapture(player, game, variables, teamScoped = true)
                return true
            }
            "remove-team-generator" -> removeGenerator(player, game, variables + mapOf(
                "team_id" to (parts.getOrNull(1) ?: ""),
                "generator_id" to (parts.getOrNull(2) ?: "")
            ), teamScoped = true)
            "save-generator" -> {
                startGeneratorCapture(player, game, variables, teamScoped = false)
                return true
            }
            "remove-generator" -> removeGenerator(player, game, variables + actionVariable(parts, 1, "generator_id"), teamScoped = false)
            "save-game-rules" -> saveGameRules(player, game, variables)
            "validate" -> validate(player, game)
            "preview" -> preview(player, game)
            else -> return false
        }
        openEditor(player, managedGameCatalog.get(game.globalId) ?: game)
        return true
    }

    /** 使用内置箱子菜单服务打开一个程序化生成的 BedWars 编辑页。 */
    private fun openChest(player: Player, game: ManagedGameConfig, section: String, menu: YamlConfiguration) {
        chestMenuService.openConfig(
            player,
            menu,
            "kagamecenter:bedwars-editor:${game.globalId}:$section",
            mapOf("game.id" to game.globalId, "game.section" to section)
        )
    }

    /** 按分类名称读取最新配置并打开对应箱子菜单。 */
    private fun openSection(player: Player, game: ManagedGameConfig, section: String) {
        val currentGame = managedGameCatalog.get(game.globalId) ?: game
        val configured = configService.readManagedGame(currentGame)
        val menu = when (section.lowercase()) {
            "overview" -> menuFactory.overview(currentGame, summary(currentGame, configured))
            "world" -> menuFactory.world(currentGame, configured)
            "arena" -> menuFactory.arena(currentGame, configured)
            "protection" -> menuFactory.protection(currentGame, configured, configService.current())
            "game-end" -> menuFactory.gameEnd(currentGame, configured, configService.current())
            else -> menuFactory.main(currentGame, configured)
        }
        openChest(player, currentGame, section, menu)
    }

    /** 打开指定页码的队伍列表。 */
    private fun openTeams(player: Player, game: ManagedGameConfig, page: Int) {
        val currentGame = managedGameCatalog.get(game.globalId) ?: game
        val configured = configService.readManagedGame(currentGame)
        openChest(player, currentGame, "teams:$page", menuFactory.teams(currentGame, configured, page))
    }

    /** 打开指定队伍详情；队伍不存在时返回列表并提示。 */
    private fun openTeam(player: Player, game: ManagedGameConfig, teamId: String?) {
        val currentGame = managedGameCatalog.get(game.globalId) ?: game
        val team = configService.readManagedGame(currentGame).teams.firstOrNull { it.id.equals(teamId, true) }
        if (team == null) {
            fail(player, "bedwars.editor_team_missing", teamId ?: "-")
            openTeams(player, currentGame, 0)
            return
        }
        openChest(player, currentGame, "team:${team.id}", menuFactory.team(currentGame, team))
    }

    /** 打开指定页码的公共生成器列表。 */
    private fun openGenerators(player: Player, game: ManagedGameConfig, page: Int) {
        val currentGame = managedGameCatalog.get(game.globalId) ?: game
        val configured = configService.readManagedGame(currentGame)
        openChest(player, currentGame, "generators:$page", menuFactory.generators(currentGame, configured, page))
    }

    /** 打开公共或队伍生成器详情；目标不存在时返回所属列表。 */
    private fun openGenerator(player: Player, game: ManagedGameConfig, teamId: String?, generatorId: String?) {
        val currentGame = managedGameCatalog.get(game.globalId) ?: game
        val configured = configService.readManagedGame(currentGame)
        val generator = if (teamId == null) {
            configured.generators.firstOrNull { it.id.equals(generatorId, true) }
        } else {
            configured.teams.firstOrNull { it.id.equals(teamId, true) }
                ?.generators?.firstOrNull { it.id.equals(generatorId, true) }
        }
        if (generator == null) {
            fail(player, "bedwars.editor_generator_missing", generatorId ?: "-")
            if (teamId == null) openGenerators(player, currentGame, 0) else openTeam(player, currentGame, teamId)
            return
        }
        openChest(player, currentGame, "generator:${teamId ?: "public"}:${generator.id}", menuFactory.generator(currentGame, generator, teamId))
    }

    /** 根据动作中的 public 或队伍作用域刷新生成器详情。 */
    private fun reopenGenerator(player: Player, game: ManagedGameConfig, scope: String?, generatorId: String?) {
        openGenerator(player, game, scope?.takeUnless { it.equals("public", true) }, generatorId)
    }

    /** 打开只包含当前分类复杂文本字段的小型专项表单。 */
    private fun openForm(player: Player, game: ManagedGameConfig, arguments: List<String>) {
        val form = arguments.firstOrNull()?.lowercase() ?: return
        val currentGame = managedGameCatalog.get(game.globalId) ?: game
        val configured = configService.readManagedGame(currentGame)
        val menu = dialog(language.getMessage("bedwars.editor_form_title", language.getMessage("bedwars.editor_form_$form")))
        when (form) {
            "groups" -> {
                input(menu, "item_group", "bedwars.editor_input_item_group", configured.itemGroup, 32)
                input(menu, "selector_group", "bedwars.editor_input_selector_group", currentGame.selectorGroup, 32)
                formButton(menu, "save", "bedwars.editor_form_save", currentGame, "save-groups")
            }
            "team" -> {
                val selected = configured.teams.firstOrNull { it.id.equals(arguments.getOrNull(1), true) }
                input(menu, "team_id", "bedwars.editor_input_team_id", selected?.id ?: nextTeamId(configured), 24)
                input(menu, "team_name", "bedwars.editor_input_team_name", selected?.displayName ?: language.getMessage("bedwars.editor_team_default_name"), 32)
                input(menu, "team_color", "bedwars.editor_input_team_color", selected?.color?.name ?: nextTeamColor(configured).name, 16)
                input(menu, "team_max_players", "bedwars.editor_input_team_max_players", selected?.maxPlayers?.toString() ?: "1", 3)
                formButton(menu, "save", "bedwars.editor_form_save", currentGame, "save-team")
            }
            "generator", "team-generator" -> {
                val teamId = arguments.getOrNull(1)
                if (form == "team-generator") input(menu, "team_id", "bedwars.editor_input_team_id", teamId.orEmpty(), 24)
                input(menu, "generator_id", "bedwars.editor_input_generator_id", nextGeneratorId(configured, teamId), 24)
                input(menu, "generator_type", "bedwars.editor_input_generator_type", if (teamId == null) "DIAMOND" else "IRON", 16)
                input(menu, "generator_interval", "bedwars.editor_input_generator_interval", "40", 6)
                formButton(menu, "save", "bedwars.editor_form_capture", currentGame, if (teamId == null) "save-generator" else "save-team-generator")
            }
            "game-rules" -> {
                input(menu, "game_rules", "bedwars.editor_input_game_rules", configured.gameRules.joinToString(";"), 512)
                formButton(menu, "save", "bedwars.editor_form_save", currentGame, "save-game-rules")
            }
            else -> return
        }
        menu.set("Bottom.exit.text", language.getMessage("bedwars.editor_menu_back"))
        menu.set("Bottom.exit.actions", listOf("kgc:module-game-action ${currentGame.globalId} open-section main"))
        menuService.openExternalConfig(player, menu, "kagamecenter:bedwars-editor-form:${currentGame.globalId}:$form")
    }

    /** 构造专项输入表单的公共 Dialog 元数据。 */
    private fun dialog(title: String): YamlConfiguration {
        val menu = YamlConfiguration()
        menu.set("Title", title)
        menu.set("Settings.can_escape", true)
        menu.set("Settings.after_action", "WAIT_FOR_RESPONSE")
        menu.set("Body.help.type", "message")
        menu.set("Body.help.width", 360)
        menu.set("Body.help.text", listOf(language.getMessage("bedwars.editor_form_help")))
        menu.set("Bottom.type", "multi")
        menu.set("Bottom.columns", 1)
        return menu
    }

    /** 向专项表单添加保存或开始采集按钮。 */
    private fun formButton(menu: YamlConfiguration, id: String, textKey: String, game: ManagedGameConfig, action: String) {
        menu.set("Bottom.buttons.$id.text", language.getMessage(textKey))
        menu.set("Bottom.buttons.$id.actions", listOf("kgc:module-game-action ${game.globalId} $action"))
    }

    /** 同时保存物品组和选择器分组字段。 */
    private fun saveGroups(player: Player, game: ManagedGameConfig, variables: Map<String, String>) {
        val itemGroup = variable(variables, "item_group") ?: "default"
        val selectorGroup = variable(variables, "selector_group")?.lowercase()
            ?.takeIf { it.length <= 32 && it.all { char -> char.isLetterOrDigit() || char == '_' || char == '-' } }
            ?: return missing(player, "bedwars.editor_selector_group_invalid")
        configService.saveManagedItemGroup(game, itemGroup)
        managedGameCatalog.save(game) { it.set("selector-group", selectorGroup) }
        player.sendMessage(Component.text(language.getMessage("bedwars.editor_groups_saved")))
    }

    /** 切换一项基础竞技场布尔规则并保存整组当前值。 */
    private fun toggleArenaRule(game: ManagedGameConfig, field: String?) {
        val configured = configService.readManagedGame(game)
        configService.saveManagedArenaRules(
            game,
            configured.islandRadius,
            if (field == "disable-generators") !configured.disableEmptyTeamGenerators else configured.disableEmptyTeamGenerators,
            if (field == "disable-npcs") !configured.disableEmptyTeamNpcs else configured.disableEmptyTeamNpcs,
            if (field == "vanilla-drops") !configured.vanillaDeathDrops else configured.vanillaDeathDrops,
            if (field == "bed-hologram") !configured.useBedHologram else configured.useBedHologram
        )
    }

    /** 按给定步长调整岛屿半径并保存。 */
    private fun adjustArenaRule(game: ManagedGameConfig, field: String?, delta: Double?) {
        if (field != "island-radius" || delta == null) return
        val configured = configService.readManagedGame(game)
        configService.saveManagedArenaRules(
            game,
            (configured.islandRadius + delta).coerceIn(1.0, 128.0),
            configured.disableEmptyTeamGenerators,
            configured.disableEmptyTeamNpcs,
            configured.vanillaDeathDrops,
            configured.useBedHologram
        )
    }

    /** 切换旁观或模板方块破坏规则并保存整组保护值。 */
    private fun toggleProtectionRule(game: ManagedGameConfig, field: String?) {
        val configured = configService.readManagedGame(game)
        saveProtection(
            game,
            configured,
            allowSpectate = if (field == "allow-spectate") !configured.allowSpectate else configured.allowSpectate,
            allowMapBreak = if (field == "allow-map-break") !configured.allowMapBreak else configured.allowMapBreak
        )
    }

    /** 按字段和步长调整世界边界或保护半径。 */
    private fun adjustProtectionRule(game: ManagedGameConfig, field: String?, delta: Double?) {
        if (delta == null) return
        val configured = configService.readManagedGame(game)
        val moduleConfig = configService.current()
        val rules = moduleConfig.blockRules
        saveProtection(
            game,
            configured,
            worldBorder = if (field == "world-border") ((configured.worldBorderSize ?: moduleConfig.worldBorderSize) + delta.toInt()).coerceIn(0, 60_000_000) else null,
            spawn = if (field == "spawn") ((configured.spawnProtectionRadius ?: rules.spawnProtectionRadius) + delta).coerceIn(0.0, 32.0) else null,
            shop = if (field == "shop") ((configured.shopProtectionRadius ?: rules.shopProtectionRadius) + delta).coerceIn(0.0, 32.0) else null,
            upgrade = if (field == "upgrade") ((configured.upgradeShopProtectionRadius ?: rules.shopProtectionRadius) + delta).coerceIn(0.0, 32.0) else null,
            generator = if (field == "generator") ((configured.generatorProtectionRadius ?: rules.generatorProtectionRadius) + delta).coerceIn(0.0, 32.0) else null
        )
    }

    /** 使用当前值补齐未修改字段并保存地图保护配置。 */
    private fun saveProtection(
        game: ManagedGameConfig,
        configured: BedWarsGameConfig,
        worldBorder: Int? = null,
        allowSpectate: Boolean = configured.allowSpectate,
        allowMapBreak: Boolean = configured.allowMapBreak,
        spawn: Double? = null,
        shop: Double? = null,
        upgrade: Double? = null,
        generator: Double? = null
    ) {
        val moduleConfig = configService.current()
        val rules = moduleConfig.blockRules
        configService.saveManagedMapProtectionRules(
            game,
            worldBorder ?: configured.worldBorderSize ?: moduleConfig.worldBorderSize,
            allowSpectate,
            allowMapBreak,
            spawn ?: configured.spawnProtectionRadius ?: rules.spawnProtectionRadius,
            shop ?: configured.shopProtectionRadius ?: rules.shopProtectionRadius,
            upgrade ?: configured.upgradeShopProtectionRadius ?: rules.shopProtectionRadius,
            generator ?: configured.generatorProtectionRadius ?: rules.generatorProtectionRadius
        )
    }

    /** 切换一项结算布尔规则并保存整组当前值。 */
    private fun toggleGameEndRule(game: ManagedGameConfig, field: String?) {
        val configured = configService.readManagedGame(game)
        val moduleConfig = configService.current()
        saveGameEnd(
            game,
            configured,
            showEliminated = if (field == "show-eliminated") !configured.showEliminatedAtGameEnd else configured.showEliminatedAtGameEnd,
            teleportEliminated = if (field == "teleport-eliminated") !configured.teleportEliminatedAtGameEnd else configured.teleportEliminatedAtGameEnd,
            chatHideMissing = if (field == "chat-hide") !(configured.chatTopHideMissing ?: moduleConfig.chatTopHideMissing) else null,
            sidebarHideMissing = if (field == "sidebar-hide") !(configured.sidebarTopHideMissing ?: moduleConfig.sidebarTopHideMissing) else null
        )
    }

    /** 循环切换聊天或 Sidebar 结算榜统计项。 */
    private fun cycleGameEndStatistic(game: ManagedGameConfig, target: String?, direction: Int) {
        val configured = configService.readManagedGame(game)
        val moduleConfig = configService.current()
        saveGameEnd(
            game,
            configured,
            chatStatistic = if (target == "chat") cycle(configured.chatTopStatistic ?: moduleConfig.chatTopStatistic, direction) else null,
            sidebarStatistic = if (target == "sidebar") cycle(configured.sidebarTopStatistic ?: moduleConfig.sidebarTopStatistic, direction) else null
        )
    }

    /** 校验专项表单中的 GameRule 字符串并保存。 */
    private fun saveGameRules(player: Player, game: ManagedGameConfig, variables: Map<String, String>) {
        val rules = parseGameRules(variables["game_rules"]?.trim().orEmpty())
            ?: return missing(player, "bedwars.editor_game_rule_invalid")
        saveGameEnd(game, configService.readManagedGame(game), gameRules = rules)
        player.sendMessage(Component.text(language.getMessage("bedwars.editor_game_end_rules_saved")))
    }

    /** 使用当前值补齐未修改字段并保存结算规则。 */
    private fun saveGameEnd(
        game: ManagedGameConfig,
        configured: BedWarsGameConfig,
        showEliminated: Boolean = configured.showEliminatedAtGameEnd,
        teleportEliminated: Boolean = configured.teleportEliminatedAtGameEnd,
        chatStatistic: BedWarsResultStatistic? = null,
        chatHideMissing: Boolean? = null,
        sidebarStatistic: BedWarsResultStatistic? = null,
        sidebarHideMissing: Boolean? = null,
        gameRules: List<String> = configured.gameRules
    ) {
        val moduleConfig = configService.current()
        configService.saveManagedGameEndRules(
            game,
            showEliminated,
            teleportEliminated,
            chatStatistic ?: configured.chatTopStatistic ?: moduleConfig.chatTopStatistic,
            chatHideMissing ?: configured.chatTopHideMissing ?: moduleConfig.chatTopHideMissing,
            sidebarStatistic ?: configured.sidebarTopStatistic ?: moduleConfig.sidebarTopStatistic,
            sidebarHideMissing ?: configured.sidebarTopHideMissing ?: moduleConfig.sidebarTopHideMissing,
            gameRules
        )
    }

    /** 循环切换公共或队伍生成器的资源类型。 */
    private fun cycleGeneratorType(game: ManagedGameConfig, scope: String?, generatorId: String?, direction: Int) {
        val generator = findGenerator(game, scope, generatorId) ?: return
        saveGenerator(game, scope, generator, cycle(generator.type, direction), generator.intervalTicks)
    }

    /** 按步长调整公共或队伍生成器刷新间隔。 */
    private fun adjustGeneratorInterval(game: ManagedGameConfig, scope: String?, generatorId: String?, delta: Int?) {
        if (delta == null) return
        val generator = findGenerator(game, scope, generatorId) ?: return
        saveGenerator(game, scope, generator, generator.type, (generator.intervalTicks + delta).coerceIn(1, 72_000))
    }

    /** 读取公共或队伍作用域中的生成器。 */
    private fun findGenerator(game: ManagedGameConfig, scope: String?, generatorId: String?): BedWarsGeneratorConfig? {
        val configured = configService.readManagedGame(game)
        return if (scope.equals("public", true)) {
            configured.generators.firstOrNull { it.id.equals(generatorId, true) }
        } else {
            configured.teams.firstOrNull { it.id.equals(scope, true) }
                ?.generators?.firstOrNull { it.id.equals(generatorId, true) }
        }
    }

    /** 保留生成器点位并更新类型或刷新间隔。 */
    private fun saveGenerator(game: ManagedGameConfig, scope: String?, generator: BedWarsGeneratorConfig, type: BedWarsGeneratorType, interval: Int) {
        if (scope.equals("public", true)) {
            configService.upsertManagedGenerator(game, generator.id, type, generator.point, interval)
        } else if (scope != null) {
            configService.upsertManagedTeamGenerator(game, scope, generator.id, type, generator.point, interval)
        }
    }

    /** 启动已存在生成器的重新定位采集，并保留其类型与间隔。 */
    private fun startExistingGeneratorCapture(player: Player, game: ManagedGameConfig, scope: String?, generatorId: String?) {
        val generator = findGenerator(game, scope, generatorId)
            ?: return missing(player, "bedwars.editor_generator_id_missing")
        startGeneratorCapture(
            player,
            game,
            buildMap {
                put("generator_id", generator.id)
                put("generator_type", generator.type.name)
                put("generator_interval", generator.intervalTicks.toString())
                if (!scope.equals("public", true) && scope != null) put("team_id", scope)
            },
            teamScoped = !scope.equals("public", true)
        )
    }

    /** 按方向循环枚举值，并在首尾处环绕。 */
    private inline fun <reified T : Enum<T>> cycle(value: T, direction: Int): T {
        val values = enumValues<T>()
        return values[Math.floorMod(value.ordinal + direction, values.size)]
    }

    /** 从动作参数安全构造一个表单变量覆盖项。 */
    private fun actionVariable(parts: List<String>, index: Int, key: String): Map<String, String> {
        return parts.getOrNull(index)?.let { mapOf(key to it) }.orEmpty()
    }

    /** 为新队伍选择尚未使用的稳定 ID。 */
    private fun nextTeamId(configured: BedWarsGameConfig): String {
        val used = configured.teams.map { it.id }.toSet()
        return DEFAULT_TEAM_IDS.firstOrNull { it !in used } ?: "team-${configured.teams.size + 1}"
    }

    /** 为新队伍选择尚未使用的标准队色。 */
    private fun nextTeamColor(configured: BedWarsGameConfig): BedWarsTeamColor {
        val used = configured.teams.map { it.color }.toSet()
        return BedWarsTeamColor.entries.firstOrNull { it !in used } ?: BedWarsTeamColor.WHITE
    }

    /** 为新生成器构造在当前作用域内不冲突的默认 ID。 */
    private fun nextGeneratorId(configured: BedWarsGameConfig, teamId: String?): String {
        val generators = teamId?.let { id -> configured.teams.firstOrNull { it.id.equals(id, true) }?.generators }.orEmpty()
            .ifEmpty { if (teamId == null) configured.generators else emptyList() }
        val prefix = if (teamId == null) "diamond" else "iron"
        val used = generators.map { it.id }.toSet()
        if (prefix !in used) return prefix
        var index = 2
        while ("$prefix-$index" in used) index++
        return "$prefix-$index"
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
        block: Boolean,
        precise: Boolean = false
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
        when {
            block -> pointCaptureService.beginBlockCapture(player, moduleId, handler)
            precise -> pointCaptureService.beginExactPositionCapture(player, moduleId, handler)
            else -> pointCaptureService.beginPositionCapture(player, moduleId, handler)
        }
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
        pointCaptureService.beginExactPositionCapture(player, moduleId) { capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId) ?: return@beginExactPositionCapture false
            val point = BedWarsPoint.from(location)
            val savedId = if (teamId != null) {
                configService.upsertManagedTeamGenerator(currentGame, teamId, generatorId, type, point, interval)
            } else {
                configService.upsertManagedGenerator(currentGame, generatorId, type, point, interval)
            }
            if (savedId == null) {
                missing(capturePlayer, "bedwars.editor_parent_missing")
                return@beginExactPositionCapture false
            }
            capturePlayer.sendMessage(Component.text(language.getMessage(
                if (teamScoped) "bedwars.editor_team_generator_saved" else "bedwars.editor_generator_saved",
                savedId
            )))
            true
        }
    }

    /** 使用骨头连续创建同类型、同间隔且 ID 自动递增的公共钻石或绿宝石生成器。 */
    private fun startPublicGeneratorBatchCapture(
        player: Player,
        game: ManagedGameConfig,
        type: BedWarsGeneratorType
    ) {
        if (type != BedWarsGeneratorType.DIAMOND && type != BedWarsGeneratorType.EMERALD) {
            missing(player, "bedwars.editor_generator_batch_type_invalid")
            return
        }
        if (activeEditedGame(player, game.globalId) == null) return
        val interval = defaultGeneratorInterval(type)
        player.sendMessage(Component.text(language.getMessage(
            "bedwars.editor_generator_batch_started",
            type.name,
            interval
        )))
        pointCaptureService.beginExactPositionCapture(player, moduleId) { capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId)
                ?: return@beginExactPositionCapture false
            val generatorId = nextBatchGeneratorId(currentGame, type)
            val savedId = configService.upsertManagedGenerator(
                currentGame,
                generatorId,
                type,
                BedWarsPoint.from(location),
                interval
            ) ?: return@beginExactPositionCapture false
            capturePlayer.sendMessage(Component.text(language.getMessage(
                "bedwars.editor_generator_saved",
                savedId
            )))
            true
        }
    }

    /** 读取模块当前第一阶段配置或标准回退值作为资源点创建间隔。 */
    private fun defaultGeneratorInterval(type: BedWarsGeneratorType): Int {
        return configService.current().generatorRules.tier(type, 0)?.intervalTicks ?: when (type) {
            BedWarsGeneratorType.DIAMOND -> 600
            BedWarsGeneratorType.EMERALD -> 1200
            BedWarsGeneratorType.IRON -> 40
            BedWarsGeneratorType.GOLD -> 120
        }
    }

    /** 按指定公共资源类型现有最大编号生成下一个 `<type>-<number>` ID。 */
    private fun nextBatchGeneratorId(game: ManagedGameConfig, type: BedWarsGeneratorType): String {
        val prefix = type.name.lowercase()
        val used = configService.readManagedGame(game).generators.map(BedWarsGeneratorConfig::id).toSet()
        val pattern = Regex("^${Regex.escape(prefix)}-(\\d+)$")
        var index = used.mapNotNull { id -> pattern.matchEntire(id)?.groupValues?.get(1)?.toLongOrNull() }
            .maxOrNull()
            ?.takeIf { it < Long.MAX_VALUE }
            ?.plus(1L)
            ?: 1L
        while ("$prefix-$index" in used) index++
        return "$prefix-$index"
    }

    /** 启动队伍出生、床、商人、回收点及铁金生成器的一键顺序采集。 */
    private fun startTeamCaptureWizard(player: Player, game: ManagedGameConfig, teamId: String?) {
        val selectedTeamId = teamId?.takeIf(String::isNotBlank)
            ?: return missing(player, "bedwars.editor_team_id_missing")
        val currentGame = activeEditedGame(player, game.globalId) ?: return
        if (configService.readManagedGame(currentGame).teams.none { it.id.equals(selectedTeamId, true) }) {
            fail(player, "bedwars.editor_team_missing", selectedTeamId)
            return
        }
        player.sendMessage(Component.text(language.getMessage(
            "bedwars.editor_team_wizard_started",
            selectedTeamId,
            TEAM_WIZARD_STEP_COUNT
        )))
        beginTeamCaptureWizardStep(player, currentGame, selectedTeamId, 0)
    }

    /** 启动队伍向导指定步骤，并在保存成功后自动切换下一种采集模式。 */
    private fun beginTeamCaptureWizardStep(
        player: Player,
        game: ManagedGameConfig,
        teamId: String,
        step: Int
    ) {
        val handler: (Player, Location) -> Boolean = handler@{ capturePlayer, location ->
            val currentGame = activeEditedGame(capturePlayer, game.globalId) ?: return@handler false
            val saved = saveTeamWizardStep(currentGame, teamId, step, location)
            if (!saved) {
                fail(capturePlayer, "bedwars.editor_team_missing", teamId)
                return@handler false
            }
            capturePlayer.sendMessage(Component.text(language.getMessage(
                "bedwars.editor_team_wizard_step_saved",
                language.getMessage(teamWizardStepKey(step))
            )))
            val nextStep = step + 1
            if (nextStep >= TEAM_WIZARD_STEP_COUNT) {
                capturePlayer.sendMessage(Component.text(language.getMessage(
                    "bedwars.editor_team_wizard_completed",
                    teamId
                )))
                return@handler false
            }
            beginTeamCaptureWizardStep(capturePlayer, currentGame, teamId, nextStep)
            true
        }
        when (step) {
            TEAM_WIZARD_BED -> pointCaptureService.beginBlockCapture(player, moduleId, handler)
            TEAM_WIZARD_SHOP,
            TEAM_WIZARD_UPGRADE,
            TEAM_WIZARD_IRON,
            TEAM_WIZARD_GOLD -> pointCaptureService.beginExactPositionCapture(player, moduleId, handler)
            else -> pointCaptureService.beginPositionCapture(player, moduleId, handler)
        }
        player.sendMessage(Component.text(language.getMessage(
            "bedwars.editor_team_wizard_prompt",
            step + 1,
            TEAM_WIZARD_STEP_COUNT,
            language.getMessage(teamWizardStepKey(step)),
            language.getMessage(if (step == TEAM_WIZARD_BED) "bedwars.editor_team_wizard_left_click" else "bedwars.editor_team_wizard_right_click")
        )))
    }

    /** 保存队伍向导当前步骤的点位或标准铁金生成器。 */
    private fun saveTeamWizardStep(
        game: ManagedGameConfig,
        teamId: String,
        step: Int,
        location: Location
    ): Boolean {
        val point = BedWarsPoint.from(location)
        return when (step) {
            TEAM_WIZARD_SPAWN -> configService.saveManagedTeamPoint(game, teamId, "spawn", point)
            TEAM_WIZARD_BED -> configService.saveManagedTeamPoint(game, teamId, "bed", point)
            TEAM_WIZARD_SHOP -> configService.saveManagedTeamPoint(game, teamId, "shop", point)
            TEAM_WIZARD_UPGRADE -> configService.saveManagedTeamPoint(game, teamId, "upgrade-shop", point)
            TEAM_WIZARD_KILL_DROPS -> configService.saveManagedTeamPoint(game, teamId, "kill-drops", point)
            TEAM_WIZARD_IRON -> configService.upsertManagedTeamGenerator(
                game,
                teamId,
                "iron",
                BedWarsGeneratorType.IRON,
                point,
                currentTeamGeneratorInterval(game, teamId, BedWarsGeneratorType.IRON)
            ) != null
            TEAM_WIZARD_GOLD -> configService.upsertManagedTeamGenerator(
                game,
                teamId,
                "gold",
                BedWarsGeneratorType.GOLD,
                point,
                currentTeamGeneratorInterval(game, teamId, BedWarsGeneratorType.GOLD)
            ) != null
            else -> false
        }
    }

    /** 保留队伍已有铁金生成间隔，首次创建时使用模块标准间隔。 */
    private fun currentTeamGeneratorInterval(
        game: ManagedGameConfig,
        teamId: String,
        type: BedWarsGeneratorType
    ): Int {
        return configService.readManagedGame(game).teams
            .firstOrNull { it.id.equals(teamId, true) }
            ?.generators
            ?.firstOrNull { it.id.equals(type.name, true) || it.type == type }
            ?.intervalTicks
            ?: defaultGeneratorInterval(type)
    }

    /** 返回队伍一键采集步骤对应的本地化字段名称。 */
    private fun teamWizardStepKey(step: Int): String = when (step) {
        TEAM_WIZARD_SPAWN -> "bedwars.editor_field_team_spawn"
        TEAM_WIZARD_BED -> "bedwars.editor_field_team_bed"
        TEAM_WIZARD_SHOP -> "bedwars.editor_field_team_shop"
        TEAM_WIZARD_UPGRADE -> "bedwars.editor_field_team_upgrade"
        TEAM_WIZARD_KILL_DROPS -> "bedwars.editor_field_team_kill_drops"
        TEAM_WIZARD_IRON -> "bedwars.editor_field_team_iron_generator"
        TEAM_WIZARD_GOLD -> "bedwars.editor_field_team_gold_generator"
        else -> "bedwars.editor_none"
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

    private companion object {
        val DEFAULT_TEAM_IDS = listOf("red", "blue", "green", "yellow", "aqua", "white", "pink", "gray")
        const val TEAM_WIZARD_SPAWN = 0
        const val TEAM_WIZARD_BED = 1
        const val TEAM_WIZARD_SHOP = 2
        const val TEAM_WIZARD_UPGRADE = 3
        const val TEAM_WIZARD_KILL_DROPS = 4
        const val TEAM_WIZARD_IRON = 5
        const val TEAM_WIZARD_GOLD = 6
        const val TEAM_WIZARD_STEP_COUNT = 7
    }
}
