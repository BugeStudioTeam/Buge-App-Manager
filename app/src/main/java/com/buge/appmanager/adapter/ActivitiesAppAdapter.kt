// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.util.CornerPressAnimator

class ActivitiesAppAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<ActivitiesAppAdapter.AppViewHolder>() {

    private var items: List<AppInfo> = emptyList()

    fun submitList(newItems: List<AppInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position])
        
        // Set corner radius based on position
        val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
        val size = items.size

        val topRadius = if (position == 0) 24f else 4f
        val bottomRadius = if (position == size - 1) 24f else 4f
        CornerPressAnimator.apply(container, topRadius, bottomRadius)
    }

    override fun getItemCount(): Int = items.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val systemAppBadge: View = itemView.findViewById(R.id.system_app_badge)

        fun bind(app: AppInfo) {
            if (app.icon != null) {
                appIcon.setImageDrawable(app.icon)
            } else {
                appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            appName.text = app.appName
            packageName.text = app.packageName

            if (app.isSystemApp) {
                systemAppBadge.visibility = View.VISIBLE
            } else {
                systemAppBadge.visibility = View.GONE
            }

            itemView.setOnClickListener { onAppClick(app) }
        }
    }
}