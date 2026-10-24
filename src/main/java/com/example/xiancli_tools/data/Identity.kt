package com.example.xiancli_tools.data

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * 记录主键的生成。
 *
 * 以前主键直接取 `System.currentTimeMillis()`。单机够用，但多台设备之间会撞车：
 * 两台手机时钟一致时，同一毫秒各建一条记录会得到同一个 id，
 * 导入合并时被当成同一条，静默吞掉其中一条。
 *
 * 现在的 id 是 `毫秒时间戳 << 20 | 20 位随机数`：
 * - 高位仍是时间，按 id 排序近似按时间排序（历史数据也满足这一点，两者不冲突）；
 * - 低 20 位随机，两台设备同一毫秒撞 id 的概率降到约百万分之一；
 * - 41 + 20 = 61 位，仍在 Long 的正数范围内，不会溢出，也不需要换主键类型。
 *
 * 闹钟请求码是 `id % 100_000`，只取决于低位，换成大 id 不影响找回同一个闹钟。
 */
object Identity {

    private val random = SecureRandom()
    private val lastSeed = AtomicLong(0L)

    fun newId(): Long {
        val millis = System.currentTimeMillis()
        // 同一毫秒内连续调用也要错开，否则单机自己就会撞。
        val seed = lastSeed.updateAndGet { previous -> maxOf(millis, previous + 1L) }
        val noise = random.nextInt(1 shl 20).toLong()
        return (seed shl 20) or noise
    }
}
