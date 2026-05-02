package com.buge.appmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.buge.appmanager.data.AppRepository
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.model.PermissionInfo
import com.buge.appmanager.shizuku.ShizukuManager
import com.buge.appmanager.shizuku.ShizukuResult
import com.buge.appmanager.util.LogManager
import kotlinx.coroutines.launch

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
                    _permissions.value = if (perms.isEmpty()) {
                        repository.getAppPermissions(packageName)
                    } else {
                        perms
                    }
                    LogManager.info(getApplication(), "App loaded", "Package: $packageName, Name: ${app.appName}")
                }
            } catch (e: Exception) {
                _appInfo.value = null
                LogManager.error(getApplication(), "Failed to load app", "Package: $packageName, Error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

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
                val perms = repository.getAppPermissions(currentPackageName)
                _permissions.value = perms
                LogManager.success(getApplication(), "Permission toggled", "Package: $currentPackageName, Permission: $permissionName, Granted: ${!currentlyGranted}")
            } else {
                LogManager.error(getApplication(), "Failed to toggle permission", "Package: $currentPackageName, Permission: $permissionName, Error: ${result.error}")
            }
            _isLoading.value = false
        }
    }

    fun forceStop() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.forceStop(currentPackageName)
            _operationResult.value = result
            if (result.success) {
                LogManager.info(getApplication(), "Force stop executed", "Package: $currentPackageName")
            } else {
                LogManager.error(getApplication(), "Force stop failed", "Package: $currentPackageName, Error: ${result.error}")
            }
            _isLoading.value = false
        }
    }

    fun clearData() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.clearData(currentPackageName)
            _operationResult.value = result
            if (result.success) {
                LogManager.info(getApplication(), "Clear data executed", "Package: $currentPackageName")
            } else {
                LogManager.error(getApplication(), "Clear data failed", "Package: $currentPackageName, Error: ${result.error}")
            }
            _isLoading.value = false
        }
    }

    fun disableApp() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.disableApp(currentPackageName)
            _operationResult.value = result
            if (result.success) {
                loadApp(currentPackageName)
                LogManager.info(getApplication(), "App disabled", "Package: $currentPackageName")
            } else {
                LogManager.error(getApplication(), "Disable app failed", "Package: $currentPackageName, Error: ${result.error}")
                _isLoading.value = false
            }
        }
    }

    fun enableApp() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.enableApp(currentPackageName)
            _operationResult.value = result
            if (result.success) {
                loadApp(currentPackageName)
                LogManager.info(getApplication(), "App enabled", "Package: $currentPackageName")
            } else {
                LogManager.error(getApplication(), "Enable app failed", "Package: $currentPackageName, Error: ${result.error}")
                _isLoading.value = false
            }
        }
    }

    fun uninstallApp() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.uninstallApp(currentPackageName)
            _operationResult.value = result
            if (result.success) {
                LogManager.info(getApplication(), "App uninstalled", "Package: $currentPackageName")
            } else {
                LogManager.error(getApplication(), "Uninstall app failed", "Package: $currentPackageName, Error: ${result.error}")
            }
            _isLoading.value = false
        }
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }
}