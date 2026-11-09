# 参与贡献

感谢愿意花时间。这个项目接受 Issue 和 PR，范围包括功能、修 bug、文档、翻译、设计稿。

## 先聊一下再动手

- **改行为、加功能、动数据结构**：先开 Issue 说明动机和方案，确认方向后再写代码。省得你写完一堆被拒。
- **修明显的 bug、补文档、调样式、改错别字**：直接提 PR，不用先问。

## 开发流程

```bash
# 1. 从 main 拉分支，命名见下
git checkout -b feat/tag-filter

# 2. 改完自测
./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug

# 3. 提交（规范见 CODE_STYLE.md 第 11 节）
git commit -m "feat(tag): 支持按多个标签组合筛选"

# 4. 推送并开 PR
git push origin feat/tag-filter
```

分支命名：

| 前缀 | 用途 | 示例 |
|---|---|---|
| `feat/` | 新功能 | `feat/photo-export` |
| `fix/` | 修 bug | `fix/marker-offset` |
| `refactor/` | 不改行为的重构 | `refactor/geo-convert` |
| `docs/` | 只改文档 | `docs/setup-guide` |
| `chore/` | 构建、依赖、CI | `chore/bump-room` |

## PR 要求

- 指向 `main` 分支，标题同样遵循 Conventional Commits
- 描述里写清楚**为什么改**，附截图或录屏（UI 改动必须附）
- CI 全绿才进入 review
- 一个 PR 只做一件事。格式化、重命名的改动单独提
- 新增行为要有单测；涉及坐标转换的必须补测试用例

Review 会重点看这几件事：坐标有没有混用、异常有没有被吞、有没有把 Key 写进代码、UI 层有没有越界访问数据。

## 加一个新功能怎么落地

以「按月份筛选」为例，照着这个顺序走，每一步都能独立编译：

1. **数据层**：DAO 加查询方法，`@Query` 写 SQL，返回 `Flow<List<Spot>>`
2. **领域层**：`SpotRepository` 接口加方法，`data` 层实现
3. **状态层**：`ViewModel` 里加筛选项，组合多个 `Flow`
4. **界面层**：Compose 加控件，接 `uiState`
5. **测试**：DAO 用内存数据库测，筛选逻辑放 `domain` 用 JVM 单测
6. **文档**：功能列表和截图更新一下 README

顺序反了会很痛苦——先画界面再改数据结构，等于返工两遍。

## 提交信息

```
feat(map): 点亮已访问城市并渲染区县边界

DistrictSearch 有 QPS 限制且不能离线，改用打包的 GeoJSON 离线绘制，
避免首屏白屏。

Closes #12
```

类型：`feat` `fix` `refactor` `perf` `test` `docs` `build` `ci` `chore`
范围：`map` `spot` `gallery` `tag` `data` `domain` `core` `ui`

subject 用英文祈使句、50 字符以内。正文可以用中文，说清楚为什么改。

## 代码风格

完整规则见 [docs/CODE_STYLE.md](docs/CODE_STYLE.md)。最容易踩的三条先列在这：

1. 坐标必须带 `Srid`，禁止裸 `Double` 跨层传
2. 主键用 UUID，时间用 `Long` 毫秒，删除用软删除
3. 不用 `!!`，不写空 `catch`，不用 `runCatching` 包 suspend 函数

## 不要提交的东西

`local.properties`、`secrets.properties`、`*.jks`、`*.keystore`。里面有高德 Key 和签名密码，一旦推到公开仓库会被立即爬走。提交前跑一次 `git status` 确认。

## 行为准则

就一条：对人客气，对代码严格。技术分歧拿测试和文档说话，不拿资历说话。
