const { canParse, parseCanara } = require('../services/canaraParser');

const sample = [
  'Statement for A/c XXXXXXXX1759 for the period 06-Aug-2025 to 05-Aug-2026',
  'DateParticularsDepositsWithdrawalsBalance',
  'Opening Balance1,751.59',
  '05-08-2025',
  'UPI/DR/521752048493/NAVI',
  'BILL/ICIC/**HARGE@ICICI/PAID',
  'Chq: 521752048493',
  '19.001,732.59',
  '05-08-2025',
  'MOB-IMPS-CR/RAMEESH',
  'KI/THE',
  'FEDERA/14612100129101/ARO',
  'Chq: 521716800204',
  '5,000.006,732.59',
  'Closing Balance36,571.37',
  'page 1'
].join('\n');

test('detects the Canara layout signature', () => {
  expect(canParse(sample)).toBe(true);
  expect(canParse('plain\nupi payment text')).toBe(false);
});

test('parses debit and credit rows into the right columns', () => {
  const txs = parseCanara(sample);
  expect(txs.length).toBe(2);
  expect(txs[0].date).toBe('2025-08-05');
  expect(txs[0].debit).toBe('19.00');
  expect(txs[0].credit).toBe('');
  expect(txs[0].balance).toBe('1732.59');
  expect(txs[0].description.toLowerCase()).toContain('upi/dr/');

  expect(txs[1].date).toBe('2025-08-05');
  expect(txs[1].credit).toBe('5000.00');
  expect(txs[1].debit).toBe('');
  expect(txs[1].balance).toBe('6732.59');
  expect(txs[1].description.toLowerCase()).toContain('mob-imps-cr');
});

test('classifies by running balance when no CR/DR marker exists', () => {
  const raw = [
    'DateParticularsDepositsWithdrawalsBalance',
    'Opening Balance1,000.00',
    '05-08-2025',
    'CASH WITHDRAWAL ATM',
    'Chq: 0',
    '200.00800.00',
    'Closing Balance800.00',
    'page 1'
  ].join('\n');
  const txs = parseCanara(raw);
  expect(txs.length).toBe(1);
  expect(txs[0].debit).toBe('200.00');
  expect(txs[0].credit).toBe('');
  expect(txs[0].balance).toBe('800.00');
});
