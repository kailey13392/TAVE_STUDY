package com.tave_2.cacheapi.domain.item.exception;

import com.tave_2.cacheapi.global.exception.BusinessException;
import com.tave_2.cacheapi.global.exception.ErrorCode;

/**
 * 없는 상품 id 로 요청했을 때 던지는 예외 → 404.
 * BusinessException 을 상속했기 때문에 GlobalExceptionHandler 에 따로 핸들러를 추가할 필요가 없다.
 */
public class ItemNotFoundException extends BusinessException {

	public ItemNotFoundException(Long id) {
		super(ErrorCode.ITEM_NOT_FOUND, "상품을 찾을 수 없습니다. id=" + id);
	}
}
