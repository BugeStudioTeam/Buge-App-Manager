package com.buge.appmanager.util

import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

object SpringAnimationHelper {

    fun createDefaultSpringForce(): SpringForce {
        return SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_MEDIUM
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
    }

    fun createFastSpringForce(): SpringForce {
        return SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_HIGH
            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        }
    }

    fun createSlowSpringForce(): SpringForce {
        return SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_HIGH_BOUNCY
        }
    }

    fun createSpringForceWithParams(stiffness: Float = 700f, dampingRatio: Float = 0.85f): SpringForce {
        return SpringForce().apply {
            this.stiffness = stiffness
            this.dampingRatio = dampingRatio
        }
    }

    fun animateTranslationY(
        view: View,
        targetY: Float,
        springForce: SpringForce? = null
    ): SpringAnimation {
        val force = springForce ?: createDefaultSpringForce()
        return SpringAnimation(view, DynamicAnimation.TRANSLATION_Y).apply {
            spring = force
            animateToFinalPosition(targetY)
            start()
        }
    }

    fun animateTranslationX(
        view: View,
        targetX: Float,
        springForce: SpringForce? = null
    ): SpringAnimation {
        val force = springForce ?: createDefaultSpringForce()
        return SpringAnimation(view, DynamicAnimation.TRANSLATION_X).apply {
            spring = force
            animateToFinalPosition(targetX)
            start()
        }
    }

    fun animateScaleX(
        view: View,
        targetScale: Float,
        springForce: SpringForce? = null
    ): SpringAnimation {
        val force = springForce ?: createFastSpringForce()
        return SpringAnimation(view, DynamicAnimation.SCALE_X).apply {
            spring = force
            animateToFinalPosition(targetScale)
            start()
        }
    }

    fun animateScaleY(
        view: View,
        targetScale: Float,
        springForce: SpringForce? = null
    ): SpringAnimation {
        val force = springForce ?: createFastSpringForce()
        return SpringAnimation(view, DynamicAnimation.SCALE_Y).apply {
            spring = force
            animateToFinalPosition(targetScale)
            start()
        }
    }

    fun animateAlpha(
        view: View,
        targetAlpha: Float,
        springForce: SpringForce? = null
    ): SpringAnimation {
        val force = springForce ?: createDefaultSpringForce()
        return SpringAnimation(view, DynamicAnimation.ALPHA).apply {
            spring = force
            animateToFinalPosition(targetAlpha)
            start()
        }
    }

    fun animateRotation(
        view: View,
        targetRotation: Float,
        springForce: SpringForce? = null
    ): SpringAnimation {
        val force = springForce ?: createDefaultSpringForce()
        return SpringAnimation(view, DynamicAnimation.ROTATION).apply {
            spring = force
            animateToFinalPosition(targetRotation)
            start()
        }
    }
}