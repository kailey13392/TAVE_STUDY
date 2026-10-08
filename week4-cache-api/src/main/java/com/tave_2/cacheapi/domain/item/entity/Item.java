package com.tave_2.cacheapi.domain.item.entity;

import com.tave_2.cacheapi.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 엔티티 (2주차 쇼핑몰 도메인의 Item 을 이어서 사용).
 * BaseTimeEntity 를 상속해서 createdAt / updatedAt 이 자동으로 채워진다.
 *
 * 주의: 엔티티는 캐시에 직접 넣지 않는다. 캐시에는 ItemResponse(DTO)를 넣는다.
 *   - 엔티티는 영속성 컨텍스트에 묶여 있고, 지연 로딩 프록시가 섞여 있을 수 있어서
 *     Redis 같은 외부 캐시에 직렬화하면 문제가 생기기 쉽다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 용 기본 생성자. 외부에서 new Item() 못 하게 막음
public class Item extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "item_id")
	private Long id;

	@Column(nullable = false)
	private String name;

	private int price;

	private int stockQuantity;

	public Item(String name, int price, int stockQuantity) {
		this.name = name;
		this.price = price;
		this.stockQuantity = stockQuantity;
	}

	/** 수정은 setter 대신 의미 있는 메서드로. 변경 감지(dirty checking)로 UPDATE 쿼리가 나간다 */
	public void update(String name, int price, int stockQuantity) {
		this.name = name;
		this.price = price;
		this.stockQuantity = stockQuantity;
	}
}
