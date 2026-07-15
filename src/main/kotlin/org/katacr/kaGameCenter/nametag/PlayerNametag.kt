package org.katacr.kaGameCenter.nametag

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

data class PlayerNametag(
    val prefix: Component = Component.empty(),
    val suffix: Component = Component.empty(),
    val color: NamedTextColor = NamedTextColor.WHITE,
    val visibility: NametagVisibility = NametagVisibility.ALWAYS,
    val collisionRule: NametagCollisionRule = NametagCollisionRule.ALWAYS
)
