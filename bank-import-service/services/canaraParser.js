// Layout-aware parser for Canara Bank PDF statements.
// pdf-parse loses column positions, so each row of this format extracts as:
//   <date DD-MM-YYYY>
//   <particulars lines ...>
//   Chq: <ref>
//   <amount><balance>   e.g. "19.001,732.59" (debit) or "5,000.006,732.59" (credit)
const moment = require('moment');

const DATE_LINE_RE = /^(\d{2}-\d{2}-\d{4})$/;
const AMOUNT_PAIR_RE = /^([\d,]+\.\d{2})([\d,]+\.\d{2})$/;
const DEBIT_RE = /(?:UPI\/)?DR\/|\bDR\b|Debit/i;
const CREDIT_RE = /(?:UPI\/)?CR\/|MOB-IMPS-CR|\bCR\b|Credit/i;

function parseNum(s) {
  const n = Number(String(s).replace(/[,\s]/g, ''));
  return isNaN(n) ? null : n;
}

function fmt(n) {
  const v = Number(n);
  return isNaN(v) ? '' : v.toFixed(2);
}

function round2(n) {
  return Math.round(n * 100) / 100;
}

function normalizeDate(s) {
  const m = moment(s, 'DD-MM-YYYY', true);
  return m.isValid() ? m.format('YYYY-MM-DD') : null;
}

function classify(amount, balance, prevBalance, desc) {
  const debitMark = DEBIT_RE.test(desc);
  const creditMark = CREDIT_RE.test(desc);
  if (debitMark && !creditMark) return { debit: amount, credit: '' };
  if (creditMark && !debitMark) return { debit: '', credit: amount };
  // Fallback: infer from the running balance chain (layout alignment).
  if (prevBalance !== null && balance !== null) {
    if (Math.abs(balance - round2(prevBalance + amount)) < 0.005) return { debit: '', credit: amount };
    if (Math.abs(balance - round2(prevBalance - amount)) < 0.005) return { debit: amount, credit: '' };
  }
  return { debit: amount, credit: '' };
}

// Only engage when the raw text matches the Canara statement signature,
// otherwise fall back to the generic parser.
function canParse(raw) {
  const text = String(raw || '');
  const compact = text.replace(/\s+/g, '');
  return /DateParticularsDepositsWithdrawalsBalance/.test(compact)
    && /OpeningBalance[\d,]+\.\d{2}/.test(compact)
    && /Chq:/.test(text);
}

function parseCanara(raw) {
  const lines = String(raw || '').split(/\r?\n/).map(l => l.trim()).filter(Boolean);
  const transactions = [];
  let prevBalance = null;
  let current = null;

  const pushCurrent = () => {
    if (!current || current.amount === null) return;
    const date = normalizeDate(current.date);
    if (!date) return;
    const description = (current.desc.join(' ') || '').replace(/\s+/g, ' ').trim();
    const { debit, credit } = classify(current.amount, current.balance, prevBalance, description);
    transactions.push({
      date,
      description: description || '(no description)',
      debit: debit ? fmt(debit) : '',
      credit: credit ? fmt(credit) : '',
      balance: fmt(current.balance)
    });
    if (current.balance !== null) prevBalance = current.balance;
  };

  for (const line of lines) {
    if (/^page\s*\d+$/i.test(line)) continue;
    if (/^Statement for A\/c/i.test(line)) continue;

    const ob = line.match(/^Opening\s*Balance\s*([\d,]+\.\d{2})/i);
    if (ob) {
      const v = parseNum(ob[1]);
      if (v !== null) prevBalance = v;
      continue;
    }
    if (/^Closing\s*Balance/i.test(line)) continue;
    if (line.replace(/\s+/g, '') === 'DateParticularsDepositsWithdrawalsBalance') continue;

    const dateMatch = line.match(DATE_LINE_RE);
    if (dateMatch) {
      pushCurrent();
      current = { date: dateMatch[1], desc: [], amount: null, balance: null };
      continue;
    }

    if (current) {
      const pair = line.match(AMOUNT_PAIR_RE);
      if (pair) {
        current.amount = parseNum(pair[1]);
        current.balance = parseNum(pair[2]);
        continue;
      }
      // Amounts row terminates the record; ignore trailing footer lines.
      if (current.amount === null) current.desc.push(line);
    }
  }
  pushCurrent();

  return transactions;
}

module.exports = { canParse, parseCanara };
