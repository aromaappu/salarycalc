// Simple bank detector with plugin architecture
const detectors = [];

function registerDetector(fn) { detectors.push(fn); }

function detectBank(text) {
  for (const fn of detectors) {
    try {
      const r = fn(text);
      if (r) return r;
    } catch (e) {}
  }
  // built-in heuristics
  if (/state bank|sbi/i.test(text)) return 'SBI';
  if (/hdfc/i.test(text)) return 'HDFC';
  if (/icici/i.test(text)) return 'ICICI';
  return 'Unknown';
}

module.exports = { detectBank, registerDetector };
