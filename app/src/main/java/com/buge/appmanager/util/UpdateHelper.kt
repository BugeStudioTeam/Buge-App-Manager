package com.buge.appmanager.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.buge.appmanager.shizuku.ShizukuManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object UpdateHelper {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    private var progressDialog: androidx.appcompat.app.AlertDialog? = null
    private var tempApkFile: File? = null
    private var isDownloading = false

    fun startSilentUpdate(context: Context, apkUrl: String, installerName: String) {
        if (isDownloading) {
            showToast(context, "Download already in progress")
            return
        }

        isDownloading = true

        showProgressDialog(context)

        GlobalScope.launch {
            try {
                val apkFile = downloadApk(context, apkUrl)
                if (apkFile != null) {
                    tempApkFile = apkFile
                    installApk(context, apkFile, installerName)
                } else {
                    dismissProgressDialog()
                    showToast(context, "Download failed")
                    isDownloading = false
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                showToast(context, "Download failed: ${e.message}")
                LogManager.error(context, "Silent update failed", e.message)
                isDownloading = false
            }
        }
    }

    private suspend fun downloadApk(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            if (cacheDir == null) {
                LogManager.error(context, "Cache dir is null")
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
                LogManager.error(context, "Download failed", "Response code: ${response.code}")
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            if (apkFile.exists() && apkFile.length() > 0) {
                val fis = java.io.FileInputStream(apkFile)
                val header = ByteArray(4)
                fis.read(header)
                fis.close()

                if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                    // Verify this APK belongs to this app before installing
                    val pm = context.packageManager
                    val apkInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
                    if (apkInfo == null || apkInfo.packageName != context.packageName) {
                        LogManager.error(context, "APK package mismatch",
                            "Expected: ${context.packageName}, Got: ${apkInfo?.packageName ?: "null"}")
                        apkFile.delete()
                        return@withContext null
                    }
                    LogManager.success(context, "APK downloaded", "Size: ${apkFile.length()}")
                    return@withContext apkFile
                } else {
                    LogManager.error(context, "Invalid APK file")
                    apkFile.delete()
                    return@withContext null
                }
            } else {
                LogManager.error(context, "APK download incomplete")
                apkFile.delete()
                return@withContext null
            }
        } catch (e: Exception) {
            LogManager.error(context, "Download APK failed", e.message)
            return@withContext null
        }
    }

    private suspend fun installApk(context: Context, apkFile: File, installerName: String) {
        withContext(Dispatchers.IO) {
            try {
                val path = apkFile.absolutePath
                val tempPath = "/data/local/tmp/update_temp.apk"

                // Copy APK to /data/local/tmp/
                val copyCmd = "cat \"$path\" > $tempPath"
                val copyResult = ShizukuManager.executeCommand(copyCmd)

                if (!copyResult.success) {
                    dismissProgressDialog()
                    showToast(context, "Failed to copy APK: ${copyResult.error}")
                    LogManager.error(context, "Copy failed", copyResult.error)
                    return@withContext
                }

                // Security: Validate installerName before interpolating into shell command.
                // Only alphanumeric, dots, dashes, underscores, spaces are safe.
                val safeName = if (installerName.isNotEmpty()) {
                    if (installerName.matches(Regex("^[a-zA-Z0-9._\\- ]*$"))) {
                        installerName
                    } else {
                        LogManager.warning(context, "Rejected unsafe installer name, installing without -i",
                            installerName)
                        ""
                    }
                } else ""

                // Build install command with proper -i format
                val installCmd = if (safeName.isNotEmpty()) {
                    "pm install -r -d -t --user 0 -i \"$safeName\" $tempPath"
                } else {
                    "pm install -r -d -t --user 0 $tempPath"
                }

                LogManager.info(context, "Installing APK", "Command: $installCmd")

                // Execute with detailed output capture
                val installResult = ShizukuManager.executeCommand(installCmd)

                // Log full output for debugging
                LogManager.debug(context, "Install output", "Success: ${installResult.success}, Error: ${installResult.error}, Output: ${installResult.output}")

                // Clean up temp file
                ShizukuManager.executeCommand("rm -f $tempPath")
                apkFile.delete()

                dismissProgressDialog()

                if (installResult.success) {
                    showToast(context, "Update installed successfully")
                    LogManager.success(context, "Update installed", "Installer: $safeName")
                } else {
                    // Try without -i flag as fallback
                    if (safeName.isNotEmpty()) {
                        LogManager.warning(context, "Install with -i failed, retrying without -i")
                        val retryCmd = "pm install -r -d -t --user 0 $tempPath"
                        val retryResult = ShizukuManager.executeCommand(retryCmd)

                        if (retryResult.success) {
                            showToast(context, "Update installed successfully (without installer name)")
                            LogManager.success(context, "Update installed (fallback)", "Without installer name")
                            return@withContext
                        }

                        val errorMsg = retryResult.error.ifEmpty {
                            retryResult.output.ifEmpty { "Unknown error" }
                        }
                        showToast(context, "Install failed: $errorMsg")
                        LogManager.error(context, "Install failed (fallback)", errorMsg)
                    } else {
                        val errorMsg = installResult.error.ifEmpty {
                            installResult.output.ifEmpty { "Unknown error" }
                        }
                        showToast(context, "Install failed: $errorMsg")
                        LogManager.error(context, "Install failed", errorMsg)
                    }
                }
            } catch (e: Exception) {
                dismissProgressDialog()
                showToast(context, "Install failed: ${e.message}")
                LogManager.error(context, "Install exception", e.message)
            } finally {
                isDownloading = false
            }
        }
    }

    private fun showProgressDialog(context: Context) {
        Handler(Looper.getMainLooper()).post {
            val builder = MaterialAlertDialogBuilder(context)
                .setTitle("Downloading update...")
                .setMessage("Please wait while the update is downloaded")
                .setCancelable(false)
                .setNegativeButton("Cancel") { _, _ ->
                    isDownloading = false
                    tempApkFile?.delete()
                    tempApkFile = null
                    dismissProgressDialog()
                }
            progressDialog = builder.show()
        }
    }

    private fun dismissProgressDialog() {
        Handler(Looper.getMainLooper()).post {
            try {
                progressDialog?.dismiss()
            } catch (e: Exception) {
                // Ignore
            }
            progressDialog = null
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                val activity = context as? android.app.Activity
                val rootView = activity?.findViewById<android.view.View>(android.R.id.content)
                if (rootView != null) {
                    SnackbarHelper.showSnackbar(rootView, message)
                }
            } catch (e: Exception) {
                LogManager.debug(context, "Toast failed", e.message)
            }
        }
    }
}