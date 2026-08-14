"""Small source-contract test for the checked-in Permission Policy inventory."""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from policy_inventory import parse_controller  # noqa: E402


class PolicyInventoryTest(unittest.TestCase):
    def test_same_line_annotations_and_guard_constants_are_discovered(self) -> None:
        secondary = parse_controller(
            ROOT / "backend/src/main/java/com/bbc/sms/academic/secondary/SecondaryCompetencyController.java",
            ROOT,
        )
        setup = parse_controller(
            ROOT / "backend/src/main/java/com/bbc/sms/setup/SetupController.java",
            ROOT,
        )

        self.assertGreaterEqual(len(secondary), 8)
        self.assertTrue(all(row["explicitActions"] for row in secondary))
        self.assertTrue(all(row["explicitActions"] for row in setup))
        self.assertIn("ACADEMIC_SUBJECT_GRADE_EDIT", secondary[-1]["explicitActions"])
        self.assertIn("TEACHING_ASSIGNMENT_MANAGE", {
            action for row in setup for action in row["explicitActions"]
        })

    def test_checked_in_inventory_is_utf8_json_and_has_no_module_only_guards(self) -> None:
        artifact = ROOT / "docs/ppv2-inventory-latest.json"
        payload = json.loads(artifact.read_text(encoding="utf-8"))
        self.assertEqual(payload["governingSpecification"], "PERMISSION_POLICY_V2_IMPLEMENTATION_PLAN.md")
        self.assertGreater(payload["summary"]["endpointCount"], 400)
        self.assertEqual(payload["summary"]["moduleGuardCount"], 0)


if __name__ == "__main__":
    unittest.main()
