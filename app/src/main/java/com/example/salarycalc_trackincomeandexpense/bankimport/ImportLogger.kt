package com.example.salarycalc_trackincomeandexpense.bankimport

import android.util.Log

/**
 * Thin wrapper around android.util.Log so the import pipeline has one
 * consistent tag and a single place to change logging behaviour.
 */
object ImportLogger {
    private const val TAG = "BankImport"

    fun d(message: String) = Log.d(TAG, message)

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }

    fun w(message: String) = Log.w(TAG, message)
}
