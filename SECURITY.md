# 安全策略

## 报告漏洞

**不要公开提 Issue。**

优先用 GitHub 的 [Private vulnerability reporting](https://docs.github.com/zh/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)（仓库 Security 标签页 → Report a vulnerability），其次发邮件到维护者邮箱。

报告里请包含：受影响的版本或 commit、复现步骤、影响范围、你建议的修法（如果有）。

会尽快确认并回复，修好之后如果你愿意，会在 Release 里署名致谢。

## 重点关注的问题类型

这是本地优先的照片 + 位置类 App，以下几类风险优先处理：

| 类型 | 具体表现 |
|---|---|
| 位置隐私泄露 | 日志、崩溃报告、导出文件里出现完整坐标或可还原的精度 |
| Key 与凭据泄露 | 高德 Key、签名密码被写进代码、资源文件或提交历史 |
| 路径穿越 | 导入外部数据时用未经校验的路径拼接，能读到应用私有目录之外 |
| 照片越权 | 分享或导出功能把不属于该标记的照片一并带出 |
| 权限滥用 | 申请了实际功能不需要的权限，或后台定位未明确告知 |

## 已知的设计取舍

这些是**已知且有意为之**的，不算漏洞，不必报告：

- 数据默认明文存储在应用私有目录。未加密是出于性能与可移植性考虑；需要更强保护的场景会做后续的数据库加密选项。
- 标记、照片、标签不上传任何服务器。当前版本没有网络请求，除了地图 SDK 自身的瓦片加载。
- 高德 SDK 会加载地图瓦片，请求中会包含设备与定位信息，这属于使用地图服务的必要行为，已在隐私政策中说明。

## 维护者自查清单

发布前逐项确认：

- [ ] `git log -S "AMAP_API_KEY" --all` 无结果
- [ ] 仓库里不存在 `local.properties`、`*.jks`、`secrets.properties`
- [ ] Release 构建关闭了调试日志
- [ ] 日志与崩溃上报中坐标已截断到 2 位小数
- [ ] 新增权限都有对应的功能理由，并在隐私政策里更新
- [ ] 第三方 SDK 清单与版本已核对

## 支持的版本

项目尚未发布稳定版，仅对 `main` 分支的最新提交提供修复。发布 1.0 之后会明确列出维护中的版本范围。
