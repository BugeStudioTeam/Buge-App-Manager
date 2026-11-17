// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LogManager {

    private const val LOG_FILE_NAME = "app_logs.json"
    private const val MAX_LOG_SIZE = 1024 * 1024
    private const val MAX_LOG_LINES = 2000
    private const val LOGCAT_DIR = "logcat_recordings"
    private const val RECORDING_PREF = "logcat_recordings_pref"
    private const val RECORDING_LIST_KEY = "recordings"

    enum class RecordStatus {
        Idle,
        RecordingInProgress,
        SaveRecordedLogs,
        ;

        fun isIdle() = this == Idle
    }

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

        fun toLine(): String {
            val time = getDisplayTime()
            val level = type.display
            val tagPart = tag?.let { " $it" } ?: ""
            return "$time $level:$tagPart $message"
        }

        fun toCsvString(): String {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            return "$date,${type.display},${tag ?: ""},${message.replace(",", ";")},${details?.replace(",", ";") ?: ""}"
        }
    }

    data class RecordingFileInfo(
        val fileName: String,
        val uri: Uri,
        val isCustomLocation: Boolean,
        val timestamp: Long
    )

    data class SavedRecordingInfo(
        val fileName: String,
        val path: String,
        val isCustom: Boolean,
        val timestamp: Long
    )

    private data class ParsedLogcatLine(
        val time: Long,
        val level: String,
        val tag: String,
        val message: String
    )

    private val lock = Any()
    private val recordingLock = Any()

    private var initialized = false
    private var isEnabled = false
    private val logEntries = mutableListOf<LogEntry>()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val recordStateListeners = java.util.concurrent.CopyOnWriteArrayList<(RecordStatus) -> Unit>()
    private var currentSessionId = System.currentTimeMillis()

    private val logTag = "BugeAppManager"

    private val logcatRegex = Regex("""(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\w)/(.+?)\(\s*\d+\):\s?(.*)""")

    private val captureExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LogcatCapture").apply { isDaemon = true }
    }

    private var logcatWatermark: Long = 0L

    @Volatile
    private var recordStatus: RecordStatus = RecordStatus.Idle

    private var recordingFileInfo: RecordingFileInfo? = null
    private var recordingWriter: BufferedWriter? = null

    private val gson = Gson()

    fun init(context: Context) {
        synchronized(lock) {
            if (initialized) return
            initialized = true
        }

        loadLogsFromFile(context)

        val now = System.currentTimeMillis()
        synchronized(lock) {
            logcatWatermark = now
        }

        isEnabled = PreferencesManager.getLoggingEnabled(context)
        currentSessionId = System.currentTimeMillis()
        info(context, "LogManager initialized", "Session started: $currentSessionId")
        info(context, "Device Info", "Model: ${Build.MODEL}, SDK: ${Build.VERSION.SDK_INT}, Android: ${Build.VERSION.RELEASE}")
        if (isEnabled) {
            submitCapture { ingestLogcat(context, Long.MAX_VALUE) }
        }
    }

    fun isEnabled(): Boolean = isEnabled

    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled) {
            isEnabled = true
            PreferencesManager.setLoggingEnabled(context, true)
            val enabledAt = System.currentTimeMillis()
            info(context, "Logging enabled", "Session resumed")
            submitCapture {
                synchronized(lock) {
                    if (enabledAt > logcatWatermark) {
                        logcatWatermark = enabledAt
                    }
                }
                ingestLogcat(context, Long.MAX_VALUE)
            }
        } else {
            val disabledAt = System.currentTimeMillis()
            isEnabled = false
            PreferencesManager.setLoggingEnabled(context, false)
            info(context, "Logging disabled", "Session paused")
            submitCapture { ingestLogcat(context, disabledAt) }
            Log.d(logTag, "Logging disabled by user")
        }
        notifyListeners()
    }

    fun getRecordStatus(): RecordStatus = recordStatus

    fun setRecordStatus(context: Context, status: RecordStatus) {
        if (recordStatus == status) return
        recordStatus = status
        notifyRecordStateListeners(status)

        when (status) {
            RecordStatus.Idle -> Unit
            RecordStatus.RecordingInProgress -> startRecording(context)
            RecordStatus.SaveRecordedLogs -> stopRecording(context)
        }
    }

    fun addRecordStateListener(listener: (RecordStatus) -> Unit) {
        if (!recordStateListeners.contains(listener)) {
            recordStateListeners.add(listener)
        }
    }

    fun removeRecordStateListener(listener: (RecordStatus) -> Unit) {
        recordStateListeners.remove(listener)
    }

    private fun notifyRecordStateListeners(status: RecordStatus) {
        recordStateListeners.forEach { it.invoke(status) }
    }

    private fun startRecording(context: Context) {
        synchronized(recordingLock) {
            if (recordingWriter != null) {
                Log.w(logTag, "startRecording called while already recording, ignored")
                return
            }
            val fileInfo = createFileToStartRecording(context)
            if (fileInfo == null) {
                Log.e(logTag, "startRecording failed: createFile returned null")
                recordStatus = RecordStatus.Idle
                notifyRecordStateListeners(recordStatus)
                return
            }
            val writer = createBufferedWriter(context, fileInfo)
            if (writer == null) {
                Log.e(logTag, "startRecording failed: createBufferedWriter returned null")
                recordStatus = RecordStatus.Idle
                notifyRecordStateListeners(recordStatus)
                return
            }
            recordingFileInfo = fileInfo
            recordingWriter = writer
            Log.d(logTag, "Recording started: ${fileInfo.fileName}")
        }
    }

    private fun stopRecording(context: Context) {
        synchronized(recordingLock) {
            val info = recordingFileInfo
            val writer = recordingWriter
            recordingWriter = null
            recordingFileInfo = null

            var success = false
            if (writer != null) {
                try {
                    writer.flush()
                    writer.close()
                    success = true
                } catch (e: Exception) {
                    Log.e(logTag, "Failed to close recording writer: ${e.message}", e)
                }
            }

            recordStatus = RecordStatus.Idle
            notifyRecordStateListeners(recordStatus)

            if (success && info != null) {
                notifyListeners()
                Log.d(logTag, "Recording saved: ${info.fileName}")
            } else {
                Log.e(logTag, "Recording stop failed or no active recording")
            }
        }
    }

    private fun createFileToStartRecording(context: Context): RecordingFileInfo? {
        val createFileResult = createFile(context, recording = true) ?: return null
        val (uri, isCustomLocation, timestamp) = createFileResult

        val fileName = if (isCustomLocation) {
            DocumentFile.fromSingleUri(context, uri)?.name
        } else {
            uri.lastPathSegment?.substringAfterLast("/")
        } ?: return null

        saveRecordingInfo(
            context,
            SavedRecordingInfo(
                fileName = fileName,
                path = uri.toString(),
                isCustom = isCustomLocation,
                timestamp = timestamp
            )
        )

        return RecordingFileInfo(
            fileName = fileName,
            uri = uri,
            isCustomLocation = isCustomLocation,
            timestamp = timestamp
        )
    }

    private fun createFile(context: Context, recording: Boolean): Triple<Uri, Boolean, Long>? {
        val date = Date()
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(date)
        val fileName = buildString {
            append("logcat_")
            if (recording) append("recording_")
            append(timeStamp)
        }

        val customSaveLocation = PreferencesManager.getLogcatSaveLocation(context)

        return if (customSaveLocation.isEmpty()) {
            val dir = File(context.filesDir, LOGCAT_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$fileName.txt")
            Triple(Uri.fromFile(file), false, date.time)
        } else {
            try {
                val treeUri = Uri.parse(customSaveLocation)
                val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return null
                val created = documentFile.createFile("text/plain", fileName) ?: return null
                Triple(created.uri, true, date.time)
            } catch (e: Exception) {
                Log.e(logTag, "createFile SAF failed: ${e.message}", e)
                null
            }
        }
    }

    private fun createBufferedWriter(context: Context, fileInfo: RecordingFileInfo): BufferedWriter? {
        return try {
            if (fileInfo.isCustomLocation) {
                context.contentResolver.openOutputStream(fileInfo.uri)?.bufferedWriter()
            } else {
                val file = File(fileInfo.uri.path ?: return null)
                val parent = file.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()
                file.bufferedWriter()
            }
        } catch (e: IOException) {
            Log.e(logTag, "createBufferedWriter failed: ${e.message}", e)
            null
        }
    }

    private fun writeToRecording(entry: LogEntry) {
        val writer = recordingWriter ?: return
        try {
            writer.write(entry.getFormattedMessage())
            writer.newLine()
        } catch (e: Exception) {
            Log.e(logTag, "writeToRecording failed: ${e.message}", e)
        }
    }

    private fun saveRecordingInfo(context: Context, info: SavedRecordingInfo) {
        try {
            val list = getSavedRecordings(context).toMutableList()
            list.add(0, info)
            val json = gson.toJson(list)
            context.getSharedPreferences(RECORDING_PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(RECORDING_LIST_KEY, json)
                .apply()
        } catch (e: Exception) {
            Log.e(logTag, "saveRecordingInfo failed: ${e.message}", e)
        }
    }

    fun getSavedRecordings(context: Context): List<SavedRecordingInfo> {
        return try {
            val json = context.getSharedPreferences(RECORDING_PREF, Context.MODE_PRIVATE)
                .getString(RECORDING_LIST_KEY, "[]") ?: "[]"
            val type = object : TypeToken<List<SavedRecordingInfo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteSavedRecording(context: Context, info: SavedRecordingInfo) {
        try {
            val uri = Uri.parse(info.path)
            if (info.isCustom) {
                val doc = DocumentFile.fromSingleUri(context, uri)
                doc?.delete()
            } else {
                val file = File(uri.path ?: return)
                if (file.exists()) file.delete()
            }
            val list = getSavedRecordings(context).toMutableList()
            list.removeAll { it.path == info.path }
            val json = gson.toJson(list)
            context.getSharedPreferences(RECORDING_PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(RECORDING_LIST_KEY, json)
                .apply()
        } catch (e: Exception) {
            Log.e(logTag, "deleteSavedRecording failed: ${e.message}", e)
        }
    }

    fun getLogs(): List<LogEntry> {
        synchronized(lock) {
            return logEntries.toList()
        }
    }

    fun getLogsByType(type: LogType): List<LogEntry> = getLogs().filter { it.type == type }

    fun getLogsByTag(tag: String): List<LogEntry> = getLogs().filter { it.tag == tag }

    fun getLogsSince(timestamp: Long): List<LogEntry> = getLogs().filter { it.timestamp >= timestamp }

    fun getSessionId(): Long = currentSessionId

    fun startNewSession(context: Context) {
        currentSessionId = System.currentTimeMillis()
        info(context, "New session started", "Session ID: $currentSessionId")
    }

    fun clearLogs(context: Context) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            logEntries.clear()
            logcatWatermark = now
        }
        PreferencesManager.setLogClearTimestamp(context, now)
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to delete log file on clear", e)
        }
        notifyListeners()
    }

    fun exportLogs(context: Context): File? {
        return try {
            val logs = getLogs()
            if (logs.isEmpty()) return null

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val exportFile = File(context.getExternalFilesDir(null), "logs_export_$timestamp.txt")

            exportFile.printWriter().use { printer ->
                printer.println("=".repeat(80))
                printer.println("Buge App Manager - Log Export")
                printer.println("=".repeat(80))
                printer.println("Export Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                printer.println("Session ID: $currentSessionId")
                printer.println("Total Logs: ${logs.size}")
                printer.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                printer.println("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                printer.println("=".repeat(80))
                printer.println()

                logs.reversed().forEach { entry ->
                    printer.println(entry.getFormattedMessage())
                    printer.println()
                }

                printer.println("=".repeat(80))
                printer.println("End of Export")
                printer.println("=".repeat(80))
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

    private fun submitCapture(task: () -> Unit) {
        try {
            captureExecutor.execute {
                try {
                    task()
                } catch (e: Exception) {
                    Log.e(logTag, "Logcat capture failed: ${e.message}", e)
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            Log.e(logTag, "Logcat capture rejected: ${e.message}", e)
        }
    }

    private fun readLogcatLines(): List<String>? {
        var process: Process? = null
        return try {
            process = ProcessBuilder("logcat", "-v", "time", "-d", "*:V")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            Log.e(logTag, "Logcat read error: ${e.message}", e)
            null
        } finally {
            process?.destroy()
        }
    }

    private fun ingestLogcat(context: Context, until: Long) {
        val lines = readLogcatLines() ?: return

        val year = Calendar.getInstance().get(Calendar.YEAR)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val parsed = ArrayList<ParsedLogcatLine>()

        for (line in lines) {
            val match = logcatRegex.find(line) ?: continue
            val message = match.groupValues[4].trim()
            if (message.isEmpty()) continue
            val tag = match.groupValues[3].trim()
            if (tag == logTag) continue
            val time = (try {
                format.parse("$year-${match.groupValues[1]}")?.time
            } catch (e: Exception) {
                null
            }) ?: continue
            parsed.add(ParsedLogcatLine(time, match.groupValues[2], tag, message))
        }

        val accepted = ArrayList<LogEntry>()

        synchronized(lock) {
            val lowerBound = logcatWatermark
            var maxTime = lowerBound

            for (item in parsed) {
                if (item.time <= lowerBound || item.time > until) continue

                val logType = when (item.level) {
                    "V" -> LogType.VERBOSE
                    "D" -> LogType.DEBUG
                    "I" -> LogType.INFO
                    "W" -> LogType.WARNING
                    "E" -> LogType.ERROR
                    "F" -> LogType.ERROR
                    else -> LogType.LOGCAT
                }

                val entry = LogEntry(
                    timestamp = item.time,
                    type = logType,
                    message = item.message,
                    details = null,
                    tag = item.tag,
                    threadName = null,
                    stackTrace = null,
                    isLogcat = true
                )

                accepted.add(entry)
                logEntries.add(0, entry)
                if (item.time > maxTime) maxTime = item.time
            }

            while (logEntries.size > MAX_LOG_LINES) {
                logEntries.removeAt(logEntries.lastIndex)
            }

            val newWatermark = if (until == Long.MAX_VALUE) maxTime else maxOf(maxTime, until)
            if (newWatermark > logcatWatermark) {
                logcatWatermark = newWatermark
            }
        }

        if (accepted.isEmpty()) return

        for (entry in accepted) {
            writeToRecording(entry)
        }

        saveLogsToFile(context)
        notifyListeners()
    }

    fun refreshLogcat(context: Context) {
        if (!isEnabled) return
        submitCapture { ingestLogcat(context, Long.MAX_VALUE) }
    }

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

        synchronized(lock) {
            logEntries.add(0, entry)
            while (logEntries.size > MAX_LOG_LINES) {
                logEntries.removeAt(logEntries.lastIndex)
            }
        }

        writeToRecording(entry)

        saveLogsToFile(context)
        notifyListeners()
    }

    private fun loadLogsFromFile(context: Context) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return

        try {
            val json = file.readText()
            if (json.isBlank()) return
            val type = object : TypeToken<List<LogEntry>>() {}.type
            val loaded: List<LogEntry>? = gson.fromJson(json, type)
            if (loaded != null) {
                synchronized(lock) {
                    logEntries.clear()
                    logEntries.addAll(loaded.filterNotNull().take(MAX_LOG_LINES))
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to load logs from file", e)
        }
    }

    private fun saveLogsToFile(context: Context) {
        try {
            val snapshot: List<LogEntry>
            synchronized(lock) {
                snapshot = logEntries.toList()
            }
            val file = File(context.filesDir, LOG_FILE_NAME)
            file.writeText(gson.toJson(snapshot))
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save logs to file", e)
        }
    }

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
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
        val snapshot = getLogs()
        val typeCounts = LogType.values().associateWith { type -> snapshot.count { it.type == type } }
        val totalLogs = snapshot.size
        val logcatCount = snapshot.count { it.isLogcat }
        val firstLogTime = snapshot.lastOrNull()?.timestamp
        val lastLogTime = snapshot.firstOrNull()?.timestamp
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
        captureExecutor.shutdownNow()
        synchronized(recordingLock) {
            try {
                recordingWriter?.flush()
                recordingWriter?.close()
            } catch (e: Exception) {
            }
            recordingWriter = null
            recordingFileInfo = null
            recordStatus = RecordStatus.Idle
        }
    }
}
