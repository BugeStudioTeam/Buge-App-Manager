// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.buge.appmanager.R
import com.buge.appmanager.databinding.FragmentWelcomeUpdateOptionsBinding
import com.buge.appmanager.util.PreferencesManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WelcomeUpdateOptionsFragment : Fragment() {

    private var _binding: FragmentWelcomeUpdateOptionsBinding? = null
    private val binding get() = _binding!!

    private var updateMethod: String = "browser"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeUpdateOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateMethod = PreferencesManager.getUpdateMethod(requireContext())
        updateMethodDisplay()

        binding.containerUpdateMethod.setOnClickListener {
            showUpdateMethodDialog()
        }

        binding.installerNameInput.setText(PreferencesManager.getInstallerName(requireContext()))
    }

    override fun onPause() {
        super.onPause()
        val name = binding.installerNameInput.text?.toString()?.trim() ?: ""
        PreferencesManager.setInstallerName(requireContext(), name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateMethodDisplay() {
        binding.updateMethodValue.text = if (updateMethod == "silent") {
            getString(R.string.update_method_silent)
        } else {
            getString(R.string.update_method_browser)
        }
    }

    private fun showUpdateMethodDialog() {
        val options = arrayOf(
            getString(R.string.update_method_browser),
            getString(R.string.update_method_silent)
        )
        val currentIndex = if (updateMethod == "silent") 1 else 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.welcome_update_options_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                updateMethod = if (which == 1) "silent" else "browser"
                PreferencesManager.setUpdateMethod(requireContext(), updateMethod)
                updateMethodDisplay()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}