package com.tave_2.cacheapi.repository;

import com.tave_2.cacheapi.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

/** 상품 리포지토리. 기본 CRUD 만 쓰므로 Spring Data JPA 기본 메서드로 충분 */
public interface ItemRepository extends JpaRepository<Item, Long> {
}
