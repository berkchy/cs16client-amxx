package com.pickle.patcher.lib

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Release bundle manifest. The GitHub workflow produces one `bundle.zip` per release:
 * entries are the amxx/metamod payload (libs + addons configs/plugins) that the patcher
 * injects into a stock CS16Client APK.
 *
 * The manifest (bundle.json, stored at the bundle root) describes every payload entry with
 * its target path inside the patched APK and the compression that must be used. Any entry
 * in the zip streams that is not listed is treated as default STORED.
 */
@Serializable
data class BundleManifest(
    val version: String = "",
    val game: String = "cs16client",
    val entries: List<BundleEntry> = emptyList(),
) {
    @Serializable
    data class BundleEntry(
        val source: String,          // path inside bundle.zip
        val target: String,          // path inside the patched APK
        val method: Compression = Compression.STORED,
        val required: Boolean = true,
        val description: String = "",
    )

    enum class Compression { STORED, DEFLATED }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

        fun parse(manifestJson: String): BundleManifest = json.decodeFromString(manifestJson)

        fun encode(manifest: BundleManifest): String = json.encodeToString(
            kotlinx.serialization.serializer<BundleManifest>(), manifest
        )
    }
}

/**
 * Describes which existing APK entries must be removed/ignored because the bundle replaces them.
 */
@Serializable
data class ExcludeRule(
    val prefixes: List<String> = emptyList(),
    val exact: List<String> = emptyList(),
) {
    companion object {
        /** Default exclusions mirror the manual flow: old amxx libs are pruned before adding. */
        val DEFAULT = ExcludeRule(
            prefixes = listOf(
                "lib/arm64-v8a/libamxmodx.so",
                "lib/arm64-v8a/lib",       // matches lib<name>_amxx_amd64.so module libs
                "lib/arm64-v8a/libmetamod.so",
            ),
            exact = listOf(
                "META-INF/",
            ),
        )
    }
}

/** A parsed bundle ready for injection. */
data class Bundle(
    val manifest: BundleManifest,
    val files: Map<String, ByteArray>,      // bundle.zip relative path -> content
) {
    fun resolveEntry(e: BundleManifest.BundleEntry): ByteArray? = files[e.source]

    companion object {
        private const val MANIFEST_PATH = "bundle.json"

        /** Parse a bundle zip's bytes. */
        fun fromZip(zipBytes: ByteArray): Bundle {
            val file = ZipRaw.open(zipBytes) ?: error("Not a zip archive")
            try {
                val manifestEntry = file.entries.getValue(MANIFEST_PATH)
                val manifestBytes = file.readRaw(manifestEntry)
                val manifest = BundleManifest.parse(manifestBytes.toString(Charsets.UTF_8))

                val files = HashMap<String, ByteArray>()
                for ((name, entry) in file.entries) {
                    if (name.endsWith("/")) continue
                    if (name == MANIFEST_PATH) continue
                    files[name] = file.readRaw(entry)
                }
                return Bundle(manifest, files)
            } finally {
                file.close()
            }
        }
    }
}