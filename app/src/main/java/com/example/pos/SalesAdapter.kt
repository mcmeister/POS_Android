package com.example.pos

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalesAdapter(
    private val context: Context,
    private val sales: MutableList<Sale>,
    private val items: List<Item>,
    private val salesChannels: List<SalesChannel>,  // List of Sales Channels
    private val onCancelSaleClick: (Sale) -> Unit
) : RecyclerView.Adapter<SalesAdapter.SaleViewHolder>() {

    class SaleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewSaleId: TextView = view.findViewById(R.id.text_view_sale_id)
        val textViewItemName: TextView = view.findViewById(R.id.text_view_item_name)
        val textViewSalesChannel: TextView = view.findViewById(R.id.text_view_sales_channel)
        val textViewQuantity: TextView = view.findViewById(R.id.text_view_quantity)
        val textViewSalePrice: TextView = view.findViewById(R.id.text_view_sale_price)
        val textViewTotal: TextView = view.findViewById(R.id.text_view_total)
        val textViewSaleTimestamp: TextView = view.findViewById(R.id.text_view_sale_timestamp)
        val buttonCancelSale: ImageButton = view.findViewById(R.id.button_cancel_sale)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SaleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.sale_item, parent, false)
        return SaleViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: SaleViewHolder, position: Int) {
        val sale = sales[position]
        val item = items.find { it.id == sale.itemId }

        holder.textViewSaleId.text = "Order ID: ${sale.id}"
        holder.textViewItemName.text = "Item: ${item?.name ?: "Unknown Item"}"

        // Find the corresponding SalesChannel by sale.id (assuming salesChannel.id somehow matches sale.id)
        val salesChannel = salesChannels.find { it.name == sale.salesChannel && it.deleted == 0 }  // Adjust logic if needed based on your app's structure

        // Determine how to display the sales channel (Deleted or Discount)
        if (salesChannel != null) {
            if (salesChannel.deleted == 0) {
                holder.textViewSalesChannel.text = if (salesChannel.discount > 0) {
                    "Sales Channel: ${salesChannel.name} (Discount: ${salesChannel.discount}%)"
                } else {
                    "Sales Channel: ${salesChannel.name} (Deleted)"
                }
            } else {
                holder.textViewSalesChannel.text = "Sales Channel: ${salesChannel.name}"
            }
        } else {
            holder.textViewSalesChannel.text = "Sales Channel: Unknown"
        }

        holder.textViewQuantity.text = "Quantity: ${sale.quantity}"
        holder.textViewSalePrice.text = "Sale Price: ${sale.salePrice}"

        // Calculate total as sale.salePrice * sale.quantity, applying the discount percentage
        val discountMultiplier = salesChannel?.let { (100 - it.discount) / 100.0 } ?: 1.0
        val total = sale.salePrice * sale.quantity * discountMultiplier

        // Round the total to the nearest integer using round()
        val roundedTotal = kotlin.math.round(total).toInt()

        // Display the rounded total
        holder.textViewTotal.text = "Total: $roundedTotal"

        // Format timestamp
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        holder.textViewSaleTimestamp.text = "Date: ${dateFormat.format(Date(sale.timestamp))}"

        // Handle Cancel button click with confirmation dialog
        holder.buttonCancelSale.setOnClickListener {
            showCancelConfirmationDialog(sale)
        }
    }

    override fun getItemCount(): Int = sales.size

    // Function to show the confirmation dialog
    private fun showCancelConfirmationDialog(sale: Sale) {
        AlertDialog.Builder(context)
            .setTitle("Cancel Order")
            .setMessage("Are you sure you want to cancel this order?")
            .setPositiveButton("Yes") { dialog, _ ->
                onCancelSaleClick(sale) // Proceed with the cancellation
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Just dismiss the dialog
            }
            .create()
            .show()
    }
}
