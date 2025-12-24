#!/bin/bash

# --- Konfigurasi Keystore (Hardcoded) ---
KEYSTORE_PATH="/home/gustyxpower/Documents/Project/XMS/Keystore/Keystore-XCAI/xcai-key.jks"
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
    echo "Error: File keystore tidak ditemukan di '$KEYSTORE_PATH'"
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