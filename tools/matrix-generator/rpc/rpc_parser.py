#!/usr/bin/env python3
"""
RPC Parser for KMP Stellar SDK Compatibility Analysis

Parses stellar-rpc protocol Go files (cmd/stellar-rpc/internal/methods/*.go) to
dynamically extract method definitions, request parameters, and their required/optional
status from Go struct field tags at runtime.

This is an intentional deviation from the Flutter SDK approach: rather than using
hardcoded METHOD_METADATA, this parser reads Go struct definitions directly so that
parameter lists stay accurate as the upstream protocol evolves.

Also absorbs the RPCMethodExtractor class from the legacy generate_rpc_comparison.py,
consolidating all protocol-parsing responsibility into one module.

Output: DATA_DIR / 'rpc' / 'rpc_methods.json'
"""

import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional
from dataclasses import dataclass

# Allow imports from the parent tools/matrix-generator package
sys.path.insert(0, str(Path(__file__).parent.parent))

from common import Colors, DATA_DIR, SDK_ROOT, get_sdk_version, camel_to_snake
from github_fetcher import (
    fetch_rpc_response_file,
    fetch_all_rpc_response_files,
    get_latest_rpc_release,
    get_latest_go_stellar_sdk_release,
    GitHubFetchError,
    SourceFileNotFoundError,
)


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class GoParameter:
    """A single Go struct field parsed from a request struct."""

    name: str           # JSON field name derived from the json tag
    go_field_name: str  # Go identifier for the field
    go_type: str        # Go type (e.g. string, *uint32, []string)
    required: bool      # True when no omitempty and not a pointer type
    json_tag: str       # Full raw json tag value (e.g. "startLedger,omitempty")


@dataclass
class GoRequestStruct:
    """A fully parsed Go request struct for one RPC method."""

    method_name: str        # camelCase RPC method name (e.g. "getEvents")
    struct_name: str        # Go struct name (e.g. "GetEventsRequest")
    parameters: List[GoParameter]
    description: str = ""


# ---------------------------------------------------------------------------
# GoProtocolParser
# ---------------------------------------------------------------------------

class GoProtocolParser:
    """
    Parse stellar-rpc protocol Go files from GitHub to extract RPC method
    request definitions, parameter names, and required/optional status.

    The parser fetches individual per-method Go files from the stellar-rpc
    repository (cmd/stellar-rpc/internal/methods/) and extracts ``XxxRequest``
    struct definitions using regex so that the KMP compatibility matrices
    always reflect the actual upstream protocol without manual updates.

    Args:
        protocol_source: Base URL of the GitHub directory that contains the
            per-method Go files.  Defaults to the latest go-stellar-sdk
            release's ``protocols/rpc/`` URL.
    """

    # Base URL for the per-method Go files.
    # Request/response struct definitions live in the go-stellar-sdk repository
    # under protocols/rpc/, not in stellar-rpc/methods/ (which contains handler
    # logic only and no longer defines the canonical request structs).
    # {ref} is resolved at runtime to the latest go-stellar-sdk module release, so
    # request-struct fields not yet in a released RPC are not measured against the SDK.
    GITHUB_METHODS_BASE_URL_TEMPLATE = (
        "https://raw.githubusercontent.com/stellar/go-stellar-sdk/{ref}"
        "/protocols/rpc/"
    )

    # Method name mapping: Go struct name prefix -> RPC camelCase method name
    METHOD_NAME_MAPPING: Dict[str, str] = {
        "GetEvents": "getEvents",
        "GetFeeStats": "getFeeStats",
        "GetHealth": "getHealth",
        "GetLatestLedger": "getLatestLedger",
        "GetLedgerEntries": "getLedgerEntries",
        "GetLedgers": "getLedgers",
        "GetNetwork": "getNetwork",
        "GetTransaction": "getTransaction",
        "GetTransactions": "getTransactions",
        "GetVersionInfo": "getVersionInfo",
        "SendTransaction": "sendTransaction",
        "SimulateTransaction": "simulateTransaction",
    }

    # Human-readable descriptions for every known RPC method
    _METHOD_DESCRIPTIONS: Dict[str, str] = {
        "getEvents": "Get filtered list of events",
        "getFeeStats": "Statistics for charged inclusion fees",
        "getHealth": "General node health check",
        "getLatestLedger": "Current latest known ledger",
        "getLedgerEntries": "Read ledger entry values",
        "getLedgers": "Get detailed list of ledgers",
        "getNetwork": "General info about the configured network",
        "getTransaction": "Get transaction status and details",
        "getTransactions": "Get detailed list of transactions",
        "getVersionInfo": "Version information about the RPC and Captive core",
        "sendTransaction": "Submit a transaction to the network",
        "simulateTransaction": "Submit a trial contract invocation",
    }

    def __init__(self, protocol_source: Optional[str] = None) -> None:
        if protocol_source is None:
            try:
                go_sdk_ref = get_latest_go_stellar_sdk_release().version
            except GitHubFetchError:
                go_sdk_ref = "master"
            protocol_source = self.GITHUB_METHODS_BASE_URL_TEMPLATE.format(ref=go_sdk_ref)

        if not protocol_source.endswith("/"):
            protocol_source += "/"
        self._base_url = protocol_source

        # In-memory cache so each file is fetched at most once per session
        self._file_cache: Dict[str, str] = {}

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def parse_all_methods(self) -> Dict[str, Dict[str, Any]]:
        """
        Fetch and parse every known RPC method file.

        Returns:
            Mapping from camelCase method name to its method dictionary as
            produced by :meth:`_struct_to_method_dict`.
        """
        methods: Dict[str, Dict[str, Any]] = {}

        for method_prefix, method_name in self.METHOD_NAME_MAPPING.items():
            filename = camel_to_snake(method_prefix) + ".go"
            content = self._fetch_file(filename)

            if content is None:
                print(f"  {Colors.YELLOW}Warning:{Colors.END} protocol file not found: {filename}")
                continue

            try:
                request_struct = self._parse_request_content(content, method_prefix, filename)
                if request_struct:
                    methods[method_name] = self._struct_to_method_dict(request_struct)
                else:
                    # Methods without a Request struct take no parameters
                    description = self._extract_description(content, method_prefix)
                    if not description:
                        description = self._METHOD_DESCRIPTIONS.get(method_name, "")
                    methods[method_name] = {
                        "description": description,
                        "required_params": [],
                        "optional_params": [],
                        "struct_name": None,
                        "parameters": [],
                    }
            except Exception as exc:  # pylint: disable=broad-except
                print(f"  {Colors.RED}Error{Colors.END} parsing {filename}: {exc}")

        return methods

    # ------------------------------------------------------------------
    # File fetching
    # ------------------------------------------------------------------

    def _fetch_file(self, filename: str) -> Optional[str]:
        """
        Return content for *filename*, using the in-memory cache to avoid
        duplicate network requests.

        Args:
            filename: Bare filename relative to the configured base URL,
                e.g. ``"get_events.go"``.

        Returns:
            File content as a string, or ``None`` when the file cannot be
            retrieved.
        """
        if filename in self._file_cache:
            return self._file_cache[filename]

        url = self._base_url + filename

        try:
            # Reuse the centralised fetcher so authentication, timeouts, and
            # error handling are consistent across all matrix-generator modules.
            from github_fetcher import fetch_url  # noqa: PLC0415 (local import OK)
            data = fetch_url(url)
            content = data.decode("utf-8")
            self._file_cache[filename] = content
            return content
        except SourceFileNotFoundError:
            return None
        except GitHubFetchError as exc:
            print(f"  {Colors.YELLOW}Warning:{Colors.END} could not fetch {url}: {exc}")
            return None

    # ------------------------------------------------------------------
    # Go source parsing
    # ------------------------------------------------------------------

    def _parse_request_content(
        self,
        content: str,
        method_prefix: str,
        source_name: str,
    ) -> Optional[GoRequestStruct]:
        """
        Locate and parse the ``<method_prefix>Request`` struct within *content*.

        Args:
            content: Full text of the Go source file.
            method_prefix: PascalCase prefix (e.g. ``"GetEvents"``).
            source_name: Filename used in diagnostic messages.

        Returns:
            :class:`GoRequestStruct` if found, otherwise ``None``.
        """
        struct_name = f"{method_prefix}Request"

        # Matches:  type GetEventsRequest struct { ... }
        # The negative lookahead on }} is not needed because [^}]* is non-greedy enough.
        struct_pattern = rf"type\s+{re.escape(struct_name)}\s+struct\s*\{{([^}}]*)\}}"
        match = re.search(struct_pattern, content, re.DOTALL)
        if not match:
            return None

        struct_body = match.group(1)
        parameters = self._parse_struct_fields(struct_body)
        description = self._extract_description(content, method_prefix)
        method_name = self.METHOD_NAME_MAPPING.get(method_prefix, method_prefix)

        return GoRequestStruct(
            method_name=method_name,
            struct_name=struct_name,
            parameters=parameters,
            description=description,
        )

    def _parse_struct_fields(self, struct_body: str) -> List[GoParameter]:
        """
        Extract all public fields with JSON tags from a Go struct body.

        A field is considered **required** when its JSON tag contains no
        ``omitempty`` directive *and* its Go type is not a pointer (``*``).

        Args:
            struct_body: Text between the outer ``{`` and ``}`` of a struct.

        Returns:
            Ordered list of :class:`GoParameter` objects.
        """
        parameters: List[GoParameter] = []

        # Matches lines of the form:
        #   FieldName  SomeType  `json:"jsonName"`
        #   FieldName  SomeType  `json:"jsonName,omitempty"`
        field_pattern = re.compile(
            r"^\s*(\w+)\s+([\w\[\]\*\.]+)\s+`json:\"([^\"]+)\"`",
        )

        for line in struct_body.splitlines():
            line = line.strip()
            if not line or line.startswith("//"):
                continue

            m = field_pattern.match(line)
            if not m:
                continue

            go_field_name = m.group(1)
            go_type = m.group(2)
            json_tag = m.group(3)

            parts = json_tag.split(",")
            json_name = parts[0]
            has_omitempty = "omitempty" in parts
            is_pointer = go_type.startswith("*")
            required = not has_omitempty and not is_pointer

            parameters.append(
                GoParameter(
                    name=json_name,
                    go_field_name=go_field_name,
                    go_type=go_type,
                    required=required,
                    json_tag=json_tag,
                )
            )

        return parameters

    def _extract_description(self, content: str, method_prefix: str) -> str:
        """
        Derive a human-readable description from either the Go source or the
        built-in descriptions table.

        The parser first checks for a const declaration of the form::

            const GetEventsMethodName = "getEvents"

        and maps the quoted string through the descriptions table.  If that
        const is absent, the descriptions table is consulted directly via the
        camelCase method name.

        Args:
            content: Full Go source text.
            method_prefix: PascalCase method prefix (e.g. ``"GetEvents"``).

        Returns:
            Description string, or empty string when nothing is found.
        """
        const_pattern = rf'const\s+{re.escape(method_prefix)}MethodName\s*=\s*"([^"]+)"'
        m = re.search(const_pattern, content)
        if m:
            return self._METHOD_DESCRIPTIONS.get(m.group(1), "")

        method_name = self.METHOD_NAME_MAPPING.get(method_prefix, "")
        return self._METHOD_DESCRIPTIONS.get(method_name, "")

    # ------------------------------------------------------------------
    # Response struct parsing
    # ------------------------------------------------------------------

    # Matches a full response struct definition, e.g.:
    #   type GetLatestLedgerResponse struct { ... }
    _RESPONSE_STRUCT_PATTERN = re.compile(
        r"type\s+(\w+Response)\s+struct\s*\{([^}]+)\}",
        re.MULTILINE | re.DOTALL,
    )

    # Matches a struct field with a JSON tag:
    #   Hash string `json:"id"`
    #   Sequence uint32 `json:"sequence,omitempty"`
    _RESPONSE_FIELD_PATTERN = re.compile(
        r"(\w+)\s+[\w\.\*\[\]]+\s+`json:\"([^,\"]+)(?:,[^\"]*)?\"`,?"
    )

    def parse_response_fields(self, response_content: str) -> List[Dict[str, str]]:
        """
        Parse response struct fields from a Go source file.

        Args:
            response_content: Go source text containing a ``*Response`` struct.

        Returns:
            List of ``{"field_name": <Go field>, "json_name": <JSON key>}``
            dicts for each public field that has a non-empty, non-``"-"`` JSON
            tag.
        """
        fields: List[Dict[str, str]] = []

        struct_match = self._RESPONSE_STRUCT_PATTERN.search(response_content)
        if not struct_match:
            return fields

        struct_body = struct_match.group(2)

        for field_match in self._RESPONSE_FIELD_PATTERN.finditer(struct_body):
            go_field = field_match.group(1)
            json_name = field_match.group(2)
            if json_name in ("-", ""):
                continue
            fields.append({"field_name": go_field, "json_name": json_name})

        return fields

    def add_response_fields_to_method(
        self,
        method_name: str,
        methods: Dict[str, Dict[str, Any]],
        response_content: str,
    ) -> None:
        """
        Augment an already-parsed method dict with response field metadata.

        Args:
            method_name: camelCase RPC method name (e.g. ``"getLatestLedger"``).
            methods: The methods dict as returned by :meth:`parse_all_methods`.
            response_content: Go source text for the response struct file.
        """
        if method_name not in methods:
            return
        methods[method_name]["response_fields"] = self.parse_response_fields(
            response_content
        )

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _struct_to_method_dict(self, struct: GoRequestStruct) -> Dict[str, Any]:
        """
        Serialise a :class:`GoRequestStruct` to the canonical method dict
        format consumed by the comparison generator.

        Args:
            struct: Fully parsed Go request struct.

        Returns:
            Dict with keys ``description``, ``required_params``,
            ``optional_params``, ``struct_name``, and ``parameters``.
        """
        return {
            "description": struct.description,
            "required_params": [p.name for p in struct.parameters if p.required],
            "optional_params": [p.name for p in struct.parameters if not p.required],
            "struct_name": struct.struct_name,
            "parameters": [
                {
                    "name": p.name,
                    "go_field": p.go_field_name,
                    "type": p.go_type,
                    "required": p.required,
                    "json_tag": p.json_tag,
                }
                for p in struct.parameters
            ],
        }


# ---------------------------------------------------------------------------
# RPCMethodExtractor  (absorbed from generate_rpc_comparison.py)
# ---------------------------------------------------------------------------

class RPCMethodExtractor:
    """
    Facade that wraps :class:`GoProtocolParser` and optionally enriches the
    output with response-field metadata fetched from GitHub.

    This class was previously defined in ``generate_rpc_comparison.py``.  It
    is consolidated here so that all RPC protocol-parsing logic lives in one
    place.

    Args:
        rpc_version: Optional RPC version tag used for enriching metadata
            (e.g. ``"v21.5.0"``).  When omitted the latest release is
            determined automatically via the GitHub API.
        protocol_source: Override the base URL used by :class:`GoProtocolParser`.
            Defaults to the GitHub methods directory.
    """

    def __init__(
        self,
        rpc_version: Optional[str] = None,
        protocol_source: Optional[str] = None,
    ) -> None:
        self._rpc_version = rpc_version
        self._parser = GoProtocolParser(protocol_source)

    def extract_methods(self) -> Dict[str, Any]:
        """
        Parse all RPC methods and return a structured data dict.

        If no *rpc_version* was supplied at construction time, the latest
        RPC server release is queried from GitHub and used for metadata.

        Returns:
            Dict with ``metadata`` and ``methods`` keys.  ``methods`` is the
            direct output of :meth:`GoProtocolParser.parse_all_methods`.
        """
        version = self._rpc_version
        release_date = "unknown"
        release_url = ""

        if version is None:
            try:
                release = get_latest_rpc_release()
                version = release.version
                release_date = release.published_at.strftime("%Y-%m-%d")
                release_url = release.html_url
            except GitHubFetchError as exc:
                print(f"  {Colors.YELLOW}Warning:{Colors.END} could not fetch RPC release: {exc}")
                version = "unknown"

        print(f"  Parsing Go protocol files for RPC {version} ...")
        methods = self._parser.parse_all_methods()

        return {
            "metadata": {
                "source": self._parser._base_url,
                "source_type": "GitHub",
                "rpc_version": version,
                "rpc_release_date": release_date,
                "rpc_release_url": release_url,
                "generated_at": datetime.now(tz=timezone.utc).isoformat(),
                "total_methods": len(methods),
                "parsing_method": "Go struct parser (dynamic)",
            },
            "methods": methods,
        }

    def enrich_with_response_fields(self, data: Dict[str, Any]) -> None:
        """
        Fetch response struct files from GitHub and add ``response_fields`` to
        each method dict in *data* in-place.

        Methods for which no response file is found are silently skipped.

        Args:
            data: Dict as returned by :meth:`extract_methods`.
        """
        methods = data.get("methods", {})
        version = data.get("metadata", {}).get("rpc_version", "unknown")

        response_files = fetch_all_rpc_response_files(
            tag=version,
            method_names=list(methods.keys()),
        )

        for method_name, response_content in response_files.items():
            self._parser.add_response_fields_to_method(
                method_name, methods, response_content
            )


# ---------------------------------------------------------------------------
# Output helper
# ---------------------------------------------------------------------------

def save_rpc_methods(data: Dict[str, Any]) -> Path:
    """
    Write *data* to ``DATA_DIR/rpc/rpc_methods.json``, creating parent
    directories as needed.

    Args:
        data: Dict as returned by :meth:`RPCMethodExtractor.extract_methods`.

    Returns:
        Absolute :class:`~pathlib.Path` of the written file.
    """
    output_path = DATA_DIR / "rpc" / "rpc_methods.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with output_path.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)

    return output_path


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main() -> int:
    """
    Standalone entry point.

    Usage::

        python rpc_parser.py [--version TAG] [--no-response-fields] [--output PATH]

    When invoked without arguments the parser fetches the latest stellar-rpc
    server release from GitHub and writes ``rpc_methods.json`` to the default
    ``DATA_DIR/rpc/`` location.
    """
    import argparse

    cli = argparse.ArgumentParser(
        description="Parse stellar-rpc Go protocol files and write rpc_methods.json",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    cli.add_argument(
        "--version",
        metavar="TAG",
        help="Specific RPC version tag to analyse (e.g. v21.5.0). "
             "Defaults to the latest release.",
    )
    cli.add_argument(
        "--no-response-fields",
        dest="skip_response_fields",
        action="store_true",
        default=False,
        help="Skip fetching and enriching response struct fields.",
    )
    cli.add_argument(
        "--output",
        "-o",
        metavar="PATH",
        help="Override output file path. Defaults to DATA_DIR/rpc/rpc_methods.json.",
    )
    cli.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        default=False,
        help="Print detailed parameter information to stdout.",
    )
    args = cli.parse_args()

    try:
        extractor = RPCMethodExtractor(rpc_version=args.version)

        print(f"{Colors.BOLD}RPC Protocol Parser{Colors.END}")
        print("-" * 60)
        print(f"SDK root  : {SDK_ROOT}")
        print(f"SDK version: {get_sdk_version()}")
        print("-" * 60)

        data = extractor.extract_methods()
        methods = data["methods"]
        meta = data["metadata"]

        print(f"  RPC version: {meta['rpc_version']}")
        print(f"  Methods parsed: {meta['total_methods']}")

        if not args.skip_response_fields:
            print("  Fetching response struct definitions ...")
            try:
                extractor.enrich_with_response_fields(data)
            except GitHubFetchError as exc:
                print(f"  {Colors.YELLOW}Warning:{Colors.END} response field enrichment failed: {exc}")

        # Determine output path
        if args.output:
            output_path = Path(args.output)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            with output_path.open("w", encoding="utf-8") as fh:
                json.dump(data, fh, indent=2)
        else:
            output_path = save_rpc_methods(data)

        print(f"\n{Colors.GREEN}Saved{Colors.END}: {output_path}")

        if args.verbose:
            print()
            print("Method summary:")
            for method_name, method_data in sorted(methods.items()):
                req = method_data.get("required_params", [])
                opt = method_data.get("optional_params", [])
                resp = method_data.get("response_fields", [])
                print(
                    f"  {method_name:<26} "
                    f"req={len(req):2d}  opt={len(opt):2d}  resp_fields={len(resp):2d}"
                )

        return 0

    except KeyboardInterrupt:
        print("\nInterrupted.", file=sys.stderr)
        return 130
    except Exception as exc:  # pylint: disable=broad-except
        print(f"{Colors.RED}ERROR{Colors.END}: {exc}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return 1


# Alias for the orchestrator which imports RPCMethodParser
RPCMethodParser = RPCMethodExtractor


if __name__ == "__main__":
    sys.exit(main())
