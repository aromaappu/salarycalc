package com.example.salarycalc_trackincomeandexpense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "SmsTrackingPrefs"
        private const val KEY_CONFIRMATION_ENABLED = "smsConfirmationEnabled"
        private const val KEY_PENDING_TRANSACTIONS = "pendingTransactions"
        private const val KEY_RECENT_PAYMENTS = "recentNotificationPaymentsV2"
        private val transactionLock = Any()
    }

    override fun onReceive(context: Context, intent: Intent) {
        // No manifest receiver uses this class anymore. SMS is now read from
        // notifications via SmsNotificationListener, so there is nothing to do.
    }

    private fun parseTransaction(
        message: String,
        sender: String?,
        timestamp: Long = System.currentTimeMillis(),
        trustedPaymentApp: Boolean = false,
        title: String = ""
    ): TransactionData? {
        val parsed = PaymentNotificationParser.parse(message, trustedPaymentApp, title) ?: return null
        val lowerMessage = message.lowercase(Locale.ROOT)
        val paymentMethod = if (trustedPaymentApp) "UPI" else extractPaymentMethod(lowerMessage)
        val category = classifyCategory(lowerMessage, parsed.name, parsed.type)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        return TransactionData(
            isTransaction = true, transactionType = parsed.type, amount = parsed.amount,
            currency = "INR", merchant = parsed.name, bank = extractBankName(sender),
            accountLast4 = parsed.accountLast4, balance = extractBalance(message),
            referenceNumber = parsed.reference, paymentMethod = paymentMethod,
            category = category, date = date, time = time, description = message.take(200),
            confidence = calculateConfidence(parsed.amount, parsed.name, category, paymentMethod)
        )
    }

    private fun extractBankName(sender: String?): String {
        if (sender == null) return ""
        
        val bankNameMap = mapOf(
            "bpojio" to "Jio Payments Bank", "jio" to "Jio Payments Bank",
            "bpaytm" to "Paytm", "paytm" to "Paytm",
            "bphonepe" to "PhonePe", "phonepe" to "PhonePe",
            "bgpay" to "Google Pay", "gpay" to "Google Pay",
            "bhdfc" to "HDFC Bank", "hdfc" to "HDFC Bank",
            "bicici" to "ICICI Bank", "icici" to "ICICI Bank",
            "bsbi" to "SBI", "sbi" to "SBI",
            "baxis" to "Axis Bank", "axis" to "Axis Bank",
            "bkotak" to "Kotak Bank", "kotak" to "Kotak Bank",
            "bpnb" to "PNB", "pnb" to "PNB",
            "bboi" to "Bank of India", "boi" to "Bank of India",
            "bob" to "Bank of Baroda",
            "bcanara" to "Canara Bank", "canara" to "Canara Bank",
            "bunion" to "Union Bank", "union" to "Union Bank",
            "bcentral" to "Central Bank", "central" to "Central Bank",
            "bucob" to "UCO Bank", "uco" to "UCO Bank",
            "bidbi" to "IDBI Bank", "idbi" to "IDBI Bank",
            "bindus" to "IndusInd Bank", "indus" to "IndusInd Bank",
            "byes" to "Yes Bank", "yes" to "Yes Bank",
            "bub" to "UBI", "ubi" to "UBI",
            "bcb" to "CBI", "cbi" to "CBI"
        )
        
        val lowerSender = sender.lowercase()
        for ((key, value) in bankNameMap) {
            if (lowerSender.contains(key)) {
                return value
            }
        }
        
        // If no match, return sender name (cleaned)
        return sender.takeLast(15)
    }

    private fun extractBalance(message: String): String {
        val patterns = listOf(
            Pattern.compile("(?:available\\s+balance|balance|bal)\\s*(?:is|:)?\\s*(?:Rs\\.?|₹)?\\s*(\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("avl\\s+bal\\s*(?:is|:)?\\s*(?:Rs\\.?|₹)?\\s*(\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val balanceStr = matcher.group(1)?.replace(",", "")
                return balanceStr ?: ""
            }
        }
        return ""
    }

    private fun extractPaymentMethod(message: String): String {
        val methods = listOf(
            "upi" to "UPI",
            "neft" to "NEFT",
            "imps" to "IMPS",
            "rtgs" to "RTGS",
            "card" to "Card",
            "atm" to "ATM",
            "wallet" to "Wallet",
            "cash" to "Cash"
        )
        
        for ((key, value) in methods) {
            if (message.contains(key)) {
                return value
            }
        }
        
        return "Unknown"
    }

    private fun classifyCategory(message: String, merchant: String, transactionType: String): String {
        if (transactionType == "credit") {
            if (merchant == "Salary" || merchant.contains("Salary", ignoreCase = true)) return "Salary"
            return "Transfer"
        }

        val lowerMessage = message.lowercase()
        val lowerMerchant = merchant.lowercase()

        // Priority-based category mapping (most specific first)
        val categoryMap = linkedMapOf(
            // Food & Beverages (highest priority for small daily transactions)
            "tea" to "Food", "chai" to "Food", "tapri" to "Food",
            "coffee" to "Food", "cafe" to "Food", "starbucks" to "Food",
            "ccd" to "Food", "barista" to "Food",
            "restaurant" to "Food", "hotel" to "Food", "dhaba" to "Food",
            "food" to "Food", "zomato" to "Food", "swiggy" to "Food",
            "pizza" to "Food", "biryani" to "Food", "juice" to "Food",
            "ice cream" to "Food", "sweet" to "Food", "bakery" to "Food",
            "mess" to "Food", "canteen" to "Food", "tiffin" to "Food",
            "lunch" to "Food", "dinner" to "Food", "breakfast" to "Food",
            "snack" to "Food", "biscuit" to "Food", "chocolate" to "Food",
            "cold drink" to "Food", "soft drink" to "Food", "water bottle" to "Food",

            // Groceries & Daily Needs
            "grocery" to "Grocery", "kirana" to "Grocery", "supermarket" to "Grocery",
            "bigbasket" to "Grocery", "blinkit" to "Grocery", "zepto" to "Grocery",
            "dmart" to "Grocery", "reliance" to "Grocery", "more store" to "Grocery",
            "vegetable" to "Grocery", "fruit" to "Grocery", "milk" to "Grocery",
            "pantry" to "Grocery", "provision" to "Grocery",

            // Shopping
            "amazon" to "Shopping", "flipkart" to "Shopping", "myntra" to "Shopping",
            "meesho" to "Shopping", "ajio" to "Shopping", "nykaa" to "Shopping",
            "store" to "Shopping", "shop" to "Shopping", "mall" to "Shopping",
            "market" to "Shopping", "clothes" to "Shopping", "clothing" to "Shopping",
            "shoe" to "Shopping", "fashion" to "Shopping", "jewellery" to "Shopping",
            "gold" to "Shopping", "silver" to "Shopping",

            // Fuel
            "petrol" to "Fuel", "fuel" to "Fuel", "bpcl" to "Fuel",
            "hpcl" to "Fuel", "iocl" to "Fuel", "shell" to "Fuel",
            "gas station" to "Fuel", "diesel" to "Fuel", "pump" to "Fuel",
            "cng" to "Fuel",

            // Healthcare
            "pharmacy" to "Healthcare", "medical" to "Healthcare", "medicine" to "Healthcare",
            "hospital" to "Healthcare", "doctor" to "Healthcare", "clinic" to "Healthcare",
            "apollo" to "Healthcare", "medplus" to "Healthcare", "netmeds" to "Healthcare",
            "1mg" to "Healthcare", "pharmeasy" to "Healthcare", "health" to "Healthcare",
            "diagnostic" to "Healthcare", "lab" to "Healthcare", "pathology" to "Healthcare",

            // Entertainment
            "movie" to "Entertainment", "cinema" to "Entertainment", "theatre" to "Entertainment",
            "netflix" to "Subscription", "hotstar" to "Subscription", "prime video" to "Subscription",
            "spotify" to "Subscription", "youtube" to "Subscription", "jiocinema" to "Subscription",
            "subscription" to "Subscription", "membership" to "Subscription",

            // Bills & Recharge
            "electricity" to "Bills", "electric" to "Bills", "power" to "Bills",
            "water bill" to "Bills", "gas bill" to "Bills", "lpg" to "Bills",
            "mobile" to "Recharge", "recharge" to "Recharge",
            "airtel" to "Recharge", "jio" to "Recharge", "vi" to "Recharge", "bsnl" to "Recharge",
            "internet" to "Bills", "broadband" to "Bills", "wifi" to "Bills",
            "dth" to "Bills", "cable" to "Bills",

            // Education
            "education" to "Education", "school" to "Education", "college" to "Education",
            "tuition" to "Education", "course" to "Education", "book" to "Education",
            "exam" to "Education", "coaching" to "Education",

            // Travel
            "uber" to "Travel", "ola" to "Travel", "rapido" to "Travel",
            "irctc" to "Travel", "redbus" to "Travel", "makemytrip" to "Travel",
            "flight" to "Travel", "train" to "Travel", "bus" to "Travel",
            "taxi" to "Travel", "cab" to "Travel", "auto" to "Travel",
            "metro" to "Travel", "parking" to "Travel", "toll" to "Travel",
            "fuel" to "Travel",

            // ATM & Bank
            "atm" to "ATM", "withdraw" to "ATM", "cash withdrawal" to "ATM",
            "neft" to "Bank Transfer", "imps" to "Bank Transfer", "rtgs" to "Bank Transfer",

            // Insurance & Tax
            "insurance" to "Insurance", "premium" to "Insurance",
            "term" to "Insurance", "health insurance" to "Insurance",
            "car insurance" to "Insurance", "bike insurance" to "Insurance",
            "tax" to "Tax", "gst" to "Tax", "income tax" to "Tax",

            // EMI & Loans
            "emi" to "EMI", "loan" to "Loan", "personal loan" to "Loan",
            "home loan" to "Loan", "car loan" to "Loan", "bike loan" to "Loan",

            // Rent & Housing
            "rent" to "Rent", "house" to "Rent", "flat" to "Rent",
            "maintenance" to "Housing", "society" to "Housing",

            // Wallet & UPI
            "paytm" to "Wallet", "phonepe" to "UPI", "google pay" to "UPI",
            "gpay" to "UPI", "bhim" to "UPI", "mobikwik" to "Wallet",
            "freecharge" to "Wallet",

            // Others
            "donation" to "Donation", "charity" to "Donation", "temple" to "Donation",
            "gift" to "Gift", "birthday" to "Gift", "wedding" to "Gift",
            "pet" to "Pet Care", "vet" to "Pet Care", "pet food" to "Pet Care",
            "laundry" to "Personal", "salon" to "Personal", "haircut" to "Personal",
            "gym" to "Personal", "fitness" to "Personal", "yoga" to "Personal"
        )

        // Check merchant first (more specific), then message
        for ((key, category) in categoryMap) {
            if (lowerMerchant.contains(key)) return category
        }
        for ((key, category) in categoryMap) {
            if (lowerMessage.contains(key)) return category
        }

        return "Other"
    }

    private fun calculateConfidence(amount: Double, merchant: String, category: String, paymentMethod: String): Double {
        var confidence = 0.5
        
        // Increase confidence if we have good data
        if (amount > 0) confidence += 0.2
        if (merchant.isNotEmpty()) confidence += 0.15
        if (category != "Other") confidence += 0.1
        if (paymentMethod != "Unknown") confidence += 0.05
        
        return minOf(confidence, 1.0)
    }

    private fun handleTransaction(context: Context, prefs: SharedPreferences, transaction: TransactionData) {
        val confirmationEnabled = prefs.getBoolean(KEY_CONFIRMATION_ENABLED, false)

        if (confirmationEnabled) {
            // Store as pending — confirmation popup will show in app
            val pending = getPendingTransactions(prefs).toMutableList()
            pending.add(transaction)
            val saved = prefs.edit()
                .putString(KEY_PENDING_TRANSACTIONS, serializeTransactions(pending))
                .commit()
            if (!saved) return
            NotificationHelper.showTransactionNotification(context, transaction)
            // Notify app to load pending transactions
            context.sendBroadcast(Intent(AutoEvents.ACTION_NEW_SMS_TRANSACTION))
        } else {
            // Auto-save: persist to store AND broadcast for live app
            if (!autoSaveTransaction(context, transaction)) return
            // Notify app directly
            val txType = if (transaction.transactionType == "credit") "income" else "expense"
            val txName = transaction.merchant.ifEmpty { transaction.category }
            val intent = Intent(AutoEvents.ACTION_AUTO_TRANSACTION)
            intent.putExtra("type", txType)
            intent.putExtra("amount", transaction.amount)
            intent.putExtra("name", txName)
            intent.putExtra("date", transaction.date)
            intent.putExtra("category", transaction.category)
            intent.putExtra("paymentMethod", transaction.paymentMethod)
            context.sendBroadcast(intent)
        }
    }

    private fun autoSaveTransaction(context: Context, transaction: TransactionData): Boolean = try {
        // Write straight into the database so the transaction is added
        // immediately (no app restart or broadcast listener needed).
        val txType = if (transaction.transactionType == "credit") "income" else "expense"
        val txName = transaction.merchant.ifEmpty { transaction.category }
        val date = transaction.date.ifBlank {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        }
        val db = AppDatabase(context)
        // Notification duplicates are checked before this write using reference IDs
        // and timestamps. Repeated legitimate same-day payments must remain separate.
        if (txType == AppDatabase.TYPE_INCOME) {
            if (!db.getIncomeSources().contains(txName)) db.addIncomeSource(txName)
        } else {
            if (!db.getExpenseCategories().contains(txName)) db.addExpenseCategory(txName)
        }
        db.addTransaction(
            type = txType,
            amount = transaction.amount,
            name = txName,
            date = date
        ) > 0L
    } catch (_: Exception) {
        false
    }

    private fun getPendingTransactions(prefs: SharedPreferences): List<TransactionData> {
        val json = prefs.getString(KEY_PENDING_TRANSACTIONS, "[]")
        return deserializeTransactions(json ?: "[]")
    }

    private fun serializeTransactions(transactions: List<TransactionData>): String {
        // Use a safe delimiter that won't appear in SMS text
        return transactions.joinToString("||TXSEP||") { 
            val safeDesc = it.description.replace("|", " ").replace(",", " ")
            val safeMerchant = it.merchant.replace("|", " ").replace(",", " ")
            "${it.isTransaction},${it.transactionType},${it.amount},${it.currency},${safeMerchant},${it.bank},${it.accountLast4},${it.balance},${it.referenceNumber},${it.paymentMethod},${it.category},${it.date},${it.time},$safeDesc,${it.confidence}" 
        }
    }

    private fun deserializeTransactions(json: String): List<TransactionData> {
        if (json == "[]") return emptyList()
        return json.split("||TXSEP||").mapNotNull { part ->
            val parts = part.split(",")
            if (parts.size >= 15) {
                TransactionData(
                    isTransaction = parts[0].toBoolean(),
                    transactionType = parts[1],
                    amount = parts[2].toDoubleOrNull() ?: 0.0,
                    currency = parts[3],
                    merchant = parts[4],
                    bank = parts[5],
                    accountLast4 = parts[6],
                    balance = parts[7],
                    referenceNumber = parts[8],
                    paymentMethod = parts[9],
                    category = parts[10],
                    date = parts[11],
                    time = parts[12],
                    description = parts.subList(13, parts.size - 1).joinToString(","),
                    confidence = parts.last().toDoubleOrNull() ?: 0.0
                )
            } else null
        }
    }

    data class TransactionData(
        val isTransaction: Boolean,
        val transactionType: String,
        val amount: Double,
        val currency: String,
        val merchant: String,
        val bank: String,
        val accountLast4: String,
        val balance: String,
        val referenceNumber: String,
        val paymentMethod: String,
        val category: String,
        val date: String,
        val time: String,
        val description: String,
        val confidence: Double
    )

    // Public wrapper for SmsNotificationListener: parses, dedups, and handles a
    // transaction read from a notification. Returns true if it was handled.
    fun processNotificationTransaction(
        context: Context,
        body: String,
        sender: String?,
        timestamp: Long,
        sourcePackage: String = "sms",
        trustedPaymentApp: Boolean = false,
        title: String = ""
    ): Boolean = synchronized(transactionLock) {
        if (!SmsNotificationListener.isTrackingEnabled(context)) return@synchronized false
        val transaction = parseTransaction(body, sender, timestamp, trustedPaymentApp, title)
            ?: return@synchronized false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val payment = PaymentNotificationParser.Payment(
            transaction.amount, transaction.transactionType, transaction.merchant,
            transaction.referenceNumber, transaction.accountLast4
        )
        val incoming = PaymentDuplicateMatcher.entry(payment, timestamp, sourcePackage, "$title $body")
        val recent = readRecentPayments(prefs).toMutableList()
        val match = recent.indexOfFirst { PaymentDuplicateMatcher.matches(it, incoming) }
        if (match >= 0) {
            val old = recent[match]
            recent[match] = old.copy(
                sources = old.sources + incoming.sources,
                reference = old.reference.ifBlank { incoming.reference },
                // Remember the most recently seen source payload so its repost is ignored.
                timestamp = incoming.timestamp,
                message = incoming.message
            )
            writeRecentPayments(prefs, recent)
            return@synchronized false
        }
        // Only remember handled transactions; a failed database write can be retried.
        handleTransaction(context, prefs, transaction)
        recent.add(incoming)
        writeRecentPayments(prefs, recent)
        true
    }

    private fun readRecentPayments(prefs: SharedPreferences): List<PaymentDuplicateMatcher.Entry> {
        return try {
            val array = JSONArray(prefs.getString(KEY_RECENT_PAYMENTS, "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val sources = item.getJSONArray("sources")
                PaymentDuplicateMatcher.Entry(
                    item.getLong("amount"), item.getString("type"), item.getString("name"),
                    item.optString("reference"), item.getLong("timestamp"),
                    (0 until sources.length()).map { sources.getString(it) }.toSet(),
                    item.getString("message")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeRecentPayments(prefs: SharedPreferences, entries: List<PaymentDuplicateMatcher.Entry>) {
        val oldest = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val array = JSONArray()
        entries.filter { it.timestamp >= oldest }.takeLast(1000).forEach { entry ->
            array.put(JSONObject().apply {
                put("amount", entry.amountPaise)
                put("type", entry.type)
                put("name", entry.name)
                put("reference", entry.reference)
                put("timestamp", entry.timestamp)
                put("sources", JSONArray(entry.sources.toList()))
                put("message", entry.message)
            })
        }
        prefs.edit().putString(KEY_RECENT_PAYMENTS, array.toString()).apply()
    }

    // Public wrappers for SmsForegroundService
    fun parseTransactionPublic(message: String, sender: String?): TransactionData? {
        return parseTransaction(message, sender)
    }

    fun handleTransactionPublic(context: Context, transaction: TransactionData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        handleTransaction(context, prefs, transaction)
    }

    fun getPendingTransactionsPublic(context: Context): List<TransactionData> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return getPendingTransactions(prefs)
    }

    fun acceptPendingTransaction(context: Context, index: Int): TransactionData? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = getPendingTransactions(prefs).toMutableList()
        if (index !in pending.indices) return null
        val transaction = pending[index]
        if (!autoSaveTransaction(context, transaction)) return null
        pending.removeAt(index)
        if (!prefs.edit()
                .putString(KEY_PENDING_TRANSACTIONS, serializeTransactions(pending))
                .commit()
        ) return null
        val txType = if (transaction.transactionType == "credit") "income" else "expense"
        val txName = transaction.merchant.ifEmpty { transaction.category }
        val intent = Intent(AutoEvents.ACTION_AUTO_TRANSACTION)
        intent.putExtra("type", txType)
        intent.putExtra("amount", transaction.amount)
        intent.putExtra("name", txName)
        intent.putExtra("date", transaction.date)
        intent.putExtra("category", transaction.category)
        intent.putExtra("paymentMethod", transaction.paymentMethod)
        context.sendBroadcast(intent)
        return transaction
    }

    fun rejectPendingTransaction(context: Context, index: Int): TransactionData? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = getPendingTransactions(prefs).toMutableList()
        if (index !in pending.indices) return null
        val transaction = pending.removeAt(index)
        prefs.edit()
            .putString(KEY_PENDING_TRANSACTIONS, serializeTransactions(pending))
            .apply()
        // Notify app so the confirmation-waiting reminder disappears
        context.sendBroadcast(Intent(AutoEvents.ACTION_NEW_SMS_TRANSACTION))
        return transaction
    }
}

