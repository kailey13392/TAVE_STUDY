package com.tave_2.cacheapi.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 설정.
 *
 * 이 클래스에서 하는 일은 딱 하나: @EnableCaching 으로 "캐시 애노테이션을 동작시켜라" 스위치를 켜는 것.
 * CacheManager(실제 캐시 저장소)는 직접 만들지 않는다!
 *   → 스프링 부트의 CacheAutoConfiguration 이 spring.cache.type 값을 보고 알아서 만들어 준다.
 *      local : spring.cache.type=caffeine → CaffeineCacheManager (서버 메모리)
 *      prod  : spring.cache.type=redis    → RedisCacheManager    (외부 Redis 서버)
 *   코드 한 줄 안 바꾸고 yml 만으로 캐시 구현체가 바뀌는 게 이번 과제의 핵심 포인트.
 *
 * 캐시 이름은 문자열 오타를 막으려고 상수로 모아 둔다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	/** 상품 단건 캐시. key = 상품 id */
	public static final String ITEM = "item";

	/** 상품 목록(페이지) 캐시. key = "페이지번호:페이지크기" (예: "0:10") */
	public static final String ITEM_PAGE = "itemPage";
}
