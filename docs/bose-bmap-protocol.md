# Bose BMAP 型号与电量协议

## 已验证设备

- 市场名称：Bose QuietComfort Headphones
- 内部代号：`prince`
- 产品 ID：`0x4075`
- 实测固件：`1.0.6-80+f5f219b`
- 当前设备蓝牙地址 OUI：`BC:87:FA`（Bose 注册）

设备名称可以被用户改成“电音耳罩”，所以名称只能用于初筛，不能作为最终
型号判据。

## 传输与分帧

BMAP 直接承载在 RFCOMM 字节流上：

```text
[functionBlock, function, flags, payloadLength, payload...]
```

`flags` 低四位为操作符：`GET=1`、`SET_GET=2`、`STATUS=3`、
`ERROR=4`、`START=5`、`RESULT=6`。

当前型号的端点顺序为：

1. RFCOMM channel 8（实机确认）
2. 标准 SPP UUID
3. `00000000-deca-fade-deca-deafdecacaff`
4. RFCOMM channel 2（兼容回退）

`deca-fade` UUID 也被 iAP2 等协议使用，不能单独作为 Bose 判型依据。

## 产品判型 `[0.3]`

```text
TX  00 03 01 00
RX  00 03 03 03 40 75 02
```

响应 payload：

| 偏移 | 长度 | 字段 |
|---:|---:|---|
| 0 | 2 | 大端产品 ID；当前设备为 `0x4075` |
| 2 | 1 | 产品变体；当前设备为 `2` |

运行时先通过已连接设备的耳机类型、名称或 Bose OUI 做无扫描初筛，始终先
进入 Bose 家族/头戴 Adapter，再由该只读请求确认协议内产品 ID。收到
`0x4075` 后，才把会话细化为 `BoseQuietComfortHeadphonesAdapter`。BMAP
响应是型号事实，用户可修改的蓝牙名称不会提前放开具体型号能力。

## 电量 `[2.2]`

只读请求：

```text
TX  02 02 01 00
```

当前头戴式设备 80% 的实机响应：

```text
RX  02 02 03 04 50 FF FF 00
```

payload 由一个或多个四字节组件组组成：

```text
[percent, remainingMinutesHi, remainingMinutesLo, componentId]
```

组件 ID：

| ID | 组件 |
|---:|---|
| 0 | 整机 |
| 1 | 左耳 |
| 2 | 右耳 |
| 3 | 充电盒 |
| 4 | 系统/整机 |

`0xFFFF` 表示剩余分钟未知。百分比只有 `0..100` 有效。

对于当前单电池头戴设备，HyperEars 保留整机电量语义，并由 MiLink 桥接层把
适配器的 `HEADPHONES` 语义映射为 MiLink 17.2.4 的原生通用头戴式类型 `7`。
MiLink 自己负责头戴式图标、单电池布局和 ANC 生命周期，不显示左右耳/充电盒，
HyperEars 不改写其卡片形态。具体型号呈现适配器只移除协议确认不支持的操作入口。
17.2.0 尚未实现该类型，需要升级 MiLink。
支持多组件上报的 Bose 入耳式设备则优先使用真实左右耳和充电盒分组。

## 模式 `[31.3]` / `[31.6]`

只有 `0x4075` 确认成功后，当前型号才在同一 BMAP 字节流上启用 AudioModes：

```text
读取当前模式  1F 03 01 00
切换 Quiet    1F 03 05 02 00 00
切换 Aware    1F 03 05 02 01 00
读取模式配置  1F 06 05 00
```

Quiet 映射为 MiLink“降噪”，Aware 映射为“通透”。后续自定义模式槽仍由
ModeConfig 动态发现；其中非内置槽且 `wind=true` 的通勤模式映射为
MiLink“抗风噪”。切换三者都只发送 CurrentMode START，随后再次读取
CurrentMode；HyperEars 不修改模式名称、CNC、空间音频或风噪参数。

该固件没有已验证的免鉴权“关闭降噪”指令，因此不会伪造第三种状态。HyperEars
使用 MiLink 17.2.4 原生头戴式 ANC 卡片，具体可用模式仍由
`BoseQuietComfortHeadphonesAdapter.supportedNoiseModes` 和协议能力共同约束。
其他未呈现的自定义模式槽按设备返回的 CNC 配置折叠为“降噪”或“通透”状态。

卡片生命周期入口按四个稳定参数类型定位，不依赖被混淆为 `m`（17.2.0）或
`p`（17.2.4）的函数名。

ANC 能力从 MiLink 稳定的
`QueryLocal/QueryServer.getSupportAncMode(targetAddress, deviceId)` 边界发布：
原始能力位 `3` 表示不含通透，`7` 表示完整原生三态集合；MiLink 自己再把它们
标准化为 UI 值 `1/2` 并执行异步加载。这样无需依赖在 17.2.0 中被混淆为
`b0.L()` 的 Controller。Bose 仍发布完整的原生三项能力集合，具体 Adapter
在卡片绑定时以 MiLink 自己的 ANC item 把设备不支持的“关闭”入口替换为
“抗风噪”；它只切换动态发现的通勤槽，不提供 ModeConfig 参数编辑。

## 实现边界

- `BoseBmapWireCodec`：BMAP 分帧、型号、电量与 AudioModes 编解码。
- `BoseEarbudAdapter`：Bose 家族初筛、端点和家族通用电量能力。
- `BoseQuietComfortHeadphonesAdapter`：`prince/0x4075` 具体型号能力、
  BMAP 模式画像与 MiLink 呈现 ID。
- `BoseBmapEarbudProtocol`：每设备会话中的型号确认、电量、模式与读回事件。

## 证据

- 本地 Bose Music China 12.4.2 APK 反编译：
  `StatusBatteryLevelGetPacket`、`StatusBatteryLevelResponse`、
  `ProductInfoProductIdVariants`。
- 当前项目既有 QuietComfort Headphones 实机抓包。
- `aaronsb/bosectl` 的 `qc_prince` 配置与捕获测试。
