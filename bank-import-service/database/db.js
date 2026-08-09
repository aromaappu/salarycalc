const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');

const DB_PATH = path.join(__dirname, '..', 'data', 'transactions.db');

function ensureDb() {
  const dir = path.dirname(DB_PATH);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  const db = new sqlite3.Database(DB_PATH);
  db.serialize(() => {
    db.run(`CREATE TABLE IF NOT EXISTS imports (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      source_filename TEXT,
      imported_at TEXT,
      total INTEGER,
      imported INTEGER,
      duplicates INTEGER,
      errors INTEGER
    )`);

    db.run(`CREATE TABLE IF NOT EXISTS transactions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      date TEXT NOT NULL,
      description TEXT,
      debit REAL,
      credit REAL,
      balance REAL,
      unique_key TEXT UNIQUE,
      import_id INTEGER,
      FOREIGN KEY(import_id) REFERENCES imports(id)
    )`);
  });
  return db;
}

module.exports = { ensureDb };
