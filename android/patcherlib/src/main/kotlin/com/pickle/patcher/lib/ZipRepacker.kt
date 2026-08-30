package com.pickle.patcher.lib

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Byte-preserving APK repacker.
 *
 * Copies every surviving entry from the source APK verbatim (stored entries as raw bytes,
 * deflated entries as their exact compressed stream), prunes entries matched by
 * [ExcludeRule], injects [Bundle] payload entries, and aligns every stored entry
 * (mirrors zipalign -f 4, with native libs aligned to 16 KB for
 * Android 15+ / 16 KB-page devices).
 *
 * Output is an unsigned but fully valid, aligned APK ready for apksig signing.
 */
object ZipRepacker {

    data class Result(
        val output: File,
        val kept: List<String>,
        val removed: List<String>,
        val added: List<String>,
        val entriesTotal: Int,
        val alignedStored: Int,
        val padBytes: Long,
        var bytesWritten: Long = 0,
    )

    fun repack(
        source: File,
        output: File,
        bundle: Bundle,
        exclude: ExcludeRule = ExcludeRule.DEFAULT,
        progress: ((Long, Long) -> Unit)? = null,
    ): Result {
        val src = ZipRaw.open(source) ?: throw IOException("Source APK could not be parsed")
        val srcLen = source.length()

        try {
            // ---- decide keep/remove/add ----
            val removed = ArrayList<String>()
            val keptEntries = ArrayList<ZipRaw.ZipEntryInfo>()
            for ((name, entry) in src.entries) {
                val excluded = excludable(name, exclude)
                if (excluded) removed.add(name) else keptEntries.add(entry)
            }

            // bundle targets shadow existing kept entries
            val bundleTargets = bundle.manifest.entries.map { it.target }.toHashSet()
            val iterator = keptEntries.iterator()
            while (iterator.hasNext()) {
                val e = iterator.next()
                if (e.name in bundleTargets) {
                    removed.add(e.name)
                    iterator.remove()
                }
            }

            val raf = RandomAccessFile(output, "rw")
            val cdEntries = ArrayList<CdRecord>()
            var alignPadBytes = 0L
            var alignedStored = 0
            var bytesWritten = 0L

            try {
                raf.setLength(0)

                fun writeLocalHeader(
                    name: String,
                    method: Int,
                    compressedSize: Long,
                    uncompressedSize: Long,
                    crc: Long,
                    data: ByteArray,
                ) {
                    var localOffset = raf.filePointer
                    var extraLen = 0
                    if (method == 0) {
                        // zipalign behaviour: native libs (lib/**) must be 16 KB
                        // aligned for Android 15+ / 16 KB-page devices; everything
                        // else just needs 4-byte alignment.
                        val unit = if (name.startsWith("lib/")) 16384 else 4
                        val base = localOffset + 30 + name.length
                        val rem = (base % unit).toInt()
                        extraLen = if (rem == 0) 0 else (unit - rem)
                        alignPadBytes += extraLen
                        alignedStored++
                    }

                    val hdr = ByteArray(30)
                    val b = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
                    b.putInt(0x04034b50)
                    b.putShort(20)                                   // version needed
                    b.putShort(0)                                    // flags (no data descriptor)
                    b.putShort(method.toShort())
                    b.putShort(0)                                    // mod time
                    b.putShort(0x0821)                               // mod date (2026-08-29)
                    b.putInt(crc.toInt())
                    b.putInt(compressedSize.toInt())
                    b.putInt(uncompressedSize.toInt())
                    b.putShort(name.length.toShort())
                    b.putShort(extraLen.toShort())
                    raf.write(hdr)
                    raf.write(name.toByteArray(Charsets.UTF_8))
                    if (extraLen > 0) raf.write(ByteArray(extraLen))

                    val dataOffset = raf.filePointer
                    raf.write(data)

                    cdEntries.add(
                        CdRecord(
                            name = name,
                            method = method,
                            compressedSize = compressedSize,
                            uncompressedSize = uncompressedSize,
                            crc = crc,
                            localOffset = localOffset,
                            dataOffset = dataOffset,
                        )
                    )
                    bytesWritten += 30 + name.length + extraLen + data.size
                }

                // copy preserved entries (raw bytes, method as-is)
                val total = keptEntries.size + bundle.manifest.entries.size
                var done = 0
                for (entry in keptEntries) {
                    val raw = ByteArray(entry.compressedSize.toInt())
                    src.file.seek(entry.dataOffset)
                    src.file.readFully(raw)

                    writeLocalHeader(
                        name = entry.name,
                        method = entry.method,
                        compressedSize = entry.compressedSize,
                        uncompressedSize = entry.uncompressedSize,
                        crc = entry.crc32,
                        data = raw,
                    )
                    done++
                    progress?.invoke(bytesWritten, srcLen)
                }

                // add bundle entries
                val added = ArrayList<String>()
                for (be in bundle.manifest.entries) {
                    val content = bundle.resolveEntry(be) ?: continue
                    val stored = be.method == BundleManifest.Compression.STORED
                    added.add(be.target)

                    if (stored) {
                        val crc = crc32(content)
                        writeLocalHeader(be.target, 0, content.size.toLong(), content.size.toLong(), crc, content)
                    } else {
                        val out = java.io.ByteArrayOutputStream()
                        val def = Deflater(9, true)
                        val inp = java.io.ByteArrayInputStream(content)
                        val buf = ByteArray(8192)
                        def.setInput(content)
                        def.finish()
                        val chunk = ByteArray(8192)
                        while (!def.finished()) {
                            val n = def.deflate(chunk)
                            out.write(chunk, 0, n)
                        }
                        def.end()
                        val compressed = out.toByteArray()
                        writeLocalHeader(be.target, 8, compressed.size.toLong(), content.size.toLong(), crc32(content), compressed)
                    }
                    done++
                    progress?.invoke(bytesWritten, srcLen)
                }

                // ---- central directory ----
                val cdOffset = raf.filePointer
                run {
                    val cb = java.io.ByteArrayOutputStream()
                    for (rec in cdEntries) {
                        val b = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN)
                        b.putInt(0x02014b50)
                        b.putShort(20)                     // version made by
                        b.putShort(20)                     // version needed
                        b.putShort(0)                      // flags
                        b.putShort(rec.method.toShort())
                        b.putShort(0)
                        b.putShort(0x0821)
                        b.putInt(rec.crc.toInt())
                        b.putInt(rec.compressedSize.toInt())
                        b.putInt(rec.uncompressedSize.toInt())
                        b.putShort(rec.name.length.toShort())
                        b.putShort(0)                      // extra len
                        b.putShort(0)                      // comment len
                        b.putShort(0)                      // disk
                        b.putShort(0)                      // internal attrs
                        b.putInt(0)                        // external attrs
                        b.putInt(rec.localOffset.toInt())
                        cb.write(b.array())
                        cb.write(rec.name.toByteArray(Charsets.UTF_8))
                    }
                    raf.write(cb.toByteArray())
                }

                // ---- EOCD ----
                val cdSize = raf.filePointer - cdOffset
                run {
                    val b = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
                    b.putInt(0x06054b50)
                    b.putShort(0)
                    b.putShort(0)
                    b.putShort(cdEntries.size.toShort())
                    b.putShort(cdEntries.size.toShort())
                    b.putInt(cdSize.toInt())
                    b.putInt(cdOffset.toInt())
                    b.putShort(0)
                    raf.write(b.array())
                }

                raf.fd.sync()

                return Result(
                    output = output,
                    kept = keptEntries.map { it.name },
                    removed = removed,
                    added = added,
                    entriesTotal = cdEntries.size,
                    alignedStored = alignedStored,
                    padBytes = alignPadBytes,
                    bytesWritten = bytesWritten,
                )
            } finally {
                raf.close()
            }
        } finally {
            src.close()
        }
    }

    private fun excludable(name: String, exclude: ExcludeRule): Boolean {
        // do not prune the signature block / meta unless explicitly requested; default prune keeps
        // the old amxx libs out but must NOT delete the signing-dir of the ORIGINAL apk blindly.
        for (p in exclude.exact) if (name == p) return true
        // exact rules for prefixes
        for (p in exclude.prefixes) {
            if (name.startsWith(p)) return true
        }
        return false
    }

    private fun crc32(data: ByteArray): Long {
        val c = CRC32()
        c.update(data)
        return c.value
    }

    private data class CdRecord(
        val name: String,
        val method: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val crc: Long,
        val localOffset: Long,
        val dataOffset: Long,
    )
}