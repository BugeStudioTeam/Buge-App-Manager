// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui.welcome

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.buge.appmanager.R
import com.buge.appmanager.databinding.FragmentWelcomeAuthBinding
import com.buge.appmanager.dhizuku.DhizukuManager
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WelcomeAuthFragment : Fragment() {

    private var _binding: FragmentWelcomeAuthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateAuthModeDisplay()
        updateAuthStatus()

        binding.authModeItem.setOnClickListener {
            showAuthModeDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            updateAuthModeDisplay()
            updateAuthStatus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateAuthModeDisplay() {
        val mode = PreferencesManager.getAuthMode(requireContext())
        binding.authModeValue.text = when (mode) {
            PreferencesManager.AUTH_MODE_DHIZUKU -> getString(R.string.auth_mode_dhizuku)
            PreferencesManager.AUTH_MODE_ROOT -> getString(R.string.auth_mode_root)
            else -> getString(R.string.auth_mode_shizuku)
        }
    }

    private fun updateAuthStatus() {
        if (!isAdded || view == null) return
        try {
            val authMode = PreferencesManager.getAuthMode(requireContext())
            val isRootMode = authMode == PreferencesManager.AUTH_MODE_ROOT
            val isDhizukuMode = authMode == PreferencesManager.AUTH_MODE_DHIZUKU

            val statusText: String
            val iconRes: Int
            val buttonEnabled: Boolean
            val buttonText: String
            val statusColor: Int
            val titleText: String
            val descText: String

            if (isRootMode) {
                val rootAvailable = ShizukuManager.isRootAvailable()
                if (rootAvailable) {
                    statusText = getString(R.string.root_status_ok)
                    statusColor = ContextCompat.getColor(requireContext(), R.color.color_granted)
                    iconRes = R.drawable.ic_shield
                } else {
                    statusText = getString(R.string.root_status_not_ok)
                    statusColor = ContextCompat.getColor(requireContext(), com.google.android.material.R.color.design_default_color_error)
                    iconRes = R.drawable.ic_shield_badge_x
                }
                buttonEnabled = false
                buttonText = if (rootAvailable) {
                    getString(R.string.shizuku_authorized)
                } else {
                    getString(R.string.not_authorized)
                }
                titleText = getString(R.string.root_authorization_title)
                descText = getString(R.string.root_authorization_desc)
            } else if (isDhizukuMode) {
                val isAuthorized = ShizukuManager.isDhizukuAuthorized()

                if (isAuthorized) {
                    statusText = getString(R.string.dhizuku_status_ok)
                    iconRes = R.drawable.ic_shield
                    buttonEnabled = false
                    buttonText = getString(R.string.shizuku_authorized)
                    statusColor = ContextCompat.getColor(requireContext(), R.color.color_granted)
                } else {
                    statusText = getString(R.string.dhizuku_status_no_auth)
                    iconRes = R.drawable.ic_shield_badge_x
                    buttonEnabled = true
                    buttonText = getString(R.string.dhizuku_request_auth)
                    statusColor = ContextCompat.getColor(requireContext(), com.google.android.material.R.color.design_default_color_error)
                }
                titleText = getString(R.string.dhizuku_title)
                descText = getString(R.string.dhizuku_desc)
            } else {
                val isAvailable = ShizukuManager.isShizukuAvailable()
                val hasPermission = ShizukuManager.hasShizukuPermission()

                when {
                    isAvailable && hasPermission -> {
                        statusText = getString(R.string.shizuku_status_ok)
                        iconRes = R.drawable.ic_shield
                        buttonEnabled = false
                        buttonText = getString(R.string.shizuku_authorized)
                        statusColor = ContextCompat.getColor(requireContext(), R.color.color_granted)
                    }
                    isAvailable && !hasPermission -> {
                        statusText = getString(R.string.shizuku_status_no_auth)
                        iconRes = R.drawable.ic_shield_badge_x
                        buttonEnabled = true
                        buttonText = getString(R.string.shizuku_request_auth)
                        statusColor = ContextCompat.getColor(requireContext(), com.google.android.material.R.color.design_default_color_error)
                    }
                    else -> {
                        statusText = getString(R.string.shizuku_status_not_running)
                        iconRes = R.drawable.ic_shield_badge_x
                        buttonEnabled = true
                        buttonText = getString(R.string.shizuku_request_auth)
                        statusColor = ContextCompat.getColor(requireContext(), com.google.android.material.R.color.design_default_color_error)
                    }
                }
                titleText = getString(R.string.shizuku_title)
                descText = getString(R.string.shizuku_desc)
            }

            binding.shizukuIcon.setImageResource(iconRes)
            binding.shizukuTitle.text = titleText
            binding.shizukuStatusText.text = statusText
            binding.shizukuStatusText.setTextColor(statusColor)
            binding.shizukuDesc.text = descText
            binding.btnRequestAuth.isEnabled = buttonEnabled
            binding.btnRequestAuth.text = buttonText
            binding.btnRequestAuth.setOnClickListener {
                when {
                    isRootMode -> {
                        SnackbarHelper.showSnackbar(binding.root, getString(R.string.root_authorization_desc))
                    }
                    isDhizukuMode -> {
                        DhizukuManager.requestDhizukuPermission()
                        binding.root.postDelayed({ if (_binding != null) updateAuthStatus() }, 800)
                    }
                    else -> {
                        ShizukuManager.requestShizukuPermission()
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun showAuthModeDialog() {
        val options = arrayOf(
            getString(R.string.auth_mode_shizuku),
            getString(R.string.auth_mode_dhizuku),
            getString(R.string.auth_mode_root)
        )
        val currentMode = PreferencesManager.getAuthMode(requireContext())
        val currentIndex = when (currentMode) {
            PreferencesManager.AUTH_MODE_DHIZUKU -> 1
            PreferencesManager.AUTH_MODE_ROOT -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auth_mode_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val newMode = when (which) {
                    1 -> PreferencesManager.AUTH_MODE_DHIZUKU
                    2 -> PreferencesManager.AUTH_MODE_ROOT
                    else -> PreferencesManager.AUTH_MODE_SHIZUKU
                }
                PreferencesManager.setAuthMode(requireContext(), newMode)
                dialog.dismiss()
                if (newMode == PreferencesManager.AUTH_MODE_DHIZUKU) {
                    DhizukuManager.init(requireContext())
                    ShizukuManager.ensureDhizukuBound()
                }
                updateAuthModeDisplay()
                updateAuthStatus()
            }
            .show()
    }
}