#!/bin/bash

# --- Konfigurasi Keystore (Dynamic) ---
# Check common locations or use env var
if [ -n "$XMS_KEYSTORE_PATH" ]; then
    KEYSTORE_PATH="$XMS_KEYSTORE_PATH"
elif [ -f "$HOME/Documents/Project/XMS/xcai-key.jks" ]; then
    KEYSTORE_PATH="$HOME/Documents/Project/XMS/xcai-key.jks"
elif [ -f "./xcai-key.jks" ]; then
    KEYSTORE_PATH="./xcai-key.jks"
else
    # Fallback to the old hardcoded path if it exists, otherwise error
    KEYSTORE_PATH="/Users/gustyx-macos/Documents/Project/XMS/xcai-key.jks"
fi

KEY_ALIAS="xcaikey"
KEYSTORE_PASSWORD="gusti717"
KEY_PASSWORD="gusti717"

# --- Membersihkan Proyek (Langkah Awal Anda) ---
echo "Membersihkan cache build..."
rm -rf app/build/
rm -rf build/
rm -rf .gradle/

echo "Membersihkan cache Gradle..."
./gradlew clean
echo "Pembersihan selesai."
echo ""

# Validasi sederhana untuk memeriksa apakah file ada
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "Error: File keystore tidak ditemukan."
    echo "Dicari di: $KEYSTORE_PATH"
    echo "Pastikan file keystore ada di lokasi tersebut atau set env var XMS_KEYSTORE_PATH."
    exit 1
fi

echo "Menggunakan keystore: $KEYSTORE_PATH"
echo "Key alias: $KEY_ALIAS"
echo ""

echo "Masukkan changelog untuk rilis ini (tekan Ctrl+D setelah selesai):"
CHANGELOG=$(cat)
echo ""

# --- Menjalankan Build Gradle dengan Properti ---
echo "Memulai build release dengan informasi keystore dan Changelog yang diberikan..."

# Menjalankan gradlew dengan meneruskan variabel sebagai properti (-P)
./gradlew buildAndPublish \
    -PmyKeystorePath="$KEYSTORE_PATH" \
    -PmyKeystorePassword="$KEYSTORE_PASSWORD" \
    -PmyKeyAlias="$KEY_ALIAS" \
    -PmyKeyPassword="$KEY_PASSWORD" \
    -PmyChangelog="$CHANGELOG"

# Cek status build
if [ $? -eq 0 ]; then
    echo "✅ Build release selesai!"
    echo "Anda bisa menemukan APK di folder: app/build/outputs/apk/release/"
else
    echo "❌ Build gagal. Silakan periksa log di atas."
    exit 1
fi