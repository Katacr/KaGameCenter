package org.katacr.kagamecenter.bedwars

import org.katacr.kaGameCenter.i18n.ModuleLanguage
import org.katacr.kaGameCenter.spectator.SpectatorHotbarItem
import org.katacr.kaGameCenter.spectator.SpectatorMode
import org.katacr.kaGameCenter.spectator.SpectatorPolicy

/** 将 BedWars 配置转换为主插件可执行的外部或托管淘汰观战策略。 */
internal fun BedWarsConfig.toSpectatorPolicy(
    language: ModuleLanguage,
    enabled: Boolean = spectatorEnabled,
    mode: SpectatorMode = spectatorMode,
    revealHiddenPlayers: Boolean = true
): SpectatorPolicy {
    return SpectatorPolicy(
        enabled = enabled,
        mode = mode,
        allowDuringRunning = true,
        allowFollowPlayer = true,
        allowFreeFly = true,
        revealHiddenPlayers = revealHiddenPlayers,
        hotbarItems = spectatorItems.map { configured ->
            SpectatorHotbarItem(
                id = configured.id,
                material = configured.material,
                slot = configured.slot,
                enchanted = configured.enchanted,
                displayName = configured.localizedName(language),
                lore = configured.localizedLore(language),
                action = configured.action,
                command = configured.command
            )
        }
    )
}

/** 解析配置名称，并为三个内建项目使用 BedWars 模块语言。 */
private fun BedWarsSpectatorItem.localizedName(language: ModuleLanguage): String {
    displayName?.let { return it }
    val key = localizedKey("name") ?: return id
    return language.getMessage(key)
}

/** 解析配置说明，并为三个内建项目使用 BedWars 模块语言。 */
private fun BedWarsSpectatorItem.localizedLore(language: ModuleLanguage): List<String> {
    if (lore.isNotEmpty()) return lore
    val key = localizedKey("lore") ?: return emptyList()
    return listOf(language.getMessage(key))
}

/** 只为已知默认项目构造语言键，避免自定义 ID 产生缺失键警告。 */
private fun BedWarsSpectatorItem.localizedKey(field: String): String? {
    if (id !in DEFAULT_LOCALIZED_ITEM_IDS) return null
    return "bedwars.spectator_item_${id}_$field"
}

private val DEFAULT_LOCALIZED_ITEM_IDS = setOf("teleporter", "menu", "leave")
