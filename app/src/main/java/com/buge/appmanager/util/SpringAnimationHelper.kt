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

    fun animateClick(view: View) {
        val originalScaleX = view.scaleX
        val originalScaleY = view.scaleY
        val springForce = SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_LOW
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        
        SpringAnimation(view, DynamicAnimation.SCALE_X).apply {
            this.spring = springForce
            animateToFinalPosition(originalScaleX * 0.95f)
            addEndListener { _, _, _, _ ->
                SpringAnimation(view, DynamicAnimation.SCALE_X).apply {
                    this.spring = springForce
                    animateToFinalPosition(originalScaleX)
                    start()
                }
            }
            start()
        }
        
        SpringAnimation(view, DynamicAnimation.SCALE_Y).apply {
            this.spring = springForce
            animateToFinalPosition(originalScaleY * 0.95f)
            addEndListener { _, _, _, _ ->
                SpringAnimation(view, DynamicAnimation.SCALE_Y).apply {
                    this.spring = springForce
                    animateToFinalPosition(originalScaleY)
                    start()
                }
            }
            start()
        }
    }

    fun animateSpringPopup(view: View) {
        view.alpha = 0f
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.visibility = View.VISIBLE
        
        val springForce = createDefaultSpringForce()
        
        SpringAnimation(view, DynamicAnimation.ALPHA).apply {
            this.spring = springForce
            animateToFinalPosition(1f)
            start()
        }
        
        SpringAnimation(view, DynamicAnimation.SCALE_X).apply {
            this.spring = springForce
            animateToFinalPosition(1f)
            start()
        }
        
        SpringAnimation(view, DynamicAnimation.SCALE_Y).apply {
            this.spring = springForce
            animateToFinalPosition(1f)
            start()
        }
    }

    fun animateSpringHide(view: View) {
        val springForce = createDefaultSpringForce()
        
        SpringAnimation(view, DynamicAnimation.ALPHA).apply {
            this.spring = springForce
            animateToFinalPosition(0f)
            addEndListener { _, _, _, _ ->
                view.visibility = View.GONE
            }
            start()
        }
        
        SpringAnimation(view, DynamicAnimation.SCALE_X).apply {
            this.spring = springForce
            animateToFinalPosition(0.8f)
            start()
        }
        
        SpringAnimation(view, DynamicAnimation.SCALE_Y).apply {
            this.spring = springForce
            animateToFinalPosition(0.8f)
            start()
        }
    }
}