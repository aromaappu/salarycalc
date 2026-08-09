package com.example.salarycalc_trackincomeandexpense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.Transaction
import com.example.salarycalc_trackincomeandexpense.ui.theme.ExpenseIconBg
import com.example.salarycalc_trackincomeandexpense.ui.theme.ExpenseIconFg
import com.example.salarycalc_trackincomeandexpense.ui.theme.ExpenseRed
import com.example.salarycalc_trackincomeandexpense.ui.theme.IncomeGreen
import com.example.salarycalc_trackincomeandexpense.ui.theme.IncomeIconBg
import com.example.salarycalc_trackincomeandexpense.ui.theme.IncomeIconFg
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import com.example.salarycalc_trackincomeandexpense.ui.util.transactionDisplayName

@Composable
fun TransactionRow(
    transaction: Transaction,
    currencySymbol: String,
    showActions: Boolean = false,
    onDownloadReceipt: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isIncome = transaction.type == AppDatabase.TYPE_INCOME
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (isIncome) IncomeIconBg else ExpenseIconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isIncome) "↑" else "↓",
                color = if (isIncome) IncomeIconFg else ExpenseIconFg,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = transactionDisplayName(transaction),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = transaction.date,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (isIncome) "+" else "-"} ${formatCurrency(currencySymbol, transaction.amount)}",
                color = if (isIncome) IncomeGreen else ExpenseRed,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (showActions) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (onDownloadReceipt != null) {
                        Text(
                            "📥",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                .clickable { onDownloadReceipt() }
                        )
                    }
                    if (onEdit != null) {
                        Text(
                            "✏️",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                .clickable { onEdit() }
                        )
                    }
                    if (onDelete != null) {
                        Text(
                            "🗑️",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                .clickable { onDelete() }
                        )
                    }
                }
            }
        }
    }
}
