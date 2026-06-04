#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/signing_constants.sh
source "${ROOT_DIR}/scripts/signing_constants.sh"

PROPERTIES="${ROOT_DIR}/keystore/hr40-distribution.properties"
STORE_FILE="hr40-distribution.keystore"
STORE_PASS="android"
SOURCE="${HOME}/.android/debug.keystore"

if [[ -f "${PROPERTIES}" ]]; then
  # shellcheck disable=SC1090
  source <(grep -E '^[a-zA-Z]+=' "${PROPERTIES}" | sed 's/\r$//')
  STORE_FILE="${storeFile:-${STORE_FILE}}"
  STORE_PASS="${storePassword:-${STORE_PASS}}"
fi

TARGET="${ROOT_DIR}/keystore/${STORE_FILE}"

fingerprint() {
  keytool -list -v -keystore "$1" -storepass "${2:-android}" 2>/dev/null \
    | awk -F': ' '/SHA256:/{gsub(/[: ]/,"",$2); print tolower($2); exit}'
}

is_accepted_fingerprint() {
  local actual="$1"
  [[ "${actual}" == "${HR40_SHA256_V348}" || "${actual}" == "${HR40_SHA256_V345}" ]]
}

mkdir -p "${ROOT_DIR}/keystore"

if [[ -n "${storeKeystoreBase64:-}" ]]; then
  echo "${storeKeystoreBase64}" | tr -d '\n\r ' | base64 --decode >"${TARGET}"
  echo "Wrote keystore from storeKeystoreBase64 in ${PROPERTIES}"
elif [[ -n "${HR40_DISTRIBUTION_KEYSTORE_BASE64:-}" ]]; then
  echo "${HR40_DISTRIBUTION_KEYSTORE_BASE64}" | base64 --decode >"${TARGET}"
  echo "Wrote keystore from HR40_DISTRIBUTION_KEYSTORE_BASE64"
elif [[ -f "${TARGET}" ]]; then
  echo "Using existing ${TARGET} (configured in ${PROPERTIES})"
elif [[ -f "${SOURCE}" ]]; then
  cp "${SOURCE}" "${TARGET}"
  echo "Copied ${SOURCE} -> ${TARGET}"
else
  echo "No keystore found. Place ${STORE_FILE} in keystore/ per ${PROPERTIES}, or set storeKeystoreBase64 / HR40_DISTRIBUTION_KEYSTORE_BASE64." >&2
  exit 1
fi

actual="$(fingerprint "${TARGET}" "${STORE_PASS}")"
if [[ -z "${actual}" ]]; then
  echo "Unable to read keystore fingerprint (check ${PROPERTIES} passwords)." >&2
  exit 1
fi

if ! is_accepted_fingerprint "${actual}"; then
  echo "Keystore SHA-256 mismatch." >&2
  echo "  required (v3.4.8 line): ${HR40_SHA256_V348}" >&2
  echo "  or (v3.4.5 line):       ${HR40_SHA256_V345}" >&2
  echo "  actual:                 ${actual}" >&2
  echo "Replace keystore/${STORE_FILE} with the v3.4.8-line debug.keystore from your build machine." >&2
  exit 1
fi

if [[ "${actual}" == "${HR40_SHA256_V348}" ]]; then
  echo "Distribution keystore OK (v3.4.8 line, ${actual})"
else
  echo "Distribution keystore OK (v3.4.5 line, ${actual})"
  echo "WARN: v3.4.6–v3.4.8 users cannot upgrade from this build; use v3.4.8-line keystore instead." >&2
fi
