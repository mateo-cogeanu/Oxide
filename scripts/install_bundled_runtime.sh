#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <java-version> <runtime-pack-dir>" >&2
  exit 1
fi

JAVA_VERSION="$1"
SOURCE_DIR="$2"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "Runtime pack directory not found: $SOURCE_DIR" >&2
  exit 1
fi

case "$JAVA_VERSION" in
  17)
    TARGET_DIR="$REPO_ROOT/app_pojavlauncher/src/main/assets/components/jre-new"
    ;;
  21|25)
    TARGET_DIR="$REPO_ROOT/app_pojavlauncher/src/main/assets/components/jre-$JAVA_VERSION"
    ;;
  *)
    echo "Unsupported Java version: $JAVA_VERSION" >&2
    echo "Supported versions: 17, 21, 25" >&2
    exit 1
    ;;
esac

required_files=(
  "version"
  "universal.tar.xz"
)

for required_file in "${required_files[@]}"; do
  if [[ ! -f "$SOURCE_DIR/$required_file" ]]; then
    echo "Missing required file: $SOURCE_DIR/$required_file" >&2
    exit 1
  fi
done

shopt -s nullglob
binpacks=("$SOURCE_DIR"/bin-*.tar.xz)
shopt -u nullglob

if [[ ${#binpacks[@]} -eq 0 ]]; then
  echo "Expected at least one binpack matching $SOURCE_DIR/bin-*.tar.xz" >&2
  exit 1
fi

rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR"

cp "$SOURCE_DIR/version" "$TARGET_DIR/version"
cp "$SOURCE_DIR/universal.tar.xz" "$TARGET_DIR/universal.tar.xz"
for binpack in "${binpacks[@]}"; do
  cp "$binpack" "$TARGET_DIR/"
done

echo "Bundled Java $JAVA_VERSION runtime assets into:"
echo "  $TARGET_DIR"
