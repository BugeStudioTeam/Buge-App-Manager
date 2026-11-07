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

class ActivityInfoHeaderAdapter(
    private val appName: String,
    private val packageName: String,
    private val isSystem: Boolean,
    private val appIcon: Drawable?
) : RecyclerView.Adapter<ActivityInfoHeaderAdapter.HeaderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_header, parent, false)
        return HeaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind(appName, packageName, isSystem, appIcon)
    }

    override fun getItemCount(): Int = 1

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val systemBadge: View = itemView.findViewById(R.id.system_badge)

        fun bind(
            name: String,
            pkg: String,
            isSystem: Boolean,
            icon: Drawable?
        ) {
            if (icon != null) {
                appIcon.setImageDrawable(icon)
            } else {
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            appName.text = name
            packageName.text = pkg
            systemBadge.visibility = if (isSystem) View.VISIBLE else View.GONE
        }
    }
}