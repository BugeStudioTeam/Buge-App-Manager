package com.buge.appmanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.databinding.ActivityLogViewerBinding
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial

class LogViewerActivity : AppCompatActivity() {

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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Clear")
        menu.add(0, 2, 1, "Copy")
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            1 -> {
                LogManager.clearLogs(this)
                Snackbar.make(binding.root, R.string.log_cleared, Snackbar.LENGTH_SHORT).show()
                return true
            }
            2 -> {
                copyLogsToClipboard()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun copyLogsToClipboard() {
        val logs = LogManager.getLogs()
        if (logs.isEmpty()) {
            Snackbar.make(binding.root, R.string.log_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        val text = logs.joinToString("\n\n") { it.getFormattedMessage() }
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("App Logs", text)
        clipboard.setPrimaryClip(clip)
        Snackbar.make(binding.root, R.string.log_copied, Snackbar.LENGTH_SHORT).show()
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
            private val logMessage: TextView = itemView.findViewById(R.id.log_message)

            fun bind(entry: LogManager.LogEntry) {
                logTime.text = entry.getDisplayTime()
                logMessage.text = entry.getFormattedMessage()
            }
        }
    }
}