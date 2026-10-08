# [4주차] 캐싱 적용 상품 API 서버

2주차 쇼핑몰 도메인의 **상품(Item)** 으로 REST API 5개를 만들고, 아래 내용을 적용했습니다.

- **캐싱**: `@Cacheable` / `@CachePut` / `@CacheEvict` (페이징 목록 포함)
- **프로파일 분리**: local은 H2 DB와 Caffeine 캐시, prod는 MySQL과 Redis 캐시
- **Actuator 헬스체크**: 프로파일마다 노출 범위를 다르게 설정
- **실무 디테일**: 페이지네이션, 공통 응답 형식, 에러 코드 기반 예외 처리, `/api/v1` 버저닝, 생성·수정 시간 자동 기록

> 프로파일을 바꿔도 **자바 코드는 한 줄도 바뀌지 않습니다.** yml의 `spring.cache.type`만 바뀌고,
> 실제 CacheManager는 스프링 부트 **자동 구성(Auto Configuration)** 이 골라서 만들어 줍니다.

📄 문서
- [`docs/api-spec.md`](docs/api-spec.md): API 명세 (설계 원칙, 요청·응답 예시, 에러 코드)
- [`docs/auto-configuration.md`](docs/auto-configuration.md): 자동 구성 동작 원리 (`/actuator/conditions` 실제 결과로 추적)
- [`http/items.http`](http/items.http): API 연동 실습 파일 (IntelliJ에서 ▶ 눌러 바로 호출)

## 기술 스택
Java 17 · Spring Boot 4.1.1 · Spring Data JPA · Spring Cache (Caffeine / Redis) · Actuator · H2 / MySQL

## API 목록

| Method | URL | 설명 | 응답 | 캐시 |
|---|---|---|---|---|
| POST | `/api/v1/items` | 상품 등록 | 201 + `Location` | `itemPage` 비움 |
| GET | `/api/v1/items?page=0&size=10` | 상품 목록 (최신순, 페이징) | 200 / 400 | `itemPage::{page}:{size}`에 저장 |
| GET | `/api/v1/items/{id}` | 상품 단건 | 200 / 404 | `item::{id}`에 저장 |
| PUT | `/api/v1/items/{id}` | 상품 수정 | 200 / 404 | `item::{id}` 덮어쓰기(`@CachePut`), `itemPage` 비움 |
| DELETE | `/api/v1/items/{id}` | 상품 삭제 | 204 / 404 | `item::{id}`와 `itemPage` 비움 |

공통 응답 형식:
```json
{ "success": true,  "data": { ... } }
{ "success": false, "error": { "code": "ITEM_NOT_FOUND", "message": "상품을 찾을 수 없습니다. id=99" } }
```

## 캐시 설계

```
GET /api/v1/items/1 ──▶ [캐시 프록시] ── 캐시에 있음? ── 예 ──▶ 바로 반환 (DB 안 감)
                                           └ 아니오 ─▶ ItemService.getItem() ─▶ SELECT ─▶ 캐시에 저장 후 반환
```

- **캐시에 넣는 값은 엔티티가 아니라 DTO**(`ItemResponse`, `PageResponse`)입니다. 엔티티는 영속성 컨텍스트와 지연 로딩 프록시에 묶여 있어서 외부 캐시로 직렬화하기 어렵습니다.
- DTO는 `Serializable`을 구현합니다. Redis 캐시가 기본으로 JDK 직렬화를 쓰기 때문입니다. local(Caffeine)에서는 없어도 돌아가서 놓치기 쉬운 부분입니다.
- **공통 응답 래퍼(`ApiResponse`)는 캐시하지 않습니다.** 컨트롤러에서 감싸기 때문에 응답 형식을 바꿔도 캐시 데이터는 영향을 받지 않습니다.
- 수정할 때는 단건 캐시를 지우지 않고 `@CachePut`으로 **덮어씁니다.** 그래서 수정 직후 조회도 DB를 거치지 않습니다.
  - 이때 `updatedAt`은 UPDATE 쿼리가 나가기 직전에 채워집니다. 그래서 `flush()`를 먼저 호출한 뒤 응답(=캐시 값)을 만듭니다. 그러지 않으면 옛날 수정 시간이 캐시에 남습니다.
- 주의할 점: 캐시는 프록시로 동작하기 때문에, 같은 클래스 안에서 `this.getItem()`처럼 호출하면 캐시가 적용되지 않습니다.

### 페이징과 캐시를 함께 쓸 때 신경 쓴 점
| 고민 | 선택 |
|---|---|
| 캐시 키 | `page:size`를 함께 키로 씁니다. page만 쓰면 size=10과 size=20 결과가 섞입니다 |
| 키 폭증 | `size`를 최대 50으로 제한합니다. 아무 값이나 받으면 캐시 키가 끝없이 늘어날 수 있습니다 |
| 정렬 | 서버에서 최신순으로 고정합니다(`createdAt desc, id desc`). 정렬까지 열면 키 조합이 너무 많아집니다 |
| 무효화 | 상품 하나만 바뀌어도 모든 페이지가 밀리므로, 등록·수정·삭제 때 `itemPage` 전체를 비웁니다 |
| 캐시 값 | 스프링의 `Page`가 아니라 직접 만든 `PageResponse`를 씁니다. JSON 형식이 안정적이고 직렬화를 직접 관리할 수 있습니다 |

## 프로파일

| | local (기본값) | prod |
|---|---|---|
| DB | H2 메모리 DB, `ddl-auto: create` | MySQL, `ddl-auto: validate` |
| 캐시 | `spring.cache.type: caffeine` (서버 메모리) | `spring.cache.type: redis` (TTL 10분) |
| 민감 정보 | 파일에 직접 작성(로컬용) | `${DB_PASSWORD}` 같은 **환경 변수**로 주입 |
| Actuator 노출 | health, info, caches, conditions, beans, metrics | **health만** |
| 헬스 상세 | `show-details: always` | `show-details: never`, liveness/readiness 프로브 |
| 샘플 데이터 | `LocalDataInitializer`(`@Profile("local")`)로 3건 | 넣지 않음 |

설정 파일 구성: `application.yml`(공통) + `application-local.yml` / `application-prod.yml`(프로파일 전용, 같은 키는 이쪽 값이 우선)

## 실행 방법

```bash
# local: 아무것도 설치하지 않아도 됩니다 (JDK 17은 Gradle이 자동으로 내려받습니다)
./gradlew bootRun

# prod: MySQL과 Redis를 Docker로 띄운 뒤 실행합니다
docker compose up -d
DB_PASSWORD=cacheapi ./gradlew bootRun --args='--spring.profiles.active=prod'
```

### 캐시 동작 눈으로 확인하기 (local)
```bash
curl localhost:8080/api/v1/items/1   # 콘솔에 Hibernate: select ... 출력
curl localhost:8080/api/v1/items/1   # 이번에는 select가 찍히지 않음 → 캐시 히트
curl localhost:8080/actuator/caches
```
또는 `http/items.http`를 IntelliJ에서 열고 ▶ 버튼을 누르면 됩니다.

### 헬스체크
```bash
curl localhost:8080/actuator/health
```
local 실행 결과(일부):
```json
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"H2"}},"diskSpace":{"status":"UP"},"ping":{"status":"UP"}, ...}}
```
prod에서는 `{"status":"UP"}`만 응답하고, `/actuator/health/liveness`와 `/actuator/health/readiness`도 함께 열립니다. readiness 판단에는 db와 redis 상태가 포함됩니다.
Actuator 응답은 공통 응답 형식으로 감싸지 않습니다. 로드밸런서가 표준 형식을 기대하기 때문입니다.

> local에서는 `management.health.redis.enabled: false`로 설정했습니다. Redis 의존성이 클래스패스에 있으면 Redis 헬스체크가 **자동으로** 추가되는데, local에는 Redis 서버가 없어서 그대로 두면 health가 DOWN으로 나오기 때문입니다.

## 테스트 (`./gradlew test`, 16개 통과)

| 테스트 | 검증 내용 |
|---|---|
| `ItemCacheTest` (7) | Hibernate `Statistics`로 **실행된 쿼리 수**를 세서 캐시를 검증합니다(2주차와 같은 방식). 단건은 3번 조회해도 쿼리 1번 / 같은 `page:size`면 캐시 히트, 다른 size면 따로 캐시 / 등록하면 모든 페이지 캐시 삭제 / 최신순 정렬 / 수정 직후 조회는 쿼리 0번이고 `updatedAt`도 갱신됨 / 삭제 후 조회는 404 / local에서는 `CaffeineCacheManager` 생성 |
| `ItemApiTest` (7) | MockMvc로 CRUD 흐름과 공통 응답 형식 확인. 에러 케이스: 본문 검증 400, size 상한 400, 타입 불일치 400, 깨진 JSON 400, PATCH 405. `/actuator/health`가 UP인지 확인 |
| `ProdProfileTest` (2) | prod 프로파일에서 `RedisCacheManager`가 자동 구성되는지, `ItemResponse`와 `PageResponse`가 Redis 기본 직렬화(JDK)를 통과하는지 확인 |

## 패키지 구조
```
com.tave_2.cacheapi
├── CacheApiApplication
├── global                              여러 도메인이 함께 쓰는 것
│   ├── common      ApiResponse, PageResponse      공통 응답 / 페이징 응답
│   ├── config      CacheConfig, JpaAuditingConfig, LocalDataInitializer
│   ├── entity      BaseTimeEntity                 createdAt / updatedAt 자동 기록
│   └── exception   ErrorCode, BusinessException, ErrorResponse, GlobalExceptionHandler
└── domain
    └── item                            상품 도메인
        ├── controller  ItemController  API 5개 (/api/v1/items)
        ├── service     ItemService     ★ 캐시 애노테이션
        ├── repository  ItemRepository
        ├── entity      Item (extends BaseTimeEntity)
        ├── dto         ItemResponse(캐시 값), ItemCreateRequest, ItemUpdateRequest
        └── exception   ItemNotFoundException (extends BusinessException)
```
도메인이 늘어나면 `domain/` 아래에 폴더를 하나 더 만들면 됩니다. 시간 기록, 응답 형식, 예외 처리는 `global`의 것을 그대로 씁니다.
