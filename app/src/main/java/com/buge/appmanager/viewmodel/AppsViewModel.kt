package com.buge.appmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.buge.appmanager.data.AppRepository
import com.buge.appmanager.model.AppFilter
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.model.AppSortOrder
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import kotlinx.coroutines.launch

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    var currentFilter = AppFilter.ALL
    var currentSort = AppSortOrder.NAME
    var searchQuery = ""

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val showSystemApps = true
                val showDisabledApps = PreferencesManager.getShowDisabledApps(getApplication())
                val result = repository.getInstalledApps(currentFilter, currentSort, searchQuery, showSystemApps, showDisabledApps)
                _apps.value = result
                LogManager.info(getApplication(), "Apps loaded", "Count: ${result.size}, Filter: $currentFilter, Sort: $currentSort, Search: $searchQuery")
            } catch (e: Exception) {
                _error.value = e.message
                LogManager.error(getApplication(), "Failed to load apps", e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFilter(filter: AppFilter) {
        currentFilter = filter
        loadApps()
    }

    fun setSort(sort: AppSortOrder) {
        currentSort = sort
        loadApps()
    }

    fun setSearch(query: String) {
        searchQuery = query
        loadApps()
    }
}