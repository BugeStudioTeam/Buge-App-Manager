package com.buge.appmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.buge.appmanager.data.AppRepository
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.model.CustomLabel
import com.buge.appmanager.util.CustomLabelManager
import com.buge.appmanager.util.LogManager
import kotlinx.coroutines.launch

class LabelDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _label = MutableLiveData<CustomLabel?>()
    val label: LiveData<CustomLabel?> = _label

    private var searchQuery: String = ""
    private var labelId: String = ""
    private var allApps: List<AppInfo> = emptyList()

    fun init(labelId: String) {
        this.labelId = labelId
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val label = CustomLabelManager.getLabelById(getApplication(), labelId)
                _label.value = label

                val apps = repository.getInstalledApps(showSystemApps = true)
                allApps = apps.sortedBy { it.appName.lowercase() }
                applyFilter()

                LogManager.info(getApplication(), "LabelDetailViewModel loaded", "Label: ${label?.name}, Apps: ${allApps.size}")
            } catch (e: Exception) {
                LogManager.error(getApplication(), "LabelDetailViewModel load error", e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearch(query: String) {
        searchQuery = query
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (searchQuery.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        _apps.value = filtered
        LogManager.debug(getApplication(), "applyFilter", "Query: $searchQuery, Results: ${filtered.size}")
    }

    fun refresh() {
        loadData()
    }

    fun toggleAppSelection(packageName: String, isChecked: Boolean) {
        viewModelScope.launch {
            try {
                val currentLabel = _label.value ?: return@launch
                val newPackages = if (isChecked) {
                    if (currentLabel.appPackages.contains(packageName)) return@launch
                    currentLabel.appPackages + packageName
                } else {
                    if (!currentLabel.appPackages.contains(packageName)) return@launch
                    currentLabel.appPackages.filter { it != packageName }
                }
                val updatedLabel = currentLabel.copy(appPackages = newPackages)
                CustomLabelManager.updateLabel(getApplication(), updatedLabel)
                _label.value = updatedLabel
                applyFilter()

                LogManager.debug(getApplication(), "App toggled in label", "Package: $packageName, Selected: $isChecked")
            } catch (e: Exception) {
                LogManager.error(getApplication(), "Toggle selection error", e.message)
            }
        }
    }

    fun isAppSelected(packageName: String): Boolean {
        return _label.value?.appPackages?.contains(packageName) ?: false
    }
}