package com.example.salarycalc_trackincomeandexpense.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.InterestType
import com.example.salarycalc_trackincomeandexpense.data.Loan
import com.example.salarycalc_trackincomeandexpense.data.LoanCalculationEngine
import com.example.salarycalc_trackincomeandexpense.data.LoanScheduler
import com.example.salarycalc_trackincomeandexpense.data.ScheduleStatus
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.FormLabel
import com.example.salarycalc_trackincomeandexpense.ui.components.FormTextField
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import com.example.salarycalc_trackincomeandexpense.ui.util.getTodayStr
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Composable
fun LoanFormScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    editLoan: Loan? = null
) {
    val context = LocalContext.current
    val currency = settings.getCurrencySymbol()

    var name by remember { mutableStateOf(editLoan?.name ?: "") }
    var lender by remember { mutableStateOf(editLoan?.lender ?: "") }
    var amount by remember { mutableStateOf(editLoan?.originalAmount?.let { trimNum(it) } ?: "") }
    var rate by remember { mutableStateOf(editLoan?.interestRate?.let { trimNum(it) } ?: "") }
    var interestType by remember { mutableStateOf(editLoan?.interestType ?: InterestType.FLAT) }
    var startDate by remember { mutableStateOf(editLoan?.startDate ?: getTodayStr()) }
    var firstDueDate by remember { mutableStateOf(editLoan?.firstDueDate ?: getTodayStr()) }
    var paymentDay by remember { mutableStateOf(editLoan?.paymentDay?.toString() ?: "") }
    var duration by remember { mutableStateOf(editLoan?.durationMonths?.toString() ?: "") }
    var monthlyPayment by remember { mutableStateOf(editLoan?.monthlyPayment?.let { trimNum(it) } ?: "") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun autoCalc() {
        val p = amount.toDoubleOrNull() ?: return
        val r = rate.toDoubleOrNull() ?: return
        val n = duration.toIntOrNull() ?: return
        if (p <= 0 || r < 0 || n < 1) {
            toast("Fill amount, rate and duration first")
            return
        }
        val emi = suggestedEmi(p, r, n, interestType)
        monthlyPayment = emi.toString()
        toast("Suggested EMI: $currency ${formatAmount(emi)}")
    }

    fun save() {
        val n = name.trim()
        val p = amount.toDoubleOrNull()
        val r = rate.toDoubleOrNull()
        val d = duration.toIntOrNull()
        val emi = monthlyPayment.toDoubleOrNull()
        val payDay = paymentDay.toIntOrNull() ?: runCatching {
            LocalDate.parse(firstDueDate).dayOfMonth
        }.getOrDefault(1)

        if (n.isEmpty() || p == null || r == null || d == null || emi == null) {
            toast("Fill all fields")
            return
        }
        if (payDay !in 1..31) {
            toast("Payment day must be 1-31")
            return
        }

        val spec = LoanCalculationEngine.LoanSpec(
            principal = BigDecimal(p),
            annualRate = BigDecimal(r),
            durationMonths = d,
            monthlyPayment = BigDecimal(emi),
            interestType = interestType,
            firstDueDate = runCatching { LocalDate.parse(firstDueDate) }.getOrElse {
                toast("Invalid first due date"); return
            },
            paymentDay = payDay
        )

        val errors = LoanCalculationEngine.validate(spec)
        if (errors.isNotEmpty()) {
            toast(errors.first())
            return
        }

        val editId = editLoan?.id
        if (editId != null) {
            val hasPayments = db.getLoanPayments(editId).isNotEmpty()
            if (hasPayments) {
                db.updateLoanNameLender(editId, n, lender.trim())
                toast("✅ Updated name/lender (payments recorded — other fields locked)")
                onSaved()
                return
            }
            db.updateLoanDetails(
                id = editId,
                name = n,
                lender = lender.trim(),
                originalAmount = p,
                interestRate = r,
                interestType = interestType,
                startDate = startDate,
                firstDueDate = firstDueDate,
                paymentDay = payDay,
                monthlyPayment = emi,
                durationMonths = d
            )
            db.clearLoanSchedules(editId)
            val schedule = LoanCalculationEngine.generateSchedule(spec)
            schedule.installments.forEach { inst ->
                db.addLoanSchedule(
                    editId,
                    inst.paymentNumber,
                    inst.dueDate.toString(),
                    inst.expectedPayment.toDouble(),
                    inst.principal.toDouble(),
                    inst.interest.toDouble(),
                    ScheduleStatus.UPCOMING
                )
            }
            val last = schedule.installments.lastOrNull()
            if (last != null) {
                db.setLoanExpectedCompletion(editId, last.dueDate.toString())
            }
            LoanScheduler.scheduleNext(context, db, editId)
            toast("✅ Loan updated: $n")
            onSaved()
            return
        }

        val loanId = db.addLoan(
            name = n,
            lender = lender.trim(),
            originalAmount = p,
            interestRate = r,
            interestType = interestType,
            startDate = startDate,
            firstDueDate = firstDueDate,
            paymentDay = payDay,
            monthlyPayment = emi,
            durationMonths = d,
            remainingBalance = p,
            expectedCompletionDate = null
        )

        val schedule = LoanCalculationEngine.generateSchedule(spec)
        db.clearLoanSchedules(loanId)
        schedule.installments.forEach { inst ->
            db.addLoanSchedule(
                loanId,
                inst.paymentNumber,
                inst.dueDate.toString(),
                inst.expectedPayment.toDouble(),
                inst.principal.toDouble(),
                inst.interest.toDouble(),
                ScheduleStatus.UPCOMING
            )
        }
        val last = schedule.installments.lastOrNull()
        if (last != null) {
            db.setLoanExpectedCompletion(loanId, last.dueDate.toString())
        }
        LoanScheduler.scheduleNext(context, db, loanId)
        toast("✅ Loan saved: $n")
        onSaved()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        OverlayHeader(if (editLoan != null) "✏️ Edit Loan" else "＋ Add Loan", onBack)
        Column(Modifier.padding(10.dp)) {

            FormLabel("Loan name")
            FormTextField(value = name, onValueChange = { name = it }, placeholder = "e.g., Car Loan")

            Spacer(Modifier.height(10.dp))
            FormLabel("Lender (optional)")
            FormTextField(value = lender, onValueChange = { lender = it }, placeholder = "e.g., HDFC Bank")

            Spacer(Modifier.height(10.dp))
            FormLabel("Loan amount ($currency)")
            FormTextField(
                value = amount,
                onValueChange = { amount = it },
                placeholder = "e.g., 500000",
                keyboardType = KeyboardType.Number
            )

            Spacer(Modifier.height(10.dp))
            FormLabel("Interest rate (% per year)")
            FormTextField(
                value = rate,
                onValueChange = { rate = it },
                placeholder = "e.g., 12",
                keyboardType = KeyboardType.Number
            )

            Spacer(Modifier.height(10.dp))
            FormLabel("Interest type")
            Row(Modifier.fillMaxWidth()) {
                listOf(InterestType.FLAT to "Flat", InterestType.REDUCING to "Reducing").forEach { (type, label) ->
                    val selected = interestType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF667EEA) else MaterialTheme.colorScheme.surface)
                            .clickable { interestType = type }
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

            Spacer(Modifier.height(10.dp))
            FormLabel("Start date")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showStartPicker = true }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📅 $startDate", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(10.dp))
            FormLabel("First due date")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showDuePicker = true }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📅 $firstDueDate", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(10.dp))
            FormLabel("Duration (months)")
            FormTextField(
                value = duration,
                onValueChange = { duration = it },
                placeholder = "e.g., 24",
                keyboardType = KeyboardType.Number
            )

            Spacer(Modifier.height(10.dp))
            FormLabel("Payment day (1-31, default = first due day)")
            FormTextField(
                value = paymentDay,
                onValueChange = { paymentDay = it },
                placeholder = "e.g., 15",
                keyboardType = KeyboardType.Number
            )

            Spacer(Modifier.height(10.dp))
            FormLabel("Monthly payment ($currency)")
            FormTextField(
                value = monthlyPayment,
                onValueChange = { monthlyPayment = it },
                placeholder = "e.g., 23500",
                keyboardType = KeyboardType.Number
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2563EB))
                    .clickable { autoCalc() }
                    .padding(vertical = 11.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("⚡ Auto-calculate EMI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10B981))
                    .clickable { save() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("✅ Save Loan", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showStartPicker) {
        DatePickerDialog(
            initialDate = startDate,
            onDismiss = { showStartPicker = false },
            onDateSelected = { startDate = it; showStartPicker = false }
        )
    }
    if (showDuePicker) {
        DatePickerDialog(
            initialDate = firstDueDate,
            onDismiss = { showDuePicker = false },
            onDateSelected = { firstDueDate = it; showDuePicker = false }
        )
    }
}

/** Suggests an EMI: flat = (P + total interest)/n, reducing = annuity formula. */
private fun suggestedEmi(p: Double, r: Double, n: Int, type: InterestType): Long {
    return when (type) {
        InterestType.FLAT -> {
            val monthly = p * (r / 100.0) / 12.0
            val total = p + monthly * n
            ceilToNearest10(total / n)
        }
        InterestType.REDUCING -> {
            val i = (r / 100.0) / 12.0
            if (i <= 0.0) return ceilToNearest10(p / n)
            val factor = Math.pow(1 + i, n.toDouble())
            ceilToNearest10(p * i * factor / (factor - 1))
        }
    }
}

private fun ceilToNearest10(v: Double): Long {
    val rounded = Math.ceil(v / 10.0).toLong() * 10
    return rounded.coerceAtLeast(10)
}

private fun formatAmount(v: Long): String =
    BigDecimal(v).setScale(0, RoundingMode.UNNECESSARY).toBigInteger().toString()

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
