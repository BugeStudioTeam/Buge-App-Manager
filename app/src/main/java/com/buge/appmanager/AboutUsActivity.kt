package com.buge.appmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar

class AboutUsActivity : BaseActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvVersion: TextView
    private lateinit var cardGithub: LinearLayout
    private lateinit var cardTelegram: LinearLayout
    private lateinit var cardWebsite: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedTheme = com.buge.appmanager.util.PreferencesManager.getThemeMode(this)
        setTheme(
            when (savedTheme) {
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> R.style.Theme_BugeAppManager
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> R.style.Theme_BugeAppManager
                else -> R.style.Theme_BugeAppManager
            }
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_us)

        setupViews()
        setupToolbar()
        setupVersionInfo()
        setupClickListeners()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        tvVersion = findViewById(R.id.tv_version)
        cardGithub = findViewById(R.id.card_github)
        cardTelegram = findViewById(R.id.card_telegram)
        cardWebsite = findViewById(R.id.card_website)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.about_us)
    }

    private fun setupVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            tvVersion.text = "Version 1.0"
        }
    }

    private fun setupClickListeners() {
        cardGithub.setOnClickListener {
            openUrl("https://github.com/BugeStudioTeam")
        }
        cardTelegram.setOnClickListener {
            openUrl("https://t.me/bugestudio")
        }
        cardWebsite.setOnClickListener {
            openUrl("https://bugestudioteam.github.io/appmanager")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Do nothing
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}