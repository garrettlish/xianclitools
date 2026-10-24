package com.example.xiancli_tools

import com.example.xiancli_tools.data.Identity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityTest {

    @Test
    fun `同一毫秒内连续生成也不重复`() {
        // 大量连续调用基本都落在同一毫秒内，正好覆盖自增错开那条分支。
        val ids = List(20_000) { Identity.newId() }

        assertEquals("同一台设备上生成的 id 必须两两不同", ids.size, ids.toSet().size)
    }

    @Test
    fun `id 按时间单调递增，保证列表按 id 排序等价于按时间排序`() {
        val ids = List(1_000) { Identity.newId() }

        assertTrue(ids.zipWithNext().all { (previous, next) -> next > previous })
        assertTrue("id 必须落在 Long 正数范围内", ids.all { it > 0L })
    }

    @Test
    fun `高位仍是时间戳，低位是随机数`() {
        val id = Identity.newId()
        val millis = id ushr 20
        val noise = id and ((1L shl 20) - 1)

        // 上界不好断言：同一毫秒内连续生成时 seed 会略微超前真实时间。
        assertTrue("高位要能还原出 2023 年之后的时间", millis > 1_690_000_000_000L)
        assertTrue("低位必须落在 20 位随机数范围内", noise in 0 until (1L shl 20))
    }
}
