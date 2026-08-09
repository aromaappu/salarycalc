function isValidRow(tx) {
  if (!tx) return false;
  if (!tx.date) return false;
  if (!tx.debit && !tx.credit) return false;
  return true;
}

module.exports = { isValidRow };
