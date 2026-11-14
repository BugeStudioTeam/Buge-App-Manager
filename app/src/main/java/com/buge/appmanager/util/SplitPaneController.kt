// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentContainerView

class SplitPaneController(
    private val listPane: View,
    private val divider: View,
    private val detailPane: View,
    private val onClosed: () -> Unit = {}
) {
    private val defaultRatio = 0.5f
    private val minRatio = 0.3f
    private val maxRatio = 0.7f
    private var ratio = defaultRatio
    private var isOpen = false
    private var dragging = false
    private var animator: ValueAnimator? = null

    init {
        divider.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        updateRatioFromTouch(event.rawX)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun updateRatioFromTouch(rawX: Float) {
        val container = (listPane.parent as? ViewGroup) ?: return
        val containerWidth = container.width
        if (containerWidth <= 0) return
        val location = IntArray(2)
        container.getLocationOnScreen(location)
        val relativeX = rawX - location[0]
        val newRatio = (relativeX / containerWidth).coerceIn(minRatio, maxRatio)
        if (newRatio != ratio) {
            ratio = newRatio
            applyWeights(ratio)
        }
    }

    private fun applyWeights(targetRatio: Float) {
        val listParams = listPane.layoutParams as? LinearLayout.LayoutParams ?: return
        val detailParams = detailPane.layoutParams as? LinearLayout.LayoutParams ?: return
        listParams.weight = targetRatio
        detailParams.weight = 1f - targetRatio
        listPane.layoutParams = listParams
        detailPane.layoutParams = detailParams
    }

    private fun animateWeights(from: Float, to: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                applyWeights(value)
            }
            start()
        }
    }

    fun isOpen(): Boolean = isOpen

    fun open(fragmentManager: FragmentManager, containerId: Int, fragment: Fragment, tag: String) {
        if (isOpen) {
            fragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(containerId, fragment, tag)
                .commit()
            return
        }

        isOpen = true
        divider.visibility = View.VISIBLE
        detailPane.visibility = View.VISIBLE

        if (detailPane is FragmentContainerView) {
            detailPane.id = containerId
        }

        detailPane.translationX = detailPane.resources.displayMetrics.widthPixels.toFloat()
        detailPane.alpha = 0f
        detailPane.post {
            detailPane.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        animateWeights(1f, ratio)

        fragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(containerId, fragment, tag)
            .commit()
    }

    fun close(fragmentManager: FragmentManager, containerId: Int) {
        if (!isOpen) return
        isOpen = false

        detailPane.animate()
            .translationX(detailPane.resources.displayMetrics.widthPixels.toFloat())
            .alpha(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                divider.visibility = View.GONE
                detailPane.visibility = View.GONE
                detailPane.translationX = 0f
                detailPane.alpha = 1f
                val existing = fragmentManager.findFragmentById(containerId)
                if (existing != null) {
                    fragmentManager.beginTransaction()
                        .remove(existing)
                        .commit()
                }
            }
            .start()

        animateWeights(ratio, 1f)

        ratio = defaultRatio
        onClosed.invoke()
    }

    fun closeIfOpen(fragmentManager: FragmentManager, containerId: Int): Boolean {
        if (isOpen) {
            close(fragmentManager, containerId)
            return true
        }
        return false
    }
}