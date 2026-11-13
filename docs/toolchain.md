# 构建与验证

本项目在**非 ASCII 路径**（`E:\新建文件夹\...`）下工作。AGP 会直接拒绝这种路径：

```
Your project path contains non-ASCII characters. This will most likely cause the build to fail on Windows.
```

因此本仓库约定：源码可以放在任意位置，但**构建必须通过一个 ASCII 路径访问同一个目录**。

## 一键构建

```powershell
# 1) 建一个指向仓库的 ASCII junction（只需一次；无需管理员权限）
cmd /c mklink /J C:\dev\apimeter "E:\新建文件夹\deepseek-harness\project"

# 2) 指向随仓库下载的 Android SDK（local.properties 里已写好，也可用 ANDROID_HOME 覆盖）
#    sdk.dir=C:\\dev\\apimeter\\tools\\android-sdk

# 3) 构建
$env:ANDROID_HOME = "C:\dev\apimeter\tools\android-sdk"
$env:JAVA_HOME    = "C:\Program Files\Zulu\zulu-17"
cd C:\dev\apimeter
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

产物：`app/build/outputs/apk/debug/app-debug.apk`
安装：`adb install -r app\build\outputs\apk\debug\app-debug.apk`

## 工具链版本（本仓库实测通过）

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | Zulu 17 | AGP 8.13 要求 JDK 17 |
| Gradle | 8.14.3 | wrapper 已生成，`gradlew.bat` 可直接用 |
| AGP | 8.13.0 | |
| Kotlin | 2.2.21 | 含 `kotlin.plugin.compose`、`kotlin.plugin.serialization` |
| compileSdk / targetSdk | 36 | |
| minSdk | 30 | Wear OS 3.0 起 |
| Compose BOM | 2025.12.01 | → compose-ui 1.10.0 |
| Compose for Wear OS | **1.6.2** | 见下方「版本陷阱」 |
| Wear Tiles | 1.6.2 | |
| ProtoLayout | 1.4.2 | |
| Complications data source (ktx) | 1.3.0 | |

`tools/gradle-8.14.3` 与 `tools/android-sdk`（含 build-tools 35）随仓库保留，方便离线复现；
不需要时可整目录删除，改用系统已装的 Gradle / Android Studio。

## 版本陷阱（踩过，别再踩）

1. **`androidx.wear.compose:1.7.x` 无法与 AGP 8.13 共存。**
   它的 `aar-metadata.properties` 声明 `minCompileSdk=37`、`minAndroidGradlePluginVersion=9.1.0`，
   构建会在 `checkDebugAarMetadata` 阶段直接失败。要么升到 AGP 9.1 + compileSdk 37，
   要么按本仓库固定在 **1.6.2**（`minCompileSdk=35`、AGP ≥ 8.6）。当前选择后者。
2. **`local.properties` 的编码。** Java 用 ISO-8859-1 读它，写中文路径会变成乱码。
   要么走 ASCII junction（推荐），要么把路径写成 `\uXXXX` 转义。
3. **Kotlin 增量编译会给出假错误。** 大范围改动后如果出现「一堆 extension 突然 Unresolved」，
   先 `clean` 再编译，不要顺着错误去改代码。
4. **表盘 Tile 预览**需要在 service 上加
   `<meta-data android:name="androidx.wear.tiles.PREVIEW" android:resource="@drawable/..."/>`。

## 已验证

```
> Task :app:compileDebugKotlin      BUILD SUCCESSFUL
> Task :app:testDebugUnitTest       75 tests, 0 failed
> Task :app:assembleDebug           app-debug.apk
> Task :app:assembleRelease         R8 通过
> Task :app:lintDebug               BUILD SUCCESSFUL
```

**真机形态验证**：在 `Wear_OS_Small_Round` 模拟器（API 37，384×384 / 320dpi，圆屏）上装包跑过，
逐页截图核对了首页 / 凭据管理 / 厂商选择 / 添加凭据 / 设置 / 网络代理，
并完整走通了「填 Key → 保存 → 回首页看到该凭据」的链路。

模拟器上发现的真实问题（已修）：

1. 单独用 `ScreenScaffold` 时顶部 `TimeText` 不显示 —— 必须在它外面套 `AppScaffold`。
2. 表单页用 `ScalingLazyColumn` 时，字段一多就只露出一部分；表单改用非惰性滚动
   （`WearFormScreen`）。
3. 添加凭据保存成功后 `pop()` 退回的是厂商选择页，用户会以为没成功 —— 改成直接回首页。
4. 首页空状态卡片文案过长，被圆屏下边缘裁切 —— 缩短文案、去掉单独占行的「同步于 …」。

## 自动化输入的坑

用 `adb shell input text` 往 **Compose 文本框**里打字时，Wear 上的 Gboard 只会把文本留在
它自己的 composing 条里，不会 `commitText` 给应用（`onValueChange` 一次都不触发）。
这**不是应用 bug**：把输入法禁掉（`adb shell ime disable <id>`）后同样的 `input text`
就能正常写入，说明应用侧的焦点与输入链路是通的。要在模拟器上做端到端输入测试，
先禁用输入法。

## 未验证

- 表盘 complication 与 Tile 的**实际渲染效果**：服务已正确注册（`aapt2 dump xmltree` 可核对
  permission / action / SUPPORTED_TYPES），系统也成功请求过 Tile 预览，
  但具体表盘上的排版、圆屏裁切、Tile 在小组件列表里的样子仍需在表盘编辑界面里手动确认。
- 六家厂商的真实接口联调（需要真实 Admin Key / AK-SK / SecretId-SecretKey）。
  目前只用无效 Key 验证了错误态能正确冒到界面上。
