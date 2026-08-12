# Privacy Notice

## Production module

HyperEars 正式模块：

- 仅使用 Android `INTERNET` 权限访问 GitHub Releases 的公开 latest 跳转地址；
- 不包含遥测、分析、广告或远程崩溃上报 SDK；
- 不上传蓝牙状态、设备名称、地址、音频或协议帧；
- 不读取或转发耳机播放的音频内容；
- 禁止 Android 自动备份应用数据。

为完成本地功能，模块会在设备内处理已配对耳机的名称、蓝牙地址、设备类别、服务
UUID、连接状态、系统电量和受支持厂商协议帧。这些数据用于设备匹配、会话隔离、状态
显示和控制，不离开设备。

自动检查更新默认开启，只在 HyperEars 应用进入前台时执行，并限制为每天最多一次；“关于”
页也可手动触发检查。请求只包含常规 HTTPS 与固定的 HyperEars User-Agent，不包含蓝牙地址、
设备名称、模块设置、日志或协议数据。关闭“自动检查更新”后，除非用户手动点击“检查更新”，
应用不会发起网络请求。Bluetooth、MiLink 和厂商控制 App 注入进程从不访问网络。
与任何 HTTPS 请求相同，GitHub 及网络服务提供者可能接收到公网 IP、请求时间和 User-Agent；
HyperEars 不另行添加设备标识或用户标识。

运行时状态主要保存在目标进程内存中，并随蓝牙/MiLink 进程或设备重启清除。Android
日志可能包含适配器名称、生命周期和错误堆栈；正式代码对蓝牙地址使用脱敏表示，但在
分享日志前仍应检查设备名称和其他系统日志是否含个人信息。

“详细日志”默认关闭。开启后，模块日志还可能包含控制 App 包名、传输端点、协议命令字节、
系统版本和错误上下文；应用日志会记录设置变更及 Root 快捷操作结果。应用内导出会读取
LSPosed 守护进程日志中的 HyperEars 条目，并在导出文件头写入设备型号和 Android 版本。
这些日志只保存在本机，但分享前仍应按[问题排查](docs/troubleshooting.md#日志采集)完成脱敏。

厂商控制 App 是可选 LSPosed 作用域。启用后，HyperEars 只在该 App 进程的
`Application.attach(Context)` 阶段登记进程级 Binder 令牌，用于判断进程是否存活并仲裁
耳机私有通道；不会读取厂商 App 的账号、界面、私有文件、蓝牙对象或协议数据。应用名称、
包名和作用域边界见[控制 App 目录](docs/control-apps.md)。

## Protocol test application

`protocol-test` 是开发工具，不属于正式 Release。它会显式显示目标蓝牙地址、端点、
原始发送/接收帧和解析结果，并需要 Android 蓝牙连接/扫描权限。使用者必须在分享截图
或日志前自行脱敏。

## Removal

在 LSPosed 禁用 HyperEars、重启设备并卸载 APK，即可停止代码注入并删除应用侧数据。
无需、也不建议清除系统蓝牙数据；那会删除与 HyperEars 无关的配对记录。
