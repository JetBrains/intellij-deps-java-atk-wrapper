#!/usr/bin/env bash
# Builds wrapper/java-atk-wrapper.jar with the bundled native library.
# Usage: build-jar.sh [--clean] [--install <path to java-atk-wrapper.jar or its directory>]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
jar="$repo_root/wrapper/java-atk-wrapper.jar"
clean=false
install_target=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean) clean=true; shift ;;
    --install) install_target="${2:?--install needs a path}"; shift 2 ;;
    -h|--help) sed -n '2,3p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

cd "$repo_root"

if [[ ! -f Makefile ]]; then
  echo "No Makefile. Run ./autogen.sh first, once per checkout." >&2
  ./autogen.sh
fi

jobs="$(nproc 2>/dev/null || echo 4)"
if $clean; then
  make clean >/dev/null
fi
# Only the native part builds in parallel. Each class has its own javac call, and
# parallel calls read class files that other calls still write.
make -C jni -j"$jobs" >/dev/null
make >/dev/null

echo "Built: $jar"
unzip -l "$jar" | awk 'NF == 4 && $4 ~ /\.so$/ { print "Native library: " $4 " (" $1 " bytes)" }'
sha256sum "$jar"

if [[ -n "$install_target" ]]; then
  if [[ -d "$install_target" ]]; then
    install_target="$install_target/java-atk-wrapper.jar"
  fi
  if [[ -f "$install_target" ]]; then
    backup="$(mktemp -t java-atk-wrapper.backup.XXXXXX.jar)"
    cp "$install_target" "$backup"
    echo "Backup of the old jar: $backup"
  fi
  cp "$jar" "$install_target"
  echo "Installed: $install_target"
fi
