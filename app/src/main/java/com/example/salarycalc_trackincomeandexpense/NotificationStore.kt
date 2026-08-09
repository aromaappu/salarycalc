package com.example.salarycalc_trackincomeandexpense

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Keeps a persistent record of every currently-scheduled notification,
// so BootReceiver can re-create all alarms after a phone restart.
// Native Kotlin code cannot read the WebView's localStorage, so this
// is a small separate store using Android's own SharedPreferences.
object NotificationStore {

    private const val PREFS_NAME = "salarycalc_notifications"
    private const val KEY_LIST = "scheduled_list"

    private fun readArray(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return JSONArray(prefs.getString(KEY_LIST, "[]") ?: "[]")
    }

    private fun writeArray(context: Context, array: JSONArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }

    // 🔥 UPDATED: Now stores transaction data (type, amount, category)
    fun add(context: Context, id: Int, title: String, message: String, timestampMillis: Long, type: String = "", amount: Double = 0.0, category: String = "") {
        val array = readArray(context)
        val newArray = JSONArray()

        // Copy over everything except the entry we're replacing (if it already exists)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getInt("id") != id) {
                newArray.put(obj)
            }
        }

        val entry = JSONObject().apply {
            put("id", id)
            put("title", title)
            put("message", message)
            put("timestamp", timestampMillis)
            // 🔥 NEW: Store transaction data
            put("type", type)
            put("amount", amount)
            put("category", category)
        }
        newArray.put(entry)

        writeArray(context, newArray)
    }

    // Removes one entry by id (called when a reminder/auto-entry is deleted or cancelled)
    fun remove(context: Context, id: Int) {
        val array = readArray(context)
        val newArray = JSONArray()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getInt("id") != id) {
                newArray.put(obj)
            }
        }

        writeArray(context, newArray)
    }

    // Returns the full saved list (used by BootReceiver and cancel-all logic)
    fun getAll(context: Context): JSONArray {
        return readArray(context)
    }

    // Removes any notifications whose scheduled time is already in the past
    fun removeExpired(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val array = readArray(context)
        val newArray = JSONArray()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optLong("timestamp", 0L) > nowMillis) {
                newArray.put(obj)
            }
        }

        writeArray(context, newArray)
    }

    // Clears everything (used when "disable all reminders" is tapped)
    fun clearAll(context: Context) {
        writeArray(context, JSONArray())
    }
}