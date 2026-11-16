"""SEP-51 (XDR-JSON) matrix: the Address Types rows the parser lists and the analyzer marks."""

import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from typing import Any, Dict, Optional
from unittest import mock

import matrix_test_support as support

import common
import sep_analyzer
import sep_parser

enter_context = support.enter_context

ADDRESS_TYPES_KEY = "address_types"

# The Address Types rows in the order of the SEP-51 list of Stellar-specific types.
ADDRESS_TYPES = [
    "sc_address", "account_id", "contract_id", "muxed_account", "muxed_account_med25519",
    "muxed_ed25519_account", "muxed_contract", "pool_id", "claimable_balance_id", "public_key", "node_id",
    "signer_key", "signer_key_ed25519_signed_payload",
]

# The XDR-JSON reader of SCAddressXdr.kt as the XDR generator emits it, one arm per strkey prefix.
SC_ADDRESS_W_ARM = "        'W' -> MuxedContract(MuxedContractXdr.fromXdrJsonTree(element))\n"
SC_ADDRESS_KT = (
    "package com.soneso.stellar.sdk.xdr\n\nsealed class SCAddressXdr {\n  companion object {\n"
    "    internal fun fromXdrJsonTree(element: JsonElement): SCAddressXdr =\n"
    "      when (XdrJson.name(element, XDR_JSON_TYPE).firstOrNull()) {\n"
    "        'G' -> AccountId(AccountIDXdr.fromXdrJsonTree(element))\n"
    "        'C' -> ContractId(ContractIDXdr.fromXdrJsonTree(element))\n"
    "        'M' -> MuxedAccount(MuxedEd25519AccountXdr.fromXdrJsonTree(element))\n"
    "        'B' -> ClaimableBalanceId(ClaimableBalanceIDXdr.fromXdrJsonTree(element))\n"
    "        'L' -> LiquidityPoolId(PoolIDXdr.fromXdrJsonTree(element))\n"
    + SC_ADDRESS_W_ARM
    + "        else -> XdrJson.fail(XDR_JSON_TYPE, \"expects a G, C, M, B, L or W strkey\")\n"
    "      }\n  }\n}\n"
)

# The XDR-JSON reader and writer of MuxedContractXdr.kt as the XDR generator emits them.
MUXED_CONTRACT_DECODE = (
    "      val payload = XdrJson.strkey(XdrJson.name(element, XDR_JSON_TYPE), XDR_JSON_TYPE, \"a W strkey\") "
    "{ StrKey.decodeMuxedContract(it) }\n"
)
MUXED_CONTRACT_ENCODE = (
    "  fun toXdrJsonElement(): JsonElement = "
    "XdrJson.name(StrKey.encodeMuxedContract(XdrJson.muxedPayload(contractId.value.value, id.value)))\n"
)
MUXED_CONTRACT_KT = (
    "package com.soneso.stellar.sdk.xdr\n\nimport com.soneso.stellar.sdk.StrKey\n\n"
    "data class MuxedContractXdr(val id: Uint64Xdr, val contractId: ContractIDXdr) {\n  companion object {\n"
    "    internal fun fromXdrJsonTree(element: JsonElement): MuxedContractXdr {\n"
    + MUXED_CONTRACT_DECODE
    + "      return MuxedContractXdr(Uint64Xdr(XdrJson.muxedId(payload)), "
    "ContractIDXdr(HashXdr(payload.copyOfRange(0, 32))))\n    }\n  }\n\n"
    + MUXED_CONTRACT_ENCODE
    + "}\n"
)


def replace_once(text: str, old: str, new: str) -> str:
    """Return *text* with the single occurrence of *old* replaced; fail if it occurs otherwise."""
    if text.count(old) != 1:
        raise AssertionError(f"expected exactly one occurrence of {old!r}, found {text.count(old)}")
    return text.replace(old, new)


def spec_definition() -> Dict[str, Any]:
    """Return the SEP-51 definition; its sections are transcribed in the parser and need no document."""
    with contextlib.redirect_stdout(io.StringIO()):
        return sep_parser.SEPParser("0051").parse()


def address_types(definition: Dict[str, Any]) -> Dict[str, Any]:
    return next(s for s in definition["sections"] if s["key"] == ADDRESS_TYPES_KEY)


class Sep51ParserTest(unittest.TestCase):

    def test_address_types_follow_the_specification_list(self) -> None:
        fields = address_types(spec_definition())["fields"]
        self.assertEqual([f["name"] for f in fields], ADDRESS_TYPES)
        self.assertTrue(all(f["required"] for f in fields))
        descriptions = {f["name"]: f["description"] for f in fields}
        self.assertEqual(descriptions["sc_address"], "SCAddress maps to a G, C, M, B, L or W strkey depending on the arm")
        self.assertEqual(descriptions["muxed_contract"], "MuxedContract maps to a W strkey")


class Sep51AddressDetectionTest(unittest.TestCase):
    """Runs the analyzer on a temporary SDK root that holds only the given generated XDR files,
    and on the SDK sources of this repository."""

    def setUp(self) -> None:
        self.sdk_root = Path(enter_context(self, tempfile.TemporaryDirectory()))
        enter_context(self, mock.patch.object(sep_analyzer, "SDK_ROOT", self.sdk_root))
        self.xdr_dir = self.sdk_root / common.SDK_PACKAGE_PATH / "xdr"
        self.xdr_dir.mkdir(parents=True)

    def mappings(self, sc_address: str = SC_ADDRESS_KT,
                 muxed_contract: Optional[str] = MUXED_CONTRACT_KT) -> Dict[str, Optional[str]]:
        (self.xdr_dir / "SCAddressXdr.kt").write_text(sc_address, encoding="utf-8")
        if muxed_contract is not None:
            (self.xdr_dir / "MuxedContractXdr.kt").write_text(muxed_contract, encoding="utf-8")
        mapped = sep_analyzer.SEPAnalyzer("0051").generate_field_mappings([], spec_definition())
        return mapped[ADDRESS_TYPES_KEY]

    def test_w_arm_and_muxed_contract_conversion_mark_both_rows(self) -> None:
        mapped = self.mappings()
        self.assertEqual(mapped["sc_address"], "SCAddressXdr.toXdrJsonElement")
        self.assertEqual(mapped["muxed_contract"], "MuxedContractXdr.toXdrJsonElement")

    def test_sc_address_without_the_w_arm_leaves_sc_address_unmarked(self) -> None:
        mapped = self.mappings(sc_address=replace_once(SC_ADDRESS_KT, SC_ADDRESS_W_ARM, ""))
        self.assertIsNone(mapped["sc_address"])
        self.assertEqual(mapped["muxed_contract"], "MuxedContractXdr.toXdrJsonElement")

    def test_absent_muxed_contract_source_leaves_muxed_contract_unmarked(self) -> None:
        mapped = self.mappings(muxed_contract=None)
        self.assertIsNone(mapped["muxed_contract"])
        self.assertEqual(mapped["sc_address"], "SCAddressXdr.toXdrJsonElement")

    def test_muxed_contract_without_its_reader_or_writer_leaves_muxed_contract_unmarked(self) -> None:
        for label, line in (("reader", MUXED_CONTRACT_DECODE), ("writer", MUXED_CONTRACT_ENCODE)):
            with self.subTest(label):
                mapped = self.mappings(muxed_contract=replace_once(MUXED_CONTRACT_KT, line, ""))
                self.assertIsNone(mapped["muxed_contract"])

    def test_the_sdk_marks_every_row(self) -> None:
        with mock.patch.object(sep_analyzer, "SDK_ROOT", common.SDK_ROOT):
            mapped = sep_analyzer.SEPAnalyzer("0051").generate_field_mappings([], spec_definition())
        self.assertEqual(sum(len(rows) for rows in mapped.values()), 38)
        self.assertEqual([name for rows in mapped.values() for name, symbol in rows.items() if symbol is None], [])


if __name__ == "__main__":
    unittest.main()
