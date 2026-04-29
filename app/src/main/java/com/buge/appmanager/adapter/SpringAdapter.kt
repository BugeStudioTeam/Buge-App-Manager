package com.buge.appmanager.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.util.SpringAnimationHelper

abstract class SpringAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    private var lastPosition = -1

    protected fun runSpringEnterAnimation(view: View, position: Int) {
        if (position > lastPosition) {
            lastPosition = position
            view.alpha = 0f
            view.translationY = 50f
            view.post {
                SpringAnimationHelper.animateAlpha(view, 1f)
                SpringAnimationHelper.animateTranslationY(view, 0f)
            }
        }
    }

    protected fun runSpringExitAnimation(view: View, onEnd: () -> Unit) {
        SpringAnimationHelper.animateAlpha(view, 0f)
        SpringAnimationHelper.animateTranslationY(view, -50f)
        view.postDelayed(onEnd, 300)
    }

    fun resetAnimationState() {
        lastPosition = -1
    }
}