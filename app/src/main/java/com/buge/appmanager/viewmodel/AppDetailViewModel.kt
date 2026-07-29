// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.buge.appmanager.data.AppRepository
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.model.AppStorageInfo
import com.buge.appmanager.model.PermissionInfo
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.shizuku.ShizukuResult
import com.buge.appmanager.util.LogManager
import kotlinx.coroutines.launch
import java.io.File

class AppDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _appInfo = MutableLiveData<AppInfo?>()
    val appInfo: LiveData<AppInfo?> = _appInfo

    private val _permissions = MutableLiveData<List<PermissionInfo>>()
    val permissions: LiveData<List<PermissionInfo>> = _permissions

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _operationResult = MutableLiveData<ShizukuResult?>()
    val operationResult: LiveData<ShizukuResult?> = _operationResult

    private val _storageInfo = MutableLiveData<AppStorageInfo?>()
    val storageInfo: LiveData<AppStorageInfo?> = _storageInfo

    private val _installerAppName = MutableLiveData<String?>()
    val installerAppName: LiveData<String?> = _installerAppName

    private var currentPackageName: String = ""

    fun loadApp(packageName: String) {
        currentPackageName = packageName
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val apps = repository.getInstalledApps(showSystemApps = true)
                val app = apps.find { it.packageName == packageName }
                _appInfo.value = app
                if (app != null) {
                    val perms = repository.getAppPermissions(packageName)
                    _permissions.value = perms
                    loadInstallerInfo(packageName)
                    loadStorageInfo(packageName)
                }
            } catch (e: Exception) {
                _appInfo.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadInstallerInfo(packageName: String) {
        try {
            val installerName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstallSourceInfo(packageName)?.installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
            _installerAppName.value = installerName
        } catch (e: Exception) {
            _installerAppName.value = null
            LogManager.warning(getApplication(), "Failed to get installer info", e.message)
        }
    }

    private suspend fun loadStorageInfo(packageName: String) {
        try {
            var appSize = 0L
            var dataSize = 0L

            // Get app size from APK file
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val apkFile = File(appInfo.sourceDir)
                if (apkFile.exists()) {
                    appSize = apkFile.length()
                }
            } catch (e: Exception) {
                LogManager.warning(getApplication(), "Failed to get APK size", e.message)
            }

            // Get data size using Shizuku
            try {
                val result = ShizukuManager.executeCommand("du -s /data/data/$packageName 2>/dev/null")
                if (result.success && result.output.isNotEmpty()) {
                    val sizeKB = result.output.split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
                    if (sizeKB != null) {
                        dataSize = sizeKB * 1024
                    }
                }
            } catch (e: Exception) {
                LogManager.warning(getApplication(), "Failed to get data size via Shizuku", e.message)
            }

            // Fallback: try to check if data directory exists
            if (dataSize == 0L) {
                try {
                    val result = ShizukuManager.executeCommand("ls -la /data/data/$packageName 2>/dev/null | wc -l")
                    if (result.success && result.output.isNotEmpty()) {
                        val lineCount = result.output.trim().toIntOrNull()
                        if (lineCount != null && lineCount > 1) {
                            dataSize = 1024L * 1024 // 1MB placeholder
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            _storageInfo.value = AppStorageInfo(
                appSize = appSize,
                dataSize = dataSize,
                totalSize = appSize + dataSize
            )
        } catch (e: Exception) {
            LogManager.warning(getApplication(), "Failed to load storage info", e.message)
            _storageInfo.value = null
        }
    }

    private val packageManager: PackageManager
        get() = getApplication<Application>().packageManager

    fun togglePermission(permissionName: String, currentlyGranted: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = if (currentlyGranted) {
                ShizukuManager.revokePermission(currentPackageName, permissionName)
            } else {
                ShizukuManager.grantPermission(currentPackageName, permissionName)
            }
            _operationResult.value = result
            if (result.success) {
                loadApp(currentPackageName)
            } else {
                _isLoading.value = false
            }
        }
    }

    fun forceStop() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.forceStop(currentPackageName)
            _operationResult.value = result
            _isLoading.value = false
        }
    }

    fun clearData() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.clearData(currentPackageName)
            _operationResult.value = result
            _isLoading.value = false
        }
    }

    fun disableApp() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.disableApp(currentPackageName)
            _operationResult.value = result
            if (result.success) loadApp(currentPackageName)
            else _isLoading.value = false
        }
    }

    fun enableApp() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.enableApp(currentPackageName)
            _operationResult.value = result
            if (result.success) loadApp(currentPackageName)
            else _isLoading.value = false
        }
    }

    fun uninstallApp() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.uninstallApp(currentPackageName)
            _operationResult.value = result
            _isLoading.value = false
        }
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }
}