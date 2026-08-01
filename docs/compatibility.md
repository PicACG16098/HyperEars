# 兼容性与证据等级

## 1. 状态定义

| 等级 | 含义 |
|---|---|
| 实机验证 | 在真实设备上完成连接、状态读取、控制写入和 UI 回读验证 |
| 公开实现画像 | 有可检查的公开协议实现，但 HyperEars 尚未覆盖足够多本地实机 |
| 参考协议盲适配 | 根据同家族公开项目建立兼容层，需社区设备继续验证 |
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

| 适配器 | 匹配依据 | 证据 | 电量 | 模式 |
|---|---|---|---|---|
| vivo TWS Air3 Pro | 规范化零售名称 | 实机验证 | 左/右/盒 | ANC/OFF/通透 |
| vivo TWS 3e | 规范化零售名称 | 公开实现画像 | 左/右/盒 | ANC/OFF/通透 |
| vivo/iQOO TWS 家族 | 明确家族名称目录 | 实验性家族画像 | 协议响应可用时发布 | ANC/OFF/通透，响应优先 |
| StarRing Ultra | 规范化零售名称 | 实机验证 | 左/右；盒未知 | 降噪/正常/通透/风噪 |
| Bose QuietComfort Headphones | BMAP 产品 `prince/0x4075` | 实机验证 | 单整机 | 安静/感知/带风噪的已发现预设 |
| Bose Headphones | 设备类别、名称和 BMAP 身份 | 保守家族回退 | 单整机 | 仅具体产品确认后开放 |
| 其他 Bose BMAP | Bose OUI/名称及协议响应 | 保守家族回退 | 视响应形态 | 不猜测未验证命令 |
| OPPO Enco Air2 Pro | 规范化零售名称 | 参考协议盲适配 | 左/右/盒 | 反向 ANC/OFF 编码 |
| OPPO Enco Free4/X3/Air5 | 规范化零售名称 | 预留具体 Profile | 左/右/盒 | 当前只暴露通用三态 |
| 其他 OPPO/Enco | 家族名称 | 参考协议盲适配 | 左/右/盒 | ANC/OFF/通透 |
| Edifier W860NB PRO | 规范化名称 + 头戴类 | 实机验证 | 单整机 | 深度/舒适降噪、风噪、环境声、关闭 |
| 其他 Edifier 头戴 | 家族名称 + 头戴类 | 保守家族回退 | 单整机 | 按已发现模式开放 |
| 标准 A2DP/HFP 耳机 | Android 设备类别、Profile 和保守名称 | 通用回退 | 系统整机电量复制为左右 | 无私有模式 |

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
