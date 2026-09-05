package com.watchit.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * ══════════════════════════════════════════════════════
 *  EARLY CRASH FINDER v2
 *  — Theme crash, font crash, native crash সব ধরে
 *  — App reopen করলে crash screen দেখায়
 *  — File এ save করে, Logcat ছাড়াই কাজ করে
 * ══════════════════════════════════════════════════════
 *
 *  ব্যবহার — WatchItApp এ দুই জায়গায় যোগ করুন:
 *
 *  1) attachBaseContext() override করুন:
 *     override fun attachBaseContext(base: Context) {
 *         EarlyCrashSetup.install(base)   // ← সবার আগে
 *         super.attachBaseContext(base)
 *     }
 *
 *  2) onCreate() তে:
 *     override fun onCreate() {
 *         super.onCreate()
 *         EarlyCrashSetup.checkPreviousCrash(this)  // ← crash থাকলে দেখাবে
 *         ...
 *     }
 */
object EarlyCrashSetup {

    private const val CRASH_FILE = "watchit_crash.txt"
    private var appContext: Context? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    // ─── Step 1: attachBaseContext এ call করুন ───────────────────────────────
    fun install(context: Context) {
        appContext = context.applicationContext ?: context
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashToFile(context, thread, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    // ─── Step 2: onCreate এ call করুন ────────────────────────────────────────
    fun checkPreviousCrash(context: Context) {
        val file = File(context.filesDir, CRASH_FILE)
        if (!file.exists()) return

        val crashLog = file.readText()
        file.delete() // পড়ার পর মুছে ফেলো

        // CrashViewActivity তে পাঠাও
        val intent = android.content.Intent(context, CrashViewActivity::class.java).apply {
            putExtra(CrashViewActivity.EXTRA_LOG, crashLog)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ─── Crash কে file এ save করো ────────────────────────────────────────────
    private fun saveCrashToFile(context: Context, thread: Thread, throwable: Throwable) {
        try {
            val ctx = appContext ?: context
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())

            val sb = StringBuilder()
            sb.appendLine("╔══════════════════════════════════════════╗")
            sb.appendLine("║         💥 WatchIT CRASH REPORT          ║")
            sb.appendLine("╚══════════════════════════════════════════╝")
            sb.appendLine("🕐 সময়: $time")
            sb.appendLine("📱 Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            sb.appendLine("🤖 Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            sb.appendLine()

            // Error summary
            sb.appendLine("━━━━━━━━ ERROR ━━━━━━━━")
            sb.appendLine("🔴 Type:    ${throwable.javaClass.name}")
            sb.appendLine("📝 Message: ${throwable.message ?: "(কোনো message নেই)"}")
            sb.appendLine("🧵 Thread:  ${thread.name}")
            sb.appendLine()

            // Cause chain
            var cause: Throwable? = throwable.cause
            var depth = 0
            while (cause != null && depth < 5) {
                sb.appendLine("⛓ Caused by: ${cause.javaClass.name}")
                sb.appendLine("   ${cause.message ?: ""}")
                cause = cause.cause
                depth++
            }
            if (depth > 0) sb.appendLine()

            // YOUR app's lines — সবচেয়ে গুরুত্বপূর্ণ
            sb.appendLine("━━━━━━━━ আপনার App এর Code ━━━━━━━━")
            val appLines = throwable.stackTrace.filter {
                it.className.startsWith("com.watchit")
            }
            if (appLines.isEmpty()) {
                sb.appendLine("(আপনার app এর কোনো line নেই — system/library crash)")
            } else {
                appLines.forEach {
                    sb.appendLine("👉 ${it.className.removePrefix("com.watchit.")}")
                    sb.appendLine("     method: ${it.methodName}()")
                    sb.appendLine("     file:   ${it.fileName} — line ${it.lineNumber}")
                    sb.appendLine()
                }
            }

            // Full stack trace
            sb.appendLine("━━━━━━━━ Full Stack Trace ━━━━━━━━")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString())

            // File এ লেখো
            File(ctx.filesDir, CRASH_FILE).writeText(sb.toString())

        } catch (_: Exception) {
            // Save fail হলেও চুপ থাকো — app কে crash করতে দাও normally
        }
    }
}
