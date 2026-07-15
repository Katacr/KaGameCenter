# GameCenter 通用基底 API

更新时间：2026-07-14

主插件只抽取与具体玩法无关、至少可被多个模块复用的能力。模块通过 `GameModuleContext` 获取这些服务。

## 房间资源

### `RoomTaskService`

按 `roomId` 追踪同步 Bukkit 任务，支持立即、延迟、循环任务及 `cancelRoom/cancelAll`。模块仍应在 `GameSession.onClose()` 主动取消本房间任务。

### `RoomEntityOwnershipService`

记录实体 UUID 对应的 `roomId/ownerId/type`。事件处理中必须再次校验房间。服务只记录归属，不负责删除实体。

### `PlayerRuntimeStateService`

捕获和恢复模块临时修改的游戏模式、飞行、速度、重力、碰撞开关与例外集合、隐身、无敌、计分板和药水效果。完整背包、位置、生命、吸收生命、生命缩放和经验由主插件 `PlayerSnapshotService` 负责；完整快照也保存碰撞开关与例外集合，保证断线宽限超时后的延迟恢复不会遗留玩法碰撞状态。

### `PlayerEliminationService`

模块判定淘汰后调用 `eliminate(room, player, spectatorLocation)`。服务统一处理立即重生、原版观战模式、观战传送和输入限制标记；可通过 `spectatorPolicy = SpectatorPolicy(mode = MANAGED)` 让内部淘汰者复用跟随、菜单和离开快捷栏，且不会改变 `room.players` 历史参赛身份。玩家离开或房间关闭时自动清理。

### `RoomResourceScopeService`

`open(room.id)` 返回房间唯一资源作用域，可登记临时实体、实体归属、待恢复方块和 Packet viewer。房间关闭时自动取消 RoomTask 并统一释放这些资源。

## 队伍、奖励与结果

### `TeamAssignmentService`

批量注册队伍、加入最少人数队伍、随机轮转分队和比例分队。具体角色含义保留在模块。

### `GamePlayerRoomJoinEvent` / `GamePlayerRoomReconnectEvent` / `GamePlayerRoomAdmissionDeniedEvent` / `GamePlayerRoomLeaveEvent`

正式玩家或外部观战者通过准入检查后、成员关系提交前发布可取消加入事件；断线宽限玩家恢复显示和玩法状态前发布独立可取消重连事件。重连事件的可空 `respawnDelayTicks` 由 `GameSession.reconnectRespawnDelayTicks` 提供，监听器可修改；通过后核心把非负值交给 `applyReconnectRespawnDelayTicks`，再附加显示并调用玩法恢复回调，`null` 表示玩法不提供或保持当前状态。目标房间存在但加入、观战或重连最终失败时发布不可取消准入拒绝事件，提供三类准入和七类稳定原因；`CALLBACK_FAILED` 表示成员、显示、Session 或重连事务已回滚，不存在房间与无在线玩家的代理预留不发布。重连取消不会应用复活时间，会先发布拒绝事件，再释放席位并恢复大厅状态。成员清理后发布不可取消离开事件，保留玩家 UUID、可空在线 Player、观战身份、可选最近伤害者及七类稳定原因；主动离房、踢出、无宽限断线、重连超时/拒绝、关房和身份转换均覆盖。相同身份幂等加入不触发。

模块的 `onPlayerJoin/onSpectatorJoin/onPlayerReconnect` 位于成员与基础显示暂存后，正常返回才完成提交；抛异常时核心撤销成员、队伍、观战、显示并恢复快照，随后发布 `CALLBACK_FAILED`。只代表成功的音效或提示应在这些回调中、且在玩法私有队伍/阶段恢复成功后发送；拒绝反馈监听 `GamePlayerRoomAdmissionDeniedEvent`，不要在可取消准入事件中提前播放。拒绝事件是观察入口，监听器不能在其中递归重试同一次准入。

BedWars 仅在运行阶段为有效未淘汰参赛者提供默认 `respawnDelayTicks = game.respawn-seconds * 20`，参考默认 100 tick；应用时限制到 0–72000 tick，并重新进入与普通死亡相同的观战倒计时、装备重建、出生点、保护和 `GamePlayerRespawnedEvent` 流程。倒计时阶段重连保持 `null`，其他模块未覆盖两个 Session 钩子时行为不变。事件原有二参数 JVM 构造签名保持不变。

### `GamePlayerTeamAssignEvent`

`GameTeamService.join` 在队伍存在和容量校验通过后、修改成员映射前发布的可取消事件，提供 `roomId/player/previousTeamId/teamId`。取消保持原队伍并返回失败；同队幂等加入不触发。该入口位于底层服务，因此覆盖菜单选队、最小队伍、轮转、比例分配和模块直接调用。

### `WeightedPool` / `WeightedRewardDistributor`

提供权重抽取和批量分发回调，不负责创建物品或应用效果。

### `GameResultService`

统一击杀、死亡、胜利、失败、批量胜负及玩法专属整数指标，自动使用 `room.module.id`。扩展指标通过 `addMetric/metric` 访问，不允许模块直接操作统计仓库。

### `GameResultRecordedEvent`

`recordWinLoss` 完成全部统计写入后同步触发的不可取消整局赛果事件，提供房间、规范化参赛者/获胜者/失败者、仍有效获胜者、可选获胜组 ID 和胜利点数。四个 UUID 集合均为不可变快照；旧四参数 `recordWinLoss` JVM 签名继续保留，并默认把全部获胜者视为有效获胜者。逐人 `recordWin/recordLoss` 不触发此事件，调用模块负责用 session 状态防止重复整局结算。

### `GamePlayerExperienceGainedEvent` / `GamePlayerLevelUpEvent`

玩法经验写入扩展指标并派生等级后发布不可取消经验快照；一次奖励跨级时先发布等级事件，再发布经验事件。两者保留 UUID、可空在线 Player、稳定来源、等级内进度和下一阈值。BedWars 用单一累计 `level-experience` 指标派生等级，覆盖每分钟、队友、胜利、破床、普通/最终击杀，支持一次奖励连续跨级并为离线历史成员结算。

BedWars 另按参考 `money-rewards` 通过可选 Vault Economy 发放六类金币。该外部账户写入不属于 `GameResultService` 持久指标，也不新增通用奖励事件；胜利与团队支持仍放在 session 的 `resultRecorded` 唯一结算入口防重。存款只面向在线玩家，并以 EconomyResponse 成功结果为准，服务缺失或失败不会发送虚假奖励反馈。

### `GameObjectiveDestroyedEvent` / `GameTeamEliminatedEvent`

玩法提交目标销毁或整队淘汰状态后触发的不可取消观察事件。目标事件提供稳定目标类型/ID、可空破坏者、双方队伍 ID 和来源 ID；原六参数玩家构造签名继续保留。淘汰事件提供稳定队伍 ID，防重由 session 负责。BedWars 已接入手动及时间线破床，以及最终死亡、主动离场和重连宽限到期导致的整队淘汰；监听器不能用后置事件撤销状态，也不应在回调中关闭房间或执行阻塞 IO。

### `GameObjectiveDestroyedFeedbackEvent`

目标状态和不可取消观察事件提交后、玩法默认反馈发送前发布的可变显示事件，提供同一目标、破坏者、双方队伍和来源上下文。`message` 是普通受众 Adventure Component，同时作为聊天总开关；`targetMessage` 为受害方专属覆盖，设为 `null` 时回退普通消息，`message=null` 时所有受众均不发送聊天。`targetTitle/targetSubtitle` 仍可独立替换或置空。新增属性位于类体中，原 10 参数 JVM 构造器保持不变。事件不实现 `Cancellable`，不能撤销目标、统计或经验；玩法也可明确保留不受显示事件控制的音效。当前 BedWars 只在玩家手动破床时发布，普通消息面向破坏者、其他队伍和观战者，专属消息及标题只面向仍存活的受害队成员；时间线自动销毁不发布。

### `GamePlayerDeathResolvedEvent` / `GamePlayerRespawnedEvent`

玩法完成死亡归因和内部状态提交后发布死亡事件，字段同时支持在线 Player 与稳定 UUID、双方队伍 ID、Bukkit 伤害原因、模块来源 ID 和最终死亡标记；离线重连超时允许 Player 与伤害原因为空。复活事件只在玩法倒计时结束并恢复位置、装备和保护后触发，不在较早的 Bukkit `PlayerRespawnEvent` 触发。两者均不可取消，持久化监听器应使用 UUID，不应长期持有 Player。

### `GamePlayerDeathFeedbackEvent`

玩法完成死亡状态、归因统计和经验提交后，在默认消息与击杀者音效发送前发布。事件提供与结果事件相同的 UUID/可空 Player、双方队伍、伤害原因、模块来源和 `finalDeath` 上下文；`message` 是可空 Adventure Component，替换可自定义全房间消息，设为 `null` 可抑制；`playKillerSound=false` 可关闭本次击杀者音效。事件不实现 `Cancellable`，队伍与归因只读，不能回滚统计或改变随后结果事件。BedWars 在线普通/最终死亡、特殊召唤物死亡和运行期重连超时均接入，倒计时超时不作为死亡发布。

### `GamePlayerFirstSpawnedEvent`

玩法首次完成正式出生位置和初始装备准备后发布的不可取消事件，提供房间、玩家和稳定队伍 ID。BedWars 使用玩家级 `firstSpawned` 防重，并为倒计时断线、开局后重连的玩家补齐初始装备后延迟发布；普通运行期重连和后续复活不会重复触发。

### `GameResourceTierChangedEvent` / `GameTimelineStageChangedEvent`

房间级资源等级提交后发布前后等级、计划时间和实际游戏时间；下一主时间线条目首次确定或改变后发布前后稳定 ID、当前时间及下一截止时间。两者均不可取消。BedWars 钻石/绿宝石升阶按资源类型和等级全房间防重，主时间线覆盖生成器 II/III、全床销毁、殊死决战与游戏结束，并与 Sidebar 共用同一选择器。

### `GameResourceCollectEvent` / `GameShopOpenEvent`

生成器地面资源通过玩法身份和 AFK 校验后、原版拾取前触发可取消收集事件，提供实时 Item/ItemStack；取消后实体保留，直接背包分流不触发。有效玩家点击真实商店 NPC、默认菜单打开前触发可取消商店事件，提供稳定商店 ID、队伍和实体；BedWars 的 `item/upgrade` NPC 已接入，页面切换和刷新不重复触发。

### `GameProjectileLaunchedEvent` / `GameSummonSpawnedEvent` / `GameStructureBlockPlacedEvent`

玩法投射物完成基础配置、但尚未提交物品消耗与默认追踪时发布可取消启动事件；取消后玩法移除实体并保留物品。召唤物完成属性和资源登记后发布不可取消生成事件；自动结构方块写入世界并登记后发布不可取消逐块事件。三者均提供稳定 `sourceId` 及房间/队伍上下文，后两者属于后置观察入口。BedWars 已接入 `fireball/bridge-egg/bed-bug` 投射物、`bed-bug/dream-defender` 召唤物和 `bridge-egg/pop-up-tower` 结构方块；逐块监听器必须保持轻量。

### `GamePlayerAfkStateChangedEvent` / `GamePlayerBaseRegionChangedEvent` / `GamePlayerInvisibilityChangedEvent`

玩法确认 AFK 开始/结束、队伍基地进入/离开或隐身外观启用/恢复后发布的不可取消状态事件。AFK 事件附带当前空闲秒数；基地事件附带基地队伍和进入标记；隐身事件附带玩家队伍和最终可见状态。BedWars 对真实状态切换防重，跨基地按离开再进入排序，死亡结束基地范围，自然到期、受伤和结算均能提交隐身恢复；离房或断线只做生命周期清理，不发布虚假的 AFK 恢复。

## 显示与广播

### `SidebarBoardRenderer`

模块自定义 sidebar 的轻量渲染器，最多 15 行并处理重复文本。仅在模块接管自定义计分板时使用。

### `GameSidebarRenderEvent`

`SidebarBoardRenderer.show` 创建并替换计分板前发布的可取消同步事件，提供玩家和稳定 objective ID，并允许修改标题、行列表、行长、头顶/Tab 生命显示及标签。取消保留当前计分板；最终仍限制 15 行和 1–128 字符。该事件按每次模块低频刷新触发，监听器不得执行阻塞 IO；主插件默认 `GameDisplayService` Sidebar 不经过此模块渲染入口。`SidebarBoardRenderer.updateTitle` 只在 objective 存在且当前标题等于调用方上一帧时原地更新，可用于高频标题动画而不重建行，也不会覆盖事件监听器的不同标题。BedWars 默认每 4 tick 调用该路径、每 20 tick 完整刷新内容；生命目标只在运行阶段启用，并按 300 tick 从模块语言列表选取标签帧。

BedWars 完整刷新前按阶段和 viewer 身份选择模块语言行列表，再展开队伍、榜单和可选单行 token；渲染完成后仍统一进入 `GameSidebarRenderEvent`。模块布局层负责默认内容和动画，事件层负责第三方最终修改，两层不要反向调用或重复创建计分板。

Sidebar 通用 token 还覆盖参考玩家名、日期、插件版本、房间人数上限及等级明细：`{player}/{playerName}/{date}/{version}/{on}/{max}/{levelUnformatted}/{currentXp}/{requiredXp}/{progress}`。静态与观战 token 为 `{server}/{serverIp}/{poweredBy}/{spectatorTarget}`：服务器 ID 来自核心跨服服务，后两项静态文本来自模块配置，观战目标只接受当前 Session 玩家。等级数字使用最多两位小数的 `k` 缩写，十格进度条读取 `levels.progress-bar`；等级关闭时对应明细为空。新增 token 只在管理员模板引用时显示，不改写既有 12 套默认布局。

BedWars 每队动态 token 为 `{Team<队名>Status/Color/Name/Letter}`，所属队伍快捷 token 为 `{teamStatus/teamColor/teamName/teamLetter}`。动态状态只统计仍是 participant 且未淘汰的成员；外部观战者不获得快捷值。管理员模板若依赖参考默认的 `TeamRed` 命名，可直接配小写 `red` 队伍 ID，模块会同时注册两种形式。

Sidebar token 有两种展开语义：整行恰好等于 token 时返回模块本地化完整行或多行列表；token 嵌入其他文字时返回参考原始值。后者使统计值保持纯数字、下一事件保持纯名称、运行期时间保持 `m:ss`，同时不改变现有默认布局。新增 `{deaths}/{finalDeaths}` 也遵循该规则。

竞技场兼容 token `{map}/{map_name}/{group}` 分别读取托管显示名、local ID 和选择器分组，旧 `{server_ip}` 与 `{serverIp}` 等价。结算期 `{winnerTeamName}/{winnerTeamColor}/{winnerTeamLetter}` 从实际获胜队读取，平局和非结算阶段为空；每队专用 Letter 无颜色，viewer 所属队伍快捷 Letter 带颜色，与参考两条替换路径一致。

`GameCenterApi.velocityBridgeService` 向外置模块提供与核心房间摘要一致的 `enabled/backendName/serverId` 及跨服查询能力。模块只应读取稳定状态或调用已有预约接口，不应自行创建 Redis 连接；禁用桥接时 `NoopVelocityBridgeService.serverId` 为 `local`。

`GameSession.usesCustomScoreboard()` 是房间级而非阶段级契约。需要分阶段开关的模块应在任一阶段启用时保持声明，并自行记录实际渲染受众，只在从启用阶段切换到禁用阶段时清理自己的 Sidebar；全部阶段均关闭时才返回 `false` 交回 `GameDisplayService`。BedWars 已按等待/倒计时与运行/结算两组开关实现该模式。

### `GameSession.usesCustomTabHeaderFooter()`

返回 `true` 时只阻止 `GameDisplayService` 写入 Tab header/footer，通用玩家列表名称和 `playerListOrder` 仍持续维护。核心会在首次附加显示前保存玩家原头尾，并在离房或统一清理时恢复；模块负责成功加入、重连、身份/阶段变化和配置刷新周期内的头尾写入。BedWars 使用 `scoreboard-settings.tab-header-footer.enable` 和默认 10 tick 的 `refresh-interval`，按等待、倒计时、运行、结算及观战/淘汰/胜负身份选择 12 组模块语言模板，并通过 Adventure 组件发送；模板列表一项代表一行，英文逗号分隔同行动画帧，每名 viewer 独立推进且切换模板时复位。旧外部语言文件缺键时由内置模块语言回退，不要求覆盖管理员文件。

### `GameSession.usesCustomTabPlayerNames()`

返回 `true` 时只阻止 `GameDisplayService` 写入 `playerListName`，核心仍维护正式玩家和外部观战者的稳定 `playerListOrder`，并在离房或统一清理时恢复入房前名称与排序。契约按房间声明而不是随阶段反复切换；部分阶段关闭格式时模块应写入纯玩家名，全部阶段都关闭时才返回 `false`。BedWars 映射参考等待/开始/运行/重启四项开关和默认 1200 tick 刷新间隔，负责外部观战者以及队伍、淘汰、胜负和持久等级格式。13 个玩家名模板键均以列表表示动画帧：外部观战者按四个阶段选择独立模板，结算获胜队区分存活与已淘汰成员，旧统一观战键保留为兼容回退。模板按目标玩家推进；阶段、身份、队伍参数改变或格式关闭时必须复位/清理，避免重连后继承旧身份帧。BedWars 还支持参考 `{vPrefix}/{vSuffix}`，借助可选 PlaceholderAPI 解析目标玩家 Vault 前后缀，缺插件或扩展时为空；模板与解析值使用 Adventure 兼容的旧式颜色反序列化。

### `GameSession.tabPlayerListOrder(player, defaultOrder)`

核心每 tick 为正式玩家计算队伍/未分组默认顺序，并为外部观战者计算尾部顺序，再把结果交给该钩子；实现必须返回非负整数。不覆盖时保持通用排序，离房和统一清理仍恢复原值。BedWars 在等待/倒计时按玩家名排序，运行/结算覆盖全部配置队伍而不受通用四队展示上限影响，之后依次排列原队淘汰成员和外部观战者。

### `RoomBroadcastService`

获取房间玩家/全部参与者并发送消息、模块本地化文本、ActionBar 和 Title。

### `PlayerNametagService`

按房间设置名牌前缀、后缀、颜色、可见性和碰撞规则。支持 viewer 刷新以及玩家离开、房间关闭和插件关闭清理。详细见 `20-玩家名牌服务.md`。

## 阶段与地图

### `GameRoomStateChangeEvent`

`GameRoom.state` 真实变化并完成写入后同步触发的不可取消事件，提供 `room/previousState/newState`。初始 `CREATED` 和重复写入同一状态不触发；监听器只应用于记录、通知或外部状态同步，不应在回调中递归修改房间状态或执行阻塞 IO。

### `GameRoomPreparedEvent` / `GameRoomClosedEvent`

房间玩法成功准备临时世界、进入 `WAITING` 并发布显示和代理状态后触发准备事件；失败回退和重复准备不触发。关闭事件在房间移出管理器、成员与通用资源清理、临时世界卸载删除并写入 `CLOSED` 后触发，提供可空世界名与世界清理结果；未创建世界时结果视为成功。两者均不可取消，是参考竞技场启用、禁用和重启事件在 KaGameCenter 临时房间模型中的通用映射。

### `GamePhaseTimer`

轻量 tick 计时器，提供剩余 tick、展示秒数和秒边界；不接管模块自己的玩法阶段。

### `SelectionService`

主插件石斧两点选区，供地图编辑器保存区域。

### `MapEditorService`

创建编辑世界、保存到模板目录、恢复管理员状态并关闭/删除编辑世界。

### `GameMapEditSessionStartedEvent` / `GameMapEditSessionClosedEvent`

共享编辑会话首次创建并完成首位编辑者传送后发布启动事件，加入既有会话不重复发布。关闭事件在会话索引、在线编辑者状态、可选保存和临时世界清理全部提交后发布，提供不可变编辑者 UUID 集合及保存、恢复请求、世界清理结果。关闭会统一移除离线编辑者索引，且 `closeSession` 仅在请求的保存与世界清理均成功时返回成功；两事件均不可取消。

### `ManagedMapPointService`

提供不绑定世界名的 `ManagedMapPoint`、稳定 ID 命名点位列表、YAML 读写和选区去世界化。

### `SpawnAssignmentService`

随机且无重复地配对参与者和出生点。点位不足返回 `null`；服务不传送玩家，也不修改玩法状态。

## 菜单与聊天

- `GameCenterMenuService`：主流程 YAML 菜单和 `kgc:` 动作。
- `ChestMenuService`：操作型箱子菜单和模块动态数据源。
- `GameRoomsMenuOpenedEvent`：KaMenu 或箱子菜单真实打开房间列表后的不可取消上下文，提供 Player、可空模块/玩法 ID 和可空非默认分组；全局/默认分组为 `null`，仅回退主 Dialog 时不发布，旧二参数构造签名保留。
- `GameStatsMenuOpenedEvent`：KaMenu 或箱子菜单真实打开战绩配置后的不可取消上下文，提供 Player 和可空玩法 ID；仅回退主 Dialog 时不发布。
- `GameChatService`：按房间/队伍路由聊天。
- `GameModuleContext.registerChatFormatter(...)`：模块定义显示格式。
- `GameSession.routeChat(...)`：玩法按阶段调整聊天频道、文本、格式变体和房间内受众。

## 购买扩展

### `GamePurchaseEvent`

玩法在确认购买资格和余额后、扣款前触发的通用可取消事件。`kind` 区分普通商品与队伍升级，监听器可通过 `room/player/productId/productType/teamId/currency/price` 判定购买上下文；取消事件不会扣款，设置 `handled = true` 则保留正常扣款和成功反馈、跳过模块默认行为。监听器应按房间、模块、商品和购买类型完整校验，并保持 Bukkit 主线程安全。

`currency` 为 Bukkit `Material` 兼容字段。BedWars 配置 `vault/economy` 时以 `Material.AIR` 表示 Vault Economy 账户货币，余额向下取整；模块在事件返回后再次检查并按事务响应扣款，服务缺失或响应失败会拒绝购买。监听器如需区分实体 AIR 和经济标记，应同时校验模块、购买类型与商品配置。

BedWars 默认商品行为还可包含管理员配置的 `buy-cmds.as-player/as-console`。两类命令位于成功扣款和默认物品处理之后，`handled = true` 会一并跳过；玩家命令进入命令预处理链，控制台命令以 Bukkit 控制台发送者执行。该能力不是面向玩家输入的命令代理，配置生成器和第三方监听器不得把未经校验的聊天文本写入命令模板。

BedWars 商品的 `buy-items` 支持参考命名映射及列表格式，一次购买按配置顺序发放所有有效子项；显式子项替代顶层单物品，但不会改变一次购买只发布一个 `GamePurchaseEvent`、只扣款一次的契约。商品级永久/不可损坏标记继承到每个子项，固定装备的复活与工具降级会重新解析当前等级子项。字段读取兼容参考 `material/name/enchants` 与模块原有别名。

BedWars 默认商品构建还支持参考 `BuyItem.potion/potion-color`。效果名称通过现代 MOB_EFFECT 注册表解析，持续时间换算为 tick，amplifier 保留 Bukkit 原始零基值；颜色限制为 24 位并写入 `PotionMeta`，成品使用 water 基础药水。该配置属于模块默认发放行为，因此 `GamePurchaseEvent.handled = true` 时不会创建药水；旧配置缺字段继续使用内置 ID 回退。

BedWars 通用 `ITEM` 护甲可通过 `auto-equip` 进入实际装备槽；`ITEM/POTION/SPECIAL` 的 `permanent/is-permanent` 使用玩家 session 内稳定商品 ID 防重，并复用永久 PDC 的丢弃、容器和死亡保护。复活与重连按当前有效配置重建永久物品并重放其 BuyCommand，不重发购买反馈；固定护甲和工具仍由类型化等级状态恢复。自定义永久登记属于默认购买行为，事件取消或 `handled = true` 时不得提交。

BedWars 非陷阱升级还可用参考 `receive` 的 `command:模式,命令` 动作，在内置升级状态提交和购买音效后按单次控制台、逐有效队员控制台或逐有效队员玩家身份执行，再发送升级广播。购买者、队伍、竞技场和逐成员 token 均由 session 展开；取消或 `handled = true` 时不执行。陷阱动作属于敌方进入基地后的触发生命周期，不复用购买时升级命令。

`receive` 现在统一解析为有序升级动作，除命令外还包含 `enchant-item/player-effect/generator-edit/dragon`。动作状态归当前 Session 队伍所有：附魔覆盖现有与后续装备，队伍效果随复活恢复，基地效果随区域进出应用，生成器参数直接参与间隔及掉落计算，末影龙数量在殊死决战生成时读取。整个列表仍只依附一次成功购买，不额外发布购买事件；外部接管时不会留下部分动作状态。

陷阱使用独立 `BedWarsQueuedTrap` 快照，不复用购买期升级动作。有效 `player-effect/remove-effect/disenchant-item` 在敌人进入基地后按队列顺序执行并消费一次；配置动作存在时替代固定类型效果，缺失时回退内置陷阱。外部 `GamePurchaseEvent.handled` 接管购买后不会留下空陷阱或延迟动作。

### `GameItemUseEvent`

支持该能力的玩法在确认玩家和交互动作有效后触发的通用可取消事件。事件提供 `room/player/itemId/item/action/hand/clickedBlock/clickedFace`；`itemId` 由玩法按可识别商品提供，也允许为空。取消事件会拒绝使用，设置 `handled = true` 会由监听器接管并跳过玩法默认行为；两种状态都会阻止原版交互。BedWars 以 Bukkit 事件对象身份记录该接管及默认特殊商品执行，并在 MONITOR 重新确认取消，因此后续监听器误解除取消不会让对应材料再执行一次原版行为；普通未接管交互不受影响。接管方必须自行处理行为、反馈和对应手的物品消耗。

## 发包与观战

- `PacketDispatchService`：伪装、发光、私有掉落物和信标光柱。
- `SpectatorService`：原版或主插件托管观战，也由 `PlayerEliminationService` 为可选托管淘汰模式复用；`SpectatorPolicy.hotbarItems` 可按模块配置托管快捷栏材质、槽位、光效、名称、Lore、内建动作或受信任玩家命令。
- `GameSession.canSpectatorFollow(...)`：按玩法存活、复活和隐藏状态过滤快捷栏跟随目标。
- 模块不要直接访问 PacketEvents wrapper 或主插件内部缓存。

托管快捷栏进入观战时保存策略快照，只接受 `0..8` 槽位和非空气材质，重复槽位以后项覆盖。命令去除开头 `/`、最多 256 字符，并且只有服务生成物品中的 `spectator_command` PDC 会被执行；同项存在 `SpectatorAction` 时动作优先。未覆盖 `hotbarItems` 的模块继续获得原有 `COMPASS/NETHER_STAR/BARRIER` 三件默认物品。

### `GameSpectatorTargetSelectEvent` / `GameSpectatorTargetChangedEvent`

`SpectatorService` 在托管传送或原版第一人称镜头提交前发布可取消选择事件，模式分别为 `TELEPORT/FIRST_PERSON`；取消保留原状态，传送失败也不提交。实际目标改变或清除后发布不可取消前后快照，保留 UUID 和可空在线 Player。初始自动跟随、快捷栏切换、潜行退出、目标死亡/离房、观战退出及在线房间清理均覆盖；模块应使用 `canSpectatorFollow` 过滤候选目标，不要直接修改 Bukkit 镜头绕过服务事件。

### `GameSpectatorFirstPersonFeedbackEvent`

只在 `VANILLA/FIRST_PERSON` 镜头完成进入或退出状态提交后、默认标题发送前发布，`action` 为 `ENTER/LEAVE`，并提供房间 ID、观战者、可空目标 UUID/Player。`title/subtitle` 可替换或设为 `null`，两者都为空时不发送标题；`fadeInTicks/stayTicks/fadeOutTicks` 默认 0/40/10，发送时分别限制到 0–72000。进入前取消仍由 `GameSpectatorTargetSelectEvent` 负责，本反馈事件不可取消；托管传送不触发。切换目标会产生新的进入反馈，潜行退出、目标死亡/离房、观战退出和在线房间清理都产生退出反馈。

## 当前接入模块

Parkour、Blockhunt、TNT Wars 已使用其中多项服务；Hunger Games 也使用通用任务、显示、结果和发包能力。是否接入某项 API 以模块当前源码为准。

## 后续候选

- 声明式托管游戏编辑器字段。
- 房间共享虚拟容器与补货服务。
