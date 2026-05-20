#!/usr/bin/env python3
"""
Stellar KMP SDK API Reference Generator

Generates a compact markdown file listing all public class/method/property signatures
for the KMP Stellar SDK by parsing Kotlin source files.

Usage: python3 generate_api_reference.py
"""

import os
import re
import sys
from pathlib import Path
from dataclasses import dataclass, field

# Configuration — paths derived from script location.
# Script lives at <repo>/tools/skill-generator/generate_api_reference.py, so
# REPO_ROOT is three levels up. SDK source is at <repo>/stellar-sdk/src/.
# Output is written into the agent skill references next to the SEP refs.
REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SDK_ROOT = REPO_ROOT / "stellar-sdk" / "src"
PKG_REL_PATH = Path("kotlin") / "com" / "soneso" / "stellar" / "sdk"
OUTPUT_PATH = REPO_ROOT / "skills" / "kmp-stellar-sdk" / "references" / "api_reference.md"

# Source sets to scan.
# (platform_label, source_set_dir, subdir_filter)
#   - platform_label: empty string for commonMain (no tag in output);
#     non-empty string is appended to class headers as "(label)".
#   - source_set_dir: directory under stellar-sdk/src/ to scan.
#   - subdir_filter: if set, restrict scanning to that subdir under the package
#     root (used to limit platform sourcesets to the smartaccount package).
SCAN_SOURCES: list[tuple[str, str, str | None]] = [
    ("",             "commonMain",  None),
    ("androidMain",  "androidMain", "smartaccount"),
    ("nativeMain",   "nativeMain",  "smartaccount"),
    ("jsMain",       "jsMain",      "smartaccount"),
]

# Directories to skip entirely
SKIP_DIRS = {"xdr", "crypto"}

# Files to skip
SKIP_FILES: set[str] = set()

# Group titles (order matters for output)
GROUP_TITLES = {
    "core": "Core Classes",
    "contract": "Smart Contracts",
    "smartaccount": "Smart Accounts",
    "scval": "Smart Contract Values",
    "horizon": "Horizon Server",
    "requests": "Horizon Requests",
    "responses": "Horizon Responses",
    "horizon_exceptions": "Horizon Exceptions",
    "rpc": "Soroban RPC",
    "rpc_exceptions": "RPC Exceptions",
    "sep": "SEP Services",
}


@dataclass
class ClassInfo:
    """Parsed class/object/interface information."""

    name: str
    kind: str = ""  # "sealed", "data", "abstract", "enum", "object", "data object", "interface", "fun interface"
    parent: str = ""
    interfaces: list = field(default_factory=list)
    type_params: str = ""
    constructor_params: str = ""  # raw primary constructor params
    constructor_public: bool = True
    companion_members: list = field(default_factory=list)
    properties: list = field(default_factory=list)
    methods: list = field(default_factory=list)
    enum_values: list = field(default_factory=list)
    override_names: set = field(default_factory=set)
    is_deprecated: bool = False
    platform: str = ""  # empty for commonMain; else source-set label (e.g. "androidMain")
    is_expect: bool = False  # KMP `expect` declaration in commonMain
    is_actual: bool = False  # KMP `actual` declaration in a platform sourceset


# ---------------------------------------------------------------------------
# Text utilities
# ---------------------------------------------------------------------------


def compact_ws(s: str) -> str:
    """Normalize whitespace."""
    return re.sub(r"\s+", " ", s).strip()


def strip_comments(content: str) -> str:
    """Remove all comments from Kotlin source, preserving string literals."""
    result: list[str] = []
    i = 0
    n = len(content)
    while i < n:
        # String literals — skip intact
        if content[i] == '"':
            # Triple-quoted raw string
            if i + 2 < n and content[i : i + 3] == '"""':
                result.append('"""')
                i += 3
                while i < n:
                    if content[i : i + 3] == '"""':
                        result.append('"""')
                        i += 3
                        break
                    result.append(content[i])
                    i += 1
            else:
                result.append('"')
                i += 1
                while i < n and content[i] != '"' and content[i] != "\n":
                    if content[i] == "\\":
                        result.append(content[i : i + 2])
                        i += 2
                    else:
                        result.append(content[i])
                        i += 1
                if i < n and content[i] == '"':
                    result.append('"')
                    i += 1
        # Char literals
        elif content[i] == "'":
            result.append("'")
            i += 1
            while i < n and content[i] != "'" and content[i] != "\n":
                if content[i] == "\\":
                    result.append(content[i : i + 2])
                    i += 2
                else:
                    result.append(content[i])
                    i += 1
            if i < n and content[i] == "'":
                result.append("'")
                i += 1
        # Multi-line comments (/* ... */)
        elif content[i : i + 2] == "/*":
            i += 2
            while i < n and content[i : i + 2] != "*/":
                if content[i] == "\n":
                    result.append("\n")
                i += 1
            i += 2  # skip */
        # Single-line comments
        elif content[i : i + 2] == "//":
            while i < n and content[i] != "\n":
                i += 1
        else:
            result.append(content[i])
            i += 1
    return "".join(result)


def strip_annotations(line: str) -> str:
    """Remove Kotlin annotations except @Deprecated."""
    # Remove all annotations except @Deprecated
    line = re.sub(r"@(?!Deprecated)\w+(?:\([^)]*\))?\s*", "", line)
    return line.strip()


# ---------------------------------------------------------------------------
# Brace / paren / angle bracket balancing
# ---------------------------------------------------------------------------


def balance_braces(text: str, start: int) -> int:
    """Find the closing brace matching the opening brace at `start`."""
    depth = 0
    i = start
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
        elif ch == '"':
            if i + 2 < n and text[i : i + 3] == '"""':
                i += 3
                while i < n and text[i : i + 3] != '"""':
                    i += 1
                i += 2  # skip closing triple (loop i+=1)
            else:
                i += 1
                while i < n and text[i] != '"' and text[i] != "\n":
                    if text[i] == "\\":
                        i += 1
                    i += 1
        i += 1
    return n - 1


def balance_parens(text: str, start: int) -> int:
    """Find the closing paren matching the opening paren at `start`."""
    depth = 0
    i = start
    n = len(text)
    while i < n:
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return i
        elif text[i] == '"':
            i += 1
            while i < n and text[i] != '"' and text[i] != "\n":
                if text[i] == "\\":
                    i += 1
                i += 1
        i += 1
    return n - 1


def balance_angles(text: str, start: int) -> int:
    """Find the closing > matching the opening < at `start`."""
    depth = 0
    i = start
    n = len(text)
    while i < n:
        if text[i] == "<":
            depth += 1
        elif text[i] == ">":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return n - 1


# ---------------------------------------------------------------------------
# Visibility and skip checks
# ---------------------------------------------------------------------------

SKIP_OVERRIDES = {"toString", "hashCode", "equals", "compareTo"}


def is_private_or_internal(text: str) -> bool:
    """Check if a declaration starts with private or internal."""
    stripped = text.lstrip()
    return bool(re.match(r"(private|internal|protected)\s+", stripped))


def should_skip_method(name: str, line: str) -> bool:
    """Check if a method should be skipped."""
    if name in SKIP_OVERRIDES and "override" in line:
        return True
    return False


# ---------------------------------------------------------------------------
# Parameter splitting
# ---------------------------------------------------------------------------


def split_params(params_str: str) -> list[str]:
    """Split a comma-separated parameter list respecting nested <>, (), {}."""
    result = []
    current: list[str] = []
    depth_a = depth_p = depth_b = 0
    for ch in params_str:
        if ch == "<":
            depth_a += 1
        elif ch == ">":
            depth_a -= 1
        elif ch == "(":
            depth_p += 1
        elif ch == ")":
            depth_p -= 1
        elif ch == "{":
            depth_b += 1
        elif ch == "}":
            depth_b -= 1
        elif (
            ch == "," and depth_a == 0 and depth_p == 0 and depth_b == 0
        ):
            result.append("".join(current))
            current = []
            continue
        current.append(ch)
    if current:
        result.append("".join(current))
    return result


# ---------------------------------------------------------------------------
# Constructor property extraction
# ---------------------------------------------------------------------------


def extract_constructor_properties(params_str: str) -> tuple[list[str], set[str]]:
    """Extract val/var properties from primary constructor parameters.

    Returns (properties_list, override_names) where override_names is the set
    of property names that are overrides from a parent class.
    """
    properties = []
    override_names: set[str] = set()
    params = split_params(params_str)
    for param in params:
        param = strip_annotations(param).strip()
        if not param:
            continue
        if re.match(r"(private|internal|protected)\s+", param):
            continue
        is_override = param.startswith("override ")
        m = re.match(
            r"(?:override\s+)?(val|var)\s+(\w+)\s*:\s*(.+)",
            param,
        )
        if m:
            kind = m.group(1)
            name = m.group(2)
            type_str = m.group(3).strip()
            # Remove default value
            type_str = remove_default_value(type_str)
            properties.append(f"{kind} {name}: {type_str}")
            if is_override:
                override_names.add(name)
    return properties, override_names


def remove_default_value(type_and_default: str) -> str:
    """Remove = default_value from a type string, respecting nested brackets."""
    depth_a = depth_p = depth_b = 0
    for i, ch in enumerate(type_and_default):
        if ch == "<":
            depth_a += 1
        elif ch == ">":
            depth_a -= 1
        elif ch == "(":
            depth_p += 1
        elif ch == ")":
            depth_p -= 1
        elif ch == "{":
            depth_b += 1
        elif ch == "}":
            depth_b -= 1
        elif ch == "=" and depth_a == 0 and depth_p == 0 and depth_b == 0:
            return type_and_default[:i].strip()
    return type_and_default.strip()


# ---------------------------------------------------------------------------
# Method signature extraction
# ---------------------------------------------------------------------------


def extract_method_sig(text: str) -> str | None:
    """Extract a clean method signature from a declaration line.

    Returns the signature string or None if not a method.
    """
    text = compact_ws(text)
    text = strip_annotations(text)

    if is_private_or_internal(text):
        return None

    # Match method pattern: [override] [inline] [suspend] fun [<T>] name(
    # `inline` is dropped from the emitted signature — it is a call-site
    # implementation detail. Without this modifier in the pattern, inline
    # methods are silently skipped (e.g. SmartAccountEventEmitter.on / once).
    m = re.match(
        r"(?:override\s+)?"
        r"(?:inline\s+)?"
        r"((?:suspend\s+)?)"
        r"fun\s+"
        r"(?:<[^>]+>\s+)?"  # type params
        r"(\w+)\s*\(",
        text,
    )
    if not m:
        return None

    suspend_kw = m.group(1).strip()
    method_name = m.group(2)

    if should_skip_method(method_name, text):
        return None

    # Find matching close paren
    paren_start = text.index("(", m.start(2))
    paren_end = balance_parens(text, paren_start)
    params_raw = text[paren_start + 1 : paren_end]

    # Clean params: strip annotations, compact
    cleaned_params = clean_method_params(params_raw)

    # Return type after closing paren
    after = text[paren_end + 1 :].strip()
    return_type = ""
    if after.startswith(":"):
        return_type = after[1:].strip()
        # Remove trailing { or = or anything after
        return_type = re.sub(r"\s*[{=].*$", "", return_type).strip()

    sig = ""
    if suspend_kw:
        sig += "suspend "
    sig += f"fun {method_name}({cleaned_params})"
    if return_type:
        sig += f": {return_type}"
    return sig


def clean_method_params(params_raw: str) -> str:
    """Clean method parameters: strip annotations, normalize whitespace."""
    params = split_params(params_raw)
    cleaned = []
    for p in params:
        p = strip_annotations(p).strip()
        p = compact_ws(p)
        if p:
            cleaned.append(p)
    return ", ".join(cleaned)


# ---------------------------------------------------------------------------
# Property extraction
# ---------------------------------------------------------------------------


def extract_property_sig(text: str) -> str | None:
    """Extract a property signature from a declaration line."""
    text = compact_ws(text)
    text = strip_annotations(text)

    if is_private_or_internal(text):
        return None

    # const val
    m = re.match(r"const\s+val\s+(\w+)\s*(?::\s*(\S+))?\s*=\s*(.*)", text)
    if m:
        name = m.group(1)
        type_str = m.group(2) or ""
        value = m.group(3).strip()
        # Truncate long values
        if len(value) > 60:
            value = value[:57] + "..."
        if type_str:
            return f"const val {name}: {type_str} = {value}"
        return f"const val {name} = {value}"

    # Regular val/var with type
    m = re.match(
        r"(?:override\s+)?(?:abstract\s+)?(val|var)\s+(\w+)\s*:\s*(.+)",
        text,
    )
    if m:
        kind = m.group(1)
        name = m.group(2)
        type_str = m.group(3).strip()
        # Remove default value, getter body, etc.
        type_str = re.sub(r"\s*get\s*\(.*$", "", type_str)
        type_str = re.sub(r"\s*=\s*.*$", "", type_str)
        type_str = re.sub(r"\s*\{.*$", "", type_str)
        type_str = type_str.strip()
        if type_str:
            abstract = "abstract " if "abstract" in text else ""
            return f"{abstract}{kind} {name}: {type_str}"

    return None


# ---------------------------------------------------------------------------
# Companion object extraction
# ---------------------------------------------------------------------------


def extract_companion_body(body: str) -> tuple[str, str]:
    """Find companion object in a class body and return (companion_body, remaining_body).

    Returns ("", body) if no companion object found.
    """
    m = re.search(r"companion\s+object\s*(?:\w+)?\s*\{", body)
    if not m:
        return "", body

    brace_start = m.end() - 1
    brace_end = balance_braces(body, brace_start)
    companion_body = body[brace_start + 1 : brace_end]

    # Remove companion block from body
    remaining = body[: m.start()] + body[brace_end + 1 :]
    return companion_body, remaining


def parse_companion_members(companion_body: str) -> list[str]:
    """Parse members from a companion object body."""
    members = []
    declarations = extract_declarations(companion_body)

    for decl in declarations:
        decl_stripped = compact_ws(strip_annotations(decl))
        if is_private_or_internal(decl_stripped):
            continue

        # Try as method
        sig = extract_method_sig(decl)
        if sig:
            members.append(sig)
            continue

        # Try as property/constant
        sig = extract_property_sig(decl)
        if sig:
            members.append(sig)

    return members


# ---------------------------------------------------------------------------
# Declaration extraction (depth-0 scanning)
# ---------------------------------------------------------------------------


def extract_declarations(body: str) -> list[str]:
    """Extract top-level declarations from a class/companion body.

    Scans at brace depth 0, collecting logical declarations.
    Tracks paren depth so multi-line method signatures stay together.
    """
    declarations = []
    current: list[str] = []
    i = 0
    n = len(body)
    brace_depth = 0
    paren_depth = 0

    while i < n:
        ch = body[i]

        # String literals
        if ch == '"':
            if i + 2 < n and body[i : i + 3] == '"""':
                current.append('"""')
                i += 3
                while i < n and body[i : i + 3] != '"""':
                    current.append(body[i])
                    i += 1
                if i < n:
                    current.append('"""')
                    i += 3
                continue
            else:
                current.append('"')
                i += 1
                while i < n and body[i] != '"' and body[i] != "\n":
                    if body[i] == "\\":
                        current.append(body[i : i + 2])
                        i += 2
                    else:
                        current.append(body[i])
                        i += 1
                if i < n and body[i] == '"':
                    current.append('"')
                i += 1
                continue

        if ch == "(":
            paren_depth += 1
        elif ch == ")":
            paren_depth -= 1

        if ch == "{":
            if brace_depth == 0 and paren_depth == 0:
                # End of a declaration header — save and skip body
                line_text = "".join(current).strip()
                if line_text:
                    declarations.append(line_text)
                current = []
                end = balance_braces(body, i)
                i = end + 1
                continue
            else:
                brace_depth += 1
        elif ch == "}":
            brace_depth -= 1
        elif ch == "\n" and brace_depth == 0 and paren_depth == 0:
            # In Kotlin, newlines can end declarations (only when parens balanced)
            line_text = "".join(current).strip()
            if line_text:
                # Check if this looks like a complete declaration
                if is_complete_declaration(line_text):
                    declarations.append(line_text)
                    current = []
                else:
                    current.append(" ")
            i += 1
            continue

        current.append(ch)
        i += 1

    remaining = "".join(current).strip()
    if remaining:
        declarations.append(remaining)

    return declarations


def is_complete_declaration(text: str) -> bool:
    """Check if text looks like a complete Kotlin declaration."""
    text = text.strip()
    if not text:
        return False

    # Ends with a type or value — likely complete
    # Not complete if it ends with , or an operator
    if text.endswith((",", "=", "->", "+", "-", "&&", "||", ".")):
        return False

    # A declaration typically starts with a keyword
    stripped = strip_annotations(text)
    stripped = re.sub(r"^(override|abstract|open|suspend|inline)\s+", "", stripped)
    if re.match(
        r"(val|var|fun|class|interface|object|enum|constructor|companion|const|sealed|data|init)\b",
        stripped,
    ):
        return True

    # Also complete if it's just a property access or ends with a type
    return True


# ---------------------------------------------------------------------------
# Enum value extraction
# ---------------------------------------------------------------------------


def extract_enum_values(body: str) -> list[str]:
    """Extract enum constant names from the top of an enum class body."""
    # Find the first ; which separates enum values from members
    # Also need to handle enum bodies without semicolons
    semi = -1
    depth_p = depth_b = 0
    for i, ch in enumerate(body):
        if ch == "(":
            depth_p += 1
        elif ch == ")":
            depth_p -= 1
        elif ch == "{":
            depth_b += 1
        elif ch == "}":
            depth_b -= 1
        elif ch == ";" and depth_p == 0 and depth_b == 0:
            semi = i
            break

    values_section = body[:semi] if semi != -1 else body

    values = []
    # Match enum value names (possibly with constructor args)
    for m in re.finditer(r"(\w+)\s*(?:\([^)]*\))?(?:\s*,|\s*$)", values_section):
        name = m.group(1)
        # Skip Kotlin keywords that might appear
        if name in (
            "companion",
            "object",
            "class",
            "fun",
            "val",
            "var",
            "override",
            "abstract",
            "init",
        ):
            break
        values.append(name)

    return values


# ---------------------------------------------------------------------------
# Secondary constructor extraction
# ---------------------------------------------------------------------------


def extract_secondary_constructors(decls: list[str]) -> list[str]:
    """Extract secondary constructor signatures from declarations."""
    constructors = []
    for decl in decls:
        text = compact_ws(strip_annotations(decl))
        if is_private_or_internal(text):
            continue
        m = re.match(r"constructor\s*\(", text)
        if m:
            paren_start = text.index("(")
            paren_end = balance_parens(text, paren_start)
            params = clean_method_params(text[paren_start + 1 : paren_end])
            constructors.append(f"constructor({params})")
    return constructors


# ---------------------------------------------------------------------------
# File grouping
# ---------------------------------------------------------------------------


def determine_group(rel_path: str) -> str:
    """Map a file's relative path to a group key."""
    parts = rel_path.split(os.sep)
    if len(parts) == 1:
        return "core"

    subdir = parts[0]

    if subdir == "horizon":
        if len(parts) == 2:
            return "horizon"
        sub2 = parts[1]
        if sub2 == "requests":
            return "requests"
        elif sub2 == "responses":
            return "responses"
        elif sub2 == "exceptions":
            return "horizon_exceptions"
        return "responses"  # nested response subdirs (effects/, operations/)

    if subdir == "rpc":
        if len(parts) >= 2 and parts[1] == "exception":
            return "rpc_exceptions"
        return "rpc"

    if subdir == "contract":
        return "contract"

    if subdir == "scval":
        return "scval"

    if subdir == "sep":
        return "sep"

    if subdir == "smartaccount":
        return "smartaccount"

    return "core"


# ---------------------------------------------------------------------------
# Main class parser
# ---------------------------------------------------------------------------

# Regex to find class-like declarations
CLASS_START = re.compile(
    r"^[ \t]*"
    r"((?:@Deprecated(?:\([^)]*\))?\s*)?)"  # optional @Deprecated
    r"((?:(?:public|expect|actual|sealed|data|abstract|open)\s+)*)"  # modifiers (incl. public for explicit API mode + expect/actual)
    r"(data\s+object|object|enum\s+class|fun\s+interface|interface|class)\s+"  # kind
    r"(\w+)",  # name
    re.MULTILINE,
)


def parse_kotlin_file(filepath: Path) -> list[ClassInfo]:
    """Parse a Kotlin file and extract all public classes with their members."""
    content = filepath.read_text(encoding="utf-8")
    clean = strip_comments(content)

    results = []

    # Stack of (short_name, body_end_index) for currently-open enclosing classes.
    # As CLASS_START matches iterate left-to-right, we pop any enclosure whose
    # body has already closed before the current match and qualify the class
    # name with the remaining enclosing chain (joined by `.`).
    enclosing_stack: list[tuple[str, int]] = []

    for m in CLASS_START.finditer(clean):
        # Pop enclosures whose body ended before this match
        while enclosing_stack and enclosing_stack[-1][1] < m.start():
            enclosing_stack.pop()

        deprecated_str = m.group(1).strip()
        modifiers = m.group(2).strip()
        kind_raw = m.group(3).strip()
        class_name = m.group(4)

        # Skip private/internal classes
        # Check the text before the match on the same line
        line_start = clean.rfind("\n", 0, m.start()) + 1
        prefix = clean[line_start : m.start()].strip()
        if prefix in ("private", "internal", "protected"):
            continue

        # Determine kind string
        kind = ""
        if "sealed" in modifiers:
            kind = "sealed"
        elif "abstract" in modifiers:
            kind = "abstract"
        elif "data" in modifiers and kind_raw == "class":
            kind = "data"

        if kind_raw == "data object":
            kind = "data object"
        elif kind_raw == "object":
            kind = "object"
        elif kind_raw == "enum class":
            kind = "enum"
        elif kind_raw == "interface":
            kind = "interface"
        elif kind_raw == "fun interface":
            kind = "fun interface"

        # Parse forward from end of class name
        pos = m.end()

        # Optional type parameters <T>
        type_params = ""
        while pos < len(clean) and clean[pos] in (" ", "\t", "\n"):
            pos += 1
        if pos < len(clean) and clean[pos] == "<":
            angle_end = balance_angles(clean, pos)
            type_params = clean[pos : angle_end + 1]
            pos = angle_end + 1

        # Optional constructor visibility + constructor keyword
        while pos < len(clean) and clean[pos] in (" ", "\t", "\n"):
            pos += 1

        constructor_public = True
        # Check for visibility + constructor
        ahead = clean[pos : pos + 50].lstrip()
        if re.match(r"(private|internal|protected)\s+constructor", ahead):
            constructor_public = False
            vis_m = re.match(r"(private|internal|protected)\s+constructor", ahead)
            pos += clean[pos:].index("constructor") + len("constructor")
        elif ahead.startswith("constructor"):
            pos += clean[pos:].index("constructor") + len("constructor")

        # Optional primary constructor params (...)
        constructor_params = ""
        while pos < len(clean) and clean[pos] in (" ", "\t", "\n"):
            pos += 1
        if pos < len(clean) and clean[pos] == "(":
            paren_end = balance_parens(clean, pos)
            constructor_params = compact_ws(clean[pos + 1 : paren_end])
            pos = paren_end + 1

        # Optional supertype list : SuperType, Interface
        parent = ""
        interfaces = []
        while pos < len(clean) and clean[pos] in (" ", "\t", "\n"):
            pos += 1
        if pos < len(clean) and clean[pos] == ":":
            pos += 1
            # Collect supertypes, respecting nested parens/angles.
            # Stop at { when balanced, or at newline when balanced
            # (handles body-less classes like sealed subclass data classes).
            super_parts: list[str] = []
            sp_paren = 0
            sp_angle = 0
            while pos < len(clean):
                ch = clean[pos]
                if ch == "{" and sp_paren == 0 and sp_angle == 0:
                    break
                if ch == "(":
                    sp_paren += 1
                elif ch == ")":
                    sp_paren -= 1
                elif ch == "<":
                    sp_angle += 1
                elif ch == ">":
                    sp_angle -= 1
                elif ch == "\n" and sp_paren == 0 and sp_angle == 0:
                    # Newline at balanced depth — check if next non-ws is {
                    rest = clean[pos + 1 :].lstrip()
                    if rest.startswith("{"):
                        super_parts.append(" ")
                        pos += 1
                        continue
                    else:
                        # Class has no body — stop here
                        break
                super_parts.append(ch)
                pos += 1
            super_str = compact_ws("".join(super_parts))
            if super_str:
                # Split supertypes on commas (respecting <> nesting)
                supertypes = split_params(super_str)
                for st in supertypes:
                    st = st.strip()
                    # Remove constructor call parens: Foo() -> Foo
                    st = re.sub(r"\(.*\)$", "", st).strip()
                    if st:
                        if not parent:
                            parent = st
                        else:
                            interfaces.append(st)

        # Find opening brace (skip whitespace only)
        while pos < len(clean) and clean[pos] in (" ", "\t", "\n"):
            pos += 1

        # Check if class has a body
        has_body = pos < len(clean) and clean[pos] == "{"
        body = ""
        if has_body:
            body_end = balance_braces(clean, pos)
            body = clean[pos + 1 : body_end]

        # Detect KMP expect/actual modifiers
        modifier_tokens = modifiers.split()
        is_expect = "expect" in modifier_tokens
        is_actual = "actual" in modifier_tokens

        # Qualify the name with the enclosing chain (short names joined by `.`)
        # so nested sealed subclasses, policy param variants, builder classes,
        # etc. get distinct qualified headers instead of flattening to their
        # short names.
        if enclosing_stack:
            qualified_name = ".".join(s[0] for s in enclosing_stack) + "." + class_name
        else:
            qualified_name = class_name

        # Push this class onto the stack so later matches inside its body
        # receive a qualified name. Push the short name (not the qualified
        # name) — joining across the stack produces the full chain.
        if has_body:
            enclosing_stack.append((class_name, body_end))

        # Build ClassInfo
        info = ClassInfo(
            name=qualified_name,
            kind=kind,
            parent=parent,
            interfaces=interfaces,
            type_params=type_params,
            constructor_params=constructor_params,
            constructor_public=constructor_public,
            is_deprecated=bool(deprecated_str),
            is_expect=is_expect,
            is_actual=is_actual,
        )

        # Extract constructor properties (from primary constructor val/var)
        if constructor_params:
            info.properties, info.override_names = extract_constructor_properties(constructor_params)

        # Handle enum values
        if kind == "enum" and has_body:
            info.enum_values = extract_enum_values(body)

        # Extract companion object
        remaining_body = body
        if has_body:
            companion_body, remaining_body = extract_companion_body(body)
            if companion_body:
                info.companion_members = parse_companion_members(companion_body)

        # Extract declarations from remaining body
        decls = extract_declarations(remaining_body) if has_body else []

        # Extract secondary constructors
        for ctor in extract_secondary_constructors(decls):
            info.methods.insert(0, ctor)  # constructors first

        # Extract methods and properties
        for decl in decls:
            text = compact_ws(strip_annotations(decl))
            if is_private_or_internal(text):
                continue

            # Skip nested class/object/interface declarations
            if re.match(
                r"(?:sealed|data|abstract|open|inner)?\s*(?:class|object|interface|enum)\s+",
                text,
            ):
                continue

            # Skip companion (already extracted)
            if text.startswith("companion"):
                continue

            # Skip init blocks
            if text.startswith("init"):
                continue

            # Skip constructor (already extracted)
            if text.startswith("constructor"):
                continue

            # Try as method
            sig = extract_method_sig(decl)
            if sig:
                info.methods.append(sig)
                continue

            # Try as property (but avoid duplicates from constructor properties)
            sig = extract_property_sig(decl)
            if sig:
                # Check if property name already in constructor properties
                prop_name_m = re.search(r"(?:val|var)\s+(\w+)", sig)
                if prop_name_m:
                    prop_name = prop_name_m.group(1)
                    already_in_ctor = any(
                        re.search(r"(?:val|var)\s+" + re.escape(prop_name) + r"\b", p)
                        for p in info.properties
                    )
                    if not already_in_ctor:
                        info.properties.append(sig)
                else:
                    info.properties.append(sig)

        results.append(info)

    return results


# ---------------------------------------------------------------------------
# Markdown formatting
# ---------------------------------------------------------------------------


def format_class_header(info: ClassInfo) -> str:
    """Format the class header line."""
    header = "## "
    if info.is_deprecated:
        header += "@Deprecated "
    if info.kind:
        header += f"{info.kind} "
    header += info.name
    if info.type_params:
        header += info.type_params
    if info.parent:
        header += f" : {info.parent}"
        for iface in info.interfaces:
            header += f", {iface}"
    elif info.interfaces:
        header += f" : {', '.join(info.interfaces)}"
    if info.platform:
        header += f" ({info.platform})"
    return header


def clean_constructor_params_no_overrides(params_raw: str, override_names: set) -> str:
    """Clean constructor params; for overridden val/var, keep the param but
    strip the `override val/var` modifier so it appears as a plain arg.

    The property listing skips override names when the class has a parent,
    so this does not create duplication: overridden params still appear as
    constructor arguments (required for callers), but not as declared
    properties (they belong to the parent class)."""
    params = split_params(params_raw)
    cleaned = []
    for p in params:
        p = strip_annotations(p).strip()
        p = compact_ws(p)
        if not p:
            continue
        # If this param is an override val/var, keep name+type+default but
        # drop the `override val/var` modifier.
        m = re.match(r"override\s+(?:val|var)\s+(\w+)\s*:\s*(.+)", p)
        if m and m.group(1) in override_names:
            name = m.group(1)
            type_and_default = m.group(2)
            cleaned.append(f"{name}: {type_and_default}")
            continue
        # Otherwise strip any stray `override` keyword defensively
        p = re.sub(r"^override\s+", "", p)
        cleaned.append(p)
    return ", ".join(cleaned)


def format_class_section(info: ClassInfo) -> str:
    """Format a complete class section for markdown output."""
    lines = [format_class_header(info)]

    member_count = 0

    # Constructor (if public and has params worth showing)
    if (
        info.constructor_public
        and info.constructor_params
        and info.kind not in ("object", "data object", "enum")
    ):
        # Strip overrides from constructor display when class has a parent
        if info.override_names and info.parent:
            ctor_params = clean_constructor_params_no_overrides(
                info.constructor_params, info.override_names
            )
        else:
            ctor_params = clean_method_params(info.constructor_params)
        if ctor_params:
            lines.append(f"constructor({ctor_params})")
            member_count += 1

    # Enum values
    for v in info.enum_values:
        lines.append(v)
        member_count += 1

    # Companion members
    if info.companion_members:
        lines.append("Companion:")
        for m in info.companion_members:
            lines.append(m)
            member_count += 1

    # Properties (skip overrides when class has a parent)
    for p in info.properties:
        if info.override_names and info.parent:
            prop_m = re.search(r"(?:val|var)\s+(\w+)", p)
            if prop_m and prop_m.group(1) in info.override_names:
                continue
        lines.append(p)
        member_count += 1

    # Methods
    for m in info.methods:
        lines.append(m)
        member_count += 1

    lines.append("")  # blank line
    return "\n".join(lines), member_count


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main():
    if not SDK_ROOT.exists():
        print(f"ERROR: SDK source root not found at {SDK_ROOT}", file=sys.stderr)
        print(
            "Run this script from the repository root, or check that "
            "stellar-sdk/src/ exists relative to the repo root.",
            file=sys.stderr,
        )
        sys.exit(1)

    # Collect all classes, grouped
    groups: dict[str, list[ClassInfo]] = {k: [] for k in GROUP_TITLES}
    stats = {"files": 0, "classes": 0, "members": 0, "skipped_dirs": 0, "errors": 0, "dedup_expect": 0}

    for platform_label, source_set, subdir_filter in SCAN_SOURCES:
        pkg_root = SDK_ROOT / source_set / PKG_REL_PATH
        if not pkg_root.exists():
            print(f"  (skipping missing source set: {source_set})", file=sys.stderr)
            continue

        scan_root = pkg_root / subdir_filter if subdir_filter else pkg_root
        if not scan_root.exists():
            print(f"  (skipping missing subdir: {source_set}/{PKG_REL_PATH}/{subdir_filter})", file=sys.stderr)
            continue

        label_desc = f"{source_set}/{subdir_filter}" if subdir_filter else source_set
        print(f"Scanning {label_desc}...", file=sys.stderr)

        for root, dirs, files in os.walk(scan_root):
            # Skip excluded directories
            dirs_to_remove = []
            for d in dirs:
                if d in SKIP_DIRS:
                    dirs_to_remove.append(d)
                    stats["skipped_dirs"] += 1
            for d in dirs_to_remove:
                dirs.remove(d)

            for fname in sorted(files):
                if not fname.endswith(".kt"):
                    continue
                if fname in SKIP_FILES:
                    continue

                filepath = Path(root) / fname
                # Group is determined by path relative to the PACKAGE root,
                # not the scan root, so that platform sourcesets with
                # `smartaccount/...` files are still grouped as "smartaccount".
                rel_path = os.path.relpath(filepath, pkg_root)
                group = determine_group(rel_path)

                try:
                    classes = parse_kotlin_file(filepath)
                    stats["files"] += 1

                    for cls in classes:
                        cls.platform = platform_label

                        member_count = (
                            len(cls.companion_members)
                            + len(cls.properties)
                            + len(cls.methods)
                            + len(cls.enum_values)
                        )
                        if cls.constructor_public and cls.constructor_params and cls.kind not in ("object", "data object", "enum"):
                            member_count += 1

                        stats["classes"] += 1
                        stats["members"] += member_count
                        groups[group].append(cls)
                        display_path = f"{source_set}/{rel_path}"
                        print(
                            f"  {display_path}: {cls.name} ({member_count} members)",
                            file=sys.stderr,
                        )

                except Exception as e:
                    print(f"  ERROR parsing {rel_path}: {e}", file=sys.stderr)
                    import traceback

                    traceback.print_exc(file=sys.stderr)
                    stats["errors"] += 1

    # KMP expect/actual dedup: if a class name has BOTH an `expect` declaration
    # (commonMain) and one or more `actual` declarations (platform sourcesets)
    # within the same group, drop the expect one — the actual is the authoritative
    # signature developers interact with.
    for group_key, class_list in groups.items():
        name_has_actual: set[str] = {c.name for c in class_list if c.is_actual}
        filtered = []
        for c in class_list:
            if c.is_expect and c.name in name_has_actual:
                stats["dedup_expect"] += 1
                continue
            filtered.append(c)
        groups[group_key] = filtered

    # Sort classes within each group: commonMain first (platform=""), then by
    # platform label, then by name. Platform tags group together in output.
    for group_key, class_list in groups.items():
        class_list.sort(key=lambda c: (c.platform, c.name))
        groups[group_key] = class_list

    # Generate markdown
    md = "# KMP Stellar SDK API Reference (Signatures)\n\n"
    md += "Compact method signature reference for `com.soneso.stellar.sdk`.\n"
    md += "Generated by `generate_api_reference.py`. Do not edit manually.\n\n"
    md += f"**Stats:** {stats['classes']} classes, {stats['members']} members\n\n"

    for group_key, title in GROUP_TITLES.items():
        class_list = groups[group_key]
        if not class_list:
            continue

        md += "---\n"
        md += f"## {title}\n"
        md += "---\n\n"

        for cls in class_list:
            section, _ = format_class_section(cls)
            md += section + "\n"

    # Write output
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(md, encoding="utf-8")

    # Print stats
    print(f"\n=== Generation Complete ===", file=sys.stderr)
    print(f"Files processed: {stats['files']}", file=sys.stderr)
    print(f"Classes extracted: {stats['classes']}", file=sys.stderr)
    print(f"Total members: {stats['members']}", file=sys.stderr)
    print(f"Directories skipped: {stats['skipped_dirs']}", file=sys.stderr)
    print(f"Expect dedup (replaced by actual): {stats['dedup_expect']}", file=sys.stderr)
    print(f"Errors: {stats['errors']}", file=sys.stderr)
    print(f"Output written to: {OUTPUT_PATH}", file=sys.stderr)
    print(
        f"File size: {OUTPUT_PATH.stat().st_size:,} bytes ({sum(1 for _ in OUTPUT_PATH.open())} lines)",
        file=sys.stderr,
    )

    print("API reference generated successfully!")


if __name__ == "__main__":
    main()
