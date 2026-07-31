# 安装、升级与卸载

## 1. 前置条件

- Xiaomi HyperOS，Android 15（API 35）或更高版本；
- root 环境可正常使用；
- LSPosed API 101 或更高版本；
- 能够在系统异常时进入安全模式或通过 ADB 禁用模块；
- 目标耳机已经通过系统蓝牙完成配对。

HyperEars 的静态作用域只有：

```text
com.android.bluetooth
com.milink.service
```

请勿为了“提高兼容性”额外勾选系统设置、System UI 或所有应用。扩大作用域不会增加
功能，只会增加不必要的注入面。

## 2. 下载与校验

只从项目 [GitHub Releases](https://github.com/silverpoetry/HyperEars/releases) 下载：

```text
HyperEars-vX.Y.Z.apk
HyperEars-vX.Y.Z.apk.sha256
```

PowerShell 校验：

```powershell
Get-FileHash .\HyperEars-vX.Y.Z.apk -Algorithm SHA256
Get-Content .\HyperEars-vX.Y.Z.apk.sha256
```

两者的十六进制摘要必须一致。不要安装只提供 APK、不提供来源和校验和的重打包版本。

## 3. 首次安装

1. 安装 APK。
2. 打开 LSPosed，启用 HyperEars。
3. 确认静态作用域为 `com.android.bluetooth` 和 `com.milink.service`。
4. 重启整台设备。
5. 连接耳机，打开 HyperEars 运行看板。
6. 确认设备会话至少完成“识别”和“发布”；需要私有能力的型号还应完成“通道”和
   “协议”。

模块不负责首次蓝牙配对。耳机必须先能在系统蓝牙页面正常连接和播放声音。

## 4. 从开发测试包迁移

首个公开 Release 使用独立发布证书。早期由本地 debug 证书签名的测试包无法直接覆盖
安装，Android 通常会提示签名不一致。迁移顺序：

1. 在 LSPosed 中禁用旧版 HyperEars；
2. 重启设备；
3. 卸载旧 APK；
4. 安装 GitHub Release APK；
5. 在 LSPosed 中重新启用两个静态作用域；
6. 再次重启设备。

这不会解除系统保存的耳机蓝牙配对。

## 5. 正常升级

同一公开签名链下的后续版本可以直接覆盖安装。升级后建议重启设备，因为蓝牙进程和
MiLink 进程可能仍持有旧模块代码。若版本说明明确要求重新配对，再单独执行该步骤；
普通升级不要先清除蓝牙系统数据。

## 6. 卸载

1. 在 LSPosed 中禁用 HyperEars；
2. 重启设备，确认系统蓝牙与 MiLink 恢复原生行为；
3. 卸载 HyperEars APK。

不要清除 `com.android.bluetooth` 数据，除非你明确愿意丢失系统蓝牙配对记录。

## 7. 安全恢复

如果启用模块后系统界面或蓝牙进程持续崩溃：

1. 使用 LSPosed 安全模式禁用模块；或
2. 通过 ADB 卸载 `dev.hyperears`；或
3. 在恢复环境中按所用 root/LSPosed 方案禁用对应模块。

恢复后请按 [问题排查](troubleshooting.md) 采集版本与崩溃信息，再提交 Issue。
