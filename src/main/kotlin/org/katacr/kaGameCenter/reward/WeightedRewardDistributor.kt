package org.katacr.kaGameCenter.reward

import org.bukkit.entity.Player

class WeightedRewardDistributor<T>(
    private val pool: WeightedPool<T>
) {
    fun distribute(
        players: Iterable<Player>,
        amountPerPlayer: Int = 1,
        reward: (Player, T) -> Unit
    ): Int {
        var distributed = 0
        players.forEach { player ->
            repeat(amountPerPlayer.coerceAtLeast(0)) {
                val value = pool.next() ?: return@repeat
                reward(player, value)
                distributed++
            }
        }
        return distributed
    }
}
