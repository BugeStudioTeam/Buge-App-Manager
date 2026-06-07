package com.buge.appmanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.databinding.ActivityLogViewerBinding
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar

class LogViewerActivity : BaseActivity() {

    private lateinit var binding: ActivityLogViewerBinding
    private lateinit var adapter: LogAdapter

    private val logUpdateListener = {
        runOnUiThread {
            updateLogDisplay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupLogSwitch()
        setupRecyclerView()
        updateLogDisplay()

        LogManager.addListener(logUpdateListener)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.log_activity_title)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Clear")
        menu.add(0, 2, 1, "Copy")
        menu.add(0, 3, 2, "Export TXT")
        menu.add(0, 4, 3, "Export CSV")
        menu.add(0, 5, 4, "Statistics")
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            1 -> {
                showClearLogsDialog()
                return true
            }
            2 -> {
                copyLogsToClipboard()
                return true
            }
            3 -> {
                exportLogsTxt()
                return true
            }
            4 -> {
                exportLogsCsv()
                return true
            }
            5 -> {
                showStatisticsDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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
        val typeCounts = stats["typeCounts"] as Map<LogManager.LogType, Int>
        val timeRangeSeconds = stats["timeRangeSeconds"] as Long
        val sessionId = stats["sessionId"] as Long

        val message = buildString {
            appendLine("=== Log Statistics ===")
            appendLine()
            appendLine("Session ID: $sessionId")
            appendLine("Total Logs: $totalLogs")
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
        val logs = LogManager.getLogs()
        if (logs.isEmpty()) {
            SnackbarHelper.showSnackbar(binding.root, getString(R.string.log_empty))
            return
        }
        val text = logs.joinToString("\n\n") { it.getFormattedMessage() }
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

    private fun setupRecyclerView() {
        adapter = LogAdapter()
        binding.logRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.logRecyclerView.adapter = adapter
    }

    private fun updateLogDisplay() {
        val logs = LogManager.getLogs()
        adapter.submitList(logs)
        val isEmpty = logs.isEmpty()
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.logRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        LogManager.removeListener(logUpdateListener)
    }

    inner class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

        private var items: List<LogManager.LogEntry> = emptyList()

        fun submitList(newItems: List<LogManager.LogEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val logTime: TextView = itemView.findViewById(R.id.log_time)
            private val logType: TextView = itemView.findViewById(R.id.log_type)
            private val logMessage: TextView = itemView.findViewById(R.id.log_message)
            private val logDetails: TextView = itemView.findViewById(R.id.log_details)

            fun bind(entry: LogManager.LogEntry) {
                logTime.text = entry.getDisplayTime()
                logType.text = entry.type.display
                logType.setTextColor(getTypeColor(entry.type))
                logMessage.text = entry.message
                if (!entry.details.isNullOrEmpty()) {
                    logDetails.visibility = View.VISIBLE
                    logDetails.text = entry.details
                } else {
                    logDetails.visibility = View.GONE
                }
            }

            private fun getTypeColor(type: LogManager.LogType): Int {
                return when (type) {
                    LogManager.LogType.INFO -> ContextCompat.getColor(itemView.context, R.color.color_granted)
                    LogManager.LogType.WARNING -> ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                    LogManager.LogType.ERROR -> ContextCompat.getColor(itemView.context, R.color.color_denied)
                    LogManager.LogType.SUCCESS -> ContextCompat.getColor(itemView.context, R.color.color_granted)
                    LogManager.LogType.PERMISSION -> ContextCompat.getColor(itemView.context, R.color.color_granted)
                    LogManager.LogType.PERMISSION_CHANGE -> ContextCompat.getColor(itemView.context, R.color.color_granted)
                    LogManager.LogType.SHIZUKU -> ContextCompat.getColor(itemView.context, R.color.color_granted)
                    LogManager.LogType.DEBUG -> ContextCompat.getColor(itemView.context, android.R.color.holo_blue_dark)
                    LogManager.LogType.VERBOSE -> ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
                    LogManager.LogType.ACTIVITY -> ContextCompat.getColor(itemView.context, R.color.color_granted)
                    LogManager.LogType.UI -> ContextCompat.getColor(itemView.context, R.color.color_granted)
                    LogManager.LogType.STORAGE -> ContextCompat.getColor(itemView.context, android.R.color.holo_blue_dark)
                    LogManager.LogType.NETWORK -> ContextCompat.getColor(itemView.context, android.R.color.holo_purple)
                    else -> ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
                }
            }
        }
    }
}