# 项目规则

## 表盘复杂功能（complication）验证口径

| 规则 | 由什么拦住 |
|---|---|
| 7 个 complication service 的 SUPPORTED_TYPES 不得含 `RANGED_VALUE`（含了三星 GradientInfoBoard 边缘槽就会画进度弧） | `ManifestTypeTest.complicationServices_neverDeclareRangedValue` 读真实清单，放回去即 FAILED |
| 改动表盘可见效果，必须用**控制变量前后对比截图**证明，不能只凭一张截图下结论 | 见下方「验证口径」；无自动守卫，靠交差时附前后截图 |
| 交差前跑通全量校验 | `clean :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug` |

### 验证口径（2026-11 实机取证后订立）

用户曾两次纠正「环形组件还是有进度条」。根因不是代码写错，而是**把一张截图当成结论**：

- 曾声称「退化区间 `min=max=0` 能消掉进度弧」，实机证明不能；
- 曾声称「圆形/弧形槽已无进度弧」，但那几段弧从未做过控制变量实验。

**凡是「表盘上还画不画 XX」这类结论，必须做控制变量**：改一个变量（range 值、数据类型、数据源绑定），前后各一张截图，看目标元素是否随之变化。只截「改完之后」的一张图不算证据。同理，若设备掉线、只能给模拟器结论，必须明确写出适用范围和没验证到的部分，不能把「理论上可行」写成「已修好」。

### 已证实的实机事实（SM-L315N / Android 16 / 三星 GradientInfoBoard）

- 表盘 4 个边缘槽（`complication-tl/tr/bl/br-edge`）声明的 supportedTypes 是
  `{RANGED_VALUE, LONG_TEXT, SHORT_TEXT, EMPTY}`，**含 SHORT_TEXT**。
- 这些槽对任意 `RANGED_VALUE` 数据都画整段进度弧/轨道：
  `value=75/max=100` 时弧按比例分段填充；返回 `NoDataComplicationData` 时该弧消失（但文字也消失）。
- 官方 KDoc：`RangedValueComplicationData.PLACEHOLDER` 仍画灰弧且只允许用于
  `NoDataComplicationData.placeholder`；`ColorRamp` 只是 renderer *may choose* 的 hint，
  全透明实测会让弧和文字一起消失。二者都不是「只隐藏弧、保留文字」的机制。
- `ComplicationDrawable.rangedValueProgressHidden` 是**表盘渲染器侧**属性，数据源无法设置。
- 因此数据源侧唯一可行的消弧方案是**不再声明 RANGED_VALUE**，让系统改请求纯文本类型。
- 主动刷新 complication 的正确广播要传 Parcelable ComponentName：
  `am broadcast -a android.support.wearable.complications.ACTION_REQUEST_UPDATE_ALL --ecn android.support.wearable.complications.EXTRA_PROVIDER_COMPONENT <ComponentName> -n com.google.android.wearable.app/com.google.android.clockwork.wcs.complications.module.StaticUpdateRequestProxyReceiver`
  （用 `--es` 字符串会被忽略，表盘会一直显示旧数据，导致误判实验结论）。

### 未完成验证（设备恢复后需补）

- 最终版（撤声明）装到实机后的截图：边缘槽是否真无弧、文字是否完整。
- 撤声明后系统对边缘槽实际请求 `SHORT_TEXT` 还是 `LONG_TEXT`（若 LONG_TEXT，正文含换行副行可能在窄弧槽截断）。
- `NoDataComplicationData(placeholder=ShortText)` 是否会被表盘当普通文字渲染。
