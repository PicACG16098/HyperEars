# 发布签名与产物验证

## 官方发布证书

从首个公开版本 `v0.10.2` 开始，GitHub Release APK 使用固定的 HyperEars Release 证书：

```text
Subject: CN=HyperEars Release, OU=Open Source, O=silverpoetry, C=CN
SHA-256: 3C:ED:7C:0E:E4:D3:0F:2B:12:5C:06:CE:51:C2:75:B7:96:39:B4:AB:99:5C:4F:0F:E3:B1:68:84:7C:35:0A:AD
```

安装后可以用 Android SDK `apksigner` 检查下载文件：

```powershell
apksigner verify --verbose --print-certs .\HyperEars-vX.Y.Z.apk
```

输出的 `Signer #1 certificate SHA-256 digest` 必须等于：

```text
3ced7c0ee4d30f2b125c06ce51c275b79639b4ab995c4f0fe3b168847c350aad
```

同时使用 Release 附带的 `.sha256` 文件验证 APK 内容完整性。证书摘要确认发布身份，文件
摘要确认下载内容；两者作用不同。

## 签名密钥管理

私钥不进入 Git 仓库。标签发布工作流从 GitHub Actions Secrets 恢复 keystore，完成
Release 构建后用 Android `apksigner` 验证，再发布 APK 和 SHA-256。没有签名环境变量
的普通本地/CI 构建只能得到未签名 APK，不能伪装成官方 Release。

维护者也可以手动运行 `Signed build` 工作流。该流程使用相同证书完成测试、Lint、Release
编译、签名验证和 SHA-256 生成，但只把带提交短哈希的 APK 上传到有保存期限的 Actions
Artifact；它不创建版本标签，也不发布 GitHub Release。

早期本地测试包使用开发证书，与上述公开证书不兼容；迁移方法见
[安装指南](installation.md#5-从开发测试包迁移)。
