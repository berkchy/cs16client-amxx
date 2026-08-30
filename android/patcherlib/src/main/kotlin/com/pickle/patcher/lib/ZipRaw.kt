package com.pickle.patcher.lib

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Minimal, dependency-free zip reader tuned for exact byte-preserving repacking.
 *
 * It can read any entry's raw (compressed) bytes straight from the archive so that
 * preserved deflated entries are copied byte-for-byte without recompression.
 */
class ZipRaw private constructor(
    internal val file: RandomAccessFile,
    val entries: Map<String, ZipEntryInfo>,
    private var closed: Boolean = false,
) : AutoCloseable {

    /** Read the raw (as stored in the archive) compressed bytes of [entry]. */
    fun readRaw(entry: ZipEntryInfo): ByteArray {
        val buf = ByteArray(entry.compressedSize.toInt())
        file.seek(entry.dataOffset)
        file.readFully(buf)
        return buf
    }

    /** Read + inflate (handles stored and deflated). */
    fun readContent(entry: ZipEntryInfo): ByteArray {
        val raw = readRaw(entry)
        return if (entry.method == 0) raw else inflate(raw, entry.uncompressedSize.toInt())
    }

    override fun close() {
        if (!closed) {
            file.close()
            closed = true
        }
    }

    data class ZipEntryInfo(
        val name: String,
        val method: Int,               // 0 = stored, 8 = deflated
        val compressedSize: Long,
        val uncompressedSize: Long,
        val crc32: Long,
        val dataOffset: Long,
        val flags: Int,
    ) {
        val requiresAlignment: Boolean
            get() = name == "resources.arsc" || name.endsWith(".so") || name.endsWith(".dex") || name.endsWith(".png")
    }

    companion object {
        fun open(bytes: ByteArray): ZipRaw? {
            val tmp = Files.createTempFile("bundle", ".zip")
            try {
                Files.write(tmp, bytes)
                return open(tmp)
            } catch (e: IOException) {
                return null
            }
        }

        fun open(path: Path): ZipRaw? = open(path.toFile())

        fun open(file: java.io.File): ZipRaw? {
            if (!file.exists() || file.length() < 22) return null
            val raf = try { RandomAccessFile(file, "r") } catch (e: IOException) { return null }
            return try {
                fromRandom(raf)
            } catch (e: IOException) {
                try { raf.close() } catch (_: IOException) {}
                null
            }
        }

        fun fromRandom(raf: RandomAccessFile): ZipRaw {
            val len = raf.length()

            // ---- locate EOCD ----
            val maxRead = minOf(len, 65535L + 22)
            val eocdBuf = ByteArray(maxRead.toInt())
            raf.seek(len - eocdBuf.size)
            raf.readFully(eocdBuf)
            var eocdPos = -1
            for (i in eocdBuf.size - 22 downTo 0) {
                if (eocdBuf[i] == 0x50.toByte() && eocdBuf[i + 1] == 0x4b.toByte() &&
                    eocdBuf[i + 2] == 0x05.toByte() && eocdBuf[i + 3] == 0x06.toByte()
                ) {
                    eocdPos = i
                    break
                }
            }
            if (eocdPos < 0) throw IOException("EOCD not found")
            val eocd = ByteBuffer.wrap(java.util.Arrays.copyOfRange(eocdBuf, eocdPos, eocdPos + 22)).order(ByteOrder.LITTLE_ENDIAN)
            val cdOffset = eocd.getInt(16).toLong() and 0xffffffffL
            val cdSize = eocd.getInt(12).toLong() and 0xffffffffL
            val entryCount = eocd.getShort(10).toInt() and 0xffff

            // ---- central directory ----
            val cdBytes = ByteArray(cdSize.toInt())
            raf.seek(cdOffset)
            raf.readFully(cdBytes)
            val cb = ByteBuffer.wrap(cdBytes).order(ByteOrder.LITTLE_ENDIAN)

            val sorted = ArrayList<ZipEntryInfo>(entryCount)
            for (n in 0 until entryCount) {
                if (cb.remaining() < 46) throw IOException("CD truncated")
                val sig = cb.int
                if (sig != 0x02014b50) throw IOException("Bad CD signature @ entry $n")
                cb.position(cb.position() + 4)              // version made/by, version needed
                val flags = cb.short.toInt() and 0xffff
                val method = cb.short.toInt() and 0xffff
                cb.position(cb.position() + 2 + 2)          // mod time/date
                val crc = cb.int.toLong() and 0xffffffffL
                val compSize = cb.int.toLong() and 0xffffffffL
                val uncompSize = cb.int.toLong() and 0xffffffffL
                val nameLen = cb.short.toInt() and 0xffff
                val extraLen = cb.short.toInt() and 0xffff
                val commentLen = cb.short.toInt() and 0xffff
                cb.position(cb.position() + 2 + 2 + 4)      // disk#, int attrs, ext attrs
                val localOffset = cb.int.toLong() and 0xffffffffL

                val nameBytes = ByteArray(nameLen)
                cb.get(nameBytes)
                val name = String(nameBytes, Charsets.UTF_8)
                cb.position(cb.position() + extraLen + commentLen)

                val dataOffset = localDataOffset(raf, localOffset)
                sorted.add(
                    ZipEntryInfo(
                        name = name, method = method,
                        compressedSize = compSize, uncompressedSize = uncompSize,
                        crc32 = crc, dataOffset = dataOffset, flags = flags,
                    )
                )
            }

            val entries = LinkedHashMap<String, ZipEntryInfo>(sorted.size)
            for (e in sorted) entries[e.name] = e
            return ZipRaw(raf, entries)
        }

        private fun localDataOffset(raf: RandomAccessFile, localOffset: Long): Long {
            val hdr = ByteArray(30)
            raf.seek(localOffset)
            raf.readFully(hdr)
            val b = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            if (b.int != 0x04034b50) return -1
            val nameLen = b.getShort(26).toInt() and 0xffff
            val extraLen = b.getShort(28).toInt() and 0xffff
            return localOffset + 30 + nameLen + extraLen
        }

        private fun inflate(raw: ByteArray, expected: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream(expected)
            val inf = java.util.zip.InflaterInputStream(java.io.ByteArrayInputStream(raw), java.util.zip.Inflater(true))
            inf.copyTo(out)
            return out.toByteArray()
        }
    }
}