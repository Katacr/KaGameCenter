package org.katacr.kagamecenter.bedwars

import org.bukkit.configuration.file.YamlConfiguration
import org.katacr.kaGameCenter.game.ManagedGameConfig
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import kotlin.math.ceil

/** 为 BedWars 托管游戏编辑器构造多级内置箱子菜单。 */
class BedWarsEditorMenuFactory(
    private val language: ModuleLanguage
) {
    /** 构造编辑器分类首页。 */
    fun main(game: ManagedGameConfig, configured: BedWarsGameConfig): YamlConfiguration {
        val menu = base("bedwars.editor_menu_main_title", game.displayName, listOf(
            "#########",
            "#WAPTGEO#",
            "#       #",
            "#  V C  #",
            "#B#####X#"
        ))
        button(menu, "W", "GRASS_BLOCK", text("bedwars.editor_menu_world"), listOf(text("bedwars.editor_menu_world_lore")), left(game, "open-section world"))
        button(menu, "A", "COMPARATOR", text("bedwars.editor_menu_arena"), listOf(text("bedwars.editor_menu_arena_lore")), left(game, "open-section arena"))
        button(menu, "P", "SHIELD", text("bedwars.editor_menu_protection"), listOf(text("bedwars.editor_menu_protection_lore")), left(game, "open-section protection"))
        button(menu, "T", "RED_BED", text("bedwars.editor_menu_teams", configured.teams.size), listOf(text("bedwars.editor_menu_teams_lore")), left(game, "open-teams-page 0"))
        button(menu, "G", "DIAMOND", text("bedwars.editor_menu_generators", configured.generators.size), listOf(text("bedwars.editor_menu_generators_lore")), left(game, "open-generators-page 0"))
        button(menu, "E", "NETHER_STAR", text("bedwars.editor_menu_game_end"), listOf(text("bedwars.editor_menu_game_end_lore")), left(game, "open-section game-end"))
        button(menu, "O", "WRITABLE_BOOK", text("bedwars.editor_menu_overview"), listOf(text("bedwars.editor_menu_overview_lore", configured.validationErrors().size)), left(game, "open-section overview"))
        button(menu, "V", "ENDER_EYE", text("bedwars.editor_button_preview"), listOf(text("bedwars.editor_menu_preview_lore")), left(game, "preview"))
        button(menu, "C", "LIME_DYE", text("bedwars.editor_button_validate"), listOf(text("bedwars.editor_menu_validate_lore")), left(game, "validate"))
        navigation(menu, game)
        return menu
    }

    /** 构造配置概览及校验菜单。 */
    fun overview(game: ManagedGameConfig, summary: List<String>): YamlConfiguration {
        val menu = base("bedwars.editor_menu_overview_title", game.displayName, listOf(
            "#########",
            "#   I   #",
            "#       #",
            "#  V C  #",
            "#B#####X#"
        ))
        button(menu, "I", "BOOK", text("bedwars.editor_menu_overview"), summary)
        button(menu, "V", "ENDER_EYE", text("bedwars.editor_button_preview"), listOf(text("bedwars.editor_menu_preview_lore")), left(game, "preview"))
        button(menu, "C", "LIME_DYE", text("bedwars.editor_button_validate"), listOf(text("bedwars.editor_menu_validate_lore")), left(game, "validate"))
        navigation(menu, game, "open-section main")
        return menu
    }

    /** 构造编辑世界及公共点位菜单。 */
    fun world(game: ManagedGameConfig, configured: BedWarsGameConfig): YamlConfiguration {
        val menu = base("bedwars.editor_menu_world_title", game.displayName, listOf(
            "#########",
            "#OSCLPVH#",
            "#       #",
            "#       #",
            "#B#####X#"
        ))
        button(menu, "O", "GRASS_BLOCK", text("bedwars.editor_button_open_world"), listOf(text("bedwars.editor_menu_open_world_lore")), left(game, "open-world"))
        button(menu, "S", "CHEST", text("bedwars.editor_button_save_world"), listOf(text("bedwars.editor_menu_save_world_lore")), left(game, "save-world"))
        button(menu, "C", "OAK_DOOR", text("bedwars.editor_button_close_world"), listOf(text("bedwars.editor_menu_close_world_lore")), left(game, "close-world"))
        captureButton(menu, "L", "COMPASS", "bedwars.editor_button_set_lobby", configured.lobby != null, game, "set-lobby")
        captureButton(menu, "P", "ENDER_PEARL", "bedwars.editor_button_set_spectator", configured.spectatorSpawn != null, game, "set-spectator")
        captureButton(menu, "V", "BLACK_CONCRETE", "bedwars.editor_button_set_void_y", configured.voidY != null, game, "set-void-y")
        captureButton(menu, "H", "SCAFFOLDING", "bedwars.editor_button_set_max_build_y", configured.maxBuildY != null, game, "set-max-build-y")
        navigation(menu, game, "open-section main")
        return menu
    }

    /** 构造基础竞技场规则菜单。 */
    fun arena(game: ManagedGameConfig, configured: BedWarsGameConfig): YamlConfiguration {
        val menu = base("bedwars.editor_menu_arena_title", game.displayName, listOf(
            "#########",
            "#RGENHIS#",
            "#       #",
            "#       #",
            "#B#####X#"
        ))
        adjustButton(menu, "R", "ENDER_EYE", text("bedwars.editor_menu_island_radius", configured.islandRadius), game, "arena island-radius", "1", "5")
        toggleButton(menu, "G", "HOPPER", "bedwars.editor_menu_disable_generators", configured.disableEmptyTeamGenerators, game, "arena disable-generators")
        toggleButton(menu, "N", "VILLAGER_SPAWN_EGG", "bedwars.editor_menu_disable_npcs", configured.disableEmptyTeamNpcs, game, "arena disable-npcs")
        toggleButton(menu, "H", "SKELETON_SKULL", "bedwars.editor_menu_vanilla_drops", configured.vanillaDeathDrops, game, "arena vanilla-drops")
        toggleButton(menu, "E", "RED_BED", "bedwars.editor_menu_bed_hologram", configured.useBedHologram, game, "arena bed-hologram")
        button(menu, "I", "CHEST", text("bedwars.editor_menu_item_group", configured.itemGroup), listOf(text("bedwars.editor_menu_form_lore")), left(game, "open-form groups"))
        button(menu, "S", "NAME_TAG", text("bedwars.editor_menu_selector_group", game.selectorGroup), listOf(text("bedwars.editor_menu_form_lore")), left(game, "open-form groups"))
        navigation(menu, game, "open-section main")
        return menu
    }

    /** 构造地图访问和关键点保护菜单。 */
    fun protection(game: ManagedGameConfig, configured: BedWarsGameConfig, moduleConfig: BedWarsConfig): YamlConfiguration {
        val rules = moduleConfig.blockRules
        val menu = base("bedwars.editor_menu_protection_title", game.displayName, listOf(
            "#########",
            "#WSMRPUG#",
            "#       #",
            "#       #",
            "#B#####X#"
        ))
        adjustButton(menu, "W", "MAP", text("bedwars.editor_menu_world_border", configured.worldBorderSize ?: moduleConfig.worldBorderSize), game, "protection world-border", "10", "100")
        toggleButton(menu, "S", "SPYGLASS", "bedwars.editor_menu_allow_spectate", configured.allowSpectate, game, "protection allow-spectate")
        toggleButton(menu, "M", "IRON_PICKAXE", "bedwars.editor_menu_allow_map_break", configured.allowMapBreak, game, "protection allow-map-break")
        adjustButton(menu, "R", "RESPAWN_ANCHOR", text("bedwars.editor_menu_spawn_protection", configured.spawnProtectionRadius ?: rules.spawnProtectionRadius), game, "protection spawn", "0.5", "2")
        adjustButton(menu, "P", "EMERALD", text("bedwars.editor_menu_shop_protection", configured.shopProtectionRadius ?: rules.shopProtectionRadius), game, "protection shop", "0.5", "2")
        adjustButton(menu, "U", "ANVIL", text("bedwars.editor_menu_upgrade_protection", configured.upgradeShopProtectionRadius ?: rules.shopProtectionRadius), game, "protection upgrade", "0.5", "2")
        adjustButton(menu, "G", "HOPPER", text("bedwars.editor_menu_generator_protection", configured.generatorProtectionRadius ?: rules.generatorProtectionRadius), game, "protection generator", "0.5", "2")
        navigation(menu, game, "open-section main")
        return menu
    }

    /** 构造队伍分页列表菜单。 */
    fun teams(game: ManagedGameConfig, configured: BedWarsGameConfig, page: Int): YamlConfiguration {
        val safePage = page.coerceIn(0, maxPage(configured.teams.size))
        val menu = listBase("bedwars.editor_menu_teams_title", game, safePage, configured.teams.size)
        configured.teams.drop(safePage * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { index, team ->
            button(
                menu,
                LIST_ICONS[index].toString(),
                team.color.wool.name,
                text("bedwars.editor_menu_team_entry", team.displayName, team.id),
                listOf(
                    text("bedwars.editor_menu_team_entry_lore", team.color.name, team.maxPlayers, team.generators.size),
                    text("bedwars.editor_menu_points_status", pointCount(team), 5)
                ),
                left(game, "open-team ${team.id}")
            )
        }
        button(menu, "C", "LIME_DYE", text("bedwars.editor_menu_team_create"), listOf(text("bedwars.editor_menu_form_lore")), left(game, "open-form team"))
        pageButtons(menu, game, "teams", safePage, configured.teams.size)
        return menu
    }

    /** 构造单个队伍及其点位和生成器菜单。 */
    fun team(game: ManagedGameConfig, team: BedWarsTeamConfig): YamlConfiguration {
        val menu = base("bedwars.editor_menu_team_title", team.displayName, listOf(
            "#########",
            "#ESDKQUI#",
            "#   W   #",
            "#1234567#",
            "#B#####X#"
        ))
        button(menu, "E", team.color.wool.name, text("bedwars.editor_menu_team_properties", team.color.name, team.maxPlayers), listOf(text("bedwars.editor_menu_form_lore")), left(game, "open-form team ${team.id}"))
        captureButton(menu, "S", "COMPASS", "bedwars.editor_button_set_team_spawn", team.spawn != null, game, "set-team-spawn ${team.id}")
        captureButton(menu, "D", team.color.colorize(org.bukkit.Material.RED_BED).name, "bedwars.editor_button_set_team_bed", team.bed != null, game, "set-team-bed ${team.id}")
        captureButton(menu, "K", "CHEST", "bedwars.editor_button_set_team_kill_drops", team.killDrops != null, game, "set-team-kill-drops ${team.id}")
        captureButton(menu, "Q", "EMERALD", "bedwars.editor_button_set_team_shop", team.shop != null, game, "set-team-shop ${team.id}", precise = true)
        captureButton(menu, "U", "ANVIL", "bedwars.editor_button_set_team_upgrade", team.upgradeShop != null, game, "set-team-upgrade ${team.id}", precise = true)
        button(menu, "I", "LAVA_BUCKET", text("bedwars.editor_menu_team_remove"), listOf(text("bedwars.editor_menu_shift_remove_lore")), mapOf("shift_left" to listOf(action(game, "remove-team ${team.id}"))))
        button(menu, "W", "BONE", text("bedwars.editor_menu_team_wizard"), listOf(text("bedwars.editor_menu_team_wizard_lore")), mapOf("left" to listOf("close", action(game, "start-team-wizard ${team.id}"))))
        team.generators.take(7).forEachIndexed { index, generator ->
            button(menu, (index + 1).toString(), generator.type.material.name, text("bedwars.editor_menu_generator_entry", generator.id), listOf(text("bedwars.editor_menu_generator_entry_lore", generator.type.name, generator.intervalTicks)), left(game, "open-team-generator ${team.id} ${generator.id}"))
        }
        if (team.generators.size < 7) {
            val icon = (team.generators.size + 1).toString()
            button(menu, icon, "LIME_DYE", text("bedwars.editor_menu_generator_create"), listOf(text("bedwars.editor_menu_form_lore")), left(game, "open-form team-generator ${team.id}"))
        }
        navigation(menu, game, "open-teams-page 0")
        return menu
    }

    /** 构造公共生成器分页列表菜单。 */
    fun generators(game: ManagedGameConfig, configured: BedWarsGameConfig, page: Int): YamlConfiguration {
        val safePage = page.coerceIn(0, maxPage(configured.generators.size))
        val menu = listBase("bedwars.editor_menu_generators_title", game, safePage, configured.generators.size)
        configured.generators.drop(safePage * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { index, generator ->
            button(menu, LIST_ICONS[index].toString(), generator.type.material.name, text("bedwars.editor_menu_generator_entry", generator.id), listOf(text("bedwars.editor_menu_generator_entry_lore", generator.type.name, generator.intervalTicks)), left(game, "open-generator ${generator.id}"))
        }
        button(menu, "C", "LIME_DYE", text("bedwars.editor_menu_generator_create"), listOf(text("bedwars.editor_menu_form_lore")), left(game, "open-form generator"))
        button(menu, "D", "DIAMOND", text("bedwars.editor_menu_generator_batch_diamond"), listOf(text("bedwars.editor_menu_generator_batch_lore")), mapOf("left" to listOf("close", action(game, "start-generator-batch DIAMOND"))))
        button(menu, "E", "EMERALD", text("bedwars.editor_menu_generator_batch_emerald"), listOf(text("bedwars.editor_menu_generator_batch_lore")), mapOf("left" to listOf("close", action(game, "start-generator-batch EMERALD"))))
        pageButtons(menu, game, "generators", safePage, configured.generators.size)
        return menu
    }

    /** 构造公共或队伍生成器详情菜单。 */
    fun generator(game: ManagedGameConfig, generator: BedWarsGeneratorConfig, teamId: String?): YamlConfiguration {
        val scope = teamId ?: "public"
        val menu = base("bedwars.editor_menu_generator_title", generator.id, listOf(
            "#########",
            "# T I P #",
            "#   D   #",
            "#       #",
            "#B#####X#"
        ))
        button(menu, "T", generator.type.material.name, text("bedwars.editor_menu_generator_type", generator.type.name), listOf(text("bedwars.editor_menu_cycle_lore")), cycle(game, "generator-type $scope ${generator.id}"))
        adjustButton(menu, "I", "CLOCK", text("bedwars.editor_menu_generator_interval", generator.intervalTicks), game, "generator-interval $scope ${generator.id}", "5", "20")
        captureButton(menu, "P", "COMPASS", "bedwars.editor_menu_generator_position", true, game, "set-generator-position $scope ${generator.id}", precise = true)
        val removeAction = if (teamId == null) "remove-generator ${generator.id}" else "remove-team-generator $teamId ${generator.id}"
        button(menu, "D", "LAVA_BUCKET", text("bedwars.editor_menu_generator_remove"), listOf(text("bedwars.editor_menu_shift_remove_lore")), mapOf("shift_left" to listOf(action(game, removeAction))))
        navigation(menu, game, if (teamId == null) "open-generators-page 0" else "open-team $teamId")
        return menu
    }

    /** 构造结算显示、榜单和 GameRule 菜单。 */
    fun gameEnd(game: ManagedGameConfig, configured: BedWarsGameConfig, moduleConfig: BedWarsConfig): YamlConfiguration {
        val chatStatistic = configured.chatTopStatistic ?: moduleConfig.chatTopStatistic
        val sidebarStatistic = configured.sidebarTopStatistic ?: moduleConfig.sidebarTopStatistic
        val menu = base("bedwars.editor_menu_game_end_title", game.displayName, listOf(
            "#########",
            "#STCHYGR#",
            "#       #",
            "#       #",
            "#B#####X#"
        ))
        toggleButton(menu, "S", "PLAYER_HEAD", "bedwars.editor_menu_show_eliminated", configured.showEliminatedAtGameEnd, game, "game-end show-eliminated")
        toggleButton(menu, "T", "ENDER_PEARL", "bedwars.editor_menu_teleport_eliminated", configured.teleportEliminatedAtGameEnd, game, "game-end teleport-eliminated")
        button(menu, "C", "PAPER", text("bedwars.editor_menu_chat_statistic", chatStatistic.name), listOf(text("bedwars.editor_menu_cycle_lore")), cycle(game, "game-end-stat chat"))
        toggleButton(menu, "H", "GRAY_DYE", "bedwars.editor_menu_chat_hide_missing", configured.chatTopHideMissing ?: moduleConfig.chatTopHideMissing, game, "game-end chat-hide")
        button(menu, "Y", "MAP", text("bedwars.editor_menu_sidebar_statistic", sidebarStatistic.name), listOf(text("bedwars.editor_menu_cycle_lore")), cycle(game, "game-end-stat sidebar"))
        toggleButton(menu, "G", "GRAY_DYE", "bedwars.editor_menu_sidebar_hide_missing", configured.sidebarTopHideMissing ?: moduleConfig.sidebarTopHideMissing, game, "game-end sidebar-hide")
        button(menu, "R", "COMMAND_BLOCK", text("bedwars.editor_menu_game_rules", configured.gameRules.size), listOf(text("bedwars.editor_menu_form_lore")), left(game, "open-form game-rules"))
        navigation(menu, game, "open-section main")
        return menu
    }

    /** 构造带边框、标题和关闭按钮的基础菜单。 */
    private fun base(titleKey: String, titleArg: Any, layout: List<String>): YamlConfiguration {
        val menu = YamlConfiguration()
        menu.set("title", text(titleKey, titleArg))
        menu.set("layout", layout)
        button(menu, "#", "GRAY_STAINED_GLASS_PANE", " ")
        button(menu, "X", "BARRIER", text("bedwars.editor_menu_close"), actions = mapOf("left" to listOf("close")))
        return menu
    }

    /** 构造对象分页列表的固定布局。 */
    private fun listBase(titleKey: String, game: ManagedGameConfig, page: Int, total: Int): YamlConfiguration {
        val menu = base(titleKey, game.displayName, listOf(
            "#########",
            "#abcdefg#",
            "#hijklmn#",
            "#opqrstu#",
            "#vwx1234#",
            "#PDCBENX#"
        ))
        menu.set("title", text(titleKey, game.displayName, page + 1, maxPage(total) + 1))
        button(menu, "B", "ARROW", text("bedwars.editor_menu_back"), actions = left(game, "open-section main"))
        return menu
    }

    /** 添加分页按钮，并确保首尾页不会越界。 */
    private fun pageButtons(menu: YamlConfiguration, game: ManagedGameConfig, section: String, page: Int, total: Int) {
        val maximum = maxPage(total)
        button(menu, "P", "SPECTRAL_ARROW", text("bedwars.editor_menu_previous_page"), actions = if (page > 0) left(game, "open-$section-page ${page - 1}") else emptyMap())
        button(menu, "N", "SPECTRAL_ARROW", text("bedwars.editor_menu_next_page"), actions = if (page < maximum) left(game, "open-$section-page ${page + 1}") else emptyMap())
    }

    /** 添加返回、关闭和公共管理页出口。 */
    private fun navigation(menu: YamlConfiguration, game: ManagedGameConfig, backAction: String = "open-section main") {
        button(menu, "B", "ARROW", text("bedwars.editor_menu_back"), actions = left(game, backAction))
        if (menu.getConfigurationSection("buttons.X") == null) {
            button(menu, "X", "BARRIER", text("bedwars.editor_menu_close"), actions = mapOf("left" to listOf("close")))
        }
        if (backAction == "open-section main") {
            menu.set("buttons.B.actions.shift_left", listOf("kgc:open-admin-managed-games"))
            menu.set("buttons.B.display.lore", listOf(text("bedwars.editor_menu_admin_back_lore")))
        }
    }

    /** 添加显示当前状态并启动骨头采集的点位按钮。 */
    private fun captureButton(
        menu: YamlConfiguration,
        icon: String,
        material: String,
        nameKey: String,
        configured: Boolean,
        game: ManagedGameConfig,
        command: String,
        precise: Boolean = false
    ) {
        val captureLore = if (precise) "bedwars.editor_menu_precise_capture_lore" else "bedwars.editor_menu_capture_lore"
        button(menu, icon, material, text(nameKey), listOf(text(captureLore), text("bedwars.editor_menu_status", status(configured))), mapOf("left" to listOf("close", action(game, command))))
    }

    /** 添加点击切换布尔值的规则按钮。 */
    private fun toggleButton(menu: YamlConfiguration, icon: String, material: String, nameKey: String, value: Boolean, game: ManagedGameConfig, command: String) {
        button(menu, icon, material, text(nameKey), listOf(text("bedwars.editor_menu_status", boolean(value)), text("bedwars.editor_menu_toggle_lore")), left(game, "toggle-$command"), value)
    }

    /** 添加支持普通及 Shift 步长的数值按钮。 */
    private fun adjustButton(menu: YamlConfiguration, icon: String, material: String, name: String, game: ManagedGameConfig, command: String, step: String, largeStep: String) {
        button(menu, icon, material, name, listOf(text("bedwars.editor_menu_adjust_lore", step), text("bedwars.editor_menu_adjust_shift_lore", largeStep)), mapOf(
            "left" to listOf(action(game, "adjust-$command $step")),
            "right" to listOf(action(game, "adjust-$command -$step")),
            "shift_left" to listOf(action(game, "adjust-$command $largeStep")),
            "shift_right" to listOf(action(game, "adjust-$command -$largeStep"))
        ))
    }

    /** 写入一个箱子菜单按钮的显示和按键动作。 */
    private fun button(menu: YamlConfiguration, icon: String, material: String, name: String, lore: List<String> = emptyList(), actions: Map<String, List<String>> = emptyMap(), enchanted: Boolean = false) {
        val path = "buttons.$icon"
        menu.set("$path.display.material", material)
        menu.set("$path.display.name", name)
        if (lore.isNotEmpty()) menu.set("$path.display.lore", lore)
        if (enchanted) menu.set("$path.display.enchanted", true)
        actions.forEach { (click, lines) -> menu.set("$path.actions.$click", lines) }
    }

    /** 生成箱子菜单左键动作。 */
    private fun left(game: ManagedGameConfig, command: String): Map<String, List<String>> = mapOf("left" to listOf(action(game, command)))

    /** 生成枚举向前或向后循环的左右键动作。 */
    private fun cycle(game: ManagedGameConfig, command: String): Map<String, List<String>> = mapOf(
        "left" to listOf(action(game, "$command 1")),
        "right" to listOf(action(game, "$command -1"))
    )

    /** 生成路由到当前托管游戏编辑器的动作文本。 */
    private fun action(game: ManagedGameConfig, command: String): String = "kgc:module-game-action ${game.globalId} $command"

    /** 读取本地化文本并应用参数。 */
    private fun text(key: String, vararg args: Any): String = language.getMessage(key, *args)

    /** 读取本地化开关值。 */
    private fun boolean(value: Boolean): String = text(if (value) "bedwars.editor_menu_enabled" else "bedwars.editor_menu_disabled")

    /** 读取点位是否已设置的本地化状态。 */
    private fun status(value: Boolean): String = text(if (value) "bedwars.editor_status_set" else "bedwars.editor_status_missing")

    /** 计算队伍已配置的五类关键点数量。 */
    private fun pointCount(team: BedWarsTeamConfig): Int = listOf(team.spawn, team.bed, team.killDrops, team.shop, team.upgradeShop).count { it != null }

    /** 计算静态列表菜单最大页码。 */
    private fun maxPage(size: Int): Int = (ceil(size / PAGE_SIZE.toDouble()).toInt() - 1).coerceAtLeast(0)

    private companion object {
        const val PAGE_SIZE = 28
        const val LIST_ICONS = "abcdefghijklmnopqrstuvwx1234"
    }
}
