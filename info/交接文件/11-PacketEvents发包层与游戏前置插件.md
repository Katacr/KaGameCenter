# PacketEvents 发包层与游戏前置插件

更新时间：2026-07-13

## 依赖策略

- PacketEvents `2.12.2` 为 `compileOnly` 和 softdepend。
- 主插件通过 `PacketDispatchService` 暴露业务能力。
- 模块不 import PacketEvents wrapper，也不使用 NMS。
- PacketEvents 未安装时主插件可启动，发包接口为空操作。
- 玩法硬依赖视觉能力时，在 `GameDefinition.requiredPlugins` 声明 `PacketEvents`。

## 当前能力

- 玩家伪装为跟随移动的方块。
- 玩家伪装为跟随移动的生物。
- 按 viewer 持续叠加真实玩家隐身/发光 metadata。
- 方块位置发光轮廓。
- 玩家发光。
- 单 viewer 私有掉落物和可拾取物。
- 拾取物颜色、缩放、持续时间和回调。
- 私有信标光柱。
- ActionBar probe。
- viewer 和插件生命周期清理。
- 名牌底层 Teams 包发送/移除；上层由 `PlayerNametagService` 管理。

## 实现要点

### 方块伪装

使用 Paper `BlockDisplay`，`setVisibleByDefault(false)` 后只对指定 viewer `showEntity`。跟随周期为 2 tick，依赖 `teleportDuration/interpolationDuration` 平滑移动；固定 yaw/pitch 避免方块随视角倾斜。

### 生物伪装

使用 `WrapperPlayServerSpawnEntity` 创建假实体，按移动距离选择相对移动或 teleport，并发送 rotation/head look。真实玩家对指定 viewer 保持隐身 overlay。

### 发光轮廓

空气或普通方块本身没有可直接发光的静态方块包。当前在目标位置生成无碰撞 BlockDisplay，并用 Teams 颜色显示轮廓，不修改真实地图方块。

### 私有拾取

服务创建只对指定 viewer 可见的 ItemDisplay，并定期检查距离。模块回调必须再次验证房间、角色、阶段和存活状态；服务不替模块决定奖励合法性。

## 测试命令

需要 `kagamecenter.admin`：

```text
/kgc admin packet probe [message]
/kgc admin packet blockself [material] [seconds]
/kgc admin packet mobself [entityType] [seconds]
/kgc admin packet blockglow [seconds]
/kgc admin packet playerglow [player] [seconds]
/kgc admin packet drop [material] [seconds] [scale]
/kgc admin packet beam [seconds] [color]
/kgc admin packet clear
```

## 已知边界

- 当前每个活动伪装/私有拾取物可能拥有自己的跟随或拾取任务；大量并发视觉对象时应合并调度。
- scoreboard entry 同时只能属于一个客户端队伍，玩家名牌、发光颜色和无碰撞 Teams 叠加时需要实服验证并考虑统一状态。
- viewer 侧 `CollisionRule` 不能替代服务端完整碰撞规则；需要同队碰撞例外的模块应同步使用 Bukkit `isCollidable` 与 `collidableExemptions`，并通过玩家快照恢复原值。
- `clearViewer` 会清理该 viewer 的所有临时视觉，模块调用时应确认不会误清同房间其他仍需保留的视觉。
