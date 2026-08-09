package com.example.salarycalc_trackincomeandexpense.ui.screens

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.Loan
import com.example.salarycalc_trackincomeandexpense.data.LoanScheduler
import com.example.salarycalc_trackincomeandexpense.data.LoanStatus
import com.example.salarycalc_trackincomeandexpense.data.ScheduleStatus
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

private sealed interface LoanNav {
    object List : LoanNav
    data class Detail(val loanId: Long) : LoanNav
    object Form : LoanNav
    data class Edit(val loanId: Long) : LoanNav
}

@Composable
fun LoanTrackerScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    var nav by remember { mutableStateOf<LoanNav>(LoanNav.List) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<Loan?>(null) }
    val context = LocalContext.current

    when (val current = nav) {
        is LoanNav.Detail -> LoanDetailScreen(
            db = db,
            settings = settings,
            loanId = current.loanId,
            onBack = { nav = LoanNav.List },
            onChange = { refreshKey++ }
        )
        is LoanNav.Form -> LoanFormScreen(
            db = db,
            settings = settings,
            onBack = { nav = LoanNav.List },
            onSaved = { nav = LoanNav.List; refreshKey++ }
        )
        is LoanNav.Edit -> LoanFormScreen(
            db = db,
            settings = settings,
            onBack = { nav = LoanNav.List },
            onSaved = { nav = LoanNav.List; refreshKey++ },
            editLoan = db.getLoan(current.loanId)
        )
        is LoanNav.List -> LoanListContent(
            db = db,
            settings = settings,
            refreshKey = refreshKey,
            onBack = onBack,
            onAddLoan = { nav = LoanNav.Form },
            onLoanClick = { nav = LoanNav.Detail(it.id) },
            onEdit = { nav = LoanNav.Edit(it.id) },
            onDelete = { deleteTarget = it }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete loan?") },
            text = { Text("Delete \"${target.name}\" and all its payments, schedule and reminders?") },
            confirmButton = {
                TextButton(onClick = {
                    LoanScheduler.cancelAll(context, db, target.id)
                    db.deleteLoan(target.id)
                    deleteTarget = null
                    refreshKey++
                    Toast.makeText(context, "🗑️ Loan deleted", Toast.LENGTH_SHORT).show()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LoanListContent(
    db: AppDatabase,
    settings: SettingsStore,
    refreshKey: Int,
    onBack: () -> Unit,
    onAddLoan: () -> Unit,
    onLoanClick: (Loan) -> Unit,
    onEdit: (Loan) -> Unit,
    onDelete: (Loan) -> Unit
) {
    val currency = settings.getCurrencySymbol()
    val active = remember(db, refreshKey) { db.getLoans(LoanStatus.ACTIVE) }
    val completed = remember(db, refreshKey) { db.getLoans(LoanStatus.COMPLETED) }
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OverlayHeader("🏦 Loan Tracker", onBack)

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            listOf("Current", "Completed").forEachIndexed { index, label ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) Color(0xFF667EEA)
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .background(Color(0xFF10B981))
                .clickable(onClick = onAddLoan)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("＋ ADD LOAN", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val loans = if (page == 0) active else completed
            if (loans.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (page == 0) "No active loans" else "No completed loans",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    loans.forEach { loan ->
                        LoanCard(
                            loan = loan,
                            currency = currency,
                            db = db,
                            onClick = { onLoanClick(loan) },
                            onEdit = { onEdit(loan) },
                            onDelete = { onDelete(loan) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanCard(loan: Loan, currency: String, db: AppDatabase, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val total = if (loan.originalAmount > 0) loan.originalAmount else 1.0
    val progress = ((loan.totalPaid - loan.totalInterestPaid).coerceAtLeast(0.0) / total).coerceIn(0.0, 1.0)
    val today = LocalDate.now()
    val nextDue = remember(loan.id) {
        db.getLoanSchedules(loan.id).firstOrNull {
            it.status != ScheduleStatus.PAID && it.paidAmount < it.expectedPayment
        }?.dueDate
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                loan.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (loan.status == LoanStatus.COMPLETED) {
                Text(
                    "✓ Completed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xFF059669), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            } else {
                val hint = statusHint(nextDue, today)
                if (hint != null) {
                    Text(
                        hint.first,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(hint.second, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                "✏️",
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable { onEdit() }
            )
            Text(
                "🗑️",
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable { onDelete() }
            )
        }
        if (loan.lender.isNotBlank()) {
            Text(
                loan.lender,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Row {
            CardStat("EMI", formatCurrency(currency, loan.monthlyPayment), Modifier.weight(1f))
            CardStat("Balance", formatCurrency(currency, loan.remainingBalance), Modifier.weight(1f))
            if (loan.status == LoanStatus.COMPLETED) {
                CardStat("Total paid", formatCurrency(currency, loan.totalPaid), Modifier.weight(1f))
            }
        }
        if (loan.status == LoanStatus.ACTIVE) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.toFloat())
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF667EEA))
                )
            }
            Text(
                "Repaid ${(progress * 100).toInt()}%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CardStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun statusHint(nextDue: String?, today: LocalDate): Pair<String, Color>? {
    val due = nextDue?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return when {
        today.isAfter(due) -> "OVERDUE" to Color(0xFFDC2626)
        today.isEqual(due) -> "DUE TODAY" to Color(0xFFD97706)
        today.isEqual(due.minusDays(1)) -> "DUE TOMORROW" to Color(0xFFD97706)
        else -> null
    }
}
