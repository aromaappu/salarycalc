package com.example.salarycalc_trackincomeandexpense.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

data class PickedReceipt(val name: String, val path: String)

@Composable
fun rememberReceiptPicker(onPicked: (PickedReceipt) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes == null) {
                    Toast.makeText(context, "Error: Could not read file", Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }
                val name = getFileName(context, uri)
                val dir = File(context.filesDir, "receipts")
                if (!dir.exists()) dir.mkdirs()
                val dest = File(dir, name)
                dest.writeBytes(bytes)
                onPicked(PickedReceipt(name, dest.absolutePath))
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    return {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        launcher.launch(Intent.createChooser(intent, "Select Receipt"))
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) result = it.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != -1) result = result?.substring(cut!! + 1)
    }
    return result ?: "receipt"
}
