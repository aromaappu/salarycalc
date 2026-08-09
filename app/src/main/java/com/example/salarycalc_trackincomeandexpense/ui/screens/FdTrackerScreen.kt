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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.Fd
import com.example.salarycalc_trackincomeandexpense.data.FdMath
import com.example.salarycalc_trackincomeandexpense.data.FdStatus
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import com.example.salarycalc_trackincomeandexpense.ui.util.formatCurrency
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface FdNav {
    object List : FdNav
    object Form : FdNav
    data class Edit(val fdId: Long) : FdNav
}

@Composable
fun FdTrackerScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    var nav by remember { mutableStateOf<FdNav>(FdNav.List) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<Fd?>(null) }
    val context = LocalContext.current

    when (val current = nav) {
        is FdNav.Form -> FdFormScreen(
            db = db,
            settings = settings,
            onBack = { nav = FdNav.List },
            onSaved = { nav = FdNav.List; refreshKey++ }
        )
        is FdNav.Edit -> FdFormScreen(
            db = db,
            settings = settings,
            onBack = { nav = FdNav.List },
            onSaved = { nav = FdNav.List; refreshKey++ },
            editFd = db.getFd(current.fdId)
        )
        is FdNav.List -> FdListContent(
            db = db,
            settings = settings,
            refreshKey = refreshKey,
            onBack = onBack,
            onAddFd = { nav = FdNav.Form },
            onEdit = { nav = FdNav.Edit(it.id) },
            onDelete = { deleteTarget = it }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete FD?") },
            text = { Text("Delete \"${target.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    db.deleteFd(target.id)
                    deleteTarget = null
                    refreshKey++
                    Toast.makeText(context, "🗑️ FD deleted", Toast.LENGTH_SHORT).show()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FdListContent(
    db: AppDatabase,
    settings: SettingsStore,
    refreshKey: Int,
    onBack: () -> Unit,
    onAddFd: () -> Unit,
    onEdit: (Fd) -> Unit,
    onDelete: (Fd) -> Unit
) {
    val currency = settings.getCurrencySymbol()
    var today by remember { mutableStateOf(LocalDate.now()) }
    var autoCompleteTick by remember { mutableIntStateOf(0) }

    // Roll today over at midnight so the Current Value grows daily on screen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            val now = LocalDate.now()
            if (now != today) today = now
        }
    }

    // Auto-move matured FDs to Completed with the gain locked in.
    LaunchedEffect(db, refreshKey, today) {
        var changed = false
        db.getAllFds().filter { it.status == FdStatus.ACTIVE }.forEach { fd ->
            val start = runCatching { LocalDate.parse(fd.startDate) }.getOrElse { return@forEach }
            if (FdMath.isMatured(start, fd.tenureYears, today)) {
                val gain = FdMath.maturityValue(fd.principal, fd.interestRate, fd.tenureYears) - fd.principal
                db.markFdCompleted(fd.id, gain.coerceAtLeast(0.0))
                changed = true
            }
        }
        if (changed) autoCompleteTick++
    }

    val active = remember(db, refreshKey, autoCompleteTick) { db.getFds(FdStatus.ACTIVE) }
    val completed = remember(db, refreshKey, autoCompleteTick) { db.getFds(FdStatus.COMPLETED) }
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    val invested = active.sumOf { it.principal }
    val currentValue = active.sumOf {
        FdMath.currentValue(
            it.principal, it.interestRate,
            runCatching { LocalDate.parse(it.startDate) }.getOrDefault(today),
            today, it.tenureYears
        )
    }
    val totalGained = completed.sumOf { it.completedGain }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OverlayHeader("📈 FD Tracker", onBack)

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            listOf("Current", "Completed").forEachIndexed { index, label ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) Color(0xFF0D9488)
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
                .clickable(onClick = onAddFd)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("＋ ADD FD", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
            if (pagerState.currentPage == 0) {
                FdStatBox("Current Value", formatCurrency(currency, currentValue), Color(0xFF10B981), Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FdStatBox("Total Gain", formatCurrency(currency, (currentValue - invested).coerceAtLeast(0.0)), Color(0xFF0D9488), Modifier.weight(1f))
            } else {
                FdStatBox("Completed FDs", completed.size.toString(), Color(0xFF0D9488), Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FdStatBox("Total Gained", formatCurrency(currency, totalGained), Color(0xFFD97706), Modifier.weight(1f))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val fds = if (page == 0) active else completed
            if (fds.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (page == 0) "No active FDs" else "No completed FDs",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fds.forEach { fd ->
                        FdCard(
                            fd = fd,
                            currency = currency,
                            today = today,
                            onEdit = { onEdit(fd) },
                            onDelete = { onDelete(fd) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FdCard(
    fd: Fd,
    currency: String,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                fd.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (fd.status == FdStatus.COMPLETED) {
                Text(
                    "✓ Matured",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xFF059669), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (fd.status == FdStatus.ACTIVE) {
                Text(
                    "✏️",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clickable { onEdit() }
                )
            }
            Text(
                "🗑️",
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable { onDelete() }
            )
        }
        Text(
            "${formatCurrency(currency, fd.principal)} · ${trimNum(fd.interestRate)}% p.a. · ${trimNum(fd.tenureYears)} yr",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (fd.status == FdStatus.ACTIVE) {
            val start = runCatching { LocalDate.parse(fd.startDate) }.getOrDefault(today)
            val value = FdMath.currentValue(fd.principal, fd.interestRate, start, today, fd.tenureYears)
            val maturity = FdMath.maturityValue(fd.principal, fd.interestRate, fd.tenureYears)
            val gain = value - fd.principal
            val daily = FdMath.dailyIncrement(value, fd.interestRate)
            val daysLeft = FdMath.daysRemaining(start, today, fd.tenureYears)
            Row {
                FdStatBox("Current Value", formatCurrency(currency, value), Color(0xFF10B981), Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FdStatBox("Maturity Value", formatCurrency(currency, maturity), Color(0xFF2563EB), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Gain ${formatCurrency(currency, gain)} · +${formatCurrency(currency, daily)}/day · $daysLeft days left",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF059669)
            )
        } else {
            val maturedValue = fd.principal + fd.completedGain
            Row {
                FdStatBox("Matured Value", formatCurrency(currency, maturedValue), Color(0xFF059669), Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FdStatBox("Total Gain", formatCurrency(currency, fd.completedGain), Color(0xFFD97706), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Matured ${trimNum(fd.tenureYears)} yr at ${trimNum(fd.interestRate)}% p.a.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FdStatBox(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
