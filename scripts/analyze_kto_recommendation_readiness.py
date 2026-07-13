from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any, Callable
from urllib.parse import urlsplit

from probe_kto_apis import ROOT, base_params, load_dotenv, now_stamp, write_json, write_text
from profile_kto_data import (
    ENG_BASE_URL,
    ENG_CONTENT_TYPE_NAMES,
    KOR_BASE_URL,
    KOR_CONTENT_TYPE_NAMES,
    KtoClient,
    call_metadata,
    fetch_all,
)


DEFAULT_OUTPUT_ROOT = ROOT / "docs" / "kto-api-analysis" / "supplements"
DEFAULT_PREFERRED_ROWS = 70000
DEFAULT_FALLBACK_ROWS = 1000

ENG_TO_KOR_CONTENT_TYPE = {
    "75": "25",
    "76": "12",
    "77": "28",
    "78": "14",
    "79": "38",
    "80": "32",
    "82": "39",
    "85": "15",
}

ONBOARDING_CONTENT_TYPES = {"12", "14", "15", "28", "38", "39"}
EXHIBITION_LCLS3 = {"VE070100", "VE070300", "VE070500", "VE070600"}


def fetch_complete(
    client: KtoClient,
    name: str,
    base_url: str,
    operation: str,
    preferred_rows: int,
    fallback_rows: int,
    extra_params: dict[str, Any] | None = None,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    params = base_params(preferred_rows)
    params.update(extra_params or {})
    first = client.call(base_url, operation, params)
    total = int(first["totalCount"] or 0)
    if len(first["items"]) == total:
        print(f"[{name}] fetched {total} rows in one call", flush=True)
        return first["items"], [call_metadata(first)]

    print(
        f"[{name}] large-page request returned {len(first['items'])}/{total}; "
        f"falling back to pages of {fallback_rows}",
        flush=True,
    )
    fetched = fetch_all(
        client,
        name,
        base_url,
        operation,
        extra_params or {},
        fallback_rows,
    )
    if fetched.profile.rows != fetched.profile.reported_total_count:
        raise RuntimeError(
            f"{name} count mismatch: fetched={fetched.profile.rows}, "
            f"reported={fetched.profile.reported_total_count}"
        )
    return fetched.items, [call_metadata(first), *fetched.calls]


def has_coordinates(item: dict[str, Any]) -> bool:
    return item.get("mapx") not in (None, "", "0", "0.0") and item.get("mapy") not in (
        None,
        "",
        "0",
        "0.0",
    )


def is_visible(item: dict[str, Any]) -> bool:
    return str(item.get("showflag")) == "1"


def has_image(item: dict[str, Any]) -> bool:
    return bool(item.get("firstimage"))


def has_address(item: dict[str, Any]) -> bool:
    return bool(item.get("addr1"))


def has_new_classification(item: dict[str, Any]) -> bool:
    return bool(item.get("lclsSystm1"))


def is_card_ready(item: dict[str, Any]) -> bool:
    return is_visible(item) and has_coordinates(item) and has_image(item) and has_address(item)


def is_onboarding_base(item: dict[str, Any]) -> bool:
    return is_card_ready(item) and str(item.get("contenttypeid")) in ONBOARDING_CONTENT_TYPES


def style_predicates() -> dict[str, Callable[[dict[str, Any]], bool]]:
    return {
        "LOCAL_FOOD": lambda item: item.get("lclsSystm1") == "FD"
        or str(item.get("contenttypeid")) == "39",
        "LOCAL_FESTIVAL": lambda item: item.get("lclsSystm1") == "EV"
        or str(item.get("contenttypeid")) == "15",
        "TRADITIONAL_MARKET": lambda item: item.get("lclsSystm2") == "SH06"
        or item.get("cat3") == "A04010200",
        "CULTURE_EXPERIENCE": lambda item: item.get("lclsSystm1") == "EX",
        "NATURE": lambda item: item.get("lclsSystm1") == "NA",
        "EXHIBITION_MUSEUM": lambda item: item.get("lclsSystm3") in EXHIBITION_LCLS3,
        "DRAMA_LOCATION": lambda _item: False,
    }


def count_funnel(items: list[dict[str, Any]]) -> dict[str, Any]:
    steps: list[tuple[str, Callable[[dict[str, Any]], bool]]] = [
        ("all", lambda _item: True),
        ("visible", is_visible),
        ("visibleWithCoordinates", lambda item: is_visible(item) and has_coordinates(item)),
        ("cardReady", is_card_ready),
        ("onboardingBase", is_onboarding_base),
        (
            "onboardingClassified",
            lambda item: is_onboarding_base(item) and has_new_classification(item),
        ),
    ]
    result: dict[str, Any] = {}
    previous_count: int | None = None
    for name, predicate in steps:
        count = sum(predicate(item) for item in items)
        result[name] = {
            "count": count,
            "ofAllRate": round(count / len(items), 6) if items else 0,
            "ofPreviousRate": round(count / previous_count, 6) if previous_count else 1,
        }
        previous_count = count
    return result


def content_type_readiness(items: list[dict[str, Any]]) -> dict[str, Any]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in items:
        grouped[str(item.get("contenttypeid") or "")].append(item)

    result: dict[str, Any] = {}
    for content_type_id, rows in sorted(grouped.items(), key=lambda item: int(item[0])):
        result[content_type_id] = {
            "name": KOR_CONTENT_TYPE_NAMES.get(content_type_id, "UNKNOWN"),
            "total": len(rows),
            "visible": sum(is_visible(item) for item in rows),
            "cardReady": sum(is_card_ready(item) for item in rows),
            "newClassification": sum(has_new_classification(item) for item in rows),
            "onboardingClassified": sum(
                is_onboarding_base(item) and has_new_classification(item) for item in rows
            ),
        }
    return result


def style_readiness(items: list[dict[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for style, predicate in style_predicates().items():
        direct_rows = [item for item in items if predicate(item)]
        ready_rows = [item for item in items if predicate(item) and is_onboarding_base(item)]
        result[style] = {
            "directRuleTotal": len(direct_rows),
            "onboardingReady": len(ready_rows),
            "visibleOnboardingReady": len({str(item["contentid"]) for item in ready_rows}),
            "requiresKeywordOrAi": style == "DRAMA_LOCATION",
        }
    return result


def coord_key(item: dict[str, Any], decimals: int = 6) -> tuple[float, float] | None:
    if not has_coordinates(item):
        return None
    try:
        return round(float(item["mapx"]), decimals), round(float(item["mapy"]), decimals)
    except (TypeError, ValueError):
        return None


def image_key(value: Any) -> str | None:
    if not value:
        return None
    parsed = urlsplit(str(value).strip())
    if not parsed.netloc or not parsed.path:
        return None
    return f"{parsed.netloc.lower()}{parsed.path.lower()}"


def localization_match(
    kor_items: list[dict[str, Any]],
    eng_items: list[dict[str, Any]],
) -> tuple[dict[str, Any], set[str]]:
    kor_by_id = {str(item["contentid"]): item for item in kor_items if item.get("contentid")}
    kor_by_coord_type: dict[tuple[tuple[float, float], str], set[str]] = defaultdict(set)
    kor_by_image: dict[str, set[str]] = defaultdict(set)

    for item in kor_items:
        content_id = str(item.get("contentid") or "")
        if not content_id:
            continue
        coordinate = coord_key(item)
        content_type = str(item.get("contenttypeid") or "")
        if coordinate and content_type:
            kor_by_coord_type[(coordinate, content_type)].add(content_id)
        image = image_key(item.get("firstimage"))
        if image:
            kor_by_image[image].add(content_id)

    evidence_counts: Counter[str] = Counter()
    unique_matches: set[str] = set()
    candidate_size_counts: Counter[int] = Counter()
    staged_resolution_counts: Counter[str] = Counter()
    old_id_examples: list[dict[str, Any]] = []
    conflict_examples: list[dict[str, Any]] = []

    direct_content_overlap = sum(
        str(item.get("contentid") or "") in kor_by_id for item in eng_items
    )

    for item in eng_items:
        old_id_candidates: set[str] = set()
        old_content_id = str(item.get("oldContentid") or "")
        if old_content_id in kor_by_id:
            old_id_candidates.add(old_content_id)
            evidence_counts["oldContentid"] += 1
            if len(old_id_examples) < 5:
                old_id_examples.append(
                    {
                        "englishContentId": item.get("contentid"),
                        "oldContentid": old_content_id,
                        "englishTitle": item.get("title"),
                        "koreanTitle": kor_by_id[old_content_id].get("title"),
                    }
                )

        image_candidates: set[str] = set()
        image = image_key(item.get("firstimage"))
        if image:
            image_candidates = set(kor_by_image.get(image, set()))
        if image_candidates:
            evidence_counts["sameImagePath"] += 1

        coordinate_candidates: set[str] = set()
        coordinate = coord_key(item)
        kor_type = ENG_TO_KOR_CONTENT_TYPE.get(str(item.get("contenttypeid") or ""))
        if coordinate and kor_type:
            coordinate_candidates = set(kor_by_coord_type.get((coordinate, kor_type), set()))
        if coordinate_candidates:
            evidence_counts["sameCoordinateAndMappedType"] += 1

        evidence_sets = [
            candidates
            for candidates in (old_id_candidates, image_candidates, coordinate_candidates)
            if candidates
        ]
        candidates = set().union(*evidence_sets) if evidence_sets else set()
        candidate_size_counts[len(candidates)] += 1
        selected: str | None = None
        if len(old_id_candidates) == 1:
            selected = next(iter(old_id_candidates))
            staged_resolution_counts["oldContentid"] += 1
        elif len(image_candidates) == 1:
            selected = next(iter(image_candidates))
            staged_resolution_counts["uniqueImagePath"] += 1
        elif len(coordinate_candidates) == 1:
            selected = next(iter(coordinate_candidates))
            staged_resolution_counts["uniqueCoordinateAndMappedTypeFallback"] += 1
        elif candidates:
            staged_resolution_counts["ambiguous"] += 1
        else:
            staged_resolution_counts["unmatched"] += 1
        if selected:
            unique_matches.add(selected)
            staged_resolution_counts["matched"] += 1

        if len(evidence_sets) >= 2 and not set.intersection(*evidence_sets):
            evidence_counts["evidenceConflict"] += 1
            if len(conflict_examples) < 5:
                conflict_examples.append(
                    {
                        "englishContentId": item.get("contentid"),
                        "englishTitle": item.get("title"),
                        "oldIdCandidates": sorted(old_id_candidates),
                        "imageCandidates": sorted(image_candidates),
                        "coordinateCandidates": sorted(coordinate_candidates),
                    }
                )

    return (
        {
            "koreanRows": len(kor_items),
            "englishRows": len(eng_items),
            "sameCurrentContentId": direct_content_overlap,
            "evidenceCounts": dict(evidence_counts),
            "stagedResolutionCounts": dict(staged_resolution_counts),
            "candidateSizeDistribution": [
                {"candidateCount": size, "englishRows": count}
                for size, count in sorted(candidate_size_counts.items())
            ],
            "uniqueMatchedKoreanContentIds": len(unique_matches),
            "englishUniqueMatchRate": round(
                staged_resolution_counts["matched"] / len(eng_items), 6
            )
            if eng_items
            else 0,
            "oldContentidExamples": old_id_examples,
            "evidenceConflictExamples": conflict_examples,
            "recommendedJoinOrder": [
                "oldContentid equals Korean contentid",
                "normalized first-image path",
                "coordinate rounded to 6 decimals plus mapped content type",
                "manual or AI review for ambiguous/unmatched rows",
            ],
        },
        unique_matches,
    )


def keyword_probe(
    client: KtoClient,
    keyword: str,
    fallback_rows: int,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    result = fetch_all(
        client,
        f"kor_keyword_{keyword}",
        KOR_BASE_URL,
        "searchKeyword2",
        {"keyword": keyword},
        fallback_rows,
    )
    return (
        {
            "keyword": keyword,
            "reportedTotal": result.profile.reported_total_count,
            "rowsFetched": result.profile.rows,
            "uniqueContentIds": len(
                {str(item["contentid"]) for item in result.items if item.get("contentid")}
            ),
            "sampleTitles": [item.get("title") for item in result.items[:20]],
        },
        result.calls,
    )


def make_markdown(result: dict[str, Any], run_dir: Path) -> str:
    funnel = result["recommendationReadiness"]["funnel"]
    localization = result["localizationMatch"]
    evidence = localization["evidenceCounts"]
    staged = localization["stagedResolutionCounts"]
    lines = [
        "# KTO Recommendation Readiness Supplement",
        "",
        f"- Generated: `{result['generatedAt']}`",
        f"- API calls: `{result['apiCallCount']}`",
        "- Korean and English sync lists were fetched completely for joint row-level analysis.",
        "",
        "## Recommendation Candidate Funnel",
        "",
        "| Step | Count | Of all | Of previous |",
        "|---|---:|---:|---:|",
    ]
    for name, values in funnel.items():
        lines.append(
            f"| `{name}` | {values['count']:,} | {values['ofAllRate'] * 100:.1f}% | "
            f"{values['ofPreviousRate'] * 100:.1f}% |"
        )

    lines.extend(
        [
            "",
            "## Travel Style Supply",
            "",
            "| Travel style | Direct rule total | Onboarding ready | Needs keyword/AI |",
            "|---|---:|---:|---:|",
        ]
    )
    for style, values in result["recommendationReadiness"]["travelStyles"].items():
        lines.append(
            f"| `{style}` | {values['directRuleTotal']:,} | {values['onboardingReady']:,} | "
            f"{'Y' if values['requiresKeywordOrAi'] else 'N'} |"
        )

    lines.extend(
        [
            "",
            "## Korean-English Matching",
            "",
            f"- Same current `contentid`: `{localization['sameCurrentContentId']:,}`",
            f"- `oldContentid` matches: `{evidence.get('oldContentid', 0):,}`",
            f"- Same normalized image path: `{evidence.get('sameImagePath', 0):,}`",
            f"- Same coordinate + mapped content type: `{evidence.get('sameCoordinateAndMappedType', 0):,}`",
            f"- English rows matched by staged rules: `{staged.get('matched', 0):,}` "
            f"(`{localization['englishUniqueMatchRate'] * 100:.1f}%`)",
            f"- Unique image-path matches: `{staged.get('uniqueImagePath', 0):,}`",
            f"- Unique coordinate/type fallback matches: "
            f"`{staged.get('uniqueCoordinateAndMappedTypeFallback', 0):,}`",
            f"- Ambiguous after staged rules: `{staged.get('ambiguous', 0):,}`",
            f"- Unmatched after staged rules: `{staged.get('unmatched', 0):,}`",
            f"- Conflicting evidence: `{evidence.get('evidenceConflict', 0):,}`",
            "",
            "## Keyword Probes",
            "",
            "| Keyword | Total | Unique content IDs |",
            "|---|---:|---:|",
        ]
    )
    for values in result["keywordProbes"]:
        lines.append(
            f"| {values['keyword']} | {values['reportedTotal']:,} | {values['uniqueContentIds']:,} |"
        )
    lines.extend(
        [
            "",
            f"Full machine-readable result: `{run_dir / 'supplement.json'}`",
            "",
        ]
    )
    return "\n".join(lines)


def run(
    output_root: Path,
    preferred_rows: int,
    fallback_rows: int,
    timeout: int,
    retries: int,
) -> int:
    service_key = os.environ.get("KTO_SERVICE_KEY")
    if not service_key:
        print("KTO_SERVICE_KEY is not set. Put it in .env.local or environment.", file=sys.stderr)
        return 2

    run_dir = output_root / now_stamp()
    client = KtoClient(service_key, timeout=timeout, retries=retries)
    calls: list[dict[str, Any]] = []

    kor_items, call_rows = fetch_complete(
        client,
        "kor_tour.areaBasedSyncList2",
        KOR_BASE_URL,
        "areaBasedSyncList2",
        preferred_rows,
        fallback_rows,
    )
    calls.extend(call_rows)
    eng_items, call_rows = fetch_complete(
        client,
        "eng_tour.areaBasedSyncList2",
        ENG_BASE_URL,
        "areaBasedSyncList2",
        preferred_rows,
        fallback_rows,
    )
    calls.extend(call_rows)

    matching, matched_kor_ids = localization_match(kor_items, eng_items)
    keyword_results: list[dict[str, Any]] = []
    for keyword in ("드라마 촬영지", "촬영지", "전통시장"):
        values, call_rows = keyword_probe(client, keyword, fallback_rows)
        keyword_results.append(values)
        calls.extend(call_rows)

    funnel = count_funnel(kor_items)
    visible_card_ids = {
        str(item["contentid"]) for item in kor_items if is_card_ready(item) and item.get("contentid")
    }
    result = {
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "apiCallCount": client.call_count,
        "recommendationReadiness": {
            "funnel": funnel,
            "contentTypes": content_type_readiness(kor_items),
            "travelStyles": style_readiness(kor_items),
            "cardReadyWithUniqueEnglishMatch": len(visible_card_ids & matched_kor_ids),
        },
        "localizationMatch": matching,
        "keywordProbes": keyword_results,
        "contentTypeMappings": {
            eng_id: {
                "englishName": ENG_CONTENT_TYPE_NAMES[eng_id],
                "koreanContentTypeId": kor_id,
                "koreanName": KOR_CONTENT_TYPE_NAMES[kor_id],
            }
            for eng_id, kor_id in ENG_TO_KOR_CONTENT_TYPE.items()
        },
    }

    write_json(run_dir / "supplement.json", result)
    write_json(run_dir / "calls.json", calls)
    write_text(run_dir / "SUMMARY.md", make_markdown(result, run_dir))
    write_text(
        output_root / "LATEST.md",
        f"# Latest KTO Readiness Supplement\n\n- Latest run: [{run_dir.name}]({run_dir.name}/SUMMARY.md)\n",
    )
    print(json.dumps({"output": str(run_dir), "apiCalls": client.call_count}, ensure_ascii=False))
    return 0


def main() -> int:
    load_dotenv(ROOT / ".env.local")
    parser = argparse.ArgumentParser(
        description="Analyze KTO multilingual matching and KoReady recommendation candidate readiness."
    )
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--preferred-rows", type=int, default=DEFAULT_PREFERRED_ROWS)
    parser.add_argument("--fallback-rows", type=int, default=DEFAULT_FALLBACK_ROWS)
    parser.add_argument("--timeout", type=int, default=90)
    parser.add_argument("--retries", type=int, default=3)
    args = parser.parse_args()
    return run(
        output_root=args.output,
        preferred_rows=args.preferred_rows,
        fallback_rows=args.fallback_rows,
        timeout=args.timeout,
        retries=args.retries,
    )


if __name__ == "__main__":
    raise SystemExit(main())
