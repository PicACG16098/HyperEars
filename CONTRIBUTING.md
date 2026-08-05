# Contributing to HyperEars

感谢参与兼容性研究。HyperEars 运行在系统蓝牙和 MiLink 进程中，因此正确性、证据和
最小注入范围优先于“尽量显示更多功能”。

## 开发环境

- JDK 17；
- Android SDK 36；
- Git；
- 用于实机验证的 Android 15+ HyperOS 设备和 LSPosed API 101+。

验证命令：

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest `
  :protocol-test:assembleDebug `
  :system-module:lintRelease `
  :system-module:assembleRelease
```

## 设计规则

1. 设备匹配按“具体型号 → 厂商家族 → 标准耳机”从窄到宽执行。
2. Adapter 声明身份、能力、端点和型号差异配置；ProtocolSession 只处理一个设备会话的帧状态。
3. WireCodec 不创建连接、不访问 Android Context、不修改 UI。
4. 未经证据验证的命令不得暴露为用户可操作能力。
5. 原生系统耳机必须留在官方路径。
6. 优先 Hook 稳定的语义边界；避免按混淆方法名、视图层级或定时轮询打补丁。
7. 新增后台工作必须说明生命周期、退避、并发和耗电影响。

## 新型号提交材料

- 零售名称及规范化别名；
- 设备形态（TWS/头戴）和 Android Profile；
- 厂商 UUID、RFCOMM channel 或 GATT service；
- 只读查询、响应和字段解释；
- 每个控制命令的写入帧、设备回读和失败行为；
- 至少一个解析器单元测试和一个 Adapter 选择测试；
- 对应 `docs/*-protocol.md` 更新。

请将完整 MAC 替换成合成地址，或只保留公开 OUI。例如
`BC:87:FA:00:00:01` 可以表达厂商 OUI，而不公开个人设备标识。

## 代码与提交

- Kotlin/Java 使用 4 空格，Markdown/YAML 使用 2 空格，统一 LF；
- 公共协议常量说明来源和语义，不只记录十六进制值；
- 不提交 APK、密钥、抓包原文件、反编译 APK 或厂商版权图片；
- 提交信息使用简短祈使句；
- PR 说明影响范围、证据、测试和实机结果。

## 许可

提交代码即表示你有权按 GNU GPL-3.0-only 提供该贡献。引用外部协议资料时必须在
`THIRD_PARTY_NOTICES.md` 或相关协议文档中标明来源和适用许可。
