package com.airdrophunter.app

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Temporary on-device crash diagnostics. Stores only the last uncaught exception
 * and the last navigation breadcrumb so a tester can copy it after reopening the app.
 */
object CrashDiagnostics {
    private const val TAG = "AirdropCrash"
    private const val REPORT_FILE = "last_runtime_crash.txt"
    @Volatile private var lastAction: String = "Application startup"
    @Volatile private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val report = buildString {
                    appendLine("Airdrop Hunter runtime crash")
                    appendLine("Time: $timestamp")
                    appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Last action: $lastAction")
                    appendLine()
                    append(trace)
                }
                app.openFileOutput(REPORT_FILE, Context.MODE_PRIVATE).bufferedWriter().use { it.write(report) }
                Log.e(TAG, "Uncaught exception saved for next launch", throwable)
            } catch (writeError: Throwable) {
                Log.e(TAG, "Could not save crash report", writeError)
            }
            if (previous != null) previous.uncaughtException(thread, throwable)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun markAction(action: String) {
        lastAction = action.take(180)
        Log.d(TAG, "Breadcrumb: $lastAction")
    }

    fun readReport(context: Context): String? =
        runCatching { context.applicationContext.openFileInput(REPORT_FILE).bufferedReader().use { it.readText() } }
            .getOrNull()

    fun clearReport(context: Context) {
        runCatching { context.applicationContext.deleteFile(REPORT_FILE) }
    }
}
