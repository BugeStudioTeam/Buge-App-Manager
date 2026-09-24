// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class BackupSettingsAdapter(
    private val onBackupClick: () -> Unit,
    private val onIntervalClick: () -> Unit,
    private val onLocationClick: () -> Unit,
    private val onAutoBackupToggle: (Boolean) -> Unit,
    private val onContentClick: () -> Unit
) : RecyclerView.Adapter<BackupSettingsAdapter.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 1
        private const val TYPE_ITEM = 2
        private const val TYPE_SWITCH = 3
    }

    private var items: List<BackupSettingItem> = emptyList()

    fun submitList(newItems: List<BackupSettingItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return when {
            item.isHeader -> TYPE_HEADER
            item.type == BackupItemType.AUTO_BACKUP_SWITCH -> TYPE_SWITCH
            else -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_setting_group_header, parent, false)
                ViewHolder(view, viewType)
            }
            TYPE_SWITCH -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_backup_setting_switch, parent, false)
                ViewHolder(view, viewType)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_backup_setting, parent, false)
                ViewHolder(view, viewType)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)

        if (!item.isHeader) {
            applyBackground(holder, position)

            when (item.type) {
                BackupItemType.AUTO_BACKUP_SWITCH -> {
                    holder.switch?.setOnCheckedChangeListener(null)
                    holder.switch?.isChecked = item.isChecked
                    holder.switch?.setOnCheckedChangeListener { _, isChecked ->
                        onAutoBackupToggle(isChecked)
                    }
                }
                BackupItemType.BACKUP -> {
                    holder.itemView.setOnClickListener { onBackupClick() }
                }
                BackupItemType.INTERVAL -> {
                    holder.itemView.setOnClickListener { onIntervalClick() }
                }
                BackupItemType.LOCATION -> {
                    holder.itemView.setOnClickListener { onLocationClick() }
                }
                BackupItemType.CONTENT -> {
                    holder.itemView.setOnClickListener { onContentClick() }
                }
                BackupItemType.NONE -> {
                    holder.itemView.setOnClickListener(null)
                }
            }
        }
    }

    private fun applyBackground(holder: ViewHolder, position: Int) {
        val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container) ?: return
        val size = items.size

        var groupStart = position
        while (groupStart > 0 && !items[groupStart - 1].isHeader) {
            groupStart--
        }

        var groupEnd = position
        while (groupEnd < size - 1 && !items[groupEnd + 1].isHeader) {
            groupEnd++
        }

        val isFirstInGroup = position == groupStart
        val isLastInGroup = position == groupEnd
        val isSingleInGroup = isFirstInGroup && isLastInGroup

        val background = when {
            isSingleInGroup -> R.drawable.bg_setting_item_single
            isFirstInGroup -> R.drawable.bg_setting_item_top
            isLastInGroup -> R.drawable.bg_setting_item_bottom
            else -> R.drawable.bg_setting_item_middle
        }
        container.setBackgroundResource(background)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View, private val viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val title: TextView? = if (viewType == TYPE_HEADER) {
            itemView.findViewById(R.id.group_header)
        } else {
            itemView.findViewById(R.id.title)
        }
        val subtitle: TextView? = if (viewType == TYPE_HEADER) null else itemView.findViewById(R.id.subtitle)
        val arrow: ImageView? = if (viewType == TYPE_HEADER || viewType == TYPE_SWITCH) null else itemView.findViewById(R.id.arrow_icon)
        val switch: MaterialSwitch? = if (viewType == TYPE_SWITCH) itemView.findViewById(R.id.switch_control) else null

        fun bind(item: BackupSettingItem) {
            title?.text = item.title
            if (viewType != TYPE_HEADER) {
                subtitle?.text = item.subtitle
                subtitle?.visibility = if (item.subtitle.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}

data class BackupSettingItem(
    val title: String,
    val subtitle: String,
    val isHeader: Boolean,
    val type: BackupItemType = BackupItemType.NONE,
    val isChecked: Boolean = false
)

enum class BackupItemType {
    NONE, BACKUP, INTERVAL, LOCATION, AUTO_BACKUP_SWITCH, CONTENT
}