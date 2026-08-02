# HyperEars 系统模块架构

本文描述当前实现的对象边界、会话生命周期和扩展约束。实现以“一台物理耳机、一个当前
Adapter、一个设备会话”为基本单位。

## 1. 目标与边界

HyperEars 在小米系统已有的蓝牙与 MiLink 流程上补充第三方耳机信息：

- 监听系统耳机连接生命周期；
- 为需要厂商私有协议的设备建立有限的 GATT、RFCOMM 或 L2CAP 通道；
- 把电量、噪声模式、物理形态和控制能力投影给 MiLink；
- 复用小米原生的 TWS/头戴耳机载体 ID，不建立自定义 MiLink 设备表；
- 只在具体呈现确有差异时进行一次性卡片扩展。

模块不替代 A2DP、HFP、系统音量、音频路由或 MiLink 的设备流转实现。

## 2. 模块划分

```text
protocol
  纯字节 WireCodec、帧解析器与协议常量

integration
  EarbudAdapter、ProtocolSession、能力/状态模型、Adapter Registry

system-module
  LSPosed 入口、系统蓝牙生命周期、私有传输、跨进程状态、MiLink 桥、运行看板
```

依赖方向固定为：

```text
system-module -> integration -> protocol
```

`protocol` 不认识 Adapter、Android、MiLink 或 Socket；`integration` 不持有 Android
蓝牙连接；`system-module` 不解释厂商帧。

## 3. 设备会话聚合

### 3.1 EarbudAdapter

`EarbudAdapter` 是一台物理耳机在当前系统会话内的聚合根。它统一拥有：

- 型号/家族身份与匹配分辨率；
- 物理形态和 MiLink 呈现 ID；
- 当前已确认的能力与支持的噪声模式；
- 电量、噪声模式等运行状态；
- 候选传输和控制确认策略；
- 一个 `ProtocolSession`。

Registry 存放工厂而不是 Adapter 单例。同一地址的新会话、不同地址的并行会话都会获得
独立 Adapter 和独立 ProtocolSession，不共享解码缓冲、序列号、ACK 队列或运行状态。

Adapter 继承链只表达可复用行为：

```text
EarbudAdapter
  └─ StandardEarbudAdapter
       ├─ vivo / iQOO
       ├─ StarRing
       ├─ OPPO Enco
       ├─ Bose
       ├─ Edifier
       ├─ ROSESELSA
       ├─ NiceHCK
       ├─ Apple AAP
       └─ Sony
```

具体型号继承对应家族 Adapter，只覆盖已知差异。型号字节差异以内嵌的 WireConfig 表达，
不再存在与 Adapter 并列、可被 UI 或运行时单独查找的 Profile 抽象。

### 3.2 ProtocolSession

`ProtocolSession` 是 Adapter 内部可转移的协议状态核心，负责：

- 保存流式解码器、序列号、ACK/请求队列和握手进度；
- 把控制请求编码为完整写事务；
- 把输入字节转换为 `ProtocolEvent`；
- 提供协议事件触发的后续只读请求和控制回读。

它不选择型号、不创建 Adapter、不连接 Socket，也不向 UI/MiLink 发布状态。

当协议身份确认需要换成更具体的 Adapter 时，新 Adapter 可以复用原 ProtocolSession；若
线协议或传输不兼容，也可以选择新 ProtocolSession 并要求重启当前通道或重新连接。

### 3.3 WireCodec

WireCodec 只处理一类厂商帧的字节语义：封包、拆包、校验、字段解析。设备名称、能力、
传输优先级和 MiLink 呈现不进入 Codec。

因此三者关系为：

```text
EarbudAdapter
  ├─ 设备身份、能力、策略、运行状态
  ├─ WireConfig（型号字节差异）
  └─ ProtocolSession
       └─ WireCodec（纯字节转换）
```

## 4. 首次匹配与协议细化

`EarbudAdapterRegistry` 仅在系统耳机连接时执行一次初始匹配，顺序为：

```text
具体型号 -> 厂商协议家族 -> 品牌标准回退 -> Standard Bluetooth
```

匹配依据来自 Android 已缓存的设备名称、Class、服务 UUID 和地址信息，不主动扫描。
Registry 不按 ID 恢复运行时 Adapter，也不参与协议升级。

需要私有协议的 Adapter 在会话第一阶段执行只读确认。结果只有四种：

- `AwaitingEvidence`：初始请求已写入，等待有效响应；
- `Ready`：当前 Adapter 和 ProtocolSession 可继续使用；
- `Rejected`：响应明确不兼容，当前候选传输失败；
- `Replace`：返回新的 Adapter 及激活策略。

`Replace` 的激活策略为：

- `KEEP_CHANNEL_READY`：复用当前通道和 ProtocolSession；
- `RESTART_ON_CURRENT_CHANNEL`：保留通道，以新 Adapter 重新执行初始化；
- `RECONNECT`：按新 Adapter 的传输声明重新连接。

替换由 `EarbudDeviceSession` 在同一设备会话内原子完成。系统只保存一个 `adapter` 成员，
不存在初始 Adapter、effectiveAdapter 或 Adapter/Profile 双状态。

## 5. 能力真实性

具体型号可直接声明已有实机或公开资料确认的能力。仅靠家族名称匹配的 Adapter 默认不
开放未经确认的私有写能力：

1. 建立候选传输；
2. 发送只读握手/状态查询；
3. ProtocolSession 产生有效 `CapabilitiesIdentified`；
4. 当前 Adapter 更新已确认能力，或替换成协议确认 Adapter；
5. 发布新的完整 AdapterSnapshot；
6. MiLink 才看到对应控制项。

有效电量响应只确认电量；有效噪声状态/协议能力响应才确认噪声控制。失败或超时不会把
静态猜测能力留在卡片上。

Bose 是型号细化示例：BMAP 产品 ID 产生 `ProductIdentified(productId)`，Bose Adapter
把产品 ID 映射为具体 Adapter，并将已有 ProtocolSession 和运行状态转移过去。未知产品
只在只读 STATUS 明确确认 AudioModes、ANR 或 CNC 方言后开放相应控制。

## 6. 生命周期

`EarbudState` 只保存一个 `DeviceLifecycle`，不保存可互相矛盾的连接布尔组合：

```text
SystemProfileState
  DISCONNECTED / CONNECTED

PrivateTransportState
  NOT_REQUIRED / IDLE / CONNECTING / CONNECTED / RECOVERING / DORMANT

ProtocolHandshakeState
  NOT_REQUIRED / PENDING / CONFIRMED / REJECTED
```

`sessionActive`、`connected`、`privateChannelConnected` 和 `handshakeAccepted` 仅是从该对象
计算出的兼容视图。声明 `PROTOCOL_HANDSHAKE` 的设备需要系统音频、私有传输和协议确认
同时就绪；声明“传输连接即就绪”的已验证设备将协议状态保持为 `NOT_REQUIRED`，不会
伪造一次确认事件。

典型流程：

```text
A2DP/HFP connected
  -> Registry 创建初始 Adapter
  -> EarbudDeviceSession(CONNECTING)
  -> 候选传输 CONNECTED
  -> 需要确认时：协议 PENDING -> Ready 或 Replace -> CONFIRMED
  -> 无需确认时：协议 NOT_REQUIRED
  -> 广播完整状态快照
  -> MiLink 接收、查询身份/能力、刷新卡片
```

通道异常进入 `RECOVERING`，有界重试耗尽后进入 `DORMANT`。系统音频会话仍保留，重新
注册或显式刷新可唤醒连接；A2DP/HFP 断开才销毁设备会话。

## 7. 传输与并发

`EarbudDeviceSession` 是私有传输唯一所有者。Adapter 只声明有序的
`EarbudTransportSpec`，包括：

- `RfcommEndpointSpec`；
- `GattTransportSpec`；
- `L2capEndpointSpec`。

每台设备最多一个活动通道、一个 Reader、一个连接任务和一个串行写事务。控制写、协议
即时响应和回读都经过同一互斥写入路径，防止重复帧和交叉事务。全局协调器只串行化昂贵
的连接尝试，不限制多个已连接设备会话。

## 8. 跨进程状态与 MiLink

Bluetooth 进程发布的状态包含：

- 完整 `AdapterSnapshot`；
- `DeviceLifecycle`；
- 电量和噪声模式运行态；
- 地址、会话令牌和单调 revision。

MiLink 和应用 UI 直接消费快照，不按 `modelId` 重新访问 Registry。旧版布尔 extra 只在
IPC 边界保留兼容编码，新版接收端优先读取三个生命周期枚举。

MiLink 设备 ID 只由 `AdapterSnapshot.formFactor` 映射：TWS 使用一个已知原生耳机载体，
头戴耳机使用一个已知原生头戴载体。具体型号不伪造新的设备 ID 查找表。

常规电量、噪声模式和能力通过官方查询路径提供。只有原生卡片无法表达的、已验证的少量
呈现差异，才由 `MiLinkCardAdapter` 在 View 创建/绑定时进行一次性处理；该层不连接蓝牙
也不轮询。卡片扩展只依赖稳定的原生 View ID，不 Hook 混淆回调：例如 Bose
AudioModes 两态设备保留系统三项布局，但把协议不支持的“关闭”项设为不可点击；支持
抗风噪的具体型号则由对应 Adapter 选择明确的卡片呈现 ID。

## 9. UI 投影

`DeviceSessionUiProjector` 只读取不可变状态快照，输出通用 `DeviceSessionUiModel`。
Compose 不导入具体 Adapter、ProtocolSession、WireCodec 或厂商类型。

每个会话展示两组真实阶段：

- 耳机侧：系统音频、Adapter 分辨率、私有传输、协议确认；
- MiLink 侧：状态接收、身份查询、能力查询、运行时通知。

连接中、恢复中、休眠和协议拒绝均来自 `DeviceLifecycle`，不由 UI 根据时间或型号猜测。

## 10. 扩展规则

新增适配按以下顺序选择最小改动：

1. 仅名称/形态差异：增加具体 Adapter；
2. 同一线协议、字段不同：增加 Adapter 内 WireConfig；
3. 同一协议状态机、产品身份可确认：在 Adapter 握手阶段返回 `Replace`；
4. 新状态机：增加 ProtocolSession；
5. 新帧格式：增加 WireCodec；
6. 原生卡片无法表达的呈现：增加独立 MiLinkCardAdapter。

不得让 Registry、UI 或 MiLink Hook 解析厂商帧；不得让 ProtocolSession 按零售名称选择
设备；不得通过共享 Adapter/ProtocolSession 单例复用会话状态。

## 11. 性能约束

- 初始匹配只在系统连接事件发生时执行；
- 不主动扫描，不周期轮询设备；
- 每台活动私有协议设备一个阻塞 Reader；
- 状态仅在 Adapter 快照、运行态或生命周期变化时发布；
- 未确认或不需要私有协议的设备不建立额外通道；
- 卡片扩展只在对应 View 生命周期执行。

## 12. 验证重点

测试至少覆盖：

- Registry 每次返回独立 Adapter/ProtocolSession；
- 具体型号、家族和标准回退顺序；
- 家族能力在协议证据前保持关闭；
- Adapter 替换时 ProtocolSession 与运行状态正确转移；
- 生命周期枚举不会组合出虚假的 ready 状态；
- 跨进程 AdapterSnapshot 和 DeviceLifecycle 往返一致；
- UI/MiLink 不依赖 Registry 按 ID 重建运行时对象；
- 一次控制只产生一次完整写事务。
