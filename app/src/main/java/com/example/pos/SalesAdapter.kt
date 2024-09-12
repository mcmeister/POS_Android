package com.example.pos

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalesAdapter(private val sales: List<Sale>, private val items: List<Item>) : RecyclerView.Adapter<SalesAdapter.SaleViewHolder>() {

    // ViewHolder class to hold each sale item view
    class SaleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewSaleId: TextView = view.findViewById(R.id.text_view_sale_id)
        val textViewItemName: TextView = view.findViewById(R.id.text_view_item_name) // New Item Name TextView
        val textViewSalesChannel: TextView = view.findViewById(R.id.text_view_sales_channel)
        val textViewQuantity: TextView = view.findViewById(R.id.text_view_quantity)
        val textViewRawPrice: TextView = view.findViewById(R.id.text_view_raw_price)
        val textViewSalePrice: TextView = view.findViewById(R.id.text_view_sale_price)
        val textViewTotal: TextView = view.findViewById(R.id.text_view_total)
        val textViewSaleTimestamp: TextView = view.findViewById(R.id.text_view_sale_timestamp)
    }

    // Inflate the layout for each sale item
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SaleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.sale_item, parent, false)
        return SaleViewHolder(view)
    }

    // Bind data to each sale item view
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: SaleViewHolder, position: Int) {
        val sale = sales[position]
        val item = items.find { it.id == sale.itemId } // Get corresponding item details

        // Set the sale details
        holder.textViewSaleId.text = "Order ID: ${sale.id}"
        holder.textViewItemName.text = "Item: ${item?.name ?: "Unknown Item"}" // Set Item Name
        holder.textViewSalesChannel.text = "Sales Channel: ${sale.salesChannel}"
        holder.textViewQuantity.text = "Quantity: ${sale.quantity}"
        holder.textViewRawPrice.text = "Raw Price: ${item?.rawPrice ?: 0}"
        holder.textViewSalePrice.text = "Sale Price: ${sale.salePrice}"

        // Correct profit calculation: (Sale Price - Raw Price) * Quantity
        // val rawPrice = item?.rawPrice ?: 0
        val total = sale.salePrice * sale.quantity
        holder.textViewTotal.text = "Total: $total"

        // Format the timestamp to a readable date
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = Date(sale.timestamp)
        holder.textViewSaleTimestamp.text = "Date: ${dateFormat.format(date)}"
    }

    // Return the total number of sales
    override fun getItemCount(): Int = sales.size
}
