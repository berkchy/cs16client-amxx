package com.pickle.patcher.lib

import java.io.File

/** Reads structural facts about a source APK (UI + validation). */
object ZipAnalyzer {

    data class ArchiveInfo(
        val name: String,
        val sizeBytes: Long,
        val entryCount: Int,
        val hasResourcesArsc: Boolean,
        val archAbi: String?,
        val libCount: Int,
        val misalignedStored: List<String>,
    )

    fun analyze(file: File): ArchiveInfo {
        val zip = ZipRaw.open(file)
            ?: return ArchiveInfo(file.name, file.length(), 0, false, null, 0, emptyList())
        try {
            val arsc = zip.entries["resources.arsc"]
            val libs = zip.entries.keys.filter { it.startsWith("lib/") && it.endsWith(".so") }
            val abi = libs.firstOrNull { it.contains("arm64-v8a") }?.let { "arm64-v8a" }
                ?: libs.firstOrNull { it.contains("armeabi-v7a") }?.let { "armeabi-v7a" }
            val misaligned = zip.entries.values
                .filter { it.method == 0 && (it.dataOffset % 4) != 0L }
                .map { it.name }
            return ArchiveInfo(
                name = file.name,
                sizeBytes = file.length(),
                entryCount = zip.entries.size,
                hasResourcesArsc = arsc != null,
                archAbi = abi,
                libCount = libs.size,
                misalignedStored = misaligned,
            )
        } finally {
            zip.close()
        }
    }

    /** Queries whether the output contains the given path with the given method. */
    fun entryState(file: File, path: String): Triple<Boolean, Int, Long> {
        val zip = ZipRaw.open(file) ?: return Triple(false, -1, -1)
        try {
            val e = zip.entries[path] ?: return Triple(false, -1, -1)
            return Triple(true, e.method, e.dataOffset)
        } finally {
            zip.close()
        }
    }
}