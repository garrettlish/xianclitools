package com.example.xiancli_tools.data.mijia

import org.json.JSONObject
import java.io.File

/** 一台设备在界面上「怎么显示」的本机覆盖。null 表示没改过，用米家的原值。 */
data class MijiaDeviceConfig(
    val name: String? = null,
    /** 空串是「未分组」，和「没改过」不是一回事，所以这里必须能区分 null 与 ""。 */
    val room: String? = null,
    val kind: String? = null
)

/**
 * 改名 / 改分组 / 改分类。只落在本机。
 *
 * 米家云端没有改名接口（`/home/device_name`、`/home/devicelist/upd` 这批候选全是 404），
 * 所以这些改动只影响这个 App 的显示，米家 App 里还是原来的名字。
 * 网页版存在 `mijia/config.json`，App 存在自己的私有目录，两边各存各的。
 */
class MijiaConfigStore(private val file: File) {

    private var cache: Map<String, MijiaDeviceConfig>? = null

    fun load(): Map<String, MijiaDeviceConfig> {
        cache?.let { return it }
        val loaded = runCatching {
            val json = JSONObject(file.readText())
            val devices = json.optJSONObject("devices") ?: JSONObject()
            val out = LinkedHashMap<String, MijiaDeviceConfig>()
            devices.keys().forEach { did ->
                val item = devices.optJSONObject(did) ?: return@forEach
                val entry = MijiaDeviceConfig(
                    name = item.optString("name").takeIf { it.isNotBlank() },
                    room = if (item.has("room")) item.optString("room") else null,
                    kind = item.optString("kind").takeIf { it.isNotBlank() }
                )
                if (entry != MijiaDeviceConfig()) out[did] = entry
            }
            out
        }.getOrDefault(emptyMap())
        cache = loaded
        return loaded
    }

    /** 名字留空就是恢复默认。 */
    fun rename(did: String, name: String?) = patch(did) {
        it.copy(name = name?.trim()?.takeIf { value -> value.isNotEmpty() })
    }

    /** `null` 恢复成米家的房间，`""` 表示「未分组」。 */
    fun setRoom(did: String, room: String?) = patch(did) { current ->
        val value = when {
            room == null -> null
            room.isBlank() -> ""
            else -> room.trim()
        }
        current.copy(room = value)
    }

    /** 分类留空就是恢复成按品类自动归类。 */
    fun setKind(did: String, kind: String?) = patch(did) {
        it.copy(kind = kind?.takeIf { value -> MijiaLabels.KINDS.any { item -> item.key == value } })
    }

    fun reset(did: String) = patch(did) { MijiaDeviceConfig() }

    private fun patch(did: String, change: (MijiaDeviceConfig) -> MijiaDeviceConfig) {
        val current = load()
        val next = LinkedHashMap(current)
        val updated = change(current[did] ?: MijiaDeviceConfig())
        if (updated == MijiaDeviceConfig()) next.remove(did) else next[did] = updated
        cache = next
        write(next)
    }

    private fun write(config: Map<String, MijiaDeviceConfig>) {
        val devices = JSONObject()
        config.forEach { (did, entry) ->
            val item = JSONObject()
            entry.name?.let { item.put("name", it) }
            entry.room?.let { item.put("room", it) }
            entry.kind?.let { item.put("kind", it) }
            if (item.length() > 0) devices.put(did, item)
        }
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(JSONObject().put("devices", devices).toString())
            if (file.exists()) file.delete()
            temp.renameTo(file)
        }
    }
}
