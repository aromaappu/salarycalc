// Parse cleaned text into structured transactions.
const moment = require('moment');

const DATE_PATTERNS = [
  'DD/MM/YYYY','DD-MM-YYYY','YYYY-MM-DD','DD MMM YYYY','MMM DD YYYY'
];

const AMOUNT_RE = /([₹$£]?\s?[\d,]+(?:\.\d{1,2})?)\s*(CR|DR|Debit|Credit)?/i;
const DATE_RE = /(\d{1,4}[\/\-]\d{1,2}[\/\-]\d{1,4}|\d{1,2}\s+[A-Za-z]{3}\s+\d{4}|[A-Za-z]{3}\s+\d{1,2}\s+\d{4})/;

function normalizeDate(s) {
  const trimmed = s.trim();
  for (const pattern of DATE_PATTERNS) {
    const m = moment(trimmed, pattern, true);
    if (m.isValid()) return m.format('YYYY-MM-DD');
  }
  // try auto-parse
  const m = moment(trimmed);
  if (m.isValid()) return m.format('YYYY-MM-DD');
  return null;
}

function parseTransactions(text) {
  const lines = text.split('\n').map(l => l.trim()).filter(Boolean);
  const transactions = [];

  let buffer = null;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const dateMatch = line.match(DATE_RE);
    if (dateMatch) {
      // start new transaction
      if (buffer) {
        const tx = finalizeBuffer(buffer);
        if (tx) transactions.push(tx);
      }
      buffer = { date: dateMatch[0], rest: line.replace(dateMatch[0], '').trim() };
    } else if (buffer) {
      buffer.rest = (buffer.rest + ' ' + line).trim();
    } else {
      // orphan line — try to attach to last transaction
      if (transactions.length > 0) {
        transactions[transactions.length - 1].description += ' ' + line;
      }
    }
  }
  if (buffer) {
    const tx = finalizeBuffer(buffer);
    if (tx) transactions.push(tx);
  }

  return transactions.map(normalizeTransaction).filter(Boolean);
}

function finalizeBuffer(buf) {
  return { date: buf.date, body: buf.rest };
}

function extractAmounts(s) {
  // find all amount-like tokens
  const tokens = s.match(/([₹$£]?\s?[\d,]+(?:\.\d{1,2})?)/g) || [];
  return tokens.map(t => t.replace(/[₹$£,\s]/g, '')).filter(Boolean);
}

function normalizeTransaction(tx) {
  const date = normalizeDate(tx.date || '');
  if (!date) return null;
  let parts = tx.body.split('\t').join(' ').split('  ').join(' ').trim();
  // Look for amounts at end
  const amtMatches = extractAmounts(parts);

  let debit = '';
  let credit = '';
  let balance = '';
  if (amtMatches.length === 1) {
    // ambiguous — treat as debit
    debit = amtMatches[0];
    parts = parts.replace(new RegExp(amtMatches[0].replace(/([.*+?^=!:${}()|[\]\/\\])/g,'\\$1')), '').trim();
  } else if (amtMatches.length >= 2) {
    // assume last is balance, previous is amount
    balance = amtMatches[amtMatches.length - 1];
    const amount = amtMatches[amtMatches.length - 2];
    // decide debit/credit by presence of CR/DR markers
    if (/CR/i.test(tx.body)) credit = amount; else debit = amount;
  }

  const description = parts.replace(/([₹$£]?\s?[\d,]+(?:\.\d{1,2})?)/g, '').replace(/CR|DR|Debit|Credit/ig,'').trim();

  // validate
  if (!date || (!debit && !credit)) return null;

  return {
    date,
    description: description || '(no description)',
    debit: debit || '',
    credit: credit || '',
    balance: balance || ''
  };
}

function detectBank(text) {
  if (/state bank|sbi/i.test(text)) return 'SBI-like';
  if (/hdfc/i.test(text)) return 'HDFC-like';
  if (/icici/i.test(text)) return 'ICICI-like';
  return 'Unknown';
}

module.exports = { parseTransactions, detectBank };
