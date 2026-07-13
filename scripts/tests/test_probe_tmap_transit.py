from __future__ import annotations

import sys
import unittest
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS_DIR))

from probe_tmap_transit import Scenario, analyze_payload, safe_error  # noqa: E402


class TmapProbeAnalysisTests(unittest.TestCase):
    def test_reads_http_200_provider_error_from_result_object(self) -> None:
        error = safe_error({"result": {"status": 11, "message": "no route"}})

        self.assertEqual({"status": 11, "message": "no route"}, error)

    def test_accepts_actual_uppercase_lane_field(self) -> None:
        payload = {
            "metaData": {
                "plan": {
                    "itineraries": [
                        {
                            "totalTime": 600,
                            "fare": {"regular": {"totalFare": 1500}},
                            "legs": [
                                {
                                    "mode": "BUS",
                                    "type": 11,
                                    "service": 1,
                                    "Lane": [{"route": "sample", "type": 11}],
                                }
                            ],
                        }
                    ]
                }
            }
        }
        scenario = Scenario("synthetic", "0", "0", "1", "1")

        summary = analyze_payload(scenario, 200, 1, 1, payload)

        self.assertEqual(["11"], summary["typeCodesByMode"]["BUS"])
        self.assertEqual(
            ["route", "type"],
            summary["nestedFieldsByMode"]["BUS"]["Lane"],
        )


if __name__ == "__main__":
    unittest.main()
