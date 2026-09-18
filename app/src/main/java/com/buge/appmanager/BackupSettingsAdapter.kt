// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BackupSettingsAdapter(
    private val onBackupClick: () -> Unit,
    private val onIntervalClick: () -> Unit,
    private val onLocationClick: () -> Unit
) : RecyclerView.Adapter<BackupSettingsAdapter.ViewHolder>() {

    private var items: List<BackupSettingItem> = emptyList()

    fun submitList(newItems: List<BackupSettingItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_backup_setting, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
        applyBackground(holder, position)

        holder.itemView.setOnClickListener {
            when (items[position].type) {
                BackupItemType.BACKUP -> onBackupClick()
                BackupItemType.INTERVAL -> onIntervalClick()
                BackupItemType.LOCATION -> onLocationClick()
                BackupItemType.NONE -> { }
            }
        }
    }

    private fun applyBackground(holder: ViewHolder, position: Int) {
        val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
        val size = items.size

        val isFirstInGroup = position == 0 || items[position - 1].isHeader
        val isLastInGroup = position == size - 1 || items[position + 1].isHeader

        val background = when {
            size == 1 -> R.drawable.bg_setting_item_single
            isFirstInGroup && isLastInGroup -> R.drawable.bg_setting_item_single
            isFirstInGroup -> R.drawable.bg_setting_item_top
            isLastInGroup -> R.drawable.bg_setting_item_bottom
            else -> R.drawable.bg_setting_item_middle
        }
        container.setBackgroundResource(background)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: FrameLayout = itemView.findViewById(R.id.item_container)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)

        fun bind(item: BackupSettingItem) {
            if (item.isHeader) {
                container.visibility = View.GONE
                itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            } else {
                container.visibility = View.VISIBLE
                itemView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                title.text = item.title
                subtitle.text = item.subtitle
                subtitle.visibility = if (item.subtitle.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}
