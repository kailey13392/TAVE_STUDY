package com.tave_2.cacheapi.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드를 한 곳에 모아 둔 목록.
 * "어떤 상황 → 어떤 HTTP 상태 + 어떤 코드 + 기본 메시지" 를 여기서 한 번에 관리한다.
 * 새 에러가 생기면 여기에 한 줄 추가하면 끝.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	INVALID_TYPE(HttpStatus.BAD_REQUEST, "요청 값의 타입이 올바르지 않습니다."),
	INVALID_JSON(HttpStatus.BAD_REQUEST, "요청 본문(JSON)을 읽을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

	// 상품
	ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String message;
}
