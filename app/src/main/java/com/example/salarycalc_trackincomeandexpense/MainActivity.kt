package com.example.salarycalc_trackincomeandexpense

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import com.example.salarycalc_trackincomeandexpense.data.AutoEntries
import com.example.salarycalc_trackincomeandexpense.data.LegacyLocalStorageMigrator
import com.example.salarycalc_trackincomeandexpense.data.SettingsStore
import com.example.salarycalc_trackincomeandexpense.ui.SalaryCalcApp
import com.example.salarycalc_trackincomeandexpense.ui.theme.SalaryCalcTrackIncomeAndExpenseTheme

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private var openStatementRequest by mutableIntStateOf(0)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                android.widget.Toast.makeText(
                    this,
                    "Notifications are disabled. Reminders will not appear until permission is allowed.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

    // Bumps AutoEvents.refreshTick whenever a bank/UPI notification is added,
    // so the open screens (Income/Expense/Charts/Dashboard) refresh at once.
    private val autoTicker = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AutoEvents.refreshTick.intValue++
        }
    }
    @Volatile
    private var autoTickerRegistered = false

    private fun registerAutoTicker() {
        if (autoTickerRegistered) return
        val filter = IntentFilter().apply {
            addAction(AutoEvents.ACTION_AUTO_TRANSACTION)
            addAction(AutoEvents.ACTION_NEW_SMS_TRANSACTION)
        }
        // Registered against the application context so it survives activity
        // recreation while the process is alive.
        ContextCompat.registerReceiver(
            applicationContext,
            autoTicker,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        autoTickerRegistered = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_STATEMENT, false)) {
            openStatementRequest++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_STATEMENT, false) == true) {
            openStatementRequest++
        }

        db = AppDatabase(this)
        db.seedDefaults()
        settings = SettingsStore(this)

        LegacyLocalStorageMigrator.tryMigrate(this, db, settings)

        NotificationHelper.createChannel(this)
        AutoEntries.applyDue(db, settings, this)
        com.example.salarycalc_trackincomeandexpense.data.LoanScheduler.reconcileAll(this, db)
        DailyFinanceWorker.ensureScheduled(this)
        ReminderScheduler.reconcileMonthly(this)

        // Import any transactions queued while the app was closed (reminder
        // payments / SMS auto-save) so they actually reach the database.
        PendingAutoTransactionStore.applyPendingToDatabase(this, db)

        registerAutoTicker()

        NotificationHelper.createChannel(this)
        requestNotificationPermissionIfNeeded()

        setContent {
            var darkMode by remember { mutableStateOf(settings.isDarkModeEnabled()) }
            SalaryCalcTrackIncomeAndExpenseTheme(
                darkTheme = darkMode,
                dynamicColor = false
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    SalaryCalcApp(
                        db,
                        settings,
                        darkMode = darkMode,
                        onDarkModeChange = { darkMode = it },
                        openStatementRequest = openStatementRequest
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

