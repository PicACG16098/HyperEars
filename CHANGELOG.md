# Changelog

本项目遵循 [Semantic Versioning](https://semver.org/)。公开版本变化记录在此文件中。

## [0.10.4] - 2026-08-01

### Fixed

- StarRing Ultra 改用官方应用采用的 GATT 特征传输控制帧，并以耳机主动上报作为模式
  状态来源，避免重复控制和界面状态滞后。
- 修正 Bose QuietComfort Headphones 抗风噪预设的 MiLink 原生槽位映射，使其能够正常
  切回安静模式且三个模式保持互斥选中。

### Architecture

- 私有传输候选统一抽象为 GATT/RFCOMM，由具体型号 Adapter 声明优先级和特征。
- 扩展噪声模式到 MiLink 原生三态的投影由具体卡片 Adapter 定义；移除全局选中态拦截，
  保留耳机上报状态作为界面唯一事实来源。

## [0.10.3] - 2026-07-31

### Added

- 增加独立“关于”页面，集中展示版本、设备支持分级、许可证、源码、发布、问题反馈、
  第三方声明和隐私说明。
- 为项目文档增加 HyperEars 横版标题图。

### Architecture

- 应用界面引入 Compose Navigation，将运行看板与静态项目信息拆分为独立导航目的地。
- 关于页不订阅运行状态，也不会触发 Bluetooth 或 MiLink 操作。

## [0.10.2] - 2026-07-31

### Added

- 首个公开预览版。
- vivo TWS Air3 Pro 实机协议适配，以及 vivo/iQOO 家族和 vivo TWS 3e Profile。
- StarRing Ultra 左右耳电量、三态噪声控制和独立风噪模式。
- Bose BMAP 产品判型、单整机电量和 QuietComfort Headphones 模式预设。
- OPPO Enco 家族 RFCOMM、电量、通知协商和三态噪声控制盲适配。
- 标准 A2DP/HFP 耳机的 MiLink 流转、音量和系统整机电量回退。
- 多设备、按地址隔离的会话管理与运行看板。
- 点击 MiLink“更多设置”时打开真实 Android 蓝牙设备详情。

### Architecture

- 建立 `Standard → Vendor → Model` Adapter 层级。
- 私有 Protocol 改为每设备会话独立实例，传输、状态和卡片呈现解耦。
- MiLink 只复用 TWS/头戴式两类已知载体身份，不再伪造每型号设备 ID。
- 跨进程状态使用 session token 与单调 revision，拒绝旧会话延迟状态。

### Security and release

- 正式作用域限制为 Bluetooth 与 MiLink 两个进程。
- Release 构建改用独立环境变量签名，不再使用 debug 证书。
- 增加 CI、标签发布、APK 签名验证和 SHA-256 产物。

[0.10.4]: https://github.com/silverpoetry/HyperEars/releases/tag/v0.10.4
[0.10.3]: https://github.com/silverpoetry/HyperEars/releases/tag/v0.10.3
[0.10.2]: https://github.com/silverpoetry/HyperEars/releases/tag/v0.10.2
