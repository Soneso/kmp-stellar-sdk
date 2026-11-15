"""Tests of generate_sac_spec.py. They run offline.

Run from the repository root:

    python3 -m unittest discover -s tools/sac-spec/tests
"""

import base64
import contextlib
import hashlib
import io
import json
import os
import re
import shutil
import sys
import tempfile
import unittest
import urllib.error
from typing import List, Optional, Tuple
from unittest import mock

TOOL_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if TOOL_DIR not in sys.path:
    sys.path.insert(0, TOOL_DIR)

import generate_sac_spec  # noqa: E402

SOURCE = bytes(range(256)) * 3 + b"spec"
SOURCE_SHA256 = hashlib.sha256(SOURCE).hexdigest()
COMMIT = "0123456789abcdef0123456789abcdef01234567"
TEMPLATE = (
    "// {{REPOSITORY}} {{FILE}} {{COMMIT}} {{SIZE}} {{SHA256}}\n"
    "const val COMMIT = \"{{COMMIT}}\"\n"
    "const val SHA256 = \"{{SHA256}}\"\n"
    "const val XDR_BASE64 = \"{{BASE64}}\"\n"
)


def expected_output() -> str:
    return (
        "// owner/spec-repo spec.xdr %s %d %s\n" % (COMMIT, len(SOURCE), SOURCE_SHA256)
        + "const val COMMIT = \"%s\"\n" % COMMIT
        + "const val SHA256 = \"%s\"\n" % SOURCE_SHA256
        + "const val XDR_BASE64 = \"%s\"\n" % base64.b64encode(SOURCE).decode("ascii")
    )


class FakeResponse:
    """A urlopen result whose read() returns *body*."""

    def __init__(self, body: bytes) -> None:
        self.body = body

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, *exc_info: object) -> None:
        return None

    def read(self) -> bytes:
        return self.body


class TemporaryRootTest(unittest.TestCase):
    """A temporary root holding a pin, a template, a source file and an empty output directory."""

    def setUp(self) -> None:
        self.root = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.root)
        os.mkdir(os.path.join(self.root, "out"))
        self.output = os.path.join(self.root, "out", "Spec.kt")
        self.source = os.path.join(self.root, "spec.xdr")
        with open(self.source, "wb") as handle:
            handle.write(SOURCE)
        self.write_template(TEMPLATE)
        self.pin = self.write_pin()

    def write_template(self, text: str, newline: str = "\n") -> None:
        with open(os.path.join(self.root, "Spec.kt.template"), "w", encoding="utf-8", newline=newline) as handle:
            handle.write(text)

    def write_pin(self, **overrides: object) -> str:
        pin = {
            "repository": "owner/spec-repo",
            "commit": COMMIT,
            "file": "spec.xdr",
            "sha256": SOURCE_SHA256,
            "template": "Spec.kt.template",
            "output": "out/Spec.kt",
        }
        pin.update(overrides)
        path = os.path.join(self.root, "pin.json")
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(pin, handle)
        return path

    def run_main(self, source: Optional[str] = None) -> Tuple[int, str, str]:
        argv: List[str] = ["--pin", self.pin, "--root", self.root]
        argv += ["--source", self.source if source is None else source]
        return self.run_argv(argv)

    @staticmethod
    def run_argv(argv: List[str]) -> Tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            status = generate_sac_spec.main(argv)
        return status, out.getvalue(), err.getvalue()

    def read_output(self) -> bytes:
        with open(self.output, "rb") as handle:
            return handle.read()

    def assert_nothing_written(self) -> None:
        self.assertFalse(os.path.exists(self.output))
        self.assertEqual(sorted(os.listdir(os.path.join(self.root, "out"))), [])


class GeneratorTest(TemporaryRootTest):
    """Each exit path of the generator with --source."""

    def test_a_matching_digest_fills_every_placeholder_from_the_source(self) -> None:
        status, out, err = self.run_main()
        self.assertEqual(status, 0, err)
        self.assertEqual(err, "")
        self.assertEqual(out, "wrote %s\n" % self.output)
        text = self.read_output().decode("utf-8")
        self.assertEqual(text, expected_output())
        self.assertNotIn("{{", text)
        self.assertEqual(sorted(os.listdir(os.path.join(self.root, "out"))), ["Spec.kt"])

    def test_the_base64_is_standard_and_on_one_line(self) -> None:
        self.assertEqual(self.run_main()[0], 0)
        line = [entry for entry in self.read_output().decode("utf-8").split("\n") if "XDR_BASE64" in entry][0]
        encoded = re.fullmatch(r'const val XDR_BASE64 = "([A-Za-z0-9+/=]+)"', line).group(1)
        self.assertEqual(base64.b64decode(encoded, validate=True), SOURCE)

    def test_two_runs_give_identical_bytes(self) -> None:
        self.assertEqual(self.run_main()[0], 0)
        first = self.read_output()
        self.assertEqual(self.run_main()[0], 0)
        self.assertEqual(self.read_output(), first)

    def test_the_output_has_lf_line_endings_for_a_crlf_template(self) -> None:
        self.write_template(TEMPLATE, newline="\r\n")
        self.assertEqual(self.run_main()[0], 0)
        output = self.read_output()
        self.assertNotIn(b"\r", output)
        self.assertEqual(output.decode("utf-8"), expected_output())

    def test_a_rerun_keeps_the_mode_of_the_existing_output(self) -> None:
        self.assertEqual(self.run_main()[0], 0)
        os.chmod(self.output, 0o640)
        self.assertEqual(self.run_main()[0], 0)
        self.assertEqual(os.stat(self.output).st_mode & 0o777, 0o640)

    def test_a_mismatched_digest_prints_both_digests_and_writes_nothing(self) -> None:
        other = os.path.join(self.root, "other.xdr")
        with open(other, "wb") as handle:
            handle.write(SOURCE + b"\x00")
        actual = hashlib.sha256(SOURCE + b"\x00").hexdigest()

        status, out, err = self.run_main(source=other)

        self.assertEqual(status, 1)
        self.assertEqual(out, "")
        self.assertEqual(err, "error: the spec file has sha256 %s, the pin expects %s\n" % (actual, SOURCE_SHA256))
        self.assert_nothing_written()

    def test_a_mismatched_digest_keeps_an_existing_output(self) -> None:
        with open(self.output, "wb") as handle:
            handle.write(b"committed")
        other = os.path.join(self.root, "other.xdr")
        with open(other, "wb") as handle:
            handle.write(b"other")

        self.assertEqual(self.run_main(source=other)[0], 1)
        self.assertEqual(self.read_output(), b"committed")
        self.assertEqual(sorted(os.listdir(os.path.join(self.root, "out"))), ["Spec.kt"])

    def test_a_leftover_placeholder_exits_1_and_writes_nothing(self) -> None:
        self.write_template(TEMPLATE + "const val DATE = \"{{DATE}}\"\n")

        status, out, err = self.run_main()

        self.assertEqual(status, 1)
        self.assertEqual(out, "")
        self.assertEqual(err, "error: the template leaves a placeholder unfilled on line 5: {{DATE}}\"\n")
        self.assert_nothing_written()

    def test_a_missing_source_names_the_path(self) -> None:
        missing = os.path.join(self.root, "missing.xdr")

        status, _, err = self.run_main(source=missing)

        self.assertEqual(status, 1)
        self.assertTrue(err.startswith("error: cannot read %s: " % missing), err)
        self.assertIn("No such file or directory", err)
        self.assert_nothing_written()

    def test_a_missing_template_names_the_path(self) -> None:
        os.remove(os.path.join(self.root, "Spec.kt.template"))

        status, _, err = self.run_main()

        self.assertEqual(status, 1)
        self.assertTrue(err.startswith("error: cannot read %s: " % os.path.join(self.root, "Spec.kt.template")), err)
        self.assert_nothing_written()

    def test_a_missing_output_directory_names_the_path(self) -> None:
        self.pin = self.write_pin(output="absent/Spec.kt")

        status, _, err = self.run_main()

        self.assertEqual(status, 1)
        self.assertTrue(err.startswith("error: cannot write %s: " % os.path.join(self.root, "absent", "Spec.kt")), err)
        self.assertFalse(os.path.exists(os.path.join(self.root, "absent")))

    def test_a_failed_replace_removes_the_temporary_file(self) -> None:
        with mock.patch.object(generate_sac_spec.os, "replace", side_effect=OSError(13, "Permission denied")):
            status, _, err = self.run_main()

        self.assertEqual(status, 1)
        self.assertEqual(err, "error: cannot write %s: [Errno 13] Permission denied\n" % self.output)
        self.assert_nothing_written()

    def test_the_pin_needs_a_40_character_lowercase_hex_commit(self) -> None:
        for commit in (COMMIT.upper(), COMMIT[:39], COMMIT + "0", "g" * 40):
            with self.subTest(commit=commit):
                self.pin = self.write_pin(commit=commit)
                status, _, err = self.run_main()
                self.assertEqual(status, 1)
                self.assertEqual(
                    err,
                    "error: the pin %s needs 'commit' as 40 lowercase hex characters, got %r\n" % (self.pin, commit),
                )
                self.assert_nothing_written()

    def test_the_pin_needs_a_64_character_lowercase_hex_sha256(self) -> None:
        for digest in (SOURCE_SHA256.upper(), SOURCE_SHA256[:63], SOURCE_SHA256 + "0"):
            with self.subTest(sha256=digest):
                self.pin = self.write_pin(sha256=digest)
                status, _, err = self.run_main()
                self.assertEqual(status, 1)
                self.assertEqual(
                    err,
                    "error: the pin %s needs 'sha256' as 64 lowercase hex characters, got %r\n" % (self.pin, digest),
                )
                self.assert_nothing_written()

    def test_the_pin_needs_every_key_as_a_non_empty_string(self) -> None:
        for key in generate_sac_spec.PIN_KEYS:
            for value in (None, "", 7):
                with self.subTest(key=key, value=value):
                    self.pin = self.write_pin(**{key: value})
                    status, _, err = self.run_main()
                    self.assertEqual(status, 1)
                    self.assertEqual(err, "error: the pin %s needs a non-empty string '%s'\n" % (self.pin, key))
                    self.assert_nothing_written()

    def test_a_pin_that_is_no_json_object_or_truncated_exits_1(self) -> None:
        with open(self.pin, "w", encoding="utf-8") as handle:
            handle.write("[]")
        status, _, err = self.run_main()
        self.assertEqual(status, 1)
        self.assertEqual(err, "error: the pin %s is not a JSON object\n" % self.pin)

        with open(self.pin, "w", encoding="utf-8") as handle:
            handle.write("{")
        status, _, err = self.run_main()
        self.assertEqual(status, 1)
        self.assertTrue(err.startswith("error: cannot read the pin %s: " % self.pin), err)
        self.assert_nothing_written()


class DownloadTest(TemporaryRootTest):
    """Without --source, the file comes from the raw URL at the pinned commit."""

    URL = "https://raw.githubusercontent.com/owner/spec-repo/%s/spec.xdr" % COMMIT

    def run_download(self, fake: mock.Mock) -> Tuple[int, str, str]:
        with mock.patch.object(generate_sac_spec.urllib.request, "urlopen", fake):
            return self.run_argv(["--pin", self.pin, "--root", self.root])

    def test_downloads_the_raw_url_with_a_60_second_timeout(self) -> None:
        fake = mock.Mock(return_value=FakeResponse(SOURCE))

        status, _, err = self.run_download(fake)

        self.assertEqual(status, 0, err)
        fake.assert_called_once_with(self.URL, timeout=60)
        self.assertEqual(self.read_output().decode("utf-8"), expected_output())

    def test_a_download_failure_names_the_url_and_the_cause(self) -> None:
        not_found = urllib.error.HTTPError(self.URL, 404, "Not Found", {}, io.BytesIO(b""))
        self.addCleanup(not_found.close)
        failures = {
            "<urlopen error [Errno 8] nodename nor servname provided, or not known>":
                urllib.error.URLError(OSError(8, "nodename nor servname provided, or not known")),
            "HTTP Error 404: Not Found": not_found,
            "timed out": TimeoutError("timed out"),
        }
        for cause, failure in failures.items():
            with self.subTest(cause=cause):
                status, out, err = self.run_download(mock.Mock(side_effect=failure))
                self.assertEqual(status, 1)
                self.assertEqual(out, "")
                self.assertEqual(err, "error: cannot download %s: %s\n" % (self.URL, cause))
                self.assert_nothing_written()

    def test_a_downloaded_file_with_another_digest_writes_nothing(self) -> None:
        status, _, err = self.run_download(mock.Mock(return_value=FakeResponse(b"changed upstream")))

        self.assertEqual(status, 1)
        self.assertEqual(
            err,
            "error: the spec file has sha256 %s, the pin expects %s\n"
            % (hashlib.sha256(b"changed upstream").hexdigest(), SOURCE_SHA256),
        )
        self.assert_nothing_written()


class DefaultsTest(unittest.TestCase):
    """Without arguments, main resolves the paths of the pin beside the script against the repository root."""

    def test_main_without_arguments_generates_from_the_default_pin_and_root(self) -> None:
        fake = mock.Mock(return_value="/out/Spec.kt")

        with mock.patch.object(generate_sac_spec, "generate", fake):
            status, out, err = TemporaryRootTest.run_argv([])

        self.assertEqual(status, 0, err)
        self.assertEqual(out, "wrote /out/Spec.kt\n")
        fake.assert_called_once_with(generate_sac_spec.DEFAULT_PIN, generate_sac_spec.DEFAULT_ROOT, None)

    def test_the_default_pin_sits_beside_the_script_and_the_root_two_directories_up(self) -> None:
        script_dir = os.path.dirname(os.path.abspath(generate_sac_spec.__file__))
        repository_root = os.path.dirname(os.path.dirname(script_dir))

        self.assertEqual(generate_sac_spec.DEFAULT_PIN, os.path.join(script_dir, "pin.json"))
        self.assertTrue(os.path.isfile(generate_sac_spec.DEFAULT_PIN))
        self.assertEqual(generate_sac_spec.DEFAULT_ROOT, repository_root)
        self.assertEqual(os.path.join(repository_root, "tools", "sac-spec"), script_dir)


class CommittedSpecTest(unittest.TestCase):
    """The committed output matches the committed pin."""

    def setUp(self) -> None:
        with open(generate_sac_spec.DEFAULT_PIN, encoding="utf-8") as handle:
            self.pin = json.load(handle)
        output = os.path.join(generate_sac_spec.DEFAULT_ROOT, self.pin["output"])
        with open(output, "rb") as handle:
            self.committed = handle.read()
        self.text = self.committed.decode("utf-8")

    def committed_bytes(self) -> bytes:
        encoded = re.search(r'const val XDR_BASE64: String = "([A-Za-z0-9+/=]*)"', self.text).group(1)
        return base64.b64decode(encoded, validate=True)

    def test_carries_the_pinned_commit_digest_and_header(self) -> None:
        self.assertIn('const val UPSTREAM_COMMIT: String = "%s"' % self.pin["commit"], self.text)
        self.assertIn('const val SHA256: String = "%s"' % self.pin["sha256"], self.text)
        self.assertEqual(hashlib.sha256(self.committed_bytes()).hexdigest(), self.pin["sha256"])
        self.assertNotIn("{{", self.text)
        self.assertNotIn(b"\r", self.committed)
        # The first line is the template's first line filled from the pin and the committed bytes,
        # so the test follows the template and fails when a header value is dropped.
        with open(os.path.join(generate_sac_spec.DEFAULT_ROOT, self.pin["template"]), encoding="utf-8") as handle:
            template_header = handle.readline().rstrip("\n")
        expected_first = template_header.replace("{{REPOSITORY}}", self.pin["repository"]).replace(
            "{{FILE}}", self.pin["file"]).replace("{{COMMIT}}", self.pin["commit"]).replace(
            "{{SIZE}}", str(len(self.committed_bytes()))).replace("{{SHA256}}", self.pin["sha256"])
        lines = self.text.split("\n")
        self.assertEqual(lines[0], expected_first)
        self.assertEqual(lines[1], "// Do not edit; change tools/sac-spec/pin.json and regenerate.")

    def test_regenerating_from_the_embedded_bytes_reproduces_the_file(self) -> None:
        root = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, root)
        template = os.path.join(root, self.pin["template"])
        os.makedirs(os.path.dirname(template))
        shutil.copyfile(os.path.join(generate_sac_spec.DEFAULT_ROOT, self.pin["template"]), template)
        os.makedirs(os.path.dirname(os.path.join(root, self.pin["output"])))
        source = os.path.join(root, self.pin["file"])
        with open(source, "wb") as handle:
            handle.write(self.committed_bytes())

        status, _, err = TemporaryRootTest.run_argv(
            ["--pin", generate_sac_spec.DEFAULT_PIN, "--root", root, "--source", source]
        )

        self.assertEqual(status, 0, err)
        with open(os.path.join(root, self.pin["output"]), "rb") as handle:
            self.assertEqual(handle.read(), self.committed)


if __name__ == "__main__":
    unittest.main()
