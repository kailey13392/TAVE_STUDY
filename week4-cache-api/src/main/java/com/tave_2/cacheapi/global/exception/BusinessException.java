package com.tave_2.cacheapi.global.exception;

import lombok.Getter;

/**
 * 우리 서비스의 "예상된" 예외(비즈니스 예외)의 부모 클래스.
 * ItemNotFoundException 처럼 이걸 상속하면 GlobalExceptionHandler 의 핸들러 하나가 전부 처리한다.
 * → 도메인마다 예외가 늘어나도 핸들러를 추가할 필요가 없다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.getMessage());
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
