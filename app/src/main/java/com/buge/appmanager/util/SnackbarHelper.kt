// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.buge.appmanager.R
import com.google.android.material.snackbar.Snackbar

object SnackbarHelper {

    fun showSnackbar(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val snackbar = buildSnackbar(view, message, duration)
        snackbar.show()
    }

    fun showSnackbar(view: View, message: String, actionText: String, action: (View) -> Unit, duration: Int = Snackbar.LENGTH_LONG) {
        val snackbar = buildSnackbar(view, message, duration)
        snackbar.setAction(actionText, action)
        snackbar.show()
    }

    private fun buildSnackbar(view: View, message: String, duration: Int): Snackbar {
        val anchor = resolveAnchorView(view) ?: view
        val snackbar = Snackbar.make(anchor, message, duration)
        val snackbarView = snackbar.view

        snackbarView.post {
            val params = snackbarView.layoutParams as? ViewGroup.MarginLayoutParams ?: return@post
            params.bottomMargin = getBottomOffset(anchor)
            snackbarView.layoutParams = params
            snackbarView.translationZ = Float.MAX_VALUE / 2f
            snackbarView.elevation = 16f * anchor.resources.displayMetrics.density
        }

        return snackbar
    }

    private fun resolveAnchorView(view: View): View? {
        val activity = view.context.findActivity() ?: return null
        return activity.findViewById(android.R.id.content)
    }

    private fun getBottomOffset(anchor: View): Int {
        var offset = 0

        val rootInsets = ViewCompat.getRootWindowInsets(anchor)
        if (rootInsets != null) {
            val systemBars = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            offset += systemBars.bottom
        }

        val activity = anchor.context.findActivity()
        if (activity != null) {
            val bottomNav = activity.findViewById<View>(R.id.bottom_nav)
            val navRail = activity.findViewById<View>(R.id.nav_rail)

            if (bottomNav != null && bottomNav.isShown) {
                offset += bottomNav.height
            } else if (navRail != null && navRail.isShown) {
                offset += 0
            }
        }

        if (offset == 0) {
            offset = getNavigationBarHeight(anchor)
        }

        return offset
    }

    private fun getNavigationBarHeight(view: View): Int {
        val resourceId = view.context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            view.context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}