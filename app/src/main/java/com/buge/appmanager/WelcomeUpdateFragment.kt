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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_welcome_update, container, false)
        val switch = view.findViewById<MaterialSwitch>(R.id.auto_update_switch)
        val statusText = view.findViewById<TextView>(R.id.auto_update_status)

        val source = (activity as? WelcomeActivity)?.getSelectedSource() ?: "GitHub"
        val defaultEnabled = source != "F-Droid"

        switch.isChecked = defaultEnabled
        updateStatus(statusText, defaultEnabled)

        (activity as? WelcomeActivity)?.onAutoUpdateChanged(defaultEnabled)

        switch.setOnCheckedChangeListener { _, isChecked ->
            updateStatus(statusText, isChecked)
            (activity as? WelcomeActivity)?.onAutoUpdateChanged(isChecked)
        }

        return view
    }

    private fun updateStatus(textView: TextView, enabled: Boolean) {
        textView.text = if (enabled) {
            getString(R.string.welcome_update_enabled)
        } else {
            getString(R.string.welcome_update_disabled)
        }
    }
}