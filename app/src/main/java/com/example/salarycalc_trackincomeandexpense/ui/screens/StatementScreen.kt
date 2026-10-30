package com.example.salarycalc_trackincomeandexpense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.data.StatementPdf
import com.example.salarycalc_trackincomeandexpense.data.Transaction
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import com.example.salarycalc_trackincomeandexpense.ui.util.getTodayStr
import com.example.salarycalc_trackincomeandexpense.ui.util.resolveNetBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatementScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currency = settings.getCurrencySymbol()

    val earliest = remember { db.getAllTransactions().minOfOrNull { it.date } ?: getTodayStr() }
    var fromDate by remember { mutableStateOf(earliest) }
    var toDate by remember { mutableStateOf(getTodayStr()) }
    var pickerTarget by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadingMonthly by remember { mutableStateOf(false) }

    val invalidRange = fromDate > toDate
    val transactions = remember(fromDate, toDate) {
        if (invalidRange) emptyList() else db.getTransactionsBetween(fromDate, toDate)
    }
    val totals = StatementPdf.totals(transactions)
    val netBalance = remember(db, settings) { resolveNetBalance(db, settings) }

    val lastMonth = remember { YearMonth.now().minusMonths(1) }
    val monthFrom = lastMonth.atDay(1).toString()
    val monthTo = lastMonth.atEndOfMonth().toString()
    val monthLabel = remember(lastMonth) {
        lastMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + lastMonth.year
    }
    val monthTransactions = remember(monthFrom, monthTo) { db.getTransactionsBetween(monthFrom, monthTo) }

    fun toast(msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()

    fun downloadPdf() {
        if (invalidRange) {
            toast("❌ From date must be before To date")
            return
        }
        if (transactions.isEmpty()) {
            toast("❌ No transactions in the selected range")
            return
        }
        downloading = true
        scope.launch {
            val saved = try {
                withContext(Dispatchers.IO) {
                    StatementPdf.generate(context, transactions, fromDate, toDate, netBalance)
                }
            } catch (t: Throwable) {
                null
            }
            downloading = false
            if (saved != null) toast("✅ Statement saved to Downloads: $saved")
            else toast("❌ Could not generate the PDF")
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OverlayHeader(
            "🧾 Statement",
            onBack,
            gradient = listOf(Color(0xFF0EA5E9), Color(0xFF059669))
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)
        ) {
            item {
                MonthlyStatementCard(
                    monthLabel = monthLabel,
                    fromDate = monthFrom,
                    toDate = monthTo,
                    transactionCount = monthTransactions.size,
                    downloading = downloadingMonthly,
                    onDownload = {
                        if (monthTransactions.isEmpty()) {
                            toast("❌ No transactions in $monthLabel")
                        } else {
                            downloadingMonthly = true
                            scope.launch {
                                val saved = try {
                                    withContext(Dispatchers.IO) {
                                        StatementPdf.generate(context, monthTransactions, monthFrom, monthTo, netBalance)
                                    }
                                } catch (t: Throwable) {
                                    null
                                }
                                downloadingMonthly = false
                                if (saved != null) toast("✅ $monthLabel statement saved to Downloads: $saved")
                                else toast("❌ Could not generate the PDF")
                            }
                        }
                    }
                )
            }
            item { DateFilterCard(fromDate, toDate, onPick = { pickerTarget = it }) }
            if (invalidRange) {
                item {
                    Text(
                        "⚠️ From date is after To date — showing no transactions",
                        fontSize = 12.sp,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
            item { SummaryCard(currency, totals.credit, totals.debit, netBalance) }
            item { TableHeader() }
            if (transactions.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No transactions in this range",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    TransactionRow(currency, tx)
                }
            }
        }
        Box(Modifier.padding(10.dp)) {
            DownloadButton(
                enabled = !downloading && transactions.isNotEmpty() && !invalidRange,
                label = if (downloading) "⏳ Generating PDF…" else "⬇️ Download PDF (${transactions.size} transactions)",
                onClick = { downloadPdf() }
            )
        }
    }

    pickerTarget?.let { target ->
        DatePickerDialog(
            initialDate = if (target == "from") fromDate else toDate,
            onDismiss = { pickerTarget = null },
            onDateSelected = { date ->
                if (target == "from") fromDate = date else toDate = date
                pickerTarget = null
            }
        )
    }
}

// ── Monthly shortcut ──────────────────────────────────────────────────────────

@Composable
private fun MonthlyStatementCard(
    monthLabel: String,
    fromDate: String,
    toDate: String,
    transactionCount: Int,
    downloading: Boolean,
    onDownload: () -> Unit
) {
    val enabled = transactionCount > 0 && !downloading
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Monthly Statement",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (transactionCount > 0) "$transactionCount transactions" else "No transactions",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(4.dp)
        Text(
            "$monthLabel · ${prettyDate(fromDate)} – ${prettyDate(toDate)}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(10.dp)
        Box(
            Modifier.fillMaxWidth()
                .background(if (enabled) Color(0xFF059669) else Color(0xFF9CA3AF), RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, onClick = onDownload)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (downloading) "⏳ Generating…" else "⬇️ Download $monthLabel Statement",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun prettyDate(date: String): String =
    runCatching {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date) ?: java.util.Date())
    }.getOrDefault(date)

// ── Date filter ────────────────────────────────────────────────────────────────

@Composable
private fun DateFilterCard(fromDate: String, toDate: String, onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Text(
            "Date Range",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("From", fromDate, Modifier.weight(1f)) { onPick("from") }
            DateField("To", toDate, Modifier.weight(1f)) { onPick("to") }
        }
    }
}

@Composable
private fun DateField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(4.dp)
        Box(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Text(
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(value) ?: java.util.Date()),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Totals card ─────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(currency: String, credit: Double, debit: Double, netBalance: Double) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryBox("Total Credit", formatCurrency(currency, credit), Color(0xFF059669), Modifier.weight(1f))
            SummaryBox("Total Debit", formatCurrency(currency, debit), Color(0xFFDC2626), Modifier.weight(1f))
        }
        Spacer(8.dp)
        Box(
            Modifier.fillMaxWidth()
                .background(Color(0xFF0EA5E9), RoundedCornerShape(10.dp))
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Net Balance: ${formatCurrency(currency, netBalance)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    "as per latest imported bank statement (matches dashboard)",
                    fontSize = 10.sp,
                    color = Color(0xCCFFFFFF)
                )
            }
        }
    }
}

@Composable
private fun SummaryBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(4.dp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ── Table ───────────────────────────────────────────────────────────────────────

@Composable
private fun TableHeader() {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0EA5E9)).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell("DATE", Modifier.weight(1f), bold = true, white = true)
        TableCell("NAME", Modifier.weight(2.2f), bold = true, white = true)
        TableCell("CREDIT", Modifier.weight(1.2f), bold = true, white = true, alignEnd = true)
        TableCell("DEBIT", Modifier.weight(1.2f), bold = true, white = true, alignEnd = true)
    }
}

@Composable
private fun TransactionRow(currency: String, tx: Transaction) {
    val isIncome = tx.type == AppDatabase.TYPE_INCOME
    Row(
        Modifier.fillMaxWidth()
            .background(if (isIncome) Color(0x0D059669) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableCell(
            SimpleDateFormat("dd-MM-yy", Locale.getDefault())
                .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(tx.date) ?: java.util.Date()),
            Modifier.weight(1f)
        )
        TableCell(tx.name, Modifier.weight(2.2f), maxLines = 1)
        TableCell(
            if (isIncome) formatCurrency(currency, tx.amount) else "",
            Modifier.weight(1.2f),
            alignEnd = true,
            color = Color(0xFF059669)
        )
        TableCell(
            if (isIncome) "" else formatCurrency(currency, tx.amount),
            Modifier.weight(1.2f),
            alignEnd = true,
            color = Color(0xFFDC2626)
        )
    }
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier,
    bold: Boolean = false,
    white: Boolean = false,
    alignEnd: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text,
        modifier = modifier,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = if (white) Color.White else color,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DownloadButton(enabled: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(top = 14.dp)
            .background(if (enabled) Color(0xFF059669) else Color(0xFF9CA3AF), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) =
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
