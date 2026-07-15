package org.katacr.kaGameCenter.chat

import java.util.UUID

/** 描述玩法会话最终选择的聊天频道、文本、格式变体和可选显式受众。 */
data class GameChatRoute(
    val channel: GameChatChannel,
    val message: String,
    val variant: String? = null,
    val audience: Set<UUID>? = null
)
