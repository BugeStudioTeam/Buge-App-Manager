package com.buge.appmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.model.AppInfo
import com.buge.appmanager.util.SpringAnimationHelper

class AppsAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    private var items: List<AppInfo> = emptyList()

    fun submitList(newItems: List<AppInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position])
        runSpringEnterAnimation(holder.itemView, position)
    }

    override fun getItemCount(): Int = items.size

    private fun runSpringEnterAnimation(view: View, position: Int) {
        if (position > 0) {
            view.alpha = 0f
            view.translationY = 50f
            view.post {
                SpringAnimationHelper.animateAlpha(view, 1f)
                SpringAnimationHelper.animateTranslationY(view, 0f)
            }
        }
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
}