# Parkour 运行时与地图编辑

更新时间：2026-07-13

## 运行阶段

```text
WAITING_START -> START_COUNTDOWN -> RUNNING -> FINISH_COUNTDOWN -> RESULT -> CLOSED
```

- 所有玩家传送至起点后冻结 5 秒并显示 Title 倒计时。
- RUNNING 中按顺序触发检查点区域。
- 跌落后传送到上一个检查点复活点；尚未通过检查点则回到起点。
- 首个完成者触发 finish countdown，其他玩家仍可继续冲线。
- 结算记录排名和点数，然后延迟关闭房间。

## 坐标模型

托管游戏配置只保存模板坐标系的绝对坐标，不保存编辑世界名或运行世界名：

- 点位按方块坐标保存，运行时需要站立位置时再居中。
- 区域保存整数 `min/max`。
- 检查点按数字 ID 排序。
- 终点和检查点可以分别保存判定区域与 glow region。

运行世界名是临时生成的，因此区域判断必须忽略配置中的世界身份。

## 私有地图快照

```text
公共模板：maps/parkour/<map>/
托管配置：modules/parkour/games/game/<localId>.yml
私有快照：modules/parkour/games/map/<localId>/
编辑世界：kgc_edit_*
运行世界：kgc_room_*
```

管理员编辑的是私有快照。保存动作先保存编辑世界，再由 `TemporaryWorldService.saveWorldToDirectory(...)` 同步到私有目录，最后卸载并删除编辑世界。

## 区域预览

- 管理员预览使用单 viewer `BlockDisplay` 边界。
- BlockDisplay 可穿透，不修改地图真实方块。
- 检查点默认黄色，终点默认绿色。
- 预览对象到期后由发包/视觉层清理。
- 玩家运行时只显示下一个目标，避免展示完整路线。

## Buff

第一版 `speed2` Buff：

- 只对目标玩家显示。
- 发光私有拾取物，可设置颜色、缩放、持续和重生时间。
- 拾取后给予速度 II 10 秒。
- 回调必须校验玩家仍在本房间且处于 RUNNING。

## 编辑命令

命令见 `09-Parkour组件.md`。所有设置命令依赖玩家当前 `MapEditorService` 会话，不再接受旧地图/路线参数。

## 清理

- 玩家离开或完成时清理私有目标提示和 Buff 视觉。
- 房间关闭时恢复玩家状态并清理显示、任务和临时世界。
- 编辑器关闭时恢复管理员原位置/模式，并保存或放弃私有快照。

完整配置结构和玩法细节以 `modules/parkour/技术细节.md` 为准。
