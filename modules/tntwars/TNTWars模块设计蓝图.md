# TNT Wars 模块设计蓝图

更新时间：2026-06-21

## 模块定位

`tntwars` 是 KaGameCenter 的外置小游戏模块，目标是把 `Map-MiniGames` 中的 TNT Wars 数据包玩法重构为 Paper 插件模块。

参考数据包路径：

```text
/home/plugins/KaGameCenter/Map-MiniGames/datapacks/map_all/data/minecraft/function/tntwars/
```

原玩法摘要：

- 玩家分为 `队伍A` 和 `队伍B`。
- 地图位于空中，两队出生在地图两侧。
- 玩家定期获得随机爆炸道具。
- 使用 TNT、火球、苦力怕、TNT 雨等道具破坏地形并把敌方炸下虚空。
- 掉入虚空的玩家被淘汰并进入观战。
- 任意一队存活玩家归零，另一队获胜。
- 原数据包包含 `boat1`、`ballon1`、`cloud1`、`planet1` 四张地图。

## 设计目标

第一版优先巩固 GameCenter 的通用基底：

- 多房间并发运行。
- 每局使用私有临时世界，可破坏，结束后卸载删除。
- 队伍系统和队伍聊天。
- 游戏中观战。
- 可配置地图边界、出生点和淘汰高度。
- 随机道具发放。
- 爆炸物所有权记录和淘汰统计。
- 胜利、失败、击杀、死亡等统计提交。
- 模块私有 i18n、配置和管理菜单。

## 模块目录约定

开发目录：

```text
modules/tntwars/
  TNTWars模块设计蓝图.md
  技术细节.md
  build.gradle.kts
  src/main/kotlin/org/katacr/kagamecenter/tntwars/
  src/main/resources/
    lang/
      zh_CN.yml
      en_US.yml
    menus/
  maps/
    default/                        # 开发期公共模板占位
  games/
    game/                           # 开发期托管游戏配置占位
    map/                            # 开发期私有地图快照占位
```

运行目录：

```text
plugins/KaGameCenter/
  modules/
    tntwars.jar
    tntwars/
      config.yml
      lang/
        zh_CN.yml
        en_US.yml
      games/
        game/
          <游戏名>.yml
        map/
          <游戏名>/
  maps/
    tntwars/
      <公共模板>/
```

注意：

- 主插件只提供房间、地图编辑、选区、队伍、聊天、显示、统计、临时世界和发包基础设施。
- TNT Wars 的规则、道具、队伍名称、菜单文本、配置字段必须放在 `tntwars` 模块内。
- 公共地图模板只作为创建托管游戏时的源，不在编辑时直接修改。
- 每个托管游戏对应一份私有配置和一份私有地图快照。

## 游戏状态机

建议状态：

```text
WAITING
COUNTDOWN
RUNNING
ENDING
CLOSED
```

状态说明：

- `WAITING`：房间已创建，玩家可加入/离开/切换队伍。
- `COUNTDOWN`：人数满足后倒计时，冻结或限制移动，分配队伍。
- `RUNNING`：正式游戏，发放道具，监听爆炸、掉落、死亡、离线。
- `ENDING`：胜负已定，展示结算，锁定玩家交互。
- `CLOSED`：清理任务、实体、玩家状态、临时世界。

## 队伍设计

模块内部注册两个队伍：

```text
red   -> 队伍A / 红队
blue  -> 队伍B / 蓝队
```

分队策略：

- 默认自动平衡分配。
- 房主或管理员后续可在房间菜单中手动调整。
- 倒计时开始时锁定队伍。
- 运行中断线/离开按淘汰处理。

聊天行为：

- 普通聊天进入本队伍频道。
- `/a` 为房间全员频道，双方和观战者可见。
- `/g` 为服务器全局频道。
- 聊天格式由模块私有语言文件定义。

## 地图与托管游戏配置

托管游戏配置示例：

```yaml
id: TNT示例
module: tntwars
display-name: TNT示例
enabled: true
shared-map-template: tntwars/default
runtime-map-template: modules/tntwars/games/map/TNT示例
min-players: 2
max-players: 16
description: ""

tntwars:
  lobby:
    x: 502.0
    y: 20.0
    z: 240.0
    yaw: 0.0
    pitch: 0.0
  spectator-spawn:
    x: 471.0
    y: -25.0
    z: 316.0
    yaw: -90.0
    pitch: 0.0
  team-spawns:
    red:
      x: 502.0
      y: -37.0
      z: 342.0
      yaw: 180.0
      pitch: 0.0
    blue:
      x: 502.0
      y: -37.0
      z: 289.0
      yaw: 0.0
      pitch: 0.0
  play-region:
    min:
      x: 430
      y: -80
      z: 250
    max:
      x: 570
      y: 80
      z: 380
  void-y: -70.0
  item-interval-seconds: 15
  initial-item-delay-seconds: 10
```

配置说明：

- 坐标不保存世界名，只保存当前地图模板内的绝对坐标。
- 房间运行时创建随机临时世界，按相同坐标传送和判定。
- `play-region` 用于限制爆炸清理、道具雨落点、实体清理和玩家越界处理。
- `void-y` 用于掉落淘汰。

## 管理编辑器

模块编辑面板应接入主插件 `/kgc admin manage`。

编辑器显示：

- 当前托管游戏名。
- 私有地图快照状态。
- 大厅点状态。
- 观战点状态。
- 红队出生点状态。
- 蓝队出生点状态。
- 游戏区域状态。
- 淘汰高度。
- 道具发放间隔。

编辑按钮：

- 打开编辑世界。
- 保存私有地图快照。
- 关闭编辑世界。
- 设置大厅点。
- 设置观战点。
- 设置红队出生点。
- 设置蓝队出生点。
- 设置游戏区域。
- 设置淘汰高度为当前位置 Y。
- 预览出生点和游戏区域。

## 道具设计

原数据包道具：

| ID | 原道具 | 第一版建议 |
| --- | --- | --- |
| `tnt_minecart` | 连发 TNT 矿车 | 第二阶段实现 |
| `tnt` | 普通 TNT | 第一版实现 |
| `long_tnt` | 远距离 TNT | 第一版可作为普通 TNT 的高速度版本 |
| `creeper` | 点燃苦力怕 | 第二阶段实现 |
| `fireball` | 火球 | 第一版实现 |
| `tnt_bow` | TNT 弓 | 第二阶段实现 |
| `tnt_rain` | TNT 雨 | 第一版实现 |
| `creeper_rain` | 苦力怕雨 | 第二阶段实现 |
| `fireball_rain` | 火球雨 | 第二阶段实现 |

第一版道具池：

```yaml
items:
  interval-seconds: 15
  give-per-player: 1
  pool:
    tnt:
      weight: 4
      fuse-ticks: 50
      velocity: 1.2
      power: 4.0
    long_tnt:
      weight: 2
      fuse-ticks: 50
      velocity: 2.0
      power: 4.0
    fireball:
      weight: 3
      velocity: 1.5
      power: 5
    tnt_rain:
      weight: 1
      duration-seconds: 10
      drops-per-second: 4
```

实现方式：

- 道具使用 `ItemStack` + `PersistentDataContainer` 标记。
- 监听 `PlayerInteractEvent` 触发道具。
- 触发后消耗 1 个道具。
- 生成实体时记录 owner UUID 和所属房间 ID。
- 爆炸、火球、掉落淘汰时尝试归因最近一次伤害/击飞来源。

## 淘汰与胜负

淘汰触发：

- 玩家 Y 坐标低于 `void-y`。
- 玩家死亡。
- 运行中离开房间或离线。

淘汰后：

- 清空背包和药水效果。
- 切换到模块选择的观战模式。
- 从存活列表移除。
- 广播剩余人数。
- 更新统计：死亡 +1。

胜负判定：

- 红队存活数为 0，蓝队获胜。
- 蓝队存活数为 0，红队获胜。
- 两队同时归零，按平局或无胜者处理，第一版建议判定为平局并不提交胜利。

## 显示设计

BossBar：

- 当前阶段。
- 剩余时间或道具发放倒计时。

Scoreboard：

- 房间 ID。
- 地图名。
- 红队存活数。
- 蓝队存活数。
- 下一次道具时间。

ActionBar：

- 本队队友列表。
- 道具冷却/雨事件提示。

Title：

- 开局队伍提示。
- 玩家淘汰。
- 队伍胜利。

## 统计数据

第一版提交：

- `wins`
- `losses`
- `kills`
- `deaths`

建议额外预留模块私有统计：

- `tntwars.eliminations`
- `tntwars.items_used`
- `tntwars.rain_called`
- `tntwars.void_knockouts`

模块私有统计后续可以通过主插件通用数据库扩展接口或模块自建表实现。

## 多房间隔离要求

必须隔离：

- 房间状态。
- 队伍成员。
- 存活列表。
- 道具发放任务。
- 雨事件任务。
- 爆炸实体 owner 映射。
- 临时世界实体清理。
- 显示对象。

禁止使用全局 Bukkit team 名称作为唯一状态来源；应以 `room.id + teamId` 作为主键。

## 第一版范围

第一版完成后应达到：

- 已完成：模块可被 GameCenter 扫描并加载。
- 已完成：可通过 `/kgc admin manage` 创建 TNT Wars 托管游戏。
- 已完成：可编辑私有地图、设置大厅/出生点/区域/淘汰高度。
- 已完成：玩家可创建房间、加入房间、开始游戏。
- 已完成：自动分两队。
- 已完成：运行中定时发放随机爆炸道具。
- 已完成：普通 TNT、远距离 TNT、火球、TNT 雨可用。
- 已完成：掉落淘汰和队伍全灭胜负判定可用。
- 已完成：结算统计可用。
- 已完成：观战和聊天可用。

第二阶段再补：

- TNT 矿车连发。
- 点燃苦力怕。
- TNT 弓。
- 苦力怕雨。
- 火球雨。
- 多地图模板迁移：`boat1`、`ballon1`、`cloud1`、`planet1`。
- 道具权重和冷却 UI 化。
- 击杀归因优化。

## 风险点

- 爆炸会破坏地图，必须只在房间临时世界或私有编辑世界内允许。
- 爆炸实体较多时可能影响 TPS，需要限制雨事件数量和实体生命周期。
- Bukkit 原版爆炸归因不稳定，需要模块维护 projectile/explosive owner 映射。
- 地图边界必须配置准确，否则雨事件或实体清理可能影响非游戏区域。
- TNT Wars 比跑酷/躲猫猫更依赖物理和实体，需要优先做压力测试。

## 与 GameCenter 基底的加固关系

TNT Wars 适合检验和加固：

- 临时世界复制、卸载、删除。
- 多房间实体和任务隔离。
- 队伍服务。
- 观战服务。
- 聊天隔离。
- 显示服务。
- 统计服务。
- 地图编辑器保存私有快照。
- 高实体/爆炸场景下的清理能力。
