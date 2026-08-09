package com.example.salarycalc_trackincomeandexpense

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.salarycalc_trackincomeandexpense.importer.ImportWorker

class ImportActivity : ComponentActivity() {
    private val PICK_PDF = 1001
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Button(onClick = { openPicker() }) { Text("Select PDF and Import") }
                }
            }
        }
    }

    private fun openPicker() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(i, PICK_PDF)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PDF && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) enqueueImport(uri)
        }
    }

    private fun enqueueImport(uri: Uri) {
        val input = Data.Builder().putString("fileUri", uri.toString()).putString("sourceFilename", uri.lastPathSegment).build()
        val req = OneTimeWorkRequestBuilder<ImportWorker>().setInputData(input).build()
        WorkManager.getInstance(applicationContext).enqueue(req)
        finish()
    }
}
