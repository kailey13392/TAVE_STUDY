package com.tave_2.cacheapi.global.exception;

/** 에러 상세. ApiResponse 의 error 필드에 담긴다 { "code": "...", "message": "..." } */
public record ErrorResponse(String code, String message) {

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.name(), message);
	}
}
