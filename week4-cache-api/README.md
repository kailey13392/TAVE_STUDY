# [4주차] 캐싱 적용 상품 API 서버

2주차 쇼핑몰 도메인의 **상품(Item)** 으로 REST API 5개를 만들고, 아래 내용을 적용했습니다.

- **캐싱**: `@Cacheable` / `@CachePut` / `@CacheEvict`
- **프로파일 분리**: local은 H2 DB와 Caffeine 캐시, prod는 MySQL과 Redis 캐시
- **Actuator 헬스체크**: 프로파일마다 노출 범위를 다르게 설정

> 프로파일을 바꿔도 **자바 코드는 한 줄도 바뀌지 않습니다.** yml의 `spring.cache.type`만 바뀌고,
> 실제 CacheManager는 스프링 부트 **자동 구성(Auto Configuration)** 이 골라서 만들어 줍니다.
> 원리는 [`docs/auto-configuration.md`](docs/auto-configuration.md)에 정리했습니다.

## 기술 스택
Java 17 · Spring Boot 4.1.1 · Spring Data JPA · Spring Cache (Caffeine / Redis) · Actuator · H2 / MySQL

## API 목록

| Method | URL | 설명 | 응답 | 캐시 |
|---|---|---|---|---|
| POST | `/api/items` | 상품 등록 | 201 + `Location` | `itemList` 비움 |
| GET | `/api/items` | 상품 목록 | 200 | `itemList`에 저장(`@Cacheable`) |
| GET | `/api/items/{id}` | 상품 단건 | 200 / 404 | `item::{id}`에 저장(`@Cacheable`) |
| PUT | `/api/items/{id}` | 상품 수정 | 200 / 404 | `item::{id}` 갱신(`@CachePut`), `itemList` 비움 |
| DELETE | `/api/items/{id}` | 상품 삭제 | 204 / 404 | `item::{id}`와 `itemList` 둘 다 비움 |

에러 응답 형식: `{"code": "ITEM_NOT_FOUND", "message": "상품을 찾을 수 없습니다. id=99"}`

## 캐시 설계

```
GET /api/items/1  ──▶ [캐시 프록시] ── 캐시에 있음? ── 예 ──▶ 바로 반환 (DB 안 감)
                                        └ 아니오 ─▶ ItemService.getItem() ─▶ SELECT ─▶ 캐시에 저장 후 반환
```

- **캐시에 넣는 값은 엔티티가 아니라 DTO(`ItemResponse`)입니다.** 엔티티는 영속성 컨텍스트와 지연 로딩 프록시에 묶여 있어서 외부 캐시로 직렬화하기 어렵습니다.
- `ItemResponse`는 `Serializable`을 구현합니다. Redis 캐시가 기본으로 JDK 직렬화를 쓰기 때문입니다. local(Caffeine)에서는 없어도 돌아가서 놓치기 쉬운 부분입니다.
- 목록은 상품 하나만 바뀌어도 내용이 달라지므로, 등록·수정·삭제 때마다 `itemList`를 통째로 비웁니다(`allEntries = true`).
- 수정할 때는 단건 캐시를 지우지 않고 `@CachePut`으로 **덮어씁니다.** 그래서 수정 직후 조회도 DB를 거치지 않습니다.
- 주의할 점: 캐시는 프록시로 동작하기 때문에, 같은 클래스 안에서 `this.getItem()`처럼 호출하면 캐시가 적용되지 않습니다.

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
curl localhost:8080/api/items/1   # 콘솔에 Hibernate: select ... 출력
curl localhost:8080/api/items/1   # 이번에는 select가 찍히지 않음 → 캐시 히트
curl localhost:8080/actuator/caches
```

### 헬스체크
```bash
curl localhost:8080/actuator/health
```
local 실행 결과(일부):
```json
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"H2"}},"diskSpace":{"status":"UP"},"ping":{"status":"UP"}, ...}}
```
prod에서는 `{"status":"UP"}`만 응답하고, `/actuator/health/liveness`와 `/actuator/health/readiness`도 함께 열립니다. readiness 판단에는 db와 redis 상태가 포함됩니다.

> local에서는 `management.health.redis.enabled: false`로 설정했습니다. Redis 의존성이 클래스패스에 있으면 Redis 헬스체크가 **자동으로** 추가되는데, local에는 Redis 서버가 없어서 그대로 두면 health가 DOWN으로 나오기 때문입니다.

## 테스트 (`./gradlew test`, 10개 통과)

| 테스트 | 검증 내용 |
|---|---|
| `ItemCacheTest` | Hibernate `Statistics`로 **실행된 쿼리 수**를 세서 캐시를 검증합니다(2주차와 같은 방식). 단건·목록은 3번 조회해도 쿼리 1번, 수정 직후 조회는 쿼리 0번, 삭제 후 조회는 404, local에서는 `CaffeineCacheManager`가 생성되는지 확인 |
| `ItemApiTest` | MockMvc로 API 5개의 CRUD 흐름, 400 검증 에러, `/actuator/health`가 UP인지 확인 |
| `ProdProfileTest` | prod 프로파일에서 `RedisCacheManager`가 자동 구성되는지, 캐시 값이 Redis 기본 직렬화(JDK)를 통과하는지 확인 |

## 패키지 구조
```
com.tave_2.cacheapi
├── CacheApiApplication
├── config/CacheConfig            @EnableCaching, 캐시 이름 상수
├── controller/ItemController     API 5개
├── service/ItemService           ★ 캐시 애노테이션
├── domain/Item
├── repository/ItemRepository
├── dto/                          ItemResponse(캐시 값), Create/UpdateRequest
├── exception/                    404/400 공통 처리
└── init/LocalDataInitializer     local 전용 샘플 데이터
```
