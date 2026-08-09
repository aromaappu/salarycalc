const crypto = require('crypto');

function makeKey(tx) {
  const key = `${tx.date}|${(tx.description||'').toLowerCase()}|${tx.debit||tx.credit||''}`;
  return crypto.createHash('sha1').update(key).digest('hex');
}

module.exports = { makeKey };
