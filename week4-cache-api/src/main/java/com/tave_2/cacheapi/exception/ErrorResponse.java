package com.tave_2.cacheapi.exception;

/** 에러 응답 형식을 하나로 통일 { "code": "...", "message": "..." } */
public record ErrorResponse(String code, String message) {
}
