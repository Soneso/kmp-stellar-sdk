"""Selection of the stellar-rpc release that the RPC matrix cites."""

import contextlib
import io
import json
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional
from unittest import mock

import matrix_test_support as support

import github_fetcher
import run_rpc_analysis
from generate_rpc_comparison import RPCComparisonAnalyzer
from github_fetcher import (
    GitHubFetchError,
    ReleaseNotFoundError,
    RPC_RELEASES_URL,
    get_latest_rpc_release,
    get_rpc_release,
)

enter_context = support.enter_context
release_record = support.release_record


def _json_page(records: List[Dict[str, Any]]) -> bytes:
    return json.dumps(records).encode("utf-8")


class ReleaseListTestCase(unittest.TestCase):
    """Serves the stellar-rpc release list from fixtures instead of the network."""

    def serve(
        self,
        first_page: Any,
        second_page: Optional[List[Dict[str, Any]]] = None,
    ) -> support.FakeUrlopen:
        """Serve one or two release list pages; bytes are served verbatim."""
        responses: Dict[str, Any] = {}
        first_body = first_page if isinstance(first_page, bytes) else _json_page(first_page)
        if second_page is None:
            responses[RPC_RELEASES_URL] = (first_body, None)
        else:
            responses[RPC_RELEASES_URL] = (
                first_body,
                f'<{support.RELEASE_PAGE_2_URL}>; rel="next", '
                f'<{support.RELEASE_PAGE_2_URL}>; rel="last"',
            )
            responses[support.RELEASE_PAGE_2_URL] = (
                _json_page(second_page),
                f'<{RPC_RELEASES_URL}>; rel="prev", <{RPC_RELEASES_URL}>; rel="first"',
            )
        fake = support.FakeUrlopen(responses)
        enter_context(self, mock.patch.object(github_fetcher.urllib.request, "urlopen", fake))
        enter_context(self, mock.patch.object(github_fetcher, "get_github_token", return_value=None))
        return fake


class NewestStableReleaseTest(ReleaseListTestCase):

    def test_client_library_tag_listed_first_is_not_a_candidate(self) -> None:
        self.serve([release_record("rpcclient-v24.0.0"), release_record("v23.0.0")])
        self.assertEqual(get_latest_rpc_release().version, "v23.0.0")

    def test_suffixed_tag_is_not_a_candidate_even_without_prerelease_flag(self) -> None:
        self.serve([release_record("v29.0.0-rc.1"), release_record("v28.0.1")])
        self.assertEqual(get_latest_rpc_release().version, "v28.0.1")

    def test_prerelease_flag_excludes_suffixless_tag(self) -> None:
        self.serve([
            release_record("v21.3.0", prerelease=True),
            release_record("v21.2.0"),
        ])
        self.assertEqual(get_latest_rpc_release().version, "v21.2.0")

    def test_draft_is_not_a_candidate(self) -> None:
        self.serve([release_record("v28.1.0", draft=True), release_record("v28.0.1")])
        self.assertEqual(get_latest_rpc_release().version, "v28.0.1")

    def test_highest_version_wins_over_creation_order(self) -> None:
        self.serve([
            release_record("v27.1.1"),
            release_record("v28.0.1"),
            release_record("v28.0.0"),
        ])
        self.assertEqual(get_latest_rpc_release().version, "v28.0.1")

    def test_versions_compare_numerically(self) -> None:
        self.serve([release_record("v9.9.0"), release_record("v9.10.0")])
        self.assertEqual(get_latest_rpc_release().version, "v9.10.0")

    def test_follows_link_header_to_a_later_page_with_a_higher_version(self) -> None:
        fake = self.serve(
            [release_record("v28.0.1")],
            second_page=[release_record("v28.1.0", published_at="2026-09-20T10:00:00Z")],
        )
        release = get_latest_rpc_release()
        self.assertEqual(release.version, "v28.1.0")
        self.assertEqual(fake.requested, [RPC_RELEASES_URL, support.RELEASE_PAGE_2_URL])
        self.assertIn("per_page=100", fake.requested[0])

    def test_returns_date_and_url_of_the_selected_record(self) -> None:
        self.serve([
            release_record("rpcclient-v24.0.0"),
            release_record("v29.0.0-rc.1", prerelease=True),
            release_record("v21.3.0", prerelease=True),
            release_record("v30.0.0", draft=True),
            release_record("v27.1.1", published_at="2026-07-07T21:51:04Z"),
            release_record("v28.0.1", published_at="2026-08-27T18:40:46Z"),
        ])
        release = get_latest_rpc_release()
        self.assertEqual(release.version, "v28.0.1")
        self.assertEqual(release.published_at, datetime(2026, 8, 27, 18, 40, 46))
        self.assertEqual(
            release.html_url, "https://github.com/stellar/stellar-rpc/releases/tag/v28.0.1"
        )

    def test_empty_list_raises(self) -> None:
        self.serve([])
        with self.assertRaises(ReleaseNotFoundError):
            get_latest_rpc_release()

    def test_list_without_a_stable_release_raises(self) -> None:
        self.serve([
            release_record("rpcclient-v24.0.0"),
            release_record("v29.0.0-rc.1", prerelease=True),
            release_record("v21.3.0", prerelease=True),
            release_record("v30.0.0", draft=True),
        ])
        with self.assertRaises(ReleaseNotFoundError):
            get_latest_rpc_release()

    def test_invalid_json_body_raises(self) -> None:
        self.serve(b"<html>rate limited</html>")
        with self.assertRaisesRegex(GitHubFetchError, "Invalid JSON"):
            get_latest_rpc_release()

    def test_non_array_body_raises(self) -> None:
        self.serve(b'{"message": "Not Found"}')
        with self.assertRaisesRegex(GitHubFetchError, "not a JSON array"):
            get_latest_rpc_release()

    def test_record_without_selection_flags_raises(self) -> None:
        record = release_record("v28.0.1")
        del record["draft"]
        del record["prerelease"]
        self.serve([record])
        with self.assertRaisesRegex(GitHubFetchError, "boolean draft and prerelease"):
            get_latest_rpc_release()


class ExplicitReleaseTest(ReleaseListTestCase):

    def test_returns_the_named_release_with_its_date_and_url(self) -> None:
        self.serve([
            release_record("v28.0.1"),
            release_record("v27.1.1", published_at="2026-07-07T21:51:04Z"),
        ])
        release = get_rpc_release("v27.1.1")
        self.assertEqual(release.version, "v27.1.1")
        self.assertEqual(release.published_at, datetime(2026, 7, 7, 21, 51, 4))
        self.assertEqual(
            release.html_url, "https://github.com/stellar/stellar-rpc/releases/tag/v27.1.1"
        )

    def test_accepts_a_prerelease(self) -> None:
        self.serve([
            release_record("v28.0.1"),
            release_record("v29.0.0-rc.1", prerelease=True),
        ])
        self.assertEqual(get_rpc_release("v29.0.0-rc.1").version, "v29.0.0-rc.1")

    def test_finds_the_release_on_a_later_page(self) -> None:
        self.serve([release_record("v28.0.1")], second_page=[release_record("v21.3.0")])
        self.assertEqual(get_rpc_release("v21.3.0").version, "v21.3.0")

    def test_absent_tag_raises(self) -> None:
        self.serve([release_record("v28.0.1")])
        with self.assertRaisesRegex(ReleaseNotFoundError, "v99.0.0 is not in the release list"):
            get_rpc_release("v99.0.0")

    def test_client_library_tag_raises(self) -> None:
        self.serve([release_record("v28.0.1"), release_record("rpcclient-v24.0.0")])
        with self.assertRaisesRegex(
            ReleaseNotFoundError, "'rpcclient-v24.0.0' is not a stellar-rpc release tag"
        ):
            get_rpc_release("rpcclient-v24.0.0")

    def test_draft_tag_raises(self) -> None:
        self.serve([release_record("v28.0.1"), release_record("v28.1.0", draft=True)])
        with self.assertRaisesRegex(ReleaseNotFoundError, "v28.1.0 is a draft"):
            get_rpc_release("v28.1.0")


class RequestHelperTest(ReleaseListTestCase):

    def test_fetch_url_returns_the_body_only(self) -> None:
        self.serve([release_record("v28.0.1")])
        self.assertEqual(
            github_fetcher.fetch_url(RPC_RELEASES_URL), _json_page([release_record("v28.0.1")])
        )


class PipelineReleaseInfoTest(ReleaseListTestCase):
    """The pipeline copies version, date, and URL from the one release record."""

    def setUp(self) -> None:
        tmp = Path(enter_context(self, tempfile.TemporaryDirectory()))
        self.data_dir = tmp / "data"
        self.compatibility_dir = tmp / "compatibility"
        for name, value in (
            ("DATA_DIR", self.data_dir),
            ("COMPATIBILITY_DIR", self.compatibility_dir),
        ):
            enter_context(self, mock.patch.object(run_rpc_analysis, name, value))
        self.jsonrpc_fetch = enter_context(self, mock.patch.object(
            run_rpc_analysis, "fetch_rpc_jsonrpc_source", return_value="package internal\n"
        ))
        enter_context(self, contextlib.redirect_stdout(io.StringIO()))

    def test_explicit_version_takes_date_and_url_from_its_record(self) -> None:
        self.serve([
            release_record("v28.0.1"),
            release_record("v27.1.1", published_at="2026-07-07T21:51:04Z"),
        ])
        pipeline = run_rpc_analysis.RPCAnalysisPipeline(rpc_version="v27.1.1")
        pipeline.fetch_rpc_release()
        self.assertEqual(pipeline.release_info, {
            "version": "v27.1.1",
            "published_at": "2026-07-07",
            "html_url": "https://github.com/stellar/stellar-rpc/releases/tag/v27.1.1",
            "source": "GitHub",
        })
        self.jsonrpc_fetch.assert_called_once_with("v27.1.1")

    def test_default_uses_the_newest_stable_release(self) -> None:
        self.serve([
            release_record("v29.0.0-rc.1", prerelease=True),
            release_record("v27.1.1", published_at="2026-07-07T21:51:04Z"),
            release_record("v28.0.1", published_at="2026-08-27T18:40:46Z"),
        ])
        pipeline = run_rpc_analysis.RPCAnalysisPipeline()
        pipeline.fetch_rpc_release()
        self.assertEqual(pipeline.release_info["version"], "v28.0.1")
        self.assertEqual(pipeline.release_info["published_at"], "2026-08-27")
        self.jsonrpc_fetch.assert_called_once_with("v28.0.1")

    def test_absent_explicit_version_exits_non_zero_and_writes_nothing(self) -> None:
        self.serve([release_record("v28.0.1")])
        pipeline = run_rpc_analysis.RPCAnalysisPipeline(rpc_version="v99.0.0")
        self.assertEqual(pipeline.run(), 1)
        self.jsonrpc_fetch.assert_not_called()
        self.assertEqual(list(self.data_dir.rglob("*.json")), [])
        self.assertFalse(self.compatibility_dir.exists())

    def test_list_without_candidates_exits_non_zero_and_writes_nothing(self) -> None:
        self.serve([
            release_record("v29.0.0-rc.1", prerelease=True),
            release_record("v21.3.0", prerelease=True),
        ])
        pipeline = run_rpc_analysis.RPCAnalysisPipeline()
        self.assertEqual(pipeline.run(), 1)
        self.jsonrpc_fetch.assert_not_called()
        self.assertEqual(list(self.data_dir.rglob("*.json")), [])
        self.assertFalse(self.compatibility_dir.exists())


class LocalModeTest(unittest.TestCase):
    """``--local`` reads the go-stellar-sdk pin from the local checkout's go.mod."""

    def setUp(self) -> None:
        self.tmp = Path(enter_context(self, tempfile.TemporaryDirectory()))
        self.jsonrpc = self.tmp / "cmd" / "stellar-rpc" / "internal" / "jsonrpc.go"
        self.jsonrpc.parent.mkdir(parents=True)
        self.jsonrpc.write_text("package internal\n", encoding="utf-8")

    def write_go_mod(self, requirement: str) -> None:
        (self.tmp / "go.mod").write_text(
            "module github.com/stellar/stellar-rpc\n\ngo 1.24\n\n"
            f"require (\n\t{requirement}\n)\n",
            encoding="utf-8",
        )

    def test_release_pin_resolves_to_its_tag(self) -> None:
        self.write_go_mod("github.com/stellar/go-stellar-sdk v0.7.2")
        self.assertEqual(run_rpc_analysis.local_go_stellar_sdk_ref(self.jsonrpc), "v0.7.2")

    def test_pseudo_version_pin_resolves_to_its_commit(self) -> None:
        self.write_go_mod("github.com/stellar/go-stellar-sdk v0.7.3-0.20260901120000-0123456789ab")
        self.assertEqual(
            run_rpc_analysis.local_go_stellar_sdk_ref(self.jsonrpc), "0123456789ab"
        )

    def test_go_mod_without_pin_raises(self) -> None:
        self.write_go_mod("github.com/stretchr/testify v1.10.0")
        with self.assertRaisesRegex(ValueError, "no github.com/stellar/go-stellar-sdk requirement"):
            run_rpc_analysis.local_go_stellar_sdk_ref(self.jsonrpc)

    def test_missing_go_mod_raises(self) -> None:
        with self.assertRaisesRegex(FileNotFoundError, "No go.mod above"):
            run_rpc_analysis.local_go_stellar_sdk_ref(self.jsonrpc)

    def test_local_release_info_and_protocol_source(self) -> None:
        self.write_go_mod("github.com/stellar/go-stellar-sdk v0.7.2")
        with mock.patch.object(run_rpc_analysis, "DATA_DIR", self.tmp / "data"), \
                contextlib.redirect_stdout(io.StringIO()):
            pipeline = run_rpc_analysis.RPCAnalysisPipeline(local_jsonrpc_path=str(self.jsonrpc))
            pipeline.fetch_rpc_release()
        self.assertEqual(pipeline.release_info, {
            "version": "local",
            "published_at": datetime.now().strftime("%Y-%m-%d"),
            "html_url": str(self.jsonrpc),
            "source": "local",
        })
        self.assertEqual(
            pipeline.protocol_source,
            "https://raw.githubusercontent.com/stellar/go-stellar-sdk/v0.7.2/protocols/rpc/",
        )


class MatrixHeaderTest(unittest.TestCase):
    """The RPC header lines are a contract with the dashboard collector."""

    def test_header_lines_keep_their_shape(self) -> None:
        url = "https://github.com/stellar/stellar-rpc/releases/tag/v27.1.1"
        rpc_data = {
            "metadata": {
                "rpc_version": "v27.1.1",
                "rpc_release_date": "2026-07-07",
                "rpc_release_url": url,
            },
            "methods": {},
        }
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch("generate_rpc_comparison.get_sdk_version", return_value="1.14.0"), \
                contextlib.redirect_stdout(io.StringIO()):
            matrix = Path(tmp) / "RPC_COMPATIBILITY_MATRIX.md"
            RPCComparisonAnalyzer(rpc_data, {"implemented_methods": {}}).generate_markdown_report(
                str(matrix)
            )
            lines = matrix.read_text(encoding="utf-8").split("\n")

        self.assertEqual(lines[2], "**RPC Version:** v27.1.1 (released 2026-07-07)  ")
        self.assertEqual(lines[3], f"**RPC Source:** [{url}]({url})  ")
        self.assertEqual(lines[4], "**SDK Version:** 1.14.0  ")
        self.assertRegex(lines[5], r"^\*\*Generated:\*\* \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$")


if __name__ == "__main__":
    unittest.main()
