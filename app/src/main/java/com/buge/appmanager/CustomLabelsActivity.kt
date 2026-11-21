// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buge.appmanager.databinding.ActivityCustomLabelsBinding
import com.buge.appmanager.model.CustomLabel
import com.buge.appmanager.util.CustomLabelManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.SnackbarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class CustomLabelsActivity : BaseActivity() {

    private lateinit var binding: ActivityCustomLabelsBinding
    private lateinit var adapter: LabelsAdapter
    private var labels: List<CustomLabel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomLabelsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadLabels()
        setupFab()

        LogManager.info(this, "CustomLabelsActivity opened")
    }

    override fun onResume() {
        super.onResume()
        loadLabels()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.title_custom_labels)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        adapter = LabelsAdapter(
            onItemClick = { label ->
                val intent = Intent(this, LabelDetailActivity::class.java)
                intent.putExtra("label_id", label.id)
                intent.putExtra("label_name", label.name)
                startActivity(intent)
            },
            onDeleteClick = { label ->
                showDeleteConfirmDialog(label)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {
                private var hasMoved = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPosition = viewHolder.bindingAdapterPosition
                    val toPosition = target.bindingAdapterPosition

                    if (
                        fromPosition == RecyclerView.NO_POSITION ||
                        toPosition == RecyclerView.NO_POSITION
                    ) {
                        return false
                    }

                    adapter.moveItem(fromPosition, toPosition)
                    hasMoved = true
                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int
                ) {
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)

                    if (hasMoved) {
                        labels = adapter.getItems()
                        CustomLabelManager.saveLabels(this@CustomLabelsActivity, labels)
                        LogManager.info(
                            this@CustomLabelsActivity,
                            "Custom label order updated"
                        )
                        hasMoved = false
                    }
                }
            }
        )

        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    private fun loadLabels() {
        labels = CustomLabelManager.getLabels(this)
        adapter.submitList(labels)

        if (labels.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }

        LogManager.debug(this, "Labels loaded", "Count: ${labels.size}")
    }

    private fun setupFab() {
        binding.fabCreate.setOnClickListener {
            showCreateLabelDialog()
        }
    }

    private fun showCreateLabelDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_label, null)
        val inputEditText = dialogView.findViewById<TextInputEditText>(R.id.label_name_input)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_new_label)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { dialog, _ ->
                val name = inputEditText?.text?.toString()?.trim()
                if (name.isNullOrEmpty()) {
                    SnackbarHelper.showSnackbar(binding.root, "Please enter a label name")
                    return@setPositiveButton
                }
                createLabel(name)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createLabel(name: String) {
        CustomLabelManager.createLabel(this, name)
        SnackbarHelper.showSnackbar(binding.root, getString(R.string.label_created))
        loadLabels()
        LogManager.success(this, "Label created", "Name: $name")
    }

    private fun showDeleteConfirmDialog(label: CustomLabel) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_label)
            .setMessage(getString(R.string.delete_label_confirm, label.name))
            .setPositiveButton(R.string.confirm) { _, _ ->
                deleteLabel(label)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteLabel(label: CustomLabel) {
        CustomLabelManager.deleteLabel(this, label.id)
        SnackbarHelper.showSnackbar(binding.root, getString(R.string.label_deleted))
        loadLabels()
        LogManager.info(this, "Label deleted", "Name: ${label.name}")
    }

    inner class LabelsAdapter(
        private val onItemClick: (CustomLabel) -> Unit,
        private val onDeleteClick: (CustomLabel) -> Unit
    ) : RecyclerView.Adapter<LabelsAdapter.ViewHolder>() {

        private var items: List<CustomLabel> = emptyList()

        fun submitList(newItems: List<CustomLabel>) {
            items = newItems.toList()
            notifyDataSetChanged()
        }

        fun getItems(): List<CustomLabel> = items.toList()

        fun moveItem(fromPosition: Int, toPosition: Int) {
            if (
                fromPosition !in items.indices ||
                toPosition !in items.indices ||
                fromPosition == toPosition
            ) {
                return
            }

            val mutableItems = items.toMutableList()
            val item = mutableItems.removeAt(fromPosition)
            mutableItems.add(toPosition, item)
            items = mutableItems

            notifyItemMoved(fromPosition, toPosition)
            notifyItemChanged(minOf(fromPosition, toPosition))
            notifyItemChanged(maxOf(fromPosition, toPosition))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_custom_label, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val label = items[position]

            holder.bind(label)
            applyItemBackground(holder, position)

            holder.itemView.setOnClickListener {
                onItemClick(label)
            }

            holder.btnDelete.setOnClickListener {
                onDeleteClick(label)
            }
        }

        private fun applyItemBackground(holder: ViewHolder, position: Int) {
            val container = holder.itemView.findViewById<FrameLayout>(R.id.item_container)
            val size = items.size

            val background = when {
                size == 1 -> R.drawable.bg_setting_item_single
                position == 0 -> R.drawable.bg_setting_item_top
                position == size - 1 -> R.drawable.bg_setting_item_bottom
                else -> R.drawable.bg_setting_item_middle
            }

            container.setBackgroundResource(background)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val labelName: TextView = itemView.findViewById(R.id.label_name)
            private val appCount: TextView = itemView.findViewById(R.id.app_count)
            val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)

            fun bind(label: CustomLabel) {
                labelName.text = label.name
                appCount.text = "${label.appPackages.size} apps"
            }
        }
    }
}
