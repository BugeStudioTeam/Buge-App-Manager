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

class BackupSettingsAdapter(
    private val onBackupClick: () -> Unit,
    private val onIntervalClick: () -> Unit,
    private val onLocationClick: () -> Unit
) : RecyclerView.Adapter<BackupSettingsAdapter.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 1
        private const val TYPE_ITEM = 2
    }

    private var items: List<BackupSettingItem> = emptyList()

    fun submitList(newItems: List<BackupSettingItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isHeader) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_setting_group_header, parent, false)
            ViewHolder(view, isHeader = true)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_backup_setting, parent, false)
            ViewHolder(view, isHeader = false)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)

        if (!item.isHeader) {
            applyBackground(holder, position)
            holder.itemView.setOnClickListener {
                when (item.type) {
                    BackupItemType.BACKUP -> onBackupClick()
                    BackupItemType.INTERVAL -> onIntervalClick()
                    BackupItemType.LOCATION -> onLocationClick()
                    BackupItemType.NONE -> { }
                }
            }
        }
    }

    private fun applyBackground(holder: ViewHolder, position: Int) {
        val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container) ?: return
        val size = items.size

        // Fuck: Find the boundaries of the current group
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

    inner class ViewHolder(itemView: View, private val isHeader: Boolean) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView? = if (isHeader) {
            itemView.findViewById(R.id.group_header)
        } else {
            itemView.findViewById(R.id.title)
        }
        private val subtitle: TextView? = if (isHeader) null else itemView.findViewById(R.id.subtitle)
        private val arrow: ImageView? = if (isHeader) null else itemView.findViewById(R.id.arrow_icon)

        fun bind(item: BackupSettingItem) {
            if (item.isHeader) {
                title?.text = item.title
            } else {
                title?.text = item.title
                subtitle?.text = item.subtitle
                subtitle?.visibility = if (item.subtitle.isEmpty()) View.GONE else View.VISIBLE

                // Fuck: Hide arrow for interval/location items since they are clickable but lead to dialogs/pickers
                arrow?.visibility = View.VISIBLE
            }
        }
    }
}

data class BackupSettingItem(
    val title: String,
    val subtitle: String,
    val isHeader: Boolean,
    val type: BackupItemType = BackupItemType.NONE
)

enum class BackupItemType {
    NONE, BACKUP, INTERVAL, LOCATION
}
