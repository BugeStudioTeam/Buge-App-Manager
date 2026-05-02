package com.buge.appmanager.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {

    private const val LOG_FILE_NAME = "app_logs.txt"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB

    enum class LogType(val display: String) {
        INFO("INFO"),
        WARNING("WARN"),
        ERROR("ERROR"),
        SUCCESS("SUCCESS"),
        PERMISSION("PERM")
    }

    data class LogEntry(
        val timestamp: Long,
        val type: LogType,
        val message: String,
        val details: String? = null
    ) {
        fun getFormattedMessage(): String {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            val detailsStr = if (details != null) "\n    └─ $details" else ""
            return "[$date] [${type.display}] $message$detailsStr"
        }

        fun getDisplayTime(): String {
            return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        }

        fun getDisplayDate(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private var isEnabled = false
    private var logEntries = mutableListOf<LogEntry>()
    private var listeners = mutableListOf<() -> Unit>()

    fun init(context: Context) {
        isEnabled = PreferencesManager.getLoggingEnabled(context)
        loadLogsFromFile(context)
    }

    fun isEnabled(): Boolean = isEnabled

    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        PreferencesManager.setLoggingEnabled(context, enabled)
        if (!enabled) {
            // 不清空日志，只是停止记录
        }
        notifyListeners()
    }

    fun getLogs(): List<LogEntry> = logEntries.toList()

    fun clearLogs(context: Context) {
        logEntries.clear()
        saveLogsToFile(context)
        notifyListeners()
    }

    fun info(context: Context, message: String, details: String? = null) {
        addLog(context, LogType.INFO, message, details)
    }

    fun warning(context: Context, message: String, details: String? = null) {
        addLog(context, LogType.WARNING, message, details)
    }

    fun error(context: Context, message: String, details: String? = null) {
        addLog(context, LogType.ERROR, message, details)
    }

    fun success(context: Context, message: String, details: String? = null) {
        addLog(context, LogType.SUCCESS, message, details)
    }

    fun permission(context: Context, message: String, details: String? = null) {
        addLog(context, LogType.PERMISSION, message, details)
    }

    private fun addLog(context: Context, type: LogType, message: String, details: String? = null) {
        if (!isEnabled) return

        val entry = LogEntry(System.currentTimeMillis(), type, message, details)
        logEntries.add(0, entry)

        while (logEntries.size > 500) {
            logEntries.removeAt(logEntries.lastIndex)
        }

        saveLogsToFile(context)
        notifyListeners()
    }

    private fun loadLogsFromFile(context: Context) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return

        try {
            val lines = file.readLines()
            logEntries.clear()
        } catch (e: Exception) {
            // 忽略读取错误
        }
    }

    private fun saveLogsToFile(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.length() > MAX_LOG_SIZE) {
                file.delete()
            }
            file.printWriter().use { printer ->
                logEntries.reversed().forEach { entry ->
                    printer.println(entry.getFormattedMessage())
                }
            }
        } catch (e: Exception) {
            // 忽略写入错误
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }
}