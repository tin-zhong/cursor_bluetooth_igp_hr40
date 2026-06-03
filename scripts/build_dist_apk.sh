#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

EXPECTED_SHA256="87fbddbb5e436e533e70972f8b995e8c551667cde43d0df0a0cf6705babb897b"
KEYSTORE="${ROOT_DIR}/keystore/hr40-distribution.keystore"

if [[ ! -f "${KEYSTORE}" ]]; then
  bash "${ROOT_DIR}/scripts/capture_distribution_keystore.sh"
fi

bash "${ROOT_DIR}/scripts/setup_android_env.sh" >/dev/null

VERSION_NAME="$(awk -F'"' '/versionName/ {print $2; exit}' app/build.gradle)"
OUTPUT_APK="${ROOT_DIR}/dist/hr40-offline-fitness-v${VERSION_NAME}.apk"
BUILD_TOOLS="${ANDROID_SDK_ROOT}/build-tools/35.0.0"
if [[ ! -d "${BUILD_TOOLS}" ]]; then
  BUILD_TOOLS="${ANDROID_SDK_ROOT}/build-tools/34.0.0"
fi

./gradlew assembleDebug

fingerprint() {
  "${BUILD_TOOLS}/apksigner" verify --print-certs "$1" 2>/dev/null \
    | awk -F': ' '/SHA-256 digest:/{print $2; exit}'
}

BUILT_APK="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
actual="$(fingerprint "${BUILT_APK}")"
if [[ "${actual}" != "${EXPECTED_SHA256}" ]]; then
  echo "Built APK signature mismatch." >&2
  echo "  expected: ${EXPECTED_SHA256}" >&2
  echo "  actual:   ${actual}" >&2
  exit 1
fi

mkdir -p dist
cp "${BUILT_APK}" "${OUTPUT_APK}"
cp "${BUILT_APK}" "${ROOT_DIR}/dist/hr40-offline-fitness-debug.apk"
echo "OK: ${OUTPUT_APK}"
