package com.example.pos

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OrderItemAdapter(
    private val orderItems: List<Sale>,
    private val salesChannels: List<SalesChannel>,
    private val onCancelSaleClick: (Sale) -> Unit
) : RecyclerView.Adapter<OrderItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewItemName: TextView = view.findViewById(R.id.text_view_item_name)
        val textViewSalePrice: TextView = view.findViewById(R.id.text_view_sale_price)
        val textViewQuantity: TextView = view.findViewById(R.id.text_view_quantity)
        val textViewSalesChannel: TextView = view.findViewById(R.id.text_view_sales_channel)
        val textViewTotal: TextView = view.findViewById(R.id.text_view_total)
        val buttonCancelSale: Button = view.findViewById(R.id.button_cancel_sale)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.order_item_row, parent, false)
        return ItemViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val sale = orderItems[position]

        holder.textViewItemName.text = sale.itemName
        holder.textViewSalePrice.text = "Sale Price: ${sale.salePrice}"
        holder.textViewQuantity.text = "Quantity: ${sale.quantity}"
        holder.textViewSalesChannel.text = "Channel: ${sale.salesChannel}"

        // Get sales channel discount safely
        val discount = salesChannels.find { it.name == sale.salesChannel }?.discount ?: 0
        val total = sale.salePrice * sale.quantity * ((100 - discount).toDouble() / 100.0)
        val roundedTotal = kotlin.math.round(total).toInt()
        holder.textViewTotal.text = "Total: $roundedTotal"

        // Handle item cancellation
        holder.buttonCancelSale.setOnClickListener {
            showCancelItemDialog(holder.itemView.context, sale)
        }
    }

    override fun getItemCount(): Int = orderItems.size

    private fun showCancelItemDialog(context: Context, sale: Sale) {
        AlertDialog.Builder(context)
            .setTitle("Cancel Item")
            .setMessage("Are you sure you want to cancel this item?")
            .setPositiveButton("Yes") { dialog, _ ->
                onCancelSaleClick(sale)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}
