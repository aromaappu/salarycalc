const express = require('express');
const path = require('path');

const uploadRouter = require('./routes/upload');

const app = express();
app.use(express.json());

app.use('/public', express.static(path.join(__dirname, 'public')));
app.use('/upload', uploadRouter);

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

const PORT = process.env.PORT || 4000;
app.listen(PORT, () => console.log(`bank-import-service listening ${PORT}`));
