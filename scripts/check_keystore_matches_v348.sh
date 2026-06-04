#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/signing_constants.sh
source "${ROOT_DIR}/scripts/signing_constants.sh"

REFERENCE_APK="${ROOT_DIR}/dist/hr40-offline-fitness-v3.4.8.apk"
BUILD_TOOLS="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}/build-tools/34.0.0"

fingerprint_keystore() {
  keytool -list -v -keystore "$1" -storepass "${2:-android}" 2>/dev/null \
    | awk -F': ' '/SHA256:/{gsub(/[: ]/,"",$2); print tolower($2); exit}'
}

fingerprint_apk() {
  "${BUILD_TOOLS}/apksigner" verify --print-certs "$1" 2>/dev/null \
    | awk -F': ' '/SHA-256 digest:/{print $2; exit}'
}

expected="$(fingerprint_apk "${REFERENCE_APK}")"
echo "v3.4.8 reference cert: ${expected}"

for candidate in \
  "${ROOT_DIR}/keystore/hr40-distribution.keystore" \
  "${HOME}/.android/debug.keystore"; do
  if [[ ! -f "${candidate}" ]]; then
    continue
  fi
  actual="$(fingerprint_keystore "${candidate}")"
  if [[ "${actual}" == "${expected}" ]]; then
    echo "MATCH: ${candidate}"
    exit 0
  fi
  echo "no match: ${candidate} (${actual})"
done

echo "No local keystore matches v3.4.8. Export the debug.keystore from the build machine that produced dist v3.4.8." >&2
exit 1
