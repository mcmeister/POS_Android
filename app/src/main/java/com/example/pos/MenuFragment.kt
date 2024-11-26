package com.example.pos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_menu, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_menu)
        val fabAddItem: FloatingActionButton = view.findViewById(R.id.fab_add_item)

        // Initialize the database
        database = AppDatabase.getDatabase(requireContext())

        // Initialize the adapter
        adapter = MenuAdapter(
            onItemClick = { item ->
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
            android.util.Log.d("MenuFragment", "Returned from EditItemActivity with result: ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                // Refresh the list when returning from EditItemActivity
                fetchItems()
            }
        }

        // Fetch items from the database and update the adapter
        fetchItems()

        // Handle FAB click
        fabAddItem.setOnClickListener {
            android.util.Log.d("MenuFragment", "FAB Add Item clicked")
            val intent = Intent(requireContext(), EditItemActivity::class.java)
            addItemLauncher.launch(intent)
        }

        return view
    }

    // Fetch items from the database
    private fun fetchItems() {
        lifecycleScope.launch {
            val itemsFromDb = withContext(Dispatchers.IO) {
                database.itemDao().getAllItems()
            }

            android.util.Log.d("MenuFragment", "Items fetched from database: ${itemsFromDb.size} items")
            items = itemsFromDb // Update the local list
            adapter.submitList(items)
        }
    }
}