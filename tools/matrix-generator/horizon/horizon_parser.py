#!/usr/bin/env python3
"""
Horizon API Endpoint Parser

Parses the Horizon Go router.go file to extract all HTTP endpoints with their
methods, parameters, and streaming capabilities. Outputs structured JSON data
for compatibility analysis.

This parser handles chi router's nested Route() blocks and complex routing patterns.

Author: Stellar KMP SDK Team
License: Apache-2.0
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import argparse
import json
import re
import traceback
from datetime import datetime
from typing import Dict, List, Optional, Set, Tuple

from common import Colors, DATA_DIR, SDK_ROOT


class HorizonEndpoint:
    """Represents a single Horizon API endpoint"""

    def __init__(self, path: str, method: str, category: str, streaming: bool,
                 parameters: List[Dict[str, str]], description: str = "", handler: str = ""):
        self.path = path
        self.method = method
        self.category = category
        self.streaming = streaming
        self.parameters = parameters
        self.description = description
        self.handler = handler

    def to_dict(self) -> Dict:
        """Convert to dictionary for JSON serialization"""
        return {
            "path": self.path,
            "method": self.method,
            "category": self.category,
            "streaming": self.streaming,
            "parameters": self.parameters,
            "description": self.description,
            "handler": self.handler
        }


class HorizonRouterParser:
    """Parser for Horizon router.go file with nested Route() support"""

    # Standard pagination parameters added to every list endpoint by default.
    PAGINATION_PARAMS = ('cursor', 'limit', 'order')

    # Per-endpoint additional query parameters.
    # Keys are either:
    #   - an exact path string (e.g. '/accounts')
    #   - a substring checked with ``in`` against the full path (e.g. 'trade_aggregations')
    # Order matters: more-specific (path-exact) entries are listed before substring ones.
    # Each value is a tuple of parameter names drawn from QUERY_PARAMETERS.
    CATEGORY_PARAMS: Dict[str, Tuple[str, ...]] = {
        '/accounts':              ('asset', 'signer', 'sponsor', 'liquidity_pool'),
        '/claimable_balances':    ('asset', 'sponsor', 'claimant'),
        '/liquidity_pools':       ('reserves', 'account'),
        '/offers':                ('seller', 'selling_asset_type', 'selling_asset_code',
                                   'selling_asset_issuer', 'buying_asset_type',
                                   'buying_asset_code', 'buying_asset_issuer', 'sponsor'),
        'assets':                 ('asset_code', 'asset_issuer'),
        'trade_aggregations':     ('start_time', 'end_time', 'resolution', 'offset',
                                   'base_asset_type', 'base_asset_code', 'base_asset_issuer',
                                   'counter_asset_type', 'counter_asset_code',
                                   'counter_asset_issuer'),
        'trades':                 ('base_asset_type', 'base_asset_code', 'base_asset_issuer',
                                   'counter_asset_type', 'counter_asset_code',
                                   'counter_asset_issuer', 'offer_id', 'account', 'type'),
    }

    # Known query parameters for various endpoints
    QUERY_PARAMETERS = {
        "cursor": {"description": "A number that points to a specific location in a collection of responses"},
        "limit": {"description": "The maximum number of records returned"},
        "order": {"description": "The order in which to return rows, 'asc' or 'desc'"},
        "asset": {"description": "Filter by asset"},
        "asset_code": {"description": "The code for the asset"},
        "asset_issuer": {"description": "The Stellar address of the asset issuer"},
        "signer": {"description": "Filter accounts by signer account ID"},
        "sponsor": {"description": "Filter by sponsor account ID"},
        "liquidity_pool": {"description": "Filter by liquidity pool ID"},
        "reserves": {"description": "Filter by reserve assets"},
        "account": {"description": "Filter by account ID"},
        "claimant": {"description": "Filter by claimant account ID"},
        "type": {"description": "Filter by type"},
        "selling_asset_type": {"description": "The type of asset being sold"},
        "selling_asset_code": {"description": "The code of asset being sold"},
        "selling_asset_issuer": {"description": "The issuer of asset being sold"},
        "buying_asset_type": {"description": "The type of asset being bought"},
        "buying_asset_code": {"description": "The code of asset being bought"},
        "buying_asset_issuer": {"description": "The issuer of asset being bought"},
        "seller": {"description": "Filter by seller account ID"},
        "offer_id": {"description": "Filter by offer ID"},
        "base_asset_type": {"description": "The type of the base asset"},
        "base_asset_code": {"description": "The code of the base asset"},
        "base_asset_issuer": {"description": "The issuer of the base asset"},
        "counter_asset_type": {"description": "The type of the counter asset"},
        "counter_asset_code": {"description": "The code of the counter asset"},
        "counter_asset_issuer": {"description": "The issuer of the counter asset"},
        "start_time": {"description": "Start time for aggregation period"},
        "end_time": {"description": "End time for aggregation period"},
        "resolution": {"description": "Segment duration as millis since epoch"},
        "offset": {"description": "Offset in milliseconds"},
        "include_failed": {"description": "Include failed transactions"},
        "join": {"description": "Include related resources"},
        "tx": {"description": "Transaction envelope XDR"},
    }

    def __init__(self, router_path: Optional[str] = None, version_info: Optional[Dict] = None):
        """
        Initialize parser with optional path to router.go and optional version metadata.

        Args:
            router_path: Path to Horizon router.go file. Optional when using
                         parse_from_content() directly.
            version_info: Optional version metadata dict with keys:
                - version: Version tag (e.g., "v2.30.0")
                - published_at: Release publication date
                - html_url: GitHub release URL
        """
        self.router_path: Optional[Path] = Path(router_path) if router_path else None
        self.version_info: Dict = version_info or {}
        self.endpoints: List[HorizonEndpoint] = []
        self.categories: Dict[str, List[HorizonEndpoint]] = {}
        self.seen_endpoints: Set[Tuple[str, str]] = set()

    def parse(self) -> None:
        """
        Parse the router.go file specified at construction time and extract endpoints.

        Delegates to parse_from_content() after reading the file. Raises ValueError if
        no router_path was provided. Raises FileNotFoundError if the file does not exist.
        """
        if not self.router_path:
            raise ValueError("router_path must be provided when using parse() method")

        print(f"Parsing Horizon router: {self.router_path}")

        if not self.router_path.exists():
            raise FileNotFoundError(f"Router file not found: {self.router_path}")

        content = self.router_path.read_text(encoding="utf-8")
        self.parse_from_content(content)

    def parse_from_content(self, content: str, version_info: Optional[Dict] = None) -> None:
        """
        Parse router content from a string.

        Accepts the router.go file content directly, bypassing any file I/O. This is
        the primary parsing entry point used by the matrix-generator pipeline when
        content is fetched from GitHub.

        Args:
            content: The full text of the router.go file.
            version_info: Optional version metadata dict to embed in JSON output.
                Keys recognised: ``version`` (tag string), ``published_at``
                (release date), ``html_url`` (GitHub release URL).  When provided
                this dict supersedes the version_info passed to __init__.
        """
        if version_info is not None:
            self.version_info = version_info

        print("Parsing Horizon router from content")

        # Reset state so the method is idempotent when called multiple times
        self.endpoints = []
        self.categories = {}
        self.seen_endpoints = set()

        # Extract route definitions from addRoutes method
        self._parse_add_routes_method(content)

        # Organise by category
        self._organize_by_category()

        print(f"Extracted {len(self.endpoints)} endpoints across {len(self.categories)} categories")

    # ------------------------------------------------------------------
    # Internal parsing helpers
    # ------------------------------------------------------------------

    def _parse_add_routes_method(self, content: str) -> None:
        """Parse the addRoutes method which contains all route definitions"""
        method_match = re.search(
            r'func \(r \*Router\) addRoutes\([^)]+\) \{(.+?)^}',
            content,
            re.MULTILINE | re.DOTALL
        )

        if not method_match:
            print("WARNING: Could not find addRoutes method")
            return

        method_body = method_match.group(1)
        lines = method_body.split('\n')

        self._parse_routes_recursive(lines, "", 0, len(lines))

    def _parse_routes_recursive(self, lines: List[str], path_prefix: str, start: int, end: int) -> None:
        """
        Recursively parse route definitions, handling nested Route() blocks.

        Args:
            lines: All lines from the method body.
            path_prefix: Current path prefix accumulated from parent Route() blocks.
            start: Start line index (inclusive).
            end: End line index (exclusive).
        """
        i = start
        while i < end:
            line = lines[i].strip()

            # Skip empty lines and comments
            if not line or line.startswith('//'):
                i += 1
                continue

            # Check for r.Route() - nested route block
            route_match = re.match(
                r'r\.Route\s*\(\s*"([^"]+)"\s*,\s*func\s*\(\s*r\s+chi\.Router\s*\)\s*\{',
                line
            )
            if route_match:
                nested_path = route_match.group(1)
                brace_count = 1
                block_start = i + 1
                j = i + 1
                while j < end and brace_count > 0:
                    if '{' in lines[j]:
                        brace_count += lines[j].count('{')
                    if '}' in lines[j]:
                        brace_count -= lines[j].count('}')
                    j += 1
                block_end = j - 1

                new_prefix = path_prefix + nested_path
                self._parse_routes_recursive(lines, new_prefix, block_start, block_end)
                i = j
                continue

            # Check for r.Group() - route group without path prefix
            group_match = re.match(
                r'r\.Group\s*\(\s*func\s*\(\s*r\s+chi\.Router\s*\)\s*\{',
                line
            )
            if group_match:
                brace_count = 1
                block_start = i + 1
                j = i + 1
                while j < end and brace_count > 0:
                    if '{' in lines[j]:
                        brace_count += lines[j].count('{')
                    if '}' in lines[j]:
                        brace_count -= lines[j].count('}')
                    j += 1
                block_end = j - 1

                self._parse_routes_recursive(lines, path_prefix, block_start, block_end)
                i = j
                continue

            # Handle multi-line Method() calls:
            #   r.With(...).Method(
            #       http.MethodGet,
            #       "/path",
            #       handler
            #   )
            if 'r.With(' in line and '.Method(' in line and 'http.Method' not in line:
                combined_line = line
                j = i + 1
                paren_count = line.count('(') - line.count(')')
                while j < end and paren_count > 0:
                    next_line = lines[j].strip()
                    combined_line += ' ' + next_line
                    paren_count += next_line.count('(') - next_line.count(')')
                    j += 1
                line = combined_line
                i = j - 1  # Will be incremented at end of loop

            # r.With(...).Method(http.MethodXxx, "/path", handler)
            with_method_match = re.search(
                r'r\.With\([^)]+\)\.Method\s*\(\s*http\.Method(\w+)\s*,\s*"([^"]+)"\s*,',
                line
            )
            if with_method_match:
                method = with_method_match.group(1).upper()
                path = path_prefix + with_method_match.group(2)
                self._add_endpoint(path, method, line)
                i += 1
                continue

            # r.Method(http.MethodXxx, "/path", handler)
            method_match = re.search(
                r'r\.Method\s*\(\s*http\.Method(\w+)\s*,\s*"([^"]+)"\s*,',
                line
            )
            if method_match:
                method = method_match.group(1).upper()
                path = path_prefix + method_match.group(2)
                self._add_endpoint(path, method, line)
                i += 1
                continue

            # r.Get("/path", handler), r.Post(...), etc.
            shorthand_match = re.search(
                r'r\.(Get|Post|Put|Delete|Patch)\s*\(\s*"([^"]+)"\s*,',
                line
            )
            if shorthand_match:
                method = shorthand_match.group(1).upper()
                path = path_prefix + shorthand_match.group(2)
                self._add_endpoint(path, method, line)
                i += 1
                continue

            i += 1

    def _add_endpoint(self, path: str, method: str, line: str) -> None:
        """Add an endpoint to the list, skipping internal/debug routes and duplicates."""
        # Skip internal endpoints
        if path.startswith("/debug") or path.startswith("/metrics") or "/internal/" in path:
            return

        path = self._normalize_path(path)

        endpoint_key = (path, method)
        if endpoint_key in self.seen_endpoints:
            return
        self.seen_endpoints.add(endpoint_key)

        category = self._determine_category(path)
        streaming = self._is_streaming(path, line)
        parameters = self._extract_parameters(path, category, method)
        handler = self._extract_handler(line)

        endpoint = HorizonEndpoint(
            path=path,
            method=method,
            category=category,
            streaming=streaming,
            parameters=parameters,
            description=self._generate_description(path, category, method),
            handler=handler
        )

        self.endpoints.append(endpoint)

    def _normalize_path(self, path: str) -> str:
        """Normalize path parameters to consistent format"""
        # Remove regex patterns from parameters (e.g., {account_id:\\w+} -> {account_id})
        normalized = re.sub(r'\{([^:}]+):[^}]+\}', r'{\1}', path)

        # Remove trailing slashes (except for root path)
        if normalized != '/' and normalized.endswith('/'):
            normalized = normalized.rstrip('/')

        # Normalize generic parameter names to specific ones
        replacements = {
            r'/accounts/\{id\}': '/accounts/{account_id}',
            r'/ledgers/\{id\}': '/ledgers/{ledger_id}',
            r'/transactions/\{id\}': '/transactions/{transaction_id}',
            r'/operations/\{id\}': '/operations/{operation_id}',
            r'/claimable_balances/\{id\}': '/claimable_balances/{claimable_balance_id}',
            r'/liquidity_pools/\{id\}': '/liquidity_pools/{liquidity_pool_id}',
            r'/offers/\{id\}': '/offers/{offer_id}',
            r'/effects/\{id\}': '/effects/{effect_id}',
        }

        for pattern, replacement in replacements.items():
            if re.search(pattern, normalized):
                normalized = re.sub(pattern, replacement, normalized)

        # Handle other parameter name variations
        normalized = normalized.replace('{tx_id}', '{transaction_id}')
        normalized = normalized.replace('{op_id}', '{operation_id}')
        normalized = normalized.replace('{sequence}', '{ledger_sequence}')

        return normalized

    def _determine_category(self, path: str) -> str:
        """Determine endpoint category from path"""
        parts = path.strip('/').split('/')
        if parts:
            return parts[0]
        return "other"

    def _is_streaming(self, path: str, line: str) -> bool:
        """Check if endpoint supports streaming by looking for streamHandler patterns"""
        if 'streamHandler' in line or 'StreamHandler' in line:
            return True
        if 'streamable' in line.lower():
            return True
        return False

    def _extract_parameters(self, path: str, category: str, method: str = "GET") -> List[Dict[str, str]]:
        """Extract parameters from path and add common query parameters.

        Path parameters are always included.  For GET list endpoints the
        standard pagination parameters (cursor, limit, order) are appended
        unconditionally, followed by any endpoint-specific parameters looked
        up from ``CATEGORY_PARAMS``.

        POST /transactions endpoints only receive the ``tx`` body parameter.
        """
        parameters = []

        # -- Path parameters --------------------------------------------------
        path_params = re.findall(r'\{([^}]+)\}', path)
        for param in path_params:
            parameters.append({
                "name": param,
                "location": "path",
                "required": "true",
                "description": f"The {param.replace('_', ' ')}"
            })

        # -- POST /transactions* : only a tx body parameter -------------------
        if method == "POST" and path in ('/transactions', '/transactions_async'):
            parameters.append({
                "name": "tx",
                "location": "body",
                "required": "true",
                "description": self.QUERY_PARAMETERS["tx"]["description"]
            })
            return parameters

        # -- List endpoints: pagination ----------------------------------------
        # An endpoint is a "list" endpoint when it has no path parameters, or
        # when it is a sub-resource list (more than two path segments and does
        # not end with a path-parameter placeholder).
        is_list_endpoint = (
            '/{' not in path
            or (path.count('/') > 2 and not path.endswith('}'))
        )

        if is_list_endpoint:
            for param in self.PAGINATION_PARAMS:
                parameters.append({
                    "name": param,
                    "location": "query",
                    "required": "false",
                    "description": self.QUERY_PARAMETERS[param]["description"]
                })

        # -- Endpoint-specific query parameters --------------------------------
        # Look up by exact path first, then by last path segment.
        # The substring fallback only checks the final segment of the path
        # to avoid incorrectly assigning top-level resource parameters
        # (e.g., /accounts filters) to sub-resource endpoints
        # (e.g., /accounts/{id}/effects).
        extra_params: Tuple[str, ...] = ()
        if path in self.CATEGORY_PARAMS:
            extra_params = self.CATEGORY_PARAMS[path]
        else:
            # Extract the last segment: /accounts/{id}/trades -> trades
            last_segment = path.rstrip('/').rsplit('/', 1)[-1]
            # Skip path parameter segments like {account_id}
            if not last_segment.startswith('{'):
                for key, params in self.CATEGORY_PARAMS.items():
                    if key == last_segment or key.lstrip('/') == last_segment:
                        extra_params = params
                        break

        for param in extra_params:
            if param in self.QUERY_PARAMETERS:
                parameters.append({
                    "name": param,
                    "location": "query",
                    "required": "false",
                    "description": self.QUERY_PARAMETERS[param]["description"]
                })

        # -- Special: include_failed for transaction list endpoints -----------
        if (
            category == 'transactions'
            and method == "GET"
            and (path == '/transactions' or path.endswith('/transactions'))
        ):
            parameters.append({
                "name": "include_failed",
                "location": "query",
                "required": "false",
                "description": self.QUERY_PARAMETERS["include_failed"]["description"]
            })

        return parameters

    def _extract_handler(self, line: str) -> str:
        """Extract handler name from route definition line"""
        handler_match = re.search(r'actions\.(\w+)', line)
        if handler_match:
            return handler_match.group(1)

        handler_patterns = [
            r'ObjectActionHandler\{.*?(\w+Handler)',
            r'streamable\w+\([^,]+,\s*actions\.(\w+)',
        ]

        for pattern in handler_patterns:
            match = re.search(pattern, line)
            if match:
                return match.group(1)

        return ""

    def _generate_description(self, path: str, category: str, method: str) -> str:
        """Generate human-readable endpoint description"""
        if path == '/fee_stats':
            return "Retrieve current fee statistics"
        if path == '/order_book':
            return "Retrieve the orderbook for a trading pair"
        if 'paths' in path:
            if 'strict-send' in path:
                return "Find payment paths for strict send"
            elif 'strict-receive' in path:
                return "Find payment paths for strict receive"
            return "Find payment paths between assets"
        if 'trade_aggregations' in path:
            return "Retrieve trade aggregations"

        if method == 'POST' and category == 'transactions':
            if 'async' in path:
                return "Submit a transaction asynchronously"
            return "Submit a transaction to the network"

        has_param = '/{' in path
        is_detail = has_param and path.endswith('}')

        category_actions = {
            "accounts": "account",
            "assets": "asset",
            "claimable_balances": "claimable balance",
            "effects": "effect",
            "ledgers": "ledger",
            "liquidity_pools": "liquidity pool",
            "offers": "offer",
            "operations": "operation",
            "payments": "payment",
            "trades": "trade",
            "transactions": "transaction",
        }

        resource_name = category_actions.get(category, category.replace('_', ' '))

        if is_detail:
            return f"Retrieve a single {resource_name}"
        elif has_param:
            sub_resource = path.split('/')[-1]
            sub_resource_name = category_actions.get(sub_resource, sub_resource.replace('_', ' '))
            return f"Retrieve {sub_resource_name}s for a {resource_name}"
        else:
            return f"List all {resource_name}s"

    def _organize_by_category(self) -> None:
        """Organise endpoints into per-category lists"""
        for endpoint in self.endpoints:
            if endpoint.category not in self.categories:
                self.categories[endpoint.category] = []
            self.categories[endpoint.category].append(endpoint)

    # ------------------------------------------------------------------
    # Output helpers
    # ------------------------------------------------------------------

    def to_json(self) -> Dict:
        """Build the full JSON data structure for serialisation"""
        metadata: Dict = {
            "source": str(self.router_path) if self.router_path else "GitHub",
            "generated_at": datetime.now().isoformat(),
            "total_endpoints": len(self.endpoints),
            "total_categories": len(self.categories),
        }

        # Embed version information when available.
        # Accepts both the Flutter convention (horizon_version / published_at / release_url)
        # and the raw GitHub release convention (version / published_at / html_url).
        if self.version_info:
            version = (
                self.version_info.get("horizon_version")
                or self.version_info.get("version")
            )
            if version:
                metadata["horizon_version"] = version

            published_at = self.version_info.get("published_at")
            if published_at:
                metadata["horizon_release_date"] = published_at

            release_url = (
                self.version_info.get("release_url")
                or self.version_info.get("html_url")
            )
            if release_url:
                metadata["horizon_release_url"] = release_url

        return {
            "metadata": metadata,
            "categories": {
                category: {
                    "total": len(endpoints),
                    "endpoints": [ep.to_dict() for ep in endpoints]
                }
                for category, endpoints in sorted(self.categories.items())
            },
            "endpoints": [ep.to_dict() for ep in self.endpoints]
        }

    def save_results(self) -> Path:
        """
        Write parsed endpoint data to the canonical output location.

        Output path: DATA_DIR / 'horizon' / 'horizon_endpoints.json'

        Returns:
            Path to the written file.
        """
        output_path = DATA_DIR / "horizon" / "horizon_endpoints.json"
        self._write_json(output_path)
        return output_path

    def save_json(self, output_path: str) -> None:
        """
        Write parsed endpoint data to a custom path.

        Provided for backward compatibility with callers that specify their own
        output path explicitly.

        Args:
            output_path: Destination file path (created with parent dirs as needed).
        """
        self._write_json(Path(output_path))

    def _write_json(self, output_file: Path) -> None:
        """Write the JSON data structure to *output_file*, creating parent dirs."""
        output_file.parent.mkdir(parents=True, exist_ok=True)
        data = self.to_json()
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"Saved endpoint data to: {output_file}")


# ---------------------------------------------------------------------------
# Standalone helpers
# ---------------------------------------------------------------------------

def _parse_from_local_file(router_path: Path, output_path: Path) -> int:
    """
    Parse a local router.go file and write results to *output_path*.

    Args:
        router_path: Path to the local router.go file.
        output_path: Destination JSON file path.

    Returns:
        0 on success, 1 on failure.
    """
    if not router_path.exists():
        print(f"ERROR: Router file not found: {router_path}")
        print("Please ensure the stellar-go repository is cloned locally.")
        return 1

    try:
        content = router_path.read_text(encoding="utf-8")
        parser = HorizonRouterParser(router_path=str(router_path))
        parser.parse_from_content(content)
        parser.save_json(str(output_path))

        print()
        print("=" * 70)
        print("SUMMARY")
        print("=" * 70)
        print(f"Total Endpoints: {len(parser.endpoints)}")
        print(f"Total Categories: {len(parser.categories)}")
        print()
        print("Endpoints by Category:")
        for category, endpoints in sorted(parser.categories.items()):
            print(f"  {category:25s}: {len(endpoints):3d} endpoints")
        print()
        print("=" * 70)
        print("Parsing completed successfully!")
        print("=" * 70)

        return 0

    except Exception as exc:
        print(f"\nERROR: {exc}")
        traceback.print_exc()
        return 1


def main() -> int:
    """Standalone entry point for direct script execution."""
    arg_parser = argparse.ArgumentParser(
        description="Parse Horizon API endpoints from router.go",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Parse from default location (stellar-go sibling of SDK root)
  %(prog)s

  # Parse a custom local file
  %(prog)s --local /path/to/router.go

  # Write to a custom output path
  %(prog)s --local /path/to/router.go --output /path/to/output.json
        """
    )

    arg_parser.add_argument(
        '--local',
        type=str,
        help='Path to local router.go file (default: ../stellar-go relative to SDK root)'
    )

    arg_parser.add_argument(
        '--output',
        type=str,
        help='Path to output JSON file (default: tools/matrix-generator/data/horizon/horizon_endpoints.json)'
    )

    args = arg_parser.parse_args()

    print("=" * 70)
    print("Horizon API Endpoint Parser")
    print("=" * 70)
    print()

    # Resolve output path
    output_path = (
        Path(args.output)
        if args.output
        else DATA_DIR / "horizon" / "horizon_endpoints.json"
    )

    # Resolve router.go path
    router_path = (
        Path(args.local)
        if args.local
        else SDK_ROOT.parent / "stellar-go" / "services" / "horizon" / "internal" / "httpx" / "router.go"
    )

    print(f"Router path : {router_path}")
    print(f"Output path : {output_path}")
    print()

    return _parse_from_local_file(router_path, output_path)


if __name__ == '__main__':
    sys.exit(main())
