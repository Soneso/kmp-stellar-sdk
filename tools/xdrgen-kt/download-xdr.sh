#!/bin/bash
# Download .x XDR definition files from stellar/stellar-xdr at the pinned commit.
#
# Usage:
#   ./download-xdr.sh            # Download using commit from xdr-source.cfg
#   ./download-xdr.sh --force    # Re-download even if files already exist
#   ./download-xdr.sh --clean    # Remove downloaded .x files
#
# The pinned commit hash and file list are read from xdr-source.cfg.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/xdr-source.cfg"
OUTPUT_DIR="$SCRIPT_DIR/xdr"
BASE_URL="https://raw.githubusercontent.com/stellar/stellar-xdr"

# --- Helpers ---------------------------------------------------------------

die() {
    echo "ERROR: $*" >&2
    exit 1
}

usage() {
    echo "Usage: $0 [--force | --clean]"
    echo ""
    echo "Options:"
    echo "  --force    Re-download all files even if they already exist"
    echo "  --clean    Remove downloaded .x files and exit"
    echo ""
    echo "Configuration is read from xdr-source.cfg."
    exit 0
}

# --- Parse arguments -------------------------------------------------------

FORCE=false
CLEAN=false

for arg in "$@"; do
    case "$arg" in
        --force) FORCE=true ;;
        --clean) CLEAN=true ;;
        --help|-h) usage ;;
        *) die "Unknown argument: $arg" ;;
    esac
done

# --- Clean mode ------------------------------------------------------------

if [ "$CLEAN" = true ]; then
    if [ -d "$OUTPUT_DIR" ]; then
        rm -rf "$OUTPUT_DIR"
        echo "Removed $OUTPUT_DIR"
    else
        echo "Nothing to clean."
    fi
    exit 0
fi

# --- Load configuration ---------------------------------------------------

if [ ! -f "$CONFIG_FILE" ]; then
    die "Configuration file not found: $CONFIG_FILE"
fi

# Source the config file to get XDR_COMMIT and XDR_FILES
# shellcheck source=/dev/null
source "$CONFIG_FILE"

if [ -z "${XDR_COMMIT:-}" ]; then
    die "XDR_COMMIT is not set in $CONFIG_FILE"
fi

if ! [[ "$XDR_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
    die "XDR_COMMIT does not look like a valid git commit hash: $XDR_COMMIT"
fi

if [ -z "${XDR_FILES:-}" ]; then
    die "XDR_FILES is not set in $CONFIG_FILE"
fi

# Convert XDR_FILES to an array, filtering empty lines
FILES=()
while IFS= read -r line; do
    line="$(echo "$line" | xargs)"  # trim whitespace
    if [ -n "$line" ]; then
        FILES+=("$line")
    fi
done <<< "$XDR_FILES"

if [ ${#FILES[@]} -eq 0 ]; then
    die "No files listed in XDR_FILES"
fi

# --- Stale-commit detection ------------------------------------------------

if [ "$FORCE" = false ] && [ -f "$OUTPUT_DIR/.commit" ]; then
    EXISTING_COMMIT=$(cat "$OUTPUT_DIR/.commit")
    if [ "$EXISTING_COMMIT" != "$XDR_COMMIT" ]; then
        echo "Commit changed ($EXISTING_COMMIT -> $XDR_COMMIT). Re-downloading all files."
        FORCE=true
    fi
fi

# --- Download files --------------------------------------------------------

mkdir -p "$OUTPUT_DIR"

echo "XDR commit: $XDR_COMMIT"
echo "Output:     $OUTPUT_DIR"
echo "Files:      ${#FILES[@]}"
echo ""

DOWNLOADED=0
SKIPPED=0
FAILED=0

for filename in "${FILES[@]}"; do
    target="$OUTPUT_DIR/$filename"

    if [ "$FORCE" = false ] && [ -f "$target" ]; then
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    url="$BASE_URL/$XDR_COMMIT/$filename"
    tmp="$target.tmp"

    if curl -Lsf -o "$tmp" "$url"; then
        # Validate that we got a non-empty file
        if [ ! -s "$tmp" ]; then
            rm -f "$tmp"
            echo "FAIL: $filename (empty response)"
            FAILED=$((FAILED + 1))
            continue
        fi
        mv "$tmp" "$target"
        echo "  OK: $filename"
        DOWNLOADED=$((DOWNLOADED + 1))
    else
        rm -f "$tmp"
        echo "FAIL: $filename (HTTP error)"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
echo "Done: $DOWNLOADED downloaded, $SKIPPED already present, $FAILED failed."

if [ "$FAILED" -gt 0 ]; then
    die "Some files failed to download. Check your network connection and verify the commit hash."
fi

# Write a marker file recording which commit was used
echo "$XDR_COMMIT" > "$OUTPUT_DIR/.commit"
