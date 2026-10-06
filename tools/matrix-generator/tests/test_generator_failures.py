"""Lookups and stages that the matrices depend on take their values from the source or fail."""

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from typing import Tuple
from unittest import mock

import matrix_test_support as support

import common
import github_fetcher
import run_analysis
import run_horizon_analysis
from github_fetcher import GitHubFetchError
from rpc_parser import RPCMethodExtractor

enter_context = support.enter_context

GO_MOD_URL = "https://raw.githubusercontent.com/stellar/stellar-rpc/v28.0.1/go.mod"


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
