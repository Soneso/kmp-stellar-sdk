package com.soneso.stellar.sdk.unitTests.sep.sep01

import com.soneso.stellar.sdk.sep.sep01.TomlParser
import kotlin.test.Test
import kotlin.test.assertEquals

class TomlParserTest {
    @Test
    fun testBasicStringEscapes() {
        val input = "v = \"quote: \\\" slash: \\\\ controls: \\b\\t\\n\\f\\r unicode: \\u00E9 \\U0001D11E\""
        assertEquals(
            mapOf<String, Any?>(
                "v" to "quote: \" slash: \\ controls: \b\t\n\u000c\r unicode: \u00e9 \ud834\udd1e",
            ),
            TomlParser.parse(input)
        )
    }

    @Test
    fun testUnterminatedSingleLineStringStaysRawAndEndsAtLineBreak() {
        val input = "A = \"abc\\\nB = \"x\""
        assertEquals(
            mapOf<String, Any?>("A" to "\"abc\\", "B" to "x"),
            TomlParser.parse(input)
        )
    }

    @Test
    fun testSingleLineStringsEndAtLineBreaks() {
        for (lineEnding in listOf("\n", "\r\n", "\r")) {
            for (prefix in listOf("'text", "\"text", "\"text\\")) {
                assertEquals(
                    listOf(prefix, "after"),
                    TomlParser.splitOutsideStrings("$prefix${lineEnding}after")
                )
            }
        }
    }

    @Test
    fun testLiteralString() {
        val input = "v = 'C:\\Users\\node\\path # [x], \"quoted\"'"
        assertEquals(mapOf<String, Any?>("v" to "C:\\Users\\node\\path # [x], \"quoted\""), TomlParser.parse(input))
    }

    @Test
    fun testMultilineBasicString() = assertMultilineBasicString("\n")

    private fun assertMultilineBasicString(lineEnding: String) {
        val input = listOf(
            "v = \"\"\"",
            "first",
            "  second\\  ",
            " \tjoined \\u00E9 with \"\"quotes\"\"",
            "\"\"\"",
            "four = \"\"\"\"quoted\"\"\"\"",
            "five = \"\"\"tail\"\"\"\"\"",
            "empty = \"\"\"\"\"\"",
        ).joinToString(lineEnding)
        assertEquals(
            mapOf<String, Any?>(
                "v" to "first\n  secondjoined \u00e9 with \"\"quotes\"\"\n",
                "four" to "\"quoted\"",
                "five" to "tail\"\"",
                "empty" to "",
            ),
            TomlParser.parse(input)
        )
    }

    @Test
    fun testMultilineLiteralString() = assertMultilineLiteralString("\n")

    private fun assertMultilineLiteralString(lineEnding: String) {
        val input = listOf(
            "v = '''",
            "first",
            "  don't change \\n or ''quotes''",
            "'''",
            "four = ''''quoted''''",
            "five = '''tail'''''",
            "empty = ''''''",
        ).joinToString(lineEnding)
        assertEquals(
            mapOf<String, Any?>(
                "v" to "first\n  don't change \\n or ''quotes''\n",
                "four" to "'quoted'",
                "five" to "tail''",
                "empty" to "",
            ),
            TomlParser.parse(input)
        )
    }

    @Test
    fun testInlineComments() = assertInlineComments("\n")

    @Test
    fun testCrLfLineEndings() {
        assertInlineComments("\r\n")
        assertMultilineBasicString("\r\n")
        assertMultilineLiteralString("\r\n")
    }

    @Test
    fun testCrLineEndings() = assertInlineComments("\r")

    @Test
    fun testMultilineBasicStringWithCrLineEndings() = assertMultilineBasicString("\r")

    @Test
    fun testMultilineLiteralStringWithCrLineEndings() = assertMultilineLiteralString("\r")

    private fun assertInlineComments(lineEnding: String) {
        val input = listOf(
            "# ignored \"\"\"",
            "text = \"# kept\" # removed",
            "literal = '# literal' # removed",
            "number = 42 # removed",
            "flag = true # removed",
            "ratio = 2.5 # removed",
            "array = [1, 2] # removed",
            "[DOCUMENTATION] # header",
            "ORG_NAME = \"kept\" # removed",
        ).joinToString(lineEnding)
        assertEquals(
            mapOf<String, Any?>(
                "text" to "# kept",
                "literal" to "# literal",
                "number" to 42L,
                "flag" to true,
                "ratio" to 2.5,
                "array" to listOf(1L, 2L),
                "DOCUMENTATION" to mapOf<String, Any?>("ORG_NAME" to "kept"),
            ),
            TomlParser.parse(input)
        )
    }

    @Test
    fun testMultilineArray() {
        val input = listOf(
            "A = [",
            " 1, # first",
            " 2,",
            " 3, # last",
            "] # end",
            "after = \"kept\"",
        ).joinToString("\n")
        assertEquals(mapOf<String, Any?>("A" to listOf<Any?>(1L, 2L, 3L), "after" to "kept"), TomlParser.parse(input))
    }

    @Test
    fun testNestedMultilineArray() {
        val input = listOf(
            "A = [",
            " [1],",
            " [2]",
            "]",
            "after = \"kept\"",
        ).joinToString("\n")
        val parsed = TomlParser.parse(input)
        assertEquals(listOf(listOf(1L), listOf(2L)), parsed["A"])
        assertEquals("kept", parsed["after"])
    }

    @Test
    fun testBracketsInsideArrayStrings() {
        val input = listOf(
            "A = [",
            " \"ends ]\",",
            " \"escaped \\\"quote, ]\",",
            " '[,#]',",
            " [3, 4]",
            "]",
            "after = \"kept\"",
        ).joinToString("\n")
        assertEquals(
            mapOf<String, Any?>(
                "A" to listOf<Any?>("ends ]", "escaped \"quote, ]", "[,#]", listOf<Any?>(3L, 4L)),
                "after" to "kept",
            ),
            TomlParser.parse(input)
        )
    }

    @Test
    fun testTablesAndArrayTables() {
        val input = listOf(
            "[DOCUMENTATION] # organization",
            "ORG_NAME = \"caf\\u00E9\"",
            "[[CURRENCIES]] # first",
            "code = 'USD'",
            "[[CURRENCIES]] # second",
            "code = \"EUR\"",
        ).joinToString("\n")
        assertEquals(
            mapOf<String, Any?>(
                "DOCUMENTATION" to mapOf<String, Any?>("ORG_NAME" to "caf\u00e9"),
                "CURRENCIES" to listOf<Any?>(mapOf<String, Any?>("code" to "USD"), mapOf<String, Any?>("code" to "EUR")),
            ),
            TomlParser.parse(input)
        )
    }
}
