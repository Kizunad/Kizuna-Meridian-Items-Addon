# 物品 Registry 与接入边界

本项目为经脉玩法提供通用物品登记、触发与消费编排。当前已实现可编译的 Java 17 服务端库；实际物品、具体效果与游戏宿主由接入方提供。API 尚未冻结，尚无真实游戏联调结果。

## 职责归属

| 模块 | 职责 |
|---|---|
| Meridian Framework | 身体、经脉、真元和寿命的权威规则、内容目录与命令处理 |
| Backpack and Storage 3D（BS3D） | 真实物品与容器身份、库存访问、物品转移及相应持久化 |
| Items Addon（本仓库） | 绑定目录、触发校验、消费候选、幂等回执与联合事务编排 |
| 内容附属模组 | 具体物品、容器、物品模型及其注册、数值和玩法内容 |
| 可信宿主 | 参与者身份、时间、线程调度及跨系统提交与恢复 |

本仓库不包含模型或模型注册流程。Human、Meridian UI、Weights 等附属保持各自职责，具体接入按需求确认，不因同属这一生态就成为必需依赖。

## 实现范围

| 能力 | 当前行为 |
|---|---|
| 物品绑定 | 使用已注册 Item 的类型 ID，关联触发 ID、效果 ID 与单次消费件数 |
| 目录加载 | 整批校验命名空间、重复项、真实物品及效果引用，成功后生成不可变目录 |
| 触发 | 仅由可信宿主事件进入；事务前、事务内和暂存前重新授权 |
| 消费 | 原版个人槽位、BS3D 穿戴容器及个人槽位容器内的普通物品，支持嵌套和部分栈扣除 |
| 零消费 | 仍校验真实物品，可用于工具或容器的触发；不增加库存修订号 |
| 效果 | 附属实现 `ItemEffect`；`MeridianItemEffects.command` 可接入一条 Meridian 命令 |
| 重试 | 同一调用者与 UUID 返回原成功回执；相同 UUID 携带不同意图被拒绝 |
| 通信 | 提供专属 Protobuf 请求、结果与内部回执；由宿主已有路由承载 |

本轮不支持消耗 BS3D 容器本体，包括空容器；容器本体可以绑定零消费触发。地面容器、页面光标、跨多个物品栈凑数量、耐久消费及使用后返还容器均未实现。不存在自动右键、命中或装备监听器，也不内置持续 buff 生命周期；宿主将真实事件映射为命名空间触发 ID。

一次正消费只改变目标栈数量，扣完后删除该条目，保留其他物品、NBT、格位、旋转和实例身份。更新嵌套物品时只重建通向其真实根容器的路径；消费前后均由 BS3D 的 `ContainerTree` 校验。成功正消费使 BS3D 库存修订号增加一次。

## 公开接口

所有接口位于 `dev.kizuna.meridian.items`，消息类为 `protocol.pb.ItemMessages`。

| 接口 | 用法 |
|---|---|
| `ItemBinding` | 声明 `id / item / trigger / effect / consumeCount` |
| `ItemRegistry.load` | 加载一组 `Contribution`，返回完整校验后的目录 |
| `ItemRegistry.find` | 按真实物品类型和触发名查找绑定 |
| `ItemUseAccess<C>` | 只读授权，返回稳定的可信调用者 ID |
| `PlayerItemAccess` | Fabric 玩家检查包装器，检查主线程、存活、旁观状态及 BS3D 搬运锁 |
| `ItemUseService<C>` | `trigger` 接受生成消息，`handle` 接受有界 Protobuf 字节 |
| `ItemEffect<C>` | 在宿主提供的 Meridian 事务参与者中准备效果 |
| `MeridianItemEffects.command` | 使用上游 `BodyCommandHandler` 准备一条权威命令，保留失败码 |
| `Bs3dInventory` | 真实个人库存的隔离副本；`resolve` 定位、`consume` 生成候选 |
| `ItemUseStore<C>` | 宿主实现的联合提交、回滚、恢复与回执查询接口 |

### 绑定登记

附属先注册自身物品，再提供目录贡献；示例中的 `example:medicine` 必须已经是实际注册的物品，`medicineEffect` 是附属提供的 `ItemEffect<ServerPlayerEntity>`。

```java
var binding = new ItemBinding(
        new Identifier("example", "use_medicine"),
        new Identifier("example", "medicine"),
        new Identifier("example", "use"),
        new Identifier("example", "medicine_effect"),
        1);

var contribution = new ItemRegistry.Contribution<ServerPlayerEntity>(
        "example",
        List.of(binding),
        Map.of(binding.effect(), medicineEffect));

var registry = ItemRegistry.load("example-items-v1", List.of(contribution));
```

同一贡献来源只出现一次，定义 ID 必须属于该来源的命名空间。引用的物品和效果可以属于其他来源，但必须实际存在。同一物品与触发名只能有一个绑定；需要复合效果时由一个效果提供者编排。消费件数位于 `[0,64]` 且不得超过物品单栈上限。

`registry_revision` 由宿主发布内容时指定。更改绑定或效果行为必须更换该修订；它不能只反映 Item ID。新目录构造失败不会修改旧目录，宿主应保留旧目录并报告加载异常。本库不覆盖 BS3D 已注册的 `ItemMetadata`，也不再登记其模型或容器类型。

### 真实触发与物品请求

`UseRequest` 包含协议版本 `1`、规范 UUID、绑定 ID、目录修订、目标身体 ID、物品来源、BS3D 库存修订和 `StackCodec.fingerprint`。来源二选一：原版槽号，或随身容器内物品 / 穿戴容器的实例 UUID。栈指纹包含数量及完整 NBT；即使原版操作没有增加 BS3D 修订，栈发生变化也会被拒绝。

客户端不能指定消费数量、效果实现或物品 NBT。`trustedTrigger` 是服务端事件参数，不在请求协议中。客户端选择身体 ID 不授予访问权；宿主策略须核实身体归属、事件来源和物品位置是否符合此次事件，例如右键事件是否确实指向当前手持槽位。

```java
var service = new ItemUseService<>(registry, new PlayerItemAccess(hostPolicy), jointStore);
// player、trigger 和请求关联必须由真实服务器事件确认。
var result = service.trigger(player, trigger, request);
```

`hostPolicy` 是宿主的身体与事件授权策略；`jointStore` 必须满足下节契约。这些不是本库自带的默认实现。单机与专用服务器均在逻辑服务端调用相同流程。

授权策略不要通过“源物品现在还存在”代替权限检查：已提交操作重试时物品可能已经用完。库存校验由服务在查询成功回执之后执行，访问已提交回执仍需重新授权。

### 效果提供者

`ItemEffect.prepare` 正常返回表示效果候选已准备好；拒绝时抛 `ItemUseRejected`。提供者只能通过传入的事务参与者准备身体变更，不能自行扣物品或使用另一个会独立提交的 Meridian 存储。上下文中的 ItemStack 是副本，修改它不会产生效果或改变库存。

对于单条既有 Meridian 命令，推荐用 `MeridianItemEffects.command(commandFactory, handlerFactory)`。命令工厂从附属内容和可信宿主信息构造上游 `RequestEnvelope`，目标 body ID 与 request ID 必须等于本次物品请求。Handler 工厂接受本库传入的 `BodyTransactionStore` 和 `BodyAuditSink`，必须把它们传给上游 `BodyCommandHandler`，其余授权、时间和内容目录由附属或宿主提供。

该适配器通过上游命令处理器完成规则检查；Meridian 拒绝时，结果为 `EFFECT_REJECTED`，并在 `meridian_code` 原样保留错误。只有本次确实暂存新命令才允许消费；仅重放其他入口的 Meridian 旧回执会被拒绝，避免再次扣物品。物品事务自身的成功重试在效果回调之前返回，不会重复执行。

内层 Handler 的审计使用无副作用接收器，因为此时外层物品事务尚未提交。真实通知、实体副作用和可靠投递必须由宿主在联合提交后处理；需要可靠投递时，将相应记录纳入同一次持久化。常规 `command` 适配器不调用管理员调试或可信碰撞专用入口。

## 联合事务：宿主接入的必要条件

`ItemUseStore.transact` 必须隔离调用者库存、目标身体、有关环境账本和回执，恰好调用业务回调一次。回调正常返回后统一提交；任何异常必须回滚所有暂存。`ItemUseService` 特意在事务外捕获业务拒绝，防止失败结果被误当成正常提交。

执行顺序如下：

1. 验证请求并授权，在宿主事务内再次授权。
2. 查询调用者与 request UUID 的成功回执；相同意图返回旧结果，不再次读取或消费物品。
3. 校验目录、绑定、真实触发、库存修订、物品类型、完整栈指纹及数量。
4. 生成库存消费候选，由附属通过本次 Meridian 参与者准备身体效果。
5. 再次授权，暂存库存候选及物品回执。
6. 宿主联合提交物品、身体、涉及的连带资源、两侧回执和必要可靠通知记录，再返回成功。

宿主 `Transaction.meridian()` 是外层联合事务中的参与者；其内层 `transact` 返回只代表准备完成。此处不能传入独立持久化的普通 Meridian store。宿主仍需复用 Meridian 对身体修订、回执、环境扣账与释放的完整一致性验证，并且对所有其他身体/库存写入维持隔离。未支持的跨资源命令应拒绝执行。

物品回执的唯一键是可信 caller ID 与 request UUID，跨身体去重；同一 UUID 改变身体、绑定、来源、指纹、修订或事件都属于冲突。回执应与资产一起保存和恢复；保留期限外的旧 ID 必须明确拒绝，不能删除回执后把它当成新消费。提交结果不确定时使用原请求重试，不能换 UUID。

**本仓库尚未提供 Minecraft 玩家存档与 Meridian 存储的生产联合事务适配。** `Bs3dInventory.consume` 只产生候选，不能单独消耗真实玩家物品。服务构造必须提供宿主存储，没有隐式的独立保存或“失败后补物品”方案。因此编译通过不意味着已经完成游戏消费与崩溃恢复验证。

## 已核对的上游接口

以下是本地源码及编译依赖中的实际入口，API 尚未冻结。

| 上游入口 | 已有能力 | 接入约束 |
|---|---|---|
| Meridian `AddonRegistry.load` | 整批校验内容包、依赖与引用，生成不可变目录 | 登记不启动动作或修改身体；不是现成的物品关联注册表 |
| Meridian `BodyCommandHandler` | 权威身体命令处理 | 复用宿主授权、时间、版本和事务语义，不能另行维护身体状态 |
| BS3D `ItemMetadata.register` / `of` | 为已注册物品提供描述与附属 `optional` 字段，并读取副本 | 每个物品类型只登记一次；容器已自动登记元数据，不能由多个附属重复注册同一物品 |
| BS3D `InventoryAccess.snapshot` | 读取原版槽位、穿戴库存和已获授权地面容器的副本 | 仅服务端主线程；快照不授予写入权限，修改副本不会消耗真实库存 |
| BS3D `InventoryAccess.previewCursor` / `exchangeCursor` | 真实容器与原版页面光标栈交换 | 受页面、指纹、修订和现有事务限制；不能作为任意物品消耗或跨系统原子提交入口 |
| BS3D `ContainerTree` / `ContainerItems.contents` / `setContents` | 校验真实容器树、定位物品、读写候选内容 | 本库仅操作副本，实际发布由宿主事务承担 |
| BS3D `Account` / `PlayerAccount` | 真实个人库存读取及其自身提交 | 现有 `commit` 不包含 Meridian，不能直接组合成联合事务 |

BS3D 的 `optional` 是本地类型元数据，使用 JSON 并不等于另建 JSON 网络协议。本库的独立绑定目录不修改该元数据。

物品消耗与经脉收益若在同一业务操作发生，需先落实宿主的联合提交、幂等和恢复契约。上述注册与快照入口本身没有提供这一保证；分别保存两侧状态不足以证明原子性。

## 构建、依赖与交付状态

工程使用 Java 17、Gradle 8.8、Fabric Loom `1.6-SNAPSHOT`（本轮解析为 `1.6.12`）、Minecraft 1.20.1、Yarn `1.20.1+build.10`、Fabric Loader 0.16.10、Fabric API `0.92.3+1.20.1`、Protobuf 4.36.2。

默认依赖目录可以通过 Gradle 属性调整：

| 属性 | 默认目录 | 使用方式 |
|---|---|---|
| `meridianFrameworkDir` | `../Kizuna-Meridian-Mod-Framework` | composite build，仅构建其 server 依赖，不复制源码 |
| `bs3dDir` | `../backpack-and-storage-3D` | 使用 `server/build/libs/backpack-storage-3d-server-0.1.0-SNAPSHOT.jar` |
| `frameworkDir` | `../Kizuna-Inventory-UI-Framework` | 编译时使用其 server 产物；运行时由 BS3D 宿主提供对应路由模组 |

BS3D 与 Inventory UI Framework 的服务端产物需先在各自工程构建。当前 Meridian 编译依据本地工作区，其中存在上游未提交变更；不将其宣称为稳定发布版。

```bash
./gradlew assemble
```

产物位于 `build/libs/`：供 Fabric 宿主嵌入的 remapped JAR、源码 JAR 和协议 ZIP。协议 ZIP 包含本库 schema 及含导入定义的描述集；不复制上游 schema 源文件。跨语言接入可以读取描述集，或使用上游原始契约与本库 schema 一起生成。

运行库不内嵌 Meridian / BS3D，也不包含 `fabric.mod.json` 或自动事件入口。宿主负责以库依赖加载、提供这些运行依赖及注册真实事件；不能直接把本 JAR 放进 `mods/` 当作独立模组。服务端源码不引用客户端或渲染类，单机可复用相同库。

已执行编译与打包，并通过 Fabric JUnit 运行临时注册测试，7 组检查全部通过：登记与查找、重复登记拒绝、缺失引用拒绝、触发消费、幂等重放、触发及库存版本校验、效果拒绝时不提交消费。测试使用真实 Minecraft 物品注册表和临时内存宿主，代码保留在被忽略的 `build/` 下，不纳入长期测试集或生产产物。尚未验证真实玩家存档、Meridian 身体效果、游戏事件与网络路由或崩溃恢复。

[返回 README](../../README.md)
