package com.tave_2.cacheapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** 상품 수정 요청 (PUT 이라 전체 필드를 다 받는다) */
public record ItemUpdateRequest(
		@NotBlank String name,
		@Min(0) int price,
		@Min(0) int stockQuantity
) {
}
