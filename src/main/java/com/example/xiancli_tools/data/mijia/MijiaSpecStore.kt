package com.example.xiancli_tools.data.mijia

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 设备规格：从 miot-spec.org 按 `spec_type` 拉一次，之后走磁盘缓存。
 *
 * 为什么要单独去这个站：米家的设备接口只给 `spec_type`（一串 URN），
 * 属性叫什么、取值范围多少、枚举有哪些，全在规格里。规格按型号共享，
 * 一个家庭几十台设备通常只有十几种型号，缓存一次就够用很久。
 */
class MijiaSpecStore(private val cacheDir: File) {

    private val memory = HashMap<String, MijiaSpec>()

    /** 读一台设备的规格。取不到时抛 [MijiaNetworkException]，界面据此显示「读不到规格」。 */
    fun load(specType: String): MijiaSpec {
        memory[specType]?.let { return it }
        val file = File(cacheDir, fileName(specType))
        if (file.exists()) {
            runCatching { parseCache(JSONObject(file.readText())) }
                .getOrNull()
                ?.let {
                    memory[specType] = it
                    return it
                }
        }

        val response = MijiaHttp.get(
            "$SPEC_BASE?type=${encode(specType)}",
            mapOf("User-Agent" to "mijiaAPI/$SPEC_VERSION", "Accept-Encoding" to "identity"),
            20_000
        )
        if (response.status != 200) throw MijiaNetworkException("取不到设备规格（HTTP ${response.status}）")
        val json = runCatching { JSONObject(response.body) }.getOrElse {
            throw MijiaNetworkException("设备规格格式不对")
        }
        val spec = MijiaSpecParser.parse(specType, json)
        memory[specType] = spec
        runCatching {
            cacheDir.mkdirs()
            val temp = File(cacheDir, "${file.name}.tmp")
            temp.writeText(toCacheJson(spec).toString())
            if (file.exists()) file.delete()
            temp.renameTo(file)
        }
        return spec
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun fileName(specType: String): String =
        specType.replace(':', '_').replace('/', '_') + ".json"

    private fun toCacheJson(spec: MijiaSpec): JSONObject {
        val properties = JSONArray()
        spec.properties.forEach { prop ->
            val values = JSONArray()
            prop.values.forEach { item ->
                values.put(JSONObject().put("value", item.value).put("label", item.label))
            }
            properties.put(
                JSONObject()
                    .put("name", prop.name)
                    .put("desc", prop.desc)
                    .put("type", prop.type)
                    .put("rw", prop.rw)
                    .put("siid", prop.siid)
                    .put("piid", prop.piid)
                    .put("range", prop.range?.let { JSONArray(it) })
                    .put("values", values)
            )
        }
        val actions = JSONArray()
        spec.actions.forEach { action ->
            actions.put(
                JSONObject()
                    .put("name", action.name)
                    .put("desc", action.desc)
                    .put("siid", action.siid)
                    .put("aiid", action.aiid)
            )
        }
        return JSONObject()
            .put("version", CACHE_VERSION)
            .put("name", spec.name)
            .put("properties", properties)
            .put("actions", actions)
    }

    private fun parseCache(json: JSONObject): MijiaSpec? {
        if (json.optInt("version", 0) != CACHE_VERSION) return null
        val properties = ArrayList<MijiaPropSpec>()
        val propArray = json.optJSONArray("properties") ?: JSONArray()
        for (index in 0 until propArray.length()) {
            val item = propArray.optJSONObject(index) ?: continue
            val values = ArrayList<MijiaEnumValue>()
            val valueArray = item.optJSONArray("values") ?: JSONArray()
            for (valueIndex in 0 until valueArray.length()) {
                val entry = valueArray.optJSONObject(valueIndex) ?: continue
                values.add(
                    MijiaEnumValue(
                        value = entry.opt("value") ?: "",
                        label = entry.optString("label")
                    )
                )
            }
            properties.add(
                MijiaPropSpec(
                    name = item.optString("name"),
                    desc = item.optString("desc"),
                    type = item.optString("type", "string"),
                    rw = item.optString("rw"),
                    range = item.optJSONArray("range")?.let { array ->
                        (0 until array.length()).map { array.optDouble(it) }
                    },
                    values = values,
                    siid = item.optInt("siid"),
                    piid = item.optInt("piid")
                )
            )
        }
        val actions = ArrayList<MijiaActionSpec>()
        val actionArray = json.optJSONArray("actions") ?: JSONArray()
        for (index in 0 until actionArray.length()) {
            val item = actionArray.optJSONObject(index) ?: continue
            actions.add(
                MijiaActionSpec(
                    name = item.optString("name"),
                    desc = item.optString("desc"),
                    siid = item.optInt("siid"),
                    aiid = item.optInt("aiid")
                )
            )
        }
        return MijiaSpec(json.optString("name"), properties, actions)
    }

    private companion object {
        const val SPEC_BASE = "https://miot-spec.org/miot-spec-v2/instance"
        const val SPEC_VERSION = "5.0.0"

        /** 缓存格式版本：解析规则变了就加一，旧缓存自动失效。 */
        const val CACHE_VERSION = 2
    }
}

/**
 * 把 miot-spec 的原始 JSON 折成「属性 / 动作」两张平表。
 *
 * 原始结构是「服务 → 属性」，同一台设备有多个服务（开关、亮度、色温各是一个服务），
 * 界面上不需要这个层级，所以拉平成一列，用 siid/piid 定位。
 */
object MijiaSpecParser {

    fun parse(specType: String, json: JSONObject): MijiaSpec {
        val services = json.optJSONArray("services")
            ?: throw MijiaNetworkException("设备规格里没有 services")
        val properties = ArrayList<MijiaPropSpec>()
        val actions = ArrayList<MijiaActionSpec>()

        for (index in 0 until services.length()) {
            val service = services.optJSONObject(index) ?: continue
            val type = service.optString("type")
            // 设备信息这一组是厂商 / 固件号之类的元数据，控制面板里没用。
            if (type.split(":").getOrNull(3) == "device-information") continue
            val siid = service.optInt("iid")

            val propArray = service.optJSONArray("properties") ?: JSONArray()
            for (propIndex in 0 until propArray.length()) {
                val prop = propArray.optJSONObject(propIndex) ?: continue
                val format = prop.optString("format")
                val propType = when {
                    format.startsWith("int") -> "int"
                    format.startsWith("uint") -> "uint"
                    else -> format
                }
                val access = prop.optJSONArray("access") ?: JSONArray()
                val accessNames = (0 until access.length()).map { access.optString(it) }
                val rw = buildString {
                    if ("read" in accessNames) append('r')
                    if ("write" in accessNames) append('w')
                }
                val values = ArrayList<MijiaEnumValue>()
                val valueList = prop.optJSONArray("value-list") ?: JSONArray()
                for (valueIndex in 0 until valueList.length()) {
                    val item = valueList.optJSONObject(valueIndex) ?: continue
                    val label = item.optString("comment").ifBlank {
                        item.optString("description").ifBlank { item.opt("value").toString() }
                    }
                    values.add(MijiaEnumValue(item.opt("value") ?: "", label))
                }
                properties.add(
                    MijiaPropSpec(
                        name = prop.optString("type").split(":").getOrNull(3).orEmpty(),
                        desc = describe(prop),
                        type = propType,
                        rw = rw,
                        range = prop.optJSONArray("value-range")?.let { array ->
                            (0 until array.length()).map { array.optDouble(it) }
                        },
                        values = values,
                        siid = siid,
                        piid = prop.optInt("iid")
                    )
                )
            }

            val actionArray = service.optJSONArray("actions") ?: JSONArray()
            for (actionIndex in 0 until actionArray.length()) {
                val action = actionArray.optJSONObject(actionIndex) ?: continue
                actions.add(
                    MijiaActionSpec(
                        name = action.optString("type").split(":").getOrNull(3).orEmpty(),
                        desc = describe(action),
                        siid = siid,
                        aiid = action.optInt("iid")
                    )
                )
            }
        }

        return MijiaSpec(
            name = json.optString("description").ifBlank { specType },
            properties = dedupeProps(properties),
            actions = dedupeActions(actions)
        )
    }

    /** `description / comment`：comment 是中文注释，没有就只留描述。 */
    private fun describe(item: JSONObject): String {
        val description = item.optString("description")
        val comment = item.optString("comment")
        return listOf(description, comment).filter { it.isNotBlank() }.joinToString(" / ")
    }

    /**
     * 同名属性加后缀区分。
     *
     * 米家里「开关」这类属性会在多个服务里重名（灯的开关、夜灯的开关），
     * 直接显示两个一模一样的行没法用；先按 siid 区分，还重名再按 piid。
     */
    private fun dedupeProps(props: List<MijiaPropSpec>): List<MijiaPropSpec> {
        val first = props.groupingBy { it.name }.eachCount()
        val step1 = props.map {
            if ((first[it.name] ?: 0) > 1) it.copy(name = "${it.name}-${it.siid}") else it
        }
        val second = step1.groupingBy { it.name }.eachCount()
        return step1.map {
            if ((second[it.name] ?: 0) > 1) it.copy(name = "${it.name}-${it.piid}") else it
        }
    }

    private fun dedupeActions(actions: List<MijiaActionSpec>): List<MijiaActionSpec> {
        val first = actions.groupingBy { it.name }.eachCount()
        val step1 = actions.map {
            if ((first[it.name] ?: 0) > 1) it.copy(name = "${it.name}-${it.siid}") else it
        }
        val second = step1.groupingBy { it.name }.eachCount()
        return step1.map {
            if ((second[it.name] ?: 0) > 1) it.copy(name = "${it.name}-${it.aiid}") else it
        }
    }
}
