<div align="center">

# 💰 SalaryCalc

**Android personal finance app — income, expenses, bank statement imports, loans, and FD tracking in one place**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Room DB](https://img.shields.io/badge/Room-Local_DB-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Android](https://img.shields.io/badge/API_26+-Android_8.0-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)

*Income & Expense · Bank Statement Import · Loan & FD Tracker · CSV/PDF Export · Dark Mode*

</div>

---

## 📖 About The Project

SalaryCalc is a full-featured Android personal finance application. Users record
income and expenses manually or **automatically import them from bank PDF/CSV statements**
using a multi-bank parser engine (with OCR fallback for scanned statements). It also
tracks loans, fixed deposits, and generates exportable reports.

---

## ✨ Features

| Module | What it does |
|---|---|
| 💰 **Income & Expense** | Add, edit, categorize transactions with notes and receipt attachments |
| 🏦 **Bank Import** | Upload PDF/CSV statements — auto-detected Canara, HDFC, SBI, generic formats; OCR fallback for scanned PDFs; duplicate detection |
| 💳 **Loan Tracker** | Add loans, track repayments, monitor outstanding balances |
| 📈 **FD Tracker** | Track fixed deposits, maturity dates, growth calculations |
| 📊 **Dashboard** | Income vs expense summaries, category-wise charts, 14-day trends |
| 📤 **Export** | CSV and PDF export of transactions |
| ☁️ **Drive Backup** | Google Drive backup & restore |
| 🔒 **Security** | App lock, local-first data |
| 🎨 **UX** | Dark mode, Material Design 3, offline-ready |

---

## 🏗️ Architecture

```
app/                          Android client
├── ui/screens/               Compose screens (Dashboard, Income, Expense, Bank Import…)
├── bankimport/               Multi-bank PDF/CSV parser engine
│   ├── CanaraParser.kt       Bank-specific parsers
│   ├── PdfReader.kt          PDF text extraction
│   ├── OcrHelper.kt          OCR fallback for scanned statements
│   ├── CsvGenerator.kt       CSV output
│   └── TransactionValidator.kt + DuplicateChecker.kt
└── db/                       Room database + DAOs

bank-import-service/           Node.js microservice (server-side PDF → transaction API)
├── services/                 Parser, OCR, CSV generator, DB import
├── tests/                    Jest test suite for parsers
└── public/                   Web upload UI

screenshots/                  App screenshots for README
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Database | Room (SQLite) |
| Bank Parsing | Android PDF text extraction + ML Kit OCR + custom parsers |
| Backup | Google Drive API |
| Server-side (optional) | Node.js, Express, Tesseract OCR, Jest tests |
| Dark Mode | System-aware theming |

---

## 🚀 Getting Started

1. Open in **Android Studio**, let Gradle sync
2. Run on device or emulator (min SDK 26)

---

## 📸 Screenshots

<p align="center">
  <img src="screenshots/dashboard.jpg" width="240" alt="Dashboard"/>
  <img src="screenshots/income.jpg" width="240" alt="Income"/>
  <img src="screenshots/expense.jpg" width="240" alt="Expense"/>
</p>

<<<<<<< HEAD
## ✨ Features

### 💰 Income & Expense Management

* Add and manage income and expenses
* 🤖 Automatic income & expense tracking
* Automatic transaction detection from bank notifications
* Automatic transaction categorization
* Custom transaction categories
* Transaction notes
* Receipt attachment

### 🏦 Bank Statement Management

* 📄 Upload bank statements
* Import transactions from bank statements
* 📥 Download bank statements
* Transaction data processing and organization
* Duplicate transaction detection

### 💳 Loan Tracker

* Add and manage loans
* Track loan amounts and repayments
* Monitor outstanding balances
* Track loan payment progress

### 🏦 Fixed Deposit (FD) Tracker

* Add and manage Fixed Deposits
* 📈 Live FD tracking
* Track FD maturity
* Calculate and track FD growth
* FD increment tracking
* Monitor investment progress

### 📊 Dashboard & Reports

* Financial dashboard
* Income and expense summaries
* Spending charts and statistics
* Category-wise expense analysis
* Transaction history
* Financial progress tracking

### 🔔 Notifications & Automation

* Transaction notifications
* Payment reminders
* Automatic transaction processing
* Automated income and expense entries

### 🔐 Security & Privacy

* App lock
* Local financial data management
* Secure transaction records

### 📤 Data Management

* Export transactions to CSV
* Export financial records to PDF
* ☁️ Google Drive backup
* Restore backed-up data

### 🎨 User Experience

* 🌙 Dark mode
* 💱 Multi-currency support
* Simple and user-friendly interface
* Responsive Android UI

## 🛠️ Tech Stack

* **Language:** Kotlin
* **UI:** Jetpack Compose
* **Database:** Room Database
* **Platform:** Android
* **Build System:** Gradle
* **Version Control:** Git & GitHub


## 📥 Download

**Available on Indus Appstore**

[Download SalaryCalc from Indus Appstore](https://indusapp.store/hnu7fmve?utm_source=chatgpt.com)

## ⚙️ How It Works

1. Add income, expenses, loans, or fixed deposits.
2. SalaryCalc stores and organizes financial records.
3. Bank notifications can be processed for automatic transactions.
4. Bank statements can be uploaded and imported.
5. The dashboard provides financial summaries and charts.
6. Loans and FD investments can be tracked separately.
7. Financial data can be exported or backed up.

## 📄 Bank Statement

SalaryCalc provides bank statement management to help users import and organize transaction records from their statements.

Users can:

* Upload statements
* Import transaction data
* Review transactions
* Detect duplicate transactions
* Download/export financial records

## 🚀 Project Status

**Active Development**

SalaryCalc is continuously being improved with new automation, financial tracking, reporting, and data-management features.

## 👨‍💻 Developer

**Aromal KP**

* GitHub: https://github.com/aromaappu
* Instagram: https://www.instagram.com/_ar.xmal_/

=======
>>>>>>> d635af8 (Add README: Kotlin personal finance app with bank statement import)
---

## 👤 Author

**Appu Aromal** — [GitHub](https://github.com/aromaappu)

---

<div align="center">
<sub>Kotlin · Compose · Room · OCR · Multi-bank Parser</sub>
</div>
