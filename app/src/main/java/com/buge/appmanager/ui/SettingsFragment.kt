package com.buge.appmanager.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.buge.appmanager.MainActivity
import com.buge.appmanager.R
import com.buge.appmanager.databinding.FragmentSettingsBinding
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.PreferencesManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import rikka.shizuku.Shizuku

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            updateShizukuStatus()
            Snackbar.make(binding.root, R.string.shizuku_authorized, Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(binding.root, R.string.shizuku_not_authorized, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        setupShizukuSection()
        setupThemePref()
        setupLanguagePref()
        setupSystemAppsToggle()
        setupAboutSection()
        updateShizukuStatus()
    }

    private fun setupShizukuSection() {
        binding.btnRequestShizuku.setOnClickListener {
            if (!ShizukuManager.isShizukuAvailable()) {
                Snackbar.make(binding.root, R.string.shizuku_status_not_running, Snackbar.LENGTH_LONG).show()
            } else {
                ShizukuManager.requestShizukuPermission()
            }
        }
    }

    private fun setupThemePref() {
        updateThemeDisplay()
        binding.prefThemeRow.setOnClickListener {
            val options = arrayOf(
                getString(R.string.pref_theme_light),
                getString(R.string.pref_theme_dark),
                getString(R.string.pref_theme_auto)
            )
            val currentMode = PreferencesManager.getThemeMode(requireContext())
            val currentIndex = when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_theme)
                .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                    val mode = when (which) {
                        0 -> AppCompatDelegate.MODE_NIGHT_NO
                        1 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    PreferencesManager.setThemeMode(requireContext(), mode)
                    AppCompatDelegate.setDefaultNightMode(mode)
                    dialog.dismiss()
                    
                    // Restart the entire app to apply theme
                    restartApp()
                }
                .show()
        }
    }

    private fun updateThemeDisplay() {
        val currentMode = PreferencesManager.getThemeMode(requireContext())
        val themeText = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.pref_theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.pref_theme_dark)
            else -> getString(R.string.pref_theme_auto)
        }
        binding.themeValue.text = themeText
    }

    private fun setupLanguagePref() {
        updateLanguageDisplay()
        binding.prefLanguageRow.setOnClickListener {
            val languages = LocaleManager.getSupportedLanguages()
            val options = languages.values.toTypedArray()
            val codes = languages.keys.toList()
            val currentCode = LocaleManager.getLanguage(requireContext())
            val currentIndex = codes.indexOf(currentCode).takeIf { it >= 0 } ?: 0
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_language)
                .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                    val selectedCode = codes[which]
                    LocaleManager.setLanguage(requireContext(), selectedCode)
                    binding.languageValue.text = options[which]
                    dialog.dismiss()
                    
                    // Restart the entire app to apply language
                    restartApp()
                }
                .show()
        }
    }

    private fun updateLanguageDisplay() {
        val currentCode = LocaleManager.getLanguage(requireContext())
        val languages = LocaleManager.getSupportedLanguages()
        val displayName = languages[currentCode] ?: languages[""] ?: "System Default"
        binding.languageValue.text = displayName
    }

    private fun updateShizukuStatus() {
        val isAvailable = ShizukuManager.isShizukuAvailable()
        val hasPermission = ShizukuManager.hasShizukuPermission()

        when {
            isAvailable && hasPermission -> {
                binding.shizukuStatusText.text = getString(R.string.shizuku_status_ok)
                binding.shizukuStatusText.setTextColor(requireContext().getColor(R.color.color_granted))
                binding.shizukuStatusChip.text = getString(R.string.shizuku_authorized)
                binding.shizukuStatusChip.setChipBackgroundColorResource(R.color.color_granted_container)
                binding.btnRequestShizuku.isEnabled = false
                binding.btnRequestShizuku.text = getString(R.string.shizuku_authorized)
            }
            isAvailable && !hasPermission -> {
                binding.shizukuStatusText.text = getString(R.string.shizuku_status_no_auth)
                binding.shizukuStatusText.setTextColor(requireContext().getColor(com.google.android.material.R.color.design_default_color_error))
                binding.shizukuStatusChip.text = getString(R.string.shizuku_not_authorized)
                binding.shizukuStatusChip.setChipBackgroundColorResource(R.color.color_denied_container)
                binding.btnRequestShizuku.isEnabled = true
                binding.btnRequestShizuku.text = getString(R.string.shizuku_request_auth)
            }
            else -> {
                binding.shizukuStatusText.text = getString(R.string.shizuku_status_not_running)
                binding.shizukuStatusText.setTextColor(requireContext().getColor(com.google.android.material.R.color.design_default_color_error))
                binding.shizukuStatusChip.text = getString(R.string.shizuku_not_running)
                binding.shizukuStatusChip.setChipBackgroundColorResource(R.color.color_denied_container)
                binding.btnRequestShizuku.isEnabled = true
                binding.btnRequestShizuku.text = getString(R.string.shizuku_request_auth)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    private fun setupSystemAppsToggle() {
        val showSystemApps = PreferencesManager.getShowSystemApps(requireContext())
        binding.switchShowSystem.isChecked = showSystemApps
        binding.switchShowSystem.setOnCheckedChangeListener { _, isChecked ->
            PreferencesManager.setShowSystemApps(requireContext(), isChecked)
            Snackbar.make(binding.root, R.string.setting_saved, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupAboutSection() {
        binding.aboutRow.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null)
        val githubItem = view.findViewById<View>(R.id.github_item)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .show()
        
        githubItem.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://github.com/BugeStudioTeam/Buge-App-Manager")
            }
            startActivity(intent)
            dialog.dismiss()
        }
    }

    private fun restartApp() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        _binding = null
    }
}
