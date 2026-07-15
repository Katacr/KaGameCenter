# 菜单模板与动态按钮 TYPE

更新时间：2026-07-14

## 模板目录

KaMenu 主流程模板：

```text
plugins/KaGameCenter/menus/
```

内置箱子菜单模板：

```text
plugins/KaGameCenter/chest-menus/
```

资源只在缺失时释放，不覆盖管理员修改。

## 当前动态 TYPE

- `game_list`：已注册模块/托管游戏列表。
- `map_list`：模块公共地图或创建流程地图列表。
- `room_rows`：本服和跨服房间行，每行房间信息、加入、观战三项；可由菜单上下文按模块 ID 或完整玩法 ID 筛选。
- `player_slots`：房间成员槽位和队伍布局。

TYPE 节点是占位模板，`GameCenterMenuService` 会按当前 viewer、房间、游戏和地图上下文生成真实节点。槽位变量必须保存在对应按钮中，不能使用共享可变 map。

## 主要变量范围

- `viewer.*`
- `room.*`
- `game.*`
- `map.*`
- `team.*`
- `player.*`
- `target.*`
- 跨服房间：`room.server_id`、`room.global_id`、`room.group` 和远程动作。

精确变量以 `GameCenterMenuService` 当前构造代码和默认模板为准。模板中的普通动作交给 KaMenu；`kgc:` 动作由主插件处理。

## 房间列表布局

- 第一行：房间名称、加入、观战表头，占位宽度与房间行一致。
- 后续每个房间固定三项，避免 Dialog 自动布局换行。
- 房间信息显示四位 ID、名称、模块显示名、地图、人数和本地化状态。
- 无活动房间时在 Body 消息显示，不生成额外空行。
- `rooms.list_action`、`room.refresh_action`、`player.open_action` 和成员管理动作由服务生成，确保筛选列表进入详情后刷新与返回不会退回全局列表。

## 成员布局

- 个人竞技默认三列。
- 团队玩法最多四列，一列一个队伍。
- 队伍标题位于第一行，成员槽位按配置补空位。
- 房主点击成员可以打开二级操作菜单；普通成员不能执行房主管理动作。

## 维护约定

- 增加 TYPE 时同时更新默认模板、i18n、动作处理和开发指南。
- 内部功能使用 TYPE/`kgc:`；不需要主插件介入的功能保持渲染器原生 YAML。
- 动态状态变化后重开菜单，不尝试原地修改 Paper Dialog 组件。
