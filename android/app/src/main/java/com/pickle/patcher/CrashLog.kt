package com.pickle.patcher

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    private const val TAG = "PatcherCrash"
    private const val FILE_NAME = "patcher-crash.txt"

    fun install(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(context.applicationContext, throwable)
            } catch (_: Throwable) {
            }
            prev?.uncaughtException(thread, throwable)
                ?: throwable.printStackTrace()
        }
    }

    private fun write(context: Context, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val body = "=== $stamp pid=${android.os.Process.myPid()} ===\n" +
            "model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}\n" +
            "thread=${Thread.currentThread().name}\n$sw\n"

        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) resolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
            }
        }
        runCatching {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, FILE_NAME).writeText(body)
        }
        runCatching {
            File(context.getExternalFilesDir(null), FILE_NAME).writeText(body)
        }
        Log.e(TAG, sw.toString())
    }
}