package com.example.xiancli_tools.data.mijia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 米家工具对外的唯一入口：登录、设备、属性，加上本机显示配置。
 *
 * 这一层负责三件事：
 * 1. 把文件路径拼好（登录态、显示配置、规格缓存各存一处）；
 * 2. 把规格的并发拉取限制在 4 路——一次进来几十台设备，全并发会把 miot-spec 惹毛；
 * 3. 把客户端的阻塞调用挡住，界面只跟这个类打交道。
 *
 * 所有方法都是阻塞的，调用方用 `withContext(Dispatchers.IO)` 包起来。
 */
class MijiaRepository(context: Context) {

    private val appContext = context.applicationContext

    private val auth = MijiaAuth(File(appContext.filesDir, AUTH_FILE))
    private val client = MijiaClient(auth)
    private val specs = MijiaSpecStore(File(appContext.cacheDir, SPEC_DIR))
    private val config = MijiaConfigStore(File(appContext.filesDir, CONFIG_FILE))

    /* ------------------------------ 登录 ------------------------------ */

    fun isLoggedIn(): Boolean = auth.hasSession

    /** 要二维码；返回 null 表示登录态其实还有效，直接进设备页。 */
    fun requestQr(force: Boolean = false): MijiaQrTicket? = client.requestQr(force)

    /** 长轮询等扫码，成功后登录态就落盘了。 */
    fun awaitLogin(ticket: MijiaQrTicket) = client.awaitLogin(ticket)

    /** 把米家给的二维码图片地址下下来。地址本身就是 PNG，不需要本地生成二维码。 */
    fun qrBitmap(imageUrl: String): Bitmap? {
        if (imageUrl.isBlank()) return null
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", auth.userAgent)
        }
        return try {
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (error: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun logout() = client.logout()

    /* ------------------------------ 设备 ------------------------------ */

    fun devices(refresh: Boolean = false): List<MijiaDevice> = client.devices(refresh)

    /**
     * 批量取规格。同一个型号只取一次（家庭里几十台设备往往只有十几种型号），
     * 单个型号失败不影响别的设备——界面上那一台显示「读不到规格」即可。
     */
    fun specs(specTypes: Collection<String>): Map<String, MijiaSpec> {
        val unique = specTypes.filter { it.startsWith("urn:miot-spec") }.distinct().take(40)
        if (unique.isEmpty()) return emptyMap()
        val out = java.util.Collections.synchronizedMap(HashMap<String, MijiaSpec>())
        val pool = Executors.newFixedThreadPool(SPEC_WORKERS)
        try {
            val futures = unique.map { specType ->
                pool.submit {
                    runCatching { specs.load(specType) }.getOrNull()?.let { out[specType] = it }
                }
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdown()
        }
        return out.toMap()
    }

    /* ------------------------------ 属性与动作 ------------------------------ */

    fun readProps(keys: Collection<MijiaPropKey>): Map<MijiaPropKey, MijiaPropValue> {
        val items = keys.distinct()
        if (items.isEmpty()) return emptyMap()
        // 米家一次最多 200 条，多出来的分批送，别指望它自己截断后还给全。
        val out = LinkedHashMap<MijiaPropKey, MijiaPropValue>()
        items.chunked(200).forEach { chunk ->
            client.readProps(chunk).forEach { value ->
                out[MijiaPropKey(value.did, value.siid, value.piid)] = value
            }
        }
        return out
    }

    fun writeProp(did: String, siid: Int, piid: Int, value: Any) =
        client.writeProp(did, siid, piid, value)

    fun runAction(did: String, siid: Int, aiid: Int) = client.runAction(did, siid, aiid)

    /* ------------------------------ 显示配置 ------------------------------ */

    fun deviceConfig(): Map<String, MijiaDeviceConfig> = config.load()

    fun rename(did: String, name: String?) = config.rename(did, name)

    fun setRoom(did: String, room: String?) = config.setRoom(did, room)

    fun setKind(did: String, kind: String?) = config.setKind(did, kind)

    fun resetConfig(did: String) = config.reset(did)

    private companion object {
        const val AUTH_FILE = "mijia-auth.json"
        const val CONFIG_FILE = "mijia-config.json"
        const val SPEC_DIR = "mijia-spec"
        const val SPEC_WORKERS = 4
    }
}
