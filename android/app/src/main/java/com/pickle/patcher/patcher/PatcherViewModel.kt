package com.pickle.patcher.patcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pickle.patcher.data.BundleProvider
import com.pickle.patcher.data.ReleaseRepository
import com.pickle.patcher.lib.ApkPatcher
import com.pickle.patcher.lib.Bundle
import com.pickle.patcher.lib.SigningKeystore
import com.pickle.patcher.lib.ZipAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class SourceInfo(
    val name: String,
    val sizeBytes: Long,
    val entryCount: Int,
)

sealed interface BundleState {
    data object None : BundleState
    data class Ready(val bundleName: String, val entries: Int, val version: String) : BundleState
    data class Downloading(val percent: Float) : BundleState
    data class DownloadError(val message: String) : BundleState
}

sealed interface PatchUiState {
    data object Idle : PatchUiState
    data class Running(val step: ApkPatcher.Step, val progress: Float) : PatchUiState
    data class Done(val report: ApkPatcher.PatchReport) : PatchUiState
    data class Failed(val message: String) : PatchUiState
}

class PatcherViewModel(app: Application) : AndroidViewModel(app) {

    private val bundleProvider = BundleProvider(app)

    private val _source = MutableStateFlow<SourceInfo?>(null)
    val source: StateFlow<SourceInfo?> = _source.asStateFlow()

    private val _receivedSource: MutableStateFlow<File?> = MutableStateFlow(null)

    private val _bundle = MutableStateFlow<BundleState>(BundleState.None)
    val bundle: StateFlow<BundleState> = _bundle.asStateFlow()

    private val _releaseNote = MutableStateFlow<String?>(null)
    val releaseNote: StateFlow<String?> = _releaseNote.asStateFlow()

    private val _patch = MutableStateFlow<PatchUiState>(PatchUiState.Idle)
    val patch: StateFlow<PatchUiState> = _patch.asStateFlow()

    private var loadedBundle: Bundle? = null
    private var lastReport: ApkPatcher.PatchReport? = null

    val hasCachedBundle: Boolean get() = bundleProvider.hasCachedBundle()

    val repo = "cloudsoft/cs16-amxx"   // TODO: point to the actual GitHub repo of this project

    private val workDir = File(app.getExternalFilesDir(null) ?: app.cacheDir, "patcher")

    fun setRepo(newRepo: String) = Unit // placeholder if repo becomes runtime-configurable

    /** Copies a SAF-picked source APK into app storage. */
    fun pickSource(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = queryName(uri) ?: "source.apk"
                val out = File(workDir, name)
                out.parentFile?.mkdirs()
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                val info = ZipAnalyzer.analyze(out)
                _source.value = SourceInfo(name, out.length(), info.entryCount)
                _receivedSource.value = out
            } catch (t: Throwable) {
                _patch.value = PatchUiState.Failed("Kaynak APK kopyalanamadı: ${t.message}")
            }
        }
    }

    private fun queryName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    fun useEmbeddedBundle() {
        val b = bundleProvider.loadEmbedded()
        if (b != null) applyBundle("Gömülü (offline)", b)
        else _bundle.value = BundleState.DownloadError("Cihazda hazır bundle yok — çevrimiçi indirin.")
    }

    fun useCachedBundle() {
        val b = bundleProvider.loadCachedBundle()
        if (b != null) applyBundle("İndirilen ($CACHE_TAG)", b)
    }

    fun fetchAndDownloadBundle() {
        viewModelScope.launch(Dispatchers.IO) {
            _bundle.value = BundleState.Downloading(0.04f)
            try {
                val rel = ReleaseRepository.latest(repo)
                val asset = rel.bundleAsset()
                    ?: throw IOException("Sürümde bundle bulunamadı")
                _bundle.update {
                    BundleState.Downloading(0.1f)
                }
                val dest = bundleProvider.cachedBundleFile()
                ReleaseRepository.download(asset, dest)
                val b = Bundle.fromZip(dest.readBytes())
                    ?: throw IOException("Bundle dosyası bozuk")
                _releaseNote.value = rel.name.ifBlank { rel.tag_name }
                applyBundle(asset.name, b)
            } catch (t: Throwable) {
                _bundle.value = BundleState.DownloadError(t.message ?: "Bilinmeyen hata")
            }
        }
    }

    private fun applyBundle(label: String, b: Bundle) {
        loadedBundle = b
        _bundle.value = BundleState.Ready(label, b.manifest.entries.size, b.manifest.version)
    }

    fun startPatch() {
        val src = _receivedSource.value ?: return
        val b = loadedBundle ?: return
        val keystoreBytes = runCatching {
            getApplication<Application>().assets.open("keystore/debug.p12").use { it.readBytes() }
        }.getOrElse {
            _patch.value = PatchUiState.Failed("İmza anahtarı (debug.p12) eksik")
            return
        }
        val keystore = SigningKeystore.loadBytes(keystoreBytes)
        val out = File(workDir, "patched.apk")

        viewModelScope.launch(Dispatchers.IO) {
            _patch.value = PatchUiState.Running(ApkPatcher.Step.ANALYZE, 0f)
            try {
                val report = ApkPatcher.patch(
                    ApkPatcher.PatchRequest(src, out, b, keystore),
                    onStep = { step, p ->
                        _patch.value = PatchUiState.Running(step, p)
                    },
                )
                lastReport = report
                _patch.value = PatchUiState.Done(report)
            } catch (t: Throwable) {
                _patch.value = PatchUiState.Failed(t.message ?: "Bilinmeyen hata")
            }
        }
    }

    fun outputApk(): File? = lastReport?.let { File(workDir, "patched.apk") }

    fun installIntent(): Intent? {
        val out = outputApk() ?: return null
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            out,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun reset() {
        _patch.value = PatchUiState.Idle
        lastReport = null
    }

    private companion object {
        const val CACHE_TAG = "v2"
    }
}