#!/usr/bin/env python3
"""
SEP Specification vs KMP Stellar SDK Compatibility Comparison Generator

This script compares SEP specifications with KMP Stellar SDK implementations
and generates detailed compatibility reports, statistics, and markdown documentation.

This is part of a 3-stage SEP compatibility analysis pipeline:
1. sep_parser.py - Parses SEP specs from GitHub -> data/sep/sep_{number}_definition.json
2. sep_analyzer.py - Analyzes SDK Kotlin code -> data/sep/kmp_sep_{number}_implementation.json
3. generate_sep_comparison.py - THIS FILE - Compares both and generates reports

Author: KMP Stellar SDK Team
Date: 2025-10-05
License: Apache-2.0
"""

import json
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Any, Optional
from dataclasses import dataclass
from enum import Enum

sys.path.insert(0, str(Path(__file__).parent.parent))

from common import Colors, DATA_DIR, COMPATIBILITY_DIR, SDK_ROOT, get_sdk_version, snake_to_camel


class CompatibilityStatus(Enum):
    """Compatibility status indicators"""
    IMPLEMENTED = "✅"
    NOT_IMPLEMENTED = "❌"
    PARTIAL = "⚠️"
    SERVER = "Server"


class FieldPriority(Enum):
    """Priority levels for missing fields"""
    CRITICAL = "critical"
    HIGH = "high"
    MEDIUM = "medium"
    LOW = "low"


@dataclass
class FieldComparison:
    """Represents a comparison between a SEP field and SDK implementation"""
    section: str
    sep_field: Dict[str, Any]
    sdk_property: Optional[str]
    status: str
    notes: str
    priority: Optional[str] = None
    server_side_only: bool = False


class SEPCompatibilityGenerator:
    """Main class for generating SEP compatibility matrices"""

    SEP_SUMMARY_OVERRIDES = {
        "0001": "The `stellar.toml` file publishes information about an organization's Stellar integration, including token details, contact info, and validator nodes. Anchors, issuers, and validators host it at their domain so clients can look up and verify their offerings.",
        "0002": "The federation protocol resolves human-readable addresses like `name*yourdomain.com` into Stellar account IDs, so users can share payment details without exchanging raw public keys.",
        "0005": "Defines methods for deriving Stellar keypairs from mnemonic phrases, making it easier to back up and move keys between wallets.",
        "0006": "A programmatic API for anchors and wallets to handle deposits and withdrawals without the user leaving the wallet. For interactive flows, see SEP-24.",
        "0008": "Defines how assets that require per-transaction issuer approval are identified, and the protocol for performing compliance checks before submission.",
        "0009": "A standard list of KYC and financial account fields (names, addresses, ID documents, bank details) used across Stellar ecosystem protocols.",
        "0010": "Lets wallets and exchanges create authenticated web sessions by proving Stellar account ownership. Supports individual, shared, and muxed accounts.",
        "0012": "A standard API for wallets to upload KYC data to anchors. Customers enter their information once and reuse it across multiple services.",
        "0024": "An interactive deposit and withdrawal flow where the anchor controls the UI via a popup within the wallet. Based on SEP-06 but limited to the interactive path.",
        "0030": "Account Recovery: multi-party recovery of Stellar accounts using alternative authentication methods",
        "0038": "Lets anchors provide quotes for exchanging on-chain assets for off-chain assets and vice versa.",
        "0045": "Web authentication for contract accounts (`C...` addresses). Extends SEP-10 to smart contract wallets; services supporting both account types should implement both SEPs.",
        "0053": "Standardizes signing and verification of arbitrary messages using Stellar Ed25519 keypairs, enabling proof-of-ownership for off-chain scenarios without requiring on-chain transactions.",
    }

    def __init__(self, sep_number: str, data_dir: Path, sdk_version: str):
        """
        Initialize the generator.

        Args:
            sep_number: SEP number (e.g., "0001")
            data_dir: Path to data directory
            sdk_version: SDK version from gradle.properties
        """
        self.sep_number = sep_number
        self.data_dir = data_dir
        self.sep_data: Dict[str, Any] = {}
        self.sdk_data: Dict[str, Any] = {}
        self.comparisons: List[FieldComparison] = []
        self.sdk_version = sdk_version
        self._cached_stats: Optional[Dict[str, Any]] = None

    def load_data(self) -> None:
        """Load JSON data from both files"""
        print(f"{Colors.CYAN}Loading SEP-{self.sep_number} definition...{Colors.END}")
        sep_file = self.data_dir / f"sep_{self.sep_number}_definition.json"

        if not sep_file.exists():
            raise FileNotFoundError(
                f"SEP definition file not found: {sep_file}\n"
                f"Please run: sep_parser.py {self.sep_number}"
            )

        with open(sep_file, 'r', encoding='utf-8') as f:
            self.sep_data = json.load(f)

        print(f"{Colors.CYAN}Loading KMP SDK SEP-{self.sep_number} implementation...{Colors.END}")
        sdk_file = self.data_dir / f"kmp_sep_{self.sep_number}_implementation.json"

        if not sdk_file.exists():
            raise FileNotFoundError(
                f"SDK implementation file not found: {sdk_file}\n"
                f"Please run: sep_analyzer.py {self.sep_number}"
            )

        with open(sdk_file, 'r', encoding='utf-8') as f:
            self.sdk_data = json.load(f)

        print(f"{Colors.GREEN}✓ SEP version: {self.sep_data['preamble'].get('version', 'N/A')}{Colors.END}")
        print(f"{Colors.GREEN}✓ SDK implementation: {'Yes' if self.sdk_data['implemented'] else 'No'}{Colors.END}")

    def determine_field_priority(self, field_name: str, required: bool, section_key: str) -> str:
        """
        Determine priority for a missing field.

        Priority assignment:
        - Critical: Required fields in core sections
        - High: Required fields in advanced sections
        - Medium: Optional fields in core sections
        - Low: Optional fields in advanced sections

        Args:
            field_name: Name of the field
            required: Whether the field is required
            section_key: Section key containing the field

        Returns:
            Priority level string
        """
        # Core sections (general information, basic functionality)
        core_sections = ['global', 'general', 'general_information', 'general information']

        is_core_section = section_key.lower().replace('_', ' ') in core_sections

        # Critical: Required fields in core sections
        if required and is_core_section:
            return FieldPriority.CRITICAL.value

        # High: Required fields in advanced sections
        if required and not is_core_section:
            return FieldPriority.HIGH.value

        # Medium: Optional fields in core sections
        if not required and is_core_section:
            return FieldPriority.MEDIUM.value

        # Low: Optional fields in advanced sections
        return FieldPriority.LOW.value

    def apply_sep_specific_field_mapping(self, field_mappings: Dict[str, Dict[str, Optional[str]]]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Apply SEP-specific field mapping logic to enhance the field_mappings from the implementation JSON.

        For SEPs 06, 09, 12, 24, 38, 45, the implementation JSON only contains service_methods mappings.
        This function generates the actual field-to-property mappings by analyzing the definition and SDK data.

        Args:
            field_mappings: Original field mappings from implementation JSON

        Returns:
            Enhanced field mappings with actual field-to-property mappings
        """
        sep_num = self.sep_number

        if sep_num == '0006':
            return self.map_sep_06_definition_fields(field_mappings)
        elif sep_num == '0009':
            return self.map_sep_09_definition_fields(field_mappings)
        elif sep_num == '0012':
            return self.map_sep_12_definition_fields(field_mappings)
        elif sep_num == '0024':
            return self.map_sep_24_definition_fields(field_mappings)
        elif sep_num == '0038':
            return self.map_sep_38_definition_fields(field_mappings)
        elif sep_num == '0045':
            return self.map_sep_45_definition_fields(field_mappings)

        return field_mappings

    def _map_definition_fields_by_property(
        self,
        field_mappings: Dict[str, Dict[str, Optional[str]]],
        *,
        check_methods: bool = False,
    ) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Shared helper: map SEP definition fields to SDK properties (and optionally methods).

        For each section in ``self.sep_data``, converts every ``field_name`` from
        snake_case to camelCase and checks whether a matching property exists in any
        SDK class.  When ``check_methods`` is ``True`` the SDK method names are also
        consulted as a fallback (used by SEP-12).

        Args:
            field_mappings: Original field mappings from the implementation JSON.
            check_methods: When ``True``, also match against SDK class method names.

        Returns:
            A copy of ``field_mappings`` with a ``section_key -> {field_name -> sdk_name}``
            entry added for every section found in the SEP definition.
        """
        enhanced = field_mappings.copy()

        sdk_properties: set[str] = set()
        sdk_methods: set[str] = set()
        for cls in self.sdk_data.get('classes', []):
            for prop in cls.get('properties', []):
                sdk_properties.add(prop['name'])
            if check_methods:
                for method in cls.get('methods', []):
                    sdk_methods.add(method['name'])

        for section in self.sep_data.get('sections', []):
            section_key = section['key']
            section_fields: Dict[str, Optional[str]] = {}

            for field in section.get('fields', []):
                field_name = field['name']
                sdk_property_name = snake_to_camel(field_name)

                if sdk_property_name in sdk_properties:
                    section_fields[field_name] = sdk_property_name
                elif check_methods and sdk_property_name in sdk_methods:
                    section_fields[field_name] = sdk_property_name
                else:
                    section_fields[field_name] = None

            enhanced[section_key] = section_fields

        return enhanced

    def map_sep_06_definition_fields(self, field_mappings: Dict[str, Dict[str, Optional[str]]]) -> Dict[str, Dict[str, Optional[str]]]:
        """Map SEP-06 definition fields to SDK properties."""
        return self._map_definition_fields_by_property(field_mappings)

    def map_sep_09_definition_fields(self, field_mappings: Dict[str, Dict[str, Optional[str]]]) -> Dict[str, Dict[str, Optional[str]]]:
        """Map SEP-09 definition fields to SDK properties"""
        enhanced = field_mappings.copy()

        # Build property map from all KYC field classes
        sdk_properties = set()
        for cls in self.sdk_data.get('classes', []):
            for prop in cls.get('properties', []):
                sdk_properties.add(prop['name'])

        # Map each section
        for section in self.sep_data.get('sections', []):
            section_key = section['key']
            section_fields = {}

            for field in section.get('fields', []):
                field_name = field['name']

                # Strip prefix (organization., card.) if present
                if '.' in field_name:
                    # e.g., "organization.name" -> "name"
                    base_field_name = field_name.split('.', 1)[1]
                else:
                    base_field_name = field_name

                # Explicit overrides for SEP-09 field name mappings
                if base_field_name == 'family_name':
                    sdk_property_name = 'lastName'
                elif base_field_name == 'given_name':
                    sdk_property_name = 'firstName'
                elif base_field_name == 'VAT_number':
                    sdk_property_name = 'VATNumber'
                else:
                    # Convert snake_case to camelCase
                    sdk_property_name = snake_to_camel(base_field_name)

                if sdk_property_name in sdk_properties:
                    section_fields[field_name] = sdk_property_name
                else:
                    section_fields[field_name] = None

            enhanced[section_key] = section_fields

        return enhanced

    def map_sep_12_definition_fields(self, field_mappings: Dict[str, Dict[str, Optional[str]]]) -> Dict[str, Dict[str, Optional[str]]]:
        """Map SEP-12 definition fields to SDK properties and methods."""
        return self._map_definition_fields_by_property(field_mappings, check_methods=True)

    def map_sep_24_definition_fields(self, field_mappings: Dict[str, Dict[str, Optional[str]]]) -> Dict[str, Dict[str, Optional[str]]]:
        """Map SEP-24 definition fields to SDK properties."""
        return self._map_definition_fields_by_property(field_mappings)

    def map_sep_38_definition_fields(self, field_mappings: Dict[str, Dict[str, Optional[str]]]) -> Dict[str, Dict[str, Optional[str]]]:
        """Map SEP-38 definition fields to SDK properties."""
        return self._map_definition_fields_by_property(field_mappings)

    def map_sep_45_definition_fields(self, field_mappings: Dict[str, Dict[str, Optional[str]]]) -> Dict[str, Dict[str, Optional[str]]]:
        """Map SEP-45 definition fields to SDK properties"""
        enhanced = field_mappings.copy()

        # SEP-45 has 6 sections with specific field mappings to SDK implementation
        for section in self.sep_data.get('sections', []):
            section_key = section['key']
            section_fields = {}

            if section_key == 'challenge_request_parameters':
                # Challenge request parameters map to getChallenge() method parameters
                for field in section.get('fields', []):
                    field_name = field['name']
                    if field_name == 'account':
                        section_fields[field_name] = 'clientAccountId'
                    elif field_name == 'home_domain':
                        section_fields[field_name] = 'homeDomain'
                    elif field_name == 'client_domain':
                        section_fields[field_name] = 'clientDomain'
                    else:
                        section_fields[field_name] = None

            elif section_key == 'challenge_response_fields':
                # Challenge response fields map to Sep45ChallengeResponse properties
                for field in section.get('fields', []):
                    field_name = field['name']
                    sdk_property_name = snake_to_camel(field_name)
                    section_fields[field_name] = sdk_property_name

            elif section_key == 'token_request_parameters':
                # Token request parameters map to sendSignedChallenge() method parameters
                for field in section.get('fields', []):
                    field_name = field['name']
                    sdk_property_name = snake_to_camel(field_name)
                    section_fields[field_name] = sdk_property_name

            elif section_key == 'token_response_fields':
                # Token response fields map to Sep45TokenResponse properties
                for field in section.get('fields', []):
                    field_name = field['name']
                    section_fields[field_name] = field_name  # Already camelCase

            elif section_key == 'jwt_claims':
                # JWT claims map to Sep45AuthToken properties
                for field in section.get('fields', []):
                    field_name = field['name']
                    if field_name == 'iss':
                        section_fields[field_name] = 'issuer'
                    elif field_name == 'sub':
                        section_fields[field_name] = 'account'
                    elif field_name == 'iat':
                        section_fields[field_name] = 'issuedAt'
                    elif field_name == 'exp':
                        section_fields[field_name] = 'expiresAt'
                    elif field_name == 'client_domain':
                        section_fields[field_name] = 'clientDomain'
                    else:
                        section_fields[field_name] = None

            elif section_key == 'contract_verification_function_arguments':
                # Contract verification arguments are validated in validateChallenge() and extractArgsFromEntry()
                for field in section.get('fields', []):
                    field_name = field['name']
                    if field_name == 'account':
                        section_fields[field_name] = 'clientAccountId'
                    elif field_name == 'home_domain':
                        section_fields[field_name] = 'homeDomain'
                    elif field_name == 'web_auth_domain':
                        section_fields[field_name] = 'serverHomeDomain'
                    elif field_name == 'web_auth_domain_account':
                        section_fields[field_name] = 'serverSigningKey'
                    elif field_name == 'client_domain':
                        section_fields[field_name] = 'clientDomain'
                    elif field_name == 'client_domain_account':
                        section_fields[field_name] = 'clientDomainAccountId'
                    elif field_name == 'nonce':
                        section_fields[field_name] = 'nonce'
                    else:
                        section_fields[field_name] = None
            else:
                # Unknown section, use generic snake_case to camelCase conversion
                for field in section.get('fields', []):
                    field_name = field['name']
                    sdk_property_name = snake_to_camel(field_name)
                    section_fields[field_name] = sdk_property_name

            enhanced[section_key] = section_fields

        return enhanced

    def compare_fields(self) -> None:
        """Compare all SEP fields with SDK implementation"""
        self._cached_stats = None
        print(f"\n{Colors.CYAN}Comparing fields...{Colors.END}")

        sections = self.sep_data.get('sections', [])
        field_mappings = self.sdk_data.get('field_mappings', {})

        # Apply SEP-specific field mapping enhancement
        field_mappings = self.apply_sep_specific_field_mapping(field_mappings)

        for section in sections:
            section_title = section['title']
            section_key = section['key']
            fields = section.get('fields', [])

            section_mapping = field_mappings.get(section_key, {})

            for field in fields:
                field_name = field['name']
                field_required = field.get('required', False)
                sdk_property = section_mapping.get(field_name)

                # Determine status
                if sdk_property:
                    status = CompatibilityStatus.IMPLEMENTED
                    notes = f"Mapped to `{sdk_property}` property"
                    priority = None
                else:
                    status = CompatibilityStatus.NOT_IMPLEMENTED
                    notes = "Not implemented"
                    priority = self.determine_field_priority(field_name, field_required, section_key)

                comparison = FieldComparison(
                    section=section_title,
                    sep_field=field,
                    sdk_property=sdk_property,
                    status=status.value,
                    notes=notes,
                    priority=priority
                )

                self.comparisons.append(comparison)

        print(f"{Colors.GREEN}✓ Compared {len(self.comparisons)} fields{Colors.END}")

    def calculate_statistics(self) -> Dict[str, Any]:
        """Calculate coverage statistics from comparisons (excluding server-side-only features)"""
        if self._cached_stats is not None:
            return self._cached_stats

        print(f"\n{Colors.CYAN}Calculating statistics...{Colors.END}")

        # Separate client-facing and server-side-only comparisons
        client_comparisons = [c for c in self.comparisons if not c.server_side_only]
        server_side_count = len([c for c in self.comparisons if c.server_side_only])

        total = len(client_comparisons)
        implemented = sum(1 for c in client_comparisons
                         if c.status == CompatibilityStatus.IMPLEMENTED.value)
        not_implemented = total - implemented

        # Separate required and optional field tracking (client-facing only)
        required_total = sum(1 for c in client_comparisons if c.sep_field.get('required', False))
        required_implemented = sum(1 for c in client_comparisons
                                  if c.sep_field.get('required', False) and
                                  c.status == CompatibilityStatus.IMPLEMENTED.value)

        optional_total = sum(1 for c in client_comparisons if not c.sep_field.get('required', False))
        optional_implemented = sum(1 for c in client_comparisons
                                  if not c.sep_field.get('required', False) and
                                  c.status == CompatibilityStatus.IMPLEMENTED.value)

        # Section statistics with required tracking (client-facing only)
        section_stats = {}
        for comp in client_comparisons:
            if comp.section not in section_stats:
                section_stats[comp.section] = {
                    'total': 0,
                    'implemented': 0,
                    'required_total': 0,
                    'required_implemented': 0
                }

            section_stats[comp.section]['total'] += 1
            if comp.status == CompatibilityStatus.IMPLEMENTED.value:
                section_stats[comp.section]['implemented'] += 1

            if comp.sep_field.get('required', False):
                section_stats[comp.section]['required_total'] += 1
                if comp.status == CompatibilityStatus.IMPLEMENTED.value:
                    section_stats[comp.section]['required_implemented'] += 1

        # Calculate percentages
        coverage_percentage = round((implemented / total * 100) if total > 0 else 0, 2)
        required_percentage = round((required_implemented / required_total * 100) if required_total > 0 else 100, 2)
        optional_percentage = round((optional_implemented / optional_total * 100) if optional_total > 0 else 100, 2)

        for section, stats in section_stats.items():
            stats['percentage'] = round(
                (stats['implemented'] / stats['total'] * 100) if stats['total'] > 0 else 0, 2
            )
            stats['required_percentage'] = round(
                (stats['required_implemented'] / stats['required_total'] * 100)
                if stats['required_total'] > 0 else 100, 2
            )

        # Gap analysis by priority (all comparisons including server-side tracked separately)
        gaps_by_priority = {
            FieldPriority.CRITICAL.value: [],
            FieldPriority.HIGH.value: [],
            FieldPriority.MEDIUM.value: [],
            FieldPriority.LOW.value: []
        }

        for comp in self.comparisons:
            if comp.status != CompatibilityStatus.IMPLEMENTED.value and comp.priority:
                gaps_by_priority[comp.priority].append({
                    'field': comp.sep_field['name'],
                    'section': comp.section,
                    'required': comp.sep_field.get('required', False),
                    'description': comp.sep_field.get('description', '')
                })

        self._cached_stats = {
            'overall': {
                'total_fields': total,
                'implemented': implemented,
                'not_implemented': not_implemented,
                'coverage_percentage': coverage_percentage,
                'required_total': required_total,
                'required_implemented': required_implemented,
                'required_percentage': required_percentage,
                'optional_total': optional_total,
                'optional_implemented': optional_implemented,
                'optional_percentage': optional_percentage,
                'server_side_only_count': server_side_count,
                'server_side_note': (
                    f'Excludes {server_side_count} server-side-only feature(s) not applicable to client SDKs'
                    if server_side_count > 0 else None
                )
            },
            'by_section': section_stats,
            'gaps_by_priority': gaps_by_priority
        }
        return self._cached_stats

    def generate_markdown_matrix(self, output_path: Path) -> None:
        """Generate detailed markdown compatibility matrix"""
        print(f"\n{Colors.CYAN}Generating markdown matrix: {output_path}{Colors.END}")

        stats = self.calculate_statistics()
        output_path.parent.mkdir(parents=True, exist_ok=True)

        with open(output_path, 'w', encoding='utf-8') as f:
            preamble = self.sep_data['preamble']

            # Header
            f.write(f"# SEP-{self.sep_number} ({preamble['title']}) Compatibility Matrix\n\n")
            f.write(f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
            f.write(f"**SEP Version:** {preamble.get('version', 'N/A')}  \n")
            f.write(f"**SEP Status:** {preamble['status']}  \n")
            f.write(f"**SDK Version:** {self.sdk_version}  \n")
            f.write(f"**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-{self.sep_number}.md\n\n")

            # SEP Summary
            f.write("## SEP Summary\n\n")
            summary = self.SEP_SUMMARY_OVERRIDES.get(self.sep_number, self.sep_data['summary'])
            f.write(f"{summary}\n\n")

            # Overall Coverage
            overall = stats['overall']
            f.write("## Overall Coverage\n\n")
            f.write(f"**Total Coverage:** {overall['coverage_percentage']}% ({overall['implemented']}/{overall['total_fields']} fields)\n\n")
            f.write(f"- ✅ **Implemented:** {overall['implemented']}/{overall['total_fields']}\n")
            f.write(f"- ❌ **Not Implemented:** {overall['not_implemented']}/{overall['total_fields']}\n\n")

            # Note about server-side exclusions if applicable
            if overall.get('server_side_only_count', 0) > 0:
                f.write(f"_Note: {overall['server_side_note']}_\n\n")

            f.write(f"**Required Fields:** {overall['required_percentage']}% ({overall['required_implemented']}/{overall['required_total']})\n\n")
            f.write(f"**Optional Fields:** {overall['optional_percentage']}% ({overall['optional_implemented']}/{overall['optional_total']})\n\n")

            # Implementation Status
            f.write("## Implementation Status\n\n")
            if self.sdk_data['implemented']:
                f.write("✅ **Fully Implemented**\n\n")
            else:
                f.write("❌ **Not Implemented**\n\n")

            # Implementation Files
            f.write("### Implementation Files\n\n")
            for file in self.sdk_data.get('files', []):
                f.write(f"- `{file}`\n")
            f.write("\n")

            # Key Classes
            f.write("### Key Classes\n\n")
            for cls in self.sdk_data.get('classes', []):
                f.write(f"- **`{cls['name']}`**")
                if cls.get('methods'):
                    method_names = [m['name'] for m in cls['methods']]
                    f.write(f" - Methods: {', '.join(method_names)}")
                f.write("\n")
            f.write("\n")

            # Test Coverage
            test_count = self.sdk_data.get('test_count', 0)
            test_files = self.sdk_data.get('test_files', [])
            if test_count > 0:
                f.write("### Test Coverage\n\n")
                f.write(f"**Tests:** {test_count} test cases\n\n")
                f.write("**Test Files:**\n\n")
                for test_file in test_files:
                    f.write(f"- `{test_file}`\n")
                f.write("\n")

            # Coverage by Section (improved table with required coverage)
            f.write("## Coverage by Section\n\n")
            f.write("| Section | Coverage | Required | Implemented | Total |\n")
            f.write("|---------|----------|----------|-------------|-------|\n")
            for section in sorted(stats['by_section'].keys()):
                section_stats = stats['by_section'][section]
                required_str = f"{section_stats['required_implemented']}/{section_stats['required_total']}" if section_stats['required_total'] > 0 else "N/A"
                f.write(f"| {section} | {section_stats['percentage']}% | "
                       f"{required_str} | "
                       f"{section_stats['implemented']} | {section_stats['total']} |\n")
            f.write("\n")

            # Detailed Field Comparison
            f.write("## Detailed Field Comparison\n\n")

            # Group by section
            by_section = {}
            for comp in self.comparisons:
                if comp.section not in by_section:
                    by_section[comp.section] = []
                by_section[comp.section].append(comp)

            for section in sorted(by_section.keys()):
                f.write(f"### {section}\n\n")
                f.write("| Field | Required | Status | SDK Property | Description |\n")
                f.write("|-------|----------|--------|--------------|-------------|\n")

                for comp in by_section[section]:
                    field = comp.sep_field
                    if comp.server_side_only:
                        required_mark = ""
                        status = CompatibilityStatus.SERVER.value
                        sdk_prop = "N/A"
                        desc = field.get('description', '')
                    else:
                        required_mark = "✓" if field.get('required') else ""
                        status = comp.status
                        sdk_prop = f"`{comp.sdk_property}`" if comp.sdk_property else "-"
                        desc = field.get('description', '')

                    # Truncate description if too long (but not URLs)
                    desc = desc.replace('\n', ' ').strip()
                    if len(desc) > 100 and 'http' not in desc:
                        desc = desc[:97] + "..."

                    f.write(f"| `{field['name']}` | {required_mark} | {status} | "
                           f"{sdk_prop} | {desc} |\n")

                f.write("\n")

            # Implementation Gaps (always shown)
            f.write("## Implementation Gaps\n\n")

            gaps = stats['gaps_by_priority']
            total_gaps = sum(len(gaps[p]) for p in gaps)

            if total_gaps == 0:
                f.write("No gaps found! All fields are implemented.\n\n")
            else:
                for priority in [FieldPriority.CRITICAL.value, FieldPriority.HIGH.value,
                               FieldPriority.MEDIUM.value, FieldPriority.LOW.value]:
                    priority_gaps = gaps[priority]
                    if priority_gaps:
                        icon = {"critical": "🔴", "high": "🟠", "medium": "🟡", "low": "🟢"}
                        f.write(f"### {icon[priority]} {priority.title()} Priority ({len(priority_gaps)} gaps)\n\n")

                        for gap in priority_gaps:
                            required_mark = " (Required)" if gap['required'] else " (Optional)"
                            f.write(f"- **`{gap['field']}`**{required_mark}\n")
                            f.write(f"  - Section: {gap['section']}\n")
                            if gap['description']:
                                desc = gap['description'].replace('\n', ' ').strip()
                                if len(desc) > 200 and 'http' not in desc:
                                    desc = desc[:200] + "..."
                                f.write(f"  - {desc}\n")

                        f.write("\n")

            # Recommendations
            f.write("## Recommendations\n\n")

            if total_gaps > 0:
                critical_gaps = len(gaps[FieldPriority.CRITICAL.value])
                high_gaps = len(gaps[FieldPriority.HIGH.value])

                if critical_gaps > 0:
                    f.write(f"1. **Immediate Action Required**: Implement {critical_gaps} critical field(s)\n")
                if high_gaps > 0:
                    f.write(f"2. **High Priority**: Implement {high_gaps} high-priority field(s)\n")

                if overall['required_percentage'] < 100:
                    missing_required = overall['required_total'] - overall['required_implemented']
                    f.write(f"3. **Required Fields**: Complete implementation of {missing_required} required field(s)\n")
            else:
                f.write(f"The SDK has full compatibility with SEP-{self.sep_number}!\n")

            f.write("\n")

            # Legend
            f.write("## Legend\n\n")
            f.write("- ✅ **Implemented**: Field is fully supported in the SDK\n")
            f.write("- ❌ **Not Implemented**: Field is not currently supported\n")
            f.write("- ⚠️ **Partial**: Field is partially supported with limitations\n")
            f.write("- **Server**: Server-side only feature (not applicable to client SDKs)\n")
            f.write("- ✓ **Required**: Field is required by SEP specification\n\n")

            # Note about server-side features in legend if any exist
            server_side_count = stats['overall'].get('server_side_only_count', 0)
            if server_side_count > 0:
                f.write(f"**Note:** {stats['overall']['server_side_note']}\n\n")

            # Additional Information
            f.write("## Additional Information\n\n")
            f.write("**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference\n\n")
            f.write(f"**Specification:** [SEP-{self.sep_number}](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-{self.sep_number}.md)\n\n")

            # Special case: SEP-53 is implemented in KeyPair class, not a sep53 package
            if self.sep_number == '0053':
                f.write(f"**Implementation Package:** `com.soneso.stellar.sdk.KeyPair`\n")
            else:
                f.write(f"**Implementation Package:** `com.soneso.stellar.sdk.sep.sep{self.sep_number}`\n")

        print(f"{Colors.GREEN}✓ Markdown matrix written to {output_path}{Colors.END}")

    def generate_statistics_report(self, output_path: Path) -> None:
        """Generate statistics JSON report with enhanced coverage stats"""
        print(f"\n{Colors.CYAN}Generating statistics report: {output_path}{Colors.END}")

        stats = self.calculate_statistics()

        report = {
            'generated_at': datetime.now().isoformat(),
            'sep_number': self.sep_number,
            'sep_version': self.sep_data['preamble'].get('version', 'N/A'),
            'sdk_version': self.sdk_version,
            'overall': stats['overall'],
            'by_section': stats['by_section'],
            'gaps_by_priority': stats['gaps_by_priority'],
            'test_coverage': {
                'test_count': self.sdk_data.get('test_count', 0),
                'test_files': self.sdk_data.get('test_files', [])
            }
        }

        output_path.parent.mkdir(parents=True, exist_ok=True)

        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(report, f, indent=2, ensure_ascii=False)

        print(f"{Colors.GREEN}✓ Statistics report written to {output_path}{Colors.END}")

    def print_summary(self) -> None:
        """Print a summary of the comparison results to console with colors"""
        stats = self.calculate_statistics()
        preamble = self.sep_data['preamble']

        print(f"\n{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}")
        print(f"{Colors.BOLD}{Colors.HEADER}SEP-{self.sep_number} COMPATIBILITY SUMMARY{Colors.END}")
        print(f"{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}\n")

        print(f"{Colors.BOLD}SEP Title:{Colors.END} {preamble.get('title', 'N/A')}")
        print(f"{Colors.BOLD}SEP Version:{Colors.END} {preamble.get('version', 'N/A')}")
        print(f"{Colors.BOLD}SEP Status:{Colors.END} {preamble.get('status', 'N/A')}")
        print(f"{Colors.BOLD}SDK Version:{Colors.END} {self.sdk_version}\n")

        overall = stats['overall']
        coverage_color = Colors.GREEN if overall['coverage_percentage'] >= 80 else Colors.YELLOW if overall['coverage_percentage'] >= 50 else Colors.RED

        print(f"{Colors.BOLD}Overall Coverage:{Colors.END} {coverage_color}{overall['coverage_percentage']}%{Colors.END}")
        print(f"  ✅ Implemented:     {overall['implemented']}/{overall['total_fields']}")
        print(f"  ❌ Not Implemented: {overall['not_implemented']}/{overall['total_fields']}\n")

        print(f"{Colors.BOLD}Required Fields:{Colors.END} {overall['required_percentage']}% "
              f"({overall['required_implemented']}/{overall['required_total']})\n")

        print(f"{Colors.BOLD}Section Breakdown:{Colors.END}")
        for section, section_stats in sorted(stats['by_section'].items()):
            color = Colors.GREEN if section_stats['percentage'] >= 80 else Colors.YELLOW if section_stats['percentage'] >= 50 else Colors.RED
            print(f"  {color}{section:35s}: {section_stats['percentage']:5.1f}% "
                  f"({section_stats['implemented']}/{section_stats['total']}){Colors.END}")

        print(f"\n{Colors.BOLD}Implementation Gaps by Priority:{Colors.END}")
        gaps = stats['gaps_by_priority']
        has_gaps = False
        for priority in [FieldPriority.CRITICAL.value, FieldPriority.HIGH.value,
                        FieldPriority.MEDIUM.value, FieldPriority.LOW.value]:
            count = len(gaps[priority])
            if count > 0:
                has_gaps = True
                icon = {"critical": "🔴", "high": "🟠", "medium": "🟡", "low": "🟢"}
                print(f"  {icon[priority]} {priority.upper():10s}: {count} gaps")

        if not has_gaps:
            print(f"  {Colors.GREEN}🎉 No gaps found!{Colors.END}")

        test_count = self.sdk_data.get('test_count', 0)
        if test_count > 0:
            print(f"\n{Colors.BOLD}Test Coverage:{Colors.END} {test_count} test cases")

        print(f"\n{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}\n")


def main():
    """Main entry point for the script"""
    # Disable colors if not running in a TTY
    if not sys.stdout.isatty():
        Colors.disable()

    print(f"{Colors.BOLD}{Colors.HEADER}SEP Compatibility Matrix Generator{Colors.END}")
    print(f"{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}\n")

    # Get SEP number from command line or default to 0001
    if len(sys.argv) < 2:
        sep_number = "0001"
        print(f"{Colors.YELLOW}No SEP number provided, using default: {sep_number}{Colors.END}\n")
    else:
        sep_number = sys.argv[1]

    # Read SDK version via shared utility
    sdk_version = get_sdk_version()

    # Data directory: tools/matrix-generator/data/sep/
    data_dir = DATA_DIR / 'sep'

    # Output paths
    markdown_output = COMPATIBILITY_DIR / 'sep' / f"SEP-{sep_number.upper()}_COMPATIBILITY_MATRIX.md"
    stats_output = DATA_DIR / 'sep' / f"sep_{sep_number}_coverage_stats.json"

    # Create generator
    generator = SEPCompatibilityGenerator(sep_number, data_dir, sdk_version)

    try:
        # Load data (with file existence checks inside)
        generator.load_data()

        # Compare fields
        generator.compare_fields()

        # Generate reports
        generator.generate_markdown_matrix(markdown_output)
        generator.generate_statistics_report(stats_output)

        # Print summary
        generator.print_summary()

        print(f"{Colors.GREEN}✓ Generation complete!{Colors.END}")
        print(f"\n{Colors.BOLD}Output files:{Colors.END}")
        print(f"  - Markdown:   {markdown_output}")
        print(f"  - Statistics: {stats_output}\n")

        return 0

    except FileNotFoundError as e:
        print(f"\n{Colors.RED}❌ ERROR: {str(e)}{Colors.END}\n")
        return 1
    except Exception as e:
        print(f"\n{Colors.RED}❌ ERROR: {str(e)}{Colors.END}")
        import traceback
        traceback.print_exc()
        return 1


if __name__ == '__main__':
    sys.exit(main())
