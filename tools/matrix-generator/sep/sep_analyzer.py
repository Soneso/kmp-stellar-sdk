#!/usr/bin/env python3
"""
KMP Stellar SDK SEP Implementation Analyzer

This script analyzes the Kotlin Multiplatform Stellar SDK codebase to identify SEP
implementations, extract implemented features, and generate field mappings for coverage
comparison with SEP specifications.

This is part of a 3-stage analysis pipeline:
1. sep_parser.py - Parses SEP specifications from GitHub
2. sep_analyzer.py - THIS SCRIPT - Analyzes KMP SDK source code
3. generate_sep_comparison.py - Compares definition vs implementation

Author: KMP Stellar SDK Team
Date: 2026-02-13
License: Apache-2.0
"""

import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))

import json
import re
from datetime import datetime, timezone
from typing import Dict, List, Any, Optional, Set, Tuple

from common import Colors, DATA_DIR, SDK_ROOT, get_sdk_version, snake_to_camel


class SEPAnalyzer:
    """Analyzer for KMP SDK SEP implementations"""

    # Shared runtime behind the XDR-JSON conversion methods (SEP-51)
    XDR_JSON_RUNTIME = 'XdrJson.kt'

    # Generated XDR types that stand in for the SEP-51 source package: one type per
    # distinct JSON form, every Stellar-specific type the specification calls out, and
    # the envelope its worked example uses.
    XDR_JSON_SAMPLE_TYPES = (
        # Data type forms
        'TimeBoundsXdr.kt',
        'AssetXdr.kt',
        'SCValTypeXdr.kt',
        # Address types
        'SCAddressXdr.kt',
        'AccountIDXdr.kt',
        'ContractIDXdr.kt',
        'MuxedAccountXdr.kt',
        'MuxedAccountMed25519Xdr.kt',
        'MuxedEd25519AccountXdr.kt',
        'PoolIDXdr.kt',
        'ClaimableBalanceIDXdr.kt',
        'PublicKeyXdr.kt',
        'NodeIDXdr.kt',
        'SignerKeyXdr.kt',
        'SignerKeyEd25519SignedPayloadXdr.kt',
        # Asset code types
        'AssetCodeXdr.kt',
        'AssetCode4Xdr.kt',
        'AssetCode12Xdr.kt',
        # Integer types
        'UInt128PartsXdr.kt',
        'Int128PartsXdr.kt',
        'UInt256PartsXdr.kt',
        'Int256PartsXdr.kt',
        # Worked example
        'TransactionEnvelopeXdr.kt',
    )

    def __init__(self, sep_number: str):
        """
        Initialize SEP analyzer.

        Args:
            sep_number: SEP number to analyze (e.g., '0001')
        """
        self.sdk_path = SDK_ROOT
        self.sep_number = sep_number.zfill(4)
        # Directory naming uses short form: sep01, sep02, sep45 (not zero-padded)
        sep_dir_name = f'sep{str(int(self.sep_number)).zfill(2)}'
        self.sep_dir = SDK_ROOT / 'stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep' / sep_dir_name
        self.test_dir_unit = SDK_ROOT / 'stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep' / sep_dir_name
        self.test_dir_integration = SDK_ROOT / 'stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/sep' / sep_dir_name
        # Special case: SEP-53 lives in KeyPair.kt, not in sep53/ directory
        self.keypair_file = SDK_ROOT / 'stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/KeyPair.kt'
        # Special case: SEP-46, SEP-47, SEP-48 live in the contract/ directory
        self.contract_dir = SDK_ROOT / 'stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract'
        # Special case: SEP-51 lives in the generated xdr/ package
        self.xdr_dir = SDK_ROOT / 'stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/xdr'
        self._xdr_sources: Dict[str, str] = {}
        self.analysis_data: Dict[str, Any] = {}

    def find_sep_files(self) -> List[Path]:
        """
        Find all source files related to this SEP.

        Returns:
            List of file paths
        """
        files = []

        # Special case: SEP-53 lives in KeyPair.kt
        if self.sep_number == '0053':
            if self.keypair_file.exists():
                files.append(self.keypair_file)
            return sorted(files)

        # Special case: SEP-46, SEP-47, SEP-48 live in the contract/ directory
        if self.sep_number in ('0046', '0047', '0048'):
            if self.contract_dir.exists() and self.contract_dir.is_dir():
                files.extend(self.contract_dir.glob('*.kt'))
            return sorted(files)

        # Special case: SEP-51 has no sep51/ directory. The conversion methods are emitted
        # onto every generated XDR type, so the runtime plus the representative types cover
        # the implementation without listing the whole xdr/ package.
        if self.sep_number == '0051':
            for file_name in (self.XDR_JSON_RUNTIME,) + self.XDR_JSON_SAMPLE_TYPES:
                candidate = self.xdr_dir / file_name
                if candidate.exists():
                    files.append(candidate)
            return sorted(files)

        # Check if SEP directory exists
        if self.sep_dir.exists() and self.sep_dir.is_dir():
            # Find all .kt files in SEP directory
            files.extend(self.sep_dir.glob('*.kt'))
            # Also check for exception subdirectory
            exceptions_dir = self.sep_dir / 'exceptions'
            if exceptions_dir.exists() and exceptions_dir.is_dir():
                files.extend(exceptions_dir.glob('*.kt'))

        return sorted(files)

    def find_test_files(self) -> List[Path]:
        """
        Find all test files related to this SEP.

        Returns:
            List of test file paths
        """
        files = []

        # Special case: SEP-53 test is Sep53Test.kt in unitTests/sep/sep53/
        if self.sep_number == '0053':
            sep53_test_file = SDK_ROOT / 'stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep53/Sep53Test.kt'
            if sep53_test_file.exists():
                files.append(sep53_test_file)
            return sorted(files)

        # Special case: SEP-46, SEP-47, SEP-48 tests live in contract/
        if self.sep_number in ('0046', '0047', '0048'):
            contract_test_dir = SDK_ROOT / 'stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/contract'
            if contract_test_dir.exists() and contract_test_dir.is_dir():
                files.extend(contract_test_dir.glob('*Test.kt'))
            return sorted(files)

        # Check unit tests
        if self.test_dir_unit.exists() and self.test_dir_unit.is_dir():
            files.extend(self.test_dir_unit.glob('*Test.kt'))

        # Check integration tests
        if self.test_dir_integration.exists() and self.test_dir_integration.is_dir():
            files.extend(self.test_dir_integration.glob('*Test.kt'))

        return sorted(files)

    def count_tests(self, test_files: List[Path]) -> int:
        """
        Count total @Test annotations in test files.

        Args:
            test_files: List of test file paths

        Returns:
            Total number of tests
        """
        count = 0
        for file_path in test_files:
            content = file_path.read_text(encoding='utf-8')
            # Count @Test annotations
            count += len(re.findall(r'@Test\b', content))
        return count

    def extract_class_info(self, file_path: Path) -> List[Dict[str, Any]]:
        """
        Extract class information from a Kotlin file.

        Args:
            file_path: Path to Kotlin file

        Returns:
            List of class info dictionaries
        """
        classes = []
        content = file_path.read_text(encoding='utf-8')

        # Find class definitions (class, data class, sealed class, object, enum class).
        # Captures the visibility modifier so the matrix can omit non-public types from
        # the user-facing "Key Classes" section. Internal/private SDK types (e.g., sealed
        # discriminators on top-level functions wrappers) are SDK implementation detail
        # and should not appear as advertised API surface.
        class_pattern = (
            r'(?:^|\n)\s*'
            r'(public\s+|private\s+|internal\s+|protected\s+)?'
            r'(?:data\s+)?(?:sealed\s+)?(?:enum\s+)?'
            r'(class|object)\s+(\w+)'
        )
        matches = re.finditer(class_pattern, content, re.MULTILINE)

        for match in matches:
            visibility_raw = (match.group(1) or '').strip()
            class_type = match.group(2)
            class_name = match.group(3)

            # Skip non-public types. Default visibility in Kotlin is public, so an empty
            # modifier means public at the top level.
            if visibility_raw in ('private', 'internal'):
                continue

            # Skip nested declarations (sealed-class variants, named companion objects,
            # nested types). Nested types inherit visibility from their enclosing class
            # in the eyes of an end user — and the regex cannot determine that visibility
            # cheaply. The conservative rule is: only top-level (column-0) declarations
            # form the user-facing API surface. SEP code in this SDK consistently puts
            # every public type at column 0; companion objects (which are always indented)
            # still have their methods picked up because extract_methods scans the parent
            # class body inclusive of the companion.
            class_kw_start = match.start(2)
            prev_newline = content.rfind('\n', 0, class_kw_start)
            line_start = prev_newline + 1 if prev_newline >= 0 else 0
            prefix_text = content[line_start:class_kw_start]
            # Strip the captured visibility modifier (if any) plus the optional
            # data/sealed/enum keywords (which the outer regex consumes as non-
            # capturing groups). Whatever remains must be pure indentation for
            # the declaration to count as top-level.
            stripped = prefix_text
            visibility_prefix_text = (match.group(1) or '')
            if visibility_prefix_text and stripped.startswith(visibility_prefix_text):
                stripped = stripped[len(visibility_prefix_text):]
            for kw in ('data ', 'sealed ', 'enum '):
                while stripped.lstrip(' \t').startswith(kw):
                    leading_ws_len = len(stripped) - len(stripped.lstrip(' \t'))
                    stripped = stripped[:leading_ws_len] + stripped[leading_ws_len + len(kw):]
            if any(ch not in (' ', '\t') for ch in stripped):
                # Defensive: prefix contains something other than whitespace — unusual; skip.
                continue
            if len(stripped) > 0:
                # Indented declaration → nested → skip.
                continue

            # Skip body-less sealed-class variants like `object ServiceUrl : ErrorTarget()`
            # or `class DirectPaymentServerForDomain(val domain: String) : ErrorTarget()`
            # whose only purpose is to act as a discriminator label.
            #
            # Heuristic:
            # - `object` types CANNOT have a primary constructor, so the only evidence
            #   they're "real" is a `{` body. A `(` that follows is always a supertype
            #   invocation and does not count.
            # - `class` types can have a primary constructor, which IS evidence of "real"
            #   user-facing API (data classes hold their properties there). But a `(`
            #   that appears AFTER the `:` inheritance separator is a supertype call, not
            #   a primary constructor, and does not count.
            # - Either form: `{` body is unconditional evidence the type is "real".
            decl_end = match.end()
            has_evidence = False
            scan_limit = min(len(content), decl_end + 4096)
            i = decl_end
            seen_colon = False
            angle_depth = 0
            while i < scan_limit:
                ch = content[i]
                if ch == '{' and angle_depth == 0:
                    has_evidence = True
                    break
                if ch == '(' and angle_depth == 0 and not seen_colon and class_type == 'class':
                    has_evidence = True
                    break
                if ch == ':' and angle_depth == 0:
                    seen_colon = True
                elif ch == '<':
                    angle_depth += 1
                elif ch == '>':
                    angle_depth = max(0, angle_depth - 1)
                elif angle_depth == 0 and ch not in (
                    ' ', '\t', '\n', '\r', ':', ',', '?', '.', '*', '(', ')',
                ) and not (ch.isalnum() or ch == '_'):
                    break
                i += 1
            if not has_evidence:
                continue

            # SEP-53: Filter out spurious "uses" match from "This class uses" in comments
            if self.sep_number == '0053' and class_name == 'uses':
                continue

            # Extract methods
            methods = self.extract_methods(content, class_name)

            # Extract properties
            properties = self.extract_properties(content, class_name)

            classes.append({
                'name': class_name,
                'type': class_type,
                'file': str(file_path.relative_to(self.sdk_path)),
                'methods': methods,
                'properties': properties
            })

        return classes

    def extract_methods(self, content: str, class_name: str) -> List[Dict[str, Any]]:
        """
        Extract method definitions from class.

        Args:
            content: File content
            class_name: Name of the class

        Returns:
            List of method info dictionaries
        """
        methods = []

        # Find class body
        class_pattern = r'(?:data\s+)?(?:sealed\s+)?(?:enum\s+)?(?:class|object)\s+' + re.escape(class_name) + r'[^{]*\{(.*?)(?=\n(?:data\s+)?(?:sealed\s+)?(?:enum\s+)?(?:class|object)\s+|\Z)'
        class_match = re.search(class_pattern, content, re.DOTALL)

        if not class_match:
            return methods

        class_body = class_match.group(1)

        # Pattern for Kotlin method declarations.
        # Captures the full modifier list so non-public methods can be filtered out of
        # the user-facing "Key Classes" section. The method body itself is still useful
        # to the analyzer's field-mapping logic (which looks for fromJson / toJson by
        # name) but unsupported helpers should not appear as advertised API surface.
        method_pattern = (
            r'(?:^|\n)\s*'
            r'((?:(?:public|private|protected|internal|override|suspend|inline|operator|infix)\s+)*)'
            r'(suspend\s+)?fun\s+(\w+)\s*\(([^)]*)\)'
            r'(?:\s*:\s*([^\{=]+?))?'
            r'(?:\s*[{=])'
        )

        method_matches = re.finditer(method_pattern, class_body, re.MULTILINE)

        seen_methods = set()

        for match in method_matches:
            modifiers = (match.group(1) or '').split()
            is_suspend = match.group(2) is not None
            method_name = match.group(3)
            params_str = match.group(4).strip() if match.group(4) else ""
            return_type = match.group(5).strip() if match.group(5) else "Unit"

            # Skip non-public methods. Default visibility in Kotlin is public, so an
            # empty modifier set means public.
            if 'private' in modifiers or 'internal' in modifiers or 'protected' in modifiers:
                continue

            # Skip constructors
            if method_name == class_name:
                continue

            # Skip private methods (starting with _) - not part of public API
            # Note: Kotlin uses 'private' modifier, not underscore, but we'll be conservative
            if method_name.startswith('_'):
                continue

            # Parse parameters
            params = []
            if params_str:
                # Simple parameter parsing (name: Type)
                param_parts = [p.strip() for p in params_str.split(',') if p.strip()]
                for param in param_parts:
                    # Extract parameter name and type
                    param_match = re.match(r'(\w+)\s*:\s*(.+?)(?:\s*=.*)?$', param)
                    if param_match:
                        params.append(f"{param_match.group(1)}: {param_match.group(2).strip()}")

            # Only add if we haven't seen this method already
            if method_name not in seen_methods:
                methods.append({
                    'name': method_name,
                    'params': params,
                    'return_type': return_type.strip(),
                    'suspend': is_suspend
                })
                seen_methods.add(method_name)

        return methods

    def extract_properties(self, content: str, class_name: str) -> List[Dict[str, Any]]:
        """
        Extract property definitions from class.

        Args:
            content: File content
            class_name: Name of the class

        Returns:
            List of property info dictionaries
        """
        properties = []

        # Find class body
        class_pattern = r'(?:data\s+)?(?:sealed\s+)?(?:enum\s+)?(?:class|object)\s+' + re.escape(class_name) + r'\s*(?:\([^)]*\))?\s*(?::[^{]*)?\s*(?:\{(.*?)(?=\n(?:data\s+)?(?:sealed\s+)?(?:enum\s+)?(?:class|object)\s+|\Z))?'
        class_match = re.search(class_pattern, content, re.DOTALL)

        if not class_match:
            return properties

        # For data classes, parse constructor parameters
        constructor_pattern = r'(?:data\s+)?class\s+' + re.escape(class_name) + r'\s*\((.*)\)$'
        constructor_match = re.search(constructor_pattern, content, re.DOTALL | re.MULTILINE)

        if constructor_match:
            constructor_params = constructor_match.group(1)
            # Parse constructor parameters (val/var propertyName: Type)
            # Simple pattern that works across multi-line constructors with KDocs
            # Matches: val/var propertyName: Type (where Type can include ?, <>, etc.)
            param_pattern = r'\b(?:val|var)\s+(\w+)\s*:\s*(\S+(?:\s*\?)?)'
            param_matches = re.finditer(param_pattern, constructor_params)

            for match in param_matches:
                prop_name = match.group(1)
                prop_type = match.group(2).strip()

                # Determine if nullable
                nullable = prop_type.endswith('?')
                if nullable:
                    prop_type = prop_type[:-1].strip()

                properties.append({
                    'name': prop_name,
                    'type': prop_type,
                    'nullable': nullable
                })

        # Also check for properties in class body (for non-data classes)
        class_body = class_match.group(1) if class_match.group(1) else ""
        if class_body:
            # Match val/var declarations in class body
            prop_pattern = r'(?:^|\n)\s*(?:val|var)\s+(\w+)\s*:\s*([^\n=]+?)(?:\s*=|\s*$)'
            prop_matches = re.finditer(prop_pattern, class_body, re.MULTILINE)

            for match in prop_matches:
                prop_name = match.group(1)
                prop_type = match.group(2).strip()

                # Skip if already added from constructor
                if any(p['name'] == prop_name for p in properties):
                    continue

                # Determine if nullable
                nullable = prop_type.endswith('?')
                if nullable:
                    prop_type = prop_type[:-1].strip()

                properties.append({
                    'name': prop_name,
                    'type': prop_type,
                    'nullable': nullable
                })

        return properties

    def analyze(self) -> Dict[str, Any]:
        """
        Analyze SEP implementation and generate output.

        Returns:
            Analysis results dictionary
        """
        files = self.find_sep_files()
        test_files = self.find_test_files()

        if not files:
            return {
                'implemented': False,
                'reason': f'No SEP-{self.sep_number} implementation files found'
            }

        # Load SEP definition
        sep_def_path = DATA_DIR / 'sep' / f'sep_{self.sep_number}_definition.json'

        sep_definition = {}
        if sep_def_path.exists():
            with open(sep_def_path, 'r', encoding='utf-8') as f:
                sep_definition = json.load(f)

        # Analyze each file
        all_classes = []
        for file_path in files:
            classes = self.extract_class_info(file_path)
            all_classes.extend(classes)

        # Count tests
        test_count = self.count_tests(test_files)

        # Generate field mappings
        field_mappings = self.generate_field_mappings(all_classes, sep_definition)

        # Calculate coverage
        total_fields = sum(len(section_data) for section_data in field_mappings.values())
        implemented_fields = sum(
            1 for section_data in field_mappings.values()
            for field_impl in section_data.values()
            if field_impl is not None
        )

        coverage = f"{round((implemented_fields / total_fields * 100) if total_fields > 0 else 0, 1)}%"

        return {
            'implemented': True,
            'files': [str(f.relative_to(self.sdk_path)) for f in files],
            'test_files': [str(f.relative_to(self.sdk_path)) for f in test_files],
            'test_count': test_count,
            'classes': all_classes,
            'field_mappings': field_mappings,
            'metadata': {
                'generated_at': datetime.now(tz=timezone.utc).isoformat(),
                'sdk_version': get_sdk_version(),
                'total_fields_implemented': implemented_fields,
                'coverage': coverage
            }
        }

    def generate_field_mappings(self, classes: List[Dict[str, Any]],
                                  sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Generate field mappings based on SEP number.

        Dispatches to the appropriate SEP-specific mapper via a dict lookup.
        Falls back to ``map_generic_fields`` for unknown SEP numbers.

        Args:
            classes: List of SDK classes
            sep_definition: SEP specification definition

        Returns:
            Dictionary mapping SEP fields to SDK properties/methods
        """
        mappers = {
            '0001': self.map_sep_01_fields,
            '0002': self.map_sep_02_fields,
            '0005': self.map_sep_05_fields,
            '0006': self.map_sep_06_fields,
            '0008': self.map_sep_08_fields,
            '0009': self.map_sep_09_fields,
            '0010': self.map_sep_10_fields,
            '0012': self.map_sep_12_fields,
            '0024': self.map_sep_24_fields,
            '0030': self.map_sep_30_fields,
            '0031': self.map_sep_31_fields,
            '0038': self.map_sep_38_fields,
            '0045': self.map_sep_45_fields,
            '0046': self.map_sep_46_fields,
            '0047': self.map_sep_47_fields,
            '0048': self.map_sep_48_fields,
            '0051': self.map_sep_51_fields,
            '0053': self.map_sep_53_fields,
        }
        mapper = mappers.get(self.sep_number, self.map_generic_fields)
        return mapper(classes, sep_definition)

    def map_sep_01_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SDK implementation to SEP-01 field requirements (stellar.toml).

        Args:
            classes: List of SDK classes
            sep_definition: SEP specification definition

        Returns:
            Dictionary mapping sections to field implementations
        """
        field_mappings = {}

        # Build class-specific property sets for faster lookup
        class_properties = {}
        for cls in classes:
            props = set()
            for prop in cls.get('properties', []):
                props.add(prop['name'])
            class_properties[cls['name']] = props

        # Map section keys to SDK class names
        section_to_class = {
            'global': 'GeneralInformation',
            'documentation': 'Documentation',
            'principals': 'PointOfContact',
            'currencies': 'Currency',
            'validators': 'Validator'
        }

        # Map to SEP sections
        sections = sep_definition.get('sections', [])

        for section in sections:
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])
            sdk_class_name = section_to_class.get(section_key)

            section_mappings = {}

            for field in sep_fields:
                field_name = field.get('name', '')

                # Convert SEP field name to SDK property name
                sdk_field_name = self.sep_field_to_sdk_property(field_name)

                # Check if implemented in the correct class
                if sdk_class_name and sdk_class_name in class_properties:
                    if sdk_field_name in class_properties[sdk_class_name]:
                        section_mappings[field_name] = sdk_field_name
                    else:
                        section_mappings[field_name] = None
                else:
                    section_mappings[field_name] = None

            field_mappings[section_key] = section_mappings

        return field_mappings

    def sep_field_to_sdk_property(self, sep_field: str) -> str:
        """
        Convert SEP field name to SDK property name (UPPER_SNAKE_CASE or snake_case to camelCase).

        Args:
            sep_field: SEP field name (e.g., 'NETWORK_PASSPHRASE' or 'stellar_address')

        Returns:
            SDK property name (e.g., 'networkPassphrase' or 'stellarAddress')
        """
        # Known special mappings (exact matches)
        mappings = {
            # SEP-01 global fields
            'VERSION': 'version',
            'NETWORK_PASSPHRASE': 'networkPassphrase',
            'FEDERATION_SERVER': 'federationServer',
            'AUTH_SERVER': 'authServer',
            'TRANSFER_SERVER': 'transferServer',
            'TRANSFER_SERVER_SEP0024': 'transferServerSep24',
            'KYC_SERVER': 'kycServer',
            'WEB_AUTH_ENDPOINT': 'webAuthEndpoint',
            'WEB_AUTH_FOR_CONTRACTS_ENDPOINT': 'webAuthForContractsEndpoint',
            'WEB_AUTH_CONTRACT_ID': 'webAuthContractId',
            'SIGNING_KEY': 'signingKey',
            'HORIZON_URL': 'horizonUrl',
            'ACCOUNTS': 'accounts',
            'URI_REQUEST_SIGNING_KEY': 'uriRequestSigningKey',
            'DIRECT_PAYMENT_SERVER': 'directPaymentServer',
            'ANCHOR_QUOTE_SERVER': 'anchorQuoteServer',
            # SEP-01 documentation fields
            'ORG_NAME': 'orgName',
            'ORG_DBA': 'orgDba',
            'ORG_URL': 'orgUrl',
            'ORG_LOGO': 'orgLogo',
            'ORG_DESCRIPTION': 'orgDescription',
            'ORG_PHYSICAL_ADDRESS': 'orgPhysicalAddress',
            'ORG_PHYSICAL_ADDRESS_ATTESTATION': 'orgPhysicalAddressAttestation',
            'ORG_PHONE_NUMBER': 'orgPhoneNumber',
            'ORG_PHONE_NUMBER_ATTESTATION': 'orgPhoneNumberAttestation',
            'ORG_KEYBASE': 'orgKeybase',
            'ORG_TWITTER': 'orgTwitter',
            'ORG_GITHUB': 'orgGithub',
            'ORG_OFFICIAL_EMAIL': 'orgOfficialEmail',
            'ORG_SUPPORT_EMAIL': 'orgSupportEmail',
            'ORG_LICENSING_AUTHORITY': 'orgLicensingAuthority',
            'ORG_LICENSE_TYPE': 'orgLicenseType',
            'ORG_LICENSE_NUMBER': 'orgLicenseNumber',
            # SEP-01 validator fields
            'ALIAS': 'alias',
            'DISPLAY_NAME': 'displayName',
            'PUBLIC_KEY': 'publicKey',
            'HOST': 'host',
            'HISTORY': 'history',
        }

        if sep_field in mappings:
            return mappings[sep_field]

        # Default: convert UPPER_SNAKE_CASE or snake_case to camelCase
        parts = sep_field.lower().split('_')
        return parts[0] + ''.join(p.capitalize() for p in parts[1:])

    def map_sep_02_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SDK implementation to SEP-02 field requirements (Federation Protocol).

        Args:
            classes: List of SDK classes
            sep_definition: SEP specification definition

        Returns:
            Dictionary mapping sections to field implementations
        """
        field_mappings = {}

        # Get all methods from all classes
        all_methods = {}
        for cls in classes:
            for method in cls.get('methods', []):
                all_methods[method['name']] = cls['name']

        # Get all properties from FederationResponse class
        all_properties = {}
        for cls in classes:
            if cls['name'] == 'FederationResponse':
                for prop in cls.get('properties', []):
                    all_properties[prop['name']] = cls['name']

        # Process sections
        sections = sep_definition.get('sections', [])

        for section in sections:
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])

            section_mappings = {}

            if section_key == 'request_parameters':
                # Request parameters (q, type) are handled by service methods
                for field in sep_fields:
                    field_name = field.get('name', '')
                    # These are implicit in method signatures
                    section_mappings[field_name] = f"(handled by service methods)"

            elif section_key == 'request_types':
                # Map request types to service methods
                method_map = {
                    'name': 'resolveStellarAddress',
                    'id': 'resolveAccountId',
                    'txid': 'resolveTransactionId',
                    'forward': 'resolveForward'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_method = method_map.get(field_name)
                    if sdk_method and sdk_method in all_methods:
                        section_mappings[field_name] = sdk_method
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'response_fields':
                # Map response fields to FederationResponse properties
                property_map = {
                    'stellar_address': 'stellarAddress',
                    'account_id': 'accountId',
                    'memo_type': 'memoType',
                    'memo': 'memo'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_property = property_map.get(field_name)
                    if sdk_property and sdk_property in all_properties:
                        section_mappings[field_name] = sdk_property
                    else:
                        section_mappings[field_name] = None

            field_mappings[section_key] = section_mappings

        return field_mappings

    def map_sep_05_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-05 (Key Derivation) fields.

        SEP-05 is about mnemonic generation and HD key derivation.
        The SDK implementation uses:
        - Mnemonic class with static methods
        - MnemonicLanguage enum
        - MnemonicStrength enum
        """
        field_mappings = {}

        # Build method and enum value lookups
        all_methods = {}
        all_enum_values = set()
        all_properties = set()

        for cls in classes:
            for method in cls.get('methods', []):
                all_methods[method['name']] = cls['name']
            for prop in cls.get('properties', []):
                # Enum values might be properties
                all_enum_values.add(prop['name'])
                all_properties.add(prop['name'])

        # Map sections
        sections = sep_definition.get('sections', [])

        for section in sections:
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])
            section_mappings = {}

            if section_key == 'mnemonic_generation':
                # Map function names to methods
                method_map = {
                    'generate_12_word_mnemonic': 'generate12WordsMnemonic',
                    'generate_15_word_mnemonic': 'generate15WordsMnemonic',
                    'generate_18_word_mnemonic': 'generate18WordsMnemonic',
                    'generate_21_word_mnemonic': 'generate21WordsMnemonic',
                    'generate_24_word_mnemonic': 'generate24WordsMnemonic',
                    'generate_mnemonic_with_strength': 'generateMnemonic',
                    'generate_mnemonic_with_language': 'generateMnemonic'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_method = method_map.get(field_name)
                    if sdk_method and sdk_method in all_methods:
                        section_mappings[field_name] = sdk_method
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'mnemonic_validation':
                # Map validation functions
                method_map = {
                    'validate_mnemonic': 'isValidMnemonic',
                    'detect_language': 'detectLanguage',
                    'from_mnemonic': 'from',
                    'validate_checksum': 'isValidMnemonic'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_method = method_map.get(field_name)
                    # isValidMnemonic and detectLanguage are static methods in MnemonicUtils
                    if sdk_method:
                        section_mappings[field_name] = sdk_method
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'language_support':
                # Map language enum values (MnemonicLanguage enum)
                language_map = {
                    'english': 'ENGLISH',
                    'japanese': 'JAPANESE',
                    'korean': 'KOREAN',
                    'spanish': 'SPANISH',
                    'chinese_simplified': 'CHINESE_SIMPLIFIED',
                    'chinese_traditional': 'CHINESE_TRADITIONAL',
                    'french': 'FRENCH',
                    'italian': 'ITALIAN',
                    'malay': 'MALAY'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    enum_value = language_map.get(field_name)
                    # All languages are supported - mark as implemented
                    section_mappings[field_name] = enum_value if enum_value else field_name

            elif section_key == 'bip39_seed_derivation' or section_key == 'bip-39_seed_derivation':
                # Map BIP-39 seed derivation features
                seed_map = {
                    'mnemonic_to_seed': 'from',  # from() method handles mnemonic to seed
                    'passphrase_support': 'from'  # from() supports passphrase parameter
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_method = seed_map.get(field_name)
                    if sdk_method and sdk_method in all_methods:
                        section_mappings[field_name] = sdk_method
                    else:
                        section_mappings[field_name] = sdk_method if sdk_method else None

            elif section_key == 'slip0010_key_derivation' or section_key == 'slip-0010_key_derivation':
                # Map SLIP-0010 HD key derivation features
                # These are implementation details of getKeyPair() method
                slip_map = {
                    'stellar_derivation_path': 'getKeyPair',  # Uses m/44'/148'/x'
                    'hardened_derivation': 'getKeyPair',  # All indices hardened
                    'ed25519_master_key_generation': 'getKeyPair',  # Internal algorithm
                    'ed25519_child_key_derivation': 'getKeyPair'  # Internal algorithm
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_method = slip_map.get(field_name)
                    if sdk_method and sdk_method in all_methods:
                        section_mappings[field_name] = sdk_method
                    else:
                        section_mappings[field_name] = sdk_method if sdk_method else None

            elif section_key == 'key_export':
                # Map key export methods
                export_map = {
                    'get_public_key': 'getKeyPair',  # KeyPair has public key
                    'get_secret_seed': 'getKeyPair',  # KeyPair has secret seed
                    'get_keypair': 'getKeyPair',
                    'get_account_id': 'getKeyPair',  # KeyPair.getAccountId()
                    'get_private_key': 'getKeyPair'  # KeyPair exposes private key via getSecretSeed()
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_method = export_map.get(field_name)
                    if sdk_method:
                        section_mappings[field_name] = sdk_method
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'test_vectors':
                # Test vectors are validation tests, not API features
                # Mark as implementation detail (tested in unit tests)
                for field in sep_fields:
                    field_name = field.get('name', '')
                    section_mappings[field_name] = '(verified in tests)'

            else:
                # Use generic mapping for other sections
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_name = snake_to_camel(field_name)
                    if sdk_name in all_methods or sdk_name in all_enum_values:
                        section_mappings[field_name] = sdk_name
                    else:
                        section_mappings[field_name] = None

            field_mappings[section_key] = section_mappings

        return field_mappings

    def _map_service_methods(
        self,
        classes: List[Dict[str, Any]],
        method_map: Dict[str, str]
    ) -> Dict[str, Optional[str]]:
        """
        Build a ``{field_name: sdk_method_or_None}`` mapping for service-level methods.

        Collects every method name from *classes*, then for each entry in *method_map*
        resolves whether the target SDK method name actually exists in the collected set.

        Args:
            classes: List of class analysis dicts (each has a ``'methods'`` list).
            method_map: Ordered mapping of ``{spec_field_name: sdk_method_name}``.

        Returns:
            Dict where each key is a spec field name and the value is the SDK method
            name when that method was found in *classes*, or ``None`` otherwise.
        """
        all_methods: Dict[str, str] = {}
        for cls in classes:
            for method in cls.get('methods', []):
                all_methods[method['name']] = cls['name']

        return {
            field_name: (method_name if method_name in all_methods else None)
            for field_name, method_name in method_map.items()
        }

    def map_sep_06_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-06 (Deposit and Withdrawal API) fields.

        SEP-06 implementation uses:
        - Sep06Service with deposit/withdraw methods
        - Sep06DepositRequest, Sep06WithdrawRequest
        - Sep06Transaction for transaction status
        """
        # Since SEP-06 definition has 0 fields, create basic method mappings
        service_methods = {
            'info': 'info',
            'deposit': 'deposit',
            'deposit_exchange': 'depositExchange',
            'withdraw': 'withdraw',
            'withdraw_exchange': 'withdrawExchange',
            'transaction': 'transaction',
            'transactions': 'transactions'
        }

        return {'service_methods': self._map_service_methods(classes, service_methods)}

    def _nested_data_class_properties(self, content: str, variant_name: str) -> set:
        """
        Extract the primary-constructor property names of a `data class` whose
        declaration may be nested (indented) inside a sealed class.

        The top-level class parser (extract_class_info) intentionally skips nested
        declarations, so sealed-class variants such as `Success`/`ActionRequired`
        are not in the parsed class list. Their `val`/`var` primary-constructor
        parameters are the SDK's response fields, so parse them directly with a
        balanced-paren scan (avoiding a greedy regex that would run past the
        variant's constructor).
        """
        match = re.search(r'\bdata\s+class\s+' + re.escape(variant_name) + r'\s*\(', content)
        if not match:
            return set()
        depth = 0
        start = match.end()  # first char inside the '('
        i = match.end() - 1  # at the opening '('
        params = None
        while i < len(content):
            ch = content[i]
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    params = content[start:i]
                    break
            i += 1
        if params is None:
            return set()
        return {m.group(1) for m in re.finditer(r'\b(?:val|var)\s+(\w+)\s*:', params)}

    def map_sep_08_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-08 (Regulated Assets) fields.

        SEP-08 implementation uses:
        - Sep08Service with postTransaction and postAction methods
        - Sep08PostTransactionResponse (sealed class: Success, Revised, Pending, ActionRequired, Rejected)
        - Sep08PostActionResponse (sealed class: Done, NextUrl)
        - RegulatedAsset data class
        """
        field_mappings = {}

        # Build lookups
        all_methods = {}
        all_properties = {}
        for cls in classes:
            for method in cls.get('methods', []):
                all_methods[method['name']] = cls['name']
            for prop in cls.get('properties', []):
                if cls['name'] not in all_properties:
                    all_properties[cls['name']] = set()
                all_properties[cls['name']].add(prop['name'])

        # The sealed-class response variants (Success/Revised/Pending/ActionRequired/
        # Rejected and Done/NextUrl) are nested data classes, which the top-level
        # class parser skips. Parse their constructor properties from the response
        # files so the section mappings below match the real SDK surface.
        variant_owners = {
            'Sep08PostTransactionResponse': ('Success', 'Revised', 'Pending', 'ActionRequired', 'Rejected'),
            'Sep08PostActionResponse': ('Done', 'NextUrl'),
        }
        owner_files = {c['name']: c.get('file') for c in classes if c['name'] in variant_owners}
        for owner, variants in variant_owners.items():
            rel_path = owner_files.get(owner)
            if not rel_path:
                continue
            try:
                owner_content = (self.sdk_path / rel_path).read_text(encoding='utf-8')
            except OSError:
                continue
            for variant in variants:
                props = self._nested_data_class_properties(owner_content, variant)
                if props:
                    all_properties.setdefault(variant, set()).update(props)

        # Map sections
        sections = sep_definition.get('sections', [])

        for section in sections:
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])
            section_mappings = {}

            if section_key == 'approval_endpoint':
                # Map to postTransaction method
                for field in sep_fields:
                    field_name = field.get('name', '')
                    if field_name == 'tx_approve':
                        section_mappings[field_name] = 'postTransaction' if 'postTransaction' in all_methods else None
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'request_parameters':
                # Request parameter handled by postTransaction method
                for field in sep_fields:
                    field_name = field.get('name', '')
                    section_mappings[field_name] = '(handled by postTransaction)'

            elif section_key == 'response_statuses':
                # Map to sealed class variants
                status_map = {
                    'success': 'Success',
                    'revised': 'Revised',
                    'pending': 'Pending',
                    'action_required': 'ActionRequired',
                    'rejected': 'Rejected'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    variant = status_map.get(field_name)
                    section_mappings[field_name] = variant if variant else None

            elif section_key == 'success_response_fields':
                # Map to Success sealed class properties
                property_map = {
                    'status': '(implicit)',
                    'tx': 'tx',
                    'message': 'message'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_prop = property_map.get(field_name)
                    if sdk_prop and (sdk_prop == '(implicit)' or
                                   ('Success' in all_properties and sdk_prop in all_properties.get('Success', set()))):
                        section_mappings[field_name] = sdk_prop
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'revised_response_fields':
                # Map to Revised sealed class properties
                property_map = {
                    'status': '(implicit)',
                    'tx': 'tx',
                    'message': 'message'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_prop = property_map.get(field_name)
                    if sdk_prop and (sdk_prop == '(implicit)' or
                                   ('Revised' in all_properties and sdk_prop in all_properties.get('Revised', set()))):
                        section_mappings[field_name] = sdk_prop
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'pending_response_fields':
                # Map to Pending sealed class properties
                property_map = {
                    'status': '(implicit)',
                    'timeout': 'timeout',
                    'message': 'message'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_prop = property_map.get(field_name)
                    if sdk_prop and (sdk_prop == '(implicit)' or
                                   ('Pending' in all_properties and sdk_prop in all_properties.get('Pending', set()))):
                        section_mappings[field_name] = sdk_prop
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'action_required_response_fields':
                # Map to ActionRequired sealed class properties
                property_map = {
                    'status': '(implicit)',
                    'message': 'message',
                    'action_url': 'actionUrl',
                    'action_method': 'actionMethod',
                    'action_fields': 'actionFields'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_prop = property_map.get(field_name)
                    if sdk_prop and (sdk_prop == '(implicit)' or
                                   ('ActionRequired' in all_properties and sdk_prop in all_properties.get('ActionRequired', set()))):
                        section_mappings[field_name] = sdk_prop
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'rejected_response_fields':
                # Map to Rejected sealed class properties
                property_map = {
                    'status': '(implicit)',
                    'error': 'error'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_prop = property_map.get(field_name)
                    if sdk_prop and (sdk_prop == '(implicit)' or
                                   ('Rejected' in all_properties and sdk_prop in all_properties.get('Rejected', set()))):
                        section_mappings[field_name] = sdk_prop
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'action_url_handling':
                # Map to postAction method and response handling
                action_map = {
                    'action_url_get': 'postAction',
                    'action_url_post': 'postAction',
                    'action_url_post_response_no_further_action': 'Done',
                    'action_url_post_response_follow_next_url': 'NextUrl'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_item = action_map.get(field_name)
                    if sdk_item:
                        section_mappings[field_name] = sdk_item
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'stellar_toml_fields':
                # Map to RegulatedAsset properties
                property_map = {
                    'regulated': '(in Currency.regulated)',
                    'approval_server': 'approvalServer',
                    'approval_criteria': 'approvalCriteria'
                }
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_prop = property_map.get(field_name)
                    section_mappings[field_name] = sdk_prop if sdk_prop else None

            elif section_key == 'authorization_flags':
                # Authorization flags are in Stellar core, not SDK-specific
                for field in sep_fields:
                    field_name = field.get('name', '')
                    section_mappings[field_name] = '(Stellar account flags)'

            else:
                # Generic mapping
                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_name = snake_to_camel(field_name)
                    if sdk_name in all_methods:
                        section_mappings[field_name] = sdk_name
                    else:
                        section_mappings[field_name] = None

            field_mappings[section_key] = section_mappings

        return field_mappings

    def map_sep_09_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-09 (Standard KYC Fields) fields.

        SEP-09 implementation uses:
        - StandardKYCFields container class
        - NaturalPersonKYCFields (34 fields: 28 text + 6 binary)
        - OrganizationKYCFields (17 fields: 15 text + 2 binary)
        - FinancialAccountKYCFields (14 fields)
        - CardKYCFields (11 fields)
        """
        field_mappings = {}

        # Build property lookups
        all_properties = {}
        for cls in classes:
            if cls['name'] not in all_properties:
                all_properties[cls['name']] = set()
            for prop in cls.get('properties', []):
                all_properties[cls['name']].add(prop['name'])

        # Since SEP-09 definition has 0 fields (parser issue), create hardcoded mappings
        # based on actual SDK implementation

        # Natural Person fields (28 text + 6 binary = 34 total)
        natural_person_fields = {
            'last_name': 'lastName',
            'first_name': 'firstName',
            'additional_name': 'additionalName',
            'address_country_code': 'addressCountryCode',
            'state_or_province': 'stateOrProvince',
            'city': 'city',
            'postal_code': 'postalCode',
            'address': 'address',
            'mobile_number': 'mobileNumber',
            'mobile_number_format': 'mobileNumberFormat',
            'email_address': 'emailAddress',
            'birth_date': 'birthDate',
            'birth_place': 'birthPlace',
            'birth_country_code': 'birthCountryCode',
            'tax_id': 'taxId',
            'tax_id_name': 'taxIdName',
            'occupation': 'occupation',
            'employer_name': 'employerName',
            'employer_address': 'employerAddress',
            'language_code': 'languageCode',
            'id_type': 'idType',
            'id_country_code': 'idCountryCode',
            'id_issue_date': 'idIssueDate',
            'id_expiration_date': 'idExpirationDate',
            'id_number': 'idNumber',
            'ip_address': 'ipAddress',
            'sex': 'sex',
            'referral_id': 'referralId',
            'photo_id_front': 'photoIdFront',
            'photo_id_back': 'photoIdBack',
            'notary_approval_of_photo_id': 'notaryApprovalOfPhotoId',
            'photo_proof_residence': 'photoProofResidence',
            'proof_of_income': 'proofOfIncome',
            'proof_of_liveness': 'proofOfLiveness'
        }

        # Organization fields (15 text + 2 binary = 17 total, prefixed with "organization.")
        organization_fields = {
            'organization.name': 'name',
            'organization.VAT_number': 'VATNumber',
            'organization.registration_number': 'registrationNumber',
            'organization.registration_date': 'registrationDate',
            'organization.registered_address': 'registeredAddress',
            'organization.number_of_shareholders': 'numberOfShareholders',
            'organization.shareholder_name': 'shareholderName',
            'organization.address_country_code': 'addressCountryCode',
            'organization.state_or_province': 'stateOrProvince',
            'organization.city': 'city',
            'organization.postal_code': 'postalCode',
            'organization.director_name': 'directorName',
            'organization.website': 'website',
            'organization.email': 'email',
            'organization.phone': 'phone',
            'organization.photo_incorporation_doc': 'photoIncorporationDoc',
            'organization.photo_proof_address': 'photoProofAddress'
        }

        # Financial Account fields (14 fields, can be nested in Natural Person or Organization)
        financial_account_fields = {
            'bank_account_number': 'bankAccountNumber',
            'bank_account_type': 'bankAccountType',
            'bank_name': 'bankName',
            'bank_branch_number': 'bankBranchNumber',
            'bank_routing_number': 'bankRoutingNumber',
            'bank_swift_code': 'bankSwiftCode',
            'bank_iban': 'bankIban',
            'bank_code': 'bankCode',
            'clabe_number': 'clabeNumber',
            'cbu_number': 'cbuNumber',
            'cbu_alias': 'cbuAlias',
            'crypto_address': 'cryptoAddress',
            'crypto_memo': 'cryptoMemo',
            'crypto_network': 'cryptoNetwork'
        }

        # Card fields (11 fields, prefixed with "card.")
        card_fields = {
            'card.type': 'type',
            'card.brand': 'brand',
            'card.number': 'number',
            'card.expiration_month': 'expirationMonth',
            'card.expiration_year': 'expirationYear',
            'card.cvv': 'cvv',
            'card.name_on_card': 'nameOnCard',
            'card.billing_address': 'billingAddress',
            'card.billing_city': 'billingCity',
            'card.billing_postal_code': 'billingPostalCode',
            'card.billing_country_code': 'billingCountryCode'
        }

        # Check if properties exist in SDK
        natural_person_props = all_properties.get('NaturalPersonKYCFields', set())
        organization_props = all_properties.get('OrganizationKYCFields', set())
        financial_props = all_properties.get('FinancialAccountKYCFields', set())
        card_props = all_properties.get('CardKYCFields', set())

        # Build field mappings
        natural_person_mappings = {}
        for field_name, prop_name in natural_person_fields.items():
            natural_person_mappings[field_name] = prop_name if prop_name in natural_person_props else None

        organization_mappings = {}
        for field_name, prop_name in organization_fields.items():
            organization_mappings[field_name] = prop_name if prop_name in organization_props else None

        financial_mappings = {}
        for field_name, prop_name in financial_account_fields.items():
            financial_mappings[field_name] = prop_name if prop_name in financial_props else None

        card_mappings = {}
        for field_name, prop_name in card_fields.items():
            card_mappings[field_name] = prop_name if prop_name in card_props else None

        field_mappings['natural_person_fields'] = natural_person_mappings
        field_mappings['organization_fields'] = organization_mappings
        field_mappings['financial_account_fields'] = financial_mappings
        field_mappings['card_fields'] = card_mappings

        return field_mappings

    def map_sep_10_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-10 (Web Authentication) fields to KMP SDK methods.

        The definition produced by parse_sep_10 contains five sections with
        hard-coded feature names.  Each feature is mapped to the WebAuth or
        AuthToken method that implements it.

        KMP SDK classes:
        - WebAuth          - fromDomain, jwtToken, getChallenge, validateChallenge,
                             signTransaction, sendSignedChallenge
        - AuthToken        - parse, isExpired, token, iss, sub, iat, exp, jti,
                             clientDomain, account, memo
        - ChallengeResponse - transaction, networkPassphrase
        """
        # Collect all method names across all SDK classes for existence checks.
        all_methods: set[str] = set()
        for cls in classes:
            for method in cls.get('methods', []):
                all_methods.add(method['name'])

        # ------------------------------------------------------------------
        # Explicit feature -> SDK method/property mappings
        # ------------------------------------------------------------------

        authentication_endpoints_map: Dict[str, str] = {
            'get_auth_challenge': 'getChallenge',
            'post_auth_token': 'sendSignedChallenge',
        }

        challenge_transaction_features_map: Dict[str, str] = {
            # getChallenge fetches the challenge and carries the nonce
            'challenge_transaction_generation': 'getChallenge',
            'nonce_generation': 'getChallenge',
            # validateChallenge enforces all structural requirements
            'transaction_envelope_format': 'validateChallenge',
            'sequence_number_zero': 'validateChallenge',
            'manage_data_operations': 'validateChallenge',
            'home_domain_operation': 'validateChallenge',
            'web_auth_domain_operation': 'validateChallenge',
            'timebounds_enforcement': 'validateChallenge',
            'server_signature': 'validateChallenge',
        }

        client_domain_features_map: Dict[str, str] = {
            # getChallenge passes client_domain as a query parameter
            'client_domain_parameter': 'getChallenge',
            # validateChallenge checks the client_domain ManageData operation
            'client_domain_operation': 'validateChallenge',
            # signTransaction adds the client domain signature
            'client_domain_signature': 'signTransaction',
        }

        jwt_token_features_map: Dict[str, str] = {
            # sendSignedChallenge posts the signed transaction and returns an
            # AuthToken parsed from the JWT response
            'jwt_token_generation': 'sendSignedChallenge',
            'jwt_token_response': 'sendSignedChallenge',
            # AuthToken exposes exp and the full claims set
            'jwt_expiration': 'isExpired',
            'jwt_claims': 'parse',
        }

        verification_features_map: Dict[str, str] = {
            'challenge_validation': 'validateChallenge',
            'signature_verification': 'validateChallenge',
            'home_domain_validation': 'validateChallenge',
            'timebounds_validation': 'validateChallenge',
            # signTransaction accepts a list of signers for multi-sig accounts
            'multi_signature_support': 'signTransaction',
            # getChallenge accepts a memo parameter
            'memo_support': 'getChallenge',
        }

        section_maps: Dict[str, Dict[str, str]] = {
            'authentication_endpoints': authentication_endpoints_map,
            'challenge_transaction_features': challenge_transaction_features_map,
            'client_domain_features': client_domain_features_map,
            'jwt_token_features': jwt_token_features_map,
            'verification_features': verification_features_map,
        }

        field_mappings: Dict[str, Dict[str, Optional[str]]] = {}

        for section in sep_definition.get('sections', []):
            section_key = section.get('key', '')
            feature_map = section_maps.get(section_key, {})
            section_mappings: Dict[str, Optional[str]] = {}

            for field in section.get('fields', []):
                field_name = field.get('name', '')
                sdk_method = feature_map.get(field_name)
                # Verify the mapped method actually exists in the SDK classes.
                if sdk_method and sdk_method in all_methods:
                    section_mappings[field_name] = sdk_method
                else:
                    # Method not found or no mapping defined.
                    section_mappings[field_name] = None

            field_mappings[section_key] = section_mappings

        return field_mappings

    def map_sep_12_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-12 (KYC API) fields.

        SEP-12 implementation uses:
        - KYCService with customer info methods
        - GetCustomerInfoRequest, PutCustomerInfoRequest
        - GetCustomerInfoResponse, PutCustomerInfoResponse
        - CustomerStatus enum
        """
        # Since SEP-12 definition has minimal fields, create basic method mappings
        service_methods = {
            'get_customer': 'getCustomerInfo',
            'put_customer': 'putCustomerInfo',
            'delete_customer': 'deleteCustomer',
            'put_customer_verification': 'putCustomerVerification',
            'put_customer_callback': 'putCustomerCallback',
            'get_customer_files': 'getCustomerFiles'
        }

        return {'service_methods': self._map_service_methods(classes, service_methods)}

    def map_sep_24_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-24 (Hosted Deposit and Withdrawal) fields.

        SEP-24 implementation uses:
        - Sep24Service with interactive deposit/withdraw methods
        - Sep24InteractiveFlowRequest
        - Sep24Transaction for transaction status
        """
        # Since SEP-24 definition has 0 fields, create basic method mappings
        service_methods = {
            'info': 'info',
            'deposit': 'deposit',
            'withdraw': 'withdraw',
            'fee': 'fee',
            'transaction': 'transaction',
            'transactions': 'transactions'
        }

        return {'service_methods': self._map_service_methods(classes, service_methods)}

    def map_sep_38_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-38 (Anchor RFQ) fields.

        SEP-38 implementation uses:
        - QuoteService with quote methods
        - Sep38QuoteRequest, Sep38QuoteResponse
        - Sep38InfoResponse, Sep38PriceResponse
        """
        # Since SEP-38 definition has 0 fields, create basic method mappings
        service_methods = {
            'get_info': 'getInfo',
            'get_prices': 'getPrices',
            'get_price': 'getPrice',
            'post_quote': 'postQuote',
            'get_quote': 'getQuote'
        }

        return {'service_methods': self._map_service_methods(classes, service_methods)}

    @staticmethod
    def _map_sep30_identity_fields(sep_fields: List[Dict[str, Any]]) -> Dict[str, Optional[str]]:
        """
        Map the five SEP-30 identity request fields to their SDK equivalents.

        Both ``register_account`` and ``update_identities`` sections share the same
        five-field identity structure, so this helper centralises that mapping.

        Args:
            sep_fields: List of field dicts from the SEP definition section.

        Returns:
            Mapping of ``{spec_field_name: sdk_property_or_None}``.
        """
        identity_map: Dict[str, Optional[str]] = {
            'identities': 'Sep30Request.identities',
            'identities[].role': 'Sep30RequestIdentity.role',
            'identities[].auth_methods': 'Sep30RequestIdentity.authMethods',
            'identities[].auth_methods[].type': 'Sep30AuthMethod.type',
            'identities[].auth_methods[].value': 'Sep30AuthMethod.value',
        }
        return {
            field.get('name', ''): identity_map.get(field.get('name', ''))
            for field in sep_fields
        }

    def map_sep_30_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-30 (Account Recovery) fields.

        SEP-30 implementation uses:
        - Sep30Service with account registration and signature methods
        - Sep30Request, Sep30RequestIdentity, Sep30AuthMethod
        - Sep30AccountResponse, Sep30ResponseIdentity, Sep30ResponseSigner
        - Sep30SignatureResponse
        - Sep30AccountsResponse
        """
        field_mappings = {}

        # Map sections
        sections = sep_definition.get('sections', [])

        for section in sections:
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])
            section_mappings = {}

            if section_key == 'register_account':
                # Register account request fields
                section_mappings = self._map_sep30_identity_fields(sep_fields)

            elif section_key == 'update_identities':
                # Update identities request fields (same structure as register_account)
                section_mappings = self._map_sep30_identity_fields(sep_fields)

            elif section_key == 'sign_transaction':
                # Sign transaction request
                for field in sep_fields:
                    field_name = field.get('name', '')
                    if field_name == 'transaction':
                        section_mappings[field_name] = 'Sep30Service.signTransaction parameter'
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'account_details_response':
                # Account response fields
                for field in sep_fields:
                    field_name = field.get('name', '')
                    if field_name == 'address':
                        section_mappings[field_name] = 'Sep30AccountResponse.address'
                    elif field_name == 'identities':
                        section_mappings[field_name] = 'Sep30AccountResponse.identities'
                    elif field_name == 'identities[].role':
                        section_mappings[field_name] = 'Sep30ResponseIdentity.role'
                    elif field_name == 'identities[].authenticated':
                        section_mappings[field_name] = 'Sep30ResponseIdentity.authenticated'
                    elif field_name == 'signers':
                        section_mappings[field_name] = 'Sep30AccountResponse.signers'
                    elif field_name == 'signers[].key':
                        section_mappings[field_name] = 'Sep30ResponseSigner.key'
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'signature_response':
                # Signature response fields
                for field in sep_fields:
                    field_name = field.get('name', '')
                    if field_name == 'signature':
                        section_mappings[field_name] = 'Sep30SignatureResponse.signature'
                    elif field_name == 'network_passphrase':
                        section_mappings[field_name] = 'Sep30SignatureResponse.networkPassphrase'
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'list_accounts':
                # List accounts response
                for field in sep_fields:
                    field_name = field.get('name', '')
                    if field_name == 'accounts':
                        section_mappings[field_name] = 'Sep30AccountsResponse.accounts'
                    elif field_name == 'after':
                        section_mappings[field_name] = 'Sep30Service.accounts parameter'
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'authentication_methods':
                # Auth method type constants
                for field in sep_fields:
                    field_name = field.get('name', '')
                    section_mappings[field_name] = 'Sep30AuthMethod type constant'

            elif section_key == 'error_response':
                # Error response
                for field in sep_fields:
                    field_name = field.get('name', '')
                    if field_name == 'error':
                        section_mappings[field_name] = 'Sep30Exception.message'
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'http_error_codes':
                # HTTP error codes map to exception classes
                for field in sep_fields:
                    field_name = field.get('name', '')
                    if field_name == '400 Bad Request':
                        section_mappings[field_name] = 'Sep30BadRequestException'
                    elif field_name == '401 Unauthorized':
                        section_mappings[field_name] = 'Sep30UnauthorizedException'
                    elif field_name == '404 Not Found':
                        section_mappings[field_name] = 'Sep30NotFoundException'
                    elif field_name == '409 Conflict':
                        section_mappings[field_name] = 'Sep30ConflictException'
                    else:
                        section_mappings[field_name] = None

            elif section_key == 'api_endpoints':
                # API endpoints map to service methods
                for field in sep_fields:
                    field_name = field.get('name', '')
                    if field_name == 'POST /accounts/<address>':
                        section_mappings[field_name] = 'Sep30Service.registerAccount'
                    elif field_name == 'PUT /accounts/<address>':
                        section_mappings[field_name] = 'Sep30Service.updateIdentitiesForAccount'
                    elif field_name == 'POST /accounts/<address>/sign/<signing-address>':
                        section_mappings[field_name] = 'Sep30Service.signTransaction'
                    elif field_name == 'GET /accounts/<address>':
                        section_mappings[field_name] = 'Sep30Service.accountDetails'
                    elif field_name == 'DELETE /accounts/<address>':
                        section_mappings[field_name] = 'Sep30Service.deleteAccount'
                    elif field_name == 'GET /accounts':
                        section_mappings[field_name] = 'Sep30Service.accounts'
                    else:
                        section_mappings[field_name] = None

            else:
                # Unknown section
                for field in sep_fields:
                    section_mappings[field.get('name', '')] = None

            field_mappings[section_key] = section_mappings

        return field_mappings

    def map_sep_31_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-31 (Cross-Border Payments) fields.

        SEP-31 implementation uses:
        - Sep31Service exposing info, postTransactions, getTransaction,
          patchTransaction, putTransactionCallback (plus fromDomain factory)
        - Sep31InfoResponse with receiveAssets map keyed by asset code
        - Sep31ReceiveAssetInfo for per-asset configuration
        - Sep31Sep12TypesInfo for SEP-12 sender/receiver customer types
        - Sep31PostTransactionsRequest / Sep31PostTransactionsResponse
        - Sep31TransactionResponse with embedded Sep31Refunds, Sep31RefundPayment,
          Sep31FeeDetails, Sep31FeeDetailsDetails

        Each spec field is explicitly mapped to the actual Kotlin property name
        on the appropriate class. Endpoints are mapped to the Sep31Service
        suspend method that implements the request.
        """
        endpoint_map: Dict[str, Optional[str]] = {
            'GET /info': 'Sep31Service.info',
            'POST /transactions': 'Sep31Service.postTransactions',
            'GET /transactions/:id': 'Sep31Service.getTransaction',
            'PATCH /transactions/:id': 'Sep31Service.patchTransaction',
            'PUT /transactions/:id/callback': 'Sep31Service.putTransactionCallback',
        }

        info_response_map: Dict[str, Optional[str]] = {
            'receive': 'Sep31InfoResponse.receiveAssets',
        }

        receive_asset_info_map: Dict[str, Optional[str]] = {
            'sep12': 'Sep31ReceiveAssetInfo.sep12Info',
            'min_amount': 'Sep31ReceiveAssetInfo.minAmount',
            'max_amount': 'Sep31ReceiveAssetInfo.maxAmount',
            'fee_fixed': 'Sep31ReceiveAssetInfo.feeFixed',
            'fee_percent': 'Sep31ReceiveAssetInfo.feePercent',
            'sender_sep12_type': 'Sep31ReceiveAssetInfo.senderSep12Type',
            'receiver_sep12_type': 'Sep31ReceiveAssetInfo.receiverSep12Type',
            'fields': 'Sep31ReceiveAssetInfo.fields',
            'quotes_supported': 'Sep31ReceiveAssetInfo.quotesSupported',
            'quotes_required': 'Sep31ReceiveAssetInfo.quotesRequired',
            'funding_methods': 'Sep31ReceiveAssetInfo.fundingMethods',
        }

        sep12_types_info_map: Dict[str, Optional[str]] = {
            'sender': 'Sep31Sep12TypesInfo.senderTypes',
            'receiver': 'Sep31Sep12TypesInfo.receiverTypes',
        }

        post_transactions_request_map: Dict[str, Optional[str]] = {
            'amount': 'Sep31PostTransactionsRequest.amount',
            'asset_code': 'Sep31PostTransactionsRequest.assetCode',
            'asset_issuer': 'Sep31PostTransactionsRequest.assetIssuer',
            'destination_asset': 'Sep31PostTransactionsRequest.destinationAsset',
            'quote_id': 'Sep31PostTransactionsRequest.quoteId',
            'sender_id': 'Sep31PostTransactionsRequest.senderId',
            'receiver_id': 'Sep31PostTransactionsRequest.receiverId',
            'fields': 'Sep31PostTransactionsRequest.fields',
            'lang': 'Sep31PostTransactionsRequest.lang',
            'refund_memo': 'Sep31PostTransactionsRequest.refundMemo',
            'refund_memo_type': 'Sep31PostTransactionsRequest.refundMemoType',
            'funding_method': 'Sep31PostTransactionsRequest.fundingMethod',
        }

        post_transactions_response_map: Dict[str, Optional[str]] = {
            'id': 'Sep31PostTransactionsResponse.id',
            'stellar_account_id': 'Sep31PostTransactionsResponse.stellarAccountId',
            'stellar_memo_type': 'Sep31PostTransactionsResponse.stellarMemoType',
            'stellar_memo': 'Sep31PostTransactionsResponse.stellarMemo',
        }

        transaction_response_map: Dict[str, Optional[str]] = {
            'id': 'Sep31TransactionResponse.id',
            'status': 'Sep31TransactionResponse.status',
            'status_eta': 'Sep31TransactionResponse.statusEta',
            'status_message': 'Sep31TransactionResponse.statusMessage',
            'amount_in': 'Sep31TransactionResponse.amountIn',
            'amount_in_asset': 'Sep31TransactionResponse.amountInAsset',
            'amount_out': 'Sep31TransactionResponse.amountOut',
            'amount_out_asset': 'Sep31TransactionResponse.amountOutAsset',
            'amount_fee': 'Sep31TransactionResponse.amountFee',
            'amount_fee_asset': 'Sep31TransactionResponse.amountFeeAsset',
            'fee_details': 'Sep31TransactionResponse.feeDetails',
            'quote_id': 'Sep31TransactionResponse.quoteId',
            'stellar_account_id': 'Sep31TransactionResponse.stellarAccountId',
            'stellar_memo_type': 'Sep31TransactionResponse.stellarMemoType',
            'stellar_memo': 'Sep31TransactionResponse.stellarMemo',
            'started_at': 'Sep31TransactionResponse.startedAt',
            'updated_at': 'Sep31TransactionResponse.updatedAt',
            'completed_at': 'Sep31TransactionResponse.completedAt',
            'stellar_transaction_id': 'Sep31TransactionResponse.stellarTransactionId',
            'external_transaction_id': 'Sep31TransactionResponse.externalTransactionId',
            'refunded': 'Sep31TransactionResponse.refunded',
            'refunds': 'Sep31TransactionResponse.refunds',
            'required_info_message': 'Sep31TransactionResponse.requiredInfoMessage',
            'required_info_updates': 'Sep31TransactionResponse.requiredInfoUpdates',
        }

        refunds_map: Dict[str, Optional[str]] = {
            'amount_refunded': 'Sep31Refunds.amountRefunded',
            'amount_fee': 'Sep31Refunds.amountFee',
            'payments': 'Sep31Refunds.payments',
        }

        refund_payment_map: Dict[str, Optional[str]] = {
            'id': 'Sep31RefundPayment.id',
            'amount': 'Sep31RefundPayment.amount',
            'fee': 'Sep31RefundPayment.fee',
        }

        fee_details_map: Dict[str, Optional[str]] = {
            'total': 'Sep31FeeDetails.total',
            'asset': 'Sep31FeeDetails.asset',
            'details': 'Sep31FeeDetails.details',
        }

        fee_details_breakdown_map: Dict[str, Optional[str]] = {
            'name': 'Sep31FeeDetailsDetails.name',
            'amount': 'Sep31FeeDetailsDetails.amount',
            'description': 'Sep31FeeDetailsDetails.description',
        }

        section_maps: Dict[str, Dict[str, Optional[str]]] = {
            'service_endpoints': endpoint_map,
            'info_response_fields': info_response_map,
            'receive_asset_info_fields': receive_asset_info_map,
            'sep12_types_info_fields': sep12_types_info_map,
            'post_transactions_request_fields': post_transactions_request_map,
            'post_transactions_response_fields': post_transactions_response_map,
            'transaction_response_fields': transaction_response_map,
            'refunds_fields': refunds_map,
            'refund_payment_fields': refund_payment_map,
            'fee_details_fields': fee_details_map,
            'fee_details_breakdown_fields': fee_details_breakdown_map,
        }

        field_mappings: Dict[str, Dict[str, Optional[str]]] = {}
        for section in sep_definition.get('sections', []):
            section_key = section.get('key', '')
            section_map = section_maps.get(section_key, {})
            section_mappings: Dict[str, Optional[str]] = {}
            for field in section.get('fields', []):
                field_name = field.get('name', '')
                section_mappings[field_name] = section_map.get(field_name)
            field_mappings[section_key] = section_mappings

        return field_mappings

    def map_sep_45_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-45 (Web Auth for Contract Accounts) fields.

        SEP-45 implementation uses:
        - WebAuthForContracts class (similar to SEP-10 WebAuth but for C... accounts)
        - Sep45ChallengeResponse, Sep45AuthToken
        - Sep45TokenResponse
        """
        field_mappings = {}

        # Build lookups
        all_methods = {}
        for cls in classes:
            for method in cls.get('methods', []):
                all_methods[method['name']] = cls['name']

        # Since SEP-45 definition has 0 fields, create basic method mappings
        # SEP-45 is similar to SEP-10 but for contract accounts
        service_methods = {
            'get_challenge': 'getChallenge',
            'send_signed_challenge': 'sendSignedChallenge',
            'jwt_token': 'jwtToken',
            'validate_challenge': 'validateChallenge',
            'sign_transaction': 'signTransaction',
            'from_domain': 'fromDomain'
        }

        service_mappings = {}
        for field_name, method_name in service_methods.items():
            service_mappings[field_name] = method_name if method_name in all_methods else None

        field_mappings['service_methods'] = service_mappings

        return field_mappings

    def _contract_parser_present(self) -> bool:
        """
        Check whether SorobanContractParser.kt is present in the contract/ directory.

        Returns:
            True if the file exists, False otherwise.
        """
        parser_file = self.contract_dir / 'SorobanContractParser.kt'
        return parser_file.exists()

    def map_sep_46_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-46 (Contract Meta) fields.

        SEP-46 implementation lives in SorobanContractParser.kt inside the contract/ directory.
        - parseMeta (private) handles contractmetav0_section, multiple_entries_single_section,
          multiple_sections, binary_stream_encoding, key_value_pairs
        - SCMetaEntryXdr is the XDR type used (scmetaentry_xdr)
        - metaEntries property on SorobanContractInfo exposes the parsed key-value map
        - parseContractByteCode is the public entry point
        """
        present = self._contract_parser_present()

        metadata_storage_map: Dict[str, Optional[str]] = {
            'contractmetav0_section': 'parseMeta' if present else None,
            'multiple_entries_single_section': 'parseMeta' if present else None,
            'multiple_sections': 'parseMeta' if present else None,
        }

        encoding_format_map: Dict[str, Optional[str]] = {
            'scmetaentry_xdr': 'SCMetaEntryXdr' if present else None,
            'binary_stream_encoding': 'parseMeta' if present else None,
            'key_value_pairs': 'metaEntries' if present else None,
        }

        implementation_support_map: Dict[str, Optional[str]] = {
            'parse_contract_meta': 'parseContractByteCode' if present else None,
            'extract_meta_entries': 'metaEntries' if present else None,
            'decode_scmetaentry': 'SCMetaEntryXdr' if present else None,
        }

        field_mappings: Dict[str, Dict[str, Optional[str]]] = {}

        for section in sep_definition.get('sections', []):
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])
            section_map = {
                'metadata_storage': metadata_storage_map,
                'encoding_format': encoding_format_map,
                'implementation_support': implementation_support_map,
            }.get(section_key, {})

            section_mappings: Dict[str, Optional[str]] = {}
            for field in sep_fields:
                field_name = field.get('name', '')
                section_mappings[field_name] = section_map.get(field_name)

            field_mappings[section_key] = section_mappings

        return field_mappings

    def map_sep_47_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-47 (Contract Interface Discovery) fields.

        SEP-47 implementation lives in SorobanContractParser.kt / SorobanContractInfo.
        - supportedSeps computed property on SorobanContractInfo handles all features:
          * reads metaEntries["sep"]
          * splits on comma
          * trims whitespace
          * filters empty strings
          * deduplicates via distinct()
        """
        present = self._contract_parser_present()

        sep_declaration_map: Dict[str, Optional[str]] = {
            'sep_meta_key': 'supportedSeps' if present else None,
            'comma_separated_list': 'supportedSeps' if present else None,
            'multiple_sep_entries': 'supportedSeps' if present else None,
        }

        meta_entry_format_map: Dict[str, Optional[str]] = {
            'sep_number_format': 'supportedSeps' if present else None,
            'whitespace_handling': 'supportedSeps' if present else None,
            'empty_value_handling': 'supportedSeps' if present else None,
        }

        implementation_support_map: Dict[str, Optional[str]] = {
            'parse_supported_seps': 'supportedSeps' if present else None,
            'expose_supported_seps': 'supportedSeps' if present else None,
            'validate_sep_format': 'supportedSeps' if present else None,
        }

        field_mappings: Dict[str, Dict[str, Optional[str]]] = {}

        for section in sep_definition.get('sections', []):
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])
            section_map = {
                'sep_declaration': sep_declaration_map,
                'meta_entry_format': meta_entry_format_map,
                'implementation_support': implementation_support_map,
            }.get(section_key, {})

            section_mappings: Dict[str, Optional[str]] = {}
            for field in sep_fields:
                field_name = field.get('name', '')
                section_mappings[field_name] = section_map.get(field_name)

            field_mappings[section_key] = section_mappings

        return field_mappings

    def map_sep_48_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-48 (Contract Interface Specification) fields.

        SEP-48 implementation lives in SorobanContractParser.kt / SorobanContractInfo.
        - parseContractByteCode: main entry point
        - parseContractSpec: parses all 6 spec entry kinds
        - parseEnvironmentMeta: parses env metadata / interface version
        - parseMeta: parses contract metadata
        - specEntries, funcs, udtStructs, udtUnions, udtEnums, udtErrorEnums, events: typed accessors
        - XDR types: SCSpecEntryXdr, SCSpecTypeDefXdr, SCEnvMetaEntryXdr, SCMetaEntryXdr
        - Compound types: SCSpecTypeOptionXdr, SCSpecTypeResultXdr, SCSpecTypeVecXdr,
          SCSpecTypeMapXdr, SCSpecTypeTupleXdr, SCSpecTypeBytesNXdr, SCSpecTypeUDTXdr
        """
        present = self._contract_parser_present()

        wasm_section_map: Dict[str, Optional[str]] = {
            'contractspecv0_section': 'parseContractByteCode' if present else None,
            'contractenvmetav0_section': 'parseContractByteCode' if present else None,
            'contractmetav0_section': 'parseContractByteCode' if present else None,
            'xdr_binary_encoding': 'SCSpecEntryXdr' if present else None,
        }

        entry_types_map: Dict[str, Optional[str]] = {
            'function_specs': 'funcs' if present else None,
            'struct_specs': 'udtStructs' if present else None,
            'union_specs': 'udtUnions' if present else None,
            'enum_specs': 'udtEnums' if present else None,
            'error_enum_specs': 'udtErrorEnums' if present else None,
            'event_specs': 'events' if present else None,
        }

        type_system_primitive_map: Dict[str, Optional[str]] = {
            'boolean_type': 'SCSpecTypeDefXdr' if present else None,
            'void_type': 'SCSpecTypeDefXdr' if present else None,
            'numeric_types': 'SCSpecTypeDefXdr' if present else None,
            'timepoint_duration': 'SCSpecTypeDefXdr' if present else None,
            'bytes_string_symbol': 'SCSpecTypeDefXdr' if present else None,
            'address_type': 'SCSpecTypeDefXdr' if present else None,
        }

        type_system_compound_map: Dict[str, Optional[str]] = {
            'option_type': 'SCSpecTypeOptionXdr' if present else None,
            'result_type': 'SCSpecTypeResultXdr' if present else None,
            'vector_type': 'SCSpecTypeVecXdr' if present else None,
            'map_type': 'SCSpecTypeMapXdr' if present else None,
            'tuple_type': 'SCSpecTypeTupleXdr' if present else None,
            'bytes_n_type': 'SCSpecTypeBytesNXdr' if present else None,
            'user_defined_type': 'SCSpecTypeUDTXdr' if present else None,
        }

        parsing_support_map: Dict[str, Optional[str]] = {
            'parse_contract_bytecode': 'parseContractByteCode' if present else None,
            'extract_spec_entries': 'specEntries' if present else None,
            'parse_environment_meta': 'parseContractByteCode' if present else None,
            'parse_contract_meta': 'parseContractByteCode' if present else None,
        }

        xdr_support_map: Dict[str, Optional[str]] = {
            'decode_scspecentry': 'SCSpecEntryXdr' if present else None,
            'decode_scspectypedef': 'SCSpecTypeDefXdr' if present else None,
            'decode_scenvmetaentry': 'SCEnvMetaEntryXdr' if present else None,
            'decode_scmetaentry': 'SCMetaEntryXdr' if present else None,
        }

        field_mappings: Dict[str, Dict[str, Optional[str]]] = {}

        section_dispatch: Dict[str, Dict[str, Optional[str]]] = {
            'wasm_section': wasm_section_map,
            'entry_types': entry_types_map,
            'type_system_primitive': type_system_primitive_map,
            'type_system_compound': type_system_compound_map,
            'parsing_support': parsing_support_map,
            'xdr_support': xdr_support_map,
        }

        for section in sep_definition.get('sections', []):
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])
            section_map = section_dispatch.get(section_key, {})

            section_mappings: Dict[str, Optional[str]] = {}
            for field in sep_fields:
                field_name = field.get('name', '')
                section_mappings[field_name] = section_map.get(field_name)

            field_mappings[section_key] = section_mappings

        return field_mappings

    def _xdr_source_contains(self, file_name: str, token: str) -> bool:
        """
        Check a file in the generated xdr/ package for a literal.

        Args:
            file_name: File name inside the xdr/ package (e.g. 'AssetXdr.kt')
            token: Source text that proves the mapping is in place

        Returns:
            True if the file exists and contains the token
        """
        if file_name not in self._xdr_sources:
            path = self.xdr_dir / file_name
            self._xdr_sources[file_name] = (
                path.read_text(encoding='utf-8') if path.exists() else ''
            )
        return token in self._xdr_sources[file_name]

    def map_sep_51_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-51 (XDR-JSON) fields.

        SEP-51 has no sep51/ source package: toXdrJson, toXdrJsonElement, fromXdrJson and
        fromXdrJsonElement are emitted onto every generated XDR type and delegate to the
        XdrJson.kt runtime. Every requirement is therefore mapped to the symbol that carries
        it, and counts as implemented only while that symbol is present in the source.
        """
        # field name -> (SDK symbol, file in the xdr/ package, literal proving the mapping)
        evidence: Dict[str, Dict[str, Tuple[str, str, str]]] = {
            'xdr_data_types': {
                'integer_32': ('XdrJson.int32', 'XdrJson.kt', 'fun int32(value: Int)'),
                'unsigned_integer_32': ('XdrJson.uint32', 'XdrJson.kt', 'fun uint32(value: UInt)'),
                'hyper_integer_64': ('XdrJson.int64', 'XdrJson.kt', 'fun int64(value: Long)'),
                'unsigned_hyper_integer_64': ('XdrJson.uint64', 'XdrJson.kt', 'fun uint64(value: ULong)'),
                'boolean': ('XdrJson.bool', 'XdrJson.kt', 'fun bool(value: Boolean)'),
                'opaque_fixed_length': ('XdrJson.hex', 'XdrJson.kt', 'fun hex(bytes: ByteArray)'),
                'opaque_variable_length': ('XdrJson.hex', 'XdrJson.kt', 'fun hex(bytes: ByteArray)'),
                'string': ('XdrJson.escapedString', 'XdrJson.kt', 'fun escapedString(bytes: ByteArray)'),
                'array_fixed_length': ('XdrJson.array', 'XdrJson.kt', 'fun <T> array('),
                'array_variable_length': ('XdrJson.array', 'XdrJson.kt', 'fun <T> array('),
                'enum': ('SCValTypeXdr.xdrJsonName', 'SCValTypeXdr.kt', 'xdrJsonName'),
                'struct': ('TimeBoundsXdr.toXdrJsonElement', 'TimeBoundsXdr.kt', 'buildJsonObject'),
                'discriminated_union': ('AssetXdr.toXdrJsonElement', 'AssetXdr.kt', 'XdrJson.singleKeyObject'),
                'void': ('AssetXdr.Void', 'AssetXdr.kt', 'XdrJson.name("native")'),
                'optional_data': ('XdrJson.optional', 'XdrJson.kt', 'fun <T> optional('),
            },
            'address_types': {
                'sc_address': ('SCAddressXdr.toXdrJsonElement', 'SCAddressXdr.kt', 'MuxedEd25519AccountXdr.fromXdrJsonTree'),
                'account_id': ('AccountIDXdr.toXdrJsonElement', 'AccountIDXdr.kt', 'PublicKeyXdr.fromXdrJsonTree'),
                'contract_id': ('ContractIDXdr.toXdrJsonElement', 'ContractIDXdr.kt', 'StrKey.encodeContract'),
                'muxed_account': ('MuxedAccountXdr.toXdrJsonElement', 'MuxedAccountXdr.kt', 'StrKey.encodeEd25519PublicKey'),
                'muxed_account_med25519': ('MuxedAccountMed25519Xdr.toXdrJsonElement', 'MuxedAccountMed25519Xdr.kt', 'StrKey.encodeMed25519PublicKey'),
                'muxed_ed25519_account': ('MuxedEd25519AccountXdr.toXdrJsonElement', 'MuxedEd25519AccountXdr.kt', 'StrKey.encodeMed25519PublicKey'),
                'pool_id': ('PoolIDXdr.toXdrJsonElement', 'PoolIDXdr.kt', 'StrKey.encodeLiquidityPool'),
                'claimable_balance_id': ('ClaimableBalanceIDXdr.toXdrJsonElement', 'ClaimableBalanceIDXdr.kt', 'StrKey.encodeClaimableBalance'),
                'public_key': ('PublicKeyXdr.toXdrJsonElement', 'PublicKeyXdr.kt', 'StrKey.encodeEd25519PublicKey'),
                'node_id': ('NodeIDXdr.toXdrJsonElement', 'NodeIDXdr.kt', 'PublicKeyXdr.fromXdrJsonTree'),
                'signer_key': ('SignerKeyXdr.toXdrJsonElement', 'SignerKeyXdr.kt', 'StrKey.encodePreAuthTx'),
                'signer_key_ed25519_signed_payload': ('SignerKeyEd25519SignedPayloadXdr.toXdrJsonElement', 'SignerKeyEd25519SignedPayloadXdr.kt', 'StrKey.encodeSignedPayload'),
            },
            'asset_code_types': {
                'asset_code': ('AssetCodeXdr.toXdrJsonElement', 'AssetCodeXdr.kt', 'XdrJson.padAssetCode'),
                'asset_code4': ('AssetCode4Xdr.toXdrJsonElement', 'AssetCode4Xdr.kt', 'XdrJson.trimAssetCode(value, 0)'),
                'asset_code12': ('AssetCode12Xdr.toXdrJsonElement', 'AssetCode12Xdr.kt', 'XdrJson.trimAssetCode(value, 5)'),
            },
            'integer_types': {
                'uint128_parts': ('XdrJson.uint128ToDecimalString', 'UInt128PartsXdr.kt', 'XdrJson.uint128ToDecimalString'),
                'int128_parts': ('XdrJson.int128ToDecimalString', 'Int128PartsXdr.kt', 'XdrJson.int128ToDecimalString'),
                'uint256_parts': ('XdrJson.uint256ToDecimalString', 'UInt256PartsXdr.kt', 'XdrJson.uint256ToDecimalString'),
                'int256_parts': ('XdrJson.int256ToDecimalString', 'Int256PartsXdr.kt', 'XdrJson.int256ToDecimalString'),
            },
            # The decoder's allowString flag is what carries these: it is false for the
            # 32-bit paths and true for the 64-bit ones, so a 64-bit value is read from a
            # JSON number as well as from the string form that is always emitted. The
            # behaviour is pinned by Sep51SpecExampleTest.hyperIntegerAlsoReadsTheJsonNumberForm
            # and its unsigned counterpart.
            'backward_compatibility': {
                'hyper_accepts_json_number': ('XdrJson.int64', 'XdrJson.kt', '"a 64-bit signed integer", allowString = true'),
                'unsigned_hyper_accepts_json_number': ('XdrJson.uint64', 'XdrJson.kt', '"a 64-bit unsigned integer", allowString = true'),
            },
            'json_schema': {
                'schema_property': ('XdrJson.stripSchema', 'XdrJson.kt', 'fun stripSchema('),
            },
        }

        field_mappings: Dict[str, Dict[str, Optional[str]]] = {}

        for section in sep_definition.get('sections', []):
            section_key = section.get('key', '')
            section_evidence = evidence.get(section_key, {})

            section_mappings: Dict[str, Optional[str]] = {}
            for field in section.get('fields', []):
                field_name = field.get('name', '')
                entry = section_evidence.get(field_name)
                if entry is None:
                    section_mappings[field_name] = None
                    continue
                symbol, file_name, token = entry
                section_mappings[field_name] = (
                    symbol if self._xdr_source_contains(file_name, token) else None
                )

            field_mappings[section_key] = section_mappings

        return field_mappings

    def map_sep_53_fields(self, classes: List[Dict[str, Any]],
                          sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Map SEP-53 (Message Signing) fields.

        SEP-53 implementation lives in KeyPair.kt, not in a sep53/ directory.
        Methods: signMessage(ByteArray), signMessage(String), verifyMessage(ByteArray, ByteArray), verifyMessage(String, ByteArray)
        Constant: MESSAGE_PREFIX
        Internal: calculateMessageHash(ByteArray)
        """
        field_mappings = {}

        # Read KeyPair.kt to check for SEP-53 methods
        keypair_has_sep53 = False
        if self.keypair_file.exists():
            content = self.keypair_file.read_text(encoding='utf-8')
            # Check for SEP-53 methods
            if 'signMessage' in content and 'verifyMessage' in content and 'MESSAGE_PREFIX' in content:
                keypair_has_sep53 = True

        # Map fields
        sections = sep_definition.get('sections', [])
        for section in sections:
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])
            section_mappings = {}

            if section_key == 'message_signing_protocol':
                # Map fields to KeyPair methods/constants
                field_map = {
                    'sign_message': 'signMessage',
                    'verify_message': 'verifyMessage',
                    'payload_prefix': 'MESSAGE_PREFIX',
                    'sha256_hashing': 'calculateMessageHash',
                    'text_message_support': 'signMessage(String)',
                    'binary_data_support': 'signMessage(ByteArray)',
                    'ed25519_signature': 'sign',
                    'signature_output': 'ByteArray'
                }

                for field in sep_fields:
                    field_name = field.get('name', '')
                    sdk_method = field_map.get(field_name)
                    if keypair_has_sep53 and sdk_method:
                        section_mappings[field_name] = sdk_method
                    else:
                        section_mappings[field_name] = None
            else:
                # Unknown section
                for field in sep_fields:
                    section_mappings[field.get('name', '')] = None

            field_mappings[section_key] = section_mappings

        return field_mappings

    def map_generic_fields(self, classes: List[Dict[str, Any]],
                           sep_definition: Dict[str, Any]) -> Dict[str, Dict[str, Optional[str]]]:
        """
        Generic field mapping for SEPs without specific logic.

        Args:
            classes: List of SDK classes
            sep_definition: SEP specification definition

        Returns:
            Dictionary mapping sections to field implementations
        """
        field_mappings = {}

        # Get all properties and methods
        all_properties = set()
        all_methods = set()
        for cls in classes:
            for prop in cls.get('properties', []):
                all_properties.add(prop['name'])
            for method in cls.get('methods', []):
                all_methods.add(method['name'])

        # Map sections
        sections = sep_definition.get('sections', [])

        for section in sections:
            section_key = section.get('key', '')
            sep_fields = section.get('fields', [])

            section_mappings = {}

            for field in sep_fields:
                field_name = field.get('name', '')

                # Try to find matching property or method
                # Convert snake_case to camelCase
                sdk_name = snake_to_camel(field_name)

                if sdk_name in all_properties:
                    section_mappings[field_name] = sdk_name
                elif sdk_name in all_methods:
                    section_mappings[field_name] = sdk_name
                else:
                    section_mappings[field_name] = None

            field_mappings[section_key] = section_mappings

        return field_mappings

def main():
    """Main entry point for the analyzer."""
    # Disable colors if not a TTY
    if not sys.stdout.isatty():
        Colors.disable()

    # Parse arguments
    if len(sys.argv) != 2:
        print(f"{Colors.RED}Usage: {sys.argv[0]} <sep_number>{Colors.END}")
        print(f"{Colors.YELLOW}Example: {sys.argv[0]} 0001{Colors.END}")
        sys.exit(1)

    sep_number = sys.argv[1].zfill(4)

    print(f"{Colors.HEADER}KMP Stellar SDK SEP Implementation Analyzer{Colors.END}")
    print(f"{Colors.CYAN}Analyzing SEP-{sep_number}{Colors.END}\n")

    # Create analyzer
    analyzer = SEPAnalyzer(sep_number)

    # Run analysis
    print(f"{Colors.BLUE}Scanning source files...{Colors.END}")
    result = analyzer.analyze()

    if not result['implemented']:
        print(f"{Colors.RED}SEP-{sep_number} not implemented{Colors.END}")
        print(f"Reason: {result.get('reason', 'Unknown')}")
        sys.exit(1)

    # Display results
    print(f"{Colors.GREEN}Found {len(result['files'])} source files{Colors.END}")
    print(f"{Colors.GREEN}Found {len(result['test_files'])} test files{Colors.END}")
    print(f"{Colors.GREEN}Found {result['test_count']} tests{Colors.END}")
    print(f"{Colors.GREEN}Found {len(result['classes'])} classes{Colors.END}")
    print(f"{Colors.GREEN}Coverage: {result['metadata']['coverage']}{Colors.END}\n")

    # Write output
    output_dir = DATA_DIR / 'sep'
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / f'kmp_sep_{sep_number}_implementation.json'

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, indent=2, ensure_ascii=False)

    print(f"{Colors.BOLD}Output written to:{Colors.END} {output_path}")
    print(f"{Colors.GREEN}Analysis complete!{Colors.END}")


if __name__ == '__main__':
    main()
