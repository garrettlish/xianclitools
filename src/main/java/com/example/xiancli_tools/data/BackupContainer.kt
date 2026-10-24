package com.example.xiancli_tools.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份文件的容器：一个 zip，里面一个 `backup.json`。
 *
 * 为什么压缩：足迹点阵全是坐标数字，deflate 能压到原来的四分之一到五分之一，
 * 三年份的备份从 20MB 掉到 5MB 上下——微信、邮件、蓝牙都发得出去。
 * 压的是 zip 而不是别的格式，是因为它到处都能解开，出事时人自己也能看一眼。
 *
 * **裸 JSON 也照样能读**：老版本导出的 `.json` 备份、以及局域网直传的 HTTP 正文，
 * 都是没有包装的 JSON 文本。所以读取先看前四个字节是不是 zip 的魔数，不是就按 JSON 解，
 * 这样升级不破坏历史备份。
 */
object BackupContainer {

    const val ENTRY_NAME = "backup.json"

    /** zip 本地文件头：PK\003\004。空 zip 是 PK\005\006，这里不认，因为它本来就解不出东西。 */
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    private const val BUFFER_BYTES = 32 * 1024

    /** 写成 zip。调用方负责关 [out]。 */
    fun write(
        payload: BackupPayload,
        out: OutputStream,
        exportedAt: Long = System.currentTimeMillis()
    ) {
        ZipOutputStream(BufferedOutputStream(out, BUFFER_BYTES)).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_NAME))
            val writer = OutputStreamWriter(zip, Charsets.UTF_8)
            BackupCodec.encodeTo(payload, writer, exportedAt)
            // 不能 close：那样会把 zip 一起关掉，后面就写不出中央目录了。
            writer.flush()
            zip.closeEntry()
        }
    }

    /** 从字节流读：zip 或裸 JSON 都认。 */
    fun read(input: InputStream): BackupPayload {
        val buffered = BufferedInputStream(input, BUFFER_BYTES)
        val head = ByteArray(ZIP_MAGIC.size)
        buffered.mark(ZIP_MAGIC.size + 1)
        val read = readUpTo(buffered, head)
        buffered.reset()
        return if (read == ZIP_MAGIC.size && head.contentEquals(ZIP_MAGIC)) {
            readZip(buffered)
        } else {
            BackupCodec.decodeFrom(buffered)
        }
    }

    private fun readZip(input: InputStream): BackupPayload {
        ZipInputStream(BufferedInputStream(input, BUFFER_BYTES)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: throw BackupFormatException("压缩包里没有备份文件")
                if (!entry.isDirectory) {
                    return BackupCodec.decodeFrom(InputStreamReader(zip, Charsets.UTF_8))
                }
                zip.closeEntry()
            }
        }
    }

    /** `InputStream.readNBytes` 要 API 33，这里自己读，兼容 minSdk 24。 */
    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val count = input.read(buffer, filled, buffer.size - filled)
            if (count <= 0) break
            filled += count
        }
        return filled
    }
}
