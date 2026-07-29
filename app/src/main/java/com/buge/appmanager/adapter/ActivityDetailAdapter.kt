// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adapter

import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.ActivityDetail
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ActivityDetailAdapter(
    private val onActivityClick: (ActivityDetail) -> Unit,
    private val onShortcutCreate: (ActivityDetail, View) -> Unit
) : ListAdapter<ActivityDetail, ActivityDetailAdapter.ActivityViewHolder>(ActivityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_detail, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activity = getItem(position)
        holder.bind(activity)
        updateItemBackground(holder, position)

        // Fuck: clear old listeners to avoid multiple registration
        holder.itemView.setOnLongClickListener(null)
        holder.itemView.setOnClickListener(null)

        // Click listener
        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val item = getItem(pos)
                onActivityClick(item)
            }
        }

        // Long click listener for shortcut creation
        holder.itemView.setOnLongClickListener { view ->
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val item = getItem(pos)
                if (item.isExported) {
                    onShortcutCreate(item, view)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val activity = getItem(position)
            holder.bind(activity)
            updateItemBackground(holder, position)
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<ActivityDetail>, currentList: MutableList<ActivityDetail>) {
        super.onCurrentListChanged(previousList, currentList)
        if (previousList.size != currentList.size) {
            notifyItemRangeChanged(0, currentList.size, "background")
        }
    }

    private fun updateItemBackground(holder: ActivityViewHolder, position: Int) {
        val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
        val size = itemCount

        val background = when {
            size == 1 -> R.drawable.bg_setting_item_single
            position == 0 -> R.drawable.bg_setting_item_top
            position == size - 1 -> R.drawable.bg_setting_item_bottom
            else -> R.drawable.bg_setting_item_middle
        }
        container.setBackgroundResource(background)
    }

    override fun submitList(list: List<ActivityDetail>?) {
        super.submitList(list)
    }

    inner class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val exportIcon: ImageView = itemView.findViewById(R.id.export_icon)
        private val activityName: TextView = itemView.findViewById(R.id.activity_name)
        private val className: TextView = itemView.findViewById(R.id.class_name)
        private val exportStatus: TextView = itemView.findViewById(R.id.export_status)
        private val launchMode: TextView = itemView.findViewById(R.id.launch_mode)
        private val permission: TextView = itemView.findViewById(R.id.permission)
        private val btnLaunch: MaterialButton = itemView.findViewById(R.id.btn_launch)

        fun bind(activity: ActivityDetail) {
            activityName.text = activity.name
            val shortClassName = activity.className.substringAfterLast(".")
            className.text = shortClassName

            if (activity.isExported) {
                exportIcon.setImageResource(R.drawable.ic_check)
                exportIcon.setColorFilter(itemView.context.getColor(R.color.color_granted))
                exportStatus.text = "Exported"
                exportStatus.setTextColor(itemView.context.getColor(R.color.color_granted))
                activityName.setTextColor(itemView.context.getColor(R.color.color_granted))
                btnLaunch.isEnabled = true
                btnLaunch.alpha = 1.0f
            } else {
                exportIcon.setImageResource(R.drawable.ic_block)
                exportIcon.setColorFilter(itemView.context.getColor(R.color.color_denied))
                exportStatus.text = "Not Exported"
                exportStatus.setTextColor(itemView.context.getColor(R.color.color_denied))
                activityName.setTextColor(itemView.context.getColor(R.color.color_denied))
                btnLaunch.isEnabled = false
                btnLaunch.alpha = 0.5f
            }

            val modeText = when (activity.launchMode) {
                "standard" -> "Mode: Standard"
                "singleTop" -> "Mode: Single Top"
                "singleTask" -> "Mode: Single Task"
                "singleInstance" -> "Mode: Single Instance"
                else -> "Mode: ${activity.launchMode}"
            }
            launchMode.text = modeText

            if (activity.permission != "None" && activity.permission.isNotEmpty()) {
                permission.visibility = View.VISIBLE
                val permShort = activity.permission.substringAfterLast(".")
                permission.text = "Required: $permShort"
            } else {
                permission.visibility = View.GONE
            }

            btnLaunch.setOnClickListener {
                if (activity.isExported) {
                    onActivityClick(activity)
                }
            }
        }
    }

    class ActivityDiffCallback : DiffUtil.ItemCallback<ActivityDetail>() {
        override fun areItemsTheSame(oldItem: ActivityDetail, newItem: ActivityDetail): Boolean {
            return oldItem.className == newItem.className
        }

        override fun areContentsTheSame(oldItem: ActivityDetail, newItem: ActivityDetail): Boolean {
            return oldItem.className == newItem.className &&
                   oldItem.isExported == newItem.isExported &&
                   oldItem.name == newItem.name
        }

        override fun getChangePayload(oldItem: ActivityDetail, newItem: ActivityDetail): Any? {
            if (oldItem.isExported != newItem.isExported ||
                oldItem.name != newItem.name) {
                return "content"
            }
            return null
        }
    }
}