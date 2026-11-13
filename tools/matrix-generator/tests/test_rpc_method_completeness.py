"""Every mapped RPC method reaches the matrix, or the run fails."""

import contextlib
import io
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Dict, Mapping, Optional, Union
from unittest import mock

import matrix_test_support as support

import common
import github_fetcher
import rpc_parser
import run_rpc_analysis
from github_fetcher import (
    GitHubFetchError,
    GitHubRelease,
    SourceFileNotFoundError,
    fetch_all_rpc_response_files,
)
from generate_rpc_comparison import RPCComparisonAnalyzer
from rpc_parser import GoProtocolParser, RPCMethodExtractor, _registered_prefixes, verify_method_set

enter_context = support.enter_context

ALL_METHODS = sorted(GoProtocolParser.METHOD_NAME_MAPPING.values())
# The method prefixes stellar-rpc v30.0.0 and v30.0.1 register, in their source order.
SERVED_PREFIXES_V30 = [
    "GetHealth", "GetEvents", "GetNetwork", "GetVersionInfo", "GetLatestLedger", "GetLedgers",
    "GetLedgerEntries", "GetTransaction", "GetTransactions", "SendTransaction",
    "SimulateTransaction", "GetFeeStats",
]
BASE_URL = "https://raw.githubusercontent.com/stellar/go-stellar-sdk/v0.7.2/protocols/rpc/"


def _prefix_for_file(file_stem: str) -> str:
    return next(p for p in support.METHOD_PREFIXES if common.camel_to_snake(p) == file_stem)


def _serve_request_files(
    overrides: Optional[Mapping[str, Union[str, Exception]]] = None,
) -> Callable[[str], bytes]:
    """Return a ``fetch_url`` stand-in that serves request files by name.

    *overrides* maps a file stem to the content served in place of the
    default one-field request file, or to an exception the stand-in raises.
    """
    def fetch_url(url: str) -> bytes:
        stem = url.rsplit("/", 1)[1][:-len(".go")]
        override = (overrides or {}).get(stem)
        if isinstance(override, Exception):
            raise override
        if override is not None:
            return override.encode("utf-8")
        return support.request_file(_prefix_for_file(stem)).encode("utf-8")
    return fetch_url


def _serve_response_files(failing_stem: Optional[str] = None,
                          content: Optional[str] = None) -> Callable[[str, str], str]:
    """Return a ``fetch_rpc_response_file`` stand-in serving response files by name.

    For *failing_stem*, the stand-in returns *content* when given, else raises
    ``SourceFileNotFoundError``.
    """
    def fetch_rpc_response_file(tag: str, stem: str) -> str:
        if stem == failing_stem:
            if content is not None:
                return content
            raise SourceFileNotFoundError(f"Failed to fetch {stem}.go: HTTP 404")
        return support.response_file(_prefix_for_file(stem))
    return fetch_rpc_response_file


class RequestFileTest(unittest.TestCase):

    def parse_with(self, fetch_url: Callable[[str], bytes]) -> Dict[str, Any]:
        with mock.patch.object(rpc_parser, "fetch_url", side_effect=fetch_url):
            return GoProtocolParser(BASE_URL).parse_all_methods()

    def test_parses_every_mapped_method(self) -> None:
        methods = self.parse_with(_serve_request_files())
        self.assertEqual(sorted(methods), ALL_METHODS)
        self.assertEqual(methods["getEvents"]["required_params"], ["startLedger"])

    def test_empty_request_struct_means_no_parameters(self) -> None:
        methods = self.parse_with(_serve_request_files({
            "get_health": "package protocol\n\ntype GetHealthRequest struct{}\n",
        }))
        self.assertEqual(methods["getHealth"]["struct_name"], "GetHealthRequest")
        self.assertEqual(methods["getHealth"]["required_params"], [])
        self.assertEqual(methods["getHealth"]["optional_params"], [])

    def test_file_without_request_struct_raises(self) -> None:
        with self.assertRaisesRegex(
            ValueError, "getEvents: request struct GetEventsRequest not found in get_events.go"
        ):
            self.parse_with(_serve_request_files({"get_events": support.response_file("GetEvents")}))

    def test_missing_file_propagates(self) -> None:
        missing = SourceFileNotFoundError("HTTP 404 error fetching get_events.go")
        with self.assertRaises(SourceFileNotFoundError):
            self.parse_with(_serve_request_files({"get_events": missing}))

    def test_network_error_propagates(self) -> None:
        network = GitHubFetchError("Network error fetching get_events.go: timed out")
        with self.assertRaisesRegex(GitHubFetchError, "timed out"):
            self.parse_with(_serve_request_files({"get_events": network}))

    def test_parse_error_propagates(self) -> None:
        original = GoProtocolParser._parse_request_content

        def parse(parser: GoProtocolParser, content: str, prefix: str) -> Any:
            if prefix == "GetEvents":
                raise ValueError("unbalanced struct body")
            return original(parser, content, prefix)

        with mock.patch.object(GoProtocolParser, "_parse_request_content", parse):
            with self.assertRaisesRegex(ValueError, "unbalanced struct body"):
                self.parse_with(_serve_request_files())


class ResponseFileTest(unittest.TestCase):

    def setUp(self) -> None:
        enter_context(self, mock.patch.object(
            github_fetcher, "_resolve_go_stellar_sdk_ref", return_value="v0.7.2"
        ))
        self.extractor = RPCMethodExtractor(support.RELEASE_INFO)
        self.data = {
            "metadata": {"rpc_version": "v28.0.1"},
            "methods": {name: {} for name in ALL_METHODS},
        }

    def serve(self, fetch_rpc_response_file: Callable[[str, str], str]) -> None:
        enter_context(self, mock.patch.object(
            github_fetcher, "fetch_rpc_response_file", side_effect=fetch_rpc_response_file
        ))

    def test_fetch_all_raises_on_a_failed_file(self) -> None:
        self.serve(_serve_response_files("get_events"))
        with self.assertRaisesRegex(SourceFileNotFoundError, "get_events"):
            fetch_all_rpc_response_files("v28.0.1", ["getHealth", "getEvents", "getNetwork"])

    def test_enrichment_raises_on_a_failed_file(self) -> None:
        self.serve(_serve_response_files("get_events"))
        with self.assertRaisesRegex(SourceFileNotFoundError, "get_events"):
            self.extractor.enrich_with_response_fields(self.data)

    def test_enrichment_raises_on_a_file_without_response_fields(self) -> None:
        self.serve(_serve_response_files("get_events", content="package protocol\n"))
        with self.assertRaisesRegex(ValueError, "getEvents"):
            self.extractor.enrich_with_response_fields(self.data)

    def test_enrichment_adds_fields_to_every_method(self) -> None:
        self.serve(_serve_response_files())
        self.extractor.enrich_with_response_fields(self.data)
        for name in ALL_METHODS:
            self.assertEqual(
                self.data["methods"][name]["response_fields"],
                [{"field_name": "LatestLedger", "json_name": "latestLedger"}],
                name,
            )

    def test_embedded_struct_declared_in_another_file_resolves(self) -> None:
        files = {
            "get_transaction": "type GetTransactionResponse struct {\n\tLatestLedger uint32 `json:\"latestLedger\"`\n"
                               "\tTransactionDetails\n\tLedgerCloseTime int64 `json:\"createdAt,string\"`\n}\n",
            "get_transactions": "type TransactionDetails struct {\n\tStatus string `json:\"status\"`\n}\n"
                                + support.response_file("GetTransactions"),
        }
        self.serve(lambda tag, stem: files.get(stem) or support.response_file(_prefix_for_file(stem)))
        self.extractor.enrich_with_response_fields(self.data)
        fields = self.data["methods"]["getTransaction"]["response_fields"]
        self.assertEqual([f["json_name"] for f in fields], ["latestLedger", "status", "createdAt"])

    def test_undeclared_embedded_struct_raises(self) -> None:
        content = "type GetTransactionResponse struct {\n\tTransactionDetails\n}\n"
        self.serve(_serve_response_files("get_transaction", content=content))
        with self.assertRaisesRegex(ValueError, "GetTransactionResponse embeds TransactionDetails"):
            self.extractor.enrich_with_response_fields(self.data)

    def test_json_variants_are_not_counted(self) -> None:
        metadata = {"rpc_version": "v28.0.1", "rpc_release_date": "2026-08-27", "rpc_release_url": ""}
        with mock.patch("generate_rpc_comparison.get_sdk_version", return_value="1.14.0"):
            analyzer = RPCComparisonAnalyzer({"metadata": metadata, "methods": {}}, {})
        metrics = analyzer._compare_response_fields(["status", "envelopeXdr", "envelopeJson"], ["status"])
        self.assertEqual((metrics.total, metrics.supported, metrics.missing), (2, 1, ["envelopeXdr"]))

    def test_json_variants_are_not_counted_for_a_missing_method(self) -> None:
        metadata = {"rpc_version": "v28.0.1", "rpc_release_date": "2026-08-27", "rpc_release_url": ""}
        fields = [{"json_name": "envelopeXdr"}, {"json_name": "envelopeJson"}]
        with mock.patch("generate_rpc_comparison.get_sdk_version", return_value="1.14.0"):
            analyzer = RPCComparisonAnalyzer(
                {"metadata": metadata, "methods": {"getTransaction": {"response_fields": fields}}}, {})
        analyzer.analyze()
        self.assertEqual(analyzer.comparisons[0].response_fields.missing, ["envelopeXdr"])


class MethodSetTest(unittest.TestCase):
    """The mapped methods are checked against the registrations in jsonrpc.go."""

    def assert_rejected(self, source: str, *parts: str) -> None:
        with self.assertRaises(ValueError) as context:
            verify_method_set(source, "v30.0.1")
        for part in ("v30.0.1",) + parts:
            self.assertIn(part, str(context.exception))

    def test_full_set_passes(self) -> None:
        verify_method_set(support.jsonrpc_file(), "v28.0.1")

    def test_missing_method_is_named(self) -> None:
        prefixes = [p for p in support.METHOD_PREFIXES if p != "GetEvents"]
        with self.assertRaisesRegex(ValueError, r"missing: getEvents\b"):
            verify_method_set(support.jsonrpc_file(prefixes), "v28.0.1")

    def test_unexpected_method_is_named(self) -> None:
        prefixes = support.METHOD_PREFIXES + ("GetLedgerState",)
        with self.assertRaisesRegex(ValueError, r"unexpected: getLedgerState\b"):
            verify_method_set(support.jsonrpc_file(prefixes), "v28.0.1")

    def test_string_literal_registrations_raise(self) -> None:
        self.assert_rejected('handlers := []struct {\n\tmethodName string\n}{\n\t{methodName: "getHealth"},\n}\n',
                             'unrecognized method registration entry: {methodName: "getHealth"}')

    def test_both_registration_forms_register_the_served_methods(self) -> None:
        for fixture in (support.RPC_V30_0_0_SOURCE, support.RPC_V30_0_1_SOURCE):
            with self.subTest(fixture=fixture.name):
                source = fixture.read_text(encoding="utf-8")
                self.assertEqual(_registered_prefixes(source, "v30.0.1"), SERVED_PREFIXES_V30)
                verify_method_set(source, "v30.0.1")

    def test_comments_and_literals_in_the_block_are_not_entries_or_brackets(self) -> None:
        cases = {
            support.RPC_V30_0_1_SOURCE: ("\t\tprotocol.GetFeeStatsMethodName: {",
                                         "\t\t// protocol.GetFooMethodName: {},\n"
                                         "\t\t/* protocol.GetBarMethodName: {}, */ protocol.GetFeeStatsMethodName: {"),
            support.RPC_V30_0_0_SOURCE: ("longName:             toSnakeCase(protocol.GetHealthMethodName),",
                                         'longName:             "get_health) // }", // ignored },'),
        }
        for fixture, (old, new) in cases.items():
            with self.subTest(fixture=fixture.name):
                source = fixture.read_text(encoding="utf-8")
                self.assertIn(old, source)
                self.assertEqual(_registered_prefixes(source.replace(old, new), "v30.0.1"),
                                 SERVED_PREFIXES_V30)

    def test_unrecognized_entry_is_named(self) -> None:
        cases = {
            support.RPC_V30_0_1_SOURCE: ("protocol.GetFeeStatsMethodName: {", '"getFeeStats": {'),
            support.RPC_V30_0_0_SOURCE: ("methodName:           protocol.GetFeeStatsMethodName,",
                                         'methodName: "getFeeStats",'),
        }
        for fixture, (old, new) in cases.items():
            with self.subTest(fixture=fixture.name):
                source = fixture.read_text(encoding="utf-8")
                self.assertIn(old, source)
                self.assert_rejected(source.replace(old, new), "unrecognized method registration entry", new)

    def test_handler_without_exactly_one_method_name_is_named(self) -> None:
        source = support.RPC_V30_0_0_SOURCE.read_text(encoding="utf-8")
        old = "\t\t\tmethodName: protocol.GetHealthMethodName,\n"
        self.assertIn(old, source)
        for count in (0, 2):
            with self.subTest(method_names=count):
                self.assert_rejected(source.replace(old, old * count),
                                     "unrecognized method registration entry", "methods.NewHealthCheck(")

    def test_method_name_reference_outside_the_block_is_named(self) -> None:
        reference = "protocol.GetLedgerStateMethodName"
        cases = {
            support.RPC_V30_0_1_SOURCE: ("\tspecs = jsonrpc.LimitsByMethod{\n",
                                         f"\tspecs = append(specs, jsonrpc.HandlerSpec{{MethodName: {reference}}})\n"),
            support.RPC_V30_0_0_SOURCE: ("\thandlersMap := handler.Map{}\n",
                                         "\thandlers = append(handlers, handlers[0])\n"
                                         f"\thandlers[len(handlers)-1].methodName = {reference}\n"),
        }
        for fixture, (anchor, added) in cases.items():
            source = fixture.read_text(encoding="utf-8")
            self.assertEqual(source.count(anchor), 1)
            for spelling in (reference, "protocol.\n\t\tGetLedgerStateMethodName"):
                with self.subTest(fixture=fixture.name, spelling=spelling):
                    self.assert_rejected(source.replace(anchor, added.replace(reference, spelling) + anchor),
                                         f"method name reference outside the registration block: {reference}")
            with self.subTest(fixture=fixture.name, spelling="comment and literals"):
                quoted = f'\t// {reference}\n\t_ = "{reference}" + `{reference}`\n'
                verify_method_set(source.replace(anchor, quoted + anchor), "v30.0.1")

    def test_empty_block_raises(self) -> None:
        self.assert_rejected("specs = jsonrpc.LimitsByMethod{\n}.Apply(specs)\n", "registration block is empty")

    def test_source_without_exactly_one_block_raises(self) -> None:
        v30_0_1 = support.RPC_V30_0_1_SOURCE.read_text(encoding="utf-8")
        for source, count in (("package rpcv1\n", "0"),
                              (v30_0_1 + support.RPC_V30_0_0_SOURCE.read_text(encoding="utf-8"), "2")):
            with self.subTest(blocks=count):
                self.assert_rejected(source, f"holds {count} method registration blocks")

    def test_unclosed_block_raises(self) -> None:
        self.assert_rejected("specs = jsonrpc.LimitsByMethod{\n\tprotocol.GetHealthMethodName: {},\n",
                             "is not closed")


class PipelineMethodSetTest(unittest.TestCase):

    def setUp(self) -> None:
        tmp = Path(enter_context(self, tempfile.TemporaryDirectory()))
        enter_context(self, mock.patch.object(run_rpc_analysis, "DATA_DIR", tmp / "data"))
        enter_context(self, contextlib.redirect_stdout(io.StringIO()))
        self.extractor = mock.Mock(spec=RPCMethodExtractor)
        self.extractor.extract_methods.return_value = {"metadata": {}, "methods": {}}
        self.pipeline = run_rpc_analysis.RPCAnalysisPipeline(rpc_version="v28.0.1")
        self.pipeline.release_info = support.RELEASE_INFO

    def run_with(self, jsonrpc_source: str) -> mock.Mock:
        self.pipeline.jsonrpc_source = jsonrpc_source
        with mock.patch.object(run_rpc_analysis, "RPCMethodExtractor", return_value=self.extractor) as cls:
            self.pipeline.parse_rpc_methods()
        return cls

    def test_unregistered_method_raises_before_parsing(self) -> None:
        prefixes = [p for p in support.METHOD_PREFIXES if p != "GetEvents"]
        with self.assertRaisesRegex(ValueError, "missing: getEvents"):
            self.run_with(support.jsonrpc_file(prefixes))
        self.assertFalse(self.pipeline.rpc_methods_file.exists())
        self.extractor.extract_methods.assert_not_called()

    def test_registration_outside_the_block_raises_before_parsing(self) -> None:
        source = support.jsonrpc_file() + "\nfunc init() { handlers[0].methodName = protocol.GetLedgerStateMethodName }\n"
        with self.assertRaisesRegex(ValueError, "RPC source v28.0.1: method name reference outside the "
                                                "registration block: protocol.GetLedgerStateMethodName"):
            self.run_with(source)
        self.assertFalse(self.pipeline.rpc_methods_file.exists())
        self.extractor.extract_methods.assert_not_called()

    def test_source_without_a_registration_block_raises_naming_the_release(self) -> None:
        with self.assertRaisesRegex(ValueError, "RPC source v28.0.1 holds 0 method registration blocks"):
            self.run_with("package internal\n")
        self.assertFalse(self.pipeline.rpc_methods_file.exists())

    def test_registered_method_set_is_written(self) -> None:
        cls = self.run_with(support.jsonrpc_file())
        cls.assert_called_once_with(support.RELEASE_INFO, protocol_source=None)
        self.assertTrue(self.pipeline.rpc_methods_file.exists())
        self.extractor.enrich_with_response_fields.assert_called_once()



class PipelineRequestStructTest(unittest.TestCase):
    """The full RPC pipeline over fixtures, with every network call patched."""

    def setUp(self) -> None:
        self.root = Path(enter_context(self, tempfile.TemporaryDirectory()))
        (self.root / "gradle.properties").write_text("version=1.14.0\n", encoding="utf-8")
        soroban_server = (
            self.root / "stellar-sdk" / "src" / "commonMain" / "kotlin" / "com" / "soneso"
            / "stellar" / "sdk" / "rpc" / "SorobanServer.kt"
        )
        soroban_server.parent.mkdir(parents=True)
        soroban_server.write_text("package com.soneso.stellar.sdk.rpc\n", encoding="utf-8")
        self.data_dir = self.root / "tools" / "matrix-generator" / "data"
        self.compatibility_dir = self.root / "compatibility"

        release = GitHubRelease(
            version="v28.0.1",
            published_at=datetime(2026, 8, 27, 18, 40, 46),
            html_url="https://github.com/stellar/stellar-rpc/releases/tag/v28.0.1",
        )
        sdk_analysis = {"metadata": {"total_methods": 0}, "implemented_methods": {}}
        for patcher in (
            mock.patch.object(common, "SDK_ROOT", self.root),
            mock.patch.object(run_rpc_analysis, "SDK_ROOT", self.root),
            mock.patch.object(run_rpc_analysis, "DATA_DIR", self.data_dir),
            mock.patch.object(run_rpc_analysis, "COMPATIBILITY_DIR", self.compatibility_dir),
            mock.patch.object(run_rpc_analysis, "get_latest_rpc_release", return_value=release),
            mock.patch.object(run_rpc_analysis, "fetch_rpc_jsonrpc_source",
                              return_value=support.jsonrpc_file()),
            mock.patch.object(run_rpc_analysis, "SorobanSDKAnalyzer", **{
                "return_value.analyze.return_value": sdk_analysis,
            }),
            mock.patch.object(github_fetcher, "_resolve_go_stellar_sdk_ref", return_value="v0.7.2"),
            mock.patch.object(github_fetcher, "fetch_rpc_response_file",
                              side_effect=_serve_response_files()),
            contextlib.redirect_stdout(io.StringIO()),
        ):
            enter_context(self, patcher)

    def run_pipeline(self, fetch_url: Callable[[str], bytes]) -> int:
        with mock.patch.object(rpc_parser, "fetch_url", side_effect=fetch_url):
            return run_rpc_analysis.RPCAnalysisPipeline().run()

    def test_file_without_request_struct_exits_non_zero_and_writes_nothing(self) -> None:
        exit_code = self.run_pipeline(
            _serve_request_files({"get_events": support.response_file("GetEvents")})
        )
        self.assertEqual(exit_code, 1)
        self.assertEqual(list(self.data_dir.rglob("*.json")), [])
        self.assertFalse(self.compatibility_dir.exists())

    def test_complete_fixture_writes_the_matrix(self) -> None:
        self.assertEqual(self.run_pipeline(_serve_request_files()), 0)
        matrix = (self.compatibility_dir / "rpc" / "RPC_COMPATIBILITY_MATRIX.md").read_text(
            encoding="utf-8"
        )
        self.assertIn("**RPC Version:** v28.0.1 (released 2026-08-27)  \n", matrix)
        url = support.RELEASE_INFO["html_url"]
        self.assertIn(f"**RPC Source:** [{url}]({url})  \n", matrix)
        self.assertIn("- ❌ **Not Supported:** 12/12\n", matrix)
        # getEvents lists the one required parameter parsed from its request struct and no
        # Kotlin method.
        self.assertIn("| `getEvents` | ❌ Not Supported | - | 0/1 | 0/1 |", matrix)


if __name__ == "__main__":
    unittest.main()
