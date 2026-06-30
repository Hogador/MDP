#!/usr/bin/env python3
"""
MDAOPay Findings Verifier — CI enforcement
Reads security/FINDINGS.md, runs verification recipes, fails CI if any CLAIMED_FIXED returns FAIL.
"""
import re
import subprocess
import sys
from pathlib import Path

FINDINGS_FILE = Path("security/FINDINGS.md")


def parse_findings():
    """Parse FINDINGS.md, extract verification recipes."""
    if not FINDINGS_FILE.exists():
        print("❌ security/FINDINGS.md not found")
        sys.exit(1)

    content = FINDINGS_FILE.read_text(encoding="utf-8")
    findings = []

    # Pattern: ### F-XXX ... ```bash ... ```
    pattern = r'### (F-\d+[a-z]?)\s.*?```bash\n(.*?)```'
    for match in re.finditer(pattern, content, re.DOTALL):
        finding_id = match.group(1)
        recipe = match.group(2).strip()

        # Extract status from the table near this finding
        status_match = re.search(
            rf'{finding_id}.*?\*\*Status\*\*\s*\|\s*(\w+(?:_\w+)*)',
            content
        )
        status = status_match.group(1) if status_match else "UNKNOWN"

        findings.append({
            'id': finding_id,
            'status': status,
            'recipe': recipe
        })

    return findings


def run_recipe(recipe):
    """Run verification recipe, return (success, output)."""
    pass_condition = None
    fail_condition = None
    command_lines = []

    for line in recipe.split('\n'):
        line = line.strip()
        if line.startswith('# PASS:'):
            pass_condition = line.replace('# PASS:', '').strip()
        elif line.startswith('# FAIL:'):
            fail_condition = line.replace('# FAIL:', '').strip()
        elif line and not line.startswith('#'):
            command_lines.append(line)

    if not command_lines:
        return True, "No command to run"

    cmd = command_lines[0]
    try:
        result = subprocess.run(
            cmd, shell=True, capture_output=True, text=True, timeout=30
        )
        output = result.stdout + result.stderr

        # Simple heuristic: check PASS/FAIL conditions
        if pass_condition:
            # Extract key phrases (e.g., "ECDSA.recover(" present)
            key_phrase = pass_condition.replace('"', '').split(',')[0].strip()
            if key_phrase and key_phrase in output:
                return True, output

        if fail_condition:
            key_phrase = fail_condition.replace('"', '').split(',')[0].strip()
            if key_phrase and key_phrase in output:
                return False, output

        # Default: exit code determines
        return result.returncode == 0, output
    except subprocess.TimeoutExpired:
        return False, "TIMEOUT"
    except Exception as e:
        return False, str(e)


def main():
    findings = parse_findings()
    failed = []
    skipped = []

    print(f"Verifying {len(findings)} findings...")
    print()

    for f in findings:
        if f['status'] in ('CLAIMED_FIXED', 'VERIFIED'):
            success, output = run_recipe(f['recipe'])
            status_icon = "✅" if success else "❌"
            print(f"{status_icon} {f['id']} ({f['status']}): {'PASS' if success else 'FAIL'}")
            if not success:
                failed.append(f['id'])
                print(f"   Output: {output[:200]}")
        else:
            skipped.append(f['id'])

    print()
    print(f"Skipped (not CLAIMED_FIXED/VERIFIED): {len(skipped)}")
    print(f"Failed: {len(failed)}")

    if failed:
        print(f"\n❌ {len(failed)} findings failed verification: {', '.join(failed)}")
        sys.exit(1)
    else:
        print("\n✅ All CLAIMED_FIXED/VERIFIED findings pass verification")
        sys.exit(0)


if __name__ == "__main__":
    main()
