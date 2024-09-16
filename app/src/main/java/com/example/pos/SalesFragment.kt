@file:Suppress("DEPRECATION")

package com.example.pos

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.media.MediaScannerConnection
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
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.*

class SalesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SalesAdapter
    private lateinit var database: AppDatabase
    private val sales = mutableListOf<Sale>()
    private val items = mutableListOf<Item>()
    private val salesChannels = mutableListOf<SalesChannel>()
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
                    Log.d("SalesFragment", "Sign-in successful, proceeding with export")
                    exportSalesToGoogleDrive()
                }, {
                    Log.e("SalesFragment", "Sign-in failed in handleSignInResult, saving report locally")
                    showToast("Failed to sign in to Google Drive. Saving report locally.")
                    retrieveAndSaveLocally()
                })
            }
        } else {
            Log.e("SalesFragment", "Sign-In canceled or failed, resultCode: ${result.resultCode}")
            showToast("Google Sign-In canceled. Saving report locally.")
            retrieveAndSaveLocally()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sales, container, false)

        // Initialize Google Drive functionality
        googleDrive = GoogleDrive(this)
        googleDrive.initializeGoogleSignIn()

        // Initialize views
        recyclerView = view.findViewById(R.id.recycler_view_sales)

        // Buttons and text fields
        val buttonStartDate: Button = view.findViewById(R.id.button_start_date)
        val buttonEndDate: Button = view.findViewById(R.id.button_end_date)
        val buttonSubmitExpense: Button = view.findViewById(R.id.button_submit_expense)
        val editTextExpense: EditText = view.findViewById(R.id.edit_text_expense)
        val buttonExport: Button = view.findViewById(R.id.button_export)

        // TextViews for displaying the sum of Expenses and Profits
        val textViewExpenseSum: TextView = view.findViewById(R.id.text_view_expense_sum)
        val textViewProfitSum: TextView = view.findViewById(R.id.text_view_profit_sum)
        val textViewSalesSum: TextView = view.findViewById(R.id.text_view_sales_sum)

        // Initialize the database
        database = AppDatabase.getDatabase(requireContext())

        // Disable the submit button initially until valid input is detected
        buttonSubmitExpense.isEnabled = false

        // Fetch sales channels before setting up the adapter
        lifecycleScope.launch {
            fetchSalesChannels()
            adapter = SalesAdapter(requireContext(), sales, items, salesChannels) { sale ->
                cancelSale(sale)
            }
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter

            fetchItems()
            filterSales(textViewExpenseSum, textViewProfitSum, textViewSalesSum)
        }

        editTextExpense.addTextChangedListener {
            buttonSubmitExpense.isEnabled = it.toString().isNotEmpty()
        }

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

        buttonStartDate.setOnClickListener {
            showDatePicker { date ->
                startDate = date
                filterSales(textViewExpenseSum, textViewProfitSum, textViewSalesSum)
            }
        }

        buttonEndDate.setOnClickListener {
            showDatePicker { date ->
                endDate = date
                filterSales(textViewExpenseSum, textViewProfitSum, textViewSalesSum)
            }
        }

        buttonExport.setOnClickListener {
            Log.d("SalesFragment", "Export button clicked, checking sign-in status...")
            lifecycleScope.launch {
                googleDrive.signInToGoogle(signInLauncher) {
                    exportSalesToGoogleDrive()
                }
            }
        }

        return view
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUTHORIZATION && resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                exportSalesToGoogleDrive()
            }
        } else {
            showToast("Google authorization failed. Saving report locally.")
            retrieveAndSaveLocally()
        }
    }

    private fun saveExpense(amount: Double) {
        lifecycleScope.launch {
            val timestamp = System.currentTimeMillis()
            val expense = Expense(amount = amount, timestamp = timestamp)

            withContext(Dispatchers.IO) {
                database.expenseDao().insertExpense(expense)
            }

            showToast("Expense saved: $amount")
            filterSales(view?.findViewById(R.id.text_view_expense_sum)!!,
                view?.findViewById(R.id.text_view_profit_sum)!!,
                view?.findViewById(R.id.text_view_sales_sum)!!)
        }
    }

    private fun cancelSale(sale: Sale) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                database.saleDao().markSaleAsCancelled(sale.id)
            }
            filterSales(view?.findViewById(R.id.text_view_expense_sum)!!,
                view?.findViewById(R.id.text_view_profit_sum)!!,
                view?.findViewById(R.id.text_view_sales_sum)!!)
        }
    }

    private suspend fun fetchSalesChannels() {
        val channelsFromDb = withContext(Dispatchers.IO) {
            database.saleDao().getAllSalesChannels()
        }
        salesChannels.clear()
        salesChannels.addAll(channelsFromDb)
    }

    private fun retrieveAndSaveLocally() {
        lifecycleScope.launch {
            val salesData = withContext(Dispatchers.IO) {
                database.saleDao().getSalesReport(startDate, endDate)
            }
            val expensesData = withContext(Dispatchers.IO) {
                database.expenseDao().getAllExpenses()
            }
            saveReportLocally(salesData, expensesData, salesChannels)
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

            // Step 5: Convert sales data to CSV format, including deleted and discount columns
            val csvData = salesDataToCSV(salesData, salesChannels)

            // Step 6: Format the startDate and endDate as 'yyyy-MM-dd'
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDateFormatted = dateFormat.format(startDate)
            val endDateFormatted = dateFormat.format(endDate)

            // Step 7: Create the report file name using the formatted dates
            val fileName = "$startDateFormatted-$endDateFormatted.csv"

            // Step 8: Save the CSV report to Google Drive
            googleDrive.saveReportToDrive(driveService, fileName, reportsFolderId, csvData.toByteArray())

            // Step 9: Create and populate a spreadsheet from the CSV, including expenses, deleted, and discount
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

    private suspend fun deleteCsvFile(driveService: Drive, folderId: String, fileName: String) {
        withContext(Dispatchers.IO) {
            val query = "mimeType='text/csv' and name='$fileName' and '$folderId' in parents"
            val fileList = driveService.files().list().setQ(query).setSpaces("drive").execute()
            if (fileList.files.isNotEmpty()) {
                val fileId = fileList.files[0].id
                driveService.files().delete(fileId).execute()
            }
        }
    }

    private fun salesDataToCSV(sales: List<SalesReport>, salesChannels: List<SalesChannel>): String {
        val header = "Item Name, Quantity, Sale Price, Sales Channel, Discount, Deleted, Total, Timestamp\n"
        return sales
            .filter { sale -> sale.cancelled != 1 } // Exclude cancelled sales
            .joinToString("\n") { sale ->
                // Find the latest SalesChannel by name (if multiple exist with the same name, pick the one with the highest ID)
                val salesChannel = salesChannels
                    .filter { it.name == sale.salesChannel }  // Get all channels with the same name
                    .maxByOrNull { it.id }  // Select the one with the latest (highest) id

                // Use discount and deleted values from the latest SalesChannel entry
                val discount = salesChannel?.discount ?: 0
                val total = calculateTotal(sale.salePrice, sale.quantity, discount)
                val roundedTotal = kotlin.math.round(total)

                // Return the CSV line
                "${sale.itemName},${sale.quantity},${sale.salePrice},${sale.salesChannel},$discount,$roundedTotal," +
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(sale.timestamp)
            }.let { header + it }
    }

    private fun calculateTotal(salePrice: Double, quantity: Int, discount: Int): Double {
        return salePrice * quantity * (1 - discount / 100.0)
    }

    private fun saveReportLocally(salesData: List<SalesReport>, expensesData: List<Expense>, salesChannels: List<SalesChannel>) {
        lifecycleScope.launch {
            try {
                Log.d("SalesFragment", "Starting to save report locally.")

                // Formatting the date for the file name
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val startDateFormatted = dateFormat.format(startDate)
                val endDateFormatted = dateFormat.format(endDate)
                Log.d("SalesFragment", "Formatted start date: $startDateFormatted, end date: $endDateFormatted")

                // Create the report file name using the formatted dates
                val fileName = "$startDateFormatted-$endDateFormatted.xls"
                val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    Log.d("SalesFragment", "Using Downloads directory for Android 10+: $downloadsDir")
                    File(downloadsDir, fileName)
                } else {
                    val externalStorage = Environment.getExternalStorageDirectory()
                    Log.d("SalesFragment", "Using external storage for Android 9 and below: $externalStorage")
                    File(externalStorage, fileName)
                }

                Log.d("SalesFragment", "Saving report to: ${file.absolutePath}")

                // Create workbook and sheet for the Excel file
                val workbook = HSSFWorkbook()
                val sheet = workbook.createSheet("Sales Report")

                // Create headers for sales data
                Log.d("SalesFragment", "Creating headers in the Excel sheet.")
                val headerRow = sheet.createRow(0)
                headerRow.createCell(0).setCellValue("Item Name")
                headerRow.createCell(1).setCellValue("Quantity")
                headerRow.createCell(2).setCellValue("Sale Price")
                headerRow.createCell(3).setCellValue("Sales Channel")
                headerRow.createCell(4).setCellValue("Discount")  // Discount column
                headerRow.createCell(5).setCellValue("Total")
                headerRow.createCell(6).setCellValue("Timestamp")

                // Write sales data
                var rowIndex = 1
                Log.d("SalesFragment", "Writing sales data to the Excel sheet.")
                salesData
                    .filter { sale -> sale.cancelled != 1 }  // Exclude cancelled sales
                    .forEach { sale ->
                        // Find the latest SalesChannel by name (get the one with the highest ID)
                        val salesChannel = salesChannels
                            .filter { it.name == sale.salesChannel }  // Get all channels with the same name
                            .maxByOrNull { it.id }  // Select the one with the latest (highest) id

                        val discount = salesChannel?.discount ?: 0
                        val total = calculateTotal(sale.salePrice, sale.quantity, discount)
                        val roundedTotal = kotlin.math.round(total).toInt()

                        // Create a new row and populate it with sales data
                        val row = sheet.createRow(rowIndex++)
                        row.createCell(0).setCellValue(sale.itemName)  // Item name
                        row.createCell(1).setCellValue(sale.quantity.toString())  // Quantity
                        row.createCell(2).setCellValue(sale.salePrice.toString())  // Sale price
                        row.createCell(3).setCellValue(sale.salesChannel)  // Sales Channel name
                        row.createCell(4).setCellValue(discount.toString())  // Discount value
                        row.createCell(5).setCellValue(roundedTotal.toString())  // Total value with discount applied
                        row.createCell(6).setCellValue(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(sale.timestamp))  // Timestamp
                    }

                // Add two-column gap before starting expense data
                rowIndex += 2

                // Create headers for expense data
                val expenseHeaderRow = sheet.createRow(rowIndex++)
                expenseHeaderRow.createCell(0).setCellValue("Expense Amount")
                expenseHeaderRow.createCell(1).setCellValue("Timestamp")

                // Write expense data
                Log.d("SalesFragment", "Writing expense data to the Excel sheet.")
                expensesData.forEach { expense ->
                    val row = sheet.createRow(rowIndex++)
                    row.createCell(0).setCellValue(expense.amount.toString())
                    row.createCell(1).setCellValue(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(expense.timestamp))
                }

                // Add two-column gap before starting calculations
                rowIndex += 2

                // Calculate and write total sales, total expenses, and profit
                val totalSales = salesData
                    .filter { sale -> sale.cancelled != 1 } // Exclude sales where cancelled is 1
                    .sumOf { sale ->
                        val salesChannel = salesChannels
                            .filter { it.name == sale.salesChannel }
                            .maxByOrNull { it.id }
                        val discount = salesChannel?.discount ?: 0
                        calculateTotal(sale.salePrice, sale.quantity, discount)
                    }
                val totalExpenses = expensesData.sumOf { it.amount }
                val profit = totalSales - totalExpenses

                val calcRow = sheet.createRow(rowIndex++)
                calcRow.createCell(0).setCellValue("Total Sales")
                calcRow.createCell(1).setCellValue(totalSales.toString())

                val expenseRow = sheet.createRow(rowIndex++)
                expenseRow.createCell(0).setCellValue("Total Expenses")
                expenseRow.createCell(1).setCellValue(totalExpenses.toString())

                val profitRow = sheet.createRow(rowIndex++)
                profitRow.createCell(0).setCellValue("Profit")
                profitRow.createCell(1).setCellValue(profit.toString())

                // Save the Excel file
                Log.d("SalesFragment", "Saving Excel file to output stream.")
                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()

                // Notify user and log success
                Log.d("SalesFragment", "Report saved successfully at: ${file.absolutePath}")
                showToast("Report saved locally: ${file.absolutePath}")
                openFile(file)
            } catch (e: Exception) {
                // Log error and show toast
                Log.e("SalesFragment", "Error saving report: ${e.message}", e)
                showToast("Error saving report: ${e.message}")
            }
        }
    }

    private fun openFile(file: File) {
        // Add a delay of 1 second (adjust the delay time if needed)
        lifecycleScope.launch {
            delay(1000)  // Delay in milliseconds (1000 ms = 1 second)

            // Refresh the system to detect the latest version of the file
            MediaScannerConnection.scanFile(requireContext(), arrayOf(file.absolutePath), null) { _, uri ->
                if (uri != null) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "application/vnd.ms-excel")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        showToast("No app found to open the report")
                    }
                } else {
                    showToast("Failed to scan and open the report")
                }
            }
        }
    }

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

    @SuppressLint("NotifyDataSetChanged")
    private fun filterSales(expenseTextView: TextView, salesTextView: TextView, profitTextView: TextView) {
        lifecycleScope.launch {
            val adjustedStartDate = getAdjustedDate(startDate, true)
            val adjustedEndDate = getAdjustedDate(endDate, false)

            val salesFromDb = withContext(Dispatchers.IO) {
                database.saleDao().getSalesBetween(adjustedStartDate, adjustedEndDate)
                    .filter { sale -> sale.cancelled != 1 } // Exclude cancelled sales here
            }

            val totalExpenses = withContext(Dispatchers.IO) {
                database.expenseDao().getTotalExpenseBetween(adjustedStartDate, adjustedEndDate)
            }

            val totalSales = salesFromDb.sumOf { sale ->
                val salesChannel = salesChannels
                    .filter { it.name == sale.salesChannel }
                    .maxByOrNull { it.id }
                val discount = salesChannel?.discount ?: 0
                calculateTotal(sale.salePrice.toDouble(), sale.quantity, discount)
            }

            val totalProfit = totalSales - totalExpenses

            sales.clear()
            sales.addAll(salesFromDb)
            adapter.notifyDataSetChanged()

            expenseTextView.text = getString(R.string.total_expense, totalExpenses)
            salesTextView.text = getString(R.string.total_sales, totalSales)
            profitTextView.text = getString(R.string.profit, totalProfit)
        }
    }

    private fun getAdjustedDate(date: Long, isStart: Boolean): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        if (isStart) {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }

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

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
