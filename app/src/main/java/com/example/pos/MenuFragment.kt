package com.example.pos

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenuFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MenuAdapter
    private lateinit var database: AppDatabase
    private var items = listOf<Item>() // Use an immutable list
    private lateinit var addItemLauncher: ActivityResultLauncher<Intent>
    private lateinit var fabCheckout: FloatingActionButton
    private lateinit var salesChannels: List<SalesChannel>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_menu, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_menu)
        val fabAddItem: FloatingActionButton = view.findViewById(R.id.fab_add_item)
        fabCheckout = view.findViewById(R.id.fab_checkout) // Initialize the new FAB

        // Initialize the database
        database = AppDatabase.getDatabase(requireContext())

        // Initialize the adapter
        adapter = MenuAdapter(
            onItemClick = { item ->
                // Log when editing an item
                android.util.Log.d("MenuFragment", "Editing item with ID: ${item.id}")
                val intent = Intent(requireContext(), EditItemActivity::class.java)
                intent.putExtra("item_id", item.id)
                addItemLauncher.launch(intent)
            },
            onItemChanged = { updatedItem ->
                // Update the item in the list and submit the new list
                items = items.map { if (it.id == updatedItem.id) updatedItem else it }
                adapter.submitList(items)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Initialize the ActivityResultLauncher
        addItemLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Refresh the list when returning from EditItemActivity
                fetchItems()
            }
        }

        // Fetch items and sales channels from the database
        fetchItems()
        fetchSalesChannels()

        // Handle FAB Add Item click
        fabAddItem.setOnClickListener {
            val intent = Intent(requireContext(), EditItemActivity::class.java)
            addItemLauncher.launch(intent)
        }

        // Handle FAB Checkout click
        fabCheckout.setOnClickListener {
            processCheckout()
        }

        return view
    }

    // Fetch items from the database
    private fun fetchItems() {
        lifecycleScope.launch {
            val itemsFromDb = withContext(Dispatchers.IO) {
                database.itemDao().getAllItems()
            }
            items = itemsFromDb
            adapter.submitList(items)
        }
    }

    // Fetch sales channels from the database
    private fun fetchSalesChannels() {
        lifecycleScope.launch {
            salesChannels = withContext(Dispatchers.IO) {
                database.saleDao().getActiveSalesChannels()
            }
        }
    }

    // Process checkout for selected items
    private fun processCheckout() {
        val selectedItems = items.filter { it.isSelected && it.quantity > 0 }
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "Please select items to checkout", Toast.LENGTH_SHORT).show()
            return
        }

        // Proceed to select sales channel
        showSalesChannelDialog(selectedItems)
    }

    // Show dialog to select sales channel
    // Show dialog to select sales channel
    private fun showSalesChannelDialog(selectedItems: List<Item>) {
        lifecycleScope.launch {
            val salesChannels = withContext(Dispatchers.IO) {
                database.saleDao().getActiveSalesChannels()
            }

            val salesChannelNames = salesChannels.map { it.name }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("Select Sales Channel")
                .setItems(salesChannelNames) { _, which ->
                    val selectedSalesChannel = salesChannels[which]
                    processSelectedItems(selectedItems, selectedSalesChannel) // ✅ Updated function
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // Process selected items as a single order
    private fun processSelectedItems(selectedItems: List<Item>, salesChannel: SalesChannel) {
        lifecycleScope.launch {
            if (selectedItems.isEmpty()) {
                Toast.makeText(requireContext(), "No items selected for the order.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val discount = salesChannel.discount
            val timestamp = System.currentTimeMillis()

            // ✅ Get the last order ID and increment it
            val lastOrderId = withContext(Dispatchers.IO) { database.saleDao().getLastOrderId() ?: 0 }
            val newOrderId = lastOrderId + 1 // ✅ Sequential order numbering

            val newSales = selectedItems.map { item ->
                Sale(
                    id = 0,  // SQLite auto-generates unique IDs
                    orderId = newOrderId, // ✅ Assign sequential orderId
                    itemId = item.id,
                    itemName = item.name,
                    quantity = item.quantity,
                    salePrice = item.salePrice.toDouble(),
                    salesChannel = salesChannel.name,
                    rawPrice = item.rawPrice.toDouble(),
                    profit = ((item.salePrice - item.rawPrice) * item.quantity * (100 - discount) / 100.0).toInt(),
                    timestamp = timestamp,
                    cancelled = 0
                )
            }

            withContext(Dispatchers.IO) {
                database.saleDao().insertSales(newSales)
            }

            // Clear selections and reset quantities
            items = items.map { if (it.isSelected) it.copy(isSelected = false, quantity = 0) else it }

            // Update UI
            withContext(Dispatchers.Main) {
                adapter.submitList(items)
                Toast.makeText(requireContext(), "Order #$newOrderId recorded successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }
}