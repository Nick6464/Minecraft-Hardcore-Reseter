#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${DATA_DIR:-$SCRIPT_DIR}"
RESET_FLAG_NAME="reset.flag"
WORLD_DIRS=("world" "world_nether" "world_the_end")
PLUGINS_DIR="$DATA_DIR/plugins"
PLUGIN_NAME="HardcoreReset.jar"
BUILD_PLUGIN_PATH="$SCRIPT_DIR/HardcoreReset/target/$PLUGIN_NAME"
PAPER_JAR="${PAPER_JAR:-paper.jar}"
JAVA_BIN="${JAVA_BIN:-java}"
MEMORY_MIN="${MEMORY_MIN:-4G}"
MEMORY_MAX="${MEMORY_MAX:-4G}"

echo "[HardcoreReset] Data directory: $DATA_DIR"

RESET_FLAG="$DATA_DIR/$RESET_FLAG_NAME"
if [[ -f "$RESET_FLAG" ]]; then
  echo "[HardcoreReset] Reset flag found. Deleting worlds..."
  for dir in "${WORLD_DIRS[@]}"; do
    if [[ -d "$DATA_DIR/$dir" ]]; then
      rm -rf "$DATA_DIR/$dir"
      echo "[HardcoreReset] Deleted $dir"
    fi
  done
  rm -f "$RESET_FLAG"
  echo "[HardcoreReset] Worlds cleared."
fi

mkdir -p "$PLUGINS_DIR"

if [[ ! -f "$PLUGINS_DIR/$PLUGIN_NAME" ]]; then
  if [[ -f "$BUILD_PLUGIN_PATH" ]]; then
    echo "[HardcoreReset] Copying plugin from $BUILD_PLUGIN_PATH"
    cp "$BUILD_PLUGIN_PATH" "$PLUGINS_DIR/$PLUGIN_NAME"
  else
    echo "[HardcoreReset] ERROR: $PLUGIN_NAME missing in $PLUGINS_DIR."
    echo "[HardcoreReset] Build the plugin (mvn package) and copy it into $PLUGINS_DIR."
    exit 1
  fi
fi

if [[ ! -f "$DATA_DIR/$PAPER_JAR" ]]; then
  echo "[HardcoreReset] ERROR: Paper server jar ($PAPER_JAR) not found in $DATA_DIR."
  echo "[HardcoreReset] Download it, e.g.: curl -Lo $DATA_DIR/$PAPER_JAR https://api.papermc.io/v2/projects/paper/versions/1.21.10/builds/113/downloads/paper-1.21.10-113.jar"
  exit 1
fi

if [[ ! -f "$DATA_DIR/eula.txt" ]]; then
  echo "eula=true" > "$DATA_DIR/eula.txt"
  echo "[HardcoreReset] eula.txt created with eula=true."
fi

cd "$DATA_DIR"
if [[ -n "${UMASK:-}" ]]; then
  umask "$UMASK"
fi

while true; do
  echo "[HardcoreReset] Starting Paper server with Xms=$MEMORY_MIN Xmx=$MEMORY_MAX"
  "$JAVA_BIN" -Xms"$MEMORY_MIN" -Xmx"$MEMORY_MAX" -jar "$PAPER_JAR" nogui || true

  echo "[HardcoreReset] Server stopped. Checking for reset flag..."
  if [[ -f "$RESET_FLAG" ]]; then
    echo "[HardcoreReset] Reset flag detected. Worlds will be wiped on next loop."
  else
    echo "[HardcoreReset] No reset flag. Restarting server in 5 seconds."
    sleep 5
  fi
done
