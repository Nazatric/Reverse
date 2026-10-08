#!/usr/bin/env python3
"""Re-emit the JVM unit-test report as GitHub check-run annotations.

The Actions artifact store is not reachable from the authoring network, so the XML report is
converted into workflow commands instead: a `TESTTOTALS` notice plus one packed `TESTFAIL` error
(pipes as line breaks, capped well under GitHub's ~4 KB per-annotation limit).
"""
import glob
import sys
import xml.etree.ElementTree as ET

pattern = sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results/testDebugUnitTest/*.xml"

total = 0
failed = 0
failures = []

for path in sorted(glob.glob(pattern)):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    total += int(root.get("tests", 0))
    failed += int(root.get("failures", 0)) + int(root.get("errors", 0))
    for tc in root.iter("testcase"):
        for fl in list(tc.findall("failure")) + list(tc.findall("error")):
            msg = " ".join((fl.get("message") or "").split())[:380]
            failures.append(f"{tc.get('name')} :: {msg}")

print(f"::notice::TESTTOTALS total={total} failed={failed}")
if failures:
    print("::error::TESTFAIL " + "|".join(failures)[:3800])
else:
    print(f"TESTTOTALS total={total} failed={failed}")
