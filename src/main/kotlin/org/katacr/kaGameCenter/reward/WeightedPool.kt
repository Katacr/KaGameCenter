package org.katacr.kaGameCenter.reward

import java.util.concurrent.ThreadLocalRandom

class WeightedPool<T>(
    entries: Collection<Entry<T>>
) {
    data class Entry<T>(
        val value: T,
        val weight: Int
    )

    private val entries = entries.filter { it.weight > 0 }
    private val totalWeight = this.entries.sumOf { it.weight }

    val isEmpty: Boolean get() = entries.isEmpty() || totalWeight <= 0

    fun next(random: ThreadLocalRandom = ThreadLocalRandom.current()): T? {
        if (isEmpty) return null
        var cursor = random.nextInt(totalWeight)
        entries.forEach { entry ->
            cursor -= entry.weight
            if (cursor < 0) return entry.value
        }
        return entries.lastOrNull()?.value
    }

    companion object {
        fun <T> of(weightedValues: Map<T, Int>): WeightedPool<T> {
            return WeightedPool(weightedValues.map { (value, weight) -> Entry(value, weight) })
        }
    }
}
