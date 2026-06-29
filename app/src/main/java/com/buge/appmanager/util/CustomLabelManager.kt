package com.buge.appmanager.util

import android.content.Context
import com.buge.appmanager.model.CustomLabel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

object CustomLabelManager {

    private const val PREF_NAME = "custom_labels"
    private const val KEY_LABELS = "labels"

    private val gson = Gson()

    fun getLabels(context: Context): List<CustomLabel> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LABELS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<CustomLabel>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLabels(context: Context, labels: List<CustomLabel>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(labels)
        prefs.edit().putString(KEY_LABELS, json).apply()
    }

    fun createLabel(context: Context, name: String): CustomLabel {
        val labels = getLabels(context).toMutableList()
        val label = CustomLabel(
            id = UUID.randomUUID().toString(),
            name = name
        )
        labels.add(label)
        saveLabels(context, labels)
        LogManager.info(context, "Custom label created", "Name: $name")
        return label
    }

    fun updateLabel(context: Context, label: CustomLabel) {
        val labels = getLabels(context).toMutableList()
        val index = labels.indexOfFirst { it.id == label.id }
        if (index >= 0) {
            labels[index] = label
            saveLabels(context, labels)
            LogManager.info(context, "Custom label updated", "Name: ${label.name}, Apps: ${label.appPackages.size}")
        }
    }

    fun deleteLabel(context: Context, labelId: String) {
        val labels = getLabels(context).toMutableList()
        val removed = labels.removeAll { it.id == labelId }
        if (removed) {
            saveLabels(context, labels)
            LogManager.info(context, "Custom label deleted", "LabelId: $labelId")
        }
    }

    fun getLabelById(context: Context, labelId: String): CustomLabel? {
        return getLabels(context).find { it.id == labelId }
    }

    fun getLabelsForApp(context: Context, packageName: String): List<CustomLabel> {
        return getLabels(context).filter { it.appPackages.contains(packageName) }
    }

    fun toggleAppInLabel(context: Context, labelId: String, packageName: String) {
        val labels = getLabels(context).toMutableList()
        val index = labels.indexOfFirst { it.id == labelId }
        if (index >= 0) {
            val label = labels[index]
            val newPackages = if (label.appPackages.contains(packageName)) {
                label.appPackages.filter { it != packageName }
            } else {
                label.appPackages + packageName
            }
            labels[index] = label.copy(appPackages = newPackages)
            saveLabels(context, labels)
        }
    }

    fun isAppInLabel(context: Context, labelId: String, packageName: String): Boolean {
        val label = getLabelById(context, labelId)
        return label?.appPackages?.contains(packageName) ?: false
    }

    fun getAppLabelNames(context: Context, packageName: String): List<String> {
        return getLabels(context)
            .filter { it.appPackages.contains(packageName) }
            .map { it.name }
    }
}