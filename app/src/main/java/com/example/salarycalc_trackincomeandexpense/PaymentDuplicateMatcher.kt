package com.example.salarycalc_trackincomeandexpense

import java.util.Locale
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToLong

/** Prefer reference IDs; use a short, cross-source window only when an ID is absent. */
object PaymentDuplicateMatcher {
    data class Entry(
        val amountPaise: Long,
        val type: String,
        val name: String,
        val reference: String,
        val timestamp: Long,
        val sources: Set<String>,
        val message: String
    )

    fun entry(payment: PaymentNotificationParser.Payment, timestamp: Long, source: String, message: String) = Entry(
        (payment.amount * 100).roundToLong(), payment.type, payment.name,
        payment.reference, timestamp, setOf(source), MessageDigest.getInstance("SHA-256")
            .digest(normalize(message).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    )

    fun matches(old: Entry, incoming: Entry): Boolean {
        if (old.amountPaise != incoming.amountPaise || old.type != incoming.type) return false
        val elapsed = abs(old.timestamp - incoming.timestamp)
        if (old.reference.isNotBlank() && incoming.reference.isNotBlank()) {
            return old.reference.equals(incoming.reference, ignoreCase = true) && elapsed <= 7L * 24 * 60 * 60 * 1000
        }
        // A repost of the same notification retains its posting time. Do not suppress
        // a new same-source payment just because its amount/name/body is identical.
        if (old.sources.intersect(incoming.sources).isNotEmpty()) {
            return old.message == incoming.message && old.timestamp == incoming.timestamp
        }
        if (elapsed > 90_000) return false
        val oldName = normalize(old.name)
        val newName = normalize(incoming.name)
        val unknownNames = setOf("payment received", "payment sent")
        return oldName == newName || oldName in unknownNames || newName in unknownNames
    }

    private fun normalize(value: String) = value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
}
