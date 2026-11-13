# Wear OS 3/4 — Exact API shapes (Kotlin + Jetpack Compose)

Researched **2026-10-27** from official sources only:

- `developer.android.com` guides (`*.md.txt` variants) and `/reference/kotlin/...` API reference
- AndroidX release notes (`wear-compose`, `wear-tiles`)
- **`dl.google.com/dl/android/maven2/.../maven-metadata.xml`** for versions
- **`*-sources.jar` from Google Maven** for literal Kotlin/Java signatures (the authoritative
  source — every signature below was read out of the published sources jar, not from memory or blogs)
- **`*.aar` → `META-INF/com/android/build/gradle/aar-metadata.properties`** for `minCompileSdk`

---

## 0. Version table (area 6)

Latest **stable** per `maven-metadata.xml` as of 2026-10-27. `<path>` is relative to
`https://dl.google.com/dl/android/maven2/` and `<path>/maven-metadata.xml` is the URL to fetch.

| Artifact | `<path>` | Latest stable | Alpha-only? | `minCompileSdk` | Min AGP |
|---|---|---|---|---|---|
| `androidx.wear.compose:compose-material3` | `androidx/wear/compose/compose-material3` | **1.7.1** | no (1.8.0-alpha02 is the newest *any* version) | **37** | **9.1.0** |
| `androidx.wear.compose:compose-foundation` | `androidx/wear/compose/compose-foundation` | **1.7.1** | no (1.8.0-alpha02 newest any) | **37** | **9.1.0** |
| `androidx.wear.tiles:tiles` | `androidx/wear/tiles/tiles` | **1.6.2** | no | **35** | 8.6.0 |
| `androidx.wear.protolayout:protolayout` | `androidx/wear/protolayout/protolayout` | **1.4.2** | no | **34** | 8.1.1 |
| `androidx.wear.protolayout:protolayout-expression` | `androidx/wear/protolayout/protolayout-expression` | **1.4.2** | no | **34** | 8.1.1 |
| `androidx.wear.protolayout:protolayout-material3` | `androidx/wear/protolayout/protolayout-material3` | **1.4.2** | no | **35** | 8.6.0 |
| `androidx.wear.watchface:watchface-complications-data-source-ktx` | `androidx/wear/watchface/watchface-complications-data-source-ktx` | **1.3.0** | no | **34** | 8.1.1 |
| `androidx.wear.watchface:watchface-complications-data` (non-ktx, for reference) | `androidx/wear/watchface/watchface-complications-data` | **1.3.0** | no | **34** | 8.1.1 |
| `androidx.wear:wear` (Wearable support lib) | `androidx/wear/wear` | **1.4.0** | no | **34** | 8.1.1 |
| `androidx.wear:wear-input` | `androidx/wear/wear-input` | **1.2.0** | no | **34** | 8.1.1 |
| `com.google.android.gms:play-services-wearable` | `com/google/android/gms/play-services-wearable` | **20.0.1** | no | — (Play services AAR, no `minCompileSdk` gate) | — |

### ⚠️ Toolchain trap you must decide on before writing code

`compose-material3` / `compose-foundation` **1.7.x** hard-require `compileSdk = 37` and
**AGP 9.1.0** (enforced by `aar-metadata.properties`; the build fails with
`Dependency ... requires libraries and applications that depend on it to compile against version 37 or later of the Android APIs`).

Measured fallbacks:

| wear-compose version | `minCompileSdk` | Min AGP |
|---|---|---|
| 1.7.1 (latest stable) | 37 | 9.1.0 |
| 1.6.2 | **35** | 8.6.0 |
| 1.5.0 | **35** | 8.6.0 |

**Recommendation for a Wear OS 3/4 app on a normal toolchain: pin `1.6.2` with `compileSdk = 35`**
(API surface used below is identical in 1.6.2 and 1.7.1 except where noted).
Tiles 1.6.x needs `compileSdk = 35`, which matches.

```kotlin
// libs.versions.toml — versions verified against maven-metadata.xml
[versions]
wearCompose      = "1.6.2"   # 1.7.1 requires compileSdk 37 + AGP 9.1.0
wearTiles        = "1.6.2"
wearProtoLayout  = "1.4.2"
wearWatchface    = "1.3.0"

[libraries]
wear-compose-material3   = { module = "androidx.wear.compose:compose-material3",   version.ref = "wearCompose" }
wear-compose-foundation  = { module = "androidx.wear.compose:compose-foundation",  version.ref = "wearCompose" }
wear-compose-navigation  = { module = "androidx.wear.compose:compose-navigation",  version.ref = "wearCompose" }
wear-tiles               = { module = "androidx.wear.tiles:tiles",                 version.ref = "wearTiles" }
wear-protolayout         = { module = "androidx.wear.protolayout:protolayout",    version.ref = "wearProtoLayout" }
wear-protolayout-m3      = { module = "androidx.wear.protolayout:protolayout-material3", version.ref = "wearProtoLayout" }
wear-protolayout-expr    = { module = "androidx.wear.protolayout:protolayout-expression", version.ref = "wearProtoLayout" }
wear-complications-ktx   = { module = "androidx.wear.watchface:watchface-complications-data-source-ktx", version.ref = "wearWatchface" }
wear-input               = { module = "androidx.wear:wear-input", version = "1.2.0" }
```

> Note: `androidx.wear.compose:compose-ui` **does not exist** on Google Maven (HTTP 404).
> There is no `androidx.wear.compose.ui` package. Anything you remember as
> "Wear Compose ui haptics" is not a real artifact.

---

## 1. Watch face complications (data source)

### Artifact

```kotlin
implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0")
```

The suspending base class lives in the **`-ktx`** artifact; the non-ktx
`watchface-complications-data-source` holds `ComplicationDataSourceService` and the
`ComplicationDataSourceUpdateRequester`.

### Exact base class and package

```
androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
```

Verified declaration (from `watchface-complications-data-source-ktx-1.3.0-sources.jar`,
`SuspendingComplicationDataSourceService.kt`):

```kotlin
public abstract class SuspendingComplicationDataSourceService : ComplicationDataSourceService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    final override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        scope.launch { listener.onComplicationData(onComplicationRequest(request)) }
    }

    @UiThread
    abstract suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData?

    @CallSuper open override fun onDestroy()
}
```

Key consequences:

- `onComplicationRequest(request, listener)` is **`final`** — you cannot override the 2-arg form.
- You override **only** the 1-arg `suspend` form: `suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData?`
- Return `null` to mean "don't overwrite the previous data".
- There is **no** `setComplicationData(...)` helper and **no** `ComplicationDataSourceUpdateRequester`
  call needed to *deliver* data — you simply **return** the `ComplicationData` from the suspend
  function; the base class feeds it to `listener.onComplicationData(...)`.
  (`ComplicationDataSourceUpdateRequester` is only for *pushing* a refresh request from app code, §1.6.)

`getPreviewData` is inherited abstract from `ComplicationDataSourceService`:

```kotlin
abstract fun getPreviewData(type: ComplicationType): ComplicationData?
```

Both live in package `androidx.wear.watchface.complications.datasource`;
`ComplicationData` / `ComplicationType` / builders are in
`androidx.wear.watchface.complications.data`.

### Complete, compiling service

```kotlin
package com.example.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class MyComplicationDataSourceService : SuspendingComplicationDataSourceService() {

    // REQUIRED override #1 — the request handler (suspend, 1 arg, nullable return)
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        // ComplicationRequest properties:
        //   request.complicationInstanceId : Int
        //   request.complicationType       : ComplicationType
        //   request.immediateResponseRequired : Boolean  (@get:JvmName("isImmediateResponseRequired"))
        //   request.isForSafeWatchFace     : Int        (API 33+, TargetWatchFaceSafety.UNKNOWN/SAFE/UNSAFE)
        val value = 0.62f
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT  -> shortText("62%")
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = value,
                min = 0f,
                max = 1f,
                contentDescription = plain("Battery 62 percent"),
            )
                .setText(plain("62%"))                                  // setText(ComplicationText?)
                .setTitle(plain("Battery"))                             // setTitle(ComplicationText?)
                .setMonochromaticImage(icon(R.drawable.ic_battery))     // MonochromaticImage?
                .setSmallImage(smallImage(R.drawable.ic_battery))       // SmallImage?
                .setTapAction(openAppPendingIntent())                   // setTapAction(PendingIntent?)
                .build()
            else -> null
        }
    }

    // REQUIRED override #2 — preview shown in the watch-face editor UI
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        shortText("62%")

    private fun shortText(text: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = plain(text),                       // required, positional/named ctor arg
            contentDescription = plain(text),         // required — NOT a setter
        )
            .setTitle(plain("Battery"))
            .setMonochromaticImage(icon(R.drawable.ic_battery))
            .setSmallImage(smallImage(R.drawable.ic_battery))
            .setTapAction(openAppPendingIntent())
            .build()

    // ComplicationText: interface androidx.wear.watchface.complications.data.ComplicationText
    // There is NO ComplicationText.plainText(...) — that static only exists on the deprecated
    // android.support.wearable.complications.ComplicationText class.
    private fun plain(text: String): ComplicationText =
        PlainComplicationText.Builder(text).build()

    private fun icon(resId: Int): MonochromaticImage =
        MonochromaticImage.Builder(
            Icon.createWithResource(this, resId)
        ).build()

    private fun smallImage(resId: Int): SmallImage =
        SmallImage.Builder(
            Icon.createWithResource(this, resId),
            SmallImageType.ICON,      // enum: ICON, PHOTO
        ).build()

    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
```

### Builder signatures (verbatim from `watchface-complications-data-1.3.0-sources.jar`)

```kotlin
// ShortTextComplicationData  — package androidx.wear.watchface.complications.data
class Builder(
    private val text: ComplicationText,          // required
    private val contentDescription: ComplicationText,  // required
) : BaseBuilder<Builder, ShortTextComplicationData>() {
    fun setTapAction(tapAction: PendingIntent?): Builder
    fun setValidTimeRange(validTimeRange: TimeRange?): Builder
    fun setTitle(title: ComplicationText?): Builder
    fun setMonochromaticImage(monochromaticImage: MonochromaticImage?): Builder
    fun setSmallImage(smallImage: SmallImage?): Builder
    override fun build(): ShortTextComplicationData
}
// companion/static: ShortTextComplicationData.TYPE, ShortTextComplicationData.MAX_TEXT_LENGTH == 7

// LongTextComplicationData — identical shape
class Builder(
    private val text: ComplicationText,
    private val contentDescription: ComplicationText,
) : BaseBuilder<Builder, LongTextComplicationData>() {
    fun setTapAction(tapAction: PendingIntent?): Builder
    fun setValidTimeRange(validTimeRange: TimeRange?): Builder
    fun setTitle(title: ComplicationText?): Builder
    fun setMonochromaticImage(icon: MonochromaticImage?): Builder   // note: param named `icon`
    fun setSmallImage(smallImage: SmallImage?): Builder
    override fun build(): LongTextComplicationData
}

// RangedValueComplicationData
class Builder(
    value: Float,                       // no-dynamicValue ctor
    min: Float,
    max: Float,
    contentDescription: ComplicationText,
) : BaseBuilder<Builder, RangedValueComplicationData>() {
    // plus @RequiresApi(33) ctor (dynamicValue, fallbackValue, min, max, contentDescription)
    // and  @RequiresApi(34) ctor (dynamicValue, min, max, contentDescription)
    fun setTapAction(tapAction: PendingIntent?): Builder
    fun setValidTimeRange(validTimeRange: TimeRange?): Builder
    fun setMonochromaticImage(monochromaticImage: MonochromaticImage?): Builder
    fun setSmallImage(smallImage: SmallImage?): Builder
    fun setTitle(title: ComplicationText?): Builder
    fun setText(text: ComplicationText?): Builder          // ONLY RangedValue has setText(...)
    fun setColorRamp(colorRamp: ColorRamp?): Builder
    fun setValueType(@RangedValueType valueType: Int): Builder  // TYPE_UNDEFINED/TYPE_RATING/TYPE_PERCENTAGE
    override fun build(): RangedValueComplicationData
}

// BaseBuilder (sealed) — the only "generic" setters
sealed class BaseBuilder<BuilderT : BaseBuilder<BuilderT, BuiltT>, BuiltT> {
    fun build(): BuiltT
    fun setExtras(extras: PersistableBundle): BuilderT
    fun setPersistencePolicy(@ComplicationPersistencePolicy policy: Int): BuilderT
    fun setDisplayPolicy(@ComplicationDisplayPolicy policy: Int): BuilderT
}

// Images
class MonochromaticImage private constructor(val image: Icon, val ambientImage: Icon?) {
    class Builder(image: Icon) {
        fun setAmbientImage(ambientImage: Icon?): Builder
        fun build(): MonochromaticImage
    }
}
class SmallImage private constructor(val image: Icon, val type: SmallImageType, val ambientImage: Icon?) {
    class Builder(image: Icon, type: SmallImageType) {
        fun setAmbientImage(ambientImage: Icon?): Builder
        fun build(): SmallImage
    }
}
enum class SmallImageType { ICON, PHOTO }

// Text
interface ComplicationText {
    fun getTextAt(resources: Resources, instant: Instant): CharSequence
    fun returnsSameText(firstInstant: Instant, secondInstant: Instant): Boolean
    fun getNextChangeTime(afterInstant: Instant): Instant
    fun isAlwaysEmpty(): Boolean
    companion object {
        @JvmField val EMPTY: ComplicationText
        @JvmField val PLACEHOLDER: ComplicationText
    }
}
class PlainComplicationText : ComplicationText {
    class Builder(text: CharSequence) { fun build(): PlainComplicationText }
}
class TimeFormatComplicationText /* Builder(format: String).setStyle/setText/setTimeZone/build() */
class TimeDifferenceComplicationText /* Builder.setText/setDisplayAsNow/setMinimumTimeUnit/build() */
```

**Corrections to the assumptions in the brief:**

| Assumed | Reality |
|---|---|
| `setText(...)` on ShortText/LongText builders | ❌ `text` is a **required constructor arg**; `setText(ComplicationText?)` exists **only** on `RangedValueComplicationData.Builder` |
| `setContentDescription(...)` | ❌ No such setter anywhere. `contentDescription` is a **required constructor arg** of every data builder |
| `ComplicationText.plainText("x")` | ❌ Not on the androidx `ComplicationText` interface. Use `PlainComplicationText.Builder("x").build()` |
| `setTapAction(PendingIntent)` | ✅ Correct: `setTapAction(tapAction: PendingIntent?)` on all four builders |
| `MonochromaticImage` / `SmallImage` builders | ✅ `MonochromaticImage.Builder(Icon)`, `SmallImage.Builder(Icon, SmallImageType)` |

### Manifest

```xml
<service
    android:name=".complication.MyComplicationDataSourceService"
    android:exported="true"
    android:label="@string/my_complication_service_label"
    android:icon="@drawable/complication_icon"        <!-- single-colour white vector -->
    android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">

    <intent-filter>
        <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST" />
    </intent-filter>

    <!-- comma-separated, from: GOAL_PROGRESS, LONG_TEXT, ICON, LARGE_IMAGE,
         RANGED_TEXT, SHORT_TEXT, SMALL_IMAGE, WEIGHTED_ELEMENTS -->
    <meta-data
        android:name="android.support.wearable.complications.SUPPORTED_TYPES"
        android:value="SHORT_TEXT,RANGED_TEXT" />

    <!-- 0 == push updates only; otherwise must be >= 300 (system-enforced minimum) -->
    <meta-data
        android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
        android:value="300" />

    <!-- optional config activity -->
    <meta-data
        android:name="android.support.wearable.complications.PROVIDER_CONFIG_ACTION"
        android:value="MY_CONFIG_ACTION" />
</service>
```

Metadata keys (constants on `ComplicationDataSourceService`):

```kotlin
ACTION_COMPLICATION_UPDATE_REQUEST = "android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"
METADATA_KEY_SUPPORTED_TYPES       = "android.support.wearable.complications.SUPPORTED_TYPES"
METADATA_KEY_UPDATE_PERIOD_SECONDS = "android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
METADATA_KEY_SAFE_WATCH_FACES      = "android.support.wearable.complications.SAFE_WATCH_FACES"
METADATA_KEY_IMMEDIATE_UPDATE_PERIOD_MILLISECONDS =
    "androidx.wear.watchface.complications.data.source.IMMEDIATE_UPDATE_PERIOD_MILLISECONDS"
```

Permission: **`com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER`**.

### Push a refresh from app code

```kotlin
import android.content.ComponentName
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

ComplicationDataSourceUpdateRequester
    .create(
        context = context,                                  // any Context; uses applicationContext
        complicationDataSourceComponent = ComponentName(context, MyComplicationDataSourceService::class.java),
    )
    .requestUpdateAll()
// interface: requestUpdateAll(); requestUpdate(vararg complicationInstanceIds: Int)
// Only works when called from the same package as the data source. Keep it >= 5 min on average.
```

### Timeline data source (bonus)

```kotlin
class MyTimelineSource : SuspendingTimelineComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline?
    override fun getPreviewData(type: ComplicationType): ComplicationData?
}
// ComplicationDataTimeline(defaultComplicationData = ..., timelineEntries = listOf(TimelineEntry(validity = TimeInterval(start, end), complicationData = ...)))
```

---

## 2. Tiles

### Artifacts

```kotlin
implementation("androidx.wear.tiles:tiles:1.6.2")
implementation("androidx.wear.protolayout:protolayout:1.4.2")
implementation("androidx.wear.protolayout:protolayout-expression:1.4.2")
implementation("androidx.wear.protolayout:protolayout-material3:1.4.2") // for Material3TileService
```

### CURRENT (2025+/2026) way: `Material3TileService`

`androidx.wear.tiles.Material3TileService` was **added in tiles 1.6.0-alpha04 (Jan 2026)** and is
**feature-complete in 1.6.0 (Mar 2026)**. It is the recommended way: one `suspend` function returns
layout **and** resources (no two-binder-call dance, no manual `onTileResourcesRequest`).

Verified declaration (`tiles-1.6.2-sources.jar`, `Material3TileService.kt`):

```kotlin
package androidx.wear.tiles

public abstract class Material3TileService
@JvmOverloads
public constructor(
    private val allowDynamicTheme: Boolean = true,
    private val defaultColorScheme: ColorScheme = ColorScheme(),        // androidx.wear.protolayout.material3.ColorScheme
    private val serviceScope: CoroutineScope? = null,
) : TileService() {

    // THE override you implement. Note the receiver: MaterialScope.
    protected abstract suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile

    final override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile>
    final override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> // no-op: resources come from ProtoLayoutScope
    // onTileEnterEvent / onTileLeaveEvent are final + deprecated (use onRecentInteractionEventsAsync)
}
```

Complete, compiling tile service:

```kotlin
package com.example.wear.tile

import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.layout.basicImage
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import androidx.wear.tiles.timeline
import androidx.wear.tiles.timelineEntry
import kotlin.time.Duration.Companion.minutes

class MyTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val layout = primaryLayout(
            mainSlot = {
                text("Hello, Tile".layoutString)
            },
            // titleSlot / bottomSlot / labelForBottomSlot / onClick / margins also available
        )
        return tile(
            timeline = timeline(timelineEntry(layout = layout)),
            freshness = 5.minutes,      // Duration? — maps to setFreshnessIntervalMillis(ms)
            // state = null,            // @RequiresSchemaVersion(1, 200)
            // resourcesVersion = null, // omitted on purpose: ProtoLayoutScope handles it
        )
    }
}
```

Exact helpers in `androidx.wear.tiles` (`Tile.kt`, `@file:JvmName("TileKt")`):

```kotlin
public fun tile(
    timeline: Timeline,
    freshness: Duration? = null,                       // kotlin.time.Duration
    @RequiresSchemaVersion(major = 1, minor = 200) state: State? = null,
    resourcesVersion: String? = null,
): Tile

public fun timeline(vararg entries: TimelineEntry): Timeline
public fun timelineEntry(layout: LayoutElement, validity: TimeInterval? = null): TimelineEntry
public fun timeInterval(start: Duration, end: Duration): TimeInterval
```

### `TileBuilders.Tile.Builder` — required fields and the `setResources` surprise

Verified from `tiles-1.6.2-sources.jar`, `TileBuilders.java`:

```java
public static final class TileBuilders.Tile {
    public @NonNull String getResourcesVersion();
    public @Nullable Timeline getTileTimeline();          // androidx.wear.protolayout.TimelineBuilders.Timeline
    public long getFreshnessIntervalMillis();
    public @Nullable State getState();

    public static final class Builder {
        public Builder() {}
        public @NonNull Builder setResourcesVersion(@NonNull String resourcesVersion);   // schema 1.0
        public @NonNull Builder setTileTimeline(@NonNull Timeline tileTimeline);         // schema 1.0  <-- CURRENT
        public @NonNull Builder setFreshnessIntervalMillis(long freshnessIntervalMillis); // schema 1.0
        public @NonNull Builder setState(@NonNull State state);                          // schema 1.200
        @Deprecated public @NonNull Builder setTimeline(androidx.wear.tiles.TimelineBuilders.Timeline timeline);
        public @NonNull Tile build();
    }
}
```

- **Required:** a timeline. In 1.6 it is `setTileTimeline(androidx.wear.protolayout.TimelineBuilders.Timeline)`
  (or the `tile(...)` DSL). The old `setTimeline(androidx.wear.tiles.TimelineBuilders.Timeline)` is
  `@Deprecated` but still present (HIDDEN-free, binary compatible).
- **`setResources(...)` does not exist** in 1.6.x. There is only `setResourcesVersion(String)`.
  Resources are shipped automatically: `TileService` collects them from
  `ProtoLayoutScope.collectResources()` and **merges/overwrites** the resources version itself
  (`TileService.java` ~line 690–760: `tileBuilder.setResourcesVersion(incomingResVer)`).
  So with `Material3TileService` / `materialScopeWithResources`, **omit `resourcesVersion`**.
- `setFreshnessIntervalMillis(long)`: **elapsed-time** ms (`0` = no auto-refresh; don't refresh
  faster than once a minute). The DSL takes a `Duration` and converts via `toLong(MILLISECONDS)`.
- No `TileId` field on `Tile`; the id is supplied by the system in `TileRequest`/`ResourcesRequest`.

### Building layout: `MaterialScope` / `layout()` / `text()`

```kotlin
// androidx.wear.protolayout.material3 (protolayout-material3)
public open class MaterialScope internal constructor(...) {
    public val context: Context
    public val deviceConfiguration: DeviceParameters
    public val colorScheme: ColorScheme
    public val shapes: Shapes
    public val hasProtoLayoutScope: Boolean
    public val protoLayoutScope: ProtoLayoutScope     // throws if not created with resources
}

// Top-level entry points — pick by whether you need image resources:
public fun materialScope(
    context: Context,
    deviceConfiguration: DeviceParameters,
    allowDynamicTheme: Boolean = true,
    defaultColorScheme: ColorScheme = ColorScheme(),
    layout: MaterialScope.() -> LayoutElement,
): LayoutElement

public fun materialScopeWithResources(
    context: Context,
    protoLayoutScope: ProtoLayoutScope,             // == TileRequest.getScope()
    deviceConfiguration: DeviceParameters,
    allowDynamicTheme: Boolean = true,
    defaultColorScheme: ColorScheme = ColorScheme(),
    layout: MaterialScope.() -> LayoutElement,
): LayoutElement

// Components (all MaterialScope extensions):
public fun MaterialScope.text(
    text: LayoutString,                             // use "Hi".layoutString  (androidx.wear.protolayout.types)
    modifier: LayoutModifier = LayoutModifier,
    @TypographyToken typography: Int = defaultTextElementStyle.typography,
    color: LayoutColor = defaultTextElementStyle.color,
    italic: Boolean = false,
    underline: Boolean = false,
    scalable: Boolean? = null,
    maxLines: Int = 1,
    alignment: Int = ...,
    overflow: Int = ...,
    settings: List<FontSetting> = emptyList(),
): LayoutElement

public fun MaterialScope.primaryLayout(
    mainSlot: (MaterialScope.() -> LayoutElement),
    titleSlot: (MaterialScope.() -> LayoutElement)? = null,
    bottomSlot: (MaterialScope.() -> LayoutElement)? = null,
    labelForBottomSlot: (MaterialScope.() -> LayoutElement)? = null,
    onClick: Clickable? = null,
    margins: PrimaryLayoutMargins = DEFAULT_PRIMARY_LAYOUT_MARGIN,
): LayoutElement

// also: button / textButton / iconButton / edgeButton / titleCard / appCard / textDataCard /
//       iconDataCard / graphicDataCard / card / buttonGroup / circularProgressIndicator /
//       segmentedCircularProgressIndicator / icon / backgroundImage / avatarImage
```

> ⚠️ There is **no public `MaterialScope.layout()` function**. `.layout()` seen inside AndroidX
> sources is the *invocation of a lambda parameter named `layout`* (Kotlin lets you write
> `receiver.lambdaParam()` for a `A.() -> B` value). Don't try to call it yourself.

### Classic (still valid) `TileService` shape

```kotlin
package androidx.wear.tiles

public abstract class TileService extends Service {
    public static final String ACTION_BIND_TILE_PROVIDER = "androidx.wear.tiles.action.BIND_TILE_PROVIDER";
    public static final String METADATA_PREVIEW_KEY = "androidx.wear.tiles.PREVIEW";
    public static final String METADATA_GROUP_KEY   = "androidx.wear.tiles.GROUP";

    @MainThread protected abstract ListenableFuture<Tile> onTileRequest(TileRequest requestParams);

    // optional in 1.6 when using ProtoLayoutScope; default impl returns empty Resources
    @MainThread protected ListenableFuture<Resources> onTileResourcesRequest(ResourcesRequest requestParams);

    @Deprecated protected ListenableFuture<androidx.wear.tiles.ResourceBuilders.Resources>
        onResourcesRequest(ResourcesRequest requestParams);   // OLD name, @Deprecated

    public static TileUpdateRequester getUpdater(@NonNull Context context);
}
```

There is **no `SuspendTileService` class**. The suspending variant is `Material3TileService`
(above). If you prefer the classic base class, bridge a coroutine yourself
(`SuspendToFutureAdapter.launchFuture { }` or a `ResolvableFuture`).

### Request a tile refresh from app code

```kotlin
import androidx.wear.tiles.TileService

// interface androidx.wear.tiles.TileUpdateRequester
//   void requestUpdate(@NonNull Class<? extends TileService> tileService);
//   default void requestUpdate(@NonNull Class<? extends TileService> tileService, int tileId);  // tileId honoured on API 37+

TileService.getUpdater(context).requestUpdate(MyTileService::class.java)
```

Note: it takes the **service class**, not a `ComponentName`; there is no `requestUpdateAll()`.

### Manifest (tile)

```xml
<service
    android:name=".tile.MyTileService"
    android:exported="true"
    android:label="@string/tile_label"
    android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
    <intent-filter>
        <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
    </intent-filter>

    <meta-data
        android:name="androidx.wear.tiles.PREVIEW"
        android:resource="@drawable/tile_preview" />
    <!-- optional, tiles 1.6+: group multiple services representing the same tile -->
    <meta-data android:name="androidx.wear.tiles.GROUP" android:value="my_group" />
</service>
```

Permission: **`com.google.android.wearable.permission.BIND_TILE_PROVIDER`**.

### CURRENT vs DEPRECATED summary

| Item | Status |
|---|---|
| `Material3TileService` + `suspend MaterialScope.tileResponse(TileRequest): Tile` | **CURRENT** (1.6.0+, Mar 2026) |
| `TileService.onTileRequest(): ListenableFuture<Tile>` | CURRENT but lower level |
| `TileService.onTileResourcesRequest()` | CURRENT; **optional** with `ProtoLayoutScope` (1.6+) |
| `TileService.onResourcesRequest()` | **DEPRECATED** (renamed `onTileResourcesRequest`) |
| `TileService.onTileEnterEvent` / `onTileLeaveEvent` | **DEPRECATED** in 1.5.0; "won't work from SDK 36+ for apps targeting API 36+". Use `onRecentInteractionEvents(List<TileInteractionEvent>)` / `onRecentInteractionEventsAsync(List)` |
| `TileBuilders.Tile.Builder.setTimeline(androidx.wear.tiles.TimelineBuilders.Timeline)` | **DEPRECATED** → `setTileTimeline(protolayout Timeline)` |
| `TileBuilders.Tile.Builder.setResources(Resources)` | **REMOVED** (never in 1.6.x) → `setResourcesVersion(String)` |
| `androidx.wear.tiles.material.*` (M2 tile material) | superseded by `androidx.wear.protolayout.material3.*`; **use protolayout-material3** |
| `compileSdk` for tiles 1.6 | **35** (raised from 34 in 1.6.0) |

---

## 3. Rotary input + haptics

### Rotary: two APIs, use the right one

**(a) Raw events — `Modifier.onRotaryScrollEvent`** (Compose UI, `androidx.compose.ui.input.rotary`):

```kotlin
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent

Modifier
    .onRotaryScrollEvent { event ->
        // event.verticalScrollPixels : Float   (already scaled; sign is "scroll down = negative")
        // event.horizontalScrollPixels : Float
        // event.uptimeMillis : Long
        volumeViewModel.onVolumeChangeByScroll(event.verticalScrollPixels)
        true                                   // consume
    }
    .focusRequester(focusRequester)
    .focusable()                               // events only arrive when focused
```

**(b) Wear-correct scrolling — `Modifier.rotaryScrollable`** (compose-foundation):

Verified (`compose-foundation-1.7.1`, `rotary/RotaryScrollable.kt`):

```kotlin
package androidx.wear.compose.foundation.rotary

public fun Modifier.rotaryScrollable(
    behavior: RotaryScrollableBehavior,
    focusRequester: FocusRequester,
    reverseDirection: Boolean = false,
    overscrollEffect: OverscrollEffect? = null,
): Modifier

public object RotaryScrollableDefaults {
    public const val LowSnapSensitivity: Float = 0.4f
    public const val HighSnapSensitivity: Float = 0.8f

    @Composable
    public fun behavior(
        scrollableState: ScrollableState,
        flingBehavior: FlingBehavior? = ScrollableDefaults.flingBehavior(),
        hapticFeedbackEnabled: Boolean = true,
    ): RotaryScrollableBehavior
}

public interface RotaryScrollableBehavior {
    public suspend fun CoroutineScope.performScroll(...)   // do NOT implement directly
}
```

Manual wiring for a custom scrollable:

```kotlin
val state = rememberScalingLazyListState()
val focusRequester = remember { FocusRequester() }
LaunchedEffect(Unit) { focusRequester.requestFocus() }

ScalingLazyColumn(
    state = state,
    modifier = Modifier
        .fillMaxSize()
        .rotaryScrollable(
            behavior = RotaryScrollableDefaults.behavior(state),
            focusRequester = focusRequester,
        ),
) { /* items */ }
```

**Good news:** `ScalingLazyColumn`, `TransformingLazyColumn` and `Picker` already do this for you —
they take a `rotaryScrollableBehavior: RotaryScrollableBehavior? = RotaryScrollableDefaults.behavior(state)`
parameter and internally apply `Modifier.requestFocusOnHierarchyActive().rotaryScrollable(behavior, focusRequester)`.
So **you do not need `focusRequester.requestFocus()` for these three** — just put them in
`AppScaffold`/`ScreenScaffold` and don't steal focus. Confirmed in
`lazy/ScalingLazyColumn.kt` (lines ~621, ~692–695).

`ScalingLazyListState` and `TransformingLazyColumnState` both implement `ScrollableState`, so
`RotaryScrollableDefaults.behavior(state)` type-checks directly.

There is **no** `focusable()` requirement on `ScalingLazyColumn` itself (it uses
`requestFocusOnHierarchyActive()`); `focusable()` + `focusRequester.requestFocus()` is only needed
when you attach `onRotaryScrollEvent` to an arbitrary composable.

### Haptics — official 2025/2026 answer

From the official **Haptics / haptic feedback** guide
(`developer.android.com/develop/ui/views/haptics/haptic-feedback`), the recommended order is:

1. **`View.performHapticFeedback(HapticFeedbackConstants.*)` — RECOMMENDED.**
   *"This approach is action-oriented, has the widest support, and doesn't require the `VIBRATE`
   permission."* Honours `Settings.System.HAPTIC_FEEDBACK_ENABLED` and
   `View.hapticFeedbackEnabled` automatically.
2. `VibrationEffect.createPredefined(int)` + `Vibrator` — more flexible, **requires `VIBRATE`**.
3. `VibrationEffect.Builder` timeline composition — most flexible, **requires `VIBRATE`**.

The guide explicitly warns against `createOneShot` / `createWaveform` for ordinary UI feedback.

Recommended Compose code (works on Wear 3/4, no permission):

```kotlin
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView

@Composable
fun HapticButton(onClick: () -> Unit) {
    val view = LocalView.current
    Button(onClick = {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)  // or VIRTUAL_KEY / CLOCK_TICK
        onClick()
    }) { Text("Tap") }
}
```

Useful `android.view.HapticFeedbackConstants` values:

```text
VIRTUAL_KEY              // soft key press
VIRTUAL_KEY_RELEASE
CONFIRM                  // API 30+ — "action confirmed"
REJECT                   // API 30+
LONG_PRESS               // API 30+ recommended substitute for 23+
CLOCK_TICK               // API 21+ — picker/rotary tick, ideal for scaling lists
CONTEXT_CLICK
GESTURE_START / GESTURE_END          // API 30+
KEYBOARD_TAP
TEXT_HANDLE_MOVE
TOGGLE_ON / TOGGLE_OFF               // API 34+
SEGMENT_TICK / SEGMENT_FREQUENT_TICK // API 34+
CONFIRM  (also HapticFeedbackConstants.CONFIRM)
```

If you *must* use the Vibrator path:

```kotlin
// <uses-permission android:name="android.permission.VIBRATE" /> required
val vib = context.getSystemService(Vibrator::class.java)
vib?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))   // EFFECT_CLICK / EFFECT_TICK / EFFECT_DOUBLE_CLICK
```

**Corrections to the 2024 assumptions:**

| Assumed | Reality |
|---|---|
| `androidx.wear.compose.ui` haptics | ❌ No such artifact/package exists |
| `androidx.wear:wear` `WearableHaptics` / `HapticFeedbackConstants` | ❌ `androidx.wear:wear:1.4.0` contains **no haptics classes at all** (verified by listing `classes.jar`: 153 classes, none matching `aptic`) |
| `androidx.wear:wear-input` gives haptics | ❌ 1.2.0 only has `WearableButtons`, `WearableRemoteInputExtender` (19 classes) |
| `com.google.wear.input.WearHapticFeedbackConstants` | Exists but is **not** a dependency of the androidx wear libraries (internal to Wear Compose's own rotary haptics, `Haptics.kt`) — do not use it |
| `Vibrator` + `createPredefined` | ✅ Works, but **not** the recommendation, and needs `VIBRATE` |
| plain `View.performHapticFeedback` | ✅ **This is the official recommendation** |

Rotary haptics for lists are automatic: `RotaryScrollableDefaults.behavior(state, hapticFeedbackEnabled = true)`
is the default of `ScalingLazyColumn` / `TransformingLazyColumn` / `Picker`.

---

## 4. Material 3 for Wear

### Versions & history (this is the part 2024-era memory gets wrong)

- `androidx.wear.compose:compose-material3` **first shipped stable as 1.5.0 on 2025-08-27**
  ("First release of the Wear Compose Material 3 library … Material 3 Expressive").
  Before that there was no stable Wear M3 at all — only M2 (`compose-material`).
- All `androidx.wear.compose:compose-*` artifacts share one version line now
  (`1.7.x`), so `compose-foundation` and `compose-material3` versions are identical by design.
- **1.5.0 introduced**: `MaterialTheme` (M3), dynamic color, `AppScaffold`, `ScreenScaffold`,
  `HorizontalPagerScaffold`, `VerticalPagerScaffold`, `ScrollIndicator`, `TransformingLazyColumn`,
  `EdgeButton`, `ButtonGroup`, `AlertDialog`, `ConfirmationDialog`, `TimePicker`, `DatePicker`,
  `CircularProgressIndicator` (+segmented), `ArcProgressIndicator`, `LinearProgressIndicator`,
  `HorizontalPager`/`VerticalPager`, hierarchical focus groups.
- **1.6.0**: Wear Compose Navigation3 (`SwipeDismissableSceneStrategy`), `TransformingLazyColumn`
  `reverseLayout` + `TransformingLazyColumnDefaults.snapFlingBehavior` +
  `Modifier.minimumVerticalContentPadding`, `AlertDialog` overloads backed by `TransformingLazyColumn`,
  `CurvedTextStyle(warpOffset)`, `LocalAmbientModeManager`.
- **1.7.0**: one-handed gestures, `PinnableContainer` for `TransformingLazyColumn`,
  item slide-out animation, `firstLayoutItemProvider`, `SwipeToReveal` improvements,
  `@FrequentlyChangingValue` / `@RememberInComposition` lint annotations.

### Theme entry point

```kotlin
package androidx.wear.compose.material3

@Composable
public fun MaterialTheme(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    typography: Typography = MaterialTheme.typography,
    shapes: Shapes = MaterialTheme.shapes,
    motionScheme: MotionScheme = MaterialTheme.motionScheme,
    content: @Composable () -> Unit,
)
```

```kotlin
public class ColorScheme(
    val primary: Color = ColorTokens.Primary,
    val primaryDim: Color = ColorTokens.PrimaryDim,
    val primaryContainer: Color = ColorTokens.PrimaryContainer,
    val onPrimary: Color = ColorTokens.OnPrimary,
    val onPrimaryContainer: Color = ColorTokens.OnPrimaryContainer,
    val secondary: Color = ColorTokens.Secondary,
    val secondaryDim: Color = ColorTokens.SecondaryDim,
    val secondaryContainer: Color = ColorTokens.SecondaryContainer,
    val onSecondary: Color = ColorTokens.OnSecondary,
    val onSecondaryContainer: Color = ColorTokens.OnSecondaryContainer,
    val tertiary: Color = ColorTokens.Tertiary,
    val tertiaryDim: Color = ColorTokens.TertiaryDim,
    val tertiaryContainer: Color = ColorTokens.TertiaryContainer,
    val onTertiary: Color = ColorTokens.OnTertiary,
    val onTertiaryContainer: Color = ColorTokens.OnTertiaryContainer,
    val surfaceContainerLow: Color = ColorTokens.SurfaceContainerLow,
    val surfaceContainer: Color = ColorTokens.SurfaceContainer,
    val surfaceContainerHigh: Color = ColorTokens.SurfaceContainerHigh,
    val onSurface: Color = ColorTokens.OnSurface,
    val onSurfaceVariant: Color = ColorTokens.OnSurfaceVariant,
    val outline: Color = ColorTokens.Outline,
    val outlineVariant: Color = ColorTokens.OutlineVariant,
    val background: Color = ColorTokens.Background,
    val onBackground: Color = ColorTokens.OnBackground,
    val error: Color = ColorTokens.Error,
    val errorDim: Color = ColorTokens.ErrorDim,
    val errorContainer: Color = ColorTokens.ErrorContainer,
    val onError: Color = ColorTokens.OnError,
    val onErrorContainer: Color = ColorTokens.OnErrorContainer,
) { fun copy(...): ColorScheme }

// Dynamic colour (returns null when the device/OEM doesn't support it)
public fun dynamicColorScheme(context: Context): ColorScheme?
```

```kotlin
// Typography: constructor is INTERNAL — you cannot build a custom Typography in 1.7.1
public class Typography internal constructor(
    val arcLarge: CurvedTextStyle, val arcMedium: CurvedTextStyle, val arcSmall: CurvedTextStyle,
    val displayLarge: TextStyle, val displayMedium: TextStyle, val displaySmall: TextStyle,
    val titleLarge: TextStyle,  val titleMedium: TextStyle,  val titleSmall: TextStyle,
    val labelLarge: TextStyle,  val labelMedium: TextStyle,  val labelSmall: TextStyle,
    val bodyLarge: TextStyle,   val bodyMedium: TextStyle,   val bodySmall: TextStyle,
    val bodyExtraSmall: TextStyle,
    val numeralExtraLarge: TextStyle, val numeralLarge: TextStyle, val numeralMedium: TextStyle,
    val numeralSmall: TextStyle,      val numeralExtraSmall: TextStyle,
) { public fun copy(...): Typography }
```

Usage:

```kotlin
@Composable
fun WearApp() {
    val colorScheme = dynamicColorScheme(LocalContext.current) ?: ColorScheme()
    MaterialTheme(colorScheme = colorScheme) {   // androidx.wear.compose.material3.MaterialTheme
        AppScaffold { ... }
    }
}
```

### Scrolling list

Still in **`androidx.wear.compose.foundation`** (not material3):

```kotlin
package androidx.wear.compose.foundation.lazy

@Composable
public fun rememberScalingLazyListState(
    initialCenterItemIndex: Int = 1,
    initialCenterItemScrollOffset: Int = 0,
): ScalingLazyListState

public class ScalingLazyListState : ScrollableState

@Composable
public fun ScalingLazyColumn(
    modifier: Modifier = Modifier,
    state: ScalingLazyListState = rememberScalingLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        Arrangement.spacedBy(space = 4.dp, alignment = if (!reverseLayout) Alignment.Top else Alignment.Bottom),
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    scalingParams: ScalingParams = ScalingLazyColumnDefaults.scalingParams(),
    anchorType: ScalingLazyListAnchorType = ScalingLazyListAnchorType.ItemCenter,
    autoCentering: AutoCenteringParams? = AutoCenteringParams(),
    rotaryScrollableBehavior: RotaryScrollableBehavior? = RotaryScrollableDefaults.behavior(state),
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: ScalingLazyListScope.() -> Unit,
)

public object ScalingLazyColumnDefaults {
    @Composable public fun scalingParams(viewportVerticalOffsetResolver: (Constraints) -> Int = { (it.maxHeight / 20f).toInt() }, ...): ScalingParams
    @Composable public fun snapFlingBehavior(state: ScalingLazyListState): FlingBehavior
}
```

`ScalingLazyColumn` is **not deprecated** in 1.7.1 (the two `@Deprecated(level = HIDDEN)` entries are
binary-compat shims that Android Studio won't even show). `TransformingLazyColumn` is the newer,
M3-Expressive-flavoured list; both are fine. `ScalingLazyColumn` is *not* in material3.

`itemsIndexed` is on the **scope**, not on `ScalingLazyColumnDefaults`:

```kotlin
// androidx.wear.compose.foundation.lazy
public inline fun <T> ScalingLazyListScope.itemsIndexed(
    items: List<T>,
    /* keys/offsets */ ...,
    crossinline itemContent: @Composable ScalingLazyListItemScope.(index: Int, item: T) -> Unit,
)
public inline fun <T> TransformingLazyColumnScope.itemsIndexed(...)

ScalingLazyColumn(state = state) {
    itemsIndexed(items, key = { _, it -> it.id }) { _, item -> Chip(item.label, onClick = {}) }
}
```

### TimeText / scroll indicator

```kotlin
package androidx.wear.compose.material3

@Composable
public fun TimeText(
    modifier: Modifier = Modifier,
    curvedModifier: CurvedModifier = CurvedModifier,
    maxSweepAngle: Float = TimeTextDefaults.MaxSweepAngle,
    backgroundColor: Color = TimeTextDefaults.backgroundColor(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(timeFormat()),
    contentPadding: PaddingValues = TimeTextDefaults.ContentPadding,
    content: CurvedScope.(String) -> Unit = { time -> timeTextCurvedText(time) },
)

// ScrollIndicator — 4 overloads, one per state type
@Composable public fun ScrollIndicator(state: ScrollState,             modifier: Modifier = Modifier, colors: ScrollIndicatorColors = ScrollIndicatorDefaults.colors(), reverseDirection: Boolean = false, positionAnimationSpec: AnimationSpec<Float> = ...)
@Composable public fun ScrollIndicator(state: ScalingLazyListState,    modifier: Modifier = Modifier, colors: ScrollIndicatorColors = ScrollIndicatorDefaults.colors(), reverseDirection: Boolean = state.layoutInfo.reverseLayout, positionAnimationSpec: AnimationSpec<Float> = ...)
@Composable public fun ScrollIndicator(state: TransformingLazyColumnState, ...)
@Composable public fun ScrollIndicator(state: LazyListState, ...)
```

**There is no `PositionIndicator` in wear-compose material3 1.7.1** (that was the M2
`androidx.wear.compose.material.PositionIndicator`). Use `ScrollIndicator` (vertical) /
`HorizontalPageIndicator` / `VerticalPageIndicator` (pagers).

### Official padding / spacing constants (round watches)

```kotlin
// androidx.wear.compose.material3 — ScreenScaffoldDefaults / PaddingDefaults
val verticalContentPaddingPercentage   = 10f     // 10% of screen height
val horizontalContentPaddingPercentage = 5.2f    // 5.2% of screen width

fun verticalContentPadding(): Dp                       // ceil(screenHeightDp * 10%)
fun horizontalContentPadding(percentage: Float = 5.2f): Dp

object ScreenScaffoldDefaults {
    val EdgeButtonSpacing: Dp = 16.dp
    val EdgeButtonMinSpacing: Dp = EdgeButtonVerticalPadding
    val contentPadding: PaddingValues   // horizontal = horizontalContentPadding(), vertical = verticalContentPadding()
}
val edgePadding = 2.dp

// ScalingLazyColumn defaults (foundation)
contentPadding  = PaddingValues(horizontal = 10.dp)
verticalArrangement = Arrangement.spacedBy(space = 4.dp, ...)
```

So: **horizontal content padding = 10.dp for `ScalingLazyColumn`, or 5.2% of screen width via
`ScreenScaffoldDefaults.contentPadding`; list item spacing = 4.dp; vertical padding = 10% of height.**

### Renames that break 1.0–1.4-era code

| Old (M2 / 1.0–1.4) | Now (1.5+/1.6/1.7) |
|---|---|
| `androidx.wear.compose.material.MaterialTheme` | `androidx.wear.compose.material3.MaterialTheme` (different `ColorScheme`/`Typography`) |
| `androidx.wear.compose.material.Colors` | `androidx.wear.compose.material3.ColorScheme` |
| `PositionIndicator` | `ScrollIndicator` (and `HorizontalPageIndicator` / `VerticalPageIndicator`) |
| `SwipeToDismissBox(backgroundContent = {...}) { ... }` | `SwipeToDismissBox(onDismissed = { }, state = rememberSwipeToDismissBoxState()) { isBackground -> ... }` |
| `rememberSwipeToDismissBoxState(positionalThreshold = ...)` | `rememberSwipeToDismissBoxState(animationSpec = ..., confirmStateChange = ...)` — **no `positionalThreshold`** |
| `Scaffold` (M3 wear) | `AppScaffold` (app root) + `ScreenScaffold` (per screen) |
| `CurvedText` / `TimeText` with `textColor` | M3 `TimeText(contentPadding, timeSource, backgroundColor, curvedModifier)` + `timeTextStyle()` |
| `ChipDefaults.SmallIconSize` & friends | `CompactButtonDefaults.SmallIconSize` / `.ExtraSmallIconSize` / `.Height` / `.HorizontalPadding` / `.ContentPadding` / `.TapTargetPadding` / `.shape` (`@Deprecated` on the old ones in 1.7) |
| `ConnectionFailureIcon`→ | renamed **from** `NetworkErrorIcon`-style names → use `ConnectionFailureIcon` |
| `CircularProgressIndicator(..., targetProgress)` | new overload without the unused `targetProgress` (old one is `@Deprecated(HIDDEN)`) |
| `TimeText(...)` 1.0 params | M3 `TimeText(modifier, curvedModifier, maxSweepAngle, backgroundColor, timeSource, contentPadding, content)` |
| `SwipeToReveal(...)` | new overload adds `actionContentSpacing` (old is `@Deprecated(HIDDEN)`) |
| `TimePicker` | new overload adds `initialSelection` |
| `ButtonGroup` | new overload adds a `transformation` parameter |
| `androidx.wear.compose:compose-material3` "1.0.0-alpha" | no such line — stable started at **1.5.0** |

---

## 5. App scaffold for a 3–4 screen `when(screen)` app

### `AppScaffold` exists in material3 (verified)

```kotlin
package androidx.wear.compose.material3

@Composable
public fun AppScaffold(
    modifier: Modifier = Modifier,
    timeText: @Composable () -> Unit = { TimeText() },
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable BoxScope.() -> Unit,
)
```

### `ScreenScaffold` — current overloads (1.7.1)

```kotlin
@Composable public fun ScreenScaffold(
    scrollState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ScreenScaffoldDefaults.contentPadding,
    timeText: (@Composable () -> Unit)? = null,
    scrollIndicator: (@Composable BoxScope.() -> Unit)? = { ScrollIndicator(scrollState) },
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: @Composable BoxScope.(PaddingValues) -> Unit,
)
// identical overloads for: TransformingLazyColumnState, LazyListState, ScrollState
// plus:  ScreenScaffold(scrollState, edgeButton = {...}, ...)   for EdgeButton screens
// plus:  ScreenScaffold(scrollInfoProvider: ScrollInfoProvider, ...) and the fully-optional form
```

Note the **parameter is `scrollState`, not `scrollInfoProvider`,** for the list-state overloads, and
the content lambda **receives `PaddingValues`** which you must consume:

```kotlin
ScreenScaffold(scrollState = listState) { padding ->
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = padding,          // <-- consume it
    ) { ... }
}
```

### Full 4-screen skeleton, no nav library

```kotlin
package com.example.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.*

enum class Screen { Home, Detail, Settings, About }

@Composable
fun WearApp() {
    var screen by remember { mutableStateOf(Screen.Home) }
    val back: () -> Unit = {
        screen = when (screen) {
            Screen.Detail   -> Screen.Home
            Screen.Settings -> Screen.Home
            Screen.About    -> Screen.Settings
            Screen.Home     -> Screen.Home
        }
    }

    MaterialTheme {
        AppScaffold {
            when (screen) {
                Screen.Home -> HomeScreen(
                    onOpenDetail   = { screen = Screen.Detail },
                    onOpenSettings = { screen = Screen.Settings },
                )
                Screen.Detail -> DetailScreen(onBack = back)
                Screen.Settings -> SettingsScreen(onOpenAbout = { screen = Screen.About }, onBack = back)
                Screen.About -> AboutScreen(onBack = back)
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpenDetail: () -> Unit, onOpenSettings: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = padding,
            flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(listState),
        ) {
            item { ListHeader { Text("Wear Demo") } }
            item { Button(onClick = onOpenDetail)   { Text("Detail") } }
            item { Button(onClick = onOpenSettings) { Text("Settings") } }
        }
    }
}

// Swipe-to-dismiss "back" on a detail screen.
// No @OptIn needed: neither SwipeToDismissBox nor rememberSwipeToDismissBoxState is experimental
// in 1.7.1 (SwipeToDismissBoxState is @Stable with an internal @OptIn).
@Composable
private fun DetailScreen(onBack: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val dismissState: SwipeToDismissBoxState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        onDismissed = onBack,                    // invoked after the dismiss animation completes
        state = dismissState,
        userSwipeEnabled = true,
        backgroundKey = "detail",                // keys let remembered state move fg<->bg
        contentKey = "detail",
    ) { isBackground ->
        if (isBackground) {
            Box(Modifier.fillMaxSize()) { Text("Back") }
        } else {
            ScreenScaffold(scrollState = listState) { padding ->
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = padding,
                ) {
                    item { ListHeader { Text("Detail") } }
                    item { Button(onClick = onBack) { Text("Back") } }
                }
            }
        }
    }
}
```

### `SwipeToDismissBox` / `SwipeToDismissBoxState` — exact current shape

```kotlin
// androidx.wear.compose.material3 — TWO overloads
@Composable
public fun SwipeToDismissBox(
    state: SwipeToDismissBoxState,
    modifier: Modifier = Modifier,
    backgroundScrimColor: Color = MaterialTheme.colorScheme.background,
    contentScrimColor: Color = MaterialTheme.colorScheme.background,
    backgroundKey: Any = SwipeToDismissKeys.Background,
    contentKey: Any = SwipeToDismissKeys.Content,
    userSwipeEnabled: Boolean = true,
    content: @Composable BoxScope.(isBackground: Boolean) -> Unit,
)

@Composable
public fun SwipeToDismissBox(
    onDismissed: () -> Unit,                              // <- the "navigate on dismiss" overload
    modifier: Modifier = Modifier,
    state: SwipeToDismissBoxState = rememberSwipeToDismissBoxState(),
    backgroundScrimColor: Color = MaterialTheme.colorScheme.background,
    contentScrimColor: Color = MaterialTheme.colorScheme.background,
    backgroundKey: Any = SwipeToDismissKeys.Background,
    contentKey: Any = SwipeToDismissKeys.Content,
    userSwipeEnabled: Boolean = true,
    content: @Composable BoxScope.(isBackground: Boolean) -> Unit,
)

// androidx.wear.compose.foundation
@Stable
@OptIn(ExperimentalWearFoundationApi::class)
public class SwipeToDismissBoxState @RememberInComposition constructor(
    animationSpec: AnimationSpec<Float> = SwipeToDismissBoxDefaults.AnimationSpec,
    confirmStateChange: (SwipeToDismissValue) -> Boolean = { true },
) {
    public val currentValue: SwipeToDismissValue
    public val targetValue: SwipeToDismissValue
    public val offset: Float                 // Float.NaN until initialised
    public val isAnimationRunning: Boolean
    public fun requireOffset(): Float
}

@Composable
public fun rememberSwipeToDismissBoxState(
    animationSpec: AnimationSpec<Float> = SWIPE_TO_DISMISS_BOX_ANIMATION_SPEC,
    confirmStateChange: (SwipeToDismissValue) -> Boolean = { true },
): SwipeToDismissBoxState

public object SwipeToDismissBoxDefaults { val AnimationSpec: SpringSpec<Float>; ... }
public enum class SwipeToDismissValue { Default, Dismissed }
```

**What changed vs 2024 memory:**

- ❌ `backgroundContent = { ... }` slot — **gone**. The signature is a single `content` lambda with
  an `isBackground: Boolean` parameter.
- ❌ `positionalThreshold` parameter in `rememberSwipeToDismissBoxState` — **gone** (threshold is now
  an internal constant; use `confirmStateChange` to veto).
- ❌ `SwipeToDismissBoxState(initialValue = ...)` — gone; state ctor takes `animationSpec` +
  `confirmStateChange`.
- ✅ `onDismissed: () -> Unit` overload exists (that's the one to use for navigation).
- ✅ `backgroundKey` / `contentKey` exist so remembered state moves correctly between fg/bg.
- For gesture from the very screen edge, combine with `Modifier.edgeSwipeToDismiss(...)`
  (`androidx.wear.compose.foundation`).
- With Navigation 3, use `SwipeDismissableSceneStrategy` from
  `androidx.wear.compose:compose-navigation` (1.6.0+).

---

## 6. Quick reference: everything that differs from early-2024 assumptions

1. **Wear M3 didn't exist** in early 2024 as stable. `compose-material3` stable starts at **1.5.0**
   (Aug 2025). `androidx.wear.compose.material3.*` ≠ `androidx.wear.compose.material.*`.
2. **`compose-material3`/`compose-foundation` 1.7.x demand `compileSdk = 37` + AGP 9.1.0.** Use 1.6.2
   (compileSdk 35) if you're not there yet.
3. **`ComplicationText.plainText(...)` does not exist** on the androidx type. `PlainComplicationText.Builder(text).build()`.
4. **`contentDescription` is a constructor arg**, not a `setContentDescription` setter, on all
   complication data builders.
5. **`setText(...)` only exists on `RangedValueComplicationData.Builder`**; `ShortText`/`LongText`
   take `text` in the constructor.
6. **`SuspendingComplicationDataSourceService` has no `setComplicationData` helper** — you *return*
   `ComplicationData?` from the suspend override; the 2-arg `onComplicationRequest` is `final`.
7. **`TileBuilders.Tile.Builder.setResources(...)` was removed**; only `setResourcesVersion(String)`,
   and tiles 1.6 collects/injects resources from `ProtoLayoutScope` automatically.
8. **`setTimeline` → `setTileTimeline(protolayout Timeline)`**; deprecated but present.
9. **`Material3TileService`** (`suspend MaterialScope.tileResponse(TileRequest): Tile`) is the new
   recommended tile base class. There is **no** `SuspendTileService`.
10. **`TileUpdateRequester.requestUpdate(MyTileService::class.java)`** takes a `Class`, not a
    `ComponentName`, and there is no `requestUpdateAll()`.
11. **`androidx.wear.compose:compose-ui` does not exist**; no Wear-Compose-specific haptics API.
    Official haptics = `View.performHapticFeedback(HapticFeedbackConstants.*)`, **no VIBRATE permission**.
12. **`PositionIndicator` is gone** from M3 → `ScrollIndicator` / `HorizontalPageIndicator` /
    `VerticalPageIndicator`.
13. **`SwipeToDismissBox` lost `backgroundContent` and `positionalThreshold`**; `content` now takes
    `isBackground`, and there's an `onDismissed` overload.
14. **`Scaffold` → `AppScaffold` + `ScreenScaffold`**, and `ScreenScaffold`'s content lambda receives
    `PaddingValues` you must consume.
15. **Rotary**: `ScalingLazyColumn` / `TransformingLazyColumn` / `Picker` already wire
    `Modifier.rotaryScrollable` + `requestFocusOnHierarchyActive()` internally — don't hand-roll
    focus unless you use raw `Modifier.onRotaryScrollEvent`.
16. **`Typography`'s constructor is `internal`** in M3 — you cannot construct a custom one (only `copy`
    from the theme's instance is exposed).
17. Tiles `onTileEnterEvent`/`onTileLeaveEvent` **stopped working for API 36+ targets** (tiles 1.5.0)
    → `onRecentInteractionEvents(List<TileInteractionEvent>)`.

---

## Sources

- Complications data sources: <https://developer.android.com/training/wearables/complications/exposing-data.md.txt>
- `SuspendingComplicationDataSourceService` reference: <https://developer.android.com/reference/kotlin/androidx/wear/watchface/complications/datasource/SuspendingComplicationDataSourceService>
- Tiles guide: <https://developer.android.com/training/wearables/tiles>
- Wear Tiles release notes: <https://developer.android.com/jetpack/androidx/releases/wear-tiles>
- Wear Compose release notes: <https://developer.android.com/jetpack/androidx/releases/wear-compose>
- Rotary input (Compose for Wear OS): <https://developer.android.com/training/wearables/compose/rotary-input.md.txt>
- Rotary input (View-based): <https://developer.android.com/training/wearables/user-input/rotary-input.md.txt>
- Haptics / haptic feedback: <https://developer.android.com/develop/ui/views/haptics/haptic-feedback.md.txt>
- Versions: `https://dl.google.com/dl/android/maven2/<path>/maven-metadata.xml` (paths in §0)
- Signatures: published `-sources.jar` and `META-INF/com/android/build/gradle/aar-metadata.properties`
  inside each `*.aar` on Google Maven (`dl.google.com/dl/android/maven2/`)
