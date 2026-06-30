package com.buge.appmanager.util

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import com.buge.appmanager.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView

object FontOverrideHelper {

    private var isEnglishLocale: Boolean = true

    private val regularTypeface: Typeface? by lazy {
        ResourcesCompat.getFont(
            getContext(),
            R.font.google_sans_regular
        )
    }

    private val mediumTypeface: Typeface? by lazy {
        ResourcesCompat.getFont(
            getContext(),
            R.font.google_sans_medium
        )
    }

    private val boldTypeface: Typeface? by lazy {
        ResourcesCompat.getFont(
            getContext(),
            R.font.google_sans_bold
        )
    }

    private fun getContext(): Context = AppGlobals.applicationContext

    fun setEnglishLocaleFlag(isEnglish: Boolean) {
        isEnglishLocale = isEnglish
    }

    fun isEnglishLocale(): Boolean = isEnglishLocale

    fun getTypefaceByStyle(style: Int): Typeface? {
        if (!isEnglishLocale) return null

        return when (style) {
            Typeface.BOLD -> boldTypeface
            Typeface.NORMAL -> regularTypeface
            else -> mediumTypeface
        }
    }

    fun applyGoogleSansToView(view: View) {
        if (!isEnglishLocale) return

        when (view) {
            is TextView -> applyTypefaceToTextView(view)
            is Button -> applyTypefaceToTextView(view)
            is AppCompatTextView -> applyTypefaceToTextView(view)
            is AppCompatButton -> applyTypefaceToTextView(view)
            is MaterialButton -> applyTypefaceToTextView(view)
            is MaterialTextView -> applyTypefaceToTextView(view)
            is Chip -> applyTypefaceToTextView(view)
            is TextInputEditText -> applyTypefaceToTextView(view)
            is MaterialSwitch -> {
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        applyGoogleSansToView(view.getChildAt(i))
                    }
                }
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyGoogleSansToView(view.getChildAt(i))
                }
            }
        }
    }

    private fun applyTypefaceToTextView(textView: TextView) {
        val currentTypeface = textView.typeface
        val style = currentTypeface?.style ?: Typeface.NORMAL

        val targetTypeface = when (style) {
            Typeface.BOLD -> boldTypeface ?: mediumTypeface ?: regularTypeface
            else -> regularTypeface
        }

        targetTypeface?.let {
            textView.typeface = it
        }
    }

    fun applyToActivity(activity: AppCompatActivity) {
        if (!isEnglishLocale) return
        val decorView = activity.window.decorView
        applyGoogleSansToView(decorView)
    }
}