# Security Policy

## Supported versions

安全修复只保证进入当前最新公开版本。旧预览版和本地测试构建不单独维护。

## Reporting a vulnerability

请使用 GitHub 仓库的 **Security → Report a vulnerability** 私下提交报告，不要先创建
公开 Issue。报告至少包括：

- 受影响版本和 ROM/LSPosed 环境；
- 可复现步骤和影响；
- 必要且已脱敏的日志或 PoC；
- 是否涉及跨应用广播、权限提升、任意代码执行或敏感信息泄露。

维护者会在合理时间内确认报告、评估影响并协调修复与披露。不要在未协调前公开可直接
利用的细节。

## Security boundaries

HyperEars 本身依赖 root/LSPosed，不能把已被恶意 root 模块完全控制的设备恢复为可信
环境。安全报告应聚焦 HyperEars 额外引入的问题，例如：

- 未经授权的外部 Intent 或广播触发控制；
- 跨设备会话 token/revision 混淆；
- 错误地址导致控制另一台耳机；
- 解析恶意或畸形私有协议帧导致宿主进程崩溃；
- 发布签名、构建流程或依赖供应链问题。

请勿提交真实账号凭据、完整个人蓝牙地址或无关用户数据。
