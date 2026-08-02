# Changelog

本项目遵循 [Semantic Versioning](https://semver.org/)。公开版本变化记录在此文件中。

## [Unreleased]

## [1.0.0] - 2026-08-02

### Added

- 增加 Bose BMAP 产品目录判型，以及 QC35/35 II、NC700、QC45、QuietComfort
  Earbuds/Ultra 系列的分协议画像盲适配。
- Bose 噪声控制按 AudioModes、ANR、CNC 三种线协议分派；静态型号画像缺失时通过
  GET-only 状态探测升级家族能力，保留 BMAP 电量且不在探测成功前发送控制写入。
- 增加 ROSESELSA EARFREE i5、ROSE BudsFeel MK2 和 NiceHCK YuanDao OriG in 的公开
  协议画像适配，支持组件电量与四态噪声控制。
- 将 ROSE 适配外推到 EARFREE/EARFEEL 与 BudsFeel 产品线；未知具体型号必须通过对应
  服务、特征和合法状态帧握手后才开放组件电量及四态控制，其他 ROSE 型号保持标准回退。
- 增加 Apple AAP L2CAP 传输和 AirPods 电量解析；以 AAP 服务 UUID 判定家族，并只为
  Pro/Max 型号开放已确认的三态噪声控制。
- 增加 Sony Headphones RFCOMM v1/v2、ACK 请求队列、单体/双耳/充电盒电量及环境声
  控制；以品牌回退、协议家族和具体型号 Profile 覆盖 WH、WF、WI 与 LinkBuds 系列。

### Architecture

- 运行看板增加统一的 `DeviceSessionUiProjector`：Adapter、传输、能力和电池拓扑只在
  投影边界转换为纯呈现数据，Compose 主界面不再查询 Adapter 或包含型号分支。
- 看板按 Adapter 的 `TransportReadiness` 区分“协议确认”和“连接即就绪”，并按设备
  形态统一投影整机或组件电量、真实传输类别和控制能力。
- 将 StarRing 的一次性抗风噪卡片扩展抽取为可复用的型号呈现组件；协议、Adapter 与
  MiLink UI 仍分别负责帧语义、能力声明和卡片绑定。
- 新增公开协议来源固定提交与许可证清单；无明确许可证的参考项目不复制代码或资源。
- 为需要设备 ACK 才能继续请求的协议增加一次性即时响应出口；默认实现为空，现有品牌
  不改变读写行为。

### Fixed

- 修复看板把所有私有协议都误判为必须握手，导致连接即就绪的 GATT/L2CAP/RFCOMM
  Profile 长期显示协议未完成的问题。
- 修复看板遗漏头戴式 `overall` 电量，以及头戴协议只提供单组件值时不显示整机电量的
  诊断问题。
- Bose RFCOMM 候选端点必须通过 BMAP 产品握手才视为就绪，修复可连接但无协议响应的
  UUID 抢占实机 channel 8、导致融合卡片只显示音量的问题。
- 补全 Apple AAP L2CAP 在多个 Android 版本中的公开隐藏构造签名，并以 `fd=-1` 创建
  新 socket，避免常见系统版本无法建立 AAP 通道。
- Apple AAP 电量通知按组件数量动态分帧，支持单组件、双组件和三组件状态，避免短包
  吞并后续通知。
- NiceHCK YuanDao OriG in 改为精确型号判定并要求合法协议帧握手，其他 YuanDao 型号
  保持标准蓝牙回退。

## [0.11.0] - 2026-08-01

### Added

- 增加 Edifier W860NB PRO 实机协议适配，支持整机电量、深度降噪、环境声、关闭和
  防风噪控制。
- 增加 W860NB PRO 专属 MiLink 四模式卡片，以及 Edifier BES 协议实验室只读探测。

### Architecture

- Edifier 采用“标准耳机 → 厂商家族 → 具体型号”适配器层级；未实机验证的家族
  型号只提供保守回退，不开放推测性控制能力。
- W860NB PRO 的模式切换遵循设备语音播报窗口限流，并按设备地址隔离控制状态。

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

[Unreleased]: https://github.com/silverpoetry/HyperEars/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/silverpoetry/HyperEars/releases/tag/v1.0.0
[0.11.0]: https://github.com/silverpoetry/HyperEars/releases/tag/v0.11.0
[0.10.4]: https://github.com/silverpoetry/HyperEars/releases/tag/v0.10.4
[0.10.3]: https://github.com/silverpoetry/HyperEars/releases/tag/v0.10.3
[0.10.2]: https://github.com/silverpoetry/HyperEars/releases/tag/v0.10.2
