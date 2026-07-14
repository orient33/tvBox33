# P0 严重问题 Review

本轮只记录已确认、可导致启动失败/核心功能完全不可用/数据不可恢复/高确定性安全事故的阻塞问题。

## 当前结论

暂无已确认 P0。

> 说明：后台 review 子任务超时无有效输出，本文件基于本地 codegraph/read 复核结果整理。发现的问题目前分级到 `P1.md` / `P2.md`。



# P1 严重问题 Review

## P1-01 本地 Spider 代理默认监听所有网卡，局域网设备可触发 Spider proxy 行为

**位置**

- `app/src/main/java/com/github/tvbox/osc/base/App.kt:40-55`
- `app/src/main/java/com/github/tvbox/newbox/server/SpiderProxyServer.kt:14-18`
- `app/src/main/java/com/github/tvbox/newbox/server/SpiderProxyServer.kt:36-65`
- `app/src/main/java/com/github/tvbox/newbox/server/SpiderProxyServer.kt:67-85`

**问题**

`App.onCreate()` 会启动 `SpiderProxyServer(port, ...)`，而 `SpiderProxyServer` 继承 `NanoHTTPD(port)`：

```kotlin
class SpiderProxyServer(
    port: Int,
    private val spiderFactory: SpiderFactory,
    private val subscriptionRepository: SubscriptionRepository,
) : NanoHTTPD(port)
```

NanoHTTPD 的 `port` 构造通常不是仅绑定 `127.0.0.1`，而是监听所有可用网卡。`serve()` 对 `/proxy` 没有来源校验、token 校验或 Host 校验，会把 query/header 组合后交给所有 Spider loader 处理：

```kotlin
uri == "/proxy" -> handleProxy(params)
...
return runBlocking {
    tryProxyViaFactory(params)
}
```

**影响**

手机/盒子处于同一 Wi-Fi 或局域网时，其他设备可能直接访问：

```text
http://<device-ip>:8964/proxy?do=...
```

并触发 app 内已加载 Spider 的 `proxy()` / `proxyLocal()` 逻辑。考虑到 Spider jar/js 属于外部不可信插件生态，这会把本应只服务本机 ExoPlayer 的代理能力暴露给局域网，可能导致：

- 未授权触发 Spider 内部网络请求或本地代理逻辑；
- 被当作局域网开放代理或资源转发端点；
- 触发高成本 Spider 操作造成 DoS；
- 与恶意/脆弱 Spider 组合时放大为文件/网络访问风险。

**触发条件**

1. App 启动；
2. `SpiderProxyServer` 成功监听 `8964` 或递增端口；
3. 攻击者与设备处于可路由网络；
4. 攻击者访问 `/proxy` 并构造 `do` 参数。

**建议**

- 代理服务必须只绑定 `127.0.0.1` / loopback；
- 如 NanoHTTPD 版本支持 hostname 构造，使用 loopback host；
- 增加一次性随机 token，播放器生成的 proxy URL 必须携带 token，`serve()` 校验失败直接 403；
- 对 `do` 和参数白名单化；
- 避免把全部 request headers 透传给 Spider。

**验证方式**

- 在同一局域网另一台设备执行：
  ```bash
  curl http://<device-ip>:8964/proxy?do=ck
  ```
- 修复后应无法从局域网访问，或返回 403；本机 `127.0.0.1` 播放链路仍可用。

---

## P1-02 收藏/历史只以 `vodId` 作为主键，会跨源覆盖、误删和错误续播

**位置**

- `app/src/main/java/com/github/tvbox/newbox/data/local/entity/VodCollect.kt:6-14`
- `app/src/main/java/com/github/tvbox/newbox/data/local/entity/VodRecord.kt:6-18`
- `app/src/main/java/com/github/tvbox/newbox/data/local/dao/VodCollectDao.kt:16-29`
- `app/src/main/java/com/github/tvbox/newbox/data/local/dao/VodRecordDao.kt:15-22`
- `app/src/main/java/com/github/tvbox/newbox/data/repository/CollectRepository.kt:24-50`
- `app/src/main/java/com/github/tvbox/newbox/data/repository/HistoryRepository.kt:30-57`
- `app/src/main/java/com/github/tvbox/newbox/feature/detailplayer/DetailPlayerViewModel.kt:171-190`

**问题**

`VodCollect` 和 `VodRecord` 都保存了 `sourceKey`，但 Room 主键、查询、删除全部只使用 `vodId`：

```kotlin
@Entity(tableName = "vod_collect")
data class VodCollect(
    @PrimaryKey
    val vodId: String,
    val sourceKey: String = "",
)

@Query("SELECT * FROM vod_collect WHERE vodId = :vodId LIMIT 1")
suspend fun getById(vodId: String): VodCollect?

@Query("DELETE FROM vod_collect WHERE vodId = :vodId")
suspend fun deleteById(vodId: String)
```

历史同样只按 `vodId` 查询：

```kotlin
override suspend fun getRecord(vodId: String): VodRecord? = dao.getById(vodId)
```

但 TVBox 多源场景里，不同源出现相同 `vod_id` 非常常见，例如同为 `12345`、`1`、`/detail/xxx` 等。

**影响**

- A 源收藏影片后，B 源相同 `vodId` 会被误判为已收藏；
- 在 B 源取消收藏可能误删 A 源收藏；
- 播放 B 源影片会覆盖 A 源历史；
- 进入详情时 `selectInitialEpisode()` 用 `historyRepository.getRecord(detail.id)`，可能拿到另一个源的 `lastPlayFlag/lastPlayIndex/progress`，导致错误续播、选错线路或 seek 到不合理位置。

这是核心数据一致性问题，会直接影响收藏和历史两个主功能。

**触发条件**

1. 用户添加多个订阅源；
2. 两个源存在相同 `vodId` 的影片；
3. 用户在其中一个源收藏/播放；
4. 再进入另一个源同 `vodId` 影片。

**建议**

- 将收藏/历史唯一标识改为 `(sourceKey, vodId)`；
- Room 可使用复合主键：
  ```kotlin
  @Entity(tableName = "vod_collect", primaryKeys = ["sourceKey", "vodId"])
  ```
- DAO、Repository、ViewModel API 全部传入 `sourceKey + vodId`；
- 历史恢复 `getRecord()` 必须按当前详情的 `sourceKey` 查询；
- 提供 Room migration，不能直接破坏已有收藏/历史。

**验证方式**

- 构造两个不同 source、相同 `vodId` 的 VodDetail；
- 分别收藏/取消收藏，确认互不影响；
- 分别播放并保存不同 episode/progress，确认详情页只恢复当前 source 的历史。

---

## P1-03 播放选集解析没有取消旧任务，快速切集可能播放错误剧集

**位置**

- `app/src/main/java/com/github/tvbox/newbox/feature/detailplayer/DetailPlayerViewModel.kt:107-132`
- `app/src/main/java/com/github/tvbox/newbox/feature/detailplayer/DetailPlayerScreen.kt:187-205`

**问题**

每次 `selectEpisode()` 都直接启动新的 `viewModelScope.launch` 去解析播放地址，但没有保存/取消上一个 Job，也没有在解析返回后校验当前选中的 episode 是否仍然是发起请求时的 episode：

```kotlin
_selectedEpisodeIndex.value = episodeIndex
...
viewModelScope.launch {
    _playerState.value = PlayerUiState.Loading
    val result = getPlayerUrlUseCase(...)
    _playerState.value = PlayerUiState.Ready(result)
}
```

UI 层只要看到 `PlayerUiState.Ready` 就立即喂给 ExoPlayer：

```kotlin
LaunchedEffect(playerState) {
    if (playerState is PlayerUiState.Ready) {
        val result = (playerState as PlayerUiState.Ready).playerResult
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }
}
```

**影响**

如果用户快速切换剧集，或 Spider/playerContent 网络响应顺序乱序，较早选集的旧解析结果可能晚于新选集返回，并覆盖 `_playerState`。最终 UI 显示选中第 N 集，但播放器实际播放第 M 集。

这是核心播放链路问题，尤其 Spider 解析慢、网络差时容易发生。

**触发条件**

1. 进入详情页；
2. 连续快速点击多个剧集；
3. 旧剧集解析请求比新剧集更晚返回；
4. 旧结果覆盖 PlayerUiState，ExoPlayer 播放旧 URL。

**建议**

- 在 ViewModel 保存 `playJob: Job?`，新选集前 cancel 旧 Job；
- 请求发起时记录 `episodeIndex/flag/url`，返回后校验当前选中状态一致再写 `_playerState`；
- 或把选集请求建模为 `selectedEpisodeFlow.flatMapLatest { ... }`；
- `PlayerUiState.Ready` 建议携带 `flag + episodeIndex + episodeUrl`，UI/VM 都可校验。

**验证方式**

- 用 fake `GetPlayerUrlUseCase` 制造第 1 集延迟 2s、第 2 集立即返回；
- 快速选择第 1 集后选择第 2 集；
- 修复前最终可能播放第 1 集；修复后必须保持第 2 集。


# P2 高影响问题 Review

## P2-01 Room 使用 `fallbackToDestructiveMigration()`，后续任意 schema 变更会清空收藏和历史

**位置**

- `app/src/main/java/com/github/tvbox/newbox/data/di/DatabaseModule.kt:20-23`
- `app/src/main/java/com/github/tvbox/newbox/data/local/AppDatabase.kt:11-15`

**问题**

数据库创建时启用了破坏性迁移：

```kotlin
Room.databaseBuilder(
    context, AppDatabase::class.java, AppDatabase.DATABASE_NAME
).fallbackToDestructiveMigration().build()
```

当前数据库承载用户收藏和播放历史，这些是用户长期数据。一旦后续版本增加字段、调整主键或升级版本号但没有提供 migration，Room 会直接删除并重建数据库。

**影响**

- App 升级后用户收藏全部丢失；
- 播放历史和进度全部丢失；
- 如果配合修复 `(sourceKey, vodId)` 主键问题时升级 schema，没有 migration 会把所有既有数据清空。

**触发条件**

1. 发布新版本并修改 Room schema；
2. `AppDatabase.version` 增加；
3. 未提供 migration；
4. 用户升级后启动 app。

**建议**

- 移除 `fallbackToDestructiveMigration()`；
- 从当前 version 1 开始维护显式 migration；
- 修复收藏/历史主键时提供迁移策略，例如保留旧数据并补充默认 `sourceKey`，或生成兼容复合键；
- `exportSchema` 建议改为 `true`，方便审查 schema 演进。

**验证方式**

- 写入收藏/历史；
- 模拟数据库 version 升级；
- 修复后升级不应丢失既有记录。

---

## P2-02 添加订阅时先持久化 URL，再拉取和解析；失败会留下不可用订阅

**位置**

- `app/src/main/java/com/github/tvbox/newbox/data/repository/DefaultSubscriptionRepository.kt:114-135`
- `app/src/main/java/com/github/tvbox/newbox/data/store/SettingsStore.kt:88-103`

**问题**

`loadSubscription()` 在真正 fetch/decode/JSON parse 成功前就把 URL 写入 DataStore：

```kotlin
val parsed = ConfigDecoder.parseSubscriptionUrl(url)
settingsStore.addSubscriptionUrl(url)

val body = fetchUrl(parsed.configUrl)
...
val root = try {
    json.parseToJsonElement(fixed).jsonObject
} catch (e: Exception) {
    Logger.e(TAG, "loadSubscription: invalid JSON from $url: ${e.message}")
    return
}
```

如果网络失败、内容不是合法 JSON、解码失败或配置为空，URL 仍可能已经被保存。后续启动或刷新时会继续处理这个坏订阅。

**影响**

- 用户添加失败的订阅会残留在设置中；
- App 后续自动加载订阅时反复失败；
- 多个坏订阅会污染源列表和错误状态；
- 用户需要手动清理，体验上接近“配置卡死”。

**触发条件**

1. 用户添加错误 URL、失效 URL 或返回非 JSON 的订阅；
2. `settingsStore.addSubscriptionUrl(url)` 已成功；
3. 后续 fetch/decode/parse 失败。

**建议**

- 先 fetch/decode/parse 并确认至少得到有效 config 后，再 `addSubscriptionUrl()`；
- 如果为了保留用户输入，需要单独保存为“草稿/失败订阅”，不要混入正式订阅列表；
- `loadSubscription()` 应向 UI 返回明确失败结果，而不是在部分路径只 log 后 return。

**验证方式**

- 添加一个返回 404 或非 JSON 的订阅 URL；
- 修复前 DataStore 中会残留该 URL；
- 修复后正式订阅列表不应出现失败 URL，并且 UI 能显示添加失败。
