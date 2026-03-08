package com.buge.appmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.AppInfo
import com.google.android.material.chip.Chip

class AppsAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppsAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val packageName: TextView = itemView.findViewById(R.id.package_name)
        private val appVersion: TextView = itemView.findViewById(R.id.app_version)
        private val systemAppBadge: View = itemView.findViewById(R.id.system_app_badge)

        fun bind(app: AppInfo) {
            appIcon.setImageDrawable(app.icon)
            appName.text = app.appName
            packageName.text = app.packageName
            appVersion.text = "v${app.versionName}"

            if (app.isSystemApp) {
                systemAppBadge.visibility = View.VISIBLE
            } else {
                systemAppBadge.visibility = View.GONE
            }

            itemView.setOnClickListener { onAppClick(app) }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName &&
                   oldItem.isEnabled == newItem.isEnabled &&
                   oldItem.versionName == newItem.versionName
        }
    }
}
