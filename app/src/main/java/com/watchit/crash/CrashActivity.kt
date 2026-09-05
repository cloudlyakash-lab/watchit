package com.watchit.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Crash হলে এই screen দেখাবে।
 * কোথায় crash হয়েছে, কী error, কপি/শেয়ার করার option থাকবে।
 */
class CrashActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CRASH_INFO = "crash_info"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashInfo = intent.getStringExtra(EXTRA_CRASH_INFO) ?: "Unknown crash"

        // Programmatically UI তৈরি — কোনো layout file লাগবে না
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 60, 32, 32)
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
        }

        // Title
        root.addView(TextView(this).apply {
            text = "💥 App Crashed"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#FF4444"))
            setPadding(0, 0, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "নিচে crash এর বিস্তারিত দেখুন:"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            setPadding(0, 0, 0, 24)
        })

        // Crash info scrollable box
        val scrollView = ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val crashText = TextView(this).apply {
            text = crashInfo
            textSize = 11.5f
            setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#0D0D1A"))
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(crashText)
        root.addView(scrollView)

        // Button row
        val btnRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        // Copy button
        val btnCopy = Button(this).apply {
            text = "📋 Copy"
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 12
            }
            setBackgroundColor(android.graphics.Color.parseColor("#2D2D4E"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", crashInfo))
                Toast.makeText(this@CrashActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        }

        // Share button
        val btnShare = Button(this).apply {
            text = "📤 Share"
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12
            }
            setBackgroundColor(android.graphics.Color.parseColor("#2D2D4E"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, crashInfo)
                        putExtra(Intent.EXTRA_SUBJECT, "WatchIT Crash Report")
                    }, "Share Crash Log"
                ))
            }
        }

        // Restart button
        val btnRestart = Button(this).apply {
            text = "🔄 Restart App"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
            setBackgroundColor(android.graphics.Color.parseColor("#CC3333"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                finish()
            }
        }

        btnRow.addView(btnCopy)
        btnRow.addView(btnShare)
        root.addView(btnRow)
        root.addView(btnRestart)

        setContentView(root)
    }
}
