#!/usr/bin/env bash
#
# Regenerates the SEP-0051 conformance corpus into a scratch directory and diffs
# it against the committed corpus.json.
#
# Usage:
#   refresh_corpus.sh
#
# Exit codes:
#   0  the committed corpus matches a fresh generation
#   1  drift: the diff is printed
#   2  a prerequisite is missing (reference CLI absent or not matching the pin)

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP_DIR="$SCRIPT_DIR/.tmp"
COMMITTED="$SCRIPT_DIR/corpus.json"
FRESH="$TMP_DIR/corpus.json"

mkdir -p "$TMP_DIR"

python3 "$SCRIPT_DIR/generate_corpus.py" --output "$FRESH"
status=$?
if [ "$status" -eq 2 ]; then
  exit 2
fi
if [ "$status" -ne 0 ]; then
  echo "refresh_corpus.sh: generation failed" >&2
  exit "$status"
fi

if [ ! -f "$COMMITTED" ]; then
  echo "refresh_corpus.sh: $COMMITTED does not exist; copy $FRESH into place." >&2
  exit 1
fi

if diff -u "$COMMITTED" "$FRESH"; then
  echo "No drift: corpus.json matches a fresh generation."
  exit 0
fi

echo "" >&2
echo "refresh_corpus.sh: corpus.json is out of date." >&2
echo "  Regenerate it with: python3 $SCRIPT_DIR/generate_corpus.py" >&2
echo "  Then re-emit the embedded copy: make -C tools/xdrgen-kt emit-json-tests" >&2
exit 1
