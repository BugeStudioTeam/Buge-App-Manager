// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch

class WelcomeUpdateFragment : Fragment() {

    private lateinit var switch: MaterialSwitch
    private lateinit var statusText: TextView
    private var userTouched = false
    private var lastSource: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_welcome_update, container, false)
        switch = view.findViewById(R.id.auto_update_switch)
        statusText = view.findViewById(R.id.auto_update_status)

        lastSource = (activity as? WelcomeActivity)?.getSelectedSource() ?: "GitHub"
        val defaultEnabled = lastSource != "F-Droid"

        switch.isChecked = defaultEnabled
        updateStatus(defaultEnabled)
        (activity as? WelcomeActivity)?.onAutoUpdateChanged(defaultEnabled)

        switch.setOnCheckedChangeListener { _, isChecked ->
            userTouched = true
            updateStatus(isChecked)
            (activity as? WelcomeActivity)?.onAutoUpdateChanged(isChecked)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        val currentSource = (activity as? WelcomeActivity)?.getSelectedSource() ?: "GitHub"
        if (currentSource != lastSource) {
            lastSource = currentSource
            if (!userTouched) {
                val defaultEnabled = currentSource != "F-Droid"
                switch.isChecked = defaultEnabled
                updateStatus(defaultEnabled)
                (activity as? WelcomeActivity)?.onAutoUpdateChanged(defaultEnabled)
            }
        }
    }

    private fun updateStatus(enabled: Boolean) {
        statusText.text = if (enabled) {
            getString(R.string.welcome_update_enabled)
        } else {
            getString(R.string.welcome_update_disabled)
        }
    }
}