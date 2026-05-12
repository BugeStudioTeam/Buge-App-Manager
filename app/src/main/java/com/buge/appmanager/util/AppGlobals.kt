package com.buge.appmanager.util

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.buge.appmanager.R

class AppGlobals : Application() {

    companion object {
        private lateinit var _applicationContext: Context
        
        val applicationContext: Context
            get() = _applicationContext
        
        lateinit var regularTypeface: Typeface
        lateinit var mediumTypeface: Typeface
        lateinit var boldTypeface: Typeface
        
        fun preloadTypefaces() {
            regularTypeface = ResourcesCompat.getFont(
                applicationContext,
                R.font.google_sans_regular
            ) ?: Typeface.DEFAULT
            
            mediumTypeface = ResourcesCompat.getFont(
                applicationContext,
                R.font.google_sans_medium
            ) ?: Typeface.DEFAULT
            
            boldTypeface = ResourcesCompat.getFont(
                applicationContext,
                R.font.google_sans_bold
            ) ?: Typeface.DEFAULT
        }
    }

    override fun onCreate() {
        super.onCreate()
        _applicationContext = applicationContext
        preloadTypefaces()
    }
}