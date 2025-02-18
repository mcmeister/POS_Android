package com.example.pos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MenuAdapter(
    private val onItemClick: (Item) -> Unit,
    private val onItemChanged: (Item) -> Unit
) : ListAdapter<Item, MenuAdapter.ItemViewHolder>(ItemDiffCallback()) {

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageItemPhoto: ImageView = view.findViewById(R.id.image_item_photo)
        val textItemName: TextView = view.findViewById(R.id.text_item_name)
        val checkboxSelectItem: CheckBox = view.findViewById(R.id.checkbox_select_item)
        val buttonDecreaseQuantity: Button = view.findViewById(R.id.button_decrease_quantity)
        val buttonIncreaseQuantity: Button = view.findViewById(R.id.button_increase_quantity)
        val textQuantity: TextView = view.findViewById(R.id.text_quantity)
    }

    // Implement DiffUtil.ItemCallback
    class ItemDiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            // Compare item IDs
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            // Compare item contents
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
        return ItemViewHolder(view)
    }

    fun getSelectedItems(): List<Item> {
        return currentList.filter { it.isSelected }
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)

        // Remove previous listeners to prevent multiple triggers
        holder.checkboxSelectItem.setOnCheckedChangeListener(null)
        holder.buttonDecreaseQuantity.setOnClickListener(null)
        holder.buttonIncreaseQuantity.setOnClickListener(null)

        // Set item name
        holder.textItemName.text = item.name

        // Load the image if photoUri is available, otherwise set a placeholder
        if (!item.photoUri.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.photoUri)
                .placeholder(R.drawable.ic_placeholder)
                .into(holder.imageItemPhoto)
        } else {
            holder.imageItemPhoto.setImageResource(R.drawable.ic_placeholder)
        }

        // Set the checkbox state
        holder.checkboxSelectItem.isChecked = item.isSelected

        // Set the quantity
        holder.textQuantity.text = item.quantity.toString()

        // Checkbox listener
        holder.checkboxSelectItem.setOnCheckedChangeListener { _, isChecked ->
            val updatedItem = item.copy(isSelected = isChecked)
            onItemChanged(updatedItem)
        }

        // Decrease quantity button click listener
        holder.buttonDecreaseQuantity.setOnClickListener {
            if (item.quantity > 0) {
                val newQuantity = item.quantity - 1
                val updatedItem = item.copy(quantity = newQuantity)
                onItemChanged(updatedItem)
            }
        }

        // Increase quantity button click listener
        holder.buttonIncreaseQuantity.setOnClickListener {
            val newQuantity = item.quantity + 1
            val updatedItem = item.copy(quantity = newQuantity)
            onItemChanged(updatedItem)
        }

        // Item click listener (optional, if you have an item click action)
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }
}