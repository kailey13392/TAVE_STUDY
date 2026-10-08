package com.tave_2.cacheapi.domain.item.service;

import com.tave_2.cacheapi.domain.item.dto.ItemCreateRequest;
import com.tave_2.cacheapi.domain.item.dto.ItemResponse;
import com.tave_2.cacheapi.domain.item.dto.ItemUpdateRequest;
import com.tave_2.cacheapi.domain.item.entity.Item;
import com.tave_2.cacheapi.domain.item.exception.ItemNotFoundException;
import com.tave_2.cacheapi.domain.item.repository.ItemRepository;
import com.tave_2.cacheapi.global.common.PageResponse;
import com.tave_2.cacheapi.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 서비스 = 캐시 애노테이션이 붙는 곳.
 *
 * 캐시 애노테이션 4종 정리
 *  - @Cacheable  : 캐시에 있으면 메서드 실행 안 하고 캐시 값 반환. 없으면 실행 후 결과를 캐시에 저장 (조회용)
 *  - @CachePut   : 메서드는 항상 실행하고, 결과로 캐시를 "덮어쓴다" (수정용)
 *  - @CacheEvict : 캐시에서 지운다 (삭제/변경으로 캐시가 낡았을 때)
 *  - @Caching    : 위 애노테이션 여러 개를 한 메서드에 같이 걸 때
 *
 * 동작 원리: 스프링이 이 클래스를 감싼 "프록시" 객체를 만들어서, 메서드 호출 전/후에 캐시를 확인한다.
 *  → 그래서 같은 클래스 안에서 this.getItem() 처럼 내부 호출하면 프록시를 안 거쳐서 캐시가 안 먹는다! (주의)
 *
 * 캐시 전략: "목록(itemPage)"은 상품 하나만 바뀌어도 모든 페이지 내용이 밀리거나 달라지므로,
 *            등록/수정/삭제 때마다 목록 캐시는 페이지 전부를 통째로 비운다(allEntries = true).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

	/** 목록 정렬: 최신 등록순. 생성 시간이 같을 때 순서가 흔들리지 않도록 id 를 2차 정렬로 */
	private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final ItemRepository itemRepository;

	/**
	 * 상품 등록.
	 * 새 상품이 생기면 목록 캐시가 낡으니 비운다. 단건 캐시는 아직 없으니 건드릴 필요 없음.
	 */
	@Transactional
	@CacheEvict(cacheNames = CacheConfig.ITEM_PAGE, allEntries = true)
	public ItemResponse createItem(ItemCreateRequest request) {
		Item item = itemRepository.save(new Item(request.name(), request.price(), request.stockQuantity()));
		return ItemResponse.from(item);
	}

	/**
	 * 상품 단건 조회. key = id
	 * 첫 호출: SELECT 쿼리 실행 → 결과를 item::{id} 로 캐시에 저장
	 * 두 번째부터: 쿼리 없이 캐시에서 바로 반환
	 * (없는 id 면 예외가 나서 캐시에 아무것도 저장되지 않는다)
	 */
	@Cacheable(cacheNames = CacheConfig.ITEM, key = "#id")
	public ItemResponse getItem(Long id) {
		return ItemResponse.from(findItem(id));
	}

	/**
	 * 상품 목록 페이지 조회 (최신순). key = "page:size" 예) "0:10"
	 *
	 * 페이징 + 캐시에서 신경 쓴 점
	 *  - 키에 page 와 size 를 둘 다 넣어야 한다. page 만 넣으면 size=10 결과와 size=20 결과가 같은 키로 섞인다.
	 *  - size 는 컨트롤러에서 최대 50 으로 막는다. 아무 숫자나 허용하면 캐시 키가 무한히 늘어날 수 있다.
	 *  - 정렬은 서버가 고정(최신순)한다. 정렬 조건까지 클라이언트에게 열면 키 조합이 너무 많아진다.
	 *  - 캐시 값은 Page 가 아니라 직접 만든 PageResponse(Serializable) 다.
	 */
	@Cacheable(cacheNames = CacheConfig.ITEM_PAGE, key = "#page + ':' + #size")
	public PageResponse<ItemResponse> getItems(int page, int size) {
		return PageResponse.of(
				itemRepository.findAll(PageRequest.of(page, size, LATEST_FIRST)),
				ItemResponse::from
		);
	}

	/**
	 * 상품 수정.
	 *  - @CachePut   : 수정된 결과로 item::{id} 캐시를 바로 갱신 → 다음 조회 때 DB 안 가도 최신 값
	 *  - @CacheEvict : 목록 캐시는 비움
	 */
	@Transactional
	@Caching(
			put = @CachePut(cacheNames = CacheConfig.ITEM, key = "#id"),
			evict = @CacheEvict(cacheNames = CacheConfig.ITEM_PAGE, allEntries = true)
	)
	public ItemResponse updateItem(Long id, ItemUpdateRequest request) {
		Item item = findItem(id);
		item.update(request.name(), request.price(), request.stockQuantity());
		// ★ flush 를 먼저 하는 이유:
		//   updatedAt(@LastModifiedDate)은 UPDATE 쿼리가 "나가기 직전"에 채워진다. 보통은 커밋할 때 나가는데,
		//   그러면 아래 ItemResponse 는 옛날 updatedAt 을 담은 채로 @CachePut 에 의해 캐시에 저장돼 버린다.
		//   여기서 flush 로 UPDATE 를 미리 내보내면 updatedAt 이 채워진 뒤에 응답(=캐시 값)을 만들 수 있다.
		itemRepository.flush();
		return ItemResponse.from(item);
	}

	/** 상품 삭제. 단건 캐시(item::{id})와 목록 캐시 둘 다 지운다 */
	@Transactional
	@Caching(evict = {
			@CacheEvict(cacheNames = CacheConfig.ITEM, key = "#id"),
			@CacheEvict(cacheNames = CacheConfig.ITEM_PAGE, allEntries = true)
	})
	public void deleteItem(Long id) {
		itemRepository.delete(findItem(id));
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id)
				.orElseThrow(() -> new ItemNotFoundException(id));
	}
}
