package com.example.salarycalc_trackincomeandexpense.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Best-effort migration of the old WebView app's localStorage (LevelDB memtable)
// into the native SQLite database. Runs only when the DB is still empty and only
// commits when the legacy data parses cleanly; otherwise it does nothing and no
// data is touched. The guaranteed path for moving data is the JSON backup/restore.
object LegacyLocalStorageMigrator {

    private val KNOWN_KEYS = setOf(
        "salaryTransactions", "billReminders", "customSources", "customCategories",
        "savedNotes", "activeMonthKey", "darkMode", "pinEnabled", "savedPin",
        "autoIncomes", "autoExpenses", "deletedAutoIncomeMonths", "deletedAutoExpenseMonths",
        "currency", "smsTrackingEnabled", "smsConfirmationEnabled"
    )

    fun tryMigrate(context: Context, db: AppDatabase, settings: SettingsStore): Boolean {
        return try {
            if (db.getAllTransactions().isNotEmpty()) return false

            val extracted = extractFromLevelDb(context) ?: return false
            val transactionsJson = extracted["salaryTransactions"] ?: return false

            // Validate that the legacy data is real before touching anything.
            JSONArray(transactionsJson)

            val backup = buildBackupJson(extracted, transactionsJson)
            BackupUtil.importBackupJson(context, db, settings, backup)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun extractFromLevelDb(context: Context): Map<String, String>? {
        val appWebview = File(context.applicationInfo.dataDir, "app_webview")
        val candidates = listOf(
            File(appWebview, "Local Storage/leveldb"),
            File(appWebview, "Default/Local Storage/leveldb")
        )

        val extracted = mutableMapOf<String, String>()
        for (dir in candidates) {
            if (!dir.isDirectory) continue
            val logs = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                ?: continue
            for (log in logs) {
                parseLogFile(log, extracted)
                if (extracted.containsKey("salaryTransactions")) break
            }
            if (extracted.containsKey("salaryTransactions")) break
        }
        return if (extracted.containsKey("salaryTransactions")) extracted else null
    }

    private fun parseLogFile(file: File, out: MutableMap<String, String>) {
        val bytes = file.readBytes()
        var offset = 0
        while (offset + 7 <= bytes.size) {
            val length = (bytes[offset + 4].toInt() and 0xFF) or
                ((bytes[offset + 5].toInt() and 0xFF) shl 8)
            val type = bytes[offset + 6].toInt() and 0xFF
            offset += 7

            when (type) {
                0 -> { // zero filler -> skip to next 32KB block
                    val blockStart = ((offset - 7) / BLOCK_SIZE + 1) * BLOCK_SIZE
                    offset = maxOf(offset, blockStart)
                    continue
                }
                5, 6 -> return // EOF / bad record
            }
            if (type > 4) return
            if (length == 0 || offset + length > bytes.size) return

            val payload = bytes.copyOfRange(offset, offset + length)
            offset += length

            if (type == 1) { // kFullType
                parseWriteBatch(payload, out)
            }
        }
    }

    // A log record payload is a serialized WriteBatch:
    //   sequence (8 bytes LE) + count (4 bytes LE) + entries
    private fun parseWriteBatch(payload: ByteArray, out: MutableMap<String, String>) {
        if (payload.size < 12) return
        val count = littleEndian32(payload, 8)
        var p = 12
        repeat(count) {
            if (p >= payload.size) return
            val type = payload[p].toInt() and 0xFF
            p += 1
            if (type == 1) { // Put
                val (klenL, p1) = readVarint(payload, p) ?: return
                val klen = klenL.toInt()
                p = p1
                if (p + klen > payload.size) return
                val key = payload.copyOfRange(p, p + klen)
                p += klen
                val (vlenL, p2) = readVarint(payload, p) ?: return
                val vlen = vlenL.toInt()
                p = p2
                if (p + vlen > payload.size) return
                val value = payload.copyOfRange(p, p + vlen)
                p += vlen
                handleEntry(key, value, out)
            } else if (type == 0) { // Delete
                val (klenL, p1) = readVarint(payload, p) ?: return
                p = p1 + klenL.toInt()
            } else {
                return
            }
        }
    }

    private fun handleEntry(keyBytes: ByteArray, valueBytes: ByteArray, out: MutableMap<String, String>) {
        // Chromium localStorage key = <origin>\x00<storageKey>
        val nul = keyBytes.lastIndexOf(0)
        if (nul < 0 || nul == keyBytes.size - 1) return
        val storageKey = String(keyBytes, nul + 1, keyBytes.size - nul - 1, Charsets.UTF_8)
        if (storageKey !in KNOWN_KEYS) return
        val decoded = decodeLocalStorageValue(valueBytes) ?: return
        out[storageKey] = decoded
    }

    private fun decodeLocalStorageValue(b: ByteArray): String? {
        return try {
            if (b.size >= 5 && (b[0].toInt() and 0xFF) == 1) {
                val len = bigEndian32(b, 1)
                if (len > 0 && 5 + len * 2 <= b.size) {
                    String(b, 5, len * 2, Charsets.UTF_16LE)
                } else if (b.size > 5) {
                    String(b, 5, b.size - 5, Charsets.UTF_16LE)
                } else {
                    null
                }
            } else if (b.size >= 2) {
                String(b, Charsets.UTF_16LE)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildBackupJson(e: Map<String, String>, transactionsJson: String): String {
        val o = JSONObject()
        o.put("transactions", JSONArray(transactionsJson))
        e["billReminders"]?.let { o.put("reminders", JSONArray(it)) }
        e["customSources"]?.let { o.put("customSources", JSONArray(it)) }
        e["customCategories"]?.let { o.put("customCategories", JSONArray(it)) }
        e["savedNotes"]?.let { o.put("savedNotes", it) }
        e["activeMonthKey"]?.let { o.put("activeMonthKey", it) }
        e["darkMode"]?.let { o.put("darkMode", it.toBoolean()) }
        e["pinEnabled"]?.let { o.put("pinEnabled", it.toBoolean()) }
        e["savedPin"]?.let { o.put("savedPin", it) }
        e["autoIncomes"]?.let { o.put("autoIncomes", JSONArray(it)) }
        e["autoExpenses"]?.let { o.put("autoExpenses", JSONArray(it)) }
        e["deletedAutoIncomeMonths"]?.let { o.put("deletedAutoIncomeMonths", JSONArray(it)) }
        e["deletedAutoExpenseMonths"]?.let { o.put("deletedAutoExpenseMonths", JSONArray(it)) }
        e["currency"]?.let { o.put("currency", JSONObject(it)) }
        return o.toString()
    }

    private fun readVarint(b: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var p = start
        while (p < b.size && shift < 64) {
            val x = b[p].toInt() and 0xFF
            p++
            result = result or ((x and 0x7F).toLong() shl shift)
            if (x and 0x80 == 0) return result to p
            shift += 7
        }
        return null
    }

    private fun littleEndian32(b: ByteArray, offset: Int): Int {
        return (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun bigEndian32(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
    }

    private const val BLOCK_SIZE = 32768
}
