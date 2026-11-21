// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import com.buge.appmanager.databinding.ActivityLogViewerBinding
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class LogViewerActivity : BaseActivity() {

    private lateinit var binding: ActivityLogViewerBinding

    private val selectedTypes = mutableSetOf<LogManager.LogType>()

    private val logUpdateListener = {
        runOnUiThread {
            updateLogDisplay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadFilterTypes()

        setupToolbar()
        setupLogSwitch()
        updateLogDisplay()

        LogManager.addListener(logUpdateListener)
    }

    override fun onResume() {
        super.onResume()
        LogManager.refreshLogcat(this)
        updateLogDisplay()
    }

    private fun loadFilterTypes() {
        val saved = PreferencesManager.getLogFilterTypes(this)
        if (saved == null) {
            selectedTypes.addAll(LogManager.LogType.values().toList())
        } else {
            for (name in saved) {
                try {
                    selectedTypes.add(LogManager.LogType.valueOf(name))
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun saveFilterTypes() {
        PreferencesManager.setLogFilterTypes(this, selectedTypes.map { it.name }.toSet())
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.log_activity_title)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Filter")
        menu.add(0, 2, 1, "Clear")
        menu.add(0, 3, 2, "Copy")
        menu.add(0, 4, 3, "Export TXT")
        menu.add(0, 5, 4, "Export CSV")
        menu.add(0, 6, 5, "Statistics")
        menu.add(0, 7, 6, "Refresh Logcat")
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            1 -> {
                showFilterDialog()
                return true
            }
            2 -> {
                showClearLogsDialog()
                return true
            }
            3 -> {
                copyLogsToClipboard()
                return true
            }
            4 -> {
                exportLogsTxt()
                return true
            }
            5 -> {
                exportLogsCsv()
                return true
            }
            6 -> {
                showStatisticsDialog()
                return true
            }
            7 -> {
                refreshLogcat()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showFilterDialog() {
        val types = LogManager.LogType.values()
        val labels = types.map { it.display }.toTypedArray()
        val checked = types.map { selectedTypes.contains(it) }.toBooleanArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.log_filter_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val type = types[which]
                if (isChecked) {
                    selectedTypes.add(type)
                } else {
                    selectedTypes.remove(type)
                }
            }
            .setPositiveButton(R.string.confirm) { _, _ ->
                saveFilterTypes()
                updateLogDisplay()
            }
            .setNeutralButton(R.string.log_filter_select_all) { _, _ ->
                selectedTypes.clear()
                selectedTypes.addAll(types.toList())
                saveFilterTypes()
                updateLogDisplay()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshLogcat() {
        SnackbarHelper.showSnackbar(binding.root, "Refreshing Logcat...", Snackbar.LENGTH_SHORT)
        LogManager.refreshLogcat(this)
        updateLogDisplay()
    }

    private fun showClearLogsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear Logs")
            .setMessage("Are you sure you want to clear all logs? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                LogManager.clearLogs(this)
                SnackbarHelper.showSnackbar(binding.root, getString(R.string.log_cleared))
                updateLogDisplay()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showStatisticsDialog() {
        val stats = LogManager.getStatistics()
        val totalLogs = stats["totalLogs"] as Int
        val logcatCount = stats["logcatCount"] as Int
        val typeCounts = stats["typeCounts"] as Map<LogManager.LogType, Int>
        val timeRangeSeconds = stats["timeRangeSeconds"] as Long
        val sessionId = stats["sessionId"] as Long

        val message = buildString {
            appendLine("=== Log Statistics ===")
            appendLine()
            appendLine("Session ID: $sessionId")
            appendLine("Total Logs: $totalLogs")
            appendLine("Logcat Entries: $logcatCount")
            appendLine("App Logs: ${totalLogs - logcatCount}")
            appendLine("Time Range: ${formatTimeRange(timeRangeSeconds)}")
            appendLine()
            appendLine("--- By Type ---")
            for ((type, count) in typeCounts) {
                if (count > 0) {
                    appendLine("${type.display}: $count")
                }
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Log Statistics")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatTimeRange(seconds: Long): String {
        if (seconds <= 0) return "N/A"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            append("${secs}s")
        }
    }

    private fun exportLogsTxt() {
        val file = LogManager.exportLogs(this)
        if (file != null) {
            SnackbarHelper.showSnackbar(binding.root, "Logs exported to: ${file.absolutePath}", Snackbar.LENGTH_LONG)
        } else {
            SnackbarHelper.showSnackbar(binding.root, "No logs to export", Snackbar.LENGTH_SHORT)
        }
    }

    private fun exportLogsCsv() {
        val file = LogManager.exportLogsAsCsv(this)
        if (file != null) {
            SnackbarHelper.showSnackbar(binding.root, "Logs exported to: ${file.absolutePath}", Snackbar.LENGTH_LONG)
        } else {
            SnackbarHelper.showSnackbar(binding.root, "No logs to export", Snackbar.LENGTH_SHORT)
        }
    }

    private fun copyLogsToClipboard() {
        val logs = LogManager.getLogs().filter { selectedTypes.contains(it.type) }
        if (logs.isEmpty()) {
            SnackbarHelper.showSnackbar(binding.root, getString(R.string.log_empty))
            return
        }
        val text = logs.joinToString("\n") { it.toLine() }
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("App Logs", text)
        clipboard.setPrimaryClip(clip)
        SnackbarHelper.showSnackbar(binding.root, getString(R.string.log_copied))
    }

    private fun setupLogSwitch() {
        val logSwitch = binding.logSwitch
        logSwitch.isChecked = LogManager.isEnabled()
        logSwitch.setOnCheckedChangeListener { _, isChecked ->
            LogManager.setEnabled(this, isChecked)
        }
    }

    private fun updateLogDisplay() {
        val logs = LogManager.getLogs().filter { selectedTypes.contains(it.type) }
        if (logs.isEmpty()) {
            binding.logText.text = ""
            binding.logScrollView.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            return
        }

        binding.logScrollView.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE

        val builder = SpannableStringBuilder()
        logs.forEachIndexed { index, entry ->
            val line = entry.toLine()
            val start = builder.length
            builder.append(line)
            val end = builder.length

            val color = colorForType(entry.type)
            if (color != null) {
                builder.setSpan(
                    ForegroundColorSpan(color),
                    start,
                    end,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (index < logs.size - 1) {
                builder.append("\n")
            }
        }

        binding.logText.text = builder
    }

    private fun colorForType(type: LogManager.LogType): Int? {
        return when (type) {
            LogManager.LogType.ERROR -> ContextCompat.getColor(this, R.color.color_denied)
            LogManager.LogType.WARNING -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            LogManager.LogType.DEBUG -> ContextCompat.getColor(this, R.color.log_debug)
            LogManager.LogType.INFO -> ContextCompat.getColor(this, R.color.md_theme_primary)
            LogManager.LogType.VERBOSE -> ContextCompat.getColor(this, android.R.color.darker_gray)
            LogManager.LogType.SUCCESS -> ContextCompat.getColor(this, R.color.color_granted)
            LogManager.LogType.PERMISSION -> ContextCompat.getColor(this, R.color.color_granted)
            LogManager.LogType.PERMISSION_CHANGE -> ContextCompat.getColor(this, R.color.color_granted)
            LogManager.LogType.SHIZUKU -> ContextCompat.getColor(this, R.color.color_granted)
            LogManager.LogType.ACTIVITY -> ContextCompat.getColor(this, R.color.color_granted)
            LogManager.LogType.UI -> ContextCompat.getColor(this, R.color.md_theme_primary)
            LogManager.LogType.STORAGE -> ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            LogManager.LogType.NETWORK -> ContextCompat.getColor(this, android.R.color.holo_purple)
            LogManager.LogType.LOGCAT -> ContextCompat.getColor(this, android.R.color.darker_gray)
            else -> null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LogManager.removeListener(logUpdateListener)
    }
}
