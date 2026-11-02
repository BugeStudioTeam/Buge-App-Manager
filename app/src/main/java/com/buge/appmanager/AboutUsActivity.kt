// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.animation.doOnEnd

class AboutUsActivity : BaseActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvVersion: TextView
    private lateinit var cardGithub: LinearLayout
    private lateinit var cardTelegram: LinearLayout
    private lateinit var cardWebsite: LinearLayout
    private lateinit var cardActivityManager: LinearLayout
    private lateinit var cardFdroid: LinearLayout
    private lateinit var rotatingShape: ImageView

    private var rotationAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_us)

        setupViews()
        setupToolbar()
        setupVersionInfo()
        setupClickListeners()
        startRotatingShape()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        tvVersion = findViewById(R.id.tv_version)
        cardGithub = findViewById(R.id.card_github)
        cardTelegram = findViewById(R.id.card_telegram)
        cardWebsite = findViewById(R.id.card_website)
        cardActivityManager = findViewById(R.id.card_activitymanager)
        cardFdroid = findViewById(R.id.card_fdroid)
        rotatingShape = findViewById(R.id.app_icon_shape_rotating)
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

    private fun startRotatingShape() {
        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 12000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                rotatingShape.rotation = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun setupClickListeners() {
        setupCardClickListener(cardGithub) {
            openUrl("https://github.com/BugeStudioTeam")
        }
        setupCardClickListener(cardTelegram) {
            openUrl("https://t.me/bugestudio")
        }
        setupCardClickListener(cardWebsite) {
            openUrl("https://bugestudio.website/")
        }
        setupCardClickListener(cardActivityManager) {
            openUrl("https://github.com/sdex/ActivityManager")
        }
        setupCardClickListener(cardFdroid) {
            openUrl("https://f-droid.org/packages/com.buge.appmanager/")
        }
    }

    private fun setupCardClickListener(card: LinearLayout, onClick: () -> Unit) {
        card.setOnClickListener { v ->
            ValueAnimator.ofFloat(1f, 0.96f).apply {
                duration = 80
                addUpdateListener { animator ->
                    val scale = animator.animatedValue as Float
                    v.scaleX = scale
                    v.scaleY = scale
                }
                doOnEnd {
                    ValueAnimator.ofFloat(0.96f, 1f).apply {
                        duration = 80
                        addUpdateListener { animator ->
                            val scale = animator.animatedValue as Float
                            v.scaleX = scale
                            v.scaleY = scale
                        }
                        start()
                    }
                }
                start()
            }
            onClick.invoke()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        rotationAnimator?.pause()
    }

    override fun onResume() {
        super.onResume()
        rotationAnimator?.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}