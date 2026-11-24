// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

object CornerPressAnimator {

    private const val PRESSED_RADIUS_DP = 24f

    fun apply(view: View, topRadiusDp: Float, bottomRadiusDp: Float) {
        (view.tag as? State)?.animator?.cancel()
        val density = view.resources.displayMetrics.density
        val original = floatArrayOf(
            topRadiusDp * density,
            topRadiusDp * density,
            bottomRadiusDp * density,
            bottomRadiusDp * density
        )
        val color = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val drawable = MaterialShapeDrawable().apply {
            fillColor = ColorStateList.valueOf(color)
            shapeAppearanceModel = model(original)
        }
        view.background = drawable
        val state = State(original)
        view.tag = state
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (v.isClickable) {
                        val pressed = PRESSED_RADIUS_DP * v.resources.displayMetrics.density
                        animate(v, state, floatArrayOf(pressed, pressed, pressed, pressed))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    animate(v, state, original)
                }
            }
            false
        }
    }

    private fun animate(view: View, state: State, target: FloatArray) {
        val drawable = view.background as? MaterialShapeDrawable ?: return
        state.animator?.cancel()
        val start = state.current.copyOf()
        state.animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val radii = FloatArray(4) { index -> start[index] + (target[index] - start[index]) * fraction }
                drawable.shapeAppearanceModel = model(radii)
                state.current = radii
            }
            start()
        }
    }

    private fun model(radii: FloatArray): ShapeAppearanceModel {
        return ShapeAppearanceModel.builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, radii[0])
            .setTopRightCorner(CornerFamily.ROUNDED, radii[1])
            .setBottomRightCorner(CornerFamily.ROUNDED, radii[2])
            .setBottomLeftCorner(CornerFamily.ROUNDED, radii[3])
            .build()
    }

    private class State(val original: FloatArray) {
        var current: FloatArray = original.copyOf()
        var animator: ValueAnimator? = null
    }
}
