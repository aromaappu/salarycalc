class Transaction {
  constructor({ date, description, debit, credit, balance }) {
    this.date = date;
    this.description = description;
    this.debit = debit || null;
    this.credit = credit || null;
    this.balance = balance || null;
  }
}

module.exports = Transaction;
