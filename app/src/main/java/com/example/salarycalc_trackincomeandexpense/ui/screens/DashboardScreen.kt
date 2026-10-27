package com.example.salarycalc_trackincomeandexpense.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.R
import com.example.salarycalc_trackincomeandexpense.AutoEvents
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.TransactionRow
import com.example.salarycalc_trackincomeandexpense.ui.theme.ExpenseRed
import com.example.salarycalc_trackincomeandexpense.ui.theme.ExpenseRedBorder
import com.example.salarycalc_trackincomeandexpense.ui.theme.FeatureIconBg
import com.example.salarycalc_trackincomeandexpense.ui.theme.GradientEnd
import com.example.salarycalc_trackincomeandexpense.ui.theme.GradientStart
import com.example.salarycalc_trackincomeandexpense.ui.theme.IncomeGreen
import com.example.salarycalc_trackincomeandexpense.ui.theme.IncomeGreenBorder
import com.example.salarycalc_trackincomeandexpense.ui.theme.MonthlyCashGradientEnd
import com.example.salarycalc_trackincomeandexpense.ui.theme.MonthlyCashGradientMid
import com.example.salarycalc_trackincomeandexpense.ui.theme.MonthlyCashGradientStart
import com.example.salarycalc_trackincomeandexpense.ui.theme.SettingsIconBg
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import com.example.salarycalc_trackincomeandexpense.ui.util.getRecentTransactions
import com.example.salarycalc_trackincomeandexpense.ui.util.getTodayStr
import com.example.salarycalc_trackincomeandexpense.ui.util.lifetimeBalance
import com.example.salarycalc_trackincomeandexpense.ui.util.monthTotalsForTransactions
import com.example.salarycalc_trackincomeandexpense.ui.util.todayExpenseForTransactions

@Composable
fun DashboardScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onOpenFeatures: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val currency = settings.getCurrencySymbol()
    val today = getTodayStr()
    var balanceVisible by remember { mutableStateOf(true) }
    var dashboardData by remember { mutableStateOf<DashboardData?>(null) }

    LaunchedEffect(db, settings, AutoEvents.refreshTick.value) {
        val data = withContext(Dispatchers.IO) {
            val transactions = db.getAllTransactions()
            val ct = monthTotalsForTransactions(transactions, today)
            DashboardData(
                monthIncome = ct.income,
                monthExpense = ct.expense,
                balance = db.getLatestClosingBalance() ?: lifetimeBalance(db),
                todayExpense = todayExpenseForTransactions(transactions, today),
                today = today,
                recent = getRecentTransactions(db, 7)
            )
        }
        dashboardData = data
    }

    val data = dashboardData ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        DashboardHeader(
            balance = data.balance,
            balanceVisible = balanceVisible,
            onToggleBalance = { balanceVisible = !balanceVisible },
            currency = currency
        )
        MonthlyCashCard(data, currency, balanceVisible)
        TodaySpendingCard(data, currency, balanceVisible)
        IncomeExpenseCards(data, currency, balanceVisible)
        ActionBoxes(onOpenFeatures = onOpenFeatures, onOpenSettings = onOpenSettings)
        RecentActivity(data, currency)
    }
}

private data class DashboardData(
    val monthIncome: Double,
    val monthExpense: Double,
    val balance: Double,
    val todayExpense: Double,
    val today: String,
    val recent: List<com.example.salarycalc_trackincomeandexpense.data.Transaction>
)

@Composable
private fun DashboardHeader(
    balance: Double,
    balanceVisible: Boolean,
    onToggleBalance: () -> Unit,
    currency: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "💰 SalaryCalc",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Track money ✦ Grow wealth",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(Color(0x33FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x1AFFFFFF))
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NET BALANCE",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    AnimatedMaskedAmount(formatCurrency(currency, balance), balanceVisible,
                        Color.White, 34.sp, FontWeight.Black)
                }
                androidx.compose.material3.IconButton(
                    onClick = onToggleBalance,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = if (balanceVisible) {
                            ImageVector.vectorResource(R.drawable.ic_eye_open)
                        } else {
                            ImageVector.vectorResource(R.drawable.ic_eye_closed)
                        },
                        contentDescription = if (balanceVisible) "Hide balance" else "Show balance",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyCashCard(data: DashboardData, currency: String, balanceVisible: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(MonthlyCashGradientStart, MonthlyCashGradientMid, MonthlyCashGradientEnd)))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "💰 This Month Cash",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Income − Expense",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }
        AnimatedMaskedAmount(formatCurrency(currency, data.monthIncome - data.monthExpense),
            balanceVisible, Color.White, 20.sp, FontWeight.ExtraBold)
    }
}

@Composable
private fun TodaySpendingCard(data: DashboardData, currency: String, balanceVisible: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "📅 Today's Spending",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = data.today,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedMaskedAmount(formatCurrency(currency, data.todayExpense), balanceVisible,
            ExpenseRed, 18.sp, FontWeight.ExtraBold)
    }
}

@Composable
private fun IncomeExpenseCards(data: DashboardData, currency: String, balanceVisible: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            title = "📈 This Month Income",
            amount = formatCurrency(currency, data.monthIncome),
            visible = balanceVisible,
            amountColor = IncomeGreen,
            borderColor = IncomeGreenBorder,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "📉 This Month Expense",
            amount = formatCurrency(currency, data.monthExpense),
            visible = balanceVisible,
            amountColor = ExpenseRed,
            borderColor = ExpenseRedBorder,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    amount: String,
    visible: Boolean,
    amountColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            AnimatedMaskedAmount(amount, visible, amountColor, 20.sp, FontWeight.ExtraBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(borderColor)
                .height(3.dp)
        )
    }
}

/** Fade between actual values and four privacy stars when the eye is tapped. */
@Composable
private fun AnimatedMaskedAmount(
    amount: String,
    visible: Boolean,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight
) {
    Crossfade(targetState = visible, label = "Amount privacy") { reveal ->
        Text(
            text = if (reveal) amount else "✦ ✦ ✦ ✦",
            color = color, fontSize = fontSize, fontWeight = fontWeight,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionBoxes(
    onOpenFeatures: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ActionBox(
            icon = "⚡",
            iconBg = FeatureIconBg,
            title = "Explore Features",
            description = "6 Android Power-Utilities",
            modifier = Modifier.weight(1f),
            onClick = onOpenFeatures
        )
        ActionBox(
            icon = "⚙️",
            iconBg = SettingsIconBg,
            title = "Main Settings",
            description = "Dark mode, backup, database reset",
            modifier = Modifier.weight(1f),
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun ActionBox(
    icon: String,
    iconBg: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 20.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "›",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = description,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun RecentActivity(
    data: DashboardData,
    currency: String
) {
    Text(
        text = "📋 Recent activity",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp)
    )
    Spacer(Modifier.height(8.dp))
    if (data.recent.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 35.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✨ No transactions yet.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column {
            data.recent.forEach { tx ->
                TransactionRow(transaction = tx, currencySymbol = currency)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
