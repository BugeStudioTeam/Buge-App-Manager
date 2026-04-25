package com.buge.appmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.buge.appmanager.data.ActivityRepository
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.util.PreferencesManager
import kotlinx.coroutines.launch

class ActivitiesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ActivityRepository(application)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val showSystemApps = PreferencesManager.getShowSystemApps(getApplication())
                val apps = repository.getInstalledAppsWithActivities(showSystemApps)
                _apps.value = apps
            } catch (e: Exception) {
                _apps.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadApps()
    }
}