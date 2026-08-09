package com.example.salarycalc_trackincomeandexpense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.salarycalc_trackincomeandexpense.data.AppDatabase
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        private const val PREFS_NAME = "SmsTrackingPrefs"
        private const val KEY_SMS_ENABLED = "smsTrackingEnabled"
        private const val KEY_CONFIRMATION_ENABLED = "smsConfirmationEnabled"
        private const val KEY_PENDING_TRANSACTIONS = "pendingTransactions"
        private const val KEY_PROCESSED_SMS_HASHES = "processedSmsHashes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // No manifest receiver uses this class anymore. SMS is now read from
        // notifications via SmsNotificationListener, so there is nothing to do.
    }

    private fun generateSmsHash(message: String, timestamp: Long): String {
        val normalized = message.lowercase().replace("\\s".toRegex(), "")
        val timeWindow = (timestamp / 60000) // Group by minute
        return "${normalized}_${timeWindow}".hashCode().toString()
    }

    private fun isDuplicate(prefs: SharedPreferences, hash: String): Boolean {
        val processedHashes = prefs.getStringSet(KEY_PROCESSED_SMS_HASHES, mutableSetOf()) ?: mutableSetOf()
        return processedHashes.contains(hash)
    }

    private fun markAsProcessed(prefs: SharedPreferences, hash: String) {
        val processedHashes = prefs.getStringSet(KEY_PROCESSED_SMS_HASHES, mutableSetOf()) ?: mutableSetOf()
        val updatedHashes = processedHashes.toMutableSet()
        updatedHashes.add(hash)

        // Keep only last 1000 hashes to avoid storage bloat
        if (updatedHashes.size > 1000) {
            val iterator = updatedHashes.iterator()
            repeat(updatedHashes.size - 1000) { iterator.next(); iterator.remove() }
        }

        prefs.edit().putStringSet(KEY_PROCESSED_SMS_HASHES, updatedHashes).apply()
    }

    private fun parseTransaction(message: String, sender: String?): TransactionData? {
        val lowerMessage = message.lowercase()
        
        // Check if it's a transaction SMS
        if (!isTransactionSms(lowerMessage)) return null
        
        // Extract all transaction details
        val amount = extractAmount(message)
        if (amount == null) return null
        
        val transactionType = determineTransactionType(lowerMessage)
        val merchant = extractMerchantName(message, sender)
        val bank = extractBankName(sender)
        val accountLast4 = extractAccountLast4(message)
        val balance = extractBalance(message)
        val referenceNumber = extractReferenceNumber(message)
        val paymentMethod = extractPaymentMethod(lowerMessage)
        val category = classifyCategory(lowerMessage, merchant, transactionType)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val description = message.take(200)
        val confidence = calculateConfidence(amount, merchant, category, paymentMethod)
        
        return TransactionData(
            isTransaction = true,
            transactionType = transactionType,
            amount = amount,
            currency = "INR",
            merchant = merchant,
            bank = bank,
            accountLast4 = accountLast4,
            balance = balance,
            referenceNumber = referenceNumber,
            paymentMethod = paymentMethod,
            category = category,
            date = date,
            time = time,
            description = description,
            confidence = confidence
        )
    }

    private fun isTransactionSms(message: String): Boolean {
        // Ignore OTP and promotional messages
        val ignoreKeywords = listOf(
            "otp", "one time password", "verification", "code", "login", "authenticate",
            "offer", "deal", "discount", "sale", "promo", "cashback", "reward",
            "delivery", "shipped", "order", "package", "courier",
            "loan", "emi", "interest rate", "apply now", "approved",
            "loyalty", "credit point", "credit points", "credit score",
            "reward point", "reward points", "loyalty point", "loyalty points",
            "points earned", "points redeemed", "redeem", "loyalty credit",
            "free", "win", "prize", "lottery", "jackpot", "congratulations",
            "claim", "bonus", "earn now", "make money", "click here",
            "unsubscribe", "stop sms", "dnd", "do not disturb"
        )
        
        if (ignoreKeywords.any { message.contains(it) }) return false
        
        // Must have at least one transaction keyword
        val transactionKeywords = listOf(
            "credited", "debited", "received", "sent", "paid", "purchase",
            "spent", "withdrawn", "deposit", "deducted", "transferred"
        )
        val hasTransactionKeyword = transactionKeywords.any { message.contains(it) }
        if (!hasTransactionKeyword) return false
        
        // And at least one bank/financial keyword to reject casual messages like "received 100rs"
        val bankKeywords = listOf(
            "account", "a/c", "bank", "upi", "neft", "imps", "rtgs",
            "balance", "avl", "available", "txn", "transaction", "ref",
            "utr", "atm", "card",             "wallet", "credited", "debited",
            "phonepe", "phone pe", "gpay", "google pay", "paytm",
            "bhim", "mobikwik", "freecharge", "amazon pay", "whatsapp pay",
            "slice", "jupiter", "fi money", "fi bank", "cRED",
            "payzapp", "olamoney", "mobiwallet", "wallet",
            "upi", "upi ref", "upi txn", "upi transaction",
            "npci", "bharatpe", "pawrh", "paysense",
            "sbi pay", "bob world", "pn b pay", "icici pay",
            "axis pay", "kotak pay", "idbi pay", "induspay"
        )
        return bankKeywords.any { message.contains(it) }
    }

    private fun extractAmount(message: String): Double? {
        // More specific patterns to avoid picking random numbers
        val patterns = listOf(
            // Match Rs./₹ followed by amount (most specific)
            Pattern.compile("Rs\\.?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("₹\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)"),
            // Match INR followed by amount
            Pattern.compile("INR\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
            // Match "amount/sum/total of Rs./₹ X"
            Pattern.compile("(?:amount|sum|total)\\s*(?:of|:)?\\s*(?:Rs\\.?|₹)?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
            // Match "debited/credited Rs./₹ X"
            Pattern.compile("(?:debited|credited|paid|received|sent|transferred)\\s*(?:by|to|from)?\\s*(?:Rs\\.?|₹)?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE),
            // Match UPI specific: "sent Rs.500" or "received Rs.500"
            Pattern.compile("(?:sent|received)\\s+(?:Rs\\.?|₹|INR)?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "")
                val amount = amountStr?.toDoubleOrNull()
                // Only return if amount is reasonable (between 1 and 10,00,000)
                if (amount != null && amount > 0 && amount < 1000000) {
                    return amount
                }
            }
        }
        return null
    }

    private fun determineTransactionType(message: String): String {
        return if (TransactionTypeClassifier.classify(message) == "CREDIT") "credit" else "debit"
    }

    private fun extractMerchantName(message: String, sender: String?): String {
        val lowerMessage = message.lowercase()

        // Salary detection
        val salaryKeywords = listOf("salary", "payroll", "wages", "monthly pay")
        val isCredit = !listOf("debited", "sent", "paid", "purchase", "spent", "withdrawn", "deducted").any { lowerMessage.contains(it) }
        if (isCredit && salaryKeywords.any { lowerMessage.contains(it) }) {
            val companyPattern = Pattern.compile("from\\s+([A-Z][A-Za-z\\s]+?)(?:\\s+via|\\s+on|\\s+credited|\\.|$)", Pattern.CASE_INSENSITIVE)
            val matcher = companyPattern.matcher(message)
            if (matcher.find()) {
                val companyName = matcher.group(1)?.trim()
                if (companyName != null && companyName.length > 2 && companyName.length < 50) return companyName
            }
            return "Salary"
        }

        // ===== STEP 1: Extract actual shop/merchant name from SMS =====
        // This catches ALL local shops: "Ravi General Store", "Sharma Medical", "Krishna Tea Stall" etc.
        val extracted = extractShopName(message)
        if (extracted.isNotEmpty()) return extracted

        // ===== STEP 2: UPI / App merchant extraction =====
        val upiExtracted = extractUpiMerchant(message)
        if (upiExtracted.isNotEmpty()) return upiExtracted

        // ===== STEP 3: Keyword-based generic fallback =====
        val keywordMatch = matchKeywordMerchant(lowerMessage)
        if (keywordMatch.isNotEmpty()) return keywordMatch

        // ===== STEP 4: Extract "at/to/from/for" patterns =====
        val atExtracted = extractAtPattern(message)
        if (atExtracted.isNotEmpty()) return atExtracted

        // ===== STEP 5: Person name (P2P transfers) =====
        val personName = extractPersonName(message)
        if (personName.isNotEmpty()) return personName

        // ===== STEP 6: Sender code mapping =====
        if (sender != null) {
            val senderMerchant = extractMerchantFromSender(sender)
            if (senderMerchant.isNotEmpty()) return senderMerchant
        }

        // ===== FINAL FALLBACK =====
        return when {
            lowerMessage.contains("salary") -> "Salary"
            lowerMessage.contains("upi") -> "UPI Transfer"
            lowerMessage.contains("atm") -> "ATM Withdrawal"
            lowerMessage.contains("wallet") -> "Wallet"
            lowerMessage.contains("bank") || lowerMessage.contains("account") -> "Bank Transfer"
            else -> "Unknown"
        }
    }

    // ===== Short merchant: "Krishna Tea Stall" → "Tea", "Ravi General Store" → "General" =====
    private fun extractShopName(message: String): String {
        // Short display names mapped from longer suffixes
        val shortNames = mapOf(
            "general store" to "General", "general shop" to "General",
            "grocery store" to "Grocery", "kirana store" to "Grocery", "kirana shop" to "Grocery",
            "medical store" to "Medical", "medical shop" to "Medical",
            "tea stall" to "Tea", "tea shop" to "Tea", "chai stall" to "Tea", "chai shop" to "Tea",
            "tea point" to "Tea", "chai point" to "Tea",
            "coffee shop" to "Coffee", "coffee house" to "Coffee", "cafe" to "Cafe",
            "sweet shop" to "Sweets", "sweet house" to "Sweets", "sweet corner" to "Sweets",
            "cake shop" to "Bakery", "bakery" to "Bakery", "bakers" to "Bakery",
            "food corner" to "Food", "food court" to "Food", "food point" to "Food", "food house" to "Food",
            "restaurant" to "Restaurant", "dhaba" to "Dhaba", "hotel" to "Hotel",
            "mess" to "Mess", "canteen" to "Canteen",
            "tiffin center" to "Tiffin", "tiffin service" to "Tiffin",
            "juice center" to "Juice", "juice point" to "Juice", "juice shop" to "Juice",
            "ice cream parlour" to "Ice Cream", "ice cream" to "Ice Cream",
            "fruit shop" to "Fruit", "fruit vendor" to "Fruit",
            "vegetable shop" to "Vegetable", "veg shop" to "Vegetable",
            "pan shop" to "Pan", "paan shop" to "Pan", "pan corner" to "Pan",
            "stationery shop" to "Stationery", "printing press" to "Printing",
            "photo copy" to "Xerox", "hardware store" to "Hardware", "hardware shop" to "Hardware",
            "paint shop" to "Paint", "cloth shop" to "Cloth", "cloth store" to "Cloth",
            "readymade" to "Cloth", "shoe shop" to "Footwear", "shoe store" to "Footwear",
            "gold shop" to "Gold", "silver shop" to "Silver",
            "gift shop" to "Gift", "gift house" to "Gift",
            "fancy store" to "Fancy", "fancy shop" to "Fancy",
            "toy shop" to "Toys", "toy store" to "Toys",
            "pet shop" to "Pet", "fish shop" to "Fish",
            "flower shop" to "Flower", "glass shop" to "Glass", "glass house" to "Glass",
            "tyre shop" to "Tyre", "tyre store" to "Tyre", "puncture shop" to "Puncture",
            "water purifier" to "Water", "water filter" to "Water", "ro service" to "Water",
            "mobile shop" to "Mobile", "mobile store" to "Mobile", "mobile repair" to "Mobile",
            "phone shop" to "Mobile", "computer shop" to "Computer", "laptop shop" to "Computer",
            "computer repair" to "Computer", "internet cafe" to "Internet", "cyber cafe" to "Internet",
            "typing center" to "Typing", "photo studio" to "Studio", "video studio" to "Studio",
            "beauty parlour" to "Salon", "beauty salon" to "Salon", "hair salon" to "Salon",
            "coaching center" to "Coaching",
            "traders" to "Traders", "trading" to "Trading",
            "enterprises" to "Enterprises", "enterprise" to "Enterprises",
            "emporium" to "Emporium", "bhandar" to "Bhandar",
            "bhawan" to "Bhawan", "nivas" to "Nivas", "palace" to "Palace",
            "bazaar" to "Bazaar", "mandi" to "Mandi", "dukan" to "Dukan",
            "mart" to "Mart", "hub" to "Hub", "agency" to "Agency",
            "corporation" to "Corp", "company" to "Co",
            "& co" to "Co", "and co" to "Co", "& sons" to "Sons", "and sons" to "Sons",
            "brothers" to "Brothers",
            "gym" to "Gym", "fitness" to "Fitness", "clinic" to "Clinic",
            "mithai" to "Sweets", "sweets" to "Sweets"
        )

        // Build regex-safe suffix list (longest first)
        val allSuffixes = shortNames.keys.toList().sortedByDescending { it.length }

        // 1. After payment keywords: "paid to Ravi General Store via UPI"
        val paymentPrefixes = listOf("paid to", "sent to", "payment to", "transferred to", "at", "to", "for", "from")
        for (prefix in paymentPrefixes) {
            for (suffix in allSuffixes) {
                val pattern = Pattern.compile(
                    "${Pattern.quote(prefix)}\\s+(?:[A-Za-z][A-Za-z0-9&'.]*\\s+)*${Pattern.quote(suffix)}(?:\\s+(?:via|upi|on|ref|txn|\\d)|\\s*\\.|\$)",
                    Pattern.CASE_INSENSITIVE
                )
                val matcher = pattern.matcher(message)
                if (matcher.find()) {
                    val short = shortNames[suffix.lowercase()]
                    if (short != null) return short
                }
            }
        }

        // 2. Standalone: "Ravi General Store UPI payment"
        for (suffix in allSuffixes) {
            val pattern = Pattern.compile(
                "(?:[A-Za-z][A-Za-z0-9&'.]*\\s+)*${Pattern.quote(suffix)}(?:\\s+(?:via|upi|on|ref|txn|payment|of|rs|₹|\\d)|\\s*\\.|\$)",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val short = shortNames[suffix.lowercase()]
                if (short != null) return short
            }
        }

        // 3. VPA handle: "XYZ@ybl"
        val vpaPattern = Pattern.compile("([A-Za-z0-9._]+)@[a-z]+", Pattern.CASE_INSENSITIVE)
        val vpaMatcher = vpaPattern.matcher(message)
        if (vpaMatcher.find()) {
            val vpa = vpaMatcher.group(1) ?: ""
            if (vpa.length >= 3 && !listOf("paytm", "phonepe", "gpay", "google", "amazon", "bhim").any { vpa.lowercase().contains(it) }) {
                return capitalizeMerchant(vpa.replace(".", " ").replace("_", " "))
            }
        }

        return ""
    }

    private fun cleanMerchantName(raw: String): String {
        var cleaned = raw.trim()
        // Remove trailing noise words
        cleaned = cleaned.replace(Regex("\\s*(via|upi|on|ref|txn|payment|of|for|rs\\.?|₹|\\d+).*$", RegexOption.IGNORE_CASE), "").trim()
        // Remove leading noise
        cleaned = cleaned.replace(Regex("^(paid|sent|transferred|payment|at|to|for|from|\\$)\\s+", RegexOption.IGNORE_CASE), "").trim()
        // Remove special chars except &, ', .
        cleaned = cleaned.replace(Regex("[^A-Za-z0-9\\s.&']"), "").trim()
        return cleaned
    }

    private fun isCommonFalsePositive(name: String): Boolean {
        val lower = name.lowercase()
        val falsePositives = listOf("account", "balance", "available", "debit", "credit", "card", "bank", "upi", "the")
        return falsePositives.any { lower == it } || lower.length < 3
    }

    // ===== UPI merchant extraction =====
    private fun extractUpiMerchant(message: String): String {
        val upiPatterns = listOf(
            // "paid to Merchant Name via UPI"
            Pattern.compile("(?:paid|sent|transferred)\\s+to\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)(?:\\s+via|\\s+upi|\\s+on|\\s+ref|\\s+txn|\\.|$)", Pattern.CASE_INSENSITIVE),
            // "to Merchant Name via Google Pay"
            Pattern.compile("to\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)(?:\\s+on\\s+upi|\\s+via|\\s+ref|\\.|$)", Pattern.CASE_INSENSITIVE),
            // "UPI to Merchant Name"
            Pattern.compile("upi\\s+to\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)(?:\\s+ref|\\s+on|\\.|$)", Pattern.CASE_INSENSITIVE),
            // "Merchant Name/UPI/ref"
            Pattern.compile("([A-Za-z][A-Za-z0-9\\s]+?)/UPI/(?:[A-Z0-9]+)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in upiPatterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val merchant = matcher.group(1)?.trim() ?: continue
                val cleaned = cleanMerchantName(merchant)
                if (cleaned.length >= 2 && !isCommonFalsePositive(cleaned)) {
                    return capitalizeMerchant(cleaned)
                }
            }
        }

        // Google Pay / PhonePe / Paytm specific
        val appPatterns = listOf(
            Pattern.compile("(?:google\\s*pay|gpay|phonepe|phone\\s*pay|paytm|bhim)\\s+(?:to|->)\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)(?:\\s|$|\\.|ref)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([A-Za-z][A-Za-z0-9\\s.&']+?)\\s+via\\s+(?:google\\s*pay|gpay|phonepe|phone\\s*pay|paytm|bhim)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("at\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)\\s+via\\s+(?:upi|google|phonepe|paytm)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in appPatterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val merchant = matcher.group(1)?.trim() ?: continue
                val cleaned = cleanMerchantName(merchant)
                if (cleaned.length >= 2 && !isCommonFalsePositive(cleaned)) {
                    return capitalizeMerchant(cleaned)
                }
            }
        }

        return ""
    }

    // ===== Keyword-based generic merchant matching =====
    private fun matchKeywordMerchant(lowerMessage: String): String {
        val merchantKeywordMap = mapOf(
            "chai" to "Tea", "tea" to "Tea", "tapri" to "Tea",
            "coffee" to "Coffee", "cafe" to "Cafe", "starbucks" to "Coffee",
            "ccd" to "Coffee", "barista" to "Coffee",
            "restaurant" to "Restaurant", "hotel" to "Hotel", "dhaba" to "Dhaba",
            "food" to "Food", "zomato" to "Food", "swiggy" to "Food",
            "pizza" to "Food", "biryani" to "Restaurant",
            "juice" to "Juice", "ice cream" to "Ice Cream",
            "grocery" to "Grocery", "kirana" to "Grocery", "supermarket" to "Grocery",
            "bigbasket" to "Grocery", "blinkit" to "Grocery", "zepto" to "Grocery",
            "dmart" to "Grocery", "reliance" to "Grocery", "more" to "Grocery",
            "vegetable" to "Grocery", "fruit" to "Grocery",
            "amazon" to "Shopping", "flipkart" to "Shopping", "myntra" to "Shopping",
            "meesho" to "Shopping", "ajio" to "Shopping", "nykaa" to "Shopping",
            "store" to "Store", "shop" to "Shop", "mall" to "Shopping", "market" to "Market",
            "petrol" to "Fuel", "fuel" to "Fuel", "bpcl" to "Fuel",
            "hpcl" to "Fuel", "iocl" to "Fuel", "shell" to "Fuel",
            "gas" to "Fuel", "diesel" to "Fuel", "pump" to "Fuel",
            "pharmacy" to "Medical", "medical" to "Medical", "medicine" to "Medical",
            "hospital" to "Hospital", "doctor" to "Doctor", "clinic" to "Clinic",
            "apollo" to "Medical", "medplus" to "Medical", "netmeds" to "Medical",
            "1mg" to "Medical", "pharmeasy" to "Medical",
            "movie" to "Movie", "cinema" to "Cinema", "theatre" to "Movie",
            "netflix" to "Subscription", "hotstar" to "Subscription", "prime" to "Subscription",
            "spotify" to "Subscription", "youtube" to "Subscription",
            "electricity" to "Electricity", "water" to "Water Bill",
            "mobile" to "Recharge", "recharge" to "Recharge",
            "airtel" to "Recharge", "jio" to "Recharge", "vi" to "Recharge", "bsnl" to "Recharge",
            "internet" to "Internet", "broadband" to "Internet", "wifi" to "Internet",
            "gas bill" to "Gas Bill", "lpg" to "Gas Bill",
            "education" to "Education", "school" to "Education", "college" to "Education",
            "tuition" to "Education", "course" to "Education",
            "uber" to "Travel", "ola" to "Travel", "rapido" to "Travel",
            "irctc" to "Travel", "redbus" to "Travel", "makemytrip" to "Travel",
            "flight" to "Travel", "train" to "Travel", "bus" to "Travel",
            "taxi" to "Travel", "auto" to "Travel", "cab" to "Travel",
            "atm" to "ATM", "withdraw" to "ATM",
            "bank" to "Transfer", "neft" to "Transfer", "imps" to "Transfer", "rtgs" to "Transfer",
            "insurance" to "Insurance", "premium" to "Insurance",
            "tax" to "Tax", "gst" to "Tax",
            "paytm" to "Wallet", "phonepe" to "UPI", "google pay" to "UPI",
            "gpay" to "UPI", "bhim" to "UPI", "mobikwik" to "Wallet",
            "emi" to "EMI", "loan" to "Loan",
            "subscription" to "Subscription", "membership" to "Subscription"
        )
        for ((keyword, merchantName) in merchantKeywordMap) {
            if (lowerMessage.contains(keyword)) {
                return merchantName
            }
        }
        return ""
    }

    // ===== Extract "at/to/from/for" patterns =====
    private fun extractAtPattern(message: String): String {
        val extractPatterns = listOf(
            Pattern.compile("at\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)(?:\\s+on|\\s+for|\\s+ref|\\s+txn|\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("to\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)(?:\\s+on|\\s+via|\\s+ref|\\s+txn|\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("from\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)(?:\\s+on|\\s+via|\\s+ref|\\s+txn|\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("for\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)(?:\\s+on|\\s+via|\\s+ref|\\s+txn|\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:paid|spent|purchase|payment)\\s+(?:at|to)\\s+([A-Za-z][A-Za-z0-9\\s.&']+?)(?:\\s|$|\\.|ref)", Pattern.CASE_INSENSITIVE)
        )

        val falsePositives = listOf("account", "balance", "available", "debit", "credit", "card", "bank", "the")
        for (pattern in extractPatterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val merchantName = matcher.group(1)?.trim() ?: continue
                if (merchantName.length in 2..60 && falsePositives.none { merchantName.lowercase().contains(it) }) {
                    return capitalizeMerchant(merchantName)
                }
            }
        }
        return ""
    }

    // ===== Person name detection (P2P transfers) =====
    private fun extractPersonName(message: String): String {
        val personPatterns = listOf(
            Pattern.compile("(?:from|received from|transfer from|sent by)\\s+([A-Z][a-z]+\\s+[A-Z][a-z]+)(?:\\s+via|\\s+on|\\s+ref|\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("to\\s+([A-Z][a-z]+\\s+[A-Z][a-z]+)(?:\\s+via|\\s+on|\\s+upi|\\.|$)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in personPatterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val name = matcher.group(1)?.trim() ?: continue
                if (name.length in 4..50) {
                    val words = name.split(" ")
                    if (words.all { it.isNotEmpty() && it[0].isUpperCase() }) {
                        return name
                    }
                }
            }
        }
        return ""
    }

    private fun capitalizeMerchant(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9\\s.&']"), "").trim()
        if (cleaned.isEmpty()) return "Unknown"
        // Don't capitalize if it looks like a code/all-caps abbreviation
        if (cleaned.all { !it.isLetter() || it.isUpperCase() } && cleaned.length <= 5) return cleaned
        return cleaned.split(" ").joinToString(" ") { word ->
            if (word.length <= 2 && word.all { it.isUpperCase() }) word
            else word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun extractMerchantFromSender(sender: String): String {
        val lowerSender = sender.lowercase().replace("[^a-z0-9]".toRegex(), "")
        // Map known sender codes to merchant names
        val senderMerchantMap = mapOf(
            "adpay" to "AD Pay", "payu" to "PayU", "razorpay" to "Razorpay",
            "snapay" to "SnaPay", "snapayin" to "SnaPay",
            "bfl" to "Bajaj Finserv", "bajaj" to "Bajaj Finserv",
            "csb" to "CSB Bank", "federal" to "Federal Bank",
            "idfc" to "IDFC First Bank", "indusind" to "IndusInd Bank",
            "yesbank" to "Yes Bank", "kotak" to "Kotak Bank",
            "bob" to "Bank of Baroda", "canara" to "Canara Bank",
            "union" to "Union Bank", "central" to "Central Bank",
            "uco" to "UCO Bank", "indian" to "Indian Bank",
            "punjab" to "PNB", "state" to "SBI", "sbi" to "SBI",
            "hdfc" to "HDFC Bank", "icici" to "ICICI Bank",
            "axis" to "Axis Bank"
        )
        for ((key, name) in senderMerchantMap) {
            if (lowerSender.contains(key)) return name
        }
        return ""
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

    private fun extractAccountLast4(message: String): String {
        val pattern = Pattern.compile("(?:account|a/c|xx|\\*\\*\\*)\\*?(\\d{4})(?:\\D|$)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(message)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }
        return ""
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

    private fun extractReferenceNumber(message: String): String {
        val patterns = listOf(
            Pattern.compile("(?:ref|reference|txn|transaction|id)\\s*(?:no|number)?\\s*[:#]?\\s*([A-Z0-9]{8,})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:utr|upi\\s+ref)\\s*[:#]?\\s*([A-Z0-9]{8,})", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                return matcher.group(1) ?: ""
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
            // Skip if an identical transaction is already waiting
            if (pending.any { isSameTransaction(it, transaction) }) return
            pending.add(transaction)
            prefs.edit()
                .putString(KEY_PENDING_TRANSACTIONS, serializeTransactions(pending))
                .apply()
            NotificationHelper.showTransactionNotification(context, transaction)
            // Notify app to load pending transactions
            context.sendBroadcast(Intent(AutoEvents.ACTION_NEW_SMS_TRANSACTION))
        } else {
            // Auto-save: persist to store AND broadcast for live app
            autoSaveTransaction(context, transaction)
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

    private fun isSameTransaction(a: TransactionData, b: TransactionData): Boolean {
        return a.amount == b.amount &&
            a.merchant == b.merchant &&
            a.date == b.date &&
            a.time == b.time
    }

    private fun autoSaveTransaction(context: Context, transaction: TransactionData) {
        // Write straight into the database so the transaction is added
        // immediately (no app restart or broadcast listener needed).
        val txType = if (transaction.transactionType == "credit") "income" else "expense"
        val txName = transaction.merchant.ifEmpty { transaction.category }
        val date = transaction.date.ifBlank {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        }
        val db = AppDatabase(context)
        // Skip if an identical transaction already exists — guards against the
        // same notification being re-posted by the phone (e.g. re-delivered SMS).
        val exists = db.getTransactions(type = txType).any {
            it.amount == transaction.amount &&
                it.name.equals(txName, ignoreCase = true) &&
                it.date == date
        }
        if (exists) return

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
        )
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
    fun processNotificationTransaction(context: Context, body: String, sender: String?, timestamp: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val smsHash = generateSmsHash(body, timestamp)
        if (isDuplicate(prefs, smsHash)) return false
        val transaction = parseTransaction(body, sender)
        if (transaction != null) {
            markAsProcessed(prefs, smsHash)
            handleTransaction(context, prefs, transaction)
            return true
        }
        return false
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
        val transaction = pending.removeAt(index)
        prefs.edit()
            .putString(KEY_PENDING_TRANSACTIONS, serializeTransactions(pending))
            .apply()
        autoSaveTransaction(context, transaction)
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
