# HyperEars 运行看板信息架构

## 状态所有权

界面状态按蓝牙地址保存为 `Map<address, DeviceSessionSnapshot>`。同一地址的
新修订只更新对应会话，不同地址同时保留；收到 `sessionActive=false` 时只
移除对应地址。

## 页面结构

- 顶部运行时卡片：Bluetooth 进程 Hook 回执、MiLink 各进程 Hook 回执，
  以及 MiLink 状态接收、身份查询和能力查询的实际会话数量。
- 设备会话列表：每个活动地址独立一张卡片。
- 每设备卡片分为两组状态：
  - 耳机链路：A2DP 会话，以及具体型号需要时的 RFCOMM 通道和协议握手；
    身份级回退显示身份桥就绪且无需私有通道。
  - MiLink 处理：状态接收、身份查询、卡片能力查询和运行时状态通知。
  卡片同时显示型号、完整蓝牙地址、电量和噪声模式。
- 手机使用单列，宽屏设备使用双列。

## 性能边界

页面不轮询、不启动服务、不建立蓝牙连接。Bluetooth 进程仅在状态实际变化
时向模块界面追加一条显式广播；界面未运行时动态接收器不会启动应用。

状态接收只在 MiLink 进程返回匹配蓝牙地址、会话令牌和当前状态修订号的
回执后成立。身份查询、能力查询和状态通知来自相应 Hook 的首次真实调用，
在同一会话内去重；页面打开和手动同步不会伪造这些阶段。旧会话或旧状态
回执不会污染新会话。

## 卡片重建竞态

融合设备中心切换耳机时，MiLink 的
`HeadsetServiceClient.onActiveHeadsetChanged()` 会在切出阶段直接把共享
`HeadsetDeviceInfo` 写成 `mode=-1`、`power=[-1,-1,-1,...]`。详情卡片随后
通过 `HeadsetDeviceManager.getBluetoothDevice()` 读取该对象，所以切出期间
只显示系统音量。这是 MiLink 的临时 UI 投影，不是耳机协议状态的所有者。

HyperEars 不修改 `HeadsetDeviceInfo` 或融合设备中心缓存。Bluetooth 进程按
地址维护逻辑设备记录，使设备身份、能力和最后一次由协议确认的电量/降噪
状态跨越 A2DP 会话结束；RFCOMM、握手和音频输出仍是瞬时连接状态。
MiLink 进程保存相同的“已知设备/活动会话”双层视图：

- `HeadsetInfo` 从已知设备视图返回身份、能力和最后确认状态；
- 控制写入只能使用活动会话视图中的当前令牌；
- 耳机重新成为活动设备时，MiLink 原生
  `HeadsetInfo -> convertToBluetoothDevice -> property callback`
  链路重建 `HeadsetDeviceInfo`，Hook 不越层写 UI 对象。

因此缓存仍可按 MiLink 原设计失效，但恢复不再等待 RFCOMM 重连和协议查询；
恢复数据来自 Bluetooth 侧的数据源状态，而不是 UI 读取时的补丁。

详情页创建时还会调用
`HeadsetServiceController.refreshHeadsetProperty()`。其下游
`getHeadsetPropertyBlock()` 的返回值是操作状态码（`100` 表示成功），不是
电量。vivo 适配层在这个官方协议边界完成刷新：返回成功码并向
`headsetPropertyChangeListener` 发布类型 4 的属性更新，由 MiLink 原生
`onHeadsetPropertyUpdated()` 重新执行 `HeadsetInfo -> HeadsetDeviceInfo`
转换。这样既不会进入不支持的 Xiaomi 私有协议查询，也不会直接改写 UI
缓存。
