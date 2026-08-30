package com.pickle.patcher.data

import android.content.Context
import com.pickle.patcher.lib.Bundle
import java.io.File

/**
 * Supplies the mod bundle:
 *  1. a bundle.zip downloaded from GitHub Releases (preferred, always fresh)
 *  2. a fallback bundled inside the APK assets so the patcher works offline
 */
class BundleProvider(private val context: Context) {

    private val assetsBundle: File? = null

    /** Where downloaded release bundles are stored. */
    fun cacheDir(): File = File(context.cacheDir, "patcher")

    fun cachedBundleFile(): File = File(cacheDir(), "amxx-bundle.zip")

    fun hasCachedBundle(): Boolean = cachedBundleFile().exists()

    fun loadCachedBundle(): Bundle? = loadFrom(cachedBundleFile())

    fun loadFrom(file: File): Bundle? = try {
        Bundle.fromZip(file.readBytes())
    } catch (t: Throwable) {
        null
    }

    /**
     * Extracts the embedded fallback bundle (assets/bundle/bundle.zip) if present.
     */
    fun loadEmbedded(): Bundle? {
        return try {
            context.assets.open("bundle/bundle.zip").use { input ->
                val f = File(cacheDir(), "embedded-bundle.zip")
                f.parentFile?.mkdirs()
                input.copyTo(f.outputStream())
                Bundle.fromZip(f.readBytes())
            }
        } catch (t: Throwable) {
            null
        }
    }
}