package com.example.salarycalc_trackincomeandexpense.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.BackupUtil
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.components.OverlayHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private data class CurrencyOption(val code: String, val symbol: String, val name: String)

private val CURRENCY_LIST = listOf(
    CurrencyOption("INR", "₹", "Indian Rupee"),
    CurrencyOption("USD", "$", "US Dollar"),
    CurrencyOption("EUR", "€", "Euro"),
    CurrencyOption("GBP", "£", "British Pound"),
    CurrencyOption("JPY", "¥", "Japanese Yen"),
    CurrencyOption("CNY", "¥", "Chinese Yuan"),
    CurrencyOption("AUD", "A$", "Australian Dollar"),
    CurrencyOption("CAD", "C$", "Canadian Dollar"),
    CurrencyOption("SAR", "﷼", "Saudi Riyal"),
    CurrencyOption("AED", "د.إ", "UAE Dirham")
)

private const val UPDATE_URL = "https://version-control-rouge.vercel.app/"
private const val STORE_URL = "https://indusapp.store/hnu7fmve"

@Composable
fun SettingsScreen(
    db: AppDatabase,
    settings: SettingsStore,
    onBack: () -> Unit,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableStateOf(0) }
    var showChangePin by remember { mutableStateOf(false) }
    var showDisablePin by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<UpdateState?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showResetFinal by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun toggleDark() {
        settings.setDarkMode(!darkMode)
        onDarkModeChange(!darkMode)
    }

    fun togglePin() {
        if (!settings.hasPinCode()) {
            showChangePin = true
            return
        }
        if (settings.isPinEnabled()) {
            showDisablePin = true
            return
        }
        settings.setPinEnabled(true)
        refreshKey++
        toast("✅ PIN lock enabled")
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (json == null) {
                toast("❌ Restore failed: could not read file")
                return@rememberLauncherForActivityResult
            }
            BackupUtil.importBackupJson(context, db, settings, json)
            onDarkModeChange(settings.isDarkModeEnabled())
            refreshKey++
            toast("✅ Data restored!")
        } catch (e: Exception) {
            toast("Restore failed: ${e.message}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        OverlayHeader(
            "⚙️ Settings",
            onBack,
            gradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
        )
        Column(Modifier.padding(10.dp)) {

            Text(
                "Appearance",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            SettingsToggleRow(
                icon = "🌓",
                iconBg = Color(0xFF4B5563),
                title = "Dark Mode",
                subtitle = "Toggle dark/light theme",
                active = darkMode,
                onClick = { toggleDark() }
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "Security",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            SettingsToggleRow(
                icon = "🔒",
                iconBg = Color(0xFFF59E0B),
                title = "PIN Lock",
                subtitle = "Secure your app with PIN",
                active = settings.isPinEnabled(),
                onClick = { togglePin() }
            )
            SettingsListRow(
                icon = "🔑",
                iconBg = Color(0xFFF59E0B),
                title = "Change PIN",
                subtitle = "Update your PIN code",
                onClick = { showChangePin = true }
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "General",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            SettingsListRow(
                icon = "💱",
                iconBg = Color(0xFF667EEA),
                title = "Currency",
                subtitle = "Change currency symbol",
                onClick = { showCurrencyPicker = true }
            )
            SettingsListRow(
                icon = "💾",
                iconBg = Color(0xFF10B981),
                title = "Backup and Restore",
                subtitle = "Backup and restore your data",
                onClick = { showBackup = true }
            )
            SettingsListRow(
                icon = "🔒",
                iconBg = Color(0xFF8B5CF6),
                title = "Privacy",
                subtitle = "About and privacy policy",
                onClick = { showPrivacy = true }
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "System",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            SettingsListRow(
                icon = "🔄",
                iconBg = Color(0xFF06B6D4),
                title = "Check for Update",
                subtitle = "Tap to check latest version",
                onClick = {
                    showUpdateDialog = UpdateState("Checking for Updates...", "Please wait while we check for the latest version.", "🔄")
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { checkForUpdate(context) }
                        showUpdateDialog = result
                    }
                }
            )
            SettingsListRow(
                icon = "🗑️",
                iconBg = Color(0xFFEF4444),
                title = "Reset All",
                subtitle = "Clear all data permanently",
                onClick = { showResetConfirm = true }
            )

        }
    }

    if (showChangePin) {
        ChangePinDialog(
            settings = settings,
            onDismiss = { showChangePin = false },
            onSaved = {
                showChangePin = false
                refreshKey++
                toast("✅ PIN updated & lock enabled!")
            }
        )
    }

    if (showDisablePin) {
        DisablePinDialog(
            settings = settings,
            onDismiss = { showDisablePin = false },
            onDisabled = {
                showDisablePin = false
                refreshKey++
                toast("PIN lock disabled")
            }
        )
    }

    if (showCurrencyPicker) {
        AlertDialog(
            onDismissRequest = { showCurrencyPicker = false },
            title = { Text("💱 Select Currency") },
            text = {
                Column {
                    CURRENCY_LIST.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settings.setCurrencySymbol(c.symbol)
                                    showCurrencyPicker = false
                                    refreshKey++
                                    toast("Currency changed to ${c.name} (${c.symbol})")
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${c.symbol}  ${c.name} (${c.code})",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (settings.getCurrencySymbol() == c.symbol) {
                                Text("✅", fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyPicker = false }) { Text("Close") }
            }
        )
    }

    if (showBackup) {
        AlertDialog(
            onDismissRequest = { showBackup = false },
            title = { Text("💾 Backup and Restore") },
            text = {
                Column {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF667EEA))
                            .clickable {
                                showBackup = false
                                toast("⏳ Backing up...")
                                scope.launch {
                                    val name = withContext(Dispatchers.IO) {
                                        BackupUtil.exportToDownloads(context, db, settings)
                                    }
                                    if (name != null) {
                                        toast("Backup saved to Downloads ($name)")
                                    } else {
                                        toast("Backup failed")
                                    }
                                }
                            }
                            .padding(vertical = 13.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📥 Backup Data", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10B981))
                            .clickable {
                                showBackup = false
                                openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                            }
                            .padding(vertical = 13.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📤 Restore Data", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Backup exports all data to a JSON file. Restore imports from a JSON file.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackup = false }) { Text("Close") }
            }
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("ℹ️ About & Privacy") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("About", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The Salary Calculator APK is a simple and useful mobile application designed to help users easily calculate their monthly and yearly salary. It provides quick results based on basic input details like basic pay, allowances, and deductions, making salary estimation fast and accurate.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This Salary Calculator App does not collect, store, or share any personal user data. All calculations are done locally on your device and no information is sent to any server. The app does not use cookies, tracking, or third-party analytics. Your privacy is fully protected while using this application.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Note", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Please add the salary on the 1st day of every month (Do not enter March 31, March 28 like this). Expense entries are fully based on the added salary amount. At the end of each month, the income section will reset automatically.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("✅ Got It") }
            }
        )
    }

    showUpdateDialog?.let { state ->
        AlertDialog(
            onDismissRequest = { showUpdateDialog = null },
            title = { Text(state.icon + " " + state.title) },
            text = { Text(state.message, fontSize = 14.sp) },
            confirmButton = {
                if (state.hasUpdate) {
                    TextButton(onClick = {
                        showUpdateDialog = null
                        openStoreUrl(context)
                    }) { Text("Download") }
                } else {
                    TextButton(onClick = { showUpdateDialog = null }) {
                        Text("OK")
                    }
                }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("⚠️ Delete ALL data?") },
            text = { Text("This cannot be undone!") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    showResetFinal = true
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetFinal) {
        AlertDialog(
            onDismissRequest = { showResetFinal = false },
            title = { Text("FINAL WARNING") },
            text = { Text("Are you absolutely sure?") },
            confirmButton = {
                TextButton(onClick = {
                    db.purgeAll(context)
                    settings.purge(context)
                    onDarkModeChange(false)
                    showResetFinal = false
                    toast("✅ All data permanently deleted!")
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showResetFinal = false }) { Text("Cancel") }
            }
        )
    }
}

private data class UpdateState(
    val title: String,
    val message: String,
    val icon: String,
    val latest: String? = null,
    val hasUpdate: Boolean = false
)

private fun checkForUpdate(context: Context): UpdateState {
    return try {
        val conn = URL(UPDATE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val match = Regex("""\d+(?:\.\d+)+""").find(text)
        val latest = match?.value
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        if (latest == null) {
            UpdateState("Check Failed", "Could not read version info from server. Please try again later.", "⚠️")
        } else if (isNewer(latest, appVersion)) {
            UpdateState(
                "Update Available!",
                "New version $latest is available. You are on version $appVersion.",
                "⬇️",
                latest = latest,
                hasUpdate = true
            )
        } else {
            UpdateState("Up to Date", "You have the latest version ($latest). No updates available.", "✅", latest = latest)
        }
    } catch (e: Exception) {
        UpdateState(
            "Check Failed",
            "Unable to check for updates. Please check your internet connection and try again.",
            "⚠️"
        )
    }
}

private fun openStoreUrl(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(STORE_URL)))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open store link", Toast.LENGTH_SHORT).show()
    }
}

private fun isNewer(v1: String, v2: String): Boolean {
    val a = v1.split(".").map { it.toIntOrNull() ?: 0 }
    val b = v2.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x > y) return true
        if (x < y) return false
    }
    return false
}

@Composable
private fun ChangePinDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun save() {
        if (settings.hasPinCode() && !settings.verifyPin(oldPin)) {
            toast("❌ Wrong old PIN!")
            return
        }
        if (newPin.length != 4 || newPin.toIntOrNull() == null) {
            toast("Enter valid 4-digit PIN.")
            return
        }
        settings.setPinCode(newPin)
        settings.setPinEnabled(true)
        onSaved()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔑 Change PIN") },
        text = {
            Column {
                if (settings.hasPinCode()) {
                    Text("Enter OLD 4-digit PIN", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    TextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text("Enter NEW 4-digit PIN", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DisablePinDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDisabled: () -> Unit
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun save() {
        if (!settings.verifyPin(enteredPin)) {
            toast("Wrong PIN!")
            return
        }
        settings.setPinEnabled(false)
        onDisabled()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔒 Disable PIN Lock") },
        text = {
            Column {
                Text("Enter current PIN to disable:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                TextField(
                    value = enteredPin,
                    onValueChange = { enteredPin = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }) { Text("Disable") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SettingsToggleRow(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 17.sp)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .background(if (active) Color(0xFF10B981) else Color(0xFFD1D5DB)),
            contentAlignment = if (active) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(20.dp)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun SettingsListRow(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 17.sp)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "›",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
