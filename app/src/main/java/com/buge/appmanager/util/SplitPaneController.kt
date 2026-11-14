// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView

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
            applyWeights()
        }
    }

    private fun applyWeights() {
        val listParams = listPane.layoutParams as? LinearLayout.LayoutParams ?: return
        val detailParams = detailPane.layoutParams as? LinearLayout.LayoutParams ?: return
        listParams.weight = ratio
        detailParams.weight = 1f - ratio
        listPane.layoutParams = listParams
        detailPane.layoutParams = detailParams
    }

    fun isOpen(): Boolean = isOpen

    fun open(fragmentManager: FragmentManager, containerId: Int, fragment: Fragment, tag: String) {
        isOpen = true
        divider.visibility = View.VISIBLE
        detailPane.visibility = View.VISIBLE
        if (detailPane is FragmentContainerView) {
            detailPane.id = containerId
        }
        applyWeights()
        fragmentManager.beginTransaction()
            .replace(containerId, fragment, tag)
            .commit()
    }

    fun close(fragmentManager: FragmentManager, containerId: Int) {
        isOpen = false
        divider.visibility = View.GONE
        detailPane.visibility = View.GONE
        val existing = fragmentManager.findFragmentById(containerId)
        if (existing != null) {
            fragmentManager.beginTransaction()
                .remove(existing)
                .commit()
        }
        val listParams = listPane.layoutParams as? LinearLayout.LayoutParams ?: return
        val detailParams = detailPane.layoutParams as? LinearLayout.LayoutParams ?: return
        listParams.weight = 1f
        detailParams.weight = 0f
        listPane.layoutParams = listParams
        detailPane.layoutParams = detailParams
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