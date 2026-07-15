package org.katacr.kagamecenter.bedwars

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** 通过 Bukkit 服务注册表反射访问可选 Vault 经济，不给模块增加运行时硬依赖。 */
internal class BedWarsVaultEconomy(
    private val plugin: JavaPlugin
) {
    /** 返回玩家非负有限余额；Vault、经济提供者或兼容方法缺失时返回 null。 */
    fun balance(player: Player): Double? {
        val provider = economyProvider() ?: return null
        val method = provider.javaClass.methods
            .asSequence()
            .filter { it.name == "getBalance" && it.parameterCount == 1 }
            .sortedBy { it.parameterTypes[0] == String::class.java }
            .firstOrNull { method ->
                method.parameterTypes[0] == String::class.java ||
                    method.parameterTypes[0].isAssignableFrom(player.javaClass)
            }
            ?: return null
        val argument = if (method.parameterTypes[0] == String::class.java) player.name else player
        return runCatching { (method.invoke(provider, argument) as? Number)?.toDouble() }
            .getOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
    }

    /** 从玩家账户扣除正数金额，仅在 Vault EconomyResponse 明确成功时返回 true。 */
    fun withdraw(player: Player, amount: Int): Boolean {
        if (amount <= 0) return true
        val provider = economyProvider() ?: return false
        val method = provider.javaClass.methods
            .asSequence()
            .filter { it.name == "withdrawPlayer" && it.parameterCount == 2 }
            .filter { it.parameterTypes[1] == Double::class.javaPrimitiveType }
            .sortedBy { it.parameterTypes[0] == String::class.java }
            .firstOrNull { candidate ->
                candidate.parameterTypes[0] == String::class.java ||
                    candidate.parameterTypes[0].isAssignableFrom(player.javaClass)
            }
            ?: return false
        val argument = if (method.parameterTypes[0] == String::class.java) player.name else player
        val response = runCatching { method.invoke(provider, argument, amount.toDouble()) }.getOrNull() ?: return false
        val success = response.javaClass.methods.firstOrNull {
            it.name == "transactionSuccess" && it.parameterCount == 0
        } ?: return false
        return runCatching { success.invoke(response) as? Boolean }.getOrNull() == true
    }

    /** 向玩家账户存入正数金额，仅在 Vault EconomyResponse 明确成功时返回 true。 */
    fun deposit(player: Player, amount: Int): Boolean {
        if (amount <= 0) return true
        val provider = economyProvider() ?: return false
        val method = provider.javaClass.methods
            .asSequence()
            .filter { it.name == "depositPlayer" && it.parameterCount == 2 }
            .filter { it.parameterTypes[1] == Double::class.javaPrimitiveType }
            .sortedBy { it.parameterTypes[0] == String::class.java }
            .firstOrNull { candidate ->
                candidate.parameterTypes[0] == String::class.java ||
                    candidate.parameterTypes[0].isAssignableFrom(player.javaClass)
            }
            ?: return false
        val argument = if (method.parameterTypes[0] == String::class.java) player.name else player
        val response = runCatching { method.invoke(provider, argument, amount.toDouble()) }.getOrNull() ?: return false
        val success = response.javaClass.methods.firstOrNull {
            it.name == "transactionSuccess" && it.parameterCount == 0
        } ?: return false
        return runCatching { success.invoke(response) as? Boolean }.getOrNull() == true
    }

    /** 查找当前 Vault Economy 服务提供者，未安装或未注册时不缓存失败结果。 */
    private fun economyProvider(): Any? {
        if (!plugin.server.pluginManager.isPluginEnabled("Vault")) return null
        return runCatching {
            val economyType = Class.forName("net.milkbowl.vault.economy.Economy")
            @Suppress("UNCHECKED_CAST")
            plugin.server.servicesManager.getRegistration(economyType as Class<Any>)?.provider
        }.getOrNull()
    }
}
