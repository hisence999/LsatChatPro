package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownPatternFallbackTest {
    @Test
    fun fallbackStrongWithQuotes_shouldWorkWithoutRpRules() {
        val annotated = buildAnnotatedStringWithCustomPatternsForTest("这是 **“测试”** 文本")

        assertEquals("这是 “测试” 文本", annotated.text)

        val target = "“测试”"
        val start = annotated.text.indexOf(target)
        val end = start + target.length

        val strongSpan = annotated.spanStyles.firstOrNull { span ->
            span.start <= start &&
                span.end >= end &&
                span.item.fontWeight == FontWeight.Bold
        }

        assertNotNull(strongSpan)
    }

    @Test
    fun fallbackTripleAsterisk_shouldPreferCombinedStyle() {
        val annotated = buildAnnotatedStringWithCustomPatternsForTest("***hello***")

        assertEquals("hello", annotated.text)

        val span = annotated.spanStyles.firstOrNull()
        assertNotNull(span)
        assertEquals(FontWeight.Bold, span?.item?.fontWeight)
        assertEquals(FontStyle.Italic, span?.item?.fontStyle)
    }

    @Test
    fun fallback_shouldNotStyleWhitespaceWrappedAsterisk() {
        val annotated = buildAnnotatedStringWithCustomPatternsForTest("* hello *")

        assertEquals("* hello *", annotated.text)
        val styledSpan = annotated.spanStyles.firstOrNull {
            it.item.fontStyle == FontStyle.Italic || it.item.fontWeight == FontWeight.Bold
        }

        assertNull(styledSpan)
    }

    @Test
    fun parserPath_shouldBoldWhenSurroundedByCjkLettersWithoutSpaces() {
        val annotated = buildAnnotatedStringWithMarkdownParserForTest("前缀**文本**后缀")

        assertEquals("前缀文本后缀", annotated.text)

        val target = "文本"
        val start = annotated.text.indexOf(target)
        val end = start + target.length

        val strongSpan = annotated.spanStyles.firstOrNull { span ->
            span.start <= start &&
                span.end >= end &&
                span.item.fontWeight == FontWeight.Bold
        }

        assertNotNull(strongSpan)
    }

    @Test
    fun parserPath_shouldBoldWhenSurroundedByAsciiLettersWithoutSpaces() {
        val annotated = buildAnnotatedStringWithMarkdownParserForTest("pre**text**post")

        assertEquals("pretextpost", annotated.text)

        val target = "text"
        val start = annotated.text.indexOf(target)
        val end = start + target.length

        val strongSpan = annotated.spanStyles.firstOrNull { span ->
            span.start <= start &&
                span.end >= end &&
                span.item.fontWeight == FontWeight.Bold
        }

        assertNotNull(strongSpan)
    }
}
