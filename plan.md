# newBox 重写计划

## TL;DR

> **Quick Summary**: 将 TVBoxOS-Mobile (MBox) 重写为现代 Android 应用，采用 Google 推荐三层架构 (UI/Domain/Data)，全 Kotlin + Coroutines + Flow，Compose UI，兼容现有 Spider 插件生态。
>
> **Deliverables**:
> - 完整的新项目脚手架（3 模块: app + spider:spider-api + spider:spider-jar，app 内按包名分 ui/data/domain/common/feature）
> - Spider 插件系统（api + jar loader + LegacySpiderAdapter 桥接）
> - 视频列表浏览 + 搜索 + 视频详情 + 播放 全链路
>
> **Estimated Effort**: Large (19-26 天)
> **Parallel Execution**: YES - 5 waves
> **Critical Path**: 脚手架 → spider-api → 配置加载 → 首页 → 搜索 → 详情 → 播放

---

## Context

### Original Request
在 newBox 目录新建工程，重写当前 TVBoxOS-Mobile 项目。原因：当前工程工具链较老，使用 View 体系。目标：使用 Google 最新推荐架构 (UI/Domain/Data 三层)，全 Kotlin + Coroutines + Flows，UI 用 Compose BOM 最新稳定版。先实现手机端视频列表浏览+搜索，然后播放，最后 crash 等。

### Current Project Analysis

**Spider 契约**（最核心抽象，必须兼容）:

当前 `com.github.catvod.crawler.Spider` (Java abstract class) 定义了视频源完整协议:
- `init(Context, String extend)` — 初始化
- `homeContent(boolean filter)` → 首页内容+筛选条件 (JSON String)
- `homeVideoContent()` → 首页推荐视频 (JSON String)
- `categoryContent(tid, pg, filter, extend)` → 分类列表 (JSON String)
- `detailContent(ids)` → 视频详情 (JSON String)
- `searchContent(key, quick)` / `searchContent(key, quick, pg)` → 搜索 (JSON String)
- `playerContent(flag, id, vipFlags)` → 播放地址 (JSON String)
- `proxyLocal(params)` / `proxy(params)` → 代理
- `cancelByTag()` / `destroy()` — 生命周期

三种 Loader 按类型分发:
- **type=0**: JarLoader (DexClassLoader, 加载 `com.github.catvod.spider.{clsKey}`)
- **type=1**: JsLoader (QuickJS, JsSpider 桥接)
- **type=3**: PyLoader (Chaquopy, Python `app.spider(api)`)

**ApiConfig 核心流**: 订阅 URL → 下载 JSON → 解析 sites/spider/parses/lives → SourceBean 列表 → 按 type 分发到 Loader

**SourceBean 关键字段**: key, name, api, type(0=JAR/1=JS/3=PY), searchable, quickSearch, filterable, playerUrl, ext, jar, categories, playerType, clickSelector

**VodInfo 关键字段**: id, name, pic, type, year, area, actor, director, des, seriesFlags(线路), seriesMap(选集), playFlag, playIndex, sourceKey

### Design Decisions (Confirmed)
- 模块划分: app (含 ui/data/domain/common/feature 按包名隔离) + spider:spider-api + spider:spider-jar
- LegacySpiderAdapter 桥接旧 JAR/JS 插件，兼容现有生态
- OkHttp 为主网络层（Spider 生态强绑定 OkHttp）
- Media3 ExoPlayer 替代旧 ExoPlayer 2.x
- spider-jar 最小可用版纳入 Phase 1
- kotlinx.serialization 内部使用，Spider 接口层保留 Gson 桥接

### Plan Adjustments (2026-07-02)

- `build-logic` 暂不作为必需交付物：当前 `newBox` 规模下直接使用 Gradle Kotlin DSL + Version Catalog 足够，避免为了结构一致性引入额外维护成本。
- 测试体系暂缓：当前阶段以功能调试和 `compileDebugKotlin`/`assembleDebug` 构建验证为主，自动化测试不作为当前阻塞项。
- Room 历史/收藏/缓存延后：等 Detail / Player 主流程调试稳定后，再接入 Room 业务读写，避免在播放链路未稳定前扩大状态复杂度。

---

## Work Objectives

### Core Objective
重写 TVBoxOS-Mobile 为现代 Kotlin/Compose 应用，兼容现有 Spider 插件生态，实现视频浏览+搜索+播放全链路。

### Concrete Deliverables
- `newBox/` 目录下完整可构建的 Android 项目
- 可加载旧格式订阅配置
- 可通过 JAR Spider 插件获取视频列表和搜索
- 可播放视频（Media3 ExoPlayer）

### Definition of Done
- [ ] `./gradlew assembleDebug` 成功
- [ ] 启动后可输入订阅 URL 并加载配置
- [ ] 首页展示视频列表，可分类筛选
- [ ] 可搜索并展示结果
- [ ] 可进入详情页查看影片信息
- [ ] 可播放视频

### Must Have
- 三层架构 (UI/Domain/Data) 严格分层
- 全 Kotlin + Coroutines + Flow，零 Java 代码
- Compose UI + Material 3
- Hilt DI
- Spider 插件兼容（至少 JAR 类型）
- UDF 状态管理 (StateFlow<UiState>)
- Room 本地存储（历史/收藏/缓存，Detail/Player 调试稳定后接入业务流）

### Must NOT Have (Guardrails)
- ❌ 不使用 EventBus（用 Flow 替代）
- ❌ 不使用 LiveData（用 StateFlow 替代）
- ❌ 不使用 OkGo（用 OkHttp 替代）
- ❌ 不使用 XML layout（纯 Compose）
- ❌ 不使用 Hawk（用 DataStore 替代）
- ❌ 不使用 ExoPlayer 2.x（用 Media3 替代）
- ❌ 不使用 Gson 做内部序列化（用 kotlinx.serialization）
- ❌ Phase 1 不做 Python Spider（Phase 2 再集成 Chaquopy）
- ❌ 不做过度抽象——每个 UseCase 只做一件事，不超过 1 个文件
- ❌ 不写 AI 模板注释——代码即文档

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: NO (新建项目)
- **Automated tests**: DEFERRED (当前阶段暂缓，不作为阻塞项)
- **Framework**: JUnit 5 + Turbine (Flow testing) + MockK + Compose UI Test
- **If TDD**: 后续恢复测试计划时，Domain UseCase 和 Repository 接口测试先行

### QA Policy
- 每个任务包含 agent-executed QA scenarios
- 证据保存到 `.omo/evidence/task-{N}-{scenario-slug}.{ext}`
- **Compose UI**: Compose Test Rule — semantic matchers, assertions
- **Domain Logic**: JUnit + MockK — unit test with mocks
- **Data Layer**: Instrumented test (Room) / unit test with mock OkHttp
- **Build Verification**: `./gradlew assembleDebug` — 编译通过即基础验证

---

## Module Map (3 modules)

```
app                  → Main application (com.github.tvbox.newbox)
│                       按包名分层:
│                       .common       → AppResult, CoroutineDispatchers, 扩展函数
│                       .domain       → BaseUseCase + Domain Models (SourceConfig, VodItem…)
│                       .data         → Room, DataStore, OkHttp, Repository 实现
│                       .ui.common    → AppTheme + 通用 Compose 组件
│                       .feature.home → HomeScreen + HomeViewModel + GetHomeContentUseCase
│                       .feature.search → SearchScreen + SearchViewModel + SearchUseCase
│                       .feature.detail → DetailScreen + DetailViewModel + GetDetailUseCase
│                       .feature.player → PlayerScreen + PlayerViewModel + GetPlayerUrlUseCase
│                       .feature.settings → SubscriptionScreen + SettingsViewModel
├── spider:spider-api → Spider interface + SpiderLoader/SpiderFactory + 结果模型 (纯 JVM)
└── spider:spider-jar → JarSpiderLoader (DexClassLoader) + LegacySpiderAdapter

依赖方向: app → spider:spider-api, spider:spider-jar
         spider:spider-jar → spider:spider-api
```

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation — 5 tasks, parallel):
├── 1. Version Catalog [quick]
├── 2. app shell + Hilt + Compose theme [quick]
├── 3. app:common — AppResult, CoroutineDispatchers, 扩展函数 [quick]
├── 4. app:domain — BaseUseCase + Domain Models [quick]
└── 5. spider-api (Spider interface, SourceConfig, result models) [quick]

Wave 2 (Core Logic — 5 tasks, parallel after Wave 1):
├── 6. app:data — Room + DataStore + OkHttp + SubscriptionRepository + GetSourcesUseCase (depends: 3,4,5) [unspecified-high]
├── 7. spider-jar: JarLoader + LegacySpiderAdapter (depends: 5) [deep]
├── 8. app:home ViewModel + UseCase (depends: 3,4,5) [unspecified-high]
├── 9. app:search ViewModel + UseCase (depends: 3,4,5) [unspecified-high]
└── 10. app:data — Room entities + DAOs (depends: 6) [unspecified-high]

Wave 3 (UI — 5 tasks, parallel after Wave 2):
├── 11. app:ui.common — AppTheme + 通用 Compose 组件 + app:home UI screens (depends: 2,8) [visual-engineering]
├── 12. app:search UI screens (depends: 2,9) [visual-engineering]
├── 13. Navigation graph integration (depends: 2,11,12) [quick]
├── 14. Spider 数据解析层 (depends: 5,7) [unspecified-high]
└── 15. app:settings UI (depends: 2,6) [visual-engineering]

Wave 4 (Detail + Player — 4 tasks, after Wave 3):
├── 16. app:detail Domain + Data (depends: 5,14) [unspecified-high]
├── 17. app:detail UI (depends: 2,16) [visual-engineering]
├── 18. app:player Domain + Data + Media3 (depends: 5,14) [deep]
└── 19. app:player UI (depends: 2,18) [visual-engineering]

Wave FINAL (Verification — 4 parallel reviews):
├── F1. Plan compliance audit (oracle)
├── F2. Code quality review (unspecified-high)
├── F3. Real QA — full user flow (unspecified-high)
└── F4. Scope fidelity check (deep)
```

### Dependency Matrix

| Task | Depends On | Blocks |
|------|-----------|--------|
| 1-5 | — | 6-15 |
| 6 | 3,4,5 | 10,15 |
| 7 | 5 | 14 |
| 8 | 3,4,5 | 11 |
| 9 | 3,4,5 | 12 |
| 10 | 6 | 16,18 |
| 11 | 2,8 | 13 |
| 12 | 2,9 | 13 |
| 13 | 2,11,12 | — |
| 14 | 5,7 | 16,18 |
| 15 | 2,6 | — |
| 16 | 5,14 | 17 |
| 17 | 2,16 | — |
| 18 | 5,14 | 19 |
| 19 | 2,18 | — |

### Agent Dispatch Summary

- **Wave 1**: 5 tasks — all `quick`
- **Wave 2**: 5 tasks — 6→`unspecified-high`, 7→`deep`, 8→`unspecified-high`, 9→`unspecified-high`, 10→`unspecified-high`
- **Wave 3**: 5 tasks — 11→`visual-engineering`, 12→`visual-engineering`, 13→`quick`, 14→`unspecified-high`, 15→`visual-engineering`
- **Wave 4**: 4 tasks — 16→`unspecified-high`, 17→`visual-engineering`, 18→`deep`, 19→`visual-engineering`
- **FINAL**: 4 tasks — F1→`oracle`, F2→`unspecified-high`, F3→`unspecified-high`, F4→`deep`

---

## TODOs

- [ ] 1. Version Catalog

  **What to do**:
  - 更新 `newBox/gradle/libs.versions.toml` version catalog
  - 定义所有依赖版本：Kotlin 2.3.x, Compose BOM, AGP 9.x, Hilt, Room, OkHttp, Media3, kotlinx.serialization, Coil 3, Navigation
  - 直接在各模块 `build.gradle.kts` 中引用 version catalog；暂不引入 build-logic convention plugins

  **Must NOT do**:
  - 不新增 build-logic convention plugins
  - 不配置 ProGuard/R8（Phase 1 不需要）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: [`android-cli`]

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2-5)
  - **Blocks**: 6-15
  - **Blocked By**: None

  **Acceptance Criteria**:
  - [ ] `newBox/gradle/libs.versions.toml` 存在且包含所有版本
  - [ ] 各模块 `build.gradle.kts` 通过 version catalog 引用依赖

  **Commit**: YES
  - Message: `build: add version catalog`

- [ ] 2. App Shell + Hilt + Compose Theme

  **What to do**:
  - `newBox/app/` 模块（唯一的应用模块，包含所有 common/domain/data/ui/feature 代码）
  - 配置 Application class + @HiltAndroidApp
  - 配置 MainActivity (ComponentActivity, setContent { AppTheme { AppNavHost() } })
  - 创建最小 Compose 主题 (Material 3, dark/light)
  - AndroidManifest.xml: INTERNET, NETWORK_STATE 权限
  - 包结构:
    ```
    com.github.tvbox.newbox/
    ├── App.kt, MainActivity.kt, Navigation.kt
    ├── common/          ← AppResult, CoroutineDispatchers, 扩展函数
    ├── domain/          ← BaseUseCase, Domain Models, UseCase 实现
    ├── data/            ← Room, DataStore, OkHttp, Repository 实现
    ├── ui/
    │   └── common/      ← AppTheme, LoadingView, ErrorView, VodCard
    └── feature/
        ├── home/        ← HomeScreen, HomeViewModel
        ├── search/      ← SearchScreen, SearchViewModel
        ├── detail/      ← DetailScreen, DetailViewModel
        ├── player/      ← PlayerScreen, PlayerViewModel
        └── settings/    ← SubscriptionScreen, SettingsViewModel
    ```

  **Must NOT do**:
  - 不写 SplashActivity——Compose 单 Activity 即可
  - 不配置签名——debug 用默认 keystore
  - 不创建独立 feature 或 core 模块——全部在 app 内按包名隔离

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: [`android-cli`]

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 11-15
  - **Blocked By**: None

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:assembleDebug` 成功
  - [ ] Hilt + Compose 主题正确配置

  **Commit**: YES
  - Message: `app: add application shell with Hilt and Compose theme`

- [ ] 3. app:common — Result 封装 + 扩展函数

  **What to do**:
  - 在 `app` 模块 `com.github.tvbox.newbox.common` 包下
  - 定义 `AppResult<out T>` sealed class (Success/Error)
  - 定义 `suspend fun <T> Result<T>.toAppResult(): AppResult<T>` 扩展
  - 通用 Flow 扩展: `stateIn`, `shareIn` 快捷封装
  - 日志工具 (Timber 或简单 wrapper)
  - `CoroutineDispatchers` injectable (Default, IO, Main)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 6, 8, 9
  - **Blocked By**: None

  **Acceptance Criteria**:
  - [ ] `AppResult` sealed class 有 Success 和 Error 两个子类
  - [ ] `CoroutineDispatchers` 可通过 Hilt 注入

  **Commit**: YES
  - Message: `app: add common package with AppResult and dispatchers`

- [ ] 4. app:domain — Base UseCase + Domain Models

  **What to do**:
  - 在 `app` 模块 `com.github.tvbox.newbox.domain` 包下
  - 定义 `BaseUseCase<in Params, out Result>` interface
  - 定义核心 Domain Models:
    - `SourceConfig` (key, name, api, type, searchable, quickSearch, filterable, ext, jar, categories, playerType)
    - `Category` (id, name)
    - `VodItem` (id, name, pic, type, year, area, note, last)
    - `FilterGroup` (key, name, items)
    - `FilterItem` (key, name, value)
    - `HomeContent` (categories, videos, filters)
    - `SearchResult` (sourceKey, vodItems)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 6, 8, 9
  - **Blocked By**: None

  **Acceptance Criteria**:
  - [ ] 所有 Domain Model 为 data class
  - [ ] BaseUseCase interface 定义清晰

  **Commit**: YES
  - Message: `app: add domain package with base UseCase and models`

- [ ] 5. spider-api — Spider Interface + SourceConfig + Result Models

  **What to do**:
  - 创建 `newBox/spider/spider-api/` 纯 Kotlin 模块
  - 定义 `Spider` Kotlin interface (所有方法为 suspend fun)
  - 定义 `SpiderLoader` interface: `fun load(config: SourceConfig): Spider`
  - 定义 `SpiderFactory` interface: `fun createLoader(type: SourceType): SpiderLoader`
  - 定义 `SourceType` enum: JAR, JS, PYTHON
  - 定义 Spider 返回值的解析模型:
    - `HomeContentResult` (对应 homeContent JSON)
    - `CategoryContentResult` (对应 categoryContent JSON)
    - `DetailContentResult` (对应 detailContent JSON)
    - `SearchContentResult` (对应 searchContent JSON)
    - `PlayerContentResult` (对应 playerContent JSON)
  - 定义 JSON 解析接口 `SpiderResultParser` (给 Data 层实现)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 6, 7, 8, 9, 14, 16, 18
  - **Blocked By**: None

  **References**:
  - 当前项目 `Spider.java` — **直接映射**
    - `homeContent(boolean filter)` → `suspend fun homeContent(filter: Boolean): String`
    - `categoryContent(tid, pg, filter, extend)` → `suspend fun categoryContent(tid: String, pg: String, filter: Boolean, extend: Map<String, String>): String`
    - `detailContent(ids)` → `suspend fun detailContent(ids: List<String>): String`
    - `searchContent(key, quick)` → `suspend fun searchContent(key: String, quick: Boolean, pg: String = "1"): String`
    - `playerContent(flag, id, vipFlags)` → `suspend fun playerContent(flag: String, id: String, vipFlags: List<String>): String`

  **Acceptance Criteria**:
  - [ ] `Spider` interface 包含 init/homeContent/homeVideoContent/categoryContent/detailContent/searchContent/playerContent/proxy/cancelByTag/destroy 全部方法
  - [ ] 模块无 Android 依赖 (纯 Kotlin JVM)
  - [ ] 每个 Spider 返回值有对应的 data class

  **Commit**: YES
  - Message: `spider: add spider-api module with Spider interface and result models`

---

**以下 Tasks 6-19 和 Final Wave 会在执行时由 Sisyphus 按 Wave 2-4 依次展开。此处列出摘要：**

> **注意**: 除 `spider:spider-api` 和 `spider:spider-jar` 外，所有代码均在 `app` 模块内按包名隔离:
> - `com.github.tvbox.newbox.common` — 通用工具
> - `com.github.tvbox.newbox.domain` — Domain Models + UseCase
> - `com.github.tvbox.newbox.data` — Room + DataStore + OkHttp + Repository
> - `com.github.tvbox.newbox.ui.common` — AppTheme + 通用组件
> - `com.github.tvbox.newbox.feature.home` — 首页
> - `com.github.tvbox.newbox.feature.search` — 搜索
> - `com.github.tvbox.newbox.feature.detail` — 详情
> - `com.github.tvbox.newbox.feature.player` — 播放
> - `com.github.tvbox.newbox.feature.settings` — 设置/订阅管理

- [ ] 6. **app:data 基础** — Room + DataStore + OkHttp 配置 + SubscriptionRepository + GetSourcesUseCase [Wave 2]
- [ ] 7. **spider-jar** — JarSpiderLoader (DexClassLoader) + LegacySpiderAdapter (桥接旧 Spider abstract class → 新 Spider interface) [Wave 2]
- [ ] 8. **app:home** — HomeViewModel + GetHomeContentUseCase + GetCategoryContentUseCase [Wave 2]
- [ ] 9. **app:search** — SearchViewModel + SearchUseCase (多源并行搜索) [Wave 2]
- [ ] 10. **app:data Room Schema** — VodRecord, VodCollect, Cache entities + DAOs + AppDatabase [Deferred until Detail/Player 调试稳定]
- [ ] 11. **app:ui.common + home UI** — AppTheme + 通用组件 + HomeScreen (分类+视频列表) + CategoryFilterSheet [Wave 3]
- [ ] 12. **app:search UI** — SearchScreen (搜索栏+结果列表) [Wave 3]
- [ ] 13. **Navigation** — AppNavHost (Home → Search → Detail → Player) [Wave 3]
- [ ] 14. **Spider 数据解析层** — SpiderResultParser 实现 (Gson 解析 JSON → Domain Model) [Wave 3]
- [ ] 15. **app:settings UI** — SubscriptionScreen (输入/管理订阅 URL) [Wave 3]
- [ ] 16. **app:detail Domain+Data** — GetDetailUseCase + 收藏/历史 Room 操作 [Wave 4]
- [ ] 17. **app:detail UI** — DetailScreen (影片信息+线路+选集) [Wave 4]
- [ ] 18. **app:player Domain+Data** — GetPlayerUrlUseCase + VIP解析 + Media3 集成 [Wave 4]
- [ ] 19. **app:player UI** — PlayerScreen (视频播放+控制+字幕) [Wave 4]

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

- [ ] F1. **Plan Compliance Audit** — `oracle`
  验证每个 Must Have 存在，每个 Must NOT Have 不存在。检查 Spider 契约完整性。

- [ ] F2. **Code Quality Review** — `unspecified-high`
  运行 `./gradlew assembleDebug`。检查: 无 Java 文件、无 LiveData、无 EventBus、无 XML layout、无 Hawk。Compose 状态管理全部 UDF。验证代码按包名分层 (common/domain/data/ui/feature)。自动化测试暂不作为当前阻塞项。

- [ ] F3. **Real QA** — `unspecified-high`
  启动应用 → 输入订阅 URL → 加载配置 → 浏览视频列表 → 搜索 → 查看详情 → 播放视频。全链路验证。

- [ ] F4. **Scope Fidelity Check** — `deep`
  验证未实现 Phase 3 内容（直播/DLNA/crash统计）。验证 spider-js 和 spider-py 模块未提前实现。验证只有 app + spider:spider-api + spider:spider-jar 三个模块，无多余 core/feature 模块。

---

## Commit Strategy

- **1**: `build: add version catalog`
- **2**: `app: add application shell with Hilt and Compose theme`
- **3**: `app: add common package with AppResult and dispatchers`
- **4**: `app: add domain package with base UseCase and models`
- **5**: `spider: add spider-api module with Spider interface and result models`
- **6**: `app: add data layer with Room, DataStore, OkHttp, and subscription loading`
- **7**: `spider: add spider-jar module with DexClassLoader and legacy adapter`
- **8**: `app: add home ViewModel and UseCase`
- **9**: `app: add search ViewModel and UseCase`
- **10**: `app: add Room entities and DAOs`
- **11**: `app: add common UI components and home UI screens`
- **12**: `app: add search UI screens`
- **13**: `app: add Navigation graph`
- **14**: `spider: add result parser implementation`
- **15**: `app: add subscription management UI`
- **16**: `app: add detail Domain and Data layers`
- **17**: `app: add detail UI screens`
- **18**: `app: add player Domain and Data layers with Media3`
- **19**: `app: add player UI screens`

## Success Criteria

### Verification Commands
```bash
cd newBox && ./gradlew assembleDebug        # Expected: BUILD SUCCESSFUL
```

### Final Checklist
- [ ] All current "Must Have" present (三层架构, Kotlin, Compose, Hilt, Spider兼容, UDF；Room 业务接入按调整计划后置)
- [ ] All "Must NOT Have" absent (No EventBus, No LiveData, No OkGo, No XML, No Hawk, No ExoPlayer2)
- [ ] Spider JAR plugin compatibility verified with at least one real plugin
- [ ] Full user flow: subscribe → browse → search → detail → play
- [ ] Module count = 3 (app + spider:spider-api + spider:spider-jar)
- [ ] Package structure follows common/domain/data/ui/feature convention
