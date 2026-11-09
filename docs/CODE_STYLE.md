# 代码规范

> 这是一份可讨论的约定，不是教条。不同意某条就开 Issue 提 PR 改这里，改完再改代码。

## 0. 三条原则

有冲突时按顺序取：

1. **坐标系是类型的一部分。** 经纬度必须携带 `Srid`，让编译器挡住混用，而不是靠 review 抓。国内地图 App 的绝大多数「标记偏了 500 米」都是这么来的。
2. **先能同步，再谈功能。** 所有表从第一天就带客户端 UUID 和同步标记，不从自增 ID 起步。后面接服务器不用迁移数据。
3. **机器能判断的，不写进文档。** 缩进、行长、import 顺序交给 ktlint，圈复杂度交给 detekt。文档只写工具判断不了的东西：为什么这样设计、什么情况该走哪条路。

## 1. 机器强制项

提交前必须通过，CI 跑同样的命令，过不了无法合并：

```bash
./gradlew ktlintCheck detekt testDebugUnitTest
```

对应 `app/build.gradle.kts`：

```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}

ktlint {
    android.set(true)
    filter { exclude("**/generated/**") }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}
```

统一开启的规则：`maxLineLength = 120`、禁止通配符 import、尾随逗号保留、`when` 分支必须穷尽。

**格式问题的修法只有一条**：`./gradlew ktlintFormat`，不要手工调空格。

## 2. 命名

| 对象 | 规则 | 示例 |
|---|---|---|
| 包 | 全小写，不用下划线 | `feature.map` |
| 类 / 接口 | PascalCase | `SpotRepository` |
| 函数 / 属性 | camelCase | `observeSpotsByTag` |
| Composable | PascalCase | `SpotDetailScreen` |
| 常量 | UPPER_SNAKE_CASE | `MAX_PHOTO_SIZE_BYTES` |
| 顶层不可变值 | camelCase | `defaultZoomLevel` |
| 数据库表 | snake_case 复数 | `spots`、`visited_areas` |
| 资源文件 | snake_case + 前缀 | `ic_marker_filled.xml` |
| 布尔值 | `is` / `has` / `should` 前缀 | `isSynced`、`hasLocationPermission` |

不用缩写，但领域内约定俗成的保留：`lat`、`lng`、`id`、`url`、`adcode`。`location` 不要写成 `loc`，`image` 不要写成 `img`。

资源前缀固定为：`ic_` 图标、`bg_` 背景、`screen_` 布局根、`tag_` 标签色。

## 3. 包结构与依赖方向

```
com.vmap.app
├── core/          通用能力，不含业务概念
│   ├── geo/       坐标类型与转换
│   ├── db/        Room 实例、TypeConverter
│   └── ui/        主题、通用组件
├── data/          仓储实现、DAO、数据源
├── domain/        仓储接口、用例、领域模型
├── feature/
│   ├── section/   两个板块的入口与切换
│   ├── map/       地图与点亮
│   ├── spot/      标记的增删改查
│   ├── gallery/   照片浏览
│   └── tag/       标签与筛选
└── MainActivity.kt
```

依赖方向只允许一条：

```
feature → domain ← data
feature → core
```

硬性规则：

- `feature` 之间**不许互相引用**。需要共享就下沉到 `domain` 或 `core`。
- `ui` 层不许出现 `@Entity`、`Dao`、`AMap`、`ContentResolver`。
- `domain` 是纯 Kotlin，不许 import `android.*`——这条能让领域逻辑在 JVM 单测里秒跑。
- 接口放 `domain`，实现放 `data`。绑定在 `di` 包里做。

## 4. Compose

**参数顺序**固定为：必填 → 可选 → `modifier` → 尾随 lambda。

```kotlin
@Composable
fun SpotCard(
    spot: Spot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
)
```

- `modifier` 默认必须是 `Modifier`，且只能应用在最外层容器一次。
- 状态向下传，事件向上抛。Composable 不收 Repository、不收 ViewModel 之外的可变对象。
- 取状态统一用 `collectAsStateWithLifecycle()`，不用 `collectAsState()`。
- `LazyColumn` 必须给 `key = { it.id }`，否则筛选时列表会错位。
- 不在 Composable 里做 IO、`SimpleDateFormat` 解析、`System.currentTimeMillis()` 之外的副作用——放进 `LaunchedEffect` 或 ViewModel。
- 每个 Screen 配一个 `@Preview`，预览用固定假数据，别依赖网络。

## 5. 数据层

**不做 Entity/Domain 双模型映射。** 这个规模的项目里它是纯粹的负担，`@Entity` 类同时当领域模型用，字段名对齐、不暴露 Room 专有注解到 UI 层即可。等真的出现存储结构与业务结构分叉时再拆。

```kotlin
@Entity(tableName = "spots")
data class Spot(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val lat: Double,
    val lng: Double,
    @Embedded(prefix = "srid_") val srid: Srid = Srid.GCJ02,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
)
```

- **主键一律 UUID**，不用 `autoGenerate = true`。
- 时间统一 `Long` 毫秒时间戳，不用 `String`，不用 `Date`。
- 删除一律软删除（`deleted = true`），物理删除只在「清理已同步的墓碑记录」时做。
- 多表写入必须包在 `@Transaction` 里。
- DAO 返回 `Flow<List<T>>` 或 `suspend fun`，不返回裸 `Cursor`。
- 查询条件写在 DAO 的 SQL 里，不要查全表再在内存 filter——照片和标记会到几千条。

## 6. 坐标（本项目最关键的一节）

```kotlin
enum class Srid { WGS84, GCJ02 }

@JvmInline
value class Lat(val value: Double) {
    init { require(value in -90.0..90.0) { "lat out of range: $value" } }
}

data class GeoPoint(val lat: Double, val lng: Double, val srid: Srid) {
    init {
        require(lat in -90.0..90.0) { "lat out of range: $lat" }
        require(lng in -180.0..180.0) { "lng out of range: $lng" }
    }
}
```

规则：

- **入库统一 GCJ-02。** 高德定位返回 GCJ-02，高德渲染吃 GCJ-02，只有照片 EXIF 和外部 GPS 是 WGS-84。
- **转换只允许在 `core/geo/GeoConvert` 里发生**，且函数签名强制标注输入坐标系：

```kotlin
object GeoConvert {
    fun wgs84ToGcj02(p: GeoPoint): GeoPoint {
        require(p.srid == Srid.WGS84) { "expected WGS84, got ${p.srid}" }
        // ...
    }
    fun gcj02ToWgs84(p: GeoPoint): GeoPoint {
        require(p.srid == Srid.GCJ02) { "expected GCJ02, got ${p.srid}" }
        // ...
    }
}
```

- **禁止裸 `Double` 坐标跨两层传递。** `fun addMarker(lat: Double, lng: Double)` 这种签名不许出现在业务层，只在调用高德 SDK 的那一层临时拆包。
- 给高德传参前必须断言坐标系，写 `check(point.srid == Srid.GCJ02)`，错了一行日志就能定位，而不是盯着偏移 500 米的地图猜。

## 7. 错误处理

- UI 状态用密封接口，穷尽 `when`：

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}
```

- **禁止空 `catch {}`**，至少记日志并转成用户能看懂的文案。
- **禁止用 `runCatching` 包 suspend 函数**——它会把 `CancellationException` 一起吞掉，导致协程取消失效、界面永远卡在 Loading。要么用 `try/catch` 只捕业务异常并重新抛出取消异常，要么用 `Result` 显式返回。
- 异常只用于「真的异常」，不用来做流程分支。

## 8. 日志与隐私

- Debug 用 Timber 并在 `Application` 里种树，Release 不种树。
- **日志里不许出现完整坐标**，打印时截断到 2 位小数（约 1km 精度），够定位问题，不泄露住址。
- 不打印文件绝对路径中的用户名段。
- 不上传任何未在隐私政策里声明的数据。开源项目尤其要守住这条。

## 9. 照片与文件

- 存放：`filesDir/photos/<yyyy>/<MM>/<uuid>.jpg`，目录按年月分，避免单目录上万文件。
- **数据库只存相对路径**（相对于 `filesDir`），绝对路径换机必失效。
- 入库同时存 `width` / `height`，列表按比例占位，避免图片加载时布局跳动。
- 列表一律用 Coil 加载缩略图，**不存 BLOB**。
- 保存前按最长边压到 2048px、质量 85，原图可选保留。

## 10. 注释

- 注释写**为什么**，不写**做了什么**。`// 自增 1` 是噪音。
- 绕开某个坑的地方必须留注释，写清楚坑长什么样，否则下一个人会「顺手优化」回去。
- `TODO` 必须带 Issue 号：`// TODO(#12): 支持按月份筛选`。没号的 TODO 不允许提交。
- 公开的 `domain` 接口写 KDoc，说明参数坐标系和可能抛出的异常。

## 11. 提交信息

Conventional Commits，subject 用英文祈使句、不超过 50 字符：

```
<type>(<scope>): <subject>

<body 可选，说明为什么这么改>

<footer 可选，关联 issue>
```

`type`：`feat` `fix` `refactor` `perf` `test` `docs` `build` `ci` `chore`
`scope`：`map` `spot` `gallery` `tag` `data` `domain` `core` `ui`

示例：

```
feat(map): 点亮已访问城市并渲染区县边界

DistrictSearch 有 QPS 限制且不能离线，改用打包的 GeoJSON 离线绘制，
避免首屏白屏。边界数据放 assets/districts/。
```

一个 commit 只做一件事。格式化产生的改动单独提交，不要混进功能提交。

## 12. 禁止清单

这些在 review 时会直接打回：

- ❌ `!!` —— 除非上一行有 `requireNotNull` 的等价判断并写明原因
- ❌ 空的 `catch {}`
- ❌ 裸 `Double` 坐标跨层传递
- ❌ 自增主键
- ❌ 把 API Key、签名密码写进代码或 `build.gradle.kts`
- ❌ 在 Composable 里构造 Repository / ViewModel
- ❌ 提交 `local.properties`、`*.jks`、`secrets.properties`
- ❌ 全局可变单例保存界面状态
- ❌ 用 `GlobalScope` 启动协程
- ❌ 直接删数据库记录而不用软删除

## 13. 命名与板块

项目名 **V-Map**，包名 `com.vmap.app`。

两个板块在代码里用枚举表示，绝不用裸字符串比较：

| 板块 | 显示名 | 角色 | 枚举值 |
|---|---|---|---|
| 天依 | 依你随行 | 洛天依 | `Section.TIANYI` |
| 初音 | 因音而遇 | 初音未来 | `Section.MIKU` |

规则：

- **界面文案全部放 `strings.xml`**，不硬编码中文。板块显示名将来可能调整，改文案不该动代码。
- 板块归属在数据库里是 `section` 字段，不要用标签代替——标签是用户自定义的内容组织，板块是固定的导航层级。
- 加第三个角色时只需要加枚举值和一个 `strings.xml` 条目，不允许改数据库结构。

真要改包名时，一次性全局替换这几处：

1. `settings.gradle.kts` 的 `rootProject.name`
2. `app/build.gradle.kts` 的 `namespace` 与 `applicationId`
3. 源码目录 `app/src/main/java/com/vmap/app/`
4. `README.md`、`SECURITY.md`、本文件
5. 高德开放平台后台的应用包名（改包名后 key 需要重新绑定，否则地图白屏）
