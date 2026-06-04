#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/signing_constants.sh
source "${ROOT_DIR}/scripts/signing_constants.sh"

TARGET="${ROOT_DIR}/keystore/hr40-distribution.keystore"
SOURCE="${HOME}/.android/debug.keystore"

fingerprint() {
  keytool -list -v -keystore "$1" -storepass "${2:-android}" 2>/dev/null \
    | awk -F': ' '/SHA256:/{gsub(/[: ]/,"",$2); print tolower($2); exit}'
}

is_accepted_fingerprint() {
  local actual="$1"
  [[ "${actual}" == "${HR40_SHA256_V348}" || "${actual}" == "${HR40_SHA256_V345}" ]]
}

mkdir -p "${ROOT_DIR}/keystore"

if [[ -n "${HR40_DISTRIBUTION_KEYSTORE_BASE64:-}" ]]; then
  echo "${HR40_DISTRIBUTION_KEYSTORE_BASE64}" | base64 --decode >"${TARGET}"
  echo "Wrote keystore from HR40_DISTRIBUTION_KEYSTORE_BASE64"
elif [[ -f "${TARGET}" ]]; then
  echo "Using existing ${TARGET}"
elif [[ -f "${SOURCE}" ]]; then
  cp "${SOURCE}" "${TARGET}"
  echo "Copied ${SOURCE} -> ${TARGET}"
else
  echo "No keystore source found. Set HR40_DISTRIBUTION_KEYSTORE_BASE64 or create ~/.android/debug.keystore" >&2
  exit 1
fi

actual="$(fingerprint "${TARGET}" "android")"
if [[ -z "${actual}" ]]; then
  echo "Unable to read keystore fingerprint" >&2
  exit 1
fi

if ! is_accepted_fingerprint "${actual}"; then
  echo "Keystore SHA-256 mismatch." >&2
  echo "  required (v3.4.8 line): ${HR40_SHA256_V348}" >&2
  echo "  or (v3.4.5 line):       ${HR40_SHA256_V345}" >&2
  echo "  actual:                 ${actual}" >&2
  echo "Use the debug.keystore from the machine that built dist v3.4.8, or set HR40_DISTRIBUTION_KEYSTORE_BASE64." >&2
  exit 1
fi

if [[ "${actual}" == "${HR40_SHA256_V348}" ]]; then
  echo "Distribution keystore OK (v3.4.8 line, ${actual})"
else
  echo "Distribution keystore OK (v3.4.5 line, ${actual})"
  echo "WARN: v3.4.6–v3.4.8 users cannot upgrade from this build; use v3.4.8-line keystore instead." >&2
fi
