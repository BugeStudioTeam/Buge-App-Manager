// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.buge.appmanager.R
import com.buge.appmanager.WelcomeActivity
import com.google.android.material.card.MaterialCardView

class WelcomeSourceFragment : Fragment() {

    private var selected: String = "GitHub"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_welcome_source, container, false)
        setupSourceOptions(view)
        return view
    }

    private fun setupSourceOptions(view: View) {
        val cardGithub = view.findViewById<MaterialCardView>(R.id.card_github)
        val cardFdroid = view.findViewById<MaterialCardView>(R.id.card_fdroid)
        val cardOther = view.findViewById<MaterialCardView>(R.id.card_other)
        val radioGithub = view.findViewById<RadioButton>(R.id.radio_github)
        val radioFdroid = view.findViewById<RadioButton>(R.id.radio_fdroid)
        val radioOther = view.findViewById<RadioButton>(R.id.radio_other)

        val accent = ContextCompat.getColor(requireContext(), R.color.md_theme_primary)
        val transparent = ContextCompat.getColor(requireContext(), android.R.color.transparent)

        fun update(source: String) {
            selected = source
            radioGithub.isChecked = source == "GitHub"
            radioFdroid.isChecked = source == "F-Droid"
            radioOther.isChecked = source == "Other"
            cardGithub.strokeColor = if (source == "GitHub") accent else transparent
            cardFdroid.strokeColor = if (source == "F-Droid") accent else transparent
            cardOther.strokeColor = if (source == "Other") accent else transparent
            (activity as? WelcomeActivity)?.onSourceSelected(source)
        }

        cardGithub.setOnClickListener { update("GitHub") }
        cardFdroid.setOnClickListener { update("F-Droid") }
        cardOther.setOnClickListener { update("Other") }

        update("GitHub")
    }
}