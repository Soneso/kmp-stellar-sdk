"""Shared setup for the matrix generator tests: import paths and offline fakes.

Run from the repository root:

    python3 -m unittest discover -s tools/matrix-generator/tests
"""

import sys
import unittest
from email.message import Message
from pathlib import Path
from typing import Any, ContextManager, Dict, List, Optional, Sequence, TypeVar

TOOLS_DIR = Path(__file__).resolve().parent.parent

for _path in (TOOLS_DIR / "rpc", TOOLS_DIR / "sep", TOOLS_DIR):
    if str(_path) not in sys.path:
        sys.path.insert(0, str(_path))

# The mapped method prefixes, in the order of METHOD_NAME_MAPPING.
METHOD_PREFIXES = (
    "GetEvents", "GetFeeStats", "GetHealth", "GetLatestLedger",
    "GetLedgerEntries", "GetLedgers", "GetNetwork", "GetTransaction",
    "GetTransactions", "GetVersionInfo", "SendTransaction", "SimulateTransaction",
)

RELEASE_PAGE_2_URL = "https://api.github.com/repositories/1/releases?per_page=100&page=2"

_T = TypeVar("_T")


def enter_context(test: unittest.TestCase, manager: ContextManager[_T]) -> _T:
    """Enter *manager* for the rest of *test* (``TestCase.enterContext`` needs Python 3.11)."""
    value = manager.__enter__()
    test.addCleanup(manager.__exit__, None, None, None)
    return value


def release_record(
    tag: str,
    *,
    draft: bool = False,
    prerelease: bool = False,
    published_at: str = "2026-08-27T18:40:46Z",
) -> Dict[str, Any]:
    """Return a GitHub release record; a draft has a null published_at, as in the API."""
    return {
        "tag_name": tag,
        "draft": draft,
        "prerelease": prerelease,
        "published_at": None if draft else published_at,
        "html_url": f"https://github.com/stellar/stellar-rpc/releases/tag/{tag}",
        "target_commitish": "main",
    }


def request_file(prefix: str) -> str:
    """Return a go-stellar-sdk protocol file with a one-field request struct."""
    return (
        "package protocol\n\n"
        f"type {prefix}Request struct {{\n"
        "\tStartLedger uint32 `json:\"startLedger\"`\n"
        "}\n"
    )


def response_file(prefix: str) -> str:
    """Return a go-stellar-sdk protocol file with a one-field response struct."""
    return (
        "package protocol\n\n"
        f"type {prefix}Response struct {{\n"
        "\tLatestLedger uint32 `json:\"latestLedger\"`\n"
        "}\n"
    )


class _FakeResponse:
    """Response object returned by :class:`FakeUrlopen`."""

    def __init__(self, body: bytes, link: Optional[str]) -> None:
        self._body = body
        self.headers = Message()
        if link is not None:
            self.headers["Link"] = link

    def read(self) -> bytes:
        return self._body

    def __enter__(self) -> "_FakeResponse":
        return self

    def __exit__(self, *exc_info: object) -> bool:
        return False


class FakeUrlopen:
    """Stand-in for ``urllib.request.urlopen`` serving canned bodies by URL.

    Every requested URL is recorded; an unknown URL raises ``KeyError`` so an
    unexpected request fails the test.
    """

    def __init__(self, responses: Dict[str, Sequence[Any]]) -> None:
        self._responses = responses
        self.requested: List[str] = []

    def __call__(self, request: Any, timeout: Optional[float] = None) -> _FakeResponse:
        url = request.full_url
        self.requested.append(url)
        body, link = self._responses[url]
        return _FakeResponse(body, link)
