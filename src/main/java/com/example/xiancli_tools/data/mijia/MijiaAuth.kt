package com.example.xiancli_tools.data.mijia

import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.TimeZone

/**
 * 米家的登录态：一份存在应用私有目录里的 JSON，字段与 mijiaAPI 的 `auth.json` 同名。
 *
 * 里面全是等价于密码的东西（serviceToken / ssecurity / passToken），
 * 所以只写应用私有目录、不进备份包、不进局域网直传——换手机要重新扫码。
 *
 * 用「字符串字典」而不是一堆具名属性，是因为这些字段一半来自登录接口、
 * 一半来自 Cookie，米家以后加字段时不用改结构，原样存原样用。
 */
class MijiaAuth(private val file: File) {

    private val values: MutableMap<String, String> = LinkedHashMap()

    /** 文件里有没有一份能用的登录态；只读本地，不联网。 */
    val hasSession: Boolean
        get() = listOf("ssecurity", "userId", "cUserId", "serviceToken").all {
            values[it].orEmpty().isNotBlank()
        }

    var ssecurity: String
        get() = values["ssecurity"].orEmpty()
        set(value) {
            values["ssecurity"] = value
        }

    val userId: String
        get() = values["userId"].orEmpty()

    /**
     * 米家 App 的 UA。服务端按它认客户端，缺了会被当成爬虫；
     * 生成一次就存下来，每台设备一个固定身份，别每次请求都换。
     */
    val userAgent: String
        get() = values.getOrPut("ua") {
            val id1 = random("0123456789ABCDEF", 40)
            val id2 = random("0123456789ABCDEF", 32)
            val id3 = random("0123456789ABCDEF", 32)
            val id4 = random("0123456789ABCDEF", 40)
            "Android-15-11.0.701-Xiaomi-23046RP50C-OS2.0.212.0.VMYCNXM-$id1-" +
                "$COUNTRY-$id3-$id2-SmartHome-MI_APP_STORE-$id1|$id4|$passO-64"
        }

    private val deviceId: String
        get() = values.getOrPut("deviceId") {
            random("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-", 16)
        }

    private val passO: String
        get() = values.getOrPut("pass_o") { random("0123456789abcdef", 16) }

    init {
        if (file.exists()) {
            runCatching {
                val json = JSONObject(file.readText())
                json.keys().forEach { key ->
                    values[key] = json.optString(key, "")
                }
            }
        }
    }

    /** 调米家接口时带的 Cookie：登录态 + 时区 + 设备号。 */
    fun cookieHeader(now: Long = System.currentTimeMillis()): String {
        val zone = TimeZone.getDefault()
        val offsetMillis = zone.getOffset(now)
        val daylight = offsetMillis - zone.rawOffset
        val totalMinutes = offsetMillis / 60_000
        val sign = if (totalMinutes < 0) "-" else "+"
        val absolute = kotlin.math.abs(totalMinutes)
        val gmt = "GMT$sign${(absolute / 60).toString().padStart(2, '0')}:" +
            "${(absolute % 60).toString().padStart(2, '0')}"
        return buildString {
            append("cUserId=").append(values["cUserId"].orEmpty()).append(';')
            append("yetAnotherServiceToken=").append(values["serviceToken"].orEmpty()).append(';')
            append("serviceToken=").append(values["serviceToken"].orEmpty()).append(';')
            append("timezone_id=").append(zone.id).append(';')
            append("timezone=").append(gmt).append(';')
            append("is_daylight=").append(if (daylight != 0) 1 else 0).append(';')
            append("dst_offset=").append(daylight).append(';')
            append("channel=MI_APP_STORE;")
            append("countryCode=").append(COUNTRY).append(';')
            append("PassportDeviceId=").append(deviceId).append(';')
            append("locale=").append(LOCALE)
        }
    }

    /** 扫码登录第一步用的 Cookie：还没有 serviceToken，只有设备号和上次的 passport。 */
    fun passportCookieHeader(): String = buildString {
        append("deviceId=").append(deviceId).append(';')
        append("pass_o=").append(passO).append(';')
        append("passToken=").append(values["passToken"].orEmpty()).append(';')
        append("userId=").append(values["userId"].orEmpty()).append(';')
        append("cUserId=").append(values["cUserId"].orEmpty()).append(';')
        append("uLocale=").append(LOCALE)
    }

    /** 登录回调里下发的 Cookie 一并收进来：serviceToken 就在里面。 */
    fun applyCookies(cookies: Map<String, String>) {
        cookies.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) values[name] = value
        }
    }

    fun put(name: String, value: String) {
        values[name] = value
    }

    fun get(name: String): String? = values[name]

    /** 原子写：先写临时文件再改名，掉电时不会留下半份 JSON 把登录态弄坏。 */
    fun save() {
        val json = JSONObject()
        values.forEach { (key, value) -> json.put(key, value) }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.toString())
        if (file.exists()) file.delete()
        temp.renameTo(file)
    }

    fun clear() {
        values.clear()
        if (file.exists()) file.delete()
    }

    fun isEmpty(): Boolean = values.isEmpty()

    private fun random(alphabet: String, length: Int): String {
        val out = StringBuilder(length)
        repeat(length) { out.append(alphabet[secure.nextInt(alphabet.length)]) }
        return out.toString()
    }

    private companion object {
        const val LOCALE = "zh_CN"
        const val COUNTRY = "CN"
        val secure = SecureRandom()
    }
}
