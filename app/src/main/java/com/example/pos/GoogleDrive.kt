@file:Suppress("DEPRECATION")

package com.example.pos

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDrive(private val fragment: Fragment) {

    private lateinit var googleSignInClient: GoogleSignInClient
    var googleAccount: GoogleSignInAccount? = null

    // Initialize Google Sign-In
    fun initializeGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        googleSignInClient = GoogleSignIn.getClient(fragment.requireActivity(), gso)
    }

    // Launch sign-in intent
    fun signInToGoogle(signInLauncher: ActivityResultLauncher<Intent>) {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    // Handle Google sign-in result
    fun handleSignInResult(task: Task<GoogleSignInAccount>, onSuccess: () -> Unit, onFailure: () -> Unit) {
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                googleAccount = account
                onSuccess()
            } else {
                onFailure()
            }
        } catch (e: ApiException) {
            onFailure()
        }
    }

    suspend fun getDriveService(): Drive? {
        return withContext(Dispatchers.IO) {
            googleAccount?.let {
                val credential = GoogleAccountCredential.usingOAuth2(
                    fragment.requireContext(),
                    listOf(DriveScopes.DRIVE_FILE)
                ).setSelectedAccount(it.account)

                Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("POS App").build()
            }
        }
    }

    suspend fun checkOrCreateFolder(driveService: Drive, folderName: String, parentFolderId: String? = null): String? {
        return withContext(Dispatchers.IO) {
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
                folder.id
            } else {
                // Return existing folder ID
                result.files[0].id
            }
        }
    }

    suspend fun saveReportToDrive(driveService: Drive, fileName: String, parentFolderId: String, fileContent: ByteArray) {
        return withContext(Dispatchers.IO) {
            val fileMetadata = File()
                .setName(fileName)
                .setParents(listOf(parentFolderId))

            val mediaContent = ByteArrayContent("text/csv", fileContent)

            driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
        }
    }
}
