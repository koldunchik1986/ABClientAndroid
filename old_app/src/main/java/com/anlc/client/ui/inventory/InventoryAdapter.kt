package com.anlc.client.ui.inventory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.anlc.client.inventory.InvEntry
import com.bumptech.glide.Glide // Assuming Glide for image loading
import com.neverlands.anlc.R
import com.neverlands.anlc.databinding.ItemInventoryBinding

class InventoryAdapter : ListAdapter<InvEntry, InventoryAdapter.InventoryViewHolder>(InvEntryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val binding = ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return InventoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val invEntry = getItem(position)
        holder.bind(invEntry)
    }

    inner class InventoryViewHolder(private val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(invEntry: InvEntry) {
            binding.itemNameTextView.text = invEntry.name
            binding.itemCountTextView.text = if (invEntry.count > 1) "(${invEntry.count} шт.)" else ""
            binding.itemDolgTextView.text = if (invEntry.dolg.isNotEmpty()) "[${invEntry.dolg}]" else ""
            binding.itemPropertiesTextView.text = invEntry.properties

            // Load image using Glide (assuming it's added as a dependency)
            Glide.with(binding.itemImageView.context)
                .load("http://" + invEntry.img) // Prepend http:// as the C# code had "src=http://"
                .placeholder(R.drawable.ic_launcher_background) // Placeholder image
                .error(R.drawable.ic_launcher_background) // Error image
                .into(binding.itemImageView)

            // Apply visual changes based on InvEntry.buildForUi() logic
            if (invEntry.isExpired()) {
                // Change background color or add an overlay for expired items
                binding.root.setBackgroundColor(binding.root.context.resources.getColor(R.color.expired_item_background))
            } else {
                binding.root.setBackgroundColor(binding.root.context.resources.getColor(android.R.color.transparent))
            }
        }
    }

    private class InvEntryDiffCallback : DiffUtil.ItemCallback<InvEntry>() {
        override fun areItemsTheSame(oldItem: InvEntry, newItem: InvEntry): Boolean {
            // Assuming name and image are unique identifiers for an item type
            return oldItem.name == newItem.name && oldItem.img == newItem.img
        }

        override fun areContentsTheSame(oldItem: InvEntry, newItem: InvEntry): Boolean {
            // Compare all relevant properties to detect content changes
            return oldItem == newItem // Data class equals handles this
        }
    }
}
