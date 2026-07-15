# Dialog 与临时世界生命周期

更新时间：2026-07-13

文件名为历史章节编号保留；当前已无超平坦 Demo 命令或 DemoSceneService。

## Paper Dialog

`GameCenterDialogService` 只承担菜单渲染全部失败时的主菜单兜底。正式主流程优先使用 KaMenu YAML，操作密集界面可以使用内置箱子菜单。

Paper Dialog 组件打开后不能原地修改。需要刷新时必须重新发送 Dialog；执行重开动作时不要先 close，否则客户端会出现明显闪烁。

## 正式世界生命周期

`TemporaryWorldService` 负责：

- 从公共地图模板或托管游戏私有快照复制运行世界。
- 在房间首次准备时创建世界，而不是开服时创建。
- 为编辑器创建 `kgc_edit_*` 世界。
- 保存编辑世界到指定私有快照目录。
- 配置禁止区块生成、世界规则和模板边界。
- 房间/编辑结束后卸载并删除世界数据。
- 启动时清理符合 KaGameCenter 命名规则的遗留临时世界。

## 模板要求

可用模板至少需要：

- `level.dat`。
- 主世界 region 数据，通常为 `region/*.mca`。
- 模块配置引用的坐标必须落在模板有效区域内。

不要把 `session.lock`、运行时 UUID、玩家数据和旧临时世界身份复制回模板。地图目录权限必须允许 `minecraft` 用户读写。

## 运行来源优先级

托管游戏房间使用：

```text
plugins/KaGameCenter/modules/<module>/games/map/<localId>/
```

非托管定义可以使用公共模板：

```text
plugins/KaGameCenter/maps/<module>/<map>/
```

公共模板不因编辑某个托管游戏而被修改。
