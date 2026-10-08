package com.tave_2.cacheapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.tave_2.cacheapi.domain.item.dto.ItemResponse;
import com.tave_2.cacheapi.global.common.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.test.context.ActiveProfiles;

/**
 * prod 프로파일 설정이 제대로 먹는지 확인 (MySQL/Redis 서버 없이).
 *
 *  - DB 는 테스트에서만 H2 로 바꿔 끼운다 (properties 로 prod 설정 덮어쓰기)
 *  - Redis 는 "실제로 명령을 보낼 때" 연결하므로, 서버가 없어도 CacheManager 빈 생성까지는 확인 가능
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:prodtest",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create"
})
@ActiveProfiles("prod")
class ProdProfileTest {

	@Autowired CacheManager cacheManager;

	@Test
	void prod_프로파일이면_Redis_캐시매니저가_자동구성된다() {
		// 코드는 local 과 똑같은데, spring.cache.type=redis 하나로 구현체가 바뀌었다
		assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
	}

	@Test
	void 캐시값은_Redis_기본직렬화를_통과해야_한다() {
		// RedisCacheManager 기본 직렬화(JDK)로 저장 → 다시 꺼내기를 흉내 낸다.
		// ItemResponse / PageResponse 에 Serializable 이 없으면 여기서 실패한다.
		JdkSerializationRedisSerializer serializer = new JdkSerializationRedisSerializer();
		ItemResponse item = new ItemResponse(1L, "키보드", 10000, 5, LocalDateTime.now(), LocalDateTime.now());
		PageResponse<ItemResponse> page = new PageResponse<>(List.of(item), 0, 10, 1, 1, false);

		// 단건 캐시 값(ItemResponse)과 목록 캐시 값(PageResponse) 둘 다 확인
		assertThat(serializer.deserialize(serializer.serialize(item))).isEqualTo(item);
		assertThat(serializer.deserialize(serializer.serialize(page))).isEqualTo(page);
	}
}
