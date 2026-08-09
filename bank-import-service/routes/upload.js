const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const uploadController = require('../controllers/uploadController');
const logger = require('../services/logger');

const uploadDir = path.join(__dirname, '..', 'uploads');
if (!fs.existsSync(uploadDir)) fs.mkdirSync(uploadDir, { recursive: true });

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, uploadDir),
  filename: (req, file, cb) => cb(null, `${Date.now()}-${file.originalname}`)
});

function fileFilter(req, file, cb) {
  const allowed = ['application/pdf'];
  if (!allowed.includes(file.mimetype)) return cb(new Error('Only PDF files are allowed'), false);
  // reject dangerous extensions
  const forbiddenExt = ['.exe', '.zip', '.js', '.html', '.htm'];
  const ext = path.extname(file.originalname).toLowerCase();
  if (forbiddenExt.includes(ext)) return cb(new Error('Forbidden file type'), false);
  cb(null, true);
}

const upload = multer({ storage, fileFilter, limits: { fileSize: 20 * 1024 * 1024 } });

const router = express.Router();

router.post('/', upload.single('statement'), (req, res) => {
  uploadController.handleUpload(req, res).catch(err => {
    logger.error('upload_route_failed', { message: err.message, stack: err.stack });
    res.status(500).json({ error: 'Upload failed' });
  });
});

module.exports = router;
