package com.tave_2.cacheapi.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity 의 createdAt / updatedAt 자동 입력을 켠다.
 *
 * 메인 클래스(CacheApiApplication)에 @EnableJpaAuditing 을 붙여도 되지만,
 * 그러면 @WebMvcTest 같은 "웹만 띄우는 테스트"에서도 JPA 설정을 찾다가 에러가 난다.
 * 그래서 별도 설정 클래스로 분리하는 게 관례.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
