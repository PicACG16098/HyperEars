# HyperEars

HyperEars 用于把非小米耳机接入 HyperOS 耳机体验。当前第一阶段聚焦
`vivo TWS Air3 Pro` 的私有 RFCOMM/GAIA 协议验证。

## 当前结论

2026-07-29 已在小米 `23116PN5BC` 与 vivo TWS Air3 Pro 上完成实机验证：

- RFCOMM 可通过 vivo GAIA UUID `00000837-d102-11e1-9b23-00025b00a5a5`
  直接连接，不依赖 vivo 官方 App。
- 握手、当前降噪模式、左右耳与充电盒电量均可读取。
- 降噪、关闭、通透三种模式均可设置，并收到耳机确认及状态上报。
- 耳机实际以 GAIA v3 返回核心状态；公开资料里的 v4 查询也能得到响应，
  正式适配默认采用已抓包确认的 Air3 Pro v3 写入格式。

详细帧、字段和未解析事件见
[`docs/vivo-tws-air3-pro-protocol.md`](docs/vivo-tws-air3-pro-protocol.md)。

## 模块

- `protocol`：纯协议编解码库，不依赖蓝牙连接和界面。
- `integration`：厂商无关的耳机状态、`Standard → vivo → Air3 Pro`
  型号 Adapter 继承链、私有协议组件与 HyperOS 数据映射。
- `system-module`：正式 LSPosed 模块。按“服务生命周期—设备连接管理—
  每设备控制通道”管理 Bluetooth RFCOMM 会话，并提供 MiLink 融合设备
  中心兼容桥和 Material 3 运行看板。连接管理按蓝牙地址支持多个会话，
  仅串行化物理建连动作；模块不注入系统设置页。
- `protocol-test`：实机协议实验室。列出已配对 vivo 耳机，探测 RFCOMM
  UUID/通道，验证握手、降噪查询与切换、电量查询，并显示完整原始日志。

系统模块的详细分层、进程边界、状态协议、安全约束与扩展流程见
[`docs/system-module-architecture.md`](docs/system-module-architecture.md)。
运行看板的产品定位、生命周期语义与性能边界见
[`docs/dashboard-ui-architecture.md`](docs/dashboard-ui-architecture.md)。
`vivo-tws-air3-pro` Adapter 提供完整电量与降噪协议支持。未知 vivo 型号会
落到 vivo 家族 Adapter，其他可确认是耳机的 A2DP 设备会落到标准 Adapter；
两种回退只向 MiLink 提供耳机身份和音频流转能力，不建立私有通道，也不
伪造电量或降噪。音箱、车机、无法判断为耳机的设备以及名称可确认由
HyperOS 原生支持的小米/REDMI 耳机不会被接管。

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
