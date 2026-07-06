package com.buge.appmanager

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.buge.appmanager.databinding.ActivityUpdateOptionsBinding
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.buge.appmanager.util.UpdateChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateOptionsActivity : BaseActivity() {

    private lateinit var binding: ActivityUpdateOptionsBinding
    private var updateMethod: UpdateMethod = UpdateMethod.BROWSER
    private var updateSource: String = "GitHub"
    private var downloadId: Long = -1
    private var tempApkFile: File? = null
    private var isDownloading = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private enum class UpdateMethod {
        BROWSER, SILENT
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == downloadId) {
                handleDownloadComplete()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUpdateMethodSelector()
        setupUpdateSourceSelector()
        setupInstallerName()
        setupButtons()
        registerDownloadReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        cleanupTempFile()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.title_update_options)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupUpdateMethodSelector() {
        val savedMethod = PreferencesManager.getUpdateMethod(this)
        updateMethod = if (savedMethod == "silent") UpdateMethod.SILENT else UpdateMethod.BROWSER
        updateMethodDisplay()

        binding.containerUpdateMethod.setOnClickListener {
            showUpdateMethodDialog()
        }
    }

    private fun updateMethodDisplay() {
        val text = when (updateMethod) {
            UpdateMethod.BROWSER -> getString(R.string.update_method_browser)
            UpdateMethod.SILENT -> getString(R.string.update_method_silent)
        }
        binding.updateMethodValue.text = text
    }

    private fun showUpdateMethodDialog() {
        val options = arrayOf(
            getString(R.string.update_method_browser),
            getString(R.string.update_method_silent)
        )
        val currentIndex = when (updateMethod) {
            UpdateMethod.BROWSER -> 0
            UpdateMethod.SILENT -> 1
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Update Method")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val method = when (which) {
                    0 -> UpdateMethod.BROWSER
                    1 -> UpdateMethod.SILENT
                    else -> UpdateMethod.BROWSER
                }
                updateMethod = method
                PreferencesManager.setUpdateMethod(this, if (method == UpdateMethod.SILENT) "silent" else "browser")
                updateMethodDisplay()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupUpdateSourceSelector() {
        val savedSource = PreferencesManager.getUpdateSource(this)
        updateSource = if (savedSource.isNotEmpty()) savedSource else "GitHub"
        updateSourceDisplay()

        binding.containerUpdateSource.setOnClickListener {
            showUpdateSourceDialog()
        }
    }

    private fun updateSourceDisplay() {
        binding.updateSourceValue.text = updateSource
    }

    private fun showUpdateSourceDialog() {
        val options = arrayOf("GitHub")
        val currentIndex = 0

        MaterialAlertDialogBuilder(this)
            .setTitle("Installation Source")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                updateSource = options[which]
                PreferencesManager.setUpdateSource(this, updateSource)
                updateSourceDisplay()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupInstallerName() {
        val savedName = PreferencesManager.getInstallerName(this)
        if (savedName.isNotEmpty()) {
            binding.installerNameInput.setText(savedName)
        }
        binding.installerNameInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val name = binding.installerNameInput.text?.toString()?.trim() ?: ""
                PreferencesManager.setInstallerName(this, name)
            }
        }
    }

    private fun setupButtons() {
        binding.btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        val installerName = binding.installerNameInput.text?.toString()?.trim() ?: ""
        PreferencesManager.setInstallerName(this, installerName)
        PreferencesManager.setUpdateMethod(this, if (updateMethod == UpdateMethod.SILENT) "silent" else "browser")
        PreferencesManager.setUpdateSource(this, updateSource)
        SnackbarHelper.showSnackbar(binding.root, "Settings saved")
        LogManager.info(this, "Update settings saved", "Method: $updateMethod, Source: $updateSource, Installer: $installerName")
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }

    private fun checkForUpdate() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.btnCheckUpdate.isEnabled = false
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val releaseInfo = UpdateChecker.checkForUpdates(this@UpdateOptionsActivity)
                binding.loadingOverlay.visibility = View.GONE
                binding.btnCheckUpdate.isEnabled = true
                binding.btnSave.isEnabled = true

                if (releaseInfo == null) {
                    showAlreadyLatestDialog()
                    return@launch
                }

                showUpdateFoundDialog(releaseInfo)
            } catch (e: Exception) {
                binding.loadingOverlay.visibility = View.GONE
                binding.btnCheckUpdate.isEnabled = true
                binding.btnSave.isEnabled = true
                SnackbarHelper.showSnackbar(binding.root, "Check failed: ${e.message}")
                LogManager.error(this@UpdateOptionsActivity, "Update check failed", e.message)
            }
        }
    }

    private fun showAlreadyLatestDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.no_update_title)
            .setMessage(R.string.no_update_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showUpdateFoundDialog(releaseInfo: UpdateChecker.ReleaseInfo) {
        val releaseNotes = if (releaseInfo.body.length > 300) {
            releaseInfo.body.take(300) + "..."
        } else {
            releaseInfo.body
        }

        val message = buildString {
            appendLine("Version: ${releaseInfo.tagName}")
            appendLine("Published: ${releaseInfo.publishedAt.substringBefore("T")}")
            appendLine()
            appendLine(releaseNotes)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_available)
            .setMessage(message)
            .setPositiveButton(R.string.download) { _, _ ->
                performUpdate(releaseInfo)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performUpdate(releaseInfo: UpdateChecker.ReleaseInfo) {
        val apkUrl = releaseInfo.apkDownloadUrl
        if (apkUrl.isNullOrEmpty()) {
            SnackbarHelper.showSnackbar(binding.root, "No APK download URL available")
            return
        }

        when (updateMethod) {
            UpdateMethod.BROWSER -> {
                openInBrowser(apkUrl)
            }
            UpdateMethod.SILENT -> {
                if (!checkShizuku()) return
                startSilentDownload(apkUrl)
            }
        }
    }

    private fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            LogManager.info(this, "Update opened in browser", "URL: $url")
        } catch (e: Exception) {
            SnackbarHelper.showSnackbar(binding.root, "Cannot open browser: ${e.message}")
            LogManager.error(this, "Failed to open browser", e.message)
        }
    }

    private fun startSilentDownload(url: String) {
        if (isDownloading) return
        isDownloading = true

        val installerName = binding.installerNameInput.text?.toString()?.trim() ?: ""
        if (installerName.isNotEmpty()) {
            PreferencesManager.setInstallerName(this, installerName)
        }

        binding.loadingOverlay.visibility = View.VISIBLE
        binding.btnCheckUpdate.isEnabled = false
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val apkFile = downloadApkWithOkHttp(url)
                if (apkFile != null) {
                    tempApkFile = apkFile
                    installApkViaTemp(apkFile, installerName)
                } else {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnSave.isEnabled = true
                    SnackbarHelper.showSnackbar(binding.root, getString(R.string.update_download_failed))
                }
            } catch (e: Exception) {
                binding.loadingOverlay.visibility = View.GONE
                binding.btnCheckUpdate.isEnabled = true
                binding.btnSave.isEnabled = true
                SnackbarHelper.showSnackbar(binding.root, "Download failed: ${e.message}")
                LogManager.error(this@UpdateOptionsActivity, "Silent download failed", e.message)
            } finally {
                isDownloading = false
            }
        }
    }

    private suspend fun downloadApkWithOkHttp(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = externalCacheDir ?: cacheDir
            if (cacheDir == null) {
                LogManager.error(this@UpdateOptionsActivity, "Cache dir is null")
                return@withContext null
            }

            val fileName = "update_${System.currentTimeMillis()}.apk"
            val apkFile = File(cacheDir, fileName)

            val oldApks = cacheDir.listFiles { file -> file.name.startsWith("update_") && file.name.endsWith(".apk") }
            oldApks?.forEach { if (it.exists()) it.delete() }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BugeAppManager")
                .header("Accept-Encoding", "identity")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                LogManager.error(this@UpdateOptionsActivity, "Download failed", "Response code: ${response.code}")
                return@withContext null
            }

            val contentLength = response.body?.contentLength() ?: -1L
            val totalSize = if (contentLength > 0) contentLength else -1L

            withContext(Dispatchers.Main) {
                showDownloadProgressDialog(totalSize)
            }

            val body = response.body ?: return@withContext null
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastUpdateTime = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime > 1000 || totalBytesRead % (512 * 1024) == 0L) {
                    lastUpdateTime = currentTime
                    if (totalSize > 0) {
                        val progress = (totalBytesRead.toFloat() / totalSize * 100).toInt()
                        withContext(Dispatchers.Main) {
                            updateDownloadProgress(progress, totalBytesRead, totalSize)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            updateDownloadProgressUnknown(totalBytesRead)
                        }
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            withContext(Dispatchers.Main) {
                dismissProgressDialog()
            }

            if (apkFile.exists() && apkFile.length() > 0) {
                val fis = java.io.FileInputStream(apkFile)
                val header = ByteArray(4)
                fis.read(header)
                fis.close()

                if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                    LogManager.success(this@UpdateOptionsActivity, "APK downloaded", "Size: ${apkFile.length()}")
                    return@withContext apkFile
                } else {
                    LogManager.error(this@UpdateOptionsActivity, "Invalid APK file")
                    apkFile.delete()
                    return@withContext null
                }
            } else {
                LogManager.error(this@UpdateOptionsActivity, "APK download incomplete")
                apkFile.delete()
                return@withContext null
            }
        } catch (e: Exception) {
            LogManager.error(this@UpdateOptionsActivity, "Download APK failed", e.message)
            return@withContext null
        }
    }

    private suspend fun installApkViaTemp(apkFile: File, installerName: String) {
        withContext(Dispatchers.IO) {
            try {
                val path = apkFile.absolutePath
                val tempPath = "/data/local/tmp/update_temp.apk"

                val copyCmd = "cat \"$path\" > $tempPath && chmod 644 $tempPath"
                LogManager.debug(this@UpdateOptionsActivity, "Copying APK to temp", copyCmd)
                val copyResult = ShizukuManager.executeCommand(copyCmd)

                if (!copyResult.success) {
                    withContext(Dispatchers.Main) {
                        binding.loadingOverlay.visibility = View.GONE
                        binding.btnCheckUpdate.isEnabled = true
                        binding.btnSave.isEnabled = true
                        SnackbarHelper.showSnackbar(binding.root, "Failed to copy APK: ${copyResult.error}")
                        LogManager.error(this@UpdateOptionsActivity, "Copy failed", copyResult.error)
                    }
                    return@withContext
                }

                val verifyResult = ShizukuManager.executeCommand("ls -la $tempPath")
                LogManager.debug(this@UpdateOptionsActivity, "Temp file verified", "Output: ${verifyResult.output}")

                val installCmd = if (installerName.isNotEmpty()) {
                    "pm install -r -d -t --user 0 -i \"$installerName\" $tempPath 2>&1"
                } else {
                    "pm install -r -d -t --user 0 $tempPath 2>&1"
                }

                LogManager.info(this@UpdateOptionsActivity, "Installing APK", "Command: $installCmd")

                val installResult = ShizukuManager.executeCommand(installCmd)

                val fullOutput = if (installResult.output.isNotEmpty()) installResult.output else installResult.error
                LogManager.debug(this@UpdateOptionsActivity, "Install output", "Success: ${installResult.success}, Output: $fullOutput")

                ShizukuManager.executeCommand("rm -f $tempPath")

                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnSave.isEnabled = true

                    if (installResult.success) {
                        SnackbarHelper.showSnackbar(binding.root, getString(R.string.update_install_success))
                        LogManager.success(this@UpdateOptionsActivity, "Update installed", "Installer: $installerName")
                        apkFile.delete()
                        tempApkFile = null
                    } else {
                        if (fullOutput.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) {
                            LogManager.warning(this@UpdateOptionsActivity, "Signature mismatch")
                            SnackbarHelper.showSnackbar(
                                binding.root,
                                "Signature mismatch: Please uninstall the current version first, or use a signed APK with the same keystore."
                            )
                        } else {
                            val errorMsg = fullOutput.ifEmpty { "Unknown error" }
                            SnackbarHelper.showSnackbar(binding.root, "${getString(R.string.update_install_failed)}: $errorMsg")
                            LogManager.error(this@UpdateOptionsActivity, "Install failed", errorMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.btnCheckUpdate.isEnabled = true
                    binding.btnSave.isEnabled = true
                    SnackbarHelper.showSnackbar(binding.root, "Install failed: ${e.message}")
                    LogManager.error(this@UpdateOptionsActivity, "Install exception", e.message)
                }
            }
        }
    }

    private var progressDialog: AlertDialog? = null
    private var progressDialogView: View? = null

    private fun showDownloadProgressDialog(totalSize: Long) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_download_progress, null)
        progressDialogView = dialogView

        val totalSizeStr = if (totalSize > 0) {
            formatFileSize(totalSize)
        } else {
            "Unknown"
        }

        dialogView.findViewById<TextView>(R.id.size_text)?.text = "Total: $totalSizeStr"
        dialogView.findViewById<TextView>(R.id.progress_text)?.text = "0%"

        progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_downloading)
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel) { _, _ ->
                isDownloading = false
                tempApkFile?.delete()
                tempApkFile = null
                binding.loadingOverlay.visibility = View.GONE
                binding.btnCheckUpdate.isEnabled = true
                binding.btnSave.isEnabled = true
            }
            .show()
    }

    private fun updateDownloadProgress(progress: Int, downloaded: Long, total: Long) {
        progressDialogView?.findViewById<CircularProgressIndicator>(R.id.progress_indicator)?.setProgress(progress)
        progressDialogView?.findViewById<TextView>(R.id.progress_text)?.text = "$progress%"
        if (total > 0) {
            progressDialogView?.findViewById<TextView>(R.id.size_text)?.text = "${formatFileSize(downloaded)} / ${formatFileSize(total)}"
        }
        if (progress % 10 == 0) {
            LogManager.debug(this, "Download progress", "$progress% (${formatFileSize(downloaded)}/${formatFileSize(total)})")
        }
    }

    private fun updateDownloadProgressUnknown(downloaded: Long) {
        progressDialogView?.findViewById<CircularProgressIndicator>(R.id.progress_indicator)?.setProgress(0)
        progressDialogView?.findViewById<TextView>(R.id.progress_text)?.text = "${formatFileSize(downloaded)}"
        progressDialogView?.findViewById<TextView>(R.id.size_text)?.text = "Downloading..."
    }

    private fun dismissProgressDialog() {
        progressDialog?.let {
            try {
                it.dismiss()
            } catch (e: Exception) {
                // Ignore
            }
        }
        progressDialog = null
        progressDialogView = null
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun handleDownloadComplete() {
        // Handled by DownloadManager receiver - we use custom download, not DownloadManager
    }

    private fun cleanupTempFile() {
        tempApkFile?.let {
            if (it.exists()) {
                it.delete()
                LogManager.debug(this, "Cleaned up temp APK", it.name)
            }
            tempApkFile = null
        }
        try {
            val cacheDir = externalCacheDir ?: cacheDir
            cacheDir?.listFiles { file ->
                file.name.startsWith("update_") && file.name.endsWith(".apk")
            }?.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun checkShizuku(): Boolean {
        if (!ShizukuManager.isShizukuAvailable() || !ShizukuManager.hasShizukuPermission()) {
            SnackbarHelper.showSnackbar(
                binding.root,
                getString(R.string.error_no_shizuku),
                getString(R.string.shizuku_request_auth),
                { ShizukuManager.requestShizukuPermission() }
            )
            return false
        }
        return true
    }
}