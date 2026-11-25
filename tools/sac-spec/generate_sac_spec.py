#!/usr/bin/env python3
"""Embeds the pinned Stellar Asset Contract spec into a source file.

The pin names a repository, a commit, a file in that repository and the sha256 of
the file. The script reads the file (from --source, or downloaded from the raw URL
at the pinned commit), checks its digest against the pin, fills the template with
the file as one line of standard base64 and writes the output. The template and
the output are paths in the pin, resolved against the root.

The output carries no timestamp, so a run at the same pin reproduces it byte for
byte.

Usage:
    python3 tools/sac-spec/generate_sac_spec.py [--pin PATH] [--root DIR] [--source FILE]

Exit codes:
    0  output written
    1  invalid pin, unreadable input, download failure, digest mismatch, a
       placeholder the template leaves unfilled, or a write failure; nothing written
"""

import argparse
import base64
import hashlib
import http.client
import json
import os
import re
import sys
import tempfile
import urllib.request
from typing import Dict, List, Optional

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_PIN = os.path.join(SCRIPT_DIR, "pin.json")
DEFAULT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
RAW_URL = "https://raw.githubusercontent.com/{repository}/{commit}/{file}"
DOWNLOAD_TIMEOUT_SECONDS = 60
PIN_KEYS = ("repository", "commit", "file", "sha256", "template", "output")
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")


class GenerationError(Exception):
    """A failure that ends the run with exit code 1 before anything is written."""


def load_pin(path: str) -> Dict[str, str]:
    """Returns the pin at *path* after checking its keys and the commit and digest formats."""
    try:
        with open(path, encoding="utf-8") as handle:
            pin = json.load(handle)
    except (OSError, ValueError) as error:
        raise GenerationError("cannot read the pin %s: %s" % (path, error))
    if not isinstance(pin, dict):
        raise GenerationError("the pin %s is not a JSON object" % path)
    for key in PIN_KEYS:
        if not isinstance(pin.get(key), str) or not pin[key]:
            raise GenerationError("the pin %s needs a non-empty string '%s'" % (path, key))
    if not COMMIT_PATTERN.fullmatch(pin["commit"]):
        raise GenerationError(
            "the pin %s needs 'commit' as 40 lowercase hex characters, got %r" % (path, pin["commit"])
        )
    if not SHA256_PATTERN.fullmatch(pin["sha256"]):
        raise GenerationError(
            "the pin %s needs 'sha256' as 64 lowercase hex characters, got %r" % (path, pin["sha256"])
        )
    return pin


def read_text(path: str) -> str:
    """Returns the UTF-8 text at *path* with its line endings read as LF."""
    try:
        with open(path, encoding="utf-8") as handle:
            return handle.read()
    except (OSError, ValueError) as error:
        raise GenerationError("cannot read %s: %s" % (path, error))


def read_source(path: str) -> bytes:
    """Returns the bytes of the spec file at *path*."""
    try:
        with open(path, "rb") as handle:
            return handle.read()
    except OSError as error:
        raise GenerationError("cannot read %s: %s" % (path, error))


def download(url: str) -> bytes:
    """Returns the body of *url*, fetched with a timeout of DOWNLOAD_TIMEOUT_SECONDS."""
    try:
        with urllib.request.urlopen(url, timeout=DOWNLOAD_TIMEOUT_SECONDS) as response:
            return response.read()
    except (OSError, http.client.HTTPException, ValueError) as error:
        raise GenerationError("cannot download %s: %s" % (url, error))


def fill(template: str, values: Dict[str, str]) -> str:
    """Replaces each {{NAME}} of *values* in *template*; a remaining '{{' is an error."""
    text = template
    for name, value in values.items():
        text = text.replace("{{%s}}" % name, value)
    leftover = text.find("{{")
    if leftover >= 0:
        line = text.count("\n", 0, leftover) + 1
        excerpt = text[leftover:].split("\n", 1)[0][:80]
        raise GenerationError("the template leaves a placeholder unfilled on line %d: %s" % (line, excerpt))
    return text


def file_mode(path: str) -> int:
    """The permission bits of *path*, or those the umask gives a new file when it does not exist."""
    try:
        return os.stat(path).st_mode & 0o777
    except FileNotFoundError:
        umask = os.umask(0)
        os.umask(umask)
        return 0o666 & ~umask


def write_atomically(path: str, text: str) -> None:
    """Writes *text* to *path* as UTF-8 with LF line endings through a temporary file."""
    directory = os.path.dirname(path) or "."
    temporary: Optional[str] = None
    try:
        mode = file_mode(path)
        handle, temporary = tempfile.mkstemp(prefix=".sac-spec-", dir=directory)
        with os.fdopen(handle, "w", encoding="utf-8", newline="\n") as output:
            output.write(text)
        # mkstemp creates the file readable by its owner only.
        os.chmod(temporary, mode)
        os.replace(temporary, path)
    except OSError as error:
        if temporary is not None and os.path.exists(temporary):
            os.remove(temporary)
        raise GenerationError("cannot write %s: %s" % (path, error))


def generate(pin_path: str, root: str, source: Optional[str]) -> str:
    """Writes the output the pin names and returns its path."""
    pin = load_pin(pin_path)
    template = read_text(os.path.join(root, pin["template"]))
    if source is None:
        data = download(RAW_URL.format(repository=pin["repository"], commit=pin["commit"], file=pin["file"]))
    else:
        data = read_source(source)

    digest = hashlib.sha256(data).hexdigest()
    if digest != pin["sha256"]:
        raise GenerationError(
            "the spec file has sha256 %s, the pin expects %s" % (digest, pin["sha256"])
        )

    text = fill(template, {
        "REPOSITORY": pin["repository"],
        "FILE": pin["file"],
        "COMMIT": pin["commit"],
        "SHA256": digest,
        "SIZE": str(len(data)),
        "BASE64": base64.b64encode(data).decode("ascii"),
    })
    output = os.path.join(root, pin["output"])
    write_atomically(output, text)
    return output


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--pin", default=DEFAULT_PIN, help="the pin file (default: pin.json beside this script)")
    parser.add_argument("--root", default=DEFAULT_ROOT,
                        help="the directory the pin's paths resolve against (default: the repository root)")
    parser.add_argument("--source", help="read the spec file from this path instead of downloading it")
    args = parser.parse_args(argv)
    try:
        output = generate(args.pin, args.root, args.source)
    except GenerationError as error:
        print("error: %s" % error, file=sys.stderr)
        return 1
    print("wrote %s" % output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
