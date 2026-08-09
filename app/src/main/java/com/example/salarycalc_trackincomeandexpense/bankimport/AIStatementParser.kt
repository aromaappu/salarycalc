package com.example.salarycalc_trackincomeandexpense.bankimport

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AIStatementParser {

    private const val TAG = "AIStatementParser"

    // Gemini 1.5 Flash — free tier, fast, sufficient for structured extraction
    private const val GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    private val SYSTEM_PROMPT = """
You are a bank statement parser. Extract ALL transactions from the text below.
Return ONLY valid JSON. No markdown. No explanation. No extra text.

Required JSON format:
{
  "bank": "BANK_NAME",
  "statementPeriod": "yyyy-MM-dd to yyyy-MM-dd",
  "transactions": [
    {
      "date": "yyyy-MM-dd",
      "description": "short description",
      "amount": 1234.56,
      "type": "CREDIT",
      "balance": 5678.90,
      "reference": "REF123"
    }
  ]
}

Rules:
- type must be exactly "CREDIT" or "DEBIT"
- date must be yyyy-MM-dd format
- amount must be a positive number
- Never guess missing transactions
- Never return markdown
- Never explain
""".trimIndent()

    data class AiParseResult(
        val transactions: List<ParsedTransaction>,
        val bank: String,
        val statementPeriod: String,
        val confidence: Float
    )

    /**
     * Calls Gemini API with the raw statement text.
     * Returns null if the API key is blank or the call fails.
     */
    fun parse(rawText: String, apiKey: String): AiParseResult? {
        if (apiKey.isBlank()) return null

        // Truncate to ~12 000 chars to stay within free-tier token limits
        val truncated = if (rawText.length > 12_000) rawText.take(12_000) else rawText

        val requestBody = buildRequestBody(truncated)
        val responseJson = callGemini(requestBody, apiKey) ?: return null
        return parseGeminiResponse(responseJson)
    }

    private fun buildRequestBody(text: String): String {
        val prompt = "$SYSTEM_PROMPT\n\n---STATEMENT TEXT---\n$text"
        val escaped = JSONObject.quote(prompt)  // handles newlines, quotes
        return """{"contents":[{"parts":[{"text":$escaped}]}]}"""
    }

    private fun callGemini(body: String, apiKey: String): String? {
        return try {
            val url = URL("$GEMINI_URL?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            if (conn.responseCode != 200) {
                Log.e(TAG, "Gemini HTTP ${conn.responseCode}")
                return null
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed: ${e.message}")
            null
        }
    }

    private fun parseGeminiResponse(responseJson: String): AiParseResult? {
        return try {
            val root = JSONObject(responseJson)
            val text = root
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

            // Strip any accidental markdown fences
            val jsonText = text
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val json = JSONObject(jsonText)
            val bank = json.optString("bank", "UNKNOWN")
            val period = json.optString("statementPeriod", "")
            val txArray = json.optJSONArray("transactions") ?: return null

            val txs = mutableListOf<ParsedTransaction>()
            for (i in 0 until txArray.length()) {
                val obj = txArray.getJSONObject(i)
                val rawDate = obj.optString("date", "")
                val date = StatementNormalizer.normalizeDate(rawDate) ?: continue
                val desc = StatementNormalizer.normalizeDescription(obj.optString("description", ""))
                val amount = obj.optDouble("amount", 0.0).takeIf { it > 0 } ?: continue
                val rawType = obj.optString("type", "DEBIT")
                val type = StatementNormalizer.normalizeType(rawType) ?: continue
                val balance = obj.optDouble("balance", 0.0)
                val ref = obj.optString("reference", "").trim()
                val hash = HashGenerator.generate(date, amount, desc, ref)
                txs.add(ParsedTransaction(date, desc, amount, type, balance, ref, hash, confidence = 0.92f))
            }

            if (txs.isEmpty()) return null

            // Confidence: ratio of valid transactions parsed
            val confidence = (txs.size.toFloat() / txArray.length().coerceAtLeast(1)).coerceIn(0f, 1f)
            AiParseResult(txs, bank, period, confidence)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response: ${e.message}")
            null
        }
    }
}
