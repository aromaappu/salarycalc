const fs = require('fs');
const pdfParse = require('pdf-parse');
const { createWorker } = require('tesseract.js');

const worker = createWorker({ logger: m => {} });

async function pdfToText(filePath) {
  const dataBuffer = fs.readFileSync(filePath);
  let text = '';
  try {
    const data = await pdfParse(dataBuffer);
    text = (data && data.text) ? data.text : '';
  } catch (err) {
    // fallthrough to OCR if pdf-parse fails
    text = '';
  }

  // If pdf-parse returned no text (image-only PDF), try OCR
  if (!text || text.trim().length < 100) {
    await worker.load();
    await worker.loadLanguage('eng');
    await worker.initialize('eng');
    const { data: { text: ocrText } } = await worker.recognize(filePath);
    text = (ocrText || '') + '\n' + text;
    await worker.terminate();
  }

  return text;
}

module.exports = { pdfToText };
