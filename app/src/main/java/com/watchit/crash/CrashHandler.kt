package com.watchit.crash

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * ✅ CRASH FINDER
 * যেকোনো crash হলে CrashActivity খুলে বিস্তারিত দেখাবে।
 *
 * ব্যবহার: WatchItApp.kt এর onCreate()-এ এক লাইন যোগ করুন:
 *   CrashHandler.init(this)
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private lateinit var appContext: Context
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashInfo = buildCrashInfo(thread, throwable)

            val intent = Intent(appContext, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_INFO, crashInfo)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            appContext.startActivity(intent)

        } catch (e: Exception) {
            // যদি CrashActivity খুলতেও সমস্যা হয়, default handler এ পাঠাও
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            exitProcess(1)
        }
    }

    private fun buildCrashInfo(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()

        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("💥 CRASH DETECTED")
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine()

        // Error Type & Message
        sb.appendLine("🔴 Error Type:")
        sb.appendLine("   ${throwable.javaClass.simpleName}")
        sb.appendLine()

        sb.appendLine("📝 Message:")
        sb.appendLine("   ${throwable.message ?: "No message"}")
        sb.appendLine()

        // Thread info
        sb.appendLine("🧵 Thread: ${thread.name}")
        sb.appendLine()

        // Cause chain
        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < 3) {
            sb.appendLine("⛓ Caused by: ${cause.javaClass.simpleName}")
            sb.appendLine("   ${cause.message ?: "No message"}")
            sb.appendLine()
            cause = cause.cause
            depth++
        }

        // Stack trace — শুধু আপনার app এর lines হাইলাইট করা
        sb.appendLine("📍 Stack Trace:")
        sb.appendLine("─────────────────────────────────")

        val stackLines = throwable.stackTrace
        var appLinesFound = 0

        for (element in stackLines) {
            val line = "   at ${element.className}.${element.methodName}" +
                    "(${element.fileName}:${element.lineNumber})"

            if (element.className.startsWith("com.watchit")) {
                // আপনার app এর code — সবচেয়ে গুরুত্বপূর্ণ
                sb.appendLine("👉 $line")
                appLinesFound++
            } else if (appLinesFound < 3) {
                // System/library lines শুধু প্রথম কিছু
                sb.appendLine(line)
            }
        }

        if (appLinesFound == 0) {
            // কোনো app line না থাকলে সব দেখাও
            for (element in stackLines.take(15)) {
                sb.appendLine("   at ${element.className}.${element.methodName}" +
                        "(${element.fileName}:${element.lineNumber})")
            }
        }

        sb.appendLine()
        sb.appendLine("═══════════════════════════════════")

        return sb.toString()
    }
}
