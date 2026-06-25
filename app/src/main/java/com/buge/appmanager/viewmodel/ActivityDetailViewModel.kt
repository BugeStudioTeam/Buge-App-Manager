package com.buge.appmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.buge.appmanager.data.ActivityRepository
import com.buge.appmanager.model.ActivityDetail
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import kotlinx.coroutines.launch

class ActivityDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ActivityRepository(application)

    private val _activities = MutableLiveData<List<ActivityDetail>>()
    val activities: LiveData<List<ActivityDetail>> = _activities

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadActivities(packageName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val showUndeclared = PreferencesManager.getShowUndeclaredActivities(getApplication())
                LogManager.info(getApplication(), "ActivityDetailViewModel loading", "Package: $packageName, ShowUndeclared: $showUndeclared")
                val activities = repository.getAppActivities(packageName, showUndeclared)
                _activities.value = activities
                LogManager.info(getApplication(), "ActivityDetailViewModel loaded", "Package: $packageName, Count: ${activities.size}")
            } catch (e: Exception) {
                _error.value = e.message
                LogManager.error(getApplication(), "ActivityDetailViewModel load error", "Package: $packageName, Error: ${e.message}")
                _activities.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}