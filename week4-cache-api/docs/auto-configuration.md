# 스프링 부트 자동 구성(Auto Configuration) 동작 원리

> 참고: 스프링 부트 - 핵심 원리와 활용(김영한) 자동 구성 파트, Spring Boot 공식 문서
> 아래 클래스 이름과 로그는 이 프로젝트(Spring Boot 4.1.1)를 실제로 실행해서 확인한 내용입니다.

## 1. 한 줄 요약
**"클래스패스에 어떤 라이브러리가 있는지"와 "설정값이 무엇인지"를 보고, 필요한 빈을 스프링 부트가 대신 등록해 주는 기능입니다.**
이 프로젝트에서는 `CacheManager`, `DataSource`, `RedisConnectionFactory`, 헬스 인디케이터 등을 직접 만들지 않았지만 모두 등록되어 있습니다.

## 2. 시작점: `@SpringBootApplication`
```
@SpringBootApplication
 ├─ @SpringBootConfiguration
 ├─ @ComponentScan             → 내가 만든 빈(@Service 등)을 등록
 └─ @EnableAutoConfiguration   → ★ 자동 구성을 시작하는 애노테이션
        └─ @Import(AutoConfigurationImportSelector.class)
```

## 3. 어떤 자동 구성 클래스를 후보로 올리는가
`AutoConfigurationImportSelector`는 **모든 jar 안의** 다음 파일을 읽습니다.
```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
예를 들어 `spring-boot-cache-4.1.1.jar` 안의 이 파일 내용은 다음과 같습니다(직접 열어서 확인).
```
org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration
org.springframework.boot.cache.autoconfigure.CachesEndpointAutoConfiguration
org.springframework.boot.cache.autoconfigure.metrics.CacheMetricsAutoConfiguration
```
→ **starter를 추가하면 그 jar에 들어 있는 자동 구성 후보가 함께 들어옵니다.**
(Spring Boot 4부터는 자동 구성이 기능별 모듈 jar로 나뉘어서 `spring-boot-cache`, `spring-boot-data-redis` 같은 jar에 각각 들어 있습니다.)

## 4. 후보 중 실제로 적용할 것은 `@Conditional`로 거른다
| 애노테이션 | 의미 | 이 프로젝트 예시 |
|---|---|---|
| `@ConditionalOnClass` | 클래스패스에 이 클래스가 있으면 적용 | Caffeine jar가 있으면 `CaffeineCacheConfiguration` 후보가 됨 |
| `@ConditionalOnMissingBean` | 내가 같은 타입의 빈을 만들지 않았으면 적용 | 직접 `CacheManager`를 만들면 부트는 만들지 않음(**내 설정 우선**) |
| `@ConditionalOnBean` | 이 빈이 있으면 적용 | `@EnableCaching`이 만든 `cacheInterceptor`가 있어야 캐시 자동 구성이 동작 |
| `@ConditionalOnProperty` | 설정값이 이것이면 적용 | `management.health.redis.enabled=false`면 Redis 헬스체크가 빠짐 |

## 5. 실제 증거: `/actuator/conditions` (local 실행 결과)
```
[적용됨] CacheAutoConfiguration
   @ConditionalOnClass found required class 'org.springframework.cache.CacheManager'
   @ConditionalOnBean ... found bean 'cacheInterceptor'
   @ConditionalOnMissingBean (types: CacheManager) did not find any beans
[적용됨] CaffeineCacheConfiguration
   found required classes 'Caffeine', 'CaffeineCacheManager'
   Cache ... CAFFEINE cache type
[적용 안 됨] RedisCacheConfiguration   → unknown cache type
[적용 안 됨] SimpleCacheConfiguration  → unknown cache type
```
즉, 다음 순서로 결정됩니다.
1. `@EnableCaching` → `cacheInterceptor` 빈 생성
2. 내가 만든 `CacheManager`가 없음 → `CacheAutoConfiguration` 적용
3. `spring.cache.type=caffeine` → Caffeine 설정만 통과하고 Redis·Simple 설정은 탈락
4. `CaffeineCacheManager` 빈 등록

prod에서는 3번만 `redis`로 바뀌어서 `RedisCacheManager`가 등록됩니다(`ProdProfileTest`로 검증).
`spring.cache.type`을 지정하지 않으면 부트가 클래스패스를 보고 정해진 우선순위에 따라 고르므로, 이 프로젝트처럼 Caffeine과 Redis가 함께 있을 때는 **명시적으로 지정하는 것이 안전합니다.**

## 6. 외부 설정과 프로파일
- `application.yml`(공통) → `application-{profile}.yml`(덮어쓰기) 순서로 적용됩니다.
- 활성 프로파일 지정: `--spring.profiles.active=prod` 또는 환경 변수 `SPRING_PROFILES_ACTIVE=prod`
- 아무것도 지정하지 않으면 `spring.profiles.default: local`이 적용됩니다.
- `${DB_PASSWORD}` 형태로 쓰면 환경 변수 값이 주입됩니다. 비밀번호를 Git에 올리지 않기 위한 방법입니다.
- `@Profile("local")`: 특정 프로파일에서만 빈을 등록합니다(`LocalDataInitializer`).

## 7. 정리
- 자동 구성은 **"기본값을 깔아 주되, 내가 직접 정의하면 비켜 주는"** 방식입니다(`@ConditionalOnMissingBean`).
- 그래서 우리는 대부분 **yml 설정값만 바꿔서** 동작을 조정합니다.
- 어떤 자동 구성이 왜 적용되었는지 궁금하면 `/actuator/conditions`를 보거나 `--debug` 옵션으로 실행하면 됩니다.
