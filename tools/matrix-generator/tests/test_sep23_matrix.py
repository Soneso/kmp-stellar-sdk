"""SEP-23 (Strkeys) matrix: parser, analyzer checks, rendered matrix and detection."""

import contextlib
import copy
import functools
import io
import json
import re
import tempfile
import unittest
from pathlib import Path
from typing import Any, Dict, Optional
from unittest import mock

import matrix_test_support as support

import common
import generate_sep_comparison
import run_analysis
import sep_analyzer
import sep_parser

enter_context = support.enter_context

SPEC = (Path(__file__).resolve().parent / "fixtures" / "sep-0023.md").read_text(encoding="utf-8")
MATRIX = "SEP-0023_COMPATIBILITY_MATRIX.md"
KEY_TYPES_KEY = "key_types"
VECTORS_KEY = "test_vectors_quoted_in_the_strkey_unit_test_files"
VECTORS_TITLE = "Test vectors quoted in the StrKey unit test files"
STRKEY_TEST = "unitTests/StrKeyTest.kt"
CLAIMABLE_BALANCE_VECTORS = "unitTests/ClaimableBalanceVectors.kt"

# (key type, base value as printed, evaluated base value, first character)
KEY_TYPES = [
    ("STRKEY_PUBKEY", "6 << 3", 48, "G"), ("STRKEY_MUXED", "12 << 3", 96, "M"),
    ("STRKEY_PRIVKEY", "18 << 3", 144, "S"), ("STRKEY_PRE_AUTH_TX", "19 << 3", 152, "T"),
    ("STRKEY_HASH_X", "23 << 3", 184, "X"), ("STRKEY_SIGNED_PAYLOAD", "15 << 3", 120, "P"),
    ("STRKEY_CONTRACT", "2 << 3", 16, "C"), ("STRKEY_LIQUIDITY_POOL", "11 << 3", 88, "L"),
    ("STRKEY_CLAIMABLE_BALANCE", "1 << 3", 8, "B"),
]

G = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJ"
M = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJ"
P = "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAA"
PAYLOAD = "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4"
B = "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RG"

# (case title, vector) in document order
VALID = [
    ("Valid non-multiplexed account", G + "VSGZ"),
    ("Valid multiplexed account", M + "UAAAAAAAAAAAACJUQ"),
    ("Valid multiplexed account in which unsigned id exceeds maximum signed 64-bit integer", M + "VAAAAAAAAAAAAAJLK"),
    ("Valid signed payload with an ed25519 public key and a 32-byte payload.", P + "QACAQDAQ" + PAYLOAD + "DUPB6IBZGM"),
    ("Valid signed payload with an ed25519 public key and a 29-byte payload which becomes zero padded.",
     P + "OQCAQDAQ" + PAYLOAD + "DUAAAAFGBU"),
    ("Valid contract", "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"),
    ("Valid liquidity pool address", "LA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUPJN"),
    ("Valid claimable balance address", B + "R4TU"),
]
INVALID = [
    ("Invalid length (Ed25519 should be 32 bytes, not 5)", "GAAAAAAAACGC6"),
    ("The unused trailing bit must be zero in the encoding of the last three bytes (24 bits) as five base-32 "
     "symbols (25 bits)", M + "UAAAAAAAAAAAACJUR"),
    ("Invalid length (congruent to 1 mod 8)", G + "VSGZA"),
    ("Invalid length (base-32 decoding should yield 35 bytes, not 36)", G + "UACUSI"),
    ("Invalid algorithm (low 3 bits of version byte are 7)", "G47QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVP2I"),
    ("Invalid length (congruent to 6 mod 8)", M + "VAAAAAAAAAAAAAJLKA"),
    ("Invalid length (base-32 decoding should yield 43 bytes, not 44)", M + "VAAAAAAAAAAAAAAV75I"),
    ("Invalid algorithm (low 3 bits of version byte are 7)", "M47QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUQ"),
    ("Padding bytes are not allowed", M + "UAAAAAAAAAAAACJUK==="),
    ("Invalid checksum", M + "UAAAAAAAAAAAACJUO"),
    ("Length prefix specifies length that is shorter than payload in signed payload",
     P + "QACAQDAQ" + PAYLOAD + "DUPB6IAAAAAAAAPM"),
    ("Length prefix specifies length that is longer than payload in signed payload", P + "OQCAQDAQ" + PAYLOAD + "Z2PQ"),
    ("No zero padding in signed payload", P + "OQCAQDAQ" + PAYLOAD + "DXFH6"),
    ("The unused trailing 2-bits must be zero in the encoding of the last symbol.", B + "R4TV"),
    ("Invalid claimable balance type (first byte of binary key is not 0)",
     "BAAT6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGXACA"),
]
NAMES = [f"valid_{n:02d}" for n in range(1, 9)] + [f"invalid_{n:02d}" for n in range(1, 16)]
VECTOR = dict(zip(NAMES, [vector for _, vector in VALID + INVALID]))

# StrKey.kt as the SDK lays it out: a private VersionByte enum written with `shl` and an encode
# and a decode function per key type.
FUNCTIONS = ["Ed25519PublicKey", "Med25519PublicKey", "Ed25519SecretSeed", "PreAuthTx", "Sha256Hash",
             "SignedPayload", "Contract", "LiquidityPool", "ClaimableBalance"]
ACCOUNT_ID_ENTRY = "        ACCOUNT_ID((6 shl 3).toByte(), 32..32),           // G\n"
STRKEY_KT = (
    "package com.soneso.stellar.sdk\n\nobject StrKey {\n\n"
    "    private enum class VersionByte(val value: Byte, val dataLengths: IntRange) {\n" + ACCOUNT_ID_ENTRY
    + "".join(f"        {name}(({shift} shl 3).toByte(), 32..32),\n" for name, shift in (
        ("MED25519_PUBLIC_KEY", 12), ("SEED", 18), ("PRE_AUTH_TX", 19), ("SHA256_HASH", 23),
        ("SIGNED_PAYLOAD", 15), ("CONTRACT", 2), ("LIQUIDITY_POOL", 11)))
    + "        CLAIMABLE_BALANCE((1 shl 3).toByte(), 33..33);\n\n"
    "        val encodedLength: Int get() = dataLengths.first\n    }\n\n"
    + "".join(f"    fun encode{f}(data: ByteArray): String = TODO()\n    fun decode{f}(data: String): ByteArray = TODO()\n"
              for f in FUNCTIONS)
    + "}\n"
)

# StrKeyTest.kt quoting every vector but the valid claimable balance one, in the SDK's styles:
# plain literals, one vector split into two literals joined with `+`, and a list.
EXACT_STRKEY_TEST_KT = (
    "package com.soneso.stellar.sdk.unitTests\n\nclass StrKeyTest {\n"
    + "".join(f'    private val valid{n} = "{VALID[n][1]}"\n' for n in (0, 1, 2, 4, 5, 6))
    + f'    private val signedPayload =\n        "{VALID[3][1][:40]}" +\n            "{VALID[3][1][40:]}"\n'
    + "    private val claimableBalanceId = ClaimableBalanceVectors.strKey\n"
    + "    private val invalidVectors = listOf(\n" + "".join(f'        "{v}",\n' for _, v in INVALID) + "    )\n}\n"
)
EXACT_CLAIMABLE_BALANCE_VECTORS_KT = (
    f'internal object ClaimableBalanceVectors {{\n    const val strKey = "{VALID[7][1]}"\n}}\n'
)
# Quotes vectors that extend valid_01, valid_03, invalid_10 and invalid_14 by a character or by
# padding, and none of those four exactly.
COLLISION_STRKEY_TEST_KT = (
    "class StrKeyTest {\n"
    + "".join(f'    private val v{i} = "{text}"\n' for i, text in enumerate((
        VECTOR["invalid_03"], VECTOR["invalid_06"], VECTOR["invalid_10"] + "===", VECTOR["invalid_14"] + "===")))
    + "}\n"
)


def replace_once(text: str, old: str, new: str) -> str:
    """Return *text* with the single occurrence of *old* replaced; fail if it occurs otherwise."""
    if text.count(old) != 1:
        raise AssertionError(f"expected exactly one occurrence of {old!r}, found {text.count(old)}")
    return text.replace(old, new)


def exact(message: str) -> str:
    """Return a pattern matching exactly *message*, for assertRaisesRegex."""
    return f"^{re.escape(message)}$"


def parse(text: str) -> Dict[str, Any]:
    """Parse *text* as the fetched SEP-23 document."""
    parser = sep_parser.SEPParser("0023")
    parser.raw_content = text
    with contextlib.redirect_stdout(io.StringIO()):
        return parser.parse()


def section(definition: Dict[str, Any], key: str) -> Dict[str, Any]:
    return next(s for s in definition["sections"] if s["key"] == key)


@functools.lru_cache(maxsize=None)
def spec_definition() -> Dict[str, Any]:
    """Return the definition parsed from the fixture; callers that edit it work on a copy."""
    return parse(SPEC)


def key_type_values(definition: Dict[str, Any]) -> Dict[str, int]:
    return {f["name"]: f["value"] for f in section(definition, KEY_TYPES_KEY)["fields"]}


class Sep23ParserTest(unittest.TestCase):

    def test_sections_and_preamble(self) -> None:
        definition = spec_definition()
        self.assertEqual([(s["title"], s["key"]) for s in definition["sections"]],
                         [("Key types", KEY_TYPES_KEY), (VECTORS_TITLE, VECTORS_KEY)])
        self.assertEqual((definition["preamble"]["title"], definition["preamble"]["version"],
                          definition["preamble"]["status"]), ("Strkeys", "1.3.0", "Active"))
        self.assertEqual(definition["metadata"]["total_fields"], 32)

    def test_key_types_carry_the_evaluated_base_value_and_first_character(self) -> None:
        self.assertEqual(section(spec_definition(), KEY_TYPES_KEY)["fields"], [
            {"name": name, "description": f"Version byte base value {printed} = {value}, first character {char}",
             "requirements": "", "required": True, "type": "key_type", "value": value}
            for name, printed, value, char in KEY_TYPES])

    def test_vectors_in_document_order_with_the_file_the_analyzer_reads(self) -> None:
        expected = []
        for name, (title, vector) in zip(NAMES, VALID + INVALID):
            test_file = CLAIMABLE_BALANCE_VECTORS if name == "valid_08" else STRKEY_TEST
            expected.append({"name": name, "description": f"quoted in `{test_file}`: {title}", "requirements": "",
                             "required": True, "type": "test_vector", "value": vector, "test_file": test_file})
        self.assertEqual(section(spec_definition(), VECTORS_KEY)["fields"], expected)

    def test_items_follow_an_edited_document(self) -> None:
        edited = replace_once(SPEC, "| STRKEY_PUBKEY            | 6 << 3     |", "| STRKEY_PUBKEY            | 7 << 3     |")
        edited = replace_once(edited, VALID[5][1], VALID[5][1][:-1] + "B")
        edited = replace_once(edited, "| STRKEY_CONTRACT          | 2 << 3     | C          | no    | Hash |\n", "")
        edited = replace_once(edited, f"1. Invalid checksum\n\n   - Strkey:\n     `{VECTOR['invalid_10']}`\n\n", "")
        definition = parse(edited)
        vectors = {f["name"]: f["value"] for f in section(definition, VECTORS_KEY)["fields"]}
        self.assertEqual(key_type_values(definition)["STRKEY_PUBKEY"], 56)
        self.assertNotIn("STRKEY_CONTRACT", key_type_values(definition))
        self.assertEqual(vectors["valid_06"], VALID[5][1][:-1] + "B")
        self.assertEqual(vectors["invalid_10"], VECTOR["invalid_11"])
        self.assertEqual(definition["metadata"]["total_fields"], 8 + 8 + 14)

    def test_a_plain_integer_base_value_is_read(self) -> None:
        field = section(parse(replace_once(SPEC, "| 6 << 3     |", "| 48         |")), KEY_TYPES_KEY)["fields"][0]
        self.assertEqual((field["value"], field["description"]), (48, "Version byte base value 48, first character G"))

    def test_only_the_specification_table_naming_both_columns_is_read(self) -> None:
        before = ("| Key type      | Base value | First char | Muxed | Alg |\n| --- | --- | --- | --- | --- |\n"
                  "| STRKEY_PUBKEY | 7 << 3     | G          | no    | PK  |\n\n## Specification\n")
        inside = "## Specification\n\n| Key type      | Meaning    |\n| --- | --- |\n| STRKEY_PUBKEY | Account ID |\n\n"
        for label, old, new in (("a table before Specification", "## Specification\n", before),
                                ("a table without First char", "## Specification\n\n", inside)):
            with self.subTest(label):
                expected = {name: value for name, _, value, _ in KEY_TYPES}
                self.assertEqual(key_type_values(parse(replace_once(SPEC, old, new))), expected)

    def test_the_paste_block_after_the_invalid_cases_adds_no_cases(self) -> None:
        numbered = replace_once(SPEC, "You can paste these invalid strkeys",
                                "You can paste these invalid strkeys\n\n1. `GAAAAAAAACGC6`\n\nAs")
        self.assertEqual(section(parse(numbered), VECTORS_KEY), section(spec_definition(), VECTORS_KEY))

    def test_broken_documents_raise(self) -> None:
        header = "| Key type                 | Base value | First char | Muxed | Alg  |\n"
        table_start = SPEC.index(header)
        table_end = SPEC.index("\n\n", table_start) + 1
        table = SPEC[table_start:table_end]
        valid = SPEC[SPEC.index("### Valid test cases\n") + 21:SPEC.index("### Invalid test cases")]
        invalid = SPEC[SPEC.index("### Invalid test cases\n") + 23:SPEC.index("You can paste")]
        muxed = "| STRKEY_MUXED             | 12 << 3    | M          | yes   | PK   |"
        cases = {
            "no Specification section": (replace_once(SPEC, "## Specification\n", "## Details\n"),
                                         "'## Specification' section not found"),
            "no version byte table": (SPEC[:table_start] + SPEC[table_end:], "version byte table not found"),
            "a table after the Specification section": (
                replace_once(SPEC[:table_start] + SPEC[table_end:], "## Implementation\n", "## Implementation\n\n" + table),
                "version byte table not found"),
            "a table without the Key type column": (replace_once(SPEC, "| Key type                 |", "| Kind       |"),
                                                    "names both 'Key type' and 'First char'"),
            "a table without rows": (replace_once(SPEC, table, header + table.split("\n")[1] + "\n"), "has no rows"),
            "a row with a missing cell": (replace_once(SPEC, muxed, muxed[:-7]), "row has 4 cells, the header 5"),
            "a key type listed twice": (replace_once(SPEC, "| STRKEY_MUXED   ", "| STRKEY_PUBKEY  "),
                                        "lists STRKEY_PUBKEY twice"),
            "a row without a key type name": (replace_once(SPEC, "| STRKEY_MUXED   ", "|                "),
                                              "row has no key type name"),
            "a first char that is not one base-32 character": (replace_once(SPEC, "| M          |", "| M1         |"),
                                                               "first char 'M1' is not one base-32 character"),
            "a table without the Base value column": (replace_once(SPEC, "| Base value |", "| Base       |"),
                                                      "no 'Base value' column"),
            "a base value that is neither an integer nor a shift": (
                replace_once(SPEC, "| 6 << 3     |", "| 6 * 8      |"), "is neither an integer nor a shift expression"),
            "no Tests section": (replace_once(SPEC, "## Tests\n", "## Test data\n"), "'## Tests' section not found"),
            "no invalid test cases subsection": (replace_once(SPEC, "### Invalid test cases\n", "### Invalid\n"),
                                                 "'### Invalid test cases' subsection not found"),
            "an empty valid case list": (replace_once(SPEC, valid, "\n"), "'### Valid test cases' lists no test cases"),
            "an empty invalid case list": (replace_once(SPEC, invalid, "\n"),
                                           "'### Invalid test cases' lists no test cases"),
            "a case with two Strkey lines": (
                replace_once(SPEC, "   - Strkey: `GAAAAAAAACGC6`\n", "   - Strkey: `GAAAAAAAACGC6`\n   - Strkey: `GA`\n"),
                "invalid case 1 .* has 2 Strkey lines"),
        }
        for label, (text, message) in cases.items():
            with self.subTest(label):
                with self.assertRaisesRegex(ValueError, message):
                    parse(text)

    def run_main(self, document: str, data_dir: Path) -> int:
        def fetch(parser: sep_parser.SEPParser) -> bool:
            parser.raw_content = document
            return True

        with mock.patch.object(sep_parser.SEPParser, "fetch_sep_markdown", autospec=True, side_effect=fetch), \
                mock.patch.object(sep_parser, "DATA_DIR", data_dir), mock.patch("sys.argv", ["sep_parser.py", "0023"]), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return sep_parser.main()

    def test_writes_the_definition_and_writes_nothing_for_a_broken_document(self) -> None:
        data_dir = Path(enter_context(self, tempfile.TemporaryDirectory()))
        self.assertEqual(self.run_main(replace_once(SPEC, "## Tests\n", "## Test data\n"), data_dir), 1)
        self.assertEqual(list(data_dir.rglob("*")), [])
        self.assertEqual(self.run_main(SPEC, data_dir), 0)
        written = json.loads((data_dir / "sep" / "sep_0023_definition.json").read_text(encoding="utf-8"))
        self.assertEqual(written["sections"], spec_definition()["sections"])


class Sep23AnalyzerCase(unittest.TestCase):
    """Base for tests that run the analyzer on a temporary SDK root."""

    def setUp(self) -> None:
        self.sdk_root = Path(enter_context(self, tempfile.TemporaryDirectory()))
        enter_context(self, mock.patch.object(sep_analyzer, "SDK_ROOT", self.sdk_root))
        test_dir = self.sdk_root / "stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk"
        self.required_files = (self.sdk_root / "stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/StrKey.kt",
                               test_dir / STRKEY_TEST, test_dir / CLAIMABLE_BALANCE_VECTORS)
        self.strkey = str(self.required_files[0].relative_to(self.sdk_root))

    def write_sdk(self, strkey: Optional[str] = STRKEY_KT, strkey_test: Optional[str] = EXACT_STRKEY_TEST_KT,
                  claimable_balance_vectors: Optional[str] = EXACT_CLAIMABLE_BALANCE_VECTORS_KT) -> None:
        for path in self.required_files:
            path.unlink(missing_ok=True)
        for path, text in zip(self.required_files, (strkey, strkey_test, claimable_balance_vectors)):
            if text is not None:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")

    def mappings(self, definition: Optional[Dict[str, Any]] = None, section_key: str = KEY_TYPES_KEY) -> Dict[str, Any]:
        mapped = sep_analyzer.SEPAnalyzer("0023").generate_field_mappings([], definition or spec_definition())
        return mapped[section_key]


class Sep23KeyTypeCheckTest(Sep23AnalyzerCase):

    def test_every_key_type_maps_to_its_function_pair(self) -> None:
        self.write_sdk()
        self.assertEqual(self.mappings(), {name: f"StrKey.encode{f} / decode{f}"
                                           for (name, *_), f in zip(KEY_TYPES, FUNCTIONS)})

    def test_a_value_that_differs_from_the_specification_is_not_implemented(self) -> None:
        edited = copy.deepcopy(spec_definition())
        section(edited, KEY_TYPES_KEY)["fields"][1]["value"] = 97
        for label, strkey, definition in (
                ("in the SDK", replace_once(STRKEY_KT, "ACCOUNT_ID((6 shl 3)", "ACCOUNT_ID((7 shl 3)"), None),
                ("in the specification", STRKEY_KT, edited)):
            with self.subTest(label):
                self.write_sdk(strkey=strkey)
                mapped = self.mappings(definition)
                self.assertEqual([name for name, symbol in mapped.items() if symbol is None],
                                 ["STRKEY_PUBKEY" if definition is None else "STRKEY_MUXED"])

    def test_a_key_type_mapped_to_none_is_not_implemented(self) -> None:
        self.write_sdk()
        with mock.patch.dict(sep_analyzer.SEPAnalyzer.SEP23_KEY_TYPES, {"STRKEY_CONTRACT": None}):
            mapped = self.mappings()
        self.assertIsNone(mapped["STRKEY_CONTRACT"])
        self.assertEqual(sum(symbol is not None for symbol in mapped.values()), 8)

    def test_every_value_form_evaluates(self) -> None:
        entries = STRKEY_KT[STRKEY_KT.index(ACCOUNT_ID_ENTRY):STRKEY_KT.index(";\n") + 2]
        rewritten = replace_once(STRKEY_KT, entries, (
            "        ACCOUNT_ID(48, 32..32),\n"
            "        MED25519_PUBLIC_KEY(0x60.toByte(), 40..40), SEED(0b1001_0000.toByte(), 32..32),\n"
            "        PRE_AUTH_TX(1__52.toByte(), 32..32), SHA256_HASH((23 shl 3), 32..32),\n"
            "        SIGNED_PAYLOAD((0xF shl 0b11).toByte(), 40..100),\n"
            "        CONTRACT(( 2 shl 3 ).toByte(), 32..32) { override fun toString() = \"C\" },\n"
            "        LIQUIDITY_POOL(0X58, 32..32), CLAIMABLE_BALANCE(0B1000, 33..33);\n"))
        self.write_sdk(strkey=rewritten)
        self.assertTrue(all(self.mappings().values()))

    def test_the_mapper_raises_for_a_missing_file(self) -> None:
        for index, path in enumerate(self.required_files[:2]):
            files = [STRKEY_KT, EXACT_STRKEY_TEST_KT, EXACT_CLAIMABLE_BALANCE_VECTORS_KT]
            files[index] = None
            with self.subTest(path.name):
                self.write_sdk(*files)
                with self.assertRaisesRegex(FileNotFoundError, "SEP-23 required file not found"):
                    self.mappings()

    def test_drift_raises_with_the_name_and_file(self) -> None:
        unmapped = copy.deepcopy(spec_definition())
        section(unmapped, KEY_TYPES_KEY)["fields"].append({"name": "STRKEY_NEW", "value": 48})
        cases = {
            "a renamed object": (replace_once(STRKEY_KT, "object StrKey {", "object StrKeys {"), None,
                                 f"SEP-23 object StrKey not found in {self.strkey}"),
            "a renamed enum": (replace_once(STRKEY_KT, "enum class VersionByte", "enum class Version"), None,
                               f"SEP-23 enum class VersionByte not found in {self.strkey}"),
            "an enum that never closes": (STRKEY_KT[:STRKEY_KT.index("    }\n")], None,
                                          f"SEP-23 enum class VersionByte in {self.strkey} has no closing brace"),
            "an absent entry": (replace_once(STRKEY_KT, ACCOUNT_ID_ENTRY, ""), None,
                                f"SEP-23 VersionByte.ACCOUNT_ID not found in {self.strkey}"),
            "an entry moved to another enum": (
                replace_once(STRKEY_KT, ACCOUNT_ID_ENTRY, "") + "\nprivate enum class Legacy(val value: Byte) { ACCOUNT_ID((6 shl 3).toByte()) }\n",
                None, f"SEP-23 VersionByte.ACCOUNT_ID not found in {self.strkey}"),
            "an absent encode function": (replace_once(STRKEY_KT, "fun encodeContract(", "fun encodeContractId("), None,
                                          f"SEP-23 function StrKey.encodeContract not found in {self.strkey}"),
            "an absent decode function": (replace_once(STRKEY_KT, "fun decodeContract(", "fun decodeContractId("), None,
                                          f"SEP-23 function StrKey.decodeContract not found in {self.strkey}"),
            "an unmapped key type": (STRKEY_KT, unmapped, "SEP-23 key type STRKEY_NEW has no entry in SEP23_KEY_TYPES"),
        }
        for value in ("SIX shl 3", "48L", "048", "value = 48"):
            cases[f"the value {value}"] = (replace_once(STRKEY_KT, "(6 shl 3).toByte()", value), None,
                                           f"SEP-23 cannot evaluate VersionByte.ACCOUNT_ID in {self.strkey}")
        for label, (strkey, definition, message) in cases.items():
            with self.subTest(label):
                self.write_sdk(strkey=strkey)
                with self.assertRaisesRegex(ValueError, exact(message)):
                    self.mappings(definition)


class Sep23VectorCheckTest(Sep23AnalyzerCase):

    def vectors(self, strkey_test: str = EXACT_STRKEY_TEST_KT, vectors: str = EXACT_CLAIMABLE_BALANCE_VECTORS_KT):
        self.write_sdk(strkey_test=strkey_test, claimable_balance_vectors=vectors)
        return self.mappings(section_key=VECTORS_KEY)

    def test_exact_fixtures_quote_every_vector(self) -> None:
        expected = {name: STRKEY_TEST for name in NAMES}
        expected["valid_08"] = CLAIMABLE_BALANCE_VECTORS
        self.assertEqual(self.vectors(), expected)

    def test_a_longer_or_padded_literal_does_not_quote_the_shorter_vector(self) -> None:
        mapped = self.vectors(COLLISION_STRKEY_TEST_KT, "internal object ClaimableBalanceVectors\n")
        self.assertEqual([name for name in NAMES if mapped[name]], ["invalid_03", "invalid_06"])

    def test_literals_joined_with_plus_count_only_as_a_whole(self) -> None:
        joined = replace_once(COLLISION_STRKEY_TEST_KT, f'"{VECTOR["invalid_03"]}"',
                              f'"{VECTOR["valid_01"][:30]}" + // first half\n        "{VECTOR["valid_01"][30:]}" +\n "A"')
        mapped = self.vectors(joined)
        self.assertIsNone(mapped["valid_01"])
        self.assertEqual(mapped["invalid_03"], STRKEY_TEST)

    def test_the_vector_is_read_from_the_file_its_definition_names(self) -> None:
        swapped_test = replace_once(EXACT_STRKEY_TEST_KT, "ClaimableBalanceVectors.strKey", f'"{VALID[7][1]}"')
        swapped_test = replace_once(swapped_test, f'"{VALID[0][1]}"', "ClaimableBalanceVectors.accountId")
        swapped_vectors = replace_once(EXACT_CLAIMABLE_BALANCE_VECTORS_KT, f'strKey = "{VALID[7][1]}"',
                                       f'accountId = "{VALID[0][1]}"')
        mapped = self.vectors(swapped_test, swapped_vectors)
        self.assertEqual((mapped["valid_01"], mapped["valid_08"], mapped["valid_02"]), (None, None, STRKEY_TEST))

    def test_comments_and_other_quotes_are_read_as_kotlin(self) -> None:
        edited = replace_once(EXACT_STRKEY_TEST_KT, "    private val valid0 =", "    // private val valid0 =")
        edited = replace_once(edited, f'    private val valid5 = "{VALID[5][1]}"\n', f'    /* "{VALID[5][1]}" */\n')
        edited = replace_once(edited, "    private val valid1 =", "    private val quote = '\"'; private val valid1 =")
        edited = replace_once(edited, "    private val valid6 =", '    private val raw = """a " quote"""; val valid6 =')
        mapped = self.vectors(edited)
        self.assertEqual((mapped["valid_01"], mapped["valid_06"]), (None, None))
        self.assertEqual((mapped["valid_02"], mapped["valid_07"]), (STRKEY_TEST, STRKEY_TEST))


class Sep23RunTest(Sep23AnalyzerCase):
    """Runs the analyzer and the comparison script with temporary data and output directories."""

    def setUp(self) -> None:
        super().setUp()
        self.data_dir = Path(enter_context(self, tempfile.TemporaryDirectory()))
        self.output_dir = Path(enter_context(self, tempfile.TemporaryDirectory()))
        (self.data_dir / "sep").mkdir()
        (self.data_dir / "sep" / "sep_0023_definition.json").write_text(json.dumps(spec_definition()), encoding="utf-8")
        for patcher in (mock.patch.object(sep_analyzer, "DATA_DIR", self.data_dir),
                        mock.patch.object(generate_sep_comparison, "DATA_DIR", self.data_dir),
                        mock.patch.object(generate_sep_comparison, "COMPATIBILITY_DIR", self.output_dir),
                        contextlib.redirect_stdout(io.StringIO())):
            enter_context(self, patcher)

    def render(self) -> str:
        implementation = sep_analyzer.SEPAnalyzer("0023").analyze()
        (self.data_dir / "sep" / "kmp_sep_0023_implementation.json").write_text(json.dumps(implementation), encoding="utf-8")
        with mock.patch("sys.argv", ["generate_sep_comparison.py", "0023"]):
            self.assertEqual(generate_sep_comparison.main(), 0)
        return (self.output_dir / "sep" / MATRIX).read_text(encoding="utf-8")

    def test_the_sdk_renders_the_tracked_matrix(self) -> None:
        with mock.patch.object(sep_analyzer, "SDK_ROOT", common.SDK_ROOT):
            rendered = self.render()
        tracked = (common.COMPATIBILITY_DIR / "sep" / MATRIX).read_text(encoding="utf-8")
        # Only the values of the generation time and the SDK version are ignored: both change
        # between regenerations, and a dropped line still fails.
        varying = re.compile(r"^(\*\*(?:Generated|SDK Version):\*\*) .*$", re.MULTILINE)
        self.assertEqual(varying.sub(r"\1", rendered), varying.sub(r"\1", tracked))

    def test_missing_vectors_count_as_not_implemented(self) -> None:
        self.write_sdk(strkey_test=COLLISION_STRKEY_TEST_KT, claimable_balance_vectors="object ClaimableBalanceVectors\n")
        matrix = self.render()
        self.assertTrue(matrix.startswith("# SEP-0023 (Strkeys) Compatibility Matrix\n\n**Generated:** "))
        self.assertIn("## Overall Coverage\n\n**Total Coverage:** 34.38% (11/32 fields)\n\n"
                      "- ✅ **Implemented:** 11/32\n- ❌ **Not Implemented:** 21/32\n\n"
                      "**Required Fields:** 34.38% (11/32)\n\n**Optional Fields:** 100% (0/0)\n\n", matrix)
        self.assertIn(f"| {VECTORS_TITLE} | 8.7% | 2/23 | 2 | 23 |\n", matrix)

    def test_reads_strkey_and_both_test_files(self) -> None:
        self.write_sdk()
        result = sep_analyzer.SEPAnalyzer("0023").analyze()
        self.assertEqual([result["files"], result["test_files"]], [
            [self.strkey], [str(path.relative_to(self.sdk_root)) for path in sorted(self.required_files[1:])]])

    def test_a_missing_or_unreadable_file_raises_with_its_path(self) -> None:
        for index, path in enumerate(self.required_files):
            relative = str(path.relative_to(self.sdk_root))
            files = [STRKEY_KT, EXACT_STRKEY_TEST_KT, EXACT_CLAIMABLE_BALANCE_VECTORS_KT]
            files[index] = None
            with self.subTest(f"missing {path.name}"):
                self.write_sdk(*files)
                with self.assertRaisesRegex(FileNotFoundError, exact(f"SEP-23 required file not found: {relative}")):
                    sep_analyzer.SEPAnalyzer("0023").analyze()
            with self.subTest(f"unreadable {path.name}"):
                self.write_sdk()
                path.write_bytes(b"\xff\xfe not UTF-8")
                with self.assertRaisesRegex(OSError, "^" + re.escape(f"SEP-23 cannot read {relative}: ")):
                    sep_analyzer.SEPAnalyzer("0023").analyze()

    def test_main_exits_non_zero_and_writes_nothing_on_a_failed_check(self) -> None:
        self.write_sdk(strkey=replace_once(STRKEY_KT, "enum class VersionByte", "enum class Version"))
        with mock.patch("sys.argv", ["sep_analyzer.py", "0023"]), \
                self.assertRaisesRegex(ValueError, "enum class VersionByte not found"):
            sep_analyzer.main()
        self.assertEqual([p.name for p in (self.data_dir / "sep").iterdir()], ["sep_0023_definition.json"])


class Sep23DetectionTest(unittest.TestCase):

    def test_sep_23_is_detected_when_strkey_exists(self) -> None:
        root = Path(enter_context(self, tempfile.TemporaryDirectory()))
        for name in ("_SEP_SOURCE_DIR", "_SEP29_CHECKER_FILE", "_KEYPAIR_FILE", "_CONTRACT_PARSER_FILE",
                     "_XDR_JSON_FILE", "_XDR_JSON_TYPE_FILE"):
            enter_context(self, mock.patch.object(run_analysis, name, root / "absent" / name))
        enter_context(self, mock.patch.object(run_analysis, "_STRKEY_FILE", root / "StrKey.kt"))
        self.assertEqual(run_analysis.AnalysisOrchestrator().detect_implemented_seps(), [])
        (root / "StrKey.kt").write_text("object Renamed\n", encoding="utf-8")
        self.assertEqual(run_analysis.AnalysisOrchestrator().detect_implemented_seps(), ["0023"])


if __name__ == "__main__":
    unittest.main()
