package com.tave_2.cacheapi.domain.item.repository;

import com.tave_2.cacheapi.domain.item.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 리포지토리.
 * JpaRepository 가 findAll(Pageable) 을 이미 제공하므로 페이징 메서드도 따로 만들 필요가 없다.
 */
public interface ItemRepository extends JpaRepository<Item, Long> {
}
