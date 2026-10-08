package dev.me_cafebabe.wakeladder

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every translation must define exactly the keys and format placeholders of the default strings. */
class StringsParityTest {
    private val res = File("src/main/res")

    private fun strings(dir: String): Map<String, String> {
        val text = File(res, "$dir/strings.xml").readText()
        return Regex("""<string name="([a-z_0-9]+)">(.*?)</string>""").findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(s: String) =
        Regex("""%(\d+\$)?[0-9]*[ds]""").findAll(s).map { it.value.replace(Regex("""\d+(?=[ds])"""), "") }.sorted().toList()

    @Test
    fun translationsMatchDefault() {
        val base = strings("values")
        assertTrue(base.isNotEmpty())
        for (dir in listOf("values-zh-rCN", "values-zh-rTW")) {
            val t = strings(dir)
            assertEquals("$dir keys", base.keys, t.keys)
            for ((k, v) in base) {
                assertEquals("$dir/$k placeholders", placeholders(v), placeholders(t.getValue(k)))
            }
        }
    }

    @Test
    fun apostrophesAreEscaped() {
        for (dir in listOf("values", "values-zh-rCN", "values-zh-rTW")) {
            strings(dir).forEach { (k, v) -> assertTrue("$dir/$k", !Regex("""(?<!\\)'""").containsMatchIn(v)) }
        }
    }
}
