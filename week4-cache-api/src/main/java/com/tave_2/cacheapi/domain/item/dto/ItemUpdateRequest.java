package com.tave_2.cacheapi.domain.item.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** 상품 수정 요청 (PUT 이라 전체 필드를 다 받는다) */
public record ItemUpdateRequest(
		@NotBlank(message = "상품 이름은 필수입니다.") String name,
		@Min(value = 0, message = "가격은 0 이상이어야 합니다.") int price,
		@Min(value = 0, message = "재고는 0 이상이어야 합니다.") int stockQuantity
) {
}
