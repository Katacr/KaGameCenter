# Parkour 组件

更新时间：2026-07-13

本章只保留主插件视角的模块接入摘要。完整玩法与配置以 `modules/parkour/技术细节.md` 为准。

## 模块形态

```text
plugins/KaGameCenter/modules/parkour.jar
plugins/KaGameCenter/modules/parkour/config.yml
plugins/KaGameCenter/modules/parkour/lang/
plugins/KaGameCenter/modules/parkour/games/game/<localId>.yml
plugins/KaGameCenter/modules/parkour/games/map/<localId>/
plugins/KaGameCenter/maps/parkour/<map>/
```

模块通过 `ParkourModuleProvider` 注册游戏、监听器、管理员命令和 `ParkourManagedGameEditor`。

## 当前玩法

- 准备时从托管游戏私有地图快照创建房间世界。
- 玩家进入起点，开局冻结并显示倒计时。
- 检查点和终点使用区域坐标触发，不使用压力板。
- 玩家跌落到 `fall-y` 以下回到上一个检查点。
- 首名完成后进入结束倒计时，未完成玩家仍可继续冲线。
- 检查点按数字自动编号。
- 速度 Buff 使用单 viewer 私有发光拾取物。
- 模块接管自定义 sidebar 和 ActionBar。
- 观战策略来自模块配置，当前默认 managed。

## 托管游戏编辑

管理员先通过 `/kgc admin manage` 创建跑酷托管游戏，再打开该游戏的私有地图编辑会话。

可配置：

- 等待大厅。
- 起点出生点。
- 起点区域。
- 终点区域。
- 检查点区域和复活点。
- 速度 Buff 点。
- 路线预览。

设置字段时同步保存当前编辑世界到 `games/map/<localId>/`，不会修改公共模板。

## 当前命令

所有命令只操作管理员当前进入的托管游戏编辑会话：

```text
/kgc admin parkour help
/kgc admin parkour reload
/kgc admin parkour setlobby
/kgc admin parkour setstart
/kgc admin parkour setstartregion
/kgc admin parkour setfinish
/kgc admin parkour addcheckpoint [id]
/kgc admin parkour removecheckpoint <id>
/kgc admin parkour addspeedbuff [id]
/kgc admin parkour removebuff <id>
/kgc admin parkour preview
/kgc admin parkour previewall
```

旧 `<map> <route>` 参数和模块级路线配置不再属于当前管理流程。

## 主插件复用能力

- `TemporaryWorldService`
- `MapEditorService`
- `SelectionService`
- `PacketDispatchService`
- `GameResultService`
- `SidebarBoardRenderer`
- `ModuleLanguage`

主插件不能加入跑酷专属检查点、Buff 或胜负逻辑。
