"""Writes a Markdown summary of a SonarQube analysis to stdout.

Reads measures.json, issues.json and hotspots.json from the directory given
as the first argument. scripts/sonar-scan.sh saves these files from the
SonarQube web API. CI adds the summary to the job summary page, because the
temporary SonarQube server keeps no results after the job.
"""

import json
import sys
from pathlib import Path

MEASURE_LABELS = {
    "alert_status": "Quality gate",
    "bugs": "Bugs",
    "vulnerabilities": "Vulnerabilities",
    "code_smells": "Code smells",
    "security_hotspots": "Security hotspots",
    "coverage": "Coverage (%)",
    "duplicated_lines_density": "Duplicated lines (%)",
    "ncloc": "Lines of code",
}


def load(report_dir, name):
    return json.loads((report_dir / name).read_text(encoding="utf-8"))


def file_path(component):
    return component.split(":", 1)[-1]


def main(report_dir):
    measures = {
        m["metric"]: m.get("value", "")
        for m in load(report_dir, "measures.json")["component"]["measures"]
    }
    issues = load(report_dir, "issues.json")["issues"]
    hotspots = load(report_dir, "hotspots.json")["hotspots"]

    lines = ["## SonarQube", "", "| Measure | Value |", "|---|---|"]
    for metric, label in MEASURE_LABELS.items():
        lines.append(f"| {label} | {measures.get(metric, 'n/a')} |")

    lines += ["", f"### Open issues: {len(issues)}", ""]
    if issues:
        lines += ["| Location | Severity | Rule | Message |", "|---|---|---|---|"]
        for issue in issues:
            location = f"`{file_path(issue['component'])}:{issue.get('line', '')}`"
            message = issue["message"].replace("|", "\\|")
            lines.append(f"| {location} | {issue['severity']} | {issue['rule']} | {message} |")

    lines += ["", f"### Security hotspots: {len(hotspots)}", ""]
    for hotspot in hotspots:
        lines.append(f"- `{file_path(hotspot['component'])}:{hotspot.get('line', '')}`: {hotspot['message']}")

    print("\n".join(lines))


if __name__ == "__main__":
    main(Path(sys.argv[1]))
