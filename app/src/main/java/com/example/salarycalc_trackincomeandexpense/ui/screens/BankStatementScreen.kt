package com.example.salarycalc_trackincomeandexpense.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.AutoEvents
import com.example.salarycalc_trackincomeandexpense.bankimport.ImportRepository
import com.example.salarycalc_trackincomeandexpense.bankimport.NarrationReader
import com.example.salarycalc_trackincomeandexpense.bankimport.ParsedTransaction
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.BankStatementRecord
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── State ─────────────────────────────────────────────────────────────────────

private sealed class ScreenState {
    object Idle : ScreenState()
    data class Loading(val message: String) : ScreenState()
    data class Preview(
        val transactions: List<ParsedTransaction>,
        val bank: String,
        val confidence: Float,
        val source: String,
        val fileName: String,
        val closingBalance: Double = 0.0,
        val skippedRows: Int = 0,
        val warnings: List<String> = emptyList(),
        val possibleDuplicateHashes: Set<String> = emptySet()
    ) : ScreenState()
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun BankStatementScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var statements by remember { mutableStateOf(db.getBankStatements()) }
    var screenState by remember { mutableStateOf<ScreenState>(ScreenState.Idle) }
    var deleteTarget by remember { mutableStateOf<BankStatementRecord?>(null) }
    var deleteStep by remember { mutableIntStateOf(1) }
    var importSummary by remember { mutableStateOf<ImportRepository.ImportSummary?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun runImport(uri: Uri, isCsv: Boolean) {
        importSummary = null
        screenState = ScreenState.Idle
        scope.launch {
            screenState = ScreenState.Loading("Reading file…")
            val name = queryDisplayName(context.contentResolver, uri)

            // Upload-size limit (validated again server-side equivalent in the pipeline)
            val size = queryFileSize(context.contentResolver, uri)
            if (size != null && size > MAX_IMPORT_BYTES) {
                screenState = ScreenState.Idle
                toast("❌ File too large. Maximum size is 30 MB.")
                return@launch
            }

            val result = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        if (isCsv) ImportRepository.processCsv(stream, db, "", name)
                        else        ImportRepository.processPdf(context, stream, db, "", name)
                    } ?: ImportRepository.ImportResult.Error("Could not open file")
                }
            } catch (e: Exception) {
                ImportRepository.ImportResult.Error("Could not read this statement: ${e.message}")
            }

            when (result) {
                is ImportRepository.ImportResult.Ready -> {
                    if (result.transactions.isEmpty()) {
                        screenState = ScreenState.Idle
                        val msg = buildString {
                            append("All transactions already imported")
                            if (result.summary.duplicatesSkipped > 0) append(" (${result.summary.duplicatesSkipped} duplicates skipped)")
                        }
                        toast(msg)
                        return@launch
                    }
                    val saved = withContext(Dispatchers.IO) {
                        ImportRepository.saveStatement(result.transactions, db, name, result.summary.closingBalance)
                    }
                    statements = db.getBankStatements()
                    screenState = ScreenState.Idle
                    AutoEvents.refreshTick.intValue++
                    importSummary = result.summary.copy(imported = saved, fileName = name)
                }

                is ImportRepository.ImportResult.NeedsPreview -> {
                    if (result.transactions.isEmpty()) {
                        screenState = ScreenState.Idle
                        toast("No valid transactions found after filtering duplicates.")
                        return@launch
                    }
                    screenState = ScreenState.Preview(
                        transactions = result.transactions,
                        bank = result.bank,
                        confidence = result.confidence,
                        source = result.source,
                        fileName = name,
                        closingBalance = result.closingBalance,
                        skippedRows = result.skippedRows,
                        warnings = result.warnings,
                        possibleDuplicateHashes = result.possibleDuplicateHashes
                    )
                }

                is ImportRepository.ImportResult.Error -> {
                    screenState = ScreenState.Idle
                    toast("❌ ${result.message}")
                }
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runImport(uri, isCsv = false)
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runImport(uri, isCsv = true)
    }

    // ── Preview screen ────────────────────────────────────────────────────────
    val preview = screenState as? ScreenState.Preview
    if (preview != null) {
        PreviewScreen(
            preview = preview,
            onConfirm = { selected ->
                scope.launch {
                    screenState = ScreenState.Loading("Saving…")
                    val saved = withContext(Dispatchers.IO) {
                        ImportRepository.saveStatement(selected, db, preview.fileName, preview.closingBalance)
                    }
                    statements = db.getBankStatements()
                    screenState = ScreenState.Idle
                    AutoEvents.refreshTick.intValue++
                    toast("✅ Saved $saved transactions")
                }
            },
            onCancel = { screenState = ScreenState.Idle }
        )
        return
    }

    // ── Main screen ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        OverlayHeader(
            "📄 Bank Statement",
            onBack,
            gradient = listOf(Color(0xFF0EA5E9), Color(0xFF2563EB))
        )
        Column(Modifier.padding(10.dp)) {

            val isLoading = screenState is ScreenState.Loading
            val loadMsg = (screenState as? ScreenState.Loading)?.message ?: ""

            Text(
                "Upload a bank statement PDF or CSV. Transactions are added to Income/Expense automatically.\n\n" +
                    "Balances and amounts are auto-read and may not be 100% accurate — please verify before relying on them.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // PDF button
            ImportButton(
                label = if (isLoading) "⏳ $loadMsg" else "📄 Upload PDF Statement",
                color = Color(0xFF0EA5E9),
                enabled = !isLoading,
                onClick = { pdfPicker.launch(arrayOf("application/pdf")) }
            )

            Spacer(Modifier.height(8.dp))

            // CSV button
            ImportButton(
                label = if (isLoading) "⏳ $loadMsg" else "📊 Upload CSV Statement",
                color = Color(0xFF10B981),
                enabled = !isLoading,
                onClick = { csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Imported statements",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))

            if (statements.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No statements imported yet",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                statements.forEach { stmt ->
                    StatementRow(stmt, onDelete = { deleteTarget = stmt; deleteStep = 1 })
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }

    deleteTarget?.let { stmt ->
        val linkedCount = remember(stmt.id) { db.getLinkedStatementTransactionCount(stmt.id) }
        val olderUnlinked = stmt.txCount > linkedCount
        AlertDialog(
            onDismissRequest = { deleteTarget = null; deleteStep = 1 },
            title = { Text("Delete statement — confirmation $deleteStep of 3") },
            text = {
                Column {
                    Text("File: ${stmt.fileName}")
                    Spacer(Modifier.height(8.dp))
                    Text("$linkedCount linked imported transactions will be permanently deleted.")
                    if (olderUnlinked) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Older imported transactions cannot be safely identified and will be kept.",
                            color = Color(0xFFDC2626)
                        )
                    }
                    if (deleteStep == 3) {
                        Spacer(Modifier.height(8.dp))
                        Text("Final confirmation. This cannot be undone.", color = Color(0xFFDC2626))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteStep < 3) {
                        deleteStep++
                    } else {
                        val removed = db.deleteBankStatementAndLinkedTransactions(stmt.id)
                        statements = db.getBankStatements()
                        deleteTarget = null
                        deleteStep = 1
                        AutoEvents.refreshTick.intValue++
                        toast("Statement deleted with $removed linked transactions")
                    }
                }) { Text(if (deleteStep == 3) "Permanently delete" else "Continue") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null; deleteStep = 1 }) { Text("Cancel") }
            }
        )
    }

    // ── Import Summary ────────────────────────────────────────────────────────
    importSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { importSummary = null },
            title = { Text("📊 Import Summary") },
            text = {
                Column {
                    SummaryRow("📄 PDF Uploaded", "✓ ${summary.fileName}")
                    SummaryRow("📝 TXT Generated", if (summary.txtGenerated) "✓" else "—")
                    SummaryRow("🔎 Bank Detected", summary.bank)
                    SummaryRow("🔢 Transactions Found", summary.transactionsFound.toString())
                    SummaryRow("✅ Imported Successfully", summary.imported.toString())
                    SummaryRow("🔁 Duplicates Skipped", summary.duplicatesSkipped.toString())
                    SummaryRow("⚠️ Errors Found", summary.errorsFound.toString())
                    if (summary.viaOcr) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "This was a scanned statement — text was read with OCR.",
                            fontSize = 12.sp,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importSummary = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Preview Screen ─────────────────────────────────────────────────────────────

@Composable
private fun PreviewScreen(
    preview: ScreenState.Preview,
    onConfirm: (List<ParsedTransaction>) -> Unit,
    onCancel: () -> Unit
) {
    val selected = remember { mutableStateListOf<Boolean>().also { list ->
        repeat(preview.transactions.size) { index ->
            list.add(preview.transactions[index].hash !in preview.possibleDuplicateHashes)
        }
    }}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        OverlayHeader(
            "🔍 Review Transactions",
            onCancel,
            gradient = listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
        )

        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                "Bank: ${preview.bank}  •  Source: ${preview.source}  •  Confidence: ${"%.0f".format(preview.confidence * 100)}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Review every PDF transaction against your statement before importing.",
                fontSize = 12.sp,
                color = Color(0xFFF59E0B)
            )
            if (preview.skippedRows > 0) {
                Text(
                    "${preview.skippedRows} PDF rows could not be validated; check for missing transactions.",
                    fontSize = 12.sp,
                    color = Color(0xFFEF4444)
                )
            }
            preview.warnings.forEach { warning ->
                Text(warning, fontSize = 12.sp, color = Color(0xFFEF4444))
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            items(preview.transactions.indices.toList()) { i ->
                val tx = preview.transactions[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selected[i],
                        onCheckedChange = { selected[i] = it }
                    )
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            if (NarrationReader.containsRawNarration(tx.description)) {
                                NarrationReader.cleanNarration(tx.description)
                            } else {
                                tx.description
                            },
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1
                        )
                        Text("${tx.date}  •  ${tx.reference.take(16)}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (tx.hash in preview.possibleDuplicateHashes) {
                            Text(
                                "Possible duplicate — unchecked. Verify before adding.",
                                fontSize = 11.sp, color = Color(0xFFD97706)
                            )
                        }
                    }
                    Text(
                        "${if (tx.type == "CREDIT") "+" else "-"}₹${"%.2f".format(tx.amount)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tx.type == "CREDIT") Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF6B7280), RoundedCornerShape(8.dp))
                    .clickable { onCancel() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF10B981), RoundedCornerShape(8.dp))
                    .clickable {
                        val toImport = preview.transactions.filterIndexed { i, _ -> selected[i] }
                        if (toImport.isNotEmpty()) onConfirm(toImport)
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                val count = selected.count { it }
                Text("Import $count transactions", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Small composables ──────────────────────────────────────────────────────────

@Composable
private fun ImportButton(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (enabled) color else Color(0xFF9CA3AF), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatementRow(stmt: BankStatementRecord, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(Color(0xFF2563EB)),
            contentAlignment = Alignment.Center
        ) { Text("📄", fontSize = 16.sp) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(stmt.fileName, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(
                "${stmt.txCount} transactions · ${
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(stmt.importedAt))
                }",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("🗑️", fontSize = 16.sp, modifier = Modifier.clickable { onDelete() })
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private const val MAX_IMPORT_BYTES = 30L * 1024 * 1024

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String =
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
        ?: uri.lastPathSegment ?: "statement"

private fun queryFileSize(resolver: android.content.ContentResolver, uri: Uri): Long? =
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
        ?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
