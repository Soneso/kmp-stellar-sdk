// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep01

/**
 * Simple TOML parser specifically designed for stellar.toml files.
 * Supports basic TOML features needed for SEP-0001.
 */
internal object TomlParser {

    fun parse(content: String): Map<String, Any?> {
        val lines = splitOutsideStrings(content)
        val result = mutableMapOf<String, Any?>()
        var currentTable: String? = null
        var currentArrayTable: String? = null
        val arrayTables = mutableMapOf<String, MutableList<MutableMap<String, Any?>>>()
        var currentArrayTableData: MutableMap<String, Any?>? = null

        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                i++
                continue
            }

            when {
                // Array of tables [[TABLE]]
                trimmed.startsWith("[[") && trimmed.endsWith("]]") -> {
                    val tableName = trimmed.substring(2, trimmed.length - 2).trim()
                    currentArrayTable = tableName
                    currentTable = null

                    // Create new entry in array of tables
                    if (!arrayTables.containsKey(tableName)) {
                        arrayTables[tableName] = mutableListOf()
                    }
                    currentArrayTableData = mutableMapOf()
                    arrayTables[tableName]!!.add(currentArrayTableData!!)
                    i++
                }
                // Regular table [TABLE]
                trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                    val tableName = trimmed.substring(1, trimmed.length - 1).trim()
                    currentTable = tableName
                    currentArrayTable = null
                    currentArrayTableData = null
                    i++
                }
                // Key-value pair
                trimmed.contains("=") -> {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        var valueStr = parts[1].trim()

                        var depth = bracketBalance(valueStr)
                        if (valueStr.startsWith("[") && depth > 0) {
                            val arrayLines = mutableListOf(valueStr)
                            while (depth > 0 && i + 1 < lines.size) {
                                val nextLine = lines[++i]
                                arrayLines.add(nextLine)
                                depth += bracketBalance(nextLine)
                            }
                            valueStr = arrayLines.joinToString("\n").trim()
                        }

                        val value = parseValue(valueStr)

                        when {
                            currentArrayTableData != null -> {
                                // In array of tables
                                currentArrayTableData!![key] = value
                            }
                            currentTable != null -> {
                                // In regular table
                                val tableData = result[currentTable] as? MutableMap<String, Any?>
                                    ?: mutableMapOf<String, Any?>().also { result[currentTable!!] = it }
                                tableData[key] = value
                            }
                            else -> {
                                // Root level
                                result[key] = value
                            }
                        }
                    }
                    i++
                }
                else -> {
                    i++
                }
            }
        }

        // Add array tables to result
        arrayTables.forEach { (name, list) ->
            result[name] = list
        }

        return result
    }

    private fun parseValue(value: String): Any? {
        return when {
            // Strings: the closing quote is a separate character from the opening one.
            value.length >= 2 && (value.startsWith("\"") || value.startsWith("'")) &&
                value.last() == value.first() -> {
                val quote = value.first()
                val multiline = value.length >= 6 && value.startsWith(quote.toString().repeat(3)) &&
                    value.endsWith(quote.toString().repeat(3))
                val width = if (multiline) 3 else 1
                var text = value.substring(width, value.length - width)
                if (multiline) text = text.removePrefix("\n")
                if (quote == '\"') decodeBasicString(text, multiline) else text
            }
            // Array
            value.startsWith("[") && value.endsWith("]") -> {
                parseArray(value)
            }
            // Boolean
            value == "true" -> true
            value == "false" -> false
            // Integer
            value.toLongOrNull() != null -> value.toLong()
            // Double
            value.toDoubleOrNull() != null -> value.toDouble()
            // Default to string
            else -> value
        }
    }

    private fun parseArray(arrayStr: String): List<Any?> =
        splitOutsideStrings(arrayStr.substring(1, arrayStr.length - 1), ',', arrays = true)
            .map { it.trim() }.filter { it.isNotEmpty() }.map { parseValue(it) }

    /**
     * Splits [content] at [separator] outside strings, and outside nested brackets when [arrays] is set;
     * [stripComments] drops comments outside strings.
     */
    internal fun splitOutsideStrings(
        content: String,
        separator: Char = '\n',
        arrays: Boolean = false,
        stripComments: Boolean = true
    ): List<String> {
        val input = if (separator == '\n') content.replace("\r\n", "\n").replace('\r', '\n') else content
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = 0
        while (i < input.length) {
            val char = input[i]
            when {
                char == '"' || char == '\'' -> {
                    val end = stringEnd(input, i)
                    current.append(input.substring(i, end))
                    i = end
                }
                char == '#' -> {
                    val end = input.indexOf('\n', i).let { if (it < 0) input.length else it }
                    if (!stripComments) current.append(input.substring(i, end))
                    i = end
                }
                char == separator && depth == 0 -> {
                    parts.add(current.toString())
                    current.clear()
                    i++
                }
                else -> {
                    if (arrays && char == '[') depth++
                    if (arrays && char == ']') depth--
                    current.append(char)
                    i++
                }
            }
        }
        parts.add(current.toString())
        return parts
    }

    private fun stringEnd(content: String, start: Int): Int {
        val quote = content[start]
        val delimiter = quote.toString().repeat(if (content.startsWith(quote.toString().repeat(3), start)) 3 else 1)
        var i = start + delimiter.length
        while (i < content.length) {
            when {
                quote == '"' && content[i] == '\\' -> i = minOf(
                    i + if (delimiter.length == 1 && content.getOrNull(i + 1) == '\n') 1 else 2,
                    content.length
                )
                content.startsWith(delimiter, i) -> {
                    i += delimiter.length
                    if (delimiter.length == 3) while (i < content.length && content[i] == quote) i++
                    return i
                }
                delimiter.length == 1 && content[i] == '\n' -> return i
                else -> i++
            }
        }
        return i
    }

    private fun bracketBalance(content: String): Int {
        var balance = 0
        var i = 0
        while (i < content.length) {
            when (content[i]) {
                '"', '\'' -> {
                    i = stringEnd(content, i)
                    continue
                }
                '[' -> balance++
                ']' -> balance--
            }
            i++
        }
        return balance
    }

    private fun decodeBasicString(value: String, multiline: Boolean): String = buildString {
        var i = 0
        while (i < value.length) {
            if (value[i] != '\\') {
                append(value[i++])
                continue
            }
            val escaped = value.getOrNull(i + 1)
            if (multiline && escaped != null && escaped in " \t\r\n") {
                var end = i + 1
                while (end < value.length && value[end] in " \t\r\n") end++
                if ('\n' in value.substring(i + 1, end)) {
                    i = end
                    continue
                }
            }
            if (escaped == 'u' || escaped == 'U') {
                val end = i + 2 + if (escaped == 'u') 4 else 8
                val code = if (end <= value.length) value.substring(i + 2, end).toIntOrNull(16) else null
                if (code != null && code in 0..0x10FFFF && code !in 0xD800..0xDFFF) {
                    if (code <= 0xFFFF) append(code.toChar()) else {
                        append((0xD800 + ((code - 0x10000) shr 10)).toChar())
                        append((0xDC00 + ((code - 0x10000) and 0x3FF)).toChar())
                    }
                    i = end
                    continue
                }
            }
            val decoded = when (escaped) {
                'b' -> '\b'
                't' -> '\t'
                'n' -> '\n'
                'f' -> '\u000C'
                'r' -> '\r'
                '"' -> '"'
                '\\' -> '\\'
                else -> null
            }
            append(decoded ?: '\\')
            i += if (decoded == null) 1 else 2
        }
    }
}
