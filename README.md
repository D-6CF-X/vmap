# V-Map

> 状态：开发中，尚未发布可安装版本。

为洛天依和初音未来的演出而做：把去过的城市点亮在地图上，把每一场的照片留在那座城里。

所有数据默认只存在手机上，不联网也能完整使用。预留了同步接口，将来可以接自己的服务器。

## 两个板块

| 板块 | 角色 | 含义 |
|---|---|---|
| **依你随行** | 洛天依 | 依着你，随行。天依与你同行。 |
| **因音而遇** | 初音未来 | 因为你的声音，所以有了这次相遇。 |

两个板块各自有独立的地图、点亮进度和照片墙，互不干扰。一个标记可以同时归属两个板块——比如一场同台演出。

## 功能

- **标记** — 长按地图任意位置新建标记，记录演出名、场馆、日期、坐标
- **照片** — 为每个标记拍摄或选择多张照片，自动按年月归档压缩
- **标签** — 自定义标签与配色，一个标记可挂多个标签（如「内场」「应援」「首场」）
- **筛选** — 按板块、标签、时间范围、行政区多条件组合筛选
- **点亮** — 去过的城市按省/市/区在地图上高亮，统计已点亮数量与场次

## 技术栈

| 部件 | 选型 |
|---|---|
| 语言 | Kotlin |
| 界面 | Jetpack Compose + Material 3 |
| 本地存储 | Room（SQLite） |
| 地图 | 高德地图 Android SDK |
| 相机 | CameraX |
| 定位 | 高德定位 SDK |
| 图片加载 | Coil |

最低支持 Android 8.0（API 26），目标 SDK 跟随最新稳定版。

## 开始开发

### 环境

- JDK 17
- Android Studio（最新稳定版）
- Android SDK Platform 35 及以上

### 配置高德 Key

本项目的 Key **不进仓库**。先去[高德开放平台](https://lbs.amap.com/)创建应用，为 Android 平台绑定本项目的包名 `com.vmap.app` 和你的签名 SHA1。

然后在项目根目录的 `local.properties` 里加上（此文件已在 `.gitignore` 中）：

```properties
AMAP_API_KEY=你的Key
```

`app/build.gradle.kts` 读取方式（环境变量优先，方便 CI 注入；两者都没有时留空，能编译但地图不可用，不影响 fork 的 PR 过 CI）：

```kotlin
val amapKey: String = System.getenv("AMAP_API_KEY")
    ?: rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.readLines()
        ?.firstOrNull { it.startsWith("AMAP_API_KEY=") }
        ?.substringAfter("=")
    ?: ""

android.defaultConfig.manifestPlaceholders["AMAP_API_KEY"] = amapKey
```

> Key 与包名、签名的 SHA1 绑定。换电脑调试时记得把新机器的 SHA1 也加到高德后台，否则地图会显示空白。

### 构建

```bash
git clone https://github.com/<your-name>/vmap.git
cd vmap
./gradlew assembleDebug
```

### 提交前检查

```bash
./gradlew ktlintCheck detekt testDebugUnitTest
```

格式化统一用 `./gradlew ktlintFormat`，不要手工改空格。

## 项目结构

```
app/src/main/java/com/vmap/app/
├── core/      坐标类型与转换、数据库、主题
├── data/      DAO、仓储实现、文件读写
├── domain/    仓储接口、用例、领域模型（纯 Kotlin）
└── feature/   依你随行、因音而遇、标记、相册、标签
```

详细的依赖规则见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 文档

- [代码规范](docs/CODE_STYLE.md) — 命名、分层、坐标处理、禁止事项
- [架构说明](docs/ARCHITECTURE.md) — 模块边界、数据流、点亮实现、同步预留

## 参与贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。发现安全问题请走 [SECURITY.md](SECURITY.md) 里的私下渠道，不要公开提 Issue。

## License

[MIT](LICENSE)
