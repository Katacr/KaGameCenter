package org.katacr.kagamecenter.bedwars

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.katacr.kaGameCenter.api.GameModuleContext
import org.katacr.kaGameCenter.api.GameModuleProvider
import org.katacr.kaGameCenter.chat.GameChatChannel
import org.katacr.kaGameCenter.chat.GameChatContext
import org.katacr.kaGameCenter.i18n.ModuleLanguage

/** 装配 BedWars 模块服务并注册玩法、管理命令和事件入口。 */
class BedWarsModuleProvider : GameModuleProvider {
    private var configService: BedWarsConfigService? = null
    private var quickBuyService: BedWarsQuickBuyService? = null

    override fun onLoad(context: GameModuleContext) {
        val configService = BedWarsConfigService(context.dataFolder)
        configService.reload()
        this.configService = configService
        val quickBuyService = BedWarsQuickBuyService(context.dataFolder)
        this.quickBuyService = quickBuyService
        val language = ModuleLanguage(
            context.plugin,
            context.languageManager,
            context.dataFolder,
            "lang"
        ) { path -> javaClass.classLoader.getResourceAsStream(path) }
        language.reload()
        context.registerChatFormatter { chat ->
            if (!configService.isChatFormattingEnabled()) return@registerChatFormatter null
            val key = when (chat.variant) {
                "spectator" -> "bedwars.chat_spectator"
                "shout" -> "bedwars.chat_shout"
                else -> when (chat.channel) {
                    GameChatChannel.TEAM -> "bedwars.chat_team"
                    GameChatChannel.ROOM -> "bedwars.chat_room"
                    GameChatChannel.GLOBAL -> null
                }
            }
            key?.let { expandChatTemplate(chat, language.getMessage(it)) }
        }
        context.registerModule(
            BedWarsGameModule(
                context.plugin,
                configService,
                context.worldService,
                language,
                context.roomManager,
                context.teamService,
                context.teamAssignmentService,
                context.roomTaskService,
                context.resultService,
                context.playerRuntimeStateService,
                context.roomBroadcastService,
                context.nametagService,
                context.chestMenuService,
                quickBuyService,
                context.eliminationService,
                context.spectatorService,
                context.roomResourceScopeService,
                context.api.velocityBridgeService
            )
        )
        val editor = BedWarsManagedGameEditor(
            configService,
            language,
            context.packetService,
            context.worldService,
            context.mapEditorService,
            context.managedGameCatalog,
            context.menuService,
            context.editorPointCaptureService
        )
        context.registerAdminCommand(
            BedWarsAdminCommand(
                configService,
                language,
                context.mapEditorService,
                context.managedGameCatalog,
                editor
            )
        )
        context.registerGameEditor(editor)
        context.registerChestMenuDataSource("shop_items", BedWarsShopDataSource(context.roomManager))
        context.registerChestMenuDataSource("trap_queue", BedWarsTrapQueueDataSource(context.roomManager))
        context.registerListener(BedWarsListener(context.plugin, context.roomManager, configService, language))
    }

    /** 展开参考聊天格式支持的玩家、队伍、等级及 Vault 前后缀 token。 */
    private fun expandChatTemplate(chat: GameChatContext, template: String): String {
        val session = chat.room.session as? BedWarsGameSession
        val (vaultPrefix, vaultSuffix) = session?.chatAffixes(chat.player) ?: ("" to "")
        val displayName = PlainTextComponentSerializer.plainText().serialize(chat.player.displayName())
        val teamName = chat.team?.displayName ?: "-"
        val replacements = linkedMapOf(
            "{vPrefix}" to plainLegacyText(vaultPrefix),
            "{vSuffix}" to plainLegacyText(vaultSuffix),
            "{playername}" to chat.player.name,
            "{player}" to displayName,
            "{level}" to (session?.chatLevel(chat.player.uniqueId) ?: 1).toString(),
            "{team}" to teamName,
            "{message}" to chat.message
        )
        when {
            chat.variant == "spectator" -> replacements.putAll(mapOf(
                "{0}" to chat.player.name,
                "{1}" to chat.message
            ))
            chat.variant == "shout" || chat.channel == GameChatChannel.TEAM -> replacements.putAll(mapOf(
                "{0}" to teamName,
                "{1}" to chat.player.name,
                "{2}" to chat.message
            ))
            chat.channel == GameChatChannel.ROOM -> replacements.putAll(mapOf(
                "{0}" to chat.room.id,
                "{1}" to chat.player.name,
                "{2}" to chat.message
            ))
        }
        return CHAT_TEMPLATE_TOKEN.replace(template) { match -> replacements[match.value] ?: match.value }
    }

    /** 去除可选前后缀中的旧式样式码，避免纯文本聊天显示字面颜色控制符。 */
    private fun plainLegacyText(value: String): String {
        return PlainTextComponentSerializer.plainText().serialize(
            LegacyComponentSerializer.legacyAmpersand().deserialize(value.replace('§', '&'))
        )
    }

    override fun onUnload() {
        quickBuyService?.flush()
        quickBuyService = null
        configService = null
    }

    private companion object {
        val CHAT_TEMPLATE_TOKEN = Regex("\\{(?:vPrefix|vSuffix|playername|player|level|team|message|0|1|2)}")
    }
}
