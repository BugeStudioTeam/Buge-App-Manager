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
import com.buge.appmanager.model.PermissionInfo
import com.google.android.material.button.MaterialButton

class PermissionDetailAdapter(
    private val onToggle: (PermissionInfo) -> Unit
) : ListAdapter<PermissionInfo, PermissionDetailAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_permission, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val statusIcon: ImageView = itemView.findViewById(R.id.perm_status_icon)
        private val permName: TextView = itemView.findViewById(R.id.perm_name)
        private val permFullName: TextView = itemView.findViewById(R.id.perm_full_name)
        private val toggleBtn: MaterialButton = itemView.findViewById(R.id.btn_toggle_perm)

        fun bind(perm: PermissionInfo) {
            // Friendly name
            permName.text = getFriendlyPermissionName(perm.name)
            permFullName.text = perm.name.substringAfterLast(".")

            if (perm.isGranted) {
                statusIcon.setImageResource(R.drawable.ic_check)
                toggleBtn.text = itemView.context.getString(R.string.revoke_permission)
            } else {
                statusIcon.setImageResource(R.drawable.ic_block)
                toggleBtn.text = itemView.context.getString(R.string.grant_permission)
            }

            // Only show toggle for dangerous/runtime permissions
            if (perm.isDangerous) {
                toggleBtn.visibility = View.VISIBLE
                toggleBtn.setOnClickListener { onToggle(perm) }
            } else {
                toggleBtn.visibility = View.GONE
            }
        }

        private fun getFriendlyPermissionName(permission: String): String {
            return when (permission) {
                "android.permission.RECORD_AUDIO" -> "Microphone"
                "android.permission.CAMERA" -> "Camera"
                "android.permission.ACCESS_FINE_LOCATION" -> "Precise Location"
                "android.permission.ACCESS_COARSE_LOCATION" -> "Approximate Location"
                "android.permission.ACCESS_BACKGROUND_LOCATION" -> "Background Location"
                "android.permission.READ_CONTACTS" -> "Read Contacts"
                "android.permission.WRITE_CONTACTS" -> "Write Contacts"
                "android.permission.GET_ACCOUNTS" -> "Get Accounts"
                "android.permission.READ_EXTERNAL_STORAGE" -> "Read Storage"
                "android.permission.WRITE_EXTERNAL_STORAGE" -> "Write Storage"
                "android.permission.READ_PHONE_STATE" -> "Phone State"
                "android.permission.CALL_PHONE" -> "Make Calls"
                "android.permission.READ_CALL_LOG" -> "Read Call Log"
                "android.permission.WRITE_CALL_LOG" -> "Write Call Log"
                "android.permission.SEND_SMS" -> "Send SMS"
                "android.permission.RECEIVE_SMS" -> "Receive SMS"
                "android.permission.READ_SMS" -> "Read SMS"
                "android.permission.READ_CALENDAR" -> "Read Calendar"
                "android.permission.WRITE_CALENDAR" -> "Write Calendar"
                "android.permission.BODY_SENSORS" -> "Body Sensors"
                "android.permission.ACTIVITY_RECOGNITION" -> "Activity Recognition"
                "android.permission.BLUETOOTH_SCAN" -> "Bluetooth Scan"
                "android.permission.BLUETOOTH_CONNECT" -> "Bluetooth Connect"
                "android.permission.POST_NOTIFICATIONS" -> "Notifications"
                "android.permission.READ_MEDIA_IMAGES" -> "Media Images"
                "android.permission.READ_MEDIA_VIDEO" -> "Media Video"
                "android.permission.READ_MEDIA_AUDIO" -> "Media Audio"
                else -> permission.substringAfterLast(".").replace("_", " ").lowercase()
                    .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PermissionInfo>() {
        override fun areItemsTheSame(old: PermissionInfo, new: PermissionInfo) = old.name == new.name
        override fun areContentsTheSame(old: PermissionInfo, new: PermissionInfo) = old == new
    }
}
