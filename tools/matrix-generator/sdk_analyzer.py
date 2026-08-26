#!/usr/bin/env python3
"""
KMP Stellar SDK Implementation Analyzer

Analyzes Kotlin Multiplatform Stellar SDK request builders and exposed methods to extract
implementation details including supported endpoints, parameters, filters, and streaming
capabilities.

Author: KMP Stellar SDK Team
License: Apache-2.0
"""

import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Set, Optional
from dataclasses import dataclass, field

from common import Colors, DATA_DIR, SDK_ROOT, get_sdk_version, camel_to_snake


@dataclass
class RequestBuilderInfo:
    """Information about a request builder class"""
    class_name: str
    file_path: str
    endpoints: List[str]
    methods: List[Dict[str, str]]
    filter_methods: List[Dict[str, str]]
    streaming_support: bool
    exposed_in_sdk: bool
    sdk_property: str = ""

    def to_dict(self) -> Dict:
        """Convert to dictionary for JSON serialization"""
        return {
            "class_name": self.class_name,
            "file_path": self.file_path,
            "endpoints": self.endpoints,
            "methods": self.methods,
            "filter_methods": self.filter_methods,
            "streaming_support": self.streaming_support,
            "exposed_in_sdk": self.exposed_in_sdk,
            "sdk_property": self.sdk_property
        }


class KmpSDKAnalyzer:
    """Analyzer for KMP Stellar SDK implementation"""

    # Map request builder classes to their primary endpoints
    BUILDER_ENDPOINTS = {
        "AccountsRequestBuilder": ["/accounts", "/accounts/{account_id}", "/accounts/{account_id}/data/{key}"],
        "AssetsRequestBuilder": ["/assets"],
        "ClaimableBalancesRequestBuilder": ["/claimable_balances", "/claimable_balances/{claimable_balance_id}"],
        "EffectsRequestBuilder": ["/effects"],
        "FeeStatsRequestBuilder": ["/fee_stats"],
        "HealthRequestBuilder": ["/health"],
        "LedgersRequestBuilder": ["/ledgers", "/ledgers/{ledger_sequence}"],
        "LiquidityPoolsRequestBuilder": ["/liquidity_pools", "/liquidity_pools/{liquidity_pool_id}"],
        "OffersRequestBuilder": ["/offers", "/offers/{offer_id}"],
        "OperationsRequestBuilder": ["/operations", "/operations/{operation_id}"],
        "OrderBookRequestBuilder": ["/order_book"],
        "PaymentsRequestBuilder": ["/payments"],
        "StrictSendPathsRequestBuilder": ["/paths/strict-send"],
        "StrictReceivePathsRequestBuilder": ["/paths/strict-receive"],
        "TradeAggregationsRequestBuilder": ["/trade_aggregations"],
        "TradesRequestBuilder": ["/trades", "/accounts/{account_id}/trades", "/liquidity_pools/{liquidity_pool_id}/trades"],
        "TransactionsRequestBuilder": ["/transactions", "/transactions/{transaction_id}"],
        "RootRequestBuilder": ["/"],
    }

    # Known filter methods and their corresponding parameters
    FILTER_PARAMETER_MAP = {
        # Standard filter methods (for*, with*, include*)
        "forAccount": "account_id",
        "forAsset": "asset",
        "forSigner": "signer",
        "forSponsor": "sponsor",
        "forLiquidityPool": "liquidity_pool",
        "forClaimant": "claimant",
        "forSeller": "seller",
        "forBuyingAsset": "buying_asset",
        "forSellingAsset": "selling_asset",
        "forOffer": "offer_id",
        "forOfferId": "offer_id",  # FIX Issue 6: TradesRequestBuilder.forOfferId()
        "forTransaction": "transaction_id",
        "forLedger": "ledger_sequence",
        "forType": "type",
        "forTradeType": "type",  # FIX Issues 2-4: Maps to "type" parameter in Horizon API
        "forReserveAssets": "reserves",
        "forPoolId": "liquidity_pool_id",
        "forClaimableBalance": "claimable_balance_id",
        "forOperation": "operation_id",
        "includeFailed": "include_failed",
        "join": "join",
        # AssetsRequestBuilder methods
        "assetCode": "asset_code",
        "assetIssuer": "asset_issuer",
        # TradesRequestBuilder methods
        "baseAsset": "base_asset",
        "counterAsset": "counter_asset",
        "tradeType": "type",  # FIX Issues 2-4: Alternative mapping
        "offerId": "offer_id",
        "liquidityPoolId": "liquidity_pool_id",
        # OrderBookRequestBuilder methods
        "sellingAsset": "selling_asset",
        "buyingAsset": "buying_asset",
        # Path builders methods
        "sourceAccount": "source_account",
        "sourceAssets": "source_assets",
        "sourceAsset": "source_asset",
        "sourceAmount": "source_amount",
        "destinationAccount": "destination_account",
        "destinationAssets": "destination_assets",
        "destinationAsset": "destination_asset",
        "destinationAmount": "destination_amount",
    }

    # Parameter name aliases - maps Horizon API parameter names to SDK parameter names
    # FIX Issues 2-4 and 5: Allow parameter name variations
    PARAMETER_ALIASES = {
        'type': ['trade_type', 'tradeType'],  # FIX Issues 2-4: trade_type <-> type
        'tx': ['transactionEnvelopeXdr'],     # FIX Issue 5: transactionEnvelopeXdr -> tx
    }

    # Endpoint equivalences - different API patterns that access the same functionality
    # FIX Issue 6: Query parameter approach vs path-based approach
    ENDPOINT_EQUIVALENCES = {
        '/offers/{offer_id}/trades': {
            'equivalent_to': '/trades?offer_id={id}',
            'builder': 'TradesRequestBuilder',
            'method': 'forOfferId',
            'note': 'Implemented via query parameter instead of path segment'
        }
    }

    def __init__(self) -> None:
        """Initialize analyzer using SDK_ROOT from common module."""
        self.sdk_root = SDK_ROOT
        self.requests_dir = (
            SDK_ROOT
            / "stellar-sdk"
            / "src"
            / "commonMain"
            / "kotlin"
            / "com"
            / "soneso"
            / "stellar"
            / "sdk"
            / "horizon"
            / "requests"
        )
        self.horizon_server = (
            SDK_ROOT
            / "stellar-sdk"
            / "src"
            / "commonMain"
            / "kotlin"
            / "com"
            / "soneso"
            / "stellar"
            / "sdk"
            / "horizon"
            / "HorizonServer.kt"
        )
        self.request_builder_base = self.requests_dir / "RequestBuilder.kt"
        self.builders: List[RequestBuilderInfo] = []
        self.exposed_builders: Set[str] = set()
        self.builder_properties: Dict[str, str] = {}  # Maps builder class name to SDK property/method name
        self.sdk_methods: Dict[str, Dict] = {}
        self.direct_endpoints: Dict[str, Dict] = {}  # Direct endpoint implementations (not via RequestBuilder)
        self.base_class_methods: Dict[str, List[str]] = {}  # Methods inherited from base classes
        self.builder_endpoints_map: Dict[str, List[str]] = {}  # Maps builder to all its endpoints (including sub-resources)

    def analyze(self) -> None:
        """Perform complete analysis of SDK implementation"""
        print(f"Analyzing KMP Stellar SDK: {self.sdk_root}")

        # Step 1: Analyze base RequestBuilder class to find inherited methods
        print("\nStep 1: Analyzing base RequestBuilder class...")
        self._analyze_base_request_builder()

        # Step 2: Find exposed builders in HorizonServer class (FIXED: now detects factory methods)
        print("\nStep 2: Analyzing HorizonServer class for exposed builders...")
        self._analyze_horizon_server_class()

        # Step 3: Analyze utility classes (like FriendBot)
        print("\nStep 3: Analyzing utility classes...")
        self._analyze_utility_classes()

        # Step 4: Analyze each request builder (FIXED: now detects multi-endpoint patterns)
        print("\nStep 4: Analyzing request builders...")
        self._analyze_request_builders()

        # Step 5: Build SDK methods mapping for comparison
        print("\nStep 5: Building SDK methods mapping...")
        self._build_sdk_methods_mapping()

        print(f"\n{'='*70}")
        print(f"ANALYSIS COMPLETE")
        print(f"{'='*70}")
        print(f"Analyzed {len(self.builders)} request builders")
        print(f"Found {len(self.exposed_builders)} exposed in HorizonServer class")
        print(f"Found {len(self.direct_endpoints)} direct endpoint implementations")
        print(f"Found {len(self.base_class_methods)} base class methods")

    def _analyze_base_request_builder(self) -> None:
        """
        Analyze the base RequestBuilder class to extract methods that all builders inherit.

        PRIORITY 2 FIX: This addresses the streaming support detection failure.
        All RequestBuilder classes inherit from the base RequestBuilder class which provides
        universal methods like stream(), cursor(), limit(), order(), etc.
        """
        if not self.request_builder_base.exists():
            print(f"  WARNING: RequestBuilder base class not found: {self.request_builder_base}")
            return

        content = self.request_builder_base.read_text()

        # Extract the base class name - matches both "open class" and "abstract class"
        base_class_match = re.search(r'(?:open|abstract)\s+class\s+(\w+)\s*\(', content)
        if not base_class_match:
            print("  WARNING: Could not find base RequestBuilder class definition")
            return

        base_class_name = base_class_match.group(1)
        print(f"  Found base class: {base_class_name}")

        # Extract public methods from base class
        # Pattern: fun <methodName>(...): <ReturnType>
        method_pattern = r'(?:suspend\s+)?fun\s+(<[^>]+>\s+)?(\w+)\s*\([^)]*\)\s*:\s*([\w<>?]+)'

        methods = []
        for match in re.finditer(method_pattern, content):
            method_name = match.group(2)
            return_type = match.group(3)

            # Skip private/internal methods
            if method_name.startswith('_'):
                continue

            # Track all public methods
            methods.append(method_name)

            print(f"    Found base method: {method_name}() -> {return_type}")

        # Store base class methods
        self.base_class_methods[base_class_name] = methods

        # Check specifically for streaming support
        if 'stream' in methods:
            print(f"  Base class provides universal streaming support via stream() method")

        print(f"  Total base class methods: {len(methods)}")

    def _analyze_horizon_server_class(self) -> None:
        """
        Analyze HorizonServer class to find exposed builders and direct endpoint implementations.

        PRIORITY 1 FIX: This now correctly detects factory methods that return RequestBuilder
        instances. Previous version only looked for zero-argument methods, missing methods with
        parameters like tradeAggregations(...).
        """
        if not self.horizon_server.exists():
            print(f"  WARNING: HorizonServer file not found: {self.horizon_server}")
            return

        content = self.horizon_server.read_text()

        # PRIORITY 1 FIX: Improved factory method detection
        # Pattern 1: fun methodName(): BuilderType (zero arguments)
        # Pattern 2: fun methodName(...): BuilderType (with arguments, like tradeAggregations)
        # Pattern 3: fun methodName(): fully.qualified.BuilderType (with package name)

        # Match both zero-arg and parameterized factory methods
        factory_pattern = r'fun\s+(\w+)\s*\([^)]*\)\s*:\s*(?:[\w.]+\.)?(\w+RequestBuilder)'
        factory_matches = re.findall(factory_pattern, content)

        print(f"  Scanning for factory methods returning RequestBuilder...")
        for method_name, builder_class in factory_matches:
            self.exposed_builders.add(builder_class)
            self.builder_properties[builder_class] = method_name
            print(f"    Found: {method_name}() -> {builder_class}")

        print(f"  Total exposed builders: {len(self.exposed_builders)}")

        # Find direct endpoint implementations (methods that construct URLs and make HTTP calls)
        self._extract_direct_endpoints(content)

    def _extract_direct_endpoints(self, content: str) -> None:
        """
        Extract direct endpoint implementations from HorizonServer class.

        Direct endpoints are those implemented directly in HorizonServer without
        using a RequestBuilder class (e.g., submitTransaction, root).
        """
        print(f"  Scanning for direct endpoint implementations...")

        # Look for root() method
        root_pattern = r'suspend\s+fun\s+root\s*\(\s*\)\s*:\s*(\w+)'
        if re.search(root_pattern, content):
            self.direct_endpoints["/"] = {
                "method": "root",
                "http_method": "GET",
                "return_type": "RootResponse",
                "implemented": True
            }
            print(f"    Found: GET / -> root()")

        # Look for submitTransaction() method
        submit_pattern = r'suspend\s+fun\s+submitTransaction\s*\('
        if re.search(submit_pattern, content):
            self.direct_endpoints["/transactions"] = {
                "method": "submitTransaction",
                "http_method": "POST",
                "return_type": "SubmitTransactionResponse",
                "implemented": True
            }
            print(f"    Found: POST /transactions -> submitTransaction()")

        # Look for submitTransactionAsync() or submitAsyncTransaction() method
        async_submit_pattern = r'suspend\s+fun\s+submit(?:Transaction)?Async(?:Transaction)?\s*\('
        if re.search(async_submit_pattern, content):
            # FIX Issue 5: Check if method maps transactionEnvelopeXdr to tx parameter
            has_tx_mapping = 'append("tx"' in content or "append('tx'" in content
            self.direct_endpoints["/transactions_async"] = {
                "method": "submitTransactionAsync",
                "http_method": "POST",
                "return_type": "SubmitTransactionAsyncResponse",
                "implemented": True,
                "parameters": ["tx"],  # FIX Issue 5: Document tx parameter support
                "notes": "Parameter transactionEnvelopeXdr is mapped to tx internally" if has_tx_mapping else ""
            }
            print(f"    Found: POST /transactions_async -> submitTransactionAsync()")
            if has_tx_mapping:
                print(f"      Note: transactionEnvelopeXdr parameter mapped to 'tx'")

        print(f"  Total direct endpoints: {len(self.direct_endpoints)}")

    def _analyze_utility_classes(self) -> None:
        """Analyze utility classes like FriendBot that provide direct endpoint access"""
        util_path = (
            SDK_ROOT
            / "stellar-sdk"
            / "src"
            / "commonMain"
            / "kotlin"
            / "com"
            / "soneso"
            / "stellar"
            / "sdk"
            / "FriendBot.kt"
        )
        if not util_path.exists():
            print(f"  FriendBot utility class not found at: {util_path}")
            return

        content = util_path.read_text()

        # Check for FriendBot object with fundTestnetAccount and fundFuturenetAccount methods
        # Look for the object declaration and the fund methods separately
        if 'object FriendBot' in content:
            # Check for fundTestnetAccount method
            if 'suspend fun fundTestnetAccount' in content or 'suspend fun fundFuturenetAccount' in content:
                # Also verify it makes HTTP calls to friendbot endpoint
                if 'friendbot' in content.lower():
                    self.direct_endpoints["/friendbot"] = {
                        "method": "FriendBot.fundTestnetAccount / FriendBot.fundFuturenetAccount",
                        "http_method": "GET",
                        "return_type": "Boolean",
                        "implemented": True,
                        "notes": "Implemented via FriendBot utility class (testnet/futurenet only)"
                    }
                    print(f"  Found utility class: FriendBot")
                    print(f"    Methods: fundTestnetAccount(), fundFuturenetAccount()")
                    print(f"    Maps to: GET /friendbot endpoint")

    def _analyze_request_builders(self) -> None:
        """Analyze all request builder files"""
        if not self.requests_dir.exists():
            raise FileNotFoundError(f"Requests directory not found: {self.requests_dir}")

        kotlin_files = list(self.requests_dir.glob("*RequestBuilder.kt"))
        print(f"Found {len(kotlin_files)} request builder files")

        for kotlin_file in sorted(kotlin_files):
            try:
                self._analyze_builder_file(kotlin_file)
            except Exception as e:
                print(f"WARNING: Error analyzing {kotlin_file.name}: {e}")

    def _analyze_builder_file(self, file_path: Path) -> None:
        """
        Analyze a single request builder file.

        PRIORITY 3 FIX: Now detects multi-endpoint patterns by analyzing setSegments() calls
        in methods like forAccount(), forLedger(), etc.
        """
        content = file_path.read_text()

        # Extract class name - pattern: class ClassName(...) : RequestBuilder
        class_match = re.search(r'class\s+(\w+RequestBuilder)\s*\([^)]*\)\s*:\s*RequestBuilder', content)

        if not class_match:
            return

        class_name = class_match.group(1)

        # PRIORITY 3 FIX: Extract endpoints dynamically from setSegments() calls
        endpoints = self._extract_endpoints_from_builder(content, class_name)

        # If no endpoints found via analysis, fall back to hardcoded list
        if not endpoints:
            endpoints = self.BUILDER_ENDPOINTS.get(class_name, [])

        # Extract methods
        methods = self._extract_methods(content, class_name)

        # Extract filter methods
        filter_methods = self._extract_filter_methods(content, class_name)

        # PRIORITY 2 FIX: Check for streaming support via base class inheritance
        # All RequestBuilder subclasses inherit stream() method
        streaming_support = True  # All builders inherit from RequestBuilder which has stream()

        # Check if exposed in SDK
        exposed_in_sdk = class_name in self.exposed_builders

        # Get SDK property/method name
        sdk_property = self.builder_properties.get(class_name, "")

        # Create builder info
        builder = RequestBuilderInfo(
            class_name=class_name,
            file_path=str(file_path.relative_to(self.sdk_root)),
            endpoints=endpoints,
            methods=methods,
            filter_methods=filter_methods,
            streaming_support=streaming_support,
            exposed_in_sdk=exposed_in_sdk,
            sdk_property=sdk_property
        )

        self.builders.append(builder)
        self.builder_endpoints_map[class_name] = endpoints

        # Debug output
        if exposed_in_sdk and len(endpoints) > 1:
            print(f"  [{class_name}] handles {len(endpoints)} endpoints")

    def _extract_endpoints_from_builder(self, content: str, class_name: str) -> List[str]:
        """
        PRIORITY 3 FIX: Extract endpoint paths by analyzing setSegments() calls.
        FIX Issue 1: Also detect endpoint-specific methods like accountData().

        This detects multi-endpoint patterns like:
        - fun forAccount(account: String) { setSegments("accounts", account, "transactions") }
          -> /accounts/{account}/transactions
        - fun forLedger(seq: Long) { setSegments("ledgers", seq.toString(), "transactions") }
          -> /ledgers/{seq}/transactions
        - fun accountData(accountId: String, key: String) { setSegments("accounts", accountId, "data", key) }
          -> /accounts/{account_id}/data/{key}

        Returns:
            List of endpoint paths this builder handles
        """
        endpoints = []

        # Find setSegments() calls with balanced-parenthesis extraction: an argument
        # may itself contain calls (e.g. ClaimableBalanceId.forId(x).toPaddedHex()),
        # and the call may span multiple lines, so a [^)]+ regex truncates the
        # argument list at the first ')' and drops trailing segments.
        # Examples:
        # setSegments("accounts", accountId)
        # setSegments("accounts", account, "transactions")
        # setSegments("ledgers", ledgerSeq.toString(), "transactions")
        # setSegments("accounts", accountId, "data", key)  # FIX Issue 1
        def _setsegments_arg_lists(text):
            idx = 0
            while True:
                pos = text.find('setSegments', idx)
                if pos == -1:
                    return
                open_pos = text.find('(', pos)
                if open_pos == -1:
                    return
                depth, i = 1, open_pos + 1
                while i < len(text) and depth:
                    if text[i] == '(':
                        depth += 1
                    elif text[i] == ')':
                        depth -= 1
                    i += 1
                yield text[open_pos + 1:i - 1]
                idx = i

        def _split_top_level_commas(args):
            parts, depth, cur = [], 0, []
            for ch in args:
                if ch == '(':
                    depth += 1
                elif ch == ')':
                    depth -= 1
                if ch == ',' and depth == 0:
                    parts.append(''.join(cur).strip())
                    cur = []
                else:
                    cur.append(ch)
            if cur:
                parts.append(''.join(cur).strip())
            return parts

        for segments_str in _setsegments_arg_lists(content):
            # Parse the segments - split on top-level commas only and clean up
            segments_raw = _split_top_level_commas(segments_str)

            # Convert to path segments
            path_parts = []
            for segment in segments_raw:
                # Remove quotes from string literals
                segment_clean = segment.strip('"').strip("'")

                # Check if it's a string literal or a variable
                if segment.startswith('"') or segment.startswith("'"):
                    # String literal - use as-is
                    path_parts.append(segment_clean)
                elif any(keyword in segment for keyword in ['.toString()', 'account', 'ledger', 'transaction',
                                                            'operation', 'claimable', 'liquidity', 'offer', 'pool',
                                                            'key', 'data']):  # FIX Issue 1: Added 'key', 'data'
                    # Variable reference - create placeholder
                    # Infer the placeholder name from common patterns
                    if 'account' in segment.lower():
                        path_parts.append('{account_id}')
                    elif 'ledger' in segment.lower():
                        path_parts.append('{ledger_sequence}')
                    elif 'transaction' in segment.lower() or 'hash' in segment.lower():
                        path_parts.append('{transaction_id}')
                    elif 'operation' in segment.lower():
                        path_parts.append('{operation_id}')
                    elif 'claimable' in segment.lower():
                        path_parts.append('{claimable_balance_id}')
                    elif 'liquidity' in segment.lower() or 'pool' in segment.lower():
                        path_parts.append('{liquidity_pool_id}')
                    elif 'offer' in segment.lower():
                        path_parts.append('{offer_id}')
                    elif 'key' in segment.lower():
                        # FIX Issue 1: data entry key placeholder
                        path_parts.append('{key}')
                    else:
                        # Generic variable: infer the placeholder from the
                        # preceding collection literal (e.g. a `ledger(id)` call's
                        # setSegments("ledgers", id.toString()) names {ledger_sequence}),
                        # falling back to {id} with no known predecessor.
                        prev = path_parts[-1] if path_parts else ''
                        path_parts.append({
                            'accounts': '{account_id}',
                            'ledgers': '{ledger_sequence}',
                            'transactions': '{transaction_id}',
                            'operations': '{operation_id}',
                            'claimable_balances': '{claimable_balance_id}',
                            'liquidity_pools': '{liquidity_pool_id}',
                            'offers': '{offer_id}',
                        }.get(prev, '{id}'))

            # Construct the endpoint path
            if path_parts:
                endpoint = '/' + '/'.join(path_parts)
                if endpoint not in endpoints:
                    endpoints.append(endpoint)

        # Also check for direct endpoint constructor argument
        # Pattern: class XxxRequestBuilder(...) : RequestBuilder(..., "endpoint_name")
        constructor_pattern = r':\s*RequestBuilder\s*\([^,]+,\s*[^,]+,\s*"([^"]+)"'
        constructor_match = re.search(constructor_pattern, content)
        if constructor_match:
            base_endpoint = '/' + constructor_match.group(1)
            if base_endpoint not in endpoints:
                endpoints.insert(0, base_endpoint)  # Add as first endpoint

        return endpoints

    def _extract_methods(self, content: str, class_name: str) -> List[Dict[str, str]]:
        """
        PRIORITY 5 FIX: Extract public methods with enhanced Kotlin pattern recognition.

        Now detects:
        - Regular methods: fun methodName(...): ReturnType
        - Suspend functions: suspend fun methodName(...): ReturnType
        - Generic methods: fun <T> methodName(...): ReturnType
        - Nullable return types: fun methodName(...): ReturnType?
        - Inline functions: inline fun methodName(...): ReturnType
        """
        methods = []

        # PRIORITY 5 FIX: Enhanced pattern for Kotlin method definitions
        # Matches: [inline] [suspend] fun [<generics>] methodName(...): ReturnType[?]
        method_pattern = r'(?:inline\s+)?(?:suspend\s+)?fun\s+(?:<[^>]+>\s+)?(\w+)\s*\([^)]*\)\s*:\s*([\w<>?]+)'

        for match in re.finditer(method_pattern, content):
            method_name = match.group(1)
            return_type = match.group(2)

            # Skip private methods (start with lowercase or underscore)
            if method_name.startswith('_'):
                continue

            # Skip inherited methods from RequestBuilder base class
            if method_name in ['cursor', 'limit', 'order', 'execute', 'executeSSE', 'buildUri',
                               'setSegments', 'forEndpoint', 'stream', 'buildUrl']:
                continue

            # Skip constructors and standard Kotlin methods
            if method_name in ['equals', 'hashCode', 'toString', 'copy', 'component1', 'component2']:
                continue

            # Detect if this is a suspend function by checking the line
            is_suspend = 'suspend' in content[max(0, match.start() - 20):match.start()]

            methods.append({
                "name": method_name,
                "return_type": return_type,
                "is_suspend": is_suspend
            })

        return methods

    def _extract_filter_methods(self, content: str, class_name: str) -> List[Dict[str, str]]:
        """
        PRIORITY 4 FIX: Extract filter methods from builder with improved parameter detection.

        Detects methods that return the builder itself (fluent API pattern) and extracts
        parameters they set on the URI.
        """
        filter_methods = []

        # Pattern for filter methods that return the builder itself
        # fun methodName(...): BuilderType
        pattern = rf'fun\s+(\w+)\s*\([^)]*\)\s*:\s*{re.escape(class_name)}'

        for match in re.finditer(pattern, content):
            method_name = match.group(1)

            # Skip inherited pagination methods
            if method_name in ['cursor', 'limit', 'order']:
                continue

            # Check our known filter parameter map first
            if method_name in self.FILTER_PARAMETER_MAP:
                param_name = self.FILTER_PARAMETER_MAP[method_name]

                filter_methods.append({
                    "method": method_name,
                    "parameter": param_name
                })
            else:
                # PRIORITY 4 FIX: Try to infer parameter name from method name
                # Common patterns: forXxx -> xxx, includeXxx -> include_xxx, withXxx -> xxx
                if method_name.startswith('for'):
                    # forAccount -> account_id, forLedger -> ledger_sequence, etc.
                    param_base = method_name[3:]  # Remove 'for'
                    param_name = self._infer_parameter_name(param_base)
                elif method_name.startswith('include'):
                    # includeFailed -> include_failed
                    param_base = method_name[7:]  # Remove 'include'
                    param_name = 'include_' + camel_to_snake(param_base)
                elif method_name.startswith('with'):
                    # withXxx -> xxx
                    param_base = method_name[4:]  # Remove 'with'
                    param_name = camel_to_snake(param_base)
                else:
                    # Direct parameter name (like assetCode -> asset_code)
                    param_name = camel_to_snake(method_name)

                filter_methods.append({
                    "method": method_name,
                    "parameter": param_name
                })

        return filter_methods

    def _infer_parameter_name(self, param_base: str) -> str:
        """
        Infer API parameter name from method suffix.

        Examples:
            Account -> account_id
            Ledger -> ledger_sequence
            Transaction -> transaction_id
            Asset -> asset
        """
        param_lower = param_base.lower()

        if param_lower == 'account':
            return 'account_id'
        elif param_lower == 'ledger':
            return 'ledger_sequence'
        elif param_lower in ['transaction', 'operation', 'offer']:
            return param_lower + '_id'
        elif param_lower == 'claimablebalance':
            return 'claimable_balance_id'
        elif param_lower in ['liquiditypool', 'pool']:
            return 'liquidity_pool_id'
        else:
            return camel_to_snake(param_base)

    def _build_sdk_methods_mapping(self) -> None:
        """Build SDK methods mapping for endpoint comparison"""
        # First, add direct endpoint implementations
        for endpoint_path, endpoint_info in self.direct_endpoints.items():
            # For POST endpoints, append the HTTP method to match the lookup key format
            if endpoint_info["http_method"] == "POST":
                key = f"{endpoint_path} ({endpoint_info['http_method']})"
            else:
                key = endpoint_path

            # Determine the class name
            method_name = endpoint_info["method"]
            if "." in method_name:
                class_name = method_name.split(".")[0]
            else:
                class_name = "HorizonServer"

            # Generate notes
            notes = endpoint_info.get("notes", "")
            if not notes:
                notes = f"Implemented via {method_name}() method"
            elif "Implemented via" not in notes:
                notes = f"Implemented via {method_name}() method. {notes}"

            # FIX Issue 5: Include parameters list if present
            filters = endpoint_info.get("parameters", [])

            self.sdk_methods[key] = {
                "implemented": endpoint_info["implemented"],
                "sdk_method": method_name,
                "class": class_name,
                "streaming": False,  # Direct endpoints don't support streaming
                "deprecated": False,
                "filters": filters,
                "http_method": endpoint_info["http_method"],
                "notes": notes
            }

        # Then, add request builder implementations
        for builder in self.builders:
            for endpoint in builder.endpoints:
                # Determine if implemented
                implemented = builder.exposed_in_sdk

                # Build filters list
                filters = [f["parameter"] for f in builder.filter_methods]

                # Add standard pagination filters if it's a list endpoint
                if not any(param in endpoint for param in ['{', '}']):
                    filters.extend(['cursor', 'limit', 'order'])

                self.sdk_methods[endpoint] = {
                    "implemented": implemented,
                    "sdk_method": builder.sdk_property if builder.sdk_property else builder.class_name,
                    "class": builder.class_name,
                    "streaming": builder.streaming_support,
                    "deprecated": False,
                    "filters": list(set(filters)),  # Remove duplicates
                    "notes": f"Implemented via {builder.class_name}" if implemented else ""
                }

                # Add sub-resource endpoints
                if endpoint.endswith('}'):
                    # This is a detail endpoint, add common sub-resources
                    base_category = endpoint.split('/')[1]
                    sub_resources = self._get_sub_resources(base_category)

                    for sub_resource in sub_resources:
                        sub_endpoint = f"{endpoint}/{sub_resource}"
                        if sub_endpoint not in self.sdk_methods:
                            # Check if there's a specific builder for this
                            sub_builder = self._find_builder_for_endpoint(sub_endpoint)
                            if sub_builder:
                                self.sdk_methods[sub_endpoint] = {
                                    "implemented": sub_builder.exposed_in_sdk,
                                    "sdk_method": f"{builder.sdk_property}.{sub_resource}" if builder.sdk_property else "",
                                    "class": sub_builder.class_name,
                                    "streaming": sub_builder.streaming_support,
                                    "deprecated": False,
                                    "filters": [f["parameter"] for f in sub_builder.filter_methods] + ['cursor', 'limit', 'order'],
                                    "notes": f"Implemented via {sub_builder.class_name}"
                                }

        # FIX Issue 6: Add endpoint equivalences
        # These are endpoints that are implemented via alternative patterns
        for equiv_endpoint, equiv_info in self.ENDPOINT_EQUIVALENCES.items():
            if equiv_endpoint not in self.sdk_methods:
                # Find the builder that implements the equivalent functionality
                builder_name = equiv_info['builder']
                builder = next((b for b in self.builders if b.class_name == builder_name), None)

                if builder and builder.exposed_in_sdk:
                    # Check if the builder has the required method
                    method_name = equiv_info['method']
                    filter_method = next((f for f in builder.filter_methods if f['method'] == method_name), None)

                    if filter_method:
                        # Add the equivalent endpoint as fully supported
                        filters = [f["parameter"] for f in builder.filter_methods]
                        filters.extend(['cursor', 'limit', 'order'])

                        self.sdk_methods[equiv_endpoint] = {
                            "implemented": True,
                            "sdk_method": f"{builder.sdk_property}.{method_name}" if builder.sdk_property else method_name,
                            "class": builder_name,
                            "streaming": builder.streaming_support,
                            "deprecated": False,
                            "filters": list(set(filters)),
                            "notes": f"{equiv_info['note']}. Use {builder.sdk_property}().{method_name}()"
                        }

    def _get_sub_resources(self, category: str) -> List[str]:
        """Get common sub-resources for a category"""
        sub_resources = {
            "accounts": ["effects", "offers", "operations", "payments", "trades", "transactions"],
            "ledgers": ["effects", "operations", "payments", "transactions"],
            "liquidity_pools": ["effects", "operations", "trades", "transactions"],
            "claimable_balances": ["operations", "transactions"],
            "transactions": ["effects", "operations", "payments"],
            "operations": ["effects"],
        }
        return sub_resources.get(category, [])

    def _find_builder_for_endpoint(self, endpoint: str) -> Optional[RequestBuilderInfo]:
        """Find request builder that handles a specific endpoint"""
        # Extract the resource type from endpoint
        parts = endpoint.strip('/').split('/')
        if len(parts) >= 3:
            resource = parts[-1]  # Last part is the resource type

            # Find builder for this resource
            for builder in self.builders:
                if resource in [e.split('/')[-1] for e in builder.endpoints if '/' in e]:
                    return builder

        return None

    def to_json(self) -> Dict:
        """Convert analyzed data to JSON structure"""
        sdk_version = get_sdk_version()

        return {
            "metadata": {
                "sdk_version": sdk_version,
                "analyzed_at": datetime.now().isoformat(),
                "total_request_builders": len(self.builders),
                "exposed_builders": len(self.exposed_builders),
                "sdk_root": str(self.sdk_root)
            },
            "request_builders": [builder.to_dict() for builder in self.builders],
            "sdk_methods": self.sdk_methods
        }

    def save_json(self, output_path: Path) -> None:
        """
        Save analyzed data to JSON file.

        Args:
            output_path: Destination path for the JSON output file.
        """
        output_path.parent.mkdir(parents=True, exist_ok=True)

        data = self.to_json()

        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

        print(f"Saved SDK analysis to: {output_path}")


def main() -> int:
    """Main entry point"""
    print("=" * 70)
    print("KMP Stellar SDK Implementation Analyzer")
    print("=" * 70)
    print()

    output_path = DATA_DIR / "horizon" / "kmp_sdk_implementation.json"

    try:
        analyzer = KmpSDKAnalyzer()
        analyzer.analyze()

        analyzer.save_json(output_path)

        print()
        print("=" * 70)
        print("SUMMARY")
        print("=" * 70)
        print(f"SDK Version: {get_sdk_version()}")
        print(f"Total Request Builders: {len(analyzer.builders)}")
        print(f"Exposed in HorizonServer: {len(analyzer.exposed_builders)}")
        print()
        print("Request Builders:")
        for builder in sorted(analyzer.builders, key=lambda b: b.class_name):
            status = "v" if builder.exposed_in_sdk else " "
            streaming = "S" if builder.streaming_support else " "
            print(f"  [{status}] [{streaming}] {builder.class_name:40s} "
                  f"({len(builder.endpoints)} endpoints, {len(builder.filter_methods)} filters)")
        print()
        print("Legend: [v] = Exposed in SDK, [S] = Streaming support")
        print()
        print("=" * 70)
        print("Analysis completed successfully!")
        print("=" * 70)

        return 0

    except Exception as e:
        print(f"\nERROR: {str(e)}")
        import traceback
        traceback.print_exc()
        return 1


if __name__ == '__main__':
    sys.exit(main())
