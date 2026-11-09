## 这个 PR 做了什么

<!-- 一句话说清。如果是修 bug，描述复现步骤和原因。 -->

## 为什么这么做

<!-- 有别的方案吗？为什么不用？ -->

## 验证方式

<!-- 怎么确认它真的生效：截图、录屏、单测名、真机型号 -->

- [ ] 在真机上跑过（机型：　　　　，Android 版本：　　　　）
- [ ] 新增/修改的行为有对应测试
- [ ] UI 改动附了截图

## 自查清单

- [ ] `./gradlew ktlintCheck detekt testDebugUnitTest` 通过
- [ ] 没有提交 `local.properties`、`*.jks`、`secrets.properties`
- [ ] 没有把 Key 或签名密码写进代码
- [ ] 坐标带 `Srid`，没有裸 `Double` 跨层传递
- [ ] 没有新增空 `catch {}` 或 `!!`
- [ ] 改动涉及数据结构时，同步更新了 `docs/ARCHITECTURE.md`
- [ ] 提交信息遵循 Conventional Commits

## 关联 Issue

<!-- Closes #123 -->
