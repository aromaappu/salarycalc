package com.example.salarycalc_trackincomeandexpense.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("SalaryCalcPrefs", Context.MODE_PRIVATE)

    // ======================= CURRENCY =======================
    fun getCurrencySymbol(): String = prefs.getString(KEY_CURRENCY, "₹") ?: "₹"
    fun setCurrencySymbol(symbol: String) = prefs.edit().putString(KEY_CURRENCY, symbol).apply()

    // ======================= THEME =======================
    fun isDarkModeEnabled(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)
    fun setDarkMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()

    // ======================= PIN LOCK =======================
    fun isPinEnabled(): Boolean = prefs.getBoolean(KEY_PIN_ENABLED, false)
    fun setPinEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_PIN_ENABLED, enabled).apply()

    // Stores only a salted SHA-256 hash of the PIN, never the PIN itself.
    fun setPinCode(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = sha256(salt, pin)
        prefs.edit().putString(KEY_PIN_CODE, "sha256:${toHex(salt)}:${toHex(hash)}").apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_CODE, "") ?: ""
        if (stored.isBlank()) return false
        // Legacy plaintext PIN stored by older versions: compare and upgrade.
        if (!stored.startsWith("sha256:")) {
            if (stored == pin) {
                setPinCode(pin)
                return true
            }
            return false
        }
        val parts = stored.split(":")
        if (parts.size != 3) return false
        val salt = hexToBytes(parts[1])
        val expected = parts[2]
        return toHex(sha256(salt, pin)) == expected
    }

    fun hasPinCode(): Boolean = !(prefs.getString(KEY_PIN_CODE, "").isNullOrBlank())

    fun getPinHash(): String? = prefs.getString(KEY_PIN_CODE, null)
    fun setPinHash(hash: String) = prefs.edit().putString(KEY_PIN_CODE, hash).apply()

    private fun sha256(salt: ByteArray, pin: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray(Charsets.UTF_8))
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // ======================= SMS AUTO TRANSACTION =======================
    fun isSmsTrackingEnabled(): Boolean = prefs.getBoolean(KEY_SMS_TRACKING, false)
    fun setSmsTrackingEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_SMS_TRACKING, enabled).apply()

    fun isSmsConfirmationEnabled(): Boolean = prefs.getBoolean(KEY_SMS_CONFIRMATION, false)
    fun setSmsConfirmationEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_SMS_CONFIRMATION, enabled).apply()

    // ======================= NET BALANCE SOURCE =======================
    /** true = use the latest uploaded statement's closing balance (default). */
    fun isStatementNetBalanceEnabled(): Boolean = prefs.getBoolean(KEY_STATEMENT_NET_BALANCE, true)
    fun setStatementNetBalanceEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_STATEMENT_NET_BALANCE, enabled).apply()

    fun getPendingSmsTransactions(): String =
        prefs.getString(KEY_PENDING_SMS, "[]") ?: "[]"
    fun setPendingSmsTransactions(json: String) =
        prefs.edit().putString(KEY_PENDING_SMS, json).apply()

    // ======================= APP STATE =======================
    fun hasSeenWelcome(): Boolean = prefs.getBoolean(KEY_WELCOME_SEEN, false)
    fun setWelcomeSeen() = prefs.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()

    // ======================= DELETED AUTO MONTHS (from legacy backup) =======================
    fun getDeletedAutoIncomeMonths(): List<String> =
        parseStringList(prefs.getString(KEY_DEL_AUTO_INCOME_MONTHS, "[]") ?: "[]")
    fun setDeletedAutoIncomeMonths(months: List<String>) =
        prefs.edit().putString(KEY_DEL_AUTO_INCOME_MONTHS, listToJson(months)).apply()

    fun getDeletedAutoExpenseMonths(): List<String> =
        parseStringList(prefs.getString(KEY_DEL_AUTO_EXPENSE_MONTHS, "[]") ?: "[]")
    fun setDeletedAutoExpenseMonths(months: List<String>) =
        prefs.edit().putString(KEY_DEL_AUTO_EXPENSE_MONTHS, listToJson(months)).apply()

    private fun parseStringList(json: String): List<String> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun listToJson(list: List<String>): String {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    fun resetAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Clears every preference and deletes the preferences file from disk so
     * the values cannot be recovered from the device.
     */
    fun purge(context: Context) {
        prefs.edit().clear().commit()
        try {
            File(context.filesDir.parentFile, "shared_prefs/SalaryCalcPrefs.xml").delete()
        } catch (e: Exception) {
            // Best-effort cleanup; the in-memory prefs are already cleared.
        }
    }

    companion object {
        private const val KEY_CURRENCY = "currencySymbol"
        private const val KEY_DARK_MODE = "darkMode"
        private const val KEY_PIN_ENABLED = "pinEnabled"
        private const val KEY_PIN_CODE = "pinCode"
        private const val KEY_SMS_TRACKING = "smsTrackingEnabled"
        private const val KEY_SMS_CONFIRMATION = "smsConfirmationEnabled"
        private const val KEY_STATEMENT_NET_BALANCE = "statementNetBalanceEnabled"
        private const val KEY_PENDING_SMS = "pendingSmsTransactions"
        private const val KEY_WELCOME_SEEN = "welcomeSeen"
        private const val KEY_DEL_AUTO_INCOME_MONTHS = "deletedAutoIncomeMonths"
        private const val KEY_DEL_AUTO_EXPENSE_MONTHS = "deletedAutoExpenseMonths"
    }
}
