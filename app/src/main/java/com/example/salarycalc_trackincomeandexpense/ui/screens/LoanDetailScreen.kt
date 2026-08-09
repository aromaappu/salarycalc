package com.example.salarycalc_trackincomeandexpense.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.LoanStatus
import com.example.salarycalc_trackincomeandexpense.data.LoanScheduler
import com.example.salarycalc_trackincomeandexpense.data.ScheduleStatus
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import java.time.LocalDate

@Composable
fun LoanDetailScreen(
    db: AppDatabase,
    settings: SettingsStore,
    loanId: Long,
    onBack: () -> Unit,
    onChange: () -> Unit
) {
    val context = LocalContext.current
    val currency = settings.getCurrencySymbol()
    var refreshKey by remember { mutableIntStateOf(0) }
    var showPayment by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }

    val loan = remember(loanId, refreshKey) { db.getLoan(loanId) }
    if (loan == null) {
        onBack()
        return
    }
    val schedules = remember(loanId, refreshKey) { db.getLoanSchedules(loanId) }
    val payments = remember(loanId, refreshKey) { db.getLoanPayments(loanId) }
    val today = LocalDate.now()

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF667EEA))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0x33FFFFFF))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("←", color = Color.White, fontSize = 18.sp)
            }
            Text(
                text = loan.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                "🗑️",
                fontSize = 16.sp,
                modifier = Modifier.clickable { confirmDelete = true }
            )
        }

        Column(Modifier.padding(10.dp)) {
            Row {
                DetailStat("Loan amount", formatCurrency(currency, loan.originalAmount), Modifier.weight(1f))
                DetailStat("Balance", formatCurrency(currency, loan.remainingBalance), Modifier.weight(1f))
                DetailStat("EMI", formatCurrency(currency, loan.monthlyPayment), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                DetailStat("Rate", "${loan.interestRate}% (${loan.interestType.name.lowercase()})", Modifier.weight(1f))
                DetailStat("Total paid", formatCurrency(currency, loan.totalPaid), Modifier.weight(1f))
                DetailStat("Interest paid", formatCurrency(currency, loan.totalInterestPaid), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                DetailStat("Started", loan.startDate, Modifier.weight(1f))
                DetailStat("First due", loan.firstDueDate, Modifier.weight(1f))
                DetailStat(
                    if (loan.status == LoanStatus.COMPLETED) "Completed" else "Expected",
                    if (loan.status == LoanStatus.COMPLETED)
                        loan.actualCompletionDate ?: "-"
                    else loan.expectedCompletionDate ?: "-",
                    Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            if (loan.status == LoanStatus.ACTIVE) {
                if (loan.remainingBalance <= 0.0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF059669))
                            .clickable { confirmComplete = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("✓ Mark as Completed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10B981))
                            .clickable { showPayment = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("💳 Record Payment", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2563EB))
                        .clickable {
                            db.reopenLoan(loan.id)
                            LoanScheduler.scheduleNext(context, db, loan.id)
                            refreshKey++
                            onChange()
                            toast("Loan reopened")
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("↩ Reopen Loan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Repayment schedule (${schedules.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            if (schedules.isEmpty()) {
                Text(
                    "No schedule yet",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                schedules.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "#${s.paymentNumber} · ${formatDisplay(s.dueDate)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "EMI ${formatCurrency(currency, s.expectedPayment)} · " +
                                    "P ${formatCurrency(currency, s.principal)} · " +
                                    "I ${formatCurrency(currency, s.interest)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (s.paidAmount > 0) {
                                Text(
                                    "Paid ${formatCurrency(currency, s.paidAmount)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF059669)
                                )
                            }
                        }
                        StatusChip(s.status, s.paidAmount, s.expectedPayment)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Payment history (${payments.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            if (payments.isEmpty()) {
                Text(
                    "No payments yet",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                payments.sortedByDescending { it.paymentDate }.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.paymentDate,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Interest ${formatCurrency(currency, p.interestAmount)} · " +
                                    "Principal ${formatCurrency(currency, p.principalAmount)}" +
                                    (if (p.extraPayment > 0) " · Extra ${formatCurrency(currency, p.extraPayment)}" else ""),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (p.paymentMethod.isNotBlank() || p.reference.isNotBlank()) {
                                Text(
                                    listOf(p.paymentMethod, p.reference).filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            formatCurrency(currency, p.paidAmount),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showPayment) {
        LoanPaymentDialog(
            db = db,
            settings = settings,
            loanId = loan.id,
            onDismiss = { showPayment = false },
            onRecorded = {
                showPayment = false
                refreshKey++
                onChange()
            }
        )
    }

    if (confirmComplete) {
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            title = { Text("Mark as completed?") },
            text = { Text("The loan balance is fully repaid. Mark ${loan.name} as completed?") },
            confirmButton = {
                TextButton(onClick = {
                    db.markLoanCompleted(loan.id, today.toString())
                    LoanScheduler.cancelAll(context, db, loan.id)
                    refreshKey++
                    onChange()
                    confirmComplete = false
                    toast("🎉 Loan completed")
                }) { Text("Complete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmComplete = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete loan?") },
            text = { Text("Delete \"${loan.name}\" and all its payments, schedule and reminders?") },
            confirmButton = {
                TextButton(onClick = {
                    LoanScheduler.cancelAll(context, db, loan.id)
                    db.deleteLoan(loan.id)
                    confirmDelete = false
                    toast("🗑️ Loan deleted")
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusChip(status: ScheduleStatus, paidAmount: Double, expected: Double) {
    val (label, color) = when (status) {
        ScheduleStatus.PAID -> "PAID" to Color(0xFF059669)
        ScheduleStatus.PARTIALLY_PAID -> "PARTIAL" to Color(0xFF2563EB)
        ScheduleStatus.DUE_TODAY -> "DUE TODAY" to Color(0xFFD97706)
        ScheduleStatus.DUE_TOMORROW -> "DUE" to Color(0xFFD97706)
        ScheduleStatus.OVERDUE -> "OVERDUE" to Color(0xFFDC2626)
        else -> "UPCOMING" to MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

private fun formatDisplay(dateStr: String): String =
    runCatching {
        val d = LocalDate.parse(dateStr)
        "${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.year}"
    }.getOrDefault(dateStr)
