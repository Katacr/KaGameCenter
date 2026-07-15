# Blockhunt 方块躲猫猫模块

更新时间：2026-07-13

本章记录主插件与 Blockhunt 的接入边界。完整玩法和配置见 `modules/blockhunt/技术细节.md`。

## 模块结构

```text
plugins/KaGameCenter/modules/blockhunt.jar
plugins/KaGameCenter/modules/blockhunt/config.yml
plugins/KaGameCenter/modules/blockhunt/lang/
plugins/KaGameCenter/modules/blockhunt/games/game/<localId>.yml
plugins/KaGameCenter/modules/blockhunt/games/map/<localId>/
plugins/KaGameCenter/maps/blockhunt/<map>/
```

Provider 注册游戏、监听器、管理员命令、托管游戏编辑器和模块聊天格式器。

## 当前玩法摘要

- 猎人与躲藏者按比例分配。
- 躲藏阶段结束后释放猎人。
- 躲藏者实体态显示移动方块伪装，真实玩家隐身且无碰撞。
- 双击潜行锁定后，在临时世界写入真实伪装方块并保存原 BlockData；解锁、被抓、离开和结束时恢复。
- 实体玩家、Interaction 命中盒、锁定真实方块和雪球命中都可触发抓捕。
- 被抓躲藏者可以按配置转为猎人。
- 猎人道具：全体躲藏者发光、范围探测、雪球补给。
- 躲藏者道具：致盲、冻结猎人、假方块、自身隐身。
- 每轮分别刷新阵营私有道具，同阵营首个触碰者获得。
- 最后狂暴阶段为躲藏者提供速度，并补充猎人雪球。
- 队伍聊天、房间聊天、统计、计分板和 ActionBar 已接入。

## 编辑字段

- 等待大厅。
- 猎人出生点。
- 躲藏者出生点。
- 游戏区域。
- 道具刷新点。

管理员通过 `/kgc admin manage` 创建托管游戏并打开私有快照，再使用模块编辑器或 `/kgc admin blockhunt ...` 设置字段。

## 主插件复用

- `TemporaryWorldService` / `MapEditorService` / `SelectionService`。
- `PacketDispatchService`：移动伪装、发光和私有拾取物。
- `TeamAssignmentService` / `GameTeamService`。
- `GameResultService`。
- `SidebarBoardRenderer`。
- `GameChatService`。
- `ModuleLanguage`。

主插件不保存 hunter/hider 身份、伪装材料、锁定方块或道具状态；这些都属于 `BlockhuntGameSession`。

## 关键清理

- 恢复锁定位置原始 BlockData。
- 移除 Interaction 命中盒和假方块实体。
- 清理 viewer 伪装/发光/拾取视觉。
- 恢复玩家游戏模式、飞行、速度、隐身和药水效果。
- 转队时同步 `GameTeamService`，避免聊天阵营错误。
- 锁定后作为游离观战本体的躲藏者不能拾取道具。

## 当前限制

- 没有伪装选择菜单，材料来自脚下方块或白名单候选。
- 锁定态是真实临时世界方块；自身隐身道具不会隐藏该真实方块。
- 私有拾取物由多个 viewer 各自显示，但共享模块道具 ID 决定唯一得主。
- 假方块道具当前为服务端 BlockDisplay，不是按 viewer 私有。
- 冻结通过位置和视角回拉实现，仍需关注高延迟手感。
