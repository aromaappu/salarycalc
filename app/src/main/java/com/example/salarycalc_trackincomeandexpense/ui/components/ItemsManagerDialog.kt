package com.example.salarycalc_trackincomeandexpense.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ItemsManagerDialog(
    title: String,
    items: List<String>,
    confirmMessage: (String) -> String,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit
) {
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text("No items yet.")
            } else {
                Column {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item, fontSize = 14.sp)
                            Text(
                                "🗑️",
                                fontSize = 16.sp,
                                modifier = Modifier.clickable { confirmDelete = item }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    val target = confirmDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove item?") },
            text = { Text(confirmMessage(target)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    confirmDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}
