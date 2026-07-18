# Koready 가공 저장형 DB 모델 초안

이 문서의 전체 모델은 여전히 초안이다. 다만 KTO 수집 기반에 필요한 권역, 장소,
축제 개최 회차, 원천 추적, 호출 로그, sync cursor, batch job 범위는
`V1__create_kto_ingestion_foundation.sql`로 확정됐다. 로그인 전 테스트 사용자와
K-Local Pick 추천 덱·페이지·30일 노출 제한 범위는
`V8__add_recommendation_decks.sql`로 확정됐다. 적용된 실제 테이블과 제약은 migration
파일을 기준으로 하며, 이후 변경은 새 migration으로만 추가한다.

## 0. 모델링 원칙

- 한국관광공사 API 응답을 그대로 메인 DB로 쓰지 않는다.
- 화면과 추천 로직에 필요한 형태로 정규화/가공 저장한다.
- 원천 API contentId와 modifiedTime은 반드시 보존한다.
- 국문 데이터가 마스터이고, 영문/번역 데이터는 localization으로 분리한다.
- TMAP 응답 원본은 DB에 저장하지 않는다.
- OpenAPI 호출 로그는 증빙용으로 별도 저장한다.
- AI 생성 데이터는 원천 데이터와 분리해 저장한다.

---

## 1. 사용자 / 인증 / 약관

## 1.1 users

| 컬럼 | 설명 |
|---|---|
| id | 내부 유저 ID |
| email | 이메일 |
| profile_image_url | 소셜 프로필 이미지 |
| preferred_language | KO/EN |
| signup_status | NEED_TERMS/NEED_LANGUAGE/NEED_ONBOARDING/COMPLETED |
| default_location_id | nullable FK. 사용자의 유일한 기본 위치 |
| onboarding_completed_at | nullable 온보딩 완료 시각. 방문 목적 정보는 저장하지 않음 |
| created_at / updated_at | 생성/수정 시각 |
| deleted_at | 탈퇴 시각 |

## 1.2 social_accounts

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| provider | GOOGLE/APPLE |
| provider_user_id | 소셜 provider subject |
| email | provider 이메일 |
| connected_at | 연결 시각 |

## 1.3 auth_sessions

Refresh token 회전, 로그아웃, 기기별 세션 폐기를 위한 서버 저장 세션이다. 원문 token은 저장하지 않는다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| refresh_token_hash | 단방향 hash, unique |
| device_id | 클라이언트 기기 식별값 |
| expires_at | 만료 시각 |
| rotated_from_session_id | nullable 이전 세션 FK |
| revoked_at | 로그아웃/강제 폐기 시각 |
| created_at / last_used_at | 생성/최근 사용 시각 |

## 1.4 terms

| 컬럼 | 설명 |
|---|---|
| id | PK |
| code | SERVICE_TERMS/PRIVACY_POLICY/MARKETING |
| title | 약관명 |
| required | 필수 여부 |
| active | 사용 여부 |

## 1.5 term_versions

| 컬럼 | 설명 |
|---|---|
| id | PK |
| term_id | FK |
| version | 예: 1.0 |
| content | 본문 또는 content_url |
| effective_date | 시행일 |
| active | 현재 활성 여부 |

## 1.6 user_term_agreements

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| term_id | FK |
| term_version_id | FK |
| agreed | 동의 여부 |
| agreed_at | 동의 시각 |

## 1.7 user_roles

| 컬럼 | 설명 |
|---|---|
| user_id | FK |
| role | USER/ADMIN/OPERATOR/AUDITOR |
| granted_by | role을 부여한 관리자 user ID |
| granted_at | 부여 시각 |

---

## 2. 온보딩 / 위치

## 2.1 user_travel_styles

| 컬럼 | 설명 |
|---|---|
| user_id | FK |
| travel_style | LOCAL_FOOD 등 7개 유형 |
| display_order | 선택 순서 |

### 제약

- 사용자당 최대 4개까지만 저장한다.
- 동일 user_id/travel_style 중복 저장을 금지한다.

## 2.2 user_locations

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| provider | KAKAO |
| provider_place_id | 외부 장소 ID |
| name | 장소명 |
| road_address | 도로명 주소 |
| address | 지번 주소 |
| detail_address | 상세 주소 |
| latitude / longitude | 좌표 |
| sido / sigungu / dong | 행정구역 텍스트 |
| service_region_code | 7개 권역 코드 |
| custom_label | 사용자가 지정한 학교/집/기숙사 등의 별칭 |
| provider_result_hash | 검색 결과 무결성·중복 확인용 hash. 검색 token 원문은 저장하지 않음 |
| deleted_at | 삭제 시각 |

### 위치 관리 제약

- 사용자는 배달앱처럼 여러 위치를 저장, 조회, 기본 위치 지정, 삭제할 수 있다.
- 위치 추가는 서버가 발행한 단기 `searchResultToken` 검증을 통과한 Kakao 검색 결과만 허용한다.
- 클라이언트가 전송한 주소·좌표·권역 문자열을 저장 원천으로 사용하지 않는다.
- 기본 위치의 단일 원천은 `users.default_location_id`다. 활성 상태이고 같은 사용자가 소유한 `user_locations.id`만 지정할 수 있다.
- 삭제된 위치는 즉시 추천과 경로 출발지에서 제외한다.
- 기본 위치 삭제는 같은 transaction에서 새 기본 위치를 지정하거나 `users.default_location_id=null`로 변경한다. 남은 위치가 없으면 다음 추천/경로 진입 전에 위치 등록을 요구한다.

## 2.3 onboarding_preference_candidate_sets

관리자가 검수한 온보딩 선호 여행지 10개를 담는 버전형 큐레이션 세트다. 사용자별로 생성하지 않으며 발행된 버전은 수정하지 않는다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| candidate_set_key | 클라이언트에 내려주는 opaque ID |
| version | 증가하는 발행 버전 |
| status | DRAFT/PUBLISHED/ARCHIVED |
| title | 관리자 식별용 세트명 |
| created_by / updated_by | 관리자 FK |
| published_by | nullable 관리자 FK |
| created_at / updated_at | 생성/수정 시각 |
| published_at / archived_at | nullable 발행/보관 시각 |

## 2.4 onboarding_preference_candidates

후보 세트에 포함된 정확히 10개 관광지 목록이다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| candidate_set_id | FK |
| place_id | FK |
| display_order | 표시 순서 |
| travel_style | 대표 관광 유형 |
| representative_image_id | nullable 관리자 선택 대표 이미지 FK |
| curator_message_ko / curator_message_en | KO/EN 한 줄 큐레이터 설명 |
| display_tags_json | 화면 노출용 검수 태그 |
| editor_note | nullable 내부 편집 메모. 사용자 API에 노출하지 않음 |

### 큐레이션 발행 제약

- `PUBLISHED`로 전환할 때 item은 정확히 10개이고 `place_id`와 `display_order`는 중복될 수 없다.
- 모든 장소는 활성/표출 가능하며 한국어 제목, 주소, 좌표, 대표 이미지가 있어야 한다.
- 발행 이후 set/item row는 수정하지 않고 새 `version`의 초안을 만든다.
- 동시에 신규 사용자에게 노출되는 현재 발행 세트는 하나지만 과거 발행 버전은 기존 온보딩 제출 검증을 위해 보존한다.

## 2.5 user_onboarding_place_selections

사용자가 취향 보정 단계에서 선택한 관광지다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| candidate_set_id | FK |
| place_id | FK |
| selected_order | 선택 순서 |
| selected_at | 선택 시각 |

### 제약

- 사용자 온보딩 완료 기준 최소 1개, 최대 3개 선택.
- `place_id`는 해당 발행 `candidate_set_id/version`에 포함된 후보여야 한다.

## 2.6 user_preference_tags

선택한 관광지의 태그를 사용자 취향 태그로 변환해 저장한다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| tag_id | FK |
| source | ONBOARDING_PLACE_SELECTION/MANUAL/BEHAVIOR_INFERRED |
| weight | 추천 점수에 반영할 가중치 |
| source_place_ids_json | 어떤 선택 관광지에서 유래했는지 |
| created_at / updated_at | 생성/수정 시각 |

### 가중치 초안

- 선택한 관광지 1개에서 나온 태그: `1.0`
- 여러 선택 관광지에서 반복된 태그: 반복 횟수만큼 가산하되 상한을 둔다.
- 사용자가 나중에 명시적으로 태그를 조정하면 `MANUAL` 출처를 우선한다.

---

## 3. 지역/권역

## 3.1 service_regions

| 컬럼 | 설명 |
|---|---|
| code | SEOUL/GYEONGGI/... |
| name_ko | 한국어 권역명 |
| name_en | 영어 권역명 |
| display_order | 표시 순서 |
| map_asset_key | 프론트 지도 asset key |

## 3.2 administrative_regions

| 컬럼 | 설명 |
|---|---|
| code | TourAPI/법정동/행정구역 코드 |
| name | 지역명 |
| level | SIDO/SIGUNGU/DONG |
| parent_code | 상위 코드 |
| service_region_code | 7권역 매핑 |
| center_latitude / center_longitude | 선택값 |

## 3.3 tour_api_category_codes

`categoryCode2` 기반 레거시 서비스 분류 코드다. 기존 contentType/category 기준 매핑 검증에 쓴다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| content_type_id | TourAPI contentTypeId |
| cat1 / cat2 / cat3 | 서비스 분류 코드 |
| name | 분류명 |
| parent_code | 상위 분류 |
| active | 사용 여부 |
| last_synced_at | 마지막 동기화 시각 |

## 3.4 tour_api_lcls_codes

`lclsSystmCode2` 기반 신분류체계 코드다. Koready 7개 관광 유형 매핑의 주요 근거로 사용한다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| lcls_systm1 | 대분류 코드 |
| lcls_systm2 | 중분류 코드 |
| lcls_systm3 | 소분류 코드 |
| name_ko | 국문 분류명 |
| mapped_travel_style | LOCAL_FOOD 등 7개 유형 후보 |
| confidence | 매핑 신뢰도 |
| active | 사용 여부 |
| last_synced_at | 마지막 동기화 시각 |

---

## 4. 관광지 마스터

## 4.1 places

| 컬럼 | 설명 |
|---|---|
| id | 내부 place ID |
| kto_content_id | TourAPI contentId |
| kto_content_type_id | TourAPI contentTypeId |
| service_region_code | 7권역 |
| area_code | TourAPI 지역코드 |
| sigungu_code | TourAPI 시군구코드 |
| ldong_regn_cd | 법정동 시도코드 |
| ldong_signgu_cd | 법정동 시군구코드 |
| lcls_systm1/2/3 | 분류체계코드 |
| address | 주소 |
| road_address | 도로명 주소 |
| latitude / longitude | 좌표 |
| tel | 문의처 |
| homepage | 홈페이지 |
| first_image_url | 대표 이미지 후보 |
| source_modified_time | TourAPI 수정일 |
| show_flag | 표출 여부 |
| active | 서비스 사용 여부 |
| data_quality_score | 내부 품질 점수 |
| created_at / updated_at | 생성/수정 시각 |

### 제약

- `kto_content_id`는 unique.
- 좌표가 없으면 Route 기능 사용 불가.
- 대표 이미지가 없으면 추천 점수 감점.

## 4.2 place_localizations

| 컬럼 | 설명 |
|---|---|
| id | PK |
| place_id | FK |
| language | KO/EN |
| title | 표시명 |
| overview | 원천/번역 개요 |
| address_text | 언어별 주소 텍스트 |
| translation_source | KTO_KO/KTO_EN/AI_TRANSLATED/MANUAL_EDITED |
| source_content_id | 영문 API contentId 등 |
| source_hash | 원천 데이터 해시 |
| updated_at | 갱신 시각 |

## 4.2.1 place_source_records

KTO 원천 레코드와 snapshot을 가공 데이터에 연결하는 계보 테이블이다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| provider | KTO |
| api_name / operation | 원천 API 구분 |
| source_content_id | 원천 content ID |
| language | KO/EN |
| raw_snapshot_id | FK |
| source_modified_time | 원천 수정 시각 |
| source_hash | 정규화 전 원천 레코드 hash |
| captured_at | 수집 시각 |

## 4.2.2 place_source_matches

영문 crosswalk와 연관 관광지 staging처럼 원천 레코드를 내부 장소에 연결한 판단을 보존한다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| source_record_id | FK |
| place_id | FK |
| match_method | IMAGE_PATH/COORDINATE_TYPE/MANUAL 등 |
| confidence | 매칭 신뢰도 |
| candidate_count | 후보 수 |
| evidence_json | 판단 근거. secret/PII 제외 |
| status | AUTO_CONFIRMED/REVIEW_REQUIRED/REJECTED |
| matcher_version | 매칭 규칙 버전 |
| created_at / reviewed_at | 처리/검수 시각 |

## 4.3 place_images

| 컬럼 | 설명 |
|---|---|
| id | PK |
| place_id | FK |
| source_type | KTO_TOURAPI/KTO_PHOTO/MANUAL/S3 |
| image_url | 원본 이미지 URL |
| thumbnail_url | 썸네일 URL |
| copyright_type | 공공누리/저작권 구분 |
| license_text | 라이선스 표시 |
| origin_title | 원천 이미지 제목 |
| display_order | 표시 순서 |
| is_primary | 대표 이미지 여부 |
| active | 사용 여부 |

## 4.4 festival_series

반복 축제의 연도와 무관한 공통 정체성이다. KTO가 연도마다 다른 place/content ID를 주더라도 검수된 시리즈로 연결할 수 있다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| series_key | 운영/매칭용 영구 unique key |
| canonical_place_id | 대표 장소 FK |
| title_ko / title_en | 시리즈 표시명 |
| match_status | AUTO_CONFIRMED/REVIEW_REQUIRED/MANUAL_CONFIRMED |
| created_at / updated_at | 생성/수정 시각 |

## 4.5 place_event_occurrences

축제의 한 개최 회차다. 이전 연도와 신규 연도의 일정·운영 정보를 같은 row에 덮어쓰지 않는다.

| 컬럼 | 설명 |
|---|---|
| id | PK. API의 occurrenceId |
| festival_series_id | nullable 반복 축제 시리즈 FK |
| place_id | 해당 회차를 표시할 장소 FK |
| event_year | `start_date` 기준 개최 연도 |
| occurrence_sequence | 같은 연도에 여러 회차가 있을 때 1부터 증가 |
| start_date | 회차 시작일 |
| end_date | 회차 종료일 |
| event_place | 행사 장소 |
| play_time | 운영/공연 시간 |
| use_fee | 요금 |
| sponsor | 주최/주관 |
| provider | KTO/MANUAL |
| source_content_id | provider 범위의 원천 행사 ID |
| source_operation | searchFestival2/detailIntro2 등 |
| source_hash | 회차 원천 record hash |
| visible_from | `start_date - 6개월`, 상세 목록 최초 조회 가능일 |
| date_validation_status | VALID/MISSING/INVALID |
| created_at / updated_at | 생성/수정 시각 |

- unique: `(festival_series_id, event_year, occurrence_sequence)`. `festival_series_id`가 없을 때는 provider key를 사용한다.
- unique: `(provider, source_content_id, event_year, occurrence_sequence)`.
- 공급자가 기간을 정정하면 같은 회차 row를 갱신하고 snapshot/source hash로 변경 전 원천을 추적한다.
- `UPCOMING/ONGOING/ENDED`는 저장하지 않고 `Asia/Seoul`의 조회일과 시작/종료일로 계산한다.
- `visible_from` 이전에는 노출하지 않는다. 이후에는 종료돼도 해당 `event_year/month` 상세 목록에 `ENDED`로 남긴다.
- 홈과 현재 추천 정렬에서는 `ONGOING`, `UPCOMING`을 `ENDED`보다 우선한다.

## 4.6 place_intro_details

MVP에서는 type별 상세 컬럼 테이블보다 key-value 방식이 빠르다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| place_id | FK |
| content_type_id | TourAPI contentTypeId |
| detail_key | parking/restDate/useTime 등 |
| detail_value | 원본 값 |
| language | KO/EN |
| source_operation | detailIntro2/detailInfo2 |

## 4.7 place_pet_infos

`detailPetTour2`를 사용할 때만 생성한다. MVP 핵심 테이블은 아니며, 향후 반려동물 동반 가능 장소 필터/태그로 확장할 때 사용한다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| place_id | FK |
| acmpy_type_cd | 동반 가능 유형 코드 |
| rela_acmpy_info | 반려동물 동반 관련 안내 |
| rela_poses_fclty | 반려동물 가능 시설 |
| rela_frnsh_prdlst | 비치 품목 |
| etc_acmpy_info | 기타 안내 |
| source_hash | 원천 데이터 해시 |
| updated_at | 갱신 시각 |

---

## 5. 태그 / 추천 가공 데이터

## 5.1 tags

| 컬럼 | 설명 |
|---|---|
| id | PK |
| code | INTERNAL TAG CODE |
| name_ko | 한글명 |
| name_en | 영문명 |
| category | STYLE/KEYWORD/SEASON/DIFFICULTY |
| active | 사용 여부 |

## 5.2 place_tags

| 컬럼 | 설명 |
|---|---|
| place_id | FK |
| tag_id | FK |
| source | KTO_CATEGORY/RULE_BASED/AI_GENERATED/MANUAL |
| confidence | 신뢰도 |
| generated_at | 생성 시각 |

## 5.3 place_style_mappings

사용자 화면의 7개 관광 유형과 place를 연결한다.

| 컬럼 | 설명 |
|---|---|
| place_id | FK |
| travel_style | 7개 관광 유형 |
| source | CONTENT_TYPE/LCLS/KEYWORD/AI/MANUAL |
| confidence | 신뢰도 |

## 5.4 place_monthly_features

| 컬럼 | 설명 |
|---|---|
| place_id | FK |
| month | 1~12 |
| reason_type | EVENT_OCCURRENCE/PHOTO_SEASON_HINT/AI_SEASON/MANUAL |
| reason_text_ko/en | 월별 추천 이유 |
| score | 점수 |

## 5.5 place_recommendation_scores

| 컬럼 | 설명 |
|---|---|
| place_id | FK |
| score_type | HOME_MONTHLY/K_LOCAL_PICK/MAP_REGION |
| score | 계산된 추천 점수 |
| calculated_at | 계산 시각 |
| version | 점수 계산 버전 |

---

## 6. AI 설명 데이터

## 6.1 place_ai_descriptions

| 컬럼 | 설명 |
|---|---|
| id | PK |
| place_id | FK |
| language | KO/EN |
| impact_title | 임팩트 소개 1줄 |
| impact_subtitle | 임팩트 소개 2줄 |
| intro_text | 2~3문단 본문 |
| source_hash | 원천 데이터 해시 |
| generation_status | PENDING/COMPLETED/FAILED |
| model_name | 사용 모델 |
| prompt_version | 프롬프트 버전 |
| reviewed | 수동 검수 여부 |
| generated_at | 생성 시각 |

## 6.2 place_ai_description_points

| 컬럼 | 설명 |
|---|---|
| id | PK |
| description_id | FK |
| display_order | 순서 |
| point_text | 즐기는 포인트 |

---

## 7. 관련 명소

## 7.1 place_relations

| 컬럼 | 설명 |
|---|---|
| id | PK |
| source_place_id | 기준 장소 |
| related_place_id | 관련 장소 |
| relation_source | KTO_RELATED/SAME_REGION_TAG/RANDOM_FALLBACK |
| rank | 표시 순서 |
| score | 연관 점수 |
| last_synced_at | 동기화 시각 |

---

## 8. 저장 / 추천 이벤트 로그

## 8.1 user_saved_places

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| place_id | FK |
| source | HOME_MONTHLY/RECOMMENDATION_CARD/PLACE_DETAIL/MAP |
| saved_at | 저장 시각 |
| deleted_at | 저장 취소 시각 |

## 8.2 user_place_events

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| place_id | FK |
| event_type | CARD_SERVED/CARD_DETAIL_CLICKED/ROUTE_OPENED 등 |
| deck_id | 추천 덱 ID |
| policy_version | `recommendation-suppression-v1` 등 기록 시점 정책 |
| suppression_days | CARD_SERVED 당시 적용한 제한 일수. v1은 30 |
| metadata_json | 추가 정보 |
| created_at | 이벤트 시각 |

## 8.3 recommendation_decks

K-Local Pick 카드더미 단위다. 같은 deck 안에서는 중복 관광지가 나오지 않아야 한다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| deck_key | 클라이언트에 내려주는 deckId |
| user_id | FK |
| scope | NEARBY/NATIONWIDE |
| seed | 사용자/날짜/요청 기준 seed |
| cursor_version | cursor 구조 버전 |
| created_at | 생성 시각 |
| expires_at | 만료 시각 |

## 8.4 recommendation_deck_items

deck에 실제 포함된 관광지와 순서다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| deck_id | FK |
| place_id | FK |
| display_order | deck 내 순서 |
| match_rank | 1/2/3 추천 우선순위 |
| match_reason_json | 관광 유형/태그 매칭 정보 |
| served_at | 응답에 포함된 시각 |

### 제약

- `deck_id + place_id` unique.
- cursor 재요청 시 같은 `deck_id/display_order` 구간은 같은 place 목록을 반환한다.

## 8.5 user_place_recommendation_states

사용자별 관광지 노출 상태다. MVP1에서 "다음에 안 나오도록" 보장하기 위한 핵심 테이블이다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| place_id | FK |
| first_served_at | 최초 추천 노출 시각 |
| last_served_at | 마지막 추천 노출 시각 |
| served_count | 노출 횟수 |
| last_deck_id | 마지막 노출 deck |
| suppress_until | 이 시각 전까지 추천 후보에서 제외 |
| suppression_policy_version | 마지막 제한을 계산한 정책 버전 |
| last_suppression_days | 마지막 제한 일수 |
| last_event_type | 마지막 관련 이벤트 |

### no-repeat 확정 정책

- 같은 deck 내 중복은 절대 금지한다.
- `CARD_SERVED`로 응답에 포함된 관광지는 `suppress_until` 전까지 새 deck 후보에서 제외한다.
- v1 suppress 기간은 `CARD_SERVED` 시각부터 30일로 고정하고 `recommendation-suppression-v1`을 기록한다.
- 후보 풀이 작아져도 30일을 줄이지 않는다. 후보 범위를 넓힌 후에도 없으면 `hasMore=false`를 반환한다.
- 저장/상세 클릭 같은 명시 행동은 별도 이벤트로 남기되, no-repeat 기준은 `served` 상태를 우선한다.
- 서비스 테스트로 후보 소진이 과도하다는 근거가 생기면 새 policy version에서 기록 시점 또는 기간을 바꿀 수 있다. 기존 이벤트의 `served_at`, 일수, `suppress_until`은 소급 수정하지 않는다.

---

## 9. Buddy Route / Hori Tip

## 9.1 route_query_logs

TMAP 경로 결과를 재현할 수 없는 호출 메타데이터만 장기 저장한다. 원본과 정규화된 시간·요금·구간은 Redis 등 임시 캐시에 두고 24시간 전에 삭제한다.

당일치기 판정은 TMAP 원본 `totalTime` 초로 수행한다. `totalTime < 10800`이면 당일치기 가능이며 표시용 분 값의 반올림 결과로 판정하지 않는다. 외부 응답은 `BUS/TRAIN`처럼 단순화하되 내부 난이도 계산에서는 버스·열차의 세부 `type/route`를 별도 정규화한다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| place_id | FK |
| origin_location_id | FK |
| requested_at | 요청 시각 |
| success | 성공 여부 |
| provider | TMAP_TRANSIT |
| provider_http_status | 외부 응답 상태 |
| duration_ms | 외부 호출 지연시간 |
| error_code | 내부 정규화 오류 코드 |
| error_message | 실패 메시지 |

## 9.2 buddy_tip_templates

| 컬럼 | 설명 |
|---|---|
| id | PK |
| code | TIP_KTX_GIMCHEON_GUMI 등 |
| status | DRAFT/ACTIVE/INACTIVE/ARCHIVED |
| priority | 우선순위 |
| placement | TOP_SUMMARY/AFTER_SEGMENT |
| scope_type | ALL_ROUTES/DESTINATION_PLACES |
| valid_from/valid_until | 선택적 노출 기간 |
| operator_note | 사용자 미노출 운영 메모 |
| version | 낙관적 잠금 version |
| created_by_user_id/updated_by_user_id | 작성·수정 운영자 |
| activated_at | 마지막 활성화 시각 |
| created_at/updated_at | 생성·수정 시각 |

`code`는 unique이며 물리 삭제하지 않는다. 사용자 노출 대상은 `status=ACTIVE`이고 현재 시각이 노출 기간 안에 있는 template뿐이다.

- index: `(status, valid_from, valid_until, priority)`

## 9.3 buddy_tip_place_scopes

`scope_type=DESTINATION_PLACES`인 팁이 적용될 목적지를 저장한다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| template_id | FK |
| place_id | FK |

- unique: `(template_id, place_id)`
- index: `(place_id, template_id)`
- `ALL_ROUTES` 팁은 이 테이블에 row를 만들지 않는다.

## 9.4 buddy_tip_translations

| 컬럼 | 설명 |
|---|---|
| id | PK |
| template_id | FK |
| language | KO/EN |
| title | 사용자 노출값 `Hori Tip` 고정 |
| body | 운영진이 입력한 툴팁 본문 |

- unique: `(template_id, language)`
- `ACTIVE` 전환 시 KO/EN 본문이 모두 있어야 한다.

## 9.5 buddy_tip_trigger_rules

| 컬럼 | 설명 |
|---|---|
| id | PK |
| template_id | FK |
| rule_version | 현재 구조는 1 |
| rule_json | 검증된 구조의 매칭 조건 JSON |
| created_at/updated_at | 생성·수정 시각 |

- unique: `(template_id)`

예시:

```json
{
  "segmentModes": ["TRAIN"],
  "routeNameContainsAny": ["KTX", "KTX-산천", "KTX이음"],
  "segmentEndNameContainsAny": ["김천(구미)역"],
  "minProviderTotalTimeSeconds": null,
  "minTransferCount": null,
  "minTotalWalkDistanceMeters": null
}
```

- 값이 있는 조건 그룹끼리는 AND, 각 `*ContainsAny` 배열 안에서는 OR로 평가한다.
- 문자열 비교는 trim 후 대소문자를 무시하는 contains이며 정규식은 허용하지 않는다.
- `AFTER_SEGMENT`는 segment 조건이 하나 이상 필요하고, 처음 일치한 segment에 한 번만 붙인다.
- `ALL_ROUTES`라도 trigger 조건이 하나 이상 있어야 활성화할 수 있다.

## 9.6 buddy_tip_exposures

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| place_id | FK |
| tip_template_id | FK |
| route_query_log_id | FK |
| segment_order | TOP_SUMMARY면 0, 구간 팁이면 1 이상의 order |
| exposed_at | 노출 시각 |

- unique: `(route_query_log_id, tip_template_id, segment_order)`
- MySQL unique index에서 null은 중복을 허용하므로 TOP_SUMMARY를 null로 저장하지 않는다.
- 팁 본문과 TMAP 구간 원문은 노출 로그에 복제하지 않는다.

## 9.7 조회 시점 조합 정책

1. TMAP 응답을 내부 Route 모델로 정규화하고 이 정규화 결과만 30분 미만 임시 캐시에 저장한다.
2. `POST /routes`와 `GET /routes/{routeId}` 응답 직전에 현재 `ACTIVE` template을 목적지 scope와 노출 기간으로 DB 조회한다.
3. 저장된 trigger rule을 summary와 segment에 평가한다.
4. 사용자 언어의 운영진 번역을 선택해 `summary.horiTips[]` 또는 `segments[].horiTips[]`에 조합한다.
5. 동일 template은 한 route에서 한 번만 노출하고 `priority DESC, code ASC` 순으로 정렬한다.

따라서 운영진이 팁을 비활성화하면 이미 발급된 `routeId`라도 다음 상세 조회부터 제외된다. 팁 조합 결과를 Route 캐시에 저장하지 않는다.

---

## 10. Buddy Connect / 쪽지

## 10.1 buddy_profiles

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| profile_image_url | 프로필 이미지 |
| nickname | 닉네임 |
| nationality | 국적 |
| korean_level | 한국어 수준 |
| bio | 한 줄 소개 |
| profile_public | 프로필 공개 여부 |
| sns_public | SNS 공개 여부 |
| allows_messages | 쪽지 허용 여부 |
| created_at/updated_at | 생성/수정 |

## 10.2 buddy_profile_languages

| 컬럼 | 설명 |
|---|---|
| profile_id | FK |
| language_code | 사용 가능 언어 |

## 10.3 buddy_profile_styles

| 컬럼 | 설명 |
|---|---|
| profile_id | FK |
| buddy_style | TRADITIONAL_CULTURE/CAFE_TOUR/FOODIE/PHOTOGRAPHY/HANOK_EXPERIENCE/QUIET_TRAVEL |

## 10.4 buddy_social_links

| 컬럼 | 설명 |
|---|---|
| id | PK |
| profile_id | FK |
| type | INSTAGRAM/KAKAOTALK/THREADS/TIKTOK/ETC |
| value | 연락 ID |
| active | 사용 여부 |

## 10.5 buddy_message_threads

두 Buddy 프로필이 특정 여행지를 주제로 주고받는 비실시간 쪽지 스레드다.

| 컬럼 | 설명 |
|---|---|
| id | opaque thread ID |
| place_id | 관련 여행지 FK |
| profile_low_id / profile_high_id | 정렬된 참여 프로필 FK |
| last_message_id | nullable 마지막 메시지 FK |
| created_at / updated_at | 생성/갱신 시각 |

`place_id + profile_low_id + profile_high_id`는 unique로 두어 같은 두 사용자와 장소에 중복 스레드가 생기지 않게 한다.

## 10.6 buddy_messages

| 컬럼 | 설명 |
|---|---|
| id | PK |
| thread_id | FK |
| sender_profile_id | 발신자 |
| receiver_profile_id | 수신자 |
| content | 1000자 이내 |
| sent_at | 전송 시각 |
| read_at | 읽은 시각 |
| deleted_by_sender_at | 발신자 삭제 |
| deleted_by_receiver_at | 수신자 삭제 |

## 10.7 message_idempotency_keys

| 컬럼 | 설명 |
|---|---|
| sender_profile_id | FK |
| idempotency_key | 클라이언트 요청 key |
| request_hash | 수신자/장소/본문 hash |
| message_id | 생성된 메시지 FK |
| created_at / expires_at | 생성/보관 만료 시각 |

`sender_profile_id + idempotency_key`는 unique다. 같은 key에 다른 request hash가 오면 409를 반환한다.

## 10.8 user_push_tokens

| 컬럼 | 설명 |
|---|---|
| id | PK |
| user_id | FK |
| provider | EXPO |
| token | Expo push token |
| active | 사용 여부 |
| updated_at | 갱신 시각 |

## 10.9 buddy_blocks / buddy_reports

MVP 안전장치로 권장.

- blocker_user_id
- blocked_user_id
- reason
- created_at

신고는 사유/메시지/대상 프로필을 저장한다.

---

## 11. 외부 API 호출 로그

## 11.1 open_api_call_logs

| 컬럼 | 설명 |
|---|---|
| id | PK |
| provider | KTO/TMAP/KAKAO/AI |
| api_name | 국문관광정보/영문관광정보 등 |
| operation | areaBasedList2 등 |
| endpoint | 호출 endpoint |
| request_started_at | 요청 시작 |
| response_received_at | 응답 수신 |
| duration_ms | 소요 시간 |
| success | 성공 여부 |
| http_status | HTTP status |
| request_params_masked | serviceKey 제거/마스킹 파라미터 |
| response_summary | totalCount, resultCode 등 요약 |
| external_result_code | 외부 API resultCode |
| item_count | 응답 item 수 |
| response_bytes | 응답 byte 수 |
| error_message | 오류 메시지 |
| related_job_id | batch job id |
| related_job_item_id | nullable batch job item id |

### 제약/주의

- `serviceKey`, `appKey`, OAuth token, idToken 등 secret은 저장하지 않는다.
- `request_params_masked`는 JSON으로 저장하되 key 단위 마스킹을 적용한다.
- 동일 batch item에서 여러 operation을 호출할 수 있으므로 `related_job_id`와 `batch_job_items.id`를 함께 둘 수 있다.
- TMAP은 경로 원본/정규화 결과를 24시간 미만 임시 캐시에만 두고, `route_query_logs`와 `open_api_call_logs`에는 결과를 재현하지 않는 호출 메타데이터만 남긴다.
- 호출 로그는 append-only이며 관리자 API에서 수정/삭제하지 않는다.

## 11.2 open_api_raw_snapshots

KTO 성공 응답을 가공하기 전에 원천 snapshot을 immutable 파일로 저장한다. 큰 원문은 DB JSON이 아니라 private object storage에 gzip으로 저장한다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| call_log_id | FK |
| provider | KTO/TMAP/... |
| api_name | API 구분 |
| operation | operation |
| storage_key | private object storage key |
| storage_format | JSON_GZIP/XML_GZIP |
| content_type | 원본 content type |
| raw_content_sha256 | 압축 해제 원문 SHA-256 |
| stored_object_sha256 | 실제 gzip 객체 SHA-256 |
| byte_size | 원문 byte 크기 |
| compressed_byte_size | 저장 파일 byte 크기 |
| item_count | 원천 item 수 |
| captured_at | 수집 시각 |
| retention_class | COMPETITION_EVIDENCE/DEBUG_TEMPORARY/PROVIDER_RESTRICTED |
| retention_until | 만료 시각. 증빙 KTO는 정책에 따라 null 가능 |
| immutable | 항상 true |

## 11.3 tour_api_sync_cursors

증분 동기화 상태를 저장한다. `areaBasedSyncList2`와 상세 보강 batch가 중단되어도 이어서 처리하기 위한 테이블이다.

| 컬럼 | 설명 |
|---|---|
| id | PK |
| provider | KTO |
| api_name | 국문관광정보/영문관광정보/관광사진/연관관광지 |
| operation | areaBasedSyncList2 등 |
| cursor_type | MODIFIED_TIME/PAGE/DATE_RANGE/MANUAL |
| cursor_value | 마지막 처리 기준값 |
| last_success_at | 마지막 성공 시각 |
| last_failure_at | 마지막 실패 시각 |
| failure_count | 연속 실패 수 |
| enabled | 사용 여부 |

## 11.4 evidence_bundles

| 컬럼 | 설명 |
|---|---|
| id | opaque bundle ID |
| name | 증빙 번들명 |
| status | QUEUED/RUNNING/COMPLETED/FAILED |
| filter_json | 기간/provider/operation/표본 조건 |
| created_by_user_id | 생성 관리자 |
| created_at/finished_at | 생성 시각 |
| storage_key | 완성 ZIP private key |
| sha256 | ZIP SHA-256 |
| byte_size | ZIP 크기 |
| call_count | 포함 호출 수 |
| raw_snapshot_count | 포함 원천 수 |
| error_message | 실패 사유 |

## 11.5 evidence_bundle_items

| 컬럼 | 설명 |
|---|---|
| bundle_id | FK |
| call_log_id | FK |
| raw_snapshot_id | nullable FK |
| included | 포함 여부 |
| exclusion_reason | TMAP_RETENTION 등 제외 사유 |

## 11.6 admin_audit_logs

| 컬럼 | 설명 |
|---|---|
| id | PK |
| actor_user_id | 관리자 user ID |
| role | 실행 당시 role |
| action | 다운로드/배치/재시도/cursor 초기화 등 |
| target_type | SNAPSHOT/BUNDLE/BATCH/CURSOR/ROLE |
| target_id | 대상 ID |
| reason | 수동 실행 사유 |
| request_metadata | IP/user-agent/traceId 등 |
| created_at | 실행 시각 |

관리자 감사 로그는 append-only다.

---

## 12. Batch Job 관리

## 12.1 batch_jobs

| 컬럼 | 설명 |
|---|---|
| id | PK |
| job_type | KTO_DAILY_SYNC/KTO_DETAIL_ENRICHMENT/KTO_EN_SYNC/AI_TRANSLATION 등 |
| status | PENDING/RUNNING/COMPLETED/FAILED/PARTIAL_FAILED |
| started_at/finished_at | 시작/종료 |
| processed_count | 처리 수 |
| success_count | 성공 수 |
| failure_count | 실패 수 |
| message | 요약 메시지 |
| trigger_source | SCHEDULED/ADMIN_MANUAL/RETRY |
| triggered_by_user_id | 수동 실행 관리자. 자동 실행은 null |
| parent_job_id | 재시도 원본 job |
| parameters_json | 마스킹된 실행 파라미터 |

## 12.2 batch_job_items

| 컬럼 | 설명 |
|---|---|
| id | PK |
| batch_job_id | FK |
| target_type | API_PAGE/PLACE/IMAGE/TRANSLATION |
| target_id | 대상 ID |
| status | PENDING/COMPLETED/FAILED |
| error_message | 오류 |

---

## 13. 우선순위 높은 테이블

MVP 1차 구현에 필요한 최소 테이블:

1. users
2. social_accounts / auth_sessions
3. terms / term_versions / user_term_agreements
4. user_travel_styles
5. user_locations
6. onboarding_preference_candidate_sets / onboarding_preference_candidates
7. user_onboarding_place_selections
8. user_preference_tags
9. service_regions / administrative_regions
10. tour_api_category_codes / tour_api_lcls_codes
11. places
12. place_localizations / place_source_records / place_source_matches
13. place_images
14. place_tags / tags / place_style_mappings
15. festival_series / place_event_occurrences
16. user_saved_places
17. user_place_events
18. recommendation_decks / recommendation_deck_items
19. user_place_recommendation_states
20. open_api_call_logs / open_api_raw_snapshots
21. tour_api_sync_cursors
22. batch_jobs / batch_job_items
23. user_roles / admin_audit_logs
24. evidence_bundles / evidence_bundle_items

MVP 2차:

1. place_ai_descriptions
2. place_relations
3. route_query_logs
4. buddy_tip_templates / place_scopes / translations / trigger_rules / exposures
5. buddy_profiles / buddy_profile_languages / buddy_profile_styles
6. buddy_message_threads / buddy_messages / message_idempotency_keys
7. buddy_blocks / buddy_reports
8. user_push_tokens
9. place_pet_infos
