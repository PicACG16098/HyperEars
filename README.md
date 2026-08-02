# HyperEars

![HyperEars 标题图](docs/assets/coolapk-title.png)

[English](README_EN.md) · [安装指南](docs/installation.md) · [兼容性](docs/compatibility.md) · [问题排查](docs/troubleshooting.md)

[![CI](https://github.com/silverpoetry/HyperEars/actions/workflows/ci.yml/badge.svg)](https://github.com/silverpoetry/HyperEars/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/silverpoetry/HyperEars?display_name=tag)](https://github.com/silverpoetry/HyperEars/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0--only-blue.svg)](LICENSE)

HyperEars 是面向 Xiaomi HyperOS 的第三方蓝牙耳机系统集成模块。它让受支持的
vivo/iQOO、OPPO Enco、Bose、Edifier、StarRing、ROSESELSA、NiceHCK、Apple 和 Sony 耳机进入 MiLink 融合设备中心，并在不接管
Android 音频路由的前提下补充电量、降噪状态和设备流转所需的兼容信息。

> [!WARNING]
> HyperEars 依赖 root、LSPosed 和 HyperOS 私有接口。安装前请确认能够恢复系统；ROM
> 更新可能暂时破坏兼容性。本项目与 Xiaomi、vivo、iQOO、OPPO、Bose、Edifier、
> ROSESELSA、NiceHCK、Apple、Sony
> 及相关品牌无关。

## 能做什么

- 把符合条件的第三方耳机发布为 MiLink 耳机设备，复用系统原生流转与音量控制。
- 按适配器声明读取左右耳/充电盒或头戴式整机电量；标准耳机回退到 Android 整机电量。
- 把耳机私有降噪协议映射为系统卡片支持的降噪、关闭、通透和型号专属模式。
- 在“更多设置”中打开真实蓝牙设备详情，而不是借用载体型号的厂商页面。
- 为没有私有适配的标准 A2DP/HFP 耳机提供流转、音量和系统整机电量回退。
- 在应用内按设备会话展示识别、通道、协议和 MiLink 发布状态，便于诊断生命周期。

HyperEars **不会**替换 Android 的 A2DP/HFP 音频链路，不会把音频流经过模块，也不会
持续扫描蓝牙。私有 GATT、RFCOMM 或 BR/EDR L2CAP 控制通道只对需要协议遥测的适配器建立，并按
设备会话管理。

## 兼容性概览

| 适配范围 | 证据等级 | 电量能力 | 噪声控制 |
|---|---|---|---|
| vivo / iQOO TWS | 实机验证、公开实现、家族外推 | 私有组件电量 | 降噪、关闭、通透 |
| OPPO Enco | 参考协议 | 私有组件电量 | 降噪、关闭、通透 |
| StarRing / 籁特易耳 | Ultra 实机验证；其他标准回退 | Ultra 私有组件电量；其他系统整机电量 | Ultra 支持降噪、关闭、通透、抗风噪 |
| Bose | 一个型号实机验证；其余公开实现、参考协议或家族外推 | 私有整机或组件电量 | 按 BMAP 产品和控制方言开放明确的模式子集 |
| Edifier / 漫步者 | W860NB PRO 实机验证；其余家族外推 | 合法电量响应后提供私有整机电量 | 合法模式响应后提供降噪、关闭、通透、抗风噪 |
| ROSESELSA / 弱水时砂 | 两个型号公开实现；产品线家族外推；其余标准回退 | 协议确认后私有组件电量；回退设备使用系统整机电量 | 协议确认后支持降噪、关闭、通透、抗风噪 |
| NiceHCK / YuanDao | OriG in 公开实现；其他标准回退 | 协议确认后私有组件电量；回退设备使用系统整机电量 | OriG in 支持降噪、关闭、通透、抗风噪 |
| Apple AirPods | 公开实现、家族外推 | 私有组件电量 | Pro / Max 支持降噪、关闭、通透 |
| Sony | 公开实现、家族外推、标准回退 | 按设备形态提供私有整机、私有组件或系统整机电量 | 按具体型号开放表中明确列出的模式 |
| 其他标准 A2DP/HFP 耳机 | 标准回退 | 系统整机电量 | 无 |

所有条目均提供设备流转和系统音量。“公开实现”“参考协议”和“家族外推”均不等于
实机验证。家族名称只用于选择候选协议；需要确认的适配器还会校验服务、线端身份或
合法状态帧。完整型号、
证据等级、判型条件、私有传输和开放能力见
[兼容性文档](docs/compatibility.md)。

## 系统要求

- Xiaomi HyperOS，Android 15 或更高版本；
- 已安装并正常工作的 LSPosed，API 版本不低于 101；
- LSPosed 作用域：`com.android.bluetooth`、`com.milink.service`；
- 耳机已通过系统蓝牙完成配对。

目前公开测试基线来自 HyperOS 设备。AOSP、MIUI、非小米 ROM 和低于 Android 15 的
系统不在支持范围内。

## 安装

1. 从 [Releases](https://github.com/silverpoetry/HyperEars/releases) 下载 APK 和同名
   `.sha256` 文件，不要安装来源不明的重打包版本。
2. 校验 SHA-256：

   ```powershell
    Get-FileHash .\HyperEars-v1.1.0.apk -Algorithm SHA256
   ```

3. 安装 APK，在 LSPosed 中启用 HyperEars，并确认两个静态作用域均已选中。
4. 重启设备。仅强停 MiLink 不一定会让两个目标进程同时重新加载模块。
5. 连接耳机后打开 HyperEars，确认对应会话显示正确的 Adapter、形态、传输和能力，
   并观察 MiLink 的状态接收、身份查询、能力查询与通知阶段。

从早期开发测试包迁移到首个公开 Release 时，若 Android 提示签名不一致，需要先在
LSPosed 禁用旧模块、卸载旧 APK，再安装公开版并重新启用。详细升级和卸载步骤见
[安装指南](docs/installation.md)。

## 设计边界

```text
Android 蓝牙事件
        │
        ▼
EarbudConnectionManager ── 每个蓝牙地址一个逻辑会话
        │
        ▼
EarbudAdapter             ── 当前设备身份、能力、配置与运行状态
        │ owns
        ▼
ProtocolSession           ── 每会话独立、可随 Adapter 替换转移的协议状态
        │ uses
        ▼
WireCodec                 ── 纯字节编解码
        │
        ▼
DeviceStateRegistry       ── 带 token/revision 的进程内状态
        │
        ▼
MiLinkServiceHook         ── 最小身份、状态与控制映射
```

- `protocol`：纯帧编解码，不创建连接、不依赖界面。
- `integration`：设备识别、状态化 Adapter、能力模型和每会话 `ProtocolSession`。
- `system-module`：LSPosed 入口、蓝牙生命周期、MiLink 桥和运行看板。
- `protocol-test`：开发者使用的只读/显式控制协议实验工具，不随正式 Release 发布。

系统模块不注入 HyperOS 设置页，不轮询 UI，也不替换系统蓝牙音频服务。型号专属卡片
扩展只在 MiLink 卡片绑定时执行，并由具体 Adapter 声明。完整架构见
[系统模块架构](docs/system-module-architecture.md)。

## 隐私与安全

- 正式模块未声明 `INTERNET` 权限，不包含分析、遥测、广告或崩溃上报 SDK。
- 蓝牙地址只用于本机会话关联；正式日志默认对地址脱敏。
- 协议测试工具会显示目标地址和原始帧，分享日志前必须手动脱敏。
- 应用数据禁止系统备份；禁用并卸载模块即可移除其应用侧数据。

详见 [隐私说明](PRIVACY.md) 和 [安全策略](SECURITY.md)。

## 构建与验证

需要 JDK 17 和 Android SDK 36：

```powershell
.\gradlew.bat --no-daemon clean testDebugUnitTest `
  :protocol-test:assembleDebug `
  :system-module:lintRelease `
  :system-module:assembleRelease
```

没有提供 Release 签名环境变量时，Gradle 只生成未签名 Release APK。正式发布使用：

- `HYPEREARS_KEYSTORE_PATH`
- `HYPEREARS_KEYSTORE_PASSWORD`
- `HYPEREARS_KEY_ALIAS`
- `HYPEREARS_KEY_PASSWORD`

CI 会验证单元测试、Lint 和 Release 编译；带 `v*` 标签的发布工作流使用仓库 Secrets
签名、验证 APK，并同时生成 SHA-256 文件。

## 文档

- [安装、升级与卸载](docs/installation.md)
- [设备兼容性与证据等级](docs/compatibility.md)
- [常见问题与日志采集](docs/troubleshooting.md)
- [发布签名与产物验证](docs/release-signing.md)
- [系统模块架构](docs/system-module-architecture.md)
- [运行看板语义](docs/dashboard-ui-architecture.md)
- [vivo TWS Air3 Pro 协议](docs/vivo-tws-air3-pro-protocol.md)
- [vivo/iQOO 家族画像](docs/vivo-family-support.md)
- [OPPO Enco 协议](docs/oppo-enco-protocol.md)
- [Bose BMAP 协议](docs/bose-bmap-protocol.md)
- [Sony Headphones 协议](docs/sony-headphones-protocol.md)
- [StarRing Ultra 协议](docs/starring-ultra-protocol.md)
- [Edifier (BES) 协议](docs/edifier-bes-protocol.md)

## 贡献

新增型号应提供可复现证据，并遵循“具体型号 → 厂商家族 → 标准耳机”的回退顺序。
请不要在 Issue、提交或日志中公开完整个人设备 MAC、账号信息、密钥或厂商专有资源。
开发流程和证据要求见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可与致谢

HyperEars 以 [GNU GPL-3.0-only](LICENSE) 发布。协议研究参考了
[1812z/OppoPods](https://github.com/1812z/OppoPods)、
[DOHEX/HyperRose](https://github.com/DOHEX/HyperRose)、
[Art-Chen/HyperPods](https://github.com/Art-Chen/HyperPods)、
[ZaeXT/NiceHCK_Controller](https://github.com/ZaeXT/NiceHCK_Controller)、
[Plutoberth/SonyHeadphonesClient](https://github.com/Plutoberth/SonyHeadphonesClient)、
[Star-ZER0/Pods-Protocol-Reverse-Engineering](https://github.com/Star-ZER0/Pods-Protocol-Reverse-Engineering)
和 [moculll/ScrewVivoTWS](https://github.com/moculll/ScrewVivoTWS)，并包含本项目的实机
抓包与验证结果。具体来源及许可说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

商标和产品名称仅用于兼容性描述，归各自权利人所有。
