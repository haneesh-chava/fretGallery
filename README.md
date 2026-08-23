# fretG — Cryptographic & Steganographic Image Birth Certificate Gallery

**fretG** is a next-generation Android gallery system built with Kotlin. It introduces an **Image Birth Certificate** paradigm powered by cryptographic signing and spatial pixel steganography. Every photo captured through the camera is sealed at birth with an immutable cryptographic proof embedded directly into its pixel matrix and metadata.

---

## ✨ Features

- **Image Birth Certificate**:
  - Sealed with unique Certificate ID (`FRETG-YYYY-XXXXXXXX`), UTC timestamp, device hardware seal, and SHA-256 fingerprint.
  - Optical sensor telemetry: ISO, exposure, aperture, focal length, and resolution.
  - Hardware-backed HMAC signature generated via Android KeyStore.
- **Pixel Matrix Steganography**:
  - Invisibly embeds compressed binary certificate payloads into pixel LSBs with CRC32 parity check and authenticated EXIF metadata.
  - Real-time forensic audit pipeline flags any cropped or modified pixels as `TAMPERED_WARNING`.
- **Apple iOS Glassmorphism UI**:
  - Dark glassmorphism acrylic surfaces with vibrant red radial gradient accents.
  - Big top navigation bar with the `fret` brand emblem.
  - Horizontal edge-scrolling section bar with liquid glass text magnification for **Camera**, **Screenshots**, **Downloads**, **WhatsApp**, and **Instagram**.
  - Interactive **Inspect Birth Certificate** bottom sheet modal with real-time audit checklist.
- **Typography**:
  - Headings & Badges: **`Handjet`** Google Font.
  - Body & Telemetry: **`Quicksand`** Google Font.

---

## 🐳 Docker (All-in-One Container)

To build and compile the APK using Docker:

### 1. Build the Docker Image & Compile APK
```bash
docker compose up --build
```
or with plain Docker:
```bash
docker build -t fretg:latest .
docker run --rm -v $(pwd)/app/build/outputs:/app/app/build/outputs fretg:latest
```

### 2. Run Unit Tests in Docker
```bash
docker run --rm fretg:latest ./gradlew test
```

---

## 📱 Local Development (Android Studio)

1. Open the project in **Android Studio**.
2. Sync Gradle dependencies.
3. Run on an Android emulator or connected device (Min SDK 24 / Android 7.0+, Target SDK 34+).
