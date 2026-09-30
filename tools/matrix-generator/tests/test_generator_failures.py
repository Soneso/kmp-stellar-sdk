"""Lookups that the matrices depend on raise on failure."""

import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import matrix_test_support as support

import common
import github_fetcher
import rpc_parser
from github_fetcher import GitHubFetchError, SourceFileNotFoundError
from rpc_parser import GoProtocolParser, RPCMethodExtractor

enter_context = support.enter_context

GO_MOD_URL = "https://raw.githubusercontent.com/stellar/stellar-rpc/v28.0.1/go.mod"


class ReleaseLookupTest(unittest.TestCase):

    def test_extract_methods_raises_when_the_release_lookup_fails(self) -> None:
        extractor = RPCMethodExtractor(protocol_source="https://example.invalid/protocols/rpc/")
        with mock.patch.object(
            rpc_parser, "get_latest_rpc_release", side_effect=GitHubFetchError("HTTP 502")
        ), mock.patch.object(GoProtocolParser, "parse_all_methods") as parse_all_methods:
            with self.assertRaisesRegex(GitHubFetchError, "HTTP 502"):
                extractor.extract_methods()
        parse_all_methods.assert_not_called()


class GoStellarSdkRefTest(unittest.TestCase):

    def setUp(self) -> None:
        enter_context(self, mock.patch.dict(github_fetcher._GO_SDK_REF_BY_RPC_TAG, clear=True))

    def test_unfetchable_go_mod_raises(self) -> None:
        with mock.patch.object(
            github_fetcher, "_make_request",
            side_effect=GitHubFetchError(f"HTTP 404 error fetching {GO_MOD_URL}: Not Found"),
        ):
            with self.assertRaisesRegex(GitHubFetchError, "HTTP 404"):
                RPCMethodExtractor(rpc_version="v28.0.1")
        self.assertNotIn("v28.0.1", github_fetcher._GO_SDK_REF_BY_RPC_TAG)

    def test_go_mod_without_go_stellar_sdk_requirement_raises(self) -> None:
        with mock.patch.object(
            github_fetcher, "_make_request",
            return_value=b"module github.com/stellar/stellar-rpc\n\ngo 1.24\n",
        ):
            with self.assertRaisesRegex(ValueError, "no github.com/stellar/go-stellar-sdk requirement"):
                RPCMethodExtractor(rpc_version="v28.0.1")
        self.assertNotIn("v28.0.1", github_fetcher._GO_SDK_REF_BY_RPC_TAG)

    def test_pinned_release_is_the_ref(self) -> None:
        with mock.patch.object(
            github_fetcher, "_make_request",
            return_value=b"require (\n\tgithub.com/stellar/go-stellar-sdk v0.7.2\n)\n",
        ) as make_request:
            self.assertEqual(github_fetcher._resolve_go_stellar_sdk_ref("v28.0.1"), "v0.7.2")
        make_request.assert_called_once_with(GO_MOD_URL)

    def test_protocol_parser_default_source_raises_when_the_release_lookup_fails(self) -> None:
        with mock.patch.object(
            rpc_parser, "get_latest_go_stellar_sdk_release",
            side_effect=GitHubFetchError("HTTP 502"),
        ):
            with self.assertRaisesRegex(GitHubFetchError, "HTTP 502"):
                GoProtocolParser()


class ParserCliTest(unittest.TestCase):

    def test_enrichment_failure_exits_non_zero_and_writes_no_json(self) -> None:
        output = Path(enter_context(self, tempfile.TemporaryDirectory())) / "rpc_methods.json"

        def fetch_url(url: str) -> bytes:
            prefix = next(p for p in support.METHOD_PREFIXES
                          if url.endswith("/" + common.camel_to_snake(p) + ".go"))
            return support.request_file(prefix).encode("utf-8")

        def fetch_response(tag: str, method_name: str) -> str:
            if method_name == "get_events":
                raise SourceFileNotFoundError("Failed to fetch get_events.go: HTTP 404")
            return support.response_file("GetHealth")

        for patcher in (
            mock.patch("sys.argv", ["rpc_parser.py", "--version", "v28.0.1", "--output", str(output)]),
            mock.patch.object(github_fetcher, "_resolve_go_stellar_sdk_ref", return_value="v0.7.2"),
            mock.patch.object(rpc_parser, "fetch_url", side_effect=fetch_url),
            mock.patch.object(github_fetcher, "fetch_rpc_response_file", side_effect=fetch_response),
            mock.patch.object(rpc_parser, "get_sdk_version", return_value="1.14.0"),
            contextlib.redirect_stdout(io.StringIO()),
            contextlib.redirect_stderr(io.StringIO()),
        ):
            enter_context(self, patcher)

        self.assertEqual(rpc_parser.main(), 1)
        self.assertFalse(output.exists())


class SdkVersionTest(unittest.TestCase):

    def setUp(self) -> None:
        self.sdk_root = Path(enter_context(self, tempfile.TemporaryDirectory()))
        enter_context(self, mock.patch.object(common, "SDK_ROOT", self.sdk_root))

    def write_gradle_properties(self, text: str) -> None:
        (self.sdk_root / "gradle.properties").write_text(text, encoding="utf-8")

    def test_reads_the_version_entry(self) -> None:
        self.write_gradle_properties("kotlin.code.style=official\nversion=1.14.0\ndemo.version=1.14.0\n")
        self.assertEqual(common.get_sdk_version(), "1.14.0")

    def test_missing_gradle_properties_raises(self) -> None:
        with self.assertRaises(FileNotFoundError):
            common.get_sdk_version()

    def test_gradle_properties_without_version_raises(self) -> None:
        self.write_gradle_properties("kotlin.code.style=official\n")
        with self.assertRaisesRegex(ValueError, "No version entry"):
            common.get_sdk_version()

    def test_blank_version_raises(self) -> None:
        self.write_gradle_properties("version=   \n")
        with self.assertRaisesRegex(ValueError, "No version entry"):
            common.get_sdk_version()


if __name__ == "__main__":
    unittest.main()
