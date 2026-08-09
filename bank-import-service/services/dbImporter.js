const { ensureDb } = require('../database/db');
const { makeKey } = require('./duplicateChecker');

function toNumber(s) {
  if (!s) return null;
  const n = Number(String(s).replace(/[^0-9\.\-]+/g, ''));
  return isNaN(n) ? null : n;
}

async function importFromCSVString(csvString, opts = {}) {
  const db = ensureDb();
  const lines = csvString.split('\n').map(l => l.trim()).filter(Boolean);
  const header = lines.shift();
  const results = { imported: 0, duplicates: 0, errors: [] };

  // insert import metadata row
  const importMeta = {
    source_filename: opts.sourceFilename || null,
    imported_at: new Date().toISOString(),
    total: lines.length,
    imported: 0,
    duplicates: 0,
    errors: 0
  };
  const importId = await new Promise((resolve, reject) => {
    db.run('INSERT INTO imports (source_filename, imported_at, total, imported, duplicates, errors) VALUES (?,?,?,?,?,?)',
      [importMeta.source_filename, importMeta.imported_at, importMeta.total, 0, 0, 0], function(err) {
        if (err) return reject(err);
        resolve(this.lastID);
      });
  });

  for (const line of lines) {
    try {
      const parts = line.split(',');
      // Expect exactly 5 columns
      if (parts.length < 5) {
        results.errors.push('Malformed CSV line: ' + line);
        continue;
      }
      const [date, description, debit, credit, balance] = parts.map(p => p.trim());
      if (!date || (!debit && !credit)) {
        results.errors.push('Invalid transaction row: ' + line);
        continue;
      }

      const tx = { date, description, debit, credit, balance };
      const key = makeKey(tx);

      const exists = await new Promise((resolve) => {
        db.get('SELECT 1 FROM transactions WHERE unique_key = ?', [key], (err, row) => {
          resolve(!!row);
        });
      });

      if (exists) {
        results.duplicates += 1;
        importMeta.duplicates += 1;
        continue;
      }

      const stmt = db.prepare('INSERT INTO transactions (date,description,debit,credit,balance,unique_key,import_id) VALUES (?,?,?,?,?,?,?)');
      await new Promise((resolve, reject) => {
        stmt.run(date, description, toNumber(debit), toNumber(credit), toNumber(balance), key, importId, function(err) {
          if (err) return reject(err);
          results.imported += 1;
          importMeta.imported += 1;
          resolve();
        });
      });
      stmt.finalize();
    } catch (err) {
      results.errors.push(err.message);
    }
  }

  // update import metadata row
  await new Promise((resolve) => {
    db.run('UPDATE imports SET imported = ?, duplicates = ?, errors = ? WHERE id = ?', [importMeta.imported, importMeta.duplicates, importMeta.errors, importId], () => resolve());
  });
  db.close();
  return results;
}

module.exports = { importFromCSVString };
