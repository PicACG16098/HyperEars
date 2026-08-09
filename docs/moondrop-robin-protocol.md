# MOONDROP Robin 协议适配

HyperEars 实现 Robin / 水月雨知更鸟接入 MiLink 所需的最小协议子集：严格握手、左右耳
电量读取和降噪、关闭、通透三态查询与控制。公开资料记录的蓝牙名称为
`Robin's Earphones`。

## 1. 判型与传输

Adapter 只在下列名称规则之一成立时选择 Robin：

- 规范化完整名称为 `robinsearphones`；
- 名称同时包含 `moondrop` 与 `robin`；
- 名称同时包含“水月雨”与“知更鸟”。

传输使用 Bluetooth SIG 标准 SPP UUID
`00001101-0000-1000-8000-00805f9b34fb`。该 UUID 被大量蓝牙设备共同使用，只负责建立
RFCOMM 端点，不属于水月雨身份依据，也不会让其他 SPP 耳机进入 Robin Adapter。

## 2. 帧格式

业务帧由 8 字节头和变长参数组成：

```text
FF TT 00 LL HH CC SS OO [parameters...]
```

- `TT`：消息类型；
- `LL HH`：参数长度，小端序；
- `CC SS OO`：三字节操作标识；
- `parameters`：恰好为长度字段指定的参数。

解码器按长度处理 RFCOMM 分片和粘包，拒绝非法类型、超长帧和字段不完整的响应。

## 3. 握手

```text
发送：FF 01 00 00 00 0A 03 00
响应：FF 04 00 04 00 0A 83 00 00 04 03 01
```

名称只选择候选；只有完整握手响应合法时，私有协议才进入确认状态。精确型号握手失败时
Adapter 不改判为其他品牌，设备会话按统一恢复策略依次退避重试，达到边界后暂时休眠；
显式刷新或后续系统连接生命周期可以唤醒新的有界连接周期。

## 4. 电量

```text
查询：FF 04 00 00 00 1D 1A 01
响应：FF 04 00 04 00 1D 1B 01 01 LL 02 RR
```

`LL`、`RR` 分别为 `0..100` 的左耳和右耳电量。当前没有充电盒电量或充电状态证据，
因此实现不会构造这些状态。握手前保留 Android 系统整机电量；首次合法电量响应后，
该会话的电量来源切换为私有左右耳遥测。

## 5. 噪声模式

查询命令：

```text
FF 04 00 00 00 1D 10 03
```

查询响应中的首个参数表示当前状态：

| 查询值 | 状态 |
|---|---|
| `00` | 关闭 |
| `01` | 降噪 |
| `02` | 通透 |

设置命令使用不同的操作标识和值：

| 状态 | 设置帧 |
|---|---|
| 关闭 | `FF 04 00 01 00 1D 10 04 01` |
| 降噪 | `FF 04 00 01 00 1D 10 04 02` |
| 通透 | `FF 04 00 01 00 1D 10 04 04` |

设备设置后不主动回报模式。Adapter 使用 `DEVICE_REPORT` 确认策略：写入完成后等待统一的
短切换间隔，主动发送模式查询，只在合法查询响应到达时更新
`NoiseModeFeatureState`。因此卡片状态不会依赖乐观 UI 或固定延时推断。

## 6. 代码边界

- `MoondropRobinWireCodec`：纯字节帧、流式解码和字段校验；
- `MoondropRobinProtocolSession`：握手进度、遥测查询、控制编码和回读；
- `MoondropRobinAdapter`：名称判型、传输候选、能力确认和恢复决策；
- MiLink：只读取 Adapter 发布的标准电量与噪声状态，不增加自定义卡片或 View Hook。

## 7. 来源与证据

- Star-ZER0，`MOONDROP-Protocol.txt`，固定提交
  [`2d97d85`](https://github.com/Star-ZER0/Pods-Protocol-Reverse-Engineering/blob/2d97d85b2cde9ee1446e9e7f67c222ac9b9f2bb9/handmade/MOONDROP-Protocol.txt)，
  CC BY-SA 4.0；
- 社区 Issue 提供的 Robin 蓝牙名称与“不主动回报模式”行为说明。

HyperEars 根据公开的可互操作协议事实独立实现，不分发厂商 App、固件、图片或上游程序。
