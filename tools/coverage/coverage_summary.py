#!/usr/bin/env python3
"""Codecov-style coverage summary from a Kover XML report.

Computes the same metric the Codecov badge shows: a line counts as covered
only when all of its branches are covered. Lines with partially covered
branches count as uncovered.

    metric = hits / (hits + partials + misses)

where, per report line: covered instructions > 0 and missed branches == 0 is
a hit; covered instructions > 0 and missed branches > 0 is a partial; covered
instructions == 0 is a miss.

Usage:
    python3 tools/coverage/coverage_summary.py [--report PATH] [--min PERCENT] [--packages]

Exits non-zero when --min is given and the overall metric is below it.
"""

import argparse
import sys
import xml.etree.ElementTree as ET

DEFAULT_REPORT = "stellar-sdk/build/reports/kover/reportJvmOnly.xml"


def classify(package):
    hits = partials = misses = 0
    for sourcefile in package.findall("sourcefile"):
        for line in sourcefile.findall("line"):
            ci = int(line.get("ci", 0))
            mb = int(line.get("mb", 0))
            if ci == 0:
                misses += 1
            elif mb > 0:
                partials += 1
            else:
                hits += 1
    return hits, partials, misses


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--report", default=DEFAULT_REPORT, help="Kover XML report path")
    parser.add_argument("--min", type=float, default=None, dest="minimum",
                        help="fail (exit 1) when overall coverage is below this percentage")
    parser.add_argument("--packages", action="store_true",
                        help="print per-package breakdown")
    args = parser.parse_args()

    try:
        root = ET.parse(args.report).getroot()
    except (OSError, ET.ParseError) as error:
        print(f"error: cannot read report {args.report}: {error}", file=sys.stderr)
        return 2

    total_hits = total_partials = total_misses = 0
    rows = []
    for package in root.findall("package"):
        hits, partials, misses = classify(package)
        total_hits += hits
        total_partials += partials
        total_misses += misses
        lines = hits + partials + misses
        if lines:
            rows.append((package.get("name").replace("/", "."), hits, partials, misses))

    total_lines = total_hits + total_partials + total_misses
    if total_lines == 0:
        print("error: report contains no line data", file=sys.stderr)
        return 2

    coverage = 100.0 * total_hits / total_lines

    if args.packages:
        print(f"{'package':<60} {'hits':>7} {'part':>6} {'miss':>6} {'cov%':>7}")
        for name, hits, partials, misses in sorted(rows, key=lambda r: r[1] / (r[1] + r[2] + r[3])):
            pct = 100.0 * hits / (hits + partials + misses)
            print(f"{name:<60} {hits:>7} {partials:>6} {misses:>6} {pct:>6.2f}%")
        print()

    print(f"lines: {total_lines}  hits: {total_hits}  partials: {total_partials}  misses: {total_misses}")
    print(f"coverage (Codecov formula): {coverage:.2f}%")

    if args.minimum is not None and coverage < args.minimum:
        print(f"FAIL: coverage {coverage:.2f}% is below the required minimum {args.minimum:.2f}%",
              file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
