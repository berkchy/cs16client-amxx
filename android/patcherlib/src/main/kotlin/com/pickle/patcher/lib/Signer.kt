package com.pickle.patcher.lib

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Wraps the debug keystore used for all patched APKs (kept stable so the app can be
 * *updated*, not reinstalled, on devices that already run an earlier patched build).
 */
class SigningKeystore(
    val keyStore: KeyStore,
    val alias: String,
    val keyPassword: CharArray,
) {
    val privateKey: PrivateKey get() = keyStore.getKey(alias, keyPassword) as PrivateKey
    val certificateChain: List<X509Certificate>
        get() = (keyStore.getCertificateChain(alias) as Array<X509Certificate>).toList()

    val certificate: X509Certificate get() = certificateChain.first()

    /** hex-uppercase SHA-256 fingerprint of the signing certificate */
    fun fingerprintSha256(): String = fingerprint(certificate, "SHA-256")

    fun fingerprintSha1(): String = fingerprint(certificate, "SHA-1")

    companion object {
        fun fingerprint(cert: X509Certificate, algo: String): String {
            val md = MessageDigest.getInstance(algo)
            return md.digest(cert.encoded).joinToString("") { "%02X".format(it) }
        }

        const val DEFAULT_STORE_PASSWORD = "android"
        const val DEFAULT_KEY_PASSWORD = "android"
        const val DEFAULT_ALIAS = "androiddebugkey"

        /**
         * Loads the bundled debug keystore. Tries PKCS12 first (Android support), falls
         * back to JKS (desktop tooling / tests).
         */
        fun load(file: File, storePassword: CharArray = DEFAULT_STORE_PASSWORD.toCharArray()): SigningKeystore {
            val bytes = file.readBytes()
            val formats = listOf("PKCS12", "JKS")
            var lastError: Exception? = null
            for (format in formats) {
                try {
                    val ks = KeyStore.getInstance(format)
                    ks.load(bytes.inputStream(), storePassword)
                    return SigningKeystore(ks, DEFAULT_ALIAS, DEFAULT_KEY_PASSWORD.toCharArray())
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw IllegalStateException("Unable to load keystore", lastError)
        }

        /** Read a keystore from bytes. */
        fun loadBytes(bytes: ByteArray, storePassword: CharArray = DEFAULT_STORE_PASSWORD.toCharArray()): SigningKeystore {
            val formats = listOf("PKCS12", "JKS")
            var lastError: Exception? = null
            for (format in formats) {
                try {
                    val ks = KeyStore.getInstance(format)
                    ks.load(bytes.inputStream(), storePassword)
                    return SigningKeystore(ks, DEFAULT_ALIAS, DEFAULT_KEY_PASSWORD.toCharArray())
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw IllegalStateException("Unable to load keystore", lastError)
        }
    }
}

/**
 * Signs an already-aligned, unsigned APK with V1+ (JAR) and V2+ (block) schemes using
 * apksig, then runs apksig's verifier and reports the result.
 */
object ApkSignerTool {

    data class SignOutcome(
        val unsigned: File,
        val signed: File,
        val verified: Boolean,
        val signerFingerprintSha256: String,
        val usedV1: Boolean,
        val usedV2: Boolean,
        val errors: List<String>,
    )

    fun sign(
        unsignedApk: File,
        outputApk: File,
        keystore: SigningKeystore,
        minSdk: Int = 24,
    ): SignOutcome {
        val config = ApkSigner.SignerConfig.Builder(
            "amxx-patcher",
            keystore.privateKey,
            keystore.certificateChain,
        ).build()

        val signer = ApkSigner.Builder(listOf(config))
            .setInputApk(unsignedApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .setV4SigningEnabled(false)
            .setMinSdkVersion(minSdk)
            .setAlignmentPreserved(true)   // our repacker already aligned stored entries
            .setDebuggableApkPermitted(true)
            .build()

        signer.sign()

        val verification = verify(outputApk)
        return SignOutcome(
            unsigned = unsignedApk,
            signed = outputApk,
            verified = verification.verified,
            signerFingerprintSha256 = verification.signerFingerprintSha256,
            usedV1 = verification.usedV1,
            usedV2 = verification.usedV2,
            errors = verification.errors,
        )
    }

    data class Verification(
        val verified: Boolean,
        val signerFingerprintSha256: String,
        val signerFingerprintSha1: String,
        val usedV1: Boolean,
        val usedV2: Boolean,
        val errors: List<String>,
    ) {
        fun sameSigner(other: Verification): Boolean =
            signerFingerprintSha256 == other.signerFingerprintSha256
    }

    fun verify(apkFile: File): Verification {
        val result = ApkVerifier.Builder(apkFile).build().verify()
        val certs = result.signerCertificates
        val fp256 = if (certs.isNotEmpty()) SigningKeystore.fingerprint(certs.first(), "SHA-256") else ""
        val fp1 = if (certs.isNotEmpty()) SigningKeystore.fingerprint(certs.first(), "SHA-1") else ""
        val errors = result.errors.map { it.toString() }
        return Verification(
            verified = result.isVerified,
            signerFingerprintSha256 = fp256,
            signerFingerprintSha1 = fp1,
            usedV1 = result.isVerifiedUsingV1Scheme,
            usedV2 = result.isVerifiedUsingV2Scheme,
            errors = errors,
        )
    }
}