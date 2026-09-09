// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {

    private const val LOG_FILE_NAME = "app_logs.txt"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
    private const val MAX_LOG_LINES = 2000

    enum class LogType(val display: String) {
        INFO("INFO"),
        WARNING("WARN"),
        ERROR("ERROR"),
        SUCCESS("SUCCESS"),
        PERMISSION("PERM"),
        DEBUG("DEBUG"),
        VERBOSE("VERBOSE"),
        SHIZUKU("SHIZUKU"),
        ACTIVITY("ACTIVITY"),
        PERMISSION_CHANGE("PERM_CHG"),
        UI("UI"),
        NETWORK("NETWORK"),
        STORAGE("STORAGE"),
        LOGCAT("LOGCAT")
    }

    data class LogEntry(
        val timestamp: Long,
        val type: LogType,
        val message: String,
        val details: String? = null,
        val tag: String? = null,
        val threadName: String? = null,
        val stackTrace: String? = null,
        val isLogcat: Boolean = false
    ) {
        fun getFormattedMessage(): String {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            val threadInfo = threadName?.let { "[$it] " } ?: ""
            val tagInfo = tag?.let { "($it) " } ?: ""
            val detailsStr = if (details != null) "\n    └─ Details: $details" else ""
            val stackTraceStr = if (stackTrace != null) "\n    └─ StackTrace:\n$stackTrace" else ""
            val logcatPrefix = if (isLogcat) "[LOGCAT] " else ""
            return "$logcatPrefix[$date] [${type.display}] ${threadInfo}${tagInfo}$message$detailsStr$stackTraceStr"
        }

        fun getDisplayTime(): String {
            return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
        }

        fun getDisplayDate(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
        }

        fun toCsvString(): String {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            return "$date,${type.display},${tag ?: ""},${message.replace(",", ";")},${details?.replace(",", ";") ?: ""}"
        }
    }

    private var isEnabled = false
    private var logEntries = mutableListOf<LogEntry>()
    private val listeners = mutableListOf<() -> Unit>()
    private var currentSessionId = System.currentTimeMillis()

    private val logTag = "BugeAppManager"

    private var logcatReaderThread: Thread? = null
    private var isLogcatReading = false

    fun init(context: Context) {
        isEnabled = PreferencesManager.getLoggingEnabled(context)
        currentSessionId = System.currentTimeMillis()
        info(context, "LogManager initialized", "Session started: $currentSessionId")
        info(context, "Device Info", "Model: ${Build.MODEL}, SDK: ${Build.VERSION.SDK_INT}, Android: ${Build.VERSION.RELEASE}")
        loadLogsFromFile(context)
        startLogcatReader(context)
    }

    fun isEnabled(): Boolean = isEnabled

    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled
        PreferencesManager.setLoggingEnabled(context, enabled)
        if (enabled) {
            info(context, "Logging enabled", "Session resumed")
            startLogcatReader(context)
        } else {
            info(context, "Logging disabled", "Session paused")
            stopLogcatReader()
            Log.d(logTag, "Logging disabled by user")
        }
        notifyListeners()
    }

    fun getLogs(): List<LogEntry> = logEntries.toList()

    fun getLogsByType(type: LogType): List<LogEntry> = logEntries.filter { it.type == type }

    fun getLogsByTag(tag: String): List<LogEntry> = logEntries.filter { it.tag == tag }

    fun getLogsSince(timestamp: Long): List<LogEntry> = logEntries.filter { it.timestamp >= timestamp }

    fun getSessionId(): Long = currentSessionId

    fun startNewSession(context: Context) {
        currentSessionId = System.currentTimeMillis()
        info(context, "New session started", "Session ID: $currentSessionId")
    }

    fun clearLogs(context: Context) {
        logEntries.clear()
        saveLogsToFile(context)
        info(context, "Logs cleared", "All logs have been cleared")
        notifyListeners()
    }

    fun exportLogs(context: Context): File? {
        return try {
            val logs = getLogs()
            if (logs.isEmpty()) return null
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(context.getExternalFilesDir(null), "logs_export_$timestamp.txt")
            
            exportFile.printWriter().use { printer ->
                printer.println("=" .repeat(80))
                printer.println("Buge App Manager - Log Export")
                printer.println("=" .repeat(80))
                printer.println("Export Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                printer.println("Session ID: $currentSessionId")
                printer.println("Total Logs: ${logs.size}")
                printer.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                printer.println("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                printer.println("=" .repeat(80))
                printer.println()
                
                logs.reversed().forEach { entry ->
                    printer.println(entry.getFormattedMessage())
                    printer.println()
                }
                
                printer.println("=" .repeat(80))
                printer.println("End of Export")
                printer.println("=" .repeat(80))
            }
            
            exportFile
        } catch (e: Exception) {
            Log.e(logTag, "Failed to export logs", e)
            null
        }
    }

    fun exportLogsAsCsv(context: Context): File? {
        return try {
            val logs = getLogs()
            if (logs.isEmpty()) return null
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(context.getExternalFilesDir(null), "logs_export_$timestamp.csv")
            
            exportFile.printWriter().use { printer ->
                printer.println("Timestamp,Type,Tag,Message,Details")
                logs.reversed().forEach { entry ->
                    printer.println(entry.toCsvString())
                }
            }
            
            exportFile
        } catch (e: Exception) {
            Log.e(logTag, "Failed to export logs as CSV", e)
            null
        }
    }

    private fun startLogcatReader(context: Context) {
        if (!isEnabled) return
        if (isLogcatReading) return
        if (logcatReaderThread?.isAlive == true) return

        isLogcatReading = true
        logcatReaderThread = Thread {
            readLogcat(context)
        }.apply {
            isDaemon = true
            start()
        }
        Log.d(logTag, "Logcat reader started")
    }

    private fun stopLogcatReader() {
        isLogcatReading = false
        logcatReaderThread?.interrupt()
        logcatReaderThread = null
        Log.d(logTag, "Logcat reader stopped")
    }

    private fun readLogcat(context: Context) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "logcat",
                "-v", "time",
                "-d",
                "*:I"
            ))

            val reader = process.inputStream.bufferedReader()
            var buffer = mutableListOf<String>()
            var lastReadTime = System.currentTimeMillis()

            while (isLogcatReading) {
                // Fuck: Read all available lines
                var line: String? = reader.readLine()
                while (line != null && isLogcatReading) {
                    buffer.add(line)
                    if (buffer.size > 100) {
                        flushBuffer(context, buffer)
                        buffer = mutableListOf()
                    }
                    line = reader.readLine()
                }

                if (buffer.isNotEmpty()) {
                    flushBuffer(context, buffer)
                    buffer = mutableListOf()
                }

                // Fuck: Poll every 2 seconds
                Thread.sleep(2000)

                // Fuck: Check if we need to restart process
                if (System.currentTimeMillis() - lastReadTime > 60000) {
                    lastReadTime = System.currentTimeMillis()
                    process.destroy()
                    break
                }
            }

            if (buffer.isNotEmpty()) {
                flushBuffer(context, buffer)
            }

        } catch (e: InterruptedException) {
            // Fuck: Thread interrupted, normal shutdown
        } catch (e: Exception) {
            Log.e(logTag, "Logcat reader error: ${e.message}", e)
        } finally {
            isLogcatReading = false
        }
    }

    private fun flushBuffer(context: Context, buffer: MutableList<String>) {
        if (buffer.isEmpty()) return

        val timestamp = System.currentTimeMillis()
        val regex = Regex("""(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\w)/([^:]+):(.*)""")

        for (line in buffer) {
            try {
                val match = regex.find(line)
                if (match != null) {
                    val timeStr = match.groupValues[1]
                    val level = match.groupValues[2]
                    val tag = match.groupValues[3].trim()
                    val message = match.groupValues[4].trim()

                    if (message.isEmpty()) continue
                    if (tag == "BugeAppManager") continue

                    val logType = when (level) {
                        "V" -> LogType.VERBOSE
                        "D" -> LogType.DEBUG
                        "I" -> LogType.INFO
                        "W" -> LogType.WARNING
                        "E" -> LogType.ERROR
                        "F" -> LogType.ERROR
                        else -> LogType.INFO
                    }

                    val entry = LogEntry(
                        timestamp = timestamp,
                        type = logType,
                        message = message,
                        details = "Logcat",
                        tag = tag,
                        threadName = null,
                        stackTrace = null,
                        isLogcat = true
                    )

                    logEntries.add(0, entry)

                    while (logEntries.size > MAX_LOG_LINES) {
                        logEntries.removeAt(logEntries.lastIndex)
                    }

                    saveLogsToFile(context)
                    notifyListeners()
                } else {
                    if (line.isNotBlank() && !line.startsWith("---------")) {
                        val entry = LogEntry(
                            timestamp = timestamp,
                            type = LogType.LOGCAT,
                            message = line.take(200),
                            details = "Raw logcat",
                            tag = "logcat",
                            threadName = null,
                            stackTrace = null,
                            isLogcat = true
                        )
                        logEntries.add(0, entry)

                        while (logEntries.size > MAX_LOG_LINES) {
                            logEntries.removeAt(logEntries.lastIndex)
                        }
                        saveLogsToFile(context)
                        notifyListeners()
                    }
                }
            } catch (e: Exception) {
                // Fuck: Skip malformed lines
            }
        }

        buffer.clear()
    }

    fun refreshLogcat(context: Context) {
        if (!isEnabled) return
        stopLogcatReader()
        startLogcatReader(context)
    }

    // Fuck: Detailed logging methods
    fun debug(context: Context, message: String, details: String? = null, tag: String? = null) {
        addLog(context, LogType.DEBUG, message, details, tag)
        Log.d(logTag, "[DEBUG] $message${details?.let { " - $it" } ?: ""}")
    }

    fun verbose(context: Context, message: String, details: String? = null, tag: String? = null) {
        addLog(context, LogType.VERBOSE, message, details, tag)
        Log.v(logTag, "[VERBOSE] $message${details?.let { " - $it" } ?: ""}")
    }

    fun info(context: Context, message: String, details: String? = null, tag: String? = null) {
        addLog(context, LogType.INFO, message, details, tag)
        Log.i(logTag, "[INFO] $message${details?.let { " - $it" } ?: ""}")
    }

    fun warning(context: Context, message: String, details: String? = null, tag: String? = null) {
        addLog(context, LogType.WARNING, message, details, tag)
        Log.w(logTag, "[WARN] $message${details?.let { " - $it" } ?: ""}")
    }

    fun error(context: Context, message: String, details: String? = null, tag: String? = null, throwable: Throwable? = null) {
        val stackTrace = throwable?.let {
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            it.printStackTrace(pw)
            sw.toString()
        }
        addLog(context, LogType.ERROR, message, details, tag, stackTrace)
        if (throwable != null) {
            Log.e(logTag, "[ERROR] $message${details?.let { " - $it" } ?: ""}", throwable)
        } else {
            Log.e(logTag, "[ERROR] $message${details?.let { " - $it" } ?: ""}")
        }
    }

    fun success(context: Context, message: String, details: String? = null, tag: String? = null) {
        addLog(context, LogType.SUCCESS, message, details, tag)
        Log.i(logTag, "[SUCCESS] $message${details?.let { " - $it" } ?: ""}")
    }

    fun permission(context: Context, message: String, details: String? = null, tag: String? = null) {
        addLog(context, LogType.PERMISSION, message, details, tag)
        Log.i(logTag, "[PERM] $message${details?.let { " - $it" } ?: ""}")
    }

    fun permissionChange(context: Context, packageName: String, permission: String, granted: Boolean, isSystemApp: Boolean) {
        val details = "Package: $packageName, Permission: $permission, Granted: $granted, IsSystemApp: $isSystemApp"
        addLog(context, LogType.PERMISSION_CHANGE, "Permission changed", details, "PermissionManager")
        Log.i(logTag, "[PERM_CHG] $details")
    }

    fun shizuku(context: Context, message: String, command: String? = null, result: String? = null) {
        val details = buildString {
            command?.let { append("Command: ${redactCommand(it)}\n") }
            result?.let { append("Result: ${redactResult(it)}") }
        }
        addLog(context, LogType.SHIZUKU, message, details.ifEmpty { null }, "Shizuku")
        Log.d(logTag, "[SHIZUKU] $message${command?.let { " - ${redactCommand(it)}" } ?: ""}")
    }

    private fun redactCommand(cmd: String): String {
        return cmd.replace(Regex("""\b[a-z][a-z0-9_.]*(\.[a-z][a-z0-9_.]*){2,}\b"""), "***")
    }

    private fun redactResult(result: String): String {
        if (result.length > 500) return result.take(500) + "..."
        return result
    }

    fun activity(context: Context, message: String, activityName: String, details: String? = null) {
        addLog(context, LogType.ACTIVITY, message, details, activityName)
        Log.d(logTag, "[ACTIVITY] $activityName: $message")
    }

    fun ui(context: Context, message: String, component: String, action: String) {
        val details = "Component: $component, Action: $action"
        addLog(context, LogType.UI, message, details, "UI")
        Log.d(logTag, "[UI] $component: $action - $message")
    }

    fun storage(context: Context, message: String, filePath: String? = null, size: Long? = null) {
        val details = buildString {
            filePath?.let { append("Path: $it\n") }
            size?.let { append("Size: ${formatSize(it)}") }
        }
        addLog(context, LogType.STORAGE, message, details.ifEmpty { null }, "Storage")
        Log.d(logTag, "[STORAGE] $message${filePath?.let { " - $it" } ?: ""}")
    }

    fun network(context: Context, message: String, url: String? = null, statusCode: Int? = null) {
        val details = buildString {
            url?.let { append("URL: $it\n") }
            statusCode?.let { append("Status: $it") }
        }
        addLog(context, LogType.NETWORK, message, details.ifEmpty { null }, "Network")
        Log.d(logTag, "[NETWORK] $message${url?.let { " - $it" } ?: ""}")
    }

    fun logWithStackTrace(context: Context, type: LogType, message: String, throwable: Throwable, tag: String? = null) {
        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()
        addLog(context, type, message, throwable.message, tag, stackTrace)
        Log.e(logTag, "[${type.display}] $message", throwable)
    }

    private fun addLog(
        context: Context,
        type: LogType,
        message: String,
        details: String? = null,
        tag: String? = null,
        stackTrace: String? = null
    ) {
        if (!isEnabled) return

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            type = type,
            message = message,
            details = details,
            tag = tag,
            threadName = Thread.currentThread().name,
            stackTrace = stackTrace,
            isLogcat = false
        )
        
        logEntries.add(0, entry)

        while (logEntries.size > MAX_LOG_LINES) {
            logEntries.removeAt(logEntries.lastIndex)
        }

        saveLogsToFile(context)
        notifyListeners()
    }

    private fun loadLogsFromFile(context: Context) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return

        try {
            val content = file.readText()
            if (content.isNotEmpty()) {
                val lines = content.split("\n").filter { it.isNotEmpty() }
                for (line in lines) {
                    try {
                        val entry = LogEntry(
                            timestamp = System.currentTimeMillis(),
                            type = LogType.INFO,
                            message = line.take(200),
                            details = "Loaded from file",
                            tag = null,
                            threadName = null,
                            stackTrace = null,
                            isLogcat = false
                        )
                        logEntries.add(entry)
                    } catch (e: Exception) {
                        // Skip malformed lines
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load logs from file", e)
        }
    }

    private fun saveLogsToFile(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            file.printWriter().use { printer ->
                logEntries.reversed().forEach { entry ->
                    printer.println(entry.getFormattedMessage())
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save logs to file", e)
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

    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun getStatistics(): Map<String, Any> {
        val typeCounts = LogType.values().associateWith { type -> logEntries.count { it.type == type } }
        val totalLogs = logEntries.size
        val logcatCount = logEntries.count { it.isLogcat }
        val firstLogTime = logEntries.lastOrNull()?.timestamp
        val lastLogTime = logEntries.firstOrNull()?.timestamp
        val timeRange = if (firstLogTime != null && lastLogTime != null) {
            (lastLogTime - firstLogTime) / 1000
        } else 0L

        return mapOf(
            "totalLogs" to totalLogs,
            "logcatCount" to logcatCount,
            "typeCounts" to typeCounts,
            "timeRangeSeconds" to timeRange,
            "sessionId" to currentSessionId
        )
    }

    fun shutdown() {
        stopLogcatReader()
    }
}