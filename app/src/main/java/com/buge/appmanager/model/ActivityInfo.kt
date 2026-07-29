// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.model

import android.graphics.drawable.Drawable

data class AppActivityInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val activities: List<ActivityDetail>
)

data class ActivityDetail(
    val name: String,
    val className: String,
    val isExported: Boolean,
    val intentFilterCount: Int,
    val permission: String,
    val launchMode: String,
    val parentActivityName: String
)