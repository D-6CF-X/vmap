# V-Map

> 状态：开发中。为洛天依和初音未来的演出而做——把去过的城市点亮在地图上，把每一场的照片留在那座城里。

所有数据默认只存在手机上，**不联网也能完整使用**。地图是自绘的中国行政区图，没有第三方地图 SDK，也不申请定位权限。

## 两个板块

| 板块 | 角色 | 含义 |
|---|---|---|
| **依你随行** | 洛天依 | 依着你，随行。天依与你同行。 |
| **因音而遇** | 初音未来 | 因为你的声音，所以有了这次相遇。 |

两个板块各自有独立的地图、点亮进度和照片墙，互不干扰。同台演出可以同时归属两个板块。

## 功能

- **城市点亮** — 搜索城市（内置 70+ 主要演出城市，离线可用），填演出名、场馆、日期、备注；去过的城市在地图上填上应援色
- **地图** — 自绘中国地图，双指缩放拖动，省级边界 + 地级市边界，城市名按地块大小自适应显示；点击城市选中、点已点亮城市看详情
- **路线** — 按演出时间连线，3D 抛物线航线带流动光点，一眼看出走过的顺序
- **照片** — 为每场演出添加照片，自动压缩并按年月归档
- **时间线** — 按时间倒序的演出列表，带序号
- **编辑 / 删除** — 任意记录可改可删

### 规划中

- 标签与筛选（按标签、时间、行政区筛选）
- 云同步（表结构已为同步预留 UUID 主键 + `updatedAt`/`deleted`/`synced` 字段）

## 技术栈

| 部件 | 选型 |
|---|---|
| 语言 | Kotlin |
| 界面 | Jetpack Compose + Material 3 |
| 本地存储 | Room（SQLite） |
| 地图 | 自绘 Canvas + DataV 行政区边界数据（零依赖、离线可用） |
| 图片加载 | Coil |

最低支持 Android 8.0（API 26），目标 SDK 35。

## 开始开发

### 环境

- JDK 17
- Android Studio（最新稳定版）
- Android SDK Platform 35

### 构建

```bash
git clone https://github.com/D-6CF-X/vmap.git
cd vmap
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`，无需任何 Key 即可完整运行。

## 项目结构

```
app/src/main/java/com/vmap/app/
├── core/      坐标类型与转换、数据库、地图数据与自绘渲染、主题
├── data/      DAO、仓储实现、照片文件读写、城市搜索
├── domain/    仓储接口、领域模型（纯 Kotlin）
└── feature/   地图、标记、时间线
```

详细的依赖规则见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 文档

- [代码规范](docs/CODE_STYLE.md) — 命名、分层、坐标处理、禁止事项
- [架构说明](docs/ARCHITECTURE.md) — 模块边界、数据流、点亮实现、同步预留

## 参与贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。发现安全问题请走 [SECURITY.md](SECURITY.md) 里的私下渠道，不要公开提 Issue。

## License

[MIT](LICENSE)
