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
                // Get all apps including system apps
                val apps = repository.getInstalledApps(showSystemApps = true)
                val app = apps.find { it.packageName == packageName }
                _appInfo.value = app
                if (app != null) {
                    val perms = repository.getAppPermissions(packageName)
                    // Ensure permissions are loaded even for system apps
                    _permissions.value = if (perms.isEmpty()) {
                        // If no permissions found, try to get them again
                        repository.getAppPermissions(packageName)
                    } else {
                        perms
                    }
                }
            } catch (e: Exception) {
                _appInfo.value = null
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
            }
            _isLoading.value = false
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
