package com.example.xiancli_tools.data.mijia

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 米家接口的加密细节：nonce、ARC4、SHA1 签名。
 *
 * 与 mijiaAPI 的 `miutils.py` 一一对应，改这里之前先读那一份。
 * 这个文件刻意不碰 Android API（Base64 自己实现、不用 `android.util.Base64`），
 * 这样加密逻辑能在 JVM 单测里跑真值向量——米家这套协议错了不会报错，只会返回看不懂的乱码，
 * 只能靠对照另一份实现来确认。
 */
internal object MijiaCrypto {

    /**
     * 生成一次请求用的 nonce：8 字节随机数 + 「距纪元多少分钟」的最短字节表示，再 base64。
     *
     * 两段的含义由米家定死，不能省掉任何一段：服务端会拿它和 `_nonce` 对账。
     * 随机数用有符号 64 位，和 Python 的 `random.getrandbits(64) - 2**63` 同形。
     */
    fun nonce(millis: Long = System.currentTimeMillis(), random: Long = SecureRandom().nextLong()): String {
        val buffer = ByteArrayOutputStream(12)
        buffer.write(longBytes(random))
        val minutes = millis / 60_000L
        // Python 按 bit_length 取最短字节数，0 时是空数组；这里保持同样的写法。
        if (minutes != 0L) buffer.write(minimalBytes(minutes))
        return base64Encode(buffer.toByteArray())
    }

    /** `base64(sha256(base64decode(ssecurity) + base64decode(nonce)))`：后面所有请求的 RC4 密钥。 */
    fun signedNonce(ssecurity: String, nonce: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(base64Decode(ssecurity))
        digest.update(base64Decode(nonce))
        return base64Encode(digest.digest())
    }

    /**
     * 签名：`POST&/uri&k1=v1&k2=v2&signedNonce` 取 SHA1 再 base64。
     *
     * 参数顺序就是 Map 的插入顺序，**不能换成无序集合**：米家按收到的顺序重算签名，
     * 顺序变了就整片 401。
     */
    fun signature(
        uri: String,
        method: String,
        signedNonce: String,
        params: Map<String, String>
    ): String {
        val text = buildString {
            append(method.uppercase())
            append('&')
            append(uri)
            params.forEach { (key, value) ->
                append('&')
                append(key)
                append('=')
                append(value)
            }
            append('&')
            append(signedNonce)
        }
        return base64Encode(MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)))
    }

    /**
     * 把请求参数加密成最终的表单体：
     * 先算出 `rc4_hash__`，再把所有值 RC4 一遍，最后用密文再算一次 `signature`。
     */
    fun encryptParams(
        uri: String,
        method: String,
        signedNonce: String,
        nonce: String,
        params: Map<String, String>,
        ssecurity: String
    ): LinkedHashMap<String, String> {
        val key = base64Decode(signedNonce)

        val plain = LinkedHashMap<String, String>()
        plain.putAll(params)
        plain["rc4_hash__"] = signature(uri, method, signedNonce, plain)

        val encrypted = LinkedHashMap<String, String>()
        plain.forEach { (name, value) ->
            encrypted[name] = base64Encode(rc4(key, value.toByteArray(Charsets.UTF_8)))
        }
        encrypted["signature"] = signature(uri, method, signedNonce, encrypted)
        encrypted["ssecurity"] = ssecurity
        encrypted["_nonce"] = nonce
        return encrypted
    }

    /**
     * 解密响应：米家偶尔整段返回密文，且可能是 gzip 压过的。
     * 按 gzip 魔数判断，比「先按 UTF-8 解、失败再当 gzip」更直接。
     */
    fun decrypt(ssecurity: String, nonce: String, payload: String): String {
        val raw = rc4(base64Decode(signedNonce(ssecurity, nonce)), base64Decode(payload))
        val bytes = if (raw.size >= 2 && raw[0] == 0x1F.toByte() && raw[1] == 0x8B.toByte()) {
            java.util.zip.GZIPInputStream(raw.inputStream()).use { it.readBytes() }
        } else {
            raw
        }
        return String(bytes, Charsets.UTF_8)
    }

    /** ARC4：标准 KSA + PRGA，但先丢掉 1024 字节密钥流（米家固定这么用）。 */
    fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "RC4 密钥不能为空" }
        val box = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + box[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val swap = box[i]
            box[i] = box[j]
            box[j] = swap
        }

        var i = 0
        j = 0
        val next = {
            i = (i + 1) and 0xFF
            j = (j + box[i]) and 0xFF
            val swap = box[i]
            box[i] = box[j]
            box[j] = swap
            box[(box[i] + box[j]) and 0xFF]
        }
        repeat(1024) { next() }
        return ByteArray(data.size) { (data[it].toInt() xor next()).toByte() }
    }

    private fun longBytes(value: Long): ByteArray =
        ByteArray(8) { index -> (value ushr (56 - index * 8)).toByte() }

    /** 大端、去掉高位零字节；负数按 64 位补足（nonce 的分钟数不会是负数）。 */
    private fun minimalBytes(value: Long): ByteArray {
        var length = 8
        while (length > 1 && (value ushr ((length - 1) * 8)) == 0L) length--
        return ByteArray(length) { index -> (value ushr ((length - 1 - index) * 8)).toByte() }
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun base64Encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index + 2 < bytes.size) {
            val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
                ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                (bytes[index + 2].toInt() and 0xFF)
            out.append(ALPHABET[(chunk ushr 18) and 0x3F])
            out.append(ALPHABET[(chunk ushr 12) and 0x3F])
            out.append(ALPHABET[(chunk ushr 6) and 0x3F])
            out.append(ALPHABET[chunk and 0x3F])
            index += 3
        }
        when (bytes.size - index) {
            1 -> {
                val chunk = (bytes[index].toInt() and 0xFF) shl 16
                out.append(ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(ALPHABET[(chunk ushr 12) and 0x3F])
                out.append("==")
            }

            2 -> {
                val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
                    ((bytes[index + 1].toInt() and 0xFF) shl 8)
                out.append(ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(ALPHABET[(chunk ushr 12) and 0x3F])
                out.append(ALPHABET[(chunk ushr 6) and 0x3F])
                out.append('=')
            }
        }
        return out.toString()
    }

    fun base64Decode(text: String): ByteArray {
        val clean = text.trim().filterNot { it == '\n' || it == '\r' }
        val out = ByteArrayOutputStream(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        clean.forEach { char ->
            if (char == '=') return@forEach
            val value = ALPHABET.indexOf(char)
            require(value >= 0) { "不是合法的 base64：$char" }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
