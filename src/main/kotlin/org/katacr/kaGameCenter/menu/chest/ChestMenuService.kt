package org.katacr.kaGameCenter.menu.chest

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.dialog.GameCenterMenuService
import org.katacr.kaGameCenter.display.IconTextParser
import kotlin.math.ceil

class ChestMenuService(
    private val plugin: JavaPlugin,
    private val gameCenterMenuService: GameCenterMenuService
) {
    private val templates = ChestMenuTemplateService(plugin)
    private val itemBuilder = ChestMenuItemBuilder(plugin)
    val dataSources = ChestMenuDataSourceRegistry()
    private lateinit var actionRouter: ChestMenuActionRouter

    fun init() {
        templates.init()
        actionRouter = ChestMenuActionRouter(plugin, this, gameCenterMenuService, itemBuilder)
    }

    fun shutdown() {
        Bukkit.getOnlinePlayers()
            .mapNotNull { it.openInventory.topInventory.holder as? ChestMenuHolder }
            .forEach { it.stopUpdate() }
    }

    fun open(player: Player, menuId: String = "main", context: Map<String, String> = emptyMap(), page: Int = 0): Boolean {
        val config = templates.load(menuId) ?: return false
        return openConfigInternal(player, menuId, config, context, page)
    }

    fun openConfig(
        player: Player,
        config: YamlConfiguration,
        contextId: String,
        context: Map<String, String> = emptyMap(),
        page: Int = 0
    ): Boolean {
        return openConfigInternal(player, contextId, convertDialogLikeConfig(config, contextId), context, page)
    }

    private fun openConfigInternal(
        player: Player,
        menuId: String,
        config: YamlConfiguration,
        context: Map<String, String>,
        page: Int
    ): Boolean {
        val layout = templates.layout(config)
        if (layout.isEmpty()) {
            player.sendMessage(Component.text("Chest menu $menuId has no layout."))
            return false
        }
        val buttons = templates.buttons(config)
        val size = (layout.size.coerceIn(1, 6)) * 9
        val title = IconTextParser.parse(replace(config.getString("title", menuId) ?: menuId, context + mapOf("menu.page" to page.toString())))
        val holder = ChestMenuHolder(menuId, layout, buttons, context, page.coerceAtLeast(0))
        val inventory = Bukkit.createInventory(holder, size, title)
        holder.bind(inventory)
        render(player, holder)
        player.openInventory(inventory)
        startUpdateTask(player, holder, config.getLong("update", 0L))
        return true
    }

    fun refresh(player: Player, holder: ChestMenuHolder) {
        render(player, holder)
        player.updateInventory()
    }

    fun handleClick(player: Player, holder: ChestMenuHolder, slot: Int, clickKey: String) {
        val icon = holder.iconAt(slot) ?: return
        val button = holder.buttons?.getConfigurationSection(icon) ?: return
        actionRouter.execute(player, holder, button, clickKey, holder.slotVariables[slot].orEmpty())
    }

    private fun render(player: Player, holder: ChestMenuHolder) {
        val inventory = holder.inventory
        inventory.clear()
        holder.slotVariables.clear()
        val buttons = holder.buttons ?: return
        val dynamicSlots = linkedMapOf<String, MutableList<Int>>()

        for (slot in 0 until inventory.size) {
            val icon = holder.iconAt(slot) ?: continue
            val button = buttons.getConfigurationSection(icon) ?: continue
            val type = button.getString("type")?.trim()
            if (!type.isNullOrBlank() && dataSources.get(type) != null) {
                dynamicSlots.getOrPut(type.lowercase()) { mutableListOf() }.add(slot)
                continue
            }
            val variables = holder.context + mapOf(
                "menu.id" to holder.menuId,
                "menu.page" to holder.currentPage.toString(),
                "viewer.name" to player.name,
                "viewer.uuid" to player.uniqueId.toString()
            )
            itemBuilder.build(button, variables)?.let { inventory.setItem(slot, it) }
            holder.slotVariables[slot] = variables
        }

        dynamicSlots.forEach { (type, slots) ->
            renderDynamic(player, holder, type, slots)
        }
    }

    private fun renderDynamic(player: Player, holder: ChestMenuHolder, type: String, slots: List<Int>) {
        val source = dataSources.get(type) ?: return
        val button = holder.buttons?.getConfigurationSectionForType(type) ?: return
        val entries = source.entries(player, holder.context)
        val pageSize = slots.size.coerceAtLeast(1)
        val page = holder.currentPage.coerceAtLeast(0)
        val maxPage = ceil(entries.size / pageSize.toDouble()).toInt().coerceAtLeast(1) - 1
        holder.currentPage = page.coerceAtMost(maxPage)
        val offset = holder.currentPage * pageSize
        val pageEntries = entries.drop(offset).take(pageSize)
        slots.forEachIndexed { index, slot ->
            val entry = pageEntries.getOrNull(index) ?: return@forEachIndexed
            val variables = holder.context + entry.variables + mapOf(
                "menu.id" to holder.menuId,
                "menu.page" to holder.currentPage.toString(),
                "menu.max_page" to maxPage.toString(),
                "viewer.name" to player.name,
                "viewer.uuid" to player.uniqueId.toString()
            )
            val renderButton = entry.display?.let { mergeDisplay(button, it) } ?: button
            itemBuilder.build(renderButton, variables)?.let { holder.inventory.setItem(slot, it) }
            holder.slotVariables[slot] = variables
        }
    }

    private fun mergeDisplay(button: ConfigurationSection, displayOverride: ConfigurationSection): ConfigurationSection {
        val copy = org.bukkit.configuration.file.YamlConfiguration()
        copy.set("type", button.getString("type"))
        button.getConfigurationSection("display")?.let { copySection(it, copy.createSection("display")) }
        val display = copy.getConfigurationSection("display") ?: copy.createSection("display")
        copySection(displayOverride, display)
        button.getConfigurationSection("actions")?.let { copySection(it, copy.createSection("actions")) }
        return copy
    }

    private fun copySection(from: ConfigurationSection, to: ConfigurationSection) {
        from.getKeys(false).forEach { key ->
            val child = from.getConfigurationSection(key)
            if (child != null) {
                copySection(child, to.createSection(key))
            } else {
                to.set(key, from.get(key))
            }
        }
    }

    private fun startUpdateTask(player: Player, holder: ChestMenuHolder, updateTicks: Long) {
        if (updateTicks <= 0) return
        holder.setUpdateTask(Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline || player.openInventory.topInventory.holder !== holder) {
                holder.stopUpdate()
                return@Runnable
            }
            refresh(player, holder)
        }, updateTicks, updateTicks))
    }

    private fun replace(text: String, variables: Map<String, String>): String {
        var output = text
        variables.forEach { (key, value) -> output = output.replace("{$key}", value) }
        return output
    }

    private fun convertDialogLikeConfig(source: YamlConfiguration, contextId: String): YamlConfiguration {
        if (templates.layout(source).isNotEmpty()) return source

        val converted = YamlConfiguration()
        converted.set("title", source.getString("Title", contextId) ?: contextId)
        converted.set("layout", listOf(
            "#########",
            "#   I   #",
            "#1234567#",
            "#890abcd#",
            "#efghijk#",
            "##B###X##"
        ))
        converted.set("buttons.#.display.material", "GRAY_STAINED_GLASS_PANE")
        converted.set("buttons.#.display.name", " ")

        val bodyLines = source.getConfigurationSection("Body")
            ?.getKeys(false)
            ?.flatMap { key ->
                val node = source.get("Body.$key.text")
                when (node) {
                    is List<*> -> node.mapNotNull { it?.toString() }
                    is String -> listOf(node)
                    else -> emptyList()
                }
            }
            .orEmpty()
        val inputDefaults = dialogInputDefaults(source)
        converted.set("buttons.I.display.material", "BOOK")
        converted.set("buttons.I.display.name", source.getString("Title", contextId) ?: contextId)
        converted.set("buttons.I.display.lore", buildList {
            addAll(bodyLines)
            if (inputDefaults.isNotEmpty()) {
                add("&8")
                inputDefaults.forEach { (key, value) -> add("&7$key: &f$value") }
            }
        })

        val chars = "1234567890abcdefghijk".map { it.toString() }
        val buttons = source.getConfigurationSection("Bottom.buttons")
        buttons?.getKeys(false)?.take(chars.size)?.forEachIndexed { index, key ->
            copyDialogButton(source.getConfigurationSection("Bottom.buttons.$key"), converted, chars[index], inputDefaults)
        }

        source.getConfigurationSection("Bottom.confirm")?.let {
            copyDialogButton(it, converted, "1", inputDefaults, fallbackMaterial = "LIME_DYE")
        }
        source.getConfigurationSection("Bottom.deny")?.let {
            copyDialogButton(it, converted, "2", inputDefaults, fallbackMaterial = "RED_DYE")
        }

        converted.set("buttons.B.display.material", "ARROW")
        converted.set("buttons.B.display.name", source.getString("Bottom.exit.text", "&7返回") ?: "&7返回")
        converted.set("buttons.B.actions.left", source.getStringList("Bottom.exit.actions").ifEmpty { listOf("kgc:open-main") })
        converted.set("buttons.X.display.material", "BARRIER")
        converted.set("buttons.X.display.name", "&c关闭")
        converted.set("buttons.X.actions.left", listOf("close"))
        return converted
    }

    private fun dialogInputDefaults(source: YamlConfiguration): Map<String, String> {
        val inputs = source.getConfigurationSection("Inputs") ?: return emptyMap()
        return inputs.getKeys(false).associateWith { key ->
            source.getString("Inputs.$key.default_id")
                ?: source.getString("Inputs.$key.default")
                ?: source.getStringList("Inputs.$key.options").firstOrNull()?.substringBefore("=>")?.trim()
                ?: ""
        }
    }

    private fun copyDialogButton(
        source: ConfigurationSection?,
        target: YamlConfiguration,
        char: String,
        inputDefaults: Map<String, String>,
        fallbackMaterial: String = "PAPER"
    ) {
        if (source == null) return
        target.set("buttons.$char.display.material", source.getString("material", fallbackMaterial) ?: fallbackMaterial)
        target.set("buttons.$char.display.name", source.getString("text", char) ?: char)
        val tooltip = source.getStringList("tooltip")
        if (tooltip.isNotEmpty()) target.set("buttons.$char.display.lore", tooltip)
        val actions = source.getStringList("actions").map { action ->
            inputDefaults.entries.fold(action) { line, (key, value) ->
                line.replace("\$($key)", value).replace("{$key}", value)
            }
        }
        if (actions.isNotEmpty()) target.set("buttons.$char.actions.left", actions)
    }

    private fun ConfigurationSection.getConfigurationSectionForType(type: String): ConfigurationSection? {
        return getKeys(false)
            .asSequence()
            .mapNotNull { getConfigurationSection(it) }
            .firstOrNull { it.getString("type")?.equals(type, true) == true }
    }
}
