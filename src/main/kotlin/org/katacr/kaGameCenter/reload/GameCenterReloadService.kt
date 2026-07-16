package org.katacr.kaGameCenter.reload

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.katacr.kaGameCenter.game.GameMapManager
import org.katacr.kaGameCenter.i18n.LanguageManager
import org.katacr.kaGameCenter.module.ManagedGameModuleService
import org.katacr.kaGameCenter.module.ManagedModuleReloadResult
import java.io.File

/** 统一执行主配置、主语言、公共地图模板和托管模块的受控热重载。 */
class GameCenterReloadService(
    private val plugin: JavaPlugin,
    private val languageManager: LanguageManager,
    private val mapManager: GameMapManager,
    private val moduleService: ManagedGameModuleService
) {
    /** 严格校验并重载主配置；数据库和跨服连接等启动期对象不会在此重建。 */
    fun reloadConfig(): CoreReloadResult {
        return runCatching {
            validateYaml(File(plugin.dataFolder, "config.yml"))
            plugin.reloadConfig()
            CoreReloadResult(true)
        }.getOrElse { CoreReloadResult(false, it.message ?: it.javaClass.simpleName) }
    }

    /** 严格校验当前语言文件并刷新主插件语言缓存。 */
    fun reloadLanguage(): CoreReloadResult {
        return runCatching {
            val locale = plugin.config.getString("language", "zh_CN") ?: "zh_CN"
            validateYaml(File(plugin.dataFolder, "lang/$locale.yml"))
            languageManager.reload()
            CoreReloadResult(true, value = languageManager.getCurrentLanguage())
        }.getOrElse { CoreReloadResult(false, it.message ?: it.javaClass.simpleName) }
    }

    /** 仅刷新主插件公共地图模板入口，不读取模块私有游戏配置或地图快照。 */
    fun reloadMaps(): CoreReloadResult {
        return runCatching {
            val result = mapManager.reload()
            check(result.success) { result.message }
            CoreReloadResult(true)
        }.getOrElse { CoreReloadResult(false, it.message ?: it.javaClass.simpleName) }
    }

    /** 安全重载指定模块，事务内会先关闭其房间和地图编辑会话。 */
    fun reloadModule(moduleId: String): ManagedModuleReloadResult = moduleService.reloadModule(moduleId)

    /** 安全重载全部已加载或已配置模块。 */
    fun reloadAllModules(): List<ManagedModuleReloadResult> = moduleService.reloadAllModules()

    /** 返回命令补全可使用的模块 ID。 */
    fun reloadableModuleIds(): List<String> = moduleService.reloadableModuleIds()

    private fun validateYaml(file: File) {
        require(file.isFile) { "YAML file not found: ${file.absolutePath}" }
        YamlConfiguration().load(file)
    }
}

/** 描述核心配置、语言或公共地图模板重载的成功状态和可选结果值。 */
data class CoreReloadResult(
    val success: Boolean,
    val detail: String? = null,
    val value: String? = null
)
