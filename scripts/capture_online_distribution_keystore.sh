#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/signing_constants.sh
source "${ROOT_DIR}/scripts/signing_constants.sh"

TARGET="${ROOT_DIR}/dist-online/keystore/hr40-online-distribution.keystore"

fingerprint() {
  keytool -list -v -keystore "$1" -storepass "${2:-android}" 2>/dev/null \
    | awk -F': ' '/SHA256:/{gsub(/[: ]/,"",$2); print tolower($2); exit}'
}

mkdir -p "${ROOT_DIR}/dist-online/keystore"

if [[ -n "${HR40_ONLINE_DISTRIBUTION_KEYSTORE_BASE64:-}" ]]; then
  echo "${HR40_ONLINE_DISTRIBUTION_KEYSTORE_BASE64}" | base64 --decode >"${TARGET}"
  echo "Wrote keystore from HR40_ONLINE_DISTRIBUTION_KEYSTORE_BASE64"
elif [[ -f "${TARGET}" ]]; then
  echo "Using committed ${TARGET}"
else
  echo "Missing ${TARGET}. Commit dist-online/keystore/hr40-online-distribution.keystore or set HR40_ONLINE_DISTRIBUTION_KEYSTORE_BASE64." >&2
  exit 1
fi

actual="$(fingerprint "${TARGET}" "android")"
if [[ -z "${actual}" ]]; then
  echo "Unable to read keystore fingerprint" >&2
  exit 1
fi

if [[ "${actual}" != "${HR40_ONLINE_DIST_PRIMARY_SHA256}" ]]; then
  echo "Online keystore SHA-256 mismatch." >&2
  echo "  required (Online v3.5.1 line): ${HR40_ONLINE_DIST_PRIMARY_SHA256}" >&2
  echo "  actual:                        ${actual}" >&2
  echo "Use the keystore committed in dist-online/keystore/hr40-online-distribution.keystore." >&2
  exit 1
fi

echo "Online distribution keystore OK (v3.5.1 line, ${actual})"
