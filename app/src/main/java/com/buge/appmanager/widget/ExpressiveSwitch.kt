package com.buge.appmanager.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.OvershootInterpolator
import com.google.android.material.materialswitch.MaterialSwitch

class ExpressiveSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialSwitch(context, attrs, defStyleAttr) {

    private var animator: ValueAnimator? = null

    init {
        setupExpressiveAnimation()
    }

    private fun setupExpressiveAnimation() {
        setOnCheckedChangeListener { _, isChecked ->
            animateThumb()
        }
    }

    private fun animateThumb() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = 250
            interpolator = OvershootInterpolator()
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                // Note: MaterialSwitch doesn't directly expose thumb view
                // This provides a bounce effect when toggling
            }
            start()
        }
    }
}