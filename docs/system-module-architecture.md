# HyperEars 系统适配模块架构

## 1. 目标与范围

当前版本在 HyperOS 3 / Android 16 上提供：

- 跟随系统 A2DP 连接，为需要私有协议的具体 Adapter 建立其声明的 GATT 或
  RFCOMM 控制链路。
- 向 MiLink 暴露最小兼容身份，使融合设备中心识别目标耳机。
- 在融合卡片读取左右耳/充电盒电量和当前降噪状态。
- 将融合卡片的控制操作交给具体型号 Adapter，翻译为已实机验证的厂商命令。
- 通过 `Standard → 厂商家族 → 具体型号` 继承链接入 vivo、StarRing、OPPO
  和 Bose，并保留其他型号的稳定扩展点。

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
  └─ vivo / StarRing / OPPO / Bose WireCodec、流式分帧与纯字节编解码

integration
  ├─ 通用耳机模型与控制事件
  ├─ Standard / 厂商家族 / 具体型号 Adapter 继承链
  ├─ Adapter Registry
  ├─ vivo 具体型号选择的不可变协议画像与通用协议组件
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
       ├─ VivoEarbudAdapter
       │    ├─ VivoTwsAir3ProAdapter
       │    └─ VivoTws3eAdapter
       ├─ StarRingEarbudAdapter
       │    └─ StarRingUltraAdapter
       ├─ OppoEarbudAdapter
       │    ├─ OppoEncoAir2ProAdapter
       │    ├─ OppoEncoFree4Adapter
       │    ├─ OppoEncoX3Adapter
       │    └─ OppoEncoAir5Adapter
       └─ BoseEarbudAdapter
            └─ BoseHeadphonesAdapter
                └─ BoseQuietComfortHeadphonesAdapter
```

子类继承父类的通用能力，只覆盖经实机确认的差异：

- `StandardEarbudAdapter` 表示 Android 原生 A2DP/HFP、音量、路由和系统
  缓存的整机电量能力。
- `VivoEarbudAdapter` 继承标准层，增加 vivo/iQOO TWS 家族名称规则和
  GAIA 端点、家族默认 v4 Profile、私有电量和三态降噪能力。
- `VivoTwsAir3ProAdapter` 继承 vivo 层，只覆盖精确名称并选择实机验证的
  Air3 Pro `mode 04 00` Profile。
- `VivoTws3eAdapter` 继承 vivo 层，选择公开实现对应的 v3 `mode 03`
  Profile，把 channel 13 作为 UUID/SDP 失败后的端点回退；电量继承家族
  `0x0207/0x8207` 只读查询。
- `OppoEarbudAdapter` 继承标准层，增加 OPPO/Enco 名称规则、固定 RFCOMM
  UUID、电量和标准三态降噪；具体 OPPO Adapter 只声明型号 Profile，
  `Air2 Pro` 覆盖其相反的 ANC/关闭编码。
- `BoseEarbudAdapter` 使用已连接耳机的名称/Bose OUI 做无扫描初筛，创建
  BMAP 会话后以 `[0.3]` 确认产品 ID，以 `[2.2]` 读取组件电量。名称只允许
  进入家族层；只有协议内产品 ID 才能升级到具体 Bose 型号。

Registry 固定按“具体型号 → 厂商家族 → 标准耳机”解析：

- vivo/iQOO TWS 家族 Adapter 建立私有通道并发布家族电量、三态降噪和
  流转能力；Air3 Pro/TWS 3e 具体 Adapter 仅覆盖已知字节差异。
- OPPO 具体型号优先覆盖通用 OPPO Profile；其余 OPPO/Enco 名称使用参考
  实现的标准电量和三态降噪协议。
- 标准 Adapter 只接管 Bluetooth Class 明确为耳机，或名称保守命中耳机
  关键词的设备，同样发布身份、系统整机电量和流转能力。
- 音箱、车机和无法确认是耳机的设备不匹配任何 Adapter；名称可确认由
  HyperOS 原生支持的小米/REDMI 耳机也明确排除，保留官方完整路径。

标准耳机身份级回退不会创建私有通道、启动 Reader、安排重连或覆盖降噪方法。
它只在 A2DP 会话建立时读取一次 Android 蓝牙电量缓存，并监听系统已有的
电量变化广播，不轮询设备。

Registry 只选择 Adapter，不包含蓝牙连接或全局状态。新增型号不修改 Hook
分发代码，也不依赖“当前只有一个型号”的默认兜底。

### 4.2 EarbudProtocol

Adapter 与有状态协议组件采用组合关系。每个私有通道会话从选中的 Adapter
创建独立 `EarbudProtocol` 实例，其职责为：

- 给出连接后的只读初始化命令。
- 把统一控制请求翻译成厂商字节帧。
- 增量消费任意分片/粘包并产生统一领域事件。
- 在权威型号事件后给出该型号才允许执行的后续只读命令。
- 给出控制成功后需要执行的权威只读回查。

厂商原始帧由 `VivoTwsProtocol`、`StarRingWireCodec`、`OppoWireCodec`、
`BoseBmapWireCodec` 等纯字节组件负责。`EarbudProtocol` 不拥有 Socket，
WireCodec 不认识 Adapter、MiLink 或生命周期。

具体型号拥有自己的不可变协议画像。例如
`VivoTwsAir3ProAdapter` 与 `VivoTws3eAdapter` 分别选择 GAIA v3 的
`mode 04 00` 和 `mode 03` Profile；通用 vivo 协议实例只消费所选 Profile，
没有零售型号分支。`VivoEarbudAdapter` 默认选择公开 v4 Profile；发现具体
型号不兼容时，由该型号 Adapter 覆盖 Profile，而不修改家族状态机。
`BoseQuietComfortHeadphonesAdapter.bmapProfile` 声明产品 ID、Quiet/Aware
槽位、模式解释阈值，以及从 ModeConfig 的 `wind=true` 动态发现通勤槽的策略；
Bose 家族协议在产品 ID 未确认前只读型号与电量，不发送 AudioModes 查询，也
不暴露具体型号能力。产品画像 Registry 是唯一组合根，
通用协议中没有具体产品常量。

控制确认策略由 Adapter 声明为
`DEVICE_REPORT / PUBLISH_AFTER_WRITE / PUBLISH_AFTER_WRITE_THEN_REFRESH`。
会话在同一个事务锁内统一执行“写入 → 可选发布 → 可选回查”，协议实现不再
通过把查询命令混进设置命令来隐式修补 UI 状态。

统一控制请求首版包含 `Refresh` 与 `SetNoiseMode`。统一领域事件明确区分
系统 Profile 会话和私有通道：

- `SessionStarted/SessionEnded`：目标设备进入或离开系统 Profile 生命周期。
- `ChannelConnected/ChannelDisconnected`：厂商控制通道可用或丢失。
- `BatteryChanged`：由标准系统电量源或具体型号私有协议产生。
- `Handshake`、`NoiseModeChanged` 和 `UnknownFrame`：私有协议产生的
  状态事件。

### 4.3 EarbudStateReducer

Reducer 是纯函数，接收旧状态和协议事件，输出新状态：

- 未出现的部件保持未知，而不是伪造为 0%。
- `0xFF` 电量映射为未连接/不可用。
- 控制写入后可发布一次明确标记为未确认的乐观降噪状态；耳机 ACK 或状态
  报告仍是最终权威值并负责纠正。
- 新 Profile 会话开始或结束时清除易失状态。
- 同一 Profile 会话内的私有通道重连保留最近一次设备状态。

系统桥只读取统一状态，不直接理解任何厂商 payload。连接、重连、Socket 和
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
                 ├─ Android BLE GATT
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
- `EarbudChannel` 把 BLE GATT 通知和 RFCOMM 流统一为有序字节通道；
  Adapter 通过 `EarbudTransportSpec` 声明候选顺序，服务、会话与协议不包含
  型号传输分支。

完整流程：

1. Hook `A2dpService.handleConnectionStateChanged`。
2. A2DP 进入 connected 且 Registry 解析到允许接入的 Adapter 时，向
   连接管理器注册设备并创建一个设备会话。
3. 若 Adapter 要求私有协议，会话按 Adapter 声明的候选传输建立 GATT 或 RFCOMM，
   通道成功后向 MiLink 发布 MMA connected，并发送家族安全的初始只读查询；
   权威型号事件可在同一串行事务中解锁具体型号后续查询。身份级回退则立即
   就绪，不创建任何私有通道。
4. Reader 在单独 IO 协程中持续解码，Reducer 更新状态。
5. 只有发生实际变化的新状态才通过显式、定向广播同步给
   `com.milink.service`；模块 App 仅在前台打开时按需请求快照。
6. 私有通道异常时发布 channel disconnected，在原设备会话内按
   `2 s / 10 s / 60 s` 最多恢复三次；仍失败则进入休眠，不再产生周期
   唤醒。新的 A2DP 注册事件或显式 Refresh 可启动下一轮有限恢复。
7. A2DP 断开时注销设备；AdapterService 销毁时执行统一 teardown，取消
   连接、Reader、重连任务，关闭 socket 并注销 Receiver。

连接管理器允许同时存在多个活动设备会话，与小米
`BluetoothDeviceManager.mConnectedList` 的形态一致；只有物理建连任务
串行。每个地址具有独立状态和 session token，旧 token 的延迟广播或旧会话
回调不能覆盖同地址的新生命周期。

### 5.2 `com.milink.service`

MiLink 桥按蓝牙地址缓存 Bluetooth 进程同步的状态，并为方法参数中的目标
设备提供：

- `checkIsMiTWS = 1`、`isMiTWS = true`。
- `getDeviceId = MiLinkCarrierIdentity.deviceId(Adapter.formFactor)`。
- `getBatteryLevel` 与运行时电量列表。
- `getAncState` 及开/关降噪、通透命令桥接。
- `isSupportAudioSwitch = 1`。

电量、降噪和佩戴接口严格按 Adapter 能力覆盖。身份级回退提供系统整机
电量，但 `getSwitchState` 返回无降噪控制，使融合卡片保留电量、系统音量
与流转入口。

MiLink 的设备 ID 只承担“进入官方耳机路径并选择物理形态”的职责，不再承担
具体第三方型号编码：

- `HeadsetFormFactor.TWS` 统一映射到官方已知 TWS 载体 ID `01010607`。
- `HeadsetFormFactor.HEADPHONES` 统一映射到官方已知头戴载体 ID
  `01013A04`。
- Adapter 只声明平台无关的物理形态；载体映射集中在 system-module，
  integration 层不依赖任何 Xiaomi 常量。
- MiLink 原有注册表和类型恢复函数据此自然得到原生 TWS 或头戴类型。模块
  不再 Hook 混淆分类器，不修改官方查找表，也不伪造一套型号 ID 表。
- 载体 ID 不能、也不用于反查具体型号。具体型号能力始终来自连接设备上的
  Adapter 与统一状态；可选的远端卡片扩展由独立元数据传递。

本地身份入口只保留 Mx Bluetooth SDK 的 `getDeviceId` 和支持能力查询。
`ProfileContext`、`AncBatteryController` 继续沿官方调用链读取 Mx 返回值；
官方的型号支持判断、蓝牙耳机去重、`HeadsetInfo → HeadsetDeviceInfo` 转换
和远端类型恢复均不再单独 Hook。

### 5.3 型号化卡片呈现

具体型号可声明一个不含品牌语义的 `MiLinkCardPresentationId`。系统模块通过
独立 `MiLinkCardAdapterRegistry` 将该 ID 绑定到具体的
`MiLinkCardAdapter`：

- 通用协调器只按地址、根 View 和呈现 ID 管理生命周期，不包含型号名称、
  View ID 或布局规则。
- 呈现 ID 优先来自本机权威状态；发布端在
  `HeadsetDeviceManager.convertToBluetoothService` 完成后，将版本化的
  呈现 ID 写入 `CirculateServiceInfo.serviceProperties`。该 `ExtraBundle`
  随官方 Parcelable 链路传输，接收端无需本地蓝牙历史即可读取。
- 具体卡片 Adapter 独占 MiLink View ID、一次性结构调整和该型号附加控件。
- 每个根 View 只安装一个 attach 监听；状态变化只调用现有 Binding 的
  `render`，不扫描布局、不轮询。
- detach 或根 View 改绑时调用 `unbind` 恢复原生 View，避免重复监听、残留
  布局和跨型号污染。

Bose QuietComfort 的具体卡片 Adapter 一次性把不受支持的“关闭”按钮替换为
同 ID、同布局参数的原生 `HeadsetControlAncItemView`，用于切换协议动态发现的
“抗风噪”模式槽。StarRing Ultra 保留 MiLink 的通透、降噪、关闭三态卡片，
具体卡片 Adapter 只在降噪分支旁加入“抗风噪”开关；布局、字体、开关尺寸和选中
动画继续由宿主控件负责。通用 MiLink Hook 不包含这两种型号的 UI 分支。

Hook 只在实机确认使用耳机桥的 MiLink `:audio`、`:core` 和 `:ui` 进程安装。
通用卡片扩展仅结构匹配 `HeadSetsDetail` 的稳定四参数绑定签名；不依赖其
混淆方法名。若版本不包含该签名，扩展安全失效，官方通用耳机卡片仍可工作。
状态通知按
身份、连接、电量、降噪字段做差量分发；未变化的协议报告不会产生广播或
MiLink 回调。

设备流转仍由系统的 A2DP/MiLink 原生路径执行。HyperEars 不直接调用隐藏
的“切走/切回”实现；只让系统把目标第三方耳机视为可流转耳机。

系统设置页和 Xiaomi Bluetooth 耳机服务明确不在作用域内，HyperEars 不修改
Settings 的 Fragment 或控件。第三方卡片点击“更多设置”时，仅在 MiLink 稳定的
`HeadsetServiceController.switchToHeadsetActivity(CirculateServiceInfo)` 语义边界
读取 `deviceId` 中的真实蓝牙地址，并启动 ROM 原生蓝牙设备详情 Activity；官方
Xiaomi 耳机和非 HyperEars 卡片继续执行原方法。若具体详情 Intent 不可用，则回退
到系统蓝牙列表。

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

标准 Adapter 的权威来源为 Android `BluetoothDevice` 电量缓存。系统通常只
提供一个整机百分比，因此映射为 `left = right = aggregate`，充电盒及充电
状态保持未知。具体型号可把 `batterySource` 覆盖为 `PRIVATE_PROTOCOL`；
Air3 Pro 即使用 vivo 上报的真实左耳、右耳和充电盒数据，系统电量广播不会
覆盖它。

### 7.2 降噪

统一模式与 vivo wire：

| 统一模式 | vivo | MiLink |
|---|---:|---:|
| 关闭 | 1 | 0 |
| 降噪 | 0 | 1 |
| 通透 | 2 | 2 |

Air3 Pro 设置帧固定使用 GAIA v3 载荷 `mode 04 00`。

### 7.3 兼容身份

兼容身份只按物理形态选择两个官方载体：

| Adapter 形态 | 官方载体 ID | MiLink 原生类型 |
|---|---|---:|
| `TWS` | `01010607`（K73） | 0 |
| `HEADPHONES` | `01013A04`（O70C） | 7 |

具体型号不再生成 MiLink ID。新增型号只需继承正确的父 Adapter；TWS 沿用
默认形态，头戴型号覆盖 `formFactor = HEADPHONES`。可选的型号化卡片能力以
版本化 `serviceProperties` 元数据传递，不能改变官方形态分类。

## 8. 可靠性约束

- RFCOMM 连接、读、写均在 IO dispatcher；系统 Binder 主线程不阻塞。
- 写操作串行化，禁止查询和设置帧交叉写入。
- 一次控制请求及其回查构成完整串行事务；并发请求不能在帧级交叉。
- 一个设备周期只执行一次初始连接及最多三次有限恢复；耗尽后不设定时器，
  A2DP 断开立即取消当前建连和 Reader。
- 物理建连使用全局 Mutex 串行，活动 socket、协议 adapter 和 Reader 按
  地址完全独立。
- 正式会话只尝试当前 Adapter 明确声明的有限端点，例如 vivo `0837`、OPPO
  `079a` 或 Bose BMAP；候选 UUID/通道枚举留在独立协议测试项目中。
- socket 关闭、任务取消、Receiver 注册和服务销毁必须幂等。
- Hook 安装逐项 `runCatching`；单个 ROM 类名变化不能阻止其余桥接加载。
- 反射方法按稳定类名和明确签名定位；卡片绑定按参数类型结构匹配，不保存
  混淆方法名兼容表。
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
2. 只覆盖精确名称，并选择经验证的 `VivoTwsProtocol.Profile`；未登记型号
   自动继承家族默认 Profile。
3. 若字节差异可由 GAIA 版本、查询载荷和设置后缀表达，只增加 Profile；
   只有全新状态机才实现新的 `EarbudProtocol`。
4. 按具体型号优先级在 Registry 注册。
5. 增加继承、匹配、抓包与能力回归测试。

Hook、跨进程广播和 HyperOS 桥不需要按型号增加条件分支。
