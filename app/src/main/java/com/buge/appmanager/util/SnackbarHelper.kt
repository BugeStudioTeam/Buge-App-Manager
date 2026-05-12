package com.buge.appmanager.util

import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar

object SnackbarHelper {

    fun showSnackbar(view: View, message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val snackbar = Snackbar.make(view, message, duration)
        
        // Get the snackbar's content view
        val snackbarView = snackbar.view
        
        // Add bottom margin to account for navigation bar
        snackbarView.post {
            val navBarHeight = getNavigationBarHeight(view)
            val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = navBarHeight
            snackbarView.layoutParams = params
        }
        
        snackbar.show()
    }
    
    fun showSnackbar(view: View, message: String, actionText: String, action: (View) -> Unit, duration: Int = Snackbar.LENGTH_LONG) {
        val snackbar = Snackbar.make(view, message, duration)
        
        val snackbarView = snackbar.view
        snackbarView.post {
            val navBarHeight = getNavigationBarHeight(view)
            val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = navBarHeight
            snackbarView.layoutParams = params
        }
        
        snackbar.setAction(actionText, action)
        snackbar.show()
    }
    
    private fun getNavigationBarHeight(view: View): Int {
        val resourceId = view.context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            view.context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }
}