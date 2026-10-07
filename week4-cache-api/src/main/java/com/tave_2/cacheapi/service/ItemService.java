package com.tave_2.cacheapi.service;

import com.tave_2.cacheapi.config.CacheConfig;
import com.tave_2.cacheapi.domain.Item;
import com.tave_2.cacheapi.dto.ItemCreateRequest;
import com.tave_2.cacheapi.dto.ItemResponse;
import com.tave_2.cacheapi.dto.ItemUpdateRequest;
import com.tave_2.cacheapi.exception.ItemNotFoundException;
import com.tave_2.cacheapi.repository.ItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
 * 캐시 전략: "목록(itemList)"은 상품 하나만 바뀌어도 내용이 달라지므로,
 *            등록/수정/삭제 때마다 목록 캐시는 통째로 비운다(allEntries = true).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

	private final ItemRepository itemRepository;

	/**
	 * 상품 등록.
	 * 새 상품이 생기면 목록 캐시가 낡으니 비운다. 단건 캐시는 아직 없으니 건드릴 필요 없음.
	 */
	@Transactional
	@CacheEvict(cacheNames = CacheConfig.ITEM_LIST, allEntries = true)
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

	/** 상품 목록 조회. 파라미터가 없으니 key 는 자동으로 SimpleKey.EMPTY 하나 */
	@Cacheable(cacheNames = CacheConfig.ITEM_LIST)
	public List<ItemResponse> getItems() {
		return itemRepository.findAll().stream()
				.map(ItemResponse::from)
				.toList();
	}

	/**
	 * 상품 수정.
	 *  - @CachePut   : 수정된 결과로 item::{id} 캐시를 바로 갱신 → 다음 조회 때 DB 안 가도 최신 값
	 *  - @CacheEvict : 목록 캐시는 비움
	 */
	@Transactional
	@Caching(
			put = @CachePut(cacheNames = CacheConfig.ITEM, key = "#id"),
			evict = @CacheEvict(cacheNames = CacheConfig.ITEM_LIST, allEntries = true)
	)
	public ItemResponse updateItem(Long id, ItemUpdateRequest request) {
		Item item = findItem(id);
		item.update(request.name(), request.price(), request.stockQuantity()); // 변경 감지로 커밋 시 UPDATE
		return ItemResponse.from(item);
	}

	/** 상품 삭제. 단건 캐시(item::{id})와 목록 캐시 둘 다 지운다 */
	@Transactional
	@Caching(evict = {
			@CacheEvict(cacheNames = CacheConfig.ITEM, key = "#id"),
			@CacheEvict(cacheNames = CacheConfig.ITEM_LIST, allEntries = true)
	})
	public void deleteItem(Long id) {
		itemRepository.delete(findItem(id));
	}

	private Item findItem(Long id) {
		return itemRepository.findById(id)
				.orElseThrow(() -> new ItemNotFoundException(id));
	}
}
