package com.buge.appmanager.ui

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.R
import com.buge.appmanager.shizuku.ShizukuManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

sealed class SettingItem {
    data class Header(val title: String) : SettingItem()
    data class Normal(val title: String, val subtitle: String, val iconRes: Int, val isClickable: Boolean = true) : SettingItem()
    data class SwitchItem(val title: String, val isChecked: Boolean, val iconRes: Int, val isEnabled: Boolean = true) : SettingItem()
    data class About(val version: String) : SettingItem()
    data class AboutMore(val title: String, val subtitle: String) : SettingItem()
    object Shizuku : SettingItem()
    object StoragePermission : SettingItem()
}

class SettingsAdapter(
    private val items: List<SettingItem>,
    private val onItemClick: (SettingItem) -> Unit,
    private val onSwitchChange: (SettingItem.SwitchItem, Boolean) -> Unit,
    private val onStorageGrantClick: () -> Unit
) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 1
        private const val TYPE_NORMAL = 2
        private const val TYPE_SWITCH = 3
        private const val TYPE_ABOUT = 4
        private const val TYPE_SHIZUKU = 5
        private const val TYPE_ABOUT_MORE = 6
        private const val TYPE_STORAGE_PERMISSION = 7
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SettingItem.Header -> TYPE_HEADER
            is SettingItem.Normal -> TYPE_NORMAL
            is SettingItem.SwitchItem -> TYPE_SWITCH
            is SettingItem.About -> TYPE_ABOUT
            is SettingItem.AboutMore -> TYPE_ABOUT_MORE
            is SettingItem.Shizuku -> TYPE_SHIZUKU
            is SettingItem.StoragePermission -> TYPE_STORAGE_PERMISSION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_group_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_NORMAL -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_normal, parent, false)
                NormalViewHolder(view)
            }
            TYPE_SWITCH -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_switch, parent, false)
                SwitchViewHolder(view)
            }
            TYPE_ABOUT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_about, parent, false)
                AboutViewHolder(view)
            }
            TYPE_ABOUT_MORE -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_about_more, parent, false)
                AboutMoreViewHolder(view)
            }
            TYPE_SHIZUKU -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_shizuku, parent, false)
                ShizukuViewHolder(view)
            }
            TYPE_STORAGE_PERMISSION -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting_storage_permission, parent, false)
                StoragePermissionViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        when (holder) {
            is HeaderViewHolder -> {
                holder.bind(item as SettingItem.Header)
            }
            is NormalViewHolder -> {
                holder.bind(item as SettingItem.Normal)
            }
            is SwitchViewHolder -> {
                holder.bind(item as SettingItem.SwitchItem)
            }
            is AboutViewHolder -> {
                holder.bind(item as SettingItem.About)
            }
            is AboutMoreViewHolder -> {
                holder.bind(item as SettingItem.AboutMore)
            }
            is ShizukuViewHolder -> {
                holder.bind()
            }
            is StoragePermissionViewHolder -> {
                holder.bind()
            }
        }

        val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
        if (container != null) {
            val size = items.size
            val isLastInGroup = position + 1 < size && items[position + 1] is SettingItem.Header ||
                    position == size - 1 ||
                    (position + 1 < size && items[position + 1] is SettingItem.Header)
            val isFirstInGroup = position == 0 || items[position - 1] is SettingItem.Header

            val background = when {
                size == 1 -> R.drawable.bg_setting_item_single
                isFirstInGroup && isLastInGroup -> R.drawable.bg_setting_item_single
                isFirstInGroup -> R.drawable.bg_setting_item_top
                isLastInGroup -> R.drawable.bg_setting_item_bottom
                else -> R.drawable.bg_setting_item_middle
            }
            container.setBackgroundResource(background)
        }
    }

    override fun getItemCount(): Int = items.size

    sealed class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class HeaderViewHolder(itemView: View) : ViewHolder(itemView) {
        private val headerText: TextView = itemView.findViewById(R.id.group_header)
        fun bind(item: SettingItem.Header) {
            headerText.text = item.title
        }
    }

    class NormalViewHolder(itemView: View) : ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        fun bind(item: SettingItem.Normal) {
            icon.setImageResource(item.iconRes)
            title.text = item.title
            subtitle.text = item.subtitle
            if (!item.isClickable) {
                itemView.isClickable = false
                itemView.alpha = 0.6f
            } else {
                itemView.isClickable = true
                itemView.alpha = 1f
                itemView.setOnClickListener { v ->
                    (v.parent as? RecyclerView)?.let { rv ->
                        val position = rv.getChildAdapterPosition(v)
                        (rv.adapter as? SettingsAdapter)?.let { adapter ->
                            ValueAnimator.ofFloat(1f, 0.96f).apply {
                                duration = 80
                                addUpdateListener { animator ->
                                    val scale = animator.animatedValue as Float
                                    v.scaleX = scale
                                    v.scaleY = scale
                                }
                                doOnEnd {
                                    ValueAnimator.ofFloat(0.96f, 1f).apply {
                                        duration = 80
                                        addUpdateListener { animator ->
                                            val scale = animator.animatedValue as Float
                                            v.scaleX = scale
                                            v.scaleY = scale
                                        }
                                        start()
                                    }
                                }
                                start()
                            }
                            adapter.onItemClick(adapter.items[position])
                        }
                    }
                }
            }
        }
    }

    class SwitchViewHolder(itemView: View) : ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val switchControl: MaterialSwitch = itemView.findViewById(R.id.switch_control)
        fun bind(item: SettingItem.SwitchItem) {
            icon.setImageResource(item.iconRes)
            title.text = item.title
            switchControl.isChecked = item.isChecked
            switchControl.isEnabled = item.isEnabled
            if (!item.isEnabled) {
                itemView.alpha = 0.6f
            } else {
                itemView.alpha = 1f
            }
            switchControl.setOnCheckedChangeListener { _, isChecked ->
                if (item.isEnabled) {
                    (itemView.parent as? RecyclerView)?.let { rv ->
                        val position = rv.getChildAdapterPosition(itemView)
                        (rv.adapter as? SettingsAdapter)?.let { adapter ->
                            adapter.onSwitchChange(item, isChecked)
                        }
                    }
                } else {
                    switchControl.isChecked = item.isChecked
                }
            }
        }
    }

    class AboutViewHolder(itemView: View) : ViewHolder(itemView) {
        private val version: TextView = itemView.findViewById(R.id.version)
        private val developer: TextView = itemView.findViewById(R.id.developer)
        private val aboutDesc: TextView = itemView.findViewById(R.id.about_desc)
        fun bind(item: SettingItem.About) {
            version.text = item.version
            developer.text = itemView.context.getString(R.string.developer_name)
            aboutDesc.text = itemView.context.getString(R.string.about_desc)
            itemView.setOnClickListener { v ->
                (v.parent as? RecyclerView)?.let { rv ->
                    val position = rv.getChildAdapterPosition(v)
                    (rv.adapter as? SettingsAdapter)?.let { adapter ->
                        ValueAnimator.ofFloat(1f, 0.96f).apply {
                            duration = 80
                            addUpdateListener { animator ->
                                val scale = animator.animatedValue as Float
                                v.scaleX = scale
                                v.scaleY = scale
                            }
                            doOnEnd {
                                ValueAnimator.ofFloat(0.96f, 1f).apply {
                                    duration = 80
                                    addUpdateListener { animator ->
                                        val scale = animator.animatedValue as Float
                                        v.scaleX = scale
                                        v.scaleY = scale
                                    }
                                    start()
                                }
                            }
                            start()
                        }
                        adapter.onItemClick(adapter.items[position])
                    }
                }
            }
        }
    }

    class AboutMoreViewHolder(itemView: View) : ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        fun bind(item: SettingItem.AboutMore) {
            icon.setImageResource(R.drawable.ic_about)
            title.text = item.title
            subtitle.text = item.subtitle
            itemView.setOnClickListener { v ->
                (v.parent as? RecyclerView)?.let { rv ->
                    val position = rv.getChildAdapterPosition(v)
                    (rv.adapter as? SettingsAdapter)?.let { adapter ->
                        ValueAnimator.ofFloat(1f, 0.96f).apply {
                            duration = 80
                            addUpdateListener { animator ->
                                val scale = animator.animatedValue as Float
                                v.scaleX = scale
                                v.scaleY = scale
                            }
                            doOnEnd {
                                ValueAnimator.ofFloat(0.96f, 1f).apply {
                                    duration = 80
                                    addUpdateListener { animator ->
                                        val scale = animator.animatedValue as Float
                                        v.scaleX = scale
                                        v.scaleY = scale
                                    }
                                    start()
                                }
                            }
                            start()
                        }
                        adapter.onItemClick(adapter.items[position])
                    }
                }
            }
        }
    }

    class ShizukuViewHolder(itemView: View) : ViewHolder(itemView) {
        fun bind() {
        }
    }

    class StoragePermissionViewHolder(itemView: View) : ViewHolder(itemView) {
        private val btnGrant: MaterialButton = itemView.findViewById(R.id.btn_grant_storage)
        private val statusText: TextView = itemView.findViewById(R.id.storage_status_text)

        fun bind() {
            val isShizukuAvailable = ShizukuManager.isShizukuAvailable() && ShizukuManager.hasShizukuPermission()

            if (isShizukuAvailable) {
                btnGrant.isEnabled = true
                btnGrant.alpha = 1f
                btnGrant.text = itemView.context.getString(R.string.storage_permission_grant)
                statusText.text = itemView.context.getString(R.string.pref_storage_permission_summary)
            } else {
                btnGrant.isEnabled = false
                btnGrant.alpha = 0.4f
                btnGrant.text = itemView.context.getString(R.string.shizuku_not_authorized)
                statusText.text = itemView.context.getString(R.string.error_no_shizuku)
            }

            btnGrant.setOnClickListener {
                (itemView.parent as? RecyclerView)?.let { rv ->
                    val position = rv.getChildAdapterPosition(itemView)
                    (rv.adapter as? SettingsAdapter)?.let { adapter ->
                        adapter.onStorageGrantClick()
                    }
                }
            }
        }
    }
}