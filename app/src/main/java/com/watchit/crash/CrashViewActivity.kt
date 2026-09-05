package com.watchit.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * Crash এর পরে app reopen করলে এই screen দেখাবে।
 * কপি ও শেয়ার করে আমাকে পাঠান — সাথে সাথে fix করব।
 */
class CrashViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG = "crash_log"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = intent.getStringExtra(EXTRA_LOG) ?: "Crash log পাওয়া যায়নি"

        // ── Root layout ──────────────────────────────────────────────────────
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F1A"))
            setPadding(0, statusBarHeight(), 0, 0)
        }

        // ── Header ───────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A0A0A"))
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        header.addView(TextView(this).apply {
            text = "💥  Crash Detected"
            textSize = 22f
            setTextColor(Color.parseColor("#FF4444"))
            setTypeface(null, Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "নিচের log টি copy করে developer কে পাঠান"
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, dp(4), 0, 0)
        })

        // ── Crash log box ────────────────────────────────────────────────────
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Color.parseColor("#0A0A14"))
        }
        scroll.addView(TextView(this).apply {
            text = log
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#DDEEFF"))
            setTextIsSelectable(true)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setLineSpacing(dp(2).toFloat(), 1f)
        })

        // ── Buttons ──────────────────────────────────────────────────────────
        val btnArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#14141F"))
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        // Copy + Share row
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        row.addView(makeButton("📋  Copy Log", "#1E3A5F") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Crash", log))
            Toast.makeText(this, "✅ Copied!", Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        })

        row.addView(makeButton("📤  Share", "#1E3A5F") {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, log)
                    putExtra(Intent.EXTRA_SUBJECT, "WatchIT Crash Log")
                }, "Share Crash Log"
            ))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) }
        })

        // Restart button
        val btnRestart = makeButton("🔄  অ্যাপ Restart করুন", "#8B0000") {
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
            finish()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        btnArea.addView(row)
        btnArea.addView(btnRestart)

        root.addView(header)
        root.addView(scroll)
        root.addView(btnArea)

        setContentView(root)
    }

    private fun makeButton(label: String, color: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor(color))
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dp(24)
    }
}
