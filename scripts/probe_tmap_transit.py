from __future__ import annotations

import argparse
import json
import os
import time
import urllib.error
import urllib.request
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
ENDPOINT = "https://apis.openapi.sk.com/transit/routes"
MAX_CALLS_PER_RUN = 3


@dataclass(frozen=True)
class Scenario:
    name: str
    start_x: str
    start_y: str
    end_x: str
    end_y: str

    def request_body(self) -> dict[str, Any]:
        return {
            "startX": self.start_x,
            "startY": self.start_y,
            "endX": self.end_x,
            "endY": self.end_y,
            "count": 1,
            "lang": 0,
            "format": "json",
        }


SCENARIOS = (
    Scenario(
        name="seoul_urban_transit",
        start_x="127.0168",
        start_y="37.5927",
        end_x="126.9770",
        end_y="37.5716",
    ),
    Scenario(
        name="seoul_to_gimcheon_intercity",
        start_x="127.0168",
        start_y="37.5927",
        end_x="128.1136",
        end_y="36.1398",
    ),
    Scenario(
        name="too_close_no_route",
        start_x="127.0168",
        start_y="37.5927",
        end_x="127.0170",
        end_y="37.5928",
    ),
)


def load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(
            key.strip().lstrip("\ufeff"),
            value.strip().strip('"').strip("'"),
        )


def type_name(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int):
        return "integer"
    if isinstance(value, float):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"
    return type(value).__name__


def field_types(value: Any) -> dict[str, str]:
    if not isinstance(value, dict):
        return {}
    return {key: type_name(item) for key, item in sorted(value.items())}


def object_items(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, dict):
        return [value]
    if isinstance(value, list):
        return [item for item in value if isinstance(item, dict)]
    return []


def safe_error(payload: Any) -> dict[str, Any] | None:
    if not isinstance(payload, dict):
        return None
    candidates = [payload.get("error"), payload.get("result")]
    metadata = payload.get("metaData")
    if isinstance(metadata, dict):
        candidates.extend([metadata.get("error"), metadata.get("message")])
    for candidate in candidates:
        if isinstance(candidate, dict):
            return {
                key: candidate.get(key)
                for key in ("id", "category", "code", "status", "message")
                if candidate.get(key) is not None
            }
        if isinstance(candidate, str):
            return {"message": candidate}
    return None


def analyze_payload(
    scenario: Scenario,
    http_status: int,
    elapsed_ms: int,
    response_bytes: int,
    payload: Any,
) -> dict[str, Any]:
    summary: dict[str, Any] = {
        "scenario": scenario.name,
        "httpStatus": http_status,
        "elapsedMs": elapsed_ms,
        "responseBytes": response_bytes,
        "payloadType": type_name(payload),
    }
    if not isinstance(payload, dict):
        summary["result"] = "non_json_object"
        return summary

    summary["topLevelFieldTypes"] = field_types(payload)
    metadata = payload.get("metaData")
    summary["metaDataFieldTypes"] = field_types(metadata)
    plan = metadata.get("plan") if isinstance(metadata, dict) else None
    summary["planFieldTypes"] = field_types(plan)
    itineraries = plan.get("itineraries") if isinstance(plan, dict) else None
    itinerary_items = object_items(itineraries)
    summary["itineraryCount"] = len(itinerary_items)

    if not itinerary_items:
        summary["result"] = "no_route_or_error"
        error = safe_error(payload)
        if error:
            summary["error"] = error
        return summary

    summary["result"] = "route"
    first = itinerary_items[0]
    summary["itineraryFieldTypes"] = field_types(first)
    regular_fare = None
    fare = first.get("fare")
    if isinstance(fare, dict):
        regular_fare = fare.get("regular")
    summary["regularFareFieldTypes"] = field_types(regular_fare)

    legs = object_items(first.get("legs"))
    summary["firstItineraryLegCount"] = len(legs)
    modes: set[str] = set()
    fields_by_mode: dict[str, set[str]] = defaultdict(set)
    type_codes_by_mode: dict[str, set[str]] = defaultdict(set)
    service_values_by_mode: dict[str, set[str]] = defaultdict(set)
    nested_fields_by_mode: dict[str, dict[str, set[str]]] = defaultdict(
        lambda: defaultdict(set)
    )

    for leg in legs:
        mode = str(leg.get("mode") or "UNKNOWN")
        modes.add(mode)
        fields_by_mode[mode].update(leg.keys())
        if leg.get("type") is not None:
            type_codes_by_mode[mode].add(str(leg["type"]))
        if leg.get("service") is not None:
            service_values_by_mode[mode].add(str(leg["service"]))
        for nested_key in (
            "start",
            "end",
            "steps",
            "passShape",
            "passStopList",
            "Lane",
            "lane",
        ):
            for nested in object_items(leg.get(nested_key)):
                nested_fields_by_mode[mode][nested_key].update(nested.keys())
                if nested_key in {"Lane", "lane"} and nested.get("type") is not None:
                    type_codes_by_mode[mode].add(str(nested["type"]))

    summary["modes"] = sorted(modes)
    summary["legFieldsByMode"] = {
        mode: sorted(fields) for mode, fields in sorted(fields_by_mode.items())
    }
    summary["typeCodesByMode"] = {
        mode: sorted(values) for mode, values in sorted(type_codes_by_mode.items())
    }
    summary["serviceValuesByMode"] = {
        mode: sorted(values) for mode, values in sorted(service_values_by_mode.items())
    }
    summary["nestedFieldsByMode"] = {
        mode: {
            nested_key: sorted(fields)
            for nested_key, fields in sorted(nested_fields.items())
        }
        for mode, nested_fields in sorted(nested_fields_by_mode.items())
    }
    return summary


def call_tmap(app_key: str, scenario: Scenario, timeout_seconds: int) -> dict[str, Any]:
    request_bytes = json.dumps(scenario.request_body()).encode("utf-8")
    request = urllib.request.Request(
        ENDPOINT,
        data=request_bytes,
        method="POST",
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
            "appKey": app_key,
            "User-Agent": "KoReady-TMAP-Profile/1.0",
        },
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            status = response.status
            response_bytes = response.read()
    except urllib.error.HTTPError as error:
        status = error.code
        response_bytes = error.read()
    elapsed_ms = round((time.perf_counter() - started) * 1000)

    try:
        payload = json.loads(response_bytes.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        payload = None
    return analyze_payload(
        scenario=scenario,
        http_status=status,
        elapsed_ms=elapsed_ms,
        response_bytes=len(response_bytes),
        payload=payload,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Profile at most three TMAP transit calls without persisting raw responses."
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Perform live API calls. Without this flag, only the call plan is printed.",
    )
    parser.add_argument("--calls", type=int, default=MAX_CALLS_PER_RUN)
    parser.add_argument("--timeout", type=int, default=20)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not 1 <= args.calls <= MAX_CALLS_PER_RUN:
        raise SystemExit(f"--calls must be between 1 and {MAX_CALLS_PER_RUN}")
    selected = SCENARIOS[: args.calls]
    if not args.execute:
        print(
            json.dumps(
                {
                    "execute": False,
                    "endpoint": ENDPOINT,
                    "callCount": len(selected),
                    "countPerRequest": 1,
                    "rawResponsePersistence": False,
                    "scenarios": [scenario.name for scenario in selected],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return 0

    load_dotenv(ROOT / ".env.local")
    app_key = os.getenv("TMAP_APP_KEY", "").strip()
    if not app_key or app_key == "replace-with-local-tmap-app-key":
        raise SystemExit("TMAP_APP_KEY is missing from .env.local or the process environment")

    results = []
    for index, scenario in enumerate(selected):
        if index:
            time.sleep(0.5)
        try:
            results.append(call_tmap(app_key, scenario, args.timeout))
        except urllib.error.URLError as error:
            results.append(
                {
                    "scenario": scenario.name,
                    "result": "network_error",
                    "errorType": type(error.reason).__name__,
                }
            )
    print(
        json.dumps(
            {
                "endpoint": ENDPOINT,
                "callCount": len(results),
                "rawResponsePersisted": False,
                "results": results,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
