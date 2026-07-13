from __future__ import annotations

import argparse
import json
import math
import os
import sys
import time
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

from probe_kto_apis import (
    ROOT,
    base_params,
    call_operation,
    load_dotenv,
    now_stamp,
    write_json,
    write_text,
)


DEFAULT_OUTPUT_ROOT = ROOT / "docs" / "kto-api-analysis"
DEFAULT_ROWS_PER_PAGE = 1000
DEFAULT_RELATED_BASE_YM = "202503"
REQUEST_DELAY_SECONDS = 0.12
MAX_EXPORTED_UNIQUE_VALUES = 200
TOP_VALUE_LIMIT = 20

KOR_BASE_URL = "https://apis.data.go.kr/B551011/KorService2"
ENG_BASE_URL = "https://apis.data.go.kr/B551011/EngService2"
PHOTO_GALLERY_BASE_URL = "https://apis.data.go.kr/B551011/PhotoGalleryService1"
PHOTO_AWARD_BASE_URL = "https://apis.data.go.kr/B551011/PhokoAwrdService"
RELATED_TOUR_BASE_URL = "https://apis.data.go.kr/B551011/TarRlteTarService1"

KOR_CONTENT_TYPE_NAMES = {
    "12": "관광지",
    "14": "문화시설",
    "15": "축제/공연/행사",
    "25": "여행코스",
    "28": "레포츠",
    "32": "숙박",
    "38": "쇼핑",
    "39": "음식",
}

ENG_CONTENT_TYPE_NAMES = {
    "75": "Travel Course",
    "76": "Tourist Attraction",
    "77": "Leisure Sports",
    "78": "Cultural Facility",
    "79": "Shopping",
    "80": "Accommodation",
    "82": "Food",
    "85": "Festival/Performance/Event",
}

HIGH_CARDINALITY_FIELDS = {
    "addr1",
    "addr2",
    "contentid",
    "contentId",
    "createdtime",
    "firstimage",
    "firstimage2",
    "galContentId",
    "galSearchKeyword",
    "galTitle",
    "galWebImageUrl",
    "mapx",
    "mapy",
    "modifiedtime",
    "orgImage",
    "rlteTatsCd",
    "rlteTatsNm",
    "tAtsCd",
    "tAtsNm",
    "tel",
    "thumbImage",
    "title",
    "zipcode",
}


def normalized(value: Any) -> str:
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, sort_keys=True)
    return str(value)


def display_value(value: str, limit: int = 160) -> str:
    compact = " ".join(value.split())
    return compact if len(compact) <= limit else compact[: limit - 3] + "..."


@dataclass
class FieldProfile:
    present_count: int = 0
    null_count: int = 0
    blank_count: int = 0
    values: Counter[str] = field(default_factory=Counter)

    def add(self, value: Any) -> None:
        self.present_count += 1
        if value is None:
            self.null_count += 1
            return
        normalized_value = normalized(value)
        if not normalized_value.strip():
            self.blank_count += 1
            return
        self.values[normalized_value] += 1

    def as_dict(self, row_count: int, field_name: str) -> dict[str, Any]:
        non_empty_count = sum(self.values.values())
        unique_count = len(self.values)
        missing_count = row_count - self.present_count
        result: dict[str, Any] = {
            "presentCount": self.present_count,
            "missingCount": missing_count,
            "nullCount": self.null_count,
            "blankCount": self.blank_count,
            "nonEmptyCount": non_empty_count,
            "nonEmptyRate": round(non_empty_count / row_count, 6) if row_count else 0,
            "uniqueNonEmptyCount": unique_count,
            "topValues": [
                {"value": display_value(value), "count": count}
                for value, count in self.values.most_common(TOP_VALUE_LIMIT)
            ],
        }
        if unique_count <= MAX_EXPORTED_UNIQUE_VALUES and field_name not in HIGH_CARDINALITY_FIELDS:
            result["allUniqueValues"] = [
                {"value": value, "count": count}
                for value, count in sorted(self.values.items(), key=lambda item: (-item[1], item[0]))
            ]
        return result


@dataclass
class DatasetProfile:
    name: str
    reported_total_count: int | None = None
    rows: int = 0
    pages: int = 0
    fields: dict[str, FieldProfile] = field(default_factory=lambda: defaultdict(FieldProfile))
    samples: list[dict[str, Any]] = field(default_factory=list)

    def add_items(self, items: list[dict[str, Any]]) -> None:
        for item in items:
            self.rows += 1
            if len(self.samples) < 3:
                self.samples.append(item)
            for key, value in item.items():
                self.fields[key].add(value)

    def as_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "reportedTotalCount": self.reported_total_count,
            "rowsFetched": self.rows,
            "pagesFetched": self.pages,
            "fieldCount": len(self.fields),
            "fields": {
                key: profile.as_dict(self.rows, key)
                for key, profile in sorted(self.fields.items())
            },
            "samples": self.samples,
        }


@dataclass
class FetchResult:
    profile: DatasetProfile
    items: list[dict[str, Any]]
    calls: list[dict[str, Any]]


class KtoClient:
    def __init__(self, service_key: str, timeout: int, retries: int) -> None:
        self.service_key = service_key
        self.timeout = timeout
        self.retries = retries
        self.call_count = 0

    def call(self, base_url: str, operation: str, params: dict[str, Any]) -> dict[str, Any]:
        request_params = dict(params)
        request_params["serviceKey"] = self.service_key
        last_result: dict[str, Any] | None = None
        for attempt in range(1, self.retries + 1):
            result = call_operation(base_url, operation, request_params, self.timeout)
            self.call_count += 1
            last_result = result
            if result["success"]:
                time.sleep(REQUEST_DELAY_SECONDS)
                return result
            if attempt < self.retries:
                time.sleep(min(attempt * 0.75, 2.0))
        assert last_result is not None
        raise RuntimeError(
            f"{operation} failed after {self.retries} attempts: "
            f"HTTP={last_result['httpStatus']} result={last_result['resultCode']} "
            f"message={last_result['resultMsg']} error={last_result['error']}"
        )


def call_metadata(result: dict[str, Any]) -> dict[str, Any]:
    return {
        "operation": result["operation"],
        "requestParams": result["requestParams"],
        "httpStatus": result["httpStatus"],
        "durationMs": result["durationMs"],
        "resultCode": result["resultCode"],
        "resultMsg": result["resultMsg"],
        "totalCount": result["totalCount"],
        "itemCount": result["itemCountInSample"],
    }


def fetch_all(
    client: KtoClient,
    name: str,
    base_url: str,
    operation: str,
    extra_params: dict[str, Any],
    rows_per_page: int,
    keep_items: bool = True,
) -> FetchResult:
    profile = DatasetProfile(name=name)
    all_items: list[dict[str, Any]] = []
    calls: list[dict[str, Any]] = []

    page = 1
    page_count = 1
    while page <= page_count:
        params = base_params(rows_per_page, page)
        params.update(extra_params)
        result = client.call(base_url, operation, params)
        items = result["items"]
        if page == 1:
            reported = result["totalCount"]
            profile.reported_total_count = int(reported) if reported is not None else None
            page_count = max(1, math.ceil((profile.reported_total_count or len(items)) / rows_per_page))
        profile.pages += 1
        profile.add_items(items)
        if keep_items:
            all_items.extend(items)
        calls.append(call_metadata(result))
        if page == 1 or page == page_count or page % 10 == 0:
            print(f"[{name}] page {page}/{page_count}, rows={profile.rows}", flush=True)
        if not items and page < page_count:
            raise RuntimeError(f"{name} returned an empty page before the reported end: page={page}")
        page += 1

    return FetchResult(profile=profile, items=all_items, calls=calls)


def fetch_code_rows(
    client: KtoClient,
    base_url: str,
    operation: str,
    params: dict[str, Any],
    rows_per_page: int,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    request_params = base_params(rows_per_page)
    request_params.update(params)
    result = client.call(base_url, operation, request_params)
    return result["items"], call_metadata(result)


def collect_codebooks(
    client: KtoClient,
    rows_per_page: int,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    calls: list[dict[str, Any]] = []

    area_roots, call = fetch_code_rows(client, KOR_BASE_URL, "areaCode2", {}, rows_per_page)
    calls.append(call)
    area_rows: list[dict[str, Any]] = []
    for root in area_roots:
        area_rows.append({"level": "SIDO", "parentCode": None, **root})
        children, child_call = fetch_code_rows(
            client, KOR_BASE_URL, "areaCode2", {"areaCode": root["code"]}, rows_per_page
        )
        calls.append(child_call)
        area_rows.extend(
            {"level": "SIGUNGU", "parentCode": root["code"], **child} for child in children
        )

    ldong_roots, call = fetch_code_rows(client, KOR_BASE_URL, "ldongCode2", {}, rows_per_page)
    calls.append(call)
    ldong_rows: list[dict[str, Any]] = []
    ldong_children_by_region: dict[str, list[dict[str, Any]]] = {}
    for root in ldong_roots:
        region_code = str(root["code"])
        ldong_rows.append({"level": "SIDO", "parentCode": None, **root})
        children, child_call = fetch_code_rows(
            client, KOR_BASE_URL, "ldongCode2", {"lDongRegnCd": region_code}, rows_per_page
        )
        calls.append(child_call)
        ldong_children_by_region[region_code] = children
        ldong_rows.extend(
            {"level": "SIGUNGU", "parentCode": region_code, **child} for child in children
        )

    category_roots, call = fetch_code_rows(client, KOR_BASE_URL, "categoryCode2", {}, rows_per_page)
    calls.append(call)
    category_rows: list[dict[str, Any]] = []
    for cat1 in category_roots:
        category_rows.append({"level": "CAT1", "parentCode": None, **cat1})
        cat2_rows, cat2_call = fetch_code_rows(
            client, KOR_BASE_URL, "categoryCode2", {"cat1": cat1["code"]}, rows_per_page
        )
        calls.append(cat2_call)
        for cat2 in cat2_rows:
            category_rows.append({"level": "CAT2", "parentCode": cat1["code"], **cat2})
            cat3_rows, cat3_call = fetch_code_rows(
                client,
                KOR_BASE_URL,
                "categoryCode2",
                {"cat1": cat1["code"], "cat2": cat2["code"]},
                rows_per_page,
            )
            calls.append(cat3_call)
            category_rows.extend(
                {"level": "CAT3", "parentCode": cat2["code"], **cat3} for cat3 in cat3_rows
            )

    lcls_roots, call = fetch_code_rows(client, KOR_BASE_URL, "lclsSystmCode2", {}, rows_per_page)
    calls.append(call)
    lcls_rows: list[dict[str, Any]] = []
    for level1 in lcls_roots:
        lcls_rows.append({"level": "LCLS1", "parentCode": None, **level1})
        level2_rows, level2_call = fetch_code_rows(
            client,
            KOR_BASE_URL,
            "lclsSystmCode2",
            {"lclsSystm1": level1["code"]},
            rows_per_page,
        )
        calls.append(level2_call)
        for level2 in level2_rows:
            lcls_rows.append({"level": "LCLS2", "parentCode": level1["code"], **level2})
            level3_rows, level3_call = fetch_code_rows(
                client,
                KOR_BASE_URL,
                "lclsSystmCode2",
                {"lclsSystm1": level1["code"], "lclsSystm2": level2["code"]},
                rows_per_page,
            )
            calls.append(level3_call)
            lcls_rows.extend(
                {"level": "LCLS3", "parentCode": level2["code"], **level3} for level3 in level3_rows
            )

    codebooks = {
        "areaCodes": area_rows,
        "legalDongCodes": ldong_rows,
        "legacyCategories": category_rows,
        "newClassificationCodes": lcls_rows,
        "counts": {
            "areaSIDO": sum(row["level"] == "SIDO" for row in area_rows),
            "areaSIGUNGU": sum(row["level"] == "SIGUNGU" for row in area_rows),
            "legalDongSIDO": sum(row["level"] == "SIDO" for row in ldong_rows),
            "legalDongSIGUNGU": sum(row["level"] == "SIGUNGU" for row in ldong_rows),
            "categoryCAT1": sum(row["level"] == "CAT1" for row in category_rows),
            "categoryCAT2": sum(row["level"] == "CAT2" for row in category_rows),
            "categoryCAT3": sum(row["level"] == "CAT3" for row in category_rows),
            "lcls1": sum(row["level"] == "LCLS1" for row in lcls_rows),
            "lcls2": sum(row["level"] == "LCLS2" for row in lcls_rows),
            "lcls3": sum(row["level"] == "LCLS3" for row in lcls_rows),
        },
        "_ldongChildrenByRegion": ldong_children_by_region,
    }
    return codebooks, calls


def collect_related_sample(
    client: KtoClient,
    ldong_children_by_region: dict[str, list[dict[str, Any]]],
    rows_per_page: int,
    base_ym: str,
) -> tuple[DatasetProfile, list[dict[str, Any]], list[dict[str, Any]], int]:
    aggregate = DatasetProfile(name="related_tour.multi_region_sample")
    coverage: list[dict[str, Any]] = []
    calls: list[dict[str, Any]] = []
    relation_pairs: set[tuple[str, str]] = set()

    for region_code, signgu_rows in sorted(ldong_children_by_region.items()):
        if not signgu_rows:
            continue
        signgu = signgu_rows[0]
        child_code = str(signgu["code"])
        if len(region_code) > 2:
            # Sejong can be returned as a full five-digit legal-dong root code.
            area_code = region_code[:2]
            full_signgu_code = region_code
        else:
            area_code = region_code
            full_signgu_code = child_code if len(child_code) > 3 else f"{region_code}{child_code}"
        result = fetch_all(
            client,
            f"related_{area_code}_{full_signgu_code}",
            RELATED_TOUR_BASE_URL,
            "areaBasedList1",
            {"baseYm": base_ym, "areaCd": area_code, "signguCd": full_signgu_code},
            rows_per_page,
            keep_items=True,
        )
        aggregate.reported_total_count = (aggregate.reported_total_count or 0) + (
            result.profile.reported_total_count or 0
        )
        aggregate.pages += result.profile.pages
        for sample in result.profile.samples:
            if len(aggregate.samples) < 3:
                aggregate.samples.append(sample)
        for field_name, source_profile in result.profile.fields.items():
            target = aggregate.fields[field_name]
            target.present_count += source_profile.present_count
            target.null_count += source_profile.null_count
            target.blank_count += source_profile.blank_count
            target.values.update(source_profile.values)
        aggregate.rows += result.profile.rows
        relation_pairs.update(
            (str(item.get("tAtsCd") or ""), str(item.get("rlteTatsCd") or ""))
            for item in result.items
            if item.get("tAtsCd") and item.get("rlteTatsCd")
        )
        coverage.append(
            {
                "areaCd": area_code,
                "signguCd": full_signgu_code,
                "signguName": signgu.get("name"),
                "rows": result.profile.rows,
            }
        )
        calls.extend(result.calls)

    return aggregate, coverage, calls, len(relation_pairs)


def choose_content_type_samples(items: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    selected: dict[str, dict[str, Any]] = {}
    for item in items:
        content_type_id = str(item.get("contenttypeid") or "")
        if not content_type_id or content_type_id in selected:
            continue
        if str(item.get("showflag", "1")) == "0":
            continue
        selected[content_type_id] = item
    return selected


def collect_detail_schemas(
    client: KtoClient,
    kor_items: list[dict[str, Any]],
    rows_per_page: int,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    details: dict[str, Any] = {}
    calls: list[dict[str, Any]] = []
    samples = choose_content_type_samples(kor_items)

    for content_type_id, source in sorted(samples.items(), key=lambda item: int(item[0])):
        content_id = source["contentid"]
        detail_result: dict[str, Any] = {
            "contentTypeId": content_type_id,
            "contentTypeName": KOR_CONTENT_TYPE_NAMES.get(content_type_id, "UNKNOWN"),
            "sampleContentId": content_id,
            "sampleTitle": source.get("title"),
            "operations": {},
        }
        operations = [
            ("detailCommon2", {"contentId": content_id}),
            ("detailIntro2", {"contentId": content_id, "contentTypeId": content_type_id}),
            ("detailInfo2", {"contentId": content_id, "contentTypeId": content_type_id}),
            ("detailImage2", {"contentId": content_id, "imageYN": "Y"}),
        ]
        for operation, extra_params in operations:
            params = base_params(rows_per_page)
            params.update(extra_params)
            result = client.call(KOR_BASE_URL, operation, params)
            calls.append(call_metadata(result))
            detail_result["operations"][operation] = {
                "itemCount": len(result["items"]),
                "fieldNames": sorted({key for item in result["items"] for key in item}),
                "sampleItems": result["items"][:2],
            }
        details[content_type_id] = detail_result
        print(f"[detail schemas] contentTypeId={content_type_id} {detail_result['contentTypeName']}", flush=True)

    return details, calls


def split_keyword_counts(items: list[dict[str, Any]], field_name: str) -> Counter[str]:
    counts: Counter[str] = Counter()
    for item in items:
        raw = item.get(field_name)
        if not raw:
            continue
        for keyword in str(raw).replace(";", ",").split(","):
            keyword = keyword.strip()
            if keyword:
                counts[keyword] += 1
    return counts


def non_empty_rate(items: list[dict[str, Any]], field_name: str) -> float:
    if not items:
        return 0
    count = sum(item.get(field_name) not in (None, "") for item in items)
    return round(count / len(items), 6)


def build_cross_analysis(
    kor_items: list[dict[str, Any]],
    eng_items: list[dict[str, Any]],
    gallery_items: list[dict[str, Any]],
    award_items: list[dict[str, Any]],
    related_profile: DatasetProfile,
    related_pair_count: int,
) -> dict[str, Any]:
    kor_ids = {str(item["contentid"]) for item in kor_items if item.get("contentid")}
    eng_ids = {str(item["contentid"]) for item in eng_items if item.get("contentid")}
    overlap = kor_ids & eng_ids

    gallery_keywords = split_keyword_counts(gallery_items, "galSearchKeyword")
    award_ko_keywords = split_keyword_counts(award_items, "koKeyWord")
    award_en_keywords = split_keyword_counts(award_items, "enKeyWord")

    return {
        "korEnglishContentIdMatch": {
            "korUniqueContentIds": len(kor_ids),
            "engUniqueContentIds": len(eng_ids),
            "overlapUniqueContentIds": len(overlap),
            "engCoveredBySameContentIdRate": round(len(overlap) / len(eng_ids), 6) if eng_ids else 0,
            "korCoveredByEnglishRate": round(len(overlap) / len(kor_ids), 6) if kor_ids else 0,
            "korOnlyUniqueContentIds": len(kor_ids - eng_ids),
            "engOnlyUniqueContentIds": len(eng_ids - kor_ids),
        },
        "qualityRates": {
            "kor": {
                "coordinate": round(
                    sum(bool(item.get("mapx") and item.get("mapy")) for item in kor_items) / len(kor_items), 6
                ),
                "firstImage": non_empty_rate(kor_items, "firstimage"),
                "address": non_empty_rate(kor_items, "addr1"),
                "telephone": non_empty_rate(kor_items, "tel"),
                "newClassificationL1": non_empty_rate(kor_items, "lclsSystm1"),
            },
            "eng": {
                "coordinate": round(
                    sum(bool(item.get("mapx") and item.get("mapy")) for item in eng_items) / len(eng_items), 6
                ),
                "firstImage": non_empty_rate(eng_items, "firstimage"),
                "address": non_empty_rate(eng_items, "addr1"),
                "telephone": non_empty_rate(eng_items, "tel"),
                "newClassificationL1": non_empty_rate(eng_items, "lclsSystm1"),
            },
        },
        "keywordTokens": {
            "gallery": {
                "uniqueCount": len(gallery_keywords),
                "topValues": [
                    {"value": value, "count": count} for value, count in gallery_keywords.most_common(50)
                ],
            },
            "awardKorean": {
                "uniqueCount": len(award_ko_keywords),
                "topValues": [
                    {"value": value, "count": count} for value, count in award_ko_keywords.most_common(50)
                ],
            },
            "awardEnglish": {
                "uniqueCount": len(award_en_keywords),
                "topValues": [
                    {"value": value, "count": count} for value, count in award_en_keywords.most_common(50)
                ],
            },
        },
        "relatedSample": {
            "scope": "one sigungu per legal-dong region for the configured base month",
            "rows": related_profile.rows,
            "uniqueSourcePlaces": len(related_profile.fields["tAtsCd"].values),
            "uniqueRelatedPlaces": len(related_profile.fields["rlteTatsCd"].values),
            "uniqueRelationPairs": related_pair_count,
        },
    }


def counter_value(profile: DatasetProfile, field_name: str) -> Counter[str]:
    return profile.fields.get(field_name, FieldProfile()).values


def markdown_top_values(profile: DatasetProfile, field_name: str, limit: int = 12) -> str:
    counter = counter_value(profile, field_name)
    if not counter:
        return "-"
    return ", ".join(f"{display_value(value, 45)} ({count:,})" for value, count in counter.most_common(limit))


def percent(value: float) -> str:
    return f"{value * 100:.1f}%"


def make_summary_markdown(
    run_dir: Path,
    profiles: dict[str, DatasetProfile],
    codebooks: dict[str, Any],
    cross: dict[str, Any],
    related_coverage: list[dict[str, Any]],
    base_ym: str,
    call_count: int,
) -> str:
    kor = profiles["kor_tour.areaBasedSyncList2"]
    eng = profiles["eng_tour.areaBasedSyncList2"]
    gallery = profiles["photo_gallery.galleryList1"]
    award = profiles["photo_award.phokoAwrdSyncList"]
    related = profiles["related_tour.multi_region_sample"]
    match = cross["korEnglishContentIdMatch"]
    quality = cross["qualityRates"]

    lines = [
        "# KTO API Data Profile",
        "",
        f"- Generated: `{datetime.now().isoformat(timespec='seconds')}`",
        f"- API calls: `{call_count}`",
        "- Scope: Kor/Eng sync lists, gallery, and photo awards are fully paginated.",
        f"- Related-tour scope: one sigungu from each available legal-dong region, base month `{base_ym}`.",
        "- Secret handling: the service key is not written to these outputs.",
        "",
        "## Dataset Size",
        "",
        "| Dataset | Reported | Fetched | Fields |",
        "|---|---:|---:|---:|",
    ]
    for profile in profiles.values():
        lines.append(
            f"| `{profile.name}` | {profile.reported_total_count or 0:,} | {profile.rows:,} | {len(profile.fields):,} |"
        )

    lines.extend(
        [
            "",
            "## Exact Codebook Counts",
            "",
            "| Codebook | Level | Unique rows |",
            "|---|---|---:|",
            f"| TourAPI area | SIDO | {codebooks['counts']['areaSIDO']:,} |",
            f"| TourAPI area | SIGUNGU | {codebooks['counts']['areaSIGUNGU']:,} |",
            f"| Legal dong | SIDO | {codebooks['counts']['legalDongSIDO']:,} |",
            f"| Legal dong | SIGUNGU | {codebooks['counts']['legalDongSIGUNGU']:,} |",
            f"| Legacy category | CAT1 | {codebooks['counts']['categoryCAT1']:,} |",
            f"| Legacy category | CAT2 | {codebooks['counts']['categoryCAT2']:,} |",
            f"| Legacy category | CAT3 | {codebooks['counts']['categoryCAT3']:,} |",
            f"| New classification | LCLS1 | {codebooks['counts']['lcls1']:,} |",
            f"| New classification | LCLS2 | {codebooks['counts']['lcls2']:,} |",
            f"| New classification | LCLS3 | {codebooks['counts']['lcls3']:,} |",
            "",
            "## Main Unique Values",
            "",
            "| Dataset | Field | Exact non-empty unique | Main values |",
            "|---|---|---:|---|",
        ]
    )
    for profile, fields in [
        (kor, ["contentid", "contenttypeid", "areacode", "cat1", "cat2", "cat3", "lclsSystm1", "lclsSystm2", "lclsSystm3", "cpyrhtDivCd", "showflag"]),
        (eng, ["contentid", "contenttypeid", "areacode", "cat1", "cat2", "cat3", "lclsSystm1", "cpyrhtDivCd", "showflag"]),
        (gallery, ["galContentId", "galContentTypeId", "galPhotographyMonth", "galPhotographer"]),
        (award, ["contentId", "lDongRegnCd", "filmDay", "koWnprzDiz", "cpyrhtDivCd", "showflag"]),
        (related, ["tAtsCd", "rlteTatsCd", "rlteCtgryLclsNm", "rlteCtgryMclsNm", "rlteCtgrySclsNm", "rlteRank", "areaNm", "rlteRegnNm"]),
    ]:
        for field_name in fields:
            field_profile = profile.fields.get(field_name)
            if not field_profile:
                continue
            lines.append(
                f"| `{profile.name}` | `{field_name}` | {len(field_profile.values):,} | "
                f"{markdown_top_values(profile, field_name)} |"
            )

    lines.extend(
        [
            "",
            "## Coverage And Quality",
            "",
            "| Metric | Korean | English |",
            "|---|---:|---:|",
            f"| Coordinates | {percent(quality['kor']['coordinate'])} | {percent(quality['eng']['coordinate'])} |",
            f"| First image | {percent(quality['kor']['firstImage'])} | {percent(quality['eng']['firstImage'])} |",
            f"| Address | {percent(quality['kor']['address'])} | {percent(quality['eng']['address'])} |",
            f"| Telephone | {percent(quality['kor']['telephone'])} | {percent(quality['eng']['telephone'])} |",
            f"| New classification L1 | {percent(quality['kor']['newClassificationL1'])} | {percent(quality['eng']['newClassificationL1'])} |",
            "",
            f"- Korean unique content IDs: `{match['korUniqueContentIds']:,}`",
            f"- English unique content IDs: `{match['engUniqueContentIds']:,}`",
            f"- Same-ID overlap: `{match['overlapUniqueContentIds']:,}`",
            f"- English rows matchable to Korean by contentId: `{percent(match['engCoveredBySameContentIdRate'])}`",
            f"- Korean rows with same-ID English content: `{percent(match['korCoveredByEnglishRate'])}`",
            "",
            "## Related-tour Sampling Coverage",
            "",
            "| Area | Sigungu | Name | Rows |",
            "|---|---|---|---:|",
        ]
    )
    for row in related_coverage:
        lines.append(
            f"| {row['areaCd']} | {row['signguCd']} | {row['signguName']} | {row['rows']:,} |"
        )

    lines.extend(
        [
            "",
            "## Files",
            "",
            "- `profiles.json`: field-by-field exact unique counts, missing/blank counts, and top values.",
            "- `codebooks.json`: expanded area, legal-dong, legacy category, and new classification trees.",
            "- `detail-schemas.json`: actual detail operation fields by Korean content type.",
            "- `cross-analysis.json`: Korean/English overlap, quality rates, and split keyword statistics.",
            "- `calls.json`: masked request metadata for reproducibility.",
            "",
            f"Output directory: `{run_dir}`",
            "",
        ]
    )
    return "\n".join(lines)


def run_profile(rows_per_page: int, output_root: Path, timeout: int, retries: int, base_ym: str) -> int:
    service_key = os.environ.get("KTO_SERVICE_KEY")
    if not service_key:
        print("KTO_SERVICE_KEY is not set. Put it in .env.local or environment.", file=sys.stderr)
        return 2

    run_dir = output_root / now_stamp()
    client = KtoClient(service_key, timeout=timeout, retries=retries)
    all_calls: list[dict[str, Any]] = []

    print("[1/6] Expanding codebooks", flush=True)
    codebooks, calls = collect_codebooks(client, rows_per_page)
    all_calls.extend(calls)
    ldong_children = codebooks.pop("_ldongChildrenByRegion")

    print("[2/6] Fetching complete Korean sync list", flush=True)
    kor = fetch_all(
        client,
        "kor_tour.areaBasedSyncList2",
        KOR_BASE_URL,
        "areaBasedSyncList2",
        {},
        rows_per_page,
    )
    all_calls.extend(kor.calls)

    print("[3/6] Fetching complete English sync list", flush=True)
    eng = fetch_all(
        client,
        "eng_tour.areaBasedSyncList2",
        ENG_BASE_URL,
        "areaBasedSyncList2",
        {},
        rows_per_page,
    )
    all_calls.extend(eng.calls)

    print("[4/6] Fetching complete image datasets", flush=True)
    gallery = fetch_all(
        client,
        "photo_gallery.galleryList1",
        PHOTO_GALLERY_BASE_URL,
        "galleryList1",
        {},
        rows_per_page,
    )
    award = fetch_all(
        client,
        "photo_award.phokoAwrdSyncList",
        PHOTO_AWARD_BASE_URL,
        "phokoAwrdSyncList",
        {},
        rows_per_page,
    )
    all_calls.extend(gallery.calls)
    all_calls.extend(award.calls)

    print("[5/6] Sampling related-tour data and detail schemas", flush=True)
    related_profile, related_coverage, calls, related_pair_count = collect_related_sample(
        client, ldong_children, rows_per_page, base_ym
    )
    all_calls.extend(calls)
    detail_schemas, calls = collect_detail_schemas(client, kor.items, rows_per_page)
    all_calls.extend(calls)

    print("[6/6] Writing aggregate outputs", flush=True)
    profiles = {
        kor.profile.name: kor.profile,
        eng.profile.name: eng.profile,
        gallery.profile.name: gallery.profile,
        award.profile.name: award.profile,
        related_profile.name: related_profile,
    }
    cross = build_cross_analysis(
        kor.items,
        eng.items,
        gallery.items,
        award.items,
        related_profile,
        related_pair_count,
    )

    write_json(
        run_dir / "profiles.json",
        {name: profile.as_dict() for name, profile in profiles.items()},
    )
    write_json(run_dir / "codebooks.json", codebooks)
    write_json(run_dir / "detail-schemas.json", detail_schemas)
    write_json(run_dir / "cross-analysis.json", cross)
    write_json(run_dir / "related-coverage.json", related_coverage)
    write_json(run_dir / "calls.json", all_calls)
    write_text(
        run_dir / "SUMMARY.md",
        make_summary_markdown(
            run_dir,
            profiles,
            codebooks,
            cross,
            related_coverage,
            base_ym,
            client.call_count,
        ),
    )
    write_text(
        output_root / "LATEST.md",
        f"# Latest KTO API Data Profile\n\n- Latest run: [{run_dir.name}]({run_dir.name}/SUMMARY.md)\n",
    )
    print(run_dir, flush=True)
    return 0


def main() -> int:
    load_dotenv(ROOT / ".env.local")
    parser = argparse.ArgumentParser(description="Deep-profile approved KTO data for KoReady development.")
    parser.add_argument("--rows-per-page", type=int, default=DEFAULT_ROWS_PER_PAGE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--retries", type=int, default=3)
    parser.add_argument("--related-base-ym", default=DEFAULT_RELATED_BASE_YM)
    args = parser.parse_args()
    return run_profile(
        rows_per_page=args.rows_per_page,
        output_root=args.output,
        timeout=args.timeout,
        retries=args.retries,
        base_ym=args.related_base_ym,
    )


if __name__ == "__main__":
    raise SystemExit(main())
