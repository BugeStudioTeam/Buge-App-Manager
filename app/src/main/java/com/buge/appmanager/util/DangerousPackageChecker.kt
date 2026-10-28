// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.content.Context
import android.util.Log

object DangerousPackageChecker {

    private const val TAG = "DangerousPackageChecker"
    private const val ASSET_FILE = "dangerous_packages.txt"

    @Volatile
    private var cachedPackages: Set<String>? = null

    fun isDangerous(context: Context, packageName: String): Boolean {
        return getDangerousPackages(context).contains(packageName)
    }

    fun getDangerousPackages(context: Context): Set<String> {
        cachedPackages?.let { return it }
        synchronized(this) {
            cachedPackages?.let { return it }
            val set = loadFromAssets(context)
            cachedPackages = set
            return set
        }
    }

    fun invalidateCache() {
        cachedPackages = null
    }

    private fun loadFromAssets(context: Context): Set<String> {
        return try {
            val result = mutableSetOf<String>()
            context.assets.open(ASSET_FILE).bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    result.add(line)
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $ASSET_FILE: ${e.message}")
            emptySet()
        }
    }
}