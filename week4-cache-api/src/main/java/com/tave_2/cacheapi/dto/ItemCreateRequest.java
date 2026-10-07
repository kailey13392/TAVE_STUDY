package com.tave_2.cacheapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** 상품 등록 요청. 검증 실패 시 GlobalExceptionHandler 가 400 으로 응답 */
public record ItemCreateRequest(
		@NotBlank String name,
		@Min(0) int price,
		@Min(0) int stockQuantity
) {
}
