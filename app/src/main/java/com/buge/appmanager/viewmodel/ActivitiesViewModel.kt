// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ActivityDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ActivityRepository(application)

    private val _activities = MutableLiveData<List<ActivityDetail>>()
    val activities: LiveData<List<ActivityDetail>> = _activities

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loadedCount = MutableLiveData<Int>()
    val loadedCount: LiveData<Int> = _loadedCount

    private val _totalCount = MutableLiveData<Int>()
    val totalCount: LiveData<Int> = _totalCount

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var loadJob: Job? = null

    fun loadActivities(packageName: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _loadedCount.value = 0
            _totalCount.value = 0
            try {
                val showUndeclared = PreferencesManager.getShowUndeclaredActivities(getApplication())
                LogManager.info(getApplication(), "ActivityDetailViewModel loading", "Package: $packageName, ShowUndeclared: $showUndeclared")
                val activities = repository.getAppActivities(
                    packageName = packageName,
                    showUndeclared = showUndeclared,
                    onProgress = { loaded, total ->
                        _loadedCount.postValue(loaded)
                        _totalCount.postValue(total)
                    },
                    isCancelled = { loadJob?.isActive != true }
                )
                _activities.value = activities
                LogManager.info(getApplication(), "ActivityDetailViewModel loaded", "Package: $packageName, Count: ${activities.size}")
            } catch (e: Exception) {
                if (loadJob?.isActive == true) {
                    _error.value = e.message
                    LogManager.error(getApplication(), "ActivityDetailViewModel load error", "Package: $packageName, Error: ${e.message}")
                    _activities.value = emptyList()
                }
            } finally {
                if (loadJob?.isActive == true) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun cancelLoading() {
        loadJob?.cancel()
        _isLoading.value = false
    }
}
