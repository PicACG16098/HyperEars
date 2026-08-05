# Bose BMAP 家族适配

## 1. 设计边界

HyperEars 只在 Android 已确认设备属于标准耳机、且名称、Bose OUI 或 Bose 专属
`FEBE` 服务 UUID 命中时创建 Bose 家族会话。公共的 accessory-side UUID
不参与品牌判型。最终型号不依赖蓝牙名称，而由只读 BMAP
`ProductInfo.ProductIdVariants [0.3]` 响应确认。

产品 ID 未登记，或已登记但没有静态控制画像时，家族协议会依次读取 AudioModes、
CNC、ANR 状态。只有某个精确的 STATUS 响应通过对应 Codec 校验后，才把会话升级到
该线协议的家族能力 Adapter；无有效响应时仍只提供 BMAP 电量和 MiLink 流转。

## 2. 传输与初始化

BMAP 帧直接承载于 RFCOMM：

```text
[functionBlock, function, flags, payloadLength, payload...]
```

`flags` 低四位为操作符：`GET=1`、`SETGET=2`、`STATUS=3`、`START=5`。
端点按以下顺序尝试：

1. RFCOMM channel 8（本地 `prince` 及公开 QC35/35 II 实现）；
2. 标准 SPP UUID；
3. 公共 accessory-side RFCOMM UUID `00000000-deca-fade-deca-deafdecacaff`
   （仅作传输候选，不作 Bose 身份）；
4. RFCOMM channel 2（较新 BMAP 设备）。

RFCOMM `connect()` 成功不代表业务端点正确：部分 UUID 可以建立 Socket 却不返回
BMAP。每个候选端点必须在 2.5 秒内对 `[0.3]` 返回合法且接受的协议握手，否则关闭
该 Socket 并尝试下一端点。只有验证成功的候选才发布“通道已连接”，避免假连接使
卡片永久退化为音量。

每个会话先发送 `GET [0.1]`。QC35/35 II 需要该初始化请求才会响应后续 BMAP
命令；其他已知设备将其作为普通只读查询处理。随后读取产品 ID `[0.3]` 和电量
`[2.2]`，具体型号确认后才读取或写入对应噪声控制功能块。

## 3. 产品目录与能力分级

产品 ID 与代号来自公开的 Bose BMAP 产品目录；控制能力按证据单独分级。

| 产品 | 代号 / ID | 形态 | HyperEars 控制画像 | 证据 |
|---|---|---|---|---|
| QuietComfort 35 | `wolfcastle/0x400C` | 头戴 | ANR：高/风噪/关闭 | 公开实机实现 |
| QuietComfort 35 II | `baywolf/0x4020` | 头戴 | ANR：高/风噪/关闭 | 公开实机实现 |
| Noise Cancelling Headphones 700 | `goodyear/0x4024` | 头戴 | CNC：降噪/通透端点/关闭 | 公开实机实现 |
| QuietComfort 45 | `duran/0x4039` | 头戴 | AudioModes：安静/感知 | 公开实机实现 |
| QuietComfort Ultra Headphones | `lonestarr/0x4066` | 头戴 | AudioModes：安静/感知 | 同代协议盲配 |
| QuietComfort Headphones | `prince/0x4075` | 头戴 | AudioModes：安静/感知/已发现风噪预设 | HyperEars 实机验证 |
| QuietComfort Ultra Headphones (2nd Gen) | `wolverine/0x4082` | 头戴 | AudioModes：安静/感知 | 公开实机实现 |
| QuietComfort Earbuds | `lando/0x402F` | TWS | AudioModes：安静/感知 | 公开实机实现 |
| QuietComfort Earbuds II | `smalls/0x4064` | TWS | AudioModes：安静/感知 | 同代协议盲配 |
| QuietComfort Ultra Earbuds | `scotty/0x4072` | TWS | AudioModes：安静/感知 | 同代协议盲配 |
| QuietComfort Ultra Earbuds (2nd Gen) | `edith/0x4062` | TWS | AudioModes：安静/感知 | 公开配置盲配 |

以下产品没有预置写画像，只登记产品 ID、形态和通用 BMAP 电量；若设备实际响应第 4
节所列只读能力探测，则运行时开放该协议确认支持的模式：Hearphones、
Hearphones II、ProFlight、SoundSport、SoundSport Pulse、QuietControl 30、SoundSport
Free、Sport Earbuds、Sport Open Earbuds、Ultra Open Earbuds。

“公开实机实现”表示存在可检查的第三方开源实现，不表示 HyperEars 已持有对应设备；
“同代协议盲配”需要社区实机继续验证。

## 4. 三种噪声控制画像

未知画像只发送以下三个 GET：`[31.3]`、`[1.5]`、`[1.6]`。ERROR、无响应和格式不符
均不产生能力；首次合法 STATUS 决定当前会话画像，之后写入和回读都只走该画像。
探测不修改设备配置，也不增加轮询。

### 4.1 QC35 ANR `[1.6]`

```text
GET:    01 06 01 00
SETGET: 01 06 02 01 <level>
STATUS: 01 06 03 02 <level> <capabilities>
```

公开实现给出的值为 `0=关闭`、`1=高降噪`、`2=风噪`、`3=低降噪`。MiLink
没有“低降噪”语义，HyperEars 不把它误报成通透；当前只发布高降噪、风噪和关闭。
具体卡片呈现用 MiLink 自己的 ANC item 把不适用的“通透”槽替换为“抗风噪”。

### 4.2 NC700 CNC `[1.5]`

```text
GET:    01 05 01 00
SETGET: 01 05 02 02 <10-level> <enabled>
STATUS: 01 05 03 03 <steps> <10-level> <enabled>
```

HyperEars 只映射系统卡片能准确表达的三个端点：最大降噪、完全感知和关闭。NC700
从关闭重新启用时会先恢复最大降噪；仅当当前状态明确为关闭、目标为完全感知时，
协议会按公开实现发送第二个相同 SETGET，使最终级别落到感知端点。其他切换只写一次。

### 4.3 AudioModes `[31.3]` / `[31.6]`

```text
GET current:   1F 03 01 00
START switch:  1F 03 05 02 <modeIndex> <voicePrompt>
START configs: 1F 06 05 00
```

已知 Quiet/Aware 索引分别为 `0/1`。没有可靠关闭命令的产品只发布这两种状态；系统
原生三项卡片中的“关闭”项保留显示但设为禁用，不会发送命令。`prince/0x4075` 额外读取 ModeConfig，并只切换设备实际
返回且 `wind=true` 的预设，不修改用户模式参数。不同代 ModeConfig 的字段偏移由
具体 Adapter 内嵌的 `BoseWireConfig` 提供，Codec 不写死成一个型号布局。

## 5. 电量 `[2.2]`

```text
GET: 02 02 01 00
```

STATUS payload 按四字节组件重复：

```text
[percent, remainingMinutesHi, remainingMinutesLo, componentId]
```

`componentId` 为 `0=整机`、`1=左`、`2=右`、`3=盒`、`4=系统`。头戴型号发布整机
电量；支持组件分组的入耳式产品优先发布真实左右耳和充电盒，不把整机值伪装成盒电量。

## 6. 运行时结构

- `BoseEarbudAdapter`：无扫描家族初筛、Bose 身份证据、端点顺序、当前能力和 BMAP 会话所有权。
- `BoseBmapModelRegistry`：产品 ID 到具体 Adapter 线配置的内部目录。
- `BoseCapabilityConfigRegistry`：型号画像缺失时，由合法 STATUS 选择的只读能力配置；
  分别保留头戴/TWS 形态。
- 具体型号 Adapter：声明产品 ID、形态、能力、`BoseWireConfig` 和可选卡片呈现。
- `BoseBmapProtocolSession`：同一字节流上的增量解码、请求状态、电量和模式回读；
  它只产生产品 ID 与协议证据，不自行选择 Adapter。
- `BoseBmapWireCodec`：纯 BMAP 分帧，以及 Product、Battery、ANR、CNC、AudioModes
  的无状态编解码。

家族 Adapter 收到产品 ID 后，由自身映射并返回 `Replace`。设备会话原子替换为具体
Adapter，同时复用已建立的 RFCOMM 与原 `ProtocolSession`；产品未知时，只有合法只读
STATUS 才会形成带对应控制方言的新 Adapter。UI 与 MiLink 只接收替换后的完整快照。

未知产品 ID 不会伪装成具体型号；只有协议能力探测成功才得到对应写命令。电量 `[2.2]`
始终独立解析，因此噪声控制探测失败不会使电量退化。协议响应、设备会话和 UI 状态仍
按蓝牙地址隔离，不增加扫描、轮询或额外常驻服务。

## 7. 研究来源

- HyperEars 对 `prince/0x4075` 的实机抓包和 Bose Music China 12.4.2 APK 行为核对。
- [aaronsb/bosectl](https://github.com/aaronsb/bosectl)：BMAP 产品目录、QC35/35 II
  ANR、QC Ultra 二代 AudioModes 和不同 RFCOMM 通道的公开实机实现。
- [danielgjackson/noisecancel](https://github.com/danielgjackson/noisecancel)：NC700、
  QC35、QC45 与 QC Earbuds 的公开 Android 控制帧实现。

HyperEars 独立实现互操作所需的协议事实，不分发上述项目的程序、抓包或品牌资源。
