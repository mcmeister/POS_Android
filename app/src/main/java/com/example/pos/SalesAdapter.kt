package com.example.pos

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SalesAdapter(
    private val context: Context,
    var orders: Map<Int, List<Sale>>,
    private val salesChannels: List<SalesChannel>,
    private val onCancelSaleClick: (Sale) -> Unit,
    private val onCancelOrderClick: (Int) -> Unit
) : RecyclerView.Adapter<SalesAdapter.OrderViewHolder>() {

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewOrderId: TextView = view.findViewById(R.id.text_view_sale_id)
        val textViewSalesChannel: TextView = view.findViewById(R.id.text_view_sales_channel) // ✅ Ensure this exists
        val textViewTotal: TextView = view.findViewById(R.id.text_view_total)
        val recyclerViewItems: RecyclerView = view.findViewById(R.id.recycler_view_items)
        val buttonCancelOrder: Button = view.findViewById(R.id.button_cancel_order)
    }

    fun updateOrders(newOrders: Map<Int, List<Sale>>) {
        val oldOrders = orders
        orders = newOrders

        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldOrders.size
            override fun getNewListSize(): Int = newOrders.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldOrderId = oldOrders.keys.toList()[oldItemPosition]
                val newOrderId = newOrders.keys.toList()[newItemPosition]
                return oldOrderId == newOrderId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldOrder = oldOrders.values.toList()[oldItemPosition]
                val newOrder = newOrders.values.toList()[newItemPosition]
                return oldOrder == newOrder
            }
        })

        diffResult.dispatchUpdatesTo(this) // More efficient updates
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.order_item, parent, false)
        return OrderViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val orderId = orders.keys.toList()[position] // ✅ Orders are already sorted in DESC order
        val orderItems = orders[orderId] ?: return

        holder.textViewOrderId.text = "Order #$orderId"

        val salesChannel = orderItems.first().salesChannel
        val salesChannelData = salesChannels.find { it.name == salesChannel }
        val discount = salesChannelData?.discount ?: 0

        holder.textViewSalesChannel.text = "Channel: $salesChannel (Discount: $discount%)"

        val orderTotal = orderItems.sumOf { sale ->
            calculateTotal(sale.salePrice, sale.quantity, discount)
        }
        holder.textViewTotal.text = "Total: $orderTotal"

        holder.recyclerViewItems.layoutManager = LinearLayoutManager(holder.itemView.context)
        holder.recyclerViewItems.adapter = OrderItemAdapter(orderItems, salesChannels, onCancelSaleClick)

        holder.buttonCancelOrder.setOnClickListener {
            showCancelOrderDialog(orderId)
        }
    }

    override fun getItemCount(): Int = orders.size

    private fun showCancelOrderDialog(orderId: Int) {
        AlertDialog.Builder(context)
            .setTitle("Cancel Order")
            .setMessage("Are you sure you want to cancel this entire order?")
            .setPositiveButton("Yes") { dialog, _ ->
                onCancelOrderClick(orderId)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun calculateTotal(salePrice: Double, quantity: Int, discount: Int): Double {
        return salePrice * quantity * (1 - discount / 100.0)
    }
}
