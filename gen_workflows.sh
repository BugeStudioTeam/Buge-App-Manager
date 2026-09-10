#!/bin/bash
# gen_workflows.sh
# Create 65 workflow yml files, one commit per file
# Safe to run from anywhere inside the repo

set -e

# Find the repo root (where .git lives)
REPO_ROOT="$(git rev-parse --show-toplevel)"
WF_DIR="$REPO_ROOT/.github/workflows"

mkdir -p "$WF_DIR"

# Shared yml content (identical for all 65 files)
read -r -d '' YML_CONTENT <<'EOF' || true
name: Build Signed Release APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Decode keystore.jks
        run: |
          echo "${{ secrets.KEYSTORE_JKS_BASE64 }}" | base64 -d > keystore.jks
          chmod 600 keystore.jks
          ls -la keystore.jks
          file keystore.jks
          keytool -list -keystore keystore.jks -storepass android
        env:
          KEYSTORE_JKS_BASE64: ${{ secrets.KEYSTORE_JKS_BASE64 }}

      - name: Fix gradle.properties
        run: |
          if [ -f gradle.properties ]; then
            sed -i '/org.gradle.java.home/d' gradle.properties
          fi

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build Signed Release APK
        run: |
          ./gradlew assembleRelease \
            -Pandroid.injected.signing.store.file=$(pwd)/keystore.jks \
            -Pandroid.injected.signing.store.password=android \
            -Pandroid.injected.signing.key.alias=bugestudioteam \
            -Pandroid.injected.signing.key.password=android

      - name: Upload Signed Release APK
        uses: actions/upload-artifact@v4
        with:
          name: app-release-signed
          path: app/build/outputs/apk/release/*.apk
          retention-days: 30

      - name: Upload Mapping File
        uses: actions/upload-artifact@v4
        with:
          name: mapping-file
          path: app/build/outputs/mapping/release/mapping.txt
          retention-days: 30
EOF

cd "$REPO_ROOT"

# Generate 65 files, one commit each
for i in $(seq 1 65); do
    printf '%s\n' "$YML_CONTENT" > "$WF_DIR/build-release-$i.yml"
    echo "created: .github/workflows/build-release-$i.yml"

    git add .
    git commit -m "updated yml, full changelog pls see the commit history"
    echo "committed: $i/65"
done

echo "Done: 65 yml files created, 65 commits made"