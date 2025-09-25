package com.anlc.client.inventory

import com.anlc.client.profile.ProfileSettings
import java.util.*

class InventoryManager(private val profileSettings: ProfileSettings) {
    private val inventory: MutableList<InvEntry> = mutableListOf()

    /**
     * Adds an InvEntry to the inventory. If an identical item already exists,
     * its count is incremented (stacking). Otherwise, the new item is added.
     */
    fun addOrUpdateInvEntry(newItem: InvEntry) {
        val existingItem = inventory.find { it.compareTo(newItem) == 0 }
        if (existingItem != null) {
            existingItem.inc()
        } else {
            inventory.add(newItem)
        }
    }

    /**
     * Returns an immutable list of the current inventory items.
     */
    fun getInventory(): List<InvEntry> {
        return inventory.toList()
    }

    /**
     * Clears the entire inventory.
     */
    fun clearInventory() {
        inventory.clear()
    }

    fun groupAndSortInventory(rawInventory: List<InvEntry>): List<InvEntry> {
        val processedInventory: MutableList<InvEntry> = mutableListOf()

        // Grouping (deduplication)
        if (profileSettings.doInvPack) {
            for (newItem in rawInventory) {
                val existingItem = processedInventory.find { it.compareTo(newItem) == 0 }
                if (existingItem != null) {
                    existingItem.inc()
                } else {
                    processedInventory.add(newItem)
                }
            }
        } else {
            processedInventory.addAll(rawInventory)
        }

        // Sorting
        if (profileSettings.doInvSort) {
            processedInventory.sortWith(Comparator { item1, item2 ->
                var result = item1.compareTo(item2)
                if (result == 0) {
                    // If compareTo returns 0, use compareDolg for further sorting
                    result = item1.compareDolg(item2)
                }
                result
            })
        }

        return processedInventory.toList()
    }
}