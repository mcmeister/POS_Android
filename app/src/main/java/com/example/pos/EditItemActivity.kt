package com.example.pos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditItemActivity : AppCompatActivity() {

    private lateinit var editTextItemName: EditText
    private lateinit var editTextRawPrice: EditText
    private lateinit var editTextSalePrice: EditText
    private lateinit var editTextQuantity: EditText
    private lateinit var salesChannelDropdown: Spinner
    private lateinit var imageViewItemPhoto: ImageView
    private lateinit var buttonAddPhoto: Button
    private lateinit var database: AppDatabase
    private var itemId: Int? = null
    private var photoUri: String? = null
    private lateinit var existingSalesChannels: MutableList<String> // Changed to MutableList for easier updates

    // Photo picker for Android 14+
    private val selectPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            photoUri = uri.toString()
            Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.ic_placeholder)
                .into(imageViewItemPhoto)
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_item)

        editTextItemName = findViewById(R.id.edit_text_item_name)
        editTextRawPrice = findViewById(R.id.edit_text_raw_price)
        editTextSalePrice = findViewById(R.id.edit_text_sale_price)
        editTextQuantity = findViewById(R.id.edit_text_quantity)
        salesChannelDropdown = findViewById(R.id.sales_channel_dropdown)
        imageViewItemPhoto = findViewById(R.id.image_item_photo)
        buttonAddPhoto = findViewById(R.id.button_add_photo)

        val buttonSave: Button = findViewById(R.id.button_save)
        val buttonProceed: Button = findViewById(R.id.button_proceed)

        // Initialize the database
        database = AppDatabase.getDatabase(this)

        itemId = intent.getIntExtra("item_id", -1).takeIf { it != -1 }

        itemId?.let {
            loadItem(it)
        }

        // Load the sales channels into the Spinner
        loadSalesChannels()

        buttonAddPhoto.setOnClickListener {
            showPhotoOptionsDialog()
        }

        buttonSave.setOnClickListener {
            saveItem()
        }

        buttonProceed.setOnClickListener {
            val salePrice = editTextSalePrice.text.toString().toIntOrNull() ?: 0
            val quantity = editTextQuantity.text.toString().toIntOrNull() ?: 1
            val salesChannel = salesChannelDropdown.selectedItem?.toString()?.trim() ?: ""

            if (salesChannel.isEmpty() || salesChannel == "Add new...") {
                Toast.makeText(this, "Please select or add a sales channel.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            insertSale(salePrice, quantity, salesChannel)
        }
    }

    private fun loadSalesChannels() {
        lifecycleScope.launch {
            existingSalesChannels = withContext(Dispatchers.IO) {
                val channels = database.saleDao().getActiveSalesChannels().toMutableList()
                channels.add("Add new...") // Add the option to enter a new sales channel
                channels.add("Edit...")    // Add the option to edit existing sales channels
                channels
            }

            val adapter = ArrayAdapter(
                this@EditItemActivity,
                android.R.layout.simple_spinner_item,
                existingSalesChannels
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            salesChannelDropdown.adapter = adapter

            val salesChannelManager = SalesChannelManager(this@EditItemActivity, lifecycleScope, database, layoutInflater)

            // Handle user selecting "Add new" or "Edit..." option
            salesChannelDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    when (existingSalesChannels[position]) {
                        "Add new..." -> showNewSalesChannelDialog()
                        "Edit..." -> salesChannelManager.showEditSalesChannelsDialog {
                            loadSalesChannels() // Callback when the dialog is dismissed
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    private fun showNewSalesChannelDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Add New Sales Channel")

        // Create a LinearLayout to hold both input fields
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)  // Add padding for better appearance
        }

        // Create EditText for Sales Channel Name
        val channelInput = EditText(this).apply {
            hint = "Sales Channel Name"
        }

        // Create EditText for Discount with default value of 0
        val discountInput = EditText(this).apply {
            hint = "Discount (%)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0")  // Set default value to 0
        }

        // Add EditTexts to the layout
        layout.addView(channelInput)
        layout.addView(discountInput)

        // Set the layout in the dialog
        builder.setView(layout)

        // Handle the positive button click
        builder.setPositiveButton("OK") { dialog, _ ->
            val newChannel = channelInput.text.toString().trim()
            val discount = discountInput.text.toString().trim()

            if (newChannel.isNotEmpty() && discount.isNotEmpty()) {
                saveNewSalesChannel(newChannel, discount.toInt())
            }

            dialog.dismiss()
        }

        // Handle the negative button click
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        // Show the dialog
        builder.show()
    }

    private fun saveNewSalesChannel(channel: String, discount: Int) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                database.saleDao().insertSalesChannel(SalesChannel(name = channel,  discount = discount))
            }
            // Reload the sales channels and include the newly added channel
            loadSalesChannels()
        }
    }

    private fun showPhotoOptionsDialog() {
        val options = arrayOf("Select from Gallery")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Add Photo")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> {
                    val pickVisualMediaRequest = PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .build()
                    selectPhotoLauncher.launch(pickVisualMediaRequest)
                }
            }
        }
        builder.show()
    }

    private fun loadItem(itemId: Int) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) {
                database.itemDao().getItemById(itemId)
            }
            item?.let {
                editTextItemName.setText(it.name)
                editTextRawPrice.setText(it.rawPrice.toString())
                editTextSalePrice.setText(it.salePrice.toString())
                photoUri = it.photoUri
                Glide.with(this@EditItemActivity).load(photoUri ?: R.drawable.ic_placeholder).into(imageViewItemPhoto)
            }
        }
    }

    private fun saveItem() {
        val itemName = editTextItemName.text.toString().trim()
        val rawPrice = editTextRawPrice.text.toString().toDoubleOrNull() ?: 0.0
        val salePrice = editTextSalePrice.text.toString().toDoubleOrNull() ?: 0.0

        if (itemName.isEmpty()) {
            android.util.Log.d("EditItemActivity", "Item name is empty. Save operation aborted.")
            return
        }

        lifecycleScope.launch {
            val savedItem: Item
            withContext(Dispatchers.IO) {
                if (itemId == null) {
                    val newItem = Item(name = itemName, rawPrice = rawPrice.toInt(), salePrice = salePrice.toInt(), photoUri = photoUri)
                    itemId = database.itemDao().insertItem(newItem).toInt()
                    savedItem = newItem.copy(id = itemId!!)
                    android.util.Log.d("EditItemActivity", "New item saved with ID: ${savedItem.id}")
                } else {
                    val updatedItem = Item(id = itemId!!, name = itemName, rawPrice = rawPrice.toInt(), salePrice = salePrice.toInt(), photoUri = photoUri)
                    database.itemDao().updateItem(updatedItem)
                    savedItem = updatedItem
                    android.util.Log.d("EditItemActivity", "Item updated with ID: ${savedItem.id}")
                }
            }

            val intent = Intent()
            intent.putExtra("new_item", savedItem)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun insertSale(salePrice: Int, quantity: Int, salesChannel: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Check if itemId is null and insert a new item if necessary
                if (itemId == null) {
                    val rawPrice = (editTextRawPrice.text.toString().toIntOrNull() ?: 0)
                    val newItem = Item(
                        name = editTextItemName.text.toString(),
                        rawPrice = rawPrice,
                        salePrice = salePrice,
                        photoUri = photoUri
                    )
                    itemId = database.itemDao().insertItem(newItem).toInt()
                }

                // Get the Item Name to insert into the Sale table
                val itemName = database.itemDao().getItemNameById(itemId!!)

                val lastSaleId = database.saleDao().getLastSaleId() ?: 0
                val newSaleId = lastSaleId + 1

                // Correct profit calculation: (Sale Price - Raw Price) * Quantity
                val rawPrice = (editTextRawPrice.text.toString().toIntOrNull() ?: 0)
                val profit = quantity * (salePrice - rawPrice)

                // Log for debugging
                android.util.Log.d("EditItemActivity", "Raw Price: $rawPrice, Sale Price: $salePrice, Quantity: $quantity, Profit: $profit")

                // Create and insert the Sale object with itemName
                val sale = Sale(
                    id = newSaleId,
                    itemId = itemId!!,
                    itemName = itemName ?: "",  // Ensure itemName is not null
                    quantity = quantity,
                    salePrice = salePrice,
                    salesChannel = salesChannel,
                    rawPrice = rawPrice,
                    profit = profit,
                    timestamp = System.currentTimeMillis()
                )

                database.saleDao().insertSale(sale)
            }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}
