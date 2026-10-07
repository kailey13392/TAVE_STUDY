package com.tave_2.cacheapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tave_2.cacheapi.config.CacheConfig;
import com.tave_2.cacheapi.dto.ItemCreateRequest;
import com.tave_2.cacheapi.dto.ItemResponse;
import com.tave_2.cacheapi.dto.ItemUpdateRequest;
import com.tave_2.cacheapi.exception.ItemNotFoundException;
import com.tave_2.cacheapi.service.ItemService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

/**
 * 캐시가 진짜 동작하는지 "실행된 SQL 개수"로 검증한다. (2주차 N+1 테스트와 같은 방식)
 *  - Hibernate Statistics 의 getPrepareStatementCount() = 이번에 DB 로 보낸 쿼리 수
 *  - 캐시가 먹으면 → 쿼리 수가 늘지 않는다
 *
 * 프로파일을 따로 안 주면 spring.profiles.default=local 이 적용 → Caffeine + H2 로 테스트.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ItemCacheTest {

	@Autowired ItemService itemService;
	@Autowired CacheManager cacheManager;
	@Autowired EntityManagerFactory emf;

	Statistics stats;

	@BeforeEach
	void setUp() {
		// 테스트끼리 캐시가 섞이지 않도록 매번 비우고, 쿼리 카운터도 0 으로
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
		stats = emf.unwrap(SessionFactory.class).getStatistics();
		stats.clear();
	}

	@Test
	void local_프로파일이면_Caffeine_캐시매니저가_자동구성된다() {
		// CacheManager 빈을 직접 안 만들었는데도, spring.cache.type=caffeine 을 보고 부트가 만들어 줬다
		assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
	}

	@Test
	void 단건조회_두번째부터는_DB를_안간다() {
		Long id = itemService.createItem(new ItemCreateRequest("키보드", 10000, 5)).id();
		stats.clear();

		itemService.getItem(id); // 1번째: 캐시 없음 → SELECT
		itemService.getItem(id); // 2번째: 캐시 히트
		itemService.getItem(id); // 3번째: 캐시 히트

		assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void 목록조회_두번째부터는_DB를_안가고_등록하면_목록캐시가_비워진다() {
		itemService.getItems();
		itemService.getItems();
		assertThat(stats.getPrepareStatementCount()).isEqualTo(1);

		itemService.createItem(new ItemCreateRequest("마우스", 5000, 3)); // @CacheEvict(itemList)
		assertThat(cacheManager.getCache(CacheConfig.ITEM_LIST).get(org.springframework.cache.interceptor.SimpleKey.EMPTY))
				.isNull();
	}

	@Test
	void 수정하면_CachePut으로_캐시가_최신값으로_바뀐다() {
		Long id = itemService.createItem(new ItemCreateRequest("모니터", 200000, 2)).id();
		itemService.getItem(id); // 캐시에 "모니터" 저장

		itemService.updateItem(id, new ItemUpdateRequest("모니터(할인)", 150000, 2));
		stats.clear();

		ItemResponse found = itemService.getItem(id);

		assertThat(found.name()).isEqualTo("모니터(할인)"); // 낡은 값이 아니라 수정된 값
		assertThat(stats.getPrepareStatementCount()).isZero(); // 그리고 DB 를 안 갔다 (CachePut 덕분)
	}

	@Test
	void 삭제하면_캐시도_지워져서_다시_조회하면_404예외() {
		Long id = itemService.createItem(new ItemCreateRequest("의자", 80000, 1)).id();
		itemService.getItem(id); // 캐시에 저장

		itemService.deleteItem(id); // @CacheEvict(item, key=id)

		// 캐시가 안 지워졌다면 삭제된 상품이 캐시에서 그대로 나왔을 것
		assertThatThrownBy(() -> itemService.getItem(id)).isInstanceOf(ItemNotFoundException.class);
	}
}
