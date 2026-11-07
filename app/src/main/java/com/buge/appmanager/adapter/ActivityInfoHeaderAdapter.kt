// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R

data class ActivityInfoHeader(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

class ActivityInfoHeaderAdapter : RecyclerView.Adapter<ActivityInfoHeaderAdapter.HeaderViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 1001
    }

    private var item: ActivityInfoHeader? = null

    fun submit(header: ActivityInfoHeader) {
        item = header
        notifyItemChanged(0)
    }

    override fun getItemCount(): Int = if (item == null) 0 else 1

    override fun getItemViewType(position: Int): Int = VIEW_TYPE_HEADER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_info_header, parent, false)
        return HeaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        item?.let { holder.bind(it) }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val systemBadge: View = itemView.findViewById(R.id.system_badge)

        fun bind(header: ActivityInfoHeader) {
            if (header.icon != null) {
                appIcon.setImageDrawable(header.icon)
            } else {
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            appName.text = header.appName
            packageName.text = header.packageName
            systemBadge.visibility = if (header.isSystemApp) View.VISIBLE else View.GONE
        }
    }
}