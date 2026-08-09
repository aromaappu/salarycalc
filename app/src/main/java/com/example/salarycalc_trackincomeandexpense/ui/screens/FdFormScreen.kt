package com.example.salarycalc_trackincomeandexpense.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.Fd
import com.example.salarycalc_trackincomeandexpense.data.FdMath
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.FormLabel
import com.example.salarycalc_trackincomeandexpense.ui.components.FormTextField
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import com.example.salarycalc_trackincomeandexpense.ui.util.getTodayStr
import java.time.LocalDate

@Composable
fun FdFormScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    editFd: Fd? = null
) {
    val context = LocalContext.current
    val currency = settings.getCurrencySymbol()

    var name by remember { mutableStateOf(editFd?.name ?: "") }
    var amount by remember { mutableStateOf(editFd?.principal?.let { trimNum(it) } ?: "") }
    var rate by remember { mutableStateOf(editFd?.interestRate?.let { trimNum(it) } ?: "") }
    var tenure by remember { mutableStateOf(editFd?.tenureYears?.let { trimNum(it) } ?: "") }
    var startDate by remember { mutableStateOf(editFd?.startDate ?: getTodayStr()) }
    var showStartPicker by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun save() {
        val n = name.trim()
        val p = amount.toDoubleOrNull()
        val r = rate.toDoubleOrNull()
        val t = tenure.toDoubleOrNull()
        if (n.isEmpty() || p == null || r == null || t == null) {
            toast("Fill all fields")
            return
        }
        if (p <= 0) {
            toast("Amount must be greater than 0")
            return
        }
        if (r < 0) {
            toast("Interest rate cannot be negative")
            return
        }
        if (t <= 0) {
            toast("Tenure must be greater than 0")
            return
        }
        val start = runCatching { LocalDate.parse(startDate) }.getOrElse {
            toast("Invalid start date"); return
        }

        val editId = editFd?.id
        if (editId != null) {
            db.updateFdDetails(editId, n, p, r, t, start.toString())
            toast("✅ FD updated: $n")
        } else {
            db.addFd(n, p, r, t, start.toString())
            toast("✅ FD saved: $n")
        }
        onSaved()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        OverlayHeader(if (editFd != null) "✏️ Edit FD" else "＋ Add FD", onBack)
        Column(Modifier.padding(10.dp)) {

            FormLabel("FD name")
            FormTextField(value = name, onValueChange = { name = it }, placeholder = "e.g., SBI FD 2026")

            Spacer(Modifier.height(10.dp))
            FormLabel("Amount ($currency)")
            FormTextField(
                value = amount,
                onValueChange = { amount = it },
                placeholder = "e.g., 100000",
                keyboardType = KeyboardType.Number
            )

            Spacer(Modifier.height(10.dp))
            FormLabel("Interest rate (% per year)")
            FormTextField(
                value = rate,
                onValueChange = { rate = it },
                placeholder = "e.g., 7.5",
                keyboardType = KeyboardType.Number
            )

            Spacer(Modifier.height(10.dp))
            FormLabel("Tenure (years)")
            FormTextField(
                value = tenure,
                onValueChange = { tenure = it },
                placeholder = "e.g., 3 or 1.5",
                keyboardType = KeyboardType.Number
            )

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

            Spacer(Modifier.height(14.dp))
            FdPreview(amount = amount, rate = rate, tenure = tenure, startDate = startDate, currency = currency)

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10B981))
                    .clickable { save() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("✅ Save FD", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
}

@Composable
private fun FdPreview(amount: String, rate: String, tenure: String, startDate: String, currency: String) {
    val p = amount.toDoubleOrNull()
    val r = rate.toDoubleOrNull()
    val t = tenure.toDoubleOrNull()
    if (p == null || p <= 0 || r == null || r < 0 || t == null || t <= 0) return
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return

    val maturity = FdMath.maturityValue(p, r, t)
    val days = FdMath.totalDays(t)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D9488).copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text("📈 Maturity preview", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Maturity value: ${formatCurrency(currency, maturity)}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0D9488)
        )
        Text(
            "Total gain: ${formatCurrency(currency, maturity - p)} · $days days",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
