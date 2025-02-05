package com.example.pos

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SalesChannelManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val database: AppDatabase,
    private val layoutInflater: LayoutInflater
) {
    // Structure to hold original sales channels
    private data class OriginalSalesChannel(
        val id: Int,
        val name: String,
        val discount: Int
    )

    @SuppressLint("InflateParams")
    fun showEditSalesChannelsDialog(onDialogDismiss: () -> Unit) {
        val builder = android.app.AlertDialog.Builder(context)

        // Inflate the layout (without the unnecessary headers)
        val view = layoutInflater.inflate(R.layout.dialog_edit_sales_channels, null)
        val salesChannelsList = view.findViewById<LinearLayout>(R.id.sales_channels_list)

        // List to hold original sales channels for restoration if canceled
        val originalSalesChannels = mutableListOf<OriginalSalesChannel>()

        // List to track deleted channels (IDs)
        val deletedChannels = mutableListOf<Int>()

        // Fetch sales channels from the database
        lifecycleScope.launch {
            val salesChannels = withContext(Dispatchers.IO) {
                database.saleDao().getAllSalesChannelsWithDiscounts()
            }

            // Filter only valid channels with data (e.g., ID > 0 and name is not empty)
            salesChannels
                .filter { it.id > 0 && it.name.isNotEmpty() }
                .forEach { channel ->
                    // Save original state for restoration if needed
                    originalSalesChannels.add(OriginalSalesChannel(channel.id, channel.name, channel.discount))

                    // Inflate row for each valid channel
                    val rowView = layoutInflater.inflate(R.layout.row_sales_channel, null) as LinearLayout

                    rowView.tag = channel.id

                    // Populate the views (EditText for channel name and discount)
                    val editTextName = rowView.findViewById<EditText>(R.id.edit_channel_name)
                    val editTextDiscount = rowView.findViewById<EditText>(R.id.edit_discount)
                    val deleteButton = rowView.findViewById<ImageButton>(R.id.button_delete)

                    // Set data in the fields
                    editTextName.setText(channel.name)
                    editTextDiscount.setText(channel.discount.toString())

                    // Handle delete button functionality
                    deleteButton.setOnClickListener {
                        val deleteConfirmDialog = android.app.AlertDialog.Builder(context)
                        deleteConfirmDialog.setMessage("Are you sure you want to delete this channel?")
                        deleteConfirmDialog.setPositiveButton("Yes") { _, _ ->
                            deletedChannels.add(channel.id)
                            salesChannelsList.removeView(rowView) // Remove the row from UI
                        }
                        deleteConfirmDialog.setNegativeButton("No", null)
                        deleteConfirmDialog.show()
                    }

                    // Add the inflated row to the list
                    salesChannelsList.addView(rowView)
                }
        }

        builder.setView(view)
        builder.setPositiveButton("Save", null)
        builder.setNegativeButton("Cancel", null)

        val dialog = builder.create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)

            positiveButton.setOnClickListener {
                lifecycleScope.launch {
                    // List to hold channels to be updated
                    val channelsToUpdate = mutableListOf<OriginalSalesChannel>()

                    // Collect all the channel names and discounts that need updating
                    salesChannelsList.children.forEach { rowView ->
                        val linearLayout = rowView as LinearLayout
                        val editTextName = linearLayout.findViewById<EditText>(R.id.edit_channel_name)
                        val editTextDiscount = linearLayout.findViewById<EditText>(R.id.edit_discount)

                        val channelName = editTextName.text.toString().trim()
                        val discount = editTextDiscount.text.toString().toIntOrNull() ?: 0
                        val channelId = rowView.tag as? Int ?: return@forEach

                        // Add to the list of channels to be updated
                        channelsToUpdate.add(OriginalSalesChannel(channelId, channelName, discount))
                    }

                    // Update each sales channel in the database
                    withContext(Dispatchers.IO) {
                        channelsToUpdate.forEach { channel ->
                            database.saleDao().updateSalesChannel(channel.name, channel.discount, channel.id)
                        }
                    }

                    // Mark deleted channels in the database
                    withContext(Dispatchers.IO) {
                        deletedChannels.forEach { channelId ->
                            database.saleDao().markChannelAsDeleted(channelId)
                        }
                    }

                    // Dismiss the dialog after all updates and deletions are completed
                    dialog.dismiss()
                }
            }

            negativeButton.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener {
            onDialogDismiss() // Refresh sales channels after the dialog is dismissed
        }

        dialog.show()
    }
}
