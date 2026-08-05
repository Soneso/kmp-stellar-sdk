#!/usr/bin/env python3
"""Builds the SEP-0051 (XDR-JSON) conformance corpus from the hand-authored seeds.

Each seed is encoded to XDR with the pinned reference CLI and decoded straight
back, so every entry is checked by the reference before it is written. The
decoded document is what the SDK must emit, except for the seeds marked
incomparable, where the reference and SEP-0051 disagree and a named
transformation derives the specified form from the reference's output.

Output is byte-deterministic: fixed entry order, fixed key order and one
serialisation configuration. It carries no timestamp, so a re-run on an
unchanged input produces an unchanged file and any diff is real drift.

Usage:
    python3 generate_corpus.py [--output PATH]

Exit codes:
    0  corpus written
    1  a seed failed, or a declared divergence has disappeared
    2  the reference CLI is missing or does not match the pin
"""

import argparse
import json
import os
import shutil
import subprocess
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
ORACLE_DIR = os.path.join(REPO_ROOT, "tools", "sep-51-oracle")
PIN_FILE = os.path.join(ORACLE_DIR, "oracle-pin.json")
TYPE_MAP_FILE = os.path.join(ORACLE_DIR, "type_map.json")
NAME_MAP_FILE = os.path.join(ORACLE_DIR, "name-map.json")
XDR_SOURCE_CFG = os.path.join(REPO_ROOT, "tools", "xdrgen-kt", "xdr-source.cfg")
DEFAULT_OUTPUT = os.path.join(SCRIPT_DIR, "corpus.json")

sys.path.insert(0, SCRIPT_DIR)
from seeds import SEEDS  # noqa: E402

# One serialisation configuration for every string written into the corpus.
DUMP = dict(ensure_ascii=False, separators=(",", ":"))


class GenerationError(Exception):
    """A seed is wrong, or a declared divergence no longer holds."""


class PrerequisiteError(Exception):
    """The reference CLI is absent or does not match the pin."""


# --- Reference CLI ------------------------------------------------------------


def resolve_cli(pin):
    cli = os.environ.get("STELLAR_XDR", "stellar-xdr")
    resolved = shutil.which(cli)
    if resolved is None and os.path.isfile(cli) and os.access(cli, os.X_OK):
        resolved = cli
    if resolved is None:
        raise PrerequisiteError(
            "reference CLI '%s' not found. Install it with:\n    %s\n"
            "then ensure it is on PATH, or set STELLAR_XDR."
            % (cli, pin["install"])
        )
    return resolved


def verify_pin(cli, pin):
    """`<cli> version` prints the tool version first and its vendored xdr commit after."""
    try:
        out = subprocess.run([cli, "version"], capture_output=True, text=True,
                             check=True).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        raise PrerequisiteError(
            "'%s version' failed; is it the XDR reference CLI? (%s)" % (cli, error)
        )
    lines = out.splitlines()
    version = lines[0].split()[1] if lines and len(lines[0].split()) > 1 else "unknown"
    commit = "unknown"
    for line in lines:
        if line.startswith("xdr:"):
            commit = line.split()[1]
            break
    if version != pin["version"] or commit != pin["xdr_commit"]:
        raise PrerequisiteError(
            "reference CLI does not match the pin.\n"
            "    want: version %s, xdr %s\n"
            "    got:  version %s, xdr %s\n"
            "  Install the pinned build with:\n    %s"
            % (pin["version"], pin["xdr_commit"], version, commit, pin["install"])
        )


def encode(cli, type_name, document):
    result = subprocess.run(
        [cli, "encode", "--type", type_name],
        input=json.dumps(document, **DUMP), capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise GenerationError(
            "the reference CLI rejected the authored JSON for %s: %s"
            % (type_name, result.stderr.strip())
        )
    return result.stdout.strip()


def decode(cli, type_name, base64_text):
    result = subprocess.run(
        [cli, "decode", "--type", type_name, "--input", "single-base64",
         "--output", "json"],
        input=base64_text, capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise GenerationError(
            "the reference CLI could not decode its own encoding of %s: %s"
            % (type_name, result.stderr.strip())
        )
    return result.stdout.strip()


def known_types(cli):
    result = subprocess.run([cli, "types", "list"], capture_output=True, text=True)
    if result.returncode != 0:
        raise PrerequisiteError("'%s types list' failed" % cli)
    return set(result.stdout.split())


# --- Type names ---------------------------------------------------------------


def reference_name(kmp_type):
    """Derives the reference spelling of an SDK type name.

    The SDK writes acronyms in full capitals (`SCValXdr`, `PoolIDXdr`); the
    reference writes them as ordinary words (`ScVal`, `PoolId`). A capital keeps
    its case only when it starts a word: at the beginning of the name, after a
    lowercase letter or digit, or immediately before a lowercase letter.
    """
    name = kmp_type[:-3] if kmp_type.endswith("Xdr") else kmp_type
    out = []
    for index, character in enumerate(name):
        if not character.isupper():
            out.append(character)
            continue
        previous_is_upper = index > 0 and name[index - 1].isupper()
        next_is_lower = index + 1 < len(name) and name[index + 1].islower()
        out.append(character if not previous_is_upper or next_is_lower
                   else character.lower())
    return "".join(out)


def check_type_names(type_map, available):
    """Every seed must name a type the reference knows and the SDK spells consistently."""
    for seed in SEEDS:
        type_name, kmp_type = seed["type"], seed["kmp_type"]
        if type_name not in available:
            raise GenerationError(
                "the reference CLI does not know the type %s (seed for %s)"
                % (type_name, kmp_type)
            )
        mapped = type_map.get(kmp_type)
        if mapped is None:
            mapped = reference_name(kmp_type)
        if mapped != type_name:
            raise GenerationError(
                "seed names %s as the reference type for %s, but the mapping "
                "gives %s" % (type_name, kmp_type, mapped)
            )


# --- Specified forms ----------------------------------------------------------


def integer_string(value):
    """SEP-0051 Hyper Integer: a 64-bit value is a base-10 string, not a JSON number."""
    if not isinstance(value, int) or isinstance(value, bool):
        raise GenerationError(
            "integer_string expects a bare JSON number, got %r" % (value,)
        )
    return str(value)


def opaque_hex(value):
    """SEP-0051 Opaque Data (Fixed Length): fixed opaque is lowercase hex, not a byte array.

    The rewrite is by value, not by type: any non-empty array whose every element
    is an integer in 0..255 becomes hex. The document carries no type information,
    so there is nothing else to key on, and the whole document is walked because
    an affected field can sit nested inside a larger value.

    That heuristic would also rewrite an array of small integers that is not
    opaque data at all -- a variable-length uint32 array whose values happened to
    stay under 256 would be turned into a hex string. It is safe only because it
    runs on nothing else: the transform is applied to the output of the seeds
    explicitly flagged ``spec_form: opaque_hex``, which are the five types
    declaring an inline fixed opaque plus the one that embeds two of them, and
    none of those declares an integer array. A seed added to that flagged set has
    to be checked against this, and the divergence assertion in build_entry will
    not catch it: it only checks that something changed, not that the right thing
    did.
    """
    if isinstance(value, dict):
        return {key: opaque_hex(item) for key, item in value.items()}
    if isinstance(value, list):
        if value and all(isinstance(item, int) and not isinstance(item, bool)
                         and 0 <= item <= 255 for item in value):
            return "".join("%02x" % item for item in value)
        return [opaque_hex(item) for item in value]
    return value


SPEC_FORMS = {
    "integer_string": (
        integer_string,
        "SEP-0051 renders a 64-bit integer as a base-10 string; the reference "
        "emits a bare JSON number.",
    ),
    "opaque_hex": (
        opaque_hex,
        "SEP-0051 renders fixed-length opaque data as lowercase hex; the "
        "reference emits an array of byte numbers.",
    ),
}


# --- Corpus -------------------------------------------------------------------


def read_json(path, what, remedy):
    """Reads a committed artefact, reporting an absent or malformed one as a prerequisite."""
    try:
        with open(path) as handle:
            return json.load(handle)
    except FileNotFoundError:
        raise PrerequisiteError("%s not found at %s. %s" % (what, path, remedy))
    except json.JSONDecodeError as error:
        raise PrerequisiteError("%s at %s is not valid JSON: %s. %s"
                                % (what, path, error, remedy))


def read_pin():
    return read_json(PIN_FILE, "the reference pin", "It is committed; restore it from git.")


def read_sdk_xdr_commit():
    try:
        handle = open(XDR_SOURCE_CFG)
    except FileNotFoundError:
        raise PrerequisiteError("XDR pin config not found at %s" % XDR_SOURCE_CFG)
    with handle:
        for line in handle:
            if line.startswith("XDR_COMMIT="):
                return line.split("=", 1)[1].strip()
    raise PrerequisiteError("XDR_COMMIT missing from %s" % XDR_SOURCE_CFG)


def read_unresolvable():
    """The names the reference CLI could not resolve when the name table was built.

    Both lists are empty while the reference vendors an XDR commit at least as
    new as the SDK's. Recording them keeps the corpus honest about its own
    coverage when that stops being true.
    """
    name_map = read_json(
        NAME_MAP_FILE, "the name table",
        "Rebuild it with: ruby tools/sep-51-oracle/name_map.rb --diff",
    )
    verification = name_map.get("verification")
    # A plain `name_map.rb` run records a sentence here instead of the counts, because it
    # never probed the reference. The corpus needs the lists, so that is a prerequisite.
    if not isinstance(verification, dict):
        raise PrerequisiteError(
            "%s carries no verification block, so the name table was built without probing "
            "the reference. Rebuild it with: ruby tools/sep-51-oracle/name_map.rb --diff"
            % NAME_MAP_FILE
        )
    try:
        return (sorted(verification["enum_members_unresolvable"]),
                sorted(verification["struct_types_unresolvable"]))
    except KeyError as error:
        raise PrerequisiteError(
            "%s has a verification block without %s. Rebuild it with: "
            "ruby tools/sep-51-oracle/name_map.rb --diff" % (NAME_MAP_FILE, error)
        )


def build_entry(cli, seed):
    base64_text = encode(cli, seed["type"], seed["json"])
    oracle_text = decode(cli, seed["type"], base64_text)
    oracle_value = json.loads(oracle_text)

    if seed.get("oracle") != "incomparable":
        return {
            "type": seed["type"],
            "kmp_type": seed["kmp_type"],
            "xdr": base64_text,
            "json": json.dumps(oracle_value, **DUMP),
            "oracle": "reference",
        }

    form = seed.get("spec_form")
    if form not in SPEC_FORMS:
        raise GenerationError(
            "seed for %s is incomparable but names no known spec_form (%r)"
            % (seed["type"], form)
        )
    transform, reason = SPEC_FORMS[form]
    specified = transform(oracle_value)
    if specified == oracle_value:
        raise GenerationError(
            "seed for %s is marked incomparable under %s, but the reference "
            "already emits the specified form; the divergence is gone and the "
            "seed must be reclassified" % (seed["type"], form)
        )
    return {
        "type": seed["type"],
        "kmp_type": seed["kmp_type"],
        "xdr": base64_text,
        "json": json.dumps(specified, **DUMP),
        "oracle": "incomparable",
        "reason": reason,
        "oracle_json": json.dumps(oracle_value, **DUMP),
    }


def generate(output_path):
    pin = read_pin()
    cli = resolve_cli(pin)
    verify_pin(cli, pin)

    type_map = read_json(
        TYPE_MAP_FILE, "the type map",
        "Rebuild it with: ruby tools/sep-51-oracle/name_map.rb --diff",
    )["type_map"]
    check_type_names(type_map, known_types(cli))

    entries = [build_entry(cli, seed) for seed in SEEDS]
    unresolvable_enum_members, unresolvable_struct_types = read_unresolvable()

    corpus = {
        "metadata": {
            "description": (
                "SEP-0051 (XDR-JSON) conformance corpus. Each entry pins the "
                "exact JSON text the SDK must emit for one XDR value and the "
                "base64 XDR that value encodes to."
            ),
            "reference_tool": pin["tool"],
            "reference_version": pin["version"],
            "reference_xdr_commit": pin["xdr_commit"],
            "sdk_xdr_commit": read_sdk_xdr_commit(),
            "entry_count": len(entries),
            "unresolvable_enum_members": unresolvable_enum_members,
            "unresolvable_struct_types": unresolvable_struct_types,
        },
        "entries": entries,
    }

    with open(output_path, "w") as handle:
        json.dump(corpus, handle, ensure_ascii=False, indent=2, sort_keys=False)
        handle.write("\n")

    return corpus


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--output", default=DEFAULT_OUTPUT,
                        help="where to write corpus.json (default: %(default)s)")
    args = parser.parse_args()

    try:
        corpus = generate(args.output)
    except PrerequisiteError as error:
        print("generate_corpus.py: %s" % error, file=sys.stderr)
        return 2
    except GenerationError as error:
        print("generate_corpus.py: %s" % error, file=sys.stderr)
        return 1

    entries = corpus["entries"]
    incomparable = [entry for entry in entries if entry["oracle"] == "incomparable"]
    incomparable_types = sorted({entry["type"] for entry in incomparable})
    print("Wrote %s" % args.output)
    print("  entries:        %d" % len(entries))
    print("  distinct types: %d" % len({entry["type"] for entry in entries}))
    print("  comparable:     %d" % (len(entries) - len(incomparable)))
    print("  incomparable:   %d (%s)" % (len(incomparable),
                                         ", ".join(incomparable_types)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
