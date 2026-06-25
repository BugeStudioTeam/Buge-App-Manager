package com.buge.appmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.buge.appmanager.data.ActivityRepository
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import kotlinx.coroutines.launch

class ActivitiesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ActivityRepository(application)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val showSystemApps = PreferencesManager.getShowSystemApps(getApplication())
                LogManager.info(getApplication(), "ActivitiesViewModel loading apps", "ShowSystemApps: $showSystemApps")
                val apps = repository.getInstalledAppsWithActivities(showSystemApps)
                _apps.value = apps
                LogManager.info(getApplication(), "ActivitiesViewModel loaded apps", "Count: ${apps.size}")
            } catch (e: Exception) {
                _error.value = e.message
                LogManager.error(getApplication(), "ActivitiesViewModel load error", e.message)
                _apps.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        LogManager.info(getApplication(), "ActivitiesViewModel refresh", "Refreshing apps")
        loadApps()
    }
}