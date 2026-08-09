const { createObjectCsvStringifier } = require('csv-writer');

function transactionsToCSV(transactions) {
  const csvStringifier = createObjectCsvStringifier({
    header: [
      { id: 'date', title: 'Date' },
      { id: 'description', title: 'Description' },
      { id: 'debit', title: 'Debit' },
      { id: 'credit', title: 'Credit' },
      { id: 'balance', title: 'Balance' }
    ]
  });

  const header = csvStringifier.getHeaderString();
  const records = csvStringifier.stringifyRecords(transactions.map(t => ({
    date: t.date,
    description: t.description,
    debit: t.debit || '',
    credit: t.credit || '',
    balance: t.balance || ''
  })));

  return header + records;
}

module.exports = { transactionsToCSV };
