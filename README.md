# NewBox

TVBoxOS-Mobile 的现代化重写版本。基于 Jetpack Compose + Hilt + 三层架构，兼容现有 Spider 插件生态（.jar / .js）。

## 构建

```bash
./gradlew assembleDebug          # Debug APK → app/build/outputs/apk/debug/
./gradlew installDebug           # 安装到设备
./gradlew :app:compileDebugKotlin  # 仅编译检查
```

### 环境要求

- **JDK 21**（JetBrains，`gradle-daemon-jvm.properties` 已锁定）
- **Gradle 9.1.0**（wrapper 已包含）
- **AGP 9.0.1**，**Kotlin 2.3.20**，**Compose Compiler 2.3.20**
- `compileSdk 36`，`minSdk 24`，`targetSdk 36`
- NDK ABI: `arm64-v8a` only

## 模块结构

```
app              → 主应用 (com.github.tvbox.newbox)
├── data/        → Room + DataStore + OkHttp + SpiderResultParser
├── domain/      → UseCase + 领域模型 (VodItem, VodDetail, Episode)
├── feature/     → UI 层 (home, search, detailplayer, favorite, history, mine, settings)
├── player/      → ExoPlayer 封装 + SpiderProxyServer (NanoHTTPD)
├── server/      → 遥控器 HTTP API
├── ui/common/   → AppTheme, AppScaffold
└── com.github.tvbox.osc.base → App 入口 + QuickJSLoader

spider/
├── spider-api   → Spider 接口契约 + 结果模型 + FlexibleSerializer
└── spider-jar   → LegacySpiderAdapter (DexClassLoader 加载旧 .jar Spider)
```

依赖方向：`app → spider:spider-api, spider:spider-jar`，`spider-jar → spider-api`。

## 架构

三层 + 单向数据流：

```
UI (Compose) → ViewModel (StateFlow) → UseCase → Repository
                                                  ↓
                                    Spider / Room / DataStore / OkHttp
```

- **UI 层**：纯 Compose，无 Android View。`@HiltViewModel` + `StateFlow<UiState>`。
- **Domain 层**：UseCase 封装业务逻辑，`@Inject` 注入 Repository。
- **Data 层**：Repository 持有数据源（Spider / Room DAO / DataStore）。

DI 全部用 Hilt。Spider 工厂通过 `SpiderFactory` + `SpiderSourceConfig` 创建，由 `LegacySpiderAdapter` 适配旧 Spider 接口。

## Spider 插件系统

兼容现有 TVBox Spider 生态，三种加载方式：

| 类型 | 加载器 | 说明 |
|------|--------|------|
| `.jar` | `LegacySpiderAdapter` (DexClassLoader) | 兼容 `com.github.catvod.crawler.Spider` 接口 |
| `.js` | `JsLoader` (QuickJS) | 兼容 JS Spider |
| `.py` | 暂未支持 | — |

Spider 通过订阅源配置（JSON）自动发现，`ApiConfig` 管理生命周期。`SpiderProxyServer`（NanoHTTPD, port 8964）桥接 ExoPlayer HTTP 请求 → `Spider.proxyLocal()`，用于处理需要本地代理的源。

## 主要依赖

| 库 | 版本 | 用途 |
|----|------|------|
| Jetpack Compose | BOM 2026.06 | UI |
| Material 3 | — | 主题 |
| Navigation Compose | 2.9.1 | 页面导航 |
| Hilt | 2.60 | 依赖注入 |
| Room | 2.7.2 | 收藏/历史本地存储 |
| DataStore | 1.1.7 | 设置偏好 |
| Media3 (ExoPlayer) | 1.7.1 | 视频播放 |
| Coil 3 | 3.3.0 | 图片加载 |
| OkHttp | 4.12.0 | 网络 |
| kotlinx.serialization | 1.8.1 | JSON 解析 |
| NanoHTTPD | 2.3.1 | Spider 代理服务器 |

## 已实现功能

- **首页**：源切换、分类筛选、分页加载
- **搜索**：多源搜索、卡片/列表视图切换、搜索历史
- **详情+播放**：合并屏、自定义 Compose 播放控制层、全屏模式、选集双模式布局（Grid/List 自动检测）
- **收藏**：Room 持久化，详情页收藏/取消
- **播放历史**：Room 持久化，自动记录播放进度
- **我的**：订阅管理入口、收藏、历史、播放设置（占位）、关于
- **Spider 兼容**：FlexibleHeaderMapSerializer 容错字符串化 header、网盘源空 detailContent fallback

## 已知限制

- 无自动化测试
- `.py` Spider 暂未支持
- 热门搜索/推荐不支持（Spider API 无此接口）
- 网盘源（ZPan）部分 Spider 内部直接调用 `Activity.finish()`，可能导致闪退（待处理）

## 致谢

本项目基于以下开源项目：

- [XiaoRanLiu3119/TVBoxOS-Mobile](https://github.com/XiaoRanLiu3119/TVBoxOS-Mobile)
- [orient33/TVBoxOS-Mobile](https://github.com/orient33/TVBoxOS-Mobile)

原项目基于 [q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS)，感谢原作者及社区贡献者。
