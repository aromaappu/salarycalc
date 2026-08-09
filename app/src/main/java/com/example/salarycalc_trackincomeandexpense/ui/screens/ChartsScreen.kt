package com.example.salarycalc_trackincomeandexpense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.AutoEvents
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.data.Transaction
import com.example.salarycalc_trackincomeandexpense.ui.components.FilterDropdown
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import com.example.salarycalc_trackincomeandexpense.ui.util.MONTHS
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import com.example.salarycalc_trackincomeandexpense.ui.util.getMonthKey
import com.example.salarycalc_trackincomeandexpense.ui.util.getTodayStr
import com.example.salarycalc_trackincomeandexpense.ui.util.getYesterdayStr
import com.example.salarycalc_trackincomeandexpense.ui.util.monthNameFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

private val PIE_COLORS = listOf(
    "#10b981", "#ef4444", "#667eea", "#f59e0b", "#8b5cf6",
    "#ec4899", "#06b6d4", "#84cc16", "#f97316", "#6366f1"
)

private const val BAR_MAX_H = 130

private data class ChartsData(
    val categories: List<String>,
    val currInc: Double,
    val currExp: Double,
    val prevInc: Double,
    val prevExp: Double,
    val filteredExpenses: List<Transaction>
)

@Composable
fun ChartsScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val currency = settings.getCurrencySymbol()

    var filterYear by remember { mutableStateOf("All Years") }
    var filterMonth by remember { mutableStateOf("All Months") }
    var filterCategory by remember { mutableStateOf("All Categories") }

    var chartsData by remember { mutableStateOf<ChartsData?>(null) }

    LaunchedEffect(db, filterYear, filterMonth, filterCategory, AutoEvents.refreshTick.value) {
        val data = withContext(Dispatchers.IO) {
            val now = Calendar.getInstance()
            val thisPrefix = "%04d-%02d".format(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
            val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            val prevPrefix = "%04d-%02d".format(prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH) + 1)

            val all = db.getAllTransactions()
            val cI = all.filter { it.type == AppDatabase.TYPE_INCOME && it.date.startsWith(thisPrefix) }.sumOf { it.amount }
            val cE = all.filter { it.type == AppDatabase.TYPE_EXPENSE && it.date.startsWith(thisPrefix) }.sumOf { it.amount }
            val pI = all.filter { it.type == AppDatabase.TYPE_INCOME && it.date.startsWith(prevPrefix) }.sumOf { it.amount }
            val pE = all.filter { it.type == AppDatabase.TYPE_EXPENSE && it.date.startsWith(prevPrefix) }.sumOf { it.amount }

            ChartsData(
                categories = db.getExpenseCategories(),
                currInc = cI, currExp = cE,
                prevInc = pI, prevExp = pE,
                filteredExpenses = filterExpensesForChart(all, filterYear, filterMonth, filterCategory)
            )
        }
        chartsData = data
    }

    val data = chartsData ?: return

    val categoryOptions = listOf("All Categories") + data.categories
    val now = Calendar.getInstance()
    val currentYear = now.get(Calendar.YEAR)
    val yearOptions = listOf("All Years") + (currentYear + 2 downTo currentYear - 5).map { it.toString() }
    val monthOptions = listOf("All Months", "Yesterday", "Today") + MONTHS

    val maxVal1 = maxOf(data.prevInc, data.currInc, data.prevExp, data.currExp, 1.0)
    val prevBalance = data.prevInc - data.prevExp
    val currBalance = data.currInc - data.currExp
    val maxBal = maxOf(kotlin.math.abs(prevBalance), kotlin.math.abs(currBalance), 1.0)

    val monthTotals = data.filteredExpenses.groupBy { it.date.substring(0, 7) }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .toSortedMap()
    val maxExp = monthTotals.values.maxOrNull() ?: 0.0
    val catTotals = data.filteredExpenses.groupBy { it.name }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }
    val totalExp = data.filteredExpenses.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        OverlayHeader("📊 Charts & Graphs", onBack)
        Column(Modifier.padding(10.dp)) {

            Text(
                "📈 Income vs Expense — Prev vs Current Month",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                BarGroup(currency, data.prevInc, "Prev Inc", Color(0xFF6EE7B7), maxVal1)
                BarGroup(currency, data.currInc, "Curr Inc", Color(0xFF10B981), maxVal1)
                BarGroup(currency, data.prevExp, "Prev Exp", Color(0xFFFCA5A5), maxVal1)
                BarGroup(currency, data.currExp, "Curr Exp", Color(0xFFEF4444), maxVal1)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "💰 Net Balance — Prev vs Current",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                BarGroup(currency, prevBalance, "Prev Bal", Color(0xFF667EEA), maxBal)
                BarGroup(currency, currBalance, "Curr Bal", Color(0xFF8B5CF6), maxBal)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "📉 Expense by Month",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterDropdown("📅 Year", yearOptions, filterYear, { filterYear = it }, Modifier.weight(1f))
                    FilterDropdown("🗓️ Month", monthOptions, filterMonth, { filterMonth = it }, Modifier.weight(1f))
                    FilterDropdown("📌 Category", categoryOptions, filterCategory, { filterCategory = it }, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp)) {
                if (monthTotals.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("No expense data for selected filter", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        monthTotals.forEach { (key, value) ->
                            val label = try { MONTHS[key.substring(5).toInt() - 1].take(3) } catch (_: Exception) { key }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
                                Text(formatCurrency(currency, value), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.width(40.dp).height(barHeight(value, maxExp)).background(Color(0xFF8B5CF6)))
                                Spacer(Modifier.height(4.dp))
                                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("📌 Category Breakdown", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (catTotals.isEmpty()) {
                    Text(
                        "No expenses for selected filter", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    catTotals.forEachIndexed { i, (cat, amt) ->
                        val pct = if (totalExp > 0) kotlin.math.round(amt / totalExp * 100).toInt() else 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(12.dp).height(12.dp).background(hexColor(PIE_COLORS[i % PIE_COLORS.size])))
                            Spacer(Modifier.width(6.dp))
                            Text("$cat: ${formatCurrency(currency, amt)} ($pct%)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarGroup(currency: String, value: Double, label: String, color: Color, maxVal: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCurrency(currency, value), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(60.dp).height(barHeight(value, maxVal)).background(color))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun barHeight(value: Double, maxVal: Double): androidx.compose.ui.unit.Dp {
    val h = if (maxVal > 0) ((kotlin.math.abs(value) / maxVal) * BAR_MAX_H).coerceAtLeast(5.0) else 5.0
    return androidx.compose.ui.unit.Dp(h.toFloat())
}

private fun hexColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

private fun filterExpensesForChart(
    all: List<Transaction>,
    year: String,
    month: String,
    category: String
): List<Transaction> {
    return all.filter { t ->
        if (t.type != AppDatabase.TYPE_EXPENSE) return@filter false
        val parts = t.date.split("-")
        val txYear = parts.getOrNull(0)?.toIntOrNull()
        val txMonth = parts.getOrNull(1)?.toIntOrNull()

        val yearOk = if (year != "All Years") txYear == year.toIntOrNull() else true
        val monthOk = when {
            month == "All Months" -> true
            month == "Today" -> t.date == getTodayStr()
            month == "Yesterday" -> t.date == getYesterdayStr()
            else -> {
                val idx = MONTHS.indexOf(month)
                idx >= 0 && txMonth != null && txMonth - 1 == idx
            }
        }
        val categoryOk = if (category != "All Categories") t.name.contains(category, ignoreCase = true) else true
        yearOk && monthOk && categoryOk
    }
}
