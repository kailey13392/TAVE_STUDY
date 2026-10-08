package com.tave_2.cacheapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tave_2.cacheapi.domain.item.dto.ItemCreateRequest;
import com.tave_2.cacheapi.domain.item.dto.ItemResponse;
import com.tave_2.cacheapi.domain.item.dto.ItemUpdateRequest;
import com.tave_2.cacheapi.domain.item.exception.ItemNotFoundException;
import com.tave_2.cacheapi.domain.item.service.ItemService;
import com.tave_2.cacheapi.global.config.CacheConfig;
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
	void 페이지조회_같은_page_size면_DB를_안가고_다른_size면_따로_캐시된다() {
		itemService.getItems(0, 10); // 1번째: SELECT(목록) + SELECT count(전체 개수) = 쿼리 2번
		long first = stats.getPrepareStatementCount();
		itemService.getItems(0, 10); // 같은 키 "0:10" → 캐시 히트
		assertThat(stats.getPrepareStatementCount()).isEqualTo(first);

		itemService.getItems(0, 5);  // 다른 키 "0:5" → 캐시 미스, 다시 DB 조회
		assertThat(stats.getPrepareStatementCount()).isGreaterThan(first);

		assertThat(cacheManager.getCache(CacheConfig.ITEM_PAGE).get("0:10")).isNotNull();
		assertThat(cacheManager.getCache(CacheConfig.ITEM_PAGE).get("0:5")).isNotNull();
	}

	@Test
	void 등록하면_목록캐시가_모든_페이지에서_비워진다() {
		itemService.getItems(0, 10);
		itemService.getItems(1, 10);

		itemService.createItem(new ItemCreateRequest("마우스", 5000, 3)); // @CacheEvict(itemPage, allEntries)

		assertThat(cacheManager.getCache(CacheConfig.ITEM_PAGE).get("0:10")).isNull();
		assertThat(cacheManager.getCache(CacheConfig.ITEM_PAGE).get("1:10")).isNull();
	}

	@Test
	void 페이지는_최신등록순으로_정렬된다() {
		Long older = itemService.createItem(new ItemCreateRequest("먼저 등록", 1000, 1)).id();
		Long newer = itemService.createItem(new ItemCreateRequest("나중 등록", 1000, 1)).id();

		var content = itemService.getItems(0, 2).content();

		assertThat(content).extracting(ItemResponse::id).containsExactly(newer, older);
	}

	@Test
	void 수정하면_CachePut으로_캐시가_최신값으로_바뀐다() {
		ItemResponse created = itemService.createItem(new ItemCreateRequest("모니터", 200000, 2));
		itemService.getItem(created.id()); // 캐시에 "모니터" 저장

		itemService.updateItem(created.id(), new ItemUpdateRequest("모니터(할인)", 150000, 2));
		stats.clear();

		ItemResponse found = itemService.getItem(created.id());

		assertThat(found.name()).isEqualTo("모니터(할인)"); // 낡은 값이 아니라 수정된 값
		assertThat(stats.getPrepareStatementCount()).isZero(); // 그리고 DB 를 안 갔다 (CachePut 덕분)
		// 캐시에 들어간 updatedAt 도 수정 시점으로 바뀌어 있어야 한다 (ItemService.updateItem 의 flush 덕분)
		assertThat(found.updatedAt()).isAfter(created.updatedAt());
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
