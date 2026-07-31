# HyperEars

HyperEars 用于把非小米耳机接入 HyperOS 耳机体验。当前包含
`vivo TWS Air3 Pro`、`StarRing Ultra` 和 Bose BMAP 耳机适配，并为其余
标准蓝牙耳机提供流转、音量和系统整机电量回退。

## 当前结论

2026-07-29 已在小米 `23116PN5BC` 与 vivo TWS Air3 Pro 上完成实机验证：

- RFCOMM 可通过 vivo GAIA UUID `00000837-d102-11e1-9b23-00025b00a5a5`
  直接连接，不依赖 vivo 官方 App。
- 握手、当前降噪模式、左右耳与充电盒电量均可读取。
- 降噪、关闭、通透三种模式均可设置，并收到耳机确认及状态上报。
- 耳机实际以 GAIA v3 返回核心状态；公开资料里的 v4 查询也能得到响应，
  正式适配默认采用已抓包确认的 Air3 Pro v3 写入格式。
- Bose QuietComfort Headphones (`prince/0x4075`) 已确认可通过 BMAP
  `[0.3]` 读取产品 ID，并通过 `[2.2]` 读取整机/组件电量；改名不影响
  OUI 初筛和协议内判型。

详细帧、字段和未解析事件见
[`docs/vivo-tws-air3-pro-protocol.md`](docs/vivo-tws-air3-pro-protocol.md)。
Bose 型号与电量帧见
[`docs/bose-bmap-protocol.md`](docs/bose-bmap-protocol.md)。

## 模块

- `protocol`：纯 WireCodec 编解码库，不依赖蓝牙连接和界面。
- `integration`：厂商无关的耳机状态、`Standard → 厂商 → 具体型号`
  Adapter 继承链、每会话 EarbudProtocol 与 HyperOS 数据映射。
- `system-module`：正式 LSPosed 模块。按“服务生命周期—设备连接管理—
  每设备控制通道”管理 Bluetooth RFCOMM 会话，并提供 MiLink 融合设备
  中心兼容桥和 Material 3 运行看板。连接管理按蓝牙地址支持多个会话，
  仅串行化物理建连动作；模块不注入系统设置页。
- `protocol-test`：实机协议实验室。列出已配对耳机，按 vivo、StarRing、
  Bose 探测各自 RFCOMM 端点，验证只读状态与电量并显示完整原始日志。

系统模块的详细分层、进程边界、状态协议、安全约束与扩展流程见
[`docs/system-module-architecture.md`](docs/system-module-architecture.md)。
运行看板的产品定位、生命周期语义与性能边界见
[`docs/dashboard-ui-architecture.md`](docs/dashboard-ui-architecture.md)。
`vivo-tws-air3-pro` Adapter 提供完整电量与降噪协议支持。未知 vivo 型号会
落到 vivo 家族 Adapter，其他可确认是耳机的 A2DP 设备会落到标准 Adapter；
两种回退向 MiLink 提供耳机身份、音频流转能力和 Android 已缓存的整机
电量；整机电量按 MiLink 数据结构同时映射到左右耳，充电盒保持未知。回退
不建立私有通道，也不伪造降噪能力。音箱、车机、无法判断为耳机的设备以及名称可确认由
HyperOS 原生支持的小米/REDMI 耳机不会被接管。

Adapter 只声明 `TWS` 或 `HEADPHONES` 物理形态。MiLink 桥分别复用一个
官方已知载体 ID，让系统原生完成耳机支持判断、去重、跨端类型恢复和卡片
形态选择；具体第三方型号不再伪造成设备 ID，也不 Hook 混淆型号分类器。
仅型号专属的可选卡片扩展通过 `CirculateServiceInfo.serviceProperties`
中的版本化命名空间元数据传递。

## 已记录的 vivo 协议变体

| 变体 | GAIA 版本 | 降噪查询载荷 | 降噪设置载荷 |
|---|---:|---|---|
| Air3 Pro 实机抓包 | 3 | 空 | `mode 04 00` |
| 手工逆向公开资料 | 4 | `00` | `mode 03 01` |
| vivo TWS 3e 公开实现 | 3 | 空 | `mode 03` |

三种变体共同使用 vivo vendor `0x001B`、降噪命令 `0x0130/0x0230`
以及模式编号 `0=降噪、1=关闭、2=通透`。电量实验使用公开资料中的
`0x0207/0x8207`。

## 构建

```powershell
./gradlew.bat testDebugUnitTest :protocol-test:assembleDebug :system-module:assembleDebug
```

Debug APK 输出到
`protocol-test/build/outputs/apk/debug/protocol-test-debug.apk` 和
`system-module/build/outputs/apk/debug/system-module-debug.apk`。

Android 12 及以上需要同时授予 `BLUETOOTH_CONNECT` 和 `BLUETOOTH_SCAN`；
应用首次启动会统一请求。连接按钮会自动完成只读探测。锁屏下做 ADB
回归时，Debug 包还支持：

```powershell
adb shell am start -n dev.hyperears.protocoltest/.MainActivity --ez auto_probe true
adb logcat -s HyperEarsProtocol:D "*:S"
```

## 资料来源

- 当前 `AndroidBluetoothHelper` 的 Air3 Pro 实机抓包实现。
- https://github.com/Star-ZER0/Pods-Protocol-Reverse-Engineering
- https://github.com/moculll/ScrewVivoTWS
