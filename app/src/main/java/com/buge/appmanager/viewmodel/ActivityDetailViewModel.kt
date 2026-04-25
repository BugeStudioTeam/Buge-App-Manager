package com.buge.appmanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.buge.appmanager.data.ActivityRepository
import com.buge.appmanager.model.ActivityDetail
import kotlinx.coroutines.launch

class ActivityDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ActivityRepository(application)

    private val _activities = MutableLiveData<List<ActivityDetail>>()
    val activities: LiveData<List<ActivityDetail>> = _activities

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadActivities(packageName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val activities = repository.getAppActivities(packageName)
                _activities.value = activities
            } catch (e: Exception) {
                _activities.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}