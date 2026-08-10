#!/usr/bin/env python3
"""
Compatibility Analysis Orchestrator

Runs all compatibility analysis scripts in sequence: Horizon, RPC, and
all auto-detected SEPs. Provides colored terminal output, per-SEP coverage
percentages, and a consolidated summary of generated report files.

Usage:
    python run_analysis.py
"""

import json
import sys
import subprocess
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from common import Colors, DATA_DIR, COMPATIBILITY_DIR, SDK_ROOT, TOOLS_DIR, get_sdk_version


# Human-readable titles for known SEPs (used in summary display)
KNOWN_SEPS: Dict[str, str] = {
    '0001': 'stellar.toml',
    '0002': 'Federation Protocol',
    '0005': 'Key Derivation',
    '0006': 'Deposit and Withdrawal API',
    '0007': 'URI Scheme',
    '0008': 'Regulated Assets',
    '0009': 'Standard KYC Fields',
    '0010': 'Web Authentication',
    '0011': 'Txrep',
    '0012': 'KYC API',
    '0024': 'Hosted Deposit/Withdrawal',
    '0030': 'Account Recovery',
    '0031': 'Cross-Border Payments',
    '0038': 'Anchor RFQ API',
    '0045': 'Web Auth for Contract Accounts',
    '0046': 'Contract Meta',
    '0047': 'Contract Interface Discovery',
    '0048': 'Contract Interface Specification',
    '0051': 'XDR-JSON',
    '0053': 'Message Signing',
}

# SEP source directory relative to SDK_ROOT
_SEP_SOURCE_DIR = (
    SDK_ROOT
    / "stellar-sdk"
    / "src"
    / "commonMain"
    / "kotlin"
    / "com"
    / "soneso"
    / "stellar"
    / "sdk"
    / "sep"
)

# KeyPair.kt path for SEP-53 detection
_KEYPAIR_FILE = (
    SDK_ROOT
    / "stellar-sdk"
    / "src"
    / "commonMain"
    / "kotlin"
    / "com"
    / "soneso"
    / "stellar"
    / "sdk"
    / "KeyPair.kt"
)

# SorobanContractParser.kt path for SEP-46/47/48 detection
_CONTRACT_PARSER_FILE = (
    SDK_ROOT
    / "stellar-sdk"
    / "src"
    / "commonMain"
    / "kotlin"
    / "com"
    / "soneso"
    / "stellar"
    / "sdk"
    / "contract"
    / "SorobanContractParser.kt"
)

# Generated XDR package used for SEP-51 detection
_XDR_SOURCE_DIR = (
    SDK_ROOT
    / "stellar-sdk"
    / "src"
    / "commonMain"
    / "kotlin"
    / "com"
    / "soneso"
    / "stellar"
    / "sdk"
    / "xdr"
)

# Shared XDR-JSON runtime backing the conversion methods
_XDR_JSON_FILE = _XDR_SOURCE_DIR / "XdrJson.kt"

# Generated XDR type sampled to confirm the conversion methods are emitted
_XDR_JSON_TYPE_FILE = _XDR_SOURCE_DIR / "TransactionEnvelopeXdr.kt"


class AnalysisOrchestrator:
    """Orchestrates Horizon, RPC, and SEP compatibility analysis pipelines."""

    def __init__(self) -> None:
        self.tools_dir: Path = TOOLS_DIR
        self.sdk_root: Path = SDK_ROOT
        self.sep_coverage: Dict[str, float] = {}
        # (description, success, output) for every step executed
        self.results: List[Tuple[str, bool, str]] = []

    # ------------------------------------------------------------------
    # SEP auto-detection
    # ------------------------------------------------------------------

    def detect_implemented_seps(self) -> List[str]:
        """Scan the SDK sep/ directory and return sorted, zero-padded SEP numbers.

        Returns:
            Sorted list of 4-digit SEP numbers (e.g. ['0001', '0002', '0053']).
        """
        sep_numbers: List[str] = []

        if _SEP_SOURCE_DIR.exists():
            for item in _SEP_SOURCE_DIR.iterdir():
                if item.is_dir() and item.name.startswith('sep'):
                    raw = item.name[3:]  # strip 'sep' prefix
                    if raw.isdigit():
                        sep_numbers.append(raw.zfill(4))

        # SEP-53 (Sign and Verify Messages) lives in KeyPair.kt, not a sep53/ dir
        if _KEYPAIR_FILE.exists():
            content = _KEYPAIR_FILE.read_text(encoding='utf-8')
            if (
                'signMessage' in content
                and 'verifyMessage' in content
                and 'MESSAGE_PREFIX' in content
            ):
                padded = '0053'
                if padded not in sep_numbers:
                    sep_numbers.append(padded)

        # SEP-46 (Contract Meta), SEP-47 (Contract Interface Discovery), and
        # SEP-48 (Contract Interface Specification) are implemented in
        # SorobanContractParser.kt inside the contract/ directory.
        if _CONTRACT_PARSER_FILE.exists():
            content = _CONTRACT_PARSER_FILE.read_text(encoding='utf-8')
            # SEP-46: contractmetav0 section parsing + SCMetaEntryXdr
            if 'contractmetav0' in content and 'SCMetaEntryXdr' in content:
                for padded in ('0046', '0047', '0048'):
                    if padded not in sep_numbers:
                        sep_numbers.append(padded)

        # SEP-51 (XDR-JSON) has no sep51/ directory: the conversion methods are emitted
        # onto every generated XDR type and delegate to the XdrJson.kt runtime. Both
        # halves are checked, since the runtime alone does not prove the methods reach
        # the types, and one type alone does not prove the runtime exists.
        if _XDR_JSON_FILE.exists() and _XDR_JSON_TYPE_FILE.exists():
            runtime = _XDR_JSON_FILE.read_text(encoding='utf-8')
            xdr_type = _XDR_JSON_TYPE_FILE.read_text(encoding='utf-8')
            if (
                'object XdrJson' in runtime
                and 'fun toXdrJson' in xdr_type
                and 'fun fromXdrJson' in xdr_type
            ):
                padded = '0051'
                if padded not in sep_numbers:
                    sep_numbers.append(padded)

        return sorted(sep_numbers)

    # ------------------------------------------------------------------
    # Script execution
    # ------------------------------------------------------------------

    def _run_script(
        self, script_spec: str, description: str
    ) -> Tuple[bool, str]:
        """Run a single analysis script via subprocess.

        Args:
            script_spec: Relative path from TOOLS_DIR, optionally followed by
                         space-separated arguments (e.g. "sep/sep_parser.py 0010").
            description: Human-readable description for log output.

        Returns:
            Tuple of (success, combined_output).
        """
        parts = script_spec.split()
        script_path = self.tools_dir / parts[0]
        script_args = parts[1:]

        if not script_path.exists():
            return False, f"Script not found: {script_path}"

        try:
            result = subprocess.run(
                [sys.executable, str(script_path)] + script_args,
                capture_output=True,
                text=True,
                timeout=300,
                cwd=str(self.tools_dir),
            )
            if result.returncode == 0:
                return True, result.stdout
            error_msg = result.stderr if result.stderr else result.stdout
            return False, (
                f"Script failed with exit code {result.returncode}:\n{error_msg}"
            )
        except subprocess.TimeoutExpired:
            return False, "Script execution timed out (5 minutes)"
        except Exception as exc:  # pylint: disable=broad-except
            return False, f"Error running script: {exc}"

    # ------------------------------------------------------------------
    # Output helpers
    # ------------------------------------------------------------------

    def _print_header(self) -> None:
        print(f"\n{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}")
        print(
            f"{Colors.BOLD}{Colors.HEADER}"
            f"KMP Stellar SDK - Compatibility Analysis"
            f"{Colors.END}"
        )
        print(f"{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}\n")
        print(f"SDK Version : {get_sdk_version()}")
        print(f"Started     : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")

    def _print_step(self, step_num: int, total: int, description: str) -> None:
        print(
            f"\n{Colors.BOLD}{Colors.CYAN}"
            f"[{step_num}/{total}] {description}"
            f"{Colors.END}"
        )
        print(f"{Colors.CYAN}{'-' * 70}{Colors.END}")

    def _print_script_result(
        self, description: str, success: bool, output: str
    ) -> None:
        if success:
            print(
                f"{Colors.GREEN}  {description} completed successfully{Colors.END}"
            )
            for line in output.splitlines():
                stripped = line.strip()
                if stripped and any(
                    kw in line
                    for kw in ('Total', 'Coverage', 'Saved', 'checkmark')
                ) or '\u2713' in line or '\u2714' in line:
                    print(f"    {stripped}")
        else:
            print(f"{Colors.RED}  {description} failed{Colors.END}")
            print(f"{Colors.RED}{output}{Colors.END}")

    # ------------------------------------------------------------------
    # Coverage helpers
    # ------------------------------------------------------------------

    def _read_sep_coverage(self, sep_number: str) -> float:
        """Read coverage percentage from a SEP coverage_stats JSON file."""
        stats_file = (
            DATA_DIR / 'sep' / f"sep_{sep_number}_coverage_stats.json"
        )
        if not stats_file.exists():
            return 0.0
        try:
            data = json.loads(stats_file.read_text(encoding='utf-8'))
            return float(data.get('overall', {}).get('coverage_percentage', 0.0))
        except Exception:  # pylint: disable=broad-except
            return 0.0

    # ------------------------------------------------------------------
    # Pipeline runners
    # ------------------------------------------------------------------

    def _run_horizon(self, step_num: int, total: int) -> bool:
        """Run the Horizon analysis pipeline (single script)."""
        script = 'horizon/run_horizon_analysis.py'
        description = 'Generating Horizon compatibility report'
        self._print_step(step_num, total, description)
        success, output = self._run_script(script, description)
        self._print_script_result(description, success, output)
        self.results.append((description, success, output))
        return success

    def _run_rpc(self, step_num: int, total: int) -> bool:
        """Run the RPC analysis pipeline (single script)."""
        script = 'rpc/run_rpc_analysis.py'
        description = 'Generating RPC compatibility report'
        self._print_step(step_num, total, description)
        success, output = self._run_script(script, description)
        self._print_script_result(description, success, output)
        self.results.append((description, success, output))
        return success

    def _run_sep_pipeline(
        self,
        sep_numbers: List[str],
        base_step: int,
        total: int,
    ) -> bool:
        """Run the 3-stage SEP pipeline for every detected SEP.

        Failures are reported but do not stop processing of remaining SEPs.

        Args:
            sep_numbers: Sorted list of 4-digit SEP numbers.
            base_step: Step counter offset (Horizon and RPC already consumed).
            total: Total step count for display.

        Returns:
            True if every SEP stage succeeded for every SEP.
        """
        stages = [
            ('sep/sep_parser.py',             'Parsing SEP-{num} specification'),
            ('sep/sep_analyzer.py',           'Analyzing SEP-{num} implementation'),
            ('sep/generate_sep_comparison.py','Generating SEP-{num} compatibility report'),
        ]

        all_success = True

        for sep_idx, sep_num in enumerate(sep_numbers):
            sep_label = f"SEP-{int(sep_num):04d}"
            step = base_step + sep_idx * len(stages)

            for stage_offset, (script_base, desc_template) in enumerate(stages):
                description = desc_template.format(num=sep_num)
                script_spec = f"{script_base} {sep_num}"
                current_step = step + stage_offset

                self._print_step(current_step, total, description)

                success, output = self._run_script(script_spec, description)
                self._print_script_result(description, success, output)
                self.results.append((description, success, output))

                if not success:
                    all_success = False

            # Read coverage after all 3 stages for this SEP
            self.sep_coverage[sep_num] = self._read_sep_coverage(sep_num)

        return all_success

    # ------------------------------------------------------------------
    # Summary
    # ------------------------------------------------------------------

    def _print_summary(self, sep_numbers: List[str]) -> None:
        print(f"\n{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}")
        print(f"{Colors.BOLD}{Colors.HEADER}Analysis Summary{Colors.END}")
        print(f"{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}\n")

        success_count = sum(1 for _, ok, _ in self.results if ok)
        total_count = len(self.results)

        print(f"Completed : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        print(f"Results   : {success_count}/{total_count} scripts succeeded\n")

        for description, success, _ in self.results:
            icon = (
                f"{Colors.GREEN}\u2713{Colors.END}"
                if success
                else f"{Colors.RED}\u2717{Colors.END}"
            )
            print(f"  {icon} {description}")

        # --- SEP coverage table ---
        if self.sep_coverage:
            print(f"\n{Colors.BOLD}SEP Coverage:{Colors.END}\n")
            for sep_num in sorted(self.sep_coverage):
                title = KNOWN_SEPS.get(sep_num, f'SEP-{sep_num}')
                pct = self.sep_coverage[sep_num]
                if pct >= 90:
                    color = Colors.GREEN
                elif pct >= 70:
                    color = Colors.YELLOW
                else:
                    color = Colors.RED
                print(
                    f"  SEP-{sep_num} ({title:<35}): "
                    f"{color}{pct:>5.1f}%{Colors.END}"
                )

        # --- Generated files ---
        print(f"\n{Colors.BOLD}Generated Reports:{Colors.END}\n")

        reports: List[Tuple[str, Path]] = [
            (
                'Horizon Endpoints',
                DATA_DIR / 'horizon' / 'horizon_endpoints.json',
            ),
            (
                'KMP SDK Horizon Implementation',
                DATA_DIR / 'horizon' / 'kmp_sdk_implementation.json',
            ),
            (
                'Horizon Compatibility Matrix',
                COMPATIBILITY_DIR / 'horizon' / 'HORIZON_COMPATIBILITY_MATRIX.md',
            ),
            (
                'Horizon Coverage Statistics',
                DATA_DIR / 'horizon' / 'coverage_stats.json',
            ),
            (
                'RPC Methods',
                DATA_DIR / 'rpc' / 'rpc_methods.json',
            ),
            (
                'KMP SDK Soroban Implementation',
                DATA_DIR / 'rpc' / 'kmp_soroban_implementation.json',
            ),
            (
                'RPC Compatibility Matrix',
                COMPATIBILITY_DIR / 'rpc' / 'RPC_COMPATIBILITY_MATRIX.md',
            ),
            (
                'RPC Coverage Statistics',
                DATA_DIR / 'rpc' / 'rpc_coverage_stats.json',
            ),
        ]

        for sep_num in sorted(sep_numbers):
            sep_label = f"SEP-{sep_num}"
            reports.extend([
                (
                    f'{sep_label} Definition',
                    DATA_DIR / 'sep' / f'sep_{sep_num}_definition.json',
                ),
                (
                    f'{sep_label} KMP Implementation',
                    DATA_DIR / 'sep' / f'kmp_sep_{sep_num}_implementation.json',
                ),
                (
                    f'{sep_label} Compatibility Matrix',
                    COMPATIBILITY_DIR / 'sep' / f'SEP-{sep_num}_COMPATIBILITY_MATRIX.md',
                ),
                (
                    f'{sep_label} Coverage Statistics',
                    DATA_DIR / 'sep' / f'sep_{sep_num}_coverage_stats.json',
                ),
            ])

        for report_name, report_path in reports:
            if report_path.exists():
                print(f"  {Colors.GREEN}\u2713{Colors.END} {report_name}")
                print(f"    {Colors.CYAN}{report_path}{Colors.END}")
            else:
                print(f"  {Colors.YELLOW}\u26a0{Colors.END} {report_name} (not generated)")

        print()

        if success_count == total_count:
            print(f"{Colors.BOLD}{Colors.GREEN}{'=' * 70}{Colors.END}")
            print(
                f"{Colors.BOLD}{Colors.GREEN}"
                f"All analyses completed successfully!"
                f"{Colors.END}"
            )
            print(f"{Colors.BOLD}{Colors.GREEN}{'=' * 70}{Colors.END}\n")
        else:
            print(f"{Colors.BOLD}{Colors.YELLOW}{'=' * 70}{Colors.END}")
            print(
                f"{Colors.BOLD}{Colors.YELLOW}"
                f"Some analyses failed. Review errors above."
                f"{Colors.END}"
            )
            print(f"{Colors.BOLD}{Colors.YELLOW}{'=' * 70}{Colors.END}\n")

    # ------------------------------------------------------------------
    # Prerequisites
    # ------------------------------------------------------------------

    def verify_prerequisites(self) -> Tuple[bool, List[str]]:
        """Verify that all prerequisites are met before running analysis.

        Returns:
            Tuple of (ok, list_of_error_messages).
        """
        errors: List[str] = []

        # Python version
        if sys.version_info < (3, 8):
            errors.append(
                f"Python 3.8+ required, found "
                f"{sys.version_info.major}.{sys.version_info.minor}"
            )

        # SDK source files
        sdk_sep_dir = _SEP_SOURCE_DIR
        if not sdk_sep_dir.exists():
            errors.append(f"SDK sep/ source directory not found at {sdk_sep_dir}")

        horizon_dir = (
            SDK_ROOT
            / "stellar-sdk"
            / "src"
            / "commonMain"
            / "kotlin"
            / "com"
            / "soneso"
            / "stellar"
            / "sdk"
            / "horizon"
        )
        if not horizon_dir.exists():
            errors.append(f"SDK horizon/ source directory not found at {horizon_dir}")

        rpc_dir = (
            SDK_ROOT
            / "stellar-sdk"
            / "src"
            / "commonMain"
            / "kotlin"
            / "com"
            / "soneso"
            / "stellar"
            / "sdk"
            / "rpc"
        )
        if not rpc_dir.exists():
            errors.append(f"SDK rpc/ source directory not found at {rpc_dir}")

        # Required pipeline scripts
        required_scripts = [
            'horizon/run_horizon_analysis.py',
            'rpc/run_rpc_analysis.py',
            'sep/sep_parser.py',
            'sep/sep_analyzer.py',
            'sep/generate_sep_comparison.py',
        ]
        for rel in required_scripts:
            path = self.tools_dir / rel
            if not path.exists():
                errors.append(f"Required script not found: {path}")

        return len(errors) == 0, errors

    # ------------------------------------------------------------------
    # Main entry point
    # ------------------------------------------------------------------

    def run(self) -> int:
        """Execute all analysis pipelines.

        Returns:
            0 on full success, 1 if any pipeline step failed.
        """
        self._print_header()

        sep_numbers = self.detect_implemented_seps()
        print(
            f"Detected {len(sep_numbers)} SEP implementation(s): "
            f"{', '.join(f'SEP-{n}' for n in sep_numbers)}\n"
        )

        # Total steps: 1 horizon + 1 rpc + 3 stages per SEP
        total_steps = 2 + len(sep_numbers) * 3

        # --- Horizon (critical) ---
        horizon_ok = self._run_horizon(step_num=1, total=total_steps)
        if not horizon_ok:
            print(
                f"\n{Colors.YELLOW}"
                f"Stopping: Horizon analysis failed (critical)."
                f"{Colors.END}"
            )
            self._print_summary(sep_numbers)
            return 1

        # --- RPC (critical) ---
        rpc_ok = self._run_rpc(step_num=2, total=total_steps)
        if not rpc_ok:
            print(
                f"\n{Colors.YELLOW}"
                f"Stopping: RPC analysis failed (critical)."
                f"{Colors.END}"
            )
            self._print_summary(sep_numbers)
            return 1

        # --- SEP pipelines (non-critical: failures reported, not fatal) ---
        sep_ok = True
        if sep_numbers:
            sep_ok = self._run_sep_pipeline(
                sep_numbers=sep_numbers,
                base_step=3,
                total=total_steps,
            )
        else:
            print(
                f"\n{Colors.YELLOW}"
                f"No SEP implementations detected; skipping SEP analysis."
                f"{Colors.END}"
            )

        self._print_summary(sep_numbers)

        return 0 if (horizon_ok and rpc_ok and sep_ok) else 1


def main() -> int:
    """Main entry point."""
    if not sys.stdout.isatty():
        Colors.disable()

    orchestrator = AnalysisOrchestrator()

    prereq_ok, errors = orchestrator.verify_prerequisites()
    if not prereq_ok:
        print(f"{Colors.RED}ERROR: Prerequisites not met:{Colors.END}\n")
        for error in errors:
            print(f"  {Colors.RED}\u2717{Colors.END} {error}")
        print(
            f"\n{Colors.YELLOW}"
            f"Ensure all required scripts exist and the SDK source tree is intact."
            f"{Colors.END}"
        )
        return 1

    return orchestrator.run()


if __name__ == '__main__':
    sys.exit(main())
