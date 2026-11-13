# 余额表（ApiMeter Wear）技术架构

## 目标

Wear OS 手表端应用：添加多家模型厂商的 API Key，在手表上快捷查看用量/余额，
并把每家厂商的用量**分别**投到表盘 complication 信息位与 Tile 小组件。

## 分层

| 层 | 包 | 职责 |
| --- | --- | --- |
| 契约 | `core` | `UsageSnapshot` / `MetricUnit` / `ProviderId` / 时间窗口 / 数字格式化，纯 Kotlin，可 JVM 单测 |
| 网络 | `net` | `HttpEngine` 抽象、OkHttp 实现、代理链路自动切换（自动/始终/关闭）、代理探测 |
| 存储 | `storage` | DataStore 存设置与凭据元数据；Android Keystore AES/GCM 加密存 API Key |
| 厂商 | `providers` | 每家厂商一个 `ProviderAdapter`，把官方响应归一化成 `UsageSnapshot`；火山 V4 / 腾讯 TC3 签名器 |
| 数据 | `data` | `UsageStore`：唯一数据入口，负责刷新、并发、错误归因、磁盘快照缓存 |
| 后台 | `sync` | WorkManager 周期同步（15 分钟下限）；`BackupSyncEngine` / `BackupMerge` 负责 WebDAV 备份 |
| 加密 | `crypto` | 备份端到端加密：scrypt 派生 + AES-256-GCM 信封、恢复码编解码 |
| WebDAV | `webdav` | 最小 WebDAV 客户端：MKCOL / GET / PUT + ETag 乐观并发 |
| 界面 | `ui` | Compose for Wear OS（Material 3 for Wear）：首页/详情/厂商选择/添加编辑/设置/代理/表盘说明 |
| 表盘 | `complication` | 6 家厂商 + 1 个总览的 `SuspendingComplicationDataSourceService` |
| 小组件 | `tile` | 总览 Tile（单厂商看表盘信息位） |

依赖方向单向：`ui` / `complication` / `tile` → `data` → `providers` / `net` / `storage` → `core`。

`AppContainer` 是唯一的装配点（手写 DI，手表上不引入 DI 框架），进程内单例，
界面、表盘、Tile 共用同一份 `UsageStore` 状态；`UsageStore.onDataRefreshed` 接到
`AppContainer.refreshSurfaces()`，后者调用 `ComplicationRefresher` 与 `TileRefresher`。

## 关键决策

1. **只走官方接口**。OpenAI 用 Organization Usage/Costs（Admin Key），Anthropic 用
   `/v1/organizations/usage_report/messages`，火山方舟用 `GetInferenceUsage`，腾讯云用
   `DescribeUsageRankList`；DeepSeek 与 Moonshot 官方没有用量明细 API，因此只展示余额并在 UI
   明确标注，不实现私有面板端点（`platform.deepseek.com/api/v0/usage/...` 需要 userToken，
   官方明确标为可能随时变更）。
2. **代理自动切换**。境外厂商（OpenAI / Anthropic / Moonshot）优先走代理，国内厂商
   （火山 / 腾讯 / DeepSeek）优先直连；失败自动换链路，成功链路记忆 10 分钟，避免每次先撞超时。
   每条链路使用独立 `OkHttpClient`（代理必须固化在 client 上）。
3. **密钥安全**。API Key 用 Android Keystore 的 AES-256/GCM 加密后写入私有 SharedPreferences；
   设置里只保存元数据，日志不打印密钥。
4. **表盘 / Tile 不和网络耦合**。二者只读 `UsageStore` 的内存/磁盘快照，请求时快速返回；
   数据过期才触发 WorkManager 后台刷新，避免系统回调超时（Tile 有 10 秒硬预算）。
5. **可测**。签名器、格式化、时间窗口、链路决策、JSON 解析全部是纯 JVM 逻辑，用单元测试覆盖；
   Android 依赖只在薄封装里。

## 界面层

6 个页面，手写 `Screen` 栈导航（手表上只有 3~4 层，不引入 navigation-compose）。

```
Home ─┬─ Detail ── Editor
      ├─ ProviderPicker ── Editor
      └─ Settings ─┬─ Proxy
                   ├─ Backup
                   └─ About
```

**入口只有一个**：首页的凭据卡片列表 + 其下方的「添加新凭据」按钮（`Button` 实心主按钮，
尺寸与「刷新全部」一致，不再做成 64dp 的卡片，免得和凭据卡挤在同一视觉层级）；
没有凭据时卡片显示「还没有 API Key」并在下方给一行小字「点击添加 API Key」。
设置页不再放「凭据管理」入口，避免入口重复。表盘/小组件不单独设页；
「刷新表盘与小组件」与「刷新全部」并排在首页，两者同为 `FilledTonalButton`（都是刷新类动作，风格必须一致）。

### 卡片文字的上下留白

Wear 的 `Card` 通过 `defaultMinSize(minHeight = CardDefaults.Height)`（64dp）撑出最小高度，
而内容列用 `Arrangement.SpaceBetween`：**只放一行标题时文字被顶到上沿**，上留白 12dp、
下留白 32dp，看起来像卡片漏了 padding。修法是给内容外面套一层
`Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center)`，
让它吃掉整块高度并居中，单行卡片与多行卡片的上、下留白就都一致了（实测 24/24、26/26）。

### 两套页面骨架

| 骨架 | 用于 | 原因 |
| --- | --- | --- |
| `WearScreen`（`ScalingLazyColumn`） | 列表类页面 | 圆屏缩放效果 + 官方滚动指示器，表冠原生支持 |
| `WearFormScreen`（`Column` + `verticalScroll` + `rotaryScrollable`） | 表单类页面（≤6 字段） | 输入项常驻组合，键盘弹出不会被打断；表冠用官方 rotary 修饰符接上，自带滚动触感 |

两者都配合顶层 `AppScaffold`：

- `AppScaffold` 提供**固定顶部 `TimeText`** 和 `ScaffoldState`。
  注意**单独用 `ScreenScaffold` 不会显示 TimeText**——它的显隐由 AppScaffold 的状态驱动，
  这是实际踩过的坑。
- `ScreenScaffold` 每页一个，提供中心靠右的弧线滚动指示器，并配合 TimeText 做滚动动画。
- 表冠触觉：**只有旋转表冠/表圈时才震动**。手指导轨滑动不震。
  实现上不能用 `snapshotFlow { centerItemIndex }` 直接触发（拖拽也会改变它），
  而是用 `onPreRotaryScrollEvent` 记录「最近一次表冠转动时间」，
  只有落在 400ms 窗口内的居中项变化才 `tick()`；表单页用官方 `rotaryScrollable`
  （它本身就只为表冠提供触感）。

### 按钮层级

Wear 上「能按」必须看得出来，而且层级要一致。全应用只用 Material 3 的标准按钮变体：

| 语义 | 组件 | 例子 |
| --- | --- | --- |
| 主要操作 | `Button`（实心填充） | 添加新凭据、保存并查询 |
| 次要操作 | `FilledTonalButton`（浅色填充） | 刷新全部、编辑凭据 |
| 一般操作 | `OutlinedButton`（描边） | 设置、刷新表盘与小组件、删除此凭据 |
| 整行可点 | `Card(onClick)` | 凭据卡片、设置项 |
| 单选 | `RadioButton` | 代理三种模式 |

四条约束：

1. **一律整宽单列**，不并排两个按钮。圆屏可用宽度只有约 176dp，两列各 86dp 时
   「正在查询…」「刷新表盘与小组件」这类稍长的标签会被压成省略号，看起来像界面坏了。
2. **标签允许折两行**（`maxLines = 2`）。卡片里的说明文字也不设单行上限——
   圆屏上截成省略号比换行更难读。这一条是逐屏截图核对过的。
3. **不放「返回」按钮**。Wear 的返回是系统左缘滑动/返回键，`BackHandler` 已接管。
4. 按钮层级保持一致：一个页面只有一个实心主按钮，其余用浅填充或描边。
5. **尺寸按职责分两档，同一列里比的是同类元素**：
   - 纯入口 / 纯动作 / 功能开关 → **52dp**：`Button` / `OutlinedButton` / `SwitchButton`，
     例如「关于应用」「刷新全部」「添加新凭据」「保存修改」和三个开关；
   - 带信息显示的行 → **64dp 卡片**（Wear `Card` 的最小高度，压不下去）：
     「网络代理」「WebDAV 备份」「统计窗口」「同步间隔」以及首页凭据卡片——
     它们的副标题或右侧值就是当前状态，信息比高度一致更重要。

   判断标准是**有没有显示状态**，不是长得像不像按钮。不带状态的统一成 52dp 控件，
   带状态的保留卡片形态，两边不要互相替代。

### 取色

强调色优先级：

1. 设置里「手动设置主色」打开时，用用户点选的预设色（6 个色点，圆屏上比色环好按）；
2. 否则走系统动态取色（Android 12+ 的 `dynamicColorScheme`，跟随表盘与系统主题）；
3. 取不到动态色时兜底为 **Wear OS 系统应用默认淡蓝** `#A8C7FA`。

`AccentPalettes.fromAccent()` 由一个色相推导整套 primary/container/on* 色调，
保证任何用户选择在纯黑背景上都有足够对比；这套推导是纯计算，有单元测试覆盖
（色相保持、主色够亮、容器够深、文字对比递增）。

表面中性色（`surfaceContainer` 三档、`outline` 等）不随主色变化，保证层级一致。
注意 Wear 的 M3 `ColorScheme` **没有** `surface` / `surfaceVariant` 字段。

### 关于应用

版本号、备份加密的几句话、版权（© 南清）与开源协议（MIT）都集中在「关于应用」页；
设置列表只留可操作项，避免被大段说明文字撑满。

## 表盘与小组件

### 表盘 complication

- 7 个数据源，只声明 **`SHORT_TEXT,LONG_TEXT`**：系统按槽位 `supportedTypes` 请求类型，数据源返回
  不匹配的类型会被丢弃；三星边缘弧槽只要读到 `RANGED_VALUE` 就画整段进度弧，而弧的显隐由表盘
  渲染器的 `rangedValueProgressHidden` 控制、数据源关不掉，所以只能不声明。
- `SHORT_TEXT` 的 `text` 与 `title` 各限 7 字符，圆形槽内 DeepSeek → `DS`、TokenHub → `腾讯云`；
  `LONG_TEXT` 无长度限制，保留全名。
- 都带 `contentDescription`（无障碍要求）和打开应用的 `PendingAction`。
  刷新由 `ComplicationDataSourceUpdateRequester.requestUpdateAll()` 触发。

### Tile 小组件

圆形 Tile 的排版约束踩过坑：按左上角横排会被圆弧裁掉标题、下面留一大片空白。现在的规则是：

- 根 `Box` **水平 + 垂直都居中**，内边距 14dp / 6dp，内容整体落在圆的最宽中带；
- 视觉层次照 Material 3 的字阶：标题 15sp 粗体居中 → 同步时间 11sp 弱化居中 →
  1dp 分隔线 → 数据行（左标签 12sp 次级色 / 右数值 12sp 粗体强调色）；
- 整块 Tile 可点（打开 App），并设置 `Semantics` 内容描述；
- 数据过期由 `freshnessIntervalMillis = 15 分钟` 自动重拉，请求线程内不发网络请求。

### 已用 / 额度

`CredentialMeta.quotaAmount` 是「额度上限」，0 表示未设置。设置后以「已用 / 上限」的文本形式
附加在信息位与 Tile 上（不再画进度环）。字段有默认值，旧数据反序列化不受影响。只有「有用量明细」
的厂商才在编辑页显示这个字段（余额类填了也没有意义）。

### 文案与单位准确性

界面上的每个数字都要能对上是「什么」：

- **命名不夸大**：汇总信息位叫「总览」而不是「用量总览」——DeepSeek / Moonshot 只有余额，
  汇总的可能是余额也可能是 Token 用量，叫「用量」是错的。
- **币种随数据走**：`UsageSnapshot.currency` 由适配器从响应里读出（DeepSeek 的 `balance_infos[].currency`、
  OpenAI 成本接口的 `amount.currency`；Moonshot 响应没有币种字段，按调用域名推断：`moonshot.cn` → CNY、
  `moonshot.ai` → USD）。拿不到币种时**不加货币符号**，绝不默认按 USD 格式化。
- **不跨币种相加**：汇总时单位相同但币种不同的金额（¥ + $）不加和，退化成「N 家」并列展示。
- **时间窗口只用于用量**：只有屏幕上真的存在**成功的用量类数据**时才显示「今天 / 近 7 天」；
  否则写「余额 · 刚刚同步」，详情页写「余额快照」。凭据配了但查询失败不算——
  屏幕上只有余额，就不冒充区间用量。设置页的统计窗口也标注「只影响用量类查询」。
- **额度字段带单位**：「额度上限（Token，可选）」，避免用户误填金额。

## 官方文档

- Wear OS 设计: https://android-docs.cn/design/ui/wear/guides/get-started
- Compose for Wear OS: https://developer.android.com/training/wearables/compose
- 表盘 complication 数据源: https://developer.android.com/training/wearables/watch-faces/information-display
- Tile: https://developer.android.com/training/wearables/tiles
- 旋转输入: https://developer.android.com/training/wearables/compose/rotary-input
- 触觉反馈: https://developer.android.com/training/wearables/compose/haptics

调研结论（版本矩阵、真实 API 签名、踩坑清单）见 [research/wearos-apis.md](research/wearos-apis.md)，
构建方式与已验证项见 [toolchain.md](toolchain.md)。
