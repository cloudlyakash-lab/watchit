package com.watchit.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.watchit.databinding.ActivitySettingsBinding
import com.watchit.repository.DataRepository

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "1.0.0" }
        binding.tvAppVersion.text = "Version $versionName"

        // ✅ Clear Cache
        binding.layoutClearCache.setOnClickListener {
            DataRepository.getInstance(this).clearCache()
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "Cache cleared!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                .show()
        }

        // ✅ Privacy Policy — নির্দিষ্ট লিংকে খুলবে
        binding.layoutPrivacyPolicy.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://od.lk/s/OV8yNjAwNDU1NDBf/watch_it_privacy_policy.txt"))
            )
        }

        // ✅ Share App — নির্দিষ্ট টেক্সট শেয়ার হবে
        binding.layoutShareApp.setOnClickListener {
            val shareText = "Watch movies, series & live TV on WatchIT!\n" +
                "https://od.lk/d/OV8yNjAwNDU1MzZf/WatchITcom.watchitv1.0.0.apk"
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    },
                    "Share WatchIT"
                )
            )
        }

        // ✅ Rate App
        binding.layoutRateApp.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.watchit"))
            )
        }
    }
}
