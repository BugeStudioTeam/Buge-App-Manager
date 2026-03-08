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
import kotlinx.coroutines.launch

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _appsWithPermission = MutableLiveData<List<Pair<AppInfo, Map<String, Boolean>>>>()
    val appsWithPermission: LiveData<List<Pair<AppInfo, Map<String, Boolean>>>> = _appsWithPermission

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _operationResult = MutableLiveData<ShizukuResult?>()
    val operationResult: LiveData<ShizukuResult?> = _operationResult

    private var currentPermissions: List<String> = emptyList()

    fun loadAppsForPermissions(permissions: List<String>) {
        currentPermissions = permissions
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.getAppsWithPermissionCategory(permissions)
                _appsWithPermission.value = result
            } catch (e: Exception) {
                _appsWithPermission.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun revokePermission(packageName: String, permission: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.revokePermission(packageName, permission)
            _operationResult.value = result
            if (result.success) {
                loadAppsForPermissions(currentPermissions)
            } else {
                _isLoading.value = false
            }
        }
    }

    fun grantPermission(packageName: String, permission: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = ShizukuManager.grantPermission(packageName, permission)
            _operationResult.value = result
            if (result.success) {
                loadAppsForPermissions(currentPermissions)
            } else {
                _isLoading.value = false
            }
        }
    }

    fun batchRevokePermission(apps: List<Pair<AppInfo, Map<String, Boolean>>>, permission: String) {
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
            loadAppsForPermissions(currentPermissions)
        }
    }

    fun batchGrantPermission(apps: List<Pair<AppInfo, Map<String, Boolean>>>, permission: String) {
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
            loadAppsForPermissions(currentPermissions)
        }
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }
}
