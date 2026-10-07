package com.tave_2.cacheapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 애플리케이션 시작점.
 *
 * @SpringBootApplication 안에는 3개가 합쳐져 있다.
 *  - @SpringBootConfiguration : 이 클래스도 설정(@Configuration) 클래스다
 *  - @ComponentScan           : 이 패키지(com.tave_2.cacheapi) 아래의 @Component/@Service/... 를 스캔
 *  - @EnableAutoConfiguration : ★ 자동 구성 시작 스위치 (docs/auto-configuration.md 참고)
 */
@SpringBootApplication
public class CacheApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CacheApiApplication.class, args);
	}
}
