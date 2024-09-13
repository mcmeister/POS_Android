@file:Suppress("DEPRECATION")

package com.example.pos

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.services.drive.Drive
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

    companion object {
        const val REQUEST_AUTHORIZATION = 1001
    }

    // Use ActivityResultLauncher to handle Google Sign-In
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d("SalesFragment", "Sign-In resultCode: ${result.resultCode}")
        Log.d("SalesFragment", "Sign-In intent data: ${result.data?.extras}")

        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            lifecycleScope.launch {
                googleDrive.handleSignInResult(task, {
                    // Sign-in success, proceed with export
                    Log.d("SalesFragment", "Sign-in successful, proceeding with export")
                    exportSalesToGoogleDrive()
                }, {
                    // Sign-in failed, show error
                    Log.e("SalesFragment", "Sign-in failed in handleSignInResult")
                    showToast("Failed to sign in to Google Drive")
                })
            }
        } else {
            // Log the failure or cancellation reason
            Log.e("SalesFragment", "Sign-In canceled or failed, resultCode: ${result.resultCode}")

            // Extract any available extra data
            val extraData = result.data?.extras?.keySet()?.joinToString { key ->
                "$key: ${result.data?.extras?.get(key)}"
            } ?: "No additional data"

            // Log and show the reason in the toast
            Log.e("SalesFragment", "Intent data: $extraData")
            showToast("Google Sign-In canceled. Result code: ${result.resultCode}, Data: $extraData")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sales, container, false)

        googleDrive = GoogleDrive(this)
        googleDrive.initializeGoogleSignIn()

        // Initialize views
        recyclerView = view.findViewById(R.id.recycler_view_sales)
        val buttonStartDate: Button = view.findViewById(R.id.button_start_date)
        val buttonEndDate: Button = view.findViewById(R.id.button_end_date)
        val buttonSubmitExpense: Button = view.findViewById(R.id.button_submit_expense)
        val editTextExpense: EditText = view.findViewById(R.id.edit_text_expense)
        val buttonExport: Button = view.findViewById(R.id.button_export)

        // TextViews for displaying the sum of Expenses and Profits
        val textViewExpenseSum: TextView = view.findViewById(R.id.text_view_expense_sum)
        val textViewProfitSum: TextView = view.findViewById(R.id.text_view_profit_sum)

        // Initialize the database
        database = AppDatabase.getDatabase(requireContext())

        // Set up the RecyclerView with the adapter
        adapter = SalesAdapter(sales, items)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Listen to changes in the expense input field
        editTextExpense.addTextChangedListener {
            buttonSubmitExpense.isEnabled = it.toString().isNotEmpty()
        }

        // Handle submit button click
        buttonSubmitExpense.setOnClickListener {
            val expenseAmount = editTextExpense.text.toString().toDoubleOrNull()
            if (expenseAmount != null) {
                saveExpense(expenseAmount)
                editTextExpense.text.clear()
                buttonSubmitExpense.isEnabled = false
            } else {
                showToast("Invalid expense amount")
            }
        }

        // Date picker buttons
        buttonStartDate.setOnClickListener {
            showDatePicker { date ->
                startDate = date
                filterSales(textViewExpenseSum, textViewProfitSum)
            }
        }

        buttonEndDate.setOnClickListener {
            showDatePicker { date ->
                endDate = date
                filterSales(textViewExpenseSum, textViewProfitSum)
            }
        }

        buttonExport.setOnClickListener {
            Log.d("SalesFragment", "Export button clicked, checking sign-in status...")
            lifecycleScope.launch {
                googleDrive.signInToGoogle(signInLauncher) {
                Log.d("SalesFragment", "Sign-in successful, proceeding with export")
                exportSalesToGoogleDrive()
            }
        }
            }

        filterSales(textViewExpenseSum, textViewProfitSum)
        fetchItems()

        return view
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUTHORIZATION && resultCode == Activity.RESULT_OK) {
            // The user has granted the required permissions, retry the spreadsheet creation
            lifecycleScope.launch {
                exportSalesToGoogleDrive()
            }
        } else {
            showToast("Google authorization failed. Cannot proceed with export.")
        }
    }

    // Function to save the expense in the database with the current timestamp
    private fun saveExpense(amount: Double) {
        lifecycleScope.launch {
            val timestamp = System.currentTimeMillis()
            val expense = Expense(amount = amount, timestamp = timestamp)

            withContext(Dispatchers.IO) {
                database.expenseDao().insertExpense(expense)
            }

            showToast("Expense saved: $amount")

            // Refresh UI after expense is saved
            filterSales(view?.findViewById(R.id.text_view_expense_sum)!!, view?.findViewById(R.id.text_view_profit_sum)!!)
        }
    }

    // Export sales to Google Drive
    private fun exportSalesToGoogleDrive() {
        lifecycleScope.launch {
            Log.d("SalesFragment", "Attempting to get Google Drive service")
            val driveService = googleDrive.getDriveService()
            val sheetsService = googleDrive.getSheetsService()
            if (driveService == null || sheetsService == null) {
                Log.e("SalesFragment", "Google services are null. Aborting export.")
                showToast("Failed to connect to Google services")
                return@launch
            }

            Log.d("SalesFragment", "Google Drive and Sheets services successfully retrieved")

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
                database.saleDao().getSalesReport(startDate, endDate)
            }
            Log.d("SalesFragment", "Sales data retrieved successfully")

            // Step 4: Retrieve expense data from the database
            val expensesData = withContext(Dispatchers.IO) {
                database.expenseDao().getAllExpenses()
            }
            Log.d("SalesFragment", "Expenses data retrieved successfully")

            // Step 5: Convert sales data to CSV format
            val csvData = salesDataToCSV(salesData)

            // Step 6: Format the startDate and endDate as 'yyyy-MM-dd'
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDateFormatted = dateFormat.format(startDate)
            val endDateFormatted = dateFormat.format(endDate)

            // Step 7: Create the report file name using the formatted dates
            val fileName = "$startDateFormatted-$endDateFormatted.csv"

            // Step 8: Save the CSV report to Google Drive
            googleDrive.saveReportToDrive(driveService, fileName, reportsFolderId, csvData.toByteArray())

            // Step 9: Create and populate a spreadsheet from the CSV and expenses
            val spreadsheetId = googleDrive.createSpreadsheet(
                driveService, sheetsService, reportsFolderId, fileName, csvData, expensesData, salesData
            )
            if (spreadsheetId == null) {
                Log.e("SalesFragment", "Failed to create Google Spreadsheet")
                showToast("Failed to create Spreadsheet")
                return@launch
            }

            Log.d("SalesFragment", "Spreadsheet created with ID: $spreadsheetId")

            // Step 10: Delete the original CSV file from the Reports folder
            deleteCsvFile(driveService, reportsFolderId, fileName)

            Log.d("SalesFragment", "Export to Google Drive and spreadsheet creation completed successfully")
            showToast("Export to Google Drive and Spreadsheet completed successfully")
        }
    }

    // Function to delete the CSV file after it's used to populate the spreadsheet
    private suspend fun deleteCsvFile(driveService: Drive, folderId: String, fileName: String) {
        return withContext(Dispatchers.IO) {
            try {
                val query = "mimeType='text/csv' and name='$fileName' and '$folderId' in parents"
                val fileList = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .execute()

                if (fileList.files.isNotEmpty()) {
                    val fileId = fileList.files[0].id
                    driveService.files().delete(fileId).execute()
                    Log.d("SalesFragment", "CSV file $fileName deleted successfully from Google Drive")
                } else {
                    Log.e("SalesFragment", "CSV file $fileName not found in folder $folderId")
                }
            } catch (e: Exception) {
                Log.e("SalesFragment", "Error deleting CSV file: $fileName", e)
            }
        }
    }

    // Convert sales data to CSV format with formatted timestamp
    private fun salesDataToCSV(sales: List<SalesReport>): String {
        val header = "Item Name, Quantity, Sale Price, Sales Channel, Timestamp\n"
        val data = sales.joinToString("\n") { sale ->
            "${sale.itemName},${sale.quantity},${sale.salePrice},${sale.salesChannel}," +
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(sale.timestamp)
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
    private fun filterSales(expenseTextView: TextView, salesTextView: TextView) {
        lifecycleScope.launch {
            // Adjust the startDate and endDate to cover the entire day
            val calendarStart = Calendar.getInstance()
            calendarStart.timeInMillis = startDate
            calendarStart.set(Calendar.HOUR_OF_DAY, 0)
            calendarStart.set(Calendar.MINUTE, 0)
            calendarStart.set(Calendar.SECOND, 0)
            calendarStart.set(Calendar.MILLISECOND, 0)
            val adjustedStartDate = calendarStart.timeInMillis

            val calendarEnd = Calendar.getInstance()
            calendarEnd.timeInMillis = endDate
            calendarEnd.set(Calendar.HOUR_OF_DAY, 23)
            calendarEnd.set(Calendar.MINUTE, 59)
            calendarEnd.set(Calendar.SECOND, 59)
            calendarEnd.set(Calendar.MILLISECOND, 999)
            val adjustedEndDate = calendarEnd.timeInMillis

            // Fetch sales for the selected date range
            val salesFromDb = withContext(Dispatchers.IO) {
                database.saleDao().getSalesBetween(adjustedStartDate, adjustedEndDate)
            }

            // Fetch total expenses for the selected date range
            val totalExpenses = withContext(Dispatchers.IO) {
                database.expenseDao().getTotalExpenseBetween(adjustedStartDate, adjustedEndDate)
            }

            // Fetch total profit for the selected date range
            val totalSales = withContext(Dispatchers.IO) {
                database.saleDao().getTotalSalesBetween(adjustedStartDate, adjustedEndDate)
            }

            // Update the RecyclerView
            sales.clear()
            sales.addAll(salesFromDb)
            adapter.notifyDataSetChanged()

            // Update TextViews with formatted values
            expenseTextView.text = getString(R.string.total_expense, totalExpenses)
            salesTextView.text = getString(R.string.total_sales, totalSales)
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
