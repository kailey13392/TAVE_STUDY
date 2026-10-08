package com.tave_2.cacheapi.domain.item.controller;

import com.tave_2.cacheapi.domain.item.dto.ItemCreateRequest;
import com.tave_2.cacheapi.domain.item.dto.ItemResponse;
import com.tave_2.cacheapi.domain.item.dto.ItemUpdateRequest;
import com.tave_2.cacheapi.domain.item.service.ItemService;
import com.tave_2.cacheapi.global.common.ApiResponse;
import com.tave_2.cacheapi.global.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 API (5개). 컨트롤러는 요청/응답 변환만 하고, 캐시 로직은 전부 ItemService 에 있다.
 *
 *  POST   /api/v1/items                  상품 등록   201
 *  GET    /api/v1/items?page=0&size=10   목록 조회   200  (최신순, 페이징, 캐시)
 *  GET    /api/v1/items/{id}             단건 조회   200  (캐시)
 *  PUT    /api/v1/items/{id}             상품 수정   200  (캐시 갱신)
 *  DELETE /api/v1/items/{id}             상품 삭제   204  (캐시 삭제)
 *
 * - /v1 : URL 버저닝. 나중에 응답 형식을 크게 바꿔야 하면 /v2 를 새로 열고, 기존 앱은 /v1 을 계속 쓰게 한다.
 * - 응답은 ApiResponse 로 감싼다. 단, 204(삭제)는 "본문 없음"이 규칙이라 감싸지 않는다.
 */
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

	private final ItemService itemService;

	@PostMapping
	public ResponseEntity<ApiResponse<ItemResponse>> create(@Valid @RequestBody ItemCreateRequest request) {
		ItemResponse created = itemService.createItem(request);
		// 201 Created + Location 헤더에 새로 만든 상품 주소를 담아 준다 (REST 관례)
		return ResponseEntity.created(URI.create("/api/v1/items/" + created.id()))
				.body(ApiResponse.ok(created));
	}

	/**
	 * page, size 에 붙인 @Min/@Max 는 스프링이 자동으로 검증한다(메서드 검증).
	 * 실패하면 HandlerMethodValidationException → GlobalExceptionHandler 가 400 으로 바꾼다.
	 */
	@GetMapping
	public ApiResponse<PageResponse<ItemResponse>> list(
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "page 는 0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "10")
			@Min(value = 1, message = "size 는 1 이상이어야 합니다.")
			@Max(value = 50, message = "size 는 50 이하여야 합니다.") int size) {
		return ApiResponse.ok(itemService.getItems(page, size));
	}

	@GetMapping("/{id}")
	public ApiResponse<ItemResponse> get(@PathVariable Long id) {
		return ApiResponse.ok(itemService.getItem(id));
	}

	@PutMapping("/{id}")
	public ApiResponse<ItemResponse> update(@PathVariable Long id, @Valid @RequestBody ItemUpdateRequest request) {
		return ApiResponse.ok(itemService.updateItem(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		itemService.deleteItem(id);
		return ResponseEntity.noContent().build();
	}
}
