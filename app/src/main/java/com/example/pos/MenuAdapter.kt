package com.example.pos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MenuAdapter(private val items: List<Item>, private val onItemClick: (Item) -> Unit) :
    RecyclerView.Adapter<MenuAdapter.ItemViewHolder>() {

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageItemPhoto: ImageView = view.findViewById(R.id.image_item_photo)
        val textItemName: TextView = view.findViewById(R.id.text_item_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]

        // Set item name
        holder.textItemName.text = item.name

        // Load the image if photoUri is available, otherwise set a placeholder
        if (!item.photoUri.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.photoUri)
                .placeholder(R.drawable.ic_placeholder)
                .into(holder.imageItemPhoto)
        } else {
            // Set a placeholder image if no photoUri is available
            holder.imageItemPhoto.setImageResource(R.drawable.ic_placeholder)
        }

        // Set OnClickListener
        holder.itemView.setOnClickListener {
            onItemClick(item)  // Pass the clicked item to the callback
        }
    }

    override fun getItemCount(): Int = items.size
}
