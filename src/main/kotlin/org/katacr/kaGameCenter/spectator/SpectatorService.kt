package org.katacr.kaGameCenter.spectator

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.game.GameRoom
import org.katacr.kaGameCenter.game.GameState
import org.katacr.kaGameCenter.event.GameSpectatorFirstPersonFeedbackAction
import org.katacr.kaGameCenter.event.GameSpectatorFirstPersonFeedbackEvent
import org.katacr.kaGameCenter.event.GameSpectatorTargetChangedEvent
import org.katacr.kaGameCenter.event.GameSpectatorTargetMode
import org.katacr.kaGameCenter.event.GameSpectatorTargetSelectEvent
import org.katacr.kaGameCenter.i18n.LanguageManager
import java.time.Duration
import java.util.UUID

class SpectatorService(
    private val plugin: JavaPlugin,
    private val languageManager: LanguageManager
) {
    private val spectatorStates = linkedMapOf<UUID, SpectatorState>()
    private val followTargets = linkedMapOf<UUID, UUID>()
    private val itemKey = NamespacedKey(plugin, "spectator_action")
    private val commandKey = NamespacedKey(plugin, "spectator_command")

    fun canSpectate(room: GameRoom, policy: SpectatorPolicy): Boolean {
        if (!policy.enabled) return false
        if (room.state == GameState.CLOSED) return false
        if (!policy.allowDuringRunning && room.state == GameState.RUNNING) return false
        return true
    }

    fun enter(player: Player, room: GameRoom, policy: SpectatorPolicy = SpectatorPolicy.DEFAULT) {
        spectatorStates[player.uniqueId] = SpectatorState(
            room.id,
            policy.mode,
            policy.allowFollowPlayer,
            normalizedHotbarItems(policy.hotbarItems)
        )
        applyMode(player, policy)
        teleportToSpectatorSpawn(player, room)
        followInitialTarget(player, room, policy)
        player.sendActionBar(Component.text(languageManager.getMessage("spectator.action_joined", room.id), NamedTextColor.GRAY))
    }

    /** 为玩法内部淘汰者应用托管观战状态和指定观战点，但不改变房间参赛身份。 */
    fun enterEliminated(player: Player, room: GameRoom, policy: SpectatorPolicy, location: Location) {
        spectatorStates[player.uniqueId] = SpectatorState(
            room.id,
            policy.mode,
            policy.allowFollowPlayer,
            normalizedHotbarItems(policy.hotbarItems)
        )
        applyMode(player, policy)
        player.teleport(location)
        followInitialTarget(player, room, policy)
    }

    fun exit(player: Player) {
        stopFollowing(player)
        spectatorStates.remove(player.uniqueId)
        clearSpectatorTarget(player)
        player.sendActionBar(Component.text(languageManager.getMessage("spectator.action_left"), NamedTextColor.GRAY))
    }

    /** 清理房间观战状态，并为仍在线且有目标的玩家提交目标结束事件。 */
    fun clearRoom(roomId: String) {
        val removed = spectatorStates.filterValues { it.roomId == roomId }.keys
        removed.mapNotNull(Bukkit::getPlayer).forEach(::stopFollowing)
        spectatorStates.entries.removeIf { it.value.roomId == roomId }
        removed.forEach(followTargets::remove)
    }

    fun isSpectator(playerId: UUID): Boolean {
        return spectatorStates.containsKey(playerId)
    }

    fun isSpectator(player: Player): Boolean {
        return isSpectator(player.uniqueId)
    }

    fun roomId(playerId: UUID): String? {
        return spectatorStates[playerId]?.roomId
    }

    /** 选择观战目标，并按托管传送或原版第一人称模式提交变化。 */
    fun follow(player: Player, target: Player): Boolean {
        val state = spectatorStates[player.uniqueId] ?: return false
        if (!state.allowFollowPlayer) return false
        val mode = targetMode(state)
        val selectEvent = GameSpectatorTargetSelectEvent(state.roomId, player, target, mode)
        Bukkit.getPluginManager().callEvent(selectEvent)
        if (selectEvent.isCancelled) return false
        val previousTargetId = followTargets[player.uniqueId]
        val previousTarget = previousTargetId?.let(Bukkit::getPlayer)
        if (state.mode != SpectatorMode.VANILLA) {
            if (!player.teleport(target.location)) return false
            followTargets[player.uniqueId] = target.uniqueId
            player.sendActionBar(Component.text(languageManager.getMessage("spectator.following", target.name), NamedTextColor.GRAY))
            publishTargetChanged(state, player, previousTargetId, previousTarget, target)
            return true
        }
        if (player.gameMode != GameMode.SPECTATOR) {
            player.gameMode = GameMode.SPECTATOR
        }
        player.spectatorTarget = target
        followTargets[player.uniqueId] = target.uniqueId
        showFirstPersonFeedback(
            state,
            player,
            target.uniqueId,
            target,
            GameSpectatorFirstPersonFeedbackAction.ENTER
        )
        player.sendActionBar(Component.text(languageManager.getMessage("spectator.following", target.name), NamedTextColor.GRAY))
        publishTargetChanged(state, player, previousTargetId, previousTarget, target)
        return true
    }

    /** 清除玩家当前观战目标，并在实际存在旧目标时发布结束变化。 */
    fun stopFollowing(player: Player): Boolean {
        val state = spectatorStates[player.uniqueId] ?: return false
        val previousTargetId = followTargets.remove(player.uniqueId) ?: return false
        val previousTarget = Bukkit.getPlayer(previousTargetId)
        clearSpectatorTarget(player)
        showFirstPersonFeedback(
            state,
            player,
            previousTargetId,
            previousTarget,
            GameSpectatorFirstPersonFeedbackAction.LEAVE
        )
        publishTargetChanged(state, player, previousTargetId, previousTarget, target = null)
        return true
    }

    /** 清除所有仍指向指定玩家的在线观战镜头。 */
    fun stopFollowingTarget(targetId: UUID) {
        followTargets.filterValues { it == targetId }.keys.toList().forEach { spectatorId ->
            Bukkit.getPlayer(spectatorId)?.let(::stopFollowing) ?: followTargets.remove(spectatorId)
        }
    }

    fun nextTarget(player: Player, room: GameRoom, canFollow: (Player) -> Boolean = { true }): Player? {
        val targets = room.players
            .mapNotNull(Bukkit::getPlayer)
            .filter { it.uniqueId != player.uniqueId && it.isOnline && canFollow(it) }
            .sortedBy { it.name.lowercase() }
        if (targets.isEmpty()) return null

        val current = followTargets[player.uniqueId]
        val currentIndex = targets.indexOfFirst { it.uniqueId == current }
        return targets[(currentIndex + 1).floorMod(targets.size)]
    }

    fun action(itemStack: ItemStack?): SpectatorAction? {
        val meta = itemStack?.itemMeta ?: return null
        val value = meta.persistentDataContainer.get(itemKey, PersistentDataType.STRING) ?: return null
        return runCatching { SpectatorAction.valueOf(value.uppercase()) }.getOrNull()
    }

    /** 读取仅由观战服务写入物品 PDC 的玩家命令，并再次限制输入长度。 */
    fun command(itemStack: ItemStack?): String? {
        val meta = itemStack?.itemMeta ?: return null
        return meta.persistentDataContainer.get(commandKey, PersistentDataType.STRING)
            ?.trim()
            ?.removePrefix("/")
            ?.take(MAX_COMMAND_LENGTH)
            ?.takeIf(String::isNotBlank)
    }

    fun sendHotbar(player: Player) {
        val state = spectatorStates[player.uniqueId] ?: return
        if (state.mode != SpectatorMode.MANAGED) return

        player.inventory.clear()
        state.hotbarItems.forEach { configured ->
            player.inventory.setItem(configured.slot, hotbarItem(configured))
        }
        player.updateInventory()
    }

    private fun applyMode(player: Player, policy: SpectatorPolicy) {
        player.isCollidable = false
        player.collidableExemptions.clear()
        when (policy.mode) {
            SpectatorMode.VANILLA -> {
                player.gameMode = GameMode.SPECTATOR
                player.spectatorTarget = null
            }
            SpectatorMode.MANAGED -> {
                clearSpectatorTarget(player)
                player.gameMode = GameMode.ADVENTURE
                player.isInvisible = true
                player.isInvulnerable = true
                player.allowFlight = policy.allowFreeFly
                player.isFlying = policy.allowFreeFly
                sendHotbar(player)
            }
        }
    }

    /** 在原版观战模式入场后通过统一事件路径选择首个在线目标。 */
    private fun followInitialTarget(player: Player, room: GameRoom, policy: SpectatorPolicy) {
        if (policy.mode != SpectatorMode.VANILLA || !policy.allowFollowPlayer) return
        room.players.firstNotNullOfOrNull(Bukkit::getPlayer)?.let { target -> follow(player, target) }
    }

    /** 把内部观战模式映射为公开目标动作类型。 */
    private fun targetMode(state: SpectatorState): GameSpectatorTargetMode {
        return if (state.mode == SpectatorMode.VANILLA) {
            GameSpectatorTargetMode.FIRST_PERSON
        } else {
            GameSpectatorTargetMode.TELEPORT
        }
    }

    /** 发布观战目标完成提交后的不可取消前后快照。 */
    private fun publishTargetChanged(
        state: SpectatorState,
        spectator: Player,
        previousTargetId: UUID?,
        previousTarget: Player?,
        target: Player?
    ) {
        val targetId = target?.uniqueId
        if (previousTargetId == targetId) return
        Bukkit.getPluginManager().callEvent(
            GameSpectatorTargetChangedEvent(
                state.roomId,
                spectator,
                targetMode(state),
                previousTargetId,
                previousTarget,
                targetId,
                target
            )
        )
    }

    /** 为原版第一人称镜头发布可修改反馈，并按非负有界 tick 显示标题。 */
    private fun showFirstPersonFeedback(
        state: SpectatorState,
        spectator: Player,
        targetId: UUID?,
        target: Player?,
        action: GameSpectatorFirstPersonFeedbackAction
    ) {
        if (state.mode != SpectatorMode.VANILLA) return
        val event = GameSpectatorFirstPersonFeedbackEvent(
            state.roomId,
            spectator,
            targetId,
            target,
            action,
            title = Component.text(languageManager.getMessage(
                if (action == GameSpectatorFirstPersonFeedbackAction.ENTER) {
                    "spectator.first_person_enter_title"
                } else {
                    "spectator.first_person_leave_title"
                },
                target?.name ?: "-"
            )),
            subtitle = Component.text(languageManager.getMessage(
                if (action == GameSpectatorFirstPersonFeedbackAction.ENTER) {
                    "spectator.first_person_enter_subtitle"
                } else {
                    "spectator.first_person_leave_subtitle"
                }
            ))
        )
        Bukkit.getPluginManager().callEvent(event)
        if (event.title == null && event.subtitle == null) return
        spectator.showTitle(Title.title(
            event.title ?: Component.empty(),
            event.subtitle ?: Component.empty(),
            Title.Times.times(
                titleDuration(event.fadeInTicks),
                titleDuration(event.stayTicks),
                titleDuration(event.fadeOutTicks)
            )
        ))
    }

    /** 把监听器提供的标题 tick 限制后转换为 Adventure 时长。 */
    private fun titleDuration(ticks: Int): Duration {
        return Duration.ofMillis(ticks.coerceIn(0, MAX_TITLE_TICKS) * 50L)
    }

    private fun clearSpectatorTarget(player: Player) {
        if (player.gameMode == GameMode.SPECTATOR) {
            player.spectatorTarget = null
        }
    }

    private fun teleportToSpectatorSpawn(player: Player, room: GameRoom) {
        val location = spectatorSpawn(room) ?: return
        player.teleport(location)
    }

    private fun spectatorSpawn(room: GameRoom): Location? {
        return room.world?.spawnLocation ?: Bukkit.getWorlds().firstOrNull()?.spawnLocation
    }

    /** 生成包含服务命名空间交互标记的托管观战物品。 */
    private fun hotbarItem(configured: SpectatorHotbarItem): ItemStack {
        return ItemStack(configured.material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(displayName(configured), NamedTextColor.AQUA))
                if (configured.lore.isNotEmpty()) {
                    meta.lore(configured.lore.map { Component.text(it, NamedTextColor.GRAY) })
                }
                if (configured.enchanted) meta.setEnchantmentGlintOverride(true)
                if (configured.action != null) {
                    meta.persistentDataContainer.set(itemKey, PersistentDataType.STRING, configured.action.name)
                } else {
                    configured.command?.let { command ->
                        meta.persistentDataContainer.set(commandKey, PersistentDataType.STRING, command)
                    }
                }
            }
        }
    }

    /** 为未指定名称的内建动作提供主插件语言回退，自定义项回退其 ID。 */
    private fun displayName(configured: SpectatorHotbarItem): String {
        configured.displayName?.takeIf(String::isNotBlank)?.let { return it }
        val key = when (configured.action) {
            SpectatorAction.FOLLOW -> "spectator.item_follow"
            SpectatorAction.MENU -> "spectator.item_menu"
            SpectatorAction.LEAVE -> "spectator.item_leave"
            null -> return configured.id
        }
        return languageManager.getMessage(key)
    }

    /** 过滤无效槽位和空气材质，规范化命令并让后配置的重复槽位覆盖前项。 */
    private fun normalizedHotbarItems(items: List<SpectatorHotbarItem>): List<SpectatorHotbarItem> {
        val bySlot = linkedMapOf<Int, SpectatorHotbarItem>()
        items.forEach { item ->
            if (item.slot !in 0..8 || item.material.isAir) return@forEach
            val command = item.command
                ?.trim()
                ?.removePrefix("/")
                ?.take(MAX_COMMAND_LENGTH)
                ?.takeIf(String::isNotBlank)
            bySlot[item.slot] = item.copy(command = command)
        }
        return bySlot.values.sortedBy(SpectatorHotbarItem::slot)
    }

    private data class SpectatorState(
        val roomId: String,
        val mode: SpectatorMode,
        val allowFollowPlayer: Boolean,
        val hotbarItems: List<SpectatorHotbarItem>
    )

    private fun Int.floorMod(modulus: Int): Int {
        return ((this % modulus) + modulus) % modulus
    }

    private companion object {
        const val MAX_COMMAND_LENGTH = 256
        const val MAX_TITLE_TICKS = 72_000
    }
}
