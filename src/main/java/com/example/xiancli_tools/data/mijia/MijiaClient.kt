package com.example.xiancli_tools.data.mijia

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 米家云的客户端：扫码登录、家庭 / 设备、属性读写。
 *
 * 逻辑与 mijiaAPI 的 `apis.py` 对齐（那个库是 Python 的，App 里没法用），
 * 关键差异只有一处：这里的每个方法都是**阻塞**的，调用方负责放到 IO 线程上。
 *
 * 米家的接口全在 `api.mijia.tech/app` 后面，参数要 RC4 加密再签名（见 [MijiaCrypto]），
 * Cookie 里要带 serviceToken。登录态过期时接口不返回 401，而是返回一个非 0 的 code，
 * 所以这里按 code 判断，失败后自动尝试一次「用 passToken 换新 token」再重试。
 */
class MijiaClient(private val auth: MijiaAuth) {

    /* ------------------------------ 登录 ------------------------------ */

    /**
     * 要一张新的二维码。
     *
     * 返回 null 表示登录态其实还有效（passToken 换到了新 token），不用扫。
     * 这也是重新进页面时的正常路径：先试刷新，再考虑让人扫码。
     */
    fun requestQr(force: Boolean = false): MijiaQrTicket? {
        val location = serviceLocation()
        val code = location.optInt("code", -1)
        if (code == 0 && !force && refreshWith(location)) return null

        val query = LinkedHashMap<String, String>()
        location.keys().forEach { key ->
            val value = location.optString(key, "")
            // 这几个是响应里的状态字段，不是跳转参数，塞进二维码链接会被拒。
            if (key == "code" || key == "result" || key == "desc" || key == "description") return@forEach
            if (value.isNotEmpty()) query[key] = value
        }
        query["theme"] = ""
        query["bizDeviceType"] = ""
        query["_hasLogo"] = "false"
        query["_qrsize"] = "240"
        query["_dc"] = System.currentTimeMillis().toString()

        val url = LOGIN_URL + "?" + query.entries.joinToString("&") { (key, value) ->
            encode(key) + "=" + encode(value)
        }
        val response = MijiaHttp.get(url, loginHeaders(), 60_000)
        val json = parse(response.body, "取二维码失败（HTTP ${response.status}）")
        if (json.optInt("code", 0) != 0) {
            throw MijiaApiException(json.optInt("code"), json.optString("desc", "取二维码失败"))
        }
        val loginUrl = json.optString("loginUrl")
        val longPoll = json.optString("lp")
        if (loginUrl.isBlank() || longPoll.isBlank()) throw MijiaApiException(-1, "米家没给出二维码")
        return MijiaQrTicket(
            imageUrl = json.optString("qr"),
            longPollUrl = longPoll,
            loginUrl = loginUrl,
            tips = json.optString("qrTips", "用米家 App 扫码登录")
        )
    }

    /**
     * 长轮询等扫码，成功后把令牌写进 [auth]。
     *
     * 这个请求会被服务端挂住最多两分钟，所以超时给到 150 秒；
     * 返回的 `location` 还要再访问一次，serviceToken 是那一跳的 Cookie。
     */
    fun awaitLogin(ticket: MijiaQrTicket) {
        val headers = loginHeaders()
        val poll = MijiaHttp.get(ticket.longPollUrl, headers, 150_000)
        val json = parse(poll.body, "扫码登录失败（HTTP ${poll.status}）")
        if (json.optInt("code", 0) != 0) {
            throw MijiaApiException(json.optInt("code"), json.optString("desc", "扫码登录失败"))
        }

        listOf("psecurity", "nonce", "ssecurity", "passToken", "userId", "cUserId").forEach { key ->
            val value = json.optString(key, "")
            if (value.isNotEmpty()) auth.put(key, value)
        }
        auth.applyCookies(poll.cookies)

        val callback = json.optString("location")
        if (callback.isNotEmpty()) {
            val hop = MijiaHttp.get(callback, headers + ("Cookie" to auth.cookieHeader()), 30_000)
            auth.applyCookies(hop.cookies)
        }
        auth.save()
        homeEntries = null
    }

    /** 退出登录：只删本地令牌，米家那边没有「注销这台设备」的接口。 */
    fun logout() {
        auth.clear()
        homeEntries = null
    }

    /* ------------------------------ 家庭与设备 ------------------------------ */

    private var homeEntries: List<HomeEntry>? = null

    /** 一个家庭：id / 名字 / 所有者 uid，以及「设备 → 房间名」的索引。 */
    private data class HomeEntry(
        val home: MijiaHome,
        val uid: Long,
        val rooms: Map<String, String>
    )

    /**
     * 米家自己会把「默认家庭」「未分配房间」写成 `"0"`，直接显示就是「0 · 未分组」这种噪声。
     * 这类没有信息量的名字统一按空处理：家庭名空了界面就不带前缀，房间名空了就落到「未分组」。
     */
    private fun cleanName(raw: String?): String {
        val text = raw?.trim().orEmpty()
        return if (text == "0") "" else text
    }

    fun homes(refresh: Boolean = false): List<MijiaHome> = homeEntries(refresh).map { it.home }

    /** 家庭列表带所有者 uid：设备列表要用它，米家按它校验权限。 */
    private fun homeEntries(refresh: Boolean = false): List<HomeEntry> {
        if (!refresh) homeEntries?.let { return it }
        val result = callObject(
            "/v2/homeroom/gethome_merged",
            JSONObject()
                .put("fg", true)
                .put("fetch_share", true)
                .put("fetch_share_dev", true)
                .put("fetch_cariot", true)
                .put("limit", 300)
                .put("app_ver", 7)
                .put("plat_form", 0)
        )
        val list = result.optJSONArray("homelist") ?: JSONArray()
        val entries = buildList {
            for (index in 0 until list.length()) {
                val home = list.optJSONObject(index) ?: continue
                val id = home.optString("id")
                if (id.isBlank()) continue
                // 房间里的 did 列表是「这台设备在哪个房间」的唯一来源，设备接口本身不返回房间。
                val rooms = LinkedHashMap<String, String>()
                val roomList = home.optJSONArray("roomlist") ?: JSONArray()
                for (roomIndex in 0 until roomList.length()) {
                    val room = roomList.optJSONObject(roomIndex) ?: continue
                    val name = cleanName(room.optString("name"))
                    val dids = room.optJSONArray("dids") ?: JSONArray()
                    for (didIndex in 0 until dids.length()) {
                        val did = dids.optString(didIndex)
                        if (did.isNotBlank()) rooms[did] = name
                    }
                }
                add(HomeEntry(MijiaHome(id, cleanName(home.optString("name"))), home.optLong("uid"), rooms))
            }
        }
        homeEntries = entries
        return entries
    }

    /** 所有家庭的设备，按「家庭 / 房间 / 名字」排好序。 */
    fun devices(refresh: Boolean = false): List<MijiaDevice> {
        val out = ArrayList<MijiaDevice>()
        homeEntries(refresh).forEach { entry ->
            val items = try {
                devicesOf(entry.home.id, entry.uid)
            } catch (error: MijiaAuthException) {
                throw error
            } catch (error: MijiaException) {
                // 一个家庭读不到不该让整页空掉：共享家庭过期、被移出家庭都会这样。
                emptyList()
            }
            items.forEach { device ->
                out.add(
                    device.copy(
                        homeName = entry.home.name,
                        room = entry.rooms[device.did].orEmpty()
                    )
                )
            }
        }
        return out.sortedWith(compareBy({ it.homeName }, { it.room }, { it.name }))
    }

    private fun devicesOf(homeId: String, ownerUid: Long): List<MijiaDevice> {
        val out = ArrayList<MijiaDevice>()
        var startDid = ""
        var hasMore = true
        while (hasMore) {
            val result = callObject(
                "/home/home_device_list",
                JSONObject()
                    .put("home_owner", ownerUid)
                    .put("home_id", homeId.toLongOrNull() ?: 0L)
                    .put("limit", 200)
                    .put("start_did", startDid)
                    .put("get_split_device", true)
                    .put("support_smart_home", true)
                    .put("get_cariot_device", true)
                    .put("get_third_device", true)
            )
            val items = result.optJSONArray("device_info") ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val did = item.optString("did")
                if (did.isBlank()) continue
                val specType = item.optString("spec_type")
                out.add(
                    MijiaDevice(
                        did = did,
                        name = item.optString("name").ifBlank { "未命名设备" },
                        model = item.optString("model"),
                        specType = specType,
                        homeId = homeId,
                        homeName = "",
                        room = "",
                        online = item.optBoolean("isOnline", true),
                        category = categoryOf(specType, item.optString("model"))
                    )
                )
            }
            startDid = result.optString("max_did")
            hasMore = result.optBoolean("has_more", false) && startDid.isNotEmpty()
        }
        return out
    }

    /* ------------------------------ 属性与动作 ------------------------------ */

    /** 一次最多读 200 条：米家按这个上限截断，超了会静默少读。 */
    fun readProps(items: List<MijiaPropKey>): List<MijiaPropValue> {
        if (items.isEmpty()) return emptyList()
        val params = JSONArray()
        items.take(200).forEach { key ->
            params.put(
                JSONObject()
                    .put("did", key.did)
                    .put("siid", key.siid)
                    .put("piid", key.piid)
            )
        }
        val result = callArray(
            "/miotspec/prop/get",
            JSONObject().put("params", params).put("datasource", 1)
        )
        return buildList {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                add(
                    MijiaPropValue(
                        did = item.optString("did"),
                        siid = item.optInt("siid"),
                        piid = item.optInt("piid"),
                        value = item.opt("value"),
                        code = item.optInt("code", 0)
                    )
                )
            }
        }
    }

    /** 写一个属性。米家返回 1 表示「网关收下了但不确定设备执行没有」，按成功处理。 */
    fun writeProp(did: String, siid: Int, piid: Int, value: Any) {
        val result = callArray(
            "/miotspec/prop/set",
            JSONObject().put(
                "params",
                JSONArray().put(
                    JSONObject()
                        .put("did", did)
                        .put("siid", siid)
                        .put("piid", piid)
                        .put("value", value)
                )
            )
        )
        val first = result.optJSONObject(0)
        val code = first?.optInt("code", 0) ?: 0
        if (code != 0 && code != 1) {
            throw MijiaApiException(code, first?.optString("message").orEmpty().ifBlank { "设备没有接受这条指令" })
        }
    }

    fun runAction(did: String, siid: Int, aiid: Int) {
        val result = callObject(
            "/miotspec/action",
            JSONObject().put(
                "params",
                JSONObject().put("did", did).put("siid", siid).put("aiid", aiid)
            )
        )
        val code = result.optInt("code", 0)
        if (code != 0 && code != 1) {
            throw MijiaApiException(code, result.optString("message").ifBlank { "动作没执行成功" })
        }
    }

    /* ------------------------------ 底层请求 ------------------------------ */

    /**
     * 调一个接口。登录过期时先尝试换 token 再重试一次——
     * serviceToken 的有效期不固定，用户不该因为隔了几天就被要求重新扫码。
     */
    private fun call(uri: String, data: JSONObject): Any {
        if (!auth.hasSession) throw MijiaAuthException("还没有登录米家")
        return try {
            callOnce(uri, data)
        } catch (error: MijiaAuthException) {
            if (!refreshToken()) throw error
            callOnce(uri, data)
        }
    }

    private fun callObject(uri: String, data: JSONObject): JSONObject {
        val result = call(uri, data)
        return result as? JSONObject ?: JSONObject()
    }

    private fun callArray(uri: String, data: JSONObject): JSONArray {
        val result = call(uri, data)
        return result as? JSONArray ?: JSONArray()
    }

    private fun callOnce(uri: String, data: JSONObject): Any {
        val nonce = MijiaCrypto.nonce()
        val signedNonce = MijiaCrypto.signedNonce(auth.ssecurity, nonce)
        val body = MijiaCrypto.encryptParams(
            uri = uri,
            method = "POST",
            signedNonce = signedNonce,
            nonce = nonce,
            params = linkedMapOf("data" to data.toString()),
            ssecurity = auth.ssecurity
        )
        val response = MijiaHttp.postForm(API_BASE + uri, apiHeaders(), body, 60_000)
        if (response.status == 401 || response.status == 403) {
            throw MijiaAuthException("登录已过期，请重新扫码")
        }
        val text = response.body
        val json = try {
            JSONObject(text)
        } catch (error: Exception) {
            // 密文响应：整段 RC4，用这次请求的 nonce 解。
            JSONObject(MijiaCrypto.decrypt(auth.ssecurity, nonce, text))
        }
        val code = json.optInt("code", 0)
        if (code != 0 || !json.has("result")) {
            val message = json.optString("message").ifBlank { json.optString("desc") }
            if (isAuthCode(code)) throw MijiaAuthException(message.ifBlank { "登录已过期，请重新扫码" })
            throw MijiaApiException(code, message.ifBlank { "米家返回 $code" })
        }
        return json.opt("result") ?: JSONObject()
    }

    /** 换 token：拿 passToken 走一遍 serviceLogin，成功时服务端会下发新的 Cookie。 */
    private fun refreshToken(): Boolean {
        if (auth.get("passToken").isNullOrBlank()) return false
        return runCatching { refreshWith(serviceLocation()) }.getOrDefault(false)
    }

    private fun refreshWith(location: JSONObject): Boolean {
        if (location.optInt("code", -1) != 0) return false
        val target = location.optString("location")
        if (target.isBlank()) return false
        val hop = MijiaHttp.get(target, loginHeaders() + ("Cookie" to auth.cookieHeader()), 30_000)
        if (hop.status != 200 || hop.body.trim() != "ok") return false
        auth.applyCookies(hop.cookies)
        val ssecurity = location.optString("ssecurity")
        if (ssecurity.isNotBlank()) auth.ssecurity = ssecurity
        auth.save()
        homeEntries = null
        return true
    }

    /** 登录第一步：问服务端「这次登录要跳到哪」，返回的 location 里带着后续要用的参数。 */
    private fun serviceLocation(): JSONObject {
        val response = MijiaHttp.get(
            "$SERVICE_LOGIN?_json=true&sid=mijia&_locale=$LOCALE",
            loginHeaders() + ("Cookie" to auth.passportCookieHeader()),
            30_000
        )
        return parse(response.body, "连不上小米账号（HTTP ${response.status}）")
    }

    /** 小米账号的响应前面挂了一段 `&&&START&&&`，不去掉就不是合法 JSON。 */
    private fun parse(text: String, message: String): JSONObject {
        val body = text.substringAfter("&&&START&&&", text).trim()
        return runCatching { JSONObject(body) }.getOrElse {
            throw MijiaNetworkException(message)
        }
    }

    private fun loginHeaders(): Map<String, String> = mapOf(
        "User-Agent" to auth.userAgent,
        "Accept-Encoding" to "identity",
        "Content-Type" to "application/x-www-form-urlencoded",
        "Connection" to "keep-alive"
    )

    private fun apiHeaders(): Map<String, String> = mapOf(
        "User-Agent" to auth.userAgent,
        "Accept-Encoding" to "identity",
        "Content-Type" to "application/x-www-form-urlencoded",
        "miot-accept-encoding" to "GZIP",
        "miot-encrypt-algorithm" to "ENCRYPT-RC4",
        "x-xiaomi-protocal-flag-cli" to "PROTOCAL-HTTP2",
        "Cookie" to auth.cookieHeader()
    )

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun categoryOf(specType: String, model: String): String {
        val parts = specType.split(":")
        if (parts.size > 3 && parts[3].isNotBlank()) return parts[3]
        val modelParts = model.split(".")
        return modelParts.getOrNull(1).orEmpty()
    }

    private fun isAuthCode(code: Int): Boolean =
        code == 401 || code == 403 || code == -10005 || code == -10030 || code == -10020

    private companion object {
        const val API_BASE = "https://api.mijia.tech/app"
        const val LOGIN_URL = "https://account.xiaomi.com/longPolling/loginUrl"
        const val SERVICE_LOGIN = "https://account.xiaomi.com/pass/serviceLogin"
        const val LOCALE = "zh_CN"
    }
}

/** 一次属性读取的最小单位：设备 + 服务 + 属性。 */
data class MijiaPropKey(val did: String, val siid: Int, val piid: Int)
