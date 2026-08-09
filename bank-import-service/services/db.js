const sqlite3 = require('sqlite3').verbose();
const path = require('path');

const DB_PATH = path.join(__dirname, '..', 'data', 'transactions.db');

function ensureDb() {
  const dbdir = path.dirname(DB_PATH);
  const fs = require('fs');
  if (!fs.existsSync(dbdir)) fs.mkdirSync(dbdir, { recursive: true });

  const db = new sqlite3.Database(DB_PATH);
  db.serialize(() => {
    db.run(`CREATE TABLE IF NOT EXISTS transactions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      date TEXT NOT NULL,
      description TEXT,
      debit REAL,
      credit REAL,
      balance REAL,
      unique_key TEXT UNIQUE
    )`);
  });
  return db;
}

module.exports = { ensureDb };
