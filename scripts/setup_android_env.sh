#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
CMDLINE_TOOLS_VERSION="13114758"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
VERSION_MARKER="${SDK_ROOT}/cmdline-tools/.hr40_cmdline_tools_version"

mkdir -p "${SDK_ROOT}/cmdline-tools" "${HOME}/.cache/hr40-android-env"

if [[ ! -x "${SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" ]] || [[ ! -f "${VERSION_MARKER}" ]] || [[ "$(<"${VERSION_MARKER}")" != "${CMDLINE_TOOLS_VERSION}" ]]; then
  curl -L --fail --retry 4 --retry-delay 4 \
    -o "${HOME}/.cache/hr40-android-env/${CMDLINE_TOOLS_ZIP}" \
    "${CMDLINE_TOOLS_URL}"
  rm -rf "${HOME}/.cache/hr40-android-env/cmdline-tools" "${SDK_ROOT}/cmdline-tools/latest"
  unzip -q -o "${HOME}/.cache/hr40-android-env/${CMDLINE_TOOLS_ZIP}" \
    -d "${HOME}/.cache/hr40-android-env"
  mv "${HOME}/.cache/hr40-android-env/cmdline-tools" "${SDK_ROOT}/cmdline-tools/latest"
  echo "${CMDLINE_TOOLS_VERSION}" >"${VERSION_MARKER}"
fi

export ANDROID_HOME="${SDK_ROOT}"
export ANDROID_SDK_ROOT="${SDK_ROOT}"
export PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

yes | sdkmanager --licenses >/dev/null || true
sdkmanager --install \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

echo "Android SDK is ready at ${ANDROID_SDK_ROOT}"
echo "Run this before building in a new shell:"
echo "export ANDROID_HOME=${ANDROID_HOME}"
echo "export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}"
echo 'export PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"'
