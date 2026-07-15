# KaMenu 动态菜单接入

更新时间：2026-07-14

## 当前定位

KaMenu 是主流程 YAML 菜单的首选渲染器，适合房间列表、托管游戏管理、下拉选择和输入框。内置箱子菜单用于 fallback 和操作型 UI，Paper Dialog 是最后兜底。

主插件通过反射调用 KaMenu API，避免编译时绑定具体 KaMenu jar：

- `KaMenuAPI.openConfig(player, config, contextId)`。
- `KaMenuAPI.registerActionHandler("kgc", handler)`。
- 关闭时注销 `kgc` handler。

当前 Paper 26.2 测试服安装 KaMenu `1.6.0`。

## 模板目录

```text
plugins/KaGameCenter/menus/
```

主插件内置模板：

- `main.yml`
- `games.yml`
- `create_game.yml`
- `create_map.yml`
- `rooms.yml`
- `room.yml`
- `room_member.yml`
- `maps.yml`
- `map_detail.yml`

模板只在缺失时释放，管理员可以修改显示文本、布局和普通动作。

## 动态 TYPE

主插件识别的动态节点包括：

- `game_list`
- `map_list`
- `room_rows`
- `player_slots`

`room_rows` 每个房间固定生成三项：房间信息、加入、观战。首行表头也固定三项且宽度对应，避免 Dialog 自动流式布局错位。无活动房间时只在消息区域提示，不额外生成空行。渲染器可接收玩法与分组筛选：模块 ID 匹配该模块全部托管玩法，完整 globalId 只匹配单个玩法，非默认分组匹配 `selector-group`；本地与 Velocity 标签使用同一规则。

玩家槽位按房间和队伍动态渲染；槽位变量包含玩家 UUID、名称、房间 ID 和队伍信息，点击时不会串到其他行。

## 动作命名空间

主插件动作使用 `kgc:`，主要包括：

- 打开主菜单、创建流程、房间列表、房间详情、成员菜单和地图菜单；`kgc:open-rooms [gameId] [group]` 可打开全局、指定玩法或指定分组列表。
- 创建、加入、观战、离开、启动和关闭房间。
- 地图选择、创建、设置出生点和重载。
- 托管游戏创建和模块编辑器动作。
- 远程房间代理加入。

没有被主插件识别的普通动作由 KaMenu 自身处理。

## 权限与身份保护

- 普通玩家可以创建、浏览、加入、观战和离开房间。
- 地图和托管游戏管理要求 `kagamecenter.admin`。
- 动作层再次校验权限，不能只依赖按钮是否显示。
- 已在房间中的正式玩家不能通过菜单切换到观战，也不能加入其他房间。
- 重复加入当前房间或重复观战当前房间是幂等操作。

## 刷新

Paper Dialog 不能原地更新，房间人数、状态等变化通过重新打开菜单刷新。会重开菜单的动作不应先执行 close。筛选列表进入房间详情和成员菜单后，刷新、返回、踢出及转让房主动作都会携带原筛选 ID。数据目录中的旧模板若仍使用原版单一默认动作，服务会在内存中补齐上下文；管理员自定义动作不会被覆盖。

## 回退链

```text
KaMenu openConfig
  -> ChestMenuService.openConfig
  -> GameCenterDialogService.openMainDialog
```

`openExternalConfig(...)` 只有前两层；Paper Dialog 兜底用于主菜单入口。
