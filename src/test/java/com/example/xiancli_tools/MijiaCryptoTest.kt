package com.example.xiancli_tools

import com.example.xiancli_tools.data.mijia.MijiaCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 米家的加密只能靠「和另一份实现对齐」来确认：参数错了不会报错，只会拿回一堆乱码。
 * 这里的期望值全部由 mijiaAPI 的 `miutils.py` 用同样的输入算出来，改实现后必须仍然相等。
 */
class MijiaCryptoTest {

    private val ssecurity = "kC8v1sYb2mQ0Y3g0dGZ0aA=="
    private val millis = 1_700_000_000_000L
    private val random = -1234567890123456789L

    @Test
    fun `nonce 是八字节随机数加分钟数`() {
        assertEquals("7t3vC4IWfusBsFUV", MijiaCrypto.nonce(millis, random))
    }

    @Test
    fun `签名用的 nonce 是 ssecurity 与 nonce 的哈希`() {
        val nonce = MijiaCrypto.nonce(millis, random)
        assertEquals(
            "uU1GcCNBWYFxC9xuuHbNQ+hLD1vy2pfkguyBiMrnqRk=",
            MijiaCrypto.signedNonce(ssecurity, nonce)
        )
    }

    @Test
    fun `签名按参数插入顺序拼接`() {
        val nonce = MijiaCrypto.nonce(millis, random)
        val signed = MijiaCrypto.signedNonce(ssecurity, nonce)
        val signature = MijiaCrypto.signature(
            uri = "/miotspec/prop/get",
            method = "POST",
            signedNonce = signed,
            params = linkedMapOf("data" to """{"params":[{"did":"1","siid":2,"piid":1}],"datasource":1}""")
        )
        assertEquals("2R86tLV2NMPjVQ9R76vyLwL8dF4=", signature)
    }

    @Test
    fun `RC4 先丢掉 1024 字节密钥流`() {
        val nonce = MijiaCrypto.nonce(millis, random)
        val signed = MijiaCrypto.signedNonce(ssecurity, nonce)
        val encrypted = MijiaCrypto.base64Encode(
            MijiaCrypto.rc4(
                MijiaCrypto.base64Decode(signed),
                """{"params":[],"datasource":1}""".toByteArray()
            )
        )
        assertEquals("OanAVMpxUqBQ9qZNi7BQibFL7C/RRCkaNAsVoQ==", encrypted)
    }

    @Test
    fun `加密参数会带上 rc4_hash 与二次签名`() {
        val nonce = MijiaCrypto.nonce(millis, random)
        val signed = MijiaCrypto.signedNonce(ssecurity, nonce)
        val params = MijiaCrypto.encryptParams(
            uri = "/miotspec/prop/get",
            method = "POST",
            signedNonce = signed,
            nonce = nonce,
            params = linkedMapOf("data" to "{}"),
            ssecurity = ssecurity
        )

        assertEquals(listOf("data", "rc4_hash__", "signature", "ssecurity", "_nonce"), params.keys.toList())
        assertEquals(ssecurity, params["ssecurity"])
        assertEquals(nonce, params["_nonce"])
        // 值都经过 RC4，不会还是明文 JSON
        assertTrue(params["data"] != "{}")
    }

    @Test
    fun `base64 编解码与原实现一致`() {
        assertEquals("aGVsbG8=", MijiaCrypto.base64Encode("hello".toByteArray()))
        assertEquals("aGVsbG8h", MijiaCrypto.base64Encode("hello!".toByteArray()))
        assertEquals("hello", String(MijiaCrypto.base64Decode("aGVsbG8=")))
        assertEquals("hello!", String(MijiaCrypto.base64Decode("aGVsbG8h")))
        assertEquals(
            ssecurity,
            MijiaCrypto.base64Encode(MijiaCrypto.base64Decode(ssecurity))
        )
    }

    @Test
    fun `密文能解回原文`() {
        val nonce = MijiaCrypto.nonce(millis, random)
        val signed = MijiaCrypto.signedNonce(ssecurity, nonce)
        val payload = """{"code":0,"result":{"homelist":[]}}"""
        val encrypted = MijiaCrypto.base64Encode(
            MijiaCrypto.rc4(MijiaCrypto.base64Decode(signed), payload.toByteArray())
        )
        assertEquals(payload, MijiaCrypto.decrypt(ssecurity, nonce, encrypted))
    }
}
