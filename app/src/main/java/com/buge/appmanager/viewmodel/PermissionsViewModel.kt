package com.buge.appmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.buge.appmanager.data.AppRepository
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.shizuku.ShizukuResult
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import kotlinx.coroutines.launch

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)

    private val _appsWithPermission = MutableLiveData<List<Pair<AppInfo, Map<String, Boolean>>>>()
    val appsWithPermission: LiveData<List<Pair<AppInfo, Map<String, Boolean>>>> = _appsWithPermission

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _operationResult = MutableLiveData<ShizukuResult?>()
    val operationResult: LiveData<ShizukuResult?> = _operationResult
    private val _systemOpBlocked = MutableLiveData<Boolean>()
    val systemOpBlocked: LiveData<Boolean> = _systemOpBlocked

    private var currentPermissions: List<String> = emptyList()

    fun loadAppsForPermissions(permissions: List<String>) {
        currentPermissions = permissions
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val showSystemApps = PreferencesManager.getShowSystemApps(getApplication())
                val allApps = repository.getInstalledApps(showSystemApps = showSystemApps)
                val result = mutableListOf<Pair<AppInfo, Map<String, Boolean>>>()
                
                for (app in allApps) {
                    val appPermMap = mutableMapOf<String, Boolean>()
                    var hasAnyPermission = false
                    
                    for (targetPerm in permissions) {
                        // Check if app has this permission in its manifest
                        val hasPermissionInManifest = repository.hasPermissionInManifest(app.packageName, targetPerm)
                        
                        if (hasPermissionInManifest) {
                            val isGranted = when (targetPerm) {
                                "android.permission.WRITE_SETTINGS" -> {
                                    ShizukuManager.getWriteSettingsStatus(app.packageName) ?: false
                                }
                                "android.permission.SYSTEM_ALERT_WINDOW" -> {
                                    ShizukuManager.getOverlayStatus(app.packageName) ?: false
                                }
                                "android.permission.REQUEST_INSTALL_PACKAGES" -> {
                                    ShizukuManager.getInstallUnknownAppsStatus(app.packageName) ?: false
                                }
                                "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                                    ShizukuManager.getManageExternalStorageStatus(app.packageName) ?: false
                                }
                                else -> {
                                    val allPerms = repository.getAppPermissions(app.packageName)
                                    allPerms.find { it.name == targetPerm }?.isGranted ?: false
                                }
                            }
                            appPermMap[targetPerm] = isGranted
                            hasAnyPermission = true
                        }
                    }
                    
                    if (hasAnyPermission) {
                        result.add(Pair(app, appPermMap))
                    }
                }
                
                _appsWithPermission.value = result.sortedBy { it.first.appName.lowercase() }
                LogManager.info(getApplication(), "Loaded apps for permissions", "Permissions: $permissions, Count: ${result.size}")
            } catch (e: Exception) {
                _appsWithPermission.value = emptyList()
                LogManager.error(getApplication(), "Failed to load apps for permissions", e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun revokePermission(packageName: String, permission: String, isSystemApp: Boolean) {
        if (isSystemApp && !PreferencesManager.getAllowSystemOps(getApplication())) {
            _systemOpBlocked.value = true
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.revokePermission(packageName, permission)
            _operationResult.value = result
            if (result.success) {
                loadAppsForPermissions(currentPermissions)
                LogManager.permission(getApplication(), "Permission revoked", "Package: $packageName, Permission: $permission")
            } else {
                LogManager.error(getApplication(), "Failed to revoke permission", "Package: $packageName, Permission: $permission, Error: ${result.error}")
                _isLoading.value = false
            }
        }
    }

    fun grantPermission(packageName: String, permission: String, isSystemApp: Boolean) {
        if (isSystemApp && !PreferencesManager.getAllowSystemOps(getApplication())) {
            _systemOpBlocked.value = true
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.grantPermission(packageName, permission)
            _operationResult.value = result
            if (result.success) {
                loadAppsForPermissions(currentPermissions)
                LogManager.permission(getApplication(), "Permission granted", "Package: $packageName, Permission: $permission")
            } else {
                LogManager.error(getApplication(), "Failed to grant permission", "Package: $packageName, Permission: $permission, Error: ${result.error}")
                _isLoading.value = false
            }
        }
    }

    fun batchRevokePermission(apps: List<Pair<AppInfo, Map<String, Boolean>>>, permission: String) {
        val hasSystemApp = apps.any { it.first.isSystemApp }
        if (hasSystemApp && !PreferencesManager.getAllowSystemOps(getApplication())) {
            _systemOpBlocked.value = true
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            var successCount = 0
            var failCount = 0
            for ((app, _) in apps) {
                val result = ShizukuManager.revokePermission(app.packageName, permission)
                if (result.success) successCount++ else failCount++
            }
            _operationResult.value = ShizukuResult(
                failCount == 0,
                "Success: $successCount, Failed: $failCount",
                if (failCount > 0) "$failCount operations failed" else ""
            )
            if (failCount == 0) {
                LogManager.success(getApplication(), "Batch revoke permission completed", "Permission: $permission, Apps: ${apps.size}")
            } else {
                LogManager.error(getApplication(), "Batch revoke permission partially failed", "Permission: $permission, Success: $successCount, Failed: $failCount")
            }
            loadAppsForPermissions(currentPermissions)
        }
    }

    fun batchGrantPermission(apps: List<Pair<AppInfo, Map<String, Boolean>>>, permission: String) {
        val hasSystemApp = apps.any { it.first.isSystemApp }
        if (hasSystemApp && !PreferencesManager.getAllowSystemOps(getApplication())) {
            _systemOpBlocked.value = true
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            var successCount = 0
            var failCount = 0
            for ((app, _) in apps) {
                val result = ShizukuManager.grantPermission(app.packageName, permission)
                if (result.success) successCount++ else failCount++
            }
            _operationResult.value = ShizukuResult(
                failCount == 0,
                "Success: $successCount, Failed: $failCount",
                if (failCount > 0) "$failCount operations failed" else ""
            )
            if (failCount == 0) {
                LogManager.success(getApplication(), "Batch grant permission completed", "Permission: $permission, Apps: ${apps.size}")
            } else {
                LogManager.error(getApplication(), "Batch grant permission partially failed", "Permission: $permission, Success: $successCount, Failed: $failCount")
            }
            loadAppsForPermissions(currentPermissions)
        }
    }

    fun clearSystemOpBlocked() {
        _systemOpBlocked.value = false
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }
}