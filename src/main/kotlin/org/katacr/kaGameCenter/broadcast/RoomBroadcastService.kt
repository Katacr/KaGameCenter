package org.katacr.kaGameCenter.broadcast

import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.i18n.ModuleLanguage
import java.time.Duration

class RoomBroadcastService {
    fun players(room: GameRoom): List<Player> {
        return room.players.mapNotNull(Bukkit::getPlayer)
    }

    fun participants(room: GameRoom): List<Player> {
        return (room.players + room.spectators).mapNotNull(Bukkit::getPlayer)
    }

    fun message(room: GameRoom, component: Component, includeSpectators: Boolean = true) {
        audience(room, includeSpectators).forEach { it.sendMessage(component) }
    }

    fun message(room: GameRoom, text: String, includeSpectators: Boolean = true) {
        message(room, Component.text(text), includeSpectators)
    }

    fun localized(room: GameRoom, language: ModuleLanguage, key: String, vararg args: Any, includeSpectators: Boolean = true) {
        message(room, language.getMessage(key, *args), includeSpectators)
    }

    fun actionBar(room: GameRoom, component: Component, includeSpectators: Boolean = false) {
        audience(room, includeSpectators).forEach { it.sendActionBar(component) }
    }

    fun actionBar(room: GameRoom, text: String, includeSpectators: Boolean = false) {
        actionBar(room, Component.text(text), includeSpectators)
    }

    fun title(
        room: GameRoom,
        title: Component,
        subtitle: Component = Component.empty(),
        includeSpectators: Boolean = false,
        times: Title.Times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
    ) {
        val payload = Title.title(title, subtitle, times)
        audience(room, includeSpectators).forEach { it.showTitle(payload) }
    }

    private fun audience(room: GameRoom, includeSpectators: Boolean): List<Player> {
        return if (includeSpectators) participants(room) else players(room)
    }
}
