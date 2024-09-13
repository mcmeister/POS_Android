@file:Suppress("DEPRECATION")

package com.example.pos

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import java.text.SimpleDateFormat
import java.util.Locale

class GoogleDrive(private val fragment: Fragment) {

    private lateinit var googleSignInClient: GoogleSignInClient
    private var googleAccount: GoogleSignInAccount? = null
    private val jsonFactory: GsonFactory? = GsonFactory.getDefaultInstance()

    companion object {
        const val REQUEST_AUTHORIZATION = 1001
    }

    private fun loadClientSecret(): GoogleClientSecrets {
        val inputStream = fragment.requireContext().resources.openRawResource(R.raw.pos_client_secret)
        return GoogleClientSecrets.load(jsonFactory, InputStreamReader(inputStream))
    }

    // Initialize Google Sign-In
    fun initializeGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_FILE),
                Scope("https://www.googleapis.com/auth/spreadsheets")
            )
            .build()
        googleSignInClient = GoogleSignIn.getClient(fragment.requireActivity(), gso)
    }

    // Launch sign-in intent or use last signed-in account
    suspend fun signInToGoogle(signInLauncher: ActivityResultLauncher<Intent>, onSuccess: () -> Unit) {
        // Check if there's already a signed-in account
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(fragment.requireContext())
        if (lastSignedInAccount != null) {
            // User is already signed in, use the last account
            googleAccount = lastSignedInAccount
            onSuccess()
            Log.d("GoogleDrive", "Using the last signed-in Google account")
            showToast("Using existing Google Drive account")
        } else {
            // User not signed in, launch the sign-in intent
            val signInIntent = googleSignInClient.signInIntent
            Log.d("GoogleDrive", "Launching Google Sign-In intent")
            signInLauncher.launch(signInIntent)
        }
    }

    // Handle Google sign-in result
    suspend fun handleSignInResult(task: Task<GoogleSignInAccount>, onSuccess: () -> Unit, onFailure: () -> Unit) {
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                googleAccount = account
                onSuccess()
                Log.d("GoogleDrive", "Sign-In successful for account: ${account.email}")
                showToast("Signed in as ${account.email}")
            } else {
                onFailure()
                Log.e("GoogleDrive", "Google Sign-In failed, account is null")
            }
        } catch (e: ApiException) {
            onFailure()
            Log.e("GoogleDrive", "Google Sign-In failed with status code: ${e.statusCode}", e)
            showToast("Google Sign-In failed: ${e.statusCode}")
        }
    }

    suspend fun getDriveService(): Drive? {
        return withContext(Dispatchers.IO) {
            googleAccount?.let {
                // Load the credentials
                val clientSecrets = loadClientSecret()
                val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

                val credential = GoogleAccountCredential.usingOAuth2(
                    fragment.requireContext(),
                    listOf(DriveScopes.DRIVE_FILE)
                ).setSelectedAccount(it.account)

                Log.d("GoogleDrive", "Creating Google Drive service for account: ${it.account?.name}")

                // Build the Drive service
                Drive.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("POS App")
                    .build()
            } ?: run {
                Log.e("GoogleDrive", "Google account is null. Cannot create Drive service.")
                null
            }
        }
    }

    suspend fun checkOrCreateFolder(driveService: Drive, folderName: String, parentFolderId: String? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                var query = "mimeType='application/vnd.google-apps.folder' and name='$folderName'"
                if (parentFolderId != null) {
                    query += " and '$parentFolderId' in parents"
                }

                val result = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .execute()

                if (result.files.isEmpty()) {
                    // Create folder if it doesn't exist
                    val folderMetadata = File()
                        .setName(folderName)
                        .setMimeType("application/vnd.google-apps.folder")
                    parentFolderId?.let { folderMetadata.parents = listOf(it) }

                    val folder = driveService.files().create(folderMetadata)
                        .setFields("id")
                        .execute()

                    // Show toast and log on success
                    showToast("Folder $folderName created successfully")
                    Log.d("GoogleDrive", "Folder $folderName created with ID: ${folder.id}")

                    folder.id
                } else {
                    // Return existing folder ID
                    result.files[0].id
                }
            } catch (e: Exception) {
                Log.e("GoogleDrive", "Error creating folder: $folderName", e)
                showToast("Failed to create folder: $folderName")
                null
            }
        }
    }

    // Convert expense data to a formatted list for spreadsheet
    private fun expensesDataToCSV(expenses: List<Expense>): List<List<String>> {
        val header = listOf("Amount", "Timestamp")
        val data = expenses.map { expense ->
            listOf(
                expense.amount.toString(),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(expense.timestamp)
            )
        }
        return listOf(header) + data
    }

    // Function to calculate total sales, total expense, and profit
    private fun calculateProfit(sales: List<SalesReport>, expenses: List<Expense>): List<List<String>> {
        // Calculate total sales: SUM(SalePrice * Quantity)
        val totalSales = sales.sumOf { it.salePrice * it.quantity }

        // Calculate total expense: SUM(expense.amount)
        val totalExpense = expenses.sumOf { it.amount }

        // Calculate profit: Total Sales - Total Expense
        val profit = totalSales - totalExpense

        // Prepare the data for the spreadsheet
        val header = listOf("Total Sales", "Total Expense", "Profit")
        val data = listOf(
            totalSales.toString(),
            totalExpense.toString(),
            profit.toString()
        )

        return listOf(header, data)
    }

    // Adjust the createSpreadsheet function to add profit calculation in columns starting from M
    suspend fun createSpreadsheet(
        driveService: Drive,
        sheetsService: Sheets,
        reportsFolderId: String,
        fileName: String,
        salesCsvData: String,
        expenses: List<Expense>,
        sales: List<SalesReport>  // Pass sales data to calculate profit
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Create the spreadsheet
                val spreadsheetMetadata = File()
                    .setName(fileName.replace(".csv", ""))  // Remove .csv from the filename
                    .setMimeType("application/vnd.google-apps.spreadsheet")
                    .setParents(listOf(reportsFolderId))

                val spreadsheetFile = driveService.files().create(spreadsheetMetadata)
                    .setFields("id")
                    .execute()

                val spreadsheetId = spreadsheetFile.id
                Log.d("GoogleDrive", "Spreadsheet created with ID: $spreadsheetId")

                // Parse the sales CSV content into a list of rows
                val salesRows = salesCsvData.split("\n").map { it.split(",") }

                // Create the data range for the sales report
                val salesData = ValueRange().setValues(salesRows)

                // Write sales data to the spreadsheet starting at A1
                sheetsService.spreadsheets().values()
                    .update(spreadsheetId, "A1", salesData)
                    .setValueInputOption("RAW")
                    .execute()

                Log.d("GoogleDrive", "Sales data populated in spreadsheet from CSV")

                // Convert expense data to list of lists (rows and columns)
                val expenseRows = expensesDataToCSV(expenses)

                // Wrap expenseRows in a ValueRange and write to the spreadsheet starting at I1
                val expenseData = ValueRange().setValues(expenseRows)

                sheetsService.spreadsheets().values()
                    .update(spreadsheetId, "I1", expenseData)
                    .setValueInputOption("RAW")
                    .execute()

                Log.d("GoogleDrive", "Expense data populated in spreadsheet")

                // Calculate and add Total Sales, Total Expense, and Profit
                val profitData = calculateProfit(sales, expenses)

                // Write the profit data to the spreadsheet starting at M1
                sheetsService.spreadsheets().values()
                    .update(spreadsheetId, "M1", ValueRange().setValues(profitData))
                    .setValueInputOption("RAW")
                    .execute()

                Log.d("GoogleDrive", "Profit data populated in spreadsheet")

                return@withContext spreadsheetId
            } catch (e: UserRecoverableAuthIOException) {
                Log.e("GoogleDrive", "UserRecoverableAuthIOException: Need user consent")
                withContext(Dispatchers.Main) {
                    fragment.startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                }
                null
            } catch (e: Exception) {
                Log.e("GoogleDrive", "Error creating spreadsheet: $fileName", e)
                showToast("Failed to create spreadsheet: $fileName")
                return@withContext null
            }
        }
    }

    // Function to get the Google Sheets service (like getDriveService)
    suspend fun getSheetsService(): Sheets? {
        return withContext(Dispatchers.IO) {
            googleAccount?.let {
                val credential = GoogleAccountCredential.usingOAuth2(
                    fragment.requireContext(),
                    listOf(DriveScopes.DRIVE_FILE, "https://www.googleapis.com/auth/spreadsheets")
                ).setSelectedAccount(it.account)

                val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

                Sheets.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("POS App")
                    .build()
            } ?: run {
                Log.e("GoogleDrive", "Google account is null. Cannot create Sheets service.")
                null
            }
        }
    }

    suspend fun saveReportToDrive(driveService: Drive, fileName: String, parentFolderId: String, fileContent: ByteArray) {
        return withContext(Dispatchers.IO) {
            try {
                val fileMetadata = File()
                    .setName(fileName)
                    .setParents(listOf(parentFolderId))

                val mediaContent = ByteArrayContent("text/csv", fileContent)

                val file = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()

                // Log and Toast after successful upload
                Log.d("GoogleDrive", "File $fileName uploaded to Google Drive with ID: ${file.id}")
                showToast("File $fileName uploaded successfully to Google Drive")
            } catch (e: Exception) {
                Log.e("GoogleDrive", "Error uploading file: $fileName", e)
                showToast("Failed to upload file: $fileName")
            }
        }
    }

    // Show toast messages
    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
