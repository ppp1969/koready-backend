# 프로필 설정 API

## 프론트 호출 흐름

1. 프로필 화면 진입 시 `GET /api/v1/profile-options`를 호출한다.
2. 응답의 `displayOrder` 순서대로 언어, 한국어 수준, 여행 스타일, Buddy 관심사,
   SNS 플랫폼 선택지를 표시한다.
3. 내 기존값은 `GET /api/v1/users/me/buddy-profile`로 조회한다.
4. 저장할 때 `PUT /api/v1/users/me/buddy-profile`로 form 전체를 보낸다.
5. 저장 응답을 화면 상태로 사용하거나 GET을 한 번 더 호출해 DB 반영값을 확인한다.

선택지 API는 로그인 전에도 조회할 수 있다. 프론트는 enum 코드나 한글·영문 이름을
별도 상수로 복제하지 않는다.

## 언어 구분

`availableLanguages`는 사용자가 다른 사람과 대화할 수 있는 언어다. 장소 설명을
한국어 또는 영어로 보여주는 콘텐츠 표시 언어와는 별개다.

지원 코드는 다음과 같다.

| code | 한국어 | English |
|---|---|---|
| KO | 한국어 | Korean |
| EN | 영어 | English |
| ZH | 중국어 | Chinese |
| JA | 일본어 | Japanese |
| VI | 베트남어 | Vietnamese |
| TH | 태국어 | Thai |
| MN | 몽골어 | Mongolian |
| RU | 러시아어 | Russian |
| ID | 인도네시아어 | Indonesian |
| ES | 스페인어 | Spanish |
| FR | 프랑스어 | French |
| DE | 독일어 | German |
| AR | 아랍어 | Arabic |

프로필에는 1개 이상 5개 이하를 중복 없이 저장한다. 기존 `KO`, `EN` 데이터는
migration 이후에도 그대로 유지된다.

## 한국어 수준

한국어 수준은 다음 세 단계만 저장한다.

- `BEGINNER`: 초급
- `INTERMEDIATE`: 중급
- `ADVANCED`: 고급

`NONE`, `NATIVE` 같은 별도 값은 사용하지 않는다.

## 여행 스타일

`travelStyles`는 추천에 실제로 사용하는 사용자 여행 스타일이다. 온보딩에서 처음
고른 값과 같은 `user_travel_styles` 데이터를 사용하므로, 프로필 PUT에서 저장하면
기존 값을 1개 이상 4개 이하의 새 목록으로 전체 교체한다.

Buddy 프로필에 보이는 관심사인 `buddyStyles`는 메이트 탐색용 표현이며
`travelStyles`와 다른 enum이다. 프론트에서 둘을 자동 변환하지 않는다.

## SNS 계정 ID

`socialLinks[].value`에는 SNS 전체 URL이 아니라 사용자가 입력한 계정 ID 또는 핸들을
저장한다. 예시는 `@emma.travels` 또는 KakaoTalk ID다. 한 프로필에 같은
`SocialLinkType`을 두 번 저장할 수 없다.

공개 프로필 응답은 `snsPublic=false`이면 `socialLinks=[]`를 반환한다. 현재 MVP는
외부 URL을 백엔드에서 조합하지 않으므로 `url=null`이고, 프론트는
`displayValue`를 계정 ID로 표시한다.

## 전체 교체 요청 예시

```json
{
  "profileImageUrl": "https://cdn.example.com/emma.jpg",
  "nickname": "Emma",
  "nationality": "France",
  "availableLanguages": ["EN", "VI"],
  "koreanLevel": "INTERMEDIATE",
  "travelStyles": ["LOCAL_FOOD", "CULTURE_EXPERIENCE"],
  "bio": "Local food fan",
  "buddyStyles": ["FOODIE", "PHOTOGRAPHY"],
  "socialLinks": [
    {
      "type": "INSTAGRAM",
      "value": "@emma.travels"
    }
  ],
  "profilePublic": true,
  "snsPublic": true,
  "allowsMessages": true
}
```
