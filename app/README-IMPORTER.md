APK-native Bank Statement Importer

Quick start

1. Open the project in Android Studio.
2. Allow Gradle to sync (new dependencies: Room, WorkManager, ML Kit, Timber).
3. Run the app on a device or emulator (minSdk 26).
4. From the app, open `ImportActivity` (registered in manifest) to select a PDF and start import.

What was added
- `app/src/main/java/.../importer/` — PDF extraction (`PdfExtractor`), OCR (`OcrManager`), cleaner (`TextCleaner`), parser (`Parser`), duplicate checker, CSV exporter.
- Room DB entities and DAO under `importer/db`.
- `ImportWorker` uses WorkManager to process PDFs in background.
- `ImportActivity` provides a simple file picker UI and enqueues the worker.

Notes
- ML Kit text recognition works on-device and is included via Gradle dependency.
- The importer writes transaction rows into Room DB `bank_imports.db` inside app storage.
- To test: build and run, then open ImportActivity and select a PDF file.
