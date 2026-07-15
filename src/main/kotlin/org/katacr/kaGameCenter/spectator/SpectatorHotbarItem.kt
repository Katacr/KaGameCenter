package org.katacr.kaGameCenter.spectator

import org.bukkit.Material

/** 描述托管观战快捷栏中的材质、槽位、显示内容和受信任交互。 */
data class SpectatorHotbarItem(
    val id: String,
    val material: Material,
    val slot: Int,
    val enchanted: Boolean = false,
    val displayName: String? = null,
    val lore: List<String> = emptyList(),
    val action: SpectatorAction? = null,
    val command: String? = null
)
