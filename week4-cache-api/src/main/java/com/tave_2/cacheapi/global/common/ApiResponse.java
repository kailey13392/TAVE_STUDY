package com.tave_2.cacheapi.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tave_2.cacheapi.global.exception.ErrorResponse;

/**
 * 공통 응답 형식. 성공과 실패 모두 이 모양으로 내려간다.
 *
 *   성공: { "success": true,  "data": {...} }
 *   실패: { "success": false, "error": { "code": "ITEM_NOT_FOUND", "message": "..." } }
 *
 * 프론트는 항상 success 를 먼저 보고 data 또는 error 를 꺼내면 된다.
 * @JsonInclude(NON_NULL): null 인 필드(성공이면 error, 실패면 data)는 JSON 에서 아예 뺀다.
 *
 * 주의: 이 래퍼는 "컨트롤러에서" 씌운다. 캐시에는 래퍼가 아니라 알맹이 DTO 만 저장한다.
 *       (응답 형식이 바뀌어도 캐시 데이터는 영향 없게)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> fail(ErrorResponse error) {
		return new ApiResponse<>(false, null, error);
	}
}
