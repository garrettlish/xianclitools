package com.example.xiancli_tools.data.mijia

/**
 * 米家这一块用到的数据模型，以及「米家的叫法 → 界面上的叫法」的对照表。
 *
 * 名字与字段刻意贴近 mijiaAPI / 网页版，方便两边对照着改：
 * 同一台设备在 App 和网页上显示成什么样，靠的是同一套映射。
 */

data class MijiaHome(val id: String, val name: String)

/** 一台设备。`category` 是 `spec_type` 的第 4 段（light / plug / sensor …）。 */
data class MijiaDevice(
    val did: String,
    val name: String,
    val model: String,
    val specType: String,
    val homeId: String,
    val homeName: String,
    val room: String,
    val online: Boolean,
    val category: String
)

/** 枚举型属性的一个取值。`value` 是米家给的原值（数字或字符串）。 */
data class MijiaEnumValue(val value: Any, val label: String)

/**
 * 一条属性规格。
 *
 * `type` 只用五种：bool / int / uint / float / string；
 * `rw` 是 `r`、`w`、`rw` 三种组合；`range` 是 [min, max, step]。
 */
data class MijiaPropSpec(
    val name: String,
    val desc: String,
    val type: String,
    val rw: String,
    val range: List<Double>?,
    val values: List<MijiaEnumValue>,
    val siid: Int,
    val piid: Int
) {
    val readable: Boolean get() = rw.contains('r')
    val writable: Boolean get() = rw.contains('w')
    val isBool: Boolean get() = type == "bool"
    val isNumber: Boolean get() = type == "int" || type == "uint" || type == "float"

    /** 界面上的名字：常见属性用中文表，表里没有就用规格里的描述。 */
    val label: String get() = MijiaLabels.propLabel(name, desc)

    /** 副标题只在规格自带中文说明时给；英文原串挂在中文标签下面只是噪声。 */
    val hint: String
        get() {
            val text = desc.trim()
            if (text.isEmpty() || text == label) return ""
            return if (text.any { it in '\u4e00'..'\u9fa5' }) text else ""
        }
}

data class MijiaActionSpec(
    val name: String,
    val desc: String,
    val siid: Int,
    val aiid: Int
) {
    val label: String get() = MijiaLabels.actionLabel(name, desc)
}

/** 一个型号（specType）的完整规格。 */
data class MijiaSpec(
    val name: String,
    val properties: List<MijiaPropSpec>,
    val actions: List<MijiaActionSpec>
) {
    /**
     * 卡片上那个开关用的属性：只认可读可写的布尔。
     * 读不到状态就不放开关——放了也只能显示一个猜出来的状态。
     */
    val primary: MijiaPropSpec?
        get() {
            val usable = properties.filter { it.isBool && it.readable && it.writable }
            return usable.firstOrNull { it.name == "on" }
                ?: usable.firstOrNull { it.name == "power" }
                ?: usable.firstOrNull()
        }
}

/** 读回来的一条属性值。`code` 非 0 表示这台设备没给出值（离线、不可读等）。 */
data class MijiaPropValue(
    val did: String,
    val siid: Int,
    val piid: Int,
    val value: Any?,
    val code: Int
) {
    val ok: Boolean get() = code == 0
}

/** 扫码登录拿到的三样东西：二维码图片地址、长轮询地址、二维码里编的链接。 */
data class MijiaQrTicket(
    val imageUrl: String,
    val longPollUrl: String,
    val loginUrl: String,
    val tips: String
)

/** 接口层的异常。登录过期单独一类，界面要据此切回扫码页。 */
open class MijiaException(message: String) : Exception(message)

class MijiaAuthException(message: String) : MijiaException(message)

class MijiaApiException(val code: Int, message: String) : MijiaException(message)

class MijiaNetworkException(message: String, cause: Throwable? = null) : MijiaException(message) {
    init {
        if (cause != null) initCause(cause)
    }
}

/**
 * 词表：品类的中文名、常见属性的中文名、动作名，以及「品类 → 大类」的归类。
 *
 * 品类来自 miot-spec（`cooker` / `airer` / `vacuum` 各算一类，太细），
 * 所以分类视图按用法归成八类；归类只是默认值，每台设备都能在配置里单独改。
 */
object MijiaLabels {

    private val CATEGORY_LABELS = mapOf(
        "light" to "灯",
        "plug" to "插座",
        "outlet" to "插座",
        "switch" to "开关",
        "power-strip" to "插排",
        "air-conditioner" to "空调",
        "air-purifier" to "净化器",
        "fan" to "风扇",
        "heater" to "取暖器",
        "humidifier" to "加湿器",
        "dehumidifier" to "除湿机",
        "air-fresh" to "新风机",
        "sensor" to "传感器",
        "thermometer" to "温度计",
        "hygrometer" to "湿度计",
        "curtain" to "窗帘",
        "window-opener" to "开窗器",
        "lock" to "门锁",
        "camera" to "摄像头",
        "speaker" to "音箱",
        "robot-cleaner" to "扫地机",
        "vacuum" to "扫地机",
        "refrigerator" to "冰箱",
        "washer" to "洗衣机",
        "dryer" to "烘干机",
        "tv" to "电视",
        "television" to "电视",
        "projector" to "投影",
        "cooker" to "饭煲",
        "airer" to "晾衣架",
        "dishwasher" to "洗碗机",
        "water-heater" to "热水器",
        "doorbell" to "门铃",
        "gateway" to "网关",
        "router" to "路由器",
        "set-top-box" to "机顶盒"
    )

    private val PROP_LABELS = mapOf(
        "on" to "开关",
        "power" to "电源",
        "brightness" to "亮度",
        "color-temperature" to "色温",
        "mode" to "模式",
        "target-temperature" to "目标温度",
        "temperature" to "温度",
        "relative-humidity" to "湿度",
        "humidity" to "湿度",
        "fan-level" to "风速",
        "fan-speed" to "风速",
        "speed" to "风速",
        "swing" to "摆风",
        "vertical-swing" to "上下摆风",
        "horizontal-swing" to "左右摆风",
        "child-lock" to "童锁",
        "volume" to "音量",
        "mute" to "静音",
        "battery-level" to "电量",
        "illuminance" to "照度",
        "pm2.5-density" to "PM2.5",
        "co2-density" to "二氧化碳",
        "filter-life-level" to "滤芯剩余",
        "filter-used-time" to "滤芯已用",
        "water-level" to "水位",
        "led" to "指示灯",
        "night-light" to "夜灯",
        "delay-off" to "延时关闭",
        "status" to "状态",
        "fault" to "故障",
        "physical-controls-locked" to "物理按键锁",
        "target-humidity" to "目标湿度",
        "heating-level" to "加热档位",
        "motor-speed" to "电机转速",
        "battery-charge" to "充电状态"
    )

    private val ACTION_LABELS = mapOf(
        "toggle" to "切换",
        "on" to "开启",
        "off" to "关闭",
        "start" to "启动",
        "stop" to "停止",
        "pause" to "暂停",
        "resume" to "继续",
        "play" to "播放",
        "pause-play" to "播放 / 暂停"
    )

    /** 品类 → 大类。没列到的落进 other，页面上照样能改。 */
    private val KIND_OF = mapOf(
        "light" to "light",
        "switch" to "light",
        "plug" to "socket",
        "outlet" to "socket",
        "power-strip" to "socket",
        "cooker" to "appliance",
        "vacuum" to "appliance",
        "robot-cleaner" to "appliance",
        "airer" to "appliance",
        "fan" to "appliance",
        "air-purifier" to "appliance",
        "air-fresh" to "appliance",
        "humidifier" to "appliance",
        "dehumidifier" to "appliance",
        "heater" to "appliance",
        "air-conditioner" to "appliance",
        "washer" to "appliance",
        "dryer" to "appliance",
        "refrigerator" to "appliance",
        "dishwasher" to "appliance",
        "water-heater" to "appliance",
        "curtain" to "appliance",
        "window-opener" to "appliance",
        "television" to "tv",
        "tv" to "tv",
        "projector" to "tv",
        "speaker" to "tv",
        "set-top-box" to "tv",
        "sensor" to "climate",
        "thermometer" to "climate",
        "hygrometer" to "climate",
        "temperature-humidity-sensor" to "climate",
        "camera" to "security",
        "lock" to "security",
        "doorbell" to "security",
        "gateway" to "network",
        "router" to "network",
        "wifi-repeater" to "network"
    )

    /** 分类视图的八个格子，顺序就是页面上的顺序。 */
    val KINDS = listOf(
        MijiaKind("light", "灯类", "灯、灯带、墙壁开关"),
        MijiaKind("appliance", "家用电器", "饭煲、机器人、晾衣架、风扇"),
        MijiaKind("tv", "电视机", "电视、投影、音箱"),
        MijiaKind("socket", "插座", "插座、插排"),
        MijiaKind("climate", "环境感知", "温湿度计、传感器"),
        MijiaKind("security", "安防", "门锁、摄像头"),
        MijiaKind("network", "网络", "网关、路由器"),
        MijiaKind("other", "其他", "没归类的设备")
    )

    fun kindOf(category: String): String = KIND_OF[category] ?: "other"

    fun kindLabel(key: String): String =
        KINDS.firstOrNull { it.key == key }?.label ?: KINDS.last().label

    fun categoryLabel(category: String): String =
        CATEGORY_LABELS[category] ?: "设备"

    fun propLabel(name: String, desc: String): String =
        PROP_LABELS[name] ?: desc.ifBlank { name }

    fun actionLabel(name: String, desc: String): String {
        if (desc.any { it in '\u4e00'..'\u9fa5' }) return desc
        return ACTION_LABELS[name] ?: desc.ifBlank { name }
    }

    /** 属性值说成人话：布尔说开关，枚举查表，其余原样。 */
    fun formatValue(prop: MijiaPropSpec, value: Any?): String {
        if (value == null) return "—"
        if (prop.isBool) return if (value == true) "开" else "关"
        if (prop.values.isNotEmpty()) {
            val hit = prop.values.firstOrNull { it.value.toString() == value.toString() }
            return hit?.label ?: value.toString()
        }
        return value.toString()
    }
}

data class MijiaKind(val key: String, val label: String, val note: String)
