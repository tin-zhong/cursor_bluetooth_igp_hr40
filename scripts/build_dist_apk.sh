#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

# shellcheck source=scripts/signing_constants.sh
source "${ROOT_DIR}/scripts/signing_constants.sh"

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

KEYSTORE="${ROOT_DIR}/keystore/hr40-distribution.keystore"

bash "${ROOT_DIR}/scripts/capture_distribution_keystore.sh"

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

if [[ "${actual}" != "${HR40_DIST_PRIMARY_SHA256}" ]]; then
  echo "Built APK signature mismatch (cannot upgrade from v3.4.8)." >&2
  echo "  required (v3.4.8 line): ${HR40_DIST_PRIMARY_SHA256}" >&2
  echo "  actual:                 ${actual}" >&2
  if [[ "${actual}" == "${HR40_SHA256_V345}" ]]; then
    echo "Hint: keystore matches v3.4.5 line; install v3.4.8-line debug.keystore and rebuild." >&2
  fi
  exit 1
fi

mkdir -p dist
cp "${BUILT_APK}" "${OUTPUT_APK}"
cp "${BUILT_APK}" "${ROOT_DIR}/dist/hr40-offline-fitness-debug.apk"
echo "OK: ${OUTPUT_APK} (v3.4.8-compatible signature)"
