package org.katacr.kaGameCenter.phase

import kotlin.math.max

class GamePhaseTimer(
    initialTicks: Int = 0
) {
    var remainingTicks: Int = initialTicks.coerceAtLeast(0)
        private set

    val active: Boolean get() = remainingTicks > 0

    val secondsLeft: Int get() = max(0, (remainingTicks + 19) / 20)

    val isSecondBoundary: Boolean get() = remainingTicks > 0 && remainingTicks % 20 == 0

    fun resetTicks(ticks: Int) {
        remainingTicks = ticks.coerceAtLeast(0)
    }

    fun resetSeconds(seconds: Int) {
        resetTicks(seconds * 20)
    }

    fun tick(): Boolean {
        if (remainingTicks > 0) {
            remainingTicks--
        }
        return remainingTicks <= 0
    }

    fun clear() {
        remainingTicks = 0
    }
}
