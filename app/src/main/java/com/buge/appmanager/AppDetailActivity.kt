package com.buge.appmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.buge.appmanager.adapter.PermissionDetailAdapter
import com.buge.appmanager.databinding.ActivityAppDetailBinding
import com.buge.appmanager.model.PermissionInfo
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SystemOpChecker
import com.buge.appmanager.viewmodel.AppDetailViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private lateinit var binding: ActivityAppDetailBinding
    private val viewModel: AppDetailViewModel by viewModels()
    private lateinit var permAdapter: PermissionDetailAdapter
    private var packageName: String = ""
    private var favoriteMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }

        setupToolbar()
        setupPermissionsRecycler()
        setupActions()
        observeViewModel()

        viewModel.loadApp(packageName)
        LogManager.info(this, "Opened app details", "Package: $packageName")
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadApp(packageName)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.title_app_detail)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_app_detail, menu)
        favoriteMenuItem = menu.findItem(R.id.action_favorite)
        updateFavoriteIcon()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            R.id.action_favorite -> {
                toggleFavorite()
                return true
            }
            R.id.action_export_apk -> {
                exportApk()
                return true
            }
            R.id.action_export_info -> {
                exportAppInfo()
                return true
            }
            R.id.action_google_play -> {
                openInGooglePlay()
                return true
            }
            R.id.action_f_droid -> {
                openInFDroid()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun exportAppInfo() {
        val app = viewModel.appInfo.value
        val permissions = viewModel.permissions.value
        
        if (app == null) {
            Snackbar.make(binding.root, "Unable to export app info", Snackbar.LENGTH_SHORT).show()
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Export App Info")
            .setMessage("Export detailed information for ${app.appName} to a text file?")
            .setPositiveButton(R.string.confirm) { _, _ ->
                doExportAppInfo(app, permissions ?: emptyList())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun doExportAppInfo(app: com.buge.appmanager.model.AppInfo, permissions: List<PermissionInfo>) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeAppName = app.appName.replace("/", "_").replace("\\", "_").replace(":", "_").replace("?", "_").replace("*", "_")
            val fileName = "${safeAppName}_${timestamp}_info.txt"
            val destFile = File(downloadDir, fileName)
            
            var counter = 1
            var finalFile = destFile
            while (finalFile.exists()) {
                finalFile = File(downloadDir, "${safeAppName}_${timestamp}_${counter}_info.txt")
                counter++
            }
            
            val content = buildAppInfoContent(app, permissions)
            
            FileOutputStream(finalFile).use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                }
            }
            
            Snackbar.make(binding.root, "App info saved to: ${finalFile.absolutePath}", Snackbar.LENGTH_LONG).show()
            LogManager.success(this, "App info exported", "Package: ${app.packageName}, Path: ${finalFile.absolutePath}")
            
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(binding.root, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            LogManager.error(this, "App info export failed", e.message)
        }
    }
    
    private fun buildAppInfoContent(app: com.buge.appmanager.model.AppInfo, permissions: List<PermissionInfo>): String {
        val currentLocale = getCurrentLocale()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", currentLocale)
        
        val separator = "=".repeat(60)
        val line = "-".repeat(60)
        
        return buildString {
            appendLine(separator)
            appendLine("Buge App Manager - Application Information Report")
            appendLine(separator)
            appendLine()
            appendLine("Generated: ${dateFormat.format(Date())}")
            appendLine()
            
            appendLine(line)
            appendLine("BASIC INFORMATION")
            appendLine(line)
            appendLine("App Name: ${app.appName}")
            appendLine("Package Name: ${app.packageName}")
            appendLine("Version Name: ${app.versionName}")
            appendLine("Version Code: ${app.versionCode}")
            appendLine("Is System App: ${if (app.isSystemApp) "Yes" else "No"}")
            appendLine("Is Enabled: ${if (app.isEnabled) "Yes" else "No"}")
            appendLine()
            
            appendLine(line)
            appendLine("INSTALLATION INFORMATION")
            appendLine(line)
            appendLine("Install Date: ${dateFormat.format(Date(app.installTime))}")
            appendLine("Update Date: ${dateFormat.format(Date(app.updateTime))}")
            appendLine("APK Path: ${app.apkPath}")
            appendLine()
            
            appendLine(line)
            appendLine("SDK INFORMATION")
            appendLine(line)
            appendLine("Target SDK: API ${app.targetSdkVersion}")
            appendLine("Min SDK: API ${app.minSdkVersion}")
            appendLine()
            
            appendLine(line)
            appendLine("PERMISSIONS (${permissions.size})")
            appendLine(line)
            
            val dangerousPerms = permissions.filter { it.isDangerous }
            val normalPerms = permissions.filter { !it.isDangerous }
            
            if (dangerousPerms.isNotEmpty()) {
                appendLine()
                appendLine("Dangerous Permissions (${dangerousPerms.size}):")
                for (perm in dangerousPerms) {
                    val status = if (perm.isGranted) "GRANTED" else "DENIED"
                    appendLine("  • ${getFriendlyPermissionName(perm.name)}")
                    appendLine("    - Name: ${perm.name}")
                    appendLine("    - Status: $status")
                }
            }
            
            if (normalPerms.isNotEmpty()) {
                appendLine()
                appendLine("Normal Permissions (${normalPerms.size}):")
                for (perm in normalPerms) {
                    appendLine("  • ${getFriendlyPermissionName(perm.name)}")
                    appendLine("    - Name: ${perm.name}")
                }
            }
            
            if (permissions.isEmpty()) {
                appendLine("  No permissions requested")
            }
            
            appendLine()
            appendLine(separator)
            appendLine("END OF REPORT")
            appendLine(separator)
        }
    }
    
    private fun getFriendlyPermissionName(permission: String): String {
        return when (permission) {
            "android.permission.RECORD_AUDIO" -> "Microphone"
            "android.permission.CAMERA" -> "Camera"
            "android.permission.ACCESS_FINE_LOCATION" -> "Precise Location"
            "android.permission.ACCESS_COARSE_LOCATION" -> "Approximate Location"
            "android.permission.ACCESS_BACKGROUND_LOCATION" -> "Background Location"
            "android.permission.READ_CONTACTS" -> "Read Contacts"
            "android.permission.WRITE_CONTACTS" -> "Write Contacts"
            "android.permission.GET_ACCOUNTS" -> "Get Accounts"
            "android.permission.READ_EXTERNAL_STORAGE" -> "Read Storage"
            "android.permission.WRITE_EXTERNAL_STORAGE" -> "Write Storage"
            "android.permission.MANAGE_EXTERNAL_STORAGE" -> "All Files Access"
            "android.permission.READ_PHONE_STATE" -> "Phone State"
            "android.permission.CALL_PHONE" -> "Make Calls"
            "android.permission.READ_CALL_LOG" -> "Read Call Log"
            "android.permission.WRITE_CALL_LOG" -> "Write Call Log"
            "android.permission.SEND_SMS" -> "Send SMS"
            "android.permission.RECEIVE_SMS" -> "Receive SMS"
            "android.permission.READ_SMS" -> "Read SMS"
            "android.permission.READ_CALENDAR" -> "Read Calendar"
            "android.permission.WRITE_CALENDAR" -> "Write Calendar"
            "android.permission.BODY_SENSORS" -> "Body Sensors"
            "android.permission.ACTIVITY_RECOGNITION" -> "Activity Recognition"
            "android.permission.BLUETOOTH_SCAN" -> "Bluetooth Scan"
            "android.permission.BLUETOOTH_CONNECT" -> "Bluetooth Connect"
            "android.permission.POST_NOTIFICATIONS" -> "Notifications"
            "android.permission.READ_MEDIA_IMAGES" -> "Media Images"
            "android.permission.READ_MEDIA_VIDEO" -> "Media Video"
            "android.permission.READ_MEDIA_AUDIO" -> "Media Audio"
            "android.permission.SYSTEM_ALERT_WINDOW" -> "Display Over Other Apps"
            "android.permission.REQUEST_INSTALL_PACKAGES" -> "Install Unknown Apps"
            "android.permission.WRITE_SETTINGS" -> "Modify System Settings"
            else -> {
                val shortName = permission.substringAfterLast(".")
                shortName.replace("_", " ").lowercase().split(" ").joinToString(" ") { 
                    it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
        }
    }

    private fun toggleFavorite() {
        if (PreferencesManager.isFavoriteApp(this, packageName)) {
            PreferencesManager.removeFavoriteApp(this, packageName)
            LogManager.info(this, "Removed from favorites", "Package: $packageName")
            Snackbar.make(binding.root, "Removed from favorites", Snackbar.LENGTH_SHORT).show()
        } else {
            PreferencesManager.addFavoriteApp(this, packageName)
            LogManager.info(this, "Added to favorites", "Package: $packageName")
            Snackbar.make(binding.root, "Added to favorites", Snackbar.LENGTH_SHORT).show()
        }
        updateFavoriteIcon()
    }

    private fun updateFavoriteIcon() {
        val isFavorite = PreferencesManager.isFavoriteApp(this, packageName)
        if (isFavorite) {
            favoriteMenuItem?.icon = ContextCompat.getDrawable(this, R.drawable.ic_favorite_filled)
        } else {
            favoriteMenuItem?.icon = ContextCompat.getDrawable(this, R.drawable.ic_favorite)
        }
    }

    private fun exportApk() {
        try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val sourcePath = applicationInfo.sourceDir

            if (sourcePath.isNullOrEmpty()) {
                Snackbar.make(binding.root, "APK path not found", Snackbar.LENGTH_SHORT).show()
                LogManager.error(this, "APK export failed", "APK path not found for $packageName")
                return
            }

            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                Snackbar.make(binding.root, "APK file does not exist", Snackbar.LENGTH_SHORT).show()
                LogManager.error(this, "APK export failed", "APK file does not exist for $packageName")
                return
            }

            val appName = try {
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (e: Exception) {
                packageName
            }

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            var destFile = File(downloadDir, "${appName}_${timestamp}.apk")
            var counter = 1
            while (destFile.exists()) {
                destFile = File(downloadDir, "${appName}_${timestamp}_$counter.apk")
                counter++
            }

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Snackbar.make(binding.root, "APK saved to: ${destFile.absolutePath}", Snackbar.LENGTH_LONG).show()
            LogManager.success(this, "APK exported", "Package: $packageName, Path: ${destFile.absolutePath}")

        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(binding.root, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            LogManager.error(this, "APK export failed", e.message)
        }
    }

    private fun openInGooglePlay() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            LogManager.info(this, "Opened Google Play", "Package: $packageName")
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                LogManager.info(this, "Opened Google Play (web)", "Package: $packageName")
            } catch (e2: Exception) {
                Snackbar.make(binding.root, "Cannot open Google Play", Snackbar.LENGTH_SHORT).show()
                LogManager.error(this, "Failed to open Google Play", e2.message)
            }
        }
    }

    private fun openInFDroid() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://f-droid.org/packages/$packageName/")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            LogManager.info(this, "Opened F-Droid", "Package: $packageName")
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Cannot open F-Droid", Snackbar.LENGTH_SHORT).show()
            LogManager.error(this, "Failed to open F-Droid", e.message)
        }
    }

    private fun setupPermissionsRecycler() {
        permAdapter = PermissionDetailAdapter(
            onToggle = { perm ->
                handlePermissionToggle(perm)
            }
        )
        binding.permissionsRecycler.layoutManager = LinearLayoutManager(this)
        binding.permissionsRecycler.adapter = permAdapter
    }

    private fun setupActions() {
        binding.btnOpen.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                LogManager.info(this, "App opened", "Package: $packageName")
            } else {
                Snackbar.make(binding.root, "Cannot launch this app", Snackbar.LENGTH_SHORT).show()
                LogManager.warning(this, "Cannot launch app", "Package: $packageName")
            }
        }

        binding.btnAppInfo.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            LogManager.info(this, "Opened system app info", "Package: $packageName")
        }

        binding.btnForceStop.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            val app = viewModel.appInfo.value ?: return@setOnClickListener
            if (!SystemOpChecker.canOperate(this, app.isSystemApp)) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.force_stop)
                .setMessage("Force stop ${binding.appName.text}?")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.forceStop()
                    LogManager.info(this, "Force stop executed", "Package: $packageName")
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnUninstall.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            val app = viewModel.appInfo.value ?: return@setOnClickListener
            if (!SystemOpChecker.canOperate(this, app.isSystemApp)) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.uninstall)
                .setMessage("Uninstall ${binding.appName.text}?")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.uninstallApp()
                    LogManager.info(this, "Uninstall initiated", "Package: $packageName")
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnClearData.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            val app = viewModel.appInfo.value ?: return@setOnClickListener
            if (!SystemOpChecker.canOperate(this, app.isSystemApp)) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_data)
                .setMessage("Clear all data for ${binding.appName.text}? This cannot be undone.")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.clearData()
                    LogManager.info(this, "Clear data executed", "Package: $packageName")
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.btnDisableEnable.setOnClickListener {
            if (!checkShizuku()) return@setOnClickListener
            val app = viewModel.appInfo.value ?: return@setOnClickListener
            if (!SystemOpChecker.canOperate(this, app.isSystemApp)) {
                showSystemOpBlockedDialog()
                return@setOnClickListener
            }
            if (app.isEnabled) {
                viewModel.disableApp()
                LogManager.info(this, "App disabled", "Package: $packageName")
            } else {
                viewModel.enableApp()
                LogManager.info(this, "App enabled", "Package: $packageName")
            }
        }
    }

    private fun showSystemOpBlockedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.system_op_blocked_title)
            .setMessage(R.string.system_op_blocked_message)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handlePermissionToggle(perm: PermissionInfo) {
        if (!checkShizuku()) return

        val app = viewModel.appInfo.value
        if (app != null && !SystemOpChecker.canOperate(this, app.isSystemApp)) {
            showSystemOpBlockedDialog()
            return
        }

        val action = if (perm.isGranted) getString(R.string.revoke_permission) else getString(R.string.grant_permission)
        MaterialAlertDialogBuilder(this)
            .setTitle(action)
            .setMessage(perm.name)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.togglePermission(perm.name, perm.isGranted)
                LogManager.permission(this, "Permission toggled", "Package: $packageName, Permission: ${perm.name}, Action: $action")
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkShizuku(): Boolean {
        if (!ShizukuManager.isShizukuAvailable() || !ShizukuManager.hasShizukuPermission()) {
            Snackbar.make(binding.root, R.string.error_no_shizuku, Snackbar.LENGTH_LONG)
                .setAction(R.string.shizuku_request_auth) {
                    ShizukuManager.requestShizukuPermission()
                }
                .show()
            return false
        }
        return true
    }

    private fun observeViewModel() {
        viewModel.appInfo.observe(this) { app ->
            app ?: return@observe
            if (app.icon != null) {
                binding.appIcon.setImageDrawable(app.icon)
            } else {
                try {
                    val icon = packageManager.getApplicationIcon(app.packageName)
                    binding.appIcon.setImageDrawable(icon)
                } catch (e: Exception) {
                    binding.appIcon.setImageResource(android.R.drawable.ic_dialog_info)
                }
            }
            binding.appName.text = app.appName
            binding.packageName.text = app.packageName
            binding.appVersion.text = "v${app.versionName} (${app.versionCode})"

            binding.btnDisableEnable.text = if (app.isEnabled) {
                getString(R.string.disable_app)
            } else {
                getString(R.string.enable_app)
            }

            binding.infoContainer.removeAllViews()
            val currentLocale = getCurrentLocale()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", currentLocale)
            addInfoRow(getString(R.string.install_date), dateFormat.format(Date(app.installTime)))
            addInfoRow(getString(R.string.update_date), dateFormat.format(Date(app.updateTime)))
            addInfoRow(getString(R.string.target_sdk), "API ${app.targetSdkVersion}")
            addInfoRow(getString(R.string.min_sdk), "API ${app.minSdkVersion}")
            addInfoRow(getString(R.string.package_name), app.packageName)
        }

        viewModel.permissions.observe(this) { perms ->
            if (perms.isNotEmpty()) {
                permAdapter.submitList(perms)
                binding.permCount.text = "${perms.count { it.isGranted }}/${perms.size}"
            } else {
                viewModel.loadApp(packageName)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.operationResult.observe(this) { result ->
            result ?: return@observe
            val msg = if (result.success) {
                getString(R.string.operation_success)
            } else {
                val errorMsg = result.error.ifEmpty { "Operation failed" }
                getString(R.string.operation_failed, errorMsg)
            }
            val duration = if (result.success) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG
            Snackbar.make(binding.root, msg, duration).show()
            viewModel.clearOperationResult()
            if (!result.success) {
                LogManager.error(this, "Operation failed", msg)
            }
        }
    }

    private fun getCurrentLocale(): Locale {
        val savedLanguage = LocaleManager.getLanguage(this)
        return if (savedLanguage.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(savedLanguage)
        }
    }

    private fun addInfoRow(label: String, value: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_info_row, binding.infoContainer, false)
        row.findViewById<TextView>(R.id.label).text = label
        row.findViewById<TextView>(R.id.value).text = value
        binding.infoContainer.addView(row)
    }
}