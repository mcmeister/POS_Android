package com.example.pos

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenuFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MenuAdapter
    private lateinit var database: AppDatabase
    private val items = mutableListOf<Item>()
    private lateinit var addItemLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_menu, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_menu)
        val buttonAddItem: Button = view.findViewById(R.id.button_add_item)

        // Initialize the database
        database = AppDatabase.getDatabase(requireContext())

        // Initialize the adapter
        adapter = MenuAdapter(items) { item ->
            // Log when editing an item
            android.util.Log.d("MenuFragment", "Editing item with ID: ${item.id}")
            val intent = Intent(requireContext(), EditItemActivity::class.java)
            intent.putExtra("item_id", item.id)
            addItemLauncher.launch(intent)
        }

        recyclerView.layoutManager = GridLayoutManager(context, 2) // Grid layout
        recyclerView.adapter = adapter

        // Initialize the ActivityResultLauncher
        addItemLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            android.util.Log.d("MenuFragment", "Returned from EditItemActivity with result: ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val newItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra("new_item", Item::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra("new_item")
                }

                newItem?.let {
                    val index = items.indexOfFirst { item -> item.id == it.id }
                    if (index != -1) {
                        // Update the existing item
                        items[index] = it
                        adapter.notifyItemChanged(index)
                        android.util.Log.d("MenuFragment", "Updated item with ID: ${it.id}")
                    } else {
                        // Add a new item
                        items.add(it)
                        adapter.notifyItemInserted(items.size - 1)
                        android.util.Log.d("MenuFragment", "New item added with ID: ${it.id}")
                    }
                }
            }
        }

        // Fetch items from the database and update the adapter
        fetchItems()

        // Log when clicking the Add Item button
        buttonAddItem.setOnClickListener {
            android.util.Log.d("MenuFragment", "Add Item button clicked")
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

            if (itemsFromDb.isNotEmpty()) {
                android.util.Log.d("MenuFragment", "Items fetched from database: ${itemsFromDb.size} items")
                items.clear()
                items.addAll(itemsFromDb)
                adapter.notifyItemRangeInserted(0, itemsFromDb.size)
            }
        }
    }
}