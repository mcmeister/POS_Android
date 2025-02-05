package com.example.pos

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SalesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SalesAdapter
    private lateinit var database: AppDatabase
    private val sales = mutableListOf<Sale>()
    private val items = mutableListOf<Item>()
    private val salesChannels = mutableListOf<SalesChannel>()
    private var startDate: Long = 0
    private var endDate: Long = System.currentTimeMillis()

    companion object {
        private const val REQUEST_STORAGE_PERMISSION = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sales, container, false)

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

            adapter = SalesAdapter(
                requireContext(),
                orders = sales.groupBy { it.id },  // Group items under one order
                salesChannels = salesChannels,
                onCancelSaleClick = { sale -> cancelSale(sale) },
                onCancelOrderClick = { orderId -> cancelOrder(orderId) }
            )

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
            Log.d("SalesFragment", "Export button clicked, saving locally...")
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

        if (!hasStoragePermission()) { // ✅ Check if permission is needed before asking
            requestStoragePermissions()
        }

        return view
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else
            ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                showToast("Storage permission granted. You can now export reports.")
            } else {
                showToast("Storage permission denied. Export will not work.")
            }
        }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            requestPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else // Android 6-12
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
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

    private fun cancelOrder(orderId: Int) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val orderItems = adapter.orders[orderId] ?: return@withContext
                orderItems.forEach { sale ->
                    database.saleDao().markSaleAsCancelled(sale.id)
                }
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

    private fun calculateTotal(salePrice: Double, quantity: Int, discount: Int): Double {
        return salePrice * quantity * (1 - discount / 100.0)
    }

    private fun saveReportLocally(salesData: List<SalesReport>, expensesData: List<Expense>, salesChannels: List<SalesChannel>) {
        lifecycleScope.launch {
            try {
                Log.d("SalesFragment", "Starting to save report locally.")

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val startDateFormatted = dateFormat.format(startDate)
                val endDateFormatted = dateFormat.format(endDate)

                val fileName = "$startDateFormatted-$endDateFormatted.xls"
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

                val workbook = HSSFWorkbook()
                val sheet = workbook.createSheet("Sales Report")

                // Create headers
                val headerRow = sheet.createRow(0)
                headerRow.createCell(0).setCellValue("Order #")
                headerRow.createCell(1).setCellValue("Item Name")
                headerRow.createCell(2).setCellValue("Quantity")
                headerRow.createCell(3).setCellValue("Sale Price")
                headerRow.createCell(4).setCellValue("Sales Channel")
                headerRow.createCell(5).setCellValue("Discount")
                headerRow.createCell(6).setCellValue("Total")
                headerRow.createCell(7).setCellValue("Timestamp")

                // Sort sales data by orderId ASC
                val groupedSales = salesData.filter { it.cancelled != 1 }
                    .groupBy { it.orderId }
                    .toSortedMap(compareBy { it }) // Sort by orderId ASC

                var rowIndex = 1
                groupedSales.forEach { (orderId, orderSales) ->
                    // Add an empty row before a new order starts
                    if (rowIndex > 1) rowIndex++

                    // Order row (merged)
                    val orderRow = sheet.createRow(rowIndex++)
                    orderRow.createCell(0).setCellValue("Order #$orderId")

                    orderSales.forEach { sale ->
                        val salesChannel = salesChannels.find { it.name == sale.salesChannel }
                        val discount = salesChannel?.discount ?: 0
                        val total = sale.salePrice * sale.quantity * (1 - discount / 100.0)

                        val row = sheet.createRow(rowIndex++)
                        row.createCell(0).setCellValue("") // Empty for order grouping
                        row.createCell(1).setCellValue(sale.itemName)
                        row.createCell(2).setCellValue(sale.quantity.toString())
                        row.createCell(3).setCellValue(sale.salePrice.toString())
                        row.createCell(4).setCellValue(sale.salesChannel)
                        row.createCell(5).setCellValue("$discount%")
                        row.createCell(6).setCellValue(total.toString())
                        row.createCell(7).setCellValue(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(sale.timestamp))
                    }
                }

                // Expenses and profit calculations
                rowIndex += 2
                val totalSales = groupedSales.values.sumOf { orderSales ->
                    orderSales.sumOf { sale ->
                        val salesChannel = salesChannels.find { it.name == sale.salesChannel }
                        val discount = salesChannel?.discount ?: 0
                        sale.salePrice * sale.quantity * (1 - discount / 100.0)
                    }
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

                val outputStream = FileOutputStream(file)
                workbook.write(outputStream)
                outputStream.close()

                // ✅ Scan file so it appears in Downloads folder
                MediaScannerConnection.scanFile(
                    requireContext(),
                    arrayOf(file.absolutePath),
                    arrayOf("application/vnd.ms-excel")
                ) { path, _ ->
                    Log.d("SalesFragment", "File scanned and now visible: $path")
                }

                // ✅ Show success message
                Log.d("SalesFragment", "Report saved successfully at: ${file.absolutePath}")
                showToast("Report saved locally: ${file.absolutePath}")

                // ✅ Open file after scanning
                openFile(file)
            } catch (e: Exception) {
                Log.e("SalesFragment", "Error saving report: ${e.message}", e)
                showToast("Error saving report: ${e.message}")
            }
        }
    }

    private fun openFile(file: File) {
        lifecycleScope.launch {
            delay(1000) // ✅ Allow time for MediaScanner to index the file

            try {
                val fileUri = FileProvider.getUriForFile(requireContext(), "com.example.pos.fileprovider", file)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "application/vnd.ms-excel")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                showToast("No app found to open the report")
            } catch (e: Exception) {
                showToast("Error opening file: ${e.message}")
                Log.e("SalesFragment", "Error opening file", e)
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
                    .filter { sale -> sale.cancelled != 1 }
            }

            // ✅ Group orders by `orderId` and sort them in DESCENDING order
            val groupedSales = salesFromDb
                .groupBy { it.orderId }
                .toSortedMap(compareByDescending { it }) // ✅ Ensures latest orders appear first

            val totalExpenses = withContext(Dispatchers.IO) {
                database.expenseDao().getTotalExpenseBetween(adjustedStartDate, adjustedEndDate)
            }

            val totalSales = groupedSales.values.sumOf { orderSales ->
                orderSales.sumOf { sale ->
                    val salesChannel = salesChannels.find { it.name == sale.salesChannel }
                    val discount = salesChannel?.discount ?: 0
                    calculateTotal(sale.salePrice, sale.quantity, discount)
                }
            }

            val totalProfit = totalSales - totalExpenses

            sales.clear()
            sales.addAll(salesFromDb)
            adapter.updateOrders(groupedSales) // ✅ Orders now appear in descending order
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
