package com.example.xiancli_tools.data

import java.io.BufferedReader
import java.io.Reader

class JsonStreamException(message: String) : Exception(message)

/**
 * 极简流式 JSON 读取器：只负责「定位结构 + 取原文」，不构造对象。
 *
 * 为什么不用 `JSONObject(整段文本)`：足迹点阵动辄几十万个点，一次性建树会让峰值内存
 * 到文件体积的三倍左右（实测 20MB 的文件要 60MB 以上堆），几年份的备份在 256MB 堆的
 * 手机上直接 OOM。这里让调用方「一条一条读」，同一时刻只有一条记录在内存里。
 *
 * 写方向不走这里：逐条用 org.json 序列化再写进 `Writer` 即可，
 * 这样字段定义只有 [JsonCodec] 一处，不会出现两套字段。
 *
 * 用法（读顶层对象）：
 * ```
 * val stream = JsonStream(reader)
 * stream.beginObject()
 * while (true) {
 *     val key = stream.nextMember() ?: break
 *     when (key) {
 *         "notes" -> { stream.beginArray(); while (stream.nextElement()) stream.readRawValue() }
 *         else -> stream.skipValue()
 *     }
 * }
 * ```
 * 约定：一个成员的值读完（`readRawValue` / `skipValue` / `nextElement` 循环走完）之后
 * 才能再调 [nextMember]，否则会把值当成键来读。
 */
internal class JsonStream(reader: Reader) {

    private val source: Reader =
        if (reader is BufferedReader) reader else BufferedReader(reader, BUFFER_CHARS)

    /** 预读的一个字符；[NO_CHAR] 表示没有预读，[EOF] 表示到底了。 */
    private var peeked: Int = NO_CHAR

    private val containers = ArrayDeque<Frame>()

    private class Frame(val close: Char, var first: Boolean = true)

    fun beginObject() = begin(OPEN_OBJECT, CLOSE_OBJECT)

    fun beginArray() = begin(OPEN_ARRAY, CLOSE_ARRAY)

    private fun begin(open: Char, close: Char) {
        val c = nextNonSpace()
        if (c != open.code) throw JsonStreamException("期望「$open」，实际是${describe(c)}")
        read()
        containers.addLast(Frame(close))
    }

    /** 对象内取下一个键；读到 `}` 时返回 null。 */
    fun nextMember(): String? {
        val frame = top(CLOSE_OBJECT)
        val c = separator(frame, CLOSE_OBJECT) ?: return null
        if (c != QUOTE.code) throw JsonStreamException("期望键名，实际是${describe(c)}")
        val key = unquote(readRawString())
        val colon = nextNonSpace()
        if (colon != COLON.code) throw JsonStreamException("期望「:」，实际是${describe(colon)}")
        read()
        return key
    }

    /** 数组内判断还有没有下一个元素；遇到 `]` 返回 false。 */
    fun nextElement(): Boolean {
        val frame = top(CLOSE_ARRAY)
        return separator(frame, CLOSE_ARRAY) != null
    }

    /**
     * 吃掉「分隔符 + 下个元素的开头」，返回下一个元素的第一个字符；
     * 容器结束（遇到对应的 `]` / `}`）时返回 null。
     */
    private fun separator(frame: Frame, close: Char): Int? {
        var c = nextNonSpace()
        if (c == close.code) {
            read()
            containers.removeLast()
            return null
        }
        if (!frame.first) {
            if (c != COMMA.code) throw JsonStreamException("期望「,」，实际是${describe(c)}")
            read()
            c = nextNonSpace()
        }
        frame.first = false
        if (c == EOF) throw JsonStreamException("内容在这里就结束了")
        return c
    }

    private fun top(expectedClose: Char): Frame {
        val frame = containers.lastOrNull()
            ?: throw JsonStreamException("「$expectedClose」出现在最外层")
        if (frame.close != expectedClose) {
            throw JsonStreamException("这里应该是「${frame.close}」，不是「$expectedClose」")
        }
        return frame
    }

    /**
     * 把当前值原样读成一段文本。
     *
     * [skipFields] 里的字段只在**这个值的顶层**被整段跳过，原文里替换成 `null`。
     * 足迹摘要只需要骨架字段，用它把 `points` 整段丢掉——既不复制也不解析，
     * 这是「打开足迹页只读摘要」真正省下时间的地方。
     */
    fun readRawValue(skipFields: Set<String> = emptySet()): String {
        val out = StringBuilder()
        copyValue(out, skipFields)
        return out.toString()
    }

    /** 读一个字符串值（已去掉引号和转义）。 */
    fun readString(): String {
        val c = nextNonSpace()
        if (c != QUOTE.code) throw JsonStreamException("期望字符串，实际是${describe(c)}")
        return unquote(readRawString())
    }

    /** 读一个整数；带引号的数字也认（老文件里偶有字符串形式）。 */
    fun readLong(): Long {
        val c = nextNonSpace()
        val raw = if (c == QUOTE.code) unquote(readRawString()) else readScalarText()
        return raw.trim().toLongOrNull()
            ?: throw JsonStreamException("期望数字，实际是「$raw」")
    }

    /** 跳过当前值，不留文本。 */
    fun skipValue() {
        when (nextNonSpace()) {
            OPEN_OBJECT.code -> skipContainer(OPEN_OBJECT, CLOSE_OBJECT)
            OPEN_ARRAY.code -> skipContainer(OPEN_ARRAY, CLOSE_ARRAY)
            QUOTE.code -> skipString()
            else -> skipScalar()
        }
    }

    // ---- 原文复制 ----

    private fun copyValue(out: StringBuilder, skipFields: Set<String>) {
        when (nextNonSpace()) {
            OPEN_OBJECT.code -> copyObject(out, skipFields)
            OPEN_ARRAY.code -> copyArray(out)
            QUOTE.code -> copyString(out)
            else -> copyScalar(out)
        }
    }

    private fun copyObject(out: StringBuilder, skipFields: Set<String>) {
        read()
        out.append(OPEN_OBJECT)
        var first = true
        while (true) {
            var c = nextNonSpace()
            if (c == CLOSE_OBJECT.code) {
                read()
                out.append(CLOSE_OBJECT)
                return
            }
            if (!first) {
                if (c != COMMA.code) throw JsonStreamException("期望「,」，实际是${describe(c)}")
                read()
                out.append(COMMA)
                c = nextNonSpace()
            }
            if (c != QUOTE.code) throw JsonStreamException("期望键名，实际是${describe(c)}")
            val rawKey = readRawString()
            val key = unquote(rawKey)
            val colon = nextNonSpace()
            if (colon != COLON.code) throw JsonStreamException("期望「:」，实际是${describe(colon)}")
            read()
            out.append(rawKey).append(COLON)
            if (key in skipFields) {
                skipValue()
                out.append(NULL)
            } else {
                // 只在这个值的顶层跳过，往下一层不再管。
                copyValue(out, emptySet())
            }
            first = false
        }
    }

    private fun copyArray(out: StringBuilder) {
        read()
        out.append(OPEN_ARRAY)
        var first = true
        while (true) {
            var c = nextNonSpace()
            if (c == CLOSE_ARRAY.code) {
                read()
                out.append(CLOSE_ARRAY)
                return
            }
            if (!first) {
                if (c != COMMA.code) throw JsonStreamException("期望「,」，实际是${describe(c)}")
                read()
                out.append(COMMA)
                c = nextNonSpace()
            }
            if (c == EOF) throw JsonStreamException("内容在这里就结束了")
            copyValue(out, emptySet())
            first = false
        }
    }

    private fun copyString(out: StringBuilder) {
        read()
        out.append(QUOTE)
        while (true) {
            val c = read()
            if (c == EOF) throw JsonStreamException("字符串没有闭合")
            out.append(c.toChar())
            if (c == BACKSLASH.code) {
                val escaped = read()
                if (escaped == EOF) throw JsonStreamException("字符串没有闭合")
                out.append(escaped.toChar())
            } else if (c == QUOTE.code) {
                return
            }
        }
    }

    private fun copyScalar(out: StringBuilder) {
        while (true) {
            val c = peek()
            if (c == EOF || c.isDelimiter()) return
            out.append(read().toChar())
        }
    }

    // ---- 跳过 ----

    private fun skipContainer(open: Char, close: Char) {
        read()
        var depth = 1
        while (depth > 0) {
            val c = read()
            when {
                c == EOF -> throw JsonStreamException("内容在这里就结束了")
                c == QUOTE.code -> skipStringBody()
                c == open.code -> depth++
                c == close.code -> depth--
            }
        }
    }

    private fun skipString() {
        read()
        skipStringBody()
    }

    private fun skipStringBody() {
        while (true) {
            val c = read()
            when {
                c == EOF -> throw JsonStreamException("字符串没有闭合")
                c == BACKSLASH.code -> read()
                c == QUOTE.code -> return
            }
        }
    }

    private fun skipScalar() {
        while (true) {
            val c = peek()
            if (c == EOF || c.isDelimiter()) return
            read()
        }
    }

    // ---- 字符流 ----

    private fun peek(): Int {
        if (peeked == NO_CHAR) peeked = source.read()
        return peeked
    }

    private fun read(): Int {
        val c = if (peeked == NO_CHAR) source.read() else peeked
        peeked = NO_CHAR
        return c
    }

    /** 跳过空白，返回下一个字符（不消费）。 */
    private fun nextNonSpace(): Int {
        while (true) {
            val c = peek()
            if (c == EOF || !c.toChar().isWhitespace()) return c
            read()
        }
    }

    private fun readRawString(): String {
        val out = StringBuilder()
        copyString(out)
        return out.toString()
    }

    private fun readScalarText(): String {
        val out = StringBuilder()
        copyScalar(out)
        return out.toString()
    }

    private fun describe(c: Int): String = when (c) {
        EOF -> "文件结尾"
        NO_CHAR -> "空"
        else -> "「${c.toChar()}」"
    }

    private fun Int.isDelimiter(): Boolean = this == COMMA.code ||
        this == CLOSE_OBJECT.code ||
        this == CLOSE_ARRAY.code ||
        toChar().isWhitespace()

    /** 去掉引号并还原转义；键名和字符串值共用。 */
    private fun unquote(raw: String): String {
        if (raw.length < 2 || raw.first() != QUOTE || raw.last() != QUOTE) return raw
        val body = raw.substring(1, raw.length - 1)
        if (!body.contains(BACKSLASH)) return body
        val out = StringBuilder(body.length)
        var index = 0
        while (index < body.length) {
            val c = body[index]
            if (c != BACKSLASH || index == body.length - 1) {
                out.append(c)
                index++
                continue
            }
            index++
            when (val escaped = body[index]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'u' -> {
                    val end = minOf(index + 5, body.length)
                    val code = body.substring(index + 1, end).toIntOrNull(16)
                    if (code != null) {
                        out.append(code.toChar())
                        index += 4
                    } else {
                        out.append(escaped)
                    }
                }
                else -> out.append(escaped)
            }
            index++
        }
        return out.toString()
    }

    private companion object {
        const val BUFFER_CHARS = 8192

        const val NO_CHAR = -2
        const val EOF = -1

        const val QUOTE = '"'
        const val BACKSLASH = '\\'
        const val COMMA = ','
        const val COLON = ':'
        const val OPEN_OBJECT = '{'
        const val CLOSE_OBJECT = '}'
        const val OPEN_ARRAY = '['
        const val CLOSE_ARRAY = ']'
        const val NULL = "null"
    }
}
