package io.apimeter.wear.complication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守住「环形纯文本、无进度条」这个用户诉求：
 *
 * 三星 GradientInfoBoard 的边缘弧槽位对任意 RANGED_VALUE 数据都会画整段
 * 进度弧（实机 expE value=75 弧按比例分段、expF NoData 弧消失已证实），
 * 退化区间 min=max=value=0 消不掉弧。唯一可行的 app 侧解法是 7 个 service
 * **不再声明 RANGED_VALUE**，让系统对边缘槽改请求 SHORT_TEXT / LONG_TEXT。
 *
 * 若有人把 RANGED_VALUE 加回声明，进度条会立刻回来，此测试必须失败。
 * 这是行为级回归守卫，而不是抄代码输出的同义反复：它读的是真正被打包的
 * AndroidManifest.xml，改这里的常量不会让它通过。
 */
class ManifestTypeTest {

    private val manifest: String by lazy {
        // 单元测试工作目录是 app/，源码清单在 src/main/AndroidManifest.xml。
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("找不到 AndroidManifest.xml，已试：${candidates.joinToString { it.path }}")
        file.readText()
    }

    @Test
    fun complicationServices_neverDeclareRangedValue() {
        // 每个 complication service 的 SUPPORTED_TYPES 元数据值里不得出现 RANGED_VALUE。
        val metadataValues = Regex(
            """android:name="android\.support\.wearable\.complications\.SUPPORTED_TYPES"\s*""" +
                """android:value="([^"]+)"""",
        ).findAll(manifest).map { it.groupValues[1] }.toList()

        assertTrue("清单里应能解析出至少 7 个 SUPPORTED_TYPES 声明", metadataValues.size >= 7)
        metadataValues.forEach { value ->
            assertFalse(
                "SUPPORTED_TYPES 又声明了 RANGED_VALUE，环形槽会重新出现进度条：$value",
                value.split(",").map { it.trim() }.contains("RANGED_VALUE"),
            )
        }
    }

    @Test
    fun complicationServices_stillDeclareShortText() {
        // 撤销 RANGED_VALUE 不能把纯文字能力一起砍掉：边缘槽靠 SHORT_TEXT 显示数值。
        val metadataValues = Regex(
            """android:name="android\.support\.wearable\.complications\.SUPPORTED_TYPES"\s*""" +
                """android:value="([^"]+)"""",
        ).findAll(manifest).map { it.groupValues[1] }.toList()

        assertTrue("清单里应能解析出至少 7 个 SUPPORTED_TYPES 声明", metadataValues.size >= 7)
        metadataValues.forEach { value ->
            val types = value.split(",").map { it.trim() }
            assertTrue("SUPPORTED_TYPES 必须保留 SHORT_TEXT：$value", types.contains("SHORT_TEXT"))
            assertTrue("SUPPORTED_TYPES 必须保留 LONG_TEXT：$value", types.contains("LONG_TEXT"))
        }
    }
}
