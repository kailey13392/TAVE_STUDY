package com.tave_2.cacheapi.dto;

import com.tave_2.cacheapi.domain.Item;
import java.io.Serializable;

/**
 * 상품 응답 DTO = "캐시에 실제로 저장되는 값".
 *
 * implements Serializable 인 이유:
 *   prod 의 RedisCacheManager 는 기본적으로 JDK 직렬화로 값을 byte[] 로 바꿔 Redis 에 저장한다.
 *   Serializable 이 없으면 prod 에서만 NotSerializableException 이 터진다.
 *   (local 의 Caffeine 은 객체를 메모리에 그대로 두니까 없어도 돌아감 → 그래서 더 놓치기 쉬움)
 *
 * record 를 쓴 이유: 불변 객체라서 캐시에 넣어 두고 여러 요청이 같이 꺼내 써도 안전하다.
 */
public record ItemResponse(Long id, String name, int price, int stockQuantity) implements Serializable {

	public static ItemResponse from(Item item) {
		return new ItemResponse(item.getId(), item.getName(), item.getPrice(), item.getStockQuantity());
	}
}
