package com.thegadget.app.core

/**
 * A small, strict JSON reader/writer.
 *
 * The web app stores everything as JSON in `localStorage`; the native app stores the same
 * documents (plugins, profile, settings blobs). Using one dependency-free implementation for both
 * the app and its unit tests keeps the persisted format identical to the browser's and makes the
 * port testable on a plain JVM.
 */
sealed interface JsonValue {
    data class Obj(val entries: Map<String, JsonValue>) : JsonValue {
        operator fun get(key: String): JsonValue? = entries[key]
    }

    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

class JsonParseException(message: String) : Exception(message)

object Json {
    fun parse(text: String): JsonValue {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.eof()) throw JsonParseException("Unexpected trailing characters at ${p.pos}")
        return v
    }

    fun write(value: JsonValue, pretty: Boolean = false): String {
        val sb = StringBuilder()
        Writer(sb, pretty).write(value, 0)
        return sb.toString()
    }

    /* ------------------------------------------------------------------ reader */

    private class Parser(private val s: String) {
        var pos = 0
        fun eof() = pos >= s.length
        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): JsonValue {
            skipWs()
            if (eof()) throw JsonParseException("Unexpected end of input")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> JsonValue.Str(string())
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                else -> if (c == '-' || c.isDigit()) number() else throw JsonParseException("Unexpected character '$c' at $pos")
            }
        }

        private fun literal(word: String, value: JsonValue): JsonValue {
            if (!s.startsWith(word, pos)) throw JsonParseException("Invalid literal at $pos")
            pos += word.length
            return value
        }

        private fun obj(): JsonValue.Obj {
            expect('{')
            val map = LinkedHashMap<String, JsonValue>()
            skipWs()
            if (peek() == '}') {
                pos++
                return JsonValue.Obj(map)
            }
            while (true) {
                skipWs()
                val key = string()
                skipWs()
                expect(':')
                map[key] = value()
                skipWs()
                when (val c = peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return JsonValue.Obj(map)
                    }
                    else -> throw JsonParseException("Expected ',' or '}' at $pos but found '$c'")
                }
            }
        }

        private fun arr(): JsonValue.Arr {
            expect('[')
            val items = ArrayList<JsonValue>()
            skipWs()
            if (peek() == ']') {
                pos++
                return JsonValue.Arr(items)
            }
            while (true) {
                items.add(value())
                skipWs()
                when (val c = peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return JsonValue.Arr(items)
                    }
                    else -> throw JsonParseException("Expected ',' or ']' at $pos but found '$c'")
                }
            }
        }

        private fun peek(): Char = if (eof()) '\u0000' else s[pos]

        private fun expect(c: Char) {
            if (eof() || s[pos] != c) throw JsonParseException("Expected '$c' at $pos")
            pos++
        }

        private fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (eof()) throw JsonParseException("Unterminated string")
                val c = s[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (eof()) throw JsonParseException("Unterminated escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonParseException("Bad \\u escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonParseException("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): JsonValue.Num {
            val start = pos
            if (peek() == '-') pos++
            while (!eof() && s[pos].isDigit()) pos++
            if (peek() == '.') {
                pos++
                while (!eof() && s[pos].isDigit()) pos++
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++
                if (peek() == '+' || peek() == '-') pos++
                while (!eof() && s[pos].isDigit()) pos++
            }
            val text = s.substring(start, pos)
            val d = text.toDoubleOrNull() ?: throw JsonParseException("Invalid number '$text'")
            return JsonValue.Num(d)
        }
    }

    /* ------------------------------------------------------------------ writer */

    private class Writer(private val sb: StringBuilder, private val pretty: Boolean) {
        fun write(v: JsonValue, indent: Int) {
            when (v) {
                is JsonValue.Obj -> {
                    if (v.entries.isEmpty()) {
                        sb.append("{}")
                        return
                    }
                    sb.append('{')
                    var first = true
                    for ((k, value) in v.entries) {
                        if (!first) sb.append(',')
                        first = false
                        newline(indent + 1)
                        sb.append(quote(k))
                        sb.append(':')
                        if (pretty) sb.append(' ')
                        write(value, indent + 1)
                    }
                    newline(indent)
                    sb.append('}')
                }

                is JsonValue.Arr -> {
                    if (v.items.isEmpty()) {
                        sb.append("[]")
                        return
                    }
                    sb.append('[')
                    v.items.forEachIndexed { i, item ->
                        if (i > 0) sb.append(',')
                        newline(indent + 1)
                        write(item, indent + 1)
                    }
                    newline(indent)
                    sb.append(']')
                }

                is JsonValue.Str -> sb.append(quote(v.value))
                is JsonValue.Num -> sb.append(numberToString(v.value))
                is JsonValue.Bool -> sb.append(if (v.value) "true" else "false")
                JsonValue.Null -> sb.append("null")
            }
        }

        private fun newline(indent: Int) {
            if (!pretty) return
            sb.append('\n')
            repeat(indent) { sb.append("  ") }
        }

        private fun numberToString(d: Double): String =
            if (d == Math.floor(d) && !d.isInfinite() && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

        private fun quote(s: String): String {
            val out = StringBuilder(s.length + 2)
            out.append('"')
            for (c in s) {
                when (c) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    '\b' -> out.append("\\b")
                    '\u000C' -> out.append("\\f")
                    else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
                }
            }
            out.append('"')
            return out.toString()
        }
    }

    /* ------------------------------------------------------------- conveniences */

    fun obj(vararg pairs: Pair<String, JsonValue?>): JsonValue.Obj =
        JsonValue.Obj(pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap())

    fun str(s: String?): JsonValue = if (s == null) JsonValue.Null else JsonValue.Str(s)
    fun num(n: Number): JsonValue = JsonValue.Num(n.toDouble())
    fun bool(b: Boolean): JsonValue = JsonValue.Bool(b)
    fun arr(items: List<JsonValue>): JsonValue = JsonValue.Arr(items)
}
