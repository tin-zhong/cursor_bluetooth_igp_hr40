#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${ROOT_DIR}/keystore/hr40-distribution.keystore"
EXPECTED_SHA256="87fbddbb5e436e533e70972f8b995e8c551667cde43d0df0a0cf6705babb897b"
SOURCE="${HOME}/.android/debug.keystore"

fingerprint() {
  keytool -list -v -keystore "$1" -storepass "${2:-android}" 2>/dev/null \
    | awk -F': ' '/SHA256:/{gsub(/ /,"",$2); print tolower($2); exit}'
}

mkdir -p "${ROOT_DIR}/keystore"

if [[ -n "${HR40_DISTRIBUTION_KEYSTORE_BASE64:-}" ]]; then
  echo "${HR40_DISTRIBUTION_KEYSTORE_BASE64}" | base64 --decode >"${TARGET}"
  echo "Wrote keystore from HR40_DISTRIBUTION_KEYSTORE_BASE64"
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

if [[ "${actual}" != "${EXPECTED_SHA256}" ]]; then
  echo "Keystore SHA-256 mismatch." >&2
  echo "  expected: ${EXPECTED_SHA256}" >&2
  echo "  actual:   ${actual}" >&2
  echo "Use the machine/secret that built v3.4.5, or keep the committed hr40-distribution.keystore." >&2
  exit 1
fi

echo "Distribution keystore OK (${actual})"
