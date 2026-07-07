# 仓库指南

## 项目结构与模块组织

本仓库是名为 `NewBox` 的 Android Gradle 项目。主应用模块为 `app`，源码位于 `app/src/main/java`。主要包结构包括：`data/` 用于 Room、DataStore、OkHttp 和解析器；`domain/` 用于领域模型与用例；`feature/` 用于 Compose 页面和 ViewModel；`player/` 用于 Media3/ExoPlayer 与代理播放；`server/` 用于遥控 HTTP API；`ui/common/` 用于通用 UI。Spider 兼容能力位于 `spider/spider-api` 和 `spider/spider-jar`。Android 资源位于 `app/src/main/res`；Spider 相关原生库和插件资源位于 `spider/spider-jar/src/main`。

## 构建、测试与开发命令

- `./gradlew assembleDebug`：构建 Debug APK，输出到 `app/build/outputs/apk/debug/`。
- `./gradlew installDebug`：将 Debug 构建安装到已连接的设备或模拟器。
- `./gradlew :app:compileDebugKotlin`：快速执行 app 模块 Kotlin 编译检查。
- `./gradlew testDebugUnitTest`：运行 JVM 单元测试（如存在）。
- `./gradlew connectedDebugAndroidTest`：在已连接设备上运行仪器测试。
- `./gradlew lintDebug`：运行 Debug 变体的 Android Lint。

请使用 JDK 21、仓库内置 Gradle wrapper、compile SDK 36 和 min SDK 24。

## 编码风格与命名约定

新增 Android 代码优先使用 Kotlin，除非需要对接现有 Java API。Gradle Kotlin DSL 使用现有的两空格缩进，源码风格保持与周边代码一致。Compose UI 放在 `feature/*` 或 `ui/common`，业务逻辑放在 `domain/usecase`，持久化和网络代码放在 `data`。ViewModel 命名为 `FeatureViewModel`，页面命名为 `FeatureScreen`，用例命名为 `VerbNounUseCase`。优先使用 Hilt 构造注入，并通过 `StateFlow` 暴露 UI 状态。

## 测试指南

README 当前说明项目尚无自动化测试。新增行为时，优先在 `app/src/test` 中为解析器、Repository 和 UseCase 添加 JVM 测试；涉及 Android 或 Compose 行为时，在 `app/src/androidTest` 中添加仪器测试。测试类以被测对象命名，例如 `SearchUseCaseTest`。

## 提交与 Pull Request 规范

近期提交历史使用 Conventional Commit 前缀，例如 `feat:`、`fix:`、`docs:`，并搭配简洁中文描述，例如 `feat: 搜索页添加搜索历史`。提交应保持聚焦。Pull Request 应包含简短摘要、关联 issue（如有）、已执行的验证命令、UI 变更截图或视频，以及 Spider 兼容性或播放风险说明。

## 参考项目

本项目是 `TVBoxOS-Mobile` 的现代化重写版本，参考了 [XiaoRanLiu3119/TVBoxOS-Mobile](https://github.com/XiaoRanLiu3119/TVBoxOS-Mobile) 和 [orient33/TVBoxOS-Mobile](https://github.com/orient33/TVBoxOS-Mobile)。原项目基于 [q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS)。涉及兼容性、Spider 行为或历史实现时，优先对照这些项目和 README 中的说明。

## 安全与配置提示

不要提交本地密钥、签名文件或机器相关 SDK 路径。`local.properties` 应仅保留在本地。外部 Spider `.jar` 和 `.js` 来源应视为不可信输入；保留防御式解析逻辑，避免未经评审的大范围文件或网络访问变更。
