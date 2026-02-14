#!/usr/bin/env python3
"""
SEP Compatibility Analysis Orchestrator

Runs the full 3-stage SEP compatibility analysis pipeline for all implemented SEPs.
Auto-detects implemented SEPs by scanning the SDK sep/ directory.

Author: KMP Stellar SDK Team
Date: 2025-10-05
License: Apache-2.0
"""

import sys
import subprocess
from pathlib import Path
from typing import List, Tuple, Dict
from datetime import datetime
import time


class Colors:
    """ANSI color codes for terminal output"""
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'
    END = '\033[0m'

    @classmethod
    def disable(cls):
        """Disable colors (for non-TTY output)"""
        cls.HEADER = ''
        cls.BLUE = ''
        cls.CYAN = ''
        cls.GREEN = ''
        cls.YELLOW = ''
        cls.RED = ''
        cls.BOLD = ''
        cls.UNDERLINE = ''
        cls.END = ''


class SEPAnalysisOrchestrator:
    """Orchestrates the execution of SEP analysis pipeline"""

    def __init__(self):
        """Initialize orchestrator"""
        self.tools_dir = Path(__file__).parent
        self.project_root = self.tools_dir.parent.parent.parent  # Go up 4 levels to project root
        self.sep_dir = self.project_root / "stellar-sdk" / "src" / "commonMain" / "kotlin" / "com" / "soneso" / "stellar" / "sdk" / "sep"
        self.output_dir = self.project_root / "compatibility" / "sep"
        self.results: List[Tuple[str, bool, float]] = []
        self.sep_coverage: Dict[str, float] = {}

    def detect_implemented_seps(self) -> List[str]:
        """
        Auto-detect implemented SEPs by scanning the sep/ directory

        Returns:
            List of SEP numbers (e.g., ['0001', '0002', '0005'])
        """
        if not self.sep_dir.exists():
            return []

        sep_numbers = []
        for item in self.sep_dir.iterdir():
            if item.is_dir() and item.name.startswith('sep'):
                # Extract number from directory name (e.g., 'sep01' -> '0001')
                sep_num = item.name[3:]  # Remove 'sep' prefix
                # Pad to 4 digits
                sep_num_padded = sep_num.zfill(4)
                sep_numbers.append(sep_num_padded)

        # Special case: SEP-53 lives in KeyPair.kt, not in a sep53/ directory
        # Check if KeyPair.kt has SEP-53 methods
        keypair_file = self.project_root / 'stellar-sdk' / 'src' / 'commonMain' / 'kotlin' / 'com' / 'soneso' / 'stellar' / 'sdk' / 'KeyPair.kt'
        if keypair_file.exists():
            content = keypair_file.read_text(encoding='utf-8')
            if 'signMessage' in content and 'verifyMessage' in content and 'MESSAGE_PREFIX' in content:
                sep_numbers.append('0053')

        return sorted(sep_numbers)

    def print_header(self):
        """Print analysis header"""
        print(f"\n{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}")
        print(f"{Colors.BOLD}{Colors.HEADER}KMP Stellar SDK - SEP Compatibility Analysis{Colors.END}")
        print(f"{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}\n")
        print(f"Started: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")

    def print_stage_header(self, stage_num: int, stage_name: str):
        """Print stage header"""
        print(f"\n{Colors.BOLD}{Colors.CYAN}Step {stage_num}/3: {stage_name}{Colors.END}")
        print(f"{Colors.CYAN}{'=' * 70}{Colors.END}")

    def run_script(self, script_name: str, sep_number: str) -> Tuple[bool, str, float]:
        """
        Run a single analysis script

        Args:
            script_name: Name of the script to run (without .py extension)
            sep_number: SEP number (e.g., '0001')

        Returns:
            Tuple of (success, output, execution_time)
        """
        script_path = self.tools_dir / f"{script_name}.py"

        if not script_path.exists():
            return False, f"Script not found: {script_path}", 0.0

        start_time = time.time()

        try:
            result = subprocess.run(
                [sys.executable, str(script_path), sep_number],
                capture_output=True,
                text=True,
                timeout=300  # 5 minute timeout per script
            )

            elapsed = time.time() - start_time

            if result.returncode == 0:
                return True, result.stdout, elapsed
            else:
                error_msg = result.stderr if result.stderr else result.stdout
                return False, f"Script failed with exit code {result.returncode}:\n{error_msg}", elapsed

        except subprocess.TimeoutExpired:
            elapsed = time.time() - start_time
            return False, "Script execution timed out (5 minutes)", elapsed
        except Exception as e:
            elapsed = time.time() - start_time
            return False, f"Error running script: {str(e)}", elapsed

    def extract_coverage(self, sep_number: str) -> float:
        """
        Extract coverage percentage from the coverage stats JSON file

        Args:
            sep_number: SEP number (e.g., '0001')

        Returns:
            Coverage percentage (0.0 to 100.0)
        """
        stats_file = self.output_dir / "data" / f"sep_{sep_number}_coverage_stats.json"
        if not stats_file.exists():
            return 0.0

        try:
            import json
            with open(stats_file, 'r') as f:
                stats = json.load(f)
                return stats.get('overall', {}).get('coverage_percentage', 0.0)
        except Exception:
            return 0.0

    def run_pipeline(self, sep_numbers: List[str]) -> bool:
        """
        Run the 3-stage pipeline for all SEPs

        Args:
            sep_numbers: List of SEP numbers to process

        Returns:
            True if all pipelines succeeded, False otherwise
        """
        if not sep_numbers:
            print(f"{Colors.YELLOW}No implemented SEPs found in {self.sep_dir}{Colors.END}\n")
            return False

        all_success = True
        total_start = time.time()

        # Stage 1: Parse SEP specifications
        self.print_stage_header(1, "Parsing SEP specifications from GitHub")
        for sep_num in sep_numbers:
            success, output, elapsed = self.run_script("sep_parser", sep_num)
            status = f"{Colors.GREEN}done{Colors.END}" if success else f"{Colors.RED}failed{Colors.END}"
            print(f"  SEP-{sep_num}... {status} ({elapsed:.1f}s)")

            if not success:
                print(f"{Colors.RED}{output}{Colors.END}")
                all_success = False

        # Stage 2: Analyze SDK implementations
        self.print_stage_header(2, "Analyzing SDK implementations")
        for sep_num in sep_numbers:
            success, output, elapsed = self.run_script("sep_analyzer", sep_num)
            status = f"{Colors.GREEN}done{Colors.END}" if success else f"{Colors.RED}failed{Colors.END}"
            print(f"  SEP-{sep_num}... {status} ({elapsed:.1f}s)")

            if not success:
                print(f"{Colors.RED}{output}{Colors.END}")
                all_success = False

        # Stage 3: Generate comparison reports
        self.print_stage_header(3, "Generating comparison reports")
        for sep_num in sep_numbers:
            success, output, elapsed = self.run_script("generate_sep_comparison", sep_num)
            status = f"{Colors.GREEN}done{Colors.END}" if success else f"{Colors.RED}failed{Colors.END}"
            print(f"  SEP-{sep_num}... {status} ({elapsed:.1f}s)")

            if not success:
                print(f"{Colors.RED}{output}{Colors.END}")
                all_success = False

            # Extract coverage percentage
            self.sep_coverage[sep_num] = self.extract_coverage(sep_num)
            self.results.append((f"SEP-{sep_num}", success, elapsed))

        total_elapsed = time.time() - total_start
        return all_success, total_elapsed

    def get_sep_name(self, sep_number: str) -> str:
        """
        Get human-readable SEP name

        Args:
            sep_number: SEP number (e.g., '0001')

        Returns:
            SEP name (e.g., 'stellar.toml')
        """
        sep_names = {
            '0001': 'stellar.toml',
            '0002': 'Federation Protocol',
            '0005': 'Key Derivation',
            '0006': 'Deposit and Withdrawal API',
            '0008': 'Regulated Assets',
            '0009': 'Standard KYC Fields',
            '0010': 'Web Authentication',
            '0012': 'KYC API',
            '0024': 'Hosted Deposit/Withdrawal',
            '0030': 'Account Recovery',
            '0038': 'Anchor RFQ API',
            '0045': 'Web Auth for Contract Accounts',
            '0053': 'Message Signing',
        }
        return sep_names.get(sep_number, f'SEP-{sep_number}')

    def print_summary(self, total_elapsed: float):
        """Print final summary"""
        print(f"\n{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}")
        print(f"{Colors.BOLD}{Colors.HEADER}SEP COMPATIBILITY ANALYSIS SUMMARY{Colors.END}")
        print(f"{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}\n")

        # Print coverage for each SEP
        for sep_num in sorted(self.sep_coverage.keys()):
            sep_name = self.get_sep_name(sep_num)
            coverage = self.sep_coverage[sep_num]
            color = Colors.GREEN if coverage >= 90 else Colors.YELLOW if coverage >= 70 else Colors.RED
            print(f"  SEP-{sep_num} ({sep_name:<30}): {color}{coverage:>5.1f}%{Colors.END} coverage")

        print()

        # Success summary
        success_count = sum(1 for _, success, _ in self.results if success)
        total_count = len(self.results)

        print(f"Total SEPs analyzed: {Colors.BOLD}{total_count}{Colors.END}")

        if success_count == total_count:
            print(f"All analyses passed: {Colors.GREEN}{Colors.BOLD}YES{Colors.END}")
        else:
            print(f"All analyses passed: {Colors.RED}{Colors.BOLD}NO{Colors.END} ({success_count}/{total_count} succeeded)")

        print(f"Total time: {Colors.BOLD}{total_elapsed:.1f}s{Colors.END}")
        print()
        print(f"Output directory: {Colors.CYAN}{self.output_dir}{Colors.END}")
        print(f"{Colors.BOLD}{Colors.HEADER}{'=' * 70}{Colors.END}\n")

    def verify_prerequisites(self) -> Tuple[bool, List[str]]:
        """
        Verify that all prerequisites are met

        Returns:
            Tuple of (success, list of error messages)
        """
        errors = []

        # Check if SEP directory exists
        if not self.sep_dir.exists():
            errors.append(f"SEP directory not found at {self.sep_dir}")

        # Check if scripts exist
        required_scripts = ['sep_parser.py', 'sep_analyzer.py', 'generate_sep_comparison.py']
        for script in required_scripts:
            script_path = self.tools_dir / script
            if not script_path.exists():
                errors.append(f"Required script not found: {script_path}")

        # Check Python version
        if sys.version_info < (3, 8):
            errors.append(f"Python 3.8+ required, found {sys.version_info.major}.{sys.version_info.minor}")

        return len(errors) == 0, errors


def main():
    """Main entry point"""
    # Check if we're in a TTY (for colors)
    if not sys.stdout.isatty():
        Colors.disable()

    orchestrator = SEPAnalysisOrchestrator()

    # Verify prerequisites
    prereq_ok, errors = orchestrator.verify_prerequisites()
    if not prereq_ok:
        print(f"{Colors.RED}ERROR: Prerequisites not met:{Colors.END}\n")
        for error in errors:
            print(f"  {Colors.RED}✗{Colors.END} {error}")
        print(f"\n{Colors.YELLOW}Please ensure all required scripts are present and paths are correct.{Colors.END}")
        return 1

    # Print header
    orchestrator.print_header()

    # Detect implemented SEPs
    sep_numbers = orchestrator.detect_implemented_seps()
    print(f"Detected {len(sep_numbers)} implemented SEPs: {', '.join([f'SEP-{n}' for n in sep_numbers])}\n")

    if not sep_numbers:
        print(f"{Colors.YELLOW}No SEPs to analyze. Exiting.{Colors.END}\n")
        return 0

    # Run pipeline
    all_success, total_elapsed = orchestrator.run_pipeline(sep_numbers)

    # Print summary
    orchestrator.print_summary(total_elapsed)

    return 0 if all_success else 1


if __name__ == '__main__':
    sys.exit(main())
