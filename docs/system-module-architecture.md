# HyperEars 系统适配模块架构

## 1. 目标与范围

首版只支持 `vivo TWS Air3 Pro`，在 HyperOS 3 / Android 16 上提供：

- 跟随系统 A2DP 连接自动建立 vivo GAIA RFCOMM 控制链路。
- 向 MiLink 暴露最小兼容身份，使融合设备中心识别目标耳机。
- 在融合卡片读取左右耳/充电盒电量和当前降噪状态。
- 将融合卡片的关闭、降噪、通透操作翻译为已实机验证的 vivo 命令。
- 保留后续 vivo 型号及其他厂商协议接入所需的稳定扩展点。

首版不伪造尚未验证的能力。空间音频、自适应降噪、入耳检测、查找耳机等
能力默认关闭；`0x820D`、`0x8224` 在语义确认前只记录，不参与系统状态。

## 2. 参考实现结论

OppoPods 的有效接入链路可归纳为三部分：

1. 在 `com.android.bluetooth` 监听 A2DP 状态，自行连接厂商 RFCOMM，
   因此电量和降噪不依赖厂商 App。
2. Hook `com.milink.service` 的耳机运行时，返回兼容设备 ID、电量、降噪
   和音频切换能力。系统仍负责 A2DP 音频路由和设备流转，模块不自行复制
   一套流转协议。

HyperEars 仅复用融合设备中心所需边界，不注入
`com.xiaomi.bluetooth` 或 `com.android.settings`。协议、型号、会话、
状态和 MiLink 桥接被拆成可独立测试的层。

## 3. 模块划分

```text
protocol
  └─ vivo GAIA 帧、流式解码、握手/电量/降噪编解码

integration
  ├─ 通用耳机模型与控制事件
  ├─ Standard / vivo / 具体型号 Adapter 继承链
  ├─ Adapter Registry
  ├─ vivo Air3 Pro 私有协议组件
  ├─ 状态 Reducer
  └─ MiLink 状态编码

system-module
  ├─ LSPosed 入口与反射 Hook 基础设施
  ├─ Bluetooth 进程服务级生命周期入口
  ├─ 设备连接管理器与每设备协议会话
  ├─ 可替换的厂商控制通道
  ├─ MiLink 运行时兼容桥
  ├─ 跨进程定向广播契约
  └─ 模块状态/诊断界面

protocol-test
  └─ 独立协议实验室，不参与系统 Hook
```

依赖方向固定为：

```text
protocol <- integration <- system-module
protocol <- protocol-test
```

系统 Hook、Android 蓝牙对象和 LSPosed API 不得反向进入 `protocol` 或
`integration`，确保协议与型号逻辑能在普通 JVM 测试中验证。

## 4. 核心抽象

### 4.1 EarbudAdapter 继承链

所有识别层级使用同一种 `EarbudAdapter` 抽象，并形成严格单继承链：

```text
EarbudAdapter
  └─ StandardEarbudAdapter
       └─ VivoEarbudAdapter
            └─ VivoTwsAir3ProAdapter
```

子类继承父类的通用能力，只覆盖经实机确认的差异：

- `StandardEarbudAdapter` 表示 Android 原生 A2DP/HFP、音量和路由能力。
- `VivoEarbudAdapter` 继承标准层，增加 vivo 家族名称规则和 GAIA 端点。
- `VivoTwsAir3ProAdapter` 继承 vivo 层，增加精确名称、已验证电量/降噪
  能力、MiLink 兼容身份及 Air3 Pro 协议创建方法。

Registry 固定按“具体型号 → 厂商家族 → 标准耳机”解析：

- 具体 Air3 Pro Adapter 建立私有通道并发布完整电量、降噪和流转能力。
- 未知 vivo 家族 Adapter 只发布耳机身份和流转能力。
- 标准 Adapter 只接管 Bluetooth Class 明确为耳机，或名称保守命中耳机
  关键词的设备，同样只发布身份和流转能力。
- 音箱、车机和无法确认是耳机的设备不匹配任何 Adapter；名称可确认由
  HyperOS 原生支持的小米/REDMI 耳机也明确排除，保留官方完整路径。

身份级回退不会创建 RFCOMM、启动 Reader、安排重连或覆盖电量/降噪方法，
因此其后台开销仅限 A2DP 生命周期开始和结束时的状态同步。

Registry 只选择 Adapter，不包含蓝牙连接或全局状态。新增型号不修改 Hook
分发代码，也不依赖“当前只有一个型号”的默认兜底。

### 4.2 EarbudProtocol

Adapter 与有状态协议组件采用组合关系。每个 RFCOMM 会话从选中的 Adapter
创建独立 `EarbudProtocol` 实例，其职责为：

- 给出连接后的只读初始化命令。
- 把统一控制请求翻译成厂商字节帧。
- 增量消费任意分片/粘包并产生统一领域事件。

统一控制请求首版包含 `Refresh` 与 `SetNoiseMode`。统一领域事件明确区分
系统 Profile 会话和私有通道：

- `SessionStarted/SessionEnded`：目标设备进入或离开系统 Profile 生命周期。
- `ChannelConnected/ChannelDisconnected`：厂商控制通道可用或丢失。
- `Handshake`、`BatteryChanged`、`NoiseModeChanged` 和 `UnknownFrame`：
  私有协议产生的状态事件。

### 4.3 EarbudStateReducer

Reducer 是纯函数，接收旧状态和协议事件，输出新状态：

- 未出现的部件保持未知，而不是伪造为 0%。
- `0xFF` 电量映射为未连接/不可用。
- 控制写入后可发布一次明确标记为未确认的乐观降噪状态；耳机 ACK 或状态
  报告仍是最终权威值并负责纠正。
- 新 Profile 会话开始或结束时清除易失状态。
- 同一 Profile 会话内的私有通道重连保留最近一次设备状态。

系统桥只读取统一状态，不直接理解 vivo payload。连接、重连、Socket 和
Reader 始终由运行时统一管理，不进入 Adapter 继承层。

## 5. 运行时与进程边界

### 5.1 `com.android.bluetooth`

这是唯一的厂商控制会话所有者。运行时分层与小米原版
`BluetoothHeadsetService → ConnectManager → BluetoothEngine/Channel`
保持同构：

```text
EarbudSessionService
  └─ EarbudConnectionManager
       ├─ ConnectionAttemptCoordinator
       └─ Map<address, EarbudDeviceSession>
            └─ EarbudChannel
                 └─ Android RFCOMM BluetoothSocket
```

各层职责：

- `EarbudSessionService` 随 `AdapterService` 创建和销毁，注册一次跨进程控制
  Receiver；销毁时统一注销 Receiver 并执行 `disconnectAllDevices`。
- `EarbudConnectionManager` 接收系统 Profile 连接事件，负责设备
  `register/unregister`、地址到会话的映射、状态归约和快照发布。
- `ConnectionAttemptCoordinator` 只串行化物理建连动作；已经连接的设备继续
  各自持有通道和 Reader，不受其他设备建连影响。
- `EarbudDeviceSession` 每台设备一个实例，拥有协议 adapter、连接循环、
  串行写入和接收循环；通道故障只在有限恢复周期内重建通道，不重建设备
  会话。
- `EarbudChannel` 隔离传输实现。当前只有 RFCOMM；后续 BLE 型号可增加
  GATT 实现而不改服务和会话生命周期。

完整流程：

1. Hook `A2dpService.handleConnectionStateChanged`。
2. A2DP 进入 connected 且 Registry 解析到允许接入的 Adapter 时，向
   连接管理器注册设备并创建一个设备会话。
3. 若具体 Adapter 要求私有协议，会话使用继承得到的首选 UUID 建立 RFCOMM，
   通道成功后向 MiLink 发布 MMA connected，并发送握手、降噪和电量查询；
   身份级回退则立即就绪，不创建任何私有通道。
4. Reader 在单独 IO 协程中持续解码，Reducer 更新状态。
5. 只有发生实际变化的新状态才通过显式、定向广播同步给
   `com.milink.service`；模块 App 仅在前台打开时按需请求快照。
6. RFCOMM 异常时发布 channel disconnected，在原设备会话内按
   `2 s / 10 s / 60 s` 最多恢复三次；仍失败则进入休眠，不再产生周期
   唤醒。新的 A2DP 注册事件或显式 Refresh 可启动下一轮有限恢复。
7. A2DP 断开时注销设备；AdapterService 销毁时执行统一 teardown，取消
   连接、Reader、重连任务，关闭 socket 并注销 Receiver。

连接管理器允许同时存在多个活动设备会话，与小米
`BluetoothDeviceManager.mConnectedList` 的形态一致；只有 SPP 建连任务
串行。每个地址具有独立状态和 session token，旧 token 的延迟广播或旧会话
回调不能覆盖同地址的新生命周期。

### 5.2 `com.milink.service`

MiLink 桥按蓝牙地址缓存 Bluetooth 进程同步的状态，并为方法参数中的目标
设备提供：

- `checkIsMiTWS = 1`、`isMiTWS = true`。
- `getDeviceId = Adapter.miLinkIdentity.deviceId`。
- `getBatteryLevel` 与运行时电量列表。
- `getAncState` 及开/关降噪、通透命令桥接。
- `isSupportAudioSwitch = 1`。

电量、降噪和佩戴接口严格按 Adapter 能力覆盖。身份级回退不覆盖这些方法，
`getSwitchState` 返回无降噪控制，使融合卡片退化为系统音量与流转入口。

Hook 只在实机确认使用耳机桥的 MiLink `:audio`、`:core` 和 `:ui` 进程安装。
状态通知按
身份、连接、电量、降噪字段做差量分发；未变化的协议报告不会产生广播或
MiLink 回调。

设备流转仍由系统的 A2DP/MiLink 原生路径执行。HyperEars 不直接调用隐藏
的“切走/切回”实现；只让系统把 vivo 耳机视为可流转耳机。

系统设置页和 Xiaomi Bluetooth 耳机服务明确不在作用域内；普通蓝牙详情
保持 ROM 原行为。

## 6. 跨进程状态协议

广播 action 使用 `dev.hyperears.action.*` 命名空间，并始终：

- 使用 `Intent.setPackage` 定向到已知作用域。
- 使用结构化 primitive extras，不跨进程传自定义 Parcelable。
- 带 `address`、`model_id` 和单调递增 `revision`。
- 带 `session_active` 区分 Profile 会话结束与私有通道暂时不可用。
- 接收端按地址拒绝 revision 倒退以及已淘汰 token 的延迟状态。

主要消息：

- `REQUEST_STATE`：请求 Bluetooth 进程立即查询并重发快照。
- `CONTROL`：携带统一控制类型及参数。
- `STATE_CHANGED`：完整状态快照，避免只发增量造成进程重启后缺字段。

## 7. HyperOS 映射

### 7.1 电量

MiLink 电量列表顺序：

```text
[case, left, right, caseCharging, leftCharging, rightCharging]
```

### 7.2 降噪

统一模式与 vivo wire：

| 统一模式 | vivo | MiLink |
|---|---:|---:|
| 关闭 | 1 | 0 |
| 降噪 | 0 | 1 |
| 通透 | 2 | 2 |

Air3 Pro 设置帧固定使用 GAIA v3 载荷 `mode 04 00`。

### 7.3 兼容身份

首版使用参考项目已验证可进入 HyperOS 耳机运行时的兼容 ID
`01010607`。该兼容身份由标准 Adapter 提供并被 vivo 和具体型号继承；未来
若某型号需要不同面板能力，只覆盖该 Adapter。

## 8. 可靠性约束

- RFCOMM 连接、读、写均在 IO dispatcher；系统 Binder 主线程不阻塞。
- 写操作串行化，禁止查询和设置帧交叉写入。
- 一个设备周期只执行一次初始连接及最多三次有限恢复；耗尽后不设定时器，
  A2DP 断开立即取消当前建连和 Reader。
- 物理建连使用全局 Mutex 串行，活动 socket、协议 adapter 和 Reader 按
  地址完全独立。
- 正式会话只尝试 Air3 Pro 已实机确认的 `0837` UUID；候选端点扫描留在
  独立协议测试项目中。
- socket 关闭、任务取消、Receiver 注册和服务销毁必须幂等。
- Hook 安装逐项 `runCatching`；单个 ROM 类名变化不能阻止其余桥接加载。
- 反射方法按明确签名优先，混淆别名只作为受测试的兼容表。
- 日志不输出完整蓝牙地址；仅显示脱敏后四位。

## 9. 安全边界

- 控制广播必须是显式包定向，并校验来源数据、目标地址和模式枚举。
- 只有 Registry 匹配的已配对目标设备会被伪装。
- 不修改系统全局 `BluetoothDevice` 名称或地址。
- 不加载厂商 App 代码，不向厂商 App 暴露控制接口。
- 不对尚未验证的帧执行写操作。

## 10. 测试策略

### JVM 单元测试

- Air3 Pro 名称匹配及其他名称拒绝。
- Adapter 继承链、Registry 回退顺序与端点继承。
- 实机握手、降噪、电量帧到统一事件的映射。
- 分片、粘包、扩展长度帧解码。
- State Reducer 对未知电量、ACK 和连接代次的处理。
- MiLink 电量/降噪映射。

### 构建检查

- `protocol`、`integration` 单元测试。
- `system-module` 与 `protocol-test` Debug APK。
- Android lint。
- APK 中检查 LSPosed `module.prop`、`scope.list`、`java_init.list`。

### 实机验收

1. 安装 APK，启用 LSPosed 模块并勾选 `com.android.bluetooth` 与
   `com.milink.service` 两个静态作用域。
2. 重启 `com.android.bluetooth` 和 `com.milink.service`，必要时重启系统。
3. 连接 vivo TWS Air3 Pro。
4. 验证模块日志出现 Air3 Pro Adapter 匹配、RFCOMM 0837、握手及完整快照。
5. 验证融合卡片电量与测试 App 一致。
6. 从融合卡片依次切换关闭、降噪、通透，确认收到 `0x8130` ACK。
7. 在融合设备中心触发音频切换，确认系统 A2DP 路由变化；此项不以 vivo
   私有协议帧作为成功判据。

## 11. 扩展新型号的最小改动

新增 vivo 型号只需要：

1. 从对应家族 Adapter 继承新的具体型号 Adapter。
2. 只覆盖型号名称、能力与 MiLink 身份。
3. 若协议差异可由参数表达，复用协议组件；否则实现新的 `EarbudProtocol`。
4. 按具体型号优先级在 Registry 注册。
5. 增加继承、匹配、抓包与能力回归测试。

Hook、跨进程广播和 HyperOS 桥不需要按型号增加条件分支。
