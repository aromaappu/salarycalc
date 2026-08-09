package com.example.salarycalc_trackincomeandexpense.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore

@Composable
fun PinLockScreen(
    settings: SettingsStore,
    onUnlock: () -> Unit
) {
    var pinAttempts by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    fun enterDigit(d: String) {
        if (pinAttempts.length >= 4) return
        pinAttempts += d
        showError = false
        if (pinAttempts.length == 4) {
            if (settings.verifyPin(pinAttempts)) {
                onUnlock()
            } else {
                showError = true
                pinAttempts = ""
            }
        }
    }

    fun clearLast() {
        if (pinAttempts.isNotEmpty()) {
            pinAttempts = pinAttempts.dropLast(1)
        }
        showError = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔒", fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter PIN",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            if (i < pinAttempts.length) Color(0xFF10B981)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (showError) "Wrong PIN! Try again" else " ",
            fontSize = 13.sp,
            color = Color(0xFFEF4444),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))

        val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "")
        digits.forEachIndexed { index, d ->
            if (index % 3 == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0..2) {
                        val cell = digits.getOrElse(index + i) { "" }
                        if (cell.isNotEmpty()) {
                            PinKey(cell) {
                                if (cell == "⌫") clearLast() else enterDigit(cell)
                            }
                        } else {
                            Spacer(Modifier.width(64.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
