# vivo / iQOO TWS 家族适配

## 适配边界

vivo 系支持分成两层：

1. **家族候选协议**：名称命中 vivo/iQOO TWS 家族后，建立 vivo GAIA
   RFCOMM 通道，查询 `0x0207/0x8207` 左右耳/盒电量和
   `0x0130/0x0230` 降噪状态；合法响应分别确认并开放相应能力。
2. **具体型号差异**：具体 Adapter 只覆盖已有证据的 GAIA 版本、查询载荷、
   设置后缀或端点差异，不复制通用协议状态机。

家族默认画像来自公开手工逆向，并由 Air3 Pro 实测和 TWS 3e 项目交叉确认
公共 vendor、命令号及三态编号。型号名称只负责 Adapter 选择，`WireConfig` 只负责
GAIA 版本和命令载荷，Socket 和生命周期仍由系统模块统一管理。

## 当前能力矩阵

| 型号范围 | Adapter | 协议画像 | 能力 |
|---|---|---|---|
| vivo TWS Air3 Pro | `VivoTwsAir3ProAdapter` | v3，`mode 04 00` | 实机验证的左右耳/盒电量、降噪、关闭、通透 |
| vivo TWS 3e | `VivoTws3eAdapter` | v3，`mode 03` | 三态降噪、家族私有电量查询 |
| 其他 vivo TWS | `VivoEarbudAdapter` | 家族默认 v4，`mode 03 01` | 合法响应后开放私有电量和三态降噪；始终提供流转、音量 |
| iQOO TWS 家族 | `VivoEarbudAdapter` | 家族默认 v4，`mode 03 01` | 合法响应后开放私有电量和三态降噪；始终提供流转、音量 |

TWS 3e 优先使用 vivo UUID `00000837-d102-11e1-9b23-00025b00a5a5`
进行正常 SDP 连接；公开实现记录的 RFCOMM channel 13 仅作为连接回退。
其公开项目本身通过主动 JSON 状态读取左右耳电量，没有发送 `0x0207`；
正式模块依据家族一致性发送同一个只读查询，后续若出现兼容性报告再为该型号
覆盖电量策略。

## 已登记零售名称

- vivo TWS、TWS 1、TWS 2、TWS 2e、TWS 3、TWS 3 Pro、TWS 3e、TWS 5e
- vivo TWS A1、A1 Pro、Air、Air Pro、Air2、Air3 Pro、Neo、X1
- iQOO TWS 1、Air、Air Pro

`vivo TWS Air200` 作为公开 Air2 服务发现记录中的实际设备名，归一到
`vivo TWS Air2`。目录用于诊断与后续 Adapter 配置覆盖；未来未登记但保持
`vivo TWS` 或 `iQOO TWS` 命名格式的型号也会选择家族默认画像。

## 协议画像

| WireConfig | GAIA | 降噪查询载荷 | 降噪设置载荷 | 绑定状态 |
|---|---:|---|---|---|
| `FAMILY_DEFAULT_V4` | 4 | `00` | `mode 03 01` | vivo/iQOO 家族默认画像 |
| `AIR3_PRO_CAPTURED` | 3 | 空 | `mode 04 00` | Air3 Pro，已实机验证 |
| `TWS_3E_V3` | 3 | 空 | `mode 03` | TWS 3e，公开实现 |

`WireConfig` 是 Adapter 内嵌的不可变字节配置，不包含零售名称、MiLink 能力或蓝牙
连接代码。家族 Adapter 选择默认配置；增加可参数化的新协议差异只需登记配置，并由
具体型号 Adapter 覆盖选择，无需复制一套 `ProtocolSession`。

家族 Adapter 初始不声明噪声控制。收到合法握手或降噪状态帧后，它才把三态能力写入
当前 Adapter 快照并通知 MiLink；已验证具体型号可以静态声明相同能力。协议无响应时
不会留下可点击但无法执行的控制入口。

## Air2 限制

公开 BlueZ 记录确认 Air2 暴露 `0837/0838` 自定义 GATT 服务，但这不能证明
它一定同时暴露当前 Classic RFCOMM 入口。当前按 vivo 家族默认画像尝试
RFCOMM；若真机报告确认 Air2 仅支持 GATT，则为 Air2 增加独立传输配置，
不能把 BLE 特征值 UUID 当作 RFCOMM 服务直接连接。

## 证据来源

- Air3 Pro：本项目实机抓包，见
  [`vivo-tws-air3-pro-protocol.md`](vivo-tws-air3-pro-protocol.md)。
- TWS 3e：<https://github.com/moculll/ScrewVivoTWS>
- 家族默认 v4 画像：<https://github.com/Star-ZER0/Pods-Protocol-Reverse-Engineering>
- Air2 GATT 服务：<https://github.com/bluez/bluez/issues/687>
- 其余型号名称：本地留存的 vivo 官方 App 反编译常量，仅作为家族身份依据。
