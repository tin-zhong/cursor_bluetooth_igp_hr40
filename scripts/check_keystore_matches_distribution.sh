#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/signing_constants.sh
source "${ROOT_DIR}/scripts/signing_constants.sh"

KEYSTORE="${ROOT_DIR}/keystore/hr40-distribution.keystore"

fingerprint_keystore() {
  keytool -list -v -keystore "$1" -storepass "${2:-android}" 2>/dev/null \
    | awk -F': ' '/SHA256:/{gsub(/[: ]/,"",$2); print tolower($2); exit}'
}

echo "v3.5.0 distribution cert: ${HR40_DIST_PRIMARY_SHA256}"

if [[ ! -f "${KEYSTORE}" ]]; then
  echo "Missing ${KEYSTORE}" >&2
  exit 1
fi

actual="$(fingerprint_keystore "${KEYSTORE}")"
if [[ "${actual}" == "${HR40_DIST_PRIMARY_SHA256}" ]]; then
  echo "MATCH: ${KEYSTORE}"
  exit 0
fi

echo "no match: ${KEYSTORE} (${actual})" >&2
exit 1
