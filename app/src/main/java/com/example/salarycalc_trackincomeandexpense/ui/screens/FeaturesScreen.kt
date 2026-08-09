package com.example.salarycalc_trackincomeandexpense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.AutoEntryDialog
import com.example.salarycalc_trackincomeandexpense.ui.components.FeatureListItem
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader

@Composable
fun FeaturesScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenAutoTx: () -> Unit,
    onOpenBankStatement: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenFds: () -> Unit
) {
    var showAutoIncome by remember { mutableStateOf(false) }
    var showAutoExpense by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        OverlayHeader("⚡ Features", onBack)
        Column(Modifier.padding(10.dp)) {
            FeatureListItem(
                icon = "💰",
                iconBg = Color(0xFF059669),
                title = "Auto Income",
                subtitle = "Set recurring monthly income",
                onClick = { showAutoIncome = true }
            )
            FeatureListItem(
                icon = "📉",
                iconBg = Color(0xFFDC2626),
                title = "Auto Expense",
                subtitle = "Set recurring monthly expenses",
                onClick = { showAutoExpense = true }
            )
            FeatureListItem(
                icon = "📊",
                iconBg = Color(0xFF764BA2),
                title = "Charts & Graphs",
                subtitle = "Visualize your spending",
                onClick = onOpenCharts
            )
            FeatureListItem(
                icon = "🔔",
                iconBg = Color(0xFFD97706),
                title = "Bill Reminders",
                subtitle = "Never miss a payment",
                onClick = onOpenReminders
            )
            FeatureListItem(
                icon = "📝",
                iconBg = Color(0xFF2563EB),
                title = "Notes",
                subtitle = "Keep your thoughts organized",
                onClick = onOpenNotes
            )
            FeatureListItem(
                icon = "📱",
                iconBg = Color(0xFFEA580C),
                title = "Auto Transaction",
                subtitle = "Track bank SMS automatically",
                onClick = onOpenAutoTx
            )
            FeatureListItem(
                icon = "📄",
                iconBg = Color(0xFF0EA5E9),
                title = "Bank Statement",
                subtitle = "Upload PDF & auto-add transactions",
                onClick = onOpenBankStatement
            )
            FeatureListItem(
                icon = "🧾",
                iconBg = Color(0xFF059669),
                title = "Statement",
                subtitle = "Download a date-wise PDF statement",
                onClick = onOpenStatement
            )
            FeatureListItem(
                icon = "🏦",
                iconBg = Color(0xFF4F46E5),
                title = "Loan Tracker",
                subtitle = "Track loans, EMIs, payments & reminders",
                onClick = onOpenLoans
            )
            FeatureListItem(
                icon = "📈",
                iconBg = Color(0xFF0D9488),
                title = "FD / Fixed Deposit",
                subtitle = "Track fixed returns with daily growth",
                onClick = onOpenFds
            )
        }
    }

    if (showAutoIncome) {
        AutoEntryDialog(
            isIncome = true,
            db = db,
            settings = settings,
            onDismiss = { showAutoIncome = false }
        )
    }

    if (showAutoExpense) {
        AutoEntryDialog(
            isIncome = false,
            db = db,
            settings = settings,
            onDismiss = { showAutoExpense = false }
        )
    }
}
