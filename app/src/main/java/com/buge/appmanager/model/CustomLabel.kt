package com.buge.appmanager.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CustomLabel(
    val id: String,
    val name: String,
    val appPackages: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable