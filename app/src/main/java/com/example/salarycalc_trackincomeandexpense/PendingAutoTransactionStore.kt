package com.example.salarycalc_trackincomeandexpense

import android.content.Context
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import org.json.JSONArray
import org.json.JSONObject

// Holds auto-income/auto-expense transactions that fired via NotificationReceiver
// while the app's WebView wasn't alive to catch the broadcast (app closed/killed).
// autoIncome.js reads and clears this list on every app launch via
// Android.getPendingAutoTransactions() / Android.clearPendingAutoTransactions().
object PendingAutoTransactionStore {

    private const val PREFS_NAME = "salarycalc_pending_auto_transactions"
    private const val KEY_LIST = "pending_list"

    private fun readArray(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return JSONArray(prefs.getString(KEY_LIST, "[]") ?: "[]")
    }

    private fun writeArray(context: Context, array: JSONArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }

    fun add(context: Context, type: String, amount: Double, name: String, date: String, time: String = "") {
        val array = readArray(context)
        // Skip if an identical entry is already queued (prevents double adding
        // when a payment notification is delivered more than once)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optString("type") == type &&
                obj.optDouble("amount") == amount &&
                obj.optString("name") == name &&
                obj.optString("date") == date &&
                obj.optString("time") == time
            ) {
                return
            }
        }
        val entry = JSONObject().apply {
            put("type", type)
            put("amount", amount)
            put("name", name)
            put("date", date)
            put("time", time)
        }
        array.put(entry)
        writeArray(context, array)
    }

    fun getAll(context: Context): String {
        return readArray(context).toString()
    }

    // Imports every queued auto transaction into the database. Called once on
    // app launch so SMS auto-added transactions actually survive (previously
    // nothing ever read this store, so auto-saved transactions were lost).
    fun applyPendingToDatabase(context: Context, db: AppDatabase): Int {
        val array = readArray(context)
        if (array.length() == 0) return 0

        var added = 0
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val type = obj.optString("type")
            val amount = obj.optDouble("amount")
            val name = obj.optString("name")
            val date = obj.optString("date")
            if (amount <= 0 || name.isBlank()) continue

            if (type == AppDatabase.TYPE_INCOME) {
                if (!db.getIncomeSources().contains(name)) db.addIncomeSource(name)
            } else {
                if (!db.getExpenseCategories().contains(name)) db.addExpenseCategory(name)
            }
            db.addTransaction(type, amount, name, date.ifBlank {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            })
            added++
        }

        if (added > 0) clearAll(context)
        return added
    }

    fun clearAll(context: Context) {
        writeArray(context, JSONArray())
    }
}
