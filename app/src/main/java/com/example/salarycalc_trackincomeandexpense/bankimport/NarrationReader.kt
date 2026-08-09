package com.example.salarycalc_trackincomeandexpense.bankimport

import java.util.Locale

/**
 * Shared, bank-agnostic narration reader model.
 *
 * Extracts the real party name from a raw bank narration (UPI / IMPS / NEFT /
 * RTGS / ATM / MOB-IMPS) for any bank, hiding timestamps, cheque numbers,
 * UPI/VIA reference tokens, hex hashes, digit runs and bank codes.
 * Falls back to a generic label when the narration carries no readable name
 * (only hashes / VIA references).
 *
 * Kept as plain JVM code so it can be unit tested without an emulator.
 */
object NarrationReader {

    /**
     * Extracts the party name from a raw bank narration.
     * Returns "UPI Payment" when no readable name is present.
     */
    fun cleanNarration(raw: String): String {
        var s = raw.trim()

        // NEFT/RTGS salary credits: "NEFT CR-<utrn>- <IFSC>-<PAYER NAME>-..."
        Regex("NEFT\\s+CR\\s*-[^-]+-\\s*[^-]+-\\s*(.+)", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val p = m.groupValues[1].trim()
                .replace(Regex("[^A-Za-z0-9 &.\\-@]"), " ")
                .replace(Regex("\\s+"), " ").trim()
                .replace(Regex("[-_]{2,}.*$"), "")
                .replace(Regex("[-_]+$"), "")
                .trim()
            if (p.length >= 2) return cap(p)
        }

        // ATM withdrawals: "ATM CASH-<terminal>- <LOCATION>"
        Regex("ATM\\s+CASH\\s*-[^-]+-\\s*(.+)$", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val p = m.groupValues[1].trim()
            if (p.length >= 2 && !p.matches(Regex("\\d+"))) return cap(p)
        }

        // UPI/IMPS/NEFT/RTGS payee: the name is the segment(s) after
        // <mode>/<ref>/ up to the bank segment (the one right before the masked
        // "**" value), so names containing slashes (e.g. "M/S.MAL AB") stay whole.
        run {
            val parts = s.split('/')
            var modeIdx = -1
            for (i in parts.indices) {
                val up = parts[i].uppercase(Locale.US)
                val isMode = up == "UPI" || up == "IMPS" || up == "NEFT" || up == "RTGS" ||
                    up.endsWith(" UPI") || up.endsWith(" IMPS")
                val next = parts.getOrNull(i + 1)
                val ref = parts.getOrNull(i + 2)
                if (isMode && next != null && next.matches(Regex("DR|CR", RegexOption.IGNORE_CASE)) && !ref.isNullOrEmpty()) {
                    modeIdx = i
                    break
                }
            }
            if (modeIdx >= 0) {
                var bankIdx = -1
                for (j in modeIdx + 3 until parts.size) {
                    if (parts.getOrNull(j + 1)?.startsWith("**") == true) {
                        bankIdx = j
                        break
                    }
                }
                if (bankIdx > modeIdx + 3) {
                    val p = parts.subList(modeIdx + 3, bankIdx).joinToString("/").trim()
                        .replace(Regex("\\s+(PAID|PAYMENT|PAY TO|NA|N/A)$", RegexOption.IGNORE_CASE), "")
                        .trim()
                    if (p.length >= 2 && !p.matches(Regex("\\d+")) && !p.matches(Regex("[0-9A-Fa-f]{10,}"))) {
                        return cap(p)
                    }
                }
            }
        }

        // MOB-IMPS: name continues past the first slash until the bank segment.
        Regex("MOB-IMPS-(?:CR|DR)/(.*?)/(?:THE\\s|HDFC|ICICI|ICIC|SBI\\b|SBIN|UTIB|CNRB|YESB|FDRL|PUNB|IDIB)", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val p = m.groupValues[1].trim()
            if (p.length >= 2 && !p.matches(Regex("\\d+")) && !p.matches(Regex("[0-9A-Fa-f]{10,}"))) return cap(p)
        }
        Regex("MOB-IMPS-(?:CR|DR)/([^/\\s]+)", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val p = m.groupValues[1].trim()
            if (p.length >= 2 && !p.matches(Regex("\\d+")) && !p.matches(Regex("[0-9A-Fa-f]{10,}"))) return cap(p)
        }
        // Plain UPI/IMPS/NEFT/RTGS fallback (narration without a bank+mask marker).
        Regex("(?:UPI|IMPS|NEFT|RTGS)/(?:DR|CR)/[^/]+/([^/]+)", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val p = m.groupValues[1].trim()
                .replace(Regex("\\s+(PAID|PAYMENT|PAY TO|NA|N/A)$", RegexOption.IGNORE_CASE), "")
                .trim()
            if (p.length >= 2 && !p.matches(Regex("\\d+")) && !p.matches(Regex("[0-9A-Fa-f]{10,}"))) return cap(p)
        }

        // Strip timestamps, cheque refs, VIA refs, digit/hex runs and bank tokens.
        s = s.replace(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"), " ")
            .replace(Regex("Chq:\\s*\\d+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("VIA//[A-Z0-9]+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b\\d{6,}\\b"), " ")
            .replace(Regex("\\b[0-9A-Fa-f]{8,}\\b"), " ")
            .replace(Regex("\\b[A-Za-z0-9]{16,}\\b"), " ")
            .replace(Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}\\b"), " ")
            .replace(Regex("[^A-Za-z0-9 &.@-]"), " ")
            .replace(Regex("\\b(PAID|PAYMENT|PAY|NACH|UPI|IMPS|NEFT|RTGS|DR|CR|BILL|MISC)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty() || s.matches(Regex("^[0-9\\W]+$"))) return "UPI Payment"
        return cap(s)
    }

    private fun cap(s: String): String = if (s.length > 60) s.take(60).trim() else s

    /**
     * Returns true when the text still looks like a raw bank narration (UPI/IMPS/
     * NEFT/RTGS markers, ATM CASH, VIA refs, cheque numbers, timestamps, hex or
     * long token runs). Used to decide whether a description needs cleaning,
     * so already-clean merchant names are never over-sanitized.
     */
    fun containsRawNarration(text: String): Boolean {
        val s = text.trim()
        if (s.isEmpty()) return false
        return Regex("(?:UPI|IMPS|NEFT|RTGS)/|MOB-IMPS", RegexOption.IGNORE_CASE).containsMatchIn(s) ||
            Regex("ATM\\s+CASH", RegexOption.IGNORE_CASE).containsMatchIn(s) ||
            Regex("VIA//", RegexOption.IGNORE_CASE).containsMatchIn(s) ||
            Regex("Chq:", RegexOption.IGNORE_CASE).containsMatchIn(s) ||
            Regex("\\d{1,2}:\\d{2}(?::\\d{2})?").containsMatchIn(s) ||
            Regex("\\b[0-9A-Fa-f]{10,}\\b").containsMatchIn(s) ||
            Regex("\\b[A-Za-z0-9]{20,}\\b").containsMatchIn(s)
    }
}
