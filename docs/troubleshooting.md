# 问题排查

## 卡片完全不出现

1. 确认耳机在系统蓝牙中已连接并可播放声音；
2. 确认 LSPosed 启用了 `com.android.bluetooth` 和 `com.milink.service`；
3. 确认安装版本满足 Android 15+ 与 LSPosed API 101；
4. 重启整台设备，而不是只重启 HyperEars 应用；
5. 打开运行看板，检查是否存在对应地址的设备会话。

如果没有会话，通常是设备未被保守判定为耳机、型号/服务未命中，或 Bluetooth 进程
未加载模块。存在会话但 MiLink 的“状态接收”长期未观测时，重点检查 MiLink 进程是否
加载模块，并核对卡片 Adapter ID 与安装版本。

## 卡片只有音量

只有音量可能是正常回退，也可能是私有能力尚未就绪：

- 标准蓝牙耳机本来就只发布系统电量和音量；
- 需要私有 GATT、RFCOMM 或 BR/EDR L2CAP 的型号必须先完成“私有通道”；只有 Adapter
  明确要求握手时才需要“协议确认”，其他型号显示“连接即就绪”；
- 快速反复展开卡片时，MiLink 可能先用基础快照创建界面，后续状态应触发原生刷新；
- 如果同一会话长期没有恢复控制按钮，采集看板 revision 和 MiLink 日志。

## 模式切换成功但卡片没有更新

确认耳机是否返回了状态报告。HyperEars 优先以设备回报为权威；只有明确声明即时确认
策略的型号才会在写入后立即更新。如果耳机已切换而 UI 未变，请记录：

- 点击前后的看板 revision；
- 耳机实际声音变化；
- 卡片当前选中项；
- 同时段 `HyperEars` 日志。

## “更多设置”闪退或打开错误页面

当前实现从 MiLink 的语义控制器边界读取真实蓝牙地址，并打开 HyperOS 的
`BluetoothDeviceDetailsFragment`。若 ROM 更改了 Settings Intent 或 Fragment 参数，
模块会尝试回退到蓝牙设置列表。提交问题时请附 Settings 崩溃堆栈和 ROM 完整版本。

## 流转超时

先区分耳机协议通道与 MiLink 设备共享通道：HyperEars 的 GATT、RFCOMM/L2CAP 通道只
负责耳机电量/模式，不承载跨设备音频流转。若两个方向表现不一致：

1. 确认两台设备都运行同一 HyperEars 版本；
2. 确认两端 MiLink 和系统蓝牙均正常；
3. 记录流转发起端、目标端、方向和时间；
4. 同时采集两端 MiLink 日志。

## 日志采集

常用过滤：

```powershell
adb logcat -c
adb logcat -v threadtime HyperEars*:V AndroidRuntime:E '*:S'
```

若 PowerShell 对 `*` 展开有影响，可以使用：

```powershell
adb logcat -v threadtime | Select-String 'HyperEars|AndroidRuntime|FATAL EXCEPTION'
```

提交前删除：

- 完整蓝牙 MAC 地址，只保留 OUI 或末两组；
- WLAN 地址、ADB 地址和设备配对码；
- 账号、手机号、通知正文和其他无关个人信息；
- 与问题无关的应用日志。

## Issue 最小信息

- HyperEars 版本与 APK 来源；
- 手机/平板型号、Android 和 HyperOS 完整版本；
- LSPosed 与 MiLink 版本；
- 耳机零售名称；
- 最短复现步骤、预期结果、实际结果；
- 已脱敏日志和截图。
