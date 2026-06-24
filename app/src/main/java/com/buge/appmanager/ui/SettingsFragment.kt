package com.buge.appmanager.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.AboutUsActivity
import com.buge.appmanager.AppearanceActivity
import com.buge.appmanager.BaseActivity
import com.buge.appmanager.LogViewerActivity
import com.buge.appmanager.MainActivity
import com.buge.appmanager.R
import com.buge.appmanager.RestoreAppsActivity
import com.buge.appmanager.databinding.FragmentSettingsBinding
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.buge.appmanager.util.SpringAnimationHelper
import com.buge.appmanager.util.UpdateChecker
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SettingsAdapter
    private var fontApplied = false
    private var pendingLanguageCode: String? = null

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            updateShizukuStatus()
            SnackbarHelper.showSnackbar(binding.root, getString(R.string.shizuku_authorized))
            LogManager.info(requireContext(), "Shizuku authorized")
        } else {
            updateShizukuStatus()
            SnackbarHelper.showSnackbar(binding.root, getString(R.string.shizuku_not_authorized))
            LogManager.warning(requireContext(), "Shizuku authorization failed")
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

        setupRecyclerView()
        runSpringEnterAnimation()
    }

    override fun onResume() {
        super.onResume()
        if (activity is BaseActivity && !fontApplied) {
            FontOverrideHelper.applyToActivity(activity as BaseActivity)
            fontApplied = true
        }
        updateShizukuStatus()
        setupRecyclerView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        _binding = null
    }

    private fun setupRecyclerView() {
        if (!isAdded || view == null) return
        val items = buildSettingItems()
        adapter = SettingsAdapter(
            items = items,
            onItemClick = { item ->
                when (item) {
                    is SettingItem.Normal -> {
                        when {
                            item.title == getString(R.string.pref_theme) -> showThemeDialog()
                            item.title == getString(R.string.pref_language) -> showLanguageDialog()
                            item.title == getString(R.string.pref_default_page) -> showDefaultPageDialog()
                            item.title == getString(R.string.pref_logging) -> {
                                startActivity(Intent(requireContext(), LogViewerActivity::class.java))
                                LogManager.info(requireContext(), "Opened log viewer")
                            }
                            item.title == getString(R.string.more_options) -> {
                                startActivity(Intent(requireContext(), AppearanceActivity::class.java))
                            }
                            item.title == getString(R.string.pref_restore_apps) -> {
                                startActivity(Intent(requireContext(), RestoreAppsActivity::class.java))
                            }
                        }
                    }
                    is SettingItem.About -> {
                        showAboutDialog()
                    }
                    is SettingItem.AboutMore -> {
                        startActivity(Intent(requireContext(), AboutUsActivity::class.java))
                    }
                    else -> {}
                }
            },
            onSwitchChange = { switchItem, isChecked ->
                when {
                    switchItem.title == getString(R.string.pref_show_disabled_apps) -> {
                        PreferencesManager.setShowDisabledApps(requireContext(), isChecked)
                        SnackbarHelper.showSnackbar(binding.root, getString(R.string.setting_saved))
                        LogManager.info(requireContext(), "Show disabled apps changed to $isChecked")
                    }
                    switchItem.title == getString(R.string.pref_show_system_apps) -> {
                        PreferencesManager.setShowSystemApps(requireContext(), isChecked)
                        SnackbarHelper.showSnackbar(binding.root, getString(R.string.setting_saved))
                        updateShizukuStatus()
                        LogManager.info(requireContext(), "Show system apps changed to $isChecked")
                    }
                    switchItem.title == getString(R.string.pref_show_undeclared_activities) -> {
                        PreferencesManager.setShowUndeclaredActivities(requireContext(), isChecked)
                        SnackbarHelper.showSnackbar(binding.root, getString(R.string.setting_saved))
                        LogManager.info(requireContext(), "Show undeclared activities changed to $isChecked")
                    }
                    switchItem.title == getString(R.string.pref_allow_system_ops) -> {
                        PreferencesManager.setAllowSystemOps(requireContext(), isChecked)
                        SnackbarHelper.showSnackbar(binding.root, getString(R.string.setting_saved))
                        LogManager.info(requireContext(), "Allow system app operations changed to $isChecked")
                    }
                    switchItem.title == getString(R.string.pref_google_services) -> {
                        handleGoogleServicesToggle(isChecked)
                    }
                    switchItem.title == getString(R.string.pref_auto_update) -> {
                        PreferencesManager.setAutoUpdate(requireContext(), isChecked)
                        SnackbarHelper.showSnackbar(binding.root, getString(R.string.setting_saved))
                        LogManager.info(requireContext(), "Auto update changed to $isChecked")
                    }
                }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.recyclerView.post {
            if (isAdded && view != null) {
                updateShizukuStatus()
            }
        }
    }

    private fun handleGoogleServicesToggle(enable: Boolean) {
        if (!enable) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.gms_disable_warning_title)
                .setMessage(R.string.gms_disable_warning_message)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    disableGms()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                    setupRecyclerView()
                }
                .show()
        } else {
            enableGms()
        }
    }

    private fun disableGms() {
        lifecycleScope.launch {
            try {
                val gmsPackage = "com.google.android.gms"
                val result = ShizukuManager.disableApp(gmsPackage)
                if (result.success) {
                    SnackbarHelper.showSnackbar(binding.root, "Google Services disabled")
                    LogManager.info(requireContext(), "Google Services disabled by user")
                } else {
                    SnackbarHelper.showSnackbar(binding.root, "Failed to disable: ${result.error}")
                    LogManager.error(requireContext(), "Failed to disable Google Services", result.error)
                }
            } catch (e: Exception) {
                SnackbarHelper.showSnackbar(binding.root, "Error: ${e.message}")
                LogManager.error(requireContext(), "Error disabling Google Services", e.message)
            }
            setupRecyclerView()
        }
    }

    private fun enableGms() {
        lifecycleScope.launch {
            try {
                val gmsPackage = "com.google.android.gms"
                val result = ShizukuManager.enableApp(gmsPackage)
                if (result.success) {
                    SnackbarHelper.showSnackbar(binding.root, "Google Services enabled")
                    LogManager.info(requireContext(), "Google Services enabled by user")
                } else {
                    SnackbarHelper.showSnackbar(binding.root, "Failed to enable: ${result.error}")
                    LogManager.error(requireContext(), "Failed to enable Google Services", result.error)
                }
            } catch (e: Exception) {
                SnackbarHelper.showSnackbar(binding.root, "Error: ${e.message}")
                LogManager.error(requireContext(), "Error enabling Google Services", e.message)
            }
            setupRecyclerView()
        }
    }

    private fun checkGmsStatus(): Boolean {
        return try {
            val packageManager = requireContext().packageManager
            val gmsPackage = "com.google.android.gms"
            val appInfo = packageManager.getApplicationInfo(gmsPackage, 0)
            appInfo.enabled
        } catch (e: Exception) {
            false
        }
    }

    private fun isGmsAvailable(): Boolean {
        return try {
            val packageManager = requireContext().packageManager
            val gmsPackage = "com.google.android.gms"
            packageManager.getApplicationInfo(gmsPackage, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildSettingItems(): List<SettingItem> {
        val showSystemApps = PreferencesManager.getShowSystemApps(requireContext())
        val showUndeclared = PreferencesManager.getShowUndeclaredActivities(requireContext())
        val showDisabledApps = PreferencesManager.getShowDisabledApps(requireContext())
        val allowSystemOps = PreferencesManager.getAllowSystemOps(requireContext())
        val autoUpdate = PreferencesManager.getAutoUpdate(requireContext())
        val currentTheme = PreferencesManager.getThemeMode(requireContext())
        val themeText = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.pref_theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.pref_theme_dark)
            else -> getString(R.string.pref_theme_auto)
        }
        val currentLanguage = LocaleManager.getLanguage(requireContext())
        val languages = LocaleManager.getSupportedLanguages()
        val languageText = languages[currentLanguage] ?: languages[""] ?: "System Default"

        val defaultPage = PreferencesManager.getDefaultPage(requireContext())
        val defaultPageText = when (defaultPage) {
            "apps" -> getString(R.string.default_page_apps)
            "permissions" -> getString(R.string.default_page_permissions)
            "activities" -> getString(R.string.default_page_activities)
            "settings" -> getString(R.string.default_page_settings)
            else -> getString(R.string.default_page_apps)
        }

        val gmsAvailable = isGmsAvailable()
        val gmsEnabled = if (gmsAvailable) checkGmsStatus() else false

        return listOf(
            SettingItem.Header(getString(R.string.settings_group_authorization)),
            SettingItem.Shizuku,
            SettingItem.Header(getString(R.string.settings_group_appearance)),
            SettingItem.Normal(getString(R.string.pref_theme), themeText, R.drawable.ic_theme),
            SettingItem.Normal(getString(R.string.pref_language), languageText, R.drawable.ic_language),
            SettingItem.Normal(getString(R.string.pref_default_page), defaultPageText, R.drawable.ic_home_page),
            SettingItem.Normal(getString(R.string.more_options), getString(R.string.more_options_summary), R.drawable.ic_palette),
            SettingItem.Header(getString(R.string.settings_group_apps)),
            SettingItem.SwitchItem(
                getString(R.string.pref_google_services),
                gmsEnabled,
                R.drawable.ic_google_services,
                gmsAvailable
            ),
            SettingItem.SwitchItem(
                getString(R.string.pref_auto_update),
                autoUpdate,
                R.drawable.ic_autoupdate
            ),
            SettingItem.SwitchItem(
                getString(R.string.pref_allow_system_ops),
                allowSystemOps,
                R.drawable.ic_allow_system
            ),
            SettingItem.Normal(
                getString(R.string.pref_restore_apps),
                getString(R.string.pref_restore_apps_summary),
                R.drawable.ic_restore
            ),
            SettingItem.Header(getString(R.string.settings_group_advanced)),
            SettingItem.SwitchItem(getString(R.string.pref_show_disabled_apps), showDisabledApps, R.drawable.ic_disabled_apps),
            SettingItem.SwitchItem(getString(R.string.pref_show_system_apps), showSystemApps, R.drawable.ic_system_apps),
            SettingItem.SwitchItem(getString(R.string.pref_show_undeclared_activities), showUndeclared, R.drawable.ic_undeclared),
            SettingItem.Normal(getString(R.string.pref_logging), getString(R.string.pref_logging_summary), R.drawable.ic_log),
            SettingItem.Header(getString(R.string.settings_group_about)),
            SettingItem.About(getVersionName()),
            SettingItem.AboutMore(getString(R.string.about_more), getString(R.string.about_more_summary))
        )
    }

    private fun getVersionName(): String {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            "v${packageInfo.versionName}"
        } catch (e: Exception) {
            "v3.6.11"
        }
    }

    private fun updateShizukuStatus() {
        if (!isAdded || view == null) return
        try {
            val isAvailable = ShizukuManager.isShizukuAvailable()
            val hasPermission = ShizukuManager.hasShizukuPermission()

            val statusText: String
            val iconRes: Int
            val buttonEnabled: Boolean
            val buttonText: String
            val statusColor: Int

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

            val recyclerView = binding.recyclerView
            for (i in 0 until (recyclerView.adapter?.itemCount ?: 0)) {
                val holder = recyclerView.findViewHolderForAdapterPosition(i)
                if (holder is SettingsAdapter.ShizukuViewHolder) {
                    val itemView = holder.itemView
                    val shizukuIcon = itemView.findViewById<ImageView>(R.id.shizuku_icon)
                    val shizukuStatusText = itemView.findViewById<TextView>(R.id.shizuku_status_text)
                    val requestButton = itemView.findViewById<MaterialButton>(R.id.btn_request_shizuku)

                    shizukuIcon?.setImageResource(iconRes)
                    shizukuIcon?.setColorFilter(null)
                    shizukuStatusText?.text = statusText
                    shizukuStatusText?.setTextColor(statusColor)
                    requestButton?.isEnabled = buttonEnabled
                    requestButton?.text = buttonText
                    requestButton?.setOnClickListener {
                        if (!ShizukuManager.isShizukuAvailable()) {
                            showShizukuGuideDialog()
                        } else {
                            ShizukuManager.requestShizukuPermission()
                        }
                    }
                    break
                }
            }
        } catch (e: Exception) {
            // Ignore update errors to avoid crash
        }
    }

    private fun showShizukuGuideDialog() {
        if (!isAdded || view == null) return

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_shizuku_guide, null)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val btnOpenShizuku = dialogView.findViewById<View>(R.id.btn_open_shizuku)
        val btnDownloadShizuku = dialogView.findViewById<View>(R.id.btn_download_shizuku)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)

        if (btnOpenShizuku == null || btnDownloadShizuku == null || btnCancel == null) {
            dialog.dismiss()
            return
        }

        btnOpenShizuku.setOnClickListener {
            try {
                val intent = requireContext().packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) {
                    startActivity(intent)
                } else {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                    startActivity(webIntent)
                }
            } catch (e: Exception) {
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                    startActivity(webIntent)
                } catch (e2: Exception) {
                    SnackbarHelper.showSnackbar(binding.root, "Cannot open Shizuku")
                }
            }
            dialog.dismiss()
        }

        btnDownloadShizuku.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                startActivity(intent)
            } catch (e: Exception) {
                SnackbarHelper.showSnackbar(binding.root, "Cannot open browser")
            }
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showThemeDialog() {
        if (!isAdded || view == null) return
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
                
                val isEnglish = Locale.getDefault().language == "en"
                FontOverrideHelper.setEnglishLocaleFlag(isEnglish)
                fontApplied = false
                
                requireActivity().recreate()
            }
            .show()
    }

    private fun showLanguageDialog() {
        if (!isAdded || view == null) return
        val languages = LocaleManager.getSupportedLanguages()
        val options = languages.values.toTypedArray()
        val codes = languages.keys.toList()
        val currentCode = LocaleManager.getLanguage(requireContext())
        val currentIndex = codes.indexOf(currentCode).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pref_language)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val selectedCode = codes[which]
                pendingLanguageCode = selectedCode
                dialog.dismiss()
                
                showRestartDialog(
                    title = getString(R.string.restart_required),
                    message = getString(R.string.language_changed_restart)
                ) {
                    LocaleManager.setLanguage(requireContext(), selectedCode)
                    
                    val isEnglish = when (selectedCode) {
                        "", "en" -> true
                        else -> {
                            val locale = if (selectedCode.isEmpty()) Locale.getDefault() else Locale(selectedCode)
                            locale.language == "en"
                        }
                    }
                    FontOverrideHelper.setEnglishLocaleFlag(isEnglish)
                    fontApplied = false
                    
                    restartApp()
                }
            }
            .show()
    }

    private fun showDefaultPageDialog() {
        if (!isAdded || view == null) return
        val options = arrayOf(
            getString(R.string.default_page_apps),
            getString(R.string.default_page_permissions),
            getString(R.string.default_page_activities),
            getString(R.string.default_page_settings)
        )
        val defaultPage = PreferencesManager.getDefaultPage(requireContext())
        val currentIndex = when (defaultPage) {
            "apps" -> 0
            "permissions" -> 1
            "activities" -> 2
            "settings" -> 3
            else -> 0
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.default_page_title)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val page = when (which) {
                    0 -> "apps"
                    1 -> "permissions"
                    2 -> "activities"
                    3 -> "settings"
                    else -> "apps"
                }
                val oldPage = PreferencesManager.getDefaultPage(requireContext())
                PreferencesManager.setDefaultPage(requireContext(), page)
                dialog.dismiss()
                
                if (oldPage != page) {
                    showRestartDialog(
                        title = getString(R.string.restart_required),
                        message = getString(R.string.restart_required_message)
                    ) {
                        restartApp()
                    }
                }
            }
            .show()
    }

    private fun showRestartDialog(title: String, message: String, onConfirm: () -> Unit) {
        if (!isAdded || view == null) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.restart_now)) { _, _ ->
                onConfirm.invoke()
            }
            .setNegativeButton(getString(R.string.later), null)
            .show()
    }

    private fun restartApp() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
    }

    private fun showAboutDialog() {
        if (!isAdded || view == null) return
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null)
        val websiteItem = view.findViewById<View>(R.id.website_item)
        val githubItem = view.findViewById<View>(R.id.github_item)
        val telegramItem = view.findViewById<View>(R.id.telegram_item)
        val updateItem = view.findViewById<View>(R.id.update_item)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .show()

        websiteItem.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://bugestudioteam.github.io/appmanager")
            }
            startActivity(intent)
        }

        githubItem.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://github.com/BugeStudioTeam/Buge-App-Manager")
            }
            startActivity(intent)
        }

        telegramItem.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://t.me/bugestudio")
            }
            startActivity(intent)
        }

        updateItem.setOnClickListener {
            dialog.dismiss()
            checkForUpdate()
        }
    }

    private fun checkForUpdate() {
        if (!isAdded || view == null) return
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.checking_for_update)
            .setMessage(R.string.please_wait)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            try {
                val releaseInfo = UpdateChecker.checkForUpdates(requireContext())
                loadingDialog.dismiss()

                if (releaseInfo != null) {
                    UpdateChecker.showUpdateDialog(
                        requireContext(),
                        releaseInfo,
                        onDownload = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.apkDownloadUrl))
                            startActivity(intent)
                        }
                    )
                } else {
                    UpdateChecker.showNoUpdateDialog(requireContext())
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                UpdateChecker.showErrorDialog(requireContext(), e.message)
            }
        }
    }

    private fun runSpringEnterAnimation() {
        if (!isAdded || view == null) return
        binding.recyclerView.alpha = 0f
        binding.recyclerView.translationY = 30f
        binding.recyclerView.post {
            if (isAdded && view != null) {
                SpringAnimationHelper.animateAlpha(binding.recyclerView, 1f)
                SpringAnimationHelper.animateTranslationY(binding.recyclerView, 0f)
            }
        }
    }
}