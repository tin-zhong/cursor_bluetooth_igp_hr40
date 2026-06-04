#!/usr/bin/env bash
# Legacy entry point — v3.5.0+ uses the committed distribution keystore.
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check_keystore_matches_distribution.sh" "$@"
