from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_ROOT = ROOT / "docs" / "kto-api-probes"
MOBILE_OS = "ETC"
MOBILE_APP = "KoReady"
REQUEST_DELAY_SECONDS = 0.15


def load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip().lstrip("\ufeff")
        value = value.strip().strip('"').strip("'")
        os.environ.setdefault(key, value)


def now_stamp() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S")


def base_params(rows: int, page: int = 1) -> dict[str, str]:
    return {
        "numOfRows": str(rows),
        "pageNo": str(page),
        "MobileOS": MOBILE_OS,
        "MobileApp": MOBILE_APP,
        "_type": "json",
    }


def sanitize_params(params: dict[str, Any]) -> dict[str, Any]:
    sanitized: dict[str, Any] = {}
    for key, value in params.items():
        if key.lower() in {"servicekey", "service_key", "appkey", "apikey", "api_key"}:
            sanitized[key] = "***"
        else:
            sanitized[key] = value
    return sanitized


def build_url(base_url: str, operation: str, params: dict[str, Any]) -> str:
    encoded = urllib.parse.urlencode(params, doseq=True)
    return f"{base_url.rstrip('/')}/{operation}?{encoded}"


def sanitize_url(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    pairs = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
    safe_pairs = [
        (key, "***" if key.lower() in {"servicekey", "service_key", "appkey", "apikey", "api_key"} else value)
        for key, value in pairs
    ]
    return urllib.parse.urlunsplit(
        (parsed.scheme, parsed.netloc, parsed.path, urllib.parse.urlencode(safe_pairs), parsed.fragment)
    )


def parse_response(text: str) -> tuple[str, Any]:
    stripped = text.lstrip("\ufeff\r\n\t ")
    if not stripped:
        return "empty", None
    if stripped.startswith("{") or stripped.startswith("["):
        try:
            return "json", json.loads(stripped)
        except json.JSONDecodeError:
            return "text", text
    if stripped.startswith("<"):
        return "xml", text
    return "text", text


def response_header(parsed: Any) -> dict[str, Any]:
    if not isinstance(parsed, dict):
        return {}
    if "resultCode" in parsed or "resultMsg" in parsed:
        return {"resultCode": parsed.get("resultCode"), "resultMsg": parsed.get("resultMsg")}
    response = parsed.get("response")
    if not isinstance(response, dict):
        return {}
    header = response.get("header")
    return header if isinstance(header, dict) else {}


def response_body(parsed: Any) -> dict[str, Any]:
    if not isinstance(parsed, dict):
        return {}
    if isinstance(parsed.get("body"), dict):
        return parsed["body"]
    response = parsed.get("response")
    if not isinstance(response, dict):
        return {}
    body = response.get("body")
    return body if isinstance(body, dict) else {}


def response_items(parsed: Any) -> list[dict[str, Any]]:
    if isinstance(parsed, dict):
        top_items = parsed.get("items")
        if isinstance(top_items, list):
            return [x for x in top_items if isinstance(x, dict)]
        if isinstance(top_items, dict):
            top_item = top_items.get("item")
            if isinstance(top_item, list):
                return [x for x in top_item if isinstance(x, dict)]
            if isinstance(top_item, dict):
                return [top_item]
    body = response_body(parsed)
    items = body.get("items")
    if not isinstance(items, dict):
        return []
    item = items.get("item")
    if item is None:
        return []
    if isinstance(item, list):
        return [x for x in item if isinstance(x, dict)]
    if isinstance(item, dict):
        return [item]
    return []


def total_count(parsed: Any) -> Any:
    body = response_body(parsed)
    return body.get("totalCount") if body else None


def result_code(parsed: Any) -> str | None:
    header = response_header(parsed)
    value = header.get("resultCode")
    return None if value is None else str(value)


def result_message(parsed: Any) -> str | None:
    header = response_header(parsed)
    value = header.get("resultMsg")
    return None if value is None else str(value)


def is_success(parsed: Any, http_status: int | None, parse_kind: str) -> bool:
    if http_status is None or http_status >= 400:
        return False
    if parse_kind != "json":
        return False
    code = result_code(parsed)
    return code in {None, "0000", "0"}


def first_value(items: list[dict[str, Any]], *keys: str) -> Any:
    for item in items:
        for key in keys:
            value = item.get(key)
            if value not in (None, ""):
                return value
    return None


def unique_summary(items: list[dict[str, Any]], max_values: int = 20) -> dict[str, Any]:
    fields: dict[str, Counter[str]] = defaultdict(Counter)
    presence: Counter[str] = Counter()
    for item in items:
        for key, value in item.items():
            presence[key] += 1
            if value is None:
                normalized = "null"
            elif isinstance(value, (dict, list)):
                normalized = json.dumps(value, ensure_ascii=False, sort_keys=True)
            else:
                normalized = str(value)
            if len(normalized) > 120:
                normalized = normalized[:117] + "..."
            fields[key][normalized] += 1
    return {
        key: {
            "presentCount": presence[key],
            "uniqueCountInSample": len(counter),
            "topValues": [{"value": value, "count": count} for value, count in counter.most_common(max_values)],
        }
        for key, counter in sorted(fields.items())
    }


def file_safe_name(value: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_.-]+", "_", value).strip("_")


class ProbeContext:
    def __init__(self) -> None:
        self.values: dict[str, Any] = {}

    def set_from_items(self, prefix: str, items: list[dict[str, Any]]) -> None:
        if not items:
            return
        mappings = {
            "contentId": ("contentid", "contentId"),
            "contentTypeId": ("contenttypeid", "contentTypeId"),
            "galContentId": ("galContentId", "galcontentid", "galContentID"),
            "galTitle": ("galTitle", "galtitle"),
            "hubTatsCd": ("hubTatsCd", "hubtatscd"),
            "rlteTatsCd": ("rlteTatsCd", "rltetatscd"),
        }
        for target, keys in mappings.items():
            value = first_value(items, *keys)
            if value not in (None, ""):
                self.values[f"{prefix}.{target}"] = value


ParamBuilder = Callable[[ProbeContext, int], dict[str, Any] | None]


def common_params(_: ProbeContext, rows: int) -> dict[str, Any]:
    return base_params(rows)


def kor_area_list(_: ProbeContext, rows: int) -> dict[str, Any]:
    params = base_params(rows)
    params.update({"areaCode": "1", "contentTypeId": "12"})
    return params


def kor_keyword(_: ProbeContext, rows: int) -> dict[str, Any]:
    params = base_params(rows)
    params.update({"keyword": "\uc2dc\uc7a5"})
    return params


def kor_festival(_: ProbeContext, rows: int) -> dict[str, Any]:
    params = base_params(rows)
    params.update({"eventStartDate": "20260701"})
    return params


def kor_detail(ctx: ProbeContext, rows: int) -> dict[str, Any] | None:
    content_id = ctx.values.get("kor.contentId")
    content_type_id = ctx.values.get("kor.contentTypeId")
    if not content_id:
        return None
    params = base_params(rows)
    params.update({"contentId": content_id})
    if content_type_id:
        params["contentTypeId"] = content_type_id
    return params


def content_only_detail(ctx: ProbeContext, prefix: str, rows: int) -> dict[str, Any] | None:
    content_id = ctx.values.get(f"{prefix}.contentId")
    if not content_id:
        return None
    params = base_params(rows)
    params.update({"contentId": content_id})
    return params


def kor_detail_image(ctx: ProbeContext, rows: int) -> dict[str, Any] | None:
    params = content_only_detail(ctx, "kor", rows)
    if params is None:
        return None
    params.update({"imageYN": "Y"})
    return params


def kor_detail_common(ctx: ProbeContext, rows: int) -> dict[str, Any] | None:
    return content_only_detail(ctx, "kor", rows)


def kor_detail_pet(ctx: ProbeContext, rows: int) -> dict[str, Any] | None:
    return content_only_detail(ctx, "kor", rows)


def eng_area_list(_: ProbeContext, rows: int) -> dict[str, Any]:
    params = base_params(rows)
    params.update({"areaCode": "1", "contentTypeId": "76"})
    return params


def eng_keyword(_: ProbeContext, rows: int) -> dict[str, Any]:
    params = base_params(rows)
    params.update({"keyword": "market"})
    return params


def eng_detail(ctx: ProbeContext, rows: int) -> dict[str, Any] | None:
    content_id = ctx.values.get("eng.contentId")
    content_type_id = ctx.values.get("eng.contentTypeId")
    if not content_id:
        return None
    params = base_params(rows)
    params.update({"contentId": content_id})
    if content_type_id:
        params["contentTypeId"] = content_type_id
    return params


def eng_detail_common(ctx: ProbeContext, rows: int) -> dict[str, Any] | None:
    return content_only_detail(ctx, "eng", rows)


def eng_detail_image(ctx: ProbeContext, rows: int) -> dict[str, Any] | None:
    params = content_only_detail(ctx, "eng", rows)
    if params is None:
        return None
    params.update({"imageYN": "Y"})
    return params


def photo_search(_: ProbeContext, rows: int) -> dict[str, Any]:
    params = base_params(rows)
    params.update({"keyword": "\uc11c\uc6b8"})
    return params


def photo_detail(ctx: ProbeContext, rows: int) -> dict[str, Any] | None:
    gal_title = ctx.values.get("photo.galTitle")
    if not gal_title:
        return None
    params = base_params(rows)
    params["title"] = gal_title
    return params


def related_area(_: ProbeContext, rows: int) -> dict[str, Any]:
    params = base_params(rows)
    params.update({"baseYm": "202503", "areaCd": "11", "signguCd": "11530"})
    return params


def related_keyword(_: ProbeContext, rows: int) -> dict[str, Any]:
    params = base_params(rows)
    params.update({"baseYm": "202503", "areaCd": "11", "signguCd": "11530", "keyword": "\uacbd\ubcf5\uad81"})
    return params


def award_area(_: ProbeContext, rows: int) -> dict[str, Any]:
    params = base_params(rows)
    params.update({"lDongRegnCd": "11"})
    return params


SERVICES: list[dict[str, Any]] = [
    {
        "name": "kor_tour",
        "title": "한국관광공사 국문 관광정보 서비스_GW",
        "base_url": "https://apis.data.go.kr/B551011/KorService2",
        "context_prefix": "kor",
        "operations": [
            ("areaCode2", common_params),
            ("categoryCode2", common_params),
            ("ldongCode2", common_params),
            ("lclsSystmCode2", common_params),
            ("areaBasedList2", kor_area_list),
            ("searchKeyword2", kor_keyword),
            ("searchFestival2", kor_festival),
            ("detailCommon2", kor_detail_common),
            ("detailIntro2", kor_detail),
            ("detailInfo2", kor_detail),
            ("detailImage2", kor_detail_image),
            ("areaBasedSyncList2", common_params),
            ("detailPetTour2", kor_detail_pet),
        ],
    },
    {
        "name": "eng_tour",
        "title": "한국관광공사 영문 관광정보서비스_GW",
        "base_url": "https://apis.data.go.kr/B551011/EngService2",
        "context_prefix": "eng",
        "operations": [
            ("areaCode2", common_params),
            ("categoryCode2", common_params),
            ("areaBasedList2", eng_area_list),
            ("locationBasedList2", lambda _ctx, rows: {**base_params(rows), "mapX": "126.981611", "mapY": "37.568477", "radius": "1000"}),
            ("searchKeyword2", eng_keyword),
            ("searchFestival2", lambda _ctx, rows: {**base_params(rows), "eventStartDate": "20260701"}),
            ("searchStay2", lambda _ctx, rows: {**base_params(rows), "areaCode": "1"}),
            ("detailCommon2", eng_detail_common),
            ("detailIntro2", eng_detail),
            ("detailInfo2", eng_detail),
            ("detailImage2", eng_detail_image),
            ("areaBasedSyncList2", common_params),
        ],
    },
    {
        "name": "photo_gallery",
        "title": "한국관광공사 관광사진 정보_GW",
        "base_url": "https://apis.data.go.kr/B551011/PhotoGalleryService1",
        "context_prefix": "photo",
        "operations": [
            ("galleryList1", common_params),
            ("gallerySearchList1", photo_search),
            ("galleryDetailList1", photo_detail),
        ],
    },
    {
        "name": "photo_award",
        "title": "한국관광공사 관광공모전(사진) 수상작 정보",
        "base_url": "https://apis.data.go.kr/B551011/PhokoAwrdService",
        "context_prefix": "award",
        "operations": [
            ("ldongCode", common_params),
            ("phokoAwrdList", award_area),
            ("phokoAwrdSyncList", common_params),
        ],
    },
    {
        "name": "related_tour",
        "title": "한국관광공사 관광지별 연관 관광지 정보",
        "base_url": "https://apis.data.go.kr/B551011/TarRlteTarService1",
        "context_prefix": "related",
        "operations": [
            ("areaBasedList1", related_area),
            ("searchKeyword1", related_keyword),
        ],
    },
]


def call_operation(base_url: str, operation: str, params: dict[str, Any], timeout: int) -> dict[str, Any]:
    url = build_url(base_url, operation, params)
    started = time.perf_counter()
    http_status: int | None = None
    text = ""
    error = None
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "KoReady-KTO-Probe/0.1"})
        with urllib.request.urlopen(request, timeout=timeout) as response:
            http_status = response.status
            text = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        http_status = exc.code
        text = exc.read().decode("utf-8", errors="replace")
        error = f"HTTPError: {exc}"
    except Exception as exc:  # noqa: BLE001 - diagnostics script should capture failures.
        error = f"{type(exc).__name__}: {exc}"
    duration_ms = int((time.perf_counter() - started) * 1000)
    parse_kind, parsed = parse_response(text)
    items = response_items(parsed)
    return {
        "operation": operation,
        "requestUrl": sanitize_url(url),
        "requestParams": sanitize_params(params),
        "httpStatus": http_status,
        "durationMs": duration_ms,
        "parseKind": parse_kind,
        "success": is_success(parsed, http_status, parse_kind),
        "resultCode": result_code(parsed),
        "resultMsg": result_message(parsed),
        "totalCount": total_count(parsed),
        "itemCountInSample": len(items),
        "fieldNames": sorted({key for item in items for key in item.keys()}),
        "items": items,
        "parsed": parsed,
        "text": text,
        "error": error,
    }


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def make_markdown(run_dir: Path, manifest: dict[str, Any]) -> str:
    lines: list[str] = []
    lines.append("# KTO API Probe Summary")
    lines.append("")
    lines.append(f"- Run at: `{manifest['runAt']}`")
    lines.append(f"- Output dir: `{run_dir}`")
    lines.append(f"- Rows per list call: `{manifest['rows']}`")
    lines.append("- Secret handling: service key was read from local env and masked in generated files.")
    lines.append("")
    lines.append("## Operation Results")
    lines.append("")
    lines.append("| Service | Operation | Success | HTTP | Result | Total | Sample Items | Fields |")
    lines.append("|---|---|---:|---:|---|---:|---:|---|")
    for service in manifest["services"]:
        for op in service["operations"]:
            fields = ", ".join(op["fieldNames"][:12])
            if len(op["fieldNames"]) > 12:
                fields += " ..."
            lines.append(
                f"| {service['name']} | `{op['operation']}` | {'Y' if op['success'] else 'N'} | "
                f"{op.get('httpStatus') or ''} | {op.get('resultCode') or op.get('parseKind') or ''} "
                f"{op.get('resultMsg') or ''} | {op.get('totalCount') if op.get('totalCount') is not None else ''} | "
                f"{op['itemCountInSample']} | {fields} |"
            )
    lines.append("")
    lines.append("## Notes For Development Prompts")
    lines.append("")
    lines.append("- Raw responses are under `raw/`; field and unique-value summaries are under `analysis/`.")
    lines.append("- Unknown or failed operations are intentionally kept in the manifest so later prompts can see what was tried.")
    lines.append("- Use successful list operations first to learn field names, then use detail operations with discovered IDs.")
    lines.append("- Treat observed unique values as sample-based hints, not complete enum definitions.")
    lines.append("")
    return "\n".join(lines)


def run_probe(rows: int, output_root: Path, timeout: int) -> int:
    service_key = os.environ.get("KTO_SERVICE_KEY")
    if not service_key:
        print("KTO_SERVICE_KEY is not set. Put it in .env.local or environment.", file=sys.stderr)
        return 2

    run_dir = output_root / now_stamp()
    context = ProbeContext()
    manifest: dict[str, Any] = {
        "runAt": datetime.now().isoformat(timespec="seconds"),
        "rows": rows,
        "services": [],
    }

    for service in SERVICES:
        service_summary = {
            "name": service["name"],
            "title": service["title"],
            "baseUrl": service["base_url"],
            "operations": [],
        }
        manifest["services"].append(service_summary)

        for operation, builder in service["operations"]:
            params = builder(context, rows)
            if params is None:
                service_summary["operations"].append(
                    {
                        "operation": operation,
                        "skipped": True,
                        "success": False,
                        "reason": "dependency value was not discovered from prior calls",
                        "fieldNames": [],
                        "itemCountInSample": 0,
                    }
                )
                continue

            params["serviceKey"] = service_key
            result = call_operation(service["base_url"], operation, params, timeout)
            raw_name = file_safe_name(f"{service['name']}_{operation}")
            raw_payload = result["parsed"] if result["parseKind"] == "json" else result["text"]
            write_json(run_dir / "raw" / f"{raw_name}.json", raw_payload)
            write_json(
                run_dir / "analysis" / f"{raw_name}_fields.json",
                {
                    "service": service["name"],
                    "operation": operation,
                    "requestUrl": result["requestUrl"],
                    "requestParams": result["requestParams"],
                    "httpStatus": result["httpStatus"],
                    "resultCode": result["resultCode"],
                    "resultMsg": result["resultMsg"],
                    "totalCount": result["totalCount"],
                    "itemCountInSample": result["itemCountInSample"],
                    "fieldNames": result["fieldNames"],
                    "uniqueSummary": unique_summary(result["items"]),
                    "sampleItems": result["items"][: min(3, len(result["items"]))],
                    "error": result["error"],
                },
            )

            context.set_from_items(service["context_prefix"], result["items"])
            service_summary["operations"].append(
                {
                    "operation": operation,
                    "requestUrl": result["requestUrl"],
                    "requestParams": result["requestParams"],
                    "httpStatus": result["httpStatus"],
                    "durationMs": result["durationMs"],
                    "parseKind": result["parseKind"],
                    "success": result["success"],
                    "resultCode": result["resultCode"],
                    "resultMsg": result["resultMsg"],
                    "totalCount": result["totalCount"],
                    "itemCountInSample": result["itemCountInSample"],
                    "fieldNames": result["fieldNames"],
                    "error": result["error"],
                }
            )
            time.sleep(REQUEST_DELAY_SECONDS)

    write_json(run_dir / "manifest.json", manifest)
    write_text(run_dir / "SUMMARY.md", make_markdown(run_dir, manifest))

    latest = output_root / "LATEST.md"
    latest.write_text(
        f"# Latest KTO API Probe\n\n- Latest run: [{run_dir.name}]({run_dir.name}/SUMMARY.md)\n",
        encoding="utf-8",
    )
    print(run_dir)
    return 0


def main() -> int:
    load_dotenv(ROOT / ".env.local")
    parser = argparse.ArgumentParser(description="Probe approved KTO APIs and summarize observed response shapes.")
    parser.add_argument("--rows", type=int, default=5, help="numOfRows for sample calls")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_ROOT, help="output directory")
    parser.add_argument("--timeout", type=int, default=20, help="request timeout seconds")
    args = parser.parse_args()
    return run_probe(args.rows, args.output, args.timeout)


if __name__ == "__main__":
    raise SystemExit(main())
