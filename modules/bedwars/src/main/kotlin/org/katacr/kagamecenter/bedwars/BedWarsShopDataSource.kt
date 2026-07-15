package org.katacr.kagamecenter.bedwars

import org.bukkit.entity.Player
import org.katacr.kaGameCenter.game.GameRoomManager
import org.katacr.kaGameCenter.menu.chest.ChestMenuDataSource
import org.katacr.kaGameCenter.menu.chest.ChestMenuEntry

/** 为 KaGameCenter 箱子菜单提供当前玩家可见的 BedWars 商品。 */
class BedWarsShopDataSource(
    private val roomManager: GameRoomManager
) : ChestMenuDataSource {
    /** 按菜单上下文返回物品商品或队伍升级条目。 */
    override fun entries(player: Player, context: Map<String, String>): List<ChestMenuEntry> {
        val room = roomManager.getPlayerRoom(player) ?: return emptyList()
        val session = room.session as? BedWarsGameSession ?: return emptyList()
        val kind = BedWarsShopKind.parse(context["shop.kind"]) ?: return emptyList()
        return session.shopEntries(player, kind, context["shop.view"])
    }
}

/** 为升级菜单提供不参与商品分页的当前队伍陷阱队列槽。 */
class BedWarsTrapQueueDataSource(
    private val roomManager: GameRoomManager
) : ChestMenuDataSource {
    /** 按队伍当前队列顺序返回只读陷阱槽。 */
    override fun entries(player: Player, context: Map<String, String>): List<ChestMenuEntry> {
        val room = roomManager.getPlayerRoom(player) ?: return emptyList()
        val session = room.session as? BedWarsGameSession ?: return emptyList()
        return session.trapQueueEntries(player)
    }
}
