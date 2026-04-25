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
import com.buge.appmanager.model.ActivityDetail
import com.google.android.material.button.MaterialButton

class ActivityDetailAdapter(
    private val onActivityClick: (ActivityDetail) -> Unit
) : ListAdapter<ActivityDetail, ActivityDetailAdapter.ActivityViewHolder>(ActivityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_detail, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val exportIcon: ImageView = itemView.findViewById(R.id.export_icon)
        private val activityName: TextView = itemView.findViewById(R.id.activity_name)
        private val className: TextView = itemView.findViewById(R.id.class_name)
        private val exportStatus: TextView = itemView.findViewById(R.id.export_status)
        private val launchMode: TextView = itemView.findViewById(R.id.launch_mode)
        private val permission: TextView = itemView.findViewById(R.id.permission)
        private val intentFilterBadge: View = itemView.findViewById(R.id.intent_filter_badge)
        private val intentFilterCount: TextView = itemView.findViewById(R.id.intent_filter_count)
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

            if (activity.intentFilterCount > 0) {
                intentFilterBadge.visibility = View.VISIBLE
                intentFilterCount.text = activity.intentFilterCount.toString()
            } else {
                intentFilterBadge.visibility = View.GONE
            }

            btnLaunch.setOnClickListener {
                if (activity.isExported) {
                    onActivityClick(activity)
                }
            }

            itemView.setOnClickListener {
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
    }
}