#!/usr/bin/env python3
"""
Stellar RPC API vs KMP Stellar SDK Soroban Comparison Generator

Compares the Stellar RPC API methods with the KMP Stellar SDK Soroban
implementation and generates comparison data including coverage statistics,
gaps analysis, and prioritized recommendations.

License: Apache-2.0
"""

import json
import re
import sys
import traceback
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Any
from dataclasses import dataclass, field
from enum import Enum

# Add parent directory to path for shared modules
sys.path.insert(0, str(Path(__file__).parent.parent))

from common import DATA_DIR, COMPATIBILITY_DIR, SDK_ROOT, get_sdk_version
from rpc_parser import GoProtocolParser


class SupportStatus(Enum):
    """Support status for RPC methods"""
    FULLY_SUPPORTED = "✅ Fully Supported"
    PARTIALLY_SUPPORTED = "⚠️ Partially Supported"
    NOT_SUPPORTED = "❌ Not Supported"
    DEPRECATED = "🔄 Deprecated"


class Priority(Enum):
    """Priority levels for gaps and missing features"""
    CRITICAL = "Critical"
    HIGH = "High"
    MEDIUM = "Medium"
    LOW = "Low"


@dataclass
class ComparisonMetrics:
    """Comparison metrics for both method parameters and response fields."""
    total: int = 0
    supported: int = 0
    missing: List[str] = field(default_factory=list)

    @property
    def percentage(self) -> float:
        """Calculate support percentage."""
        return (self.supported / self.total * 100) if self.total > 0 else 0.0


@dataclass
class MethodComparison:
    """Complete comparison data for a single RPC method"""
    rpc_method: str
    sdk_implementation: Dict[str, Any] = field(default_factory=dict)
    parameters: Dict[str, ComparisonMetrics] = field(default_factory=dict)
    response_fields: Optional[ComparisonMetrics] = None
    status: str = SupportStatus.NOT_SUPPORTED.value
    notes: str = ""
    priority: str = Priority.MEDIUM.value

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        result: Dict[str, Any] = {
            "rpc_method": self.rpc_method,
            "sdk_implementation": self.sdk_implementation,
            "parameters": {
                "required": {
                    "total": self.parameters.get("required", ComparisonMetrics()).total,
                    "supported": self.parameters.get("required", ComparisonMetrics()).supported,
                    "missing": self.parameters.get("required", ComparisonMetrics()).missing,
                },
                "optional": {
                    "total": self.parameters.get("optional", ComparisonMetrics()).total,
                    "supported": self.parameters.get("optional", ComparisonMetrics()).supported,
                    "missing": self.parameters.get("optional", ComparisonMetrics()).missing,
                }
            },
            "status": self.status,
            "notes": self.notes,
            "priority": self.priority
        }

        if self.response_fields is not None:
            result["response_fields"] = {
                "total": self.response_fields.total,
                "supported": self.response_fields.supported,
                "missing": self.response_fields.missing
            }

        return result


class SorobanSDKAnalyzer:
    """Analyze KMP Stellar SDK Soroban implementation"""

    def __init__(self, soroban_server_path: str):
        """Initialize with path to SorobanServer.kt"""
        self.server_path = Path(soroban_server_path)
        self.methods: Dict[str, Dict] = {}
        # Path to request classes directory
        self.requests_dir = self.server_path.parent / "requests"

    def analyze(self) -> Dict[str, Any]:
        """Analyze Soroban SDK implementation"""
        if not self.server_path.exists():
            raise FileNotFoundError(f"Soroban server file not found: {self.server_path}")

        content = self.server_path.read_text(encoding='utf-8')

        self._extract_methods(content)

        return {
            "metadata": {
                "source": str(self.server_path),
                "analyzed_at": datetime.now().isoformat(),
                "total_methods": len(self.methods),
                "sdk_version": get_sdk_version()
            },
            "implemented_methods": self.methods
        }

    def _extract_methods(self, content: str) -> None:
        """Extract implemented RPC methods from Soroban server"""
        # Match suspend fun declarations: suspend fun methodName(...): ResponseType
        pattern = r'suspend\s+fun\s+(\w+)\s*\(([^)]*)\)\s*:\s*(\w+(?:Response)?)'

        for match in re.finditer(pattern, content):
            method_name = match.group(1)
            params_str = match.group(2)
            response_type = match.group(3)

            # Skip private/internal helpers
            if method_name.startswith('_') or method_name in ('call', 'request'):
                continue

            params = self._extract_kotlin_method_params(params_str)
            response_fields = self._extract_response_fields(response_type)

            self.methods[method_name] = {
                "implemented": True,
                "kotlin_method": method_name,
                "response_type": response_type,
                "parameters": params,
                "response_fields": response_fields
            }

    def _extract_response_fields(self, response_type: str) -> List[Dict[str, str]]:
        """Extract fields from a Kotlin response data class.

        Scans the responses/ directory for a file matching the response type name
        and parses its data class constructor parameters as response fields.

        Args:
            response_type: Name of the response class (e.g., 'GetHealthResponse')

        Returns:
            List of field dicts with 'field_name' and 'json_name' keys.
        """
        responses_dir = self.server_path.parent / "responses"
        response_file = responses_dir / f"{response_type}.kt"

        if not response_file.exists():
            return []

        try:
            content = response_file.read_text(encoding="utf-8")
            class_pattern = (
                r'data\s+class\s+' + re.escape(response_type) + r'\s*\((.*?)\)'
            )
            class_match = re.search(class_pattern, content, re.DOTALL)
            if not class_match:
                return []

            fields: List[Dict[str, str]] = []
            body = class_match.group(1)
            # Match val/var declarations: val fieldName: Type
            for field_match in re.finditer(r'(?:val|var)\s+(\w+)\s*:', body):
                field_name = field_match.group(1)
                fields.append({
                    "field_name": field_name,
                    "json_name": field_name
                })
            return fields
        except (IOError, re.error):
            return []

    def _extract_kotlin_method_params(self, params_str: str) -> List[Dict[str, Any]]:
        """Extract parameters from a Kotlin method signature"""
        params: List[Dict[str, Any]] = []

        if not params_str.strip():
            return params

        # Split by comma while respecting generic angle brackets
        param_parts: List[str] = []
        current: List[str] = []
        angle_depth = 0

        for char in params_str + ',':
            if char == '<':
                angle_depth += 1
                current.append(char)
            elif char == '>':
                angle_depth -= 1
                current.append(char)
            elif char == ',' and angle_depth == 0:
                segment = ''.join(current).strip()
                if segment:
                    param_parts.append(segment)
                current = []
            else:
                current.append(char)

        for param_part in param_parts:
            if not param_part:
                continue

            # name: Type or name: Type = default
            param_match = re.match(r'(\w+)\s*:\s*([\w.<>?]+)(?:\s*=\s*.+)?', param_part)
            if not param_match:
                continue

            param_name = param_match.group(1)
            param_type = param_match.group(2)
            is_request_obj = param_type.endswith("Request") and '.' not in param_type

            if is_request_obj:
                request_params = self._parse_request_class(param_type)
                if request_params:
                    params.extend(request_params)
                else:
                    has_default = '=' in param_part
                    params.append({
                        "name": param_name,
                        "type": param_type,
                        "optional": has_default,
                        "supported": True
                    })
            else:
                has_default = '=' in param_part
                params.append({
                    "name": param_name,
                    "type": param_type,
                    "optional": has_default,
                    "supported": True
                })

        return params

    def _parse_request_class(self, request_class_name: str) -> List[Dict[str, Any]]:
        """
        Parse a Kotlin data class file to extract RPC request parameters.

        Args:
            request_class_name: Name of the request class (e.g. GetEventsRequest)

        Returns:
            List of parameter dictionaries, or empty list if class not found.
        """
        request_file = self.requests_dir / f"{request_class_name}.kt"

        if not request_file.exists():
            print(f"  Warning: Request class file not found: {request_file}")
            return []

        try:
            content = request_file.read_text(encoding='utf-8')
            class_pattern = (
                r'data\s+class\s+' + re.escape(request_class_name) + r'\s*\((.*?)\)'
            )
            class_match = re.search(class_pattern, content, re.DOTALL)
            if not class_match:
                print(f"  Warning: Could not find data class definition in {request_file}")
                return []
            return self._parse_kotlin_data_class_properties(class_match.group(1))
        except Exception as exc:
            print(f"  Warning: Error parsing request class {request_class_name}: {exc}")
            return []

    def _parse_kotlin_data_class_properties(self, properties_str: str) -> List[Dict[str, Any]]:
        """
        Parse constructor properties from a Kotlin data class body.

        Args:
            properties_str: Raw constructor body text.

        Returns:
            List of parameter dictionaries with name, type, optional, supported keys.
        """
        params: List[Dict[str, Any]] = []
        prop_pattern = r'val\s+(\w+)\s*:\s*([\w<>?.]+)(?:\s*=\s*(.+?))?(?:,|\s*$)'

        for line in properties_str.split('\n'):
            line = line.strip()
            if not line or line.startswith('//') or line.startswith('*'):
                continue

            prop_match = re.search(prop_pattern, line)
            if prop_match:
                param_name = prop_match.group(1)
                param_type = prop_match.group(2)
                default_value = prop_match.group(3)
                is_optional = param_type.endswith('?') or default_value is not None
                params.append({
                    "name": param_name,
                    "type": param_type,
                    "optional": is_optional,
                    "supported": True
                })

        return params


class RPCComparisonAnalyzer:
    """Main analyzer for comparing the Stellar RPC API with the KMP SDK implementation"""

    # Optional parameters excluded from compatibility checks by design:
    # - xdrFormat: SDK uses XDR exclusively; JSON format is not supported
    IGNORED_OPTIONAL_PARAMS = {"xdrFormat"}

    # Response fields excluded from compatibility checks by design. These are the
    # JSON-format variants the server returns only when the request sets
    # xdrFormat=json; the SDK uses XDR exclusively (see IGNORED_OPTIONAL_PARAMS),
    # so the XDR-variant fields are its supported surface.
    IGNORED_RESPONSE_FIELDS = {
        "errorResultJson",
        "diagnosticEventsJson",
        "transactionDataJson",
        "eventsJson",
    }

    def __init__(self, rpc_data: Dict[str, Any], kotlin_data: Dict[str, Any]):
        """
        Initialize with pre-loaded RPC and SDK data.

        Args:
            rpc_data: Dictionary containing RPC methods data from rpc_parser.
            kotlin_data: Dictionary containing KMP SDK implementation data.
        """
        self.rpc_data = rpc_data
        self.kotlin_data = kotlin_data
        self.comparisons: List[MethodComparison] = []
        self.sdk_version: str = get_sdk_version()
        self._cached_stats: Optional[Dict[str, Any]] = None

        metadata = rpc_data.get("metadata", {})
        self.rpc_version: str = metadata.get("rpc_version", "Unknown")
        self.rpc_release_date: str = metadata.get("rpc_release_date", "Unknown")
        self.rpc_release_url: str = metadata.get("rpc_release_url", "")

    def analyze(self) -> None:
        """Perform complete comparison analysis"""
        self._cached_stats = None
        rpc_methods = self.rpc_data.get("methods", {})
        kotlin_methods = self.kotlin_data.get("implemented_methods", {})

        for method_name, method_data in rpc_methods.items():
            comparison = self._compare_method(
                method_name,
                method_data,
                kotlin_methods.get(method_name)
            )
            self.comparisons.append(comparison)

    def _compare_method(
        self,
        method_name: str,
        rpc_method: Dict[str, Any],
        kotlin_method: Optional[Dict[str, Any]]
    ) -> MethodComparison:
        """Compare a single RPC method against its Kotlin implementation"""
        comparison = MethodComparison(rpc_method=method_name)

        if not kotlin_method or not kotlin_method.get("implemented", False):
            comparison.status = SupportStatus.NOT_SUPPORTED.value
            comparison.priority = self._determine_priority(method_name, None)
            comparison.notes = "Method not implemented in KMP Stellar SDK"
            comparison.sdk_implementation = {
                "implemented": False,
                "kotlin_method": None,
                "response_type": None
            }
            comparison.parameters["required"] = ComparisonMetrics(
                total=len(rpc_method.get("required_params", [])),
                supported=0,
                missing=list(rpc_method.get("required_params", []))
            )
            comparison.parameters["optional"] = ComparisonMetrics(
                total=len(rpc_method.get("optional_params", [])),
                supported=0,
                missing=list(rpc_method.get("optional_params", []))
            )

            rpc_response_fields = rpc_method.get("response_fields", [])
            if rpc_response_fields:
                comparison.response_fields = ComparisonMetrics(
                    total=len(rpc_response_fields),
                    supported=0,
                    missing=[
                        f["json_name"] if isinstance(f, dict) else f
                        for f in rpc_response_fields
                    ]
                )

            return comparison

        comparison.sdk_implementation = {
            "implemented": True,
            "kotlin_method": kotlin_method.get("kotlin_method", method_name),
            "response_type": kotlin_method.get("response_type")
        }

        comparison.parameters = self._compare_parameters(
            rpc_method,
            kotlin_method.get("parameters", [])
        )

        rpc_response_fields = rpc_method.get("response_fields", [])
        if rpc_response_fields:
            comparison.response_fields = self._compare_response_fields(
                rpc_response_fields,
                kotlin_method.get("response_fields", [])
            )

        comparison.status, comparison.notes = self._determine_status(comparison)
        comparison.priority = self._determine_priority(method_name, comparison)

        return comparison

    def _compare_parameters(
        self,
        rpc_method: Dict[str, Any],
        kotlin_params: List[Dict[str, Any]]
    ) -> Dict[str, ComparisonMetrics]:
        """Compare required and optional parameters"""
        result: Dict[str, ComparisonMetrics] = {}
        kotlin_param_names = {p["name"] for p in kotlin_params if p.get("supported", False)}

        for param_type in ("required", "optional"):
            rpc_param_list: List[str] = list(rpc_method.get(f"{param_type}_params", []))

            if param_type == "optional":
                rpc_param_list = [
                    p for p in rpc_param_list if p not in self.IGNORED_OPTIONAL_PARAMS
                ]

            supported = [p for p in rpc_param_list if p in kotlin_param_names]
            missing = [p for p in rpc_param_list if p not in kotlin_param_names]

            result[param_type] = ComparisonMetrics(
                total=len(rpc_param_list),
                supported=len(supported),
                missing=missing
            )

        return result

    def _compare_response_fields(
        self,
        rpc_response_fields: List[Any],
        kotlin_response_fields: List[Any]
    ) -> ComparisonMetrics:
        """
        Compare RPC response fields with SDK response fields.

        Args:
            rpc_response_fields: List of dicts with json_name/field_name keys, or plain strings.
            kotlin_response_fields: List of field names (strings) or dicts from SDK analysis.

        Returns:
            ComparisonMetrics with total, supported, and missing counts.
        """
        def _extract_name(f: Any) -> str:
            return f["json_name"] if isinstance(f, dict) else str(f)

        rpc_names = [
            n for n in (_extract_name(f) for f in rpc_response_fields)
            if n not in self.IGNORED_RESPONSE_FIELDS
        ]
        kotlin_names_lower = {
            (_extract_name(f) if isinstance(f, dict) else str(f)).lower()
            for f in kotlin_response_fields
        }

        supported = [n for n in rpc_names if n.lower() in kotlin_names_lower]
        missing = [n for n in rpc_names if n.lower() not in kotlin_names_lower]

        return ComparisonMetrics(
            total=len(rpc_names),
            supported=len(supported),
            missing=missing
        )

    def _determine_status(self, comparison: MethodComparison) -> Tuple[str, str]:
        """Determine implementation status based on parameter and response field support"""
        required_params = comparison.parameters.get("required", ComparisonMetrics())
        optional_params = comparison.parameters.get("optional", ComparisonMetrics())
        response_fields = comparison.response_fields

        issues: List[str] = []

        if required_params.missing:
            issues.append(f"Missing required parameters: {', '.join(required_params.missing)}")

        if optional_params.missing:
            issues.append(f"Missing optional parameters: {', '.join(optional_params.missing)}")

        if response_fields and response_fields.missing:
            issues.append(f"Missing response fields: {', '.join(response_fields.missing)}")

        if issues:
            return SupportStatus.PARTIALLY_SUPPORTED.value, "; ".join(issues)

        return SupportStatus.FULLY_SUPPORTED.value, "All parameters and response fields implemented"

    def _determine_priority(
        self,
        method_name: str,
        comparison: Optional[MethodComparison]
    ) -> str:
        """Determine priority level for an implementation gap or fix"""
        critical_methods = {
            "sendTransaction", "simulateTransaction", "getTransaction",
            "getLedgerEntries", "getNetwork", "getHealth"
        }
        high_priority_methods = {
            "getEvents", "getLatestLedger", "getFeeStats", "getTransactions", "getLedgers"
        }

        if not comparison or comparison.status == SupportStatus.NOT_SUPPORTED.value:
            if method_name in critical_methods:
                return Priority.CRITICAL.value
            if method_name in high_priority_methods:
                return Priority.HIGH.value
            return Priority.MEDIUM.value

        required_params = comparison.parameters.get("required", ComparisonMetrics())
        if required_params.missing:
            return Priority.CRITICAL.value

        if comparison.status == SupportStatus.PARTIALLY_SUPPORTED.value:
            if method_name in critical_methods:
                return Priority.HIGH.value
            return Priority.MEDIUM.value

        return Priority.LOW.value

    def generate_comparison_data(self) -> Dict[str, Any]:
        """Generate complete comparison data structure"""
        return {
            "metadata": {
                "rpc_methods": len(self.rpc_data.get("methods", {})),
                "sdk_methods": len(self.kotlin_data.get("implemented_methods", {})),
                "comparison_date": datetime.now().isoformat(),
                "coverage_percentage": self._calculate_overall_coverage(),
                "rpc_version": self.rpc_version,
                "rpc_release_date": self.rpc_release_date,
                "rpc_release_url": self.rpc_release_url,
                "sdk_version": self.sdk_version
            },
            "method_comparison": [comp.to_dict() for comp in self.comparisons],
            "gaps": self._generate_gaps_summary()
        }

    def _calculate_overall_coverage(self) -> float:
        """Calculate overall coverage percentage"""
        if not self.comparisons:
            return 0.0
        fully_supported = sum(
            1 for c in self.comparisons
            if c.status == SupportStatus.FULLY_SUPPORTED.value
        )
        return round(fully_supported / len(self.comparisons) * 100, 2)

    def _generate_gaps_summary(self) -> Dict[str, Any]:
        """Generate a summary of gaps and missing features"""
        missing_methods = []
        partial_implementations = []

        for comp in self.comparisons:
            if comp.status == SupportStatus.NOT_SUPPORTED.value:
                missing_methods.append({
                    "method": comp.rpc_method,
                    "priority": comp.priority
                })
            elif comp.status == SupportStatus.PARTIALLY_SUPPORTED.value:
                partial_implementations.append({
                    "method": comp.rpc_method,
                    "priority": comp.priority,
                    "notes": comp.notes
                })

        return {
            "missing_methods": missing_methods,
            "partial_implementations": partial_implementations
        }

    def generate_coverage_stats(self) -> Dict[str, Any]:
        """Generate detailed coverage statistics"""
        if self._cached_stats is not None:
            return self._cached_stats

        total_methods = len(self.comparisons)
        fully_supported = sum(
            1 for c in self.comparisons if c.status == SupportStatus.FULLY_SUPPORTED.value
        )
        partially_supported = sum(
            1 for c in self.comparisons if c.status == SupportStatus.PARTIALLY_SUPPORTED.value
        )
        not_supported = sum(
            1 for c in self.comparisons if c.status == SupportStatus.NOT_SUPPORTED.value
        )

        required_total = sum(
            c.parameters.get("required", ComparisonMetrics()).total
            for c in self.comparisons
        )
        required_supported = sum(
            c.parameters.get("required", ComparisonMetrics()).supported
            for c in self.comparisons
        )

        gaps_by_priority: Dict[str, List[Dict[str, str]]] = {
            "critical": [], "high": [], "medium": [], "low": []
        }
        for comp in self.comparisons:
            if comp.status != SupportStatus.FULLY_SUPPORTED.value:
                priority_key = comp.priority.lower()
                gaps_by_priority[priority_key].append({
                    "method": comp.rpc_method,
                    "status": comp.status,
                    "notes": comp.notes
                })

        self._cached_stats = {
            "overall": {
                "total_methods": total_methods,
                "fully_supported": fully_supported,
                "partially_supported": partially_supported,
                "not_supported": not_supported,
                "coverage_percentage": round(
                    fully_supported / total_methods * 100 if total_methods > 0 else 0, 2
                )
            },
            "parameters": {
                "required_total": required_total,
                "required_supported": required_supported,
                "required_percentage": round(
                    required_supported / required_total * 100 if required_total > 0 else 0, 2
                )
            },
            "gaps_by_priority": gaps_by_priority
        }
        return self._cached_stats

    def generate_markdown_report(self, output_path: str) -> None:
        """Generate markdown compatibility report"""
        stats = self.generate_coverage_stats()
        output_file = Path(output_path)
        output_file.parent.mkdir(parents=True, exist_ok=True)

        with open(output_file, 'w', encoding='utf-8') as f:
            f.write("# Soroban RPC vs KMP Stellar SDK Compatibility Matrix\n\n")

            f.write(f"**RPC Version:** {self.rpc_version}")
            if self.rpc_release_date != "Unknown":
                f.write(f" (released {self.rpc_release_date})")
            f.write("  \n")

            if self.rpc_release_url:
                f.write(
                    f"**RPC Source:** [{self.rpc_release_url}]({self.rpc_release_url})  \n"
                )

            f.write(f"**SDK Version:** {self.sdk_version}  \n")
            f.write(f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")

            # Overall Statistics
            f.write("## Overall Coverage\n\n")
            overall = stats['overall']
            f.write(f"**Coverage:** {overall['coverage_percentage']}%\n\n")
            f.write(
                f"- ✅ **Fully Supported:** {overall['fully_supported']}"
                f"/{overall['total_methods']}\n"
            )
            f.write(
                f"- ⚠️ **Partially Supported:** {overall['partially_supported']}"
                f"/{overall['total_methods']}\n"
            )
            f.write(
                f"- ❌ **Not Supported:** {overall['not_supported']}"
                f"/{overall['total_methods']}\n\n"
            )

            # Method Comparison Table
            f.write("## Method Comparison\n\n")
            f.write(
                "| RPC Method | Status | Kotlin Method | Required Params"
                " | Response Fields | Notes |\n"
            )
            f.write(
                "|------------|--------|---------------|-----------------|"
                "-----------------|-------|\n"
            )

            for comp in sorted(self.comparisons, key=lambda x: x.rpc_method):
                kotlin_method = comp.sdk_implementation.get("kotlin_method", "-")
                required = comp.parameters.get("required", ComparisonMetrics())
                param_status = (
                    f"{required.supported}/{required.total}"
                    if required.total > 0 else "N/A"
                )

                if comp.response_fields:
                    response_status = (
                        f"{comp.response_fields.supported}/{comp.response_fields.total}"
                    )
                else:
                    response_status = "N/A"

                f.write(
                    f"| `{comp.rpc_method}` | {comp.status} | "
                    f"`{kotlin_method}` | {param_status} | {response_status}"
                    f" | {comp.notes} |\n"
                )

            f.write("\n")

            # Response Field Coverage Detail
            response_rows = [
                c for c in sorted(self.comparisons, key=lambda x: x.rpc_method)
                if c.response_fields and c.response_fields.total > 0
            ]
            if response_rows:
                f.write("## Response Field Coverage\n\n")
                f.write("Detailed breakdown of response field support per method.\n\n")
                f.write(
                    "| RPC Method | RPC Fields | SDK Fields | Missing Fields |\n"
                )
                f.write(
                    "|------------|------------|------------|----------------|\n"
                )
                for comp in response_rows:
                    assert comp.response_fields is not None  # narrowing
                    missing_list = (
                        ", ".join(comp.response_fields.missing)
                        if comp.response_fields.missing else "-"
                    )
                    f.write(
                        f"| `{comp.rpc_method}` | {comp.response_fields.total} | "
                        f"{comp.response_fields.supported} | {missing_list} |\n"
                    )
                f.write("\n")

            # Implementation Gaps
            gaps = stats['gaps_by_priority']
            total_gaps = sum(len(gaps[p]) for p in gaps)

            if total_gaps > 0:
                f.write("## Implementation Gaps\n\n")
                for priority in ("critical", "high", "medium", "low"):
                    priority_gaps = gaps[priority]
                    if priority_gaps:
                        icon = {
                            "critical": "🔴", "high": "🟠",
                            "medium": "🟡", "low": "🟢"
                        }[priority]
                        f.write(
                            f"### {icon} {priority.title()} Priority"
                            f" ({len(priority_gaps)} items)\n\n"
                        )
                        for gap in priority_gaps:
                            f.write(f"- `{gap['method']}` - {gap['status']}\n")
                            if gap['notes']:
                                f.write(f"  - {gap['notes']}\n")
                        f.write("\n")

        print(f"  ✓ Markdown report written to {output_path}")


def main() -> int:
    """Main execution function"""
    print("=" * 70)
    print("Stellar RPC API vs KMP Stellar SDK Comparison Generator")
    print("=" * 70)
    print()

    # Paths resolved via shared common module
    rpc_data_dir = DATA_DIR / 'rpc'
    rpc_methods_file = rpc_data_dir / 'rpc_methods.json'
    kmp_implementation_file = rpc_data_dir / 'kmp_soroban_implementation.json'
    comparison_output_file = rpc_data_dir / 'rpc_comparison.json'
    stats_output_file = rpc_data_dir / 'rpc_coverage_stats.json'
    markdown_output_file = COMPATIBILITY_DIR / 'rpc' / 'RPC_COMPATIBILITY_MATRIX.md'

    soroban_server_path = (
        SDK_ROOT
        / "stellar-sdk"
        / "src"
        / "commonMain"
        / "kotlin"
        / "com"
        / "soneso"
        / "stellar"
        / "sdk"
        / "rpc"
        / "SorobanServer.kt"
    )

    try:
        # Stage 1: Load RPC methods (from JSON produced by rpc_parser, or fallback metadata)
        print("Step 1/3: Loading RPC method definitions")
        print("-" * 60)

        rpc_data: Optional[Dict[str, Any]] = None
        if rpc_methods_file.exists():
            try:
                with open(rpc_methods_file, 'r', encoding='utf-8') as f:
                    loaded = json.load(f)
                if "methods" in loaded:
                    rpc_data = loaded
                    print(f"  ✓ Loaded RPC methods from: {rpc_methods_file}")
            except (json.JSONDecodeError, IOError):
                pass

        if rpc_data is None:
            # rpc_methods.json has not been generated yet.
            # Build a minimal stub from the known method names so that
            # the comparison can still run; run rpc_parser.py to get full
            # parameter data fetched from the upstream Go source.
            print(
                "  rpc_methods.json not found — using method-name stubs.\n"
                "  Run rpc_parser.py (or run_rpc_analysis.py) to generate full data."
            )
            stub_methods: Dict[str, Any] = {
                name: {
                    "description": GoProtocolParser._METHOD_DESCRIPTIONS.get(name, ""),
                    "required_params": [],
                    "optional_params": [],
                    "response_fields": []
                }
                for name in GoProtocolParser.METHOD_NAME_MAPPING.values()
            }
            rpc_data = {
                "metadata": {
                    "source": "built-in stubs",
                    "generated_at": datetime.now().isoformat(),
                    "total_methods": len(stub_methods),
                    "rpc_version": "Unknown",
                    "rpc_release_date": "Unknown",
                    "rpc_release_url": ""
                },
                "methods": stub_methods
            }

        # Save RPC methods snapshot
        rpc_data_dir.mkdir(parents=True, exist_ok=True)
        with open(rpc_methods_file, 'w', encoding='utf-8') as f:
            json.dump(rpc_data, f, indent=2, ensure_ascii=False)
        print(f"  ✓ Saved RPC methods to: {rpc_methods_file}")

        # Stage 2: Analyze KMP SDK Soroban implementation
        print()
        print("Step 2/3: Analyzing KMP Soroban implementation")
        print("-" * 60)
        print(f"  Source: {soroban_server_path}")

        soroban_analyzer = SorobanSDKAnalyzer(str(soroban_server_path))
        kotlin_data = soroban_analyzer.analyze()

        with open(kmp_implementation_file, 'w', encoding='utf-8') as f:
            json.dump(kotlin_data, f, indent=2, ensure_ascii=False)
        print(f"  ✓ Saved KMP implementation to: {kmp_implementation_file}")
        print(
            f"  ✓ Detected {kotlin_data['metadata']['total_methods']} implemented methods"
        )

        # Stage 3: Compare and generate outputs
        print()
        print("Step 3/3: Generating comparison and reports")
        print("-" * 60)

        analyzer = RPCComparisonAnalyzer(rpc_data, kotlin_data)
        analyzer.analyze()

        comparison_data = analyzer.generate_comparison_data()
        with open(comparison_output_file, 'w', encoding='utf-8') as f:
            json.dump(comparison_data, f, indent=2, ensure_ascii=False)
        print(f"  ✓ Saved comparison to: {comparison_output_file}")

        coverage_stats = analyzer.generate_coverage_stats()
        with open(stats_output_file, 'w', encoding='utf-8') as f:
            json.dump(coverage_stats, f, indent=2, ensure_ascii=False)
        print(f"  ✓ Saved statistics to: {stats_output_file}")

        analyzer.generate_markdown_report(str(markdown_output_file))

        # Summary
        print()
        print("=" * 70)
        print("SUMMARY")
        print("=" * 70)
        metadata = comparison_data['metadata']
        if metadata['rpc_version'] != "Unknown":
            print(f"RPC Version: {metadata['rpc_version']}")
            if metadata['rpc_release_date'] != "Unknown":
                print(f"RPC Release Date: {metadata['rpc_release_date']}")
        print(f"SDK Version: {metadata['sdk_version']}")
        print()
        print(f"Total RPC Methods: {metadata['rpc_methods']}")
        print(f"SDK Methods: {metadata['sdk_methods']}")
        print(f"Overall Coverage: {metadata['coverage_percentage']}%")
        print()

        overall = coverage_stats['overall']
        print(f"✅ Fully Supported: {overall['fully_supported']}")
        print(f"⚠️  Partially Supported: {overall['partially_supported']}")
        print(f"❌ Not Supported: {overall['not_supported']}")
        print()

        gaps = comparison_data['gaps']
        if gaps['missing_methods']:
            print(f"Missing Methods: {len(gaps['missing_methods'])}")
        if gaps['partial_implementations']:
            print(f"Partial Implementations: {len(gaps['partial_implementations'])}")

        print()
        print("=" * 70)
        print("✓ Comparison completed successfully!")
        print("=" * 70)

        return 0

    except FileNotFoundError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    except json.JSONDecodeError as exc:
        print(f"ERROR: Invalid JSON: {exc}", file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"ERROR: Unexpected error: {exc}", file=sys.stderr)
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())
