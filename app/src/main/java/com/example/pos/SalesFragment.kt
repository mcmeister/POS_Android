@file:Suppress("DEPRECATION")

package com.example.pos

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

class SalesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SalesAdapter
    private lateinit var database: AppDatabase
    private val sales = mutableListOf<Sale>()
    private val items = mutableListOf<Item>()
    private var startDate: Long = 0
    private var endDate: Long = System.currentTimeMillis()

    private lateinit var googleDrive: GoogleDrive

    // Use ActivityResultLauncher to handle Google Sign-In
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            googleDrive.handleSignInResult(task, {
                exportSalesToGoogleDrive()
            }, {
                showToast("Failed to sign in to Google Drive")
            })
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sales, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_sales)
        val buttonStartDate: Button = view.findViewById(R.id.button_start_date)
        val buttonEndDate: Button = view.findViewById(R.id.button_end_date)
        val buttonExport: Button = view.findViewById(R.id.button_export)

        // Initialize the database
        database = AppDatabase.getDatabase(requireContext())

        // Set up the RecyclerView with the adapter (initialize with both sales and items)
        adapter = SalesAdapter(sales, items)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Initialize GoogleDrive instance
        googleDrive = GoogleDrive(this)
        googleDrive.initializeGoogleSignIn()

        // Handle start date selection
        buttonStartDate.setOnClickListener {
            showDatePicker { date ->
                startDate = date
                filterSales()
            }
        }

        // Handle end date selection
        buttonEndDate.setOnClickListener {
            showDatePicker { date ->
                endDate = date
                filterSales()
            }
        }

        // Handle export button click
        buttonExport.setOnClickListener {
            if (googleDrive.googleAccount == null) {
                googleDrive.signInToGoogle(signInLauncher)
            } else {
                exportSalesToGoogleDrive()
            }
        }

        // Fetch initial sales and items
        filterSales()
        fetchItems()

        return view
    }

    // Export sales to Google Drive
    private fun exportSalesToGoogleDrive() {
        lifecycleScope.launch {
            Log.d("SalesFragment", "Attempting to get Google Drive service")
            val driveService = googleDrive.getDriveService()
            if (driveService == null) {
                Log.e("SalesFragment", "Google Drive service is null. Aborting export.")
                showToast("Failed to connect to Google Drive")
                return@launch
            }

            Log.d("SalesFragment", "Google Drive service successfully retrieved")

            // Log export start
            Log.d("SalesFragment", "Starting export to Google Drive")
            showToast("Starting export to Google Drive")

            // Step 1: Check or create "Oh My Grill" folder
            val ohMyGrillFolderId = googleDrive.checkOrCreateFolder(driveService, "Oh My Grill")
            if (ohMyGrillFolderId == null) {
                Log.e("SalesFragment", "Failed to create 'Oh My Grill' folder.")
                showToast("Failed to create 'Oh My Grill' folder")
                return@launch
            }

            Log.d("SalesFragment", "'Oh My Grill' folder created or found with ID: $ohMyGrillFolderId")

            // Step 2: Check or create "Reports" folder inside "Oh My Grill"
            val reportsFolderId = googleDrive.checkOrCreateFolder(driveService, "Reports", ohMyGrillFolderId)
            if (reportsFolderId == null) {
                Log.e("SalesFragment", "Failed to create 'Reports' folder.")
                showToast("Failed to create 'Reports' folder")
                return@launch
            }

            Log.d("SalesFragment", "'Reports' folder created or found with ID: $reportsFolderId")

            // Step 3: Retrieve sales data from the database for the selected date range
            val salesData = withContext(Dispatchers.IO) {
                database.saleDao().getSalesBetween(startDate, endDate)
            }
            Log.d("SalesFragment", "Sales data retrieved successfully")

            // Step 4: Convert sales data to CSV format
            val csvData = salesDataToCSV(salesData)

            // Step 5: Format the startDate and endDate as 'yyyy-MM-dd'
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDateFormatted = dateFormat.format(startDate)
            val endDateFormatted = dateFormat.format(endDate)

            // Step 6: Create the report file name using the formatted dates
            val fileName = "$startDateFormatted-$endDateFormatted.csv"

            // Step 7: Save the report to Google Drive
            googleDrive.saveReportToDrive(driveService, fileName, reportsFolderId, csvData.toByteArray())
            Log.d("SalesFragment", "Export to Google Drive completed successfully")
            showToast("Export to Google Drive completed successfully")
        }
    }

    // Convert sales data to CSV format
    private fun salesDataToCSV(sales: List<Sale>): String {
        val header = "Sale ID, Item Name, Quantity, Raw Price, Sale Price, Profit, Sales Channel, Timestamp\n"
        val data = sales.joinToString("\n") { sale ->
            "${sale.id},${sale.itemName},${sale.quantity},${sale.rawPrice},${sale.salePrice},${sale.profit},${sale.salesChannel},${sale.timestamp}"
        }
        return header + data
    }

    // Show the date picker dialog to select a date
    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val calendar = Calendar.getInstance()
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.timeInMillis)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    // Fetch sales from the database based on the date range and update the RecyclerView
    @SuppressLint("NotifyDataSetChanged")
    private fun filterSales() {
        lifecycleScope.launch {
            // Set the end date to 23:59:59 of the selected day
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = endDate
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val adjustedEndDate = calendar.timeInMillis

            val salesFromDb = withContext(Dispatchers.IO) {
                database.saleDao().getSalesBetween(startDate, adjustedEndDate)
            }
            sales.clear()
            sales.addAll(salesFromDb)
            adapter.notifyDataSetChanged()
        }
    }

    // Fetch items from the database and update the RecyclerView
    @SuppressLint("NotifyDataSetChanged")
    private fun fetchItems() {
        lifecycleScope.launch {
            val itemsFromDb = withContext(Dispatchers.IO) {
                database.itemDao().getAllItems()
            }
            items.clear()
            items.addAll(itemsFromDb)
            adapter.notifyDataSetChanged()
        }
    }

    // Show toast messages
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
