#!/usr/bin/env python3
"""
RPC Parser for KMP Stellar SDK Compatibility Analysis

Reads the request and response structs of the stellar-rpc JSON-RPC methods from
the go-stellar-sdk protocol files (protocols/rpc/*.go) at the version that the
analysed stellar-rpc release pins in its go.mod, and checks the mapped methods
against the methods the release registers in its jsonrpc.go.
"""

import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence
from dataclasses import dataclass

# Allow imports from the parent tools/matrix-generator package
sys.path.insert(0, str(Path(__file__).parent.parent))

from common import camel_to_snake
from github_fetcher import fetch_all_rpc_response_files, fetch_url


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

    struct_name: str        # Go struct name (e.g. "GetEventsRequest")
    parameters: List[GoParameter]


# ---------------------------------------------------------------------------
# GoProtocolParser
# ---------------------------------------------------------------------------

class GoProtocolParser:
    """
    Parse go-stellar-sdk protocol Go files from GitHub to extract RPC method
    request definitions, parameter names, and required/optional status.

    The parser fetches the per-method Go files from the go-stellar-sdk
    repository (protocols/rpc/) and extracts ``XxxRequest`` struct definitions
    using regex so that the KMP compatibility matrices reflect the upstream
    protocol without manual updates.

    Args:
        protocol_source: Base URL of the GitHub directory that contains the
            per-method Go files, at the go-stellar-sdk version that the RPC
            release pins in its go.mod.
    """

    # Base URL for the per-method Go files. The request and response structs
    # live in the go-stellar-sdk repository under protocols/rpc/; the methods/
    # directory of stellar-rpc holds handler logic only. {ref} is the
    # go-stellar-sdk version that the RPC release pins in its go.mod, so the
    # structs match what that release exposes.
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

    def __init__(self, protocol_source: str) -> None:
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

        The matrix must list every mapped method with its parameters, so
        every fetch and parse error propagates. Every method file declares a
        ``<Prefix>Request`` struct, ``struct{}`` for a method without
        parameters, so a file without it fails the run.

        Returns:
            Mapping from camelCase method name to its method dictionary as
            produced by :meth:`_struct_to_method_dict`.

        Raises:
            GitHubFetchError: If a method file cannot be fetched.
            ValueError: If a method file has no ``<Prefix>Request`` struct.
        """
        methods: Dict[str, Dict[str, Any]] = {}

        for method_prefix, method_name in self.METHOD_NAME_MAPPING.items():
            filename = camel_to_snake(method_prefix) + ".go"
            content = self._fetch_file(filename)

            request_struct = self._parse_request_content(content, method_prefix)
            if request_struct is None:
                raise ValueError(
                    f"{method_name}: request struct {method_prefix}Request "
                    f"not found in {filename}"
                )
            methods[method_name] = self._struct_to_method_dict(request_struct)

        return methods

    # ------------------------------------------------------------------
    # File fetching
    # ------------------------------------------------------------------

    def _fetch_file(self, filename: str) -> str:
        """
        Return content for *filename*, using the in-memory cache to avoid
        duplicate network requests.

        Args:
            filename: Bare filename relative to the configured base URL,
                e.g. ``"get_events.go"``.

        Returns:
            File content as a string.

        Raises:
            GitHubFetchError: If the file cannot be fetched.
        """
        if filename in self._file_cache:
            return self._file_cache[filename]

        content = fetch_url(self._base_url + filename).decode("utf-8")
        self._file_cache[filename] = content
        return content

    # ------------------------------------------------------------------
    # Go source parsing
    # ------------------------------------------------------------------

    def _parse_request_content(
        self,
        content: str,
        method_prefix: str,
    ) -> Optional[GoRequestStruct]:
        """
        Locate and parse the ``<method_prefix>Request`` struct within *content*.

        Args:
            content: Full text of the Go source file.
            method_prefix: PascalCase prefix (e.g. ``"GetEvents"``).

        Returns:
            :class:`GoRequestStruct` if found, otherwise ``None``.
        """
        struct_name = f"{method_prefix}Request"
        struct_body = _struct_body(struct_name, [content])
        if struct_body is None:
            return None

        return GoRequestStruct(
            struct_name=struct_name,
            parameters=self._parse_struct_fields(struct_body),
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

    # ------------------------------------------------------------------
    # Response struct parsing
    # ------------------------------------------------------------------

    # Matches a struct field with a JSON tag, or an embedded struct, which is a
    # type name alone on its line:
    #   Hash string `json:"id"`
    #   Sequence uint32 `json:"sequence,omitempty"`
    #   TransactionDetails
    _RESPONSE_FIELD_PATTERN = re.compile(
        r"(\w+)\s+[\w\.\*\[\]]+\s+`json:\"([^,\"]+)(?:,[^\"]*)?\"`,?|^\s*(\w+)\s*$",
        re.MULTILINE,
    )

    def parse_response_fields(self, struct_name: str, sources: Sequence[str]) -> List[Dict[str, str]]:
        """
        Parse the fields of the response struct *struct_name*.

        encoding/json serialises the fields of an embedded struct as fields of
        the embedding struct, so they are listed at the position of the
        embedding; the embedded struct may be declared in any of *sources*.

        Args:
            struct_name: Response struct name (e.g. ``"GetTransactionResponse"``).
            sources: Go source texts of the fetched protocol files.

        Returns:
            List of ``{"field_name": <Go field>, "json_name": <JSON key>}``
            dicts for each public field that has a non-empty, non-``"-"`` JSON
            tag; empty when no source declares the struct.

        Raises:
            ValueError: If no source declares an embedded struct.
        """
        fields: List[Dict[str, str]] = []
        struct_body = _struct_body(struct_name, sources)
        if struct_body is None:
            return fields

        for field_match in self._RESPONSE_FIELD_PATTERN.finditer(struct_body):
            embedded = field_match.group(3)
            if embedded:
                if _struct_body(embedded, sources) is None:
                    raise ValueError(
                        f"{struct_name} embeds {embedded}, which no fetched protocol file declares"
                    )
                fields.extend(self.parse_response_fields(embedded, sources))
            elif field_match.group(2) not in ("-", ""):
                fields.append({"field_name": field_match.group(1), "json_name": field_match.group(2)})

        return fields

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
            Dict with keys ``required_params``, ``optional_params``,
            ``struct_name``, and ``parameters``.
        """
        return {
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


def _struct_body(struct_name: str, sources: Sequence[str]) -> Optional[str]:
    """Return the body of the Go struct *struct_name* in the first of *sources* that declares it."""
    # Matches:  type GetEventsRequest struct { ... }
    pattern = re.compile(rf"type\s+{re.escape(struct_name)}\s+struct\s*\{{([^}}]*)\}}")
    for source in sources:
        match = pattern.search(source)
        if match:
            return match.group(1)
    return None


# stellar-rpc registers each JSON-RPC method in cmd/stellar-rpc/internal/jsonrpc.go
# as ``methodName: protocol.<Prefix>MethodName``.
_METHOD_REGISTRATION = re.compile(r"\bmethodName:\s*protocol\.(\w+)MethodName\b")


def verify_method_set(jsonrpc_source: str) -> None:
    """
    Require the methods that *jsonrpc_source* registers to be exactly the
    methods of :attr:`GoProtocolParser.METHOD_NAME_MAPPING`.

    The matrix computes coverage over the mapped methods; this check keeps
    that denominator equal to the methods the release serves.

    Args:
        jsonrpc_source: Text of the release's cmd/stellar-rpc/internal/jsonrpc.go.

    Raises:
        ValueError: If the file registers no method in that form, or naming
            every missing and every unexpected method.
    """
    registered = set(_METHOD_REGISTRATION.findall(jsonrpc_source))
    if not registered:
        raise ValueError("No methodName: protocol.<Name>MethodName registrations in jsonrpc.go")
    mapping = GoProtocolParser.METHOD_NAME_MAPPING
    missing = sorted(mapping[prefix] for prefix in mapping.keys() - registered)
    unexpected = sorted(prefix[0].lower() + prefix[1:] for prefix in registered - mapping.keys())
    if missing or unexpected:
        problems = []
        if missing:
            problems.append(f"missing: {', '.join(missing)}")
        if unexpected:
            problems.append(f"unexpected: {', '.join(unexpected)}")
        raise ValueError(
            f"Registered stellar-rpc methods differ from METHOD_NAME_MAPPING ({'; '.join(problems)})"
        )


# ---------------------------------------------------------------------------
# RPCMethodExtractor
# ---------------------------------------------------------------------------

class RPCMethodExtractor:
    """
    Facade that wraps :class:`GoProtocolParser` for one stellar-rpc release
    and enriches its output with response-field metadata fetched from GitHub.

    Args:
        release_info: ``version``, ``published_at`` (YYYY-MM-DD) and
            ``html_url`` of the release, as the matrix header cites them.
        protocol_source: Base URL of the go-stellar-sdk protocol files.
            Defaults to the version that the release pins in its go.mod.
    """

    def __init__(
        self,
        release_info: Dict[str, str],
        protocol_source: Optional[str] = None,
    ) -> None:
        self._release_info = release_info
        # Request params and response fields are both read at the go-stellar-sdk
        # ref that the RPC release pins in its go.mod.
        if protocol_source is None:
            from github_fetcher import _resolve_go_stellar_sdk_ref
            go_sdk_ref = _resolve_go_stellar_sdk_ref(release_info["version"])
            protocol_source = GoProtocolParser.GITHUB_METHODS_BASE_URL_TEMPLATE.format(ref=go_sdk_ref)
        self._parser = GoProtocolParser(protocol_source)

    def extract_methods(self) -> Dict[str, Any]:
        """
        Parse all RPC methods and return a structured data dict.

        Returns:
            Dict with ``metadata`` and ``methods`` keys.  ``methods`` is the
            direct output of :meth:`GoProtocolParser.parse_all_methods`.

        Raises:
            GitHubFetchError: If a method file fetch fails.
            ValueError: If a method file has no request struct.
        """
        version = self._release_info["version"]
        print(f"  Parsing Go protocol files for RPC {version} ...")
        methods = self._parser.parse_all_methods()

        return {
            "metadata": {
                "source": self._parser._base_url,
                "source_type": "GitHub",
                "rpc_version": version,
                "rpc_release_date": self._release_info["published_at"],
                "rpc_release_url": self._release_info["html_url"],
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

        Every method needs a ``<Prefix>Response`` struct that yields at least
        one field; the first method without one raises. Embedded structs
        resolve across the fetched files.

        Args:
            data: Dict as returned by :meth:`extract_methods`.

        Raises:
            GitHubFetchError: If a response file or the go.mod pin cannot be fetched.
            ValueError: If a response file yields no response fields, or the
                go.mod of the release has no go-stellar-sdk requirement.
        """
        methods = data["methods"]
        version = data["metadata"]["rpc_version"]

        response_files = fetch_all_rpc_response_files(
            tag=version,
            method_names=list(methods.keys()),
        )

        sources = list(response_files.values())
        for method_name in response_files:
            struct_name = method_name[0].upper() + method_name[1:] + "Response"
            response_fields = self._parser.parse_response_fields(struct_name, sources)
            if not response_fields:
                raise ValueError(
                    f"No response fields parsed from the {method_name} response "
                    f"file of RPC {version}"
                )
            methods[method_name]["response_fields"] = response_fields
