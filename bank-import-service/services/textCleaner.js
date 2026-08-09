// Clean and normalize raw text extracted from PDFs.
const DATE_RE = /\b(\d{1,4}[\/\-]\d{1,2}[\/\-]\d{1,4}|\d{1,2}\s+[A-Za-z]{3}\s+\d{4}|[A-Za-z]{3}\s+\d{1,2}\s+\d{4})\b/;

function removeHeadersAndFooters(lines) {
  // Heuristics: drop lines that look like page headers/footers
  return lines.filter(l => {
    const t = l.trim();
    if (!t) return false;
    if (/page\s*\d+/i.test(t)) return false;
    if (/^statement of account/i.test(t)) return false;
    if (/^account number/i.test(t)) return false;
    if (/^opening balance/i.test(t)) return false;
    if (/^closing balance/i.test(t)) return false;
    if (/^for more information/i.test(t)) return false;
    if (/^www\./i.test(t)) return false;
    return true;
  });
}

function mergeWrappedDescriptions(lines) {
  const merged = [];
  for (let i = 0; i < lines.length; i++) {
    const cur = lines[i].trim();
    if (!cur) continue;
    // If line does not start with a date or an amount and previous exists, merge
    if (!DATE_RE.test(cur) && merged.length > 0 && !/^[\d,\.₹£$\-\s]+$/.test(cur)) {
      merged[merged.length - 1] += ' ' + cur;
    } else {
      merged.push(cur);
    }
  }
  return merged;
}

function cleanText(raw) {
  const normalized = raw.replace(/\r/g, '\n');
  const lines = normalized.split('\n').map(l => l.replace(/\t/g, ' ').replace(/\s+/g, ' ').trim());
  const filtered = removeHeadersAndFooters(lines);
  const merged = mergeWrappedDescriptions(filtered);
  // Remove repetitive blank lines
  return merged.join('\n');
}

module.exports = { cleanText };
