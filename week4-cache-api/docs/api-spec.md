# 상품 API 명세 (v1)

- Base URL: `http://localhost:8080/api/v1`
- 요청·응답 형식: `application/json`
- 아래 응답 예시는 local 서버를 실제로 실행해서 받은 값입니다.

## 설계 원칙
| 원칙 | 적용 |
|---|---|
| 리소스 네이밍 | URL은 **복수형 명사**(`/items`)로 쓰고 동사는 넣지 않습니다. 행위는 HTTP 메서드로 표현합니다 |
| 버저닝 | URL 경로 버저닝(`/api/v1`)을 씁니다. 응답 형식이 호환되지 않게 바뀌면 `/v2`를 새로 엽니다 |
| 상태 코드 | 200 조회·수정 / 201 생성(+`Location`) / 204 삭제 / 400 잘못된 요청 / 404 없음 / 405 메서드 불가 / 500 서버 오류 |
| 공통 응답 | 성공과 실패 모두 `success` 필드로 구분합니다. 단, 204는 본문이 없습니다 |
| 페이징 | `page`(0부터), `size`(1~50), 최신 등록순 고정 |

## 공통 응답 형식
```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "error": { "code": "ITEM_NOT_FOUND", "message": "상품을 찾을 수 없습니다. id=99" } }
```

## 에러 코드
| code | HTTP | 발생 상황 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 요청 값 검증 실패 (이름 공백, 가격 음수, size > 50 등) |
| `INVALID_TYPE` | 400 | 경로·파라미터 타입 불일치 (`/items/abc`) |
| `INVALID_JSON` | 400 | JSON 문법 오류 |
| `ITEM_NOT_FOUND` | 404 | 없는 상품 id |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 메서드 (`PATCH` 등) |
| `INTERNAL_ERROR` | 500 | 예상하지 못한 서버 오류 (상세 내용은 서버 로그에만 남김) |

---

## 1. 상품 등록
`POST /api/v1/items`

| 필드 | 타입 | 규칙 |
|---|---|---|
| name | String | 필수, 공백 불가 |
| price | int | 0 이상 |
| stockQuantity | int | 0 이상 |

```json
// 요청
{ "name": "USB 허브", "price": 25000, "stockQuantity": 20 }

// 응답 201 Created, Location: /api/v1/items/4
{ "success": true, "data": { "id": 4, "name": "USB 허브", "price": 25000, "stockQuantity": 20,
  "createdAt": "2026-10-08T10:30:31.840377", "updatedAt": "2026-10-08T10:30:31.840377" } }
```
캐시: `itemPage` 전체를 비웁니다.

## 2. 상품 목록 조회 (페이징)
`GET /api/v1/items?page=0&size=2`

| 파라미터 | 기본값 | 규칙 |
|---|---|---|
| page | 0 | 0 이상 |
| size | 10 | 1 ~ 50 |

```json
// 응답 200
{ "success": true, "data": {
    "content": [
      { "id": 3, "name": "27인치 모니터", "price": 289000, "stockQuantity": 10, "createdAt": "...", "updatedAt": "..." },
      { "id": 2, "name": "기계식 마우스", "price": 39000, "stockQuantity": 50, "createdAt": "...", "updatedAt": "..." }
    ],
    "page": 0, "size": 2, "totalElements": 3, "totalPages": 2, "hasNext": true } }

// size=1000 → 400
{ "success": false, "error": { "code": "INVALID_REQUEST", "message": "size 는 50 이하여야 합니다." } }
```
캐시: `itemPage::{page}:{size}` (예: `itemPage::0:2`)

## 3. 상품 단건 조회
`GET /api/v1/items/{id}`
```json
// 응답 200
{ "success": true, "data": { "id": 1, "name": "무선 키보드", "price": 59000, "stockQuantity": 30, "createdAt": "...", "updatedAt": "..." } }

// 없는 id → 404
{ "success": false, "error": { "code": "ITEM_NOT_FOUND", "message": "상품을 찾을 수 없습니다. id=99" } }
```
캐시: `item::{id}`

## 4. 상품 수정
`PUT /api/v1/items/{id}` (모든 필드를 보내는 전체 수정, 규칙은 등록과 같음)
```json
// 요청
{ "name": "무선 키보드(할인)", "price": 49000, "stockQuantity": 30 }

// 응답 200 (updatedAt이 수정 시각으로 바뀜)
{ "success": true, "data": { "id": 1, "name": "무선 키보드(할인)", "price": 49000, "stockQuantity": 30,
  "createdAt": "2026-10-08T10:30:29.790662", "updatedAt": "2026-10-08T10:30:31.857532" } }
```
캐시: `item::{id}`를 새 값으로 덮어쓰고(`@CachePut`), `itemPage` 전체를 비웁니다.

## 5. 상품 삭제
`DELETE /api/v1/items/{id}` → `204 No Content` (본문 없음), 없는 id면 404
캐시: `item::{id}`와 `itemPage` 전체를 비웁니다.

---

## 헬스체크 (Actuator)
Actuator 응답은 공통 응답 형식으로 감싸지 **않습니다.** 로드밸런서나 모니터링 도구가 표준 형식(`{"status":"UP"}`)을 기대하기 때문입니다.

| URL | local | prod |
|---|---|---|
| `/actuator/health` | 상세 정보 포함 | `{"status":"UP"}`만 |
| `/actuator/health/liveness` | ✅ | ✅ |
| `/actuator/health/readiness` | ✅ | ✅ (db, redis 포함) |
