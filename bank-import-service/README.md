# Bank Import Service

Lightweight Node.js service to upload bank statement PDFs and import transactions to a local SQLite DB.

Installation

```
cd bank-import-service
npm install
npm start
```

Usage
- Open http://localhost:4000
- Upload a PDF; the service will parse and import transactions automatically.

Notes
- Uses `pdf-parse` and `tesseract.js` as a fallback for image PDFs.
- CSV is kept temporary in memory and not persisted unless you export it.
