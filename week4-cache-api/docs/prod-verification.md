# prod 프로파일 실측 검증 (Redis 캐싱 · Actuator)

> 2026-10-08, 커밋 `fa2be3c` 기준.
> 단위 테스트 결과나 README 내용을 옮긴 것이 아니라, **실제 MySQL·Redis에 붙인 prod 서버**에 직접 요청을 보내 확인한 결과입니다.
> 응답 본문, Redis 명령 결과, SQL 로그는 실행 화면에서 그대로 발췌했습니다. 시각 같은 값은 실행할 때마다 달라집니다.

## 검증 환경

| 항목 | 값 |
|---|---|
| 인프라 | `docker compose up -d` → `mysql:8.4`, `redis:7` (이 폴더의 `docker-compose.yml`) |
| 실행 | `DB_PASSWORD=<docker-compose.yml의 MYSQL_PASSWORD> ./gradlew bootRun --args='--spring.profiles.active=prod --spring.jpa.show-sql=true'` |
| 프로파일 확인 | 시작 로그: `The following 1 profile is active: "prod"` |
| SQL 로그 | `--spring.jpa.show-sql=true`는 **이번 실행에만** 붙였습니다. `application-prod.yml`은 수정하지 않았습니다 |
| Redis | `redis-cli PING` → `PONG`, 시작할 때 `DBSIZE` = 0 |
| MySQL | `docker/mysql/init.sql`로 `item` 테이블 생성, 시작할 때 0건 |

검증할 때마다 직접 등록한 상품의 ID만 썼고, 끝난 뒤 그 상품은 삭제했습니다. `FLUSHALL`은 쓰지 않았습니다. 검증 후 DB는 0건, Redis 키는 0개입니다.

## 결과 요약

| # | 항목 | 결과 |
|---|---|---|
| 1 | MySQL, Redis 실행 / `PING` → `PONG` / prod 프로파일 | ✅ 성공 |
| 2 | 첫 조회 후 단건 캐시 키 생성 | ✅ 성공 |
| 3 | 두 번째 조회에서 SELECT 생략 | ✅ 성공 |
| 4 | key-prefix가 붙은 실제 키 (SCAN) | ✅ 성공 `cache-api::item::{id}` |
| 5 | TTL 10분 | ✅ 성공 (첫 조회 직후 `TTL` = 598) |
| 6 | 값이 JDK 직렬화 형식 (JSON 아님) | ✅ 성공 |
| 7 | 목록 캐시 생성과 재조회 시 캐시 히트 | ✅ 성공 |
| 8 | 수정하면 단건 캐시 갱신(`@CachePut`), 목록 캐시 삭제 | ✅ 성공 |
| 9 | 수정 후 재조회: 최신 값 + SELECT 생략 | ✅ 성공 |
| 10 | 삭제하면 단건 캐시 제거, 재조회는 404, 404는 캐시 안 됨 | ✅ 성공 |
| 11 | `/actuator/health`, `liveness`, `readiness` | ✅ 성공 (모두 200 UP) |
| 12 | prod에서 health 상세 숨김 | ✅ 성공 |
| 13 | 노출하지 않은 엔드포인트의 응답 코드 | ❌ **실패**: 404가 아니라 500 ([알려진 문제](#알려진-문제) 참고) |
| – | 장애 실험 (Redis·DB를 내린 상태) | ⏭️ 이번 범위에서 제외 |

---

## 1. 단건 캐시 (`@Cacheable`)

**등록**: `POST /api/v1/items` → `201`, `Location: /api/v1/items/1`
```
SQL: insert into item (created_at,name,price,stock_quantity,updated_at) values (?,?,?,?,?)
Redis SCAN: (비어 있음)   ← 등록만으로는 단건 캐시가 생기지 않음
```

**첫 조회**: `GET /api/v1/items/1` → 200
```
SQL: select i1_0.item_id,i1_0.created_at,i1_0.name,i1_0.price,i1_0.stock_quantity,i1_0.updated_at
     from item i1_0 where i1_0.item_id=?
redis-cli --scan --pattern '*'   → cache-api::item::1
TYPE cache-api::item::1          → string
TTL  cache-api::item::1          → 598      (spring.cache.redis.time-to-live: 10m)
```
실제 키는 `key-prefix("cache-api::")` + 캐시 이름(`item`) + `::` + 키(`1`)로 만들어집니다.

**두 번째 조회**: 같은 응답, 200, **이 요청에서 나간 SQL 없음** → 캐시 히트

**저장된 값** (`redis-cli GET`, 앞부분):
```
"\xac\xed\x00\x05sr\x000com.tave_2.cacheapi.domain.item.dto.ItemResponse..."   (383 bytes)
```
`\xac\xed\x00\x05`는 JDK 직렬화 시그니처입니다. JSON이 아니라서 redis-cli로는 내용을 바로 읽을 수 없습니다. 그래서 `ItemResponse`가 `Serializable`이어야 합니다.

## 2. 목록 캐시 · 수정 · 삭제

| 단계 | 응답 | 이 요청에서 나간 SQL | Redis |
|---|---|---|---|
| 목록 `GET ?page=0&size=10` | 200 (1건) | `select ... order by i1_0.created_at desc,i1_0.item_id desc limit ?,?` | `cache-api::itemPage::0:10` 생성 (TTL 599) |
| 목록 재조회 | 200 | **없음** | 목록 캐시 히트 |
| 수정 `PUT /items/1` | 200, 새 이름과 새 `updatedAt` | `select ... where item_id=?`<br>`update item set name=?,price=?,stock_quantity=?,updated_at=? where item_id=?` | `itemPage::0:10` **삭제됨**, `item::1`만 남음 (TTL 599로 다시 설정) |
| 수정 후 재조회 `GET /items/1` | 200, `발표검증용 상품(수정)` | **없음** | 캐시 값 바이트 안에 새 이름이 들어 있음 → `@CachePut`으로 갱신됨 |
| 삭제 `DELETE /items/1` | 204 | `select ... where item_id=?`<br>`delete from item where item_id=?` | SCAN 결과 없음, `EXISTS cache-api::item::1` → 0 |
| 삭제 후 재조회 | **404** `ITEM_NOT_FOUND` | `select ... where item_id=?` (캐시에 없으니 DB까지 감) | SCAN 결과 없음 → 404 결과는 캐시되지 않음 |

- 목록을 조회할 때 `count` 쿼리가 따로 나가지 않았습니다. 첫 페이지 결과 수(1)가 페이지 크기(10)보다 작으면 Spring Data가 전체 개수를 바로 계산할 수 있어서 count 쿼리를 생략하기 때문입니다.
- 수정 응답과 그 캐시 값의 `updatedAt`은 나노초 9자리(`...16.525915142`)입니다. MySQL `DATETIME(6)`에는 마이크로초까지만 저장되므로, 캐시가 만료된 뒤 DB에서 다시 읽으면 끝 3자리가 잘립니다. 기능에는 영향이 없습니다.

## 3. Actuator (prod)

| 요청 | HTTP | 응답 본문 |
|---|---|---|
| `GET /actuator/health` | 200 | `{"groups":["liveness","readiness"],"status":"UP"}` |
| `GET /actuator/health/liveness` | 200 | `{"status":"UP"}` |
| `GET /actuator/health/readiness` | 200 | `{"status":"UP"}` |
| `GET /actuator/health/db` | 404 | (본문 없음) |
| `GET /actuator/health/redis` | 404 | (본문 없음) |
| `GET /actuator` | 200 | `_links`에 `health`와 `health-path`만 있음 |

- **상세 숨김**: `components`와 `details`가 응답에 없고, 세부 항목 경로(`/db`, `/redis`)도 404입니다. `show-details: never`가 적용된 결과입니다.
- readiness가 UP이라는 건 설정상 `readinessState`, `db`, `redis` 검사를 함께 통과했다는 뜻입니다. 다만 상세 정보를 숨겨서 각각의 결과는 응답에 보이지 않습니다.

## 알려진 문제

**노출하지 않은 엔드포인트와 없는 경로가 404가 아니라 500을 돌려줍니다.** (아직 수정하지 않음)
```
GET /actuator/conditions | /beans | /caches | /env | /no-such-path
→ 500 {"success":false,"error":{"code":"INTERNAL_ERROR","message":"서버 내부 오류가 발생했습니다."}}
서버 로그: NoResourceFoundException: No static resource actuator/env for request '/actuator/env'.
```
- **원인**: 없는 경로로 요청하면 스프링은 `NoResourceFoundException`(원래 404)을 던집니다. 그런데 `GlobalExceptionHandler`의 마지막 `Exception` 핸들러가 이 예외까지 잡아서 500으로 바꿉니다.
- **영향**: 엔드포인트 내용은 노출되지 않습니다. 숨김 자체는 정상입니다. 하지만 응답 코드가 틀렸고, 없는 URL이 호출될 때마다 ERROR 로그가 남습니다.
- **해결 방향**: `NoResourceFoundException` 핸들러를 추가해서 404로 응답하고, 테스트를 추가합니다.

## 다시 해 보는 방법
```bash
docker compose up -d
docker compose exec redis redis-cli PING
DB_PASSWORD=<MYSQL_PASSWORD> ./gradlew bootRun --args='--spring.profiles.active=prod --spring.jpa.show-sql=true'

# 다른 터미널에서
curl -i -X POST localhost:8080/api/v1/items -H 'Content-Type: application/json' \
     -d '{"name":"검증용","price":1000,"stockQuantity":1}'          # 돌려받은 id 사용
curl localhost:8080/api/v1/items/{id}                              # 2번 호출 → 두 번째는 SQL 없음
docker compose exec redis redis-cli --scan --pattern 'cache-api::*'
docker compose exec redis redis-cli TTL cache-api::item::{id}
curl -i localhost:8080/actuator/health/readiness

# 정리
docker compose down
```
