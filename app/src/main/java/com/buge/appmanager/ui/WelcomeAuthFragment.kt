// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.buge.appmanager.R
import com.buge.appmanager.databinding.FragmentWelcomeAuthBinding
import com.buge.appmanager.dhizuku.DhizukuManager
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        binding.authModeItem.setOnClickListener {
            showAuthModeDialog()
        }

        binding.btnGrantStorage.setOnClickListener {
            grantStoragePermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            updateAuthModeDisplay()
            updateStorageButtonState()
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
        updateStorageButtonState()
    }

    private fun updateStorageButtonState() {
        val authorized = ShizukuManager.isAuthorized()
        binding.btnGrantStorage.isEnabled = authorized
        binding.btnGrantStorage.alpha = if (authorized) 1f else 0.4f
        binding.btnGrantStorage.text = if (authorized) {
            getString(R.string.welcome_auth_grant)
        } else {
            getString(R.string.welcome_auth_not_authorized)
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
            }
            .show()
    }

    private fun grantStoragePermission() {
        if (!ShizukuManager.isAuthorized()) {
            SnackbarHelper.showSnackbar(
                binding.root,
                getString(R.string.error_no_privilege),
                getString(R.string.request_auth),
                { ShizukuManager.requestAuthorization() }
            )
            return
        }

        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val resultRead = withContext(Dispatchers.IO) {
                ShizukuManager.executeCommand("pm grant com.buge.appmanager android.permission.READ_EXTERNAL_STORAGE")
            }
            val resultWrite = withContext(Dispatchers.IO) {
                ShizukuManager.executeCommand("pm grant com.buge.appmanager android.permission.WRITE_EXTERNAL_STORAGE")
            }
            if (resultRead.success && resultWrite.success) {
                SnackbarHelper.showSnackbar(binding.root, "Storage permissions granted")
            } else {
                val error = if (!resultRead.success) resultRead.error else resultWrite.error
                SnackbarHelper.showSnackbar(binding.root, "Failed to grant: $error")
            }
        }
    }
}