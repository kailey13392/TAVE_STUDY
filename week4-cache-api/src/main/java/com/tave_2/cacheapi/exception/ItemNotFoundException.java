package com.tave_2.cacheapi.exception;

/** 없는 상품 id 로 요청했을 때 던지는 예외 → 404 */
public class ItemNotFoundException extends RuntimeException {

	public ItemNotFoundException(Long id) {
		super("상품을 찾을 수 없습니다. id=" + id);
	}
}
