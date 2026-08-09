const { createWorker } = require('tesseract.js');

async function runOCR(filePath) {
  const worker = createWorker({ logger: () => {} });
  try {
    await worker.load();
    await worker.loadLanguage('eng');
    await worker.initialize('eng');
    const { data: { text } } = await worker.recognize(filePath);
    await worker.terminate();
    return text || '';
  } catch (err) {
    try { await worker.terminate(); } catch (e) {}
    throw err;
  }
}

module.exports = { runOCR };
