# 兼容性与证据等级

## 1. 状态定义

| 等级 | 含义 |
|---|---|
| 实机验证 | 在真实设备上完成连接、状态读取、控制写入和 UI 回读验证 |
| 公开实现画像 | 有可检查的公开协议实现，但 HyperEars 尚未覆盖足够多本地实机 |
| 参考协议盲适配 | 根据同家族公开项目建立兼容层，需社区设备继续验证 |
| 家族外推 | 名称只选择候选协议；还需要服务、线端身份或合法状态帧确认能力 |
| 通用回退 | 只使用 Android 已知的标准耳机身份、音量和整机电量 |

状态描述的是证据强度，不是品牌或产品质量评价。

## 2. HyperOS 平台

HyperEars 的当前目标是 Android 15+ 的 Xiaomi HyperOS 和 LSPosed API 101。MiLink、
蓝牙服务以及原生卡片类均属于 ROM 内部实现，不承诺跨 HyperOS 大版本二进制稳定。

开发过程中已在 Xiaomi 14 Pro、Xiaomi Pad 6S Pro 12.4 和 REDMI K Pad 等 HyperOS
设备上验证过流转路径，但发布兼容性仍以具体 ROM 构建为准。遇到问题时应同时提供：

- 设备型号和 HyperOS 完整版本；
- Android API 级别；
- MiLink 版本；
- LSPosed 版本；
- HyperEars 版本。

## 3. 耳机支持矩阵

“传输”是 HyperEars 读取厂商遥测和发送私有控制的附加通道，不是 A2DP/HFP 音频
链路。所有条目均继续由 Android 负责音频、通话、配对和系统音量。

### 3.1 vivo / iQOO

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| vivo TWS Air3 Pro | 精确规范化零售名称 | 实机验证 | GAIA RFCOMM `0837` | 左/右/盒 | ANC/OFF/通透 |
| vivo TWS 3e | 精确规范化零售名称 | 公开实现画像 | GAIA RFCOMM `0837`，channel 13 回退 | 左/右/盒 | ANC/OFF/通透 |
| vivo/iQOO TWS 家族目录 | 明确目录名称 | 家族外推 | GAIA RFCOMM `0837` | 合法响应中的左/右/盒 | ANC/OFF/通透；未逐型号验证 |

### 3.2 OPPO

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| OPPO Enco Air2 Pro | 精确产品名称 | 参考协议盲适配 | RFCOMM `079a` | 左/右/盒 | 反向 ANC/OFF 编码、通透 |
| OPPO Enco Free4 / X3 / Air5 | 精确产品名称进入独立 Profile | 参考协议盲适配 | RFCOMM `079a` | 左/右/盒 | 当前沿用家族 ANC/OFF/通透；不宣称自适应或空间音频 |
| 其他 OPPO/Enco | 保守家族名称 | 参考协议盲适配 | RFCOMM `079a` | 左/右/盒 | ANC/OFF/通透 |

### 3.3 StarRing / 籁特易耳

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| StarRing Ultra | 精确规范化零售名称 | 实机验证 | 官方 GATT `7777/8888` 优先，RFCOMM 候选回退 | 左/右；盒仅在实际上报时显示 | 降噪/正常/通透/抗风噪 |
| 其他 StarRing/籁特易耳 | 保守家族名称 | 通用回退 | 无 | Android 整机复制为左右 | 无私有模式 |

### 3.4 Bose

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| Bose QuietComfort Headphones | BMAP `prince/0x4075` | 实机验证 | BMAP RFCOMM 候选经产品 STATUS 确认 | 单整机 | 安静/感知/配置中发现的抗风噪预设 |
| Bose QC35 / QC35 II | BMAP `0x400C/0x4020` | 公开实现画像 | BMAP RFCOMM + 产品 STATUS | 单整机 | 高降噪/抗风噪/关闭；不把低降噪冒充通透 |
| Bose NC Headphones 700 | BMAP `goodyear/0x4024` | 公开实现画像 | BMAP RFCOMM + 产品 STATUS | 单整机 | CNC 最大降噪/完全感知/关闭 |
| Bose QC45 / QuietComfort Earbuds | BMAP `0x4039/0x402F` | 公开实现画像 | BMAP RFCOMM + 产品 STATUS | 整机或协议组件 | AudioModes 安静/感知 |
| Bose QC Ultra Headphones / Earbuds / Earbuds II | BMAP `0x4066/0x4072/0x4064` | 参考协议盲适配 | BMAP RFCOMM + 产品 STATUS | 整机或协议组件 | AudioModes 安静/感知；附加 ANC 预设归一为降噪 |
| Bose QC Ultra Headphones/Earbuds 二代 | BMAP `wolverine/0x4082`、`edith/0x4062` | 参考协议盲适配 | BMAP RFCOMM + 产品 STATUS | 整机或协议组件 | AudioModes 安静/感知；附加 ANC 预设归一为降噪 |
| Bose Hearphones、ProFlight、Hearphones II、SoundSport/Pulse/Free、QuietControl 30、Sport Earbuds/Open、Ultra Open | 对应 BMAP 产品 ID | 产品目录身份画像 | BMAP RFCOMM + 产品 STATUS | BMAP 响应可用时发布 | 静态 Profile 不发送未确认控制；合法 GET 探测可升级能力 |
| 未知 Bose BMAP | Bose 名称/OUI/服务 + 合法 BMAP STATUS | 家族外推 | BMAP RFCOMM 候选逐个验证 | BMAP 响应可用时发布 | 仅在 GET-only AudioModes/ANR/CNC 响应确认后开放 |

### 3.5 Edifier / 漫步者

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| Edifier W860NB PRO | 精确规范化名称 + 头戴形态 | 实机验证 | Edifier BES RFCOMM，channel 1 回退 | 单整机 | 深度降噪、关闭、环境声、抗风噪 |
| 其他 Edifier/W820/W830/W860 头戴 | 品牌/系列名称 + 头戴类别 | 家族外推 | Edifier BES RFCOMM | 合法响应中的整机电量 | 不开放未验证控制 |

### 3.6 ROSESELSA / 弱水时砂

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| ROSESELSA EARFREE i5 | 精确名称 | 公开实现画像 | GATT `011bf5da`，`7777/8888` | 左/右/盒 | ANC/OFF/通透/抗风噪 |
| 其他 EARFREE/EARFEEL | 产品线名称 + 相同服务/特征 + 合法状态帧 | 家族外推 | GATT `011bf5da` | 握手后左/右/盒 | 握手后 ANC/OFF/通透/抗风噪 |
| ROSE BudsFeel MK2 | 精确名称 | 公开实现画像 | RFCOMM `0cf12d31-…` | 左/右/盒 | ANC/OFF/通透/抗风噪 |
| 其他 BudsFeel | 产品线名称 + 相同服务 + 合法状态帧 | 家族外推 | RFCOMM `0cf12d31-…` | 握手后左/右/盒 | 握手后 ANC/OFF/通透/抗风噪 |
| 其他 ROSESELSA/ROSE | 保守品牌名称 | 通用回退 | 无 | Android 整机复制为左右 | 无私有模式 |

### 3.7 NiceHCK / YuanDao

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| NiceHCK YuanDao OriG in | 精确规范化名称 + `a100` 服务 + 合法电量/模式帧 | 公开实现画像 | RFCOMM `a100` | 握手后左/右/盒 | ANC/OFF/通透/抗风噪；深度档归一为 ANC |
| 其他 NiceHCK/YuanDao | 保守家族名称 | 通用回退 | 无 | Android 整机复制为左右 | 无私有模式 |

### 3.8 Apple

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| AirPods Pro | AAP SDP UUID + Pro 名称细分 | 公开实现画像 | BR/EDR L2CAP PSM `0x1001` | 每次通知动态解析 1–3 个左/右/盒组件 | ANC/OFF/通透；Adaptive 状态归一为 ANC |
| AirPods Max | AAP SDP UUID + Max 名称细分 | 公开实现画像 | BR/EDR L2CAP PSM `0x1001` | 动态组件电量；头戴卡片取可用单组件作为整机值 | ANC/OFF/通透；Adaptive 状态归一为 ANC |
| 其他带 AAP 服务的 AirPods | AAP SDP UUID | 家族外推 | BR/EDR L2CAP PSM `0x1001` | 动态组件电量 | 不开放无法从型号确认的控制 |

### 3.9 Sony

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| Sony WH-1000XM2/XM3/XM4 | 精确型号 + 合法 Sony v1/v2 初始化 | 公开实现画像 | Sony RFCOMM v1 优先 | 单整机 | ANC/OFF/环境声/抗风噪 |
| Sony WH-1000XM5/XM6、WH-CH720N | 精确型号 + 合法 Sony v1/v2 初始化 | 公开实现画像 | Sony RFCOMM v1 优先 | 单整机 | ANC/OFF/环境声 |
| Sony ULT WEAR (WH-ULT900N) | 精确型号 + 合法 Sony v1/v2 初始化 | 公开实现画像 | Sony RFCOMM v2 优先 | 单整机 | ANC/OFF/环境声 |
| Sony WF-1000XM3/XM4 | 精确型号 + 合法 Sony v1/v2 初始化 | 公开实现画像 | Sony RFCOMM v1 优先 | 左/右/盒 | ANC/OFF/环境声/抗风噪 |
| Sony WF-1000XM5、WF-SP800N、LinkBuds S | 精确型号 + 合法 Sony v1/v2 初始化 | 公开实现画像 | 型号指定 v1/v2 优先级 | 左/右/盒 | ANC/OFF/环境声 |
| Sony WF-C700N/C710N | 精确型号 + 合法 Sony v1/v2 初始化 | 公开实现画像 | Sony RFCOMM v1 优先 | 左/右/盒 | ANC/OFF/环境声 |
| Sony WF-C510 | 精确型号 + 合法 Sony v1/v2 初始化 | 公开实现画像 | Sony RFCOMM v1 优先 | 左/右/盒 | OFF/环境声；不伪造 ANC |
| Sony WF-C500 / LinkBuds | 精确型号 + 合法 Sony v1/v2 初始化 | 公开实现画像 | 型号指定 v1/v2 优先级 | 左/右；C500 无盒，LinkBuds 含盒 | 仅电量 |
| Sony WI-SP600N / WI-C100 | 精确型号 + 合法 Sony v1/v2 初始化 | 公开实现画像 | Sony RFCOMM v1 优先 | 单整机 | SP600N 支持 ANC/OFF/环境声/抗风噪；C100 仅电量 |
| 其他 Sony WH/WI/MDR 或 WF/LinkBuds | 产品线名称或 Sony 服务 + 合法初始化 | 家族外推 | Sony RFCOMM v1/v2 | 形态对应整机或组件 | 名称明确表示降噪时开放三态，否则仅协议电量 |

### 3.10 通用蓝牙耳机

| 适配器 | 匹配与确认依据 | 证据 | 私有传输 | 电量 | 模式 |
|---|---|---|---|---|---|
| 标准 A2DP/HFP 耳机 | Android 蓝牙设备类别或保守耳机名称，排除 HyperOS 原生型号 | 通用回退 | 无 | Android 整机复制为左右；盒不可用 | 无私有模式 |

ROSESELSA、NiceHCK 和 AirPods 条目来自公开实现画像，当前尚未完成 HyperEars 本地实机
验证。ROSE 的外推限定在 EARFREE/EARFEEL 与 BudsFeel 两条已知协议产品线：名称只选择
候选协议，捕获的服务/特征和合法状态帧共同确认协议就绪；其他 ROSE 型号仍走标准回退。
NiceHCK 仅精确的 YuanDao OriG in 名称进入其私有协议候选，并由合法电量或模式帧完成
协议确认；其他 NiceHCK/YuanDao 名称保持标准回退。
AirPods 的 AAP 服务 UUID 是家族判型依据，设备名称只用于把已确认的 AAP 设备细分为
Pro 或 Max 能力 Profile。电量通知不是固定三组件包：解析器按包内组件计数处理一至三
个组件，缺失字段保持不可用，不把上一帧或其他组件值硬填入。
Sony 条目来自公开实现画像，当前尚未完成 HyperEars 本地逐型号实机验证。名称选择具体
电池拓扑和控制 Profile，合法 RFCOMM 初始化响应负责确认协议；无法确认私有协议时不会
把控制通道标记为就绪。

## 4. vivo/iQOO 型号目录

家族目录包括：TWS Air3 Pro、TWS 3e、TWS Air2/Air200、TWS 5e、TWS 3 Pro、
TWS 3、TWS 2e、TWS 2、TWS 1、TWS A1 Pro、TWS A1、TWS Air Pro、TWS Air、
TWS Neo、TWS X1，以及已登记的 iQOO TWS 系列。

除 Air3 Pro 与 TWS 3e 外，目录命中只代表进入 vivo 家族 Profile，并不代表所有私有
命令已经逐型号验证。家族设备返回不兼容帧时，正确做法是新增具体型号 Adapter，而
不是在通用解析器中扩大猜测范围。

## 5. 原生 Xiaomi 耳机

名称和系统能力可确认由 HyperOS 原生支持的 Xiaomi/REDMI 耳机不会被 HyperEars
接管。音箱、车机和无法保守判定为耳机的设备同样不会进入标准耳机回退。

## 6. 提交新设备证据

请按照 [CONTRIBUTING.md](../CONTRIBUTING.md) 提交经过脱敏的设备名称、OUI、UUID、
只读响应和控制回读。完整个人 MAC 地址不属于协议证据，不应公开。
