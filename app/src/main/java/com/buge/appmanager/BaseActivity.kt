package com.buge.appmanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.buge.appmanager.util.FontOverrideHelper

abstract class BaseActivity : AppCompatActivity() {

    private var fontApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (!fontApplied) {
            FontOverrideHelper.applyToActivity(this)
            fontApplied = true
        }
    }
}