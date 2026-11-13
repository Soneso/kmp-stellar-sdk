"""Lookups and stages that the matrices depend on take their values from the source or fail."""

import contextlib
import http.client
import io
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from typing import Any, Dict, Tuple
from unittest import mock

import matrix_test_support as support

import common
import github_fetcher
import run_analysis
import run_horizon_analysis
from github_fetcher import GitHubFetchError, SourceFileNotFoundError, fetch_rpc_jsonrpc_source
from rpc_parser import RPCMethodExtractor

enter_context = support.enter_context

GO_MOD_URL = "https://raw.githubusercontent.com/stellar/stellar-rpc/v28.0.1/go.mod"


def _method_source_url(tag: str, path: str) -> str:
    return f"https://raw.githubusercontent.com/stellar/stellar-rpc/{tag}/cmd/stellar-rpc/internal/{path}"


RPCV1_URL = _method_source_url("v30.0.1", "rpcv1/jsonrpc.go")
INTERNAL_URL = _method_source_url("v30.0.1", "jsonrpc.go")


class MethodSourceFetchTest(unittest.TestCase):
    """The method list file is read at the tag; the older path only after HTTP 404 at that tag."""

    def setUp(self) -> None:
        self.responses: Dict[str, Any] = {}
        self.fake = support.FakeUrlopen(self.responses)
        enter_context(self, mock.patch.object(github_fetcher.urllib.request, "urlopen", self.fake))
        enter_context(self, mock.patch.object(github_fetcher, "get_github_token", return_value=None))

    def test_reads_the_rpcv1_file(self) -> None:
        self.responses[RPCV1_URL] = (support.RPC_V30_0_1_SOURCE.read_bytes(), None)
        self.responses[INTERNAL_URL] = (b"internal source", None)
        self.assertEqual(fetch_rpc_jsonrpc_source("v30.0.1"),
                         support.RPC_V30_0_1_SOURCE.read_text(encoding="utf-8"))
        self.assertEqual(self.fake.requested, [RPCV1_URL])

    def test_falls_back_to_the_internal_file_of_the_same_tag_on_404(self) -> None:
        rpcv1_url = _method_source_url("v30.0.0", "rpcv1/jsonrpc.go")
        internal_url = _method_source_url("v30.0.0", "jsonrpc.go")
        self.responses[rpcv1_url] = support.http_error(rpcv1_url, 404, "Not Found")
        self.responses[internal_url] = (support.RPC_V30_0_0_SOURCE.read_bytes(), None)
        self.responses[RPCV1_URL] = (b"rpcv1 source of another tag", None)
        self.assertEqual(fetch_rpc_jsonrpc_source("v30.0.0"),
                         support.RPC_V30_0_0_SOURCE.read_text(encoding="utf-8"))
        self.assertEqual(self.fake.requested, [rpcv1_url, internal_url])

    def test_any_other_failure_on_either_path_raises_naming_the_tag_and_url(self) -> None:
        failures = {
            "HTTP 500 error fetching": lambda url: support.http_error(url, 500, "Internal Server Error"),
            "rate limit exceeded": lambda url: support.http_error(url, 403, "rate limit exceeded"),
            "Network error fetching": lambda url: urllib.error.URLError("connection refused"),
            "Timeout fetching": lambda url: TimeoutError("timed out"),
            "'utf-8' codec can't decode": lambda url: (b"package rpcv1 \xff", None),
            "IncompleteRead(7 bytes read, 100 more expected)":
                lambda url: (http.client.IncompleteRead(b"package", 100), None),
            "[Errno 54] Connection reset by peer":
                lambda url: (ConnectionResetError(54, "Connection reset by peer"), None),
        }
        for failing_url, earlier_requests in ((RPCV1_URL, []), (INTERNAL_URL, [RPCV1_URL])):
            for cause, response in failures.items():
                with self.subTest(url=failing_url, failure=cause):
                    self.fake.requested.clear()
                    self.responses[RPCV1_URL] = support.http_error(RPCV1_URL, 404, "Not Found")
                    self.responses[INTERNAL_URL] = (b"internal source", None)
                    self.responses[failing_url] = response(failing_url)

                    with self.assertRaises(GitHubFetchError) as context:
                        fetch_rpc_jsonrpc_source("v30.0.1")

                    self.assertNotIsInstance(context.exception, SourceFileNotFoundError)
                    message = str(context.exception)
                    self.assertTrue(message.startswith(
                        f"Failed to read {failing_url} for stellar-rpc tag v30.0.1: "), message)
                    self.assertIn(cause, message)
                    self.assertEqual(self.fake.requested, earlier_requests + [failing_url])

    def test_both_paths_absent_names_both_urls(self) -> None:
        for url in (RPCV1_URL, INTERNAL_URL):
            self.responses[url] = support.http_error(url, 404, "Not Found")
        with self.assertRaises(SourceFileNotFoundError) as context:
            fetch_rpc_jsonrpc_source("v30.0.1")
        self.assertEqual(str(context.exception), "No JSON-RPC method source for stellar-rpc tag v30.0.1: "
                                                 f"HTTP 404 for {RPCV1_URL} and {INTERNAL_URL}")
        self.assertEqual(self.fake.requested, [RPCV1_URL, INTERNAL_URL])


class GoStellarSdkRefTest(unittest.TestCase):

    def setUp(self) -> None:
        enter_context(self, mock.patch.dict(github_fetcher._GO_SDK_REF_BY_RPC_TAG, clear=True))

    def test_unfetchable_go_mod_raises(self) -> None:
        with mock.patch.object(
            github_fetcher, "_make_request",
            side_effect=GitHubFetchError(f"HTTP 404 error fetching {GO_MOD_URL}: Not Found"),
        ):
            with self.assertRaisesRegex(GitHubFetchError, "HTTP 404"):
                RPCMethodExtractor(support.RELEASE_INFO)
        self.assertNotIn("v28.0.1", github_fetcher._GO_SDK_REF_BY_RPC_TAG)

    def test_go_mod_without_go_stellar_sdk_requirement_raises(self) -> None:
        with mock.patch.object(
            github_fetcher, "_make_request",
            return_value=b"module github.com/stellar/stellar-rpc\n\ngo 1.24\n",
        ):
            with self.assertRaisesRegex(ValueError, "no github.com/stellar/go-stellar-sdk requirement"):
                RPCMethodExtractor(support.RELEASE_INFO)
        self.assertNotIn("v28.0.1", github_fetcher._GO_SDK_REF_BY_RPC_TAG)

    def test_pinned_release_is_the_ref(self) -> None:
        with mock.patch.object(
            github_fetcher, "_make_request",
            return_value=b"require (\n\tgithub.com/stellar/go-stellar-sdk v0.7.2\n)\n",
        ) as make_request:
            self.assertEqual(github_fetcher._resolve_go_stellar_sdk_ref("v28.0.1"), "v0.7.2")
        make_request.assert_called_once_with(GO_MOD_URL)


class HorizonReleaseTest(unittest.TestCase):

    def test_explicit_version_takes_date_and_url_from_its_record(self) -> None:
        url = "https://github.com/stellar/stellar-horizon/releases/tag/v27.0.0"
        record = {"tag_name": "v27.0.0", "published_at": "2026-06-11T17:44:47Z", "html_url": url}
        fake = support.FakeUrlopen({
            "https://api.github.com/repos/stellar/stellar-horizon/releases/tags/v27.0.0":
                (json.dumps(record).encode("utf-8"), None),
            "https://raw.githubusercontent.com/stellar/stellar-horizon/v27.0.0/internal/httpx/router.go":
                (b"package httpx\n", None),
        })
        tmp = Path(enter_context(self, tempfile.TemporaryDirectory()))
        with mock.patch.object(github_fetcher.urllib.request, "urlopen", fake), \
                mock.patch.object(github_fetcher, "get_github_token", return_value=None), \
                mock.patch.object(run_horizon_analysis, "DATA_DIR", tmp / "data"), \
                contextlib.redirect_stdout(io.StringIO()):
            pipeline = run_horizon_analysis.HorizonAnalysisPipeline(horizon_version="v27.0.0")
            pipeline.fetch_horizon_release()
        self.assertEqual(pipeline.release_info, {
            "version": "v27.0.0",
            "published_at": "2026-06-11",
            "html_url": url,
            "source": "GitHub",
        })


class SepStageFailureTest(unittest.TestCase):

    def test_failed_stage_ends_its_sep(self) -> None:
        orchestrator = run_analysis.AnalysisOrchestrator()
        ran = []

        def run_script(script_spec: str, description: str) -> Tuple[bool, str]:
            ran.append(script_spec)
            return script_spec != "sep/sep_parser.py 0010", ""

        with mock.patch.object(orchestrator, "_run_script", side_effect=run_script), \
                mock.patch.object(orchestrator, "_read_sep_coverage", return_value=100.0), \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertFalse(orchestrator._run_sep_pipeline(["0010", "0012"], base_step=3, total=8))

        self.assertEqual(ran, [
            "sep/sep_parser.py 0010",
            "sep/sep_parser.py 0012", "sep/sep_analyzer.py 0012", "sep/generate_sep_comparison.py 0012",
        ])
        self.assertEqual(orchestrator.sep_coverage, {"0012": 100.0})


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
