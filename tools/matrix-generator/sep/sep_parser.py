#!/usr/bin/env python3
"""
SEP (Stellar Ecosystem Proposal) Documentation Parser

This script parses SEP markdown files from the stellar-protocol GitHub repository,
extracts specification details, requirements, and field definitions, and saves
structured data for compatibility analysis with the KMP Stellar SDK.

Author: KMP Stellar SDK Team
Date: 2026-02-13
License: Apache-2.0
"""

import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))

import json
import re
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Dict, List, Any, Optional, Set
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

from common import Colors, DATA_DIR, SDK_ROOT


@dataclass
class Field:
    """Represents a field definition"""
    name: str
    description: str
    requirements: str = ""
    required: bool = False
    field_type: str = ""


@dataclass
class Section:
    """Represents a specification section"""
    title: str
    key: str
    fields: List[Field] = field(default_factory=list)

    @property
    def field_count(self) -> int:
        return len(self.fields)

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        result = {
            'title': self.title,
            'key': self.key,
            'field_count': self.field_count,
            'fields': []
        }

        for f in self.fields:
            field_dict: Dict[str, Any] = {
                'name': f.name,
                'description': f.description,
                'requirements': f.requirements,
                'required': f.required
            }
            if f.field_type:
                field_dict['type'] = f.field_type
            result['fields'].append(field_dict)

        return result


class SEPParser:
    """Parser for Stellar Ecosystem Proposal (SEP) documentation"""

    USER_AGENT = 'KMP-Stellar-SDK-SEP-Parser/1.0'
    TIMEOUT = 30
    MAX_RETRIES = 3

    def __init__(self, sep_number: str):
        """
        Initialize SEP parser for a specific SEP number.

        Args:
            sep_number: SEP number (e.g., '1', '0001', '10', '0010')
        """
        # Normalize to 4 digits
        self.sep_number = sep_number.zfill(4)
        self.raw_content = ""

    def fetch_sep_markdown(self) -> bool:
        """
        Fetch SEP markdown from GitHub repository with retry logic.

        Returns:
            True if successful, False otherwise
        """
        url = f"https://raw.githubusercontent.com/stellar/stellar-protocol/master/ecosystem/sep-{self.sep_number}.md"

        print(f"{Colors.CYAN}Fetching SEP-{self.sep_number} from GitHub...{Colors.END}")
        print(f"URL: {url}")

        for attempt in range(1, self.MAX_RETRIES + 1):
            try:
                request = Request(url, headers={'User-Agent': self.USER_AGENT})
                with urlopen(request, timeout=self.TIMEOUT) as response:
                    if response.status == 200:
                        self.raw_content = response.read().decode('utf-8')
                        print(f"{Colors.GREEN}Successfully fetched {len(self.raw_content)} bytes{Colors.END}")
                        return True
                    else:
                        print(f"{Colors.RED}HTTP {response.status}: {response.msg}{Colors.END}")
                        return False
            except HTTPError as e:
                print(f"{Colors.RED}HTTP Error {e.code}: {e.reason}{Colors.END}")
                if e.code in [404, 403, 401]:
                    return False  # Don't retry for these errors
            except URLError as e:
                print(f"{Colors.YELLOW}URL Error (attempt {attempt}/{self.MAX_RETRIES}): {e.reason}{Colors.END}")
            except Exception as e:
                print(f"{Colors.YELLOW}Error (attempt {attempt}/{self.MAX_RETRIES}): {str(e)}{Colors.END}")

            if attempt < self.MAX_RETRIES:
                wait_time = 2 ** attempt  # Exponential backoff
                print(f"Retrying in {wait_time} seconds...")
                time.sleep(wait_time)

        return False

    def extract_preamble(self) -> Dict[str, str]:
        """
        Extract preamble metadata from SEP markdown.

        Returns:
            Dictionary containing preamble fields
        """
        preamble: Dict[str, str] = {}

        # Try to find preamble section
        preamble_pattern = r'^##\s+Preamble\s*\n(.*?)(?=\n##|\Z)'
        match = re.search(preamble_pattern, self.raw_content, re.MULTILINE | re.DOTALL)

        if match:
            preamble_text = match.group(1)
        else:
            # Try alternative format (list at beginning of document)
            preamble_text = self.raw_content[:1500]

        # Extract individual fields
        field_patterns = {
            'sep': r'SEP:\s*(\S+)',
            'title': r'Title:\s*(.+)',
            'author': r'Author:\s*(.+)',
            'track': r'Track:\s*(.+)',
            'status': r'Status:\s*(.+)',
            'created': r'Created:\s*(.+)',
            'updated': r'Updated:\s*(.+)',
            'version': r'Version:?\s*(.+)',
        }

        for field_name, pattern in field_patterns.items():
            match = re.search(pattern, preamble_text, re.IGNORECASE)
            if match:
                preamble[field_name] = match.group(1).strip()

        # Ensure sep field uses normalized format
        if 'sep' not in preamble:
            preamble['sep'] = self.sep_number

        return preamble

    def extract_summary(self) -> str:
        """
        Extract summary/abstract section from SEP document.

        Returns:
            Summary text or empty string if not found
        """
        # Try different section names
        patterns = [
            r'##\s+(?:Simple\s+)?Summary\s*\n(.*?)(?=\n##|\Z)',
            r'##\s+Abstract\s*\n(.*?)(?=\n##|\Z)',
        ]

        for pattern in patterns:
            match = re.search(pattern, self.raw_content, re.MULTILINE | re.DOTALL)
            if match:
                summary = match.group(1).strip()
                # Clean up extra whitespace
                summary = re.sub(r'\n\s*\n+', ' ', summary)
                summary = re.sub(r'\s+', ' ', summary)
                return summary

        return ""

    def extract_fields_from_table(self, content: str, seen_fields: Set[str]) -> List[Field]:
        """
        Extract field definitions from markdown tables.

        Args:
            content: Content to parse
            seen_fields: Set of already-seen field names (for deduplication)

        Returns:
            List of Field objects
        """
        fields: List[Field] = []

        # Table format: | Field | Requirements | Description |
        table_pattern = r'\|\s*([A-Za-z_][A-Za-z0-9_\.]*)\s*\|([^|]+)\|([^|]+)\|'
        matches = re.finditer(table_pattern, content, re.MULTILINE)

        for match in matches:
            field_name = match.group(1).strip()
            requirements = match.group(2).strip()
            description = match.group(3).strip()

            # Skip header rows
            if field_name.lower() == 'field' or '---' in field_name:
                continue

            # Skip if it looks like a header row (check for keywords in description)
            if field_name.lower() == 'name' and ('description' in description.lower() or 'requirement' in requirements.lower()):
                continue

            # Clean up description
            description = re.sub(r'\s+', ' ', description).strip()

            # Determine if required
            required = self._determine_required(requirements, description)

            if field_name not in seen_fields:
                fields.append(Field(
                    name=field_name,
                    description=description,
                    requirements=requirements,
                    required=required
                ))
                seen_fields.add(field_name)

        return fields

    def extract_fields_from_toml(self, content: str, seen_fields: Set[str]) -> List[Field]:
        """
        Extract field definitions from TOML code blocks.

        Args:
            content: Content to parse
            seen_fields: Set of already-seen field names

        Returns:
            List of Field objects
        """
        fields: List[Field] = []

        # Pattern for TOML field assignments
        toml_pattern = r'^([A-Z_][A-Z0-9_]*|[a-z_][a-z0-9_]*)\s*=\s*(.+)$'
        matches = re.finditer(toml_pattern, content, re.MULTILINE)

        for match in matches:
            field_name = match.group(1).strip()
            example_value = match.group(2).strip()

            if field_name in seen_fields:
                continue

            # Skip TOML section headers
            if example_value.startswith('['):
                continue

            # Try to find description in comments above
            field_pos = match.start()
            before_text = content[max(0, field_pos-500):field_pos]

            description = f"TOML field (example value: {example_value[:50]})"
            comment_pattern = rf'#\s*([^\n]+)\n\s*{re.escape(field_name)}\s*='
            comment_match = re.search(comment_pattern, before_text)
            if comment_match:
                description = comment_match.group(1).strip()

            fields.append(Field(
                name=field_name,
                description=description,
                requirements="varies",
                required=False
            ))
            seen_fields.add(field_name)

        return fields

    def extract_fields_from_lists(self, content: str, seen_fields: Set[str]) -> List[Field]:
        """
        Extract field definitions from bulleted lists.

        Args:
            content: Content to parse
            seen_fields: Set of already-seen field names

        Returns:
            List of Field objects
        """
        fields: List[Field] = []

        # Format: - `FIELD_NAME`: description
        list_pattern = r'-\s+`?([A-Za-z_][A-Za-z0-9_\.]*)`?:?\s*(.+?)(?=\n-\s+`?[A-Za-z_]|\n\n|\Z)'
        matches = re.finditer(list_pattern, content, re.MULTILINE | re.DOTALL)

        for match in matches:
            field_name = match.group(1).strip()
            description = match.group(2).strip()

            if field_name in seen_fields:
                continue

            # Clean up description
            description = re.sub(r'\s+', ' ', description).strip()

            # Determine if required
            required = self._determine_required("", description)

            fields.append(Field(
                name=field_name,
                description=description,
                requirements="varies",
                required=required
            ))
            seen_fields.add(field_name)

        return fields

    def _determine_required(self, requirements: str, description: str) -> bool:
        """
        Determine if a field is required based on requirements and description text.

        Args:
            requirements: Requirements column text
            description: Description text

        Returns:
            True if field is required, False otherwise
        """
        requirements_lower = requirements.lower()
        description_lower = description.lower()

        required_indicators = ['required', 'yes', 'mandatory']
        optional_indicators = ['optional', 'may', 'can be omitted', 'not required']

        # Check for "(optional)" at start of description
        if description_lower.strip().startswith('(optional)'):
            return False

        # Check requirements column first
        if any(ind in requirements_lower for ind in required_indicators):
            return True
        if any(ind in requirements_lower for ind in optional_indicators):
            return False

        # Check description
        if 'required' in description_lower and 'optional' not in description_lower:
            return True
        if any(ind in description_lower for ind in optional_indicators):
            return False

        # Default to False (optional)
        return False

    def _extract_fields_from_markdown_table(self, content: str, field_type_suffix: str = 'field') -> List[Field]:
        """
        Extract fields from markdown table with 3 columns: Name | Type | Description

        Args:
            content: Markdown content containing the table
            field_type_suffix: Suffix for field_type ('parameter' or 'response_field')

        Returns:
            List of extracted Field objects
        """
        fields: List[Field] = []
        seen_fields: Set[str] = set()

        # Split content into lines and find table rows
        lines = content.split('\n')

        for line in lines:
            # Must start with pipe and have at least 3 columns
            if not line.strip().startswith('|'):
                continue

            # Split by pipe and remove empty first/last elements
            columns = [col.strip() for col in line.split('|')]
            columns = [col for col in columns if col]  # Remove empty strings

            if len(columns) < 3:
                continue

            raw_name_cell = columns[0]
            field_type = columns[1]
            description = columns[2]

            # Skip header separator rows (contain dashes)
            if '---' in raw_name_cell or '---' in field_type:
                continue

            # Skip header rows
            if raw_name_cell.lower() in ['name', 'parameter', 'field']:
                continue
            if field_type.lower() in ['type', 'description']:
                continue

            # Extract field name from backticks (handle compound names like `family_name` or `last_name`)
            # Take the first field name if multiple are present
            backtick_pattern = r'`([a-zA-Z_][a-zA-Z0-9_\.]*)`'
            backtick_match = re.search(backtick_pattern, raw_name_cell)
            if backtick_match:
                field_name = backtick_match.group(1)
            else:
                # No backticks - try to match a simple identifier
                simple_pattern = r'^([a-zA-Z_][a-zA-Z0-9_\.]*)$'
                simple_match = re.match(simple_pattern, raw_name_cell)
                if simple_match:
                    field_name = simple_match.group(1)
                else:
                    # Can't extract a valid field name, skip this row
                    continue

            # Clean up
            description = re.sub(r'\s+', ' ', description).strip()
            field_type = re.sub(r'\s+', ' ', field_type).strip()

            # Determine if required
            required = self._determine_required("", description)

            if field_name not in seen_fields:
                fields.append(Field(
                    name=field_name,
                    description=description,
                    requirements="required" if required else "optional",
                    required=required,
                    field_type=field_type if field_type else field_type_suffix
                ))
                seen_fields.add(field_name)

        return fields

    def extract_all_fields(self, content: str) -> List[Field]:
        """
        Extract fields using all three methods (tables, TOML, lists).

        Args:
            content: Content to parse

        Returns:
            Deduplicated list of Field objects

        Note:
            Deduplication priority: table > TOML > list
            This ensures table-based definitions (most authoritative) are not
            overwritten by TOML examples or list-based descriptions.
        """
        seen_fields: Set[str] = set()
        fields: List[Field] = []

        # Method 1: Markdown tables (primary)
        table_fields = self.extract_fields_from_table(content, seen_fields)
        fields.extend(table_fields)

        # Method 2: TOML examples
        toml_fields = self.extract_fields_from_toml(content, seen_fields)
        fields.extend(toml_fields)

        # Method 3: Bulleted lists (fallback)
        list_fields = self.extract_fields_from_lists(content, seen_fields)
        fields.extend(list_fields)

        return fields

    # SEP-specific parsers

    def parse_sep_01(self) -> Dict[str, Any]:
        """Parse SEP-01 (stellar.toml) structure"""
        print(f"{Colors.BLUE}Using SEP-01 specific parser{Colors.END}")

        sections: List[Section] = []

        section_definitions = [
            {
                'title': 'General Information',
                'key': 'global',
                'patterns': [
                    r'###\s+General Information.*?\n(.*?)(?=\n###|\n##|\Z)',
                    r'##\s+General Information.*?\n(.*?)(?=\n###|\n##|\Z)',
                ]
            },
            {
                'title': 'Organization Documentation',
                'key': 'documentation',
                'patterns': [
                    r'###\s+Organization Documentation.*?\n(.*?)(?=\n###|\n##|\Z)',
                    r'##\s+Organization Documentation.*?\n(.*?)(?=\n###|\n##|\Z)',
                    r'###?\s+.*DOCUMENTATION.*?\n(.*?)(?=\n###|\n##|\Z)',
                ]
            },
            {
                'title': 'Point of Contact Documentation',
                'key': 'principals',
                'patterns': [
                    r'###\s+Point of Contact Documentation.*?\n(.*?)(?=\n###|\n##|\Z)',
                    r'##\s+Point of Contact Documentation.*?\n(.*?)(?=\n###|\n##|\Z)',
                    r'###?\s+.*PRINCIPALS.*?\n(.*?)(?=\n###|\n##|\Z)',
                ]
            },
            {
                'title': 'Currency Documentation',
                'key': 'currencies',
                'patterns': [
                    r'###\s+Currency Documentation.*?\n(.*?)(?=\n###|\n##|\Z)',
                    r'##\s+Currency Documentation.*?\n(.*?)(?=\n###|\n##|\Z)',
                    r'###?\s+.*CURRENCIES.*?\n(.*?)(?=\n###|\n##|\Z)',
                ],
                'inject_fields': [
                    Field(
                        name='toml',
                        description='Alternately, stellar.toml can link out to a separate TOML file for each currency by specifying toml as the currency\'s only field',
                        requirements='optional',
                        required=False
                    )
                ]
            },
            {
                'title': 'Validator Information',
                'key': 'validators',
                'patterns': [
                    r'###\s+Validator Information.*?\n(.*?)(?=\n###|\n##|\Z)',
                    r'##\s+Validator Information.*?\n(.*?)(?=\n###|\n##|\Z)',
                    r'###?\s+.*VALIDATORS.*?\n(.*?)(?=\n###|\n##|\Z)',
                ]
            }
        ]

        for section_def in section_definitions:
            section_content = None

            for pattern in section_def['patterns']:
                match = re.search(pattern, self.raw_content, re.MULTILINE | re.DOTALL | re.IGNORECASE)
                if match:
                    section_content = match.group(1)
                    break

            if section_content:
                fields = self.extract_all_fields(section_content)

                # Inject additional fields if defined
                if 'inject_fields' in section_def:
                    fields.extend(section_def['inject_fields'])

                section = Section(
                    title=section_def['title'],
                    key=section_def['key'],
                    fields=fields
                )
                sections.append(section)
                print(f"{Colors.GREEN}  Found '{section_def['title']}': {len(fields)} fields{Colors.END}")
            else:
                print(f"{Colors.YELLOW}  Section '{section_def['title']}' not found{Colors.END}")

        return self._build_result(sections)

    def parse_sep_02(self) -> Dict[str, Any]:
        """Parse SEP-02 (Federation Protocol) structure"""
        print(f"{Colors.BLUE}Using SEP-02 specific parser{Colors.END}")

        sections: List[Section] = []

        # Request parameters
        request_params = Section(title='Request Parameters', key='request_parameters')
        request_params.fields = [
            Field(name='q', description='String to look up (stellar address, account ID, or transaction ID)',
                  field_type='parameter', required=True),
            Field(name='type', description='Type of lookup (name, id, txid, or forward)',
                  field_type='parameter', required=True)
        ]
        sections.append(request_params)

        # Request types
        request_types = Section(title='Request Types', key='request_types')
        request_types_pattern = r'Supported types:\s*\n\n(.*?)(?=\n##|\Z)'
        match = re.search(request_types_pattern, self.raw_content, re.MULTILINE | re.DOTALL)
        if match:
            types_text = match.group(1)
            type_pattern = r'-\s+`(\w+)`:\s+(.*?)(?=\n-\s+`\w+`:|Example|##|$)'
            for type_match in re.finditer(type_pattern, types_text, re.DOTALL):
                type_name = type_match.group(1)
                description = re.sub(r'\s+', ' ', type_match.group(2).strip())
                is_required = type_name in ['name', 'id']
                request_types.fields.append(Field(
                    name=type_name,
                    description=description,
                    field_type='query_type',
                    required=is_required
                ))
        sections.append(request_types)

        # Response fields (hardcoded - markdown parsing unreliable)
        response_section = Section(title='Response Fields', key='response_fields')
        response_section.fields = [
            Field(
                name='stellar_address',
                description='stellar address',
                field_type='field',
                required=True
            ),
            Field(
                name='account_id',
                description='Stellar public key / account ID',
                field_type='field',
                required=True
            ),
            Field(
                name='memo_type',
                description='type of memo to attach to transaction, one of text, id or hash',
                field_type='field',
                required=False
            ),
            Field(
                name='memo',
                description='value of memo to attach to transaction, for hash this should be base64-encoded',
                field_type='field',
                required=False
            )
        ]
        sections.append(response_section)

        print(f"{Colors.GREEN}  Found {sum(s.field_count for s in sections)} total fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_05(self) -> Dict[str, Any]:
        """Parse SEP-05 (Key Derivation) structure - hardcoded definitions"""
        print(f"{Colors.BLUE}Using SEP-05 specific parser (hardcoded){Colors.END}")

        sections: List[Section] = []

        # Mnemonic Generation
        section = Section(title='Mnemonic Generation', key='mnemonic_generation')
        section.fields = [
            Field(name='generate_12_word_mnemonic', description='Generate 12-word mnemonic from 128 bits of entropy', field_type='function', required=True),
            Field(name='generate_15_word_mnemonic', description='Generate 15-word mnemonic from 160 bits of entropy', field_type='function', required=True),
            Field(name='generate_18_word_mnemonic', description='Generate 18-word mnemonic from 192 bits of entropy', field_type='function', required=True),
            Field(name='generate_21_word_mnemonic', description='Generate 21-word mnemonic from 224 bits of entropy', field_type='function', required=True),
            Field(name='generate_24_word_mnemonic', description='Generate 24-word mnemonic from 256 bits of entropy', field_type='function', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Mnemonic Generation': {len(section.fields)} fields{Colors.END}")

        # Mnemonic Validation
        section = Section(title='Mnemonic Validation', key='mnemonic_validation')
        section.fields = [
            Field(name='validate_mnemonic', description='Validate mnemonic phrase by checking word list membership and checksum', field_type='function', required=True),
            Field(name='detect_language', description='Detect the language of a mnemonic phrase by matching words against word lists', field_type='function', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Mnemonic Validation': {len(section.fields)} fields{Colors.END}")

        # Language Support
        section = Section(title='Language Support', key='language_support')
        section.fields = [
            Field(name='english', description='English BIP-39 word list (2048 words)', field_type='wordlist', required=True),
            Field(name='japanese', description='Japanese BIP-39 word list (2048 words)', field_type='wordlist', required=False),
            Field(name='korean', description='Korean BIP-39 word list (2048 words)', field_type='wordlist', required=False),
            Field(name='spanish', description='Spanish BIP-39 word list (2048 words)', field_type='wordlist', required=False),
            Field(name='chinese_simplified', description='Simplified Chinese BIP-39 word list (2048 words)', field_type='wordlist', required=False),
            Field(name='chinese_traditional', description='Traditional Chinese BIP-39 word list (2048 words)', field_type='wordlist', required=False),
            Field(name='french', description='French BIP-39 word list (2048 words)', field_type='wordlist', required=False),
            Field(name='italian', description='Italian BIP-39 word list (2048 words)', field_type='wordlist', required=False),
            Field(name='malay', description='Malay BIP-39 word list (2048 words)', field_type='wordlist', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Language Support': {len(section.fields)} fields{Colors.END}")

        # BIP-39 Seed Derivation
        section = Section(title='BIP-39 Seed Derivation', key='bip39_seed_derivation')
        section.fields = [
            Field(name='mnemonic_to_seed', description='Convert mnemonic to 64-byte BIP-39 seed using PBKDF2-HMAC-SHA512 with 2048 iterations', field_type='function', required=True),
            Field(name='passphrase_support', description='Support optional passphrase for additional security in seed derivation', field_type='feature', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'BIP-39 Seed Derivation': {len(section.fields)} fields{Colors.END}")

        # SLIP-0010 Key Derivation
        section = Section(title='SLIP-0010 Key Derivation', key='slip0010_key_derivation')
        section.fields = [
            Field(name='stellar_derivation_path', description="Stellar-specific derivation path m/44'/148'/x' where 44' is BIP-44 purpose and 148' is Stellar coin type", field_type='constant', required=True),
            Field(name='hardened_derivation', description='All derivation indices must be hardened (index + 2^31) for Ed25519', field_type='algorithm', required=True),
            Field(name='ed25519_master_key_generation', description="Generate master key using HMAC-SHA512(key='ed25519 seed', data=BIP39_seed)", field_type='algorithm', required=True),
            Field(name='ed25519_child_key_derivation', description='Derive child keys using HMAC-SHA512(key=parent_chain_code, data=0x00||parent_key||index+2^31)', field_type='algorithm', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'SLIP-0010 Key Derivation': {len(section.fields)} fields{Colors.END}")

        # Key Export
        section = Section(title='Key Export', key='key_export')
        section.fields = [
            Field(name='get_keypair', description='Get full Stellar KeyPair (public and private key) at specified derivation index', field_type='function', required=True),
            Field(name='get_account_id', description='Get Stellar account ID (G... address) at specified derivation index', field_type='function', required=True),
            Field(name='get_public_key', description='Get raw 32-byte Ed25519 public key at specified derivation index', field_type='function', required=True),
            Field(name='get_private_key', description='Get raw 32-byte Ed25519 private key at specified derivation index', field_type='function', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Key Export': {len(section.fields)} fields{Colors.END}")

        # Test Vectors
        section = Section(title='Test Vectors', key='test_vectors')
        section.fields = [
            Field(name='test_vector_1_12words', description="12-word mnemonic test vector: 'illness spike retreat truth genius clock brain pass fit cave bargain toe' with expected accounts", field_type='test', required=True),
            Field(name='test_vector_2_15words', description="15-word mnemonic test vector: 'resource asthma orphan phone ice canvas fire useful arch jewel impose vague theory cushion top' with expected accounts", field_type='test', required=True),
            Field(name='test_vector_3_24words', description="24-word mnemonic test vector: 'bench hurt jump file august wise...' with expected accounts", field_type='test', required=True),
            Field(name='test_vector_4_24words_passphrase', description="24-word mnemonic with passphrase test vector: 'cable spray genius state float twenty...' with passphrase 'p4ssphr4se'", field_type='test', required=True),
            Field(name='test_vector_5_abandon_about', description="Known test vector: 'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about'", field_type='test', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Test Vectors': {len(section.fields)} fields{Colors.END}")

        return self._build_result(sections)

    def _parse_endpoint_sections(
        self,
        endpoint_sections: List[tuple],
        content: str
    ) -> List[Section]:
        """
        Parse endpoint sections that share a common ## heading / ### Request+Response structure.

        Each element of *endpoint_sections* is a ``(section_name, section_key)`` tuple.
        For every entry the method searches *content* for:

        * A ``##  <section_name>`` block
        * ``### Request`` and ``### Response`` sub-blocks within that block

        Fields are extracted from Markdown tables in those sub-blocks.

        Args:
            endpoint_sections: Ordered list of ``(display_name, dict_key)`` pairs.
            content: Raw Markdown content to search inside (usually ``self.raw_content``).

        Returns:
            List of :class:`Section` objects that contained at least one field.
        """
        sections: List[Section] = []

        for section_name, section_key in endpoint_sections:
            section_pattern = rf'##\s+{re.escape(section_name)}\s*\n(.*?)(?=\n##\s+[A-Z]|\Z)'
            match = re.search(section_pattern, content, re.MULTILINE | re.DOTALL | re.IGNORECASE)

            if not match:
                continue

            section_content = match.group(1)
            all_fields: List[Field] = []

            # Extract request parameters
            request_pattern = r'###\s+Request.*?\n(.*?)(?=\n###|\n##|\Z)'
            request_match = re.search(request_pattern, section_content, re.MULTILINE | re.DOTALL | re.IGNORECASE)
            if request_match:
                request_fields = self._extract_fields_from_markdown_table(request_match.group(1), 'parameter')
                all_fields.extend(request_fields)

            # Extract response fields
            response_pattern = r'###\s+Response.*?\n(.*?)(?=\n###|\n##|\Z)'
            response_match = re.search(response_pattern, section_content, re.MULTILINE | re.DOTALL | re.IGNORECASE)
            if response_match:
                response_fields = self._extract_fields_from_markdown_table(response_match.group(1), 'response_field')
                all_fields.extend(response_fields)

            if all_fields:
                section = Section(title=section_name, key=section_key, fields=all_fields)
                sections.append(section)
                print(f"{Colors.GREEN}  Found '{section_name}': {len(all_fields)} fields{Colors.END}")

        return sections

    def parse_sep_06(self) -> Dict[str, Any]:
        """Parse SEP-06 (Deposit/Withdrawal API) structure"""
        print(f"{Colors.BLUE}Using SEP-06 specific parser{Colors.END}")

        # SEP-06 uses ## level sections (Deposit, Withdraw, etc.) with ### Request/Response subsections
        endpoint_sections = [
            ('Deposit', 'deposit'),
            ('Withdraw', 'withdraw'),
            ('Deposit Exchange', 'deposit_exchange'),
            ('Withdraw Exchange', 'withdraw_exchange'),
            ('Info', 'info'),
            ('Transactions', 'transactions'),
            ('Transaction', 'transaction'),
            ('Fee', 'fee')
        ]

        sections = self._parse_endpoint_sections(endpoint_sections, self.raw_content)
        return self._build_result(sections)

    def parse_sep_08(self) -> Dict[str, Any]:
        """Parse SEP-08 (Regulated Assets) structure - hardcoded definitions"""
        print(f"{Colors.BLUE}Using SEP-08 specific parser (hardcoded){Colors.END}")

        sections: List[Section] = []

        # Approval Endpoint (1 field)
        section = Section(title='Approval Endpoint', key='approval_endpoint')
        section.fields = [
            Field(name='tx_approve', description='POST /tx_approve - Approval server endpoint that receives a signed transaction, checks for compliance, and signs it on success', requirements='required', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Approval Endpoint': {len(section.fields)} fields{Colors.END}")

        # Request Parameters (1 field)
        section = Section(title='Request Parameters', key='request_parameters')
        section.fields = [
            Field(name='tx', description='A base64 encoded transaction envelope XDR signed by the user. This is the transaction that will be tested for compliance and signed on success.', requirements='required', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Request Parameters': {len(section.fields)} fields{Colors.END}")

        # Response Statuses (5 fields)
        section = Section(title='Response Statuses', key='response_statuses')
        section.fields = [
            Field(name='success', description='Transaction was found compliant and signed without being revised', requirements='required', required=True),
            Field(name='revised', description='Transaction was revised to be made compliant', requirements='required', required=True),
            Field(name='pending', description='Issuer could not determine whether to approve the transaction at the time of receiving it', requirements='required', required=True),
            Field(name='action_required', description='User must complete an action before this transaction can be approved', requirements='required', required=True),
            Field(name='rejected', description='Transaction is not compliant and could not be revised to be made compliant', requirements='required', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Response Statuses': {len(section.fields)} fields{Colors.END}")

        # Success Response Fields (3 fields)
        section = Section(title='Success Response Fields', key='success_response_fields')
        section.fields = [
            Field(name='status', description='Status value "success"', requirements='required', required=True),
            Field(name='tx', description='Transaction envelope XDR, base64 encoded. This transaction will have both the original signature(s) from the request as well as one or multiple additional signatures from the issuer.', requirements='required', required=True),
            Field(name='message', description='A human readable string containing information to pass on to the user', requirements='optional', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Success Response Fields': {len(section.fields)} fields{Colors.END}")

        # Revised Response Fields (3 fields)
        section = Section(title='Revised Response Fields', key='revised_response_fields')
        section.fields = [
            Field(name='status', description='Status value "revised"', requirements='required', required=True),
            Field(name='tx', description='Transaction envelope XDR, base64 encoded. This transaction is a revised compliant version of the original request transaction, signed by the issuer.', requirements='required', required=True),
            Field(name='message', description='A human readable string explaining the modifications made to the transaction to make it compliant', requirements='required', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Revised Response Fields': {len(section.fields)} fields{Colors.END}")

        # Pending Response Fields (3 fields)
        section = Section(title='Pending Response Fields', key='pending_response_fields')
        section.fields = [
            Field(name='status', description='Status value "pending"', requirements='required', required=True),
            Field(name='timeout', description='Number of milliseconds to wait before submitting the same transaction again. Use 0 if the wait time cannot be determined.', requirements='required', required=True),
            Field(name='message', description='A human readable string containing information to pass on to the user', requirements='optional', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Pending Response Fields': {len(section.fields)} fields{Colors.END}")

        # Action Required Response Fields (5 fields)
        section = Section(title='Action Required Response Fields', key='action_required_response_fields')
        section.fields = [
            Field(name='status', description='Status value "action_required"', requirements='required', required=True),
            Field(name='message', description='A human readable string containing information regarding the action required', requirements='required', required=True),
            Field(name='action_url', description='A URL that allows the user to complete the actions required to have the transaction approved', requirements='required', required=True),
            Field(name='action_method', description='GET or POST, indicating the type of request that should be made to the action_url. If not provided, GET is assumed.', requirements='optional', required=False),
            Field(name='action_fields', description='An array of additional fields defined by SEP-9 Standard KYC / AML fields that the client may optionally provide to the approval service when sending the request to the action_url', requirements='optional', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Action Required Response Fields': {len(section.fields)} fields{Colors.END}")

        # Rejected Response Fields (2 fields)
        section = Section(title='Rejected Response Fields', key='rejected_response_fields')
        section.fields = [
            Field(name='status', description='Status value "rejected"', requirements='required', required=True),
            Field(name='error', description='A human readable string explaining why the transaction is not compliant and could not be made compliant', requirements='required', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Rejected Response Fields': {len(section.fields)} fields{Colors.END}")

        # Action URL Handling (4 fields)
        section = Section(title='Action URL Handling', key='action_url_handling')
        section.fields = [
            Field(name='action_url_get', description='Support for GET method to action_url with query parameters', requirements='required', required=True),
            Field(name='action_url_post', description='Support for POST method to action_url with JSON body', requirements='required', required=True),
            Field(name='action_url_post_response_no_further_action', description='Handle POST response with result "no_further_action_required"', requirements='required', required=True),
            Field(name='action_url_post_response_follow_next_url', description='Handle POST response with result "follow_next_url" and next_url field', requirements='required', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Action URL Handling': {len(section.fields)} fields{Colors.END}")

        # Stellar TOML Fields (3 fields)
        section = Section(title='Stellar TOML Fields', key='stellar_toml_fields')
        section.fields = [
            Field(name='regulated', description='A boolean indicating whether or not this is a regulated asset. If missing, false is assumed.', requirements='required', required=True),
            Field(name='approval_server', description='The URL of an approval service that signs validated transactions', requirements='required', required=True),
            Field(name='approval_criteria', description="A human readable string that explains the issuer's requirements for approving transactions", requirements='optional', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Stellar TOML Fields': {len(section.fields)} fields{Colors.END}")

        # Authorization Flags (2 fields)
        section = Section(title='Authorization Flags', key='authorization_flags')
        section.fields = [
            Field(name='authorization_required', description='Authorization Required flag must be set on issuer account', requirements='required', required=True),
            Field(name='authorization_revocable', description='Authorization Revocable flag must be set on issuer account', requirements='required', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Authorization Flags': {len(section.fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_09(self) -> Dict[str, Any]:
        """Parse SEP-09 (KYC Fields) structure"""
        print(f"{Colors.BLUE}Using SEP-09 specific parser{Colors.END}")

        sections: List[Section] = []

        # SEP-09 uses ## level headings for field sections
        section_definitions = [
            {
                'title': 'Natural Person Fields',
                'key': 'natural_person',
                'pattern': r'##\s+Natural [Pp]erson [Ff]ields\s*\n(.*?)(?=\n##|\Z)'
            },
            {
                'title': 'Financial Account Fields',
                'key': 'financial_account',
                'pattern': r'##\s+Financial [Aa]ccount [Ff]ields\s*\n(.*?)(?=\n##|\Z)'
            },
            {
                'title': 'Organization Fields',
                'key': 'organization',
                'pattern': r'##\s+Organization [Ff]ields\s*\n(.*?)(?=\n##|\Z)'
            },
            {
                'title': 'Card Fields',
                'key': 'card',
                'pattern': r'##\s+Card [Ff]ields\s*\n(.*?)(?=\n##|\Z)'
            }
        ]

        for section_def in section_definitions:
            match = re.search(section_def['pattern'], self.raw_content, re.MULTILINE | re.DOTALL | re.IGNORECASE)
            if match:
                content = match.group(1)
                # Use helper to extract fields from table
                fields = self._extract_fields_from_markdown_table(content, 'kyc_field')

                if fields:
                    section = Section(
                        title=section_def['title'],
                        key=section_def['key'],
                        fields=fields
                    )
                    sections.append(section)
                    print(f"{Colors.GREEN}  Found '{section_def['title']}': {len(fields)} fields{Colors.END}")
                else:
                    print(f"{Colors.YELLOW}  No fields found in '{section_def['title']}'{Colors.END}")

        return self._build_result(sections)

    def parse_sep_10(self) -> Dict[str, Any]:
        """
        Parse SEP-10 (Web Authentication) structure.

        Uses a hard-coded feature manifest instead of regex extraction because
        SEP-10 is a protocol specification rather than a data schema.  The
        manifest mirrors the Flutter SDK's SEP-10 definition so that both SDKs
        produce directly comparable compatibility matrices.
        """
        print(f"{Colors.BLUE}Using SEP-10 specific parser{Colors.END}")

        # --- Authentication Endpoints ----------------------------------------
        auth_endpoint_fields = [
            Field(
                name='get_auth_challenge',
                description='GET /auth endpoint - Returns challenge transaction',
                required=True,
            ),
            Field(
                name='post_auth_token',
                description='POST /auth endpoint - Validates signed challenge and returns JWT token',
                required=True,
            ),
        ]

        # --- Challenge Transaction Features ----------------------------------
        challenge_fields = [
            Field(
                name='challenge_transaction_generation',
                description='Generate challenge transaction with proper structure',
                required=True,
            ),
            Field(
                name='transaction_envelope_format',
                description='Challenge uses proper Stellar transaction envelope format',
                required=True,
            ),
            Field(
                name='sequence_number_zero',
                description='Challenge transaction has sequence number 0',
                required=True,
            ),
            Field(
                name='manage_data_operations',
                description='Challenge uses ManageData operations for auth data',
                required=True,
            ),
            Field(
                name='home_domain_operation',
                description='First operation contains home_domain + " auth" as data name',
                required=True,
            ),
            Field(
                name='web_auth_domain_operation',
                description='Optional operation with web_auth_domain for domain verification',
                required=False,
            ),
            Field(
                name='timebounds_enforcement',
                description='Challenge transaction has timebounds for expiration',
                required=True,
            ),
            Field(
                name='server_signature',
                description='Challenge is signed by server before sending to client',
                required=True,
            ),
            Field(
                name='nonce_generation',
                description='Random nonce in ManageData operation value',
                required=True,
            ),
        ]

        # --- Client Domain Features ------------------------------------------
        client_domain_fields = [
            Field(
                name='client_domain_parameter',
                description='Support optional client_domain parameter in GET /auth',
                required=False,
            ),
            Field(
                name='client_domain_operation',
                description='Add client_domain ManageData operation to challenge',
                required=False,
            ),
            Field(
                name='client_domain_signature',
                description='Require signature from client domain account',
                required=False,
            ),
        ]

        # --- JWT Token Features ----------------------------------------------
        jwt_fields = [
            Field(
                name='jwt_token_generation',
                description='Generate JWT token after successful challenge validation',
                required=True,
            ),
            Field(
                name='jwt_token_response',
                description='Return JWT token in JSON response with "token" field',
                required=True,
            ),
            Field(
                name='jwt_expiration',
                description='JWT token includes expiration time',
                required=True,
            ),
            Field(
                name='jwt_claims',
                description='JWT token includes required claims (sub, iat, exp)',
                required=True,
            ),
        ]

        # --- Verification Features -------------------------------------------
        verification_fields = [
            Field(
                name='challenge_validation',
                description='Validate challenge transaction structure and content',
                required=True,
            ),
            Field(
                name='signature_verification',
                description='Verify all signatures on challenge transaction',
                required=True,
            ),
            Field(
                name='multi_signature_support',
                description='Support multiple signatures on challenge (client account + signers)',
                required=True,
            ),
            Field(
                name='timebounds_validation',
                description='Validate challenge is within valid time window',
                required=True,
            ),
            Field(
                name='home_domain_validation',
                description='Validate home domain in challenge matches server',
                required=True,
            ),
            Field(
                name='memo_support',
                description='Support optional memo in challenge for muxed accounts',
                required=False,
            ),
        ]

        sections: List[Section] = [
            Section(
                title='Authentication Endpoints',
                key='authentication_endpoints',
                fields=auth_endpoint_fields,
            ),
            Section(
                title='Challenge Transaction Features',
                key='challenge_transaction_features',
                fields=challenge_fields,
            ),
            Section(
                title='Client Domain Features',
                key='client_domain_features',
                fields=client_domain_fields,
            ),
            Section(
                title='JWT Token Features',
                key='jwt_token_features',
                fields=jwt_fields,
            ),
            Section(
                title='Verification Features',
                key='verification_features',
                fields=verification_fields,
            ),
        ]

        for section in sections:
            print(f"{Colors.GREEN}  Defined '{section.title}': {section.field_count} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_12(self) -> Dict[str, Any]:
        """Parse SEP-12 (KYC API) structure"""
        print(f"{Colors.BLUE}Using SEP-12 specific parser{Colors.END}")

        sections: List[Section] = []

        # SEP-12 uses ## level sections like "## Customer GET", "## Customer PUT", etc.
        endpoint_sections = [
            ('Customer GET', 'customer_get'),
            ('Customer PUT', 'customer_put'),
            ('Customer DELETE', 'customer_delete'),
            ('Customer Callback PUT', 'customer_callback_put'),
            ('Customer Files POST', 'customer_files_post'),
            ('Customer Files GET', 'customer_files_get')
        ]

        for section_name, section_key in endpoint_sections:
            section_pattern = rf'##\s+{re.escape(section_name)}\s*\n(.*?)(?=\n##\s+[A-Z]|\Z)'
            match = re.search(section_pattern, self.raw_content, re.MULTILINE | re.DOTALL | re.IGNORECASE)

            if not match:
                continue

            content = match.group(1)
            all_fields: List[Field] = []

            # Extract request parameters
            request_pattern = r'###\s+Request.*?\n(.*?)(?=\n###|\n##|\Z)'
            request_match = re.search(request_pattern, content, re.MULTILINE | re.DOTALL | re.IGNORECASE)
            if request_match:
                request_fields = self._extract_fields_from_markdown_table(request_match.group(1), 'parameter')
                all_fields.extend(request_fields)

            # Extract response fields
            response_pattern = r'###\s+Response.*?\n(.*?)(?=\n###|\n##|\Z)'
            response_match = re.search(response_pattern, content, re.MULTILINE | re.DOTALL | re.IGNORECASE)
            if response_match:
                response_fields = self._extract_fields_from_markdown_table(response_match.group(1), 'response_field')
                all_fields.extend(response_fields)

            if all_fields:
                section = Section(title=section_name, key=section_key, fields=all_fields)
                sections.append(section)
                print(f"{Colors.GREEN}  Found '{section_name}': {len(all_fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_24(self) -> Dict[str, Any]:
        """Parse SEP-24 (Hosted Deposit/Withdrawal) structure"""
        print(f"{Colors.BLUE}Using SEP-24 specific parser{Colors.END}")

        # SEP-24 uses ## level sections (Deposit, Withdraw, Info, etc.) with ### Request/Response subsections
        endpoint_sections = [
            ('Deposit', 'deposit'),
            ('Withdraw', 'withdraw'),
            ('Info', 'info'),
            ('Fee', 'fee'),
            ('Transactions', 'transactions'),
            ('Transaction', 'transaction')
        ]

        sections = self._parse_endpoint_sections(endpoint_sections, self.raw_content)
        return self._build_result(sections)

    def parse_sep_38(self) -> Dict[str, Any]:
        """Parse SEP-38 (Anchor RFQ) structure"""
        print(f"{Colors.BLUE}Using SEP-38 specific parser{Colors.END}")

        sections: List[Section] = []

        # Split content by ### to get endpoint sections
        # Pattern: find ### followed by HTTP method
        endpoint_pattern = r'^###\s+(GET|POST|PUT|DELETE|PATCH)\s+(.+)$'

        # Find all endpoint headers and their positions
        endpoints = []
        for match in re.finditer(endpoint_pattern, self.raw_content, re.MULTILINE):
            endpoints.append({
                'method': match.group(1),
                'path': match.group(2).strip(),
                'start': match.end(),
                'line': match.group(0)
            })

        # Extract content for each endpoint (from end of header to start of next ### or end of file)
        for i, endpoint in enumerate(endpoints):
            # Find content until next ### heading at same level
            if i < len(endpoints) - 1:
                next_section_pattern = r'^###\s+'
                next_match = re.search(next_section_pattern, self.raw_content[endpoint['start']:], re.MULTILINE)
                if next_match:
                    content = self.raw_content[endpoint['start']:endpoint['start'] + next_match.start()]
                else:
                    content = self.raw_content[endpoint['start']:]
            else:
                content = self.raw_content[endpoint['start']:]

            all_fields: List[Field] = []

            # SEP-38 uses #### Request and #### Response (level 4 headings within the endpoint section)
            # Extract request parameters
            request_pattern = r'####\s+Request.*?\n(.*?)(?=####|^###|\Z)'
            request_match = re.search(request_pattern, content, re.MULTILINE | re.DOTALL | re.IGNORECASE)
            if request_match:
                request_fields = self._extract_fields_from_markdown_table(request_match.group(1), 'parameter')
                all_fields.extend(request_fields)

            # Extract response fields
            response_pattern = r'####\s+Response.*?\n(.*?)(?=####|^###|\Z)'
            response_match = re.search(response_pattern, content, re.MULTILINE | re.DOTALL | re.IGNORECASE)
            if response_match:
                response_fields = self._extract_fields_from_markdown_table(response_match.group(1), 'response_field')
                all_fields.extend(response_fields)

            section = Section(
                title=f"{endpoint['method']} {endpoint['path']}",
                key=f"{endpoint['method'].lower()}_{endpoint['path'].replace('/', '_').replace('-', '_').strip('_')}",
                fields=all_fields
            )
            sections.append(section)
            print(f"{Colors.GREEN}  Found '{endpoint['method']} {endpoint['path']}': {len(all_fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_45(self) -> Dict[str, Any]:
        """Parse SEP-45 (Contract Web Authentication) structure - hardcoded definitions"""
        print(f"{Colors.BLUE}Using SEP-45 specific parser (hardcoded){Colors.END}")

        sections: List[Section] = []

        # Challenge Request Parameters
        section = Section(title='Challenge Request Parameters', key='challenge_request_parameters')
        section.fields = [
            Field(name='account', description='The Client Account address (C...) that the Client wishes to authenticate', field_type='parameter', required=True),
            Field(name='home_domain', description='A Home Domain. Servers that generate tokens for multiple Home Domains can use this parameter', field_type='parameter', required=False),
            Field(name='client_domain', description='a Client Domain. Supplied by Clients that intend to verify their domain in addition', field_type='parameter', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Challenge Request Parameters': {len(section.fields)} fields{Colors.END}")

        # Challenge Response Fields
        section = Section(title='Challenge Response Fields', key='challenge_response_fields')
        section.fields = [
            Field(name='authorization_entries', description='XDR-encoded SorobanAuthorizationEntries. It contains an entry for the Client Account', field_type='response_field', required=True),
            Field(name='network_passphrase', description='Stellar network passphrase used by the Server. This allows a Client to verify', field_type='response_field', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Challenge Response Fields': {len(section.fields)} fields{Colors.END}")

        # Token Request Parameters
        section = Section(title='Token Request Parameters', key='token_request_parameters')
        section.fields = [
            Field(name='authorization_entries', description='XDR-encoded SorobanAuthorizationEntries', field_type='parameter', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Token Request Parameters': {len(section.fields)} fields{Colors.END}")

        # Token Response Fields
        section = Section(title='Token Response Fields', key='token_response_fields')
        section.fields = [
            Field(name='token', description='The JWT that can be used to authenticate future endpoint calls with the anchor', field_type='response_field', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Token Response Fields': {len(section.fields)} fields{Colors.END}")

        # JWT Claims
        section = Section(title='JWT Claims', key='jwt_claims')
        section.fields = [
            Field(name='iss', description='a Uniform Resource Identifier (URI) for the issuer', field_type='jwt_claim', required=True),
            Field(name='sub', description="the Client Account's address (C...)", field_type='jwt_claim', required=True),
            Field(name='iat', description='current timestamp', field_type='jwt_claim', required=True),
            Field(name='exp', description='a server can pick its own expiration period for the token', field_type='jwt_claim', required=True),
            Field(name='client_domain', description='included if the challenge transaction contained a client_domain', field_type='jwt_claim', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'JWT Claims': {len(section.fields)} fields{Colors.END}")

        # Contract Verification Function Arguments
        section = Section(title='Contract Verification Function Arguments', key='contract_verification_function_arguments')
        section.fields = [
            Field(name='account', description='The client account address', field_type='function_argument', required=True),
            Field(name='home_domain', description='The home domain', field_type='function_argument', required=True),
            Field(name='web_auth_domain', description="The server's domain", field_type='function_argument', required=True),
            Field(name='web_auth_domain_account', description="The server's SIGNING_KEY", field_type='function_argument', required=True),
            Field(name='client_domain', description='The client domain', field_type='function_argument', required=False),
            Field(name='client_domain_account', description="The client domain's SIGNING_KEY", field_type='function_argument', required=False),
            Field(name='nonce', description='A random string generated by the server to prevent replay attacks', field_type='function_argument', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Contract Verification Function Arguments': {len(section.fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_30(self) -> Dict[str, Any]:
        """Parse SEP-30 (Account Recovery) structure - hardcoded definitions"""
        print(f"{Colors.BLUE}Using SEP-30 specific parser (hardcoded){Colors.END}")

        sections: List[Section] = []

        # Register Account (POST /accounts/<address>)
        section = Section(title='Register Account (POST /accounts/<address>)', key='register_account')
        section.fields = [
            Field(name='identities', description='Array of identities for registration', field_type='array', required=True),
            Field(name='identities[].role', description='Role of the identity (owner, other)', field_type='string', required=True),
            Field(name='identities[].auth_methods', description='Authentication methods', field_type='array', required=True),
            Field(name='identities[].auth_methods[].type', description='Auth method type', field_type='string', required=True),
            Field(name='identities[].auth_methods[].value', description='Auth method value', field_type='string', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Register Account (POST /accounts/<address>)': {len(section.fields)} fields{Colors.END}")

        # Update Identities (PUT /accounts/<address>)
        section = Section(title='Update Identities (PUT /accounts/<address>)', key='update_identities')
        section.fields = [
            Field(name='identities', description='Replacement identities', field_type='array', required=True),
            Field(name='identities[].role', description='Role of the identity', field_type='string', required=True),
            Field(name='identities[].auth_methods', description='Authentication methods', field_type='array', required=True),
            Field(name='identities[].auth_methods[].type', description='Auth method type', field_type='string', required=True),
            Field(name='identities[].auth_methods[].value', description='Auth method value', field_type='string', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Update Identities (PUT /accounts/<address>)': {len(section.fields)} fields{Colors.END}")

        # Sign Transaction (POST /accounts/<address>/sign/<signing-address>)
        section = Section(title='Sign Transaction (POST /accounts/<address>/sign/<signing-address>)', key='sign_transaction')
        section.fields = [
            Field(name='transaction', description='XDR base64 encoded transaction', field_type='string', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Sign Transaction (POST /accounts/<address>/sign/<signing-address>)': {len(section.fields)} fields{Colors.END}")

        # Account Details Response
        section = Section(title='Account Details Response', key='account_details_response')
        section.fields = [
            Field(name='address', description='Stellar account address', field_type='string', required=True),
            Field(name='identities', description='Array of response identities', field_type='array', required=True),
            Field(name='identities[].role', description='Role of the identity', field_type='string', required=False),
            Field(name='identities[].authenticated', description='Whether identity is authenticated', field_type='boolean', required=True),
            Field(name='signers', description='Array of signers', field_type='array', required=True),
            Field(name='signers[].key', description='Signer public key', field_type='string', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Account Details Response': {len(section.fields)} fields{Colors.END}")

        # Signature Response
        section = Section(title='Signature Response', key='signature_response')
        section.fields = [
            Field(name='signature', description='Base64 encoded signature', field_type='string', required=True),
            Field(name='network_passphrase', description='Network passphrase', field_type='string', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Signature Response': {len(section.fields)} fields{Colors.END}")

        # List Accounts (GET /accounts)
        section = Section(title='List Accounts (GET /accounts)', key='list_accounts')
        section.fields = [
            Field(name='accounts', description='Array of account objects', field_type='array', required=True),
            Field(name='after', description='Pagination cursor', field_type='string', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'List Accounts (GET /accounts)': {len(section.fields)} fields{Colors.END}")

        # Authentication Methods
        section = Section(title='Authentication Methods', key='authentication_methods')
        section.fields = [
            Field(name='stellar_address', description='Stellar address auth method', field_type='string', required=False),
            Field(name='phone_number', description='Phone number auth method', field_type='string', required=False),
            Field(name='email', description='Email auth method', field_type='string', required=False)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Authentication Methods': {len(section.fields)} fields{Colors.END}")

        # Error Response
        section = Section(title='Error Response', key='error_response')
        section.fields = [
            Field(name='error', description='Error message', field_type='string', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Error Response': {len(section.fields)} fields{Colors.END}")

        # HTTP Error Codes
        section = Section(title='HTTP Error Codes', key='http_error_codes')
        section.fields = [
            Field(name='400 Bad Request', description='Invalid request', field_type='status', required=True),
            Field(name='401 Unauthorized', description='Authentication failed', field_type='status', required=True),
            Field(name='404 Not Found', description='Account not found', field_type='status', required=True),
            Field(name='409 Conflict', description='Account already registered', field_type='status', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'HTTP Error Codes': {len(section.fields)} fields{Colors.END}")

        # API Endpoints
        section = Section(title='API Endpoints', key='api_endpoints')
        section.fields = [
            Field(name='POST /accounts/<address>', description='Register account', field_type='endpoint', required=True),
            Field(name='PUT /accounts/<address>', description='Update identities', field_type='endpoint', required=True),
            Field(name='POST /accounts/<address>/sign/<signing-address>', description='Sign transaction', field_type='endpoint', required=True),
            Field(name='GET /accounts/<address>', description='Account details', field_type='endpoint', required=True),
            Field(name='DELETE /accounts/<address>', description='Delete account', field_type='endpoint', required=True),
            Field(name='GET /accounts', description='List accounts', field_type='endpoint', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'API Endpoints': {len(section.fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_31(self) -> Dict[str, Any]:
        """Parse SEP-31 (Cross-Border Payments) structure - hardcoded definitions.

        Section structure and field selection follow the SEP-31 specification at
        https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md.
        The 11 sections enumerate every JSON field exposed by the Sending Anchor's
        client-side surface area: service endpoints, GET /info response, POST
        /transactions request/response, GET /transactions/:id response, and the
        embedded refund and fee-detail sub-objects.

        Field `required` flags reflect what the spec marks as MUST or
        unconditionally required; fields marked "(optional)" or "(Deprecated,
        optional)" are recorded as required=False.
        """
        print(f"{Colors.BLUE}Using SEP-31 specific parser (hardcoded){Colors.END}")

        sections: List[Section] = []

        # Service Endpoints
        section = Section(title='Service Endpoints', key='service_endpoints')
        section.fields = [
            Field(name='GET /info', description='Discover assets, limits, fees, and KYC requirements', field_type='endpoint', required=True),
            Field(name='POST /transactions', description='Initiate a cross-border payment', field_type='endpoint', required=True),
            Field(name='GET /transactions/:id', description='Fetch transaction status', field_type='endpoint', required=True),
            Field(name='PATCH /transactions/:id', description='Update transaction info (deprecated)', field_type='endpoint', required=True),
            Field(name='PUT /transactions/:id/callback', description='Register status callback URL', field_type='endpoint', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Service Endpoints': {len(section.fields)} fields{Colors.END}")

        # Info Response Fields
        section = Section(title='Info Response Fields', key='info_response_fields')
        section.fields = [
            Field(name='receive', description='Map of asset code to per-asset receive configuration', field_type='object', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Info Response Fields': {len(section.fields)} fields{Colors.END}")

        # Receive Asset Info Fields
        section = Section(title='Receive Asset Info Fields', key='receive_asset_info_fields')
        section.fields = [
            Field(name='sep12', description='Per-asset SEP-12 customer type requirements (deprecated)', field_type='object', required=False),
            Field(name='min_amount', description='Minimum amount the anchor accepts', field_type='number', required=False),
            Field(name='max_amount', description='Maximum amount the anchor accepts', field_type='number', required=False),
            Field(name='fee_fixed', description='Fixed fee charged by the anchor', field_type='number', required=False),
            Field(name='fee_percent', description='Percentage fee charged by the anchor', field_type='number', required=False),
            Field(name='sender_sep12_type', description='Deprecated sender SEP-12 type identifier', field_type='string', required=False),
            Field(name='receiver_sep12_type', description='Deprecated receiver SEP-12 type identifier', field_type='string', required=False),
            Field(name='fields', description='Deprecated per-transaction field requirements', field_type='object', required=False),
            Field(name='quotes_supported', description='Whether anchor accepts an optional SEP-38 quote_id', field_type='boolean', required=False),
            Field(name='quotes_required', description='Whether anchor requires a SEP-38 quote_id', field_type='boolean', required=False),
            Field(name='funding_methods', description='Methods the anchor uses to deliver the off-chain asset', field_type='array', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Receive Asset Info Fields': {len(section.fields)} fields{Colors.END}")

        # SEP-12 Types Info Fields
        section = Section(title='SEP-12 Types Info Fields', key='sep12_types_info_fields')
        section.fields = [
            Field(name='sender', description='SEP-12 sender customer types map', field_type='object', required=True),
            Field(name='receiver', description='SEP-12 receiver customer types map', field_type='object', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'SEP-12 Types Info Fields': {len(section.fields)} fields{Colors.END}")

        # POST /transactions Request Fields
        section = Section(title='POST /transactions Request Fields', key='post_transactions_request_fields')
        section.fields = [
            Field(name='amount', description='Amount of the Stellar asset to send', field_type='number', required=True),
            Field(name='asset_code', description='Code of the Stellar asset being sent', field_type='string', required=True),
            Field(name='asset_issuer', description='Issuer of the Stellar asset', field_type='string', required=False),
            Field(name='destination_asset', description='SEP-38 off-chain asset to deliver', field_type='string', required=False),
            Field(name='quote_id', description='SEP-38 firm quote id', field_type='string', required=False),
            Field(name='sender_id', description='SEP-12 customer id of the Sending Client', field_type='string', required=False),
            Field(name='receiver_id', description='SEP-12 customer id of the Receiving Client', field_type='string', required=False),
            Field(name='fields', description='Deprecated per-transaction field values', field_type='object', required=False),
            Field(name='lang', description='ISO 639-1 language code', field_type='string', required=False),
            Field(name='refund_memo', description='Memo to attach when issuing refunds', field_type='string', required=False),
            Field(name='refund_memo_type', description='Type of refund_memo (id, text, or hash)', field_type='string', required=False),
            Field(name='funding_method', description='Anchor funding method to use for delivery', field_type='string', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'POST /transactions Request Fields': {len(section.fields)} fields{Colors.END}")

        # POST /transactions Response Fields
        section = Section(title='POST /transactions Response Fields', key='post_transactions_response_fields')
        section.fields = [
            Field(name='id', description='Persistent transaction identifier', field_type='string', required=True),
            Field(name='stellar_account_id', description='Receiving Anchor Stellar account to pay', field_type='string', required=False),
            Field(name='stellar_memo_type', description='Type of stellar_memo (text, hash, or id)', field_type='string', required=False),
            Field(name='stellar_memo', description='Memo to attach to the on-chain payment', field_type='string', required=False),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'POST /transactions Response Fields': {len(section.fields)} fields{Colors.END}")

        # Transaction Response Fields
        section = Section(title='Transaction Response Fields', key='transaction_response_fields')
        section.fields = [
            Field(name='id', description='Transaction identifier matching POST /transactions', field_type='string', required=True),
            Field(name='status', description='Lifecycle status of the transaction', field_type='string', required=True),
            Field(name='status_eta', description='Estimated seconds until next status change', field_type='number', required=False),
            Field(name='status_message', description='Human-readable status description', field_type='string', required=False),
            Field(name='amount_in', description='Amount received by the Receiving Anchor', field_type='string', required=False),
            Field(name='amount_in_asset', description='SEP-38 asset of the inbound amount', field_type='string', required=False),
            Field(name='amount_out', description='Amount sent to the Receiving Client', field_type='string', required=False),
            Field(name='amount_out_asset', description='SEP-38 asset of the delivered amount', field_type='string', required=False),
            Field(name='amount_fee', description='Deprecated aggregate fee charged', field_type='string', required=False),
            Field(name='amount_fee_asset', description='Deprecated fee asset', field_type='string', required=False),
            Field(name='fee_details', description='Structured fee breakdown', field_type='object', required=True),
            Field(name='quote_id', description='SEP-38 quote id used by this transaction', field_type='string', required=False),
            Field(name='stellar_account_id', description='Receiving Anchor Stellar account', field_type='string', required=False),
            Field(name='stellar_memo_type', description='Type of stellar_memo', field_type='string', required=False),
            Field(name='stellar_memo', description='Memo attached to the on-chain payment', field_type='string', required=False),
            Field(name='started_at', description='UTC ISO 8601 transaction creation timestamp', field_type='string', required=False),
            Field(name='updated_at', description='UTC ISO 8601 last status transition timestamp', field_type='string', required=False),
            Field(name='completed_at', description='UTC ISO 8601 completion timestamp', field_type='string', required=False),
            Field(name='stellar_transaction_id', description='Stellar transaction hash of the on-chain payment', field_type='string', required=False),
            Field(name='external_transaction_id', description='External off-chain transaction identifier', field_type='string', required=False),
            Field(name='refunded', description='Deprecated full-refund flag', field_type='boolean', required=False),
            Field(name='refunds', description='Structured refund aggregate', field_type='object', required=False),
            Field(name='required_info_message', description='Message accompanying required_info_updates', field_type='string', required=False),
            Field(name='required_info_updates', description='Fields requiring update from the Sending Anchor', field_type='object', required=False),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Transaction Response Fields': {len(section.fields)} fields{Colors.END}")

        # Refunds Fields
        section = Section(title='Refunds Fields', key='refunds_fields')
        section.fields = [
            Field(name='amount_refunded', description='Total amount refunded across all payments', field_type='string', required=True),
            Field(name='amount_fee', description='Total fee charged for processing the refunds', field_type='string', required=True),
            Field(name='payments', description='Array of individual refund payments', field_type='array', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Refunds Fields': {len(section.fields)} fields{Colors.END}")

        # Refund Payment Fields
        section = Section(title='Refund Payment Fields', key='refund_payment_fields')
        section.fields = [
            Field(name='id', description='Stellar transaction hash of the refund payment', field_type='string', required=True),
            Field(name='amount', description='Amount returned in this refund payment', field_type='string', required=True),
            Field(name='fee', description='Fee charged for processing this refund payment', field_type='string', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Refund Payment Fields': {len(section.fields)} fields{Colors.END}")

        # Fee Details Fields
        section = Section(title='Fee Details Fields', key='fee_details_fields')
        section.fields = [
            Field(name='total', description='Aggregate fee amount charged', field_type='string', required=True),
            Field(name='asset', description='SEP-38 asset of the fee', field_type='string', required=True),
            Field(name='details', description='Array of fee line items', field_type='array', required=False),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Fee Details Fields': {len(section.fields)} fields{Colors.END}")

        # Fee Details Breakdown Fields
        section = Section(title='Fee Details Breakdown Fields', key='fee_details_breakdown_fields')
        section.fields = [
            Field(name='name', description='Name of the fee line item', field_type='string', required=True),
            Field(name='amount', description='Amount of this fee line item', field_type='string', required=True),
            Field(name='description', description='Optional description of the fee line item', field_type='string', required=False),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Fee Details Breakdown Fields': {len(section.fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_46(self) -> Dict[str, Any]:
        """Parse SEP-46 (Contract Meta) structure - hardcoded definitions"""
        print(f"{Colors.BLUE}Using SEP-46 specific parser (hardcoded){Colors.END}")

        sections: List[Section] = []

        # Metadata Storage
        section = Section(title='Metadata Storage', key='metadata_storage')
        section.fields = [
            Field(name='contractmetav0_section', description='Support for contractmetav0 Wasm custom section', field_type='feature', required=True),
            Field(name='multiple_entries_single_section', description='Support for multiple entries in single section', field_type='feature', required=True),
            Field(name='multiple_sections', description='Support for multiple sections interpreted sequentially', field_type='feature', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Metadata Storage': {len(section.fields)} fields{Colors.END}")

        # Encoding Format
        section = Section(title='Encoding Format', key='encoding_format')
        section.fields = [
            Field(name='scmetaentry_xdr', description='Uses SCMetaEntry XDR type for encoding', field_type='feature', required=True),
            Field(name='binary_stream_encoding', description='Encodes entries as binary stream', field_type='feature', required=True),
            Field(name='key_value_pairs', description='Stores metadata as key-value string pairs', field_type='feature', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Encoding Format': {len(section.fields)} fields{Colors.END}")

        # Implementation Support
        section = Section(title='Implementation Support', key='implementation_support')
        section.fields = [
            Field(name='parse_contract_meta', description='Parse contract metadata from bytecode', field_type='function', required=True),
            Field(name='extract_meta_entries', description='Extract meta entries as key-value pairs', field_type='function', required=True),
            Field(name='decode_scmetaentry', description='Decode SCMetaEntry XDR structures', field_type='function', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Implementation Support': {len(section.fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_47(self) -> Dict[str, Any]:
        """Parse SEP-47 (Contract Interface Discovery) structure - hardcoded definitions"""
        print(f"{Colors.BLUE}Using SEP-47 specific parser (hardcoded){Colors.END}")

        sections: List[Section] = []

        # SEP Declaration
        section = Section(title='SEP Declaration', key='sep_declaration')
        section.fields = [
            Field(name='sep_meta_key', description="Support for 'sep' meta entry key", field_type='feature', required=True),
            Field(name='comma_separated_list', description='Parse comma-separated SEP numbers', field_type='feature', required=True),
            Field(name='multiple_sep_entries', description="Support for multiple 'sep' meta entries", field_type='feature', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'SEP Declaration': {len(section.fields)} fields{Colors.END}")

        # Meta Entry Format
        section = Section(title='Meta Entry Format', key='meta_entry_format')
        section.fields = [
            Field(name='sep_number_format', description="Parse SEP numbers (e.g., '41', '0041', 'SEP-41')", field_type='feature', required=True),
            Field(name='whitespace_handling', description='Trim whitespace from SEP numbers', field_type='feature', required=True),
            Field(name='empty_value_handling', description="Handle empty/missing 'sep' entries gracefully", field_type='feature', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Meta Entry Format': {len(section.fields)} fields{Colors.END}")

        # Implementation Support
        section = Section(title='Implementation Support', key='implementation_support')
        section.fields = [
            Field(name='parse_supported_seps', description='Parse and extract list of supported SEPs', field_type='function', required=True),
            Field(name='expose_supported_seps', description='Expose supportedSeps property on contract info', field_type='feature', required=True),
            Field(name='validate_sep_format', description='Validate SEP number format and filter invalid entries', field_type='feature', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Implementation Support': {len(section.fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_48(self) -> Dict[str, Any]:
        """Parse SEP-48 (Contract Interface Specification) structure - hardcoded definitions"""
        print(f"{Colors.BLUE}Using SEP-48 specific parser (hardcoded){Colors.END}")

        sections: List[Section] = []

        # WASM Section
        section = Section(title='WASM Section', key='wasm_section')
        section.fields = [
            Field(name='contractspecv0_section', description='Contract specification Wasm custom section', field_type='feature', required=True),
            Field(name='contractenvmetav0_section', description='Environment metadata Wasm section', field_type='feature', required=True),
            Field(name='contractmetav0_section', description='Contract metadata Wasm section', field_type='feature', required=True),
            Field(name='xdr_binary_encoding', description='XDR binary encoded specification entries', field_type='feature', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'WASM Section': {len(section.fields)} fields{Colors.END}")

        # Entry Types
        section = Section(title='Entry Types', key='entry_types')
        section.fields = [
            Field(name='function_specs', description='Parse function specifications', field_type='feature', required=True),
            Field(name='struct_specs', description='Parse struct type specifications', field_type='feature', required=True),
            Field(name='union_specs', description='Parse union type specifications', field_type='feature', required=True),
            Field(name='enum_specs', description='Parse enum type specifications', field_type='feature', required=True),
            Field(name='error_enum_specs', description='Parse error enum specifications', field_type='feature', required=True),
            Field(name='event_specs', description='Parse event specifications', field_type='feature', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Entry Types': {len(section.fields)} fields{Colors.END}")

        # Type System - Primitive
        section = Section(title='Type System - Primitive', key='type_system_primitive')
        section.fields = [
            Field(name='boolean_type', description='Boolean type support', field_type='type', required=True),
            Field(name='void_type', description='Void type support', field_type='type', required=True),
            Field(name='numeric_types', description='Numeric types (u32, i32, u64, i64, u128, i128, u256, i256)', field_type='type', required=True),
            Field(name='timepoint_duration', description='Timepoint and duration types', field_type='type', required=True),
            Field(name='bytes_string_symbol', description='Bytes, string, symbol types', field_type='type', required=True),
            Field(name='address_type', description='Address type support', field_type='type', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Type System - Primitive': {len(section.fields)} fields{Colors.END}")

        # Type System - Compound
        section = Section(title='Type System - Compound', key='type_system_compound')
        section.fields = [
            Field(name='option_type', description='Option<T> type', field_type='type', required=True),
            Field(name='result_type', description='Result<T, E> type', field_type='type', required=True),
            Field(name='vector_type', description='Vec<T> type', field_type='type', required=True),
            Field(name='map_type', description='Map<K, V> type', field_type='type', required=True),
            Field(name='tuple_type', description='Tuple types', field_type='type', required=True),
            Field(name='bytes_n_type', description='Fixed-length bytes type', field_type='type', required=True),
            Field(name='user_defined_type', description='User-defined types', field_type='type', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Type System - Compound': {len(section.fields)} fields{Colors.END}")

        # Parsing Support
        section = Section(title='Parsing Support', key='parsing_support')
        section.fields = [
            Field(name='parse_contract_bytecode', description='Parse contract specifications from WASM', field_type='function', required=True),
            Field(name='extract_spec_entries', description='Extract and decode all specification entries', field_type='function', required=True),
            Field(name='parse_environment_meta', description='Parse environment metadata (interface version)', field_type='function', required=True),
            Field(name='parse_contract_meta', description='Parse contract metadata key-value pairs', field_type='function', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Parsing Support': {len(section.fields)} fields{Colors.END}")

        # XDR Support
        section = Section(title='XDR Support', key='xdr_support')
        section.fields = [
            Field(name='decode_scspecentry', description='Decode SCSpecEntry structures', field_type='xdr_type', required=True),
            Field(name='decode_scspectypedef', description='Decode SCSpecTypeDef structures for type definitions', field_type='xdr_type', required=True),
            Field(name='decode_scenvmetaentry', description='Decode SCEnvMetaEntry structures', field_type='xdr_type', required=True),
            Field(name='decode_scmetaentry', description='Decode SCMetaEntry structures', field_type='xdr_type', required=True),
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'XDR Support': {len(section.fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_sep_53(self) -> Dict[str, Any]:
        """Parse SEP-53 (Message Signing) structure - hardcoded definitions"""
        print(f"{Colors.BLUE}Using SEP-53 specific parser (hardcoded){Colors.END}")

        sections: List[Section] = []

        # Message Signing Protocol
        section = Section(title='Message Signing Protocol', key='message_signing_protocol')
        section.fields = [
            Field(name='sign_message', description='Sign arbitrary messages with Ed25519 keypair', field_type='function', required=True),
            Field(name='verify_message', description='Verify Ed25519 message signatures', field_type='function', required=True),
            Field(name='payload_prefix', description='Domain separation prefix "Stellar Signed Message:\\n"', field_type='constant', required=True),
            Field(name='sha256_hashing', description='SHA-256 hash of prefix + message before signing', field_type='algorithm', required=True),
            Field(name='text_message_support', description='Sign and verify UTF-8 text messages', field_type='feature', required=True),
            Field(name='binary_data_support', description='Sign and verify arbitrary binary data', field_type='feature', required=True),
            Field(name='ed25519_signature', description='64-byte Ed25519 signature output', field_type='signature_format', required=True),
            Field(name='signature_output', description='ByteArray signature return type', field_type='return_type', required=True)
        ]
        sections.append(section)
        print(f"{Colors.GREEN}  Found 'Message Signing Protocol': {len(section.fields)} fields{Colors.END}")

        return self._build_result(sections)

    def parse_generic_sep(self) -> Dict[str, Any]:
        """Parse any SEP with generic extraction logic"""
        print(f"{Colors.BLUE}Using generic SEP parser{Colors.END}")

        sections: List[Section] = []

        # Extract all ## level sections (skip preamble, summary, abstract)
        section_pattern = r'##\s+([^#\n]+)\n(.*?)(?=\n##|\Z)'
        matches = re.finditer(section_pattern, self.raw_content, re.MULTILINE | re.DOTALL)

        skip_sections = {'preamble', 'summary', 'simple summary', 'abstract', 'motivation', 'introduction'}

        for match in matches:
            title = match.group(1).strip()
            content = match.group(2).strip()

            if title.lower() in skip_sections:
                continue

            fields = self.extract_all_fields(content)

            if fields:
                # Generate a key from the title
                key = re.sub(r'[^a-z0-9_]', '_', title.lower())
                key = re.sub(r'_+', '_', key).strip('_')

                section = Section(title=title, key=key, fields=fields)
                sections.append(section)
                print(f"{Colors.GREEN}  Found '{title}': {len(fields)} fields{Colors.END}")

        return self._build_result(sections)

    def _build_result(self, sections: List[Section]) -> Dict[str, Any]:
        """Build the final result dictionary"""
        total_fields = sum(s.field_count for s in sections)

        result = {
            'sep_number': self.sep_number,
            'preamble': self.extract_preamble(),
            'summary': self.extract_summary(),
            'sections': [s.to_dict() for s in sections],
            'metadata': {
                'parsed_at': datetime.now(timezone.utc).isoformat(),
                'source_url': f"https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-{self.sep_number}.md",
                'total_fields': total_fields,
                'content_length': len(self.raw_content)
            }
        }

        return result

    def parse(self) -> Dict[str, Any]:
        """
        Parse the SEP document using the appropriate parser.

        Returns:
            Structured data dictionary
        """
        # Map SEP numbers to specific parsers
        parsers = {
            '0001': self.parse_sep_01,
            '0002': self.parse_sep_02,
            '0005': self.parse_sep_05,
            '0006': self.parse_sep_06,
            '0008': self.parse_sep_08,
            '0009': self.parse_sep_09,
            '0010': self.parse_sep_10,
            '0012': self.parse_sep_12,
            '0024': self.parse_sep_24,
            '0030': self.parse_sep_30,
            '0031': self.parse_sep_31,
            '0038': self.parse_sep_38,
            '0045': self.parse_sep_45,
            '0046': self.parse_sep_46,
            '0047': self.parse_sep_47,
            '0048': self.parse_sep_48,
            '0053': self.parse_sep_53,
        }

        parser_func = parsers.get(self.sep_number, self.parse_generic_sep)
        return parser_func()

    def save_to_file(self, data: Dict[str, Any], output_path: Path) -> bool:
        """
        Save parsed data to JSON file.

        Args:
            data: Structured data to save
            output_path: Path to output file

        Returns:
            True if successful, False otherwise
        """
        try:
            # Create output directory if it doesn't exist
            output_path.parent.mkdir(parents=True, exist_ok=True)

            # Write JSON with pretty formatting
            with open(output_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=4, ensure_ascii=False)

            print(f"{Colors.GREEN}Saved to: {output_path}{Colors.END}")
            return True
        except IOError as e:
            print(f"{Colors.RED}Error saving file: {e}{Colors.END}")
            return False
        except Exception as e:
            print(f"{Colors.RED}Unexpected error: {e}{Colors.END}")
            return False


def print_summary(data: Dict[str, Any]) -> None:
    """Print a summary of parsed data"""
    print(f"\n{Colors.BOLD}Parsing Summary:{Colors.END}")
    print(f"  SEP Number: {data['sep_number']}")
    print(f"  Title: {data['preamble'].get('title', 'N/A')}")
    print(f"  Status: {data['preamble'].get('status', 'N/A')}")
    print(f"  Total Fields: {data['metadata']['total_fields']}")
    print(f"  Sections: {len(data['sections'])}")

    if data['sections']:
        print(f"\n{Colors.BOLD}Section Breakdown:{Colors.END}")
        for section in data['sections']:
            print(f"    {section['title']}: {section['field_count']} fields")


def main() -> int:
    """Main entry point"""
    # Disable colors if not TTY
    if not sys.stdout.isatty():
        Colors.disable()

    # Parse command line arguments
    if len(sys.argv) != 2:
        print(f"{Colors.RED}Usage: {sys.argv[0]} <sep_number>{Colors.END}")
        print(f"Example: {sys.argv[0]} 0002")
        return 1

    sep_number = sys.argv[1]

    # Output path: tools/matrix-generator/data/sep/
    output_dir = DATA_DIR / 'sep'
    output_file = output_dir / f"sep_{sep_number.zfill(4)}_definition.json"

    print(f"{Colors.BOLD}{Colors.HEADER}KMP Stellar SDK - SEP Parser{Colors.END}")
    print(f"Output directory: {output_dir}")
    print()

    # Create parser and fetch content
    parser = SEPParser(sep_number)

    if not parser.fetch_sep_markdown():
        print(f"{Colors.RED}Failed to fetch SEP-{parser.sep_number}{Colors.END}")
        return 1

    print()
    print(f"{Colors.CYAN}Parsing SEP-{parser.sep_number}...{Colors.END}")

    # Parse content
    try:
        data = parser.parse()
    except Exception as e:
        print(f"{Colors.RED}Error parsing SEP: {e}{Colors.END}")
        import traceback
        traceback.print_exc()
        return 1

    # Print summary
    print_summary(data)

    # Save to file
    print()
    if parser.save_to_file(data, output_file):
        print(f"\n{Colors.GREEN}{Colors.BOLD}Success!{Colors.END}")
        return 0
    else:
        return 1


if __name__ == '__main__':
    sys.exit(main())
