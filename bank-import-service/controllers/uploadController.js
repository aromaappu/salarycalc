const path = require('path');
const fs = require('fs');
const csvGenerator = require('../services/csvGenerator');
const pdfConverter = require('../services/pdfConverter');
const ocrService = require('../services/ocrService');
const textCleaner = require('../services/textCleaner');
const bankDetector = require('../services/bankDetector');
const parser = require('../services/parser');
const canaraParser = require('../services/canaraParser');
const validation = require('../services/validation');
const dbImporter = require('../services/dbImporter');
const logger = require('../services/logger');

async function handleUpload(req, res) {
  const summary = {
    uploadedFile: req.file ? req.file.originalname : null,
    txtGenerated: false,
    bankDetected: null,
    totalTransactions: 0,
    imported: 0,
    duplicatesSkipped: 0,
    invalidRows: 0,
    errors: [],
    processingTimeMs: 0
  };

  const start = Date.now();
  if (!req.file) return res.status(400).json({ error: 'No file uploaded' });

  const tempPath = req.file.path;
  try {
    let text = await pdfConverter.pdfToText(tempPath);
    if (!text || text.trim().length < 50) {
      // run OCR only if necessary
      text = await ocrService.runOCR(tempPath);
    }
    summary.txtGenerated = !!text;

    // sanitize and clean
    const cleaned = textCleaner.cleanText(text);
    const bank = bankDetector.detectBank(cleaned);
    summary.bankDetected = bank;

    let parsed;
    if (canaraParser.canParse(text)) {
      // Canara statements lose column layout in pdf-parse, so parse the raw
      // text with the layout-aware parser (uses the running balance chain).
      parsed = canaraParser.parseCanara(text);
    } else {
      parsed = parser.parseTransactions(cleaned);
    }
    summary.totalTransactions = parsed.length;

    // validate and filter
    const valid = [];
    for (const tx of parsed) {
      if (validation.isValidRow(tx)) valid.push(tx);
      else summary.invalidRows += 1;
    }

    // generate CSV
    const csv = csvGenerator.transactionsToCSV(valid);

    // import to DB
    const importResult = await dbImporter.importFromCSVString(csv, {
      sourceFilename: req.file.originalname
    });
    summary.imported = importResult.imported;
    summary.duplicatesSkipped = importResult.duplicates;
    summary.errors = importResult.errors || [];

    summary.processingTimeMs = Date.now() - start;

    // cleanup
    try { fs.unlinkSync(tempPath); } catch (e) { logger.warn('cleanup failed', e); }

    logger.info('import_summary', summary);
    res.json({ summary });
  } catch (err) {
    logger.error('upload_failed', { message: err.message, stack: err.stack });
    try { fs.unlinkSync(tempPath); } catch (e) {}
    res.status(500).json({ error: 'Import failed', details: err.message });
  }
}

module.exports = { handleUpload };
