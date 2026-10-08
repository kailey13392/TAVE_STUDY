package com.tave_2.cacheapi.global.exception;

import com.tave_2.cacheapi.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 컨트롤러의 예외를 한 곳에서 잡아 "공통 응답 형식"으로 바꿔 주는 클래스.
 *
 * 처리 순서 (구체적인 예외 → 마지막에 Exception 으로 나머지 전부)
 *  1. BusinessException                 : 우리가 일부러 던진 예외 (404 상품 없음 등)
 *  2. MethodArgumentNotValidException   : @Valid @RequestBody 검증 실패 → 400
 *  3. HandlerMethodValidationException  : @RequestParam 검증 실패 (page=-1, size=1000) → 400
 *  4. MethodArgumentTypeMismatchException : /api/v1/items/abc 처럼 타입이 안 맞음 → 400
 *  5. HttpMessageNotReadableException   : JSON 문법 오류 → 400
 *  6. HttpRequestMethodNotSupportedException : 없는 메서드 (PATCH 등) → 405
 *  7. Exception                         : 위에서 못 잡은 모든 예외 → 500 (원인은 로그로만, 응답엔 숨김)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		return toResponse(e.getErrorCode(), e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException e) {
		// 여러 필드가 틀려도 첫 번째 것만 알려준다. 메시지는 DTO 의 message 속성 값 ex) "상품 이름은 필수입니다."
		String message = e.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse(ErrorCode.INVALID_REQUEST.getMessage());
		return toResponse(ErrorCode.INVALID_REQUEST, message);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<Void>> handleParamValidation(HandlerMethodValidationException e) {
		String message = e.getAllErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse(ErrorCode.INVALID_REQUEST.getMessage());
		return toResponse(ErrorCode.INVALID_REQUEST, message);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return toResponse(ErrorCode.INVALID_TYPE, e.getName() + " 값의 타입이 올바르지 않습니다.");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
		return toResponse(ErrorCode.INVALID_JSON, ErrorCode.INVALID_JSON.getMessage());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
		return toResponse(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		// 예상 못 한 에러: 스택트레이스는 서버 로그에만 남기고, 클라이언트에는 일반 메시지만 (내부 정보 노출 방지)
		log.error("Unexpected error", e);
		return toResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
	}

	private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
		return ResponseEntity.status(errorCode.getStatus())
				.body(ApiResponse.fail(ErrorResponse.of(errorCode, message)));
	}
}
