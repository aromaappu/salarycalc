package com.example.salarycalc_trackincomeandexpense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun CalculatorDialog(
    onDismiss: () -> Unit,
    onUseAmount: (String) -> Unit
) {
    var display by remember { mutableStateOf("0") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Text(
                text = "🧮 Calculator",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp)
            ) {
                Text(
                    text = display,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))

            val grid = listOf(
                listOf("C", "⌫", "(", ")"),
                listOf("÷", "7", "8", "9"),
                listOf("×", "4", "5", "6"),
                listOf("−", "1", "2", "3"),
                listOf("+", "0", ".", "=")
            )
            grid.forEach { rowButtons ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    rowButtons.forEach { b ->
                        CalcKey(
                            text = b,
                            modifier = Modifier.weight(1f),
                            bg = when (b) {
                                "C" -> Color(0xFFEF4444)
                                "⌫" -> Color(0xFFF59E0B)
                                "=" -> Color(0xFF10B981)
                                "+", "−", "×", "÷", "(", ")" -> Color(0xFF667EEA)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            textColor = if (b == "C" || b == "⌫" || b == "=" || b in setOf("+", "−", "×", "÷", "(", ")")) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ) {
                            when (b) {
                                "C" -> display = "0"
                                "⌫" -> display = if (display.length == 1 || display == "Error") "0"
                                else display.dropLast(1)
                                "=" -> display = evaluateCalc(display)
                                else -> display = appendCalc(display, b)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF94A3B8))
                        .clickable { onDismiss() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF10B981))
                        .clickable {
                            val result = display
                            if (result != "Error" && result.toDoubleOrNull() != null) {
                                onUseAmount(result)
                            }
                            onDismiss()
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✅ Use Amount", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun CalcKey(
    text: String,
    modifier: Modifier,
    bg: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private fun appendCalc(current: String, v: String): String {
    if (current == "Error") return v
    if (current == "0" && v.toDoubleOrNull() != null) return v
    return current + v
}

private fun evaluateCalc(expr: String): String {
    return try {
        val normalized = expr
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
        val result = ExprParser(tokenize(normalized)).parse()
        if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            // Trim excessive decimals like JS float rendering
            val s = result.toString()
            if (s.length > 12) "%.6f".format(result).trimEnd('0').trimEnd('.') else s
        }
    } catch (e: Exception) {
        "Error"
    }
}

private fun tokenize(expr: String): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < expr.length) {
        val c = expr[i]
        if (c.isDigit() || c == '.') {
            var j = i
            while (j < expr.length && (expr[j].isDigit() || expr[j] == '.')) j++
            out.add(expr.substring(i, j))
            i = j
        } else {
            out.add(c.toString())
            i++
        }
    }
    return out
}

private class ExprParser(private val tokens: List<String>) {
    private var pos = 0

    fun parse(): Double {
        val v = expr()
        if (pos < tokens.size) throw IllegalArgumentException("Unexpected token")
        return v
    }

    private fun expr(): Double {
        var v = term()
        while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "-")) {
            val op = tokens[pos++]
            val r = term()
            v = if (op == "+") v + r else v - r
        }
        return v
    }

    private fun term(): Double {
        var v = factor()
        while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/")) {
            val op = tokens[pos++]
            val r = factor()
            v = if (op == "*") v * r else v / r
        }
        return v
    }

    private fun factor(): Double {
        val t = tokens.getOrNull(pos) ?: throw IllegalArgumentException("Missing operand")
        if (t == "(") {
            pos++
            val v = expr()
            if (tokens.getOrNull(pos) != ")") throw IllegalArgumentException("Missing )")
            pos++
            return v
        }
        if (t == "-") {
            pos++
            return -factor()
        }
        if (t == "+") {
            pos++
            return factor()
        }
        val n = t.toDoubleOrNull() ?: throw IllegalArgumentException("Bad number")
        pos++
        return n
    }
}
