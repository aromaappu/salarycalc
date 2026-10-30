package com.example.salarycalc_trackincomeandexpense.ui

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.AutoEvents
import com.example.salarycalc_trackincomeandexpense.R
import com.example.salarycalc_trackincomeandexpense.SmsReceiver
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.LoanPaymentRecorder
import com.example.salarycalc_trackincomeandexpense.data.LoanScheduler
import com.example.salarycalc_trackincomeandexpense.data.LoanStatus
import com.example.salarycalc_trackincomeandexpense.data.ScheduleStatus
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.FormLabel
import com.example.salarycalc_trackincomeandexpense.ui.components.FormTextField
import com.example.salarycalc_trackincomeandexpense.ui.screens.AutoTransactionScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.BankStatementScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.ChartsScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.DashboardScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.ExpenseScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.FdTrackerScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.FeaturesScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.IncomeScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.LoanTrackerScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.NotesScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.PinLockScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.RemindersScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.SettingsScreen
import com.example.salarycalc_trackincomeandexpense.ui.screens.StatementScreen
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import com.example.salarycalc_trackincomeandexpense.ui.util.getTodayStr
import java.time.LocalDate

private data class NavItem(
    val label: String,
    val icon: ImageVector,
    val destination: Destination
)

private enum class Destination { HOME, INCOME, EXPENSE }

private enum class Overlay { FEATURES, CHARTS, REMINDERS, NOTES, AUTO_TX, BANK_STATEMENT, SETTINGS, STATEMENT, LOANS, FD }

@Composable
fun SalaryCalcApp(
    db: AppDatabase,
    settings: SettingsStore,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    openStatementRequest: Int = 0
) {
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var overlayStack: List<Overlay> by remember { mutableStateOf(listOf<Overlay>()) }
    val currentOverlay = overlayStack.lastOrNull()

    // Month-end notification taps open the Statement screen directly.
    androidx.compose.runtime.LaunchedEffect(openStatementRequest) {
        if (openStatementRequest > 0) {
            overlayStack = overlayStack + Overlay.STATEMENT
        }
    }
    var locked by remember { mutableStateOf(settings.isPinEnabled()) }
    var showFirstRunCurrency by remember { mutableStateOf(!settings.hasSeenWelcome()) }

    val navItems = listOf(
        NavItem("Home", ImageVector.vectorResource(R.drawable.ic_home), Destination.HOME),
        NavItem("Income", ImageVector.vectorResource(R.drawable.ic_income), Destination.INCOME),
        NavItem("Expense", ImageVector.vectorResource(R.drawable.ic_expense), Destination.EXPENSE)
    )

    val onBack = { overlayStack = overlayStack.dropLast(1) }

    if (locked) {
        PinLockScreen(settings = settings, onUnlock = { locked = false })
        return
    }

    // Pressing the system back button while an overlay (Charts, Settings, etc.)
    // is open simply pops back to the previous screen — never closes the app.
    BackHandler(enabled = currentOverlay != null) {
        overlayStack = overlayStack.dropLast(1)
    }

    // Number of transactions waiting for confirmation. Shown as a small
    // top-right reminder until every one is confirmed.
    val context = LocalContext.current
    val pendingConfirmations = remember(AutoEvents.refreshTick.value) {
        SmsReceiver().getPendingTransactionsPublic(context).size
    }

    // App-open reconciliation: re-arms loan due-date alarms, raises overdue
    // alerts, and pops a payment prompt when a loan payment is due/overdue.
    var pendingDueLoanId by remember { mutableStateOf<Long?>(null) }
    val loanDuePopupHandled = remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (loanDuePopupHandled.value) return@LaunchedEffect
        loanDuePopupHandled.value = true
        LoanScheduler.reconcileAll(context, db)
        val today = LocalDate.now()
        val dueLoan = db.getLoans(LoanStatus.ACTIVE).firstOrNull { loan ->
            db.getLoanSchedules(loan.id).firstOrNull {
                it.status != ScheduleStatus.PAID && it.paidAmount < it.expectedPayment
            }?.let { schedule ->
                runCatching { LocalDate.parse(schedule.dueDate) }.getOrDefault(today)
            }?.let { due -> !due.isAfter(today) } == true
        }
        if (dueLoan != null) {
            LoanScheduler.notifyOverdue(context, db, dueLoan.id)
            pendingDueLoanId = dueLoan.id
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = currentTab == index && currentOverlay == null,
                            onClick = {
                                overlayStack = emptyList()
                                currentTab = index
                            },
                            icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                // Overlays (Features, Settings, etc.) render inside the content
                // area so the bottom navigation stays visible — no full screen.
                when {
                    currentOverlay != null -> when (currentOverlay) {
                        Overlay.FEATURES -> FeaturesScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack,
                            onOpenCharts = { overlayStack = overlayStack + Overlay.CHARTS },
                            onOpenReminders = { overlayStack = overlayStack + Overlay.REMINDERS },
                            onOpenNotes = { overlayStack = overlayStack + Overlay.NOTES },
                            onOpenAutoTx = { overlayStack = overlayStack + Overlay.AUTO_TX },
                            onOpenBankStatement = { overlayStack = overlayStack + Overlay.BANK_STATEMENT },
                            onOpenStatement = { overlayStack = overlayStack + Overlay.STATEMENT },
                            onOpenLoans = { overlayStack = overlayStack + Overlay.LOANS },
                            onOpenFds = { overlayStack = overlayStack + Overlay.FD }
                        )
                        Overlay.CHARTS -> ChartsScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack
                        )
                        Overlay.REMINDERS -> RemindersScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack
                        )
                        Overlay.NOTES -> NotesScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack
                        )
                        Overlay.AUTO_TX -> AutoTransactionScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack
                        )
                        Overlay.BANK_STATEMENT -> BankStatementScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack
                        )
                        Overlay.STATEMENT -> StatementScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack
                        )
                        Overlay.LOANS -> LoanTrackerScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack
                        )
                        Overlay.FD -> FdTrackerScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack
                        )
                        Overlay.SETTINGS -> SettingsScreen(
                            db = db,
                            settings = settings,
                            onBack = onBack,
                            darkMode = darkMode,
                            onDarkModeChange = onDarkModeChange
                        )
                    }
                    else -> when (navItems[currentTab].destination) {
                        Destination.HOME -> DashboardScreen(
                            db,
                            settings,
                            onOpenFeatures = { overlayStack = listOf(Overlay.FEATURES) },
                            onOpenSettings = { overlayStack = listOf(Overlay.SETTINGS) }
                        )
                        Destination.INCOME -> IncomeScreen(db, settings)
                        Destination.EXPENSE -> ExpenseScreen(db, settings)
                    }
                }
            }
        }
        if (currentOverlay == null && pendingConfirmations > 0) {
            Text(
                text = "🔔 Confirmation waiting",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 6.dp, end = 12.dp)
                    .background(Color(0xFFF59E0B), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }

    if (showFirstRunCurrency) {
        FirstRunCurrencyDialog(
            settings = settings,
            onDismiss = {
                settings.setWelcomeSeen()
                showFirstRunCurrency = false
            }
        )
    }

    var paymentDialogLoanId by remember { mutableStateOf<Long?>(null) }

    pendingDueLoanId?.let { dueId ->
        val dueLoan = db.getLoan(dueId)
        if (dueLoan == null) {
            pendingDueLoanId = null
        } else {
            val pendingSchedule = remember(dueId) { LoanPaymentRecorder.nextUnpaid(db, dueId) }
            val defaultAmount = ((pendingSchedule?.expectedPayment ?: 0.0) -
                (pendingSchedule?.paidAmount ?: 0.0)).coerceAtLeast(0.0)
            var dueAmount by remember(dueId) { mutableStateOf(defaultAmount.toString()) }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingDueLoanId = null },
                title = { Text("💳 Loan payment due") },
                text = {
                    Column {
                        Text("${dueLoan.name} · ${dueLoan.lender.ifBlank { "Loan" }}")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "EMI ${formatCurrency(settings.getCurrencySymbol(), dueLoan.monthlyPayment)} · " +
                                "Balance ${formatCurrency(settings.getCurrencySymbol(), dueLoan.remainingBalance)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pendingSchedule != null) {
                            Text(
                                "Due ${formatLoanDate(pendingSchedule.dueDate)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        FormLabel("Amount paid (${settings.getCurrencySymbol()})")
                        FormTextField(
                            value = dueAmount,
                            onValueChange = { dueAmount = it },
                            placeholder = "Enter paid amount",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { pendingDueLoanId = null }) { Text("Not Paid") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val amt = dueAmount.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val result = LoanPaymentRecorder.record(
                            context = context,
                            db = db,
                            loanId = dueId,
                            amount = amt,
                            paymentDate = getTodayStr(),
                            method = "",
                            reference = "",
                            notes = ""
                        )
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        pendingDueLoanId = null
                    }) { Text("Confirm") }
                }
            )
        }
    }
}

@Composable
private fun FirstRunCurrencyDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥",
        "CNY" to "¥", "AUD" to "A$", "CAD" to "C$", "SAR" to "﷼", "AED" to "د.إ"
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💱 Select Currency") },
        text = {
            Column {
                options.forEach { (name, symbol) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                settings.setCurrencySymbol(symbol)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$symbol  $name",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun formatLoanDate(dateStr: String): String =
    runCatching {
        val d = LocalDate.parse(dateStr)
        "${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.year}"
    }.getOrDefault(dateStr)

@Composable
fun SectionPlaceholder(title: String) {    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
    }
}

