package com.anlc.client.ui.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.anlc.client.inventory.InventoryManager
import com.neverlands.anlc.ui.main.MainViewModel // Assuming MainViewModel will provide access to InventoryManager
import com.neverlands.anlc.databinding.FragmentInventoryBinding

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    // Assuming MainViewModel holds the InventoryManager instance
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var inventoryAdapter: InventoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeInventory()
    }

    private fun setupRecyclerView() {
        inventoryAdapter = InventoryAdapter()
        binding.inventoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = inventoryAdapter
        }
    }

    private fun observeInventory() {
        // Observe changes in the inventory from the MainViewModel
        // MainViewModel needs to expose the inventory data (e.g., via LiveData or StateFlow)
        mainViewModel.inventory.observe(viewLifecycleOwner) { inventoryList ->
            inventoryAdapter.submitList(inventoryList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
