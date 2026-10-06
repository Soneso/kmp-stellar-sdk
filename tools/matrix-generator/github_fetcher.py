#!/usr/bin/env python3
"""
GitHub Fetcher for Stellar Horizon and RPC Source Code

This module provides functionality to fetch the latest Horizon and RPC release information
and source code from the stellar/stellar-horizon and stellar/stellar-rpc GitHub repositories.

Uses only Python standard library for maximum compatibility.

Authentication:
    To avoid GitHub API rate limits (60 req/hour unauthenticated vs 5,000 authenticated),
    set a GitHub token via one of these methods:

    1. Environment variable: export GITHUB_TOKEN=your_token
    2. gh CLI config: The token is read from ~/.config/gh/hosts.yml if available

    To create a token: https://github.com/settings/tokens
    Required scope: No scopes needed for public repo access (just need authentication)

Example usage:
    from github_fetcher import (
        get_horizon_release, fetch_router_source,
        get_latest_rpc_release, fetch_rpc_jsonrpc_source
    )

    # Horizon
    release = get_horizon_release()
    print(f"Latest Horizon version: {release.version}")
    source = fetch_router_source(release.version)

    # RPC
    rpc_release = get_latest_rpc_release()
    print(f"Latest RPC version: {rpc_release.version}")
    jsonrpc_source = fetch_rpc_jsonrpc_source(rpc_release.version)
"""

import json
import os
import re
import urllib.request
import urllib.error
from dataclasses import dataclass
from datetime import datetime
from email.message import Message
from pathlib import Path
from typing import Any, Dict, Optional, List, Tuple

from common import camel_to_snake


RPC_RELEASES_URL = 'https://api.github.com/repos/stellar/stellar-rpc/releases?per_page=100'

# A stable stellar-rpc server release carries a plain vX.Y.Z tag. Client
# library tags (rpcclient-v24.0.0) and suffixed prerelease tags (v23.0.0-rc.1)
# do not match.
_STABLE_RELEASE_TAG = re.compile(r'^v(\d+)\.(\d+)\.(\d+)$')

# An explicit --rpc-version tag names a server release, stable or prerelease
# (v23.0.0-rc.1). The matrix header cites it as the RPC version, so client
# library tags (rpcclient-v24.0.0) are rejected.
_RELEASE_TAG = re.compile(r'^v\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?$')

_NEXT_PAGE_LINK = re.compile(r'<([^>]+)>\s*;\s*rel="next"')


@dataclass
class GitHubRelease:
    """Metadata for a GitHub release (Horizon or RPC)."""

    version: str
    published_at: datetime
    html_url: str

    @classmethod
    def from_api_response(cls, data: Dict) -> 'GitHubRelease':
        """
        Create GitHubRelease from GitHub API response.

        Args:
            data: GitHub API release response dictionary

        Returns:
            GitHubRelease instance

        Raises:
            KeyError: If required fields are missing from API response
            ValueError: If date parsing fails
        """
        published_at = datetime.strptime(
            data['published_at'],
            '%Y-%m-%dT%H:%M:%SZ'
        )

        return cls(
            version=data['tag_name'],
            published_at=published_at,
            html_url=data['html_url'],
        )

    def release_info(self) -> Dict[str, str]:
        """Return the version, UTC release date, URL and source ("GitHub") that the pipelines store for the release."""
        return {
            "version": self.version,
            "published_at": self.published_at.strftime("%Y-%m-%d"),
            "html_url": self.html_url,
            "source": "GitHub",
        }


class GitHubFetchError(Exception):
    """Base exception for GitHub fetching errors."""
    pass


class ReleaseNotFoundError(GitHubFetchError):
    """Raised when no release is found."""
    pass


class SourceFileNotFoundError(GitHubFetchError):
    """Raised when source file cannot be fetched."""
    pass


# Cache for GitHub token
_github_token_cache: Optional[str] = None
_github_token_checked: bool = False


def get_github_token() -> Optional[str]:
    """
    Get GitHub token for authenticated API requests.

    Checks in order:
    1. GITHUB_TOKEN environment variable
    2. gh CLI config file (~/.config/gh/hosts.yml)

    Returns:
        GitHub token string, or None if not found

    Note:
        Authenticated requests get 5,000 requests/hour vs 60 for unauthenticated.
    """
    global _github_token_cache, _github_token_checked

    if _github_token_checked:
        return _github_token_cache

    _github_token_checked = True

    # Check environment variable first
    token = os.environ.get('GITHUB_TOKEN')
    if token:
        _github_token_cache = token
        return token

    # Check gh CLI config
    gh_config_path = Path.home() / '.config' / 'gh' / 'hosts.yml'
    if gh_config_path.exists():
        try:
            content = gh_config_path.read_text()
            # Simple YAML parsing for the token (avoid external dependencies)
            # Format: github.com:\n    oauth_token: TOKEN
            for line in content.split('\n'):
                if 'oauth_token:' in line:
                    token = line.split('oauth_token:')[1].strip()
                    if token:
                        _github_token_cache = token
                        return token
        except (IOError, IndexError):
            pass

    return None


def is_authenticated() -> bool:
    """Check if GitHub authentication is available."""
    return get_github_token() is not None


def _make_request(url: str, headers: Optional[Dict[str, str]] = None) -> bytes:
    """
    Make HTTP request with proper error handling and authentication.

    Args:
        url: URL to fetch
        headers: Optional HTTP headers

    Returns:
        Response body as bytes

    Raises:
        GitHubFetchError: If request fails
    """
    return _make_request_with_headers(url, headers)[0]


def _make_request_with_headers(
    url: str,
    headers: Optional[Dict[str, str]] = None,
) -> Tuple[bytes, Message]:
    """
    Make HTTP request like :func:`_make_request` and also return the response
    headers, which paginated API responses need for their ``Link`` header.

    Args:
        url: URL to fetch
        headers: Optional HTTP headers

    Returns:
        Tuple of the response body as bytes and the response headers

    Raises:
        GitHubFetchError: If request fails
    """
    if headers is None:
        headers = {}

    # Add User-Agent header (GitHub API requires it)
    if 'User-Agent' not in headers:
        headers['User-Agent'] = 'kmp-stellar-sdk-compatibility-tools'

    # Add authentication if token is available
    token = get_github_token()
    if token and 'Authorization' not in headers:
        headers['Authorization'] = f'Bearer {token}'

    request = urllib.request.Request(url, headers=headers)

    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read(), response.headers
    except urllib.error.HTTPError as e:
        # Provide helpful message for rate limit errors
        if e.code == 403 and 'rate limit' in str(e.reason).lower():
            auth_status = "authenticated" if token else "unauthenticated"
            raise GitHubFetchError(
                f"GitHub API rate limit exceeded ({auth_status}). "
                f"Set GITHUB_TOKEN env var for 5,000 requests/hour. "
                f"See: https://github.com/settings/tokens"
            ) from e
        raise GitHubFetchError(
            f"HTTP {e.code} error fetching {url}: {e.reason}"
        ) from e
    except urllib.error.URLError as e:
        raise GitHubFetchError(
            f"Network error fetching {url}: {e.reason}"
        ) from e
    except TimeoutError as e:
        raise GitHubFetchError(
            f"Timeout fetching {url}"
        ) from e


def fetch_url(url: str) -> bytes:
    """
    Fetch a URL using the shared HTTP client with authentication and error handling.

    This is the public API for modules that need to fetch arbitrary URLs through
    the same HTTP infrastructure (auth, timeouts, rate limit messages).

    Args:
        url: URL to fetch

    Returns:
        Response body as bytes

    Raises:
        GitHubFetchError: If request fails
    """
    return _make_request(url)


def get_horizon_release(tag: Optional[str] = None) -> GitHubRelease:
    """
    Fetch a Horizon release record from the GitHub API.

    Args:
        tag: Release tag (e.g. 'v28.0.1'). None selects the latest release.

    Returns:
        GitHubRelease instance with release metadata

    Raises:
        ReleaseNotFoundError: If no release is found
        GitHubFetchError: If API request fails or the response is invalid
    """
    api_url = 'https://api.github.com/repos/stellar/stellar-horizon/releases/' + (
        f'tags/{tag}' if tag else 'latest'
    )

    try:
        data = json.loads(_make_request(api_url).decode('utf-8'))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        raise GitHubFetchError(
            f"Invalid JSON response from GitHub API: {e}"
        ) from e

    if not data:
        raise ReleaseNotFoundError("No release data returned from GitHub API")

    return _release_from_record(data)


def fetch_router_source(tag: str) -> str:
    """
    Fetch router.go source code for a specific Horizon release tag.

    Args:
        tag: Git tag name (e.g., 'v28.0.1')

    Returns:
        Content of router.go as string

    Raises:
        SourceFileNotFoundError: If source file cannot be fetched
        GitHubFetchError: If request fails
    """
    if not tag:
        raise ValueError("Tag parameter cannot be empty")

    # Construct raw GitHub URL for router.go
    source_url = (
        f"https://raw.githubusercontent.com/stellar/stellar-horizon/"
        f"{tag}/internal/httpx/router.go"
    )

    try:
        response_data = _make_request(source_url)
        return response_data.decode('utf-8')
    except GitHubFetchError as e:
        raise SourceFileNotFoundError(
            f"Failed to fetch router.go for tag {tag}: {e}"
        ) from e


def _fetch_release_records(url: str) -> List[Dict[str, Any]]:
    """
    Fetch every record of a paginated GitHub release list.

    Follows the ``Link: rel="next"`` response header until the last page.
    Release selection depends on ``tag_name``, ``draft`` and ``prerelease``,
    so every record must carry them with their API types.

    Args:
        url: URL of the first page of the release list

    Returns:
        Release records of all pages, in API order

    Raises:
        GitHubFetchError: If a request fails, a page is not a JSON array, or a
            record lacks a string ``tag_name`` or boolean ``draft`` and
            ``prerelease`` flags
    """
    records: List[Dict[str, Any]] = []
    page_url: Optional[str] = url

    while page_url:
        body, headers = _make_request_with_headers(page_url)
        try:
            page = json.loads(body.decode('utf-8'))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            raise GitHubFetchError(
                f"Invalid JSON in release list {page_url}: {e}"
            ) from e

        if not isinstance(page, list):
            raise GitHubFetchError(
                f"Release list {page_url} is not a JSON array: {str(page)[:200]}"
            )
        for record in page:
            if not (
                isinstance(record, dict)
                and isinstance(record.get('tag_name'), str)
                and isinstance(record.get('draft'), bool)
                and isinstance(record.get('prerelease'), bool)
            ):
                raise GitHubFetchError(
                    f"Release list {page_url} contains a record without a string "
                    f"tag_name and boolean draft and prerelease flags: {str(record)[:200]}"
                )
        records.extend(page)

        next_link = _NEXT_PAGE_LINK.search(headers.get('Link') or '')
        page_url = next_link.group(1) if next_link else None

    return records


def _newest_stable_release(records: List[Dict[str, Any]]) -> Dict[str, Any]:
    """
    Select the stable release with the highest version.

    A stable release has a plain ``vX.Y.Z`` tag and is neither a draft nor a
    prerelease. The prerelease flag is checked in addition to the tag because
    stellar-rpc has published a suffix-less tag (``v21.3.0``) as a prerelease.
    Versions compare numerically; the API's list order is not a version order.

    Raises:
        ReleaseNotFoundError: If no record is a stable release
    """
    candidates = []
    for record in records:
        tag_match = _STABLE_RELEASE_TAG.match(record['tag_name'])
        if tag_match and not record['draft'] and not record['prerelease']:
            version = tuple(int(part) for part in tag_match.groups())
            candidates.append((version, record))

    if not candidates:
        raise ReleaseNotFoundError(
            f"No stable vX.Y.Z release (neither draft nor prerelease) among "
            f"{len(records)} stellar-rpc release records"
        )

    return max(candidates, key=lambda candidate: candidate[0])[1]


def _published_release(records: List[Dict[str, Any]], tag: str) -> Dict[str, Any]:
    """
    Select the non-draft release record for ``tag``.

    The tag must have the form ``vX.Y.Z`` or ``vX.Y.Z-suffix``. Prereleases
    are accepted, so an explicit tag can cite one.

    Raises:
        ReleaseNotFoundError: If the tag has another form, no record has the
            tag, or only a draft has it
    """
    if not _RELEASE_TAG.match(tag):
        raise ReleaseNotFoundError(
            f"{tag!r} is not a stellar-rpc release tag of the form vX.Y.Z or vX.Y.Z-suffix"
        )
    matches = [record for record in records if record['tag_name'] == tag]
    published = [record for record in matches if not record['draft']]
    if published:
        return published[0]
    if matches:
        raise ReleaseNotFoundError(
            f"stellar-rpc release {tag} is a draft; only published releases can be analysed"
        )
    raise ReleaseNotFoundError(
        f"stellar-rpc release {tag} is not in the release list of stellar/stellar-rpc"
    )


def _release_from_record(record: Dict[str, Any]) -> GitHubRelease:
    """
    Build a :class:`GitHubRelease` from a release record.

    Raises:
        GitHubFetchError: If the record lacks a field or holds a malformed one
    """
    try:
        return GitHubRelease.from_api_response(record)
    except KeyError as e:
        raise GitHubFetchError(
            f"Missing required field in API response: {e}"
        ) from e
    except (TypeError, ValueError) as e:
        raise GitHubFetchError(
            f"Invalid data format in API response for {record['tag_name']}: {e}"
        ) from e


def get_latest_rpc_release() -> GitHubRelease:
    """
    Fetch the newest stable Stellar RPC server release from the GitHub API.

    Reads the complete release list and selects the highest ``vX.Y.Z``
    version among releases that are neither drafts nor prereleases.

    Returns:
        GitHubRelease instance for the newest stable server release

    Raises:
        ReleaseNotFoundError: If the list holds no stable server release
        GitHubFetchError: If a request fails or the response is invalid
    """
    records = _fetch_release_records(RPC_RELEASES_URL)
    return _release_from_record(_newest_stable_release(records))


def get_rpc_release(tag: str) -> GitHubRelease:
    """
    Fetch the Stellar RPC release record for an explicit tag.

    The tag must name a non-draft release in the release list; a prerelease
    is accepted.

    Args:
        tag: Release tag (e.g. 'v28.0.1')

    Returns:
        GitHubRelease instance for that release

    Raises:
        ReleaseNotFoundError: If the tag is absent or names only a draft
        GitHubFetchError: If a request fails or the response is invalid
    """
    records = _fetch_release_records(RPC_RELEASES_URL)
    return _release_from_record(_published_release(records, tag))


def fetch_rpc_jsonrpc_source(tag: str) -> str:
    """
    Fetch jsonrpc.go source code for a specific Stellar RPC release tag.

    Args:
        tag: Git tag name (e.g., 'v21.5.0')

    Returns:
        Content of jsonrpc.go as string

    Raises:
        SourceFileNotFoundError: If source file cannot be fetched
        GitHubFetchError: If request fails
    """
    if not tag:
        raise ValueError("Tag parameter cannot be empty")

    # Construct raw GitHub URL for jsonrpc.go
    source_url = (
        f"https://raw.githubusercontent.com/stellar/stellar-rpc/"
        f"{tag}/cmd/stellar-rpc/internal/jsonrpc.go"
    )

    try:
        response_data = _make_request(source_url)
        return response_data.decode('utf-8')
    except GitHubFetchError as e:
        raise SourceFileNotFoundError(
            f"Failed to fetch jsonrpc.go for tag {tag}: {e}"
        ) from e


_GO_SDK_REF_BY_RPC_TAG: Dict[str, str] = {}


def go_stellar_sdk_ref_from_go_mod(go_mod: str, source: str) -> str:
    """Return the go-stellar-sdk git ref that a stellar-rpc ``go.mod`` pins.

    The ref is usable on raw.githubusercontent.com: a release tag (e.g.
    ``v0.6.0``) for a normal version, or the commit hash for a Go
    pseudo-version.

    Args:
        go_mod: Text of the go.mod file
        source: Location of the go.mod file, used in the error message

    Raises:
        ValueError: If the go.mod has no github.com/stellar/go-stellar-sdk requirement
    """
    match = re.search(r'github\.com/stellar/go-stellar-sdk\s+(\S+)', go_mod)
    if not match:
        raise ValueError(
            f"{source} has no github.com/stellar/go-stellar-sdk requirement; "
            f"the go-stellar-sdk ref for the RPC structs cannot be resolved"
        )
    version = match.group(1)
    # A Go pseudo-version is not a git tag; its ref is the trailing commit
    # hash. Both pseudo-version forms carry a 14-digit UTC timestamp directly
    # before the final 12-hex segment (vX.Y.Z-yyyymmddhhmmss-<hash> and the
    # base-incremented vX.Y.Z-0.yyyymmddhhmmss-<hash>). A normal release tag
    # is used as-is.
    pseudo = re.match(r'^v\S*\d{14}-([0-9a-f]{12})$', version)
    return pseudo.group(1) if pseudo else version


def _resolve_go_stellar_sdk_ref(rpc_tag: str) -> str:
    """Resolve (and cache) the go-stellar-sdk ref that stellar-rpc@<rpc_tag> pins.

    The RPC request/response structs (protocols/rpc) live in go-stellar-sdk, which
    versions independently of stellar-rpc. Reading them from the ref that the RPC
    release depends on (per its go.mod) matches the comparison to what that RPC
    release exposes.

    Raises:
        GitHubFetchError: If the go.mod of the release cannot be fetched
        ValueError: If the go.mod has no go-stellar-sdk requirement
    """
    if rpc_tag in _GO_SDK_REF_BY_RPC_TAG:
        return _GO_SDK_REF_BY_RPC_TAG[rpc_tag]

    go_mod_url = f"https://raw.githubusercontent.com/stellar/stellar-rpc/{rpc_tag}/go.mod"
    go_mod = _make_request(go_mod_url).decode('utf-8')
    ref = go_stellar_sdk_ref_from_go_mod(go_mod, go_mod_url)

    _GO_SDK_REF_BY_RPC_TAG[rpc_tag] = ref
    return ref


def fetch_rpc_response_file(tag: str, method_name: str) -> str:
    """
    Fetch response struct source file from go-stellar-sdk for a specific method.

    Response structs are defined in the go-stellar-sdk repository:
    protocols/rpc/<method_name>.go

    Args:
        tag: stellar-rpc release tag (e.g. 'v27.1.1'). The go-stellar-sdk ref is
             resolved from this release's go.mod so the response fields match what
             the RPC release actually exposes.
        method_name: Full method file stem in snake_case (e.g. 'get_latest_ledger'
             for getLatestLedger, 'send_transaction' for sendTransaction)

    Returns:
        Content of the response file as string

    Raises:
        SourceFileNotFoundError: If source file cannot be fetched
        GitHubFetchError: If the go.mod of the release cannot be fetched
        ValueError: If the go.mod of the release has no go-stellar-sdk requirement
    """
    if not method_name:
        raise ValueError("method_name parameter cannot be empty")

    # Read the response struct from the go-stellar-sdk ref that this RPC release
    # pins in its go.mod, so the comparison reflects the released RPC's surface.
    go_sdk_ref = _resolve_go_stellar_sdk_ref(tag)
    source_url = (
        f"https://raw.githubusercontent.com/stellar/go-stellar-sdk/"
        f"{go_sdk_ref}/protocols/rpc/{method_name}.go"
    )

    try:
        response_data = _make_request(source_url)
        return response_data.decode('utf-8')
    except GitHubFetchError as e:
        raise SourceFileNotFoundError(
            f"Failed to fetch {method_name}.go from go-stellar-sdk {go_sdk_ref}: {e}"
        ) from e


def fetch_all_rpc_response_files(tag: str, method_names: List[str]) -> Dict[str, str]:
    """
    Fetch multiple RPC response files for a given tag.

    Every method must have a response file, so the first failed fetch raises.

    Args:
        tag: Git tag name (e.g., 'v28.0.1')
        method_names: List of method names in camelCase (e.g., ['getLatestLedger', 'getHealth'])

    Returns:
        Dictionary mapping each method_name to its file content

    Raises:
        SourceFileNotFoundError: If a response file cannot be fetched
        GitHubFetchError: If the go-stellar-sdk ref cannot be resolved
        ValueError: If the go.mod of the release has no go-stellar-sdk requirement
    """
    results = {}

    for method_name in method_names:
        # The response file is named after the full method name in snake_case:
        # getLatestLedger -> get_latest_ledger.go, sendTransaction -> send_transaction.go.
        results[method_name] = fetch_rpc_response_file(tag, camel_to_snake(method_name))

    return results
