const { parseTransactions } = require('../services/parser');

test('parses broken txt with wrapped descriptions', () => {
  const raw = `01-08-2026\nUPI PAYMENT\nAMAZON INDIA\n350.00\n24500.00`;
  const tx = parseTransactions(raw);
  expect(tx.length).toBe(1);
  expect(tx[0].date).toBe('2026-08-01');
  expect(tx[0].description.toLowerCase()).toContain('upi payment');
});
