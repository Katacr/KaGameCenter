package org.katacr.kaGameCenter.api

interface GameModuleProvider {
    fun onLoad(context: GameModuleContext)

    fun onUnload() {}
}
