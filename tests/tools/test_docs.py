import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class DocsTests(unittest.TestCase):
    def test_agents_commands_exist(self):
        text = (ROOT / "AGENTS.md").read_text()
        used = set(re.findall(r"\./tv ([a-z]+)", text))
        known = {"doctor", "probe", "build", "install", "switch", "verify",
                 "update", "rollback", "report", "version", "sign"}
        self.assertEqual(set(), used - known, f"unknown commands in AGENTS.md: {used - known}")

    def test_fail_codes_are_documented(self):
        text = (ROOT / "AGENTS.md").read_text()
        codes = set(re.findall(r"(?:FAIL|BLOCKED) ([a-z][a-z0-9]*(?:\.[a-z0-9]+)+)", text))
        trouble = (ROOT / "docs" / "TROUBLESHOOTING.md").read_text()
        self.assertEqual(set(), {c for c in codes if c not in trouble}, "undocumented codes")

    def test_common_profile_matches_home_mode(self):
        source = (ROOT / "app/src/io/freemytv/HomeMode.java").read_text()
        match = re.search(r"DEBLOAT = \{(.*?)\};", source, re.S)
        block = match.group(1) if match else ""
        self.assertTrue(block, "DEBLOAT block not found in HomeMode.java")
        java = set(re.findall(r'"([a-z][a-z0-9_.]+)"', block))
        common = json.loads((ROOT / "tooling/profiles/_common.json").read_text())
        self.assertEqual(set(), java - set(common["allowed_disable"]),
                         "HomeMode.DEBLOAT has packages missing from tooling/profiles/_common.json")

    def test_readme_has_the_agent_prompt(self):
        readme = (ROOT / "README.md").read_text()
        self.assertIn("AGENTS.md", readme)
        self.assertIn("Install Majlis on my Android TV", readme)


if __name__ == "__main__":
    unittest.main()
