# HyperEars 运行看板信息架构

## 状态所有权

界面状态按蓝牙地址保存为 `Map<address, DeviceSessionSnapshot>`。同一地址的
新修订只更新对应会话，不同地址同时保留；生命周期进入 `DISCONNECTED` 时只
移除对应地址。

## 页面结构

应用使用单 Activity 和 Compose Navigation。运行看板与“关于”是独立导航目的地；
关于页不订阅运行状态，也不会触发蓝牙或 MiLink 操作。系统返回手势恢复原看板及其
滚动和会话状态。

- 顶部运行时卡片：Bluetooth 进程 Hook 回执、MiLink 各进程 Hook 回执，
  以及 MiLink 状态接收、身份查询和能力查询的实际会话数量。
- 设备会话列表：每个活动地址独立一张统一卡片，不按品牌或型号选择页面布局。
- 每设备卡片分为两组状态：
  - Adapter 摘要：当前 Adapter 的显示名、稳定 ID、判定等级、耳机形态、电量来源、
    传输类别和已确认能力；这些值来自不可变快照，不由 Compose 查询 Adapter。
  - 耳机链路：A2DP 会话，以及具体型号需要时的 GATT、RFCOMM 或 BR/EDR L2CAP
    私有通道。要求协议握手的 Adapter 显示“协议确认”；声明
    `TransportReadiness.CONNECTED` 的 Adapter 显示“连接即就绪”。身份级回退显示
    身份桥就绪且无需私有通道。
  - MiLink 处理：状态接收、身份查询、卡片能力查询和运行时状态通知。
  - 遥测指标：TWS 显示左/右/盒，头戴设备显示整机；缺失字段显示 `—`，不复用旧值
    或伪造组件。模式能力未声明时明确显示“不支持”。
  卡片同时显示真实蓝牙名称和本机完整蓝牙地址。地址只在本地界面显示。
- 手机使用单列，宽屏设备使用双列。

## 视图边界

`DashboardScreen` 只接收 `DeviceSessionUiModel`。它不导入或查询
`EarbudAdapterRegistry`，也不识别具体 Adapter、Protocol、传输实现或电池拓扑。

```text
EarbudState + BridgeReceipt
             │
             ▼
DeviceSessionSnapshot       会话与 MiLink 回执语义
             │
             ▼
DeviceSessionUiProjector    唯一允许读取通用 Adapter 元数据的 UI 边界
             │
             ▼
DeviceSessionUiModel        文本、阶段、指标等纯呈现数据
             │
             ▼
DashboardScreen             单一布局、无型号分支
```

新增型号只修改 Adapter、其内部线配置和 `ProtocolSession`。除非增加全新的跨设备
通用信息类型，否则主界面不需要跟随型号改动。

## 性能边界

页面不轮询、不启动服务、不建立蓝牙连接。Bluetooth 进程仅在状态实际变化
时向模块界面追加一条显式广播；界面未运行时动态接收器不会启动应用。

状态接收只在 MiLink 进程返回匹配蓝牙地址、会话令牌和当前状态修订号的
回执后成立。身份查询、能力查询和状态通知来自相应 Hook 的首次真实调用，
在同一会话内去重；页面打开和手动同步不会伪造这些阶段。旧会话或旧状态
回执不会污染新会话。

`EarbudState.lifecycle` 是唯一生命周期事实。界面分别投影系统音频、私有传输和协议
确认三个正交阶段；`connected`、`privateChannelConnected`、`handshakeAccepted` 仅是
兼容旧 IPC 的派生读取，不得反向参与状态推断。

协议确认或产品身份细化后，设备会话会原子替换当前 Adapter，并发布一份完整的
`AdapterSnapshot + AdapterRuntimeState + DeviceLifecycle`。Compose 不观察替换过程中的
半成品，也不根据型号 ID 再去 Registry 查能力，因此卡片能力与控制入口始终来自同一
个当前 Adapter。

## 卡片重建竞态

融合设备中心切换耳机时，MiLink 的
`HeadsetServiceClient.onActiveHeadsetChanged()` 会在切出阶段直接把共享
`HeadsetDeviceInfo` 写成 `mode=-1`、`power=[-1,-1,-1,...]`。详情卡片随后
通过 `HeadsetDeviceManager.getBluetoothDevice()` 读取该对象，所以切出期间
只显示系统音量。这是 MiLink 的临时 UI 投影，不是耳机协议状态的所有者。

HyperEars 不修改 `HeadsetDeviceInfo` 或融合设备中心缓存。Bluetooth 进程按
地址维护逻辑设备记录，使设备身份、能力和最后一次由协议确认的电量/降噪
状态跨越 A2DP 会话结束；私有通道、握手和音频输出仍是瞬时连接状态。
MiLink 进程保存相同的“已知设备/活动会话”双层视图：

- `HeadsetInfo` 从已知设备视图返回身份、能力和最后确认状态；
- 控制写入只能使用活动会话视图中的当前令牌；
- 耳机重新成为活动设备时，MiLink 原生
  `HeadsetInfo -> convertToBluetoothDevice -> property callback`
  链路重建 `HeadsetDeviceInfo`，Hook 不越层写 UI 对象。

因此缓存仍可按 MiLink 原设计失效，但恢复不再等待私有通道重连和协议查询；
恢复数据来自 Bluetooth 侧的数据源状态，而不是 UI 读取时的补丁。

详情页创建时还会调用
`HeadsetServiceController.refreshHeadsetProperty()`。其下游
`getHeadsetPropertyBlock()` 的返回值是操作状态码（`100` 表示成功），不是
电量。HyperEars 对已接管设备在这个官方协议边界完成刷新：返回成功码并向
`headsetPropertyChangeListener` 发布类型 4 的属性更新，由 MiLink 原生
`onHeadsetPropertyUpdated()` 重新执行 `HeadsetInfo -> HeadsetDeviceInfo`
转换。这样既不会进入不支持的 Xiaomi 私有协议查询，也不会直接改写 UI
缓存。
