package com.tave_2.cacheapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 컨트롤러에서 터진 예외를 한 곳에서 HTTP 응답으로 바꿔 주는 클래스.
 * 이게 없으면 예외가 그대로 500 에러가 되어 버린다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** 없는 상품 → 404 */
	@ExceptionHandler(ItemNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(ItemNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("ITEM_NOT_FOUND", e.getMessage()));
	}

	/** @Valid 검증 실패 → 400. 첫 번째로 실패한 필드 메시지만 내려준다 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.orElse("잘못된 요청입니다.");
		return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", message));
	}
}
